# [Search 01] Freemium profile + Like

**Parent:** [Epic] Date search (`search/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `search-01-profile-attachment.zip` (handoff folder for this ticket — **re-issued 27 Oct 2026** with the developer answers, taxonomy 1.4.24 and the no-results reference) · `search-01-profile-spec-sheet.png` (unchanged) · first comment: `search-01-profile-tracking-comment.md`
**Blocked by:** **Check-In 06** (a committed Check-In unlocks date search, and the Uplift text feeds ⑫), **Check-In 03** (§30 motivations), **Check-In 04** (§31 vibes), **Profile 06–08 and 14–21** (the fields this screen shows). **Blocks:** every later date-search story. `ShowUpCount` is built here.

## Description

The Search tab for a Freemium user with a committed Check-In: **one profile at a time**. The user reads the profile, then does one of three things: **Like**, **Not now** or **Not interested**. Like opens a two-step flow (Be sure → optional message), and the like is only sent at the end of it.

Six frames on the sheet: **A** at rest · **B** Like → Be sure · **C** Like → optional message · **D** minimum content · **E** full scroll, every possible section · **F** actions coach — what the three buttons do (added 21 Oct 2026).

Entered from:

- Check-In 06 **Find my date**, saved → here.
- The **Search** tab, while a Check-In is active.

Exits:

- **Like → Be sure → message → sent**, **Not now** or **Not interested** → **the next profile slides in**, on the same screen.
- Top-bar **filter** → **Check-In 05 Filters**. Saving or going back returns here as a **new Search visit** (27 Oct 2026).
- The queue runs dry → the **interim no-results screen** (below). The one-tap widen is its own story.

## Decided before build

All decided 20 Oct 2026. The kit matches every point below.

- **Every section is optional** (sheet note, D). If a field has no content, its section is not rendered. That includes questions skipped in profile creation. Never an empty card, a placeholder or "not answered".
- **A hidden detail drops its row** in Essentials (⑰). **A hidden age leaves the name alone**, with no comma (⑥).
- **Distance is rounded** (⑤): `< 1 km` under one km, `~N km` otherwise. Never the exact value.
- **The meet card shows the earliest overlap** of both Check-Ins (⑧): `19:00 today`, or `19:00 tomorrow` when the only overlap is tomorrow.
- **Show-up is a count, never a percentage** (⑨). `ShowUpCount` shows `N dates attended`, counting only dates both people confirmed. **Count 0 shows `New in the App`**, whether the user is new or has had no confirmed date yet. A missed date is never counted, shown or recorded. The confirmation mechanic (Arrive → QR scan) is a later story; this one only shows the number the server sends. ⚠ *Supersedes the percentage `ShowUpPill` on this screen. The pill stays in the kit for screens not yet re-ticketed.*
- **The Uplift highlight shows while the Check-In is active** (⑫), on both days if the Check-In covers two.
- **Vibe today is the only overlap that is highlighted** (⑩): a row under the meet card with at most 3 shared vibes plus `+N`. **Shared interests and vibes are marked in their own cards** with the same chip state (⑪ ⑬ ⑳). **Nothing else is compared** (not language, How I live, religion, politics, motivation; no match %). Only fields both users have filled in are compared, and if nothing is shared there is no row and no marking.
- **Like flow:** Be sure → optional message → sent (㉒ ㉓). The Be sure CTA reads **`Continue`**, not "Send like". **`Skip message` sends the like without a message.** Dismissing either sheet sends nothing.
- **The message is max 100 characters**, and only a **Premium** recipient can read it.
- **After Like sent, Not now or Not interested, the next profile slides in.** No toast, no confirmation screen.
- **Not now** hides the profile **until the viewer's next Check-In**. **Not interested** removes it from the viewer's pool **for good**. There is **no undo**.
- **Several open likes are allowed.** The first one to become mutual wins, and the server **withdraws** the rest (1 match = 1 date).
- **Media plays on tap only** (⑭ ⑯ ⑱). A photo tap opens the fullscreen viewer. Voice and video never autoplay, and they pause when scrolled out of view.
- **Likes chip (①):** shows the open-likes count (the same number as the Likes tab badge). **A tap opens the likes upsell screen** — its own story, not built: until it ships the tap does nothing. ⚠ *Superseded 21 Oct 2026: was "a static label".*
- **TabBar (③): four tabs, `Search · Likes · Instant · Settings`, with the Likes badge (open received likes)** — the same bar as Premium (21 Oct 2026). A Freemium tap on Likes opens the **likes upsell screen**, its own story and not built yet: until it ships the tap does nothing, and never opens Likes received.
- **21 Oct 2026 — actions coach (F, ㉔ ㉕).** Over the **first profile of every Search visit**, a card explains the three buttons: `Not interested` · `Not now` · `Like`. A scrim covers the screen; the action bar is lifted above it so the user sees the buttons being explained. **The buttons are inert while it shows.** Two buttons close it:
  - **`Got it`** closes it **for this visit only**. **A scrim tap and Android back are `Got it`**, never Don't show again.
  - **`Don't show again`** closes it and is **the only act stored**: a server-side flag per user, so it never shows again on any device.
  - **Freemium and Premium both get it.** Freemium: over the first queue profile. Premium: over the first profile opened from the grid (Search 02). **Not on the Likes tab or a `Liked you` profile**, whose third button is `Confirm date`; the coach waits for the next profile with `Like`.
- **No lockout state.** A user with an active date can't search: **07a** blocks Search and Instant in the nav bar (epic rule 1).
- **No report or block** in MVP.
- **Queue composition and order belong to the server ticket**, not this one.

## Decided 27 Oct 2026 — developer questions (SHOWUP-184)

- **Gender is tracked in `filters_active`.** The start value is `checkinGenderDefault(gender, orientation)` on the viewer's current profile, computed on the client. No server change. Only *that* it differs is sent, never the genders.
- **Filters opened from Search is tracked apart from Check-In**: same event `check_in_filters_changed`, new property `opened_from: "search"`. It is not a Check-In step: no `check_in_step_answered`, no `check_in_closed`.
- **Back from Filters is a new Search visit** for tracking: `screen_viewed`, `search_viewed` and `position` from 1. Changed filters reload the queue. **The actions coach does not show again** on that return.
- **Three states get copy** (Copy → *States*): load failed, no results (interim, from the kit's `search-empty`), filter save failed.
- **Charms stay hidden at launch** (`charms` null). Their source is the post-date rating story, written by design, not a backend-only ticket.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** The height of the stack *is* the profile's content, so it differs on every profile.

**Screen:** StatusBar → SearchTopBar (①) → **the stack** (the only scroll container) → action bar (②, sticky, bottom 64) → TabBar (③).

**Stack:** padding `0 16 16`, **flex column, gap 14**, in this order (the JSX order in `ScreenSearchProfile` is the spec):
hero (photo 1 ④ + meet card ⑧) → 18 spacer → shared vibes ⑩ → highlight ⑫ → vibe today ⑬ → photo 2 → prompt 1 → voice ⑯ → essentials ⑰ → photo 3 → prompt 2 → video ⑱ → How I live ⑲ → interests ⑳ → prompt 3 → photos 4 + 5 side by side → photo 6 → charms ㉑ → 80 spacer.

- **A missing section is not rendered** and the gap closes by itself. Photo 4 without photo 5 renders as a single 300 photo.
- **The meet card is the one overlap**: it hangs 22 below the hero photo, and the 18 spacer after it keeps ⑩ clear.
- **The action bar is sticky** above the TabBar with a fade behind it. The 80 spacer at the end of the stack keeps the last section reachable above it.
- **The sheets** (㉒ ㉓) open over the screen. The stack keeps its scroll position underneath.
- **The next profile** replaces the stack and resets the scroll to the top.
- **The actions coach (F)** sits **inside the action bar's own column**, above the buttons with gap 14, so its position follows the bar on every device — never a fixed Y. While it shows, the bar is raised above the scrim and its fade is removed. The scrim covers everything else, the top bar and TabBar included.

📎 All specs are on the attached **search-01-profile-spec-sheet.png**: frames A–F and callouts ①–㉕. Violet callouts are the 20 and 21 Oct decisions. The design system has colour and type tokens only and no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/search/screen-search-profile-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. States: `<ScreenSearchProfile/>` (A) · `<ScreenSearchProfile initialModal="confirm"/>` (B) · `<ScreenSearchProfile initialModal="message"/>` (C) · `<ScreenSearchProfile profile={SEARCH_V2_PROFILE_MIN} tall tallHeight={2000}/>` (D) · `<ScreenSearchProfile tall tallHeight={4600}/>` (E) · `<ScreenSearchProfile coach/>` (F). Interim no-results: `design_handoff_showup/search/screen-search-empty-reference.jsx` → `<ScreenSearchEmpty/>`, normal mode only (27 Oct 2026; not on the sheet). `tall` exists only to draw the whole stack on the sheet; the product screen always scrolls. **Builds** `ScreenSearchProfile`, **`ShowUpCount` (new shared component)**, `SharedVibesRow`, `SharedInterestsBlock`, `SharedChip`, `HowILiveBlock`, `VideoPromptCardV2`, `searchDistance`, the two sheets, and the actions coach (`SearchActionsCoach` + `SEARCH_COACH_ROWS`). **Consumes** `CHECKIN_MOTIVATIONS` (Check-In 03), `CHECKIN_VIBE_LABEL` (Check-In 04), `LIFESTYLE_GROUPS` (Profile 21). A second copy of any of these is a bug.
> Shell components: `design_handoff_showup/components/shared.jsx` (`StatusBar`, `Card`, `Chip`, `Icon`, `Button`, `PortraitPlaceholder`, `TabBar`, `Wordmark`) · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **Status bar and home indicator** are stylized mocks. Ship the platform chrome; anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.
- **Photos** in the kit are placeholders. Ship the user's photos, `object-fit: cover`.

## Copy — final strings

`{name}` is the profile's first name. `{day}` is `today` or `tomorrow`, from the meet card.

**Top bar:** likes label `{n} likes` (`1 like`) · filter button accessible label `Filters`
**Hero:** distance `< 1 km · {district}` / `~{n} km · {district}` · name `{name}, {age}`, or `{name}` when age is hidden
**Meet card:** `You could meet at:` · `{HH:MM} {day}`
**Show-up:** `{n} dates attended` · `1 date attended` · `New in the App`
**Shared vibes:** `You and {name} both want today` · overflow `+{n}`
**Section titles:** `{name}'s highlight` · `{name}'s vibe today` · `{name}'s voice` (sub `Voice prompt · {m:ss}`) · `{name}'s essentials` · `{name}'s video` (sub `Video prompt · {m:ss}`) · `{name}'s everyday` (sub `How I live`) · `{name}'s interests` (sub `{n} things {name} is into`, plus ` · {k} in common` when k ≥ 1) · `Charms from past dates`
**Essentials labels, in this order:** `Height` · `Gender` · `Orientation` · `Dates in` · `Education` · `Religion` · `Politics`
**Action bar, accessible names:** `Not interested` · `Not now` · `Like` (Like also shows its label)

**Be sure (㉒)**
Header: `Be sure.` — em on the whole phrase
Line: `If {name} becomes your match, you two have a confirmed, binding date {day}.` — `confirmed, binding date {day}` in violet
Sub: `What happens after a match?`
Rows, in this order:
1. `A safe, curated meeting spot — so you can focus on each other, not on logistics.`
2. `A time that fits both of your stated availability windows.`
3. `Stuck for words? Match Mate has topics, ice breakers, and quick games.`
4. `Only 30 minutes committed. If there's no spark — leave with a smile.`
5. `You meet who you matched — the Show-up Rate keeps it real.`
Buttons: `Cancel` · `Continue` + send icon

**Optional message (㉓)**
Header: `Optional message to {name}` — em on **Optional**
Body: `Premium users can see who sent them a like and can see your message. Adding a personal message increases your chances of getting matched.`
Draft (pre-filled, editable): `Hi {name}, I love the vibe of your profile. Would be great to meet you {day} :)`
Counter: `{n} / 100`
Buttons: `Skip message` · `Send message` + send icon

**Actions coach (F)**
Title: `Three ways to decide` — em on `decide`
Rows, in this order, each with the matching button's icon:
1. `Not interested` — `You won't see this profile again.`
2. `Not now` — `This profile can come back at your next Check-In.`
3. `Like` — `You'd like to meet. If they like you back, you have a date.`
Buttons: `Don't show again` · `Got it`
Accessible name of the card: `What the three buttons do`

**States (27 Oct 2026)**
Load failed (no connection or server error), in place of the stack: `Couldn't load profiles. Check your connection and try again.` · button `Try again`
No results, interim, from the kit's `search-empty` (`design_handoff_showup/search/screen-search-empty-reference.jsx`, normal mode): headline `Unfortunately there are no people left matching your search filters.` — em on `no people` · body `Widen your search filters to find more people.` · button `Widen my search` + sliders icon, which **opens Filters** until the no-results story defines the one-tap widen. Leave out the CTA sub-line and `Adjust filters manually`.
Filter save failed (Filters opened from Search): `Couldn't save your filters. Check your connection and try again.` — the user stays on Filters with their changes kept.

## Behaviour

**Sections and data**
- Each section renders only when its source has content (see the stack order above). Prompts render 1–3, photos 2–6 render what exists (4 minimum from Profile 06), How I live renders only the answered groups.
- **Hidden fields never reach the client.** A hidden age or a hidden detail is left out of the payload by the server, not hidden by the app.
- **Distance arrives rounded** from the server. The client never receives an exact distance or coordinates.
- **The Uplift card** shows while the profile's Check-In is active, and is absent when `uplift` is null.

**Meet card**
- Shows the **earliest overlap** of the viewer's and the profile's Check-Ins, as start time + `today` / `tomorrow`. The server computes it.
- `ShowUpCount` reads `attended_count` from the server: ≥ 1 → `N dates attended`, 0 → `New in the App`.

**Overlap**
- The client compares the profile's vibes and interests with **the viewer's own active Check-In vibes and profile interests**. Nothing else is compared.
- **Shared vibes row:** the shared vibes in the profile's order, at most 3, then `+N` for the rest. **Not rendered when nothing is shared.**
- **Inside the vibe and interests cards:** a shared chip is selected-orange with a ✓, every other chip is outlined. **One state, used in all three places.**
- If the viewer has no interests, nothing is marked in Interests and the `· k in common` suffix is absent.

**Media**
- **Photo tap → fullscreen viewer**, opening at that photo and swiping through all of them, in profile order. Close returns to the same scroll position.
- **Voice and video play on tap only.** Video plays with sound. Only one item plays at a time; starting one stops the other.
- **Playback pauses when the card scrolls out of view**, and when a sheet opens or the profile changes.

**Not now / Not interested**
- One tap, no confirmation, **no undo**. The next profile slides in.
- **Not now:** the profile is hidden until the viewer's next Check-In.
- **Not interested:** the profile is removed from the viewer's pool for good. It only affects the viewer's pool, not the other user's.

**Like**
- **Like → Be sure.** `Cancel`, a scrim tap or a swipe down closes it and sends nothing. `Continue` swaps to the message sheet.
- **Message sheet.** The draft is pre-filled and editable, at most **100 characters**. Input past 100 is not accepted, and a paste is cut at 100. `Skip message` sends the like without a message. `Send message` sends it with the trimmed text (an empty or whitespace-only text sends as no message). A scrim tap or swipe down closes it and **sends nothing**.
- **On send:** both sheets close and the next profile slides in. No toast. The request runs in the background. If it fails, retry silently; the like is idempotent per viewer + profile.
- **Several open likes are allowed.** When one becomes mutual, the server creates the match and **withdraws every other open like** of that user. The match notification is out of scope.

**Actions coach (F)**
- **When:** once per Search visit (each `screen_viewed` of the Search tab, **except the return from Filters**), over the first profile that has a `Like` button: the first queue profile (Freemium) or the first profile opened from the grid (Premium). Never on the Likes tab or a `Liked you` profile. An empty queue shows no coach.
- **Until the user opts out.** It shows on every visit until `Don't show again`. The server holds that flag per user; nothing else is stored. Backgrounding or killing the app while it shows closes nothing, so it shows again next visit.
- **While it shows:** `Not interested`, `Not now` and `Like` are visible above the scrim but **do nothing**; the stack does not scroll; the top bar and TabBar are under the scrim and not tappable.
- **`Got it`, a tap anywhere on the scrim, or Android back** close it for this visit. **`Don't show again`** closes it and sets the flag. Either way the same profile is revealed and nothing else is sent.
- The rows' icons and tones are the buttons' own (danger outline · muted outline · sunset), so each row reads as the button below it.

**Top bar**
- The likes chip shows the open-likes count. Tap → the likes upsell (its own story); until that ships, the tap does nothing and never opens Likes received.
- The filter button opens **Check-In 05 Filters**. Save or back returns to Search as a new visit; changed filters reload the queue from the first profile, unchanged filters keep the current profile. No coach on that return.
- **Load failed** replaces the stack with the load-failed state; `Try again` re-requests the queue.
- **Empty queue** shows the interim no-results screen; `Widen my search` opens Filters.

**Accessibility**
- The three action buttons have the accessible names above; the icon-only two are never unlabelled.
- A shared chip's state is carried by the ✓ as well as colour, and is announced (`shared`).
- Both sheets trap focus while open and return it to `Like` on close. The message counter is a polite live region.
- The coach is a `dialog` named `What the three buttons do`. Focus moves to `Got it` when it opens; everything behind it, the three buttons included, is hidden from assistive tech while it shows. On close, focus goes to `Like`.

## Build inventory

- **Client:** everything listed under *Builds* above. **`ShowUpCount` goes into the shared component set**, next to `Chip`; later stories move every Show-up display onto it.
- **Server — the search profile payload:** every section field nullable; hidden age and details **omitted**; distance **rounded** (`< 1` or whole km); `meet_at` + `meet_day` (earliest overlap, `today` | `tomorrow`); `attended_count` (int, ≥ 0); `uplift` while the Check-In is active; media URLs.
- **Server — like:** create a like with an optional message (≤ 100, enforced server-side), idempotent per viewer + profile. On the first mutual like: create the match and **withdraw the user's other open likes** (`like_withdrawn` · `reason: matched`). Fires `like_sent`, `match_created`, `like_withdrawn`.
- **Server — expiry (from Search 03):** a sent like also closes when the two Check-Ins have no overlap left (`like_withdrawn` · `reason: no_overlap`, `match_id` null). Nothing changes on this screen; the recipient sees it as an `Out of time` card on the Likes tab.
- **Server — pass:** `not_now` stored against the viewer's current Check-In window, so the profile comes back on the next one. `not_interested` stored as a permanent one-way exclusion.
- **Server — coach flag:** one boolean per user (`search_coach_hidden`), returned with the session and set **only** by `Don't show again`. Default false for every existing user.
- **Server — queue:** composition and order are **not in this ticket**. This screen reads the next profile from the queue endpoint.
- **Fullscreen photo viewer** — reuse the platform's own media viewer if the build has one.

## Acceptance criteria

- [ ] The stack renders in the order above, as a flex column with gap 14, and is the only element that scrolls
- [ ] **Every section without content is absent**: no empty card, no placeholder, no "not answered". Frame D renders with only the mandatory fields
- [ ] A hidden age shows the name alone, with no comma; a hidden detail has no Essentials row; neither value is in the API response
- [ ] Distance reads `< 1 km` or `~N km`; the exact distance is not in the API response
- [ ] The meet card shows the earliest overlap, `today` or `tomorrow`, on one line at 375 × 667 without wrapping
- [ ] `ShowUpCount` reads `N dates attended` (`1 date attended`) for N ≥ 1 and `New in the App` for 0; no percentage appears anywhere on the screen
- [ ] The shared vibes row shows at most 3 shared vibes plus `+N`, and is **absent** when nothing is shared
- [ ] Shared chips in the vibe card and in Interests use the same selected-orange state with a ✓; other chips are outlined; the Interests sub adds `· k in common` only when k ≥ 1
- [ ] No other field is compared or highlighted
- [ ] A photo tap opens the fullscreen viewer at that photo; voice and video never autoplay; playback pauses when scrolled out of view, when a sheet opens and when the profile changes
- [ ] `Not now` and `Not interested` act in one tap with no confirmation and no undo; the next profile slides in and the scroll resets to the top
- [ ] A `Not now` profile does not reappear before the viewer's next Check-In; a `Not interested` profile never reappears
- [ ] `Like` opens Be sure; `Cancel`, scrim or swipe-down sends nothing; `Continue` opens the message sheet
- [ ] The Be sure line names `today` or `tomorrow` to match the meet card
- [ ] The message field accepts at most 100 characters (a paste is cut at 100); the counter reads `n / 100`
- [ ] `Skip message` sends the like with no message; `Send message` sends it with the trimmed text; dismissing the message sheet sends nothing
- [ ] After a like is sent, the next profile slides in with no toast; a failed request retries without creating a second like
- [ ] When one of several open likes becomes mutual, the others are withdrawn server-side
- [ ] The likes chip shows the open-likes count, equal to the Likes tab badge; a tap does not open Likes received (the upsell is its own story); the filter button opens Check-In 05 Filters
- [ ] **Actions coach (F):** shows over the first profile of **every** Search visit, for Freemium (queue) and Premium (first profile opened from the grid), never on the Likes tab or a `Liked you` profile, never twice in one visit, not on an empty queue; the three buttons are visible above the scrim and **inert**
- [ ] `Got it`, a scrim tap and Android back each close it and it **shows again on the next visit**; `Don't show again` closes it and it **never shows again**, on any device; nothing else sets the flag
- [ ] The coach sits in the action bar's column above the buttons at 375 × 667, 390 × 844 and 430 × 932, never overlapping them and never clipped by the top bar; both buttons fit on one row
- [ ] No absolute Y in the stack; the meet card's −22 overlap is the only overlap
- [ ] Load failed, no results and filter save failed show the strings under *States*; `Try again` reloads; `Widen my search` opens Filters
- [ ] `filters_active` includes `meet_gender` only when the saved genders differ from `checkinGenderDefault` of the current profile; no gender value appears in any payload
- [ ] Filters opened from Search fires `check_in_filters_changed` with `opened_from: "search"` and no `check_in_step_answered` / `check_in_closed`; the return is a new visit with no coach
- [ ] The message text appears in **no** tracking payload
- [ ] Copy matches the strings above exactly
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**
- [ ] **Evidence of done:** screenshots of **A, B, C, D and F** at **375 × 667, 390 × 844 and 430 × 932** (fifteen images), plus **E** as a full-length capture at 390 wide. Each shows the top bar, action bar and TabBar fully visible and nothing clipped. Check 375 × 667 first. Add one screen recording: Like → Be sure → Continue → Skip message → next profile, one of Not now → next profile, and one of the coach: `Got it` → leave Search and return (coach again) → `Don't show again` → leave and return (no coach).

## Tracking

⚠ **The Tracking section is the ticket's first comment** (pinned), `search-01-profile-tracking-comment.md`. Split by length only (Jira caps a description at 32,767 characters), not by authority: the comment is part of this ticket and its rules are acceptance criteria. Registry **1.4.31**, taxonomy **1.4.24**.

Three rules that break first in a hurry:
- **`screen_viewed` fires once per Search visit, not per profile**; `position` resets on each one, including the return from Filters (no coach on that return).
- **Filters opened from Search** fires `check_in_filters_changed` with `opened_from: "search"`, never `check_in_step_answered` / `check_in_closed`.
- **The message text and the gender values are never sent**, in any event.

Do not run Jira's "improve description" on this ticket or the comment.

## Out of scope

Premium: the grid, Likes received, the likes upsell. Queue composition and order. The no-results story (the one-tap widen; this ticket ships the interim screen). Report and block. The Show-up confirmation mechanic (Arrive → QR scan). Match notifications and anything after a match. Instant.

## Dependencies

- **Check-In 06:** the commit that unlocks Search, and the Uplift text.
- **Check-In 03 / 04 / 05:** §30 motivations, §31 vibes, the Filters screen the top bar opens.
- **Profile 06–08, 14–21:** photos, prompts, media, details, How I live, interests, with their visibility flags.
- **Server:** the search profile payload, the like and pass endpoints, match creation with like withdrawal, the queue endpoint, the per-user coach flag.
- **Tracking registry 1.4.31 / taxonomy 1.4.24.**

## Open (not blocking)

- ~~**The Date search epic**~~ — written 21 Oct 2026 (`search/tickets/00-epic.md`). Its open points (Check-In expiry while browsing, the queue ticket, no-results) do not change this story.
- **`Show-up Rate` in Be sure row 5.** The term stays, but the display is now a count. Keep the line, or reword it once the confirmation mechanic is designed?
- **Search 02** shows the same coach over the first profile opened from the grid; it consumes `coach` on `ScreenSearchProfile`, nothing new.
- **Charms** have no source until the post-date rating story ships (kit `ScreenRating`, not ticketed; design writes it). The section is absent at launch. That story must drop name-bearing charms such as `From Tom`.
