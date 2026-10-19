# [Profile 16] Orientation

**Parent:** Profile creation flow (SHOWUP-TBD — epic `profile/tickets/00-epic.md`) · **Type:** Story · **Priority:** Medium
**Attachments:** `profile-16-orientation-attachment.zip` (handoff folder for this ticket) · `profile-16-orientation-spec-sheet.png`
**Blocked by:** Profile 15 (Gender), which builds `OptionRow`, and Profile 14 (Height), which builds `DetailsScaffold`. This screen renders through both.

## Description

Step 3 of **"Share some details"**. One question, six options, single-select: the user's sexual orientation. The answer is used for matching. The user can hide it from their profile, but **cannot skip the step**.

**This screen builds nothing.** It is the gender screen with six options: it **consumes** `OptionRow` from Profile 15 and `DetailsScaffold`, `ValidationToast` and the footer from Profile 14. A second copy of any of them is a bug.

Three states in one column: **A** empty (arrival), **B** an option selected, **C** Continue refused. Nothing moves between them.

Entered from **gender's Continue**, or by **back from dating language**. Exits:

- **Continue** with an option selected → dating language (step 4). Saves orientation and visibility.
- **Back chevron** → pops to gender.

## Decided before build

All decided 5 Oct 2026. Every point below matches the kit as drawn.

- **Orientation is mandatory.** There is no `Skip for now`. Under profile README rule 0, a screen without a SkipLink is mandatory. The footer row is `flex-end` and its left side stays **empty** (callout ⑪). There is no `Prefer not to say` option.
- **Visibility is unchecked by default**, the same as gender (callout ⑩). Orientation shows on the profile unless the user ticks the box.
- **All six options, always, in a fixed order.** The list is **never filtered or reordered by the gender answer**. A man can pick `Lesbian`; nothing validates the combination.
- **Short screens scroll the list, and the rows keep their size.** Six rows (≈ 324) do not fit at 375 × 667: the column is ≈ 45 short. There, the **answer region scrolls internally** (callout ⑨). The spacer resolves to 0, and the header, progress bar, headline, sub, band and CTA never move. Rows keep padding 16 / 4 on every device. Do not shorten them to make the list fit.
- **A radio never clears**, `Other` is a plain option, and there is **no auto-advance**. These are the same rules as gender.
- **Refusal is the group's toast** (callout ⑯). **Continue is never disabled.**

## Layout — read this before building

⚠ **Layout is flex, not absolute.** It is the `DetailsScaffold` column from Profile 14, unchanged: StatusBar (54) → AppHeader (52) → content (`flex: 1`, padding `4 24 0`, overflow hidden) → HomeIndicator (28).

**Answer region:** `flex: 1`, `min-height: 0`, `overflow-y: auto` (already in the scaffold). Inside it, a radiogroup of six `OptionRow`s, stacked with no gap (the divider separates them) → **one `flex: 1` spacer**. At 390 × 844 the spacer resolves to ≈ 68. At 375 × 667 it resolves to 0 and the region scrolls. Header, progress bar, band and footer never move.

⚠ **The only absolutely-positioned elements are the Atmosphere backdrop and the toast.** The toast is an overlay against the footer row and moves nothing.

**No keyboard on this screen.**

📎 All specs are on the attached **profile-16-orientation-spec-sheet.png**: frames A, B and C, callouts ①–⑰, with danger red for state C. The design system has colour and type tokens only and no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/profile/screen-orientation-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. States: A `<ScreenProfileOrientation/>` · B `<ScreenProfileOrientation initialValue="Bisexual"/>` · C `<ScreenProfileOrientation initialHint={true}/>`. `initialHint` exists **for the spec sheet only**. In the app, the toast appears only after a Continue press. `OptionRow`, `DetailsScaffold` and the footer appear in that file only so it renders. **Build `OptionRow` from Profile 15 and the rest from Profile 14**, not from this file.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **Status bar and home indicator** are stylized mocks in the reference file. Ship the platform chrome and anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.
- **The scroll indicator** on short screens is the platform's. We do not draw a fade, a chevron or a "more" hint.

## Copy — final strings

Header: `Share some details`
Headline: `What's your sexual orientation?` — em on **orientation**, orange wash from the shared `.su-underlined em` rule
Sub: `Used to find the right matches.`
Options, in this order: `Straight` · `Gay` · `Lesbian` · `Bisexual` · `Pansexual` · `Other`
Visibility: `Don't display on my profile`
CTA: `Continue`
Toast: `Pick an orientation to continue`

The headline uses a typographic apostrophe if the platform font renders one; the string is otherwise exact. The toast has no closing full stop, as drawn. The option labels are display strings. What gets stored and tracked is the `enums.json` §1 value, never the label (see *Tracking*).

## Behaviour

- **On arrival nothing is selected**, unless this is a resume or a back navigation. No keyboard opens and no row is focused. The list is scrolled to the top.
- **Tapping a row selects it** and moves the selection off any other row. Tapping the selected row **does nothing**. The change is immediate, with the 180 ms background and indicator transition. Nothing else on the screen changes, and the list does not scroll.
- **The whole row is the hit area**: the label, the indicator and the padding between them.
- **Continue with an option selected:** persist `orientation` (the §1 value) and the visibility flag. Then advance the saved flow position, then navigate to dating language. Persist on Continue, before navigating. Never persist per tap (flow README rule 4a).
- **Continue with nothing selected:** show the toast. It fades in over 200 ms and auto-hides at 2600 ms. Nothing is persisted and nothing navigates. A second press restarts the timer. The toast hides as soon as an option is picked. There is no shake, no row is recoloured and the list does not scroll. The toast text is announced (`aria-live="polite"`).
- **There is no way past this step without an answer.** No Skip, no header action, no gesture.
- **Short screens (the six rows do not fit):** only the answer region scrolls. Flash the platform scroll indicator once on arrival, so the hidden rows are discoverable. The band and the CTA stay visible and tappable at every scroll position.
- **Visibility band:** unchecked by default. When checked, orientation is **not shown on the profile but is still used for matching**. It is saved with Continue. Only the box and the label are tappable, never the CTA's x-range.
- **Back** (chevron, iOS swipe-back, Android back) pops to gender and discards an unsaved selection. Gender shows its saved value.
- **Resume, and back from dating language,** pre-fill the saved value (state B) and the saved visibility. If the saved row is below the fold on a short screen, the list opens **scrolled so that row is fully visible**, without animation. Nothing is announced, and no toast shows on arrival.
- **Accessibility:** the list is a `radiogroup` **labelled by the headline** (`aria-labelledby`). Each row is a `radio` with `aria-checked`. A screen reader reads position (e.g. "Straight, 1 of 6"). Moving focus to a row below the fold scrolls it into the region's view.
- **CTA press feedback** comes from the shared NextButton. The label and the circle form one hit area.

## Build inventory

- **Profile entity: orientation as an enum, plus a hidden flag.** `enums.json` §1 marks orientation as backend *Not built*: there is no column yet. Add it as an enum of the six §1 values, so the column and the screen cannot drift, plus the hidden flag. Persist the §1 value, never the display label. **Until both exist, Continue is blocked and the step does not ship.**
- **No user property.** `properties.json` has no `orientation` user property, and this ticket does not ask for one. Orientation is Class 2; segmenting by it is a restricted-role analysis, not a default segment.

## Acceptance criteria

- [ ] The screen renders through **`DetailsScaffold` from Profile 14** and **`OptionRow` from Profile 15**. Neither is a copy, and the step count is the shell's prop
- [ ] AppHeader title `Share some details`, **back chevron** in the leading slot, trailing slot empty. StepProgress `steps={10} current={3}`
- [ ] Headline Lora 700 / 30 / 1.12, **two lines at 390**, one em on `orientation`, size **from the scaffold**
- [ ] Options appear in the order `Straight · Gay · Lesbian · Bisexual · Pansexual · Other`, **on every account, whatever gender was saved**. `Other` opens nothing
- [ ] Rows are `OptionRow` as built: pad 16 / 4, radius 10, label Manrope 500 16, 22 radio, divider under every row except the last. **Rows are the same height on every device**
- [ ] Selected row: wash `rgba(129,42,236,.06)`, label weight 700, filled violet radio with an 8 px white dot. **No element changes Y**
- [ ] Tapping the selected row again **keeps it selected**
- [ ] Picking an option **does not navigate**. Only Continue does
- [ ] **There is no SkipLink and no `Prefer not to say`.** The footer is `flex-end` with the left side empty
- [ ] **Continue is never disabled.** With nothing selected it shows the toast (⑯) for 2600 ms, with no navigation and nothing persisted. The toast hides as soon as an option is picked
- [ ] The toast is an overlay: **no element in the column changes Y** between A, B and C
- [ ] At 375 × 667, **only the answer region scrolls**. Header, progress bar, headline, sub, band and CTA are fixed and fully visible at every scroll position. The scroll indicator flashes once on arrival
- [ ] At 390 × 844 and 430 × 932, all six rows are visible and **nothing scrolls**
- [ ] Continue with a selection persists the **§1 value** and the visibility flag, then the flow position, then navigates to dating language
- [ ] Visibility is unchecked by default. When checked, orientation is hidden on the profile and kept in matching
- [ ] Back pops to gender and discards an unsaved selection
- [ ] A resume, or a back from dating language, pre-fills the saved value and visibility, with no toast. On a short screen a saved row below the fold is scrolled fully into view
- [ ] The radiogroup is labelled by the headline, and each row announces its checked state and position
- [ ] Exactly one `flex: 1` spacer, and no absolute Y positioning in the content column
- [ ] Copy matches the strings above exactly
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**. `form_validation_failed` fires on the refused press, not on render. No skip event can fire from this screen, and scrolling fires nothing
- [ ] **Evidence of done:** screenshots of **all three states** at **375 × 667, 390 × 844 and 430 × 932**, nine images, plus **one extra at 375 × 667 scrolled to the bottom**, ten in total. Each shows all chrome visible, nothing clipped, the visibility band and the CTA fully visible. At 390 × 844 and 430 × 932, all six rows are visible with no scroll. Check 375 × 667 first.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (families **D — Profile Identity**, **F — Profile Attributes**, **T — Errors**, and `screen_viewed` in family A). Use them exactly. Do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_orientation"`, `screen_name: "ProfileOrientation"`, `referrer_screen_id` |
| Step shown | `profile_step_viewed` | `step_id: "orientation"`, `step_index: 3` (§2) |
| Continue accepted | `detail_answered` | `field_id: "orientation"`, `value_bucketed` — the **§1 value** for the selected option, never the display label |
| Continue accepted | `profile_step_completed` | `step_id: "orientation"`, `time_on_step_s` |
| Continue refused | `form_validation_failed` | `field_id: "orientation"`, `rule: "required_missing"` (§8), `screen_id: "profile_orientation"`, `step_id: "orientation"` |
| Visibility box ticked | `field_display_opted_out` | `field_id: "orientation"` — **on tick only**, no event on untick |

Rules that are easy to get wrong here:

- **Orientation is Class 2** (special category, GDPR Art. 9, `requirements.json` → Class 2). Its §1 set is already closed, so no bucketing is needed: the value is sent as is, but only as a §1 value. `sensitivity_class` and `field_registry_version` are stamped at emit time. See the emitter rules in `design_handoff_showup/CLAUDE.md`.
- **`gay` and `lesbian` are separate values** (§1 note). Do not merge them in the client; an analysis that wants the combined figure unions the two.
- **`detail_answered` fires once, on the accepted Continue, not on each tap.** A user who taps through three options before Continue sends one answer.
- **`referrer_screen_id` is the screen the user actually came from**: `profile_gender` going forward, `profile_dating_language` on a back from dating language. It is set by the navigation and never hard-coded.
- **On Continue, `detail_answered` and `profile_step_completed` fire before navigation**, in that order, and only for an accepted press.

**Events that must NOT fire here.**

- **`profile_step_skipped` and `detail_skipped`.** This step has no Skip. Either one appearing with `step_id` / `field_id: "orientation"` is a bug.
- **Anything for a row tap, a changed selection, a scroll or the toast itself.** No `option_selected`, no `list_scrolled`. `form_validation_failed` already records the refusal.
- **`profile_build_started`**, which fires once, on screen 01.

**Screen-to-screen conversion.** Each screen's `screen_viewed` carries the previous screen's id in `referrer_screen_id`, so every edge is a ratio of two counts.

| Edge | Numerator | Denominator |
|---|---|---|
| Gender → orientation | `screen_viewed` · `screen_id: "profile_orientation"` · `referrer_screen_id: "profile_gender"` | `screen_viewed` · `screen_id: "profile_gender"` |
| Orientation → dating language | `screen_viewed` · `screen_id: "profile_dating_language"` · `referrer_screen_id: "profile_orientation"` | `screen_viewed` · `screen_id: "profile_orientation"` |

Orientation is the second mandatory special-category question in a row. The second edge measures the drop-off from it; watch it beside gender → orientation.

The `profile_orientation` §11 row was registered 5 Oct 2026 (registry 1.4.8). This ticket marks it as ticketed (registry 1.4.11).

**Missing from the current build:** every event above is `implemented: false`. `detail_answered` for orientation is backend *Not built*.

## Out of scope

Dating language and the later steps. Also: a free-text or extended list behind `Other` (e.g. asexual, queer), a `Prefer not to say` option, editing orientation from Settings after onboarding, how orientation shows on the public profile, any compatibility check between the gender and orientation answers, and the search filter `meet_orientation` (§10). That filter belongs to matching, and its payloads are Class 2 about the person filtering (`requirements.json` decision 07).

## Dependencies

- **Profile 15 (Gender)** builds `OptionRow` and is the only forward origin.
- **Profile 14 (Height)** builds `DetailsScaffold`, `ValidationToast` and the footer.
- **Dating language** (step 4), the only destination.
- **Profile entity orientation enum + hidden flag** — see *Build inventory*.
- The saved flow position and resume (flow README rule 4a).
- ProfileVisibility, NextButton and Atmosphere `form` from `shared.jsx`.

## Open (not blocking)

- **Two mandatory special-category questions in a row**, in a group whose first step is skippable. Mandatory, no `Prefer not to say` and shown by default were all chosen deliberately on 5 Oct 2026. If the orientation → dating language edge drops noticeably, revisit them before redesigning anything else.
- **The 375 × 667 deficit is an estimate** (≈ 45). The scroll handles any value, so nothing depends on it; the evidence screenshot shows the real one.
- **The progress-bar count** (10 segments against 9 §2 indices) is still open from Profile 14. It belongs to the group shell; this screen is `current={3}` either way.
