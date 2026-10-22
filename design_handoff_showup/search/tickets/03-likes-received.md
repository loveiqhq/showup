# [Search 03] Likes tab — likes received

**Parent:** [Epic] Date search (`search/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `search-03-likes-received-attachment.zip` (the handoff folder, `design_handoff_showup/`) · `search-03-likes-received-spec-sheet.png` · first comment: `search-03-likes-received-tracking-comment.md`
**Blocked by:** **Search 02** (`ConfirmRefusedSheet`, the fly-out, the availability status call, `premiumProfile`, the mutual mode of `LikeConfirmModal`, the five `ScreenSearchProfile` props, `LikesUndoToast`, the serialised confirm), **Search 01** (`ScreenSearchProfile`, `ShowUpCount`, `searchDistance`, the like and pass endpoints), **Check-In 03** (§30 motivations), the **Premium entitlement** (`plan_tier`). **Hands over to:** **07a** Match & date confirmation.

## Description

A new **Likes** tab in the TabBar, for **Premium** users: the people who **already liked them**, apart from the normal search. The tab carries a badge with the number of open likes.

Each card shows when the two could meet, the liker's motivation and their message if they wrote one. The user can:

- **View profile**, or tap the photo → **the full profile**. That is the card's only action: **every decision is made on the profile.**
- On the profile, **Confirm date** → Be sure (mutual, no message step) → the match. What happens after the match is 07a.
- On the profile, **Not interested** → back on the list, the card flies out and the like is declined for good after a 6-second Undo. The liker is not told.
- On the profile, **Not now** → back on the list, the card is labelled `Not now` and the like stays open.

A like that closed without a decision stays for one visit as a **closed card**: **Too late** if the liker matched someone else, **Out of time** if the two Check-Ins no longer overlap.

Ten frames on the sheet: **A** the Likes tab · **B** View profile → full profile · **C** Confirm on the profile → Be sure · **D** Not interested on the profile → card gone, Undo toast · **E** confirm refused, matched → Too late · **F** no overlap left → Out of time · **G** no likes yet · **H** the Not interested fly-out, frozen at 440 of 800 ms · **I** confirm refused → sorry sheet · **J** Not now → labelled. *H–J added 21 Oct 2026.*

Entered from:

- The **Likes** tab, from any screen that shows the TabBar (a Premium user; a Freemium tap opens the likes upsell instead — its own story).
- Back from a profile opened from this list.

Exits:

- A card's photo or `View profile` → **the full profile** (`search_profile`, referrer `likes_received`).
- `Back to search` on the empty state, or the Search tab → **Search**.
- A confirm that wins → **the match** (07a).

## Decided before build

Decided 20 Oct 2026, card actions revised 21 Oct 2026 (`search/README.md` → *Likes received*; the TabBar rule is in `design_handoff_showup/CLAUDE.md`). The kit matches every point below.

- **Likes is its own tab** (② on the sheet). The TabBar is `Search · Likes · Instant · Settings`. **The tab is in every user's bar** (21 Oct 2026), with the badge: **Premium → this list; Freemium → the likes upsell screen** (its own story, not built yet). The badge counts **open likes only**, for Freemium too. ⚠ *Superseded 21 Oct 2026: was "Premium only: a Freemium user keeps the three-tab bar until the likes upsell story ships".*
- **The Likes tab is a tab root: no back button** (①). The top bar is the Search wordmark + `PremiumTag`, with **no filter and no likes chip**.
- **The likes chip stays in the Search top bar for every user** (21 Oct 2026): Premium → this tab, Freemium → the likes upsell. Same count as the badge. Shown on the **Search and Instant tabs only** — never here on the Likes tab, nor on a profile opened from it. ⚠ *Superseded 21 Oct 2026: was "Premium has no likes chip (supersedes Search 02 ①)".*
- **Order: earliest meet time first** (④): today before tomorrow, then by time. Closed cards go last. The list is sorted **once, on load**, and never re-sorted while the user is on it.
- **21 Oct 2026 — the card has one action: `View profile`** (⑥ ⑪), the same target as the photo. **No decision is made from the list:** `Confirm date` and `Not interested` exist on the profile only, so the user always sees who they are deciding about. ⚠ *Supersedes the 20 Oct card with `Confirm date` (which already opened the profile) + `Not interested`, and the underlined `View profile` link under the photo.*
- Be sure has **no message step** (⑭): a confirm is immediately a binding date.
- **21 Oct 2026 — the opened profile has all three actions** (⑬): `Not interested` · `Not now` · `Confirm date`, as every profile. `Not now` returns to the list with the card **labelled `Not now`** (㉑, J): the like is not declined and stays open until it closes. **Label only, no dimming** — a dimmed card here would read as closed. The label lasts for this visit. ⚠ *21 Oct 2026: supersedes "the card unchanged".* ⚠ *Supersedes the two-button action bar on the sheet's earlier export.*
- **The server holds the confirm; the first confirm to arrive wins** (⑭ ⑯). If it is refused, the **sorry sheet** opens first (⑱, I) — Search 02's `ConfirmRefusedSheet`, button `Back to likes` — and then the user lands back on the list with **that card closed, in place** (E, F). ⚠ *21 Oct 2026: supersedes "There is no error sheet". On this tab every confirm is one the user expected to become a match.*
- **21 Oct 2026 — availability re-check** (⑲), as Search 02. **On every return from a profile** the list re-reads every open card: `matched` / `no_overlap` → the card closes in place (⑫) and the badge drops; a new meet time → the chip shows it. No re-sort, no live push in MVP.
- **Not interested (on the profile) has a 6-second Undo** (⑮). The profile closes, **the card flies out** (⑳, H — Search 02's motion, the same implementation) and a toast offers `Undo`. **The decline is sent when the toast ends**, or straight away if the user leaves the tab or backgrounds the app. Undo puts the card back in the same place. ⚠ *Supersedes Search 01's "no undo" for this tab only.*
- **Not interested is tracked as `pass_sent`**, with `pass_type: not_interested` and `surface: likes_received`. There is no separate decline event.
- **A closed card has two reasons** (⑫): `matched` → **Too late**; `no_overlap` (the two Check-Ins have nothing in common still ahead) → **Out of time**. Both have no actions, are never counted by the badge, and stay **until the next visit after they were seen**.
- **Empty state:** `Back to search` switches to the Search tab (⑰).
- **The `Like received` push notification** and its deep link are **a separate story**.

## Layout — read this before building

⚠ **Card height comes from its content.** A message wraps and the chips wrap, so no card, list or Y position is fixed. A card that leaves (a decline) or closes (a refused confirm) reflows the list.

**Screen:** StatusBar → top bar (①) → **the list** (④, the only scroll container) → TabBar (②, Likes active) → home indicator.

**Top bar:** `SearchTopBar` with `premium` and `filter={false}`: Wordmark 20, `PremiumTag` size 10. Nothing else.

**Screen column:** a flex column at min-height 100% of the area between status bar and home indicator. **The list is `flex: 1`**, so on a short list the TabBar sits at the bottom. No min-height number and no fixed height.

**List:** padding `8 16 88`, column, gap 12. The intro block (③) comes first, then the cards. The 88 bottom padding keeps the last card clear of the toast.

**Open card** (⑤–⑪): `Card` padding 14, row gap 14. Left column: photo 88 × 120 r 16. Right column, gap 7: name · distance → `ShowUpCount` → the message (only if written) → chips (meet time, motivation) → `View profile` (`Button` sm sunset, margin-top 2).

**Closed card** (⑫): the same row with no actions, raised background, no shadow, 1px border, photo grayscale .92 / opacity .7.

**Not now label** (㉑): in the name row after the distance, `margin-left: auto`; padding `4 9`, white, 1px `--liq-border`, 11 / 800 muted, nowrap. The card is otherwise unchanged: no grayscale, no opacity.

**Fly-out** (⑳): Search 02 ⑳ unchanged — hold 150 → 800 ms to the visible list's centre x and 72% of its height at scale .12 and opacity 0, `cubic-bezier(.55,0,.8,.2)` → the cards below move up over 250 ms (FLIP). Reduced motion: a 200 ms fade, then the reflow. **One implementation for the grid and this list** (`useCardFlyOut` in the kit).

**Sorry sheet** (⑱): Search 02 ⑱, consumed, with `backLabel="Back to likes"`.

**Undo toast** (⑮): Search 02's `LikesUndoToast`, 16 from each side, 12 above the TabBar, over the list. It does not scroll with the list.

**Likes tab** (②): second item, heart 24. Badge min-width 18, height 18, sunset gradient, 10.5 / 800 white, 2px background ring, top −4 / left 15 of the icon. `0` shows no badge; over 99 shows `99+`.

📎 All specs are on the attached **search-03-likes-received-spec-sheet.png**: frames A–J and callouts ①–㉑. Violet callouts are the 20 Oct decisions. The design system has colour and type tokens only and no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/search/screen-search-likes-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. States: `<ScreenLikedMe/>` (A) · `<ScreenLikedMe initialOpen={0}/>` (B) · `<ScreenLikedMe initialModal={0}/>` (C) · `<ScreenLikedMe initialUndo="Anna"/>` (D) · `<ScreenLikedMe initialClosed={{ Anna: 'matched' }}/>` (E) · `<ScreenLikedMe initialClosed={{ Anna: 'no_overlap' }}/>` (F) · `<ScreenLikedMe empty/>` (G) · `<ScreenLikedMe initialUndo="Anna" freezeLeaving={{ name: 'Anna', t: 440 }}/>` (H) · `<ScreenLikedMe initialModal={0} initialSheet="refused" refuse={{ Leonie: 'matched' }}/>` (I) · `<ScreenLikedMe initialNotNow={['Anna']}/>` (J). `refuse`, `recheck`, `freezeLeaving` and `initialNotNow` are kit demo props; in the build they are the server's answers and the visit's state. **Builds** `ScreenLikedMe`, `LikesReceivedCard` (open + closed), `likesOrder`, `likesOpenCount`; the **Likes tab** on the shared `TabBar` (`likes`); the `tab` prop on `ScreenSearchProfile` (`"likes"` → Likes active, no filter, no chip); the `filter` and `chip` props on `SearchTopBar`; the Premium chip's tap → this tab. **Consumes** Search 02's **`ConfirmRefusedSheet`**, **the fly-out** (`GRID_LEAVE_*`), **the availability status call**, `premiumProfile`, `premiumName`, `premiumMotivation`, `LikesUndoToast` + `LIKES_UNDO_MS`, the mutual `LikeConfirmModal` and the `ScreenSearchProfile` props (`premium`, `onBack`, `backLabel`, `likedYou`, `onAction`), and Search 01's `ShowUpCount` and `searchDistance`. **One TabBar and one profile:** a second copy of either is a bug.
> Shell components: `design_handoff_showup/components/shared.jsx` (`Phone`, `Card`, `Chip`, `Icon`, `Button`, `PortraitPlaceholder`, `TabBar`) · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **Status bar and home indicator** are stylized mocks. Ship the platform chrome, and anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.
- **Photos** in the kit are placeholders. Ship the liker's first photo, `object-fit: cover`.

## Copy — final strings

`{name}` is the liker's first name. `{day}` is `today` or `tomorrow`.

**Tab:** `Likes` · badge accessible label `{n} open likes` (`1 open like`)
**Intro:** `People who want to meet you` (em on `meet you`) · `Open a profile, then decide. Confirm and you two have a binding date.` (21 Oct 2026)
**Open card:** name `{name}, {age}`, or `{name}` when age is hidden · `· < 1 km` / `· ~{n} km` · Show-up as Search 01 (`{n} dates attended` · `1 date attended` · `New in the App`) · the message in straight double quotes, as written · meet chip `{HH:MM} {day}` · motivation: the §30 label · `View profile`
**Accessible names:** photo `Open {name}'s profile`
**Closed — matched:** `Too late — {name} has a date with someone else.` · `A like closes as soon as either of you matches. Confirm sooner next time.`
**Closed — no overlap:** `Out of time — you and {name} have no time left to meet.` · `A like closes when your Check-Ins no longer overlap. Confirm sooner next time.`
No gendered pronoun in either.
**Undo toast:** `{name}'s like declined.` · `Undo`
**Not now label:** `Not now` · accessible name of the photo becomes `Open {name}'s profile, not now`
**Sorry sheet:** exactly Search 02 ⑱ (both reasons), with the button `Back to likes`
**Empty:** `No likes yet` (em on `yet`) · `When someone likes you, they show up here — with their message, if they wrote one.` · `Back to search`
**Profile from the list:** back `All likes` · badge `Liked you` · action bar `Not interested` · `Not now` · `Confirm date` (`Not now` added 21 Oct 2026)

**Be sure (C):** exactly Search 02 ⑮. Line `Confirm, and you and {name} have a confirmed, binding date {day}.` · `Cancel` · `Confirm date` + check icon.

## Behaviour

**Who sees it**
- Every user sees the Likes tab and its badge. The server's entitlement (`plan_tier`) decides only **where the tap goes**: `premium` → this list, otherwise → the likes upsell (its own story). A user who loses Premium mid-session gets the upsell on the next app open; the destination never changes under the user.

**The badge**
- The number of open received likes, from the server. It updates when the list loads, when a decline is sent, and when a confirm wins or is refused. Closed cards are never counted.

**Loading**
- The list loads once per visit to the tab, in server order: earliest meet time first, closed last. The client renders what it receives in that order and does not re-sort.
- A closed card is in the payload until the visit after the one where it was first shown. The server marks it seen when the list loads.
- Nothing open and nothing closed → the empty state (G).
- Switching tabs and coming back restores the scroll position unless the list reloaded.

**Opening a profile**
- The photo and `View profile` both open `ScreenSearchProfile` with `premium`, `likedYou`, `tab="likes"`, `onBack` and `backLabel="All likes"`. The Likes tab stays active and the top bar has no filter. Otherwise the profile is unchanged (sections, media, overlap).
- **Back** (`All likes`) returns to the list at the same scroll position.
- **`Not now`** returns to the list the same way, the card **labelled `Not now`** (J), no toast and no undo. A second Not now changes nothing; Confirm date or Not interested on the same profile later replaces the label. The like stays open; in Search the liker is out of the pool until the next Check-In (Search 01 rule).

**Confirm (on the profile)**
- `Confirm date` opens Be sure in mutual mode (C). `Cancel`, a scrim tap or a swipe down closes it, stays on the profile, and sends nothing.
- `Confirm date` in the sheet sends the confirm. **The button stays inert until the server answers**; the sheet does not close early.
- **Won** → the match is created and the user goes to 07a. The user's other open likes are withdrawn (Search 01 rule).
- **Refused** → Be sure swaps to the **sorry sheet** for the server's reason (I). `Back to likes`, a scrim tap, swipe-down and Android back all close it and the profile; the user is back on the list with **that card closed in its current position**, showing the reason's copy (E or F). It moves to the end only on the next load.

**Not interested (on the profile)**
- The profile closes and the user is back on the list; **the card flies out** (H: hold 150, 800, then the list closes the gap in 250) and the toast shows for **6 s** (D). The leaving card is not a tap target.
- A second decline inside the window sends the first one and starts a new toast.
- **The decline is sent** when the toast ends, when the user leaves the list (another tab, or a profile), or when the app goes to the background.
- **Undo** within the window puts the card back in the same position (the reflow in reverse), and nothing is sent. Undo during the fly-out stops it: the card stays at full opacity in its slot.
- A sent decline closes the like for good and removes the liker from the pool (Search 01's `not_interested`). The liker receives nothing.

**Availability re-check** (⑲)
- On every return from a profile (Back or any action), the client asks Search 02's status call for every open card on the list. `matched` / `no_overlap` → the card closes in place (⑫) and the badge drops by one; a new `meet_at` / `meet_day` → the meet chip shows it. Cards never reorder, appear or disappear. A failed call leaves the list unchanged until the next return. Nothing else triggers it in MVP.

**Closed cards**
- No buttons and no tap target. **A closed card never flies out**: it is not the user's answer.

**Accessibility**
- The photo (`Open {name}'s profile`) and `View profile` go to the same place; the card has no other control.
- The badge has its own accessible label; the tab reads `Likes, {n} open likes`.
- The toast is `role="status"` and is announced. `Undo` is a 44-high button reachable by focus; while focus is on it, the 6 s does not run out.
- A card that closes after a refused confirm is announced with its first line.
- Be sure traps focus and returns it to the profile's `Confirm date`.

## Build inventory

- **Client:** everything listed under *Builds* above. The Likes tab is a prop on the one shared `TabBar`; every screen that shows the TabBar gets the tab and passes `likes` (the badge) for every user, with no other changes.
- **Server — entitlement:** `plan_tier`, read at app open, decides where the Likes tab goes (list or upsell).
- **Server — badge count:** open received likes only. Pushed to the client on each change it causes (decline, match, refusal).
- **Server — list payload:** open received likes plus not-yet-seen closed ones, sorted as above. Per card: first photo, name, age (omitted if hidden), rounded distance, `meet_at` + `meet_day` (earliest overlap still ahead), `attended_count`, motivation, message, `closed` (`null` · `matched` · `no_overlap`). Marks closed cards seen on load.
- **Server — confirm:** the mutual-like endpoint, with the confirm serialisation built in Search 02: the first to arrive wins. Any other answers `closed` with the reason.
- **Server — expiry:** closes a received like when the two Check-Ins have no overlap left, sets `closed: no_overlap`, and fires `like_withdrawn` with `reason: no_overlap`.
- **Server — availability status:** Search 02's call (list of ids → `available` · `matched` · `no_overlap` + current `meet_at` / `meet_day`), reused.
- **Server — decline:** Search 01's pass endpoint with `not_interested`; on a received like it also closes the like. Called once, when the Undo window ends.

## Acceptance criteria

- [ ] Every user's TabBar is `Search · Likes · Instant · Settings`, with the badge for Freemium too; a Premium tap opens this list, a Freemium tap opens the likes upsell (until that story ships: does nothing, and never this list)
- [ ] The Likes badge equals the open cards on the list; closed cards are not counted; `0` shows no badge
- [ ] The Search top bar shows the **likes chip** for every user, with the badge's count: Premium → this tab, Freemium → not this tab (upsell, own story); the chip shows on the Search and Instant tabs only, never on the Likes tab or a profile opened from it
- [ ] The Likes tab has **no back button**; its top bar is the wordmark and `PremiumTag` only, with no filter
- [ ] Cards are in earliest-meet-time order (today before tomorrow, then by time) with closed cards last, and the list is not re-sorted while open
- [ ] Every open card shows `ShowUpCount`, the name (no comma when age is hidden), the rounded distance, the meet chip, the motivation chip, and the message only when one was written; no percentage and no exact distance appear anywhere
- [ ] The photo and `View profile` on a card both open the full profile with `All likes` as the back label and the Likes tab active
- [ ] **No decision can be made from the list:** an open card has `View profile` as its only button — no `Confirm date`, no `Not interested` — and Be sure never opens over the list
- [ ] On that profile, `Confirm date` opens Be sure in mutual mode with **no message step**; Cancel, scrim and swipe-down stay on the profile and send nothing
- [ ] On that profile the action bar is `Not interested` · `Not now` · `Confirm date`; `Not now` returns to the list with the card labelled `Not now` (no dimming, still opens) and the badge unchanged
- [ ] While a confirm is in flight, `Confirm date` is inert and the sheet stays open
- [ ] A won confirm hands over to 07a
- [ ] A refused confirm shows the sorry sheet first (Search 02 copy, `Back to likes`); closing it in any way returns to the list with that card closed **in the same position**: `Too late — …` for `matched`, `Out of time — …` for `no_overlap`
- [ ] On every return from a profile the list re-reads every open card: a `matched` / `no_overlap` card closes in its slot and the badge drops; nothing reorders; nothing else re-checks in MVP
- [ ] A like whose overlap ran out before the visit shows as an `Out of time` card
- [ ] `Not interested` on the profile returns to the list, the card **flies out exactly as on the Search 02 grid** (same code, same timings; reduced motion = fade) and `{name}'s like declined.` with `Undo` shows for **6 s**
- [ ] Undo during the fly-out leaves the card in its slot at full opacity
- [ ] Undo within 6 s restores the card in the same position and **nothing reaches the server**
- [ ] The decline is sent when the toast ends, when the user leaves the list, or when the app is backgrounded — and only once
- [ ] The liker receives nothing when declined
- [ ] A closed card has no actions and is gone on the visit after the one it was first shown in
- [ ] With nothing open and nothing closed, the empty state shows; `Back to search` switches to the Search tab
- [ ] On a short list the TabBar sits at the bottom of the screen and the toast clears it by 12
- [ ] There is one `TabBar` and one `ScreenSearchProfile`; with `tab` unset the profile behaves exactly as in Search 02
- [ ] No absolute Y anywhere in the list; the toast is the only overlay
- [ ] Copy matches the strings above exactly
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**
- [ ] **Evidence of done:** screenshots of **A–J** at **375 × 667, 390 × 844 and 430 × 932** (thirty images; H frozen at 440 ms), plus the Search grid showing the four-tab bar with a badge. Each shows the top bar and TabBar fully visible and nothing clipped; on D the toast clears the TabBar. Check 375 × 667 first. Add five screen recordings: View profile → Not interested → card flies out → Undo → card back in place · View profile → Not interested → wait 6 s → card gone, badge down by one · View profile → Confirm date → Be sure → (staged) refusal → sorry sheet → Back to likes → card Too late in place · View profile → Not now → card labelled · View profile → Back with another like (staged) closed → that card closes in place, badge down.

## Tracking

⚠ **The Tracking section is the ticket's first comment** (pinned), `search-03-likes-received-tracking-comment.md`. Split by length only (Jira caps a description at 32,767 characters), not by authority: the comment is part of this ticket and its rules are acceptance criteria.

Two rules that break first in a hurry:
- **`pass_sent` for a decline fires when the Undo window ends, never on the tap**, with `surface: "likes_received"`. An undone decline is `pass_undone` and no `pass_sent`.
- **The fly-out, the Not now label, the re-check and the sorry sheet's dismissal fire nothing.** A refusal is `like_confirm_failed`, once, when the sheet opens.

Do not run Jira's "improve description" on this ticket or the comment.

## Out of scope

The `Like received` push notification and its deep link. The likes upsell screen a Freemium tap (tab or chip) opens, and buying Premium. The match screen and everything after it (07a). Premium filters. Report and block. Instant.

## Dependencies

- **Search 02:** `premiumProfile`, the mutual Be sure, the `ScreenSearchProfile` props, `LikesUndoToast` and the serialised confirm. Its ticket carries the superseded likes-chip lines, and since 21 Oct 2026 the same refused-confirm and Undo behaviour on the grid.
- **Search 01:** `ScreenSearchProfile`, `ShowUpCount`, `searchDistance`, the like and pass endpoints.
- **Server:** entitlement, the badge count, the list payload and seen-marking, serialised confirms, overlap expiry, decline closing the like.
- **Tracking registry 1.4.28 / taxonomy 1.4.19.**

## Open (not blocking)

- ~~**The Date search epic**~~ — written 21 Oct 2026 (`search/tickets/00-epic.md`); the four-tab bar is its rule 10.
- **Other spec sheets that show the TabBar** (07a, Check-In 01's search-unavailable screen, Instant) still show three tabs. They gain the Likes tab, for every user, with no change of their own; re-export them when each is next touched.
