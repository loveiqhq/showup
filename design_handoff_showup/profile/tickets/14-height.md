# [Profile 14] Height

**Parent:** Profile creation flow (SHOWUP-TBD — epic `profile/tickets/00-epic.md`) · **Type:** Story · **Priority:** Medium
**Attachments:** `profile-14-height-attachment.zip` (handoff folder for this ticket) · `profile-14-height-spec-sheet.png`

## Description

Step 1 of **"Share some details"**, the optional detail steps that follow Embrace 2 (screen 13). One question, one numeric field: the user's height in centimetres. The answer is used for matching; the user can hide it from their profile, and can skip the step.

**This screen builds the group's shell.** Every "Share some details" step renders through one scaffold — header, progress bar, headline, sub copy, answer region, visibility band, footer. Height is the first consumer, so the shell is built here and steps 2–9 reuse it. A second copy of it is a bug.

Three states on one column: **A** empty (arrival), **B** a valid value, **C** Continue refused. Nothing moves between them.

Entered from **Embrace 2's CTA**, or by **back from gender**. Exits:

- **Continue** with a valid value → gender (step 2). Saves height and visibility.
- **Skip for now** → gender. Saves nothing.
- **Back chevron** → pops to Embrace 2.

## Decided before build

- **Height is skippable.** Decided 5 Oct 2026: every screen that shows `Skip for now` can be skipped by the user — a standing rule, recorded in `design_handoff_showup/CLAUDE.md` → *Skip for now — binding*. Skip (⑩) advances without saving; the two Skip tracking rows stand. The `enums.json` §1 note calling education the *"first field with a Skip CTA"* was wrong and is corrected (registry 1.4.9).
- **The group shell is built here** — `DetailsScaffold` in the reference file. Step count is a **prop of the shell** (`SHARE_STEPS_TOTAL`, 10 today), never typed per screen.
- **Headline 30 is this group's size.** One headline size per screen group (decided 5 Oct 2026, `CLAUDE.md`). The size is carried by the scaffold, not a per-screen prop. The cross-group unification ticket is still to be written (`profile/README.md` → *Backlog*).
- **Refusal is a toast, not an inline error card.** "The basics" uses the inline error card (flow README rule 2c); this group uses the dark toast above the footer (callout ⑮) — the same pattern as photos and prompts. What both groups share: **Continue is never disabled.**
- **Label clipping fixed in the kit, 5 Oct 2026.** The answer region's `overflow-y: auto` clipped the field's notched label. The region is now `margin-top 12 + padding-top 10` — the same 22 gap, no Y change. Applies to every step on the scaffold.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** StatusBar (54) → AppHeader (52) → content (`flex: 1`, padding `4 24 0`, overflow hidden) → HomeIndicator (28).

**Content column:** StepProgress (mb 26) → headline (mb 8) → sub → **answer region** (`flex: 1`, `min-height: 0`, margin-top 12, padding-top 10) → ProfileVisibility band (margin-top 6) → footer row (margin-top 26, margin-bottom 22).

**Answer region:** field → note (margin-top 10) → **one `flex: 1` spacer**. Header, progress bar, band and footer never move.

⚠ **The only absolutely-positioned elements are the Atmosphere backdrop and the toast.** The toast is an overlay against the footer row and moves nothing.

**Keyboard open:** the numeric keypad opens on arrival. The content column ends at the keyboard's top edge, so the band and footer ride above the keypad. The spacer absorbs the difference.

📎 All specs are on the attached **profile-14-height-spec-sheet.png** — frames A, B and C, callouts ①–⑯, danger red for state C. The design system has colour and type tokens only — there is no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/profile/screen-height-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. States: A `<ScreenProfileHeight initialValue=""/>` · B `<ScreenProfileHeight initialValue="175"/>` · C `<ScreenProfileHeight initialValue="" initialHint={true}/>`. `initialHint` exists **for the spec sheet only**; in the app the toast appears only from a Continue press.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **The keyboard.** Request the platform numeric keypad (`inputMode="numeric"` / iOS `.numberPad` / Android `TYPE_CLASS_NUMBER`). Never spec its height and never build a mock. The iOS number pad has **no return key**, so Continue is the only submit.
- **Status bar and home indicator** — stylized mocks in the reference file. Ship the platform chrome and anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.

## Copy — final strings

Header: `Share some details`
Headline: `How tall are you?` — em on **tall**, orange wash from the shared `.su-underlined em` rule
Sub: `Used to find the right matches.`
Field label: `Enter height in cm`
Placeholder: `e.g. 175`
Note: `Be honest — it helps us find the right matches.`
Visibility: `Don't display on my profile`
Skip: `Skip for now`
CTA: `Continue`
Toast: `Enter a height between 120 and 230 cm to continue`

The toast is **one string** for both the empty and the out-of-range refusal. It has no closing full stop, as drawn.

## Behaviour

- **On arrival the field is focused** and the numeric keypad is open. The user can type without tapping.
- **Input keeps digits only, max 3.** Any other character is dropped as it is typed, and pasted text is filtered the same way. No decimals, no unit suffix, no inch toggle.
- **Valid = a whole number from 120 to 230 inclusive.** When the value is valid, the field shows the success tick (⑭). Nothing else changes.
- **Continue with a valid value:** persist `height_cm` (integer) and the visibility flag, then advance the saved flow position, then navigate to gender. Persist on Continue, before navigating — never per keystroke (flow README rule 4a).
- **Continue with an empty or out-of-range value:** show the toast. It fades in over 200 ms and auto-hides at 2600 ms. Nothing is persisted and there is no navigation. A second press restarts the timer. The toast hides as soon as the value becomes valid. There is no shake, the field is not recoloured, and focus stays in the field. The toast text is announced (`aria-live="polite"`).
- **Skip for now** advances the flow position and goes to gender. **It writes no height, even if a valid value is typed.** A value saved earlier (from a resume or back navigation) is left alone — Skip does not delete.
- **Visibility band:** unchecked by default. When checked, the height is **not shown on the profile but is still used for matching**. It is saved with Continue and ignored on Skip. The hit area is the box and label only, never the CTA's x-range.
- **Back** pops to Embrace 2. **The confetti does not replay** — that is a pop, not a push (Profile 13 → *Behaviour*). An unsaved typed value is discarded.
- **Resume:** a relaunch onto height pre-fills the saved value (state B) and the saved visibility. Nothing is announced, and there is no toast on arrival.
- **CTA press feedback** is the shared NextButton's. The label and circle form one hit area.

## Build inventory

- **Profile entity: height field + hidden flag.** `enums.json` §1 marks `height` as backend *Not built* (no field on the profile entity yet). This blocks Continue. Until it exists, the step cannot save, so it does not ship.
- **On-device bucketing** for `value_bucketed` (§1 buckets). The exact cm never goes to analytics.

## Acceptance criteria

- [ ] The screen renders through **`DetailsScaffold`**, built once in this ticket, with the step count as a prop — steps 2–9 consume it
- [ ] AppHeader title `Share some details`, **back chevron** in the leading slot, trailing slot empty; StepProgress `steps={10} current={1}`, mb 26
- [ ] Headline Lora 700 / **30** / 1.12 / −0.015em, one em on `tall`, **from the scaffold**
- [ ] Field is the shared FloatingField: h 64, radius 14, label `Enter height in cm`, placeholder `e.g. 175`; **the notched label is not clipped** at any size
- [ ] Focused on arrival with the **numeric keypad** open; band and footer stay fully visible above the keypad
- [ ] Input accepts digits only, max 3, including on paste
- [ ] The success tick shows only for an integer 120–230 inclusive
- [ ] **Continue is never disabled.** On an empty or out-of-range value it shows the toast (⑮) for 2600 ms, with no navigation and no persistence; the toast hides as soon as the value becomes valid
- [ ] The toast is an overlay: **no element in the column changes Y** between A, B and C
- [ ] Continue with a valid value persists `height_cm` and the visibility flag, then the flow position, then navigates to gender
- [ ] **Skip writes no height**, even with a valid value typed, and goes to gender
- [ ] Visibility unchecked by default; checked hides the height on the profile and keeps it in matching
- [ ] Back pops to Embrace 2 with **no confetti replay**
- [ ] A resume onto height pre-fills the saved value and visibility, with no toast
- [ ] Exactly one `flex: 1` spacer; no absolute Y positioning in the content column
- [ ] Copy matches the strings above exactly
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**; `form_validation_failed` fires on the refused press, not on render
- [ ] **Evidence of done:** screenshots of **all three states, keypad open,** at **375 × 667, 390 × 844 and 430 × 932** — nine images. All content visible, nothing clipped, no scroll, CTA and visibility band fully visible above the keypad. Check 375 × 667 first, and state what the spacer resolved to there.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (families **D — Profile Identity**, **F — Profile Attributes**, **T — Errors**; `screen_viewed` in family A). Use them exactly — do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_height"`, `screen_name: "ProfileHeight"`, `referrer_screen_id` |
| Step shown | `profile_step_viewed` | `step_id: "height"`, `step_index: 1` (§2) |
| Continue accepted | `detail_answered` | `field_id: "height"`, `value_bucketed` — §1 bucket, computed on device, **never the raw cm** |
| Continue accepted | `profile_step_completed` | `step_id: "height"`, `time_on_step_s` |
| Continue refused — empty | `form_validation_failed` | `field_id: "height"`, `rule: "required_missing"`, `screen_id: "profile_height"`, `step_id: "height"` |
| Continue refused — out of range | `form_validation_failed` | same, `rule: "impossible"` (§8) |
| Skip for now | `profile_step_skipped` | `step_id: "height"`, `screen_id: "profile_height"` |
| Skip for now | `detail_skipped` | `field_id: "height"` |
| Visibility box ticked | `field_display_opted_out` | `field_id: "height"` — **on tick only**, no event on untick |

Rules that are easy to get wrong here:

- **`referrer_screen_id` is the screen the user actually came from** — `profile_embrace_details` from Embrace 2, `profile_gender` on a back from gender. Set by the navigation, never hard-coded.
- **On Continue, `detail_answered` and `profile_step_completed` fire before navigation**, in that order, and only for an accepted press.
- **Out of range is `rule: "impossible"`.** §8 has no range value. If analytics wants `out_of_range`, it gets defined in `enums.json` first — not typed here.
- Every attribute event carries `sensitivity_class` and `field_registry_version`, stamped at emit time — see the emitter rules in `design_handoff_showup/CLAUDE.md`.

**Events that must NOT fire here.**

- **`embrace_bridge_viewed`** — that is screen 13's; a back-pop onto 13 fires `screen_viewed` there and nothing else.
- **`profile_build_started`** — fires once, on screen 01.
- **Anything for the toast itself, a keystroke or the tick.** `form_validation_failed` already records the refusal.

**Screen-to-screen conversion.** Each screen's `screen_viewed` carries the previous screen's id in `referrer_screen_id`, so every edge is a ratio of two counts.

| Edge | Numerator | Denominator |
|---|---|---|
| Embrace 2 → height | `screen_viewed` · `screen_id: "profile_height"` · `referrer_screen_id: "profile_embrace_details"` | `screen_viewed` · `screen_id: "profile_embrace_details"` |
| Height → gender | `screen_viewed` · `screen_id: "profile_gender"` · `referrer_screen_id: "profile_height"` | `screen_viewed` · `screen_id: "profile_height"` |

The `profile_height` §11 row was registered 5 Oct 2026 (registry 1.4.8) and is marked ticketed by this ticket (registry 1.4.9).

**Missing from the current build:** every event above is `implemented: false`; `detail_answered` and `detail_skipped` are backend *Partial*.

## Out of scope

Gender and the later "Share some details" steps (beyond consuming the shell), an inch / feet toggle or locale-based units, editing height from Settings after onboarding, and how height shows on the public profile.

## Dependencies

- **Embrace 2 (screen 13)** — the only forward origin. Back from gender is the other way in.
- **Gender** (step 2) — the only destination
- **Profile entity height field + hidden flag** — see *Build inventory*
- The saved flow position and resume (flow README rule 4a)
- Shared FloatingField (screen 01), ProfileVisibility, SkipLink, NextButton, Atmosphere `form`

## Open (not blocking)

- **375 × 667 with the keypad is tight.** By estimate the fixed parts need ≈430 of the ≈431 available above the iOS number pad, so the spacer is near zero. The answer region must not scroll internally. If it overflows, shorten the note before shrinking anything in the layout.
- **The kit has no keypad-open frame.** The sheet shows the screen without a keyboard. A keypad-open artboard would make the 375 × 667 check reviewable by eye.
- **"find the right matches" appears twice** — once in the sub copy and once in the note. The copy is final as drawn; flagging it for a copy pass.
- **The toast can sit over the visibility band** (frame C). It disappears after 2600 ms and moves nothing, but while it shows it covers the privacy control. This pattern is shared by the whole group.
- **Progress-bar count:** the shell uses 10 segments, while §2 step indices end at 9 and the kit's life screen uses 8. Owned by the group shell; this screen is `current={1}` either way.
- **Skip fires two events** (`profile_step_skipped` in D, `detail_skipped` in F) for one act. Both are in the registry with overlapping triggers. Fire both until the registry retires one.
