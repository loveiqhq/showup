# [Profile 18] Education

**Parent:** Profile creation flow (SHOWUP-TBD — epic `profile/tickets/00-epic.md`) · **Type:** Story · **Priority:** Medium
**Attachments:** `profile-18-education-attachment.zip` (handoff folder for this ticket) · `profile-18-education-spec-sheet.png`
**Blocked by:** Profile 14 (Height), which builds `DetailsScaffold` and the footer, and Profile 15 (Gender), which builds `OptionRow`. This screen renders through both.

## Description

Step 5 of **"Share some details"**. One question, four options, **single-select**: the user's highest level of education. The user can hide it from their profile, and **can skip the step**.

**This screen builds nothing.** It **consumes** `DetailsScaffold` and the footer from Profile 14 and `OptionRow kind="radio"` from Profile 15. A second copy of any of them is a bug.

Two states in one column: **A** empty (arrival), **B** `University degree` selected. Nothing moves between them. **There is no refused state.**

Entered from **dating language's Continue or Skip**, or by **back from religion**. Exits:

- **Continue** with an option selected → religion (step 6). Saves the selection and visibility.
- **Continue** with nothing selected → religion. **Behaves exactly like Skip.**
- **Skip for now** → religion. Saves nothing.
- **Back chevron** → pops to dating language.

## Decided before build

All decided 5 Oct 2026. Every point below matches the kit as drawn.

- **Education is skippable.** The footer carries `Skip for now` (callout ⑪). Under profile README rule 0, Skip advances the flow position, saves nothing for this step and **never deletes a value saved earlier**.
- **Continue with nothing selected is a Skip** (callout ⑫). It advances, saves nothing and fires the skip events. There is no toast, no shake and no disabled state. Same rule as dating language (Profile 17).
- **Tapping the selected row again clears it** (callout ⑰, violet). This is a **named exception** to the gender rule "a radio never clears" (Profile 15). It applies here because the step is skippable, so empty is a valid answer. The clear is **screen logic** in the row's tap handler. It is **not** a new `OptionRow` prop, and gender and orientation keep their behaviour.
- **Sub copy is `Select one.`** (callout ⑤). It replaces `Used to find the right matches.`, which the kit carried until 5 Oct 2026.
- **Four options, fixed order**, never reordered by device locale. `A-Levels / Abitur` stays as one option.
- **Visibility is unchecked by default** (callout ⑩). Education shows on the profile unless the user ticks the box.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** It is the `DetailsScaffold` column from Profile 14, unchanged: StatusBar (54) → AppHeader (52) → content (`flex: 1`, padding `4 24 0`, overflow hidden) → HomeIndicator (28).

**Answer region:** `flex: 1`, `min-height: 0`, `overflow-y: auto` (already in the scaffold). Inside it, a radiogroup of four `OptionRow`s (≈ 55 each), stacked with no gap (the divider separates them) → **one `flex: 1` spacer**. Nothing scrolls at 390 × 844 or 430 × 932. At 375 × 667 the four rows are **≈ 6 px short**: the spacer resolves to 0 and the region scrolls by that much (callout ⑨). The `PhD` label stays fully visible; only part of its bottom padding is below the fold. Header, progress bar, headline, sub, band and footer never move.

⚠ **The only absolutely-positioned element is the Atmosphere backdrop.** This screen has no toast.

**No keyboard on this screen.**

📎 All specs are on the attached **profile-18-education-spec-sheet.png**: frames A and B, callouts ①–⑱. Callout ⑰ is **violet**: it marks the exception to the gender rule. The design system has colour and type tokens only and no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/profile/screen-education-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. States: A `<ScreenProfileEducation/>` · B `<ScreenProfileEducation initialValue="University degree"/>`. `DetailsScaffold` and the footer appear in this file only so it renders. **Build those from Profile 14**, and **`OptionRow` from Profile 15**, not from this file.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **Status bar and home indicator** are stylized mocks in the reference file. Ship the platform chrome and anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.
- **The scroll indicator** on 375 × 667 is the platform's. We do not draw a fade, a chevron or a "more" hint.

## Copy — final strings

Header: `Share some details`
Headline: `What's your highest level of education?` — em on **education**, orange wash from the shared `.su-underlined em` rule
Sub: `Select one.`
Options, in this order: `A-Levels / Abitur` · `Apprenticeship` · `University degree` · `PhD`
Visibility: `Don't display on my profile`
Skip: `Skip for now`
CTA: `Continue`

The headline uses a typographic apostrophe if the platform font renders one; the string is otherwise exact. The option labels are display strings. What gets stored and tracked is the `enums.json` §1 value (`a_levels_abitur` · `apprenticeship` · `university_degree` · `phd`), never the label.

## Behaviour

- **On arrival nothing is selected**, unless this is a resume or a back navigation. No keyboard opens and no row is focused.
- **Tapping an unselected row selects it** and deselects any other row. The change is immediate, with the 180 ms background and indicator transition. Nothing else on the screen changes.
- **Tapping the selected row clears it.** The screen returns to state A with the same 180 ms transition. Nothing else changes, and nothing is saved or tracked.
- **The whole row is the hit area**: the label, the indicator and the padding between them.
- **No auto-advance.** Selecting a row never navigates. Only Continue and Skip do.
- **Continue with a selection:** persist `education` (the §1 value) and the visibility flag. Then advance the saved flow position, then navigate to religion. Persist on Continue, before navigating. Never persist per tap (flow README rule 4a).
- **Continue with nothing selected:** identical to Skip — see below. No toast, no error, nothing refused.
- **Skip for now:** advance the saved flow position and navigate to religion. Save nothing for this step. **A value saved earlier is kept** (README rule 0). No confirmation. The visibility flag is not saved on a skip.
- **375 × 667:** only the answer region scrolls, by ≈ 6 px. The band and the footer stay visible and tappable at every scroll position. Do not flash the scroll indicator; nothing meaningful is hidden.
- **Visibility band:** unchecked by default. When checked, education is **not shown on the profile**; the value is still saved. It is saved with an accepted Continue. Only the box and the label are tappable.
- **Back** (chevron, iOS swipe-back, Android back) pops to dating language and discards an unsaved selection. Dating language shows its saved value.
- **Resume, and back from religion,** pre-fill the saved selection (state B) and the saved visibility, without animation. Nothing is announced.
- **Clearing the selection on a resume and pressing Continue is a Skip**, so the earlier saved value is kept. See *Open*.
- **Accessibility:** the list is a `radiogroup` **labelled by the headline** (`aria-labelledby`). Each row is a `radio` with `aria-checked`. A screen reader reads position (e.g. "PhD, radio button, not checked, 4 of 4"). Activating the checked radio clears it; the change is announced as "not checked".
- **CTA press feedback** comes from the shared NextButton. The label and the circle form one hit area. `SkipLink` is the shared one from `shared.jsx`.

## Build inventory

- **Nothing new to build on the client.** `OptionRow` (Profile 15), `DetailsScaffold` and the footer (Profile 14) are consumed.
- **Profile entity: education as one enum value, plus a hidden flag.** `enums.json` §1 marks it backend *Not built*. Store the §1 value (not the label). The search profile's `education` tile reads from it. **Until both exist, Continue is blocked and the step does not ship.**
- **No user property.** `properties.json` has no education user property, and this ticket does not ask for one.

## Acceptance criteria

- [ ] The screen renders through **`DetailsScaffold` from Profile 14**, not a copy, and the step count is the shell's prop
- [ ] AppHeader title `Share some details`, **back chevron** in the leading slot, trailing slot empty. StepProgress `steps={10} current={5}`
- [ ] Headline Lora 700 / 30 / 1.12, **two lines at 390**, one em on `education`, size **from the scaffold**
- [ ] Sub copy is `Select one.`, not `Used to find the right matches.`
- [ ] Options appear in the order `A-Levels / Abitur · Apprenticeship · University degree · PhD` on every device and locale
- [ ] Rows are **`OptionRow kind="radio"` from Profile 15**, not a copy: pad 16 / 4, radius 10, label Manrope 500 16, 22 round indicator, divider under every row except the last. **Rows are the same height on every device**
- [ ] Selected row: wash `rgba(129,42,236,.06)`, label weight 700, filled violet circle with an 8 px white dot. **No element changes Y**
- [ ] Selecting a row deselects any other. **Tapping the selected row clears it**, and the screen shows state A
- [ ] The clear is implemented in this screen's tap handler. **`OptionRow` gains no new prop**, and gender and orientation still never clear
- [ ] Selecting a row **does not navigate**. Only Continue and Skip do
- [ ] The footer is `space-between`: `Skip for now` on the left, `Continue` on the right. **Continue is never disabled**
- [ ] **Continue with nothing selected behaves exactly like Skip**: no toast, no shake, no error, advances to religion, saves nothing
- [ ] Skip advances, saves nothing and **keeps a value saved earlier**, with no confirmation
- [ ] Continue with a selection persists the **§1 value** and the visibility flag, then the flow position, then navigates to religion
- [ ] At 390 × 844 and 430 × 932, all four rows are visible and **nothing scrolls**
- [ ] At 375 × 667, **only the answer region scrolls**. Header, progress bar, headline, sub, band and footer are fixed and fully visible. The `PhD` label is fully visible without scrolling
- [ ] Visibility is unchecked by default. When checked, education is hidden on the profile
- [ ] Back pops to dating language and discards an unsaved selection
- [ ] A resume, or a back from religion, pre-fills the saved selection and visibility
- [ ] The radiogroup is labelled by the headline, and each row announces its checked state and position
- [ ] Exactly one `flex: 1` spacer, and no absolute Y positioning in the content column
- [ ] Copy matches the strings above exactly
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**. Nothing fires per tap, including the clear. An empty Continue fires the skip events, never `form_validation_failed`
- [ ] **Evidence of done:** screenshots of **both states** at **375 × 667, 390 × 844 and 430 × 932**, six images. Each shows all chrome visible, nothing clipped, the visibility band and the footer fully visible. At 390 × 844 and 430 × 932, all four rows are visible with no scroll. On 375 × 667, state how far the region scrolls. Check 375 × 667 first.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (families **D — Profile Identity**, **F — Profile Attributes**, and `screen_viewed` in family A). Use them exactly. Do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_education"`, `screen_name: "ProfileEducation"`, `referrer_screen_id` |
| Step shown | `profile_step_viewed` | `step_id: "education"`, `step_index: 5` (§2) |
| Continue with a selection | `detail_answered` | `field_id: "education"`, `value_bucketed` — one §1 value (`enums.json` §1) |
| Continue with a selection | `profile_step_completed` | `step_id: "education"`, `time_on_step_s` |
| Skip, **or Continue with nothing selected** | `detail_skipped` | `field_id: "education"` |
| Skip, **or Continue with nothing selected** | `profile_step_skipped` | `step_id: "education"`, `screen_id: "profile_education"` |
| Visibility box ticked | `field_display_opted_out` | `field_id: "education"` — **on tick only**, no event on untick |

Rules that are easy to get wrong here:

- **One `detail_answered` per accepted Continue**, carrying the selected value. Selecting, changing and clearing send nothing.
- **Education is Class 1** (§1). The value is sent as is, but only as a §1 value, never the label. `sensitivity_class` and `field_registry_version` are stamped at emit time. See the emitter rules in `design_handoff_showup/CLAUDE.md`.
- **An empty Continue and a Skip are recorded identically.** This includes a user who selected and then cleared. Nothing distinguishes them in the payload, by decision. Do not add a property to tell them apart.
- **`referrer_screen_id` is the screen the user actually came from**: `profile_dating_language` going forward, `profile_religion` on a back from religion. It is set by the navigation and never hard-coded.
- **On an accepted Continue, `detail_answered` and `profile_step_completed` fire before navigation**, in that order. On a skip, `detail_skipped` and `profile_step_skipped` fire before navigation, in that order.

**Events that must NOT fire here.**

- **Anything per tap**, including the clear. No `option_selected`, no `option_cleared`, no `education_deselected`.
- **`form_validation_failed`.** There is no refused state on this screen.
- **`detail_answered` with an empty `value_bucketed`.** An empty answer is a skip.
- **Anything for a scroll.**
- **`profile_build_started`**, which fires once, on screen 01.

**Screen-to-screen conversion.** Each screen's `screen_viewed` carries the previous screen's id in `referrer_screen_id`, so every edge is a ratio of two counts.

| Edge | Numerator | Denominator |
|---|---|---|
| Dating language → education | `screen_viewed` · `screen_id: "profile_education"` · `referrer_screen_id: "profile_dating_language"` | `screen_viewed` · `screen_id: "profile_dating_language"` |
| Education → religion | `screen_viewed` · `screen_id: "profile_religion"` · `referrer_screen_id: "profile_education"` | `screen_viewed` · `screen_id: "profile_education"` |

The skip rate for this step is `profile_step_skipped` · `step_id: "education"` over `profile_step_viewed` · `step_id: "education"`.

The `profile_education` §11 row was registered 5 Oct 2026 (registry 1.4.8). This ticket marks it as ticketed (registry 1.4.13). No value in any set changed.

**Missing from the current build:** every event above is `implemented: false`. `detail_answered` for education is backend *Not built*.

## Out of scope

Religion and the later steps. Also: school or university names, field of study, graduation year, a free-text or "Other" option, a "Prefer not to say" option, splitting `University degree` into Bachelor's / Master's, reordering or relabelling by market or locale, editing education from Settings after onboarding, how the education tile renders on the public profile, and any matching or search filter on education.

## Dependencies

- **Profile 14 (Height)** builds `DetailsScaffold` and the footer.
- **Profile 15 (Gender)** builds `OptionRow`.
- **Profile 17 (Dating language)**, the only forward origin.
- **Religion** (step 6), the only destination.
- **Profile entity education value + hidden flag** — see *Build inventory*.
- The saved flow position and resume (flow README rule 4a).
- ProfileVisibility, SkipLink, NextButton and Atmosphere `form` from `shared.jsx`.

## Open (not blocking)

- **A saved answer cannot be removed in onboarding.** Clearing the selection on a resume and pressing Continue is a Skip, and rule 0 keeps the earlier value. The screen then shows nothing selected while the profile still holds the old value. Default: keep it this way for the MVP; removing belongs to profile editing. Same open point as Profile 17.
- **The 375 × 667 deficit (≈ 6 px) is measured from the kit**, not a device. The scroll handles any value; the evidence screenshot shows the real one.
- **The option set is German-market** (`A-Levels / Abitur`). Kept as drawn for the MVP; a market-specific set is a later decision.
- **The progress-bar count** (10 segments against 9 §2 indices) is still open from Profile 14. It belongs to the group shell; this screen is `current={5}` either way.
