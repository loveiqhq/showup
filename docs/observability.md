# Observability (Epic 16)

How the system reports its own health and its own failures. Four pieces: health endpoints,
structured logging, backend error monitoring, and crash reporting on the two apps.

---

## 1. Health endpoints (SHOWUP-92)

Two endpoints, both public (no token — they are called by machines), implemented in
`src/health/health.controller.ts` with `@nestjs/terminus`.

| Endpoint | Purpose | What it checks | Use it for |
| --- | --- | --- | --- |
| `GET /health` | Liveness | Nothing beyond the process being up | **Restarting** the container |
| `GET /health/ready` | Readiness | PostgreSQL + Redis | **Deciding whether to send traffic** |

**Which to configure where — this distinction is the whole point:**

- Point the platform's **restart / liveness probe** at `GET /health`. It deliberately checks no
  dependencies, so a brief database or Redis blip never causes the service to be killed and restarted.
  Restarting the app does not fix a database outage; it just removes capacity while the database
  recovers.
- Point the **load balancer / traffic (readiness) probe** at `GET /health/ready`. It pings the database
  (`TypeOrmHealthIndicator.pingCheck('database')`) and Redis (`RedisHealthIndicator.isHealthy('redis')`)
  and fails with `503` if either is unreachable, naming the one that failed. An instance that cannot
  reach its database should not receive requests, but it should not be restarted either.

Getting this backwards causes a restart loop during a dependency outage, which is worse than the
outage itself.

**Adding a dependency later:** only add a check to readiness if the backend genuinely cannot serve
requests without it. The email and push providers, for example, should **not** be in readiness — they
are stubbed and non-essential, and a failing check there would pull healthy instances out of rotation
for something that only affects notifications.

**Covered by** `src/health/health.controller.spec.ts`, including the failing-dependency cases: liveness
stays healthy while the database is down; readiness fails when either the database or Redis is down.

---

## 2. Structured logging (SHOWUP-91)

Every log line is a single-line JSON record instead of a sentence, so lines can be filtered by field
and everything belonging to one request can be gathered together.

**Nothing at the call sites changed.** The existing `new Logger('Context')` usages across the codebase
still work exactly as before; `app.useLogger(new JsonLogger())` in `src/main.ts` only changes the shape
of the output.

A line looks like this:

```json
{"level":"info","time":"2026-08-11T19:41:00.861Z","msg":"inside a request",
 "context":"CheckInsService","requestId":"req-smoke-1","userId":"u1","phone":"[redacted]"}
```

**Fields**

| Field | Meaning |
| --- | --- |
| `level` | `info`, `warn`, `error`, `debug`, `trace`, `fatal` |
| `time` | ISO 8601, UTC |
| `msg` | The message |
| `context` | Which part of the code wrote it (the `new Logger('...')` name) |
| `requestId` | The request this line belongs to. Absent for background jobs, which have no request |

Metadata passed by the caller is merged in, and the reserved fields above always win, so a stray `msg`
or `level` in metadata cannot disguise what happened.

**Request correlation.** `RequestIdMiddleware` gives every request an id and runs the rest of the
request inside it using Node's `AsyncLocalStorage`, so lines written deep inside services are tagged
without threading anything through method signatures. An incoming `x-request-id` header is honoured, so
a trace started by the app or a proxy continues; the id is returned on the response, which means a
person reporting a problem can quote it and the exact request can be found.

To follow one request:

```bash
# every line for one request
grep '"requestId":"<id>"' app.log | jq .

# only failures, newest first
grep '"level":"error"' app.log | jq -r '[.time, .context, .msg] | @tsv'
```

**Never in a log line.** Contact details, secrets, coordinates and private message content are
redacted automatically by `redactForLog`, which replaces the value with `[redacted]` so it is visible
that a field existed. The list of prohibited names lives in
`src/common/privacy/prohibited-fields.ts` and is **shared with the analytics strip**, so logs and
analytics cannot disagree about what must be withheld. Identifiers and outcomes — the useful part of a
log line — are kept.

One naming rule follows from this: the secret in our sign-in payloads is named `code`, so that name is
withheld. An **API error code must be named `error_code`** or it will be redacted too.

---

## 3. Error monitoring with Sentry (SHOWUP-90)

Unhandled failures are reported to Sentry with a stack trace, the environment, and the request id, so
they can be lined up with the log lines for the same request.

**It is off by default and needs no account to build or run.** `initSentry` does nothing at all unless
`SENTRY_ENABLED` is true *and* `SENTRY_DSN` is set. With it off, `Sentry.captureException` is a no-op
and the backend behaves exactly as before.

To switch it on later — configuration only, no code change:

```bash
SENTRY_ENABLED=true
SENTRY_DSN=https://<key>@<org>.ingest.sentry.io/<project>
SENTRY_ENVIRONMENT=staging   # defaults to NODE_ENV
```

**What is reported, and what is not.** `AllExceptionsFilter` extends Nest's `BaseExceptionFilter`, so
it adds reporting and changes nothing about status codes or response bodies. `shouldReportToSentry`
decides:

- **Reported:** unexpected errors (anything that is not a deliberate HTTP response) and server-side
  failures, meaning HTTP 5xx.
- **Not reported:** ordinary client mistakes — 400, 401, 403, 404, 429. These are a normal part of
  running an API, they are already in the logs, and reporting them would bury the failures that
  actually need attention.

**Personal data never leaves in a report.** `sendDefaultPii` is off, so the SDK adds nothing of its
own, and every event passes through `scrubSentryEvent` in `beforeSend`, which drops `authorization` and
`cookie` headers and all cookies outright, runs the request body and query string through the same
redaction the logs use, and removes the user's email, username and IP address while keeping the user
id — knowing which account hit a failure is what makes it fixable.

---

## 4. Crash reporting on the apps (4 September 2026)

Sentry on Android and iOS, the same shape as the backend above: **off unless switched on and given a
DSN**, so both apps build and run with no account and no credential committed.

| Surface | Started by | Switched on with |
|---|---|---|
| Android | `Crashes.start` from `ShowUpApplication.onCreate` | `SENTRY_ENABLED` / `SENTRY_DSN` build-config fields |
| iOS | `Crashes.start` from `ShowUpWelcomeApp.init` | `CrashReporting.enabled` / `.dsn` in `Crashes.swift` |

**Android auto-init is disabled in the manifest** (`io.sentry.auto-init` = `false`). Sentry otherwise
reads a DSN from manifest metadata, which would mean committing one and losing the switch.

**Why reporting starts that early.** Android gains an `Application` subclass for this single purpose:
`MainActivity.onCreate` is already too late, and a crash during application startup is both the
hardest to reproduce and the most likely to hit every user at once. iOS starts it in the `App`
initialiser for the same reason.

### What the apps deliberately do not collect

Within reach of a mobile stack trace are phone numbers, one-time codes, coordinates and message text.

| Setting | Value | Why |
|---|---|---|
| `sendDefaultPii` | **off** | otherwise the SDK attaches IP address and device identifiers itself |
| Session tracking | **off** | release health needs a persistent installation identifier |
| Breadcrumbs | **0** | they auto-capture UI interactions and request URLs — on the phone screen, that is the number being typed |
| Screenshots, view hierarchy (iOS) | **off** | a screenshot of the phone screen *is* the phone number |
| `beforeSend` | **always set** | redacts every prohibited field name before an event leaves |
| `user` object | **always cleared** | an id, email or IP is never needed to fix a crash |

Breadcrumbs are the most useful feature here and the most dangerous, which is why they are off rather
than tuned. Reinstate only with an explicit allowlist.

### One list, three copies, compared

`prohibited-fields.ts`, `ProhibitedFields.kt` and `ProhibitedFields.swift` hold the same **47** field
names, matched on the normalised form exactly as section 3 describes.

**The three copies are not trusted to match — `audit/check-privacy-parity.py` compares them.** The
drift is silent and one-directional: a name added to the backend and forgotten on mobile means the
apps keep sending something the backend has decided is too sensitive to record, and nothing anywhere
fails. Verified by injection — removing a field from one list, or adding one, both fail the check and
name the field.

The Swift normaliser strips to ASCII explicitly rather than using `isLetter`, which accepts `ü`
and `é`. Left as `isLetter` the three normalisers would disagree on any non-ASCII field name.

### Before switching it on

1. **Create the Sentry organisation in the EU region.** Chosen at creation and **not migratable
   afterwards**; the DSN differs (`ingest.de.sentry.io`). The backend is already pinned to
   `eu-west-1`, so error reports landing in the US would be an odd gap. Available on the free tier.
2. **Sign Sentry's DPA** — they are a processor under GDPR even with scrubbing in place.
3. **Set the spending cap to $0** — an error loop should stop sending, not start billing.
4. Supply the DSN through the release pipeline, never by committing it.

The free tier is sufficient for now. Check current limits before relying on a number.

### Verification status

| | |
|---|---|
| Android gating and scrubbing | **verified** — 11 unit tests, plain JVM, no emulator |
| Android release build with the SDK | **verified** — `assembleRelease` passes, so R8 keeps what Sentry needs |
| Three-way list parity | **verified**, and the checker itself verified by injection |
| iOS gating and scrubbing | **UNVERIFIED** — 13 tests written, never compiled |

**iOS is unverified because CI has no runners.** The `sentry-cocoa` package reference, `Crashes.swift`
and `CrashesTests.swift` have never been through a compiler. `audit/check-pbxproj.py` confirms the
project structure is internally consistent, which is a different and much weaker claim.

---

## Where things live

```
src/common/logging/     json.logger.ts · request-context.ts · request-id.middleware.ts · redact.ts
src/common/errors/      sentry.setup.ts · all-exceptions.filter.ts · report-policy.ts · scrub.ts
src/common/privacy/     prohibited-fields.ts   (shared with analytics)
src/health/             health.controller.ts

mobile/welcome-screen/android-preview-project/app/src/main/java/com/showup/
                        ShowUpApplication.kt
                        observability/          Crashes.kt · ProhibitedFields.kt
mobile/welcome-screen/ios-app/ShowUpWelcome/
                        Crashes.swift · ProhibitedFields.swift · ShowUpWelcomeApp.swift
mobile/welcome-screen/audit/
                        check-privacy-parity.py   (compares all three lists)
```
