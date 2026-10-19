# [Profile 19] Religion

**Parent:** Profile creation flow (SHOWUP-TBD — epic `profile/tickets/00-epic.md`) · **Type:** Story · **Priority:** Medium
**Attachments:** `profile-19-religion-attachment.zip` (handoff folder for this ticket) · `profile-19-religion-spec-sheet.png`
**Blocked by:** Profile 14 (Height), which builds `DetailsScaffold` and the footer, and Profile 15 (Gender), which builds `OptionRow`. This screen renders through both.

## Description

Step 6 of **"Share some details"**. One question, nine options, **single-select**: the user's religion or belief. The user can hide it from their profile, and **can skip the step**. Religion is **special-category data** (GDPR Art. 9, tracking Class 2).

**This screen builds nothing.** It **consumes** `DetailsScaffold` and the footer from Profile 14 and `OptionRow kind="radio"` from Profile 15. A second copy of any of them is a bug.

Two states in one column: **A** empty (arrival), **B** `Buddhist` selected. The sheet adds frame **C**: state A with the list scrolled to the end. Only the answer region moves between frames. **There is no refused state.**

Entered from **education's Continue or Skip**, or by **back from politics**. Exits:

- **Continue** with an option selected → politics (step 7). Saves the selection and visibility.
- **Continue** with nothing selected → politics. **Behaves exactly like Skip.**
- **Skip for now** → politics. Saves nothing.
- **Back chevron** → pops to education.

## Decided before build

All decided 5 Oct 2026. Every point below matches the kit as drawn.

- **Nine options: `Muslim` is added, before `Jewish`** (callout ⑧). The kit carried eight until 5 Oct 2026, with no option for Islam. `enums.json` §1 gains `muslim` (registry 1.4.14). Nothing had shipped, so nothing migrates.
- **Rows are the standard `OptionRow` (16 / 4), not `OptionRowCompact` (12 / 4).** The kit drew religion with a compact row so eight options fitted. That was a second copy of `OptionRow`, and the rule is that rows are never shortened to fit (Profile 16, orientation). The list scrolls instead (callouts ⑨ and ⑲, violet).
- **The list scrolls on every device**, not only on short screens. This is the first screen in the group where that happens. Nine rows are ≈ 494 tall; the region shows ≈ 390 at 390 × 844. See *Layout*.
- **Religion is skippable.** The footer carries `Skip for now` (callout ⑪). Under profile README rule 0, Skip advances the flow position, saves nothing for this step and **never deletes a value saved earlier**.
- **Continue with nothing selected is a Skip** (callout ⑫). No toast, no shake, no disabled state. Same rule as education (Profile 18).
- **Tapping the selected row again clears it** (callout ⑰). This is the education rule, applied unchanged. It is **screen logic** in the row's tap handler and **not** a new `OptionRow` prop. Gender and orientation still never clear.
- **Sub copy is `Select one.`** (callout ⑤). It replaces `Used to find the right matches.`
- **Visibility is unchecked by default** (callout ⑩). When checked, religion is **not shown on the profile but is still used for matching**. Same meaning as gender and orientation.
- **Tracking sends the §1 value**, as registered, the same as gender. See *Tracking*.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** It is the `DetailsScaffold` column from Profile 14, unchanged: StatusBar (54) → AppHeader (52) → content (`flex: 1`, padding `4 24 0`, overflow hidden) → HomeIndicator (28).

**Answer region:** `flex: 1`, `min-height: 0`, `overflow-y: auto` (already in the scaffold). Inside it, a radiogroup of nine `OptionRow`s (≈ 55 each including the divider), stacked with no gap → **one `flex: 1` spacer**, which resolves to 0 on every device.

| Device | Region shows | List is short by | Rows fully visible |
|---|---|---|---|
| 375 × 667 | ≈ 213 | ≈ 281 | 3 |
| 390 × 844 | ≈ 390 | ≈ 104 | 7 |
| 430 × 932 | ≈ 478 | ≈ 16 | 8 |

The header, progress bar, headline, sub, visibility band and footer **never move** at any scroll position. Never shrink a row, the band or the CTA to make the list fit.

⚠ **The only absolutely-positioned element is the Atmosphere backdrop.** This screen has no toast.

**No keyboard on this screen.**

📎 All specs are on the attached **profile-19-religion-spec-sheet.png**: frames A, B and C, callouts ①–⑲. Callouts ⑨ and ⑲ are **violet**: the list scrolls on every device, which no earlier screen in the group does. The design system has colour and type tokens only and no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/profile/screen-religion-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. States: A `<ScreenProfileReligion/>` · B `<ScreenProfileReligion initialValue="Buddhist"/>` · frame C is state A scrolled to the end, not a prop. `DetailsScaffold` and the footer appear in this file only so it renders. **Build those from Profile 14**, and **`OptionRow` from Profile 15**, not from this file.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **Status bar and home indicator** are stylized mocks in the reference file. Ship the platform chrome and anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.
- **The scroll indicator** is the platform's. We flash it once on arrival. We do not draw a fade, a chevron or a "more" hint.

## Copy — final strings

Header: `Share some details`
Headline: `What are your religious beliefs?` — em on **religious**, orange wash from the shared `.su-underlined em` rule
Sub: `Select one.`
Options, in this order: `Protestant` · `Catholic` · `Orthodox` · `Muslim` · `Jewish` · `Buddhist` · `Hindu` · `Atheist` · `Spiritual / other`
Visibility: `Don't display on my profile`
Skip: `Skip for now`
CTA: `Continue`

The option labels are display strings. What gets stored and tracked is the `enums.json` §1 value (`protestant` · `catholic` · `orthodox` · `muslim` · `jewish` · `buddhist` · `hindu` · `atheist` · `spiritual_other`), never the label.

## Behaviour

- **On arrival nothing is selected**, unless this is a resume or a back navigation. No keyboard opens and no row is focused. The list is scrolled to the top.
- **Flash the platform scroll indicator once on arrival**, so the rows below the fold are discoverable. Nothing else hints at them.
- **Tapping an unselected row selects it** and deselects any other row. The change is immediate, with the 180 ms background and indicator transition. Nothing else on the screen changes, and the list does not scroll.
- **Tapping the selected row clears it.** The screen returns to state A with the same 180 ms transition. Nothing is saved or tracked.
- **The whole row is the hit area**: the label, the indicator and the padding between them. A scroll gesture that starts on a row does not select it.
- **No auto-advance.** Selecting a row never navigates. Only Continue and Skip do.
- **Continue with a selection:** persist `religion` (the §1 value) and the visibility flag. Then advance the saved flow position, then navigate to politics. Persist on Continue, before navigating. Never persist per tap (flow README rule 4a).
- **Continue with nothing selected:** identical to Skip — see below. No toast, no error, nothing refused.
- **Skip for now:** advance the saved flow position and navigate to politics. Save nothing for this step. **A value saved earlier is kept** (README rule 0). No confirmation. The visibility flag is not saved on a skip.
- **Scrolling:** only the answer region scrolls, on every device. The band and the footer stay visible and tappable at every scroll position.
- **Visibility band:** unchecked by default. When checked, religion is **not shown on the profile but is still used for matching**. It is saved with an accepted Continue. Only the box and the label are tappable.
- **Back** (chevron, iOS swipe-back, Android back) pops to education and discards an unsaved selection. Education shows its saved value.
- **Resume, and back from politics,** pre-fill the saved selection (state B) and the saved visibility, without animation. If the saved row is below the fold, the list opens **scrolled so that row is fully visible**. Nothing is announced.
- **Clearing the selection on a resume and pressing Continue is a Skip**, so the earlier saved value is kept. See *Open*.
- **Accessibility:** the list is a `radiogroup` **labelled by the headline** (`aria-labelledby`). Each row is a `radio` with `aria-checked`. A screen reader reads position (e.g. "Muslim, radio button, not checked, 4 of 9"). Moving focus to a row below the fold scrolls it into the region. Activating the checked radio clears it; the change is announced as "not checked".
- **CTA press feedback** comes from the shared NextButton. The label and the circle form one hit area. `SkipLink` is the shared one from `shared.jsx`.

## Build inventory

- **Nothing new to build on the client.** `OptionRow` (Profile 15), `DetailsScaffold` and the footer (Profile 14) are consumed. `OptionRowCompact` is **not** used.
- **Profile entity: religion as one enum value, plus a hidden flag.** `enums.json` §1 marks it backend *Not built*. Store the §1 value (not the label). It is special-category data: storage and access follow the Class 2 rules. The search profile's `religion` tile reads from it. **Until both exist, Continue is blocked and the step does not ship.**
- **No user property.** `properties.json` has no religion user property, and this ticket does not ask for one. A Class 2 value as a segmentation property is a separate decision.

## Acceptance criteria

- [ ] The screen renders through **`DetailsScaffold` from Profile 14**, not a copy, and the step count is the shell's prop
- [ ] AppHeader title `Share some details`, **back chevron** in the leading slot, trailing slot empty. StepProgress `steps={10} current={6}`
- [ ] Headline Lora 700 / 30 / 1.12, **two lines at 390**, one em on `religious`, size **from the scaffold**
- [ ] Sub copy is `Select one.`, not `Used to find the right matches.`
- [ ] **Nine options**, in the order `Protestant · Catholic · Orthodox · Muslim · Jewish · Buddhist · Hindu · Atheist · Spiritual / other`, on every device and locale
- [ ] Rows are **`OptionRow kind="radio"` from Profile 15**, not a copy and **not `OptionRowCompact`**: pad 16 / 4, radius 10, label Manrope 500 16, 22 round indicator, divider under every row except the last. **Rows are the same height on every device**
- [ ] Selected row: wash `rgba(129,42,236,.06)`, label weight 700, filled violet circle with an 8 px white dot. **No element changes Y**
- [ ] Selecting a row deselects any other. **Tapping the selected row clears it**, and the screen shows state A
- [ ] The clear is implemented in this screen's tap handler. **`OptionRow` gains no new prop**, and gender and orientation still never clear
- [ ] Selecting a row **does not navigate**. Only Continue and Skip do
- [ ] The footer is `space-between`: `Skip for now` on the left, `Continue` on the right. **Continue is never disabled**
- [ ] **Continue with nothing selected behaves exactly like Skip**: no toast, no shake, no error, advances to politics, saves nothing
- [ ] Skip advances, saves nothing and **keeps a value saved earlier**, with no confirmation
- [ ] Continue with a selection persists the **§1 value** and the visibility flag, then the flow position, then navigates to politics
- [ ] On **all three devices, only the answer region scrolls**. Header, progress bar, headline, sub, band and footer are fixed and fully visible at every scroll position
- [ ] The platform scroll indicator flashes once on arrival. No fade, chevron or "more" hint is drawn
- [ ] Visibility is unchecked by default. When checked, religion is hidden on the profile and kept in matching
- [ ] Back pops to education and discards an unsaved selection
- [ ] A resume, or a back from politics, pre-fills the saved selection and visibility. A saved row below the fold is scrolled fully into view
- [ ] The radiogroup is labelled by the headline, and each row announces its checked state and position out of 9
- [ ] Exactly one `flex: 1` spacer, and no absolute Y positioning in the content column
- [ ] Copy matches the strings above exactly
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**. Nothing fires per tap, including the clear, or per scroll. An empty Continue fires the skip events, never `form_validation_failed`. `value_bucketed` is only ever a §1 value
- [ ] **Evidence of done:** screenshots of **both states** at **375 × 667, 390 × 844 and 430 × 932**, six images, plus **one at 375 × 667 scrolled to the bottom**, seven in total. Each shows all chrome visible, nothing clipped, the visibility band and the footer fully visible. The scrolled one shows `Spiritual / other` fully visible with no divider under it. Check 375 × 667 first.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (families **D — Profile Identity**, **F — Profile Attributes**, and `screen_viewed` in family A). Use them exactly. Do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_religion"`, `screen_name: "ProfileReligion"`, `referrer_screen_id` |
| Step shown | `profile_step_viewed` | `step_id: "religion"`, `step_index: 6` (§2) |
| Continue with a selection | `detail_answered` | `field_id: "religion"`, `value_bucketed` — one §1 value (`enums.json` §1) |
| Continue with a selection | `profile_step_completed` | `step_id: "religion"`, `time_on_step_s` |
| Skip, **or Continue with nothing selected** | `detail_skipped` | `field_id: "religion"` |
| Skip, **or Continue with nothing selected** | `profile_step_skipped` | `step_id: "religion"`, `screen_id: "profile_religion"` |
| Visibility box ticked | `field_display_opted_out` | `field_id: "religion"` — **on tick only**, no event on untick |

Rules that are easy to get wrong here:

- **Religion is Class 2** (special category, `requirements.json` → Class 2). Its §1 set is closed, so no further bucketing is needed: the value is sent as is, but **only as a §1 value, never the label**. `sensitivity_class` and `field_registry_version` (now **1.4.14**) are stamped at emit time. See the emitter rules in `design_handoff_showup/CLAUDE.md`.
- **`muslim` is a new §1 value** (registry 1.4.14). An emitter built from an older copy of `enums.json` will not know it, and fail closed. Check the registry version before building.
- **One `detail_answered` per accepted Continue**, carrying the selected value. Selecting, changing and clearing send nothing.
- **An empty Continue and a Skip are recorded identically.** This includes a user who selected and then cleared. Nothing distinguishes them in the payload, by decision. Do not add a property to tell them apart.
- **`referrer_screen_id` is the screen the user actually came from**: `profile_education` going forward, `profile_politics` on a back from politics. It is set by the navigation and never hard-coded.
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
| Education → religion | `screen_viewed` · `screen_id: "profile_religion"` · `referrer_screen_id: "profile_education"` | `screen_viewed` · `screen_id: "profile_education"` |
| Religion → politics | `screen_viewed` · `screen_id: "profile_politics"` · `referrer_screen_id: "profile_religion"` | `screen_viewed` · `screen_id: "profile_religion"` |

The skip rate for this step is `profile_step_skipped` · `step_id: "religion"` over `profile_step_viewed` · `step_id: "religion"`.

The `profile_religion` §11 row was registered 5 Oct 2026 (registry 1.4.8). This ticket marks it as ticketed and adds `muslim` to §1 (registry 1.4.14).

**Missing from the current build:** every event above is `implemented: false`. `detail_answered` for religion is backend *Not built*.

## Out of scope

Politics and the later steps. Also: denominations or sub-groups (e.g. within Christianity or Islam), a free-text field behind `Spiritual / other`, a "Prefer not to say" option (Skip covers it), reordering or relabelling by market or locale, editing religion from Settings after onboarding, how the religion tile renders on the public profile, any matching or search filter on religion, and the Art. 9 consent mechanism (see *Open*).

## Dependencies

- **Profile 14 (Height)** builds `DetailsScaffold` and the footer.
- **Profile 15 (Gender)** builds `OptionRow`.
- **Profile 18 (Education)**, the only forward origin.
- **Politics** (step 7), the only destination.
- **Profile entity religion value + hidden flag**, with Class 2 storage — see *Build inventory*.
- **Tracking registry 1.4.14** (`enums.json` §1 `muslim`).
- The saved flow position and resume (flow README rule 4a).
- ProfileVisibility, SkipLink, NextButton and Atmosphere `form` from `shared.jsx`.

## Open (not blocking)

- **GDPR Art. 9 explicit consent for religion data — owned by legal.** Religion is special-category data. Whether the sign-up terms cover processing it, or the screen needs its own consent line, is not decided here. If a consent line is needed, it is a kit change first, then a re-exported sheet, then an update to this ticket — and it would push the list further below the fold.
- **At 375 × 667 only 3 of 9 rows are fully visible.** Accepted by decision (rows are never shortened). The scroll-indicator flash is the only hint. If the evidence screenshots show users would not find the rest, the fallback is a group-level decision, not a per-screen compact row.
- **Politics still uses `OptionRowCompact` in the kit.** It should follow this decision when it is ticketed, so the two 8+-option screens match. Once it does, `OptionRowCompact` can be deleted.
- **A saved answer cannot be removed in onboarding.** Clearing on a resume and pressing Continue is a Skip, and rule 0 keeps the earlier value. Default: keep it for the MVP; removing belongs to profile editing. Same open point as Profiles 17 and 18.
- **The option set is fixed and Western-weighted** (three Christian denominations listed separately). Kept as decided for the MVP; a market-specific set is a later decision.
- **The deficits are measured from the kit**, not a device. The scroll handles any value; the evidence screenshots show the real ones.
- **The progress-bar count** (10 segments against 9 §2 indices) is still open from Profile 14. It belongs to the group shell; this screen is `current={6}` either way.
