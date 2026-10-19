# [Profile 17] Dating language

**Parent:** Profile creation flow (SHOWUP-TBD — epic `profile/tickets/00-epic.md`) · **Type:** Story · **Priority:** Medium
**Attachments:** `profile-17-dating-language-attachment.zip` (handoff folder for this ticket) · `profile-17-dating-language-spec-sheet.png`
**Blocked by:** Profile 14 (Height), which builds `DetailsScaffold` and the footer. This screen renders through it.

## Description

Step 4 of **"Share some details"**. One question, eight languages, **multi-select with no limit**: the languages the user is happy to date in. The answer fills the profile's **"Date in"** line. The user can hide it from their profile, and **can skip the step**.

**This screen builds `CheckRow`**, the group's multi-select row. It is a new component, not a variant of `OptionRow`. It **consumes** `DetailsScaffold` and the footer from Profile 14. A second copy of either is a bug.

Two states in one column: **A** empty (arrival), **B** three languages ticked. Nothing moves between them. **There is no refused state.**

Entered from **orientation's Continue**, or by **back from education**. Exits:

- **Continue** with at least one ticked → education (step 5). Saves the ticked languages and visibility.
- **Continue** with nothing ticked → education. **Behaves exactly like Skip.**
- **Skip for now** → education. Saves nothing.
- **Back chevron** → pops to orientation.

## Decided before build

All decided 5 Oct 2026. Every point below matches the kit as drawn.

- **Dating language is skippable.** The footer carries `Skip for now` (callout ⑪). Under profile README rule 0, Skip advances the flow position, saves nothing for this step and **never deletes a value saved earlier**.
- **Continue with nothing ticked is a Skip** (callout ⑫). It advances, saves nothing and fires the skip events. There is no toast, no shake and no disabled state. This is the one screen in the group where an empty Continue is not refused.
- **No selection limit.** All eight can be ticked.
- **`CheckRow` is its own component** (callouts ⑥ ⑦ ⑮ ⑯): pad **12 / 4**, a **round** 22 indicator with an 11 px tick. `OptionRow` (Profile 15) is 16 / 4 with a radio, and its `kind="checkbox"` branch is **not** used here.
- **Saved in list order, never tap order** (callout ⑰). Ticking Spanish, then German, saves `german,spanish`. The profile shows them in the same order.
- **Fixed list for the MVP**, in a fixed order. It is never reordered by device locale.
- **Visibility is unchecked by default** (callout ⑩). Dating language shows on the profile unless the user ticks the box.
- **Tracking fires once, on Continue, with the final set.** This is a named exception to the multi-select rule in `requirements.json` — see *Tracking*.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** It is the `DetailsScaffold` column from Profile 14, unchanged: StatusBar (54) → AppHeader (52) → content (`flex: 1`, padding `4 24 0`, overflow hidden) → HomeIndicator (28).

**Answer region:** `flex: 1`, `min-height: 0`, `overflow-y: auto` (already in the scaffold). Inside it, a group of eight `CheckRow`s, stacked with no gap (the divider separates them) → **one `flex: 1` spacer**. At 390 × 844 the eight rows (≈ 375) only just fit and the spacer resolves to ≈ 6. At 430 × 932 it is ≈ 94. At 375 × 667 the list is ≈ 170 short: the spacer resolves to 0 and **the region scrolls internally** (callout ⑨). Header, progress bar, headline, sub, band and footer never move.

⚠ **The only absolutely-positioned element is the Atmosphere backdrop.** This screen has no toast.

**No keyboard on this screen.**

📎 All specs are on the attached **profile-17-dating-language-spec-sheet.png**: frames A and B, callouts ①–⑰. The design system has colour and type tokens only and no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/profile/screen-dating-language-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. States: A `<ScreenProfileLanguages/>` · B `<ScreenProfileLanguages initialValues={['German','English','Spanish']}/>`. **Build `CheckRow` from this file.** `DetailsScaffold` and the footer appear here only so it renders — **build those from Profile 14**, not from this file.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **Status bar and home indicator** are stylized mocks in the reference file. Ship the platform chrome and anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.
- **The scroll indicator** on short screens is the platform's. We do not draw a fade, a chevron or a "more" hint.

## Copy — final strings

Header: `Share some details`
Headline: `What's your preferred dating language?` — em on **language**, orange wash from the shared `.su-underlined em` rule
Sub: `Select all that apply.`
Options, in this order: `German` · `English` · `Spanish` · `Italian` · `French` · `Turkish` · `Russian` · `Arabic`
Visibility: `Don't display on my profile`
Skip: `Skip for now`
CTA: `Continue`

The headline uses a typographic apostrophe if the platform font renders one; the string is otherwise exact. The option labels are display strings. What gets stored and tracked is the `enums.json` §1 value, never the label.

## Behaviour

- **On arrival nothing is ticked**, unless this is a resume or a back navigation. No keyboard opens and no row is focused. The list is scrolled to the top.
- **Tapping a row toggles it.** Tapping a ticked row unticks it. Other rows are unaffected. The change is immediate, with the 180 ms background and indicator transition. Nothing else on the screen changes, and the list does not scroll.
- **The whole row is the hit area**: the label, the indicator and the padding between them.
- **Continue with at least one ticked:** persist `dating_language` (the ticked §1 values, **in list order**) and the visibility flag. Then advance the saved flow position, then navigate to education. Persist on Continue, before navigating. Never persist per tap (flow README rule 4a).
- **Continue with nothing ticked:** identical to Skip — see below. No toast, no error, nothing refused.
- **Skip for now:** advance the saved flow position and navigate to education. Save nothing for this step. **A value saved earlier is kept** (README rule 0). No confirmation. The visibility flag is not saved on a skip.
- **Short screens (the eight rows do not fit):** only the answer region scrolls. Flash the platform scroll indicator once on arrival, so the hidden rows are discoverable. The band and the footer stay visible and tappable at every scroll position.
- **Visibility band:** unchecked by default. When checked, dating language is **not shown on the profile**; the value is still saved. It is saved with an accepted Continue. Only the box and the label are tappable.
- **Back** (chevron, iOS swipe-back, Android back) pops to orientation and discards unsaved ticks. Orientation shows its saved value.
- **Resume, and back from education,** pre-fill the saved languages (state B) and the saved visibility. The list opens **scrolled to the top**, without animation. Nothing is announced.
- **Unticking everything on a resume and pressing Continue is a Skip**, so the earlier saved value is kept. See *Open*.
- **Accessibility:** the list is a `group` **labelled by the headline** (`aria-labelledby`). Each row is a `checkbox` with `aria-checked`. A screen reader reads position (e.g. "German, checkbox, not checked, 1 of 8"). Moving focus to a row below the fold scrolls it into the region's view.
- **CTA press feedback** comes from the shared NextButton. The label and the circle form one hit area. `SkipLink` is the shared one from `shared.jsx`.

## Build inventory

- **`CheckRow`**, built here. Later multi-select steps consume it.
- **Profile entity: dating language as a set of enum values, plus a hidden flag.** `enums.json` §1 marks it backend *Not built*. Store the §1 values of the eight options (not the labels), in list order. The search profile's `datingLanguage` ("Date in") reads from it. **Until both exist, Continue is blocked and the step does not ship.**
- **No user property.** `properties.json` has no dating-language user property, and this ticket does not ask for one.

## Acceptance criteria

- [ ] The screen renders through **`DetailsScaffold` from Profile 14**, not a copy, and the step count is the shell's prop
- [ ] AppHeader title `Share some details`, **back chevron** in the leading slot, trailing slot empty. StepProgress `steps={10} current={4}`
- [ ] Headline Lora 700 / 30 / 1.12, **two lines at 390**, one em on `language`, size **from the scaffold**
- [ ] Options appear in the order `German · English · Spanish · Italian · French · Turkish · Russian · Arabic` on every device and locale
- [ ] Rows are **`CheckRow`**, not `OptionRow`: pad 12 / 4, radius 10, label Manrope 500 16, 22 **round** indicator, divider under every row except the last. **Rows are the same height on every device**
- [ ] Ticked row: wash `rgba(129,42,236,.06)`, label weight 700, filled violet circle with an 11 px white tick. **No element changes Y**
- [ ] Tapping a ticked row unticks it. All eight can be ticked at once
- [ ] Ticking a row **does not navigate**. Only Continue and Skip do
- [ ] The footer is `space-between`: `Skip for now` on the left, `Continue` on the right. **Continue is never disabled**
- [ ] **Continue with nothing ticked behaves exactly like Skip**: no toast, no shake, no error, advances to education, saves nothing
- [ ] Skip advances, saves nothing and **keeps a value saved earlier**, with no confirmation
- [ ] Continue with ticks persists the **§1 values in list order** and the visibility flag, then the flow position, then navigates to education
- [ ] At 375 × 667, **only the answer region scrolls**. Header, progress bar, headline, sub, band and footer are fixed and fully visible at every scroll position. The scroll indicator flashes once on arrival
- [ ] At 390 × 844 and 430 × 932, all eight rows are visible and **nothing scrolls**
- [ ] Visibility is unchecked by default. When checked, dating language is hidden on the profile
- [ ] Back pops to orientation and discards unsaved ticks
- [ ] A resume, or a back from education, pre-fills the saved languages and visibility, scrolled to the top
- [ ] The group is labelled by the headline, and each row announces its checked state and position
- [ ] Exactly one `flex: 1` spacer, and no absolute Y positioning in the content column
- [ ] Copy matches the strings above exactly
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**. Nothing fires per tap. An empty Continue fires the skip events, never `form_validation_failed`
- [ ] **Evidence of done:** screenshots of **both states** at **375 × 667, 390 × 844 and 430 × 932**, six images, plus **one extra at 375 × 667 scrolled to the bottom**, seven in total. Each shows all chrome visible, nothing clipped, the visibility band and the footer fully visible. At 390 × 844 and 430 × 932, all eight rows are visible with no scroll. Check 375 × 667 first.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (families **D — Profile Identity**, **F — Profile Attributes**, and `screen_viewed` in family A). Use them exactly. Do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_dating_language"`, `screen_name: "ProfileDatingLanguage"`, `referrer_screen_id` |
| Step shown | `profile_step_viewed` | `step_id: "dating_language"`, `step_index: 4` (§2) |
| Continue with ticks | `detail_answered` | `field_id: "dating_language"`, `value_bucketed` — the ticked **§1 values in list order, comma-joined, no spaces**, e.g. `"german,english,spanish"` — **NEW format, registry 1.4.12** |
| Continue with ticks | `profile_step_completed` | `step_id: "dating_language"`, `time_on_step_s` |
| Skip, **or Continue with nothing ticked** | `detail_skipped` | `field_id: "dating_language"` |
| Skip, **or Continue with nothing ticked** | `profile_step_skipped` | `step_id: "dating_language"`, `screen_id: "profile_dating_language"` |
| Visibility box ticked | `field_display_opted_out` | `field_id: "dating_language"` — **on tick only**, no event on untick |

Rules that are easy to get wrong here:

- **One `detail_answered` per accepted Continue, carrying the final set.** This is a **named exception** to the `requirements.json` multi-select rule ("fire once per change with a running total"), decided 5 Oct 2026 and recorded there and in `enums.json` §1. That rule still applies to interests (`interest_selected` / `interest_deselected`); do not copy it here, and do not copy this exception there.
- **List order, not tap order.** `value_bucketed` for Spanish-then-German is `"german,spanish"`. Same order in storage.
- **Dating language is Class 1** (§1). The values are sent as is, but only as §1 values. `sensitivity_class` and `field_registry_version` are stamped at emit time. See the emitter rules in `design_handoff_showup/CLAUDE.md`.
- **An empty Continue and a Skip are recorded identically.** Nothing distinguishes them in the payload, by decision. Do not add a property to tell them apart.
- **`referrer_screen_id` is the screen the user actually came from**: `profile_orientation` going forward, `profile_education` on a back from education. It is set by the navigation and never hard-coded.
- **On an accepted Continue, `detail_answered` and `profile_step_completed` fire before navigation**, in that order. On a skip, `detail_skipped` and `profile_step_skipped` fire before navigation, in that order.

**Events that must NOT fire here.**

- **Anything per tap.** No `interest_selected`, no `option_selected`, no `language_toggled`, no running total. A user who ticks and unticks five times before Continue sends one answer.
- **`form_validation_failed`.** There is no refused state on this screen.
- **`detail_answered` with an empty `value_bucketed`.** An empty set is a skip.
- **Anything for a scroll.**
- **`profile_build_started`**, which fires once, on screen 01.

**Screen-to-screen conversion.** Each screen's `screen_viewed` carries the previous screen's id in `referrer_screen_id`, so every edge is a ratio of two counts.

| Edge | Numerator | Denominator |
|---|---|---|
| Orientation → dating language | `screen_viewed` · `screen_id: "profile_dating_language"` · `referrer_screen_id: "profile_orientation"` | `screen_viewed` · `screen_id: "profile_orientation"` |
| Dating language → education | `screen_viewed` · `screen_id: "profile_education"` · `referrer_screen_id: "profile_dating_language"` | `screen_viewed` · `screen_id: "profile_dating_language"` |

The skip rate for this step is `profile_step_skipped` · `step_id: "dating_language"` over `profile_step_viewed` · `step_id: "dating_language"`.

The `profile_dating_language` §11 row was registered 5 Oct 2026 (registry 1.4.8). This ticket marks it as ticketed (registry 1.4.12).

**Missing from the current build:** every event above is `implemented: false`. `detail_answered` for dating language is backend *Not built*.

## Out of scope

Education and the later steps. Also: **spoken languages** (the profile's "Speaks" line), more than eight languages, BCP 47 codes, reordering by device locale, a free-text or "Other" language, a selection limit, editing dating language from Settings after onboarding, how "Date in" renders on the public profile, and any matching or search filter on shared dating language.

## Dependencies

- **Profile 14 (Height)** builds `DetailsScaffold` and the footer.
- **Profile 16 (Orientation)**, the only forward origin.
- **Education** (step 5), the only destination.
- **Profile entity dating-language set + hidden flag** — see *Build inventory*.
- The saved flow position and resume (flow README rule 4a).
- ProfileVisibility, SkipLink, NextButton and Atmosphere `form` from `shared.jsx`.

## Open (not blocking)

- **A saved answer cannot be cleared in onboarding.** Unticking everything on a resume and pressing Continue is a Skip, and rule 0 keeps the earlier value. The screen then shows nothing ticked while the profile still holds the old set. Default: keep it this way for the MVP; clearing belongs to profile editing.
- **The 375 × 667 deficit is an estimate** (≈ 170, about three and a half rows). The scroll handles any value, so nothing depends on it; the evidence screenshot shows the real one.
- **390 × 844 has ≈ 6 px of slack.** Any growth in the scaffold above the list (a three-line headline in a longer locale) makes this screen scroll there too. That is acceptable; rows are never shortened to prevent it.
- **The progress-bar count** (10 segments against 9 §2 indices) is still open from Profile 14. It belongs to the group shell; this screen is `current={4}` either way.
