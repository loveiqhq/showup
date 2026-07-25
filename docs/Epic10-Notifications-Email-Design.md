# Epic 10 — Notifications & Email Backend (SHOWUP-10)

Design of record. Branch `feat/notifications-epic-10` (stacked on `feat/queue-epic-17`; retarget to
`main` once Epic 17 merges). Covers SHOWUP-66..71.

## Product decisions (confirmed with founder, 2026-07-24)

1. **SHOWUP-69 (date reminders):** build the reminder machinery + a public
   `scheduleDateReminder()` / `cancelDateReminder()` API now; **defer the Epic 7 trigger** (the
   `dates` domain is not built on this branch). The job payload carries everything the send needs,
   so no `dates` table is required.
2. **Email (SHOWUP-71):** provider is **AWS SES (eu-west-1)** — same region as the Rekognition face
   tools, so data stays in the EEA (GDPR Art. 9). Built behind an `EMAIL_SENDER` seam with a log
   stub as default; the real SES adapter is config-gated and ships now but activates only when
   credentials are present.
3. **Push (SHOWUP-68):** `PUSH_SENDER` seam + `LogPushSender` stub (default) + a config-gated
   `FcmPushSender` (uses `firebase-admin`, activates only when `fcm.projectId` is set).
4. **Locale (EN/DE):** add `users.locale` (`'en' | 'de'`, default `'en'`) + a small typed message
   catalog (no i18n framework dependency). Stored on the user so queue workers — which have no HTTP
   request — can resolve language. Requests may set it from `Accept-Language`.

## Data model — migration `1717000009000-Epic10Notifications`

| Object | Purpose |
| --- | --- |
| `notification_preferences` | `user_id` PK → `users` ON DELETE CASCADE; `essential`/`engagement` bool default true, `marketing` bool **default false**; timestamps. One row per user. |
| `push_tokens` | `id`, `user_id` → `users` CASCADE, `token text` **UNIQUE**, `platform` enum(`ios`,`android`), timestamps. Upsert on `token` conflict = dedupe. |
| `notification_log` | `id`, `user_id` → `users` ON DELETE **SET NULL** (survives purge, like `audit_logs`), `channel`(`push`,`email`), `type`, `category`, `locale`, `status`(`sent`,`failed`,`skipped`), `provider_message_id`, `error`, `created_at`. |
| `users.locale` | `varchar(8)` NOT NULL DEFAULT `en` on existing `users` (varchar, not an enum, so more languages need no migration). |
| `check_ins.expiry_reminder_sent_at` | nullable `timestamptz` added to the Epic 4 table — fire the expiry reminder once (SHOWUP-70). |

PG enums created: `push_tokens_platform_enum`, `notification_channel_enum`,
`notification_status_enum`. `locale` and the log's `category` are stored as `varchar` (small, growable
sets) rather than enums.

## Architecture — a single gated send-path

Every notification (push or email) is dispatched through **`NotificationDispatchService`** so that
preference-gating, the soft-delete status gate, locale resolution, logging, and dead-token pruning
live in exactly one place:

```
trigger → NotificationDispatchService.send(userId, type, params)
  1. load user; skip (log 'skipped') if status ∈ {suspended, deleted, deletion_pending}
  2. load prefs; if !shouldSend(category(type), prefs, {critical}) → log 'skipped'
  3. resolve locale (users.locale); render(type, locale, params) from the catalog
  4. hand to PUSH_SENDER / EMAIL_SENDER (seam → stub | gated real adapter)
  5. write notification_log; on invalid-token error → prune push_tokens
```

**Why:** a new feature literally cannot send a notification without going through the gate, so the
GDPR "never message a deleted user" rule and preference checks can't be forgotten.

### Preference gating policy (`util/preferences.ts`, pure)

- Categories: `essential`, `engagement`, `marketing`.
- Type → category map; `engagement`: `date_reminder`, `check_in_expiry`, `new_match`;
  `essential`: `email_verification`, `password_reset`, `account_closure`, `security_alert`,
  `safety_alert`; `marketing`: `marketing_*`.
- **Critical (always-send, bypasses toggles):** `email_verification`, `password_reset`,
  `account_closure`, `safety_alert`, `security_alert` — mandatory service/safety messages.
  Everything else respects its category toggle. Marketing additionally requires explicit consent
  (consent lives in the privacy epic; here it is the `marketing` toggle, default false).

## Module boundaries (dependencies flow one way: `check-ins → notifications`)

- **notifications module owns all sending.** Date reminders live fully here: `QUEUE_DATE_REMINDERS`,
  delayed jobs, deterministic job id `date-reminder:{dateId}` (idempotent + cancellable). Public
  `NotificationsScheduler.scheduleDateReminder(payload, sendAt)` / `cancelDateReminder(dateId)` for
  Epic 7 to call later.
- **check-ins module keeps owning check-in lifecycle** (SHOWUP-70): add
  `CheckInsService.findExpiringSoon()` + a delayed-job scheduler/processor there that calls
  `NotificationDispatchService`. No circular dependency.

## Seams

- `push/`: `PUSH_SENDER` token + `PushSender` interface; `LogPushSender` (stub) + `FcmPushSender`
  (real, gated). `PushResult` reports invalid tokens for pruning.
- `email/`: `EMAIL_SENDER` token + `EmailSender` interface; `LogEmailSender` (stub) + `SesEmailSender`
  (real, gated).
- `messages/catalog.ts`: typed `{ type: { en, de } }` dictionary + pure `render(type, locale, params)`.

## Per-ticket acceptance

- **66** prefs CRUD; marketing off by default; `GET`/`PATCH /me/notification-preferences`.
- **67** `POST`/`DELETE /me/push-tokens`; upsert dedupe; deregister; deleted-user gate.
- **68** push send; failure handling; dead-token prune; delivery log; EN/DE.
- **70** reminder scheduled on check-in; pref-gated; no dup on cancel/expire; discreet wording.
- **71** SES seam; transactional emails (verify, reset, closure, safety) EN/DE; failure log; no
  marketing without consent.
- **69** scheduler API + processor; fires to both participants only; idempotent; cancellable.

## Deferred / out of scope

Real Firebase & SES **credentials** (adapters ship gated-off; stubs run by default); the Epic 7
date **trigger**; `new_match` push (Epic 6 trigger not in these tickets — catalog entry only);
hard-purge of tokens (rides the existing soft-delete story — sends stay gated by live status).

## Testing

Unit specs with hand-rolled fakes (repo fakes, `jest.fn()` senders/queues) for the pure logic and
services. One `test/notifications.e2e-spec.ts` bootstrapping `AppModule`, seeding via repositories,
using the social-login helper to dodge the OTP throttle. Requires Docker Postgres + Redis.
