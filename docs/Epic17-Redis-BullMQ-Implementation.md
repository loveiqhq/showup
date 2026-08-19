# Epic 17 - Redis & BullMQ Background-Job Infrastructure

**Implementation notes.** What we built, the decisions behind it, and the tools used. This complements the Jira tickets ([SHOWUP-17](https://loveiq.atlassian.net/browse/SHOWUP-17)) - it does not repeat the requirements already written there. It focuses on the *how* and *why*: design choices, the extra pieces we added, deviations from the original plan, and the technology used.

Branch `feat/queue-epic-17`, PR #10. Tickets: [SHOWUP-93](https://loveiq.atlassian.net/browse/SHOWUP-93), [SHOWUP-94](https://loveiq.atlassian.net/browse/SHOWUP-94), [SHOWUP-95](https://loveiq.atlassian.net/browse/SHOWUP-95), [SHOWUP-111](https://loveiq.atlassian.net/browse/SHOWUP-111).

## At a glance

Epic 17 is **pure infrastructure** - it adds almost no user-facing surface (only a Redis check on the readiness probe). Its payoff is that *other* features can now run work in the background reliably: scheduled chores, retryable tasks, and (next) push notifications. It was built on NestJS 11 + TypeScript, adding three libraries - `ioredis` (the Redis driver), `bullmq` (the queue engine), and `@nestjs/bullmq` (the NestJS wiring) - plus `@nestjs/terminus` for the health check. Everything new is covered by Jest tests; the build is clean and lint is at zero errors.

One design thread runs through the whole epic: **a Redis hiccup must never crash the app.** Connection errors are logged rather than thrown, the client auto-reconnects, and the scheduler tolerates a Redis blip at boot. Consequences of that single rule appear in three of the four tickets.

## [SHOWUP-93 - Redis connection & configuration](https://loveiq.atlassian.net/browse/SHOWUP-93)

Beyond "make the app talk to Redis," the choices worth recording:

- **One config, two consumers.** Both the raw `ioredis` client and BullMQ read the *same* `redis.host/port/password` keys (from environment variables, defaulting to `localhost:6379`). Moving between a developer laptop, staging, and production is therefore a pure `.env` change - no code is environment-specific.
- **Resilience is deliberate, not default.** Connection errors are logged as warnings and never thrown, so a Redis outage at start-up does not crash boot. The client reconnects on its own with a capped backoff (200 ms rising to 2 s). `maxRetriesPerRequest` is set to `null` - required by BullMQ so its blocking commands wait for a reconnection instead of erroring out.
- **Clean shutdown.** The client is closed (`quit()`) when the module is destroyed, so tests and deploys never leak a dangling connection.
- **Where the health check lives.** Redis is pinged on `GET /health/ready` (readiness), *not* on `GET /health` (liveness). This is intentional: when Redis is down we want an orchestrator to hold traffic back, not to kill and restart the container.

## [SHOWUP-94 - BullMQ job-queue setup](https://loveiq.atlassian.net/browse/SHOWUP-94)

- **A global module so features stay one-liners.** `QueueModule` is `@Global`, so any feature registers its own queue with a single line - `BullModule.registerQueue({ name })` - and can inject the shared client and health indicator. There is no per-feature Redis boilerplate. The check-ins module is the worked example (it registers the `check-in-expiry` queue).
- **A single job-options policy** is applied to every queue (detailed under SHOWUP-95).

## [SHOWUP-95 - Idempotent, safely-retried jobs](https://loveiq.atlassian.net/browse/SHOWUP-95)

The ticket asks for jobs that are safe to retry and safe to run more than once; these are the *specific* mechanisms we standardised on:

- **Repeatable schedules via `upsertJobScheduler(stableId, { every }, ...)`.** Because each schedule has a *stable id*, restarting the app never stacks a second copy of the same schedule - it avoids the classic "cron got registered twice" bug. This is the pattern every future recurring job should copy.
- **State-guarded handlers as a second layer.** The work itself is written so a repeat run is a no-op: `expireDue` only touches rows still `available` with a window in the past, so a retry (or an accidental double-run) affects zero rows the second time.
- **One retry/retention policy for all jobs** (`DEFAULT_JOB_OPTIONS`): 3 attempts with exponential backoff (5 s base); completed jobs are pruned (keep ~100, for a day) so Redis does not fill up; failed jobs are kept longer (up to 1,000, for a week) so problems can be reviewed before they age out.

## [SHOWUP-111 - Automatic check-in expiry](https://loveiq.atlassian.net/browse/SHOWUP-111)

- **The first real consumer of the new infrastructure** - proof the plumbing works end to end. It is split into a *scheduler* (registers an every-60-seconds repeatable job when the module starts) and a *processor* (the worker that runs the work).
- **The work is one bulk UPDATE**, not a per-row loop: mark every check-in that is still `available` but whose window has ended as `expired`, and report how many rows changed.
- **It is housekeeping, not correctness-critical** - and that framing matters. Past-window check-ins are *already* treated as inactive the moment they are read (Epics 4-5); this job only tidies the *stored* status so the database does not accumulate stale "available" rows. That is precisely why a late or missed run is harmless, and why the idempotency above is enough.

## Changes from the original proposal

Based on the planning notes and the built code (for a line-by-line comparison against the original ticket text, I can pull the current Jira descriptions on request):

1. **SHOWUP-111 was previously deferred and is delivered here.** During the Epic 4/5 work the check-in "tidy-up" job was parked because there was no background-job runner to host it. Epic 17 created that runner, so 111 was un-deferred and built as the infrastructure's first consumer. In effect it was *conceived* with check-ins but *delivered* with the queue.
2. **We used BullMQ's own scheduler instead of adding a separate cron library** (e.g. `@nestjs/schedule`). One fewer dependency, and the schedule lives in Redis with the jobs - so it survives restarts and behaves correctly across multiple app instances (each due run is picked up once), which an in-process cron would not.
3. **The "never crash on a Redis blip" posture was an added design decision**, applied across 93/95/111 (log-not-throw, auto-reconnect, graceful scheduler failure at boot) rather than something the tickets spelled out.
4. **The epic stayed pure infrastructure** - no new user-facing endpoints beyond adding Redis to the readiness probe. That was a deliberate scoping choice: features consume the queue in their own epics.

## Tools used

| Tool | Version | Role |
|---|---|---|
| Redis | (Docker `showup-redis`) | In-memory store that backs the queue; also available for caching / ad-hoc use |
| BullMQ (`bullmq`) | ^5.80.6 | The job-queue engine - queues, workers, repeatable schedules, retries |
| `@nestjs/bullmq` | ^11.0.4 | NestJS integration (`BullModule`, `@Processor`, `@InjectQueue`, `WorkerHost`) |
| `ioredis` | ^5.11.1 | The Redis client/driver for Node |
| `@nestjs/terminus` | ^11.1.1 | Health-check framework (readiness/liveness, the Redis indicator) |
| NestJS + TypeScript | 11.x | Application framework and language |
| Jest | 30.x | Unit and end-to-end tests |
| Docker / docker compose | n/a | Runs local Redis (and Postgres) for development and e2e |

## Testing & current status

- **Unit** (`queue.constants.spec.ts`): asserts the queue name, the Redis injection token, and the shared job-options policy (retry/backoff and prune/retain counts).
- **End-to-end** (`test/queue.e2e-spec.ts`): boots the whole app and verifies (a) Redis reports healthy [93]; (b) the recurring schedule is registered [94/111]; (c) running the processor expires a past-window check-in [111]; and (d) running it a second time changes nothing [95 - idempotency].
- The e2e suite needs a running Redis + Postgres (via Docker); the Redis container was already up.
- Build is clean and lint reports zero errors. All four tickets sit on `feat/queue-epic-17`, gathered into **PR #10** for review.

## How it fits

This epic is a dependency-unlocker. It directly unblocks **Epic 10 (Notifications)** - which needs a queue to fan out and schedule messages - and any future scheduled or asynchronous work (retryable webhooks, digest jobs, cleanups). Nothing here is user-visible on its own; its value is entirely in what it lets later epics do safely.

## Ticket links

- Epic: [SHOWUP-17 - Redis & BullMQ Infrastructure](https://loveiq.atlassian.net/browse/SHOWUP-17)
- [SHOWUP-93 - Redis connection & configuration](https://loveiq.atlassian.net/browse/SHOWUP-93)
- [SHOWUP-94 - BullMQ job-queue setup](https://loveiq.atlassian.net/browse/SHOWUP-94)
- [SHOWUP-95 - Idempotent, safely-retried jobs](https://loveiq.atlassian.net/browse/SHOWUP-95)
- [SHOWUP-111 - Automatic check-in expiry](https://loveiq.atlassian.net/browse/SHOWUP-111)