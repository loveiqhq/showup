# [Profile 21] Interests

**Parent:** Profile creation flow (SHOWUP-TBD — epic `profile/tickets/00-epic.md`) · **Type:** Story · **Priority:** Medium
**Attachments:** `profile-21-interests-attachment.zip` (handoff folder for this ticket) · `profile-21-interests-spec-sheet.png`
**Blocked by:** none for the client. The screen consumes shared chrome only (`shared.jsx`). It does **not** render through `DetailsScaffold` (Profile 14) — see *Decided before build*.

## Description

Step 8 of 10 of **"Share some details"**. One screen, two blocks:

- **How I live** — six groups (Activity level, Diet, Alcohol, Smoking, Rhythm, Social battery). **Single-select per group**, every group optional, **uncapped**.
- **What I'm into** — six groups (Move, Food & Drink, Culture, Cozy at home, Going out, Travel style), 51 chips. **Multi-select across all groups, capped at 10.** Each group shows its first 4 chips and a `Show more`.

The user **can skip the step**. There is **no visibility switch**: everything on this screen is shown on the profile.

Three states in one column: **A** empty (arrival), **B** two How I live answers and six interests, **C** the cap (10 of 10). The sheet adds frame **D**: state B with one group expanded. Only the answer region moves between frames. **There is no refused state.**

Entered from **politics' Continue or Skip**, or by **back from Life**. Exits:

- **Continue** with anything picked → Life (step 9). Saves both blocks.
- **Continue** with nothing picked → Life. **Behaves exactly like Skip.**
- **Skip for now** → Life. Saves nothing.
- **Back chevron** → pops to politics.

## Decided before build

All decided 8 Oct 2026. Every point below matches the kit as drawn.

- **The habits screen is cut.** Its questions moved here: alcohol and smoking are now How I live groups; cannabis and other drugs are no longer asked. `habit_answered`, `enums.json` §5 and the `habits` rows in §1 / §2 / §11 are retired (registry 1.4.16, taxonomy 1.4.7).
- **The group's bar is 10 segments: Interests 8 · Life 9 · Future 10** (callout ③). ⚠ This **supersedes** the 9-segment count written into Profiles 14–20 on 6 Oct 2026. Their open notes are rewritten in place; their ACs already read `steps={10}`. The count is the shell's `SHARE_STEPS_TOTAL` prop, so no screen hard-codes it.
- **No `ProfileVisibility` band.** Interests is fully public — including alcohol and smoking, which were Class 2 with a switch on the habits screen. Tracking keeps them Class 2 (see *Tracking*); display does not hide them — leaving the group unanswered is how a user keeps it off their profile.
- **Continue is never disabled** (callout ⑪). With nothing picked it **behaves exactly like Skip**: no toast, no shake. Same rule as Profiles 17–20. The kit previously greyed Continue out; that is gone.
- **Headline is 30**, the group size (callout ④). The kit drew 28.
- **The 10-cap covers What I'm into only.** How I live never counts toward it, never fades, and the counter ignores it (callouts ⑯ ⑰).
- **No hide option for alcohol and smoking, by decision (8 Oct 2026).** Every How I live group is optional: a user who does not want an answer on their profile leaves the group unanswered. That is the control — no band, no per-group switch.
- **Not `DetailsScaffold`.** The scaffold carries the visibility band and a single answer list; this screen has neither. It is its own column built from the shared chrome. Do not fork the scaffold to fit it.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** StatusBar (54) → AppHeader (52) → content (`flex: 1`, padding `4 24 0`, overflow hidden) → HomeIndicator (28).

**Content column:** StepProgress (mb 22) → headline (mb 10) → InterestsCounter (margin 4 / 0 / 18) → **answer region** (`flex: 1`, `min-height: 0`, `overflow-y: auto`, padding-bottom 12) → footer (`flex: none`, 1px `--liq-border-soft` top, padding-top 12, padding-bottom 22).

**The answer region scrolls on every device.** At 390 × 844 it shows ≈ 437 of ≈ 1673 (all groups collapsed). How I live alone is taller than the region. The header, progress bar, headline, counter and footer **never move** at any scroll position. Chips wrap; never shrink a chip, a gap or the CTA to fit.

⚠ **The only absolutely-positioned element is the Atmosphere backdrop.** No toast on this screen. **No keyboard.**

📎 All specs are on the attached **profile-21-interests-spec-sheet.png**: frames A–D, callouts ①–㉓. Callout ⑨ is **violet**: the region scrolls on every device. The design system has colour and type tokens only and no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/profile/screen-interests-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. States: A `<ScreenProfileInterests/>` · B `initialLifestyle={{ activity: 'Active', diet: 'Flexitarian' }} initialPicked={[6 tags]}` · C six lifestyle answers + 10 tags · frame D is B with `initialExpanded={['move']}`, scrolled — not a state. The chrome appears in this file only so it renders; build it from `shared.jsx`.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **Status bar and home indicator** are stylized mocks. Ship the platform chrome and anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.
- **The scroll indicator** is the platform's. We flash it once on arrival. No fade, chevron or "more" hint of ours.

## Copy — final strings

Header: `Share some details`
Headline: `Show how you live and what you love, so others know if you'd click` — em on **click**, orange wash from the shared `.su-underlined em` rule
Counter: `{N} picked · {M} left` · at the cap `10 of 10 picked`
Block titles: `How I live` · `What I'm into`
Toggle: `Show more` / `Show less`
Skip: `Skip for now` · CTA: `Continue`

**How I live** (group → options, in order):
- Activity level: `Couch-lover` · `Moderately active` · `Active` · `Sport is my life`
- Diet: `Everything` · `Flexitarian` · `Vegetarian` · `Vegan`
- Alcohol: `Never` · `Rarely` · `Socially` · `Regularly`
- Smoking: `No` · `Sometimes` · `Yes`
- Rhythm: `Early bird` · `Night owl`
- Social battery: `Cozy at home` · `Balanced` · `Always out`

**What I'm into:** the six groups and 51 chips in `INTEREST_GROUPS` in the reference file, in that order. Not repeated here so there is one list.

Labels are display strings. What gets stored and tracked is the `enums.json` §1 value for How I live and the §3 `tag_id` for What I'm into — never the label.

## Behaviour

- **On arrival nothing is picked**, unless this is a resume or a back navigation. The region is scrolled to the top, every What I'm into group is collapsed.
- **Flash the platform scroll indicator once on arrival.**
- **How I live:** tapping a chip selects it and deselects any other chip **in the same group**. Tapping the selected chip clears it. Groups are independent. 180 ms transition; nothing else moves.
- **What I'm into:** tapping an unpicked chip picks it, tapping a picked chip unpicks it. The counter and its bar update immediately (bar width 280 ms).
- **At 10 picked:** the counter label and bar turn orange; every unpicked chip fades to .4 and is `aria-disabled`. **A tap on a faded chip does nothing** visible — no toast, no shake. Picked chips still unpick, which un-fades the rest.
- **Show more / Show less** expands or collapses that group in place. The region does not scroll on toggle. A picked chip beyond the first 4 is **hidden while its group is collapsed** — the counter still counts it. See *Open*.
- **No auto-advance.** Only Continue and Skip navigate.
- **Continue with anything picked** (any How I live answer or any interest): persist the How I live answers (§1 values per field) and the interest list (§3 tag_ids), then advance the saved flow position, then navigate to Life. Persist on Continue, never per tap (flow README rule 4a).
- **Continue with nothing picked:** identical to Skip.
- **Skip for now:** advance the flow position and navigate to Life. Save nothing. **Values saved earlier are kept** (README rule 0). No confirmation.
- **Back** (chevron, iOS swipe-back, Android back) pops to politics and discards unsaved picks.
- **Resume, and back from Life,** pre-fill saved answers and interests without animation. Groups open collapsed; the region opens at the top.
- **Accessibility:** each How I live group is a `radiogroup` labelled by its group label; its chips are `radio` with `aria-checked`, and activating the checked one clears it. Each What I'm into chip is a toggle button with `aria-pressed`; faded chips are `aria-disabled`. The counter is a polite live region, announced on change. Show more is a button with `aria-expanded`.

## Build inventory

- **Client:** `InterestChip`, `InterestsCounter`, `ShowMoreToggle`, `InterestGroup`, `CategoryTitle`. One chip component for both blocks; the cap and single-select rules are screen logic, not chip props.
- **Profile entity: six How I live fields** (`activity`, `diet`, `alcohol`, `smoking`, `rhythm`, `social`), each one §1 value or empty, **and an interest list** of up to 10 §3 tag_ids. `enums.json` marks all of them backend *Not built*. Alcohol and smoking follow the Class 2 storage rules. **Until these exist, Continue is blocked and the step does not ship.**
- **No user property** for any of them. A segmentation property is a separate decision.

## Acceptance criteria

- [ ] AppHeader title `Share some details`, back chevron, trailing slot empty. StepProgress `steps={10} current={8}`, from the shell's `SHARE_STEPS_TOTAL`
- [ ] Headline Lora 700 / **30** / 1.12, one em on `click`. No sub copy
- [ ] **No `ProfileVisibility` band** on this screen
- [ ] The screen is not a fork of `DetailsScaffold`; all chrome comes from `shared.jsx`
- [ ] Chips: h 38, pad 0 / 15, pill, Manrope 600 14, 1px `--liq-border`, wrap with gap 8. Selected: #812AEC fill and 1.5px border, white, weight 700. **No element changes Y on selection**
- [ ] How I live: one answer per group, tapping the selected chip clears it, groups are independent, all chips always shown
- [ ] What I'm into: first 4 chips per group + `Show more`; toggling reveals/hides in place and does not scroll the region
- [ ] The counter reads `{N} picked · {M} left` and counts **What I'm into only**
- [ ] At 10: counter label and bar turn #FE6839, unpicked chips fade to .4, a tap on one changes nothing on screen, picked chips still unpick
- [ ] Selecting never navigates. Only Continue and Skip do
- [ ] Footer is `space-between`: `Skip for now` left, `Continue` right. **Continue is never disabled**
- [ ] **Continue with nothing picked behaves exactly like Skip**: no toast, advances to Life, saves nothing
- [ ] Skip advances, saves nothing and **keeps values saved earlier**, with no confirmation
- [ ] Continue with anything picked persists the §1 values and §3 tag_ids, then the flow position, then navigates to Life
- [ ] On all three devices **only the answer region scrolls**; header, progress, headline, counter and footer are fixed and fully visible
- [ ] The platform scroll indicator flashes once on arrival. No fade, chevron or "more" hint is drawn
- [ ] Back pops to politics and discards unsaved picks. A resume or back from Life pre-fills saved picks
- [ ] Radiogroups / toggle buttons / live counter / `aria-expanded` as in *Behaviour*
- [ ] Copy matches the strings above and `INTEREST_GROUPS` exactly
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**. An empty Continue fires the skip event, never `form_validation_failed`
- [ ] **Evidence of done:** screenshots of **A, B and C** at **375 × 667, 390 × 844 and 430 × 932** (nine images), plus **one at 375 × 667 scrolled to the bottom** with one group expanded — ten in total. Each shows all chrome and the footer fully visible, nothing clipped. Check 375 × 667 first.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (families **D — Profile Identity**, **F — Profile Attributes**, and `screen_viewed` in family A). Use them exactly. Do not invent names, and do not restate a payload the registry already defines. Registry **1.4.16**, taxonomy **1.4.7**.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_interests"`, `screen_name: "ProfileInterests"`, `referrer_screen_id` |
| Step shown | `profile_step_viewed` | `step_id: "interests"`, `step_index: 8` (§2) |
| Interest picked | `interest_selected` | `tag_id` (§3), `total_selected` — What I'm into only, after the tap |
| Interest unpicked | `interest_deselected` | `tag_id` (§3), `total_selected` |
| Tap on a faded chip at 10 | `interest_cap_reached` | `cap: 10` — every such tap |
| Show more opened | `show_more_expanded` | `screen_id: "profile_interests"`, `section_id` (§3 section), `revealed_count` |
| Continue with anything picked | `detail_answered` | **one per answered How I live group**: `field_id` ∈ `activity · diet · alcohol · smoking · rhythm · social`, `value_bucketed` — the §1 value |
| Continue with anything picked | `profile_step_completed` | `step_id: "interests"`, `time_on_step_s` |
| Skip, **or Continue with nothing picked** | `profile_step_skipped` | `step_id: "interests"`, `screen_id: "profile_interests"` |

Rules that are easy to get wrong here:

- **Two cadences on one screen, by registry rule.** What I'm into fires **per tap** with a running total (`requirements.json` multi-select rule). How I live fires **once per answered group on an accepted Continue**, like every other detail step. Do not fire `detail_answered` per tap, and do not fire `interest_selected` for How I live chips.
- **An unanswered How I live group sends nothing** — no `detail_skipped`, no empty `detail_answered`. `detail_skipped` does not fire on this screen at all: there is no single `field_id` for the step.
- **Alcohol and smoking are Class 2** (`enums.json` §1), kept from the retired habits row, even though the screen shows them publicly. `sensitivity_class` and `field_registry_version` are stamped at emit time per `design_handoff_showup/CLAUDE.md`.
- **`total_selected` never includes How I live**, and `tag_id` is the §3 slug, never the label.
- **`show_more_expanded` fires on expand only**, not on `Show less`.
- **On an accepted Continue, the `detail_answered` events (in How I live list order) then `profile_step_completed` fire before navigation.** On a skip, `profile_step_skipped` fires before navigation.
- **`referrer_screen_id`** is `profile_politics` going forward, `profile_life_now` on a back from Life — set by the navigation, never hard-coded.

**Events that must NOT fire here.**

- **`habit_answered`** — retired (taxonomy 1.4.7).
- **`field_display_opted_out`** — there is no switch.
- **`detail_skipped`**, `form_validation_failed`, anything per scroll, anything on `Show less`.
- **`profile_build_started`**, which fires once, on screen 01.

**Screen-to-screen conversion.**

| Edge | Numerator | Denominator |
|---|---|---|
| Politics → interests | `screen_viewed` · `screen_id: "profile_interests"` · `referrer_screen_id: "profile_politics"` | `screen_viewed` · `screen_id: "profile_politics"` |
| Interests → Life | `screen_viewed` · `screen_id: "profile_life_now"` · `referrer_screen_id: "profile_interests"` | `screen_viewed` · `screen_id: "profile_interests"` |

The skip rate is `profile_step_skipped` · `step_id: "interests"` over `profile_step_viewed` · `step_id: "interests"`.

**Registry changes made for this ticket (1.4.16):** §1 six new How I live fields, `habits` retired · §2 `habits` retired, `life_ahead` 9 → 10 · §3 rewritten from the kit (51 tag_ids) · §5 retired · §11 `profile_habits` removed, `profile_interests` ticketed. Events 1.4.7: `habit_answered` retired; `interest_*` payload types cite §3.

**Missing from the current build:** every event above is `implemented: false`; the How I live fields and interest list are backend *Not built*.

## Out of scope

Life and Future. Also: free-text or custom interests, search inside the chip list, reordering chips by popularity or locale, interest-based matching or filters (the `lifestyle` filter in §10 is a separate ticket), how interests render on the public profile, editing from Settings after onboarding, and cannabis / other drugs questions (cut with the habits screen).

## Dependencies

- **Profile 20 (Politics)**, the forward origin. **Life** (step 9), the destination.
- **Profile entity How I live fields + interest list**, with Class 2 storage for alcohol and smoking — see *Build inventory*.
- **Tracking registry 1.4.16 / taxonomy 1.4.7.**
- The saved flow position and resume (flow README rule 4a).
- AppHeader, StepProgress, SkipLink, NextButton, Atmosphere `form` from `shared.jsx`.

## Open (not blocking)

- **A picked interest beyond the first 4 is hidden while its group is collapsed** (frame C: `Hiking` is picked but not visible). Acceptable on arrival, since groups open collapsed; on a resume it means saved picks are out of sight. Default for the MVP: keep. Fallback: open any group that holds a picked chip beyond 4.
- **At 375 × 667 the region is very short** (status, header, progress, a three-line headline and the counter sit above it). Rows never shrink; the evidence screenshots show the real height.
- **A saved answer cannot be removed in onboarding.** Clearing everything on a resume and pressing Continue is a Skip, and rule 0 keeps the earlier values. Same open point as Profiles 17–20.
- **Headline sizes across groups** are still open (CLAUDE.md, profile README *Backlog*). This screen takes the details-group size, 30.
