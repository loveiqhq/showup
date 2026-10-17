# [Profile 22] Life

**Parent:** Profile creation flow (SHOWUP-TBD — epic `profile/tickets/00-epic.md`) · **Type:** Story · **Priority:** Medium
**Attachments:** `profile-22-life-attachment.zip` (handoff folder for this ticket) · `profile-22-life-spec-sheet.png`
**Blocked by:** none for the client. The screen consumes shared chrome only (`shared.jsx`). It does **not** render through `DetailsScaffold` (Profile 14). **Blocks Profile 23 (Future)**, which consumes `LifePill`, `CategoryHeader` and the toggle rule built here.

## Description

Step 9 of 10 of **"Share some details"**. One screen, three questions about the user's life today:

- **Partnership situation** — 4 options
- **Family situation** — 5 options, one of them exclusive (`No children`)
- **Living situation** — 4 options

**Multi-select per question**, no limit, every question optional. **The answers are never shown on the profile** — they are used for matching only, so there is no visibility switch. The user **can skip the step**.

Two states in one column: **A** empty (arrival), **B** all three answered. Nothing moves between them except the picked pills. **There is no refused state.**

Entered from **interests' Continue or Skip**, or by **back from Future**. Exits:

- **Continue** with anything picked → Future (step 10). Saves the answered questions.
- **Continue** with nothing picked → Future. **Behaves exactly like Skip.**
- **Skip for now** → Future. Saves nothing.
- **Back chevron** → pops to interests.

## Decided before build

All decided 8 Oct 2026. Every point below matches the kit as drawn.

- **Multi-select per question** (callouts ⑮ ⑯ ⑰). Real lives combine: `Single` + `Divorced`, `I have children` + `Co-parenting` + `Single parent`. The kit previously drew radios; that is gone.
- **One exclusive option: `No children`** (callout ⑨). Picking it **clears the rest of Family**; picking any other Family option **clears `No children`**. Partnership and Living have no exclusive option. The rule lives in one function, `toggleLifePick`, which Future reuses with its own map.
- **`Partnered` / `Married` removed** from Partnership. `Open relationship` covers the partnered case on a dating app; the remaining four are final.
- **Family restructured**: `No children` · `I have children` · `Single parent` · `Co-parenting` · `Empty nester`. The child-count options are gone.
- **Never shown on the profile.** No `ProfileVisibility` band, no per-question switch, and the public profile API never returns these fields. The sub copy says so.
- **Continue is never disabled** (callout ⑫). With nothing picked it **behaves exactly like Skip**: no toast, no shake. Same rule as Profiles 17–21.
- **Headline is 30**, the details-group size (callout ④).
- **Partnership is Class 2** (special-category data — it reveals sexual life). Family and Living are Class 1. See *Tracking* and *Open*.
- **The answer region scrolls on short screens** (callout ⑧). Added to the kit 8 Oct 2026 with this ticket; it previously clipped at 375 × 667.
- **Not `DetailsScaffold`.** The scaffold carries the visibility band and a single answer list; this screen has neither. Do not fork the scaffold to fit it.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** StatusBar (54) → AppHeader (52) → content (`flex: 1`, padding `4 24 0`, overflow hidden) → HomeIndicator (28).

**Content column:** StepProgress (mb 22) → headline (mb 8) → sub → **answer region** (`flex: 1`, `min-height: 0`, `overflow-y: auto`, margin-top 22) → footer row (`flex: none`, margin-top 16, margin-bottom 22, `space-between`).

**Answer region:** three sections, gap 22. Each is `CategoryHeader` (mb 12) → pills wrapping with gap 8. At 390 the three sections are ≈ 434 tall in a region of ≈ 446 — **it fits with ≈ 12 to spare**. At 430 × 932 it fits easily. At 375 × 667 **the region scrolls internally**; header, progress bar, headline, sub and footer never move. Pills are never shrunk or shortened to make it fit.

**Rows wrap by content width.** At 390 Partnership and Living take 2 rows, Family 3. The row count is not fixed; never hard-code it.

⚠ **The only absolutely-positioned element is the Atmosphere backdrop** (the coloured shapes, `form` variant). No toast. **No keyboard.**

📎 All specs are on the attached **profile-22-life-spec-sheet.png**: frames A and B, callouts ①–⑱. The design system has colour and type tokens only and no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/profile/screen-life-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. States: A `<ScreenProfileLife/>` · B `<ScreenProfileLife initialValues={{ partnership: ['Single','Divorced'], family: ['I have children','Co-parenting'], living: ['Living alone'] }}/>`. **Build `LifePill`, `CategoryHeader` and `toggleLifePick` from this file.** The chrome appears here only so it renders; build it from `shared.jsx`.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **Status bar and home indicator** are stylized mocks. Ship the platform chrome and anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.
- **The scroll indicator** on short screens is the platform's. We flash it once on arrival when the region overflows. No fade, chevron or "more" hint of ours.

## Copy — final strings

Header: `Share some details`
Headline: `What fits your current life?` — em on **life**, orange wash from the shared `.su-underlined em` rule
Sub: `Select all that apply. Never shown on your profile. Used to find the right matches.`
Category labels: `Partnership situation` · `Family situation` · `Living situation`
Partnership: `Single` · `Open relationship` · `Ethically non-monogamous` · `Divorced`
Family: `No children` · `I have children` · `Single parent` · `Co-parenting` · `Empty nester`
Living: `Living alone` · `Living with roommates` · `Living with family` · `Living with children`
Skip: `Skip for now` · CTA: `Continue`

Options appear in exactly this order. Labels are display strings; what gets stored and tracked is the `enums.json` §4 value, never the label.

## Behaviour

- **On arrival nothing is picked**, unless this is a resume or a back navigation. The region is scrolled to the top. If it overflows, flash the platform scroll indicator once.
- **Tapping an unpicked pill picks it**; tapping a picked pill unpicks it. Other pills in the question are unaffected — **except** the exclusive rule below. Questions are independent. 180 ms background / colour transition. Nothing else on the screen changes and the region does not scroll.
- **Exclusive rule (Family only):** tapping `No children` replaces every Family pick with `No children` alone. Tapping any other Family pill while `No children` is picked unpicks `No children` and picks the tapped one. No toast, no animation beyond the normal 180 ms.
- **The picked weight (700) widens a pill slightly**, so a row may re-wrap on selection. Accepted; do not fix it with a fixed width box.
- **No auto-advance.** Only Continue and Skip navigate.
- **Continue with anything picked:** persist each answered question as its §4 values **in list order** (not tap order). An unanswered question is saved as empty. Then advance the saved flow position, then navigate to Future. Persist on Continue, never per tap (flow README rule 4a).
- **Continue with nothing picked:** identical to Skip.
- **Skip for now:** advance the flow position and navigate to Future. Save nothing. **Values saved earlier are kept** (README rule 0). No confirmation.
- **Back** (chevron, iOS swipe-back, Android back) pops to interests and discards unsaved picks.
- **Resume, and back from Future,** pre-fill the saved answers without animation, region at the top.
- **Never shown on the profile.** These fields are not part of the public profile payload, the profile preview, or any accordion section. Matching reads them server-side.
- **Accessibility:** each question is a `group` labelled by its category label (`aria-label`). Each pill is a `checkbox` with `aria-checked`. The category icons are decorative (`aria-hidden`). A screen reader reads e.g. "Single, checkbox, checked, Partnership situation". When `No children` clears other picks, announce nothing extra — the changed states are read on focus.
- **CTA press feedback** comes from the shared NextButton. `SkipLink` is the shared one.

## Build inventory

- **Client:** `LifePill`, `CategoryHeader`, `toggleLifePick(prev, questionId, option, exclusiveMap)`. Built here; **Future consumes all three** with its own exclusive map. A second copy is a bug.
- **Profile entity: three matching fields** (`partnership`, `family`, `living`), each a set of §4 values, in list order, possibly empty. `enums.json` §4 marks them backend *Not built*. **Partnership follows the Class 2 storage rules.** **None of the three may be exposed by the public profile endpoint.** Until the fields exist, Continue is blocked and the step does not ship.
- **No user property** for any of them. A segmentation property is a separate decision.

## Acceptance criteria

- [ ] AppHeader title `Share some details`, back chevron, trailing slot empty. StepProgress `steps={10} current={9}`, from the shell's `SHARE_STEPS_TOTAL`
- [ ] Headline Lora 700 / **30** / 1.12, one em on `life`. Sub copy exactly as above
- [ ] **No `ProfileVisibility` band** on this screen, and the screen is not a fork of `DetailsScaffold`
- [ ] Three sections in the order Partnership · Family · Living, gap 22, each with a 28 lavender medallion icon (heart / users / home) and a Manrope 700 15 label
- [ ] Options appear in the order listed in *Copy* on every device and locale
- [ ] Pills: h 34, pad 0 / 14, pill radius, Manrope 500 13.5, 1px `--liq-border`, white, wrap with gap 8. Picked: #812AEC fill and border, white, weight 700, violet shadow, 180 ms
- [ ] Several pills can be picked in each question; tapping a picked pill unpicks it
- [ ] `No children` clears every other Family pick; any other Family pick clears `No children`. Partnership and Living have no exclusive option
- [ ] Picking never navigates. Only Continue and Skip do
- [ ] Footer is `space-between`: `Skip for now` left, `Continue` right. **Continue is never disabled**
- [ ] **Continue with nothing picked behaves exactly like Skip**: no toast, advances to Future, saves nothing
- [ ] Skip advances, saves nothing and **keeps values saved earlier**, with no confirmation
- [ ] Continue with picks persists each question's **§4 values in list order**, then the flow position, then navigates to Future
- [ ] At 390 × 844 and 430 × 932 all three sections are visible with **no scroll**. At 375 × 667 **only the answer region scrolls**; header, progress, headline, sub and footer are fixed and fully visible
- [ ] The three fields **never appear** on the public profile, the profile preview, or any profile API response other than the user's own matching settings
- [ ] Back pops to interests and discards unsaved picks. A resume or back from Future pre-fills saved picks, scrolled to the top
- [ ] Groups / checkboxes / decorative icons as in *Behaviour*
- [ ] Exactly one flexible element (the answer region) and no absolute Y positioning in the content column
- [ ] Copy matches the strings above exactly
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**. Nothing fires per tap. An empty Continue fires the skip event, never `form_validation_failed`
- [ ] **Evidence of done:** screenshots of **A and B** at **375 × 667, 390 × 844 and 430 × 932** (six images), plus **one at 375 × 667 scrolled to the bottom** — seven in total. Each shows all chrome and the footer fully visible, nothing clipped. Check 375 × 667 first.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (families **D — Profile Identity**, **F — Profile Attributes**, and `screen_viewed` in family A). Use them exactly. Do not invent names, and do not restate a payload the registry already defines. Registry **1.4.17**, taxonomy **1.4.8**.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_life_now"`, `screen_name: "ProfileLifeNow"`, `referrer_screen_id` |
| Step shown | `profile_step_viewed` | `step_id: "life_now"`, `step_index: 9` (§2) |
| Continue with anything picked | `life_question_answered` | **one per answered question**: `question_id` ∈ `partnership · family · living` (§4), `value_bucketed` — the picked **§4 values in list order, comma-joined, no spaces**, e.g. `"single,divorced"` — **NEW payload, taxonomy 1.4.8** |
| Continue with anything picked | `profile_step_completed` | `step_id: "life_now"`, `time_on_step_s` |
| Skip, **or Continue with nothing picked** | `profile_step_skipped` | `step_id: "life_now"`, `screen_id: "profile_life_now"` |

Rules that are easy to get wrong here:

- **Once per answered question, on an accepted Continue.** Not per tap, not a running total. A user who picks and unpicks ten times sends at most three events.
- **An unanswered question sends nothing** — no empty `value_bucketed`, no `detail_skipped`. `detail_skipped` does not fire on this screen at all.
- **List order, not tap order.** Divorced-then-Single is `"single,divorced"`. Same order in storage.
- **`answers_complete` is gone** from `life_question_answered` (taxonomy 1.4.8). Nothing shipped; do not send it.
- **Partnership is Class 2** (§4). Family and Living are Class 1. `sensitivity_class` is stamped **per question** at emit time, with `field_registry_version`, per the emitter rules in `design_handoff_showup/CLAUDE.md`.
- **Never `detail_answered`.** Life uses `life_question_answered` with §4 `question_id`s; §1 `field_id`s are not used here.
- **On an accepted Continue, the `life_question_answered` events (partnership → family → living) then `profile_step_completed` fire before navigation.** On a skip, `profile_step_skipped` fires before navigation.
- **`referrer_screen_id`** is `profile_interests` going forward, `profile_life_ahead` on a back from Future — set by the navigation, never hard-coded.

**Events that must NOT fire here.**

- **Anything per tap** — no `option_selected`, `life_option_toggled`, no running total, nothing when `No children` clears other picks.
- **`future_question_answered`** — that is Profile 23.
- **`field_display_opted_out`** — there is no switch.
- **`detail_answered`, `detail_skipped`**, `form_validation_failed`, anything per scroll.
- **`profile_build_started`**, which fires once, on screen 01.

**Screen-to-screen conversion.**

| Edge | Numerator | Denominator |
|---|---|---|
| Interests → Life | `screen_viewed` · `screen_id: "profile_life_now"` · `referrer_screen_id: "profile_interests"` | `screen_viewed` · `screen_id: "profile_interests"` |
| Life → Future | `screen_viewed` · `screen_id: "profile_life_ahead"` · `referrer_screen_id: "profile_life_now"` | `screen_viewed` · `screen_id: "profile_life_now"` |

The skip rate is `profile_step_skipped` · `step_id: "life_now"` over `profile_step_viewed` · `step_id: "life_now"`.

**Registry changes made for this ticket (1.4.17 / taxonomy 1.4.8):** §4 re-slugged from the kit for all six questions (Future rows confirmed with Profile 23), new `exclusive` column, partnership + partnership_ahead Class 2, DAMAGED flag cleared · §11 `profile_life_now` ticketed · `life_question_answered` respecified (per answered question on Continue, `value_bucketed` added, `answers_complete` removed).

**Missing from the current build:** every event above is `implemented: false`; the three fields are backend *Not built*.

## Out of scope

Future (Profile 23). Also: how matching uses these answers, any search filter on them, showing them anywhere on the profile, a free-text or "Other" option, child counts or ages, editing from Settings after onboarding, and a "Prefer not to say" option (leaving a question unanswered is that).

## Dependencies

- **Profile 21 (Interests)**, the forward origin. **Profile 23 (Future)**, the destination and the consumer of `LifePill` / `CategoryHeader` / `toggleLifePick`.
- **Profile entity partnership / family / living sets**, Class 2 storage for partnership, excluded from the public profile — see *Build inventory*.
- **Tracking registry 1.4.17 / taxonomy 1.4.8.**
- The saved flow position and resume (flow README rule 4a).
- AppHeader, StepProgress, SkipLink, NextButton, Atmosphere `form`, Icon from `shared.jsx`.

## Open (not blocking)

- **Art. 9 consent basis for Partnership** — `Open relationship` and `Ethically non-monogamous` reveal sexual life. Owned by legal, same open point as religion (19) and politics (20). The screen ships as drawn; if legal requires explicit consent, it is a separate surface, not a change to this one.
- **A saved answer cannot be removed in onboarding.** Clearing everything on a resume and pressing Continue is a Skip, and rule 0 keeps the earlier values. Same open point as Profiles 17–21.
- **390 × 844 has ≈ 12 px of slack.** A longer locale or a fourth Family row makes the region scroll there too. Acceptable; pills are never shrunk to prevent it.
- **Headline sizes across groups** are still open (CLAUDE.md, profile README *Backlog*). This screen takes the details-group size, 30.
