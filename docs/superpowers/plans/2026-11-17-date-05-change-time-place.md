# Date 05 · change meet time and place Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build SHOWUP-196 (frames A–N) on the server, Android and iOS: time and place pickers, suggestions in the
thread, the orange request card, agree, expiry, the four failure states, the server's slots / Places proxy /
suggestion state machine / expiry job, and the tracking.

**Architecture:**
- The server owns every rule: slots, cutoff, check-in hiding, replacement, deadlines, expiry, system-line inputs and
  the request card choice. It ships them in `GET /me/date` (`change`, `changeRequest`) and the chat GET
  (`suggestions`).
- The apps render values and send intents (suggest, agree) through the generated client.
- Places sit behind a provider interface: Google Places (New) with a server-side key, and a fixture provider for
  dev / test.

**Tech Stack:** NestJS + TypeORM + Postgres/PostGIS + BullMQ (Jest unit + e2e); Kotlin / Jetpack Compose (Robolectric);
Swift / SwiftUI (XCTest on CI macos-15 only, no Mac here); OpenAPI-generated clients.

**Spec:** `docs/superpowers/specs/2026-11-17-date-05-change-time-place-design.md` (rulings R1–R22, server and client
shapes). Ticket: `design_handoff_showup/date/tickets/05-change-time-place.md`. Numbers:
`design_handoff_showup/date/screen-change-reference.jsx`.

## Global Constraints

- **Branch and commits:**
  - Branch `feat/date-05-change-time-place` (from development 622ed1b; tracks its origin branch). Push after every
    commit.
  - Never commit `.superpowers/` or `.semgrep/`.
  - Both apps change in the same commit for any screen or rule.
  - Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. No emoji.
- **Copy is the ticket's, exactly** (its *Copy* section). New visible strings only with Esma's OK; the push strings
  in R10 are the only additions.
- **Deadline:** binding meet time − 30 min, always from the BINDING time. Slots are 15-min steps, later the same
  local day, inside the match's availability, the last at availability end − 30 min.
- **Hidden for both:** from binding − 30 min, after either person's committed check-in, and `Change meet time` also
  when there are no slots. An open suggestion stays answerable until its own deadline.
- **Privacy:** never a place name, address, coordinates or distance in analytics, push data or logs.
- **Registry:** stamp stays `1.4.38`; events.json taxonomy is the Date 05 slice of 1.4.28 (the five `change_*`
  events).
- **Contract:** new payload fields are optional/nullable, so the apps' JSON fixtures keep decoding. Regenerate both
  openapi copies (`openapi.json`, `ios-app/ShowUpAPI/Sources/ShowUpAPI/openapi.json`); run the welcome and api
  packages' tests and `npm run test:cov` before pushing a contract change.
- **House rules:** 44 touch floor on both platforms; tokens before literals (the Google gray `#5E5E5E` is the one
  allowed brand literal, with its reason); previews at 320 × 686 and 430 × 932; every frame in the fit sweep.
- **Running the builds:**
  - Android: `JAVA_HOME=C:/Users/krnji/.jdks/jbr-21.0.11`; narrow `--tests` filters only, then `./gradlew --stop`;
    the evidence harness runs only with `-Pevidence` in its own invocation. Full runs get reaped for memory; CI
    does them.
  - iOS: not runnable here. Run `audit/check-swift-*.py` and `check-pbxproj.py`; report iOS as NOT RUN.
  - After an enum's associated-value type changes, grep `case .<name>(` patterns and the TESTS for the old outcome
    (two 195 CI failures came from exactly this).
- Subagents never dispatch subagents.

## Review Focus

1. **Deadlines and time:** a time agreed while a place suggestion is open moves that place's deadline. An
   agreed later time must never re-lock an open chat (R11). The expiry job must skip replaced rows.
2. **Payload consistency:** the receiver's purple card must never show the match's suggestion (only the orange card
   and thread do). The sender's pending suggestion shows small under the binding value, with exactly one compact
   binding note even when a time AND a place are pending.
3. **Failures:** K keeps the picker open with the selection, and a retry with the same clientId never stores twice.
   L changes nothing anywhere until the server confirms. M keeps the search field usable. N is not an error.
4. **Fit at 375 × 667:** the picker footer (note + two buttons) is fully visible; the last slot or place scrolls
   clear of it (bottom padding 280); both request-card buttons are fully visible.
5. **Hiding:** the tiles and chips disappear in place at the cutoff while the screen is open, and after either
   person's check-in, on BOTH phones.

---

### Task 1: Tracking — the Date 05 slice (taxonomy 1.4.28)

**Files:**
- Modify: `design_handoff_showup/tracking/events.json` (the repo file stays the base).
- Source (read-only): `C:/Users/krnji/AppData/Local/Temp/claude/C--Users-krnji-showup/33f1e072-9d7d-4446-a930-54c75a306b73/scratchpad/195v2/zip-new/design_handoff_showup/tracking/events.json`.

**Steps:**
- [ ] Add the five events `change_picker_opened`, `change_picker_dismissed`, `change_suggested`, `change_agreed`
  and `change_expired` to family K, copied from the source, with `implemented: false` until Task 7.
- [ ] Add `changed_in_1_4_28` (the source's lines), and update `merge_note_1_4_31` to say 1.4.28's Date 05 slice is
  merged while 1.4.29–1.4.30 (Date 06/07) are not.
- [ ] Update `event_count` and family counts where the file keeps them.
- [ ] Check that enums.json §42 `change_entry` is already present (registry 1.4.38).
- [ ] Run `python -I audit/verify-profile.py` and `check-analytics-parity.py` → all pass.
- [ ] Commit `chore(tracking): the Date 05 events, taxonomy 1.4.28 slice (SHOWUP-196)`; push.

### Task 2: Server foundations — schema, Places provider, slots

**Files:**
- Create:
  - `src/database/migrations/1717000036000-DateChangeSuggestions.ts`;
  - `src/modules/dates/entities/date-change-suggestion.entity.ts`;
  - `src/modules/places/` (module, `places.provider.ts` interface + token, `google-places.provider.ts`,
    `fixture-places.provider.ts`, `places.service.ts`);
  - `src/modules/dates/util/change-slots.ts` (+ spec);
  - `src/modules/dates/util/change-rules.ts` (`changeDeadline(bindingAt)`, `cutoffPassed`, type mapping, + spec).
- Modify: `src/config/env.validation.ts` (`GOOGLE_PLACES_API_KEY` optional), `.env.example`, the venues dev / seed
  code (R12), DatesController (the places route).

**Interfaces:**
- Produces:
  - `changeSlots(bindingAt: Date, runs: {start: Date; end: Date}[], tz: string): Date[]` (R6);
  - `changeDeadline(bindingAt: Date): Date` (− 30 min);
  - `PlacesProvider` with `nearby(center: LatLng): Promise<Place[]>`, `search(center: LatLng, q: string):
    Promise<Place[]>` and `details(placeId: string): Promise<Place | null>`, where
    `Place = { placeId, name, type: 'cafe'|'bar'|'restaurant', address, location: LatLng }`;
  - `PlacesService.listFor(dateId, userId, q?) → { attribution: 'google'|null, places: (Place & { distanceMeters })[] }`,
    sorted by distance from the binding venue;
  - `GET /dates/:id/change/places?q=`, answering 503 on provider failure or a missing key in production.
- Values: the Google request shapes and field masks in R1; fixtures = the kit's `CHANGE_PLACES` with real Kreuzberg
  coordinates.

**Steps:**
- [ ] Write failing tests:
  - slots for the kit's case: binding 19:00, availability until 22:00 → `19:15 … 21:30`;
  - none past local midnight; none outside the runs; empty when the binding is already at the last slot;
  - deadline 19:00 → 18:30;
  - type mapping;
  - the places route: distance-sorted, only three types, 503 path;
  - the Google provider against a mocked fetch: request bodies, the field mask header, filtering out non-venue
    types / street addresses.
- [ ] Implement:
  - the migration (with `down`), the entities, the provider binding (Google when the key is set, fixture otherwise
    outside production, missing-key error in production), and the route;
  - the venues name-unique drop with `place_id` unique.
- [ ] Run `npm run test:cov`, the relevant e2e and `npm run lint` → PASS.
- [ ] Apply the migration to the e2e DB only. Never touch the dev DB the user's backend on :3000 uses.
- [ ] Commit `feat(dates): Date 05 foundations -- suggestions schema, Places provider, slots (SHOWUP-196)`; push.

### Task 3: Server — suggest, agree, expire, payload, thread, pushes, dev routes

**Files:**
- Create: `src/modules/dates/change-suggestions.service.ts` (+ spec); `src/modules/dates/dto/change.dto.ts`;
  `src/modules/dates/jobs/change-expiry.{scheduler,processor}.ts`.
- Modify: DatesController (suggest, agree); `date-screen` payload (`change`, `changeRequest`); the chat GET
  (`suggestions`); `analytics/events/date-events.ts` (three builders); the notifications catalog + `NotificationType`
  + categories (R10); the date-reminder rescheduling (R11); the dev controller / service (R-dev routes);
  `queue.constants.ts`; both openapi copies; `test/change.e2e-spec.ts`.

**Interfaces:**
- Produces (contract; all new response fields optional/nullable):
  - `POST /dates/:id/suggestions` — body `{ kind: 'time'|'place', time?: ISO, placeId?: string, entry: §42,
    fromSearch?: boolean, clientId: uuid }`;
  - `POST /dates/:id/suggestions/:sid/agree` — body `{ surface: 'coordinate_card'|'thread' }`;
  - payload `change: { cutoffAt, allowed, slots: ISO[], timeWas?: ISO, placeWas?: {name,address}, pending: [{ id,
    kind, byYou, time?, place?: {placeId,name,type,address}, wasTime, wasPlace: {name}, deadline, createdAt }] }`;
  - `changeRequest: <the pending item> | null`;
  - chat GET `suggestions: [{ id, kind, byYou, time?, place?, wasTime, wasPlace, status, deadline, createdAt,
    answeredAt?, expiredAt?, expiredBindingTime?, expiredBindingPlace? }]`.
- Rules (R7–R15): see the spec; every one is unit-tested and the main paths are covered e2e.

**Steps:**
- [ ] Write failing tests:
  - suggest:
    - stores a suggestion; the binding is unchanged;
    - replaces the open same-kind one whoever sent it (`replaces` none / own / theirs);
    - refuses past the cutoff, after a check-in, an unlisted slot, or the current place;
    - is idempotent on clientId;
  - agree:
    - a time moves `scheduled_at`, recomputes the open place deadline, reschedules the reminder and stamps an
      open-by-old-time chat with `cause: time`;
    - a place upserts the venue and moves `venue_id`;
    - refuses non-pending (409) and the sender;
  - expiry: the job expires only pending rows past the deadline, snapshots the binding, and fires one event + the
    pushes;
  - payload: request choice (earliest deadline, time first on a tie), `pending` / `timeWas` / `placeWas`,
    `allowed`, `cutoffAt`, `slots`;
  - events: exact properties, no place fields;
  - e2e: suggest → agree on the card; suggest → expire; replace; the race (agree after replace = 409).
- [ ] Implement it.
- [ ] Regenerate both openapi copies.
- [ ] Dev routes:
  - `POST /dev/date/change { kind, minutesLater? }`: the test match suggests;
  - `POST /dev/date/change/agree`: the match agrees to the caller's pending suggestion;
  - `POST /dev/date/change/expire`: deadlines → now, then runs the job.
- [ ] Run `npm run test:cov`, `npm run test:e2e -- change`, the arrival / date-screen / date-chat e2e, and
  `npm run lint` → PASS.
- [ ] Commit `feat(dates): Date 05 suggest / agree / expire, payload and thread (SHOWUP-196)`; push.

### Task 4: Clients — data layer and the pickers (A, B, K, M, N), both platforms

**Files:**
- Create (Android `com.showup.change`; iOS twins in `ShowUpWelcome/`): `ChangeCopy`, `ChangeRules`,
  `ChangeRepository`, `ChangePickerViewModel` / `ChangePickerModel`, `ChangeSheets` (`ChangeFullSheet`,
  `TimeChangeSheet`, `PlaceChangeSheet`, `BindingNote`, `ChangeError`).
- Modify: the generated-client model mapping (`ActiveDate` gains `change`, `changeRequest`); `ChatReasons.shown` for
  the two change reasons; `CHANGE_TILES` → gated by R7; the host routes (Android `AppRoute.ChangePicker(kind, entry)`,
  iOS NavigationStack destination); `DateViewModel.changeTile` and `ChatViewModel.pick` → open the picker.
- Tests: the VM / model (load nearby, debounce 300, search, select, suggest OK → close, suggest fail → K kept
  selection, retry same clientId, cancel → `change_picker_dismissed{had_selection}`); rules (formatDistance,
  visibility at the cutoff / check-in / no slots); a sheet test per frame (A, B, K, M, N); fixtures updated for the
  new payload fields.

**Steps:**
- [ ] Write failing tests on Android first, then the Swift twins.
- [ ] Implement both platforms, with values from `screen-change-reference.jsx`:
  - the shell: 62 top spacer, list padding `0 20 280`, footer padding `14 20 30`, gap 10, fill
    `rgba(255,251,247,.94)` with no blur, top border soft, shadow `0 -10px 28px rgba(29,17,41,.08)`;
  - the head: Lora 700 24 / 1.15, −0.01em, the violet-tint info box `12 14`, radius 14;
  - slots: a 4-column grid, gap 6, h 44, r 12;
  - the search field: h 48, r 14, 1.5 border;
  - rows: `11 12`, r 16, icon tile 40 r 12, radio 22;
  - the primary button: h 56, r 18, sunset, alpha .45 when disabled; the secondary: h 50, r 16;
  - `Google Maps` attribution per R2;
  - analytics: `action_chip_used` then `change_picker_opened` from chips and tiles; `change_picker_opened` only
    from Suggest another (Task 5/6 call sites); `change_picker_dismissed` on Cancel or back.
- [ ] Run Android `--tests '*Change*' --tests '*Chat*' --tests '*Date*'`, then `--stop`; the Swift checkers; all
  audits.
- [ ] Commit `feat(change): the time and place pickers with their failure states, both apps (SHOWUP-196)`; push.

### Task 5: Clients — the thread (C, F, H, L in the thread), both platforms

**Files:**
- Create: `ChangeSuggestionCard`, `ChangeRetryLine`, `SystemLine` (if Date 02 has none).
- Modify: ChatRepository / ChatViewModel / ChatModel (suggestions interleaved with messages by time; system lines
  from `answeredAt` / `expiredAt` per R9); ChatScreen thread; the chat header reads the binding values (already
  `meetAt` / `place`, which the server moves on agree).

**Steps:**
- [ ] Write failing tests:
  - the thread order;
  - mine right / theirs left;
  - each status's footer: pending-mine waiting line + compact note; pending-theirs `Answer by` box + Suggest another
    / I agree; Agreed · {time}; Replaced; Expired;
  - the system line copy for agreed time, agreed place and expired;
  - I agree in the thread → agree(surface thread) → re-read; failure → the red retry line (L), nothing else changes;
    a 409 → re-read, no event;
  - Suggest another → the picker with `entry counter_thread` and `change_picker_opened` only;
  - no `message_sent` for a suggestion.
- [ ] Implement both platforms with the reference values:
  - the card: width 84 %, r 20, 1.5 violet 22 % border, shadow sm; the header Manrope 800 11 uppercase violet with
    a 13 icon; the value Lora 700 24 / 1.1; was 13 struck subtle; opacity .55 for replaced / expired;
  - the deadline box: amber 9 % r 12; the buttons h 44 pill;
  - the footer timestamp: 11 / 600 subtle, top border.
- [ ] Run the commands as in Task 4.
- [ ] Commit `feat(change): suggestion cards and system lines in the thread, both apps (SHOWUP-196)`; push.

### Task 6: Clients — the Date screen (D, E, G, I, J, L on the card), both platforms

**Files:**
- Modify: `DateCards` (TimePlaceCard: `ChangeTag` under the time / place for the viewer's own pending suggestion
  or an agreed change; one compact `BindingNote` at the card's bottom while the viewer has any pending;
  `CoordinateModule` request variant); `DateScreen` (hoisting: arrival → request → message; `has_unread`);
  `DateRules`; the locked card tiles (I); the host wiring (I agree on the card → agree(surface coordinate_card);
  Open chat → `chat_opened{entry read_reply}`; Suggest another → the picker `counter_card`; L retry line); the dev
  menu date actions (match suggests time / place, match agrees, expire now) on both platforms.

**Steps:**
- [ ] Write failing tests:
  - D: two own pending → two waiting tags + ONE compact note; the binding values stay big;
  - E / J: the request card copy and button order (Open chat / Suggest another first, I agree second), `Messaging
    opens at {opensAt}` only when locked;
  - G: the new value big + `Changed · was {original}` (original only) hidden after `meetAt`;
  - I: the tiles visible and opening the pickers;
  - hidden at the cutoff, after a check-in, and the time tile when no slots;
  - L on the card;
  - the hoisting order;
  - `has_unread` true with a request.
- [ ] Implement both platforms with the reference values:
  - the request card: rail 4 orange, padding 16, avatar 46 + count 1 + pulse; tag `Change request`; `needs your
    answer · {time}`; the ReasonBadge; the value Lora 700 22; the amber deadline box; buttons h 48 pill gap 8;
  - ChangeTag: the pending amber box `9 11` r 12, 13.5 / 700 + 12.5 / 500; the agreed refresh 12 + `Changed · was`
    12.5;
  - BindingNote: compact `9 11`, 12 / 1.45, amber 9 % fill, 28 % border.
- [ ] Run the commands as in Task 4.
- [ ] Commit `feat(change): the request card, the purple card's change tags and the tiles, both apps (SHOWUP-196)`;
  push.

### Task 7: Frames, fit, evidence, audit

**Files:**
- `ChangeSamples` + `ChangePreviews` (Android) and the Swift twin previews.
- The States page entries 196 A–N on both platforms (+ DevStatesTest / DevMenuRulesTests counts).
- `ChangeFitTest` / `ChangeFitTests` (every frame × `DEVICES + EDGE_DEVICES` × font steps; the 375 × 667 footer and
  card-button checks).
- The evidence harness: A–N at 375 × 667, 390 × 844 and 430 × 932 on both platforms.
- `mobile/welcome-screen/audit/verify-change.py` (the ticket's Copy section on both platforms; the attribution
  text; §42 values; no place keys in change payloads; no `message_sent` / `message_received` for a suggestion).
- events.json `implemented: true` for the five events.

**Steps:**
- [ ] Build it all.
- [ ] Run Android `--tests '*Change*' --tests '*DevStates*'` and the evidence in its own `-Pevidence` run; the Swift
  checkers; all audits.
- [ ] Commit `test(change): Date 05 frames A-N in previews, States, fit and evidence; verify-change (SHOWUP-196)`;
  push.

### Task 8: Final review, PR (controller)

- [ ] Final whole-branch review (Sonnet).
- [ ] One fix round.
- [ ] Update the design doc with any rulings changed.
- [ ] Open the PR to development, with the CI evidence and the owed list.
