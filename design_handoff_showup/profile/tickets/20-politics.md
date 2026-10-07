# [Profile 20] Politics

**Parent:** Profile creation flow (SHOWUP-TBD — epic `profile/tickets/00-epic.md`) · **Type:** Story · **Priority:** Medium
**Attachments:** `profile-20-politics-attachment.zip` (handoff folder for this ticket) · `profile-20-politics-spec-sheet.png`
**Blocked by:** Profile 14 (Height), which builds `DetailsScaffold` and the footer, and Profile 15 (Gender), which builds `OptionRow`. This screen renders through both.

## Description

Step 7 of **"Share some details"**. One question, eight options, **single-select**: the user's political leaning. The user can hide it from their profile, and **can skip the step**. Political opinion is **special-category data** (GDPR Art. 9, tracking Class 2).

**This screen builds nothing.** It **consumes** `DetailsScaffold` and the footer from Profile 14 and `OptionRow kind="radio"` from Profile 15. A second copy of any of them is a bug.

Two states in one column: **A** empty (arrival), **B** `Middle` selected. The sheet adds frame **C**: state A with the list scrolled to the end. Only the answer region moves between frames. **There is no refused state.**

**Handled exactly like Religion (Profile 19).** The differences are the option set and its order, the step index, and that the list fits at 430 × 932.

Entered from **religion's Continue or Skip**, or by **back from interests**. Exits:

- **Continue** with an option selected → interests (step 8). Saves the selection and visibility.
- **Continue** with nothing selected → interests. **Behaves exactly like Skip.**
- **Skip for now** → interests. Saves nothing.
- **Back chevron** → pops to religion.

## Decided before build

All decided 5 Oct 2026. Every point below matches the kit as drawn.

- **The order is linear** (callout ⑧): `Left · Mid-left · Middle · Mid-right · Right · Conservative · Libertarian · Apolitical`. The kit had `Conservative` between `Mid-right` and `Right`. It now follows the left → right spectrum, with the three off-axis answers at the end. `enums.json` §1 is reordered to match (registry 1.4.15). No value was renamed. Nothing had shipped, so nothing migrates.
- **Rows are the standard `OptionRow` (16 / 4).** The kit drew politics with `OptionRowCompact` (12 / 4). That component was a second copy of `OptionRow` and has been **deleted from the kit**. Rows are never shortened to fit; the list scrolls instead (callouts ⑨ and ⑲, violet). Same decision as Profile 19.
- **The list scrolls at 390 × 844 and 375 × 667, and fits at 430 × 932.** See *Layout*.
- **Politics is skippable.** The footer carries `Skip for now` (callout ⑪). Under profile README rule 0, Skip advances the flow position, saves nothing for this step and **never deletes a value saved earlier**.
- **Continue with nothing selected is a Skip** (callout ⑫). No toast, no shake, no disabled state.
- **Tapping the selected row again clears it** (callout ⑰). The education rule (Profiles 18, 19), applied unchanged. **Screen logic** in the row's tap handler, **not** a new `OptionRow` prop.
- **Sub copy is `Select one.`** (callout ⑤). It replaces `Used to find the right matches.`
- **Visibility is unchecked by default** (callout ⑩). When checked, politics is **not shown on the profile but is still used for matching**.
- **Tracking sends the §1 value**, the same as religion. This settles the registry's open note on whether to log politics at all. See *Tracking*.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** It is the `DetailsScaffold` column from Profile 14, unchanged: StatusBar (54) → AppHeader (52) → content (`flex: 1`, padding `4 24 0`, overflow hidden) → HomeIndicator (28).

**Answer region:** `flex: 1`, `min-height: 0`, `overflow-y: auto` (already in the scaffold). Inside it, a radiogroup of eight `OptionRow`s (≈ 55 each including the divider, ≈ 439 in total), stacked with no gap → **one `flex: 1` spacer**.

| Device | Region shows | List | Rows fully visible |
|---|---|---|---|
| 375 × 667 | ≈ 213 | ≈ 226 short — scrolls | 3 |
| 390 × 844 | ≈ 390 | ≈ 49 short — scrolls | 7 |
| 430 × 932 | ≈ 478 | **fits**, ≈ 39 spare — no scroll | 8 |

The header, progress bar, headline, sub, visibility band and footer **never move** at any scroll position. Never shrink a row, the band or the CTA to make the list fit.

⚠ **The only absolutely-positioned element is the Atmosphere backdrop.** This screen has no toast.

**No keyboard on this screen.**

📎 All specs are on the attached **profile-20-politics-spec-sheet.png**: frames A, B and C, callouts ①–⑲. Callouts ⑨ and ⑲ are **violet**: the scrolling answer region. The design system has colour and type tokens only and no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/profile/screen-politics-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. States: A `<ScreenProfilePolitics/>` · B `<ScreenProfilePolitics initialValue="Middle"/>` · frame C is state A scrolled to the end, not a prop. `DetailsScaffold` and the footer appear in this file only so it renders. **Build those from Profile 14**, and **`OptionRow` from Profile 15**, not from this file.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **Status bar and home indicator** are stylized mocks in the reference file. Ship the platform chrome and anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.
- **The scroll indicator** is the platform's. Where the list overflows, we flash it once on arrival. We do not draw a fade, a chevron or a "more" hint.

## Copy — final strings

Header: `Share some details`
Headline: `What are your political beliefs?` — em on **political**, orange wash from the shared `.su-underlined em` rule
Sub: `Select one.`
Options, in this order: `Left` · `Mid-left` · `Middle` · `Mid-right` · `Right` · `Conservative` · `Libertarian` · `Apolitical`
Visibility: `Don't display on my profile`
Skip: `Skip for now`
CTA: `Continue`

The option labels are display strings. What gets stored and tracked is the `enums.json` §1 value (`left` · `mid_left` · `middle` · `mid_right` · `right` · `conservative` · `libertarian` · `apolitical`), never the label.

## Behaviour

- **On arrival nothing is selected**, unless this is a resume or a back navigation. No keyboard opens and no row is focused. The list is scrolled to the top.
- **Where the list overflows, flash the platform scroll indicator once on arrival**, so the rows below the fold are discoverable. At 430 × 932 nothing overflows and nothing flashes.
- **Tapping an unselected row selects it** and deselects any other row. The change is immediate, with the 180 ms background and indicator transition. Nothing else on the screen changes, and the list does not scroll.
- **Tapping the selected row clears it.** The screen returns to state A with the same 180 ms transition. Nothing is saved or tracked.
- **The whole row is the hit area**: the label, the indicator and the padding between them. A scroll gesture that starts on a row does not select it.
- **No auto-advance.** Selecting a row never navigates. Only Continue and Skip do.
- **Continue with a selection:** persist `politics` (the §1 value) and the visibility flag. Then advance the saved flow position, then navigate to interests. Persist on Continue, before navigating. Never persist per tap (flow README rule 4a).
- **Continue with nothing selected:** identical to Skip — see below. No toast, no error, nothing refused.
- **Skip for now:** advance the saved flow position and navigate to interests. Save nothing for this step. **A value saved earlier is kept** (README rule 0). No confirmation. The visibility flag is not saved on a skip.
- **Scrolling:** only the answer region scrolls. The band and the footer stay visible and tappable at every scroll position.
- **Visibility band:** unchecked by default. When checked, politics is **not shown on the profile but is still used for matching**. It is saved with an accepted Continue. Only the box and the label are tappable.
- **Back** (chevron, iOS swipe-back, Android back) pops to religion and discards an unsaved selection. Religion shows its saved value.
- **Resume, and back from interests,** pre-fill the saved selection (state B) and the saved visibility, without animation. If the saved row is below the fold, the list opens **scrolled so that row is fully visible**. Nothing is announced.
- **Clearing the selection on a resume and pressing Continue is a Skip**, so the earlier saved value is kept. See *Open*.
- **Accessibility:** the list is a `radiogroup` **labelled by the headline** (`aria-labelledby`). Each row is a `radio` with `aria-checked`. A screen reader reads position (e.g. "Middle, radio button, not checked, 3 of 8"). Moving focus to a row below the fold scrolls it into the region. Activating the checked radio clears it; the change is announced as "not checked".
- **CTA press feedback** comes from the shared NextButton. The label and the circle form one hit area. `SkipLink` is the shared one from `shared.jsx`.

## Build inventory

- **Nothing new to build on the client.** `OptionRow` (Profile 15), `DetailsScaffold` and the footer (Profile 14) are consumed. There is no compact row variant to build — it was deleted from the kit.
- **Profile entity: politics as one enum value, plus a hidden flag.** `enums.json` §1 marks it backend *Not built*. Store the §1 value (not the label). It is special-category data: storage and access follow the Class 2 rules. The search profile's `politics` tile reads from it. **Until both exist, Continue is blocked and the step does not ship.**
- **No user property.** `properties.json` has no politics user property, and this ticket does not ask for one.

## Acceptance criteria

- [ ] The screen renders through **`DetailsScaffold` from Profile 14**, not a copy, and the step count is the shell's prop
- [ ] AppHeader title `Share some details`, **back chevron** in the leading slot, trailing slot empty. StepProgress `steps={10} current={7}`
- [ ] Headline Lora 700 / 30 / 1.12, **two lines at 390**, one em on `political`, size **from the scaffold**
- [ ] Sub copy is `Select one.`, not `Used to find the right matches.`
- [ ] **Eight options in linear order**: `Left · Mid-left · Middle · Mid-right · Right · Conservative · Libertarian · Apolitical`, on every device and locale. `Conservative` is **after** `Right`
- [ ] Rows are **`OptionRow kind="radio"` from Profile 15**, not a copy and not a compact variant: pad 16 / 4, radius 10, label Manrope 500 16, 22 round indicator, divider under every row except the last. **Rows are the same height on every device**
- [ ] Selected row: wash `rgba(129,42,236,.06)`, label weight 700, filled violet circle with an 8 px white dot. **No element changes Y**
- [ ] Selecting a row deselects any other. **Tapping the selected row clears it**, and the screen shows state A
- [ ] The clear is implemented in this screen's tap handler. **`OptionRow` gains no new prop**, and gender and orientation still never clear
- [ ] Selecting a row **does not navigate**. Only Continue and Skip do
- [ ] The footer is `space-between`: `Skip for now` on the left, `Continue` on the right. **Continue is never disabled**
- [ ] **Continue with nothing selected behaves exactly like Skip**: no toast, no shake, no error, advances to interests, saves nothing
- [ ] Skip advances, saves nothing and **keeps a value saved earlier**, with no confirmation
- [ ] Continue with a selection persists the **§1 value** and the visibility flag, then the flow position, then navigates to interests
- [ ] At 375 × 667 and 390 × 844, **only the answer region scrolls**. Header, progress bar, headline, sub, band and footer are fixed and fully visible at every scroll position. The scroll indicator flashes once on arrival
- [ ] At 430 × 932, all eight rows are visible and **nothing scrolls**
- [ ] No fade, chevron or "more" hint is drawn
- [ ] Visibility is unchecked by default. When checked, politics is hidden on the profile and kept in matching
- [ ] Back pops to religion and discards an unsaved selection
- [ ] A resume, or a back from interests, pre-fills the saved selection and visibility. A saved row below the fold is scrolled fully into view
- [ ] The radiogroup is labelled by the headline, and each row announces its checked state and position out of 8
- [ ] Exactly one `flex: 1` spacer, and no absolute Y positioning in the content column
- [ ] Copy matches the strings above exactly
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**. Nothing fires per tap, including the clear, or per scroll. An empty Continue fires the skip events, never `form_validation_failed`. `value_bucketed` is only ever a §1 value
- [ ] **Evidence of done:** screenshots of **both states** at **375 × 667, 390 × 844 and 430 × 932**, six images, plus **one at 375 × 667 scrolled to the bottom**, seven in total. Each shows all chrome visible, nothing clipped, the visibility band and the footer fully visible. The scrolled one shows `Apolitical` fully visible with no divider under it. At 430 × 932, all eight rows are visible with no scroll. Check 375 × 667 first.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (families **D — Profile Identity**, **F — Profile Attributes**, and `screen_viewed` in family A). Use them exactly. Do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_politics"`, `screen_name: "ProfilePolitics"`, `referrer_screen_id` |
| Step shown | `profile_step_viewed` | `step_id: "politics"`, `step_index: 7` (§2) |
| Continue with a selection | `detail_answered` | `field_id: "politics"`, `value_bucketed` — one §1 value (`enums.json` §1) |
| Continue with a selection | `profile_step_completed` | `step_id: "politics"`, `time_on_step_s` |
| Skip, **or Continue with nothing selected** | `detail_skipped` | `field_id: "politics"` |
| Skip, **or Continue with nothing selected** | `profile_step_skipped` | `step_id: "politics"`, `screen_id: "profile_politics"` |
| Visibility box ticked | `field_display_opted_out` | `field_id: "politics"` — **on tick only**, no event on untick |

Rules that are easy to get wrong here:

- **Politics is Class 2** (special category, `requirements.json` → Class 2). Its §1 set is closed, so no further bucketing is needed: the value is sent as is, but **only as a §1 value, never the label**. `sensitivity_class` and `field_registry_version` (now **1.4.15**) are stamped at emit time. See the emitter rules in `design_handoff_showup/CLAUDE.md`.
- **Logging the value is decided** (5 Oct 2026). The registry's note asking whether to log politics at all is closed. Do not drop the value, and do not add a coarser bucket.
- **The §1 order changed in 1.4.15** (`conservative` now after `right`). No value was renamed, so an emitter keyed on values is unaffected. Anything that reads the list **by index** is not — key on the value.
- **One `detail_answered` per accepted Continue**, carrying the selected value. Selecting, changing and clearing send nothing.
- **An empty Continue and a Skip are recorded identically.** This includes a user who selected and then cleared. Do not add a property to tell them apart.
- **`referrer_screen_id` is the screen the user actually came from**: `profile_religion` going forward, `profile_interests` on a back from interests. It is set by the navigation and never hard-coded.
- **On an accepted Continue, `detail_answered` and `profile_step_completed` fire before navigation**, in that order. On a skip, `detail_skipped` and `profile_step_skipped` fire before navigation, in that order.

**Events that must NOT fire here.**

- **Anything per tap**, including the clear. No `option_selected`, no `option_cleared`.
- **Anything for a scroll or the scroll-indicator flash.** No `list_scrolled`.
- **`form_validation_failed`.** There is no refused state on this screen.
- **`detail_answered` with an empty `value_bucketed`**, or with a display label. An empty answer is a skip.
- **`profile_build_started`**, which fires once, on screen 01.

**Screen-to-screen conversion.** Each screen's `screen_viewed` carries the previous screen's id in `referrer_screen_id`, so every edge is a ratio of two counts.

| Edge | Numerator | Denominator |
|---|---|---|
| Religion → politics | `screen_viewed` · `screen_id: "profile_politics"` · `referrer_screen_id: "profile_religion"` | `screen_viewed` · `screen_id: "profile_religion"` |
| Politics → interests | `screen_viewed` · `screen_id: "profile_interests"` · `referrer_screen_id: "profile_politics"` | `screen_viewed` · `screen_id: "profile_politics"` |

The skip rate for this step is `profile_step_skipped` · `step_id: "politics"` over `profile_step_viewed` · `step_id: "politics"`.

The `profile_politics` §11 row was registered 5 Oct 2026 (registry 1.4.8). This ticket marks it as ticketed and reorders §1 politics (registry 1.4.15).

**Missing from the current build:** every event above is `implemented: false`. `detail_answered` for politics is backend *Not built*.

## Out of scope

Interests and the later steps. Also: party names, a political-engagement or "how important is it" scale, a free-text option, a "Prefer not to say" option (Skip covers it), relabelling the spectrum by market or locale, editing politics from Settings after onboarding, how the politics tile renders on the public profile, any matching or search filter on politics, and the Art. 9 consent mechanism (see *Open*).

## Dependencies

- **Profile 14 (Height)** builds `DetailsScaffold` and the footer.
- **Profile 15 (Gender)** builds `OptionRow`.
- **Profile 19 (Religion)**, the only forward origin.
- **Interests** (step 8), the only destination.
- **Profile entity politics value + hidden flag**, with Class 2 storage — see *Build inventory*.
- **Tracking registry 1.4.15** (`enums.json` §1 politics order).
- The saved flow position and resume (flow README rule 4a).
- ProfileVisibility, SkipLink, NextButton and Atmosphere `form` from `shared.jsx`.

## Open (not blocking)

- **GDPR Art. 9 explicit consent for political-opinion data — owned by legal.** Same open point as Profile 19, and it should be settled once for both. If a consent line is needed, it is a kit change first, then a re-exported sheet, then an update to this ticket and Profile 19.
- **`Conservative` after `Right` is a reading of the scale, not a fact.** It was chosen so the left → right options run in one direction. If research shows users read `Conservative` as a point on the spectrum, it moves — a §1 reorder, no rename.
- **At 375 × 667 only 3 of 8 rows are fully visible.** Accepted by decision (rows are never shortened). The scroll-indicator flash is the only hint. Same open point as Profile 19.
- **A saved answer cannot be removed in onboarding.** Clearing on a resume and pressing Continue is a Skip, and rule 0 keeps the earlier value. Default: keep it for the MVP; removing belongs to profile editing. Same open point as Profiles 17–19.
- **The deficits are measured from the kit**, not a device. The scroll handles any value; the evidence screenshots show the real ones.
- **The progress-bar count** (10 segments against 9 §2 indices) is still open from Profile 14. It belongs to the group shell; this screen is `current={7}` either way.
