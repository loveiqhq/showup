# Observability (Epic 16)

How the backend reports its own health and its own failures. Three pieces: health endpoints, structured
logging, and error monitoring.

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

## Where things live

```
src/common/logging/     json.logger.ts · request-context.ts · request-id.middleware.ts · redact.ts
src/common/errors/      sentry.setup.ts · all-exceptions.filter.ts · report-policy.ts · scrub.ts
src/common/privacy/     prohibited-fields.ts   (shared with analytics)
src/health/             health.controller.ts
```
