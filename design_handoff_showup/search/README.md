# Date search — decisions

```
search/
  screen-search-profile-reference.jsx  ← screen 01: Freemium profile + Like. Read values from here.
  screen-search-grid-reference.jsx     ← screen 02: Premium grid (extends 01's ScreenSearchProfile)
  screen-search-likes-reference.jsx    ← screen 03: Likes received (consumes 02, adds no profile prop)
  screen-location-gate-reference.jsx   ← screen 04: location gate = Profile 12's screen, context="app"
  screen-search-empty-reference.jsx    ← screen 01 interim no-results (kit search-empty, normal mode)
  tickets/00-epic.md                   ← flow shape, built-once inventory, flow-level rules
  tickets/01-profile.md                ← scope, copy strings, AC, tracking
  spec-sheets/01-profile.png · 02-grid.png · 03-likes-received.png · 04-location-gate.png
```

| # | Screen | States | Status |
|---|---|---|---|
| 01 | Freemium profile + Like | A at rest · B Be sure · C message · D minimum · E full scroll · F actions coach | ticketed — not built |
| 02 | Premium grid | A at rest · B marked · C card or heart → profile · D Like on the profile → Be sure · E liked-you → Confirm date · F refused → closed in place · G liked-you declined → Undo · H/I refused → sorry sheet · J availability re-check · K fly-out | ticketed — not built |
| 03 | Likes tab — likes received (Premium) | A list · B View profile → profile · C Be sure · D Not interested on the profile → Undo toast · E refused → Too late · F Out of time · G empty · H fly-out · I sorry sheet · J Not now label | ticketed — not built |
| 04 | Location gate | A never granted · B off for Show Up · C off on the phone · D from Likes, Confirm date | ticketed — not built |
| 00 | Epic | — | written 21 Oct 2026 · `tickets/00-epic.md` |

Kit source: `ui_kits/show-up/screens-search-profile.jsx` (v2, 14 Oct 2026) · section **05 · Date search**.
Artboards: `search-v2-1up` · `search-v2-coach` (actions coach, every visit until Don't show again) · `search-v2-full` (all content) · `search-v2-min` (minimum content) · `search-v2-confirm` · `search-v2-msg` · `search-empty`.

## Decided 27 Oct 2026 — Search 01 developer answers (SHOWUP-184)

- **Gender is tracked in `filters_active`** against `checkinGenderDefault(gender, orientation)` of the viewer's current profile, computed on the client. No server change; only *that* it differs is sent.
- **Filters opened from Search**: same `check_in_filters_changed`, new `opened_from: "search"`. No `check_in_step_answered` / `check_in_closed`.
- **Back from Filters is a new Search visit** (`screen_viewed`, `search_viewed`, `position` from 1). Changed filters reload the queue. **No coach** on that return.
- **Three states get copy** (ticket → Copy → *States*): load failed, no results (interim, kit `search-empty` normal mode, `Widen my search` opens Filters), filter save failed.
- **Charms stay hidden** (`charms` null) until the post-date rating story, written by design.
- Taxonomy **1.4.24**.

## Decided 22 Oct 2026 — Search 04 location gate

- **The gate is Profile 12's screen in a second context** (`ScreenProfileLocation context="app"`, wrapper `ScreenLocationGate`). One radar, one privacy note, one status reader. Profile 12 is built and unchanged.
- **Three states = §26:** A never granted (`Allow location access` → OS dialog) · B off for Show Up · C off on the phone (both `Open Settings`).
- **No platform copy on the gate**; the button routes per platform. iOS C can only open our app page — accepted.
- **Order:** active date never reaches Search · no Check-In → Check-In 01 wins · then the gate. The 24h lockout is undecided and not in the order.
- **No filter button.** TabBar live: Likes and Settings reachable.
- **Likes without location:** browsing works; `Confirm date` pushes the gate over the profile (D). After granting, the user taps `Confirm date` again.
- **No Not now, no retry link** — foreground re-read lifts the gate.
- Instant: same gate, Instant header — Instant not built, so not ticketed.
- Tracking: §11 `search_location_gate` (registry 1.4.30); permission events gain optional `screen_id` (taxonomy 1.4.21) — absent = Profile 12.

## Decided 20 Oct 2026 — Freemium 1-up profile

**Scope**
1. Premium is in MVP. Freemium and Premium are separate stories.
2. Users with an active date can't search. That's covered by 07a Match & date confirmation, where Search and Instant are blocked in the nav bar. The search ticket has no lockout state.

**Profile content**
3. Charms are in MVP.
4. Rule for every section: if there's no content, the section isn't drawn. No placeholder, no "not answered". This covers questions skipped during profile creation.
5. A hidden detail (religion, politics, any hideable essential) drops its row entirely.
6. If age is hidden, the hero shows the name only.
7. The Uplift highlight shows for as long as the Check-In is active. If the user is available today and tomorrow, the one text has to cover both days. The user is responsible for that, and there's no per-day text.

**Meet card**
8. Always the earliest overlap of both Check-Ins. If that's only tomorrow: `19:00 tomorrow`.

**Show-up Rate**
9. A user with no dates yet shows **New in the App**.
10. **The Show-up count replaces the percentage (reward-only).** Only dates that both people confirmed count. A missed date is never counted, shown or recorded. There is no percentage and no "x of y".
    - UI now: `ShowUpCount` in `shared.jsx` shows `4 dates attended` (`1 date attended`), `New in the App` for new users.
    - Logic later, a separate story: both tap **Arrive**, one phone shows a QR code, the other scans it, and the count goes up for both.
    - Count 0 always shows `New in the App`, whether the user is new or has had dates but none confirmed (decided 20 Oct 2026, "for now").
    - The percentage `ShowUpPill` is still used in the grid, Liked-Me, the date screens and Instant. Those move to the count in their own stories.

**Overlap with the viewer (MVP)**
- **Vibe today:** the only overlap that gets highlighted. A row directly under the meet card, "You and Leonie both want today", with at most 3 chips plus "+N". The card is white with a 1.5px orange border. The chips use the same shared state as everywhere else, because the user learns that state once and it never changes.
- **Interests:** shared chips are marked inside their own card (filled + ✓, others outlined). The subtitle adds "· N in common".
- **Inside the vibe card:** the same chip states as in Interests.
- **Nothing else is compared:** not language, How I live, religion, politics or motivation. No match %.
- **Rules:** only compare what both users have filled in. No overlap means no row and no marking. ✓ plus fill so the state doesn't rely on colour alone.
- **Data dependency:** the client needs the viewer's own active Check-In vibes and profile interests.

**Actions**
11. **Not now** skips the profile for today. **Not interested** removes the profile from the user's pool permanently. No undo.
12. Like flow: **Be sure** confirm → optional message → sent.
13. Message limit: 100 characters, hard limit (`maxLength`).
14. No daily like limit for now.
15. Only Premium users can read the message, because Freemium users can't see who liked them. The modal copy already says this.

**Top bar and states**
16. Likes chip: shows the open-likes count; a tap opens the likes upsell (its own story, not built — until then the tap does nothing). Premium: the same chip opens the Likes tab (21 Oct 2026).
17. Empty queue: the existing **No results · widen filters (one tap)** screen (`search-empty`).

## Decided 21 Oct 2026 — Search 02 availability + refused confirm

- **Re-check, MVP:** on load the server sends only available people; **on every return from a profile** the grid re-reads every card. Matched / no overlap → closed in place (liked or not); meet time moved → updated. No live push yet.
- **A plain Like is never refused.** It just never becomes mutual; the server closes it (`like_withdrawn`).
- **A refused Confirm date shows the sorry sheet** (`ConfirmRefusedSheet`): photo + name, an apology, the timing, `Find someone else`. Then the card is closed in place. Search 03 reuses it (`Back to likes`) — its ticket is not updated yet.
- **A final answer removes the card.** Like, Not interested and a confirmed date → the card flies out on return (hold 150, 800 ms to the bottom-centre, scale .12 + fade, then a 250 reflow); Undo puts it back. **Not now stays, marked.** Supersedes "cards stay, marked" (20 Oct) for Like and Not interested.
- Ticket over the Jira limit → Tracking split into `tickets/02-grid-tracking-comment.md`.

## Decided 21 Oct 2026 — Search 03 carries the Search 02 changes

- **Sorry sheet** on a refused confirm, button `Back to likes`; then the card closes in place.
- **Availability re-check** on every return from a profile; closed cards stay in place, the badge drops.
- **Not interested flies out** with the grid's motion — one implementation for both screens.
- **Not now labels the card** (`Not now`, no dimming — the like is still open), this visit only.
- Not carried over: "a plain Like is never refused" (no plain Likes here) and "no closed card on load" (the Likes tab shows a closed like once, on purpose).
- Kit: Clara (closed card) now has a photo.

## Decided 21 Oct 2026 — Search 01 actions coach (revised the same day)

- Over the **first profile of every Search visit**, "Three ways to decide" explains `Not interested` · `Not now` · `Like`. Kit: `SearchActionsCoach` + `SEARCH_COACH_ROWS`, `<ScreenSearchProfile coach/>`, artboard `search-v2-coach`; sheet frame **F**, callouts ㉔ ㉕.
- Scrim over the screen; the action bar is lifted above it and its buttons are **inert**. The card sits in the bar's own column (never a fixed Y).
- **Two buttons: `Don't show again` · `Got it`.** `Got it` closes it for this visit. **A scrim tap and Android back are `Got it`.** Only `Don't show again` is stored: a server-side flag per user.
- **Freemium and Premium.** Freemium: the first queue profile. Premium: the first profile opened from the grid (Search 02). Not on the Likes tab or a `Liked you` profile (third button is `Confirm date`).
- Tracking: `search_coach_shown`, `search_coach_dismissed` (`dismiss_method` §23, `dont_show_again`, `visible_ms`) — NEW, registry 1.4.29 / taxonomy 1.4.20. Overlay, not a screen: no §11 row.
- ⚠ *Supersedes the first version the same day: "once per account, any dismissal sets the flag, Freemium only".*

## Decided 20 Oct 2026 — Premium

**Scope.** Two stories: **Search 02 Premium grid** and **Search 03 Likes received**. Every Freemium rule carries over unchanged: `ShowUpCount` (never a %), rounded distance, age hidden → name only, §30 motivation labels, meet time = earliest overlap with `today` / `tomorrow`, the same Be sure + optional message sheets.

**Grid (02)**
- Tapping a card opens the **full v2 profile** (same screen as Search 01, with a back button `All profiles`). ~~A heart on the card likes straight from the grid.~~ **21 Oct 2026: no shortcut.** A tap anywhere on the card, the heart included, opens the full profile; every decision is made there. The heart is a marker (filled once liked).
- **From the full profile, every action returns to the grid**, with the card marked: `Not now` and `Not interested` straight away; `Like` once the flow is finished (Be sure → `Skip message` or `Send message`); `Confirm date` on a "Liked you" profile once Be sure is confirmed. Cancelling a sheet stays on the profile. (The Freemium "next profile slides in" does not apply here.)
- After Like, Not now or Not interested the card **stays in the grid, marked**: filled heart for liked, dimmed with a `Not now` / `Not interested` label for the other two. The next visit applies the Search 01 pool rules.
- **"Liked you" badge** stays on the card (sunset badge + orange ring). Liking a "Liked you" card is a confirm: Be sure in mutual mode, CTA `Confirm date`, no message step.
- **Aligned with Likes received, 21 Oct 2026:** the confirm is only ever made on the profile. A **refused confirm** returns to the grid with the card **closed in place** (`Too late` / `Out of time`, no heart, no tap). **Not interested on a "Liked you" profile** gets the same **6 s Undo** as the Likes tab; other passes keep no undo.
- **21 Oct 2026 — every profile has all three actions**, `Liked you` ones included (grid and Likes tab): `Not interested` · `Not now` · `Confirm date` (or `Like`). `Not now` = back in the pool at the next Check-In; on a `Liked you` profile it **does not decline the like**, which stays open in the Likes tab until it closes. Supersedes “a Liked you profile has no Not now”.
- **No header count** ("Live · 47 ready to meet" removed). The Premium tag sits in the top bar.

**Likes received (03)**
- ~~Reached by tapping the **likes chip** in the top bar.~~ Superseded 20 Oct 2026: it is the **Likes tab** in the TabBar (every user; a Freemium tap opens the likes upsell — own story). ⚠ *21 Oct 2026:* the likes chip is back, for every user — Premium → Likes tab, Freemium → upsell. See the decisions below.
- **Confirm date → Be sure** (mutual: `Confirm, and you and {name} have a confirmed, binding date {day}.`, CTA `Confirm date`) → match. What happens after the match is 07a.
- **`Not interested` declines the like for good.** The liker is not told. (The kit's "Not now" was renamed, because the act is permanent.)
- A **withdrawn like** (the liker matched someone else) stays as a neutral **"Too late"** card: `Too late — {name} has a date with someone else.` / `A like closes as soon as either of you matches. Confirm sooner next time.` No gendered pronoun.
- Each card shows the meet time and the liker's **motivation** chip, plus their message if they wrote one.
- **Empty state:** `No likes yet` with `Back to profiles`.

**Likes received (03) — ticket decisions, 20 Oct 2026 (revised the same day)**
- **Likes is its own tab** in the TabBar (Search · Likes · Instant · Settings), **in every user's bar** (revised 21 Oct 2026): Premium → this list, Freemium → the likes upsell (own story). Badge = **open likes only**, Freemium too. The tab root has **no back button**. The **likes chip stays** in the Search top bar for every user (21 Oct 2026, reverses the 20 Oct removal): Premium → Likes tab, Freemium → upsell; on the Search and Instant tabs only, never on the Likes tab.
- Order: **earliest meet time first**, closed cards last. Sorted on load, never re-sorted in place.
- **21 Oct 2026 — the card has one action, `View profile`** (same target as the photo). No decision from the list: `Confirm date` and `Not interested` are on the profile only. The confirm is Be sure, mutual, **no message step**. Supersedes the card's `Confirm date` + `Not interested` buttons.
- **Not interested (on the profile) has a 6 s Undo**, shown back on the list. The decline is sent when the toast ends or the user leaves. Tracked as `pass_sent` · `not_interested` · `surface: likes_received`; Undo is `pass_undone`.
- **The server holds the confirm; the first to arrive wins.** A refused confirm returns to the list with the card closed in place.
- **Closed cards, two reasons:** `matched` → **Too late**; `no_overlap` → **Out of time**. No actions, not counted, kept until the next visit after they were seen.
- Empty state: **Back to search**.
- The `Like received` push and its deep link are a separate story.

- **No Premium filters in MVP.** Premium is the live grid and Likes received. Height, How I live and dating intent filters come later. The Show-up band filter is dropped for good: Show-up is a count, there are no bands. Check-In 05's "stay in Search's filter sheet" note is superseded by this.

## Decided 20 Oct 2026 — ticket scope (Search 01)

- **One ticket** covers the profile, the Like flow (Be sure → message) and Not now / Not interested. The modals are endings of the same attempt.
- **Be sure** CTA reads `Continue` (it no longer says "Send like", because the like is only sent after the message step). The binding-date line names the meet day: `today` or `tomorrow`.
- **Message step:** `Skip message` sends the like without a message, and `Send message` sends it with one. Dismissing the sheet sends nothing.
- **After Like sent, Not now or Not interested** the next profile slides in straight away. No toast, no confirmation screen.
- **Not now** brings the profile back at the **next Check-In only**.
- **Several open likes are allowed.** The first like to become mutual wins and the rest are **withdrawn** (server).
- **No report or block** in MVP.
- **Distance is rounded:** `< 1 km` under one km, `~N km` otherwise. Never the exact value.
- **Media:** tap a photo for the fullscreen viewer. Voice and video play on tap only, and pause when scrolled out of view.
- **Queue order** belongs to the server ticket, not this one.
- **Top-bar filter button** opens Check-In 05 Filters. The likes chip opens the likes upsell (Freemium, own story) or the Likes tab (Premium).
