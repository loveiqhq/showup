# [Check-In 01] Intro + date search not available

**Parent:** [Epic] 48hrs Check-In flow (`checkin/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `checkin-01-intro-attachment.zip` (handoff folder for this ticket) · `checkin-01-intro-spec-sheet.png`
**Blocked by:** **Profile 23 (Future)**, which sets the profile-creation-complete flag and navigates here. **Blocks:** every Check-In step ticket (02 Availability onwards), which adds its own ✕.

## Description

The first screen of the **48hrs Check-In**, and **the first screen after profile creation**. Two screens in one ticket:

- **A — Intro.** A bridge screen: the headline, a five-row numbered list of what the Check-In asks, and one CTA. It **can be closed** with a ✕.
- **B — Date search not available.** Where the ✕ lands. It is **the Search tab for a user who has no Check-In**: one statement and one CTA. Settings works as normal. **Search, Instant and the CTA all reopen the Check-In.**

Entered from:

- **Profile 23** (Continue or Skip): the end of profile creation → **A**.
- **B**, if the user has **never passed the intro** (tapped its CTA) → **A**. If they have passed it before → **the Today step**, intro skipped (see *Decided before build*).

Exits from A:

- **CTA `Check-in for a date`** → Check-In step 2 (Today).
- **✕** → **B**.
- There is **no back**. Profile 23 is never re-entered from here.

## Decided before build

Decided 12–13 Oct 2026. Every point matches the kit as drawn.

- **Profile creation done ≠ ready to date** (callouts ④ ⑤ ⑰ ⑱, violet). Reaching the Check-In for the first time means the profile exists. It does not mean the user can date now, because that needs a Check-In. So the user may close the Check-In and look round the app, **without date search**. Recorded as a standing rule in `design_handoff_showup/CLAUDE.md` → *Check-In gates date search*.
- **The ✕ is on every Check-In screen.** This ticket builds it on the intro (leading slot, callout ④). Each step ticket puts it in the AppHeader's trailing slot, with back staying leading. `CheckInCloseButton` is built here, once.
- **Nothing is kept as a draft** (13 Oct 2026). Closing mid-flow discards the availability and the Uplift text; Motivation, Vibe and Filters keep the last answer given. **There is no draft message anywhere.** The step tickets own what each step keeps.
- **B reopens at Today once the user has passed the intro** (tapped its CTA at least once), and at A before that. The flag is server-side and set by A's CTA; until it exists, B always routes to A.
- **B is a tab state, not a modal or an error.** The tabs are **not disabled** (no lock badge, no hint toast): tapping Search or Instant does something — it opens the Check-In.
- **Instant opens the 48hrs Check-In**, the same as Search. Instant's own 15-second flow is not changed by this ticket and is ticketed later.
- **Headline is 34**, the bridge group's size (Profiles 05, 13; was 36). One size per screen group — `CLAUDE.md`.
- **The list has five rows** — one per step, with Today and Tomorrow sharing row 1. The heads-up time step was removed from the flow (13 Oct 2026), and its row with it.
- **CTA arrow is an SVG icon** (`CheckInArrow`), as on Embrace — not a `→` text glyph.
- **Copy kept as drawn:** `Check-in` (lower-case i, with hyphen) in the headline, the CTA and B; `The rest is magic to happen.`
- **A Check-In window is opened when profile creation completes**, so `window_id` is never null here — see *Build inventory*.

## Layout — read this before building

⚠ **Layout is flex, not absolute.**

**A:** StatusBar (54) → **close row (52**, the AppHeader height, `flex: none`) → headline block (`flex: none`, padding `0 28`) → body (`flex: 1`, padding `28 28 0`: list → closing line mt 22 → **spacer `flex: 1`** → CTA mb 22) → HomeIndicator (28). The close row replaced the headline's 52 of top padding, so the headline did not move. At 375 × 667 the spacer drops to ≈ 127 and nothing else moves; **the screen never scrolls**.

**B:** StatusBar (54) → top row (52, Wordmark only) → **statement block (`flex: 1`, centred both ways)** → CTA block (`flex: none`, padding `0 24 22`) → TabBar → HomeIndicator (28).

⚠ **The only absolutely-positioned elements are A's three backdrop layers.**

📎 All specs are on the attached **checkin-01-intro-spec-sheet.png**: frames A and B, callouts ①–⑱. **Violet ④ ⑤ ⑰ ⑱** are the 12 Oct concept change. The design system has colour and type tokens only and no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/checkin/screen-checkin-intro-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. Screens: A `<CheckInIntro onStart onClose/>` · B `<ScreenCheckInNeeded onCheckIn/>`. Also builds `CheckInCloseButton`, `CheckInArrow` and `NumberedRow` — the step tickets consume the first; a second copy is a bug.
> Shell components: `design_handoff_showup/components/shared.jsx` (`Button`, `TabBar`, `Wordmark`, `Phone`) · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **Status bar and home indicator** are stylized mocks. Ship the platform chrome; anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.

## Copy — final strings

**A**
Headline: `Check-in to be dated soon.` — em on **dated soon.**
List: `Define your availability` · `Show your motivation` · `Define your desired dating vibe` · `Filter who you wish to date` · `Give reasons to match you`
Closing line: `The rest is magic to happen.` — em on **magic**
CTA: `Check-in for a date` + arrow icon
✕ accessible label: `Close Check-In`

**B**
Headline: `Check-in to start dating.` — em on **start dating.**
Body: `Date search and Instant open once you've told us when you're free to date.`
CTA: `Check-in for a date` + arrow icon

The em wash is the shared `.su-underlined em` rule. One em per headline.

## Behaviour

**A — Intro**
- Shown on arrival from Profile 23, and from B until the user has passed it once (tapped the CTA). **Nothing animates in**; the backdrop is static.
- **CTA** → step 2 (Today). Never disabled. Single-fire: a second tap during the push does nothing.
- **✕** → B, replacing the Check-In stack (B is the root of the Search tab, not pushed over the intro). No confirmation.
- **No back.** Suppress iOS swipe-back. **Android back = ✕.**
- Reopening A from B shows it fresh, every time.

**B — Date search not available**
- **Is the Search tab** whenever the user has **no completed Check-In**: after the ✕, and on every app open until a Check-In completes. App launch lands here, not on the intro.
- **CTA, Search tab, Instant tab** → reopen the Check-In: **intro never passed → A**; **passed before → Today**, which starts empty. Search is the active tab, and tapping it again still reopens.
- **Settings** navigates normally. Everything reachable from Settings (profile, Show-up Rate) works.
- No profiles, no filter button, no likes chip, no Premium upsell on this screen.
- A push or deep link that targets search or Instant lands on B.
- **Accessibility:** the tile icon is decorative; the headline is the screen title.

## Build inventory

- **Client:** `CheckInIntro`, `ScreenCheckInNeeded`, `CheckInCloseButton`, `CheckInArrow`, `NumberedRow`.
- **Search tab routing** reads "has a completed Check-In" and **"has passed the intro"** from the server on every foreground. Without the first, B cannot be shown and **date search must not ship**.
- **Server: open a Check-In window when the profile-creation-complete flag is set** (Profile 23). `window_id` is read by A for `check_in_started` and `check_in_closed`.
- **Intro-passed flag, server-side** — set by A's CTA, survives a reinstall. Until it exists, B always routes to A.

## Acceptance criteria

- [ ] **A has no AppHeader title, no back chevron and no StepProgress.** The only controls are the ✕ and the CTA
- [ ] The ✕ is `CheckInCloseButton`: 36 target, Icon `x` 22 / stroke 2, in a **52-high row** at the leading inset 16. The headline sits directly below that row with no extra top padding
- [ ] Headline Lora 700 / **34** / 1.1, two lines at 390, one em on `dated soon.`
- [ ] The list has **five rows in the order in *Copy***, each a 26 orange disc with a white numeral — an element, not a list-style
- [ ] CTA full-width sunset, label `Check-in for a date` with the **SVG arrow**, never disabled
- [ ] A **never scrolls** at 375 × 667, 390 × 844 or 430 × 932. Exactly one flexible element (the spacer), no absolute Y in the content
- [ ] Android back and the ✕ both go to B; iOS swipe-back does nothing. Profile 23 is never re-entered
- [ ] B is **the Search tab's root** for a user with no completed Check-In — after the ✕ and on every later app open
- [ ] On B the **CTA, the Search tab and the Instant tab** each reopen the Check-In: **A** until the user has passed the intro once, **Today** after that
- [ ] On B the tabs are **not disabled** — no lock badge, no hint toast — and Settings works
- [ ] B shows no profiles, filters, likes chip or upsell
- [ ] No message on A or B refers to earlier answers
- [ ] Copy matches the strings above exactly
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**
- [ ] **Evidence of done:** screenshots of **A and B** at **375 × 667, 390 × 844 and 430 × 932** (six images), plus a **recording** of ✕ → B → Instant tab → A. Each screenshot shows all content, nothing clipped, no scroll. Check 375 × 667 first.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **H — 48hr Check-In**, and `screen_viewed` in family A). Use them exactly. Do not invent names, and do not restate a payload the registry already defines. Registry **1.4.21**, taxonomy **1.4.12**.

⚠ **The family is now `check_in_*`** (decision 26, done with this ticket). `checkin_started` and every other `checkin_*` name are gone. `checkin_intro_viewed` was **removed**, not renamed — it duplicated `screen_viewed`.

| What | Event | Payload |
|---|---|---|
| A shown | `screen_viewed` | `screen_id: "checkin_intro"`, `screen_name: "CheckinIntro"`, `referrer_screen_id` |
| A · CTA | `check_in_started` | `window_id` — **respecified, taxonomy 1.4.10** |
| A · ✕ | `check_in_closed` | `step_id: "intro"` (§28), `window_id` — **NEW** |
| B shown | `screen_viewed` | `screen_id: "checkin_search_unavailable"`, `screen_name: "CheckinSearchUnavailable"`, `referrer_screen_id` — **NEW §11 row** |
| B · CTA / Search tab / Instant tab | `check_in_gate_tapped` | `target` (§27: `cta` · `search_tab` · `instant_tab`), `intro_passed` — **NEW** |

Rules that are easy to get wrong here:

- **`referrer_screen_id` on A** is `profile_life_ahead` (end of profile creation) or `checkin_search_unavailable` (reopened). On B it is `checkin_intro` after the ✕, and null on an app launch. Set by navigation, never hard-coded.
- **`check_in_started` fires on A's CTA only.** A reopen after the intro has been passed skips A, so it fires `check_in_gate_tapped` and the Today `screen_viewed` — not `check_in_started`.
- **Emit order:** the act's event, then navigation, then the next `screen_viewed`.
- **`intro_passed` on `check_in_gate_tapped`** is the server flag at tap time: true → Today, false → A. `check_in_closed` carries no such flag.
- Nothing here is attribute data: `sensitivity_class` 0 on every row.

**Events that must NOT fire here.**

- **`checkin_intro_viewed`** or any `checkin_*` name.
- **`profile_step_viewed` / `profile_step_completed`** — the intro is not a profile step; profile creation ended on Profile 23.
- **`check_in_step_answered`** — the intro asks nothing.
- **`search_viewed`** on B — there are no results, and B is not the discovery view.
- Anything for the Settings tab on B beyond its own `screen_viewed`.

**Screen-to-screen conversion.**

| Edge | Numerator | Denominator |
|---|---|---|
| Profile complete → intro | `screen_viewed` · `checkin_intro` · referrer `profile_life_ahead` | `screen_viewed` · `profile_life_ahead` |
| Intro → started | `check_in_started` | `screen_viewed` · `checkin_intro` |
| Intro → closed | `check_in_closed` · `step_id: "intro"` | `screen_viewed` · `checkin_intro` |
| Closed → reopened | `check_in_gate_tapped` | `screen_viewed` · `checkin_search_unavailable` |

**Registry changes made for this ticket (1.4.19 / taxonomy 1.4.10):** family H renamed `checkin_*` → `check_in_*` (8 events) · `checkin_intro_viewed` removed · `check_in_started` respecified · `check_in_closed` and `check_in_gate_tapped` NEW · §11 `checkin_intro` ticketed · §11 **`checkin_search_unavailable` NEW** · §27 `check_in_gate_target` NEW · §28 `check_in_step` NEW · decision 26 marked done. **1.4.20 / 1.4.11 (13 Oct 2026):** §28 loses `prep` and `prep_time_set` is removed, with the heads-up time step. **1.4.21 / 1.4.12 (13 Oct 2026, with Check-In 02):** the draft is removed — `check_in_closed` carries no draft flag, and `check_in_gate_tapped` carries `intro_passed`.

**Missing from the current build:** every event above is `implemented: false`. Until this ships, the Profile 23 → intro edge reads zero.

## Out of scope

Check-In steps (stories 02–06) and their ✕. Instant's own 15-second flow. The Search tab once a Check-In exists. What B shows when a previous Check-In has **expired** (see *Open*). Notifications that prompt a Check-In (`check_in_prompted`).

## Dependencies

- **Profile 23 (Future)** — the only first-time origin, and the profile-creation-complete flag.
- **Server Check-In window on profile completion** — see *Build inventory*.
- **Shared `TabBar`** with an `onTab` handler.
- **Tracking registry 1.4.21 / taxonomy 1.4.12.**

## Open (not blocking)

- **An expired Check-In.** When a 48-hour window ends with no new Check-In, does the user see B as drawn, or different copy ("Check in again…")? B's copy is written for a first-time user.
- **`check_in_completed` vs `check_in_created`.** Both describe a committed Check-In record; the second already ships. Settle which one survives in the completion ticket.
- **`quick_checkin_completed`** (Instant) keeps the old prefix until Instant is ticketed.
- **`Check-in` vs the product term `Check-In`.** Kept as drawn by decision. The terminology rule in `CLAUDE.md` names `Check-In`; if the two are ever unified, it is a copy change across A, B and the step header.
