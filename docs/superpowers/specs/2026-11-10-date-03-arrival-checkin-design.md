# Date 03 — Arrival check-in (SHOWUP-195): design

Written 10 Nov 2026. Source of truth: the Jira description of SHOWUP-195 (no comments on it, none on the
epic SHOWUP-192). The ticket's reference implementation (`design_handoff_showup/date/screen-checkin-reference.jsx`,
from the 6 Nov zip "Show Up Design System.zip", Jira attachment 12189) wins on numbers. The ticket wins on
behaviour, scope and copy. The spec-sheet PNG wins on nothing. Frame G has no handoff reference: its numbers
come from the kit's own source, decoded from the spec-sheet HTML bundle and saved unedited as
`docs/superpowers/specs/kit/2026-11-10-date-gate-kit-excerpt.jsx`.

## 1. Intent

When a person reaches the meeting place they say so, deliberately. `Yes, I've arrived` stores the phone's
location, a timestamp and their user id against the date: evidence that flow 10 later compares (150 m each).
Nothing here decides anything. Right after, they can undo it. Once they close the confirmation, their match is
told, on the Date screen's Coordinate card (and by push), and never before, so an undo never reaches them.

## 2. Goals, non-goals, anti-goals

**Goals**
- Every acceptance criterion and tracking criterion in SHOWUP-195, on Android and iOS, in one PR, with the
  server it needs (store, undo, commit, notify, payload, 1-year retention).
- Frames A–F and H pixel-faithful to the reference at 375 × 667, 390 × 844 and 430 × 932, and clean in the fit
  sweep on every phone and type size the 193 audit measures (`DEVICES + EDGE_DEVICES`, every font step).
- Frame G (the location gate over the Date screen): the AC requires it ("with location not usable, the gate
  (Search 04) is shown instead of the Date screen") and the date context of that gate does not exist in either
  app. Built here from the kit source (see §3, ruling R1).

**Non-goals** (the ticket's *Out of scope* / *Not built here*)
- `DateStatusBar` (the End Date bar), `DateWrapSheet`, `ReportProblemSheet` — Date 06. After check-in the
  footer slot is **empty** until 06 ships; the TabBar stays (callout ⑦).
- The 150 m comparison and every Show-up Rate outcome (flow 10). The client never compares locations.
- Push copy (open item 1): the push fires on commit with interim copy (R6); the final copy lands later.
- The OS location prompt and Settings pages; the Search 04 gate's existing contexts.

**Anti-goals**
- A coordinate, accuracy, distance, place or address in any analytics payload, push payload, log line or UI.
- A check-in without coordinates, a last-known or low-accuracy fallback, a client-side time window.
- The match notified on `Yes`, or ever for a check-in that was undone.
- An undo anywhere but D; the Check-in bar again after D was closed.
- `message_sent` from the chip before check-in; `date_cancelled`; any flow-10 event; `screen_viewed` for B, C, D.
- A second copy of `CoordinateModule`, `ModalShell`, `SheetErrorBox` or the chat vocabulary.

## 3. Rulings (my calls, to report; each names what it rests on)

- **R1 — Frame G is built here.** The ticket says G is "consumed, not rebuilt", but its AC requires the gate
  over the Date screen and the date context (`ScreenProfileLocation context="date"`, `DateGateCard`,
  `LOCATION_COPY.date`) exists only in the kit. Esma's standing rule: when the AC and a "not built here" note
  clash, build what the AC says. Built as a third context of the ONE location screen (Profile 12 / Search 04),
  never a copy. **No event fires from it**: the registry has no §11 row and no event for the date gate, and
  `location_gate_shown` / `permission_settings_opened` are defined for `search_location_gate` only. Reported as
  a tracking gap for Philipp.
- **R2 — The arrival card before the chat unlocks.** The ticket: "the card shows anyway; `Open chat` follows Date
  01's locked rules"; Date 02: "Before that the Coordinate card is locked (Date 01) and nothing here is
  reachable." So before meet − 1:30 the card shows (hoisted, orange) and `Open chat` **clears it** (the "taps
  it" clear) without opening the chat; after the unlock it opens the chat as any `Open chat` does.
- **R3 — "Seen" is the chat-read stamp.** "It clears when the match opens the chat or taps it, like an unread
  message": the card shows while the partner's committed check-in is newer than the viewer's chat read stamp.
  Opening the chat stamps it (Date 02); the locked-card tap stamps it through the same `POST /dates/:id/chat/read`.
- **R4 — Commit is the client's, explicitly.** The server notifies only on `POST …/arrival/commit`, sent on Close,
  the scrim / back / swipe on D, and the app going to the background with D up (Android `ON_STOP`, iOS scene
  `.background` — killing an app passes through the background first). There is no server-side auto-commit, so
  nothing can notify a check-in that was being undone. A crash with D up leaves the record stored but
  uncommitted: the match is not told and the Check-in bar shows again; a new `Yes` replaces that record.
- **R5 — A failed store is frame C.** The ticket has no copy for "location found, server unreachable". C's
  string ("Check your signal, then try again") covers it, nothing is stored, `Try again` repeats read + store.
  A dedicated string is proposed to Philipp: `Couldn't check you in. Check your connection and try again.`
- **R6 — Interim push copy** (the ticket: "the push fires on commit; the copy can land later"): type
  `date_partner_arrived`, title `{name} has arrived`, body `{name} has arrived at the meeting location.` (the H
  card's final sentence); DE `{name} ist da` / `{name} ist am Treffpunkt angekommen.` Category `engagement`, as
  `date_reminder`. Data `{ type, dateId }` only.
- **R7 — Busy guard.** While the location is read and stored, both buttons are inert (disabled semantics), the
  primary at alpha .6 (the 194 End date sheet's waiting look), and scrim / back / swipe do nothing.
- **R8 — Undo and commit are optimistic and retried.** Undo closes D at once and lands on the Check-in bar;
  Close swaps the footer at once. Each request retries in the background (2 s doubling to 30 s) until it lands.
  Neither can turn into the other: an unlanded undo is never committed (R4).
- **R9 — The sheets swap in place.** B → C grows the error box (Motion.STATE, the brand curve); B/C → D
  crossfades the content and animates the height (Motion.PROGRESS) on the same scrim; D → closed lowers the
  sheet as every sheet does. Reduced motion: instant. The kit has no animation; this keeps one scrim instead of
  two sheets dropping and rising.
- **R10 — `{time}` on the arrival card** is the unread card's formatter (`now` within the minute, then `HH:mm`)
  of the check-in's stored timestamp.
- **R11 — The arrival outranks an unread message** in the hoisted slot (the reference's order: arrival →
  request → message). Both clear together (R3). `date_screen_viewed.has_unread` keeps meaning a message.
- **R12 — The Check-in bar at extreme sizes.** The text column never goes below its longest word; where pin
  tile + text + button cannot share the row, the button moves under the text, full width, 10 below. The bar
  already moves into the scroll when bar + TabBar exceed 60 % of the screen (193).
- **R13 — `I arrived at the spot` is shown** (Date 02 hid it until this ticket). The grid shows six chips:
  on_my_way, arrived, cant_find, when_arrive, running_late, cant_make_it.
- **R14 — Retention = one calendar year** from the stored timestamp (`now() - interval '1 year'`), daily job.
- **R15 — `End date` link** is built behind a switch that is off (epic rule 8, as 193's change tiles).

## 4. Server

**Table `date_arrival_check_ins`** (migration `1717000035000-DateArrivalCheckIns`):
`id uuid pk` · `date_id uuid not null → dates(id) on delete cascade` · `user_id uuid not null → users(id) on
delete cascade` · `location geography(Point,4326) not null` · `accuracy_m double precision null check (>= 0)` ·
`entry arrival_entry not null` (enum `date_screen | chat_chip | report`) · `client_id uuid not null` ·
`checked_in_at timestamptz not null default now()` · `committed_at timestamptz null` ·
`unique (date_id, user_id)` · index on `checked_in_at` (retention). Match the FK style of `date_chat_messages`.

**Routes** (DatesController, participant only; 404 unknown date, 403 not yours):
- `POST /dates/:id/arrival` (`createArrivalCheckIn`) body `{ latitude (-90..90), longitude (-180..180),
  accuracyM? (0..100000, finite), entry (§41), clientId (uuid) }` → 201 `{ checkedInAt, committed }`.
  409 unless the date is `confirmed`. Same `clientId` as the stored row → that row, no event. A committed row →
  409 "already checked in". An uncommitted row with another `clientId` → replaced in place (location, accuracy,
  entry, client id, timestamp). A unique-index race → answered with the winner. `arrival_confirmed` once per
  stored row (create or replace).
- `DELETE /dates/:id/arrival` (`undoArrivalCheckIn`) → 204. Deletes only an uncommitted row
  (`… AND committed_at IS NULL RETURNING`), then `arrival_undone`. A committed row → 409 "no undo after Close".
  None → 204 (an undo already done), no event.
- `POST /dates/:id/arrival/commit` (`commitArrivalCheckIn`) → 204. `UPDATE … SET committed_at = now() WHERE …
  AND committed_at IS NULL RETURNING`; only the call that set it sends the push (R6). Already committed → 204,
  nothing sent. None → 404.
- `POST /dev/date/arrival` (`devPartnerCheckIn`, non-production): the test match stores and commits a check-in
  for the caller's test date (at the venue's point), sending the push — how H is tested on one phone.

**Payload** (`GET /me/date`): `checkedIn` = the viewer has a **committed** row. New `arrival: { checkedInAt } |
null` = the partner's committed row when the viewer's chat read stamp is null or older than its `committed_at`
(R3). Pure, in `toActiveDate` / a new `arrivalOf` in `active-date.ts`.

**Events** (server, `date-events.ts`): `arrival_confirmed { date_id, entry, minutes_vs_start, is_first }`,
`arrival_undone { date_id, entry, seconds_since_confirm }`. `minutes_vs_start` = whole minutes from the latest
agreed meet time to the store, negative before, truncated toward zero (the inverse of `minutesBeforeStart`).
`is_first` = the partner has no row for this date. `seconds_since_confirm` = whole seconds from `checked_in_at`.

**Retention**: queue `arrival-retention`, scheduler `upsertJobScheduler('arrival-retention-daily', { every:
86_400_000 })`, processor deletes rows with `checked_in_at < now() - interval '1 year'` (R14).

**Privacy**: coordinates and accuracy never logged (`accuracy`, `accuracyM`, `accuracy_m` join the prohibited
fields if absent; `latitude` / `longitude` are there), never in an event, never in a push, never in a response.

## 5. Clients (Android, iOS twins with the same names)

**Package `com.showup.arrival`** (Swift: files of the same names in `ios-app/ShowUpWelcome/`):
- `ArrivalCopy` — every string of the ticket's copy section (A, B, C, D, H, the report `note`).
- `ArrivalRules` — `minutesVsStart(meetAt, now)`, `LOCATION_FIX_TIMEOUT_MS = 10_000`, the retry schedule.
- `ArrivalAnalytics` — `arrival_confirm_shown`, `arrival_confirm_submitted` (`date_id`, `entry`, `minutes_vs_start`).
- `LocationFixReader` — one fresh fix, foreground, no fallback: Android `LocationManagerCompat.getCurrentLocation`
  (fused → network → GPS, as `AndroidPlaceReader`); iOS `CLLocationManager.requestLocation()` with best accuracy.
  Returns `LocationFix(latitude, longitude, accuracyM?)` or null (no permission, services off, error).
- `ArrivalRepository` (`ArrivalStore`) — the three routes and the dev route through the generated client.
- `ArrivalGateViewModel` (Swift `ArrivalGateModel`) — `useArrivedGate`: one per date for the session, shared by
  the Date screen and the chat. Phases Idle / Confirm(entry, locError, busy, note) / Done(entry).
- `CheckInBar`, `CheckInConfirmSheet`, `CheckInDoneSheet` (on the shared `ModalShell`; C uses `SheetErrorBox`).

**Values (reference).**
- ① `CheckInBar`: padding 16 16 10, column gap 6; ink 86 % over cream (no backdrop blur on either platform —
  the 193 rule), top corners 22, shadow `0 -6 24 rgba(29,17,41,.22)` cast up. Row centred, gap 14: pin tile 46,
  radius 14, white 12 %, `map-pin` 22 / 2.2 white · text column: `Your date with {name}` Lora 700 18 / 1.15
  white, then the line Manrope 12.5 / 1.4 white, 3 below, pretty · `Check-in` h 46, padding 0 20, pill,
  sunset, Manrope 800 15 white, no wrap, `--liq-shadow-cta`. ② `End date` (switch off): centred, padding 8 12,
  Manrope 600 13 `#E8DCF5`, 44 touch laid over (OverflowTapTarget).
- ③ `CheckInConfirmSheet`: content padding 14 24 24, centred. Circle 80 (8 below the grabber's row) violet
  12 %, `map-pin` 34 / 2.2 violet · title Lora 700 26 / 1.15, −0.01em, ink, 16 above, 22 below, max 310,
  pretty · optional `note` Manrope 14 / 1.5 muted, −10 above, 20 below, max 310 · ④ `SheetErrorBox`, −8 above,
  16 below · `Not yet` h 50, radius 16, white, 1 px soft border, Manrope 700 14.5 muted · primary 10 below, h 56,
  radius 18, sunset + `--liq-shadow-cta`, Manrope 700 16.5 white, `Yes, I've arrived` / `Try again`.
- ⑤ `CheckInDoneSheet`: same shell; circle 80 violet 12 %, `heart-filled` 34 violet · title 16 above, 8 below,
  max 300 · body Manrope 15 / 1.5 muted, max 300, 22 below · `Close` h 56 radius 18 sunset · ⑥ `Undo check-in`
  6 below, min-h 44, padding 0 16, Manrope 600 14 muted, no fill, no border.
- ⑩ arrival card: the unread card's frame in orange — 1.5 orange 22 % border, 4 orange rail, `--liq-shadow-md`,
  body padding 16; avatar 46 + count badge `1` with the pulse ring; name Lora 700 19 / 1.1; tag `ARRIVED` (pad
  2 8, orange 11 %, dot 5, Manrope 800 10, 0.05 tracking); `checked in · {time}` 12.5 muted, 2 below; snippet 13
  below, padding 11 13, radius 14, orange 11 %: `map-pin` 17 / 2.2 orange, gap 9, Manrope 600 14 / 1.4 ink,
  pretty; `Open chat` 14 below, h 48 pill orange + `--liq-shadow-cta`, `message-circle` 18 / 2.2, gap 8, 15 / 700.
- G: `docs/superpowers/specs/kit/2026-11-10-date-gate-kit-excerpt.jsx` — padding 20 24 0, `DateGateCard`
  (padding 14, radius 20, `#E8DCF5`, gap 12; avatar 40 radius 13; Lora 700 19 / 1.2 balanced with the day
  italic violet; clock / pin 17 / 2.2 violet + Lora 700 19 values, gap 14 wrapping; pill h 30 padding 0 12 pill
  orange `Starts in {h}h {m}m` today only, red `Started {n} min ago` + `{name} may be waiting` 13 / 700 after
  the meet time), headline Lora 700 30 / 1.12 −0.015em 26 below with the underlined italic, body Manrope 500
  14.5 / 1.5 neutral-200 12 below, the privacy note 18 below on A only, spacer ≥ 16, the sunset lg CTA with
  `settings` / `map-pin` 18 / 2.2, 22 above the TabBar in date mode. Backdrop `live` on A only.

**Behaviour.**
- Entry points: the bar's `Check-in` → `open(DateScreen)`; the chat chip → `action_chip_used` (Date 02) then, if
  not checked in, `open(ChatChip)` over the chat and nothing selected, drafted or sent; once checked in the chip
  selects with its prefill. `Report` (06) is supported by `open(Report, note)` and never called here.
- `open` fires `arrival_confirm_shown`; `Yes` / `Try again` fire `arrival_confirm_submitted`, then read (10 s
  timeout) → no fix: C, nothing stored · fix: store → D · store failed: C (R5) · 409 already checked in: close,
  checked in.
- D: Close / scrim / back / swipe / app to background → commit (R4, R8), the footer swaps (empty until 06).
  Undo → delete (R8), Check-in bar; from the chat, the host leaves the chat for the Date screen.
- Checked in = the server's `checkedIn` OR this session's commit for the date; the footer, the chip and `open`
  all read it, so a poll in flight never brings the bar back.
- H: `arrival` hoists the orange card under the hero (R11); `Open chat` opens the chat (entry `open_chat`) or,
  locked, clears (R2). A read never brings back an arrival this phone has cleared.
- Location not usable on the Date screen → G instead of the Date screen, TabBar locked; it lifts on its own when
  location returns (the gate's foreground re-read).

## 6. Tracking

Registry 1.4.34 / taxonomy 1.4.27: the repo's files stay the base (they carry `implemented` and the
reconciliation record); taken from the zip: family M's `arrival_confirm_shown` and `arrival_undone` (new),
`arrival_confirm_submitted` and `arrival_confirmed` (respecified), `changed_in_1_4_27`, enums §41 `arrival_entry`
and `changed_in_1_4_34`, the version numbers and `event_count`. The field registry stamp moves to 1.4.34 on the
server and both apps. At the end, `implemented: true` for the four events, as 193/194 marked theirs.

## 7. Verification

- Unit + e2e on the server (`npm run test:cov` holds the util coverage gate); Robolectric on Android; XCTest on
  CI for iOS.
- Fit sweeps: A (bar on the Date screen), B, C, B with a note, D, F, H, G on `DEVICES + EDGE_DEVICES` × every
  font step; rules: 44 touch floor, no overlapping touch areas, whole words, every control on screen; **at 375 ×
  667 default type the guard's buttons and C's error lie fully inside the sheet's visible part**.
- Evidence: A–F and H at 375 × 667, 390 × 844, 430 × 932 per platform (21 each), plus G and A-with-link for the
  audit. Pixel scans against the spec sheet at 2x (callouts ①–⑫).
- `audit/verify-arrival.py`: the copy on both platforms equals the ticket's; no coordinate key in any arrival
  payload; the link switch off; `arrived` shown; no `message_sent` / `date_cancelled` / `screen_viewed` from the gate.

## 8. Owed by others

- Philipp: push copy (R6 interim); R5's proposed string; the date gate's tracking (R1); R2.
- Esma's devices: the recording (Check-in → Yes → Undo → bar → Check-in → Yes → Close, the match's phone showing
  H only after Close); a real iPhone; iOS 17.
