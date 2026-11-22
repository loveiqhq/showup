# Date 06 + Date 07 — end the date, report a problem, cancel this date: design

SHOWUP-197 (Date 06, Jira In Progress, Esma) and SHOWUP-198 (Date 07, Jira To Do), requested together by Esma on
21 Nov 2026. Parent epic SHOWUP-192 (07a). Both end a date, so they share one server state machine, one ended payload
and one landing rule; they are designed together and built on one branch, `feat/date-06-07-end-cancel`.

## 1. Sources and authority

- **Behaviour, scope and copy:** the Jira descriptions. SHOWUP-197 has no comments. SHOWUP-198 has one comment
  (Philipp, 16 Nov): the guard sheet shows C (with `Message {name} instead`) whenever the chat is open, including
  when the match checked in before meet − 1:30, plus one AC. Jira 198's description already carries it.
- **Numbers:** `design_handoff_showup/date/screen-wrap-report-reference.jsx` (06) and `screen-cancel-reference.jsx`
  (07). Order of authority on both tickets: reference wins on numbers, ticket on behaviour/scope/copy, PNG on nothing.
- **Handoff version.** Esma gave `Show Up Design System (3).zip` for 197 and `(4).zip` for 198. **(4) is the older
  export**: it lacks the 16 Nov rules (C2, a check-in opens the chat) and the 17 Nov Date 08 rule, and its Date 07
  frame I is "from the meet time" (`pastMeet`). (3) matches both Jira tickets word for word (frame I "from meet − 0:30",
  `changeClosed`). The repo's `design_handoff_showup/` is synced from **(3)**. The Date 07 spec-sheet PNG/HTML of
  21 Nov also draws the old frame I; the ticket wins, and the layout of I is identical either way.
- **The 17 Nov rule wins over two leftovers:** the match's Date 08 screen follows `end_reason` **alone**
  (Jira 197 Decided + AC; Date 08 ticket). Jira 197's Build inventory line ("… from `end_reason` + `partner_checked_in`")
  and the §43 note in `enums.json` are 10 Nov leftovers. `partner_checked_in` is still stored and sent.
- **Tracking:** the registry in (3) is `enums.json` **1.4.39** / taxonomy 1.4.31: it carries Date 06's 1.4.36, Date 07's
  1.4.37 and Date 08's 1.4.39 rows. Server and both apps move to it; the field-registry stamp becomes `1.4.39`.

## 2. Scope

**Built (both apps + server):**
- **Date 06:** frames A–C and E–J (D is Date 03's gate, consumed with its `note`); the end endpoint, the ended state,
  the discomfort flag; un-hiding Date 03's `End date` link; wiring Date 01's Report card; family N tracking.
- **Date 07:** frames A–F, H and I (G is flow 10's); the cancel endpoint respecified, the no-show record with penalty
  points from a rate config, the permanent pair block and its search filter, the match's push and H; family K tracking.
- Dev: States rows for both tickets open live on the in-memory date server (as 196's do), pictures for evidence.

**Not built:** flow 10 (`rating-you-noshow`, the review) · Date 08's three screens · the +2 h switch (Date 09; no job
exists to cancel) · *I'm in danger* / *Something else* (parked) · the Show-up Rate formula and display · Settings'
blocked deletion · final push copy · Date 05's pickers (consumed with `entry: cancel_sheet`).

## 3. Rulings (my calls; each says what it rests on and what it costs if wrong)

- **R1 — Where an end lands, until flow 10 and Date 08 exist.** Every Date 06 end sends the ender to flow 10 and the
  match to Date 08; a cancel sends the canceller to flow 10. None of those screens exist. Date 07's ticket decides
  its case: *"If [flow 10] isn't built when this ships, the link ships hidden — a cancel with nowhere to land is a
  dead end."* The same holds for every Date 06 end, so one rule covers both:
  - **One server switch, `DATE_ENDS_ENABLED`:** `true`/`false` when set; unset means on everywhere, production
    included. The active-date payload carries it as `endsEnabled`.
  - **Esma, 22 Nov 2026:** do not hide them, even before flow 10 and Date 08 exist. This overrides Date 07's
    dependency clause above; an end and a cancel land on Search until those screens exist.
  - **Off:**
    - the End Date bar's button, the Check-in bar's `End date` link and the Cancel link are not rendered;
    - the Report card is not rendered (epic rule 8, *hidden until real*);
    - the endpoints answer 404.
  - **On** (every environment unless the variable is `false`): everything works. After an end, the
    ender leaves the Date screen the way a date that went away does today (to Search). Flow 10's slot is one
    function per platform, `landAfterEnd(outcome)`.
    - The match of a Date 06 end also goes to Search (Date 08's slot, the same function).
    - The match of a cancel sees **H**, which is built here.
  - Rests on: Date 07's dependency clause; epic rule 8.
  - Costs if wrong: one default. Flipping the switch, or wiring flow 10 / Date 08 into `landAfterEnd`, is all
    that changes.
- **R2 — States after an end.**
  - New `date_status_enum` value **`ended`** for a Date 06 end. A cancel uses the existing `cancelled`.
  - Neither status locks matching (`date-lock.ts`), so both people can search again.
  - Search stays blocked until the review (epic rule 5), but that block is flow 10's to enforce. Until then the
    end lands on Search, with nothing enforcing the block yet.
  - The pair's match is closed, as Date 02's end in chat does.
- **R3 — The ended payload, one shape for every end.** `GET /me/date`'s `ended` object gains:
  - `reason`: `chat` · `cancelled` · `met` · `partner_no_show` · `uncomfortable`;
  - `partnerScreen` for the match of a Date 06 end: `date_closed_positive` · `you_no_showed` ·
    `date_ended_discomfort`, picked from `end_reason` alone (§1).
  - It is reported until the viewer marks it seen. The existing `a/b_end_seen_at` columns and
    `POST dates/:id/ended/seen` serve every reason.
  - The clients route on it:
    - `chat` → Date 02's G/H, unchanged;
    - `cancelled`, not by you → **H**;
    - every other case → `landAfterEnd` (R1).
- **R4 — The permanent pair block is its own table.** `blocks` rows are directional, can be lifted by their blocker
  and carry no date. The ticket wants both IDs, the date ID, a timestamp and reason `red_link_cancel`, permanent and
  read by search.
  - Table: `date_pair_blocks` (`id`, `user_low`, `user_high`, `date_id`, `reason`, `created_at`), unique on the pair.
  - `SafetyService.blockedUserIdsFor` unions it, so search, Likes received, matching and every other reader of that
    function exclude the pair in both directions.
  - Nothing deletes a row.
- **R5 — Rate config.** `src/modules/dates/util/show-up-rate.config.ts`: `noShowPenalty: 10`, `attendedBonus: 10`,
  marked as placeholders until the Show-up Rate screens.
  - The active-date payload carries `cancelPenaltyPoints`, and the cost box renders it: `by {drop}`, never a literal.
  - The cancel stores `penalty_points` from the same config.
- **R6 — `change_requested` on the cancel is derived by the server:** true when the canceller has any suggestion on
  this date with `entry = cancel_sheet`. The client's `cancel_abandoned_at_guard.change_requested` is the sheet
  session's own flag.
- **R7 — Serialised ends and cancels.** Both run in one transaction under a date row lock
  (`SELECT … FOR UPDATE`), with a conditional status update.
  - The first wins. A later end or cancel on a date that is no longer `confirmed` gets **409 `date_not_active`**;
    the client reads the date again and routes on `ended`. A loser of two simultaneous cancels therefore lands on H.
  - A cancel from a checked-in user gets **409 `checked_in`**: the sheet closes and the Date screen reads again
    without the link.
  - A no-show end before the binding time gets **409 `before_meet`**. A no-show or uncomfortable end without a
    committed check-in gets **409 `not_checked_in`**.
- **R8 — `EndErrorBox` is Date 06's name for the shared `SheetErrorBox`.** Date 02's end sheet and Date 03's
  `locError` already render it, and the reference says it is "the same box".
  - `EndErrorBox(text = DateEndCopy.END_FAILED)` is a thin wrapper, so there is one visual implementation. Date 07
    passes `text = CancelCopy.CANCEL_FAILED`.
  - The box's numbers are checked against the reference: padding 11/13, radius 14, danger 8 % / 20 %, icon 17,
    13.5/600/1.4, 14 below. Any drift is fixed in the shared box.
- **R9 — The gate continues to the picked step.**
  - `ArrivalEntry` gains `Wrapup`; `Report` is live.
  - The end model, not the gate, remembers the step a gated route was heading for:
    - E/F for *My date isn't showing up*, I for *I don't feel comfortable*.
    - **Close** on the done sheet (commit) reopens that sheet at that step.
    - **Not yet** reopens the menu the route came from (B or H).
    - **Undo** closes everything, and the Check-in bar is back (Date 03).
- **R10 — `{when}` in the guard pill** reuses the chat header's day-and-time formatter, `Today · 19:00`. The kit's
  `Tonight · 19:00` is sample data. The binding (agreed) values are used.
- **R11 — The no-show unlock.**
  - `pastMeet = now ≥ meetAt` (the agreed time) decides E or F. It is evaluated when the sheet opens and on the
    Date screen's 1-second tick.
  - `chatOpen` is the shown date's `coordinateOpen`, which includes a committed check-in (195).
  - Both are plain functions, tested.
- **R12 — Cancel sheet tiles and body: Date 05's gate, the same function.**
  - The body line and both tiles show only while `change.allowed` holds at `now` (`changeKindAvailable`'s first
    half).
  - Each tile follows `changeKindAvailable(change, kind, now)`, so `Change meet time` hides with no later slot.
  - `Message {name} instead` shows while `coordinateOpen || changeClosed` (the reference's `showMessage`).
- **R13 — The ended date leaves every surface.** After an end or cancel (by either person), the End Date bar, the
  Check-in bar, the Report card and the Cancel link are gone, because the date is no longer active and `GET /me/date`
  reports `ended` until seen. No client-side special case.
- **R14 — Process death.** The open sheet (wrap or report or cancel), its step and a sent-from-sheet request survive.
  - Android: `SavedStateHandle`, as 196's picker.
  - iOS: an epoch-scoped `@SceneStorage` draft.
  - A restored step is re-validated against the date read: `pastMeet` again, and a gated step without a check-in
    goes back to the menu.

## 4. Server

- **Migration `1717000037000-DateEnds`:**
  - `date_status_enum` gains `ended`.
  - `dates` gains:
    - `end_reason` (`date_end_reason_enum`: met · partner_no_show · uncomfortable);
    - `end_entry` (`end_entry_enum`: end_date_bar · checkin_bar_link · report);
    - `ender_checked_in`, `partner_checked_in`, `cancel_change_requested` (bool, nullable);
    - `hours_notice` (numeric(8,2)), `penalty_points` (int).
  - New tables `date_discomfort_flags` (id, date_id, user_id, created_at) and `date_pair_blocks` (R4).
  - The existing `ended_by_id` / `ended_at` and `cancelled_by_id` / `cancelled_at` are reused.
- **`POST dates/:id/end`** `{ reason, entry }`:
  - validates participant, active, R7's checks, and the switch;
  - stores R2/§4's fields and each person's committed-check-in state;
  - closes the match;
  - fires `date_ended` once on success (`date_id, end_reason, entry, ender_checked_in, partner_checked_in,
    minutes_vs_start` from the agreed time; negative = before).
  - Answers 200 with the ended outcome.
- **`POST dates/:id/discomfort`** (Stay for now): active date and participant; stores a flag row; fires
  `problem_stay_flagged` (`date_id, minutes_vs_start`). Every tap stores one more.
- **`POST dates/:id/cancel`** (respecified, body `{}`, an old `reason` ignored):
  - validates participant, `confirmed`, not checked in, and the switch;
  - in one transaction stores status `cancelled`, `cancelled_by_id`, `cancelled_at`, `hours_notice` to the agreed
    time (negative after it), `penalty_points` from R5, both check-in states and R6's `change_requested`;
  - writes the pair block and closes the match;
  - pushes the match (new kind `date_cancelled`, placeholder copy, 198 Open item 1);
  - fires `date_cancelled` (`date_id, hours_notice, penalty_points, change_requested`; **no** `initiator`, **no**
    `reason`, no `had_reason`).
  - The dev cancel route (`date-dev.service.ts`) follows the same path.
- **`GET /me/date`:** the active date gains `endsEnabled` and `cancelPenaltyPoints`. `ended` covers every reason
  (R3) and is shown until seen.
- **Search:** `blockedUserIdsFor` unions `date_pair_blocks` (R4).
- **Registry:** server `enums.json` → 1.4.39 (from the handoff). New value sets §43–§46; §41 gains `wrapup`; §42
  gains `cancel_sheet`; §11 gains `date_partner_cancelled`.
- **Dev routes** for the demo walk: the match ends the date (each reason), and the match cancels.

## 5. Clients (Android and iOS twins, same names)

- **Date 06:**
  - `EndDateBar` (`DateStatusBar`, one state), in the footer slot after check-in.
  - `DateWrapSheet` (B, C), `ReportProblemSheet` (H, two options).
  - Shared, one copy each: `WrapConfirm` (`secondary`, `error`), `WrapBack`, `NoShowGuide`
    (`pastMeet`, `meetTime`, `chatOpen`), `NoShowConfirm`, `UncomfortableStep`, `EndErrorBox`.
  - `DateEndModel` (Android `DateEndViewModel`) owns the open sheet, its step, the gated route's step, the end in
    flight, the error, Stay for now, and the family N client events.
  - Host wiring: the bar and the link open B (entry `end_date_bar` / `checkin_bar_link`); the Report card opens H;
    the gate continuation (R9); `Message {name}` opens the chat; success → `landAfterEnd`.
- **Date 07:**
  - The Cancel link (exists; wired, and visible while active, not checked in and `endsEnabled`).
  - `CancelDateConfirmSheet` (B/C/D/E/I in place, one `ModalShell`, scrolls on every device).
  - The tiles open Date 05's pickers over it with `entry: cancel_sheet`; a send shows D, and the sheet stays.
  - `CancelModel` owns the sheet, its sent value, `change_requested`, the cancel in flight, the error, and family K
    client events.
  - `DateEndedScreen` (Date 02's component) gains the default `cancelled` variant, H, with no back.
- **Tracking (client):**
  - `wrapup_opened` (`date_id, entry, checked_in, minutes_vs_start`)
  - `wrapup_outcome_selected` (`date_id, outcome, checked_in`), on the tap, before any gate
  - `problem_report_opened` (`date_id, checked_in, minutes_vs_start`)
  - `problem_category_selected` (`date_id, category, checked_in`), on the tap
  - the gate's events with `entry: report | wrapup`
  - `cancel_guard_shown` (`date_id, chat_open, penalty_points, minutes_before_start`), each open
  - `cancel_abandoned_at_guard` (`date_id, exit, change_requested`), once per close without a cancel
  - Date 05's picker events with `cancel_sheet`
  - `screen_viewed` `date_partner_cancelled` with its referrer
  - Cancel, scrim, Back, `Message {name}` and the disabled end button fire nothing.
  - Retired names are never emitted.
- **Copy:** exactly the tickets' strings, in `DateEndCopy` / `CancelCopy`, with the bold and italic runs as the
  reference draws them.

## 6. Verification

- **Server:** unit tests for every rule in R7 and the payload; e2e for end (each reason), discomfort, cancel
  (penalty, block, push, 409s), and search exclusion both ways.
- **Clients:**
  - model tests for every step, gate path, failure and retry, and every event and payload;
  - screen tests per frame for copy, order and disabled states;
  - fit at 17 sizes;
  - evidence screenshots at 375 × 667, 390 × 844 and 430 × 932: 197 A–C and E–J, 198 A–F, H and I;
  - `verify-end.py` / `verify-cancel.py` quoting the tickets' copy.
- **Emulator walk** of both tickets on the live States frames and on a real test date.

## 7. Owed by others

- **Philipp:**
  - Date 06 Open item 2, the copy consistency (`End Date → Review` vs `End date and review`, `Cancel date in the
    chat menu`, `End Date` vs `End date`), kept as written until he decides;
  - push copy;
  - final rate values.
- **Devices:** the two-phone recordings both tickets ask for.
