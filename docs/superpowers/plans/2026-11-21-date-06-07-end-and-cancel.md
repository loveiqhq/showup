# Date 06 + Date 07 (end, report, cancel) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build SHOWUP-197 (End date and Report a problem) and SHOWUP-198 (Cancel this date) on the server, Android and iOS: every way one person ends or cancels a date, stored and tracked, with exact copy and pixel-faithful sheets.

**Architecture:** One server state machine for every end: `ended` (Date 06) and `cancelled` (Date 07). Each runs in a serialised transaction and reports through one `ended` payload. Both apps add one model per sheet family (`DateEndModel`, `CancelModel`), shared step components, and host wiring. One server switch (`DATE_ENDS_ENABLED`, surfaced as `endsEnabled`) hides every end control where flow 10 does not exist yet (spec R1).

**Tech Stack:** NestJS + TypeORM + Postgres, Jest (unit + e2e); Kotlin/Compose + Robolectric; SwiftUI + XCTest (CI compiles iOS on macos-15); OpenAPI-generated clients.

**Spec:** `docs/superpowers/specs/2026-11-21-date-06-07-end-and-cancel-design.md` (rulings R1–R14). The tickets are
`design_handoff_showup/date/tickets/06-end-report.md` and `07-cancel.md` (identical to Jira 197/198). Numbers come
from `date/screen-wrap-report-reference.jsx` and `date/screen-cancel-reference.jsx`.

## Global Constraints

- Copy: exactly the tickets' *Copy — final strings*, in `DateEndCopy` / `CancelCopy` on both platforms. Keep `Show Up` and `Show-up Rate` capitalised as written; UI says `Check-in`.
- Numbers from the reference files; the PNG wins on nothing. Touch floor 44 on both platforms (`ComponentSizes.minTapTarget`), never 48.
- No absolute Y. Sheets are `ModalShell` with height from content and scroll inside. The cancel sheet scrolls on every device.
- Both platforms change in the same task pair; Android and iOS names match.
- Never log or track: coordinates, distance, place, free text, the match's ID, phone, email, token, message body.
- Tracking names, payloads and values come from `design_handoff_showup/tracking/{events,enums}.json` (registry 1.4.39). Retired names (`wrapup_prompted`, `wrapup_confirmed`, `grace_period_*`, `no_penalty_exit_granted`, `cancel_link_tapped`, `partner_cancellation_received`, `rematch_started`) are never emitted.
- `date_ended`, `problem_stay_flagged` and `date_cancelled` are server events: fired once, on success only.
- Never commit `.semgrep/` or `.superpowers/`. Push every commit. iOS strict concurrency: no `@unchecked Sendable`, `nonisolated(unsafe)` or `@preconcurrency`.
- A new required OpenAPI field breaks both apps' JSON fixtures: update them in the same task (memory: contract-change-breaks-fixtures).

## Review Focus

1. **Gated route continuation.** Not checked in → My date isn't showing up → gate → Yes → Close must land on E/F, not on the Date screen. Not yet → back to the menu. Undo → everything closes with the Check-in bar back. Test each in the model (Task 4/5).
2. **The clock crossing the meet time while E is open.** The end button must enable in place (E → F) on the 1 s tick. Test with an injected clock (Task 4/5).
3. **A failed end or cancel retried.** The error box shows above the CTA, the CTA keeps its label, and a retry that succeeds ends exactly once. The server event fires once (Tasks 1, 2, 4–7).
4. **Two people ending or cancelling at once.** The second gets 409 `date_not_active`. Its client reads the date again and routes on `ended`: the losing canceller lands on H. Server e2e plus model test (Tasks 2, 6, 7).
5. **The pair block both ways, including Likes.** After a cancel, neither person appears to the other in search, Premium grid or Likes received (Task 2 e2e).

---

### Task 1: Server — Date 06 end, discomfort flag, ended payload, switch, registry

**Files:**
- Create: `src/database/migrations/1717000037000-DateEnds.ts`, `src/modules/dates/util/date-end.ts` (+ spec), `src/modules/dates/date-end.service.ts` (+ spec), `src/modules/dates/dto/end-date.dto.ts`, `src/modules/dates/entities/date-discomfort-flag.entity.ts`, `test/date-end.e2e-spec.ts`
- Modify: `src/modules/dates/entities/date.entity.ts`, `src/modules/dates/util/date-lifecycle.ts`, `src/modules/dates/dates.controller.ts`, `src/modules/dates/dates.module.ts`, `src/modules/date-screen/util/active-date.ts`, `src/modules/date-screen/dto/active-date.dto.ts`, `src/modules/date-screen/date-screen.service.ts`, `src/modules/analytics/events/date-events.ts`, `src/modules/analytics/registry/enums.json` (→ 1.4.39 from `design_handoff_showup/tracking/enums.json`), `src/modules/date-screen/date-dev.*` (dev: the match ends the date)

**Interfaces:**
- Produces:
  - `POST /dates/:id/end` body `{ reason: 'met'|'partner_no_show'|'uncomfortable', entry: 'end_date_bar'|'checkin_bar_link'|'report' }`. It answers 200 with `EndedOutcomeDto`, or 409 with `code` ∈ `date_not_active | before_meet | not_checked_in`, or 404 when the switch is off.
  - `POST /dates/:id/discomfort` → 204.
  - `ActiveDateDto` gains `endsEnabled: boolean` (required) and `cancelPenaltyPoints: number` (required; Task 2 fills it, Task 1 sets it from a config stub at 10).
  - `EndedInChatDto` is renamed in OpenAPI to `EndedOutcomeDto`, keeping the field names. It gains `reason` (required, `chat|cancelled|met|partner_no_show|uncomfortable`) and `partnerScreen` (optional, `date_closed_positive|you_no_showed|date_ended_discomfort`, set only for the match of a Date 06 end).
  - `function dateEndsEnabled(env: { flag?: string; nodeEnv?: string }): boolean` in `util/date-end.ts`.
  - `function partnerScreenFor(reason): PartnerScreen` (end_reason alone).
  - `function endRefusal(input: { status, now, meetAt, reason, enderCheckedIn }): RefusalCode | null`.

- [ ] Step 1: Unit tests for `dateEndsEnabled` (`'true'`/`'false'` win; unset is off only in production), `partnerScreenFor` (met → date_closed_positive, partner_no_show → you_no_showed whatever the match's check-in, uncomfortable → date_ended_discomfort), `endRefusal` (not confirmed → date_not_active; partner_no_show before meetAt → before_meet; partner_no_show/uncomfortable without check-in → not_checked_in; met never needs a check-in), and `minutesVsStart` (negative before the agreed time).
- [ ] Step 2: Run them (`npx jest src/modules/dates/util/date-end.spec.ts`); expect FAIL (module missing).
- [ ] Step 3: Implement `util/date-end.ts`, the migration (spec §4), the entity columns and `DateStatus.Ended`, and the flag entity.
- [ ] Step 4: Service tests (`date-end.service.spec.ts`), with a real DB as the existing date services test:
  - end stores reason, entry, ender, `ended_at`, both check-in states, and closes the match;
  - a second end → 409 date_not_active;
  - `date_ended` fires exactly once, with the six fields;
  - a failed end fires nothing;
  - discomfort stores one row per call and fires `problem_stay_flagged`;
  - switch off → 404.
- [ ] Step 5: Implement `DateEndService` (row lock + conditional update, as change-suggestions' agree does) and the controller routes. Committed check-in is `arrival_check_ins.committed_at IS NOT NULL` for that user and date.
- [ ] Step 6: `GET /me/date`:
  - `endedOutcome` reports every ended reason (chat, met, partner_no_show, uncomfortable) until `a/b_end_seen_at`;
  - `partnerScreen` is set only for the non-ender of a Date 06 end;
  - `endsEnabled` and `cancelPenaltyPoints` on the active date.
  - Tests in the date-screen specs.
- [ ] Step 7: Registry: copy `design_handoff_showup/tracking/enums.json` values into the server's `enums.json`, keeping its shape, and set `registry_version` 1.4.39. Run the analytics registry tests.
- [ ] Step 8: e2e `test/date-end.e2e-spec.ts`: each reason end to end; before_meet; not_checked_in; double end; discomfort; the payload for both people before and after `ended/seen`.
- [ ] Step 9: `npm run lint && npm run build && npm test && npm run test:e2e -- date-end` green.
- [ ] Step 10: Regenerate the OpenAPI document (the repo's script) and commit with the server change.

### Task 2: Server — Date 07 cancel, penalty config, pair block, push

**Files:**
- Create: `src/modules/dates/util/show-up-rate.config.ts`, `src/modules/dates/util/date-cancel.ts` (+ spec), `src/modules/dates/entities/date-pair-block.entity.ts`, `test/date-cancel.e2e-spec.ts`
- Modify: `src/modules/dates/dates.service.ts` (`cancel`), `src/modules/dates/dto/cancel-date.dto.ts`, `src/modules/safety/safety.service.ts` (`blockedUserIdsFor` unions pair blocks), `src/modules/analytics/events/date-events.ts` (`date_cancelled` payload), `src/modules/notifications/util/preferences.ts` + `messages/catalog.ts` (kind `date_cancelled`, placeholder copy), `src/modules/date-screen/*` (`cancelPenaltyPoints`, ended `cancelled`), `src/modules/date-screen/date-dev.service.ts` (dev cancel through the same path; dev: the match cancels)

**Interfaces:**
- Consumes: Task 1's migration (the columns `hours_notice`, `penalty_points`, `cancel_change_requested`, `partner_checked_in`, `ender_checked_in`), `EndedOutcomeDto.reason = 'cancelled'`, and the refusal codes.
- Produces:
  - `POST /dates/:id/cancel` body `{}`: 200 with `EndedOutcomeDto`; 409 `date_not_active | checked_in`; 404 when the switch is off.
  - `SHOW_UP_RATE = { noShowPenalty: 10, attendedBonus: 10 }`.
  - `function hoursNotice(now: Date, meetAt: Date): number` (two decimals, negative after).
  - `function cancelRefusal({ status, cancellerCheckedIn }): RefusalCode | null`.

- [ ] Step 1: Unit tests: `hoursNotice` (3 days ahead, 10 minutes ahead, after the meet time negative); `cancelRefusal`; `date_cancelled` builder has exactly `date_id, hours_notice, penalty_points, change_requested`; `blockedUserIdsFor` includes a pair block from either side.
- [ ] Step 2: Run; expect FAIL.
- [ ] Step 3: Implement the config, the pure functions, the pair-block entity (unique on the ordered pair), and the cancel rewrite (one transaction with a row lock):
  - status `cancelled`, `cancelled_by_id`, `cancelled_at`, `hours_notice`, `penalty_points`;
  - both check-in states, and `cancel_change_requested` from the canceller's suggestions with `entry = 'cancel_sheet'`;
  - pair block, match closed, push to the match after commit.
- [ ] Step 4: e2e `test/date-cancel.e2e-spec.ts`:
  - success stores everything and fires `date_cancelled` once;
  - a checked-in canceller → 409 `checked_in`;
  - a second cancel → 409 `date_not_active`, with no second event;
  - the match's `GET /me/date` reports `ended {reason: cancelled, endedByYou: false}` until seen;
  - after the cancel neither user appears in the other's search results, Premium grid or Likes received;
  - a cancel after a `cancel_sheet` suggestion stores `change_requested = true`.
- [ ] Step 5: `npm run lint && npm run build && npm test && npm run test:e2e -- date-cancel` green; regenerate OpenAPI; commit.

### Task 3: Client contract — both apps' API layer and fixtures

**Files:**
- Modify (Android): `date/DateRepository.kt` (+ `ActiveDate.endsEnabled`, `cancelPenaltyPoints`), `date/ActiveDate.kt` (`EndedOutcome` with `reason`, `partnerScreen`; rename `EndedInChat` → `EndedOutcome` keeping a typealias only if needed), new `date/DateEndRepository.kt` (`DateEndStore { end(dateId, reason, entry): EndResultX; flagDiscomfort(dateId) }`, `CancelStore { cancel(dateId): CancelResult }`), `dev/DevDateDemo.kt` (implements both stores), JSON test fixtures.
- Modify (iOS): the twins (`DateRepository.swift`, `ActiveDate.swift`, new `DateEndRepository.swift`, `DevDateDemo.swift`), JSON fixtures.

**Interfaces:**
- Produces:
  - `enum class EndReason(val wire) { Met, PartnerNoShow, Uncomfortable }`;
  - `enum class EndEntry(val wire) { EndDateBar, CheckinBarLink, Report }`;
  - `sealed interface EndDateResult { Ended(outcome) ; Refused(code) ; Failed(why) }` — the name avoids Date 02's `EndResult`;
  - `sealed interface CancelResult { Cancelled(outcome) ; Refused(code) ; Failed(why) }`;
  - `enum class OutcomeReason { Chat, Cancelled, Met, PartnerNoShow, Uncomfortable }`;
  - the same shapes in Swift.
- [ ] Step 1: Repository tests (Android `DateEndRepositoryTest`, iOS `DateEndRepositoryTests`) map 200/409 codes/404/network to the results; the ended payload maps every reason; a payload without `reason` (an older server) reads as `Chat`.
- [ ] Step 2: Implement; update every JSON fixture with `endsEnabled`/`cancelPenaltyPoints`. Run the full Android unit suite for the touched packages and `audit/check-*.py`.
- [ ] Step 3: Commit.

### Task 4: Android — Date 06 UI, model, gate continuation, tracking

**Files:**
- Create: `date/end/DateEndCopy.kt`, `date/end/EndDateBar.kt`, `date/end/DateEndSheets.kt` (`DateWrapSheet`, `ReportProblemSheet`, `WrapConfirm`, `WrapBack`, `NoShowGuide`, `NoShowConfirm`, `UncomfortableStep`, `EndErrorBox`), `date/end/DateEndViewModel.kt`, `date/end/DateEndRules.kt` (`pastMeet`, `minutesVsStart`, `endStepAfterGate`), `date/end/DateEndAnalytics.kt`; tests `date/end/DateEndViewModelTest.kt`, `date/end/DateEndSheetsTest.kt`, `date/end/DateEndRulesTest.kt`
- Modify: `arrival/ArrivalRules.kt` (`ArrivalEntry.Wrapup`), `arrival/CheckInBar.kt` (link shown when `endsEnabled`, `END_DATE_LINK_SHOWN` removed), `MainActivity.kt` (bar into `endDateBar`; `onEnd`; `onReport`; Report card hidden when `!endsEnabled`; gate continuation; `landAfterEnd`), `date/DateScreen.kt` / `DateCards.kt` (Report card visibility parameter only)

**Interfaces:**
- Consumes: Task 3's stores and results; `ArrivalGateViewModel.open(entry, note)`, its committed/undone/Idle signals; `ModalShell`, `SheetErrorBox`.
- Produces: `DateEndViewModel.openWrap(entry)`, `openReport()`, `pick(option)`, `back()`, `close()`, `end()`, `stay()`, `gateCommitted()`, `gateNotYet()`, `gateUndone()`; `fun landAfterEnd(outcome: EndedOutcome)` in the host.

- [ ] Step 1: Rules tests:
  - `pastMeet` at meet − 1 s / at meet;
  - `minutesVsStart` sign;
  - `endStepAfterGate` (no-show → Guide, uncomfortable → Uncomfortable);
  - the end button label/enabled for both sides of meetAt;
  - `Message {name}` hidden when `!chatOpen`.
- [ ] Step 2: Model tests:
  - every frame reached from its entry;
  - We met with no gate;
  - the gated routes from B and from H (Yes → Close → step; Not yet → menu; Undo → closed);
  - end in flight disables both buttons;
  - failure → error with the same step, and retry → success once;
  - 409 `date_not_active` → re-read;
  - Stay for now calls `flagDiscomfort` and closes;
  - every event and payload, including the `entry` values and *on the tap* order;
  - nothing fires on Cancel/scrim/Back/Message/the disabled button;
  - saved-state restore re-validates (R14).
- [ ] Step 3: Implement rules, model and analytics until green.
- [ ] Step 4: Screen tests (`DateEndSheetsTest`):
  - each frame's exact copy and order;
  - the bold/italic runs;
  - E disabled with the clock icon and `You can end the date from {time}`;
  - F enabled;
  - J's box above the CTA, with buttons unchanged;
  - two options in H;
  - every target ≥ 44 dp.
- [ ] Step 5: Implement the composables to the reference numbers, with these shared components:
  - `ModalShell`;
  - the error box via `EndErrorBox` → `SheetErrorBox`;
  - the existing Lora/Manrope roles, icons from `BrandIcon` (add `hourglass`, `sparkles`, `hand`, `shield-check`, `home`, `star`, `calendar` if missing, drawn from the kit's Lucide paths).
- [ ] Step 6: Host wiring in `MainActivity` (spec §5).
- [ ] Step 7: Add the frames to the fit sweep (`fit/DateEndFitTest.kt`, 17 sizes) and fix every finding.
- [ ] Step 8: `./gradlew :app:testDebugUnitTest --tests 'com.showup.date.end.*' --tests 'com.showup.arrival.*' --tests 'com.showup.fit.DateEndFitTest'` green; `assembleDebug`; commit.

### Task 5: iOS — Date 06 twin

**Files:** the Swift twins of Task 4 (`DateEndCopy.swift`, `EndDateBar.swift`, `DateEndSheets.swift`, `DateEndModel.swift`, `DateEndRules.swift`, `DateEndAnalytics.swift`; tests `DateEndModelTests.swift`, `DateEndSheetsTests.swift`, `DateEndRulesTests.swift`, `DateEndFitTests.swift`), `ArrivalRules.swift`, `CheckInBar.swift`, `ShowUpWelcomeApp.swift`, `DateScreen.swift`; run `python ios-app/gen_pbxproj.py`.

**Interfaces:** the same names and behaviour as Task 4; `DateEndModel` is `@MainActor @Observable final class`.

- [ ] Steps mirror Task 4. Verification is CI (`iOS build, unit tests & screen fit`), plus `audit/check-swift-*.py`, `check-ios-availability.py` and `check-pbxproj.py` locally.

### Task 6: Android — Date 07 UI, model, H, tracking

**Files:**
- Create: `date/cancel/CancelCopy.kt`, `date/cancel/CancelDateConfirmSheet.kt`, `date/cancel/CancelViewModel.kt`, `date/cancel/CancelRules.kt` (`changeClosed`, `showMessage`, `sentLabel`, `minutesBeforeStart`), `date/cancel/CancelAnalytics.kt`; tests `CancelViewModelTest.kt`, `CancelDateConfirmSheetTest.kt`, `CancelRulesTest.kt`, `fit/CancelFitTest.kt`
- Modify: `date/DateCards.kt` (Cancel link wired, visible only while active, not checked in and `endsEnabled`), `chat/DateEndedScreen.kt` (+ the `cancelled` default variant, H), `change/ChangeRepository.kt` (`ChangeEntry.CancelSheet("cancel_sheet")`), `MainActivity.kt` (open the sheet, pickers over it with `cancel_sheet`, D on a send, Message instead → chat, success → `landAfterEnd`, the match's `ended.reason == cancelled` → H, `screen_viewed date_partner_cancelled`)

**Interfaces:**
- Consumes: Task 3's `CancelStore`; Date 05's `changeKindAvailable`, `ChangePickerViewModel`, `AppRoute.ChangePicker`; `EndErrorBox(text)` from Task 4.
- Produces: `CancelViewModel.open()`, `keep()`, `dismiss()`, `messageInstead()`, `pickerSent(value)`, `cancel()`.

- [ ] Step 1: Rules tests:
  - `changeClosed` true from cutoff and after either check-in, using the same function as Date 05;
  - `showMessage` (`coordinateOpen || changeClosed`; the match checked in before meet − 1:30 shows C — Philipp's AC);
  - `Change meet time` hidden with no later slot;
  - `sentLabel` for a time and a place.
- [ ] Step 2: Model tests:
  - `cancel_guard_shown` on every open, with `chat_open`, `penalty_points` (from `cancelPenaltyPoints`) and `minutes_before_start`;
  - `cancel_abandoned_at_guard` once per close with `keep`/`dismissed`/`message` and the session's `change_requested`;
  - a picker open is not an exit;
  - the cancel in flight disables both;
  - failure → E, then a retry;
  - 409 `checked_in` → close + re-read;
  - 409 `date_not_active` → re-read (lands on H);
  - success → `landAfterEnd`;
  - a cancel after a request is still a cancel;
  - saved state restores the sheet and the sent value.
- [ ] Step 3: Implement until green.
- [ ] Step 4: Screen tests:
  - B/C/D/E/I copy and order;
  - the cost box reads `by 10` and follows the value passed (change it to 7, see 7);
  - the error box between the cost box and the choices;
  - the link is the last item of the scroll and is hidden after own check-in;
  - H copy, no back, CTA → Search.
- [ ] Step 5: Implement the sheet and H to the reference numbers: portrait 244 r24 with the gradient overlay, pill, Lora 24/27, `su-grad-lilac`/`su-grad-blush` from the design tokens.
- [ ] Step 6: Host wiring; fit sweep at 17 sizes; fix findings.
- [ ] Step 7: Tests green; `assembleDebug`; commit.

### Task 7: iOS — Date 07 twin

**Files:** the Swift twins of Task 6, `DateEndedScreen.swift`, `ChangeRepository.swift`, `ShowUpWelcomeApp.swift`, `DateScreen.swift`; `gen_pbxproj.py`.

- [ ] Steps mirror Task 6; CI verifies.

### Task 8: Dev, previews, evidence, checkers, registry stamps

**Files:**
- Modify:
  - `dev/DevJump.kt` / `DevCheckIn.swift`: States rows `197A`–`197J` and `198A`–`198I`, minus 198G.
  - `devLiveFrame`: also 197/198, opening live.
  - `dev/DevDateDemo.kt` / `.swift`: end/discomfort/cancel with failure frames J and E failing once; landings open the right sheet and step.
  - The debug gallery activity and `CheckInGalleryFrame` (pictures).
  - `DevStatesTest`, `DevMenuRulesTests` (counts).
  - Previews (`DateEndPreviews.kt` / `.swift`, `CancelPreviews`).
  - Evidence harness entries (Android `EvidenceScreenshots`, iOS `EvidenceShots`) at 375 × 667, 390 × 844 and 430 × 932.
  - Client registry stamp `1.4.39` (Android `ProfileAnalytics.FIELD_REGISTRY_VERSION`, iOS twin) and both events files.
- Create: `audit/verify-end.py`, `audit/verify-cancel.py`, quoting every ticket string and checking it in both apps' copy files.

- [ ] Step 1: Write the States/demo tests (every row opens its own frame; 197/198 rows open live; the demo's end, cancel and discomfort follow the server rules: before_meet, not_checked_in, checked_in, once-failing J/E).
- [ ] Step 2: Implement; run `DevStatesTest`, `DevDateDemoTest`, the fit tests, `-Pevidence` on Android, and all `audit/*.py`.
- [ ] Step 3: Commit; push; open the PR to `development`; CI green on all four checks.
