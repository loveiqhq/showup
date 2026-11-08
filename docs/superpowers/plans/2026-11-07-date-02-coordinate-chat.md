# Date 02 — Coordinate chat (SHOWUP-194) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The limited pre-date chat — tutorial, chip-led thread, the cancel guide, and ending the date from it
(frames A–H) — on Android and iOS, with the server it needs.

**Architecture:** The server keeps the existing SHOWUP-115 chat tables and routes, moves them to the
registry's §40 reasons (reason optional, body required), and adds the tutorial flag, the `ended_in_chat`
state and its outcome. Both apps get one chat vocabulary (`ChatReason`, built once, read by Date 01), a
session-scoped chat model that polls, and the screens drawn from the reference. Routing hangs off the Date
screen's two entry points, which 193 left as no-ops.

**Tech Stack:** NestJS / TypeORM / Postgres; Kotlin + Jetpack Compose (Robolectric tests); Swift + SwiftUI
(XCTest, CI on macos-15); swift-openapi-generator and the Android OpenAPI client generated from
`openapi.json`.

**Spec:** `docs/superpowers/specs/2026-11-07-date-02-coordinate-chat-design.md` · Ticket: SHOWUP-194 (Jira
description; no comments) · Reference: `design_handoff_showup/date/screen-chat-reference.jsx` (6 Nov zip,
copied into the repo by Task 1).

## Global Constraints

- **Order of authority:** reference wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on
  nothing.
- **Copy:** exact strings from the ticket's *Copy — final strings* (A–H, header, chips, drafts,
  placeholders). Product terms: Show Up · Show-up Rate · Check-In.
- **Chips:** `CHAT_REASONS` order; only five shown — `on_my_way`, `cant_find`, `when_arrive`,
  `running_late`, `cant_make_it`; `arrived`, `change_time`, `change_place` hidden (Esma, epic rule 8). B: a
  2-column grid of tiles. Dock: a 2-column grid of compact chips, min-h 36, no heading. Full labels.
- **`cant_make_it`:** no draft; field and send locked until `Got it`; the guide is a thread item, local.
- **End date card:** only with a `cant_make_it` message in the thread; hidden while `cant_make_it` is
  selected; no time cut-off. Card and sheet buttons say `End date`; the sheet shows `Keep the date` first.
- **Layout:** one column; the thread is the only scroll in C–E; the dock sits under it; no absolute Y; the
  OS keyboard, never a height in code (evidence alone simulates one).
- **Tokens only** (`Spacing`, `Radius`, `IconSizes`, `Motion`, colours). Raw reference numbers no token
  holds stay local with a reason. `Motion.TINT` = 160 ms (added in Task 7) for every colour that follows a
  control's state.
- **Both platforms in the same PR.** iOS bans `@unchecked Sendable`, `nonisolated(unsafe)`,
  `@preconcurrency`, mutable `static var`. No emoji. No logging of a phone number, code, token, email — or a
  message body, draft or name.
- **Tracking:** registry 1.4.33 / taxonomy 1.4.26; screen ids from §11, `entry` from §39, `reason` from §40,
  never typed at a call site. Never in a payload: message text, draft, guide text, place. Never fired from
  here: `date_cancelled`, `arrival_*`, suggestion events, flow-10 events, `coordinate_viewed`,
  `date_status_sent`, `date_status_undone`.
- **Polling:** the chat re-reads thread and date every 5 s on screen, and at once after a send or a return
  to the foreground; the Date screen re-reads the date every 15 s on screen.
- **Fit:** every frame on all 27 sizes of the 193 audit (`DEVICES` + `EDGE_DEVICES`), every type size, with
  its rules (48 dp / 44 pt, no overlapping touch areas, whole words, nothing behind the dock).
- **Evidence:** A–H at 375 × 667, 390 × 844, 430 × 932 per platform; C–E with a keyboard-sized inset.

## Review Focus

1. **The other person ends the date while this user is mid-draft or has the sheet open** — the next poll
   (or a 409 from `End date`) closes the sheet, drops the draft and shows that user's outcome, no error.
   Pinned in Task 8 (`a poll that finds the date ended shows the outcome`, `a 409 on end shows the outcome`).
2. **A send that fails** (offline, 5xx) — the text and the selected chip stay in the dock, nothing is added
   to the thread, the next tap sends once. Pinned in Task 8 (`a failed send keeps the draft and the chip`).
3. **A poll that brings messages while the user types** — the draft, the selection and the guide stay as
   they are; only the thread grows. Pinned in Task 8 (`a poll never touches the dock`).
4. **The match's `cant_make_it` arrives while this user has `cant_make_it` selected** — the guide keeps the
   slot; the card appears when another chip is picked or after sending. Pinned in Task 8
   (`the end card waits while cant_make_it is selected`).
5. **The meet time passes with the chat open** — the countdown leaves the header, the chat and the card stay
   usable until the date ends. Pinned in Task 8 (`countdown is null after the meet time`) and Task 5 (the
   server accepts `end-in-chat` after the meet time).

---

### Task 1: Handoff files and registry 1.4.33 / taxonomy 1.4.26

**Files:**
- Copy from the 6 Nov zip into `design_handoff_showup/`: `date/screen-chat-reference.jsx`,
  `date/tickets/02-coordinate-chat.md`, `date/tickets/00-epic.md`, `date/07a-decisions.md`,
  `date/spec-sheets/02-coordinate-chat.png`.
- Modify: `design_handoff_showup/tracking/events.json`, `enums.json`, `properties.json`, `requirements.json`.

**Interfaces:** Produces the registry every later task reads: §39 `chat_entry` =
`open_chat · read_reply · tutorial · notification`; §40 `chat_reason` =
`on_my_way · arrived · cant_find · when_arrive · change_time · change_place · running_late · cant_make_it`;
§11 rows `date_chat_tutorial` / `Date - Chat tutorial`, `date_chat` / `Date - Coordinate chat`,
`date_ended_in_chat` / `Date - Ended in chat`; events `cancel_guide_acknowledged`, `end_date_sheet_opened`,
`date_ended_in_chat` (new) and family L respecified.

- [ ] **Step 1:** Diff the zip's four tracking files against the repo's. Keep the repo's file as the base
  (it carries the `implemented` / reconciliation record the zip predates) and take every zip change to
  family L, §11, §39, §40 and the three new events, field for field. Bump `taxonomy_version` to 1.4.26 and
  the registry version to 1.4.33.
- [ ] **Step 2:** `python audit/check-tracker-wiring.py` and the registry JSON validators pass (the new
  events start `implemented: false`).
- [ ] **Step 3:** Commit.

### Task 2: Server schema and the §40 reasons

**Files:**
- Create: `src/database/migrations/1717000033000-DateChat.ts`.
- Modify: `src/modules/dates/util/pre-date-chat.ts` (enum + `SENDABLE_REASONS`),
  `src/modules/dates/util/date-lifecycle.ts`, `src/modules/dates/entities/date.entity.ts`,
  `src/modules/dates/entities/date-chat-message.entity.ts`, and every reference to the renamed members.
- Test: `src/modules/dates/util/pre-date-chat.spec.ts`, `date-lifecycle.spec.ts`, `date-lock.spec.ts`.

**Interfaces:**
- `enum ChatReason { OnMyWay='on_my_way', Arrived='arrived', CantFind='cant_find', WhenArrive='when_arrive',
  ChangeTime='change_time', ChangePlace='change_place', RunningLate='running_late', CantMakeIt='cant_make_it' }`
- `const SENDABLE_REASONS: ReadonlySet<ChatReason>` — every value but `ChangeTime` and `ChangePlace`.
- `DateStatus.EndedInChat = 'ended_in_chat'`, terminal; `confirmed → ended_in_chat` allowed.
- `DateEntity` gains `endedById: string | null`, `endedAt: Date | null`, `aChatTutorialAt` / `bChatTutorialAt:
  Date | null`, `aEndSeenAt` / `bEndSeenAt: Date | null` (snake_case columns as named in the spec).
- `DateChatMessage.reason: ChatReason | null`.

- [ ] **Step 1: Failing tests:** `SENDABLE_REASONS` has six members and excludes `change_time` /
  `change_place`; `canTransition(Confirmed, EndedInChat)` is true and `EndedInChat` has no exits;
  `dateLocksUser({ status: EndedInChat, … }, a)` is false for both people.
- [ ] **Step 2:** `npx jest src/modules/dates/util` fails on the missing members.
- [ ] **Step 3:** Implement. The migration renames the enum values with `ALTER TYPE … RENAME VALUE` (four
  renames), adds `arrived` and `when_arrive`, drops `NOT NULL` on `reason`, adds `ended_in_chat` to
  `dates_status_enum`, and adds the six columns; `down` reverses each, mapping any `arrived` /
  `when_arrive` row to `on_my_way` and any null reason to `on_my_way` first.
- [ ] **Step 4:** Unit tests pass; `npm run build` passes; the migration runs and reverts against the e2e
  database (`npm run migration:run`, `migration:revert`, `migration:run`).
- [ ] **Step 5:** Commit.

### Task 3: The chat contract — `GET` / `POST /dates/:id/chat`

**Files:**
- Modify: `src/modules/dates/dto/date.dto.ts`, `src/modules/dates/dates.service.ts`,
  `src/modules/dates/dates.controller.ts`, `test/dates.e2e-spec.ts` (the SHOWUP-115 chat cases move to the
  new contract).

**Interfaces:**
- `SendChatMessageDto { reason?: ChatReason; body: string }` — `body` `@IsString`, trimmed, 1..1000
  (`CHAT_MESSAGE_MAX_LENGTH`); `reason` `@IsOptional @IsEnum`, refused with 400 when not in
  `SENDABLE_REASONS`.
- `ChatMessageDto { id; reason: ChatReason | null; body: string | null; fromYou: boolean; createdAt: Date;
  read: boolean | null }` — `read` only on the caller's own messages: the other person's chat-read stamp is
  at or after `createdAt`; `null` on theirs. Field names stay SHOWUP-115's.
- `DatesService.sendChatMessage(dateId, userId, reason: ChatReason | null, body: string): Promise<DateChatMessage>`
  — refused unless the date is `confirmed`, the caller is in it, and `isChatOpen(scheduledAt)`.
- `DatesService.listChatMessages(dateId, userId): Promise<{ messages: DateChatMessage[]; otherReadAt: Date | null }>`.

- [ ] **Step 1: Failing e2e cases** in `test/dates.e2e-spec.ts`:
  - a message with `body` and no `reason` → 201, listed with `reason: null`;
  - `{ reason: 'running_late', body: 'Running…' }` → 201; `{ reason: 'change_time', body: 'x' }` → 400;
    `{ reason: 'cant_find' }` with no body → 400; a 1001-character body → 400;
  - before meet − 1:30 → 400 with the window message; on an `ended_in_chat` date → 409;
  - the sender's message reads `read: false`, then `read: true` after the other person `POST …/chat/read`;
  - the other person's copy of it has `read: null` and `fromYou: false`.
- [ ] **Step 2:** `npx jest --config ./test/jest-e2e.json test/dates.e2e-spec.ts` fails on them.
- [ ] **Step 3:** Implement. No message body or name is ever logged.
- [ ] **Step 4:** The e2e file passes, and `npx jest src/modules/dates` passes.
- [ ] **Step 5:** Commit.

### Task 4: The tutorial flag and `GET /me/date`

**Files:**
- Modify: `src/modules/date-screen/dto/active-date.dto.ts`, `date-screen.service.ts`,
  `date-screen.controller.ts`, `src/modules/date-screen/util/active-date.ts`, `src/modules/dates/dates.controller.ts`
  and `dates.service.ts` (the tutorial route), `test/date-screen.e2e-spec.ts`.
- Test: `src/modules/date-screen/util/active-date.spec.ts`.

**Interfaces:**
- `ActiveDateDto.chatTutorialSeen: boolean` (the caller's column is non-null).
- `DateUnreadDto.reason: ChatReason | null`.
- `EndedInChatDto { dateId: string; name: string; endedByYou: boolean }`.
- `ActiveDateResponseDto { date: ActiveDateDto | null; ended: EndedInChatDto | null }` — `ended` is set only
  when `date` is null and the caller has an `ended_in_chat` date whose own end-seen stamp is null (the
  newest such).
- `POST /dates/:id/chat/tutorial` (`operationId: markChatTutorialSeen`) → 204, idempotent, participant only.
- Pure: `endedOutcomeOf(row, viewerId, partnerName): EndedInChatDto | null` in `active-date.ts`.

- [ ] **Step 1: Failing tests:** `endedOutcomeOf` returns `endedByYou` from `ended_by_id`, and null once the
  viewer's stamp is set or the status is not `ended_in_chat`. e2e: `chatTutorialSeen` false, then true after
  `POST …/chat/tutorial` for the caller only; an unread message with no reason has `unread.reason: null`.
- [ ] **Step 2:** Both fail.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `npx jest src/modules/date-screen` and the date-screen e2e pass.
- [ ] **Step 5:** Commit.

### Task 5: Ending in the chat

**Files:**
- Modify: `src/modules/dates/dates.service.ts`, `dates.controller.ts`, `dto/date.dto.ts`,
  `src/modules/analytics/events/date-events.ts`, `test/dates.e2e-spec.ts`.
- Create: `src/modules/dates/util/end-in-chat.ts` + `.spec.ts`.
- Test: `src/modules/analytics/events/date-events.spec.ts`.

**Interfaces:**
- Pure: `cantMakeItFrom(messages: { senderId: string; reason: ChatReason | null }[], enderId: string): 'self' | 'match' | null`
  — the most recent `cant_make_it` message decides; null when there is none.
- `dateEndedInChatEvent(dateId: string, from: 'self' | 'match', minutesBeforeStart: number): AnalyticsEventSpec`
  — `date_ended_in_chat` with `date_id`, `cant_make_it_from`, `minutes_before_start`.
- `DatesService.endInChat(dateId, userId, now?): Promise<EndedInChatDto>` — 409 unless `confirmed` and
  `cantMakeItFrom(...)` is non-null; sets `ended_in_chat`, `ended_by_id`, `ended_at`; logs the status change;
  sends the event once (for the ender's user id); touches no score, rating or review field.
- `DatesService.markEndSeen(dateId, userId): Promise<void>` — stamps the caller's `*_end_seen_at`.
- `POST /dates/:id/end-in-chat` (`endDateInChat`) → 200 `EndedInChatDto`; `POST /dates/:id/ended/seen`
  (`markDateEndSeen`) → 204.

- [ ] **Step 1: Failing tests:** `cantMakeItFrom` → `'self'` when the ender sent the latest `cant_make_it`,
  `'match'` when the other did, null with none (and the latest wins when both sent one). The event builder's
  payload has exactly the three properties. e2e:
  - `end-in-chat` with no `cant_make_it` message → 409;
  - after the match sends one → 200 `{ endedByYou: true }`; a second call → 409;
  - both people's `GET /me/date` → `date: null`, `ended` with `endedByYou` true / false; after each
    `POST …/ended/seen`, their `ended` is null;
  - search for either person no longer refuses (the lock is released);
  - accepted after the meet time has passed;
  - the analytics spy saw `date_ended_in_chat` once and `date_cancelled` never.
- [ ] **Step 2:** They fail.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Unit and e2e pass.
- [ ] **Step 5:** Commit.

### Task 6: Dev routes, OpenAPI and the contract

**Files:**
- Modify: `src/modules/date-screen/date-dev.controller.ts`, `date-dev.service.ts`, `openapi.json` (via
  `npm run openapi:emit`), `test/api-contract.e2e-spec.ts`.

**Interfaces:**
- `POST /dev/date/message` takes `{ reason?: ChatReason; body: string }` (was reason-required) — the test
  match sends it.
- `POST /dev/date/end-in-chat` — the test match ends the caller's date in the chat (needs a `cant_make_it`
  message, as the real route).
- Both non-production only, as the existing dev routes.

- [ ] **Step 1:** The contract test lists every new route and operation id; it fails.
- [ ] **Step 2:** Implement; `npm run openapi:emit`.
- [ ] **Step 3:** The full e2e suite, `npm run lint:ci` and `npm run test:cov` pass (the coverage gate holds
  for the new utils).
- [ ] **Step 4:** Commit.

### Task 7: The chat vocabulary on both apps, the clients, `Motion.TINT`

**Files:**
- Create (Android `com.showup.chat`): `ChatReasons.kt`, `ReasonBadge.kt`. (iOS: `ChatReasons.swift`,
  `ReasonBadge.swift`.)
- Modify: both generated clients (rebuilt from `openapi.json`; never edited), 193's
  `date/DateRules.kt` / `DateRules.swift` (`chatReasonStyle` removed), `DateCards` (the unread card reads
  the new vocabulary; a null reason draws no badge), `ActiveDate` (`unread.reason` nullable,
  `chatTutorialSeen`), `DateRepository` (map `ended`), `designsystem/Motion` (both), 193's Copy pill
  (raw 0.16 → `Motion.TINT`).
- Test: `ChatReasonsTest.kt` / `ChatReasonsTests.swift`; the 193 Date tests updated.

**Interfaces:**
- `enum class ChatReason(val wire: String, val label: String, val draft: String?, val icon: BrandIcon,
  val tone: ReasonTone, val shown: Boolean)` in grid order, values and copy from the ticket's chip table;
  `ChatReason.fromWire(s: String?): ChatReason?`; `val SHOWN_REASONS: List<ChatReason>`.
- `enum class ReasonTone { Violet, Orange, Danger, DarkRed }` with `fg`, `bg`, `chipBorder` from the
  reference's `REASON_TONES` (`#8E1116` dark red is the one literal, with its reason).
- `@Composable fun ReasonBadge(reason: ChatReason, onLight: Boolean)` — the reference's badge.
- `data class EndedInChat(val dateId: String, val name: String, val endedByYou: Boolean)`;
  `DateRead.Ended(val outcome: EndedInChat)` beside `Active` / `None`.
- `Motion.TINT = 160` (ms) — a control's colour following its state.
- Swift: the same names (`ChatReason: String, CaseIterable` with `rawValue` = wire, `shownReasons`,
  `ReasonTone`, `ReasonBadge`, `EndedInChat`, `DateRead.ended`).

- [ ] **Step 1: Failing tests:** `SHOWN_REASONS` equals `[OnMyWay, CantFind, WhenArrive, RunningLate,
  CantMakeIt]`; every label and draft equals the ticket's table (`CantMakeIt.draft` null);
  `fromWire("cant_make_it") == CantMakeIt`, `fromWire("cant_make_it_today") == null`, `fromWire(null) == null`.
- [ ] **Step 2:** They fail.
- [ ] **Step 3:** Implement; rebuild both clients; move the unread card onto the vocabulary.
- [ ] **Step 4:** Android `testDebugUnitTest --tests 'com.showup.chat.*' --tests 'com.showup.date.*'` and
  the iOS suite (CI) pass; `check-swift-*` pass.
- [ ] **Step 5:** Commit.

### Task 8: Android chat repository, rules and model

**Files:**
- Create (`com.showup.chat`): `ChatRepository.kt` (`ChatStore` + `ApiChatStore`), `ChatRules.kt`,
  `ChatViewModel.kt`, `ChatAnalytics.kt`, `ChatSamples.kt`.
- Test: `ChatRulesTest.kt`, `ChatViewModelTest.kt`, `ChatRepositoryTest.kt`.

**Interfaces:**
- `data class ChatMessage(val id: String, val reason: ChatReason?, val body: String, val fromYou: Boolean,
  val sentAt: Instant, val read: Boolean?)` (a legacy null body reads as the reason's label).
- `interface ChatStore { suspend fun thread(dateId: String): Outcome<List<ChatMessage>>; suspend fun
  send(dateId: String, reason: ChatReason?, body: String): Outcome<ChatMessage>; suspend fun
  markRead(dateId: String); suspend fun markTutorialSeen(dateId: String); suspend fun endInChat(dateId:
  String): Outcome<EndedInChat>; suspend fun markEndSeen(dateId: String); suspend fun matchSends(reason:
  ChatReason?, body: String): Result<Boolean>; suspend fun matchEndsInChat(): Result<Boolean> }`
  (`endInChat` maps 409 to `Outcome.Failure(CallFailure.Conflict)` — add `Conflict` to `CallFailure` if
  absent).
- `ChatRules.kt`: `fun endCardVisible(messages: List<ChatMessage>, selected: ChatReason?): Boolean`;
  `fun fieldLocked(selected: ChatReason?, guide: Guide): Boolean`; `fun canSend(text: String, locked:
  Boolean): Boolean`; `fun countdown(now: Instant, meetAt: Instant): String?` (`in {h}h {m}m`, null at or
  after `meetAt`); `fun prefillEdited(reason: ChatReason?, sent: String): Boolean?`; `fun
  cantMakeItFrom(messages): String?` (`self` / `match`, the latest wins).
- `enum class Guide { None, Locked, Acked }`.
- `data class ChatUi(val messages: List<ChatMessage>, val selected: ChatReason?, val draft: String, val
  guide: Guide, val endSheet: Boolean, val sending: Boolean, val sendFailed: Boolean, val outcome:
  EndedInChat?)`.
- `class ChatViewModel(store: ChatStore, dates: DateStore, analytics: Analytics, clock: () -> Instant)`:
  `fun open(date: ActiveDate, entry: ChatEntry)`, `fun pick(reason: ChatReason, surface: ChipSurface)`,
  `fun edit(text: String)`, `fun ackGuide()`, `fun send()`, `fun openEndSheet()`, `fun keepDate()`, `fun
  endDate()`, `fun onScreen(visible: Boolean)` (starts / stops the 5 s poll of `thread` and `dates.active`),
  `fun close()`; `val ui: StateFlow<ChatUi>`. One instance per date for the app session (held by the host,
  replaced when the date id changes).
- `enum class ChatEntry(val wire: String) { OpenChat("open_chat"), ReadReply("read_reply"),
  Tutorial("tutorial"), Notification("notification") }`; `enum class ChipSurface(val wire) { Entry("entry"),
  Dock("dock") }`.
- `ChatAnalytics` builds every family L payload in the ticket's table, nothing more.

- [ ] **Step 1: Failing rule tests:** `endCardVisible` false with no `cant_make_it`, true with one from
  either side, false while `selected == CantMakeIt`; `fieldLocked(CantMakeIt, Locked)` true,
  `(CantMakeIt, Acked)` false; `countdown(meet − 78 min)` = `in 1h 18m`, `(meet − 45 min)` = `in 0h 45m`,
  `(meet)` = null; `prefillEdited(RunningLate, its draft)` false, edited true, `CantMakeIt` null, `null`
  reason null.
- [ ] **Step 2: Failing model tests** (a fake store and a fake clock; foreground `launch` for collectors):
  - `pick` replaces the draft and selects; `pick(CantMakeIt)` clears the field and sets `Locked`; `ackGuide`
    sets `Acked`; picking another chip sets `None`;
  - `send` posts the selected reason and text, then clears both; `message_sent` carries `reason`,
    `char_count`, `prefill_edited`, `minutes_before_start`, `is_first`, `date_id` and no text;
  - **a failed send keeps the draft and the chip** and adds nothing to the thread;
  - **a poll never touches the dock** (draft, selection, guide unchanged; messages grow);
  - **the end card waits while cant_make_it is selected**;
  - **a poll that finds the date ended shows the outcome**, closing the sheet and dropping the draft;
  - **a 409 on end shows the outcome** from the next date read;
  - `endDate` success → `outcome.endedByYou` true; `end_date_sheet_opened` carries `cant_make_it_from`;
  - the `action_chip_used` payload carries `surface` and fires for every chip tap; `chat_opened` carries
    `entry`, `is_first`, `minutes_before_start`; `chat_closed` carries `messages_sent`, `duration_s`;
  - `ackGuide` fires `cancel_guide_acknowledged` (`date_id`, `read_time_s`, `minutes_before_start`);
  - a poll that brings a message from the match fires `message_received` (`date_id`, `reason`, `char_count`)
    once per message, and marks the thread read; `open` marks it read too;
  - `onScreen(false)` stops polling (no store call after it).
- [ ] **Step 3:** `./gradlew :app:testDebugUnitTest --tests 'com.showup.chat.*'` fails.
- [ ] **Step 4:** Implement.
- [ ] **Step 5:** The chat tests and `com.showup.date.*` pass; `python audit/check-tracker-wiring.py` passes
  (the model takes `analytics =`).
- [ ] **Step 6:** Commit.

### Task 9: Android chat UI

**Files:**
- Create (`com.showup.chat`): `ChatTutorialScreen.kt`, `ChatScreen.kt`, `ChatParts.kt` (`ChatHeader`,
  `ChatBubble`, `ThreadScroll`, `BigActionChip`), `CancelGuide.kt` (+ `EndDateCard`), `EndDateSheet.kt`,
  `DateEndedScreen.kt`, `ChatCopy.kt`.
- Test: `ChatScreenTest.kt`, `ChatTutorialScreenTest.kt`, `DateEndedScreenTest.kt`.

**Interfaces:**
- `ChatTutorialScreen(name: String, onOpen: () -> Unit, onBack: () -> Unit)` — reference lines 189–303.
- `ChatScreen(ui: ChatUi, date: ActiveDate, now: Instant, zone: ZoneId, onBack, onPick: (ChatReason,
  ChipSurface) -> Unit, onEdit: (String) -> Unit, onAckGuide, onSend, onEndCard, onKeep, onEnd)` — B when the
  thread is empty and no chip is selected (reference 409–452), otherwise C–E (484–588); F is `EndDateSheet`
  over it when `ui.endSheet`.
- `DateEndedScreen(outcome: EndedInChat, onFindNext: () -> Unit)` — G when `endedByYou`, H otherwise
  (reference 625–700).
- `ThreadScroll`: the indicator (track `rgba(29,17,41,.06)`, thumb `.34`, inset 8, min thumb 30, fades over
  `Motion.TINT`), and the reveal rule (a new guide / collapsed line / card scrolls into view; to its top when
  taller than the viewport, `top − 8`).
- `ChatCopy` holds every string of the ticket's copy section.

- [ ] **Step 1: Failing UI tests** (Robolectric):
  - B shows five tiles in two columns and the privacy line, and no field;
  - tapping a tile calls `onPick(_, Entry)`; C shows the draft in the field and the chip selected;
  - with `Guide.Locked` the field is disabled with `Read the note above, then tap Got it`, send disabled,
    the guide's title and `Got it` shown; with `Acked` the collapsed line and the placeholder
    `Explain why and say sorry to Leonie…`;
  - the dock has no "Tap a chip" text; compact chips are 36 dp high;
  - the card shows `Not meeting today?` and `End date` only with a `cant_make_it` message;
  - the sheet shows `Keep the date` above `End date`; a scrim tap calls `onKeep`;
  - G reads `You ended the date` with the you-told row; H reads `Leonie had to cancel the date`; both CTA
    `Find your next date`;
  - the tutorial has two steps and `Open the chat`.
- [ ] **Step 2:** They fail.
- [ ] **Step 3:** Implement from the reference (values there; tokens where one holds the value). The keyboard:
  `imePadding()` on the chat column, the thread `weight(1f)`.
- [ ] **Step 4:** The UI tests pass.
- [ ] **Step 5:** Commit.

### Task 10: Android routing, the Date screen's entry points, the dev menu

**Files:**
- Modify: `AppRoute.kt` (`ChatTutorial`, `Chat`, `DateEnded`), `MainActivity.kt`, `date/DateRouting.kt`,
  `date/DateViewModel.kt` (15 s poll while on screen; `DateRead.Ended` → outcome), `dev/DevJump.kt`,
  `dev/DevMenu.kt`, `debug/.../CheckInGalleryActivity.kt` (the States page frames A–H).
- Test: `date/DateRoutingTest.kt`, `chat/ChatRoutingTest.kt`, `dev/DevStatesTest.kt`.

**Interfaces:**
- `fun chatRoute(date: ActiveDate): AppRoute` — `ChatTutorial` when `!date.chatTutorialSeen`, else `Chat`.
- `Open the chat` → `markTutorialSeen` then `Chat` with entry `Tutorial`; back from either → the Date screen.
- An outcome (`DateRead.Ended` or a model `outcome`) → `DateEnded`; `Find your next date` → `markEndSeen`
  then Search, with Search, Likes and Instant unlocked (the date read is `Ended` / `None`).
- Dev actions: "Match sends a message", "Match can't make it" (a `cant_make_it` message), "Match ends the
  date"; States page: A–H frames.
- The host fires `screen_viewed` for `date_chat_tutorial`, `date_chat` (B and C–E are one screen, one view
  per open) and `date_ended_in_chat` (G and H); `chat_tutorial_shown` (`date_id`) when the tutorial appears;
  `chat_tutorial_acknowledged` (`date_id`, `read_time_s`) on `Open the chat`.

- [ ] **Step 1: Failing tests:** `chatRoute` both ways; Read & reply passes `ReadReply`; the outcome routes to
  `DateEnded` and `Find your next date` lands on Search; the three screen views and two tutorial events fire
  with their payloads; `DEV_STATES["194"]` lists A–H.
- [ ] **Step 2:** They fail.
- [ ] **Step 3:** Implement. The chat model lives in `MainActivity` beside `DateViewModel`, keyed by date id.
- [ ] **Step 4:** `com.showup.chat.*`, `com.showup.date.*`, `com.showup.dev.*`,
  `com.showup.welcome.FlowRestorationTest` pass; `assembleDebug` passes.
- [ ] **Step 5:** Commit.

### Task 11: iOS — Tasks 8 to 10

**Files:** Swift twins under `ios-app/ShowUpWelcome/`: `ChatRepository.swift`, `ChatRules.swift`,
`ChatModel.swift` (`@MainActor @Observable final class`), `ChatAnalytics.swift`, `ChatSamples.swift`,
`ChatTutorialScreen.swift`, `ChatScreen.swift`, `ChatParts.swift`, `CancelGuide.swift`, `EndDateSheet.swift`
(on `SheetScaffold`), `DateEndedScreen.swift`, `ChatCopy.swift`; modify `AppRoute.swift`, `FlowScreen.swift`,
`ShowUpWelcomeApp.swift`, `DateModel.swift`, `DevMenu.swift` (chat actions, and 193's Date actions it
lacks); tests `ChatRulesTests`, `ChatModelTests`, `ChatRepositoryTests`, `DevMenuRulesTests`.

**Interfaces:** the same names and signatures as Tasks 8–10 in Swift (`ChatStoring` protocol, `ChatUi`,
`Guide`, `ChatEntry`, `ChipSurface`, `chatRoute(_:)`). The polling `Task` is cancelled in `onScreen(false)`
and on deinit. The keyboard is the system's: no manual inset. The field grows to 90 then scrolls.

- [ ] **Step 1:** Port the tests first (the same cases as Tasks 8–10, including the five Review Focus
  cases); regenerate the project (`python gen_pbxproj.py`).
- [ ] **Step 2:** Implement.
- [ ] **Step 3:** `check-swift-structure`, `check-swift-arg-order`, `check-swift-concurrency`,
  `check-ios-availability` pass locally; push and read the iOS CI job (build, unit tests) — green.
- [ ] **Step 4:** Commit (with the CI run id in the ledger).

### Task 12: The fit sweep and the evidence

**Precondition:** PR #172 merged into development (its CI green); `git merge origin/development` into this
branch.

**Files:**
- Create: `android-preview-project/app/src/test/java/com/showup/fit/ChatFitTest.kt`,
  `ios-app/ShowUpWelcomeTests/ChatFitTests.swift`.
- Modify: `fit/EvidenceScreenshots.kt` (`SHOWUP-194` A–H), `ShowUpWelcomeTests/EvidenceShots.swift`.

**Interfaces:** Uses the 193 audit's `measureFit(…, extra = …)`, `FitFrame` (`tapTargets`, `lineBreaks`,
`tagged`), `DEVICES + EDGE_DEVICES`, all seven Android font steps and iOS `.large … .accessibility5`. New
tags / anchors: `chat:thread`, `chat:dock`, `chat:field`, `chat:send`, `chat:guide`, `chat:got-it`,
`chat:end-card`, `chat:sheet`, `chat:cta`.

- [ ] **Step 1: Failing sweeps** for A–H on every phone and type size: the shared checks plus the 193 rules
  (48 dp / 44 pt, no overlapping touch areas, whole words); the dock never covers the thread's last item at
  the end of its scroll; the tutorial CTA and the outcome CTA on screen; and **at 375 × 667, D's guide title
  and `Got it` lie inside the thread's visible part** after the reveal.
- [ ] **Step 2:** Run (Android locally, iOS on CI); fix every finding at its cause.
- [ ] **Step 3:** Evidence: A–H at the three frames on both platforms; C–E with a keyboard-sized inset
  (Android `keyboardDp`, iOS a fixed bottom inset) — only the evidence simulates it. Look at every image
  against the spec sheet; fix what differs.
- [ ] **Step 4:** Commit.

### Task 13: `audit/verify-chat.py`, the tracking flags, the PR

**Files:**
- Create: `audit/verify-chat.py`.
- Modify: `design_handoff_showup/tracking/events.json` (`implemented: true` for the events both apps and
  the server now fire), the PR body.

- [ ] **Step 1:** `verify-chat.py` quotes the ticket's copy and checks both platforms' `ChatCopy`, the five
  shown chips in order and the three hidden, no "Tap a chip" string anywhere, and no retired event name in
  either app; it passes.
- [ ] **Step 2:** All `audit/verify-*.py` / `check-*.py` pass; Android `testDebugUnitTest` (narrow runs, then
  CI), server unit + e2e + `test:cov`, iOS CI — green.
- [ ] **Step 3:** Open the PR to development with the IMPLEMENTATION COMPLETE block, the evidence, the
  proposed copy for Philipp (none expected beyond the spec's §9) and the recording still owed by a device.
- [ ] **Step 4:** Commit.
