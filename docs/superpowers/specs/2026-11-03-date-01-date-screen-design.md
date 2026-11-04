# Date 01 — the Date screen (SHOWUP-193): design

Written 3 Nov 2026. Source of truth: the Jira description of SHOWUP-193 (no comments on it). The ticket's
reference implementation (`design_handoff_showup/date/screen-date-reference.jsx`, from the 3 Nov zip) wins
on numbers. The ticket wins on behaviour, scope and copy. The spec-sheet PNG wins on nothing.

## 1. Intent

From the moment two people match until their date ends, the app has one home: the Date screen. It answers
"when, where, with whom". It unlocks a way to coordinate 90 minutes before. It keeps a safety exit in
reach, and it blocks taking a second date. Every later 07a story (chat, check-in, changes, ending,
cancelling, Match Mate) hangs off it, so it has to be built once and extended, never forked.

## 2. Goals, non-goals, anti-goals

**Goals**
- Every acceptance criterion in SHOWUP-193, on Android and iOS, in the same PR.
- Pixel-faithful to the reference at 375 × 667, 390 × 844 and 430 × 932, and clean in the fit sweep on
  every phone size.
- The server payload the screen reads, so it shows real data rather than samples.

**Non-goals** (other 07a tickets)
- The chat and its tutorial (02).
- Check-in pop-ups and the stored location (03).
- The arrival card (04).
- Pickers, change tags and the binding note (05).
- The wrap-up and Report sheets (06).
- The cancel sheets (07).
- The other side's screens (08).
- The +2 h switch (09).
- Match Mate content (10).
- Order Ride.
- Push copy.

**Anti-goals**
- A second copy of `TabBar`, `ShowUpCount` or the Search profile.
- Any Show-up percentage.
- Any absolute Y position.
- A pending suggestion moving the time or place.
- Location, chat text or addresses in analytics.

## 3. Decisions

From Esma, 3 Nov 2026:
- **Footer = the dark Check-in bar** (with its `End date` link) until check-in, then the End Date bar,
  with the TabBar below, in one sticky container. Build what the acceptance criteria describe.
- **Entry points into later tickets are drawn and do nothing yet**:
  - `Open chat` / `Read & reply` (02);
  - `Report a problem` (06);
  - `Cancel this date` (07);
  - `Check-in` and `End date` / `End Date` in the bars (03 / 06).

  Each is a named callback in the screen, wired to a no-op in the host until its ticket lands.
- **About line without a job**: `{area} · {distance}`. No profile question collects a job, and the
  ticket's rule is that missing content is not drawn. To mention to Philipp.

From the ticket, restated because they shape the build:
- **States.**
  - A: more than 90 min before the latest agreed meet time.
  - B: 90 min or less.
  - C: B plus an unread message from the match.
  - D: the full scroll.
- **A → B in place** at meet − 1:30 while the screen is open: no reload, no toast, no event except the
  server's `coordinate_unlocked`.
- **C hoists the same Coordinate card** above the time + place card, and it goes back once the thread is
  opened.
- **No header, no back.** Search, Likes and Instant are disabled. Settings stays open. A disabled tap
  shows the hint for 3200 ms and fires nothing.
- **Order Ride is not rendered.** Quick actions are 3 tiles.
- **Match Mate is an empty slot**: it renders nothing until Date 10.

Builder's calls, mine, flagged for review:
- **Active date** = a date with status `confirmed` that the viewer has not reviewed. This is exactly the
  server's existing matching lock (`dateLocksUser`), so "locked out of Search" and "on the Date screen"
  can never disagree.
- **`{day}`** comes from the server with the viewer's time zone, using Search's own `meetDay` (the
  Check-In's 7 am day). Times are shown in device local time, 24 h, like the rest of the app.
- **`{distance}`** uses Search's rounded formatter (`< 1 km` / `~N km`) plus ` away`, because Search
  decided the exact distance is never sent.
- **C's `{time}`** after "needs a response ·" is the unread message's time: `now` under a minute,
  otherwise `HH:mm`.
- **Unread** = messages from the match newer than the viewer's last read of the thread. Reading the
  thread (`GET /dates/:id/chat`, which Date 02's chat calls when it opens) marks it read. No new endpoint
  is needed.
- **`checked_in`** is a payload field that is always false until Date 03 adds the arrival store. The
  footer swap is built and tested against it now.
- **Venue address**: venues have only a name today, so `venues.address` is added. The demo venues get
  real-looking addresses. Google Places replaces the source in Date 05.

Settled during the builder check (3 Nov), each one a builder's call:
- **Latest agreed meet time** = `dates.scheduled_at`. No suggestion or change history exists yet; Date 05
  adds it, and the payload builder reads it then. The unlock reuses the server's existing
  `chatOpensAt(scheduledAt)` (meet − 90 min), so the chat and the card can never disagree.
- **Unread clears on an explicit `POST /dates/:id/chat/read`** (`markDateChatRead`), not as a side effect
  of a GET. Date 02's chat calls it when the thread opens. Until then C is reached by a message and stays
  until the read.
- **The unread badge's reason** maps the server's six chat reasons onto the reference's `CHAT_REASONS`:
  - `change_meet_time` → time
  - `change_location` → place
  - `on_my_way` → onway
  - `running_late` → late
  - `cant_find_you` → find
  - `cant_make_it_today` → cancel

  The table's other two keys (arrived, when) are carried for 02/03. An unknown reason falls back to the
  first entry, as the reference's `reasonFor` does.
- **Launch order.** Signed in → profile complete → **active date? → Date screen** → otherwise the
  existing Check-In / Search resume. It sits after the session-ended notice and after profile creation,
  and wins over the Check-In gate, because an active date blocks Search anyway. The location gate is a
  Search-tab state and is never reached while a date is active.
- **The date disappears while the screen is open** (cancelled on the other side, or ended): a foreground
  re-read that gets 204 leaves for the normal resume. Date 08's outcome screens replace that later. A 404
  from `/me/date/profile` shows the profile's existing load-failed state, with the back pill.
- **After the meet time** the screen stays in B (`minutes_before_start` negative) until the date ends.
  Leaving is the later tickets' job, and the +2 h switch is Date 09. So testing never gets stuck, the dev
  menu gets **End test date** (`POST /dev/date/end`, debug builds and dev server only).
- **No venue** (the server found none near the pair):
  - the place row and its divider are not drawn, nor are Copy and Directions (no destination);
  - Share and Calendar leave the place out;
  - "How the date works" row 1 reads `Meet at {time}`.

  This is an edge of demo data, to mention to Philipp.
- **The unlock clock is device time**, with the foreground re-read correcting it from the server's
  `coordinateOpen`. The server's `coordinate_unlocked` job uses server time, which is authoritative for
  analytics.
- **iOS calendar.** The purpose string is `Show Up adds your date to your calendar, nothing else.`,
  flagged for Philipp. Directions opens `https://maps.apple.com/?daddr=…`, a universal link, so no
  `LSApplicationQueriesSchemes` entry is needed.
- **The dev partner.** `POST /dev/date` creates or reuses a test partner, "Leonie", with a complete
  profile (age, district, motivation, vibes and interests overlapping the caller's) and no photo, so the
  hero shows the kit's placeholder gradient. A photo arrives with real users.

## 4. Server

**Migration `1717000032000-DateScreen`**
- `venues.address varchar(300) NULL`, with the demo rows backfilled.
- `dates.coordinate_unlocked_at timestamptz NULL`.
- `dates.a_chat_read_at`, `dates.b_chat_read_at timestamptz NULL`.

**`GET /me/date`** (`operationId: getActiveDate`)
- Query: `tz` (IANA), as Search takes it.
- 200 with `ActiveDateDto`, or 204 when there is no active date.

`ActiveDateDto` fields:
- `id`, `meetAt` (latest agreed, ISO), `meetDay` (`today` | `tomorrow`), `unlockAt` (`meetAt` − 90 min),
  `coordinateOpen`.
- `place { name, address }`, nullable when the date has no venue.
- `match { userId, name, age?, district?, distanceKm?, photoUrl?, attendedCount }`.
  - Hidden fields are omitted, as in Search.
  - Distance is rounded server-side.
- `motivations { you?, them? }`. The client shows the intent line only when they are equal.
- `sharedVibes` (§31 ids, in the match's order) and `sharedInterests`, both computed on the server.
- `unread { count, reason, body, sentAt }`, null when nothing is unread.
- `checkedIn` (false until Date 03).

It is built from one query and reuses Search's profile pieces (photo, age, district, distance,
attended count) rather than re-deriving them.

**`GET /me/date/profile`** (`operationId: getActiveDateProfile`)
- Returns the match's `SearchProfileDto`, the same builder as the queue, for "See {name}'s full
  profile".
- 404 when there is no active date.

**`coordinate_unlocked`**
- A once-a-minute BullMQ job, the `LikeExpiryScheduler` shape.
- For every confirmed date whose `unlockAt` has passed and `coordinate_unlocked_at` is null: stamp it and
  send `coordinate_unlocked { minutes_before_start }` through the analytics service.
- Idempotent by the stamp.

**`POST /dates/:id/chat/read`** (`operationId: markDateChatRead`) stamps the viewer's read time. 204.

**Dev only (`/dev`, as `/dev/plan-tier`)**
- `POST /dev/date` creates an active date between the caller and a test partner, with a chosen meet time
  and a demo venue.
- `POST /dev/date/message` sends one message from the partner through the real send path, to reach C.
- `POST /dev/date/end` ends the caller's test date.
- So every state can be walked on the emulator, and a test never gets stuck on the screen.

**Contract**: `openapi.json` is re-emitted. No existing response changes shape.

## 5. Apps (Android and iOS, same PR)

**One screen, `DateScreen`** (Compose / SwiftUI). It takes an `ActiveDate` value and callbacks, and
returns pixels. `DateViewModel` / `@Observable DateModel` owns the load, the foreground re-read, the
unlock timer and tracking.

Structure, from the reference:
- One scroll container holding the sunset wash (top 0, h 320, opacity .18, blur 40, scrolling with the
  content) and the content column (padding 0 16 24, gap 8).
- Column order:
  1. hero (h 196, r 24, the match's first photo, centre 24 %, `It's a date.` Lora 700 italic 30,
     −0.02em, white, shadow 0 2 12 .18)
  2. 12 spacer
  3. [C: Coordinate]
  4. time + place card (`#E8DCF5`)
  5. [A/B: Coordinate]
  6. Report row
  7. intent card (`ShowUpCount` sm; "You both want" orange chips sm, full scroll only, shared only)
  8. quick actions, a 3 × 1 grid
  9. About (photo 92 r 16, `ShowUpCount` xs, "See {name}'s full profile")
  10. What you have in common (lavender chips, shared only)
  11. How the date works
  12. the Match Mate slot (empty)
  13. 24 spacer
  14. `Cancel this date`
  15. 80 spacer
- Then the sticky footer.
- Card heights come from content. Long names and addresses wrap. Only C's preview clamps, at 2 lines.

New pieces, each in its own file and reused by later tickets:
- `CoordinateModule` (locked / available / unread, with the arrival variant left for 04).
- `CopyAddress`.
- `DateWorksCard`.
- `CheckInBar`, built visually per the AC; Date 03 adds behaviour.
- `DateStatusBar`, the not-complete variant only; Date 06 adds the rest.
- `DateFooter`: the one sticky container holding the bar plus the TabBar, swapping on `checkedIn` with
  the bar region keeping its own height so the swap does not jump.

Pure rules (`DateRules`, unit-tested with no UI):
- state from (now, `unlockAt`, unread);
- `minutes_before_start` (negative after);
- "Messaging available from {meet − 1:30}" / "…tomorrow from…";
- the Share text and the Calendar event (title, start, 1 h, location);
- the C time label;
- the distance line.

Changes to shared components:
- **`TabBar`** gains the kit's disabled state on both platforms:
  - `disabled` set, `disabledHint`, no active tab;
  - the lock badge (14, faint ring, lock 8) and faint label colour;
  - the hint above the bar (13 / 500, fg fill, r 14, lock icon orange-400, 200 ms fade + 8 rise, 3200 ms,
    a re-tap restarts it).

  Existing callers are unchanged.
- **Search profile** gains a read-only mode: no action bar, back pill to the Date screen. It is opened
  from `GET /me/date/profile`. It fires `screen_viewed` `search_profile` with referrer `date_screen` and
  no other Search events.

OS hand-offs, each tracked on the tap before handing off:
- **Copy**: clipboard `{place}, {address}`, `Copied` + check + success tint for 1600 ms, colour
  transition 160 ms.
- **Directions**: Android a `geo:0,0?q={place}, {address}` intent (any maps app); iOS Apple Maps
  (`maps://?daddr=`).
- **Share**: the system share sheet with the ticket's sentence.
- **Calendar**: Android `ACTION_INSERT` on `CalendarContract.Events` (no permission); iOS
  `EKEventEditViewController`. This needs `NSCalendarsWriteOnlyAccessUsageDescription`, added to
  `Info.plist` with a purpose string.

Routing:
- **Launch**: the resume logic reads `GET /me/date` before the Check-In read. An active date lands on the
  Date screen.
- **A like that returns `matched`, or a won Confirm date** (Search 01 / 02 / 03), re-reads and lands on
  the Date screen.
- **Every foreground** re-reads, so an agreed change appears in place.
- **Leaving** happens only through later tickets.

Motion:
- C's count badge carries the kit's pulsing ring (`su-pulse-ring`, from the tokens CSS, reduced-motion
  aware).
- A → B swaps in place with no transition.
- C's hoist is a re-layout of the same card, not a second card.
- Press feedback (scale 0.98) on tappable cards and tiles.
- All durations come from the `Motion` tokens or the numbers above.

## 6. Tracking

- Registry merged to **1.4.32 / taxonomy 1.4.25** from the zip, keeping the repo's `implemented` flags.
  This covers the handoff copy, the backend copy and both apps' stamps.
- §11 `date_screen` / `Date - Date screen` goes in both apps' screen enums (never literals).
- `screen_viewed` + `date_screen_viewed { date_id, state, has_unread, checked_in, minutes_before_start }`
  fire once per visit, never on the in-place unlock.
- `date_quick_action_tapped { date_id, action ∈ §38 minus ride, minutes_before_start }` fires on the tap.
- No event on a disabled-tab tap. None of `match_mate_*`, `coordinate_viewed`, `arrival_*`,
  `date_status_*` or `date_cancelled` fire from this screen.

## 7. Verification

- **Unit tests**: `DateRules`, the view models (unlock flip, foreground re-read, tracking once per visit),
  and the `TabBar` hint timing.
- **Server**: unit tests for the payload builder (shared-only vibes and interests, equal-only motivation,
  hidden fields omitted, the unread count). A real-database e2e for `GET /me/date` (204, 200 in A and B,
  unread → read), `GET /me/date/profile` and the unlock job.
- **Fit sweep**: A, B, C and D (plus long names and addresses, and no venue) on every phone at 1x / 1.3x /
  2x, both platforms. Checks: the CTA reachable, nothing clipped, 48 dp / 44 pt targets.
- **Evidence**: A–D at 375 × 667, 390 × 844 and 430 × 932, compared against the sheet. D full-length.
- **Checker**: a new `audit/verify-date.py` that quotes the ticket's Copy section at run time against
  both apps, plus the existing 19.
- **CI** green on Android, iOS and the server.
- **Device**: Esma on the emulator with `/dev/date` (the recording of A → B).

## 8. Delivery

One branch, `feat/date-01-date-screen`, and one PR into development: server, Android and iOS together,
as the 185/186 PR was. Staging only when Esma asks.

## 9. For Philipp (not blocking)

- Job in the About line: no profile question collects one, so the line shows area and distance.
- The C line's `{time}` reads as the message's time (`now` / `HH:mm`); the copy legend's `{time}` means
  the meet time elsewhere.
- Directions on iOS opens Apple Maps; on Android, the user's chosen maps app.
