# Date 03 — Arrival check-in (SHOWUP-195) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The arrival check-in — Check-in bar, guard, no-fix retry, done + Undo, the chat chip's gate, what the
match sees, and the location gate over the Date screen (frames A–H) — on Android and iOS, with the server it needs.

**Architecture:** The server adds one table (`date_arrival_check_ins`) with create / undo / commit routes on the
dates controller, notifies the match on commit, and adds `arrival` and a real `checkedIn` to `GET /me/date`. Both
apps get one gate model per date (`useArrivedGate`), shared by the Date screen and the chat, three components
(`CheckInBar`, `CheckInConfirmSheet`, `CheckInDoneSheet`), the arrival variant of the existing `CoordinateModule`,
and the date context of the existing location screen.

**Tech Stack:** NestJS / TypeORM / Postgres + PostGIS, BullMQ; Kotlin + Jetpack Compose (Robolectric); Swift +
SwiftUI (XCTest on CI, macos-15); both API clients generated from `openapi.json`.

**Spec:** `docs/superpowers/specs/2026-11-10-date-03-arrival-checkin-design.md` (rulings R1–R15, value tables §5)
· Ticket SHOWUP-195 (Jira description; no comments) · Reference `design_handoff_showup/date/screen-checkin-reference.jsx`
· G: `docs/superpowers/specs/kit/2026-11-10-date-gate-kit-excerpt.jsx`.

## Global Constraints

- **Order of authority:** reference wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.
- **Copy, exact** (`{name}` = the match's first name):
  - A: `Your date with {name}` · `Confirm you arrived at the meeting location, we will let your date know.` · `Check-in` · `End date` (hidden)
  - B: `Have you arrived at the meeting location for your date with {name}?` · `Not yet` · `Yes, I've arrived`
  - C: `We couldn't find your location. Check your signal, then try again.` · `Not yet` · `Try again`
  - D: `You are amazing for showing up!` · `We will let {name} know that you have arrived. Enjoy your date.` · `Close` · `Undo check-in`
  - H: `{name}` · `Arrived` (drawn uppercase) · `checked in · {time}` · `{name} has arrived at the meeting location.` · `Open chat`
  - Report note (built, unused): `Check in first, so we can confirm you were there.`
  - G (kit `LOCATION_COPY.date`): A `Urgent! Allow location to get access to your date information.` / `Your date screen needs your location. It gets you to the meeting place and lets you check in when you arrive, so {name} knows you're there.` / `Allow location access`; B `Urgent! Turn on location to get access to your date information.` / `Location is off for Show Up, so your date screen is locked. Turn it on to see your date, get directions and check in when you arrive, so {name} knows you're there.` / `Open Settings`; C same headline / `Location is off for every app on this phone, so your date screen is locked. Turn it on to see your date, get directions and check in when you arrive, so {name} knows you're there.` / `Open Settings`; pill `Starts in {h}h {m}m` · `Started {n} min ago` · `{name} may be waiting`. `date information` is the underlined italic.
  - Push (interim, R6): EN `{name} has arrived` / `{name} has arrived at the meeting location.`; DE `{name} ist da` / `{name} ist am Treffpunkt angekommen.`
- **Values:** design §5 tables, from the reference. Tokens where one holds the value; a raw reference number no
  token holds stays local with a one-line reason. CSS borders count in the box (padding = reference + border).
- **Touch floor 44 on both platforms**, never 48; small controls drawn small with a 44 touch laid over
  (`OverflowTapTarget` / `.overflowTapTarget()`), never growing the layout.
- **Both platforms in the same PR.** iOS bans `@unchecked Sendable`, `nonisolated(unsafe)`, `@preconcurrency`,
  mutable `static var`. No emoji. Never log a coordinate, an accuracy, a phone number, a code, a token, an email,
  a name or a message body.
- **Tracking:** registry 1.4.34 / taxonomy 1.4.27; `entry` values from §41 (`date_screen`, `chat_chip`; `report`
  reserved). Never in a payload: coordinates, accuracy, distance, place, address. Never fired from here:
  `message_sent`, `date_cancelled`, `screen_viewed` for B/C/D, any flow-10 event, anything from G (R1).
- **Motion:** `Motion.STATE` (180) for the error box, `Motion.PROGRESS` (280) for the sheet's height and content
  swap, the brand curve; reduced motion → instant. Press is `scale(0.98)` as elsewhere.
- **Fit:** `DEVICES + EDGE_DEVICES`, every Android font step and iOS `.large … .accessibility5`; rules: 44 touch,
  no overlapping touch areas, whole words, every control on screen; at 375 × 667 default type the guard's
  buttons and C's error are fully visible.
- **Git:** branch `feat/date-03-arrival-checkin` from `development`; push after every commit; never commit a
  `.semgrep/` folder; the PR targets `development`.

## Review Focus

1. **Close lands while the commit is offline, then a poll returns `checkedIn: false`** — the footer stays swapped
   and the chip stays ungated; the commit retries until it lands. Pinned in Task 5 (`a poll before the commit
   lands keeps the check-in`).
2. **Undo is tapped and the network is down** — D closes, the bar is back, the delete retries, and nothing ever
   commits that record. Pinned in Task 5 (`an undo that fails is retried and never committed`) and Task 3
   (`commit after undo is 404`).
3. **The app is backgrounded with D up** — the commit fires once; coming back shows E, not D. Pinned in Task 5
   (`leaving the app on D commits once`) and Task 7 (the host's lifecycle hook).
4. **Yes is tapped twice fast, or the phone answers after the 10 s timeout** — one read, one store; a late fix after
   the timeout is ignored. Pinned in Task 5 (`yes while busy does nothing`, `a fix after the timeout is ignored`).
5. **The match opens the Date screen while the chat is still locked and their date has checked in** — the orange
   card shows; `Open chat` clears it and never opens the chat; it does not come back on the next poll. Pinned in
   Task 6 (`a locked arrival card's Open chat clears it`) and Task 5 of the Date model (`a cleared arrival stays
   cleared`), Task 4 (`the arrival clears once the chat is read`).

---

### Task 1: Handoff files, registry 1.4.34 / taxonomy 1.4.27, the field stamp

**Files:**
- Copy from the 6 Nov zip (`Show Up Design System.zip`) into `design_handoff_showup/`: `date/screen-checkin-reference.jsx`,
  `date/tickets/03-arrival-checkin.md`, `date/README.md`, `date/07a-decisions.md`, `date/tickets/00-epic.md`,
  `components/shared.jsx` (adds `ShowUpCount`), `profile/tickets/12-location.md`.
- Modify: `design_handoff_showup/tracking/events.json`, `enums.json`, `tracking/README.md` (one merge note, as 1.4.32's);
  `src/modules/analytics/registry/sensitivity.ts`; Android `profile/ProfileAnalytics.kt` (`FIELD_REGISTRY_VERSION`
  and its changelog comment); iOS `ProfileAnalytics.swift` (`fieldRegistryVersion`); every test asserting `"1.4.33"`
  (Android `PickStepsViewModelTest`, `ProfileAnalyticsTest`, `ProfileDetailsViewModelTest`, `PromptsTrackingTest`;
  iOS `CheckInModelTests`, `DetailsModelTests`, `PickModelTests`, `ProfileAnalyticsTests`).

**Interfaces:** Produces the registry every later task reads: §41 `arrival_entry` = `date_screen · chat_chip · report`;
events `arrival_confirm_shown`, `arrival_confirm_submitted`, `arrival_confirmed`, `arrival_undone` as the zip has them.

- [ ] **Step 1:** Merge with a script in the scratchpad (`python -I`): the repo's `events.json` is the base; replace
  family M's two arrival events with the zip's and append the zip's two new ones in the zip's order; copy
  `changed_in_1_4_27`, `taxonomy_version` 1.4.27 and `event_count` 308; keep every `implemented` / `code_delta` /
  `backend_status` the repo has. `enums.json`: add §41 and `changed_in_1_4_34`, `registry_version` 1.4.34. Build the
  new text in memory, then write once.
- [ ] **Step 2:** Bump the stamp to `1.4.34` (server, both apps, the tests above).
- [ ] **Step 3:** `python audit/check-tracker-wiring.py`, `python audit/check-analytics-parity.py`, `npx jest
  src/modules/analytics` pass; `git diff --stat` shows no `.semgrep` path.
- [ ] **Step 4:** Commit `chore(tracking): registry 1.4.34 / taxonomy 1.4.27 and the Date 03 handoff (SHOWUP-195)`; push.

### Task 2: Server schema, entity and the pure rules

**Files:**
- Create: `src/database/migrations/1717000035000-DateArrivalCheckIns.ts`, `src/modules/dates/entities/date-arrival-check-in.entity.ts`,
  `src/modules/dates/util/arrival.ts`, `src/modules/dates/util/arrival.spec.ts`.
- Modify: `src/modules/analytics/events/date-events.ts` (+ `.spec.ts`), `src/modules/dates/dates.module.ts` (entity),
  `src/common/privacy/prohibited-fields.ts` (+ spec) if `accuracy` / `accuracyM` / `accuracy_m` are absent.

**Interfaces:**
- `enum ArrivalEntry { DateScreen = 'date_screen', ChatChip = 'chat_chip', Report = 'report' }` (in `util/arrival.ts`).
- Entity `DateArrivalCheckIn { id; dateId; userId; location: Point (GeoJSON); accuracyM: number | null; entry:
  ArrivalEntry; clientId: string; checkedInAt: Date; committedAt: Date | null }` on the design §4 table.
- `minutesVsStart(meetAt: Date, at: Date): number` — whole minutes `at − meetAt`, truncated toward zero.
- `secondsSince(from: Date, now: Date): number` — whole seconds, never negative.
- `arrivalOf(partnerRow: { checkedInAt: Date; committedAt: Date | null } | null, viewerReadAt: Date | null): { checkedInAt: string } | null`.
- `retentionCutoff(now: Date): Date` — the same instant one calendar year earlier.
- `arrivalConfirmedEvent(dateId, entry, minutesVsStart, isFirst): AnalyticsEventSpec`,
  `arrivalUndoneEvent(dateId, entry, secondsSinceConfirm): AnalyticsEventSpec` — exactly the registry's properties.

- [ ] **Step 1: Failing tests:** `minutesVsStart(meet, meet − 90 s) === -1`, `(meet, meet + 59 s) === 0`,
  `(meet, meet + 61 min) === 61`; `secondsSince(t, t − 5 s) === 0`; `arrivalOf(null, x) === null`;
  `arrivalOf({committedAt: null}, null) === null`; committed with `viewerReadAt` null → `{ checkedInAt }`; read
  before `committedAt` → shown; read at or after → null; `retentionCutoff(2026-11-10T12:00Z)` = `2025-11-10T12:00Z`
  and `2028-02-29` → `2027-02-28`; each event payload has exactly its properties and no key matching
  `/lat|lon|accur|dist|place|address/`.
- [ ] **Step 2:** `npx jest src/modules/dates/util/arrival.spec.ts src/modules/analytics/events` fails.
- [ ] **Step 3:** Implement; the migration creates the enum type, the table, the unique index and the
  `checked_in_at` index, and `down` drops them in reverse.
- [ ] **Step 4:** Tests pass; `npm run build`; `npm run migration:run`, `migration:revert`, `migration:run` against the
  e2e database.
- [ ] **Step 5:** Commit; push.

### Task 3: Store, undo, commit, notify

**Files:**
- Create: `src/modules/dates/arrival-check-ins.service.ts`, `src/modules/dates/dto/arrival.dto.ts`, `test/arrival.e2e-spec.ts`.
- Modify: `src/modules/dates/dates.controller.ts` (three routes), `src/modules/dates/dates.module.ts` (service,
  NotificationsModule — `forwardRef` only if Nest reports a cycle), `src/modules/notifications/util/preferences.ts`
  (`date_partner_arrived`, `engagement`), `src/modules/notifications/messages/catalog.ts` (+ `catalog.spec.ts`).

**Interfaces:**
- `CreateArrivalCheckInDto { latitude: number; longitude: number; accuracyM?: number; entry: ArrivalEntry; clientId: string }`
  (`@IsLatitude`, `@IsLongitude`, `@Min(0) @Max(100000)`, `@IsEnum`, `@IsUUID('4')`).
- `ArrivalCheckInDto { checkedInAt: string; committed: boolean }`.
- `ArrivalCheckInsService.create(dateId, userId, dto, now?): Promise<DateArrivalCheckIn>`,
  `.undo(dateId, userId, now?): Promise<void>`, `.commit(dateId, userId, now?): Promise<void>`,
  `.rowsFor(dateId): Promise<DateArrivalCheckIn[]>` (Task 4 reads it).
- Routes and status codes exactly as design §4; operation ids `createArrivalCheckIn`, `undoArrivalCheckIn`,
  `commitArrivalCheckIn`.

- [ ] **Step 1: Failing e2e** (two users with a confirmed date, as `dates.e2e-spec.ts` builds them):
  - create → 201 `{ committed: false }`; the row has the point, the accuracy, the entry; the response has no coordinate;
  - same `clientId` again → 201, one row, `arrival_confirmed` seen once; another `clientId` while uncommitted →
    the row's point replaced, a second `arrival_confirmed`;
  - `is_first` true for the first person, false for the second;
  - undo → 204, no row, `arrival_undone` with `entry` and `seconds_since_confirm`; undo again → 204, no event;
  - commit → 204, `committed_at` set, the push sender saw one `date_partner_arrived` to the partner with data
    `{ type, dateId }` only; commit again → 204, no second push;
  - undo after commit → 409; create after commit → 409; **commit after undo → 404**;
  - a stranger → 403; an unknown date → 404; an ended date (`ended_in_chat`) → 409;
  - latitude 91, longitude −181, accuracy −1, `entry: 'nowhere'`, no `clientId` → 400 each.
- [ ] **Step 2:** `npx jest --config ./test/jest-e2e.json test/arrival.e2e-spec.ts` fails.
- [ ] **Step 3:** Implement. Writes are single conditional statements (`… AND committed_at IS NULL RETURNING`); a
  unique-violation race is answered with the stored row. The push goes through `NotificationDispatchService.sendPush`
  with `{ name }` (the checker's first name) and data `{ type: 'date_partner_arrived', dateId }`. No coordinate in
  any log or exception message.
- [ ] **Step 4:** The e2e file and `npx jest src/modules/dates src/modules/notifications` pass.
- [ ] **Step 5:** Commit; push.

### Task 4: The payload, the dev route, retention, the contract

**Files:**
- Modify: `src/modules/date-screen/dto/active-date.dto.ts` (`DateArrivalDto`, `arrival`, `checkedIn` description),
  `src/modules/date-screen/util/active-date.ts` (+ spec), `src/modules/date-screen/date-screen.service.ts`,
  `test/date-screen.e2e-spec.ts`, `src/modules/date-screen/date-dev.controller.ts`, `date-dev.service.ts`,
  `src/queue/queue.constants.ts` (`QUEUE_ARRIVAL_RETENTION = 'arrival-retention'`), `test/api-contract.e2e-spec.ts`,
  `openapi.json` (`npm run openapi:emit`).
- Create: `src/modules/dates/jobs/arrival-retention.scheduler.ts`, `arrival-retention.processor.ts` (+ spec).

**Interfaces:**
- `ActiveDateDto.arrival: DateArrivalDto | null` with `DateArrivalDto { checkedInAt: string (date-time) }`;
  `ActiveDateSource.arrival` and `.checkedIn` filled from `rowsFor`.
- `POST /dev/date/arrival` (`devPartnerCheckIn`) → 204.
- `ArrivalCheckInsService.deleteExpired(now?): Promise<number>`.

- [ ] **Step 1: Failing tests:** e2e — A checks in (uncommitted) → A's `checkedIn` false, B's `arrival` null; A commits
  → A's `checkedIn` true, B's `arrival.checkedInAt` set; **B posts `chat/read` → B's `arrival` null** (the arrival
  clears once the chat is read); `devPartnerCheckIn` → the caller's `arrival` set and one push; the contract lists the
  four new operation ids. Unit — `deleteExpired` removes a row stamped one year and one second ago and keeps one a
  second younger.
- [ ] **Step 2:** They fail.
- [ ] **Step 3:** Implement; `npm run openapi:emit`.
- [ ] **Step 4:** The full e2e suite, `npm run lint:ci`, `npm run test:cov` (the util gate holds) pass.
- [ ] **Step 5:** Commit; push.

### Task 5: Android — client, data, rules, location, gate model

**Files:**
- Regenerate the Android client from `openapi.json` (never edited by hand).
- Create (`com.showup.arrival`): `ArrivalCopy.kt`, `ArrivalRules.kt`, `ArrivalAnalytics.kt`, `LocationFixReader.kt`
  (`LocationFix`, `LocationFixReader`, `AndroidLocationFixReader`), `ArrivalRepository.kt`, `ArrivalGateViewModel.kt`.
- Modify: `date/ActiveDate.kt` (`DateArrival`, `ActiveDate.arrival`), `date/DateRepository.kt` (map `arrival`;
  `DateStore.markArrivalSeen(dateId)` → `markDateChatRead`; `DateStore.partnerChecksIn()` → `devPartnerCheckIn`),
  `date/DateViewModel.kt` (`arrivalSeen()`, the cleared-arrival filter), `date/DateRules.kt` (`hoisted`),
  `chat/ChatRouting.kt` (`chatEntryFor` → `OpenChat` with an arrival), `chat/ChatReasons.kt` (`Arrived.shown = true`),
  `chat/ChatViewModel.kt` (`pick(reason, surface, gated)`), every hand-written `ActiveDateDto` fixture.
- Test: `arrival/ArrivalRulesTest.kt`, `arrival/ArrivalGateViewModelTest.kt`, `arrival/ArrivalRepositoryTest.kt`,
  `date/DateViewModelTest.kt`, `date/DateRulesTest.kt`, `chat/ChatViewModelTest.kt`, `chat/ChatReasonsTest.kt`.

**Interfaces:**
- `data class DateArrival(val checkedInAt: Instant)`; `ActiveDate.arrival: DateArrival?`.
- `enum class ArrivalEntry(val wire: String) { DateScreen("date_screen"), ChatChip("chat_chip"), Report("report") }`.
- `data class LocationFix(val latitude: Double, val longitude: Double, val accuracyM: Double?)`;
  `fun interface LocationFixReader { suspend fun fix(): LocationFix? }`.
- `interface ArrivalStore { suspend fun checkIn(dateId: String, fix: LocationFix, entry: ArrivalEntry, clientId: String): Outcome<Unit>;
  suspend fun undo(dateId: String): Outcome<Unit>; suspend fun commit(dateId: String): Outcome<Unit> }` (409 →
  `Outcome.Failed(CallFailure.Conflict)`).
- `fun minutesVsStart(meetAt: Instant, now: Instant): Int`; `const val LOCATION_FIX_TIMEOUT_MS = 10_000L`;
  `fun retryDelayMs(attempt: Int): Long` (2 000, 4 000, … capped at 30 000).
- `fun hoisted(state: DateState, arrival: DateArrival?): Boolean = arrival != null || state == DateState.Unread`.
- `sealed interface GatePhase { data object Idle; data class Confirm(val entry: ArrivalEntry, val locError: Boolean,
  val busy: Boolean, val note: String?); data class Done(val entry: ArrivalEntry) }`;
  `data class GateUi(val phase: GatePhase = GatePhase.Idle, val committed: Boolean = false, val undone: Boolean = false)`.
- `class ArrivalGateViewModel(store: ArrivalStore, reader: LocationFixReader, analytics: AnalyticsTracker?, clock: () -> Instant, newId: () -> String)`:
  `fun bind(date: ActiveDate)`, `fun checkedIn(): Boolean`, `fun open(entry: ArrivalEntry, note: String? = null): Boolean`,
  `fun yes()`, `fun notYet()`, `fun close()`, `fun undo()`, `fun leftApp()`, `fun consumeUndone()`; `val ui: StateFlow<GateUi>`.
- `ChatViewModel.pick(reason: ChatReason, surface: ChipSurface, gated: Boolean = false)` — gated: `action_chip_used` only.
- `DateViewModel.arrivalSeen()` — clears `date.arrival` now, sends `markArrivalSeen`, and later reads drop an
  arrival whose `checkedInAt` is not after the cleared one.

- [ ] **Step 1: Failing rule tests:** `minutesVsStart` mirrors the server's three cases; `retryDelayMs(0..5)` =
  2000, 4000, 8000, 16000, 30000, 30000; `hoisted(Locked, arrival)` true, `(Available, null)` false, `(Unread, null)`
  true; `SHOWN_REASONS` = `[OnMyWay, Arrived, CantFind, WhenArrive, RunningLate, CantMakeIt]`.
- [ ] **Step 2: Failing gate tests** (fake store, fake reader, fake clock, `runTest` virtual time):
  - `open` on a checked-in date returns false and fires nothing; otherwise Confirm + `arrival_confirm_shown` with
    `date_id`, `entry`, `minutes_vs_start`, and no coordinate key;
  - `notYet` → Idle, no event, no store call;
  - `yes` fires `arrival_confirm_submitted`, sets busy, reads once, stores, → Done; **`yes while busy does nothing`**;
  - reader null → Confirm(locError = true), no store; `Try again` fires submitted again and no new shown;
  - **a fix after the timeout is ignored** (reader answers at 10 001 ms → C, no store);
  - store failed → C (R5); store Conflict → Idle, `committed` true;
  - `close` → Idle, `committed` true, one commit; **a poll before the commit lands keeps the check-in**
    (`bind` with `checkedIn = false` after `close` → `checkedIn()` true);
  - commit failed → retried on `retryDelayMs` until it lands;
  - `undo` → Idle, `undone` true, one delete, `checkedIn()` false; **an undo that fails is retried and never
    committed**; a `yes` after an undo works and stores again;
  - **leaving the app on D commits once** (`leftApp` twice → one commit); `leftApp` on Idle or Confirm does nothing;
  - `bind` with another date id resets the phase and `committed`.
- [ ] **Step 3: Failing model tests:** `pick(Arrived, Dock, gated = true)` fires `action_chip_used` and leaves
  `selected`, `draft` and the thread unchanged; ungated it selects with the draft; the Date model's **a cleared arrival
  stays cleared** (a read bringing the same `checkedInAt` after `arrivalSeen` keeps it null; a later one shows);
  the repository maps `arrival`.
- [ ] **Step 4:** `./gradlew :app:testDebugUnitTest --tests 'com.showup.arrival.*' --tests 'com.showup.date.*' --tests 'com.showup.chat.*'` fails.
- [ ] **Step 5:** Implement. `AndroidLocationFixReader`: permission and services checked first (null if either is
  missing); `LocationManagerCompat.getCurrentLocation` on the provider `AndroidPlaceReader` picks; no last-known;
  accuracy only when `hasAccuracy()`. The 10 s bound is the view model's `withTimeoutOrNull`.
- [ ] **Step 6:** Those tests, plus `com.showup.welcome.*` and `com.showup.api.*` (fixtures), pass;
  `python audit/check-tracker-wiring.py` passes.
- [ ] **Step 7:** Commit; push.

### Task 6: Android — the bar, the sheets, the arrival card, the date gate

**Files:**
- Create (`com.showup.arrival`): `CheckInBar.kt`, `ArrivalSheets.kt` (`CheckInConfirmSheet`, `CheckInDoneSheet`,
  `ArrivalSheetHost`).
- Modify: `date/DateCards.kt` (`ArrivalCoordinate`, `CoordinateModule` arrival branch + `onArrivalSeen`),
  `date/DateScreen.kt` (hoist by `hoisted`), `profile/ProfileLocationScreen.kt` (`LocationScreenContext.Date`,
  `DateGateCard`, the date layout).
- Test: `arrival/CheckInBarTest.kt`, `arrival/ArrivalSheetsTest.kt`, `date/DateScreenTest.kt`, `profile/DateLocationGateTest.kt`.

**Interfaces:**
- `@Composable fun CheckInBar(name: String, onCheckIn: () -> Unit, modifier: Modifier = Modifier, onEnd: (() -> Unit)? = null)`
  — `onEnd == null` hides `End date`; `const val END_DATE_LINK_SHOWN = false`.
- `@Composable fun ArrivalSheetHost(ui: GateUi, name: String, onYes: () -> Unit, onNotYet: () -> Unit, onClose: () -> Unit, onUndo: () -> Unit)`
  — one `ModalShell` while the phase is not Idle; Confirm draws `CheckInConfirmSheet(name, note, locError, busy, …)`,
  Done draws `CheckInDoneSheet(name, …)`; R9's motion.
- `CoordinateModule(..., onArrivalSeen: () -> Unit = {})` — `date.arrival != null` draws `ArrivalCoordinate`; its
  CTA calls `onOpenChat` when `state != Locked`, else `onArrivalSeen`.
- `LocationScreenContext.Date(name: String, meetDay: String, meetAt: Instant, place: String?, photoUrl: String?, now: Instant, zone: ZoneId, onTab: (Tab) -> Unit)`;
  `fun gatePill(meetDay: String, meetAt: Instant, now: Instant): GatePill?` (`Starts(h, m)` today before the meet,
  `Started(min)` after, null tomorrow).
- Tags: `arrival:bar`, `arrival:check-in`, `arrival:end-date`, `arrival:sheet`, `arrival:not-yet`, `arrival:yes`,
  `arrival:error`, `arrival:close`, `arrival:undo`, `date:arrival-card`, `date-gate:card`, `date-gate:cta`.

- [ ] **Step 1: Failing UI tests** (Robolectric):
  - the bar shows its three strings and no `End date`; with `onEnd` it shows `End date` with a 44 touch area;
  - B shows the circle, the title, `Not yet` above `Yes, I've arrived`; C shows the error between title and
    buttons and `Try again`; busy: `Yes` and `Not yet` are disabled and a scrim tap calls nothing; D shows `Close`
    above `Undo check-in` (≥ 44 tall); a scrim tap on D calls `onClose`; with a note, the note sits under the title;
  - the Date screen with an arrival draws the card above the time + place card, tag `ARRIVED`, `checked in · now`,
    the sentence and `Open chat`; **a locked arrival card's Open chat clears it** (calls `onArrivalSeen`, not `onOpenChat`);
  - G: denied draws the card, the B headline, body and `Open Settings`, no privacy note; not determined draws
    `Allow location access` and the note; `gatePill` = `Starts in 1h 18m` 78 min before, `Started 10 min ago` 10 min
    after, null tomorrow.
- [ ] **Step 2:** They fail.
- [ ] **Step 3:** Implement from the reference and design §5; the bar over cream, the sheet buttons drawn as
  `EndDateSheet` draws its own (radius 16 / 18, sunset through `roundedShadows` with `ShadowCta`).
- [ ] **Step 4:** They pass, with `com.showup.date.*` and `com.showup.profile.*`.
- [ ] **Step 5:** Commit; push.

### Task 7: Android — wiring, background commit, the gate over the Date screen, the dev menu

**Files:**
- Modify: `MainActivity.kt` (the gate model per account + date beside the chat model; `CheckInBar` into
  `DateScreen(checkInBar = …)`; `ArrivalSheetHost` over the Date screen and over `AppRoute.Chat`; the chat's
  `onPick` gate; `undone` → leave the chat for the Date screen; `ON_STOP` → `gate.leftApp()`; the footer and the chip
  read `date.checkedIn || gate.ui.committed`; the date location gate in place of the Date screen while
  `locationGate.state.blocked != null`; the arrival card's `onArrivalSeen`), `dev/DevMenu.kt` + `dev/DevJump.kt`
  (`Leonie checks in`; `DEV_STATES` 195 A–H), `debug/.../CheckInGalleryActivity.kt` (the frames).
- Test: `arrival/ArrivalRoutingTest.kt`, `dev/DevStatesTest.kt`, `welcome/FlowRestorationTest.kt` (counts).

**Interfaces:**
- `fun arrivalChipGated(reason: ChatReason, gate: ArrivalGateViewModel): Boolean = reason == ChatReason.Arrived && !gate.checkedIn()`
  — the host calls `chat.pick(r, s, gated)` first, then `gate.open(ArrivalEntry.ChatChip)` when gated (event order:
  `action_chip_used`, then `arrival_confirm_shown`).
- `LocationGateViewModel.dateMounted()` / `dateLeft()` — the date context reads and asks as the gate does and fires
  no event.

- [ ] **Step 1: Failing tests:** `arrivalChipGated` both ways; the event order above; undo from the chat routes to the
  Date screen with the bar; `DEV_STATES["195"]` lists A–H and each draws its own frame; the States count moves by eight.
- [ ] **Step 2:** They fail.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `com.showup.arrival.*`, `com.showup.date.*`, `com.showup.chat.*`, `com.showup.dev.*`,
  `com.showup.welcome.FlowRestorationTest` pass; `:app:assembleDebug` passes; `./gradlew --stop`.
- [ ] **Step 5:** Commit; push.

### Task 8: iOS — Tasks 5 to 7

**Files:** Swift twins in `ios-app/ShowUpWelcome/`: `ArrivalCopy.swift`, `ArrivalRules.swift`, `ArrivalAnalytics.swift`,
`LocationFixReader.swift` (`CLLocationFixReader`, desired accuracy best, `requestLocation()`), `ArrivalRepository.swift`,
`ArrivalGateModel.swift` (`@MainActor @Observable final class`), `CheckInBar.swift`, `ArrivalSheets.swift`; modify
`ActiveDate` / `DateRepository.swift`, `DateModel.swift`, `DateRules.swift`, `DateCards.swift`, `DateScreen.swift`,
`ChatReasons.swift`, `ChatModel.swift`, `ChatRouting.swift`, `ProfileLocation.swift` (date context), `LocationGateModel.swift`,
`ShowUpWelcomeApp.swift` (wiring; scene `.background` → `leftApp()`), `DevMenu.swift`; the regenerated client; tests
`ArrivalRulesTests`, `ArrivalGateModelTests`, `ArrivalRepositoryTests`, `DateModelTests`, `ChatModelTests`,
`ChatReasonsTests`, `DevMenuRulesTests`, `DateLocationGateTests`; `python gen_pbxproj.py`.

**Interfaces:** the names and signatures of Tasks 5–7 in Swift (`ArrivalStoring` protocol, `GatePhase`, `GateUi`,
`ArrivalEntry: String`, `LocationFix`, `minutesVsStart(_:_:)`, `hoisted(_:_:)`, `arrivalChipGated(_:_:)`). Retry and
timeout `Task`s are cancelled on `bind` to another date and on deinit.

- [ ] **Step 1:** Port the tests first (every case of Tasks 5–7, the five Review Focus cases included).
- [ ] **Step 2:** Implement.
- [ ] **Step 3:** `check-swift-structure`, `check-swift-arg-order`, `check-swift-concurrency`, `check-ios-availability`,
  `check-pbxproj` pass locally; push; the iOS CI job (build + unit tests) is green.
- [ ] **Step 4:** Commit (CI run id in the message body); push.

### Task 9: Fit sweeps and evidence

**Files:**
- Create: `android-preview-project/app/src/test/java/com/showup/fit/ArrivalFitTest.kt`, `ios-app/ShowUpWelcomeTests/ArrivalFitTests.swift`.
- Modify: `fit/EvidenceScreenshots.kt` (`SHOWUP-195` A–F, H; G and A-with-link as audit frames),
  `ShowUpWelcomeTests/EvidenceShots.swift`.

**Interfaces:** the 193 harness (`measureFit(…, extra = …)`, `FitFrame` `tapTargets` / `lineBreaks` / `tagged`,
`DEVICES + EDGE_DEVICES`, all font steps; iOS split into tests of two Dynamic Type sizes each, each layout in
`autoreleasepool`, every test under 10 minutes).

- [ ] **Step 1: Failing sweeps** for A, B, B + note, C, D, F, H, G: the shared rules; the bar's words whole (R12);
  **at 375 × 667 default type, `arrival:not-yet`, `arrival:yes` and `arrival:error` lie inside the sheet's visible part**.
- [ ] **Step 2:** Run (Android narrow and in the foreground, `--stop` after; iOS on CI); fix each finding at its cause.
- [ ] **Step 3:** Evidence at 375 × 667, 390 × 844, 430 × 932 on both platforms; scan each against the spec sheet at
  2x (PowerShell `GetPixel` on both PNGs): ① bar height and paddings, ③ title lines and button tops, ④ error box
  height, ⑤ ⑥, ⑩ card rows; fix every difference over 1 px.
- [ ] **Step 4:** Commit; push.

### Task 10: `audit/verify-arrival.py`, the tracking flags, the PR

**Files:**
- Create: `audit/verify-arrival.py`.
- Modify: `design_handoff_showup/tracking/events.json` (`implemented: true` + `backend_status` for the four events,
  as 193/194 marked theirs), `tracking/README.md` (totals).

- [ ] **Step 1:** `verify-arrival.py` quotes the ticket's copy and checks both platforms' `ArrivalCopy`, the date
  gate's strings, `END_DATE_LINK_SHOWN = false` on both, `Arrived` shown on both, no coordinate key in either app's
  `ArrivalAnalytics` or the server's arrival builders, no `message_sent` / `date_cancelled` / `screen_viewed` in the
  arrival package; it passes.
- [ ] **Step 2:** Every `audit/verify-*.py` and `check-*.py` passes; Android `testDebugUnitTest` (narrow locally, full
  on CI), server unit + e2e + `test:cov`, iOS CI — green.
- [ ] **Step 3:** Whole-branch review (Sonnet, per Esma's routing); fix what it confirms.
- [ ] **Step 4:** Open the PR to `development`: what shipped, the rulings R1–R15, the evidence, Philipp's owed items
  (push copy, R5 string, the date gate's tracking, R2), the device items owed (the recording, a real iPhone, iOS 17).
