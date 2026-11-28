# Date 09 — your date has ended (the +2 h switch): Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When nobody ends a date, the server closes it 2 h after the latest agreed meet time (switch shipped OFF). Both people see Date 08's screen with a fourth payload, *Your date has ended*, whose only control is `Proceed to review`.

**Architecture:**
- The server gains a once-a-minute sweep that closes due dates under Date 06's per-date serialisation, a `timed_out` state, and an ended outcome that both clients read (`reason timed_out`, `partnerScreen date_timed_out`, `meetAt`).
- Each app adds one payload to the existing `PartnerEndedScreen`, routes `timed_out` to it first, and discards what is open (including the check-in done sheet) without sending anything.

**Tech Stack:** NestJS + TypeORM + Postgres + BullMQ (server); Kotlin / Jetpack Compose + Robolectric (Android); SwiftUI + XCTest (iOS, compiled only in CI on macos-15); Python checkers.

**Spec:** `docs/superpowers/specs/2026-11-28-date-09-timed-out-design.md` (rulings R1–R14).

## Global Constraints

- **Workspace:** the worktree `C:/Users/krnji/showup-200`, branch `feat/date-09-timed-out`, stacked on `feat/date-08-partner-end`.
  - Commit there only and push every commit.
  - Never touch `C:/Users/krnji/showup`: it is another branch, in use.
- **Copy:** the Jira ticket's, verbatim (`design_handoff_showup/date/tickets/09-timed-out.md`, `## Copy — final strings`), with straight `'` as the ticket writes it. `{name}` is the match's first name. Product terms: **Show Up**, **Show-up Rate**.
- **Numbers:** from `design_handoff_showup/date/screen-timed-out-reference.jsx` and spec R8, never from the PNG.
- **Parity:** both apps stay in parity (names, rules, tests). Android first, iOS mirrors it.
- **Tokens, not literals:** `LilacWash`, `Purple` (primary 500), `Orange`, `SuccessFg`, and the iOS twins. No new hex in a screen.
- **General rules:**
  - Touch floor 44 on both platforms.
  - No emoji. Comments say why, not what.
  - Never log coordinates, accuracy, phone, code, token, email, name or message body.
- **Memory on this machine:**
  - Gradle:
    - Run `./gradlew --stop` first, and close the emulator.
    - Run ONE Gradle at a time, across both checkouts, with `-Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.compiler.execution.strategy=in-process`.
    - Wrap every Gradle call in `timeout 900`.
    - Use `JAVA_HOME=$HOME/.jdks/jbr-21.0.11`.
    - Run narrow `--tests` filters; CI runs the full suite.
    - Run `./gradlew --stop` after.
  - Server: run `npm run lint:ci && npm test` before committing. Run e2e files locally only if Postgres is up; otherwise say they run in CI.
- **Never commit** `.semgrep/` or `.superpowers/`.
- **Commit messages** end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. A Date 06 end that loses the race to the switch shows Date 09, never `EndErrorBox`. Tests: Task 2 e2e, Task 4 / 5 routing.
2. A check-in done sheet open at the switch: the row is kept, there is no arrival push, Undo is refused, and the sheet is gone. Tests: Task 2 e2e, Task 4 / 5 discard.
3. Two sweeps (or a crashed retry) never close twice nor fire `date_ended` twice. Test: Task 2.
4. An agreed later time moves the switch; a pending suggestion and an agreed place change do not. Test: Task 2.
5. With the switch off nothing closes; the dev frames still show the screen. Tests: Task 2, Task 4 / 5 dev.
6. A / B / C render pixel-identically after `PartnerBadgeTone`. Test: Task 3 (evidence diff of the 199 shots).

---

### Task 1: Server — the timed-out state, the outcome, `checked_in_count`, registry 1.4.40

**Files:**
- Create: `src/database/migrations/1717000039000-DateTimeout.ts`. Follow the shape of `1717000037000-DateEnds.ts`: `ALTER TYPE … ADD VALUE IF NOT EXISTS` for `dates_status_enum` `timed_out`, `date_end_reason_enum` `timed_out` and `end_entry_enum` `auto_switch`. The `down` rebuilds each type, the way 037's `down` does, after moving any `timed_out` rows back to `confirmed` with a null reason and entry.
- Modify:
  - `src/modules/dates/util/date-lifecycle.ts`: `DateStatus.TimedOut`; `ALLOWED_TRANSITIONS` Confirmed → TimedOut, and TimedOut terminal.
  - `src/modules/dates/util/date-end.ts`: `EndReason.TimedOut`, `EndEntry.AutoSwitch`, `PartnerScreen.DateTimedOut = 'date_timed_out'`, and `partnerScreenFor` TimedOut → DateTimedOut. Also export `CLIENT_END_REASONS` / `CLIENT_END_ENTRIES` (the three Date 06 values each).
  - `src/modules/dates/dto/end-date.dto.ts`: `@IsIn(CLIENT_END_REASONS)` / `@IsIn(CLIENT_END_ENTRIES)`, so a client can never post `timed_out` or `auto_switch`. Keep the OpenAPI enum to the three.
  - `src/modules/date-screen/dto/active-date.dto.ts`: `ENDED_REASONS` + `timed_out`, `PARTNER_SCREENS` + `date_timed_out`, and an optional `meetAt?: string` on `EndedOutcomeDto` (ISO 8601, described as "the latest agreed meet time").
  - `src/modules/date-screen/date-screen.service.ts`: add `ENDED_STATUSES` + TimedOut. Select `scheduledAt` for the outcome if the query does not already.
  - `src/modules/date-screen/util/active-date.ts`: `endedOutcomeOf` accepts TimedOut. For it: reason `timed_out`, `endedByYou: false` (`ended_by_id` is null), `partnerScreen: 'date_timed_out'` for both people. Set `meetAt` on every Ended / EndedInChat / TimedOut / Cancelled outcome from the row's `scheduledAt`.
  - `src/modules/analytics/events/date-events.ts`: `dateEndedEvent` takes `checkedInCount: number` (always sent as `checked_in_count`). `enderCheckedIn` / `partnerCheckedIn` become optional and are omitted when undefined.
  - `src/modules/dates/date-end.service.ts`: pass `checkedInCount = Number(enderCheckedIn) + Number(partnerCheckedIn)`.
  - Registry:
    - `design_handoff_showup/tracking/events.json` and `enums.json`: copy from `C:/Users/krnji/AppData/Local/Temp/claude/1a871033e80c680c/33f1e072-9d7d-4446-a930-54c75a306b73/scratchpad/200/merged-events.json` and `merged-enums.json`. These are the zip's definitions with the repo's build-status carried over; do not hand-edit them.
    - The server copy `src/modules/analytics/registry/enums.json`: `registry_version` `1.4.40`, `taxonomy_version` `1.4.32`. Add, in that file's own shape: §43 `timed_out`, §45 `auto_switch`, and the §11 row `date_timed_out` / `Date - Timed out`. `sensitivity.spec.ts` compares it with the handoff copy.
  - `openapi.json` + `mobile/welcome-screen/ios-app/ShowUpAPI/Sources/ShowUpAPI/openapi.json`: regenerate with `npm run openapi:emit`. Never hand-edit them.
- Test:
  - `src/modules/date-screen/util/active-date.spec.ts` (or wherever `endedOutcomeOf` is tested)
  - `src/modules/analytics/events/date-events.spec.ts`
  - `src/modules/dates/date-end.service.spec.ts`
  - `src/modules/dates/util/date-end.spec.ts`
  - the lifecycle spec if one exists
  - `test/date-end.e2e-spec.ts` (`checked_in_count`)
  - `test/date-screen.e2e-spec.ts` (a timed-out row seen by both people)

**Interfaces:**
- Produces:
  - `DateStatus.TimedOut = 'timed_out'`, `EndReason.TimedOut = 'timed_out'`, `EndEntry.AutoSwitch = 'auto_switch'`, `PartnerScreen.DateTimedOut = 'date_timed_out'`.
  - `dateEndedEvent({ dateId, endReason, entry, checkedInCount, enderCheckedIn?, partnerCheckedIn?, minutesVsStart })`.
  - `EndedOutcomeDto.meetAt?: string`.
  - OpenAPI enums `EndedReason` (…, `timed_out`) and `PartnerScreen` (…, `date_timed_out`).

- [ ] **Step 1: failing tests.**
  - `endedOutcomeOf`:
    - a TimedOut row gives `{ reason: 'timed_out', endedByYou: false, partnerScreen: 'date_timed_out', meetAt }` for viewer A AND for viewer B;
    - a seen TimedOut gives null;
    - an Ended row also carries `meetAt`.
  - `dateEndedEvent`:
    - a Date 06 end carries `checked_in_count` (0, 1, 2 from the booleans) and the six old fields;
    - a timed-out call omits `ender_checked_in` / `partner_checked_in` and carries `end_reason: timed_out`, `entry: auto_switch`, `checked_in_count`.
    - Replace the old "six fields and nothing else" assertion with the new exact key sets.
  - `EndDateDto` rejects `timed_out` and `auto_switch` (class-validator test or e2e 400).
  - Lifecycle: Confirmed → TimedOut is allowed; TimedOut → anything is refused.
  - e2e:
    - a Date 06 end's `date_ended` has `checked_in_count` matching committed check-ins;
    - `GET /me/date` for both people of a date set to `timed_out` (insert via the repository) returns the outcome above, and `POST /dates/:id/ended/seen` clears it.
- [ ] **Step 2:** run them and see them fail.
- [ ] **Step 3:** implement. Run `npm run openapi:emit` and commit both copies.
  - Grep both apps for exhaustive `when` / `switch` over the generated `EndedReason` / `PartnerScreen` types, and report every hit in the report. Tasks 3–5 handle them; do not edit the apps.
- [ ] **Step 4:** `npm run lint:ci && npm test` (0 errors). Run the touched e2e files if Postgres is up.
- [ ] **Step 5:** commit `feat(dates): the timed-out state, its outcome for both people, and checked_in_count on every date_ended (SHOWUP-200)`; push.

### Task 2: Server — the +2 h switch (off by default) and the arrival guards

**Files:**
- Create:
  - `src/modules/dates/util/date-timeout.ts`, pure:
    - `TIMEOUT_AFTER_MS = 2 * 60 * 60 * 1000`;
    - `timeoutDue(scheduledAt: Date, now: Date): boolean` (true at exactly +2 h);
    - `dateTimeoutEnabled(flag: string | undefined): boolean`, true only for `'true'` case-insensitive;
    - `TIMEOUT_SWEEP_LIMIT = 100`.
  - `src/modules/dates/date-timeout.service.ts`: `closeDue(now = new Date()): Promise<number>`.
  - `src/modules/dates/jobs/date-timeout.scheduler.ts`: id `date-timeout-every-minute`, job `close-due`, every 60 000.
  - `src/modules/dates/jobs/date-timeout.processor.ts`, with the shapes of `change-expiry.*`.
- Modify:
  - `src/queue/queue.constants.ts`: `QUEUE_DATE_TIMEOUT = 'date-timeout'`.
  - `src/modules/dates/dates.module.ts`: register the queue, service, scheduler and processor.
  - `src/config/env.validation.ts`: `DATE_TIMEOUT_ENABLED`, `Joi.string().valid('true','false').insensitive().optional()`.
  - `.env.example`: a commented line saying it is off by default until flow 10 exists.
  - `src/modules/dates/arrival-check-ins.service.ts`: R6.
- Test:
  - `src/modules/dates/util/date-timeout.spec.ts` (every function: the util coverage gate is 100 % functions)
  - `src/modules/dates/date-timeout.service.spec.ts`
  - `src/modules/dates/jobs/date-timeout.processor.spec.ts`
  - `src/modules/dates/arrival-check-ins.service.spec.ts`
  - new `test/date-timeout.e2e-spec.ts` (shape of `test/date-end.e2e-spec.ts`)

**Interfaces:**
- Consumes: Task 1's `DateStatus.TimedOut`, `EndReason.TimedOut`, `EndEntry.AutoSwitch`, `dateEndedEvent` with `checkedInCount`.
- Produces:
  - `DateTimeoutService.closeDue(now)` returns how many dates it closed. It is called directly by tests, the way `expireDue` is.
  - The arrival refusals use `code: 'date_not_active'`.

- [ ] **Step 1: failing tests.**
  - util:
    - due at exactly +2 h, not at +2 h − 1 ms;
    - the switch is on only for `true` / `TRUE`; off for unset, `false`, `1` and `yes`.
  - service (mocks, shape of `date-end.service.spec.ts`):
    - the switch off closes nothing and runs no query;
    - one transaction per date with a `pessimistic_write` lock;
    - a locked row that is no longer confirmed, or whose `scheduledAt` moved so it is no longer due, is skipped and fires nothing;
    - the conditional update sets status / endedAt / endReason / endEntry, with `endedById` null;
    - the match is cancelled and a status-change row is written;
    - `date_ended` fires once after commit with `end_reason timed_out`, `entry auto_switch`, `checked_in_count`, `minutes_vs_start`, no ender fields, and participant A's userId;
    - one date throwing does not stop the next;
    - no push is sent.
  - processor: `process()` calls `closeDue` once and returns its count.
  - arrival:
    - `undo` on a non-confirmed date gives 409 `date_not_active` and deletes nothing;
    - `create` on one gives 409 `date_not_active`;
    - `commit` on one gives 409 `date_not_active`, with no push and no chat open;
    - `commit` re-checks the status under a `FOR SHARE` lock inside its transaction.
  - e2e, with `DATE_TIMEOUT_ENABLED=true` set for the file:
    - a date at +2 h closes for 0, 1 and 2 committed check-ins, and with an open discomfort flag; both people's `GET /me/date` show the timed-out outcome;
    - at +2 h − 1 min it does not close;
    - after an agreed later time (`agree` on a time suggestion) the old due time closes nothing and the new one does;
    - a pending time suggestion and an agreed place change leave the due time alone;
    - a date already ended / cancelled / ended in chat is untouched and fires nothing;
    - calling `closeDue` twice closes once and fires one `date_ended`;
    - a Date 06 `end` and a Date 07 `cancel` after the switch answer 409 `date_not_active` and store nothing;
    - a check-in made (Yes) before the switch but committed after gets 409, its row is still there, and no `date_partner_arrived` is queued;
    - with the switch unset, nothing closes.
- [ ] **Step 2:** run them and see them fail.
- [ ] **Step 3:** implement per R2 / R3 / R6 / R7.
  - The candidate query selects ids of `status = 'confirmed' AND scheduled_at <= now - interval '2 hours'`, ordered by `scheduled_at`, `LIMIT TIMEOUT_SWEEP_LIMIT`.
  - Each date is closed in `this.db.transaction` with the same lock + conditional-update pattern as `date-end.service.ts`. Count committed check-ins inside the transaction.
  - Log only counts and date ids.
  - The scheduler is registered whatever the switch says; `closeDue` reads the switch per run through `ConfigService`.
- [ ] **Step 4:** `npm run lint:ci && npm test` and `npm run test:cov` (util thresholds hold). Run `test/date-timeout.e2e-spec.ts` if Postgres is up.
- [ ] **Step 5:** commit `feat(dates): the +2 h switch closes a date nobody ended, off until flow 10 (SHOWUP-200)`; push.

### Task 3: Android — the fourth payload, `PartnerBadgeTone`, `{time}`

**Files** (main code under `mobile/welcome-screen/android-preview-project/app/src/main/java/com/showup/`):
- Modify:
  - `date/ActiveDate.kt`: `PartnerScreen.DateTimedOut("date_timed_out")`, `OutcomeReason.TimedOut("timed_out")`, and `EndedOutcome.meetAt: Instant?` (nullable, default null).
  - `date/DateEndRepository.kt`: map `meetAt` and both new wire values in `toOutcome`.
  - `date/partnerend/PartnerEndedRules.kt`: `enum class PartnerBadgeTone { Orange, Success, Violet }` replaces `badgeSuccess`; payload D per R8; `partnerEndedScreenId` D → `ProfileScreen.DateTimedOut`.
  - `profile/ProfileAnalytics.kt`: add the row `ProfileScreen.DateTimedOut("date_timed_out", "Date - Timed out")` here, because the `when` is exhaustive. Task 4 bumps the stamp.
  - `date/partnerend/PartnerEndedCopy.kt`: a `// D ·` section after C with `D_HEADLINE_LEAD` `Your date has `, `D_HEADLINE_ITALIC` `ended.`, `dSub(name, time)` and `D_SUB_NO_TIME`, and `D_ROWS` as `Run` lists with the bold pieces.
  - `date/partnerend/PartnerEndedScreen.kt`: the badge colour from `PartnerBadgeTone`; the D branches of the headline and sub `when`s; a new `meetTime: String?` parameter (the formatted clock, null → no-time sub).
  - `date/partnerend/PartnerEndedSamples.kt` + `PartnerEndedPreviews.kt`: add D (sample time `19:00`).
- Create: `date/partnerend/MeetClock.kt`: `fun meetClock(meetAt: Instant, zone: ZoneId, is24h: Boolean): String`, giving `HH:mm` or `h:mm a` with `Locale.ENGLISH` AM/PM.
- Test (under `app/src/test/java/com/showup/`):
  - `date/partnerend/PartnerEndedRulesTest.kt`
  - `date/partnerend/PartnerEndedScreenTest.kt`
  - `fit/PartnerEndedFitTest.kt`
  - new `date/partnerend/MeetClockTest.kt`

**Interfaces:**
- Consumes: the OpenAPI `PartnerScreen.date_timed_out` / `EndedReason.timed_out` / `meetAt` from Task 1 (the Gradle build regenerates the client from the root `openapi.json`).
- Produces:
  - `PartnerScreen.DateTimedOut`, `OutcomeReason.TimedOut`, `EndedOutcome.meetAt`;
  - `PartnerBadgeTone`, `ProfileScreen.DateTimedOut`;
  - `PartnerEndedScreen(screen, name, photoUrl, meetTime: String?, scroll, onProceed)`;
  - `meetClock(...)`.

- [ ] **Step 1: failing tests.**
  - Rules:
    - D maps to lilac, padding 24 / 30, gap 18, `usesPhoto = true`, `PartnerBadgeTone.Violet`, Clock 15 / 2.6, rows top 18, rows Heart / Search, `RowAccent.Violet`;
    - A / B / C keep their values, with `PartnerBadgeTone.Orange` / `Orange` / `Success`;
    - the shared-layout test covers D.
  - Screen:
    - D reads the ticket's headline, sub (with `19:00` and with `7:00 PM`) and both rows exactly;
    - with `meetTime = null` the sub ends `…2 hours after the meet time.`;
    - the CTA is the only click action;
    - the hero shows the match photo, not the heart disc;
    - no node reads a pronoun about the match, "no-show", "uncomfortable" or a digit other than the time.
  - MeetClock:
    - 19:00 local → `19:00` (24 h) and `7:00 PM` (12 h);
    - 00:05 → `00:05` / `12:05 AM`;
    - noon → `12:00` / `12:00 PM`;
    - a zone other than UTC is honoured.
  - Fit: D at the 17 `FitHarness` sizes and the largest-type row (no clip, CTA reachable, CTA below the last row); at 375 × 667, state in the report whether D's CTA needs a scroll.
- [ ] **Step 2:** run them and see them fail (`--tests "com.showup.date.partnerend.*" --tests "com.showup.fit.PartnerEndedFitTest"`).
- [ ] **Step 3:** implement. One layout: D only adds a payload row and copy. The badge uses `Purple` for Violet.
- [ ] **Step 4:** run the tests above. Then run `-Pevidence` for the existing SHOWUP-199 shots, if the harness allows a filter, and confirm they are byte-identical to the committed ones (A / B / C unchanged by `PartnerBadgeTone`). If they cannot be filtered, say so.
- [ ] **Step 5:** commit `feat(date): Date 09's payload on PartnerEndedScreen, with the meet time in the device's clock, on Android (SHOWUP-200)`; push.

### Task 4: Android — routing, closing what is open, tracking, dev frames, evidence

**Files** (under `…/java/com/showup/`):
- Modify:
  - `date/cancel/CancelRules.kt`: `endedRoute` puts the `TimedOut` branch first and returns `PartnerEnded` with screen `DateTimedOut`. Update the KDoc.
  - `chat/ChatRouting.kt`: `partnerEndedRoute` takes the screen from `partnerScreen ?: DateTimedOut.takeIf { reason == TimedOut }` (never `checkNotNull` on a timed-out outcome), and carries `meetAt` as an ISO string from `outcome.meetAt ?: heldDate?.meetAt`.
  - `AppRoute.kt`: `PartnerEnded` gains `meetAt: String? = null`.
  - `MainActivity.kt`: the route composable formats `meetAt` with `meetClock(…, ZoneId.systemDefault(), DateFormat.is24HourFormat(context))` and passes `meetTime`. `showPartnerEnded` also calls the new arrival-gate and chat discards.
  - `arrival/ArrivalGateViewModel.kt`: `fun discardForPartnerEnd()`. It moves `phase` to `Idle` and clears saved state, with no `store.commit`, no `store.undo` and no `undone`, and cancels pending commit retries for that date.
  - `chat/ChatViewModel.kt`: `fun discardForPartnerEnd()`, which clears `draft`, `selected`, `guide` and `sending` as `ended()` does.
  - The arrival gate's `create` failure path: a 409 / `date_not_active` closes the gate and calls the date re-read (no error UI). Check the current behaviour first; change it only if it shows an error.
  - `profile/ProfileAnalytics.kt`: `FIELD_REGISTRY_VERSION = "1.4.40"` and its KDoc history line (the `DateTimedOut` row came in Task 3).
  - `dev/DevJump.kt`, `dev/DevDateDemo.kt`, `dev/DevMenu.kt`, `app/src/debug/.../CheckInGalleryActivity.kt`: frames `200` and `200-chat` per R13. The demo gets `timesOut()`: `ended = EndedOutcome(reason = TimedOut, endedByYou = false, partnerScreen = DateTimedOut, meetAt = demo meetAt, photoUrl = …)` and `endSeen = false`. States rows: `200` · `Your date has ended`, and `200-chat` · `In the chat when the switch lands`.
- Modify (tests):
  - `date/partnerend/PartnerEndedRoutingTest.kt`
  - `profile/ProfileAnalyticsTest.kt` and every test pinning `1.4.39` (`PickStepsViewModelTest`, `ProfileDetailsViewModelTest`, … grep `"1.4.39"`)
  - `dev/DevDateDemoTest.kt`
  - `dev/DevStatesTest.kt`
  - the arrival gate tests
  - the chat view-model tests
  - `fit/EvidenceScreenshots.kt`: `SHOWUP-200_timed-out` at 375 × 667, 390 × 844, 430 × 932, plus 375 × 667 scrolled to the CTA

**Interfaces:**
- Consumes: Task 3's `DateTimedOut`, `OutcomeReason.TimedOut`, `meetClock`, `PartnerEndedScreen(…, meetTime, …)`.
- Produces:
  - `ArrivalGateViewModel.discardForPartnerEnd()` and `ChatViewModel.discardForPartnerEnd()`;
  - dev ids `200`, `200-chat`.

- [ ] **Step 1: failing tests.**
  - `endedRoute`: a `timed_out` outcome is `PartnerEnded(DateTimedOut)` with `endedByYou` true or false, with `partnerScreen` null or set, and is never `DateEnded` (the old Chat fallback).
  - Every existing reason × `endedByYou` row is unchanged.
  - `partnerEndedScreenId(DateTimedOut)` is `date_timed_out` / `Date - Timed out`.
  - The route carries `meetAt` and survives being restored (serialization round-trip).
  - Once per date; the referrer is the chat's from the chat and none on a cold start; `screen_viewed` fires per showing; Proceed fires nothing and calls `startReview`; system back does nothing.
  - **The switch closes every sheet:**
    - with the check-in done sheet open, the screen shows, the gate is `Idle`, and neither `commit` nor `undo` reached the store;
    - with a chat draft typed and the end found by the Date screen, the draft is empty afterwards;
    - the Date 05 / 06 / 07 sheets are gone (199's tests extended to D).
  - A late Date 06 end answered `date_not_active` re-reads and routes to D with no `EndErrorBox` (view-model + routing test). Same for a Date 07 cancel.
  - Dev: `200` and `200-chat` are live frames and open D through the real routing; `200-chat` discards the demo draft.
  - Registry stamp `1.4.40` everywhere.
- [ ] **Step 2:** run them and see them fail.
- [ ] **Step 3:** implement per R9–R13. `showPartnerEnded` serves Date 08 too: the new discards also close a done sheet / draft when a partner ends the date (intended, R11).
- [ ] **Step 4:** run the new and touched tests, plus `com.showup.date.*`, `com.showup.chat.*`, `com.showup.arrival.*` and `com.showup.dev.*` (one Gradle, narrow filters). Then run `-Pevidence` for the 200 shots and look at each PNG.
- [ ] **Step 5:** commit `feat(date): route a timed-out date to Date 09 for both people and close what is open, on Android (SHOWUP-200)`; push.

### Task 5: iOS — the twin of Tasks 3 and 4

**Files** (`mobile/welcome-screen/ios-app/ShowUpWelcome/`, tests in `ShowUpWelcomeTests/`):
- Modify:
  - `ActiveDate.swift` (`PartnerScreen.dateTimedOut`, the reason `.timedOut`, `EndedOutcome.meetAt: Date?`)
  - the outcome mapping (wherever `toOutcome`'s twin lives)
  - `PartnerEndedRules.swift` (`PartnerBadgeTone`, payload D, `partnerEndedHeadline` / `partnerEndedSub(screen:name:meetTime:)` / `partnerEndedScreenID`)
  - `PartnerEndedCopy.swift` (`// D ·` section)
  - `PartnerEndedScreen.swift` (`meetTime: String?`)
  - `PartnerEndedSamples.swift`, `PartnerEndedPreviews.swift`
  - `CancelRules.swift` (`timedOut` first)
  - `ShowUpWelcomeApp.swift` (the route's `meetAt`, the clock, the arrival-gate discard in `showPartnerEnded`)
  - `ArrivalGateModel.swift` (`discardForPartnerEnd()`)
  - the iOS `AppRoute` (`.partnerEnded(…, meetAt: String?)`)
  - `ProfileAnalytics.swift` (`dateTimedOut` id + name, stamp `1.4.40`)
  - `DevCheckIn.swift`, `DevDateDemo.swift`, `DevMenu.swift` (frames `200`, `200-chat`)
- Create: `MeetClock.swift`: `func meetClock(_ meetAt: Date, timeZone: TimeZone, is24h: Bool) -> String`, plus a small `deviceUses24h(locale:)` reading `DateFormatter.dateFormat(fromTemplate: "j", options: 0, locale:)`.
- Test:
  - `PartnerEndedRulesTests.swift`, `PartnerEndedScreenTests.swift`, `PartnerEndedRoutingTests.swift`, `PartnerEndedFitTests.swift` (six `sweep` groups for D)
  - `MeetClockTests.swift`, `DevDateDemoTests.swift`, `ProfileAnalyticsTests.swift`
  - `EvidenceShots.swift` (the 200 shots)
- Regenerate the project with `python gen_pbxproj.py`.

**Interfaces:** the Swift spellings of Tasks 3 / 4: `PartnerBadgeTone`, `PartnerScreen.dateTimedOut`, `meetClock(_:timeZone:is24h:)`, `PartnerEndedScreen(screen:name:photoURL:meetTime:onProceed:)`, `ArrivalGateModel.discardForPartnerEnd()`, `ProfileScreen.dateTimedOut`, dev ids `200` / `200-chat`.

- [ ] **Step 1: failing tests** mirroring every Task 3 / 4 test name. Add `deviceUses24h`: true for `de_DE`, false for `en_US`.
- [ ] **Step 2:** iOS cannot run here (no Mac); say so.
- [ ] **Step 3:** implement:
  - Strict concurrency: `@MainActor` views, no escape hatches. iOS 17 APIs only.
  - `showPartnerEnded` already nils the chat model; add the arrival discard and clear its `@SceneStorage` if it has one.
- [ ] **Step 4:** run `python -I audit/check-swift-concurrency.py`, `check-swift-structure.py`, `check-swift-arg-order.py`, `check-ios-availability.py` and `check-pbxproj.py`; all must pass. iOS compiles and tests in CI only.
- [ ] **Step 5:** commit `feat(date): Date 09 on iOS -- the fourth payload, the meet clock, routing for both people, closing what is open, dev frames and evidence (SHOWUP-200)`; push.

### Task 6: The checkers

**Files:**
- Create: `mobile/welcome-screen/audit/verify-timed-out.py` (shape of `verify-partner-end.py`).
- Modify: `mobile/welcome-screen/audit/verify-partner-end.py`. Its C group must end at the new `// D ·` marker instead of end-of-file, and it must accept the fourth id. `check-analytics-parity.py` changes only if it needs the new row.

- [ ] **Step 1:** write the checker. It must cover:
  - It reads `09-timed-out.md`'s `## Copy` section at run time and checks every backticked string in both apps' D copy. Bold and italic markers are stripped; `{name}` / `{time}` match `%s` / the Swift interpolation.
  - D's copy holds no `no-show`, `uncomfortable`, `she / he / her / his / him`, and no digit.
  - Exactly one `PartnerEndedScreen` per platform (no second layout).
  - `date_timed_out` / `Date - Timed out` appears in both `ProfileScreen`s, and the stamp is `1.4.40` in both.
  - Neither app fires `date_ended`, `date_cancelled` or `date_ended_in_chat` from the D code.
  - The server's `EndDateDto` does not accept `timed_out` / `auto_switch`.
  - `DATE_TIMEOUT_ENABLED` defaults off: `dateTimeoutEnabled` returns true only for `'true'`.
  - One mutation per group.
- [ ] **Step 2:** run it: all pass. Then run every `audit/verify-*.py` and `audit/check-*.py` by exit code: 0 failing.
- [ ] **Step 3:** commit `test(audit): verify-timed-out.py quotes Date 09's ticket on both apps and pins the switch off (SHOWUP-200)`; push.
