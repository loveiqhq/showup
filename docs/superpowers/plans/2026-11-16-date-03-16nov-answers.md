# Date 03 · 16 Nov answers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Philipp's 16 Nov re-issue of SHOWUP-195 on top of the merged Date 03 build: frame C2 (check-in not saved), a committed check-in opening the chat for both people, the date location gate's two tracking events, and registry 1.4.38 / taxonomy 1.4.31.

**Architecture:** The server makes the first committed check-in stamp `dates.coordinate_unlocked_at` (the column the meet − 1:30 job already stamps), so `coordinateOpen` and the chat send rule read "time or stamp". Both apps already trust the server's `coordinateOpen`; the checker's own phone flips at Close through the existing session override. C2 splits the guard's one error flag into two kinds. The gate events reuse Search 04's emitters with a `screen_id`.

**Tech Stack:** NestJS + TypeORM + Postgres (Jest unit + e2e); Kotlin / Jetpack Compose (Robolectric); Swift / SwiftUI (XCTest on CI macos-15 only, no Mac here).

**Spec:** the SHOWUP-195 description as re-issued 16 Nov 2026, copied verbatim to `design_handoff_showup/date/tickets/03-arrival-checkin.md` (every change marked **(16 Nov)**), with Date 01 / 02 in the same folder; the build's rulings in `docs/superpowers/specs/2026-11-10-date-03-arrival-checkin-design.md` (its 16 Nov addendum is written in Task 5). The kit: `design_handoff_showup/date/screen-checkin-reference.jsx` (`locError="save"`).

## Global Constraints

- Branch `feat/date-03-16nov-answers` (from development c24ace7). Never commit `.superpowers/` or `.semgrep/`. Push after every commit (`git push`; the branch tracks `origin/feat/date-03-16nov-answers`).
- Both apps change in the same commit for any screen or rule change (mobile `CLAUDE.md`).
- C2 copy, exactly: `Couldn't check you in. Check your connection and try again.` with `Not yet` · `Try again`. C keeps `We couldn't find your location. Check your signal, then try again.`
- Never log, track or push coordinates, accuracy, distance or place.
- Touch floor 44 on both platforms; no new raw colours, sizes or durations where a token holds them.
- `screen_id` value for the date gate: `date_location_gate`; §11 label `Date - Location gate`.
- `coordinate_unlocked.cause` values: `time` | `arrival_check_in`.
- Registry stamp `1.4.38`, taxonomy `1.4.31`.
- Android full Gradle runs get reaped for memory here: run narrow `--tests` filters with `--no-daemon`-free single invocations and `./gradlew --stop` afterwards; CI runs the full suite. iOS is verified only by CI (say so; never claim it locally).
- Subagents never dispatch subagents.

## Review Focus

1. A commit AFTER meet − 1:30 (chat already open by time) must not stamp, must not fire `coordinate_unlocked`, and the job must still fire its own `cause: time` event once.
2. The second person's commit, an undo, or a store with no commit must open nothing and fire nothing.
3. The match's phone: `GET /me/date` must return `coordinateOpen: true` before the push is sent (stamp first, then push), so the push-driven re-read lands on B.
4. C2 → `Try again` with no fix shows C; C → `Try again` with a failed store shows C2; either success lands on D — the error kind always follows the LAST attempt.
5. Search's `location_gate_shown` must stay without `screen_id` (absent = Search); only the date context adds it.

---

### Task 1: Tracking registry 1.4.38 / taxonomy 1.4.31 (the Date 03 slice)

**Files:**
- Modify: `design_handoff_showup/tracking/enums.json` (replace with the zip's: it is a strict superset — sets 42–46 NEW, set 11 + row `date_location_gate`, set 41 + `wrapup`, changelogs 1.4.35–1.4.38)
- Modify: `design_handoff_showup/tracking/events.json` (repo file stays the base; `implemented` flags stay the repo's)
- Modify: `src/modules/analytics/registry/enums.json` (the server's cleaned copy, its own format)
- Modify: `src/modules/analytics/registry/sensitivity.ts:17`, `mobile/.../profile/ProfileAnalytics.kt` (`FIELD_REGISTRY_VERSION` + version history comment), `ios-app/ShowUpWelcome/ProfileAnalytics.swift` (`fieldRegistryVersion` + comment)
- Test: the files pinning `1.4.34` — Android `PickStepsViewModelTest`, `ProfileAnalyticsTest`, `ProfileDetailsViewModelTest`, `PromptsTrackingTest`; iOS `CheckInModelTests`, `DetailsModelTests`, `PickModelTests`, `ProfileAnalyticsTests`, `PromptsTrackingTests` (grep `1.4.34`)

Source files of the 16 Nov zip: `C:/Users/krnji/AppData/Local/Temp/claude/C--Users-krnji-showup/33f1e072-9d7d-4446-a930-54c75a306b73/scratchpad/195v2/zip-new/design_handoff_showup/tracking/` (read-only).

**Interfaces:**
- Produces: registry `1.4.38` everywhere `1.4.34` was the stamp; §11 row `date_location_gate` / `Date - Location gate` in both enums files; events.json `coordinate_unlocked` payload gains `cause` (`"time"|"arrival_check_in"`), `location_gate_shown` payload gains optional `screen_id`, `permission_settings_opened.screen_id` documents `date_location_gate` — each copied from the zip's event entry, with the repo's `implemented` / `code_anchor` values kept.

- [ ] **Step 1:** Copy the zip's `enums.json` over the handoff one; confirm with a script that every set and row of the old file is still present unchanged (superset check) — print the result.
- [ ] **Step 2:** In `events.json`: replace the three events' `payload` / `trigger` / description fields with the zip's, keeping each event's repo `implemented`, `code_anchor`, `backend_status`; set `taxonomy_version` `1.4.31`; add `changed_in_1_4_31` (the zip's four lines) and a `merge_note_1_4_31`: `1.4.28–1.4.30 (Date 05 / 06 / 07) are not merged here: their tickets carry them.`
- [ ] **Step 3:** Server copy `src/modules/analytics/registry/enums.json`: `registry_version` `1.4.38`, `taxonomy_version` `1.4.31`, and the same additions in that file's own shape (study how set 11 rows and set 41 are written there; mirror them for the new row, `wrapup`, and sets 42–46).
- [ ] **Step 4:** Stamps to `1.4.38` (server const, Kotlin, Swift) with one history line each: `1.4.38 (16 November 2026): §11 date_location_gate (Date 03); 1.4.35–1.4.37 add Date 05 / 06 / 07 value sets.` Update every test pin.
- [ ] **Step 5:** Run: `npx jest src/modules/analytics` → PASS; `python -I mobile/welcome-screen/audit/verify-profile.py` and `check-analytics-parity.py` → all pass; Android `./gradlew :app:testDebugUnitTest --tests '*ProfileAnalyticsTest' --tests '*PickStepsViewModelTest' --tests '*ProfileDetailsViewModelTest' --tests '*PromptsTrackingTest'` → PASS, then `./gradlew --stop`.
- [ ] **Step 6:** Commit `chore(tracking): registry 1.4.38 / taxonomy 1.4.31, the Date 03 slice (SHOWUP-195)`; push.

### Task 2: Server — the first committed check-in opens the chat

**Files:**
- Modify: `src/modules/dates/util/pre-date-chat.ts` (`isChatOpen`, `chatWindowError`)
- Modify: `src/modules/dates/dates.service.ts` (send rule ~L303; `unlockDueCoordinates` cause)
- Modify: `src/modules/dates/arrival-check-ins.service.ts` (`commit`)
- Modify: `src/modules/analytics/events/date-events.ts` (`coordinateUnlockedEvent`)
- Modify: `src/modules/date-screen/util/active-date.ts`, `src/modules/date-screen/date-screen.service.ts`, `src/modules/date-screen/dto/active-date.dto.ts` (description only)
- Modify: `openapi.json` and `mobile/welcome-screen/ios-app/ShowUpAPI/Sources/ShowUpAPI/openapi.json` (regenerated: description text only)
- Test: the specs next to each file; `test/arrival.e2e-spec.ts`

**Interfaces:**
- Consumes: Task 1's `cause` values.
- Produces: `isChatOpen(scheduledAt: Date, now: Date = new Date(), unlockedAt: Date | null = null): boolean` — true when `unlockedAt != null` or the time rule; `chatWindowError(scheduledAt, now, unlockedAt)`; `coordinateUnlockedEvent(minutesBeforeStart: number, cause: 'time' | 'arrival_check_in')` with properties `{ minutes_before_start, cause }` only; `ActiveDateSource.coordinateUnlockedAt: Date | null`; payload `coordinateOpen = now >= unlockAt || coordinateUnlockedAt != null`.

- [ ] **Step 1: Failing tests.**
  - `pre-date-chat.spec`: open before meet − 1:30 when `unlockedAt` is set; closed without it.
  - `active-date.spec`: `coordinateOpen` true at meet − 3 h with `coordinateUnlockedAt` set; false with null.
  - `date-events.spec`: `coordinate_unlocked` carries exactly `minutes_before_start` and `cause`.
  - `dates.service` job spec: the job's event has `cause: 'time'`.
  - `arrival-check-ins.service.spec`:
    - commit at meet − 3 h stamps `coordinate_unlocked_at` and fires `coordinate_unlocked` `{ cause: 'arrival_check_in', minutes_before_start: 180 }` for BOTH users, BEFORE the push is sent (assert call order);
    - commit at meet − 1 h stamps nothing and fires nothing (the job's case);
    - the partner's later commit fires nothing;
    - an already-committed (no-op) commit fires nothing.
  - e2e: A stores + commits at meet − 3 h; `GET /me/date` gives `coordinateOpen: true` for A and B; B's `POST /dates/:id/chat` succeeds; a separate date where A stores then undoes keeps `coordinateOpen: false` and B's send is still 400.
- [ ] **Step 2:** Run them; expect FAIL.
- [ ] **Step 3: Implement.**
  - `commit`, only when its UPDATE set `committed_at`, runs `UPDATE "dates" SET "coordinate_unlocked_at" = $2 WHERE "id" = $1 AND "coordinate_unlocked_at" IS NULL AND "scheduled_at" > $3 RETURNING "user_a_id", "user_b_id", "scheduled_at"`, with `$3 = now + CHAT_OPENS_MINUTES_BEFORE min`, so a chat already open by time is left to the job.
  - For a returned row: `trackServerEvent` for both users. Do it BEFORE `notifyPartner`. A failure to stamp must not fail the commit: log the error name only, as `notifyPartner` does.
  - Inject the analytics service the way `DatesService` gets it.
  - The job passes `cause: 'time'`.
  - The send rule passes `date.coordinateUnlockedAt`.
  - `date-screen.service` passes `coordinateUnlockedAt`.
  - DTO description: `True from unlockAt on, or once either person's arrival check-in is committed (Date 03, 16 Nov 2026).`
  - Regenerate both openapi copies with the repo's script (`scripts/emit-openapi.ts`, see `package.json`).
- [ ] **Step 4:** `npm run test:cov` (holds the util coverage gate) and `npm run test:e2e -- arrival` → PASS; `npm run lint` clean.
- [ ] **Step 5:** Commit `feat(dates): the first committed check-in opens the chat for both (SHOWUP-195, 16 Nov)`; push.

### Task 3: Clients — C2, check-in not saved (Android + iOS, one commit)

**Files (Android `app/src/main/java/com/showup/arrival/` unless noted):**
- Modify: `ArrivalGateViewModel.kt` (`GatePhase.Confirm`), `ArrivalCopy.kt`, `ArrivalSheets.kt`, `ArrivalSamples.kt`, `ArrivalPreviews.kt`, `../dev/CheckInGalleryActivity.kt` + the dev States list (`DEV_STATES` 195 entries)
- Test: `ArrivalGateViewModelTest`, `ArrivalFitTest`, `EvidenceScreenshots` (SHOWUP-195 block)
- iOS twins in `ios-app/ShowUpWelcome/`: `ArrivalGateModel.swift`, `ArrivalCopy.swift`, `ArrivalSheets.swift`, the samples / States page twin; tests `ArrivalGateModelTests`, `ArrivalFitTests`, the evidence test
- Modify: `mobile/welcome-screen/audit/verify-arrival.py`

**Interfaces:**
- Produces:
  - `enum class ConfirmError { NoFix, NotSaved }` (Swift `enum ConfirmError { case noFix, notSaved }`).
  - `GatePhase.Confirm(entry, error: ConfirmError?, busy, note, attempt)` replaces `locError: Boolean`. `null` = B, `NoFix` = C, `NotSaved` = C2.
  - `ArrivalCopy.SAVE_ERROR` = the C2 string.
  - `ArrivalSamples.C2`.
- Rule: the error kind is the LAST attempt's. No fix (null, stale, timeout) → `NoFix`; `CheckInResult.Failed` (network, 5xx, any other non-409) → `NotSaved`; stored → D. `Not yet` closes from either; no retry cap; every `Try again` reads the location again. Analytics unchanged: C2 fires nothing new; its `Try again` fires `arrival_confirm_submitted` again.

- [ ] **Step 1: Failing tests (Android first, then the Swift twins).**
  - A failed store shows `NotSaved` with `Try again`.
  - C2 → Try again with no fix shows `NoFix`.
  - C → Try again with a failed store shows `NotSaved`.
  - C2 → Try again stored → `Done`.
  - C2 → Not yet → Idle, nothing stored.
  - The sheet draws `SAVE_ERROR` for `NotSaved` and the C string for `NoFix`.
- [ ] **Step 2: Implement both platforms.** The C2 sheet is C's sheet and error box with the other string; the box is still announced as an alert. Add the C2 frame to:
  - the samples, and previews at 320 × 686 and 430 × 932;
  - the States page: `C2 · Check-in not saved`, after C;
  - the fit sweep: every size and font step, the same rules as C;
  - the evidence harness: C2 at 375 × 667, 390 × 844 and 430 × 932.
- [ ] **Step 3:** `verify-arrival.py` reads the `**C2 · Check-in not saved:**` line of the ticket's Copy and requires its string on both platforms, the way it checks C. Drop any check that asserted a failed store shows C.
- [ ] **Step 4: Run.**
  - Android: `./gradlew :app:testDebugUnitTest --tests '*Arrival*'`, then `./gradlew --stop`.
  - `python -I audit/verify-arrival.py` → all pass.
  - iOS: NOT RUN locally; CI.
- [ ] **Step 5:** Commit `feat(arrival): C2, check-in not saved, on both apps (SHOWUP-195, 16 Nov)`; push.

### Task 4: Clients — a committed check-in opens the chat; the date gate's tracking (Android + iOS, one commit)

**Files:**
- Modify (Android): `MainActivity.kt` (the session override near `arrivalUi.committed && heldDate.date?.checkedIn == false`), `date/DateCards.kt` + `date/DateScreen.kt` (the arrival card's CTA), `arrival/ArrivalPreviews.kt` (drop `Date 03 · H · before the chat unlocks`), `search/LocationGateViewModel.kt`, `profile/ProfileAnalytics.kt` (`ProfileScreen.DateLocationGate`, `locationGateShown(reason, screen)`)
- Modify (iOS twins): the root view's session override, `DateCards.swift` / `DateScreen.swift`, `LocationGateModel.swift`, `ProfileAnalytics.swift`, previews
- Test: `DateRulesTest` / `DateViewModelTest`, `LocationGateViewModelTest`, `ProfileAnalyticsTest` and the Swift twins

**Interfaces:**
- Consumes: Task 1's §11 row; Task 2's payload rule (server `coordinateOpen` covers the partner's check-in).
- Produces:
  - `ProfileScreen.DateLocationGate("date_location_gate", "Date - Location gate")` (Swift `case dateLocationGate = "date_location_gate"`, same label).
  - `ProfileAnalytics.locationGateShown(reason, screen: ProfileScreen? = null)`. It puts `screen_id` only when `screen` is given; Search passes none.

- [ ] **Step 1: Failing tests.**
  - The override: committed → the shown date has `checkedIn = true` AND `coordinateOpen = true`. Extract it into a tested pure function if it isn't one; e.g. `arrival/ArrivalRouting.kt` `committedView(date: ActiveDate, committed: Boolean): ActiveDate`, plus its Swift twin.
  - `dateState` is Available at meet − 3 h when `coordinateOpen` is true.
  - The arrival card's CTA calls `onOpenChat` in every state; there is no locked-only clear.
  - The date gate shown → `location_gate_shown { reason, screen_id: "date_location_gate" }`, by the same once-per-showing rule as Search.
  - Open Settings on the date gate → `permission_settings_opened { type: "location", screen_id: "date_location_gate" }`.
  - Search's `location_gate_shown` has no `screen_id` key.
  - The date gate's other permission events stay silent (unchanged).
- [ ] **Step 2: Implement both platforms.**
  - The override sets both fields.
  - The CTA always opens the chat through the existing `openChatFromArrivalCard` route, which already shows the tutorial first if it hasn't been seen.
  - Remove the now-unused locked branch, and its parameter, from the call sites.
  - `dateMounted()` stops silencing these two events. It reports them with the date screen id and keeps every other gate event silent on the date.
- [ ] **Step 3: Run.**
  - Android: `./gradlew :app:testDebugUnitTest --tests '*DateRules*' --tests '*DateViewModel*' --tests '*LocationGate*' --tests '*ProfileAnalyticsTest' --tests '*Arrival*' --tests '*ChatRouting*'`, then `./gradlew --stop`.
  - All audits: `for f in audit/verify-*.py audit/check-*.py; do python -I "$f"; done` → all pass.
  - iOS: CI.
- [ ] **Step 4:** Commit `feat(date): a committed check-in opens the chat; the date gate reports its two events (SHOWUP-195, 16 Nov)`; push.

### Task 5: Addendum, evidence, pull request (controller)

- [ ] Add a `## 9. 16 Nov 2026 addendum` to the design doc:
  - R2 superseded (Open chat always opens);
  - R5 superseded by C2;
  - R16, the commit opens the chat (the stamp, its conditions, the event, the order before the push);
  - R17, the date gate's two events (the others stay silent);
  - registry 1.4.38;
  - owed: nothing new from Philipp.
- [ ] Final whole-branch review (Sonnet), one fix round, then a PR to `development`, with the CI evidence artifacts listed.
