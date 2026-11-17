# [Date 05] Change meet time and place — pickers, suggestions, answer, expiry

**Parent:** [Epic] 07a Match & date confirmation (`date/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `date-05-change-time-place-attachment.zip` (the handoff folder, `design_handoff_showup/`) · `date-05-change-time-place-spec-sheet.png`
**Blocks:** Date 07 Cancel this date (its "Would a later time or another place work?" opens these pickers) · Date 09 (the +2 h switch reads the latest agreed time)
**Blocked by:** Date 01 Date screen (`ScreenMatchCoord`, the time + place card, `CoordinateModule` incl. the hidden change tiles) · Date 02 Coordinate chat (thread, dock, `SystemLine`, the change chips)

## Description

Either person can suggest a later time or another public place, from the match until shortly before the date. The other person agrees, suggests something else, or lets it expire. **A suggestion moves nothing until it is agreed** — until then the original time and place stay binding, and not being there is a no-show.

Every suggestion and its answer are stored as evidence for flow 10 (epic rule 6).

**Fourteen frames, one ticket** (A–J the flow, K–N failure and empty states):

| | Frame | When | Reference |
|---|---|---|---|
| A | Time picker | `Change meet time` chip or tile | `ScreenChatChange initialSheet="time" initialPick="19:30"` |
| B | Place picker | `Change location` chip or tile | `ScreenChatChange initialSheet="place" initialPick="lenz"` |
| C | Sent — waiting | sender, after Suggest | `ScreenChatChange stage="sent"` |
| D | Date screen — yours waiting | sender, one time + one place open | `ScreenMatchCoord coordOpen checkInFlow change={[time pending by you, place pending by you]}` |
| E | Change request — chat open | receiver, Date screen | `ScreenMatchCoord coordOpen checkInFlow coordUnread={{ time, request }} change={{ …by: 'them' }}` |
| F | Answering in the chat | receiver, thread | `ScreenChatChange role="receiver"` |
| G | Agreed | both, Date screen | `ScreenMatchCoord coordOpen checkInFlow change={{ kind: 'time', status: 'agreed' }}` |
| H | Expired | both, thread | `ScreenChatChange stage="expired"` |
| I | Before the chat opens | locked Coordinate card with tiles | `ScreenMatchCoord checkInFlow earlyChange` |
| J | Change request — chat locked | receiver, before meet − 1:30 | `ScreenMatchCoord checkInFlow earlyChange coordUnread={{ time, request }} change={{ …by: 'them' }}` |
| K | Suggest failed | Suggest tapped, no server OK | `ScreenChatChange initialSheet="time" initialPick="19:30" sendError` |
| L | I agree failed | I agree tapped, no server OK | `ScreenMatchCoord … coordUnread={{ request: { …, agreeError: true } }}` · thread: `ScreenChatChange role="receiver" agreeError` |
| M | Places couldn't load | the Places request failed | `ScreenChatChange initialSheet="place" placesState="error"` |
| N | No place found | search with no result | `ScreenChatChange initialSheet="place" initialQuery="sushi"` |

Place variants (kit Change · 3b, 4b, 5b, 6, 11, 15) use the same components with `kind="place"`; they are not separate frames.

## Decided before build

- **The binding value is the big one.** While a suggestion is pending, the time + place card and the chat header keep the binding value at full size; your own suggestion sits under it, small. Only **Agreed** makes the new value big. (3 Nov 2026)
- **Deadline = binding meet time − 0:30**, for time and place alike. It counts from the **binding** time, never the suggested one (19:00 binding, 19:30 suggested → answer by 18:30). After a time is agreed, a still-open place suggestion takes its deadline from the new time. Not agreed by then → **expired**, the original stays. (3 Nov 2026)
- **One open suggestion per type.** One time and one place can be open together, answered separately. A new suggestion of the same type **replaces** the open one, whoever sent it. (2 Nov 2026)
- **Times: later the same day only**, inside the match's availability, 15-min slots. **No move to another day in the app** — Date 02's cancel guide covers that. (2 / 5 Nov 2026)
- **Places: public venues only** — cafés, bars, restaurants from Google Places (may be swapped during implementation). No private addresses, no free text. (2 Nov 2026)
- **From the match on.** Before the chat opens, the locked Coordinate card's **Change meet time · Change location** tiles (built hidden by Date 01) open the pickers; after it opens, the chat chips do. **This ticket un-hides both** — the tiles and the two change chips in Date 02's grid and dock. (3 / 5 Nov 2026)
- **Agree on the card.** The orange request card on the Date screen carries **I agree** (second, primary). Its first button is **Open chat** while the chat is open and **Suggest another** while it is locked. (3 Nov 2026)
- **Purple = real, orange = needs your reaction.** The match's suggestion never appears on your purple card — only on the orange one. (3 Nov 2026)
- **Hidden from binding meet − 0:30.** From the deadline on, a new suggestion could never be agreed, so the locked card's tiles and the two change chips are **hidden for both people**. One cutoff value, used for both kinds. (10 Nov 2026 — closes the 3 Nov *To decide*)
- **Hidden after a check-in.** Once **either** person has checked in (Date 03, committed on Close), the tiles and change chips are hidden for both — someone is already at the place. An open suggestion stays answerable until its deadline. (10 Nov 2026)
- **Hidden when there is no later slot.** If the slot list for the time picker is empty, the `Change meet time` tile / chip is hidden; `Change location` is unaffected. (10 Nov 2026)
- **Two requests from the match: one card at a time.** The hoisted request card shows the open request with the **earlier deadline**, time first on a tie; the other takes the card once the first is agreed, replaced or expired. Both are in the thread. (10 Nov 2026)
- **The chat never re-locks.** An agreed time moves the unlock to the new meet − 1:30 while the chat is still locked; once open, it stays open. (10 Nov 2026)
- **"Nearby" = the binding meet place.** Places are ordered and their distance is measured from the binding place, so both people see the same list. (10 Nov 2026)
- **No Decline and no Withdraw.** The answers are I agree, Suggest another, or nothing (it expires). A sender who changes their mind suggests again or lets it expire. (kit, 3 Nov 2026 — written down 10 Nov)

## Layout — read this before building

⚠ **Both pickers are one shell (`ChangeFullSheet` ①)**: full screen over the chat or the Date screen, one scrolling list, a **footer pinned to the bottom of the column** (binding note ⑤ + two buttons ⑥). The footer is always above the fold; the list's bottom padding (280) lets the last row scroll clear of it.

⚠ **The suggestion card ⑦ lives in the thread** like a message, mine right, theirs left. System lines are Date 02's `SystemLine`.

⚠ **The request card ⑫ takes Date 01's hoisted unread slot** under the hero and outranks an ordinary unread message. Your own waiting suggestion ⑩ lives inside the purple time + place card.

⚠ **No absolute Y positioning.** The footer is bottom-anchored in the column, never placed by Y; chrome sits on the safe-area insets.

📎 All specs are on the attached **date-05-change-time-place-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–⑯ across ten frames. Black callouts are built here; **violet callouts are another ticket's UI or not ours**. **Red is the failure state** (K–M). The design system has colour and type tokens only, with no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/date/screen-change-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. Frame props are in the table above. From `CoordinateModule`, only the `unread.request` branch and the locked card's tiles are this ticket; the rest is Date 01 / 03. `CHANGE_SLOTS` and `CHANGE_PLACES` are fixtures — both lists come from the server.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **Google Places** — the venue data, its ranking and its search. We filter to cafés, bars and restaurants and show name, type, address and distance. **Google's attribution rules apply** to Places results shown without a Google map: check the current Places policy and add the required attribution under the list ④. This overrides the sheet **for that property only**.
- **Push delivery.** Pushes fire on a new suggestion, on Agreed and on Expired; copy is TBD (epic *Open*).
- **Status bar and home indicator** are mocks. Anchor to the safe-area insets.

## Copy — final strings

`{name}` is the match's first name. `{deadline}` is binding meet time − 0:30. `{latest}` is the last slot the server returns.

**A · Time picker:** `Suggest a new time` · `Your current meet time: {time}` · `Choose a time within {name}'s availability. Latest possible meet time: {latest}.` · `{name} has to agree by {deadline}, so you both still arrive on time. If not, {time} stays binding.` · button `Pick a time` (disabled) → `Suggest {slot}` · `Cancel`

**B · Place picker:** `Suggest a new place` · `Your current meet place: {place}` · `Choose a public place: café, bar or restaurant.` · `{name} has to agree by {deadline}, so you both still arrive on time. If not, {place} stays binding.` · placeholder `Search cafés, bars, restaurants` · `Nearby public places` · row `{venue}` · `{TYPE}` · `{address} · {distance}` · button `Pick a place` (disabled) → `Suggest {venue}` · `Cancel`

**Binding note (A, B, C, D):** `{time} at {place} stays binding unless {name} agrees by {deadline}.` (bold) ` Not showing up there counts as a no-show.`

**Suggestion card:** `You suggested · Meet time` / `{name} suggested · Meeting place` · value · old value struck · place: `{type} · {address}` · timestamp
- yours, pending: `Waiting for {name}` + compact binding note
- theirs, pending: `Answer by {deadline}` · `Until you agree, {time} at {place} stays binding. Not being there counts as a no-show.` · `Suggest another` · `I agree`
- `Agreed · {time}` · `Replaced by a new suggestion` · `Expired · not agreed by {deadline}`

**System lines:** `Meet time is now {time}` · `Meeting place is now {venue}` · `No answer by {deadline} — {time} at {place} stays.`

**D · Purple card, yours waiting:** `Waiting for {name} to agree` · `You suggested {value}` + compact binding note
**G · Purple card, agreed:** `Changed · was {original}`

**E / J · Request card:** `{name}` · tag `Change request` · `needs your answer · {time}` · reason badge (Date 02) · `{name} suggested` · value · old value struck · deadline box as on the card above · `Open chat` (E) / `Suggest another` (J) · `I agree` · J only: `Messaging opens at {opensAt}`

**K · Suggest failed:** `Couldn't send your suggestion. Check your connection and try again.`
**L · I agree failed:** `Couldn't agree. Check your connection and try again.` (the line is the retry button)
**M · Places couldn't load:** `Couldn't load places. Check your connection and try again.` · `Try again`
**N · No place found:** `No cafés, bars or restaurants found for “{q}”.` · `Try another name.` · list label `Results` while a query is typed

**I · Locked card:** `Messaging available from {opensAt}` · `Request to change the location or meet time.` · `Change meet time` · `Change location`

Product terms capitalise exactly: **Show Up · Show-up Rate · Check-In** (the 48 h one). The binding note says **no-show**, never a rate.

## Behaviour

**Entry points — four, one picker per kind** (`entry`, §42)

- **Locked Coordinate card tiles** (`coordinate_tile`) — before meet − 1:30.
- **Chat chips** `Change meet time` / `Change location` (`chat_chip`) — start grid and dock, after the unlock.
- **Suggest another on the request card** while the chat is locked (`counter_card`).
- **Suggest another on the suggestion card** in the thread (`counter_thread`).
- Date 07's cancel sheet opens the same pickers; it adds its own §42 value when it is written.

**A · Time picker**

- The **server** returns the slots: 15-min start times later than the binding time, inside the match's availability for the date's day; the latest is the availability end − 0:30 so a 30-minute date still fits. The client never computes them.
- Tap a slot to select it; tap another to switch. The primary reads `Suggest {slot}` once one is selected.
- `Cancel` or back closes the picker. Nothing is stored or sent.

**B · Place picker**

- On open, the nearby list shows public cafés, bars and restaurants, nearest first. Typing searches the same types (debounced); results are places, **never street addresses**.
- One row selected at a time; the primary reads `Suggest {venue}`.
- **Ordered by distance from the binding meet place** — the same list for both people.
- **M:** if the Places request fails, a message and `Try again` replace the list; the search field stays usable and the primary stays disabled.
- **N:** a query with no result shows the empty line — not an error, no red, no Try again.

**Sending (C, D)**

- `Suggest …` stores the suggestion, posts the card into the thread (also when the chat is still locked — it is there when the chat opens), closes the picker and returns to where it was opened.
- **The binding values do not change.** The sender's purple card shows the small waiting line ⑩ and one compact binding note; the chat header keeps the binding values.
- If an open suggestion of the same kind existed, its card shows `Replaced by a new suggestion` — whoever sent it. A replaced suggestion never expires and can no longer be agreed.
- The match gets the orange request card (E / J) and a push.
- **K:** no server OK → the picker **stays open** with the selection kept, the error replaces the binding note, and `Suggest {value}` retries. Nothing is stored or posted until the server confirms. Cancel discards it.

**Answering (E, F, J)**

- `I agree` — on the request card or in the thread — makes the suggestion binding: the server posts `Meet time is now …` / `Meeting place is now …`, both purple cards show the new value big with `Changed · was {original}`, both chat headers update, and the request card clears.
- `Suggest another` opens the picker of the same kind. Sending replaces the open suggestion; the roles swap.
- `Open chat` opens the thread (Date 02) with the card in view.
- The request card **outranks an unread message** in the hoisted slot and stays until the suggestion is agreed, replaced or expired. With a time **and** a place request open, it shows the one with the earlier deadline (time first on a tie); the other follows.
- **L:** no server OK on `I agree` → the red line shows under the buttons, on the request card and in the thread alike. Tapping the line or `I agree` retries. **Nothing changes** — not the purple card, not the header, no system line — until the server confirms.

**Expiry (H)**

- A server job at the deadline marks a still-pending suggestion **Expired** and posts `No answer by {deadline} — {time} at {place} stays.` The request card and the sender's waiting line clear.
- When a time is agreed, an open **place** suggestion's deadline is recomputed from the new time.

**When the tiles and chips are hidden** — for both people, all three are one check:

- from **binding meet − 0:30**;
- once **either** person has checked in;
- `Change meet time` only: while the server's slot list is empty.

An open suggestion stays answerable until its own deadline in every case.

**What an agreed change moves**

- Everything reads the **latest agreed** time and place (epic rule 2): the purple card, the chat header, `Starts in …`, the chat unlock (meet − 1:30 — a locked chat follows the new time, an open chat **never re-locks**), the location gate's date line, Directions and Copy address, the Cancel penalty's notice, and the +2 h switch (Date 09).
- `Changed · was {original}` shows the **original** value only, never a chain, and is hidden after the meet time.
- A calendar event added earlier is **not** updated — we never read the calendar (Date 01).

**Races**

- `I agree` on a suggestion that is no longer pending (replaced or expired a moment earlier) is rejected by the server; the card re-renders in its real state. No toast, no event.
- Two suggestions of the same kind sent at once are serialised by the server; the later one replaces the earlier.

## Data and legal

- **Stored per suggestion:** date ID, sender, kind, value (time, or the Places `place_id` plus a name/address snapshot), status (`pending` · `agreed` · `replaced` · `expired`), deadline, created / answered / expired timestamps. Kept as evidence with the chat (retention: epic *Open*).
- A meet place is **always a public venue**; no private address is ever stored as one.
- **Never in analytics:** place name, address, coordinates, distance.

## Build inventory

- **Client:** `ChangeFullSheet`, `TimeChangeSheet`, `PlaceChangeSheet`, `ChangeSuggestionCard`, `ChangeTag`, `BindingNote`, `changeDeadline`, the `unread.request` branch of `CoordinateModule`; un-hiding and wiring the locked card's tiles and the two change chips.
- **Server:** the slot list (availability × binding time × 15 min), a Places proxy (server-side key, type filter, search), the suggestion store and its state machine, one-per-type replacement, the expiry job and deadline recompute, the system lines, the binding-value update and everything it reschedules (unlock, +2 h job), the date payload carrying open suggestions by kind and sender, pushes on suggest / agree / expire.
- **Not built here:** the chat, thread and dock (Date 02) · the Date screen and the time + place card (Date 01) · the cancel sheet's entry (Date 07) · push copy.

## Acceptance criteria

- [ ] A suggestion **never changes the binding values**; only `I agree` does, for both people at once
- [ ] Time slots come from the server: later the same day, inside the match's availability, 15-min steps, none later than `{latest}`; there is no way to pick another day
- [ ] The place picker shows only cafés, bars and restaurants; search never returns a street address
- [ ] The deadline is **binding meet time − 0:30**, counted from the binding time; after a time is agreed, an open place suggestion's deadline follows the new time
- [ ] At most one open suggestion per kind; a new one of the same kind marks the old card `Replaced by a new suggestion`, whoever sent it
- [ ] Not agreed by the deadline → the card shows `Expired · not agreed by {deadline}` and the system line is posted; the original stays binding
- [ ] The match's suggestion appears **only** on the orange request card and in the thread — never on the receiver's purple card
- [ ] Your own pending suggestion shows small under the binding value with one compact binding note
- [ ] After Agreed, the new value is big with `Changed · was {original}` — the original only — hidden after the meet time
- [ ] `I agree` works on the request card and in the thread and posts the same system line from both
- [ ] On the request card the binding action is **second**; `Open chat` (chat open) or `Suggest another` (chat locked) is first, with `Messaging opens at {opensAt}` when locked
- [ ] The locked card's tiles and the two change chips are **shown** and open the pickers; a suggestion sent before the unlock is in the thread when it opens
- [ ] An agreed time moves `Starts in`, the chat unlock and the +2 h switch
- [ ] From binding meet − 0:30, and once either person has checked in, the tiles and change chips are **hidden for both**; `Change meet time` is also hidden while there is no later slot
- [ ] With a time and a place request open, the request card shows the earlier deadline first (time on a tie), then the other
- [ ] An agreed later time moves a locked chat's unlock; an open chat **never re-locks**
- [ ] Nearby places are ordered and measured from the binding meet place
- [ ] **K:** a failed Suggest keeps the picker open with the selection; nothing is stored until the server confirms
- [ ] **L:** a failed I agree changes nothing on either phone; the red line retries
- [ ] **M / N:** a failed Places request shows Try again; an empty search shows the empty line with no red
- [ ] `Cancel` and back in a picker store and send nothing
- [ ] The picker footer is fully visible on 375 × 667 and the last slot / place scrolls clear of it
- [ ] Copy matches the strings above exactly
- [ ] **Evidence of done:** screenshots of **A–N** at **375 × 667, 390 × 844 and 430 × 932** — 42 images. On 375 × 667, state that the picker footer and both request-card buttons are fully visible. Plus two recordings on two phones: suggest a time → the match agrees on the request card → both purple cards change; and a place suggestion left to expire.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **K — Date Coordination**). Use them exactly — do not invent names, and do not restate a payload the registry already defines. **Registry 1.4.35 / taxonomy 1.4.28** — five events **NEW**, one value set **NEW**.

| What | Event | Payload |
|---|---|---|
| Chip or tile tapped | `action_chip_used` | unchanged (Date 02) · `reason` `change_time` / `change_place`, `surface` `entry` · `dock` · `coordinate_card` |
| Picker shown | `change_picker_opened` | **NEW** · `date_id`, `kind`, **`entry`** (§42 — **NEW**), `minutes_before_start` |
| Picker closed, nothing sent | `change_picker_dismissed` | **NEW** · `date_id`, `kind`, `entry`, `had_selection` |
| Suggestion stored | `change_suggested` | **NEW, server** · `suggestion_id`, `kind`, `entry`, `replaces`, `offset_min`, `from_search`, `minutes_to_deadline` |
| I agree accepted | `change_agreed` | **NEW, server** · `suggestion_id`, `kind`, `surface`, `seconds_since_suggested` |
| Suggestion expired | `change_expired` | **NEW, server** · `suggestion_id`, `kind` |
| Open chat on the request card | `chat_opened` | unchanged (Date 02) · `entry: read_reply` |

The pickers are overlays on `date_screen` / `date_chat`: **no `screen_viewed` and no §11 row.**

**Rules for this screen**

- **A suggestion is not a message.** `message_sent` and `message_received` never fire for one.
- `action_chip_used` fires on the chip / tile tap, then `change_picker_opened`. **Suggest another** fires only `change_picker_opened` (no chip was tapped).
- **Replaced is not an event.** `change_suggested.replaces` (`none` · `own` · `theirs`) says what it replaced; Suggest another is `replaces: theirs`.
- Server events fire on the **accepted write** only. A rejected `I agree` (race) and the failures K–M fire nothing; a retry that succeeds fires the normal event once.
- `offset_min` is time-only (suggested − binding, always > 0); `from_search` is place-only. The other is `null`.
- `date_screen_viewed.has_unread` is `true` while a request card is shown.
- **Never in a payload:** place name, address, coordinates, distance.
- **Must NOT fire:** `message_sent` / `message_received` for a suggestion, `date_cancelled` (07), and no invented `change_declined`, `change_replaced` or `change_withdrawn` — none of those acts exist.

**Tracking acceptance criteria**

- [ ] Every change event carries `date_id` and `kind`; `entry` uses only §42 values
- [ ] One `change_suggested` per stored suggestion and one `change_agreed` or `change_expired` at most per suggestion — never both
- [ ] No change payload contains a place name, address, coordinates or distance

**Currently in the build: none of it.** Every row marked NEW is `Not built`.

## Out of scope

The Date screen and the time + place card (Date 01). The chat, thread, dock and system lines (Date 02). Check-in (Date 03). Cancel this date and its entry into these pickers (Date 07). Moving a date to another day. Push, SMS, WhatsApp and email copy. Order Ride.

## Dependencies

- **Date 01:** `ScreenMatchCoord`, the time + place card, `CoordinateModule`'s hoisted slot and the hidden tiles
- **Date 02:** the thread, `SystemLine`, `ReasonBadge`, the change chips in the grid and dock
- **Check-In 02:** the match's availability (for the slot list)
- **Shared:** `Card`, `Avatar`, `Icon`
- **Server:** the slot list, the Places proxy, the suggestion store with expiry, the binding-value update and its reschedules
- **Tracking registry 1.4.35 / taxonomy 1.4.28**

## Open before the sprint

Settled 10 Nov 2026: the meet − 0:30 cutoff, changes after a check-in, two open requests, the chat unlock, the "nearby" reference point, no later slot, and the failure states (K–N, Date 02's patterns) — all in *Decided before build* and the AC above.

1. **Push copy** for a new suggestion, Agreed and Expired — epic *Open*.
2. **Show-up Rate display** — still open project-wide (`JIRA-HANDOFF.md` → *Open decisions every ticket must carry*). This ticket says "counts as a no-show" and shows no rate, so it is unaffected.
