# [Date 01] Date screen — from the match until the date ends

**Parent:** [Epic] 07a Match & date confirmation (`date/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `date-01-date-screen-attachment.zip` (the handoff folder, `design_handoff_showup/`) · `date-01-date-screen-spec-sheet.png`
**Blocks:** Date 02 Coordinate chat · Date 03 Arrival check-in · Date 04 Match notified · Date 05 Change meet time and place · Date 06 End date and report a problem · Date 07 Cancel this date · Date 09 Switch to flow 10 · Date 10 Match Mate
**Blocked by:** Search 01 (`ShowUpCount`), Search 03 (the four-tab `TabBar`)

> ⚠ **Reissued 5 Nov 2026 — one addition.** The locked Coordinate card (state A) now carries **Change meet time · Change location** tiles. See *Decided before build*, callout ⑥ on the re-exported sheet, *Copy*, *Behaviour*, the rewritten AC and the new tracking row. Nothing else changed. If you started from the 2 Nov version, only the locked card is affected.

## Description

The screen both people live on **from the moment they match until the date ends**. It is where the app opens while a date is active, and it is the host for everything else in 07a: the chat, the check-in, time and place changes, ending, reporting and cancelling all start here.

This ticket builds **the screen and its time states**. The things it hosts are built in their own tickets; this one builds the **slots** they drop into (violet callouts on the sheet).

**Three time states and the full scroll, one screen, one ticket:**

| | State | When | Reference props |
|---|---|---|---|
| A | Announced — Coordinate locked | more than 1 h 30 before the meet time | `coordOpen={false} checkInFlow` |
| B | Available — Coordinate open | 1 h 30 or less before | `coordOpen={true} checkInFlow` |
| C | Unread message waiting | B + a message from the match is unread | `coordOpen={true} checkInFlow coordUnread={…}` |
| D | Full scroll | A, every section | `coordOpen={false} tall checkInFlow` |

All times count from the **latest agreed meet time**. A suggestion that is still waiting does not move it.

## Decided before build

- **07a is the only version.** There is no separate section 07. (2 Nov 2026)
- **The footer is the Check-in bar from the match until the user checks in**, then the End Date bar. This screen owns the sticky slot and the swap. The bars themselves are Date 03 and Date 06. ⚠ *The kit's 07a artboards showed the End Date bar before check-in until 2 Nov 2026. They were corrected with this ticket.*
- **The link under the Check-in bar is `End date`, not a cancel.** It opens the same wrap-up sheet as the End Date bar, so a user can end the date before they ever checked in. **`Cancel this date` stays** as its own link at the end of the scroll. (2 Nov 2026)
- **The Show-up percentage is gone from this screen.** Both places that showed it (intent card ⑩, About {name} ⑫) now use `ShowUpCount`, "N dates attended" / "New in the App". This applies the Search decision of 20 Oct 2026 ("the date screens move to the count in their own story"); this is that story.
- **No second date while one is active.** Search, Likes and Instant are disabled from the match until the date ends; **Settings stays open**. (2 Nov 2026 — supersedes "Search and Instant only".)
- **Only what matches is shown.** Different motivations → no intent line. Vibes → only the shared ones, under `You both want`; none shared → no row. (2 Nov 2026)
- **No timer exists in 07a.** "How the date works" was rewritten to what the app does. (2 Nov 2026)
- **"After the date" is removed** from this screen; it is designed with the review (flow 10). (2 Nov 2026)
- **Order Ride is hidden** until a ride-app API (e.g. Uber) is built. Quick actions are three tiles. (2 Nov 2026)
- **The locked Coordinate card has no progress bar** — only `Messaging available from {meet − 1:30}`. (2 Nov 2026)
- **Time and place can be changed from the match on, not only once the chat opens.** The locked card gains a line `Request to change the location or meet time.` and two tiles, **Change meet time · Change location**, in the quick-action tile style. **This ticket builds the tiles**; each opens the **Date 05** picker, and they ship **hidden until Date 05 ships** (epic rule 8). The card itself stays non-tappable. ⚠ *Superseded 5 Nov 2026 (decision of 3 Nov, kit Change · 12): the 2 Nov version of this ticket had no tiles on the locked card.* A change request received while the chat is locked (Change · 14–15) is the unread slot's request variant, built in **Date 05**.
- **Match Mate is a slot here.** Its content is designed and built in **Date 10**. (2 Nov 2026)
- **No header, no back.** While a date is active this screen *is* the app's home.

## Layout — read this before building

⚠ **One scroll container, one sticky footer.** StatusBar (54) → scroll container (`flex: 1`) → HomeIndicator. Inside the scroll: the sunset wash (absolute, behind) → the content column → the sticky footer (the Check-in or End Date bar plus the TabBar, **one** sticky container).

⚠ **Content order is fixed:** hero → time + place → Coordinate → Report a problem → intent → quick actions → About {name} → What you have in common → How the date works → Match Mate (slot, Date 10) → Cancel this date. **One exception:** in state C, the Coordinate card moves **above** the time + place card. It is the same card in a different position, never a second card.

⚠ **No absolute Y positioning.** Card heights come from content; long names and addresses wrap. Only the C message preview is clamped (2 lines).

At 390 × 844 the footer covers the lower part of the first screen, so the Coordinate CTA in B is reached by scrolling. That's accepted; don't pin anything to make it fit.

📎 All specs are on the attached **date-01-date-screen-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–⑱ across four frames. Black callouts are built here; **violet callouts are slots** built in the ticket they name. The design system has colour and type tokens only, with no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/date/screen-date-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. State props are in the table above.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **Status bar and home indicator** are mocks. Ship the platform chrome; anchor to the safe-area insets.
- **Photos** are placeholders. Ship the match's first photo, `object-fit: cover`.
- **The three quick-action hand-offs** — the maps app, the share sheet, the calendar sheet. We start them; their UI, copy and outcome are the platform's. We never learn whether the user finished them.

## Copy — final strings

`{name}` is the match's first name. `{day}` is `today` or `tomorrow`. `{time}` and `{place}` are the latest **agreed** values.

**Hero:** `It's a date.`
**Time + place:** `You and {name} are meeting {day}` — `{day}` italic, violet · `Meeting time:` · `Meeting place:` · Copy pill `Copy` → `Copied`
**Coordinate, locked (A):** `Coordinate with {name}` · `Sort last-minute details before you meet` · `Messaging available from {meet − 1:30}` (if the date is tomorrow: `Messaging available tomorrow from {meet − 1:30}`). No progress bar. Then `Request to change the location or meet time.` and the tiles `Change meet time` · `Change location` — same labels as the chat chips.
**Coordinate, available (B):** `Coordinate with {name}` · `Running late / Can't find your date at the spot? Tell {name} here!` · CTA `Open chat`
**Coordinate, unread (C):** tag `New message` / `{n} new` · `needs a response · {time}` · CTA `Read & reply`
**Report row:** **`Report a problem`** `If you feel unsafe or uncomfortable, let us know.`
**Intent card:** same motivation → eyebrow `Mutual intent` + the §30 label · different → eyebrow `{name}'s Show-up`, no label · `ShowUpCount` · shared vibes: `You both want`
**Quick actions:** `Directions` · `Share Date` · `Calendar` (`Order Ride` hidden)
**About:** `{name}, {age}` · `{job} · {area} · {distance}` · `See {name}'s full profile`
**Common:** `What you have in common`
**Link:** `Cancel this date`
**TabBar hint:** `Available again once your date is finished.`
**How the date works:** `Meet at {place} at {time}` / `A public place — a café, bar or restaurant.` · `Check in when you arrive` / `Tap Check-in at the spot — we let {name} know you're there.` · `30 minutes, no pressure` / `Stay if you're vibing, leave with a smile.` · `End the date in the app` / `Tap End date when you’re done — then a short review.`

**Match Mate:** no strings in this ticket — Date 10.

Product terms capitalise exactly: **Show Up · Show-up Rate · Match Mate · Check-In**.

## Behaviour

**Arriving**

- The app opens on this screen **whenever the user has an active date**: from the match (Search 02 / 03 confirm), from any match or reminder notification, and on every app launch.
- The screen reads the **latest agreed** time and place on every foreground. An agreed change (Date 05) updates ③, ⑭ row 1 and the locked line in ⑥ in place.

**Time states**

- **A → B happens in place** at meet − 1:30 while the screen is open: same slot, no reload, no toast. A user who opens the app after that time lands on B.
- **B → C** when a message from the match arrives unread. The card moves above ③. **C → B** once the thread has been opened.

**What each element does**

- **Copy (④):** copies `{place}, {address}` to the clipboard and shows `Copied` for 1600 ms. No toast.
- **Open chat / Read & reply:** opens the Coordinate chat (Date 02), with the tutorial first if not seen.
- **Change meet time / Change location (locked card, A):** open the Date 05 time / place picker — the same pickers as the chat chips. Nothing else on the locked card is tappable. Hidden until Date 05 ships. In B and C the tiles are not shown: the chat's chips do the same job.
- **Report a problem:** opens the Report sheet (Date 06). Always present while the date is active.
- **Directions:** opens the platform maps app with the agreed place as destination.
- **Order Ride:** hidden. It returns as a fourth tile when a ride-app API exists (own ticket).
- **Share Date:** opens the OS share sheet with `I'm meeting {name} {day} at {time} at {place}, {address}.` (for telling a friend where you are).
- **Calendar:** opens the OS add-to-calendar sheet: title `Date with {name}`, start = meet time, 1 h, location = place + address.
- **See {name}'s full profile:** opens Search 01's profile screen **read-only** (no action bar), with a back pill to this screen.
- **Match Mate:** the slot is always visible on this screen; its content is Date 10.
- **Cancel this date:** opens the penalty-guard sheet (Date 07).
- **Disabled tab (Search, Likes, Instant):** shows the hint above the bar for 3200 ms; a second tap restarts it. Nothing navigates. **Settings opens as normal.**
- Press feedback only, no hover.

**Leaving**

- The screen is left only by the 07a endings: End Date (Date 06), cancel (Date 07), the other side's outcome screens (Date 08), or the +2 h switch to flow 10 (Date 09).

## Build inventory

- **Client:** `ScreenMatchCoord` with the A / B / C states, `CoordinateModule` (locked **with the two change tiles**, available, unread; the arrival variant is Date 04, the request variant Date 05), `CopyAddress`, `DateWorksCard`, the Match Mate slot, the sticky footer slot with its `checkedIn` swap, the `TabBar` disabled state. The three OS hand-offs.
- **Server:** the date payload this screen reads — both people, the latest **agreed** time and place, both §30 motivations (the client shows the line only when they are equal), the **shared** vibes, shared interests, show-up count, age, job, area, distance. The unlock time (meet − 1:30) and `coordinate_unlocked`.
- **Not built here:** both footer bars, the change tags and binding note, every sheet, Match Mate's content, Order Ride. A slot whose content is not built yet ships **empty**, not as a placeholder.

## Acceptance criteria

- [ ] The app opens on this screen whenever the user has an active date, from every entry named under *Arriving*; there is no header and no back
- [ ] Hero: first photo h 196, radius 24, `It's a date.` in Lora 700 italic 30, white, centred
- [ ] Time + place card shows the **latest agreed** values and `{day}` as `today` or `tomorrow`; a pending suggestion never replaces them
- [ ] Copy writes `{place}, {address}` to the clipboard and shows `Copied` for 1600 ms
- [ ] **A** shows the locked Coordinate card with `Messaging available from {meet − 1:30}`, the line `Request to change the location or meet time.` and the two tiles `Change meet time` · `Change location` (2 columns, gap 8, tile padding 14 / 8, radius 16). **The card is not tappable; only the tiles are**, each opening the Date 05 picker. Until Date 05 ships, the line and tiles are not rendered. *(Rewritten 5 Nov 2026 — was: "the locked Coordinate card, not tappable", with no tiles.)*
- [ ] **A → B unlocks in place** at meet − 1:30 with the screen open — no reload, no toast
- [ ] **C** moves the same Coordinate card above the time + place card, in the message's tone, preview clamped to 2 lines; it returns to its slot after the thread is opened
- [ ] `Report a problem` is present in every state while the date is active
- [ ] **No percentage anywhere on the screen.** ⑩ and ⑫ use `ShowUpCount`
- [ ] **Three** quick actions (Directions, Share Date, Calendar), each starting the hand-off under *Behaviour*; **Order Ride is not rendered**
- [ ] **No progress bar** on the locked Coordinate card
- [ ] Intent line only when both motivations are equal; vibes row shows **only shared vibes**, and is absent when none are shared
- [ ] "How the date works" shows the four rows under *Copy* — **no timer, no "halfway"**
- [ ] **No "After the date" card** on this screen
- [ ] The Match Mate slot sits between "How the date works" and `Cancel this date` and renders nothing until Date 10 ships
- [ ] `See {name}'s full profile` opens the profile read-only, with no Like, Not now or Confirm date
- [ ] A section with no content (no shared interests, no shared vibes) is not drawn — no placeholder
- [ ] The footer is **one sticky container**: Check-in bar (with its `End date` link) before check-in, End Date bar after, TabBar below — the swap happens on `checkedIn` without a layout jump
- [ ] TabBar: no tab active; **Search, Likes and Instant disabled, Settings open**; a disabled tap shows `Available again once your date is finished.` for 3200 ms
- [ ] Only the content column scrolls; no absolute Y positioning anywhere
- [ ] Copy matches the strings above exactly
- [ ] **Evidence of done:** screenshots of **A, B, C and D** at **375 × 667, 390 × 844 and 430 × 932** (D as a full-page capture) — twelve images. Plus one screen recording of the A → B unlock with the screen open.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **K — Date Coordination**, `screen_viewed` in family A). Use them exactly — do not invent names, and do not restate a payload the registry already defines. **Registry 1.4.32 / taxonomy 1.4.25** — the screen row, one value set and one event are **NEW** with this ticket.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "date_screen"`, `screen_name: "Date - Date screen"`, `referrer_screen_id` — §11, **NEW row** |
| Date screen state | `date_screen_viewed` | `date_id`, `state` (`locked` / `available`), **`has_unread`**, **`checked_in`** — **NEW properties**, `minutes_before_start` — from the latest agreed meet time |
| Coordinate unlocks | `coordinate_unlocked` | server, at meet − 1:30 · `minutes_before_start` |
| **Quick action or Copy** | `date_quick_action_tapped` | **NEW event** · `date_id`, `action` (§38 — **NEW**; `ride` is reserved until Order Ride ships), `minutes_before_start` |
| **Change tile (locked card)** | `action_chip_used` | `date_id`, `reason` (`change_time` / `change_place`, §40), **`surface: "coordinate_card"`** — **added 5 Nov 2026**, taxonomy 1.4.26. Same event as the chat chips, so "change requested" is one funnel whatever the surface |
| Full profile | `screen_viewed` | `screen_id: "search_profile"`, `referrer_screen_id: "date_screen"` — no new event |

**Rules for this screen**

- `date_screen_viewed` fires **once per visit**, alongside `screen_viewed` — **not** on the in-place A → B unlock (`coordinate_unlocked` records that).
- `date_quick_action_tapped` fires **on the tap**, before the hand-off. Whether the user completes the OS sheet is not ours and is not measured.
- **A tap on a disabled tab fires nothing.** No `tab_switched`.
- **Must NOT fire from this ticket:** `match_mate_*` (Date 10), `coordinate_viewed` (**retired** 5 Nov 2026 by Date 02 — do not emit), `arrival_*` (Date 03), `date_status_*` (**retired** by Date 02), `date_cancelled` (Date 07), any Date 05 suggestion event (the picker fires those). Their entry points are on this screen; their events belong to their tickets.

**Tracking acceptance criteria**

- [ ] `screen_viewed` carries `screen_id: "date_screen"` and `screen_name: "Date - Date screen"`, read from §11, not literals
- [ ] `date_screen_viewed` carries `has_unread` and `checked_in`, and `minutes_before_start` counts from the latest **agreed** time (negative after it)
- [ ] `date_quick_action_tapped.action` uses only `directions`, `share`, `calendar`, `copy_address`
- [ ] A change tile tap fires `action_chip_used` with `surface: "coordinate_card"` and nothing from the quick-action event
- [ ] No event fires on a disabled-tab tap or on the in-place unlock (except the server's `coordinate_unlocked`)

**Currently in the build: none of it.** Every row above is `Not built`.

## Out of scope

The Coordinate chat and its tutorial (Date 02). The Check-in bar, both pop-ups and the stored location (Date 03). The arrival card (Date 04). The pickers, suggestion cards, change tags and binding note (Date 05). The End Date bar, the wrap-up and Report sheets (Date 06). Cancel this date (Date 07). The other side's outcome screens (Date 08). The +2 h switch (Date 09). Match Mate's content (Date 10). Order Ride and any ride-app API. The "After the date" card (flow 10). Push, SMS, WhatsApp and email copy. The parked AI call.

## Dependencies

- **Search 01:** `ShowUpCount` and the profile screen (opened read-only here)
- **Search 03:** the four-tab `TabBar` with its disabled state
- **Check-In 03:** §30 motivation labels
- **The date payload** listed under *Build inventory*, with the **agreed** time and place kept separate from any pending suggestion
- **Tracking registry 1.4.32 / taxonomy 1.4.25** — plus **taxonomy 1.4.26** for the change-tile row (`action_chip_used.surface`)

## Open (not blocking)

- **Order Ride** — needs a ride-app API (e.g. Uber). Own ticket when it is scheduled; `ride` is already reserved in §38.

**Settled 2 Nov 2026** (were open in the first version of this ticket): no timer — card rewritten · After the date removed · Match Mate → Date 10 · Likes blocked too, Settings open · `End date` link under the Check-in bar, `Cancel this date` kept · only shared intent and vibes shown · Order Ride hidden · no progress bar.
