# [Profile 23] Future

**Parent:** Profile creation flow (SHOWUP-TBD — epic `profile/tickets/00-epic.md`) · **Type:** Story · **Priority:** Medium
**Attachments:** `profile-23-future-attachment.zip` (handoff folder for this ticket) · `profile-23-future-spec-sheet.png`
**Blocked by:** **Profile 22 (Life)**, which builds `LifePill`, `CategoryHeader` and `toggleLifePick`. This screen consumes all three; a second copy is a bug.

## Description

Step 10 of 10 of **"Share some details"** — **the last step of profile creation**. One screen, three questions about the life the user imagines:

- **Family vision** — 3 options, **one answer at most**
- **Partnership vision** — 4 options
- **Living style vision** — 4 options

**Multi-select per question** (Family excepted), no limit, every question optional. Each question has a relational answer, `I want to find out together`, which stands alone. **The answers are never shown on the profile** — matching only, so there is no visibility switch. The user **can skip the step**.

Two states in one column: **A** empty (arrival), **B** all three answered. The sheet adds frame **C**: state B with the answer region scrolled to the bottom. Only the picked pills change between states. **There is no refused state.**

Entered from **Life's Continue or Skip** only. Exits:

- **Continue** with anything picked → **48hrs Check-In intro**. Saves the answered questions. Profile creation is complete.
- **Continue** with nothing picked → Check-In intro. **Behaves exactly like Skip.**
- **Skip for now** → Check-In intro. Saves nothing.
- **Back chevron** → pops to Life.

## Decided before build

All decided 8 Oct 2026. Every point below matches the kit as drawn.

- **Same column, pill and toggle as Life** (callouts ① ⑥ ⑦). Future passes its own `FUTURE_EXCLUSIVE_OPTIONS` to the shared `toggleLifePick`; nothing else differs.
- **Contradictory answers clear each other** (callout ⑨). All three Family options are exclusive: `I want kids` and `I don't want kids` cannot both hold, and either one replaces `I want to find out together`. Family is effectively single-select, but **tapping the picked option clears it**.
- **`I want to find out together` is exclusive** in Partnership and Living (callouts ⑩ ⑪): picking it clears the rest of its question; any other pick clears it.
- **The other options combine.** `Stay local` + `Move abroad`, `Monogamous relationship` + `Open relationship` can be held together — read as "open to either". See *Open*.
- **Typo fixed:** `Expat live` → `Expat life`. `Ethically non-monogamous` matches Life.
- **Never shown on the profile.** No `ProfileVisibility` band; the public profile API never returns these fields. Same sub copy as Life.
- **Continue is never disabled** (callout ⑬). Empty = Skip. **The label stays `Continue`** on the last step — no "Finish", no sunset gradient.
- **Exit is the 48hrs Check-In intro** (callouts ⑬ ⑱), for Continue and Skip alike. The intro has no back control, so Future is never re-entered from it.
- **Partnership vision is Class 2.** Family and Living are Class 1.
- **The answer region scrolls at 390 × 844** (callouts ⑧ ⑲, violet). Partnership wraps one pill per row (4 rows), so the content is ≈ 30 taller than the region. Added to the kit 8 Oct 2026 with this ticket; pills and gaps are never tightened to avoid it.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** The Life column from Profile 22, unchanged: StatusBar (54) → AppHeader (52) → content (`flex: 1`, padding `4 24 0`, overflow hidden) → HomeIndicator (28).

**Content column:** StepProgress (mb 22) → headline (mb 8) → sub → **answer region** (`flex: 1`, `min-height: 0`, `overflow-y: auto`, margin-top 22) → footer row (`flex: none`, margin-top 16, margin-bottom 22, `space-between`).

**Answer region:** Family · Partnership · Living, gap 22; each `CategoryHeader` (mb 12) → pills wrapping with gap 8. At 390 the sections are ≈ 476 tall in a region of ≈ 446, so **the region scrolls by ≈ 30** and Living's last row starts below the fold (frame A). At 375 × 667 it scrolls further. At 430 × 932 it fits. Header, progress bar, headline, sub and footer never move.

**Rows wrap by content width.** At 390 Family and Living take 2 rows, Partnership 4. Never hard-code the row count.

⚠ **The only absolutely-positioned element is the Atmosphere backdrop.** No toast. **No keyboard.**

📎 All specs are on the attached **profile-23-future-spec-sheet.png**: frames A–C, callouts ①–⑲. Callouts ⑧ and ⑲ are **violet**: the region scrolls at 390 × 844. The design system has colour and type tokens only and no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/profile/screen-future-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. States: A `<ScreenProfileFuture/>` · B `<ScreenProfileFuture initialValues={{ family: ['I want kids'], partnership: ['Monogamous relationship'], living: ['Stay local','Move abroad'] }}/>` · frame C is B scrolled to the bottom — not a state. **`LifePill`, `CategoryHeader` and `toggleLifePick` come from Profile 22** (`screen-life-reference.jsx`); this file adds only the Future data and exclusive map.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **Status bar and home indicator** are stylized mocks. Ship the platform chrome and anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.
- **The scroll indicator** is the platform's. We flash it once on arrival. No fade, chevron or "more" hint of ours.

## Copy — final strings

Header: `Share some details`
Headline: `What do you imagine for your future life?` — em on **life**, orange wash from the shared `.su-underlined em` rule
Sub: `Select all that apply. Never shown on your profile. Used to find the right matches.`
Category labels: `Family vision` · `Partnership vision` · `Living style vision`
Family: `I want kids` · `I don't want kids` · `I want to find out together`
Partnership: `Monogamous relationship` · `Open relationship` · `Ethically non-monogamous` · `I want to find out together`
Living: `Stay local` · `Expat life` · `Move abroad` · `I want to find out together`
Skip: `Skip for now` · CTA: `Continue`

Categories and options appear in exactly this order — note **Family comes first** here, unlike Life. Labels are display strings; what gets stored and tracked is the `enums.json` §4 value, never the label.

## Behaviour

- **On arrival nothing is picked**, unless this is a resume or a back navigation. The region is scrolled to the top. **Flash the platform scroll indicator once** (it overflows on 390 and 375).
- **Tapping an unpicked pill picks it; tapping a picked pill unpicks it.** Questions are independent. 180 ms transition. The region does not scroll on a tap.
- **Family:** picking any option replaces whatever Family option was picked. Tapping the picked one leaves Family empty.
- **Partnership and Living:** `I want to find out together` replaces every other pick in its question; any other pick unpicks `I want to find out together`. The remaining three combine freely.
- **The picked weight (700) widens a pill slightly**, so a row may re-wrap. Accepted.
- **No auto-advance.** Only Continue and Skip navigate.
- **Continue with anything picked:** persist each answered question as its §4 values **in list order**. Then mark the saved flow position as **profile creation complete**, then navigate to the Check-In intro. Persist on Continue, never per tap (flow README rule 4a).
- **Continue with nothing picked:** identical to Skip.
- **Skip for now:** mark profile creation complete and navigate to the Check-In intro. Save nothing. **Values saved earlier are kept** (README rule 0). No confirmation.
- **Back** (chevron, iOS swipe-back, Android back) pops to Life and discards unsaved picks.
- **Resume** pre-fills saved answers without animation, region at the top. A user who killed the app on this screen resumes here, not on the Check-In intro.
- **Never shown on the profile.** Not in the public profile payload, the preview, or any accordion section. Matching reads them server-side.
- **Accessibility:** each question is a `group` labelled by its category label; each pill a `checkbox` with `aria-checked`. Icons are decorative. When an exclusive pick clears others, announce nothing extra.

## Build inventory

- **Client:** nothing new. `FUTURE_CATEGORIES` and `FUTURE_EXCLUSIVE_OPTIONS` are data for the Profile 22 components.
- **Profile entity: three matching fields** (`family_ahead`, `partnership_ahead`, `living_ahead`), each a set of §4 values in list order, possibly empty. Backend *Not built*. **`partnership_ahead` follows the Class 2 storage rules.** **None may be exposed by the public profile endpoint.**
- **Profile-creation-complete flag** on the flow position, set by this screen's Continue or Skip. Until it and the three fields exist, Continue is blocked and the step does not ship.
- **No user property** for any of them.

## Acceptance criteria

- [ ] The screen consumes **`LifePill`, `CategoryHeader` and `toggleLifePick` from Profile 22** — no copy, no fork
- [ ] AppHeader title `Share some details`, back chevron, trailing slot empty. StepProgress `steps={10} current={10}`, from the shell's `SHARE_STEPS_TOTAL`
- [ ] Headline Lora 700 / **30** / 1.12, two lines at 390, one em on `life`. Sub copy exactly as above
- [ ] **No `ProfileVisibility` band**
- [ ] Sections in the order **Family · Partnership · Living**, icons users / heart / compass
- [ ] Options in the order listed in *Copy* on every device and locale
- [ ] Family holds **one answer at most**; picking another replaces it; tapping the picked one clears it
- [ ] In Partnership and Living, `I want to find out together` clears the other picks and is cleared by any other pick; the other three combine
- [ ] Picking never navigates. Only Continue and Skip do
- [ ] Footer `space-between`: `Skip for now` left, `Continue` right. **Continue is never disabled** and its label is `Continue`
- [ ] **Continue with nothing picked behaves exactly like Skip**
- [ ] Skip saves nothing, **keeps values saved earlier**, completes profile creation and goes to the Check-In intro, with no confirmation
- [ ] Continue with picks persists each question's **§4 values in list order**, then the profile-complete flow position, then navigates to the Check-In intro
- [ ] At 390 × 844 and 375 × 667 **only the answer region scrolls**; header, progress, headline, sub and footer are fixed and fully visible. At 430 × 932 nothing scrolls. The scroll indicator flashes once on arrival
- [ ] The three fields **never appear** on the public profile, the preview, or any profile API response other than the user's own matching settings
- [ ] Back pops to Life and discards unsaved picks. A resume on this screen lands here, pre-filled, scrolled to the top
- [ ] Exactly one flexible element (the answer region) and no absolute Y positioning in the content column
- [ ] Copy matches the strings above exactly
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**. Nothing fires per tap. An empty Continue fires the skip event
- [ ] **Evidence of done:** screenshots of **A and B** at **375 × 667, 390 × 844 and 430 × 932** (six images), plus **one at 390 × 844 scrolled to the bottom** (frame C) — seven in total. Each shows all chrome and the footer fully visible, nothing clipped. Check 375 × 667 first.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (families **D — Profile Identity**, **F — Profile Attributes**, and `screen_viewed` in family A). Use them exactly. Do not invent names, and do not restate a payload the registry already defines. Registry **1.4.18**, taxonomy **1.4.9**.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_life_ahead"`, `screen_name: "ProfileLifeAhead"`, `referrer_screen_id` |
| Step shown | `profile_step_viewed` | `step_id: "life_ahead"`, `step_index: 10` (§2) |
| Continue with anything picked | `future_question_answered` | **one per answered question**: `question_id` ∈ `family_ahead · partnership_ahead · living_ahead` (§4), `value_bucketed` — the picked **§4 values in list order, comma-joined, no spaces**, e.g. `"stay_local,move_abroad"` — **NEW payload, taxonomy 1.4.9** |
| Continue with anything picked | `profile_step_completed` | `step_id: "life_ahead"`, `time_on_step_s` |
| Skip, **or Continue with nothing picked** | `profile_step_skipped` | `step_id: "life_ahead"`, `screen_id: "profile_life_ahead"` |

Rules that are easy to get wrong here:

- **Once per answered question, on an accepted Continue.** Not per tap, not a running total. An unanswered question sends nothing.
- **`_ahead` ids, always.** `family`, `partnership` and `living` without the suffix are Life's (Profile 22). Mixing them merges present and future answers in every table keyed on question — the collision fixed in `requirements.json` decision 12.
- **Emit order is screen order:** family_ahead → partnership_ahead → living_ahead, then `profile_step_completed`, all before navigation. On a skip, `profile_step_skipped` fires before navigation.
- **`partnership_ahead` is Class 2** (§4); the other two Class 1. `sensitivity_class` is stamped **per question** at emit time, with `field_registry_version`, per `design_handoff_showup/CLAUDE.md`.
- **`referrer_screen_id`** is `profile_life_now` — the only origin. Set by the navigation, never hard-coded.
- **No profile-complete event.** None exists in the registry, and this ticket does not invent one. The end of profile creation is measured by the conversion edge below. See *Open*.

**Events that must NOT fire here.**

- **Anything per tap**, and nothing when an exclusive pick clears others.
- **`life_question_answered`** — that is Profile 22.
- **`detail_answered`, `detail_skipped`, `field_display_opted_out`**, `form_validation_failed`, anything per scroll.
- **`checkin_started`** — that is the Check-In intro's own CTA, not this screen's Continue.
- **`profile_build_started`**, which fires once, on screen 01. No `profile_build_completed` or similar.

**Screen-to-screen conversion.**

| Edge | Numerator | Denominator |
|---|---|---|
| Life → Future | `screen_viewed` · `screen_id: "profile_life_ahead"` · `referrer_screen_id: "profile_life_now"` | `screen_viewed` · `screen_id: "profile_life_now"` |
| Future → Check-In intro (end of profile creation) | `screen_viewed` · `screen_id: "checkin_intro"` · `referrer_screen_id: "profile_life_ahead"` | `screen_viewed` · `screen_id: "profile_life_ahead"` |

The skip rate is `profile_step_skipped` · `step_id: "life_ahead"` over `profile_step_viewed` · `step_id: "life_ahead"`.

**Registry changes made for this ticket (1.4.18 / taxonomy 1.4.9):** §4 `_ahead` rows confirmed (re-slugged in 1.4.17, unchanged) · §11 `profile_life_ahead` ticketed · §11 **`checkin_intro` / `CheckinIntro` NEW** — registered so the exit edge resolves; its own events come with its ticket · `future_question_answered` respecified (per answered question on Continue, `value_bucketed` added, per-question class).

**Missing from the current build:** every event above is `implemented: false`; the three fields and the profile-complete flag are backend *Not built*. The Check-In intro does not fire `screen_viewed` with `checkin_intro` until its own ticket ships — **until then the exit edge reads zero.**

## Out of scope

Life (Profile 22) and the Check-In flow. Also: how matching uses these answers, any search filter on them, showing them on the profile, a free-text or "Other" option, timelines ("kids within 2 years"), editing from Settings after onboarding, a "Prefer not to say" option, and any end-of-profile celebration screen.

## Dependencies

- **Profile 22 (Life)** — the only origin, and the builder of `LifePill` / `CategoryHeader` / `toggleLifePick`.
- **48hrs Check-In intro** — the destination; its `checkin_intro` screen view closes the profile funnel.
- **Profile entity `_ahead` sets** (Class 2 for partnership) and the **profile-complete flag** — see *Build inventory*.
- **Tracking registry 1.4.18 / taxonomy 1.4.9.**
- The saved flow position and resume (flow README rule 4a).

## Open (not blocking)

- **Combinable options that read as opposites.** `Stay local` + `Move abroad` and `Monogamous relationship` + `Open relationship` can both be held, by decision ("open to either"); only Family's pair is treated as contradictory. Default: ship as drawn. If matching needs them exclusive, it is one line in `FUTURE_EXCLUSIVE_OPTIONS`.
- **Partnership wraps to one pill per row at 390**, which is why the region scrolls there. Shortening `Monogamous relationship` to `Monogamous` would pair it with `Open relationship`, save a row and make 390 fit. Copy change, not taken; the kit is drawn with the long label.
- **Art. 9 consent basis for Partnership vision** — owned by legal, same as Life, religion and politics.
- **No profile-complete event.** The end of profile creation is a conversion edge, which depends on the Check-In ticket emitting `checkin_intro`. If the funnel needs its own completion event, define it in `events.json` first (family D, sibling of `profile_build_started`), then add it here.
- **A saved answer cannot be removed in onboarding.** Same open point as Profiles 17–22.
