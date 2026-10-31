# [Check-In 05b] Filters v2 — gender checkboxes + dealbreaker switches

**Parent:** [Epic] 48hrs Check-In flow (`checkin/tickets/00-epic.md`) · **Type:** Story (change to a built screen) · **Priority:** High
**Attachments:** `checkin-05b-filters-v2-attachment.zip` (handoff folder for this ticket) · `checkin-05b-filters-v2-spec-sheet.png`
**Changes:** **Check-In 05 Filters (built).** Only what this ticket names changes; everything else stays as built.
**Blocked by:** nothing for the UI. **The dealbreaker switches ship hidden** until the matching ticket applies the soft range (see *Build inventory*).

## Description

Two changes to the built Filters step (`step_id: filters`, step 5 of 6), which is also Search's filter sheet (one store):

1. **Gender reads as multi-select.** Today the four gender chips look like single-choice pills; users don't see they can pick several. Each option gets a **checkbox square**, and the label row says **`Pick all that apply`**.
2. **Dealbreaker switch on Age range and on Distance.** Off (default): the setting is a preference — people just outside it may appear, ranked lower. On: nobody outside it is ever shown. Gender gets no switch; it is always strict.

Three frames on the sheet: **A** both switches off (default) · **B** two genders picked, age a dealbreaker · **C** both dealbreakers on.

Entries, exits, toast, Continue and ✕ are unchanged from Check-In 05.

## Decided before build

Decided 26 Oct 2026 (`matching-plan.md` §1, the matching launch rules).

- **Gender options are `GenderCheckPill`, not `LifePill`** (②, violet). ⚠ Supersedes Check-In 05's rule *"Selected: LifePill selected state. No checkmark and no second indicator."* The square is the platform cue for "pick several"; a plain pill reads as one choice. `LifePill` itself is unchanged and stays in use on Vibe and Profile 22.
- **`Pick all that apply`** sits in the existing hint slot of the Gender row, in the muted style (①). The value hints on Age and Distance keep their violet style.
- **Dealbreaker switch on Age range and Distance only** (④ ⑤ ⑥). **Default off**, for every user, including existing filter records.
- **Off = preference, on = strict.** What "just outside" means (how many years, how many km) is set by matching, not by this screen — the copy never names a number.
- **The switch is `ConsentToggle` from Profile 10**, consumed, never copied.
- **Ships hidden** behind a remote flag until matching applies the soft range. Without it, the off line ("People just outside may appear") would be untrue. The gender change ships immediately.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** The step shell, header block and footer are unchanged from Check-In 05.

- **`DealbreakerRow`** sits inside the Age and Distance filter rows, **margin-top 12** below the slider: flex, align center, gap 12 — text column `flex: 1`, switch `flex: none`.
- **The filter list stays the only flexible element and the only scroll.** The two rows add ≈ 110. Measured: it still fits at **390 × 844**; at **375 × 667** it scrolls inside itself. **Never shrink the sliders, chips, switches or CTA to avoid it.**

📎 All specs are on the attached **checkin-05b-filters-v2-spec-sheet.png**: frames A–C and callouts ①–⑦, all violet (the delta). Anything without a number on this sheet is Check-In 05's ①–⑭, unchanged.

> **Reference implementation:** `design_handoff_showup/checkin/screen-checkin-filters-reference.jsx` — **revised in place 26 Oct 2026**; the header says what changed. Read values from it rather than measuring the PNG. States: `<FiltersScreen/>` (A) · `<FiltersScreen filters={{ gender: ['woman', 'non_binary'], age: [28, 35], dist: 10, ageStrict: true }}/>` (B) · `<FiltersScreen filters={{ gender: ['woman', 'man', 'non_binary'], age: [25, 40], dist: 15, ageStrict: true, distStrict: true }}/>` (C). **Builds** `GenderCheckPill` and `DealbreakerRow`; adds `hintMuted` to `CheckinFilterRow`. **Consumes** `ConsentToggle` from Profile 10 (`profile/screen-reachability-reference.jsx`) and `Icon` (`check`) from `components/shared.jsx`. A second copy of any of these is a bug.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## Copy — final strings

New strings only; everything else is Check-In 05's copy, unchanged.

Gender row hint: `Pick all that apply`
Gender group accessible label: `Gender, pick all that apply`

Under Age range and under How far away?:
- Label: `Dealbreaker`
- Line, switch off: `People just outside may appear, ranked lower.`
- Line, switch on (Age): `Never show anyone outside this age range.`
- Line, switch on (Distance): `Never show anyone outside this distance.`

Switch accessible labels: `Age range is a dealbreaker` · `Distance is a dealbreaker`

## Behaviour

**Gender**
- Unchanged: multi-select, ≥ 1 required, same toast.
- Each option shows a 16 checkbox square: empty when off, a violet tick on white when on. The pill colours are `LifePill`'s.

**Dealbreaker switches**
- A tap toggles the switch and swaps the line immediately. Nothing else on the screen changes.
- Saved with the other settings **on an accepted Continue** — never on the tap, never on ✕. Same rule as the sliders.
- Shared with Search: a switch changed in Search shows here at the next Check-In, and the other way round.
- The switch is independent of the value: an age range of `Any age` with the switch on is valid (it limits nothing).

**Accessibility**
- Each gender option is a checkbox (`role="checkbox"`, `aria-checked`) in a group labelled `Gender, pick all that apply`. The square is decorative (`aria-hidden`); the state is announced from the checkbox.
- Each switch is `role="switch"` with its label above; the line below is read after the state.

## Build inventory

- **Client:** `GenderCheckPill`, `DealbreakerRow`, `CheckinFilterRow` `hintMuted`. Search's filter sheet uses the same components (one store, one screen).
- **Filter settings, server-side:** two new fields on the per-user record, `age_dealbreaker` and `distance_dealbreaker` (bool, default `false`, enums §10). Migrate existing records to `false`.
- **Remote flag `filters_dealbreakers`**, default off. With the flag off the two `DealbreakerRow`s are **not rendered** (hidden, not disabled) and the fields are not sent. Turned on when matching applies the soft range.
- **Matching (separate ticket, not built here):** `*_dealbreaker: true` → hard filter; `false` → soft range, ranked lower. Values live in `matching-plan.md` §1. This screen depends on it only for the flag.

## Acceptance criteria

- [ ] Gender options are `GenderCheckPill`: 16 checkbox square, r 5, off border 1.5 rgba(29,17,41,.32), on white fill with a 12 violet tick; pill values identical to `LifePill`
- [ ] The Gender row shows `Pick all that apply` in 500 / 12 `--liq-fg-muted`; Age and Distance hints are unchanged
- [ ] Each gender option is a checkbox in a group labelled `Gender, pick all that apply`
- [ ] With the flag on: a `DealbreakerRow` under the Age slider and under the Distance slider, **none under Gender**
- [ ] The switch is Profile 10's `ConsentToggle`; no second switch component
- [ ] Both switches default **off** for new and existing users
- [ ] The line swaps between the off and on strings above on each tap
- [ ] Switch state is saved only on an accepted Continue; a switch changed and then ✕ is not stored
- [ ] A switch changed in Search shows here at the next Check-In
- [ ] With the flag off, no `DealbreakerRow` renders and the layout is Check-In 05's plus the gender change
- [ ] The filter list is still the **only element that scrolls**; at 390 × 844 all three filters and both switches fit; at 375 × 667 the list scrolls inside itself; headline, note and CTA never move
- [ ] No absolute Y in the content
- [ ] Copy matches the strings above exactly
- [ ] Tracking: every row below fires with the payload named there, and **no invented event names**
- [ ] **Evidence of done:** screenshots of **A, B and C** at **375 × 667, 390 × 844 and 430 × 932** (nine images), plus **A with the flag off** at 390 × 844. Each shows header, note and CTA fully visible and nothing clipped. Check 375 × 667 first.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **H — 48hr Check-In**). Use them exactly. Do not invent names, and do not restate a payload the registry already defines. Registry **1.4.31**, taxonomy **1.4.23**.

| What | Event | Payload |
|---|---|---|
| Continue accepted | `check_in_filters_changed` | `age_dealbreaker`, `distance_dealbreaker` — **NEW, taxonomy 1.4.23** · `filters_changed` order **extended** |

Every other row in Check-In 05's Tracking section is unchanged.

Rules that are easy to get wrong here:

- **`filters_changed` order is now** `meet_gender,age_range,age_dealbreaker,distance_km,distance_dealbreaker` (enums §10 ids, comma-joined, no spaces). An emitter written against 05 still compiles and never reports a switch change.
- **Send both booleans on every accepted Continue**, changed or not. With the flag off, send `false` for both.
- **A switch tap fires nothing.** Do not invent `dealbreaker_toggled`, `age_dealbreaker_changed` or similar.
- `sensitivity_class` stays **0**.

**Registry changes made for this ticket (1.4.31 / taxonomy 1.4.23):** §10 `age_dealbreaker` **NEW** · §10 `distance_dealbreaker` **NEW** · `check_in_filters_changed` two payload properties **NEW**, `filters_changed` order extended.

**Missing from the current build:** both new properties, and the extended `filters_changed` order.

## Out of scope

The matching logic behind the switches (soft range, ranking). Dealbreakers on any other filter. Any change to Vibe, Profile 22 or `LifePill`.

## Dependencies

- **Check-In 05** (built): this ticket changes it.
- **Profile 10:** `ConsentToggle`.
- **Server:** two fields on the filter-settings record, shared with Search.
- **Matching ticket:** soft range, for turning the flag on.
- **Tracking registry 1.4.31 / taxonomy 1.4.23.**

## Open (not blocking this build)

- **Soft-range values** — how far outside the age range and the distance a profile may be when the switch is off. ✅ Set in **Matching 01** (`matching-01-ranking-ticket.md`, 27 Oct 2026): ±3 years / +25% of the radius, confirmed 27 Oct 2026. Blocks only turning the flag on.
