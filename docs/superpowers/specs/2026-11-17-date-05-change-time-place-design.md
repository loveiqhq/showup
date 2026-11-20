# Date 05 — change meet time and place: design

SHOWUP-196, Jira status In Progress (Esma, 17 Nov 2026). Parent epic SHOWUP-192 (07a Match & date confirmation).
No Jira comments.

## 1. Sources and authority

- **Ticket (behaviour, scope, copy wins):** the Jira description, copied verbatim to
  `design_handoff_showup/date/tickets/05-change-time-place.md`. It is identical in the 10 Nov and 16 Nov handoffs.
- **Numbers:** `design_handoff_showup/date/screen-change-reference.jsx`. The ticket's order of authority is the
  reference on numbers, the ticket on behaviour, and the PNG on nothing. Shell components live in
  `components/shared.jsx`; tokens in `tokens/colors_and_type.css`.
- **The 16 Nov rules that touch this ticket** (in the repo's Date 01/02/03/epic copies):
  - a committed arrival check-in opens the chat for both and it never re-locks (195 R16);
  - all push copy is written at the end of the project, so tickets build triggers with placeholder strings.
- **Philipp, 17 Nov:** notifications later; impact on other flows (e.g. the Tutorial) later.
- Downloads: `date-05-change-time-place-spec-sheet.html/.png`, `Show Up Design System (2).zip`. The zip is the
  10 Nov export; its date files are older than the repo's 16 Nov copies, which stay.

## 2. Scope

**Built:**
- Frames A–N on Android and iOS.
- The server:
  - slots;
  - a Places proxy;
  - the suggestion store and its state machine, with one-per-kind replacement;
  - agree, with the binding update and its reschedules;
  - the expiry job and deadline recompute;
  - system lines, payload fields and pushes;
  - tracking (5 NEW events) and dev routes.
- Un-hiding the locked card's two tiles and the two change chips.

**Not built here:** the chat, thread and dock (Date 02); the Date screen and time + place card (Date 01); the
Cancel sheet's entry (Date 07); final push copy; the +2 h switch (Date 09 — it reads `scheduled_at`, which an agreed
time moves); Tutorial or notification-settings changes.

## 3. Rulings (my calls; each names what it rests on and what it costs if wrong)

- **R1 — Places provider behind an interface.** `PlacesProvider { nearby(center), search(center, q), details(placeId) }`.
  - **`GooglePlacesProvider`** (Places API (New), server-side key `GOOGLE_PLACES_API_KEY`):
    - `places:searchNearby` with `includedTypes [cafe, bar, restaurant]`, `rankPreference DISTANCE`, a 2 km circle;
    - `places:searchText` with a 5 km `locationBias`, results filtered to those three types;
    - `places/{id}` for details;
    - field masks: id, displayName, shortFormattedAddress, location, primaryType, types; at most 20 results.
  - **`FixturePlacesProvider`** is used when no key is set outside production: the kit's five Berlin venues around
    Cafe Krone, with real coordinates.
  - **Production with no key:** the routes answer 503, which is frame M.
  - Rests on the ticket ("Google Places — may be swapped"; "server-side key"). If wrong, one provider class is swapped.
  - A key is needed for real data; owed by Esma / Philipp.
- **R2 — Google attribution.** Google's policy for Places results without a map asks for the logo or, where space is
  limited, the text **Google Maps**: one line, not localised, sans-serif 400, 12–16, gray `#5E5E5E`, near the top or
  bottom of the results in the same container.
  - Built as the text `Google Maps`, Manrope 400 12, `#5E5E5E`, under the list ④ inside the scroll, 12 above.
  - `#5E5E5E` is a third-party brand colour, the house rule's allowed literal; the call site says so.
  - Shown only when the server says `attribution: "google"` (never for fixtures).
  - The ticket says this overrides the sheet for that property only.
- **R3 — Distance.** The server returns `distanceMeters` from the **binding** meet place; the clients format it.
  - Under 1000: rounded to 50, `350 m`. From 1000: one decimal, `1.1 km` (the kit's values).
  - One pure function per platform, tested. Never in analytics.
- **R4 — Place type.** The server maps Google types to `cafe | bar | restaurant` (primaryType first, then types,
  in that order). The clients label them `Café`, `Bar`, `Restaurant`; the row tag is uppercase as the kit draws it,
  and the card and request card read `{Type} · {address}`.
- **R5 — Google caching versus the ticket's evidence snapshot.** Built as the ticket says: the place id plus a
  name / address / type snapshot per suggestion, and an agreed place becomes a `venues` row with coordinates.
  - Google permits place ids indefinitely, coordinates for 30 days, and names and addresses only "as expressly
    permitted". Owed by Philipp / legal, recorded in the PR. Not built: a purge.
- **R6 — Slots.**
  - Server-computed from the **match's** availability (the `check_ins` runs of their latest committed window)
    on the date's local day. The time zone is the client's IANA `tz`, as `GET /me/date` already takes it.
  - Slots are 15-minute starts `s` with `binding < s`, `run.start ≤ s ≤ run.end − 30 min`, and `s` before local
    midnight; the union of runs, ascending. `{latest}` is the last.
  - The list rides in the `GET /me/date` payload (`change.slots`), so the time picker makes no request and has no
    load or error state.
- **R7 — When the tiles and chips hide (one check, both people).**
  - The payload carries `change.cutoffAt` (binding − 0:30), `change.allowed` (false once either person has a
    committed check-in) and the slot list (empty hides `Change meet time` only).
  - The clients also hide from `cutoffAt` by their clock tick, in place. The server refuses a suggestion past
    the cutoff or after a check-in (409), so a stale screen cannot sneak one in.
- **R8 — The hoisted request card.**
  - The server returns `changeRequest`: the match's pending suggestion with the earliest deadline, time first
    on a tie.
  - It outranks an unread message, and the arrival card outranks it (195 R11's order: arrival → request →
    message).
  - It stays until agreed, replaced or expired; a chat read never clears it.
  - `date_screen_viewed.has_unread` is true while it shows (the ticket).
- **R9 — System lines are derived, not stored.** The chat thread returns each suggestion with its history.
  - Agreed → `Meet time is now {time}` / `Meeting place is now {venue}` at `answered_at`.
  - Expired → `No answer by {deadline} — {time} at {place} stays.` at `expired_at`, with the binding values at
    that moment, snapshotted on the row when it expires.
  - The copy lives in the clients (the verifiers quote it). Rests on "the server posts …": the server owns
    whether and when; one source.
- **R10 — Pushes with temporary copy (all notification copy comes later — Philipp, 17 Nov).**
  - Types `date_change_suggested` (to the receiver), `date_change_agreed` (to the sender) and
    `date_change_expired` (to both). Data `{ type, dateId }`; never a place. Category `engagement`; EN + DE.
  - Strings: `{name} suggested a change` / `Answer in Show Up before {deadline}.`;
    `{name} agreed to your change` / `It's now binding for you both.`;
    `Your change wasn't agreed` / `The original time and place stay.`
- **R11 — Agreeing a time.**
  - Sets `dates.scheduled_at`; recomputes an open place suggestion's deadline to the new time − 0:30; reschedules
    the date-reminder push.
  - If the chat was open by the **old** time and is still unstamped, it stamps `coordinate_unlocked_at` and fires
    `coordinate_unlocked {cause: time}`. That is what the job was about to do, and without it the later time
    would re-lock the chat.
  - A locked chat follows the new meet − 1:30 on its own (unlock is derived from `scheduled_at`).
- **R12 — Agreeing a place.** The venue is upserted by `place_id` from the suggestion's snapshot, and
  `dates.venue_id` is set. The migration adds `venues.place_id` (unique where not null) and `venues.place_type`,
  and drops the `venues.name` unique constraint (two Google places can share a name). The dev venue code follows.
- **R13 — `Changed · was {original}`.** The original is the `was` snapshot of the **first** agreed suggestion of
  that kind; the payload carries `change.timeWas` / `change.placeWas`. The clients hide it after `meetAt`.
- **R14 — Idempotent suggest.** A `clientId` (uuid) per Suggest tap, as Date 02 J; K's retry resends the same id
  and the server answers with the stored row (no second event). `Suggest another` and a new picker are new ids.
- **R15 — Races.**
  - Suggest runs in a transaction that locks the date row (`SELECT … FOR UPDATE`): the later of two same-kind
    suggestions replaces the earlier.
  - `I agree` on a non-pending suggestion is 409; the client re-reads and the card shows its real state, with no
    toast and no event.
  - A partial unique index `(date_id, kind) WHERE status = 'pending'` backs the one-open-per-kind rule.
- **R16 — The picker is a pushed full-screen destination.** Android: a Navigation Compose route; iOS: a
  NavigationStack destination.
  - It uses the chat / profile push and pop transitions and the system back gesture, and returns where it was
    opened.
  - The kit has no motion of its own; this keeps one way of going full screen in the app.
  - The footer fill is the kit's `rgba(255,251,247,.94)` **without** backdrop blur, the 193 rule for pinned
    bars (at 94 % the blur is invisible anyway).
- **R17 — Waiting states.**
  - While Suggest or I agree is in flight, both buttons are inert and the primary sits at alpha .6, as the 194
    End date and 195 guard waits do.
  - While the nearby list loads, the list area stays empty and the search field is usable, with an
    accessibility "Loading places" description. No new visible string or skeleton; adding one needs Esma's OK.
- **R18 — Search.** Debounced 300 ms on the trimmed query. A non-empty query shows `Results` and searches; an
  empty one shows `Nearby public places`. One selection survives across list changes only while the selected
  place is still listed.
- **R19 — Times shown.** Every `{time}`, `{deadline}`, `{latest}` and slot is local `HH:mm`, from the server's
  instants in the date's zone (as `meetTime` does now). The thread card's timestamp and the request card's
  `needs your answer · {time}` use the unread card's formatter.
- **R20 — Entry and surface.**
  - The client sends `entry` (§42) with Suggest and `surface` (`coordinate_card | thread`) with I agree. The
    server sends them on `change_suggested` / `change_agreed`, with `from_search` for places.
  - `chat_opened.entry = read_reply` for Open chat on the request card.
- **R21 — Chips in the grid.** The two change chips take their places in Date 02's reason order when allowed.
  The start grid and dock fire `action_chip_used` (`entry` / `dock`), then `change_picker_opened`.
- **R22 — `I agree` on the card** reads the same server route as the thread's; both post the same system line.

## 4. Server

- **Migration `1717000036000-DateChangeSuggestions`.**
  - Enums `change_kind (time, place)`, `change_status (pending, agreed, replaced, expired)` and `change_entry`
    (the §42 values incl. `cancel_sheet` for Date 07).
  - Table `date_change_suggestions` with these columns:
    - `id`; `date_id` (→ dates, on delete cascade); `sender_id` (→ users, cascade); `kind`;
    - `value_time timestamptz null`; `place_id`, `place_name`, `place_type`, `place_address` (nullable);
      `place_location geography(Point) null`;
    - `was_time timestamptz not null`; `was_venue_id uuid null`; `was_place_name`;
    - `status`; `deadline timestamptz not null`; `entry`; `from_search bool null`; `client_id uuid not null`;
    - `expired_binding_time`, `expired_binding_place_name` (both null);
    - `created_at`, `answered_at`, `replaced_at`, `expired_at`.
  - Indexes: unique `(sender_id, client_id)`; partial unique `(date_id, kind) WHERE status = 'pending'`; an index
    on `(status, deadline)`.
  - Venues: add `place_id` (unique where not null) and `place_type`; drop the name unique constraint.
- **Routes** (participant only; 404 unknown, 403 not yours):
  - `GET /dates/:id/change/places?q=` → `{ attribution, places: [{ placeId, name, type, address, distanceMeters }] }`;
    503 when the provider fails or is missing.
  - `POST /dates/:id/suggestions` with body `{ kind, time? | placeId?, entry, fromSearch?, clientId }`.
    Answers 201 with the suggestion; 409 past the cutoff, after a check-in, for a slot not in the list, or for the
    current place; 400 for a non-public place.
  - `POST /dates/:id/suggestions/:sid/agree` with body `{ surface }` → 200; 409 if not pending or not the receiver.
  - The chat GET gains `suggestions` (with status history and system-line inputs).
  - Dev routes, non-production only:
    - `POST /dev/date/change` with body `{ kind, minutesLater? }`: the match suggests;
    - `POST /dev/date/change/agree`: the match agrees to the caller's pending one;
    - `POST /dev/date/change/expire`: deadlines → now, then runs the job.
- **Payload** (`GET /me/date`):
  - `change: { cutoffAt, allowed, slots[], timeWas?, placeWas?, pending: [{ id, kind, byYou, value, was,
    deadline, createdAt }] }`;
  - `changeRequest` (R8) or null.
  - New fields are optional / nullable in the contract, so the apps' JSON fixtures keep decoding.
- **Jobs:** `change-expiry` every minute (the coordinate-unlock scheduler pattern). It runs a conditional UPDATE
  `… status='pending' AND deadline <= now RETURNING`, then the event and push per row.
- **Events** (on the accepted write only):
  - `change_suggested {date_id, suggestion_id, kind, entry, replaces, offset_min, from_search,
    minutes_to_deadline, minutes_before_start}`;
  - `change_agreed {date_id, suggestion_id, kind, surface, seconds_since_suggested, minutes_to_deadline}`;
  - `change_expired {date_id, suggestion_id, kind}`;
  - never a place name, address, coordinate or distance;
  - the prohibited-fields lists gain nothing new (no new field names carry personal data).

## 5. Clients (Android and iOS twins, same names)

- **Package `change`.**
  - Copy: `ChangeCopy` (every ticket string).
  - Rules: `ChangeRules` (formatDistance, placeTypeLabel, chips/tiles visibility, the request-card text helpers).
  - Data: `ChangeRepository` (places, suggest, agree through the generated client); `ChangePickerViewModel`
    (Swift `ChangePickerModel`).
  - UI: `ChangeFullSheet`, `TimeChangeSheet`, `PlaceChangeSheet`, `ChangeSuggestionCard`, `ChangeTag`,
    `BindingNote`, `ChangeError` (built on `SheetErrorBox`), `ChangeRetryLine` (built on Date 02's red line).
  - The `request` variant of `CoordinateModule`.
- **Wiring:** the picker route; the tiles (`CHANGE_TILES`) and chips (`ChatReason.shown`) gated by R7; the thread
  items (cards + system lines interleaved by time); the purple card's ChangeTag / BindingNote; the request card
  with I agree, Open chat / Suggest another and L; the chat header's binding values.
- **Analytics:** `change_picker_opened` / `change_picker_dismissed` (client), on top of the existing
  `action_chip_used` and `chat_opened`.
- **Previews, the States page (196 A–N), the fit sweep and the evidence harness** for every frame.

## 6. Values

Read from the reference (it wins on numbers). Conversions follow `css-fidelity-rules`:
- numeric letterSpacing is px;
- CSS line boxes;
- borders count in the box;
- `rgba` fills as given;
- shadows through the existing token conversions;
- the sunset gradient as `cssLinearGradient(135f)`.

Kit primitives map to existing ones where they exist: Card, Avatar, Icon, ReasonBadge, SystemLine (new if absent),
PrimaryButton.

## 7. Verification

- **Server:** unit specs for slots, the state machine, the deadline recompute, payload and events; e2e for
  suggest → agree / expire / replace / races / cutoff / check-in; `npm run test:cov`.
- **Clients:** unit tests for rules and VMs, a Robolectric / XCTest sheet test per frame, and the fit sweep.
  - Fit covers every frame at every size and font step: the 44 touch floor, whole words, no overlap, and the
    picker footer plus both request-card buttons fully visible at 375 × 667.
- **Evidence:** A–N at 375 × 667, 390 × 844 and 430 × 932 per platform (42 each).
- **`audit/verify-change.py`:**
  - every ticket string on both platforms;
  - no place name, address, coordinate or distance in any change payload;
  - no `message_sent` for a suggestion;
  - the Google attribution text exact;
  - the §42 values.

## 8. Owed by others

- **Philipp:** push copy (R10 temporary); the Google caching decision (R5); a Places API key for real data (R1).
- **Devices:** the ticket's two recordings on two phones (time agreed on the request card; a place left to
  expire), a real iPhone, and TalkBack / VoiceOver on the pickers.
