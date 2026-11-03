# Date 01 Date screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build SHOWUP-193's Date screen (states A, B, C, D) on Android and iOS, with the server payload it
reads, the locked TabBar, the read-only profile, the OS hand-offs and the ticket's tracking.

**Architecture:**
- A new NestJS module `date-screen` composes `DatesService` and `SearchService` into `GET /me/date` and
  `GET /me/date/profile`.
- The dates module gains the chat read marker and the `coordinate_unlocked` sweep.
- Each app gets a `date` feature in the existing per-platform shape:
  - pure rules;
  - a repository over the generated client;
  - a view model / `@Observable` model;
  - stateless views.
- The screen is reached as a new `FlowScreen.Date` value.

**Tech Stack:** NestJS + TypeORM + Postgres + BullMQ; Kotlin + Compose (Robolectric tests); Swift +
SwiftUI (XCTest on CI only); OpenAPI-generated clients on both platforms.

**Spec:** `docs/superpowers/specs/2026-11-03-date-01-date-screen-design.md`. The ticket text is SHOWUP-193
in Jira; the reference is `design_handoff_showup/date/screen-date-reference.jsx` (copied in by Task 1).

## Global Constraints

- **Order of authority:** reference wins on numbers · ticket wins on behaviour, scope and copy · PNG wins
  on nothing.
- **Copy:** exact strings from the ticket's *Copy — final strings*, including `It's a date.` (straight
  apostrophe, as the ticket types it) and `Tap End date when you’re done — then a short review.` (curly,
  as the reference has it).
- **Product terms capitalise exactly:** Show Up · Show-up Rate · Match Mate · Check-In.
- **No Show-up percentage anywhere.** `ShowUpCount` (`sm` on the intent card, `xs` in About).
- **Order Ride is never rendered**; `ride` is never emitted.
- **The Match Mate slot renders nothing.**
- **Footer** = one sticky container: `CheckInBar` while `!checkedIn`, `DateStatusBar` (End Date) while
  `checkedIn`, the TabBar below.
- **TabBar during a date:** no active tab; `search`, `likes` and `instant` disabled; `settings` open; hint
  `Available again once your date is finished.` for 3200 ms.
- **Later-ticket controls** (Open chat, Read & reply, Report a problem, Cancel this date, Check-in, End
  date, End Date) are rendered and call named callbacks that the host wires to no-ops.
- **No absolute Y.** One scroll container. Card heights from content. Only C's preview clamps (2 lines).
- **Tokens only** (`Spacing`, `Radius`, `IconSizes`, `Motion`, `DesignSystem` colours). Raw kit
  numbers that no token holds stay local, with a reason in a comment.
- **Both platforms change in the same PR.** iOS bans `@unchecked Sendable`, `nonisolated(unsafe)`,
  `@preconcurrency` and mutable `static var`. No emoji. No logging of a phone number, a code, a token or
  an email.
- **Tracking from `enums.json` / `events.json` registry 1.4.32 / taxonomy 1.4.25**; screen ids from §11
  enums, never literals.
- **Never in a payload:** coordinates, distances in metres, chat text, addresses.
- **Fit:** every phone in the sweep at 1x / 1.3x / 2x, with 48 dp / 44 pt targets.
- **Evidence:** A–D at 375 × 667, 390 × 844 and 430 × 932.

## Review Focus

1. **A long venue name, address or match name at 320 wide and 2x type wraps and never truncates.** Its
   fit-sweep rows live in Task 11 (Android) and Task 13 (iOS).
2. **A date tomorrow** reads `You and {name} are meeting tomorrow`, and the locked line reads `Messaging
   available tomorrow from {HH:mm}`. Tested in `DateRulesTest` (Task 8) and its Swift twin (Task 13).
3. **The app is backgrounded across the unlock and resumed:** it lands on B, with no second
   `date_screen_viewed` and no `screen_viewed`. Tested in the `DateViewModelTest` case "foreground after
   unlock shows B without a visit event" (Task 10), and its twin.
4. **No venue, no photo, hidden age, no district or distance:** the screen renders without the missing
   pieces, with no placeholder text. The "sparse" fit row is in Tasks 11 and 13; the payload omissions
   are tested in Task 3.
5. **A rapid double tap on a disabled tab** restarts the 3200 ms hint rather than stacking it. Tested in
   `TabBarDisabledTest` (Task 9) and its twin.

---

### Task 1: Handoff files and registry 1.4.32 / taxonomy 1.4.25

**Files:**
- Create: `design_handoff_showup/date/` (README.md, 07a-decisions.md, screen-date-reference.jsx,
  spec-sheets/01-date-screen.png, tickets/00-epic.md, tickets/01-date-screen.md), copied from the 3 Nov
  zip unpack (`scratchpad/s193/z23/design_handoff_showup/date/`).
- Modify: `design_handoff_showup/tracking/{events.json,enums.json,properties.json,requirements.json,README.md,RECONCILIATION.md,ALIGNMENT-BRIEF.md}`,
  merged from the zip while keeping the repo's `implemented` flags (the 188 recipe).
- Modify: `src/modules/analytics/registry/enums.json` (backend copy: `registry_version` 1.4.32,
  `taxonomy_version` 1.4.25).
- Modify: Android `profile/ProfileAnalytics.kt` (`FIELD_REGISTRY_VERSION = "1.4.32"`) and iOS
  `ProfileAnalytics.swift` (the same stamp).

**Interfaces:** Produces §11 `date_screen` and §38 `date_quick_action`
(`directions, ride, share, calendar, copy_address`), plus `date_screen_viewed` / `date_quick_action_tapped`
/ `coordinate_unlocked` in `events.json`.

- [ ] **Step 1:** Write the merge as a scratchpad script (`-I`). Zip values win except `implemented`,
  which keeps the repo's value.
- [ ] **Step 2:** Run every `audit/check-*.py` and `verify-*.py`. Expect 19/19. Fix any checker that
  pins the old stamp by moving it to 1.4.32.
- [ ] **Step 3:** Commit `chore(tracking): registry 1.4.32 / taxonomy 1.4.25 and the Date handoff (SHOWUP-193)`.

### Task 2: Server schema

**Files:**
- Create: `src/database/migrations/1717000032000-DateScreen.ts`
- Modify: `src/modules/dates/entities/date.entity.ts`
- Test: covered by the e2e in Task 3. The e2e global setup runs the migrations.

The migration:
- `venues.address varchar(300) NULL`, with the demo rows backfilled in Berlin street form (e.g.
  `Oranienstrasse 42, Kreuzberg`);
- `dates.coordinate_unlocked_at timestamptz NULL`;
- `dates.a_chat_read_at timestamptz NULL` and `dates.b_chat_read_at timestamptz NULL`.

The down migration drops all four.

**Interfaces:** `DateEntity.coordinateUnlockedAt: Date | null`, `aChatReadAt: Date | null`,
`bChatReadAt: Date | null`.

- [ ] **Step 1:** Write the migration and the entity columns.
- [ ] **Step 2:** `npx tsc --noEmit -p tsconfig.json` passes. `npm run migration:run` against the
  local Docker DB prints `DateScreen1717000032000 has been executed successfully`.
- [ ] **Step 3:** Commit.

### Task 3: `GET /me/date`

**Files:**
- Create:
  - `src/modules/date-screen/util/active-date.ts` (pure)
  - `src/modules/date-screen/util/active-date.spec.ts`
  - `src/modules/date-screen/dto/active-date.dto.ts`
  - `src/modules/date-screen/date-screen.service.ts`
  - `src/modules/date-screen/date-screen.controller.ts`
  - `src/modules/date-screen/date-screen.module.ts`
  - `test/date-screen.e2e-spec.ts`
- Modify: `src/app.module.ts` (import the module); `src/modules/search/search.service.ts` (make
  `attendedCounts` reachable, and add a public
  `partnerSummary(viewerId, partnerId, now, zone): Promise<PartnerSummary | null>` that reuses
  `buildSearchProfile`'s pieces: first photo, age unless hidden, district, rounded distance, attended
  count).

**Interfaces:**
- Pure:
  - `sharedInOrder(theirs: string[], mine: string[]): string[]`, in the match's order;
  - `unreadOf(messages: {senderId, reason, body, createdAt}[], viewerId, readAt: Date | null): UnreadDto | null`.
- `ActiveDateDto`:

  ```
  { id: string; meetAt: string; meetDay: 'today'|'tomorrow'; unlockAt: string; coordinateOpen: boolean;
    place: { name: string; address: string | null } | null;
    match: { userId: string; name: string; age?: number; district?: string; distanceKm?: number;
             photoUrl?: string; attendedCount: number };
    motivations: { you: string | null; them: string | null };
    sharedVibes: string[]; sharedInterests: string[];
    unread: { count: number; reason: string; body: string | null; sentAt: string } | null;
    checkedIn: boolean }
  ```

- `GET /me/date?tz=` (`operationId: getActiveDate`, tag `dates`) returns 200 `ActiveDateDto`, or 204.
- The active date is the viewer's newest date for which `dateLocksUser(row, viewerId)` holds.
- `meetAt` = `scheduled_at`; `unlockAt` = `chatOpensAt(scheduled_at)`; `meetDay` = `search/util/search`'s
  `meetDay`.
- Motivations and vibes come from each person's Check-In window that covers `meetAt`, falling back to
  their latest committed one.

- [ ] **Step 1: Failing unit tests** in `active-date.spec.ts`:
  - `sharedInOrder(['a','b','c'], ['c','a'])` equals `['a','c']`, and `sharedInOrder(['a'], [])` equals `[]`;
  - `unreadOf` counts only messages from the other person newer than `readAt`, returns the newest one's
    reason, body and `sentAt`, and returns `null` when there are none.
- [ ] **Step 2:** `npx jest src/modules/date-screen` fails with "Cannot find module".
- [ ] **Step 3:** Implement the util, the DTO, the service and the controller (204 via
  `@HttpCode` + `res.status(204)`, or by returning `undefined` with `@ApiNoContentResponse`, as Search's
  204s do).
- [ ] **Step 4: e2e**, two users with committed Check-Ins matched through the real like path (as
  `test/search-ranking.e2e-spec.ts` builds pairs):
  - no date → 204;
  - after the match → 200 with `match.name`, `place.name`, `coordinateOpen` false for a meet time 3 h
    ahead;
  - `sharedVibes` only shared;
  - `motivations.them` equals the partner's;
  - a hidden age is absent from the JSON.
- [ ] **Step 5:** The unit tests and `npx jest --config ./test/jest-e2e.json test/date-screen.e2e-spec.ts`
  pass.
- [ ] **Step 6:** Commit.

### Task 4: `GET /me/date/profile` and the chat read marker

**Files:**
- Modify: `date-screen.controller.ts` and `.service.ts` (profile); `src/modules/dates/dates.controller.ts`
  and `dates.service.ts` (read marker).
- Test: extend `test/date-screen.e2e-spec.ts`.

**Interfaces:**
- `GET /me/date/profile?tz=` (`getActiveDateProfile`) returns `SearchProfileDto` through a new public
  `SearchService.profileFor(viewerId, partnerId, now, zone)` that wraps `loadProfiles`. 404 when there
  is no active date.
- `POST /dates/:id/chat/read` (`markDateChatRead`) returns 204 and stamps `a_chat_read_at` or
  `b_chat_read_at`. It is 404 for a non-participant.

- [ ] **Step 1: Failing e2e:**
  - the profile returns the partner's `name` and `photos`, and its JSON has no `distanceMeters`;
  - the partner sends a chat message → `GET /me/date` has `unread.count` 1;
  - `POST .../chat/read` → `unread` is null;
  - a third user's read → 404.
- [ ] **Step 2:** Implement. **Step 3:** The e2e passes. **Step 4:** Commit.

### Task 5: The `coordinate_unlocked` sweep

**Files:**
- Create: `src/modules/dates/jobs/coordinate-unlock.scheduler.ts`, `coordinate-unlock.processor.ts`,
  `src/modules/dates/util/coordinate-unlock.ts` (+ `.spec.ts`).
- Modify: `src/queue/queue.constants.ts` (`QUEUE_COORDINATE_UNLOCK`); `dates.module.ts`;
  `src/modules/analytics/events/date-events.ts` (`coordinateUnlockedEvent(minutesBeforeStart)`).

**Interfaces:**
- `dueForUnlock(rows, now): string[]` returns the ids of confirmed dates with `unlockAt <= now` and
  `coordinateUnlockedAt === null`.
- `minutesBeforeStart(meetAt, now): number` is negative after the meet time and rounded toward zero.
- The scheduler mirrors `LikeExpiryScheduler`, with id `coordinate-unlock-every-minute` and `every: 60_000`.
- The processor stamps each due row inside one UPDATE ... RETURNING, so two workers can't double-fire.
  It then calls `analytics.trackServerEvent` once per participant, with
  `coordinate_unlocked { minutes_before_start }`.

- [ ] **Step 1: Failing unit tests:**
  - `dueForUnlock` picks a date 89 min before its start, skips one at 91 min, and skips one already
    stamped;
  - `minutesBeforeStart` gives 90 at the unlock, 0 at the meet time and −15 a quarter after;
  - `coordinateUnlockedEvent(90)` builds the envelope with event name `coordinate_unlocked`.
- [ ] **Step 2:** Implement. **Step 3:** The unit tests pass. The full `npm run -s test:cov` passes its
  coverage gate.
- [ ] **Step 4:** Commit.

### Task 6: Dev endpoints and the contract

**Files:**
- Create: `src/modules/date-screen/date-dev.controller.ts` (`@Controller('dev/date')`, tag `dev`,
  production → 404 like `SearchDevController`).
- Modify: `openapi.json` and the iOS copy via `npm run -s openapi:emit`.

**Interfaces:**
- `POST /dev/date` (`createDevDate`), body `{ minutesFromNow: number }`. It creates or reuses the test
  partner `Leonie` (age 28, district Neukölln, motivation equal to the caller's, two vibes and two
  interests overlapping the caller's, no photo). It ends any open test date, then creates a confirmed
  date at now + `minutesFromNow` with the nearest demo venue. Returns 204.
- `POST /dev/date/message` (`sendDevDateMessage`), body `{ reason: string; body?: string }`, sends as
  the partner through `DatesService.sendChatMessage`. Returns 204.
- `POST /dev/date/end` (`endDevDate`) cancels the caller's active date. Returns 204.

- [ ] **Step 1:** Failing e2e: `POST /dev/date {minutesFromNow: 60}`, then `GET /me/date` gives
  `coordinateOpen: true`; `/dev/date/end` → 204.
- [ ] **Step 2:** Implement. Emit openapi. Run the welcome `api` package build and `npm run -s test:cov`
  (contract-change trap).
- [ ] **Step 3:** Commit.

### Task 7: Android generated client and repository

**Files:**
- Modify: `api/ShowUpApi.kt` (add `val dates: DatesApi by lazy { … }` if it isn't exposed yet).
- Create: `date/DateRepository.kt`, `date/ActiveDate.kt` (the app's value types mapped from the
  generated DTO), `date/DevDates.kt` (the dev calls).
- Test: `app/src/test/java/com/showup/date/DateRepositoryTest.kt` (MockWebServer, as
  `PhotosReorderTest`).

**Interfaces:**
- `data class ActiveDate(id, meetAt: Instant, meetDay: MeetDay, unlockAt: Instant, coordinateOpen: Boolean, place: Place?, match: DateMatch, motivationYou: String?, motivationThem: String?, sharedVibes: List<String>, sharedInterests: List<String>, unread: Unread?, checkedIn: Boolean)`.
- `sealed interface DateRead { data class Active(val date: ActiveDate) : DateRead; data object None : DateRead; data object Failed : DateRead }`.
- `open class DateRepository(api: ShowUpApi)`:
  - `open suspend fun active(zone: String): DateRead` — 200 → Active, 204 → None, anything else or no
    answer → Failed;
  - `open suspend fun partnerProfile(zone: String): SearchProfile?`, mapped with Search's existing mapper.

- [ ] **Step 1:** Failing tests: 200 maps every field; 204 → `None`; 500 → `Failed`; a dropped socket →
  `Failed`.
- [ ] **Step 2:** Implement. **Step 3:** `./gradlew :app:testDebugUnitTest --tests 'com.showup.date.*'`
  passes. **Step 4:** Commit.

### Task 8: Android `DateRules`

**Files:** Create `date/DateRules.kt` and `date/DateCopy.kt` (every ticket string as a constant). Test:
`date/DateRulesTest.kt`.

**Interfaces:**
- `enum class DateState { Locked, Available, Unread }` and
  `fun dateState(now: Instant, unlockAt: Instant, unread: Unread?): DateState`.
- `fun minutesBeforeStart(meetAt: Instant, now: Instant): Int`, the same rule as the server's.
- `fun lockedLine(unlockAt: Instant, now: Instant, zone: ZoneId): String`, giving `Messaging available from 17:30` or
  `Messaging available tomorrow from 17:30`.
- `fun shareText(name: String, day: MeetDay, meetAt: Instant, place: Place?, zone: ZoneId): String`, giving
  `I'm meeting Leonie today at 19:00 at Cafe Krone, Oranienstrasse 42, Kreuzberg.`. With no place:
  `I'm meeting Leonie today at 19:00.`
- `data class CalendarEvent(title, start: Instant, end: Instant, location: String?)` and
  `fun calendarEvent(date: ActiveDate): CalendarEvent`, giving title `Date with Leonie` and a 1 h event.
- `fun unreadTime(sentAt: Instant, now: Instant, zone: ZoneId): String`, giving `now` under 60 s and `HH:mm`
  otherwise.
- `fun aboutLine(district: String?, distanceKm: Double?): String?`, giving `Neukölln · ~1 km away`,
  `< 1 km away`, or null when both are absent. It reuses Search's distance formatter.
- `fun intentShared(you: String?, them: String?): Boolean`, true only when both are non-null and equal.
- `fun meetLine(place: Place?, time: String): String`, giving `Meet at Cafe Krone at 19:00` or
  `Meet at 19:00`.

- [ ] **Step 1:** Failing tests covering every example above, plus:
  - `dateState` flips exactly at `unlockAt` (Locked 1 s before, Available at the instant, Unread when
    `unread != null` and open);
  - a date tomorrow (Review Focus 2).
- [ ] **Step 2:** Implement. **Step 3:** Pass. **Step 4:** Commit.

### Task 9: TabBar disabled state (both platforms, shared component)

**Files:**
- Modify: Android `designsystem/TabBar.kt`; iOS `TabBar.swift`.
- Test: Android `designsystem/TabBarDisabledTest.kt` (Compose UI with a test clock); iOS
  `ShowUpWelcomeTests/TabBarDisabledTests.swift`.

**Interfaces:**
- Android: `TabBar(active: Tab?, onTab, modifier, likes = 0, disabled: Set<Tab> = emptySet(), disabledHint: String? = null)`.
- iOS: `TabBar(active: AppTab?, onTab:, likes:, disabled: Set<AppTab> = [], disabledHint: String? = nil)`.
- Existing callers are unchanged, because `active` stays non-null for them.
- Visuals per the kit:
  - disabled colour `--liq-fg-faint`;
  - lock badge 14 at right −7 / bottom −3, `--liq-bg` fill, 1.5 border fg-faint, lock 8 stroke 3,
    fg-muted;
  - hint 10 above the bar, insets 14, max width 320, padding 11/14, r 14, fg fill, white Manrope 13/500
    lh 1.4, lock 14 in orange-400, shadow 0 12 28 rgba(20,12,28,.22);
  - shown with opacity and translateY 8 → 0 over 200 ms;
  - hidden after 3200 ms; a re-tap restarts the timer.
- A disabled tap never calls `onTab`.

- [ ] **Step 1:** Failing tests:
  - a disabled tap shows the hint and doesn't call `onTab`;
  - the hint is gone at 3200 ms;
  - a second tap at 3000 ms keeps it up until 6200 ms (Review Focus 5);
  - `Settings` still calls `onTab`;
  - each tab keeps a 48 dp target.
- [ ] **Step 2:** Implement on both platforms. **Step 3:** The Android tests pass. The existing
  `ScreenFitTest` still passes for every TabBar caller. **Step 4:** Commit.

### Task 10: Android `DateViewModel` and analytics

**Files:**
- Create: `date/DateViewModel.kt` and `date/DateAnalytics.kt`.
- Modify: `profile/ProfileAnalytics.kt` (`ProfileScreen.DateScreen("date_screen", "Date - Date screen")`
  from §11).
- Test: `date/DateViewModelTest.kt` (fake repository, `RecordingAnalytics`, test dispatcher, injectable
  clock).

**Interfaces:**
- `class DateViewModel(repo: DateRepository, analytics: AnalyticsTracker?, clock: () -> Instant, zone: () -> ZoneId) : ViewModel`.
- `state: StateFlow<DateUi>` where
  `DateUi(date: ActiveDate?, now: Instant, loading: Boolean, failed: Boolean, copied: Boolean, left: Boolean)`.
- Functions:
  - `arrived(referrer: ProfileScreen?)` fires `screen_viewed` and `date_screen_viewed { date_id, state: locked|available, has_unread, checked_in, minutes_before_start }`
    once per call;
  - `foreground()` re-reads, sets `left = true` on `None` (the host leaves), and fires nothing;
  - `quickAction(action: QuickAction)`, with `QuickAction { Directions, Share, Calendar, CopyAddress }`,
    fires `date_quick_action_tapped` with the §38 value;
  - `copyShown()` keeps `copied` true for 1600 ms;
  - `tick()` advances `now` on a 1 s ticker while started, for the in-place unlock (no event).
- `DateAnalytics` builders take the §38 value through an enum, never a literal.

- [ ] **Step 1:** Failing tests:
  - `arrived` fires the two events with exact payloads in state A, B and C;
  - the unlock flips `dateState` with zero new events;
  - "foreground after unlock shows B without a visit event" (Review Focus 3);
  - a 204 on foreground sets `left`;
  - each quick action sends its §38 value, and `ride` is never produced;
  - a disabled-tab tap is not routed here at all (asserted in Task 12's host test).
- [ ] **Step 2:** Implement. **Step 3:** Pass. **Step 4:** Commit.

### Task 11: Android Date screen UI

**Files:** Create in `date/`:
- `DateScreen.kt` (`fun DateScreen(ui: DateUi, onOpenChat, onReport, onDirections, onShare, onCalendar, onCopy, onSeeProfile, onCancel, onCheckIn, onEndDate, onTab)`);
- `CoordinateModule.kt`, `CopyAddress.kt`, `DateWorksCard.kt`;
- `DateFooter.kt` (`CheckInBar`, `DateStatusBar`, `DateFooter(checkedIn, name, …, tabBar)`);
- `DateHeroAndCards.kt` (hero, time + place card, Report row, intent card, quick actions, About, common).

Test:
- `fit/DateFitTest.kt`;
- rows added to `fit/ScreenFitTest.kt`;
- `fit/EvidenceScreenshots.kt` (`shoot("SHOWUP-193", "A"…"D")` at the three sizes);
- `date/DateScreenTest.kt` (semantics: order, the hoist, slot emptiness, the 1600 ms Copied).

**Interfaces:** Consumes `DateUi`, `DateRules`, `DateCopy`, `TabBar(disabled…)`, `ShowUpCount`, `Chip`,
`Card`, and Search's photo loading. All numbers come from the reference, as listed in the spec §5.
Motion:
- the count badge's pulse ring (scale 1 → 2.2, alpha .6 → 0, 1600 ms, infinite, off under reduced motion);
- Copy colours tween 160 ms;
- press scale 0.98 with `Motion` duration.

- [ ] **Step 1: Failing `DateScreenTest`:**
  - A order is hero → time card → Coordinate → Report → intent → actions → About → common → how →
    Cancel;
  - C puts Coordinate before the time card and the node count of "Coordinate with" is 1;
  - no node contains `%`;
  - no `Order Ride`;
  - no Match Mate text;
  - `Report a problem` exists in A, B and C;
  - `End date` exists while `!checkedIn` and `End Date` while `checkedIn`;
  - the footer's top Y is equal in both, so there's no layout jump.
- [ ] **Step 2:** Implement until the tests pass.
- [ ] **Step 3: Fit rows:**
  - A, B, C, D;
  - "long" (a 40-char venue, an 80-char address, `Maximiliana` as the name; Review Focus 1);
  - "sparse" (no venue, no photo, no age, no district or distance; Review Focus 4);
  - "tomorrow";
  - each on every device at 1x / 1.3x / 2x.

  Expected: no clipped text, no target under 48 dp. Run
  `./gradlew :app:testDebugUnitTest --tests 'com.showup.fit.DateFitTest' --tests 'com.showup.date.*'`,
  then `./gradlew --stop`.
- [ ] **Step 4: Evidence:** run `./gradlew :app:testDebugUnitTest -Pevidence --tests '*EvidenceScreenshots*'`.
  Look at A–D at 375 / 390 / 430 next to the spec sheet. Fix every visible difference.
- [ ] **Step 5:** Commit.

### Task 12: Android routing, hand-offs and the read-only profile

**Files:**
- Modify:
  - `welcome/FlowScreen.kt` (`Date`);
  - `MainActivity.kt` (the launch read before Check-In, `like matched` / confirm won → Date, the
    foreground re-read, Settings from Date with back → Date, the `AppRoute.SearchProfile` read-only
    entry);
  - `search/SearchProfileScreen.kt` (`readOnly: Boolean`: no action bar, back pill, no Search events);
  - `search/SearchViewModel.kt` (on `LikeResult.matched` and on confirm won, call `onMatched()`).
- Create: `date/DateHandoffs.kt`:
  - `fun openDirections(context, place)` (`geo:0,0?q=Uri.encode("$name, $address")`, with a chooser);
  - `fun shareDate(context, text)` (`ACTION_SEND` text/plain with a chooser);
  - `fun addToCalendar(context, event)` (`ACTION_INSERT` with `CalendarContract.Events.CONTENT_URI`,
    `TITLE`, `EVENT_LOCATION`, `EXTRA_EVENT_BEGIN_TIME`, `EXTRA_EVENT_END_TIME`);
  - `fun copyAddress(context, text)` (`ClipboardManager`, label `Meeting place`).
- Test: `date/DateRoutingTest.kt` (pure: `launchTarget(dateRead, profileComplete, checkIn)` returns
  `FlowScreen.Date` before any Check-In target), `date/DateHandoffsTest.kt` (Robolectric `shadowOf`
  intents: action, data, extras).

- [ ] **Step 1:** Failing tests:
  - the launch target is `Date` for `Active`, and the old target for `None` or `Failed`;
  - each hand-off intent carries the exact data above;
  - a read-only profile has no `Like`, `Not now` or `Confirm date` node;
  - a disabled-tab tap fires no event (`RecordingAnalytics` is empty).
- [ ] **Step 2:** Implement. **Step 3:** Pass, and the existing Search and Check-In suites pass.
  **Step 4:** Commit.

### Task 13: iOS, everything in Tasks 7–12

**Files (`ios-app/ShowUpWelcome/`):**
- `DateRepository.swift`, `ActiveDate.swift`, `DateRules.swift`, `DateCopy.swift`, `DateModel.swift`
  (`@MainActor @Observable final class`), `DateAnalytics.swift`;
- `DateScreen.swift`, `CoordinateModule.swift`, `CopyAddress.swift`, `DateWorksCard.swift`,
  `DateFooter.swift`, `DateCards.swift`;
- `DateHandoffs.swift`:
  - `UIPasteboard.general.string`;
  - `openURL(https://maps.apple.com/?daddr=…)`;
  - a `ShareSheet` UIViewControllerRepresentable over `UIActivityViewController`, with the written
    reason in the file;
  - `EKEventEditViewController` through a representable over `EKEventStore`
    `requestWriteOnlyAccessToEvents`;
- `Info.plist`: `NSCalendarsWriteOnlyAccessUsageDescription` =
  `Show Up adds your date to your calendar, nothing else.`.

Modify: `FlowScreen.swift`, `ShowUpWelcomeApp.swift`, `AppRoute.swift`, `SearchProfileView.swift`
(`readOnly`), `SearchModel.swift`, `ProfileAnalytics.swift` (`.dateScreen`), `TabBar.swift` (Task 9).

Tests (`ShowUpWelcomeTests/`): `DateRulesTests`, `DateRepositoryTests`, `DateModelTests`, `DateFitTests`
(+ rows in `ScreenFitTests`), `TabBarDisabledTests`, `DateRoutingTests`, with the same cases and values
as the Kotlin twins. Evidence: `EvidenceShots.swift` A–D at the three sizes (the on-screen sRGB recipe).

- [ ] **Step 1:** Write the Swift tests as ports of Tasks 7–12's Kotlin tests, with the same names in
  camelCase.
- [ ] **Step 2:** Implement. Run `python gen_pbxproj.py`, `audit/check-swift-concurrency.py` and
  `check-ios-availability.py` (the `EventKit` write-only API is iOS 17, which is the floor).
- [ ] **Step 3:** Commit. iOS compiles first on CI (no Mac). Fix from the CI log until green.

### Task 14: `audit/verify-date.py`, the dev menu, and the PR

**Files:**
- Create: `mobile/welcome-screen/audit/verify-date.py`.
- Modify: Android `dev/DevJump.kt` / `DevMenu.kt` and iOS `DevMenu.swift`:
  - the States entries `193A–193D`, which open the Date screen with sample values;
  - the actions **Create test date (3 h)**, **Create test date (1 h)**, **Message from Leonie** and
    **End test date**, calling Task 6's endpoints;
  - debug builds only.

`verify-date.py`:
- reads the ticket's *Copy — final strings* at run time from `design_handoff_showup/date/tickets/01-date-screen.md`;
- checks every string in `DateCopy.kt` and `DateCopy.swift`;
- checks that `Order Ride` is absent from both screens, that `date_screen` comes from the enums, and that
  the §38 values match.

- [ ] **Step 1:** Write the checker. Make one ticket string wrong in a scratch copy and confirm both
  platforms fail (the mutation check used for `verify-search.py`). Restore the copy.
- [ ] **Step 2:** Run all checkers (20/20) and the full server `test:cov`.
- [ ] **Step 3:** Push. Open the PR into development, with the IMPLEMENTATION COMPLETE block. Wait for
  CI on all three jobs, using `gh pr checks --watch` in the background.
- [ ] **Step 4:** Request one whole-branch review, then fix the confirmed findings.
- [ ] **Step 5:** Merge on green. Give Esma the emulator walk using the dev menu actions: A → wait or
  "1 h" → B unlocking in place → Message → C → End.
