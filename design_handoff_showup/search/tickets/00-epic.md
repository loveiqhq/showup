# [Epic] Date search

**Type:** Epic · **Priority:** High
**Handoff folder:** `design_handoff_showup/search/` — read `README.md` first
**Design source:** Show Up UI kit, section `05 · Date search` (`ui_kits/show-up/screens-search-profile.jsx`, v2 14 Oct 2026)

## What this epic delivers

Everything between "the user has committed a Check-In" and "the user has a match". The user finds people who can meet in the same 48-hour window, decides on each one from their full profile, and either sends a like or confirms someone who already liked them. A confirm is a binding date; what happens after it is **07a Match & date confirmation**, not this epic.

**One match = one date.** A user may have several open likes at once. The first one to become mutual wins, and the server closes the rest. Every rule below that mentions *closed*, *withdrawn* or *refused* follows from this.

## Shape of the flow

One Search tab, two surfaces chosen by plan, plus one Likes tab.

| # | Story | Who | What the user does |
| --- | --- | --- | --- |
| 01 | Freemium profile + Like | Freemium | One profile at a time: `Like` · `Not now` · `Not interested`. The next profile slides in after each |
| 02 | Premium grid | Premium | A two-column grid. A card tap opens the 01 profile; every action there returns to the grid with the card marked |
| 03 | Likes tab — likes received | Premium (Freemium → upsell) | The people who already liked them. `Confirm date` opens the profile; the confirm is made there |

Entry: a committed Check-In (Check-In 06) unlocks Search. Exit: a won confirm hands over to 07a.

## Stories

Each story is one screen with all its states, a spec sheet and a reference file. Numbering matches the handoff folder.

| # | Story | States | Status |
| --- | --- | --- | --- |
| 01 | Freemium profile + Like | 6 — A at rest · B Be sure · C optional message · D minimum content · E full scroll · F actions coach (every visit until Don't show again) | ticketed — not built |
| 02 | Premium grid | 7 — A at rest · B marked · C card or heart → profile · D Like on the profile → Be sure · E liked-you → Confirm date · F refused → closed in place · G liked-you declined → Undo | ticketed — not built |
| 03 | Likes tab — likes received | 7 — A list · B Confirm date → profile · C Be sure · D Undo toast · E refused → Too late · F Out of time · G empty | ticketed — not built |

**Not ticketed yet, inside this epic:** the server queue (composition and order) · the no-results screen (`search-empty`, *widen filters* in one tap). **Outside it, own stories:** the likes upsell · the `Like received` push · the Show-up confirmation mechanic (Arrive → QR scan).

Build order is 01 → 02 → 03: each extends the one before and copies nothing.

## Built once, not per screen

A second copy of any of these is a bug.

- **`ScreenSearchProfile`** — the one full profile. Built by 01. 02 adds five props (`premium`, `onBack`, `backLabel`, `likedYou`, `onAction`); 03 adds `tab`. With no props set it behaves exactly as 01. **Extended, never forked**
- **`ShowUpCount`** — the Show-up display everywhere in Search. Built by 01, into the shared component set next to `Chip`
- **`searchDistance`** — `< 1 km` / `~N km`. Built by 01
- **`SharedVibesRow` · `SharedInterestsBlock` · `SharedChip`** — the overlap marking. Built by 01
- **`SearchActionsCoach` + `SEARCH_COACH_ROWS`** — the actions coach for the three buttons. Built by 01; Search 02 shows it over the first profile opened from the grid. Only `Don't show again` is stored (server, per user)
- **`LikeConfirmModal` + the message sheet** — Be sure and the optional message. Built by 01; **`mutual` mode** added by 02 (CTA `Confirm date`, no message step), consumed by 03
- **`SearchTopBar`** — built by 01; `premium` added by 02; `filter` and `chip` added by 03
- **`PremiumGridCard`** (incl. the closed state, `GRID_CLOSED`) and **`PremiumLikedYouBadge`** — built by 02
- **`LikesUndoToast` + `LIKES_UNDO_MS` (6000)** — built by 02, reused by 03
- **The Likes tab** — a prop on the one shared `TabBar`. Built by 03; every screen with a TabBar passes it
- **`likesOrder` · `likesOpenCount`** — built by 03; the badge and the chip read the same count
- **Consumed, not built here:** `CHECKIN_MOTIVATIONS` (Check-In 03), `CHECKIN_VIBE_LABEL` (Check-In 04), `LIFESTYLE_GROUPS` (Profile 21), the Filters screen (Check-In 05), `PremiumTag`

**Server, built once:** the search profile payload (01, reused per grid card and likes card) · the like endpoint with **withdrawal of the user's other open likes on a match** (01) · the pass endpoint (01) · **serialised confirms, first to arrive wins** (02) · overlap expiry → `no_overlap` (03) · the open-likes badge count (03) · `plan_tier` on the session (02).

## Flow-level rules — every story inherits these

**1. Search needs a committed Check-In** (Check-In epic, rule 1). **A user with an active date can't search**: Search and Instant are blocked in the nav bar by 07a. No Search story draws a lockout state.

**2. The plan picks the surface, not the tab.** Freemium → 01, Premium → 02, both on the Search tab. Nothing in 02 or 03 changes 01.

**3. The full profile comes first.** Every decision is made on `ScreenSearchProfile`, never on a card. The grid heart is a marker; `Confirm date` on a likes card opens the profile.

**4. Show-up is a count, never a percentage.** `ShowUpCount`: `N dates attended`, counting only dates both people confirmed; `0` shows `New in the App`. A missed date is never counted, shown or recorded. No "x of y", no bands.

**5. Every section is optional.** No content, no section — never a placeholder. A hidden detail drops its row; a hidden age leaves the name alone. Distance is always rounded. The meet time is the **earliest overlap** of both Check-Ins, `today` or `tomorrow`.

**6. One overlap is highlighted: vibe today.** Shared interests and vibes are marked in their own cards (filled + ✓). Nothing else is compared, and there is no match %. Only fields both users filled in are compared.

**7. Every profile has three actions**, `Liked you` ones included: `Not interested` · `Not now` · `Like` (or `Confirm date`).
- `Not now` — back at the viewer's **next Check-In**. On a `Liked you` profile it does **not** decline the like.
- `Not interested` — out of the pool **for good**. On a `Liked you` profile it also declines the like, the liker is not told.
- **Undo (6 s) only where a like is declined:** `Not interested` on a `Liked you` profile and on the Likes tab. Every other pass has no undo.

**8. The like flow.** Be sure (`Continue`) → optional message (≤ 100, Premium recipients only can read it) → sent. On a `Liked you` profile it is a confirm: mutual Be sure, `Confirm date`, **no message step**. Cancelling or dismissing a sheet sends nothing and stays where it was.

**9. The server decides who matches.** Confirms are serialised; the first wins. A refused confirm returns the user to where they came from with **that card closed in place**, never an error sheet. Two closed reasons, §36: `matched` → **Too late**, `no_overlap` → **Out of time**. Closed cards have no actions and are never counted.

**10. Four tabs for every user: `Search · Likes · Instant · Settings`** (21 Oct 2026). The Likes badge and the likes chip show the **open likes** count, Freemium too. Premium → the Likes tab; Freemium → the likes upsell (own story; until it ships the tap does nothing). The chip is on the Search and Instant tabs only. *Spec sheets of other flows that still show three tabs gain the Likes tab with no change of their own; re-export each when next touched.*

**11. Not in MVP:** Premium filters (the filter button opens Check-In 05 for everyone; the Show-up band filter is dropped for good) · report and block · a daily like limit.

**No absolute Y positioning, anywhere.** One scroll container per screen, nested flex columns, chrome on `env(safe-area-inset-*)`.

**Copy is final and lives in the stories.** Product terms: **Show Up** · **Show-up Rate** · **Match Mate**.

## Tracking

Family **I — Search & Liking** in `design_handoff_showup/tracking/events.json`, plus `screen_viewed` (family A). Registry **1.4.29** / taxonomy **1.4.20**. Screens §11: `search_profile` (01), `search_grid` (02), `likes_received` (03). Value sets: §33 like-abandon `stage` · §34 `search_pass_type` · §35 `like_surface` · §36 `like_close_reason` · §37 `nav_tab`.

- `like_sent` · `match_created` · `like_withdrawn` — server; `like_withdrawn` carries `reason` (§36), one per closed like
- `pass_sent` — every `Not now` / `Not interested`, with `surface`; a decline from the Likes tab is `pass_sent` · `not_interested`, sent when the Undo window ends. **There is no separate decline event**
- `pass_undone` — the Undo
- `tab_switched` — respecified for four tabs (§37)
- `search_coach_shown` · `search_coach_dismissed` — the actions coach (Search 01 and 02), every visit until `Don't show again`; an overlay, no §11 row
- Show-up in payloads is `target_attended_count`, never a band

Cite event names in every ticket; never describe them. Never run Jira's "improve description" over a pasted ticket.

## Device matrix

375 × 667 · 390 × 844 · 430 × 932. Check 375 × 667 first. The profile, the grid and the likes list each scroll on every device.

## Dependencies

- **Check-In 06** — the commit that unlocks Search, and the Uplift text. **Check-In 03 / 04 / 05** — §30 motivations, §31 vibes, Filters
- **Profile 06–08, 14–21** — every field the profile shows, with its visibility flags
- **07a Match & date confirmation** — receives a won confirm; blocks Search and Instant during an active date
- **Server:** `plan_tier` · the profile payload · like, pass and confirm endpoints · withdrawal on match · overlap expiry · the badge count · the queue endpoint (own ticket)

## Definition of done — epic level

- [ ] A Freemium and a Premium user can each go from a committed Check-In to a confirmed date on all three devices
- [ ] `ScreenSearchProfile`, `ShowUpCount`, `LikeConfirmModal`, `LikesUndoToast` and the `TabBar` exist **once each**
- [ ] No Show-up percentage or band appears anywhere in Search
- [ ] Two users confirming the same third person: one wins, the other lands on a closed card, no error sheet
- [ ] A match closes the user's other open likes, and they show as `Too late` to their recipients
- [ ] Every user's TabBar has four tabs; the badge and the chip always show the same number
- [ ] Every event fires per `events.json`, with no invented names

## Out of scope

The likes upsell and buying Premium. The `Like received` push. The Show-up confirmation mechanic. The match screen and everything after a match (07a). Premium filters. Report and block. Instant.

## Open — epic level

- **Check-In expiry while browsing.** The 48-hour window ends with the user on a profile or the grid: what do they see, and what happens to their open likes?
- **The queue ticket** (composition and order) is not written. 01 reads from its endpoint.
- **The no-results screen** (`search-empty`) has no ticket. It is drawn in the kit.
- **`Show-up Rate` in Be sure row 5** — keep the term over a count, or reword once the confirmation mechanic is designed? (Search 01)
- **The percentage `ShowUpPill`** is still used by the date screens and Instant. Each moves to `ShowUpCount` in its own story.
- ~~**Lockout during an active date**~~ — settled 20 Oct 2026: 07a blocks Search and Instant in the nav bar.
- ~~**Report / block**~~ — settled 20 Oct 2026: not in MVP.
