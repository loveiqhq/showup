# [Search 02] Premium grid

**Parent:** [Epic] Date search (`search/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `search-02-grid-attachment.zip` (the handoff folder, `design_handoff_showup/`) · `search-02-grid-spec-sheet.png` · first comment: `search-02-grid-tracking-comment.md`
**Blocked by:** **Search 01** (`ScreenSearchProfile`, `ShowUpCount`, `searchDistance`, the Be sure and message sheets: this story extends them and copies none of them), **Check-In 03** (§30 motivations), the **Premium entitlement** on the server (`plan_tier`). **Blocks:** nothing. Search 03 opens from this screen's likes chip **and** from the Likes tab it adds to the TabBar (chip restored 21 Oct 2026).

## Description

The Search tab for a **Premium** user with a committed Check-In: **a grid of profiles**, two per row, instead of Search 01's one-at-a-time queue. A user without Premium gets Search 01; nothing here changes that screen.

A card does one thing: **a tap anywhere on it — photo or heart — opens the full profile** (Search 01's screen, every section, with a back button). **There is no shortcut.** Like, Not now, Not interested and Confirm date exist only on the profile, so the user always sees the whole person before deciding. Every action there returns to the grid.

**A final answer takes the card out of the grid**: after Like or Not interested the card flies out and the rest close the gap. After Not now it **stays, marked**, and can be reopened. A card for someone who **already liked the viewer** carries a `Liked you` badge, and liking them back is a **confirm**: it makes the match.

Eleven frames on the sheet: **A** at rest · **B** after Like / Not interested → gone, Not now stays · **C** card or heart → full profile · **D** Like on the profile → Be sure · **E** a "Liked you" profile → Be sure, mutual · **F** after a refused confirm → card closed in place · **G** a "Liked you" profile declined → Undo toast · **H** confirm refused → sorry sheet (matched) · **I** sorry sheet (no overlap) · **J** back from a profile → availability re-checked · **K** the fly-out, frozen at 440 of 800 ms. *H–K added and B redrawn 21 Oct 2026.*

Entered from:

- Check-In 06 **Find my date**, saved → here (Premium).
- The **Search** tab, while a Check-In is active (Premium).
- Back from a profile opened from this grid.

Exits:

- Card tap, heart included → **the full profile** (`search_profile`). Back, Not now, Not interested, Like (once the message step is done) and Confirm date all return here.
- Top-bar **filter** → **Check-In 05 Filters**.
- Top-bar **likes chip** → the **Likes tab** (Search 03). Received likes are also the Likes tab in the TabBar (badge = open likes). ⚠ *21 Oct 2026: the chip is restored; the 20 Oct "Premium has no likes chip" is superseded.*
- The queue is empty → the *no results · widen filters* screen (its own story).

## Decided before build

All decided 20 Oct 2026 (`search/README.md` → *Premium*); the **21 Oct** points align this screen with Search 03 and remove the Like shortcut. The kit matches every point below.

- **Every Search 01 rule carries over unchanged:** `ShowUpCount`, never a percentage (⑦); distance rounded, `< 1 km` / `~N km` (⑨); a hidden age leaves the name alone (⑧); the motivation label from §30 (⑩); the meet time is the **earliest overlap** of both Check-Ins, `today` or `tomorrow` (⑨); the same Be sure and message sheets (⑫), opened from the profile. ⚠ *Supersedes the kit's earlier grid: percentage `ShowUpPill`, exact distance, "Av. from" and the free-text intent are all gone.*
- **No header and no count** (②). "Live · 47 ready to meet" is removed. The Premium tag sits in the top bar (①).
- **No Premium filters in MVP.** The filter button opens the same Check-In 05 Filters as Freemium. Height, How I live and intent filters come later; the Show-up band filter is dropped for good.
- **21 Oct — always the full profile first; no shortcut** (④ ⑥ ⑫). The card is **one control**: a tap on the photo or on the heart opens the full profile. The heart is a **marker** (outline / filled when liked), not a button. Every decision is made on the profile. ⚠ *Supersedes "the heart likes from the grid", 20 Oct 2026.*
- **21 Oct — a final answer removes the card** (⑳, B, K). After **Like**, **Not interested** or a **confirmed date** the user returns to the grid and the card **flies out**; the rest close the gap. After **Not now** the card **stays, marked** (⑪: dimmed, label, no heart) and still opens the profile, where a new decision replaces the mark. **The next visit applies the Search 01 pool rules.** ⚠ *Supersedes "after an action the card stays, marked" (20 Oct 2026) for Like and Not interested.*
- **From the full profile, every action returns to the grid** (⑬), where Not now marks the card and every other action removes it (⑳): `Not now` and `Not interested` straight away; `Like` once the flow is finished (`Skip message` or `Send message`); `Confirm date` once Be sure is confirmed. **Cancelling or dismissing a sheet stays on the profile.** The Search 01 rule "the next profile slides in" does **not** apply to a profile opened from the grid.
- **The `Liked you` badge stays** (⑤), with an orange ring on the card, even though Likes received (Search 03) also lists these people.
- **Liking a `Liked you` card is a confirm** (⑭ ⑮). Be sure opens in **mutual mode**: its line says `Confirm, and you and {name} have…`, the CTA is `Confirm date`, and **there is no message step**. Confirming creates the match; what happens after the match is **07a**, not this story.
- **21 Oct — the server holds the confirm; the first to arrive wins** (⑯). A refused confirm first shows the **sorry sheet** (⑱, H, I) over the profile, then returns to the grid with **that card closed, in place** (F): `Too late` (matched) or `Out of time` (no overlap). ⚠ *21 Oct 2026: supersedes "No error sheet". The user expected a match here, so a silent card change read like a fake profile.*
- **21 Oct — a plain Like is never refused.** Liking someone who has just matched or run out of time succeeds for the user; the like simply never becomes mutual. The sheet is for **Confirm date on a `Liked you` profile only**.
- **21 Oct — availability re-check** (⑲, J). On load the server sends **only available people**. **On every return from a profile** the client re-reads the status of every card in the grid: `matched` or `no_overlap` → the card closes in place (⑯), **liked or not**; the earliest overlap moved → the card shows the new meet time, no highlight. **No live push in MVP** — a later story.
- ~~A `Liked you` profile has no `Not now`.~~ ⚠ *Superseded 21 Oct 2026:* **every profile has all three actions** (⑭), `Liked you` ones included: `Not interested` · `Not now` · `Confirm date`. `Not now` puts them back in the pool at the next Check-In and **does not decline their like**: it stays open in the Likes tab until it closes. `Not interested` there also **declines their like for good**, as on Likes received.
- **21 Oct — that decline has the Search 03 6-second Undo** (⑰). Back on the grid the card flies out and the toast offers `Undo`, which **puts the card back in its old slot**; the decline is sent when the toast ends. ⚠ *Supersedes Search 01's "no undo" for this case only. Not now, and Not interested on any other profile, stay without undo.*
- **21 Oct 2026 — the Search 01 actions coach shows here too** (Search 01 frame F). Over the **first profile opened from the grid** on each Search visit, never on a `Liked you` profile (its third button is `Confirm date`); the coach waits for the next one with `Like`. `Got it`, a scrim tap or Android back close it for this visit; `Don't show again` stores the per-user flag. **Built by Search 01; this story only passes `coach` to `ScreenSearchProfile`.** Tracking as Search 01 (`search_coach_shown`, `search_coach_dismissed`).
- **Likes chip:** for Premium it is a **button** that opens the Likes tab (①). Count = open likes, the same as the tab badge. The top bar is Wordmark + PremiumTag + likes chip + filter. ⚠ *21 Oct 2026: restored; the 20 Oct removal is superseded.*

## Layout — read this before building

⚠ **The grid is the only scroll container, and its height is the queue.** Nothing in it is positioned by Y.

**Screen:** StatusBar → SearchTopBar (①) → **the grid** (②) → TabBar (③).

**Grid:** padding `4 16 16`, **two columns `minmax(0, 1fr)`, gap 12**. Every card is the same height (248). There is no header above the first row.

**Card:** **one button** (→ profile). The heart sits inside it, top-right, and is part of the same tap target.
- Photo h **248**, r 20, cover, with a bottom scrim (`rgba(20,12,28,0)` at 42% → `.66`).
- **Closed card** (⑯): dimmed as a marked card, `Too late` / `Out of time` pill, a reason line that **wraps** in place of meet time and motivation; no badge, ring, heart or `ShowUpCount`.
- `Liked you` badge top 12 / left 10; marked label top 12 / right 10; heart 36, top / right 8, `aria-hidden`.
- **Removal motion** (⑳): t = 0 when the back transition has settled. Hold **150** ms (the filled heart or the label is visible) → the card moves to the visible grid's centre x and 72% of its height, scales to **.12** and fades to 0 over **800** ms, `cubic-bezier(.55,0,.8,.2)` → the other cards move to their new slots over **250** ms, `cubic-bezier(.2,.7,.2,1)` (FLIP). **Reduced motion:** a 200 ms fade, then the reflow. Undo runs the reflow in reverse. The leaving card is not a tap target. Values are in `GRID_LEAVE_HOLD_MS` / `GRID_LEAVE_MS` / `GRID_REFLOW_MS`; the motion is **`useCardFlyOut`**, built here and reused by Search 03's Likes tab — one implementation.
- **The info block is anchored to the bottom** (12 from each edge, flex column, gap 5) and grows upward: `ShowUpCount` → name → meet time · distance → motivation.

**Profile from the grid:** Search 01's screen unchanged, plus the `All profiles` back pill under the top bar (⑬). Returning restores the grid's scroll position.

**Sheets** open over the grid or the profile; whatever is underneath keeps its scroll position.

**Sorry sheet** (⑱): `ConfirmRefusedSheet` in Search 01's `ModalShell`, over the profile. Padding `20 24 24`, a centred column with gap 14: photo **96** round (grayscale .6, opacity .85) with the reason pill overlapping its bottom edge → headline → body → motivation line → one full-width sunset button. Its height is its content.

**Undo toast** (⑰): `LikesUndoToast`, built here and reused by Search 03, 16 from each side, 12 above the TabBar, over the grid. It does not scroll with the grid.

📎 All specs are on the attached **search-02-grid-spec-sheet.png**: frames A–K and callouts ①–⑳. Violet callouts are the 20 Oct decisions. The design system has colour and type tokens only and no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/search/screen-search-grid-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. States: `<ScreenSearchGrid/>` (A) · `<ScreenSearchGrid initialMarks={{ Maya: 'not_now' }} initialGone={['Sofia', 'Emma']}/>` (B) · `<ScreenSearchGrid initialOpen={1}/>` (C) · `<ScreenSearchGrid initialOpen={1} initialProfileModal="confirm"/>` (D) · `<ScreenSearchProfile premium likedYou … initialModal="confirm"/>` (E) · `<ScreenSearchGrid initialClosed={{ Leonie: 'matched' }}/>` (F) · `<ScreenSearchGrid initialUndo="Anna" initialGone={['Anna']}/>` (G) · `<ScreenSearchGrid initialOpen={0} initialProfileModal="refused" refuse={{ Leonie: 'matched' }}/>` (H) · `<ScreenSearchGrid initialOpen={3} initialProfileModal="refused" refuse={{ Anna: 'no_overlap' }}/>` (I) · `<ScreenSearchGrid initialClosed={{ Sofia: 'matched', Emma: 'no_overlap' }} initialMoved={{ Anna: { meetAt: '18:00', meetDay: 'tomorrow' } }}/>` (J) · `<ScreenSearchGrid initialMarks={{ Sofia: 'liked', Maya: 'not_now' }} freezeLeaving={{ name: 'Sofia', t: 440 }}/>` (K). `refuse`, `recheck`, `initialMoved`, `initialGone` and `freezeLeaving` are kit demo props; in the build they are the server's answers. **Builds** **`ConfirmRefusedSheet` + `CONFIRM_REFUSED_COPY`** (Search 03 reuses them), `ScreenSearchGrid`, `PremiumGridCard` (incl. the closed state, `GRID_CLOSED`), **`LikesUndoToast` + `LIKES_UNDO_MS` (6000)** — Search 03 reuses them, `PremiumLikedYouBadge`, the `premium` prop on `SearchTopBar` (⚠ `likes` / `onLikes` superseded: Premium has no chip — Search 03), the `mutual` mode of `LikeConfirmModal`, and five props on **`ScreenSearchProfile`**: `premium`, `onBack`, `backLabel`, `likedYou`, `onAction`. **Consumes** everything Search 01 builds, `CHECKIN_MOTIVATIONS` (Check-In 03) and `PremiumTag`. **The profile is extended, not forked**: a second profile component is a bug.
> Shell components: `design_handoff_showup/components/shared.jsx` (`StatusBar`, `Card`, `Icon`, `Button`, `PortraitPlaceholder`, `TabBar`, `Wordmark`) · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **Status bar and home indicator** are stylized mocks. Ship the platform chrome; anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.
- **Photos** in the kit are placeholders. Ship the user's first photo, `object-fit: cover`.

## Copy — final strings

`{name}` is the profile's first name. `{day}` is `today` or `tomorrow`.

**Top bar:** Premium tag `Premium` (its own label, always that word) · filter accessible label `Filters` · likes chip `{n} likes` / `1 like` (accessible label the same; restored 21 Oct 2026)
**Card:** `Liked you` · name `{name}, {age}`, or `{name}` when age is hidden · `{HH:MM} {day} · < 1 km` / `{HH:MM} {day} · ~{n} km` · motivation: the §30 label · Show-up as Search 01 (`{n} dates attended` · `1 date attended` · `New in the App`)
**Marked label:** `Not now` (`Not interested` shows only during the 150 ms hold)
**Accessible names:** card `Open {name}'s profile` · liked card `Open {name}'s profile, liked` · the heart has none (it is part of the card)
**Closed card:** pill `Too late` · line `Has a date with someone else.` · accessible name `{name}. Too late — {name} has a date with someone else.` — or pill `Out of time` · line `No time left to meet.` · accessible name `{name}. Out of time — you and {name} have no time left to meet.` (the Search 03 first lines). No gendered pronoun.
**Undo toast:** `{name}'s like declined.` · `Undo` (Search 03)

**Sorry sheet — matched (H, ⑱)**
Pill: `Too late`
Headline: `We're really sorry, {name} confirmed a date just before you did.` — em on `just before you did.`
Body: `A like closes the moment either of you matches, and this time {name}'s match came first.`
Line: `There are still people ready to meet you. Your date could be one of them.`
Button: `Find someone else`

**Sorry sheet — no overlap (I, ⑱)**
Pill: `Out of time`
Headline: `Sorry, you and {name} ran out of time.` — em on `ran out of time.`
Body: `We're sorry this one didn't work out. Your Check-In and {name}'s no longer share any time, so the like has closed.`
Line and button: as above

No gendered pronoun in either.
**Profile from the grid:** back `All profiles` · on a liked-you profile: badge `Liked you`, action bar `Not interested` · `Confirm date`

**Be sure — Like on the profile (D):** exactly Search 01 ㉒, CTA `Continue`. **Optional message:** exactly Search 01 ㉓.

**Be sure — mutual (E, ⑮)**
Header: `Be sure.` — em on the whole phrase
Line: `Confirm, and you and {name} have a confirmed, binding date {day}.` — `confirmed, binding date {day}` in violet
Sub and rows: exactly Search 01 ㉒
Buttons: `Cancel` · `Confirm date` + check icon

## Behaviour

**Who sees it**
- The server's entitlement (`plan_tier: premium`) decides **grid or queue** each time the Search tab opens. Premium → this screen; Freemium → Search 01. A user who loses Premium mid-session gets Search 01 on the next open; the screen never switches under the user.

**Grid**
- Cards in queue order from the server. The order is not this story's.
- The grid loads once per visit, with **only available people** (no closed card on load). Marks are local to the visit; the server's pool rules (Search 01) decide what the next visit contains.
- **Availability re-check (J):** on every return from a profile (Back or any action), the client asks for the status of **every card in the grid** in one call. `matched` / `no_overlap` → the card closes in place (⑯), whatever its mark; a new `meet_at` / `meet_day` → the card shows it. Cards never reorder, appear or disappear. If the call fails, the grid stays as it is and nothing is retried until the next return. Nothing else triggers it in MVP: no polling, no push, no foreground refresh.
- An empty queue → the no-results screen.

**Card tap → profile** (the photo, the heart, anywhere on the card)
- Opens `ScreenSearchProfile` with `premium`, `onBack`, `backLabel="All profiles"`, and `likedYou` when the card has the badge. The profile behaves exactly as Search 01 (sections, media, overlap), except where it ends.
- **Back** returns to the grid at the same scroll position, card unchanged.
- **Not now** → back to the grid, card marked. **Not interested** → back to the grid, card flies out (⑳). Pool effect as Search 01. On a `Liked you` profile, `Not now` leaves their like **open**: the Likes badge and the Likes tab do not change.
- **Like** → Be sure → message → `Skip message` / `Send message` → back to the grid, card flies out (⑳).
- **Confirm date** (liked-you profile) → Be sure (mutual) → `Confirm date`. **The button stays inert until the server answers**; the sheet does not close early.
  - **Won** → match. The match screen is 07a's; this story hands over (the grid is not shown again this visit).
  - **Refused** → Be sure swaps to the **sorry sheet** for the server's reason (H `matched`, I `no_overlap`). `Find someone else`, a scrim tap, swipe-down and Android back all do the same: the sheet and the profile close and the user is back on the grid with **that card closed in its current position** (F), announced with its accessible name. It never moves while the user is on the grid.
- **Not interested on a liked-you profile** → back to the grid, card flies out, toast for **6 s** (G). The decline is sent when the toast ends, when the user leaves the grid (another card, a tab, Filters) or when the app goes to the background. **Undo** puts the card back in its old slot (the reflow in reverse) and nothing is sent. A second decline inside the window sends the first and starts a new toast.
- **Cancel, scrim tap or swipe-down on a sheet** closes it and stays on the profile. Nothing is sent.

**The heart**
- A marker only: outline on every card in the grid. It turns filled sunset during the 150 ms hold after a Like, then the card flies out. A tap on it is a tap on the card. There is no unlike.

**Marked cards**
- **Only Not now marks a card:** photo grayscale .9 + opacity .5, the `Not now` label, no heart, no ring, no badge. It **still opens the profile**, and the profile's actions still work: Like or Not interested there removes it.
- **Liked and Not interested cards are not in the grid** after the fly-out. The availability re-check therefore never touches a card the user liked.
- **Closed** (refused confirm, or the re-check): **stays in its slot** — not the user's answer, so no fly-out — dimmed the same way, with the reason pill and line. **Not a tap target** and no heart. Gone on the next visit.
- **The Likes badge** (Search 03) follows the server: it drops when a confirm wins or is refused, or when a decline is sent from the grid.

**Top bar**
- PremiumTag is a label, not a control.
- Likes chip → the Likes tab (Search 03), count = open likes. ⚠ *21 Oct 2026: restored (was removed 20 Oct).* Until Search 03 ships, the chip shows the count and does nothing on tap.
- Filter → Check-In 05 Filters; returning reloads the grid with the new filters.

**Accessibility**
- **One control per card** with the accessible name above; the heart is `aria-hidden`. A closed card is not a control (`role="group"` with its accessible name).
- A marked card announces its mark (`Not now`, `Not interested`, `liked`) as part of its name; the mark is never colour-only.
- Sheets trap focus and return it to the control that opened them.

## Build inventory

- **Client:** everything listed under *Builds* above. The five `ScreenSearchProfile` props are additive; Search 01's behaviour with none of them set must not change.
- **Server — entitlement:** `plan_tier` on the session, read when the Search tab opens.
- **Server — grid payload:** the Search 01 profile payload per card (rounded distance, `meet_at` + `meet_day`, `attended_count`, hidden fields omitted) plus `liked_you` (bool). The card uses only the first photo, name, age, distance, meet time, motivation and `attended_count`; the rest loads when the profile opens.
- **Server — like:** Search 01's endpoint. A like on a `liked_you` profile is **mutual**: create the match and withdraw the user's other open likes, as Search 01. **Confirms are serialised per like — built here, reused by Search 03: the first wins; any other answers `closed` with the reason** (`matched` · `no_overlap`).
- **Server — like, the other side (21 Oct 2026):** a plain like to someone who is no longer available is **accepted** (the client sees success) and closed at once with `like_withdrawn` · `reason`. Only a mutual confirm is refused.
- **Server — availability status (NEW, 21 Oct 2026):** one call for a list of profile ids → per id `available` · `matched` · `no_overlap`, plus the current `meet_at` + `meet_day`. Read on every return from a profile.
- **Server — pass:** Search 01's endpoint. `not_interested` on a `liked_you` profile also **closes the inbound like** for good; the liker is not told. Called once, when the Undo window ends.

## Acceptance criteria

- [ ] A Premium user with an active Check-In sees the grid; a Freemium user sees Search 01, unchanged
- [ ] The grid is two columns, gap 12, padding `4 16 16`, and is the only element that scrolls; there is no header and no count
- [ ] Every card shows `ShowUpCount`, name (no comma when age is hidden), `{HH:MM} {day} · ~N km` on **one line at 375 × 667**, and the motivation label on one line with ellipsis; no percentage and no exact distance appear anywhere
- [ ] A `Liked you` card shows the badge and the orange ring
- [ ] Tapping **anywhere on a card, the heart included,** opens the full, scrollable profile with `All profiles`; Back returns to the grid at the same scroll position
- [ ] **No decision can be made from the grid:** Be sure never opens over the grid, and the grid has no Like, Not now or Not interested control
- [ ] From the profile, Not now and Not interested return to the grid at once with the card marked; Like returns after `Skip message` or `Send message` with the card marked liked
- [ ] Cancel, scrim or swipe-down on either sheet stays on the profile and sends nothing
- [ ] After Like (`Skip message` or `Send message`), Not interested or a confirmed date, the card **flies out** on return: hold 150, 800 ms to the bottom-centre at scale .12 and opacity 0, then the rest reflow in 250 ms. With reduced motion it fades in 200 ms, then reflows
- [ ] The motion starts only after the back transition has settled, and the leaving card cannot be tapped
- [ ] Not now cards stay, dimmed and labelled, with no heart; they still open the profile, and Like or Not interested there removes them
- [ ] A closed card (refused confirm, re-check) never flies out: it stays in its slot
- [ ] On a `Liked you` profile, `Confirm date` opens Be sure in mutual mode: `Confirm, and you and {name} have…`, CTA `Confirm date`, **no message step**; while the confirm is in flight the button is inert and the sheet stays open
- [ ] A refused confirm returns to the grid with that card closed **in the same position**: `Too late` for `matched`, `Out of time` for `no_overlap`; the closed card has no heart and is not a tap target
- [ ] A refused confirm shows the sorry sheet for its reason first (H `matched`, I `no_overlap`) with the person's photo, name and the exact copy above; `Find someone else`, scrim, swipe-down and Android back each close it and land on F
- [ ] A plain Like to someone who has since matched or run out of time shows **no error and no sheet**: the user returns to the grid with the card marked liked
- [ ] On load the grid contains no closed card. On every return from a profile the grid re-reads every card's status: a `matched` / `no_overlap` card closes **in its slot** (liked or not), a moved meet time is shown, and no card reorders, appears or disappears
- [ ] Nothing re-checks the grid except a return from a profile (no timer, no push, no foreground refresh in MVP); a failed re-check leaves the grid unchanged
- [ ] A `Liked you` profile's action bar is `Not interested` · `Not now` · `Confirm date` (superseded from `Not interested` + `Confirm date` on 21 Oct 2026: every profile has all three)
- [ ] `Not now` on a `Liked you` profile returns to the grid with the card marked `Not now`, **no undo**, and their like stays open: the Likes badge and the Likes tab are unchanged
- [ ] `Not interested` on a `Liked you` profile returns to the grid, the card flies out and `{name}'s like declined.` · `Undo` shows for **6 s**; Undo puts the card back in its old slot and **nothing reaches the server**
- [ ] The decline is sent when the toast ends, when the user leaves the grid, or when the app is backgrounded — and only once; it closes the inbound like for good and the liker receives nothing
- [ ] `Not now`, and `Not interested` on a profile without `Liked you`, still act with **no undo**
- [ ] Not now cards are gone on the next visit, following the Search 01 pool rules
- [ ] The Premium top bar shows the **likes chip** with the open-likes count (equal to the tab badge) and it opens the Likes tab (restored 21 Oct 2026); the filter opens Check-In 05 Filters
- [ ] With none of the new props set, `ScreenSearchProfile` behaves exactly as Search 01
- [ ] No absolute Y in the grid; the card info block is anchored to the card's bottom edge
- [ ] Copy matches the strings above exactly
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**
- [ ] **Evidence of done:** screenshots of **A–K** at **375 × 667, 390 × 844 and 430 × 932** (thirty-three images; K frozen with the same 440 ms offset). Each shows the top bar and TabBar fully visible and nothing clipped; on A and B the card meta line is on one line; on G the toast clears the TabBar; on H and I the sheet's button is fully visible above the home indicator. Check 375 × 667 first. Add six screen recordings: heart → full profile → scroll → Like → Be sure → Continue → Skip message → grid, card flies out · card → profile → Not now → grid, card marked · the same Like with reduced motion on · `Liked you` card → profile → Confirm date → Be sure → (staged) refusal → sorry sheet → Find someone else → grid, card Too late in place · card → profile → Back with another card's status (staged) changed → that card closes in place · `Liked you` profile → Not interested → card flies out → Undo → card back in its slot.

## Tracking

⚠ **The Tracking section is the ticket's first comment** (pinned), `search-02-grid-tracking-comment.md`. Split by length only (Jira caps a description at 32,767 characters), not by authority: the comment is part of this ticket and its rules are acceptance criteria.

Two rules that break first in a hurry:
- **Use the event names in `tracking/events.json` exactly** (registry **1.4.28**, taxonomy **1.4.19**). Do not invent names: opening a card is `screen_viewed`, a confirm is `like_tapped` + the Be sure events with `is_mutual: true`, a refusal is `like_confirm_failed` (once, when the sorry sheet opens).
- **The re-check and the sorry sheet's dismissal fire nothing.** They are state, not acts.

Do not run Jira's "improve description" on this ticket or the comment.

## Out of scope

Premium filters. The Likes tab and Likes received (Search 03). The paywall, the upsell and buying Premium. The match screen and everything after a match (07a). Queue composition and order. The no-results screen. Report and block. Instant.

## Dependencies

- **Search 01:** `ScreenSearchProfile`, `ShowUpCount`, `searchDistance`, the like and pass endpoints, the Be sure and message sheets.
- **Search 03** builds on this story: it reuses `LikesUndoToast`, the serialised confirm and the closed reasons (§36) built here.
- **Check-In 03 / 05 / 06:** §30 motivations, the Filters screen, the commit that unlocks Search.
- **Server:** `plan_tier`, the grid payload with `liked_you`, mutual-like match creation with serialised confirms, closing an inbound like on `not_interested`.
- **Tracking registry 1.4.28 / taxonomy 1.4.19.**

## Open (not blocking)

- ~~**A liked card closing on the re-check.**~~ **Settled 21 Oct 2026:** a liked card is removed with the fly-out, so the re-check never reaches it.
- **Every card closed after a re-check:** the grid stays as it is. Should it switch to the no-results screen instead? (That screen has no ticket yet.)
- **Live updates** (push or polling) are a later story.

- ~~**The Date search epic**~~ — written 21 Oct 2026 (`search/tickets/00-epic.md`).
- ~~**Search 03** will define the event for declining a like from Likes received.~~ **Settled 20 Oct 2026:** a decline is `pass_sent` · `not_interested`, here with `surface: "profile"` (in the table above).
