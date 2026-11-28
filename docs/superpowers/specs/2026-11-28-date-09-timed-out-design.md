# Date 09 — your date has ended (the +2 h switch): design

SHOWUP-200 (Date 09, Jira In Progress, Esma), asked for on 27 Nov 2026; build started 28 Nov ("do the 200 ticket").
Parent epic 07a, the last story in its build order. Built on Date 08 (SHOWUP-199, `PartnerEndedScreen`): branch
`feat/date-09-timed-out` is stacked on `feat/date-08-partner-end` and takes development once 199 merges.

## 1. Sources and authority

- **Behaviour, scope and copy:** the Jira description (re-read 28 Nov; **no comments**), synced to
  `design_handoff_showup/date/tickets/09-timed-out.md`.
- **Numbers:** `design_handoff_showup/date/screen-timed-out-reference.jsx` (`ScreenDateTimedOut`), from
  `Show Up Design System (1) (1).zip` (27 Nov). It is token-identical to the kit function embedded in the spec-sheet
  HTML. (`Robot07_Lab05.zip`, named first, was an unrelated lab and was never used.)
- **Tracking:** registry 1.4.40 / taxonomy 1.4.32 from the same zip. Definitions come from the zip; the repo's
  build-status fields (`implemented`, `backend_status`, `code_delta`) are carried over (the 197 trap).
- **Order of authority:** reference wins on numbers, ticket on behaviour / scope / copy, PNG on nothing.

## 2. Scope

**Built:**
- the server's +2 h switch, shipped OFF (R1, R2);
- the *timed out* state (R3) and the outcome both clients read (R4);
- *already ended* for every late action (R6);
- `checked_in_count` on every `date_ended` (R7);
- the fourth payload `DateTimedOut` on `PartnerEndedScreen` (R8), with `{time}` (R9);
- routing for both people (R10), and closing what is open at the switch (R11);
- `screen_viewed date_timed_out` (R12), dev frames (R13), evidence (R14).

**Not built:** push and review reminders (notifications pass), flow 10 (`ScreenRating`), any countdown before the
switch, and a last message.

## 3. Rulings

- **R1 — The switch ships OFF (Esma, 27 Nov).** This follows Jira's dependency clause: with flow 10 unbuilt, the switch
  has nowhere to land.
  - One server switch, `DATE_TIMEOUT_ENABLED`. Only `true` (any case) turns it on; unset, `false` or anything else is off.
    This is unlike `DATE_ENDS_ENABLED`, which defaults on.
  - The sweep is always scheduled, and it closes nothing while the switch is off.
  - Everything else (screen, routing, *already ended*, the arrival guards) is live and tested. Dev frames show the screen
    without the server.
  - When on, `Proceed to review` uses 199's `startReview` slot (marks the end seen, lands on Search) until flow 10 exists.
- **R2 — A once-a-minute sweep, the codebase's job shape** (`change-expiry`, `coordinate-unlock`: `upsertJobScheduler`
  with a stable id, every 60 s). The ticket asks for one job per date. A sweep over `status = confirmed AND
  scheduled_at + 2 h <= now` meets every rule of that job without per-date job state:
  - An agreed later time moves it, because `ChangeSuggestionsService.agree` updates `scheduled_at` under the row lock.
  - A pending suggestion or a place change moves nothing.
  - Any end cancels it, because the date is no longer confirmed.
  - A retry cannot close twice. Each date closes in its own transaction: `SELECT … FOR UPDATE`, then re-check confirmed
    and due on the locked row, then a conditional `UPDATE … WHERE status = 'confirmed'`. `date_ended` fires after commit,
    only for a row actually updated. This is the same per-date serialisation as Date 06's `end()`.
  - Up to 60 s late is accepted. One failing date is logged by id and does not stop the others.
  - Each run takes at most 100 dates, oldest first.
- **R3 — State.**
  - New `DateStatus.TimedOut = 'timed_out'` (Jira: "the state *timed out*"), terminal. `Confirmed → TimedOut` is the only
    way in.
  - `end_reason` becomes `timed_out` and `end_entry` becomes `auto_switch`. Both are new values of the existing Postgres
    enums, added in migration `1717000039000-DateTimeout` with a working `down`.
  - The switch also sets `ended_at = now` and `ended_by_id = null`, and leaves `ender_checked_in` / `partner_checked_in`
    null.
  - As in `end()`, the match is cancelled and a `DateStatusChange` row is written.
  - Check-ins, chat and suggestions are kept as evidence (epic rule 6).
  - `timed_out` and `auto_switch` are never accepted from a client: `EndDateDto` validates against the three Date 06
    reasons and entries only.
- **R4 — The outcome.** `GET /me/date` returns this ended outcome for a timed-out date, to both people:
  - `reason: timed_out`, `endedByYou: false`, `partnerScreen: date_timed_out`;
  - a new optional `meetAt` (ISO 8601, the date's `scheduled_at`, the latest agreed meet time), set on every ended
    outcome the read returns;
  - the usual `photoUrl`.

  `ENDED_STATUSES` and `endedOutcomeOf` accept `TimedOut`. `markEndSeen` already works for any status.
  `openapi.json` is regenerated (both copies): `EndedReason` + `timed_out`, `PartnerScreen` + `date_timed_out`,
  `EndedOutcomeDto.meetAt`. `meetAt` is optional, so no fixture breaks.
  Old builds would mis-read `timed_out`. That is acceptable only because the switch ships off and no store build
  exists.
- **R5 — "Realtime" means the reads that already exist** (Date 08 R2): the chat polls every 5 s, the Date screen every
  15 s, a late action's 409 re-reads, and a cold start reads. There is no socket, and there is no push here (Jira).
- **R6 — *Already ended*.**
  - **Date 06 end / report and Date 07 cancel** already answer `409 date_not_active` once the date is not confirmed.
    Both apps already close the sheet and re-read on that code, so with R10 they land on Date 09 with no `EndErrorBox`.
    This is tested, not rebuilt.
  - **Date 02's chat end** answers 409 and the chat re-reads (unchanged).
  - **Arrival `create` / `commit` / `undo`** answer `409` with `code: date_not_active` (the shared refusal shape) once the
    date is not confirmed.
    - `undo` gains that check (Jira: "Undo is no longer possible").
    - `create`, `commit` and `undo` each run in a transaction that holds the date row `FOR SHARE` and re-checks
      confirmed. That serialises them with the sweep: a check-in committed after the switch is refused and pushes
      nothing. A commit that won the race still pushes, sent after its transaction, so it can arrive milliseconds
      after the switch for a check-in made before it. Nothing is pushed inside a lock.
    - An uncommitted check-in (Yes tapped, done sheet open) stays stored. That row is evidence for flow 10.
- **R7 — `checked_in_count` on every `date_ended`.** It counts committed arrival check-ins at the moment of the end
  (0 / 1 / 2, the registry's definition).
  - Date 06's `date_ended` gains it (`Number(ender) + Number(partner)`).
  - The switch's `date_ended` has `end_reason: timed_out`, `entry: auto_switch`, `checked_in_count` and
    `minutes_vs_start`, and **no** `ender_checked_in` / `partner_checked_in`.
  - No person ended it, but `trackServerEvent` needs a user. The switch's event carries participant A's id (hashed
    server-side): it is stable and once per date, and `date_id` is the join.
  - Costs if wrong: one line, if analytics wants a system actor.
- **R8 — Payload `DateTimedOut`** (reference): it is C's anatomy, with three changes.
  - The hero is lilac, padding 30 / 24, avatar → headline gap 18.
  - **The avatar is the match's photo** (`MatchPhoto` 76, as A / B), not C's heart disc.
  - **The badge is violet** (primary 500, the `Purple` token) with `clock` 15 / stroke 2.6.
  - Rows start 18 below the hero; `heart` and `search` are violet 500 on violet 12 % (C's rows, D's copy).
  - The badge colour becomes a three-way `PartnerBadgeTone { Orange, Success, Violet }`, replacing `badgeSuccess`. A / B / C
    render identically. Every other layout value is the shared one (an AC: switching payloads changes no layout value).
- **R9 — `{time}`.** A pure `meetClock(meetAt, zone, is24h)`: 24 h gives `HH:mm` (`19:00`), 12 h gives `h:mm a` with
  English AM/PM (`7:00 PM`); the app's copy is English.
  - The host passes the device setting: Android `DateFormat.is24HourFormat(context)`; iOS reads the `j` template of the
    current locale (`DateFormatter.dateFormat(fromTemplate: "j", …)` contains `a` → 12 h).
  - Source: the outcome's `meetAt`, else the held date's `meetAt`. It is carried on the route as `meetAt` so a restored
    route keeps it.
  - With no time at all (an older server), the sub drops ` ({time})`. The screen never shows a placeholder.
- **R10 — Routing.** `OutcomeReason.TimedOut` is new.
  - `endedRoute` checks `reason == TimedOut` **first** and returns `PartnerEnded(DateTimedOut)` whatever `endedByYou` or
    `partnerScreen` say. Today an unknown reason falls back to Date 02's G.
  - Every rule of 199 holds: no exit (Android back does nothing, no swipe, no TabBar), once per date, the referrer from
    where the person was, none on a launch, `screen_viewed` per showing, and Proceed fires nothing.
- **R11 — What is open at the switch closes, and nothing is sent.** `showPartnerEnded` is shared with Date 08, so these
  close for both stories:
  - **Check-in done sheet:** discarded without commit or undo. It needs a new `discardForPartnerEnd()` on the arrival
    gate on both apps. Today the phase stays `Done` behind the screen, and `close()` would commit.
  - **Android chat draft:** discarded when the Date screen found the end first. iOS already drops the chat model.
  - Date 05 / 06 / 07 sheets already close (199).
- **R12 — Tracking.**
  - `ProfileScreen.DateTimedOut("date_timed_out", "Date - Timed out")` on both apps.
  - Registry 1.4.40 / 1.4.32 in the handoff and in the server copy (`src/modules/analytics/registry/enums.json`).
  - `FIELD_REGISTRY_VERSION` becomes `1.4.40`.
  - Must not fire: a client `date_ended`, `date_cancelled`, `date_ended_in_chat`, a second `arrival_confirmed`, or any
    push event.
- **R13 — Dev frames.**
  - `200` (the Date screen when the switch lands) and `200-chat` (in the chat, the switch lands 3 s later; the chat draft
    goes) are LIVE frames on `DevDateDemo`, through a new demo `timesOut()`.
  - Both have States rows (ticket label `SHOWUP-200 Your date has ended`), gallery pictures and dev-menu buttons.
- **R14 — Evidence.** Screenshots at 375 × 667 (plus scrolled to the CTA), 390 × 844 and 430 × 932 on both apps.
  - The two-phone recording needs a server with the switch on. Esma decides when; this build makes it possible (switch
    + dev route) and says so.

## 4. Copy (Jira, verbatim)

- Headline: `Your date has *ended.*`
- Sub: `We closed your date with {name} automatically, 2 hours after the meet time ({time}).`
- Row 1 (heart): `If you met, we hope you had a great time. **Either way, tell us how it went in the review.**`
- Row 2 (search): `Once the review is done, you're free to **search for your next date.**`
- CTA: `Proceed to review`

Apostrophes as the ticket writes them (straight `'`). `{name}` is the match's first name.

## 5. Review focus

1. A Date 06 end that loses the race to the switch shows `date-timed-out`, never `EndErrorBox`.
2. A check-in whose done sheet is open at the switch: the row is kept, there is no arrival push, Undo is refused, and the
   sheet is gone.
3. Two sweeps (or a crashed retry) never close a date twice nor fire `date_ended` twice.
4. An agreed later time moves the switch; a pending suggestion and an agreed place change do not.
5. With the switch off, nothing closes, and the screen is still reachable from dev frames.
6. A / B / C render pixel-identically after the `PartnerBadgeTone` change.
