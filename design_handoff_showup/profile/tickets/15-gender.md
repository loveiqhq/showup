# [Profile 15] Gender

**Parent:** Profile creation flow (SHOWUP-TBD — epic `profile/tickets/00-epic.md`) · **Type:** Story · **Priority:** Medium
**Attachments:** `profile-15-gender-attachment.zip` (handoff folder for this ticket) · `profile-15-gender-spec-sheet.png`
**Blocked by:** Profile 14 (Height) — it builds `DetailsScaffold`, which this screen renders through.

## Description

Step 2 of **"Share some details"**. One question, four options, single-select: the user's gender. The answer is used for matching. The user can hide it from their profile, but **cannot skip the step**.

**This screen builds `OptionRow`**, the single-select row. Orientation, education, religion and politics reuse it, so build it once here as a separate component. A second copy is a bug. The screen itself adds nothing to the group shell, because it **consumes** `DetailsScaffold` from Profile 14.

Three states in one column: **A** empty (arrival), **B** an option selected, **C** Continue refused. Nothing moves between them.

Entered from **height's Continue or Skip**, or by **back from orientation**. Exits:

- **Continue** with an option selected → orientation (step 3). Saves gender and visibility.
- **Back chevron** → pops to height.

## Decided before build

All decided 5 Oct 2026. Every point below matches the kit as drawn.

- **Gender is mandatory.** There is no `Skip for now`. Under profile README rule 0, a screen without a SkipLink is mandatory. The footer row is `flex-end` and its left side stays **empty** (callout ⑪). Do not add a SkipLink to match height.
- **A radio never clears.** Tapping the selected row again keeps it selected. The kit comments in the habits and life screens said gender clears on a second tap. They were wrong and are corrected. Habits and life chips do clear; `OptionRow` does not.
- **"Other" is a plain option.** It has no free-text field, no sub-list and no follow-up.
- **No auto-advance.** Picking an option changes only the selection. Continue moves on.
- **The visibility band stays**, the same band as height (callout ⑩).
- **Refusal is the group's toast** (callout ⑯), the same `ValidationToast` as height. **Continue is never disabled.**
- **One footer component.** `FooterContinue` is the footer without its SkipLink. Build it as the Profile 14 footer with an optional skip, not as a second footer.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** It is the `DetailsScaffold` column from Profile 14, unchanged: StatusBar (54) → AppHeader (52) → content (`flex: 1`, padding `4 24 0`, overflow hidden) → HomeIndicator (28).

**Answer region:** a radiogroup of four `OptionRow`s, stacked with no gap (the divider separates them) → **one `flex: 1` spacer**. Header, progress bar, band and footer never move.

⚠ **The only absolutely-positioned elements are the Atmosphere backdrop and the toast.** The toast is an overlay against the footer row and moves nothing.

**No keyboard on this screen.**

📎 All specs are on the attached **profile-15-gender-spec-sheet.png**: frames A, B and C, callouts ①–⑰, with danger red for state C. The design system has colour and type tokens only and no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/profile/screen-gender-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. States: A `<ScreenProfileGender/>` · B `<ScreenProfileGender initialValue="Non-binary"/>` · C `<ScreenProfileGender initialHint={true}/>`. `initialHint` exists **for the spec sheet only**. In the app, the toast appears only after a Continue press. `DetailsScaffold` and the footer appear in that file only so it renders. **Build them from Profile 14**, not from this file.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **Status bar and home indicator** are stylized mocks in the reference file. Ship the platform chrome and anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.

## Copy — final strings

Header: `Share some details`
Headline: `Which gender describes you best?` — em on **you**, orange wash from the shared `.su-underlined em` rule
Sub: `Used to find the right matches.`
Options, in this order: `Woman` · `Man` · `Non-binary` · `Other`
Visibility: `Don't display on my profile`
CTA: `Continue`
Toast: `Pick a gender to continue`

The toast has no closing full stop, as drawn. The option labels are display strings. What gets stored and tracked is the `enums.json` §1 value, never the label (see *Tracking*).

## Behaviour

- **On arrival nothing is selected**, unless this is a resume or a back navigation. No keyboard opens and no row is focused.
- **Tapping a row selects it** and moves the selection off any other row. Tapping the selected row **does nothing**. The change is immediate, with the 180 ms background and indicator transition. Nothing else on the screen changes.
- **The whole row is the hit area**: the label, the indicator and the padding between them.
- **Continue with an option selected:** persist `gender` (the §1 value) and the visibility flag. Then advance the saved flow position, then navigate to orientation. Persist on Continue, before navigating. Never persist per tap (flow README rule 4a).
- **Continue with nothing selected:** show the toast. It fades in over 200 ms and auto-hides at 2600 ms. Nothing is persisted and nothing navigates. A second press restarts the timer. The toast hides as soon as an option is picked. There is no shake and no row is recoloured. The toast text is announced (`aria-live="polite"`).
- **There is no way past this step without an answer.** No Skip, no header action, no gesture.
- **Visibility band:** unchecked by default. When checked, gender is **not shown on the profile but is still used for matching**. It is saved with Continue. Only the box and the label are tappable, never the CTA's x-range.
- **Back** (chevron, iOS swipe-back, Android back) pops to height and discards an unsaved selection. Height shows its own saved value, if any.
- **Resume, and back from orientation,** pre-fill the saved value (state B) and the saved visibility. Nothing is announced, and no toast shows on arrival.
- **Accessibility:** the list is a `radiogroup` **labelled by the headline** (`aria-labelledby`). Each row is a `radio` with `aria-checked`. A screen reader reads position (e.g. "Woman, 1 of 4"). The kit's `radiogroup` has no label; the ticket wins here.
- **CTA press feedback** comes from the shared NextButton. The label and the circle form one hit area.

## Build inventory

- **Profile entity: gender as an enum, plus a hidden flag.** `enums.json` §1 marks gender as backend *Partial*: the column is `varchar(40)` and is to become an enum, so the two cannot drift (`requirements.json` decision 06). Persist the §1 value, never the display label. If the hidden flag for gender is not on the entity yet, Continue is blocked, and the step does not ship until it exists.
- **User property `gender`** (`properties.json`), set from `detail_answered`. It is missing today, so gender cannot be used as a segment.

## Acceptance criteria

- [ ] The screen renders through **`DetailsScaffold` from Profile 14**. It is not a copy, and the step count is the shell's prop
- [ ] AppHeader title `Share some details`, **back chevron** in the leading slot, trailing slot empty. StepProgress `steps={10} current={2}`
- [ ] Headline Lora 700 / 30 / 1.12, **two lines at 390**, one em on `you`, size **from the scaffold**
- [ ] **`OptionRow` is built once as a shared component**, ready for orientation, education, religion and politics: pad 16 / 4, radius 10, label Manrope 500 16, 22 radio, divider under every row except the last
- [ ] Options appear in the order `Woman · Man · Non-binary · Other`. `Other` opens nothing
- [ ] Selected row: wash `rgba(129,42,236,.06)`, label weight 700, filled violet radio with an 8 px white dot. **No element changes Y**
- [ ] Tapping the selected row again **keeps it selected**
- [ ] Picking an option **does not navigate**. Only Continue does
- [ ] **There is no SkipLink.** The footer is `flex-end` with the left side empty
- [ ] **Continue is never disabled.** With nothing selected it shows the toast (⑯) for 2600 ms, with no navigation and nothing persisted. The toast hides as soon as an option is picked
- [ ] The toast is an overlay: **no element in the column changes Y** between A, B and C
- [ ] Continue with a selection persists the **§1 value** and the visibility flag, then the flow position, then navigates to orientation
- [ ] Visibility is unchecked by default. When checked, gender is hidden on the profile and kept in matching
- [ ] Back pops to height and discards an unsaved selection
- [ ] A resume, or a back from orientation, pre-fills the saved value and visibility, with no toast
- [ ] The radiogroup is labelled by the headline, and each row announces its checked state and position
- [ ] Exactly one `flex: 1` spacer, and no absolute Y positioning in the content column
- [ ] Copy matches the strings above exactly
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**. `form_validation_failed` fires on the refused press, not on render. No skip event can fire from this screen
- [ ] **Evidence of done:** screenshots of **all three states** at **375 × 667, 390 × 844 and 430 × 932**, nine images in total. All content visible, nothing clipped, no scroll, all four rows, the visibility band and the CTA fully visible. Check 375 × 667 first, and state what the spacer resolved to there.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (families **D — Profile Identity**, **F — Profile Attributes**, **T — Errors**, and `screen_viewed` in family A). Use them exactly. Do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_gender"`, `screen_name: "ProfileGender"`, `referrer_screen_id` |
| Step shown | `profile_step_viewed` | `step_id: "gender"`, `step_index: 2` (§2) |
| Continue accepted | `detail_answered` | `field_id: "gender"`, `value_bucketed` — the **§1 value** for the selected option, never the display label |
| Continue accepted | `profile_step_completed` | `step_id: "gender"`, `time_on_step_s` |
| Continue refused | `form_validation_failed` | `field_id: "gender"`, `rule: "required_missing"` (§8), `screen_id: "profile_gender"`, `step_id: "gender"` |
| Visibility box ticked | `field_display_opted_out` | `field_id: "gender"` — **on tick only**, no event on untick |

Rules that are easy to get wrong here:

- **Gender is Class 2** (special category, `requirements.json` → Class 2). Its §1 set is already closed, so no bucketing is needed: the value is sent as is, but only as a §1 value. `sensitivity_class` and `field_registry_version` are stamped at emit time. See the emitter rules in `design_handoff_showup/CLAUDE.md`.
- **`detail_answered` fires once, on the accepted Continue, not on each tap.** A user who taps through three options before Continue sends one answer.
- **`referrer_screen_id` is the screen the user actually came from**: `profile_height` going forward, `profile_orientation` on a back from orientation. It is set by the navigation and never hard-coded.
- **On Continue, `detail_answered` and `profile_step_completed` fire before navigation**, in that order, and only for an accepted press.

**Events that must NOT fire here.**

- **`profile_step_skipped` and `detail_skipped`.** This step has no Skip. Either one appearing with `step_id` / `field_id: "gender"` is a bug.
- **Anything for a row tap, a changed selection or the toast itself.** No `option_selected`. `form_validation_failed` already records the refusal.
- **`profile_build_started`**, which fires once, on screen 01.

**Screen-to-screen conversion.** Each screen's `screen_viewed` carries the previous screen's id in `referrer_screen_id`, so every edge is a ratio of two counts.

| Edge | Numerator | Denominator |
|---|---|---|
| Height → gender | `screen_viewed` · `screen_id: "profile_gender"` · `referrer_screen_id: "profile_height"` | `screen_viewed` · `screen_id: "profile_height"` |
| Gender → orientation | `screen_viewed` · `screen_id: "profile_orientation"` · `referrer_screen_id: "profile_gender"` | `screen_viewed` · `screen_id: "profile_gender"` |

Because gender is mandatory, the second edge measures the drop-off from a required question. It is the number to watch on this screen.

The `profile_gender` §11 row was registered 5 Oct 2026 (registry 1.4.8). This ticket marks it as ticketed (registry 1.4.10).

**Missing from the current build:** every event above is `implemented: false`. `detail_answered` is backend *Partial*.

## Out of scope

Orientation and the later steps (beyond reusing `OptionRow`). Also: a free-text or extended identity list behind `Other`, editing gender from Settings after onboarding, how gender shows on the public profile, and how `non_binary` / `other` map onto the search filter `meet_gender` (§10). That mapping belongs to matching.

## Dependencies

- **Profile 14 (Height)** builds `DetailsScaffold`, `ValidationToast` and the footer. It is also the only forward origin.
- **Orientation** (step 3), the only destination. It is the first consumer of `OptionRow` after this screen.
- **Profile entity gender enum + hidden flag** — see *Build inventory*.
- The saved flow position and resume (flow README rule 4a).
- ProfileVisibility, NextButton and Atmosphere `form` from `shared.jsx`.

## Open (not blocking)

- **A mandatory step in an optional-looking group.** Height, step 1, has `Skip for now`. Gender, step 2, is the first step in "Share some details" without one. A user who skipped height may expect to skip here. Watch the gender → orientation edge.
- **375 × 667 should fit.** There is no keyboard, and by estimate the column needs ≈ 590 of the ≈ 647 below the SE's 20 status bar (no home indicator), so the spacer resolves to ≈ 55. The answer region must not scroll internally.
- **The progress-bar count** (10 segments against 9 §2 indices) is still open from Profile 14. It belongs to the group shell; this screen is `current={2}` either way.
