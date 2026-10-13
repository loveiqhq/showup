# [Check-In 05] Filters

**Parent:** [Epic] 48hrs Check-In flow (`checkin/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `checkin-05-filters-attachment.zip` (handoff folder for this ticket) · `checkin-05-filters-spec-sheet.png`
**Blocked by:** **Check-In 03** (`CoachNote`), **Check-In 02** (the step shell, `CheckInFooter`), **Check-In 01** (`CheckInCloseButton`, `CheckInArrow`) and **Profile 22** (`LifePill`). **Blocks:** Search's filter sheet, which edits the same settings and consumes `RangeSlider`.

## Description

Step 5 of 6 in the 48hrs Check-In (`step_id: filters`). The user sets who they want to see: **gender**, **age range** and **distance**. These are the user's filter settings, the same ones Search's filter sheet edits.

**Required:** at least one gender. Age range and distance always have a valid value.

Three frames on the sheet: **A** first Check-In, pre-filled from the profile · **B** every gender deselected, with the toast · **C** narrowed (also how a returning user lands).

Entered from:

- Vibe **Continue** (Check-In 04) → Filters.
- **Back** from Uplift (06) → Filters, with the settings intact.

Exits:

- **Continue** (≥ 1 gender) → Check-In 06 (Uplift).
- **Back** → Vibe, with the picks intact.
- **✕** → the gate (date search not available). **Changes are not saved.**

## Decided before build

All decided 13 Oct 2026. The kit already matches every point below.

- **The Sexuality filter is removed** (violet in the sheet's note). Matching is **two-way on gender**: a user is shown only to people whose own gender filter includes the user's gender. Picking *Men* therefore already means "men who want to meet men". An orientation filter could only hide bisexual and pansexual people, and gave straight users nothing. **Orientation stays on the profile** (Profile 16) and matching uses it. `enums.json` §10 `meet_orientation` is retired.
- **Gender is mandatory** (⑨, violet). At least one; Continue dims at zero and a tap raises `Pick at least one gender to date.`. The kit's "always pre-set" rule is replaced.
- **Age range is optional.** It defaults to the full range, **18 → 65+**, shown as `Any age`. The top stop means **no upper limit**. Continue never depends on it.
- **Distance defaults to 10 km**, adjustable 1–50 km in 1 km steps.
- **One store with Search's filter sheet.** This step edits the user's filter settings; it is not a Check-In-only copy. A returning user sees the current settings, wherever they were last changed.
- **First Check-In pre-fill from the profile** (frame A). Gender is derived from the profile's gender and orientation (both mandatory, so always known):

| Profile | Gender filter pre-filled |
|---|---|
| straight woman / straight man | the other of `man` / `woman` |
| gay or lesbian | own gender |
| bisexual or pansexual (woman or man) | `woman` + `man` |
| gender `non_binary` or `other`, or orientation `other` | all four |

  Age `Any age`, distance `10 km`.
- **Chips are `LifePill`** from Profile 22, with `multi`, as on Vibe. The kit's shared `Chip size="sm"` is gone from this step.
- **No premium filters on this step.** Height, lifestyle, Show-up band and dating intent stay in Search's filter sheet.
- **Slider thumbs get a 44 hit target** (28 visual) and are adjustable for VoiceOver / TalkBack. The track is inset **22** on each side (half the hit box) so a thumb at either end, hit area included, never overflows the list: no sideways pan, no clipped target.

## Layout — read this before building

⚠ **Layout is flex, not absolute.**

**Step shell (from Check-In 02, never copied):** StatusBar (54) → AppHeader (52: back leading, title `Check-in`, `CheckInCloseButton` trailing) → content (`flex: 1`, padding `4 24 0`: StepProgress `steps={6} current={5}` mb 22 → step body `flex: 1` → `CheckInFooter` mb 22) → HomeIndicator (28).

**Step body:** header block (`flex: none`: headline mt 8 → CoachNote mt 14) → **filter list (`flex: 1`, `min-height: 0`, `overflow-y: auto`, mt 18, row gap 22, pb 12)**.

- **The filter list is the only flexible element and the only thing that scrolls.** The headline, note and CTA never move.
- At 390 × 844 the three filters fit. At 375 × 667 the list scrolls inside itself. **Never shrink the sliders, chips or CTA to avoid it.**
- The toast sits inside `CheckInFooter` above the button. Its height comes out of the list.

📎 All specs are on the attached **checkin-05-filters-spec-sheet.png**: frames A–C and callouts ①–⑭. Violet ⑦ ⑨ are the 13 Oct decisions; red ⑬ ⑭ are the failure state. The design system has colour and type tokens only and no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/checkin/screen-checkin-filters-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. States: `<FiltersScreen/>` (A) · `<FiltersScreen filters={{ gender: [], age: [18, 65], dist: 10 }} toast={FILTERS_TOAST_GENDER}/>` (B) · `<FiltersScreen filters={{ gender: ['woman', 'non_binary'], age: [25, 35], dist: 15 }}/>` (C). Builds `FiltersStep`, `CHECKIN_GENDERS`, `checkinGenderDefault`, `CheckinFilterRow`, `RangeSlider` and `SliderThumb`. **Consumes** `CoachNote` from Check-In 03, the step shell and `CheckInFooter` from Check-In 02, `CheckInCloseButton` and `CheckInArrow` from Check-In 01, and `LifePill` from Profile 22 (`profile/screen-life-reference.jsx`). A second copy of any of these is a bug.
> Shell components: `design_handoff_showup/components/shared.jsx` (`StatusBar`, `AppHeader`, `StepProgress`, `Button`, `Icon`, `HomeIndicator`) · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **Status bar and home indicator** are stylized mocks. Ship the platform chrome; anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.

## Copy — final strings

Header: `Check-in` · ✕ accessible label `Close Check-In`
Headline: `Filter who you wish to date` — em on **you wish to date**
Coach note: `This will limit who you will see to potentially date.`

Filters, in this order:

1. `Gender` — chips `Women` · `Men` · `Non-binary` · `Other` (§10 `woman` · `man` · `non_binary` · `other`)
2. `Age range` — value `Any age` (full range) · `30+` (minimum set, no maximum) · `25 – 35` (both set)
3. `How far away?` — value `10 km`

Slider accessible labels: `Minimum age` · `Maximum age` · `Distance in km`
CTA: `Continue` + arrow icon
Toast: `Pick at least one gender to date.`

## Behaviour

**Gender**
- Multi-select. A tap toggles a chip. Any combination of the four is valid; zero is not.
- Selected: `LifePill` selected state. No checkmark and no second indicator.

**Age range**
- Two thumbs over 18–65 in 1-year steps. The thumbs may meet (one age) but never cross: dragging one past the other carries the other with it.
- The top stop is **65+**: a maximum of 65 is stored as **no upper limit**.

**Distance**
- One thumb over 1–50 km in 1 km steps. Measured from where the phone is when the search runs, not from the profile location.

**Continue**
- **Dimmed** (opacity .45, saturate .4) while no gender is selected. **A tap on the dimmed button still registers** and shows the toast for 2600 ms; a repeat tap restarts the timer. It is never a dead button.
- With ≥ 1 gender → Uplift. **The settings are saved server-side on every accepted Continue** as the user's filter settings, the same record Search reads and writes. They also apply to the Check-In committed on `Find my date` (06).

**Re-entry** (epic rules 3 and 5)
- **First Check-In:** pre-filled from the profile (table above), any age, 10 km.
- **Every later Check-In:** the current filter settings, including changes made in Search since the last Check-In.
- **✕** saves nothing. Changes not Continued are lost; the stored settings stand.
- **Back and forward within one flow keep the settings.** Uplift → back shows them as left.

**Accessibility**
- Gender is a group labelled `Gender`; each chip is a checkbox (`LifePill multi`).
- Each thumb is a slider with the label above, its min / max / current value, and ±1 on swipe up / down (VoiceOver adjustable, TalkBack slider).
- The toast is announced as a polite live region.

## Build inventory

- **Client:** `FiltersStep`, `CHECKIN_GENDERS`, `checkinGenderDefault`, `CheckinFilterRow`, `RangeSlider` + `SliderThumb`. Search's filter sheet consumes the slider and the gender list; it does not build its own.
- **Filter settings, server-side:** one record per user (`meet_gender`, `min_age`, `max_age` or null, `distance_km`), shared with Search. Written on each accepted Continue here and on save in Search. Never written on ✕ or on a tap.
- **First-Check-In pre-fill:** `checkinGenderDefault(gender, orientation)` applied once, when the user has no filter settings yet.
- **Matching:** two-way on gender. Not built here, but this step depends on it: without it, removing the sexuality filter would show users people who are not looking for them.

## Acceptance criteria

- [ ] Renders from the Check-In 02 step shell with content props only. StepProgress is `steps={6} current={5}`
- [ ] Headline Lora 700 / **30** / 1.15, one em on **you wish to date**
- [ ] Three filters in the order above: Gender, Age range, How far away? **There is no Sexuality filter**
- [ ] Gender chips are `LifePill` from Profile 22 with `multi`; no second chip component
- [ ] Gender is mandatory: with none selected Continue is dimmed, and a tap shows `Pick at least one gender to date.` for 2600 ms
- [ ] Age range defaults to 18 → 65+ and reads `Any age`; 65 is stored as no upper limit; the thumbs can meet but never cross
- [ ] Distance defaults to 10 km, 1–50 km in 1 km steps
- [ ] A first Check-In pre-fills gender exactly per the table above
- [ ] A later Check-In shows the current filter settings, including a change made in Search since; settings changed and then ✕ (no Continue) are not stored
- [ ] Stored gender values are §10 exactly (`woman` · `man` · `non_binary` · `other`)
- [ ] Each slider thumb has a **44 × 44** hit target and works with VoiceOver / TalkBack adjust. With a thumb at either end, the filter list does **not** scroll sideways
- [ ] ✕ → the gate. Back → Vibe, with the picks intact. Uplift → back → Filters, with the settings intact
- [ ] The filter list is the **only element that scrolls**. At 375 × 667 it scrolls inside itself; headline, note and CTA never move
- [ ] No absolute Y in the content; one `flex: 1` element (the filter list)
- [ ] Gender values appear in **no** tracking payload
- [ ] Copy matches the strings above exactly
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**
- [ ] **Evidence of done:** screenshots of **A, B and C** at **375 × 667, 390 × 844 and 430 × 932** (nine images). Each shows the header, the note and the CTA fully visible and nothing clipped, including both age thumbs at the ends of the track in A; on 375 × 667 state whether the distance row is visible before scrolling. Check 375 × 667 first.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **H — 48hr Check-In**, and `screen_viewed` in family A). Use them exactly. Do not invent names, and do not restate a payload the registry already defines. Registry **1.4.24**, taxonomy **1.4.15**.

| What | Event | Payload |
|---|---|---|
| Filters shown | `screen_viewed` | `screen_id: "checkin_filters"`, `screen_name: "CheckinFilters"`, `referrer_screen_id` — **NEW §11 row** |
| Continue accepted | `check_in_step_answered` | `step_id: "filters"` (§28), `answer_value: "prefilled"` or `"picked"`, `time_to_answer_ms` |
| Continue accepted | `check_in_filters_changed` | `filters_changed`, `min_age`, `max_age`, `distance_km`, `window_id` — **RESPECIFIED, taxonomy 1.4.15 · Class 0** |
| ✕ | `check_in_closed` | `step_id: "filters"` (§28), `window_id` |

Rules that are easy to get wrong here:

- **⚠ `check_in_filters_changed` changed shape and cadence.** The old row had `filter_id` + `value` + `is_premium_filter` and fired on every modification. It now fires **once per accepted Continue**, even when nothing changed. `filter_id`, `value` and `is_premium_filter` are **gone**. An emitter written against the old row still compiles and sends the wrong keys, once per drag.
- **`filters_changed`** names the §10 filter_ids whose value differs from what the step showed on entry, in the order `meet_gender,age_range,distance_km`, comma-joined, no spaces. `"none"` when nothing changed.
- **Gender values are never sent.** `meet_gender` is Class 2 and stays server-side. `filters_changed` may say *that* gender changed, never *to what*.
- **`max_age` is `null` for 65+** (no upper limit), never `65`.
- **`answer_value`** is `"prefilled"` when Continue accepts the shown settings unchanged (`filters_changed: "none"`), and `"picked"` otherwise.
- **"Accepted" means the step moved on.** A dimmed tap fires nothing; the toast is not an event.
- **`time_to_answer_ms`** runs from this screen's `screen_viewed` to the accepted Continue.
- **`referrer_screen_id`** is `checkin_vibe` (forward) or Uplift's screen (back). Set by navigation, never hard-coded.
- **Emit order:** `check_in_step_answered` → `check_in_filters_changed` → navigation → the next `screen_viewed`.
- `sensitivity_class` is **0** on every row.

**Events that must NOT fire here.**

- **Anything per tap or drag.** No event for a chip tap or a slider move. Do not invent `filter_tapped`, `age_range_changed` or `distance_changed`, and do not keep firing `check_in_filters_changed` per change.
- **`filters_relaxed`** (family I). That is a Search event for widening after an empty result, not a Check-In step.
- **`profile_step_viewed` / `profile_step_completed`.** These are not profile steps.
- **`check_in_created` / `check_in_completed`.** The commit is on Uplift (06).

**Screen-to-screen conversion.**

| Edge | Numerator | Denominator |
|---|---|---|
| Vibe → Filters | `screen_viewed` · `checkin_filters` · referrer `checkin_vibe` | `check_in_step_answered` · `step_id: "vibe"` |
| Filters → Uplift | `check_in_step_answered` · `step_id: "filters"` | `screen_viewed` · `checkin_filters` |
| First Check-Ins that change the pre-fill | `check_in_filters_changed` · `filters_changed` contains `meet_gender`, on a first Check-In | all `check_in_filters_changed` on a first Check-In |
| Users who set an age limit | `check_in_filters_changed` · `min_age > 18` or `max_age` not null | all `check_in_filters_changed` |
| Closed on Filters | `check_in_closed` · `step_id: "filters"` | `screen_viewed` · `checkin_filters` |

**Registry changes made for this ticket (1.4.24 / taxonomy 1.4.15):** §10 `meet_gender` values **repaired** (were the run-together `womanmannon_binaryother`) and marked mandatory, with the pre-fill table · §10 `meet_orientation` **RETIRED** · §10 `age_range` (18–65, null = 65+) and `distance_km` (1–50, default 10) specified · §11 `checkin_filters` **NEW** · §28 `filters` confirmed · `check_in_filters_changed` **respecified** (per accepted Continue, Class 0, no gender values) · `check_in_step_answered` trigger extended for `filters`.

**Missing from the current build:** every event above is `implemented: false`. `check_in_filters_changed` is backend *Not built*, so nothing has to be migrated.

## Out of scope

Uplift and the commit (06). Search's filter sheet UI, beyond sharing the store, the slider and the gender list. Premium filters. Two-way gender matching on the server (a dependency, see below). Editing a committed Check-In.

## Dependencies

- **Check-In 03:** `CoachNote`.
- **Check-In 02:** the step shell and `CheckInFooter`.
- **Check-In 01:** `CheckInCloseButton` and `CheckInArrow`.
- **Profile 22:** `LifePill` (with `multi`).
- **Profiles 15 and 16:** gender and orientation, for the first-Check-In pre-fill.
- **Server: one filter-settings record per user**, shared with Search.
- **Server: two-way gender matching.** Removing the sexuality filter relies on it.
- **Location** (Profile 12) for distance from the device.
- **A `window_id`** from the server window opened at profile completion.
- **Tracking registry 1.4.24 / taxonomy 1.4.15.**

## Open (not blocking)

- **No location permission.** If Location is denied or off when the search runs, what distance is measured from (last known location, or the profile's area)? To settle with the Search ticket; it does not change this screen.
