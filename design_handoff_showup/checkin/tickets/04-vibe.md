# [Check-In 04] Vibe

**Parent:** [Epic] 48hrs Check-In flow (`checkin/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `checkin-04-vibe-attachment.zip` (handoff folder for this ticket) · `checkin-04-vibe-spec-sheet.png`
**Blocked by:** **Check-In 03** (`CoachNote`), **Check-In 02** (the step shell, `CheckInFooter`), **Check-In 01** (`CheckInCloseButton`, `CheckInArrow`) and **Profile 22** (`LifePill`). **Blocks:** nothing structurally. Search's *vibe today* card reads the §31 labels defined here.

## Description

Step 4 of 6 in the 48hrs Check-In (`step_id: vibe`). The user picks **one to five** vibes from twenty, in three groups: what they want to do, how they feel, and their energy and pacing. The vibes are shown on their profile while the Check-In is active.

**Required:** at least one. **Multi-select, at most five**, across all groups.

Four frames on the sheet: **A** first Check-In, nothing selected · **B** empty, with the toast · **C** three vibes picked (also how a returning user lands) · **D** five picked, a sixth tapped, list scrolled to the end.

Entered from:

- Motivation **Continue** (Check-In 03) → Vibe.
- **Back** from Filters (05) → Vibe, with the selection intact.

Exits:

- **Continue** (one or more selected) → Check-In 05 (Filters).
- **Back** → Motivation, with the selection intact.
- **✕** → the gate (date search not available). **The selection is not saved.**

## Decided before build

All decided 13 Oct 2026. The kit already matches every point below.

- **Maximum five** (callout ⑪, violet). The kit's comments said three; the code and copy said five. Five wins. ⚠ *The epic's story table said "max 3" — superseded, see the epic.*
- **A tap past the maximum explains itself** (⑭, red). At 5/5 a tap on an idle chip **selects nothing** and raises `You can pick up to 5 vibes.`. Chips are **never locked, dimmed or disabled**; the kit's `opacity .4` / `not-allowed` state is deleted. The counter turning violet at 5/5 is the only visual change.
- **Only the chip groups scroll** (⑧, violet). Headline, note, counter and CTA stay fixed, as on Motivation. The groups scroll **on every device**: the kit artboard had been drawn 1080 tall to hide it.
- **Chips are `LifePill`** from Profile 22, with `multi`, **as built**. No new chip component and no new props. The kit's hand-rolled pill is gone.
- **Stored values are a new value set, `enums.json` §31 `check_in_vibe`**: 20 snake_case values in three groups (`activity` · `headspace` · `pacing`), in list order. The kit's punctuation-stripped ids (`funplayful`, `coffeeachat` …) are gone. Nothing had shipped, so nothing migrates.
- **One vocabulary.** Search's *vibe today* card and Instant show §31 labels and no others. The kit's display strings (`Grab a bite`, `Walk & talk`, `Coffee and talk` …) have been replaced. Building those surfaces is **out of scope here**; using the §31 labels when they are built is not optional.
- **Class 0.** No value discloses a special category, so the vibes can travel on their own event without restriction.
- **The option list stays as it is.** All 20 labels, including the slash pairs, `Quick 30-min hello` and `Ready to meet now`, are final.
- **Pre-selection** (epic rule 3). A first Check-In starts empty (A). Every later Check-In pre-selects the **last accepted set**, saved on each accepted Continue, whether or not that flow was later closed with ✕ or committed. No message mentions it.

## Layout — read this before building

⚠ **Layout is flex, not absolute.**

**Step shell (from Check-In 02, never copied):** StatusBar (54) → AppHeader (52: back leading, title `Check-in`, `CheckInCloseButton` trailing) → content (`flex: 1`, padding `4 24 0`: StepProgress `steps={6} current={4}` mb 22 → step body `flex: 1` → `CheckInFooter` mb 22) → HomeIndicator (28).

**Step body:** header block (`flex: none`: headline mt 8 → CoachNote mt 14 → counter row mt 14) → **chip groups (`flex: 1`, `min-height: 0`, `overflow-y: auto`, mt 12, group gap 18, pb 12)**.

- **The chip groups are the only flexible element and the only thing that scrolls.** The headline, note, counter and CTA never move.
- The groups need about twice the space they get at 390 × 844, so they scroll on all three devices. **Never shrink the chips, the CTA or the headline to avoid it.**
- The toast sits inside `CheckInFooter` above the button. Its height comes out of the chip groups.

📎 All specs are on the attached **checkin-04-vibe-spec-sheet.png**: frames A–D and callouts ①–⑭. Violet ⑧ ⑪ are the 13 Oct decisions; red ⑫ ⑬ ⑭ are the failure states. The design system has colour and type tokens only and no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/checkin/screen-checkin-vibe-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. States: `<VibeScreen vibes={[]}/>` (A) · `<VibeScreen vibes={[]} toast={VIBE_TOAST_EMPTY}/>` (B) · `<VibeScreen vibes={['coffee_chat','playful','no_rush']}/>` (C) · `<VibeScreen vibes={[five]} toast={VIBE_TOAST_MAX}/>` (D). Builds `VibeStep`, `CHECKIN_VIBE_GROUPS` and `CHECKIN_VIBE_LABEL`. **Consumes** `CoachNote` from Check-In 03, the step shell and `CheckInFooter` from Check-In 02, `CheckInCloseButton` and `CheckInArrow` from Check-In 01, and `LifePill` from Profile 22 (`profile/screen-life-reference.jsx`). A second copy of any of these is a bug.
> Shell components: `design_handoff_showup/components/shared.jsx` (`StatusBar`, `AppHeader`, `StepProgress`, `Button`, `Icon`, `HomeIndicator`) · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **Status bar and home indicator** are stylized mocks. Ship the platform chrome; anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.

## Copy — final strings

Header: `Check-in` · ✕ accessible label `Close Check-In`
Headline: `What best describes your dating vibe today?` — em on **dating vibe**
Coach note: `Your vibes tell people how you feel, what you long for, and how you'd like to meet — a quick read on where your head is at today.`
Counter: `Pick up to 5 — mix and match` · `n/5`

Groups and chips, in this order (label · §31 value):

**What you want to do** · hint `Activities` · group `activity`
1. `Coffee & a chat` · `coffee_chat`
2. `Kiosk beer / Casual drink` · `casual_drink`
3. `Grabbing food` · `grab_food`
4. `Park or nature walk` · `nature_walk`
5. `Bouldering / Table tennis` · `bouldering_table_tennis`
6. `Run or active workout` · `workout`
7. `Watching the sunset` · `sunset`
8. `Drinks & music` · `drinks_music`

**How you feel right now** · hint `Headspace` · group `headspace`
9. `Calm & peaceful` · `calm`
10. `Deep conversation` · `deep_talk`
11. `Fun & playful` · `playful`
12. `Cozy & comfortable` · `cozy`
13. `Spontaneous / Up for anything` · `spontaneous`
14. `Curious / Open-minded` · `curious`

**Your energy & pacing** · hint `Boundaries` · group `pacing`
15. `Quick 30-min hello` · `quick_hello`
16. `Low-key energy` · `low_key`
17. `High social energy` · `high_energy`
18. `Free evening (no rush)` · `no_rush`
19. `Early night (home early)` · `early_night`
20. `Ready to meet now` · `meet_now`

CTA: `Continue` + arrow icon
Toast, nothing picked: `Pick at least one dating vibe (up to 5).`
Toast, sixth chip tapped: `You can pick up to 5 vibes.`

## Behaviour

**Selection**
- Multi-select. A tap on an idle chip selects it; a tap on a selected chip deselects it. The limit is five in total, not per group.
- **At 5/5, a tap on an idle chip selects nothing** and shows the max toast for 2600 ms. A repeat tap restarts the timer. Idle chips look exactly as they do below the limit.
- Selected: `LifePill` selected state (`--liq-primary-500` fill, white 700 text, soft shadow). No checkmark and no second indicator.
- The counter shows `n/5`, `--liq-fg-subtle`, and turns `--liq-primary-500` at 5/5.

**Continue**
- **Dimmed** (opacity .45, saturate .4) while nothing is selected. That only happens on a first Check-In, or after the user deselects everything. **A tap on the dimmed button still registers** and shows the empty toast for 2600 ms. It is never a dead button.
- With one or more selected → Filters. **The set is saved server-side on every accepted Continue** as the last answer, keyed to the user (not to `window_id`). It is also written to the Check-In on `Find my date` (06).

**Re-entry** (epic rules 3 and 5)
- **First Check-In:** nothing pre-selected.
- **Every later Check-In:** the last accepted set is pre-selected, including after a flow that was closed with ✕.
- **✕** saves nothing. Chips tapped but not Continued are lost, and the previous last answer stands.
- **Back and forward within one flow keep the selection.** Filters → back shows the current picks.

**Accessibility**
- Each group is a `group` labelled by its label; each chip is a checkbox (`LifePill multi`) that announces its label and checked state.
- The counter and both toasts are polite live regions.

## Build inventory

- **Client:** `VibeStep`, `CHECKIN_VIBE_GROUPS` and `CHECKIN_VIBE_LABEL` (one constant; Search and Instant read the labels from it).
- **Last-answer store, server-side:** one §31 set per user, written on each accepted Continue and read when the step mounts. Never written on ✕ or on a tap.
- **Check-In record:** `vibes` (§31, 1–5) on the committed Check-In, written on `Find my date` (06).
- **Search and Instant:** no UI change in this story. Their vibe strings come from `CHECKIN_VIBE_LABEL` when they are built.

## Acceptance criteria

- [ ] Renders from the Check-In 02 step shell with content props only. StepProgress is `steps={6} current={4}`
- [ ] Headline Lora 700 / **30** / 1.15, one em on **dating vibe**
- [ ] Three groups and twenty chips in the order above, with the exact strings
- [ ] Chips are `LifePill` from Profile 22 with `multi`. No second chip component, and no new props on `LifePill`
- [ ] **At most five** chips are ever selected, across all groups. A tap on a selected chip deselects it
- [ ] At 5/5 a tap on an idle chip **selects nothing** and shows `You can pick up to 5 vibes.` for 2600 ms. No chip is ever disabled, dimmed or shown as locked
- [ ] The counter shows `n/5` and turns `--liq-primary-500` at 5/5
- [ ] Stored and tracked values are §31 exactly, with no kit ids (`funplayful`, `coffeeachat` …) anywhere
- [ ] A first Check-In shows nothing selected and Continue dimmed. A tap shows `Pick at least one dating vibe (up to 5).` for 2600 ms
- [ ] A later Check-In pre-selects the last **accepted** set. Picks followed by ✕ (no Continue) do not change it
- [ ] ✕ → the gate. Back → Motivation, with the pick intact. Filters → back → Vibe, with the selection intact
- [ ] The chip groups are the **only element that scrolls**, on all three devices. Headline, note, counter and CTA never move
- [ ] No absolute Y in the content; one `flex: 1` element (the chip groups)
- [ ] Copy matches the strings above exactly
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**
- [ ] **Evidence of done:** screenshots of **A, B, C and D** at **375 × 667, 390 × 844 and 430 × 932** (twelve images), plus **D scrolled to the end** at 375 × 667. Each shows the header, the note, the counter and the CTA fully visible and nothing clipped; on 375 × 667 state how many chip rows are visible before scrolling. Check 375 × 667 first.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **H — 48hr Check-In**, and `screen_viewed` in family A). Use them exactly. Do not invent names, and do not restate a payload the registry already defines. Registry **1.4.23**, taxonomy **1.4.14**.

| What | Event | Payload |
|---|---|---|
| Vibe shown | `screen_viewed` | `screen_id: "checkin_vibe"`, `screen_name: "CheckinVibe"`, `referrer_screen_id` — **NEW §11 row** |
| Continue accepted | `check_in_step_answered` | `step_id: "vibe"` (§28), `answer_value: "prefilled"` or `"picked"`, `time_to_answer_ms` |
| Continue accepted | `vibe_selected` | `vibe_ids` (§31), `vibe_count`, `window_id` — **RESPECIFIED, taxonomy 1.4.14 · Class 0** |
| ✕ | `check_in_closed` | `step_id: "vibe"` (§28), `window_id` |

Rules that are easy to get wrong here:

- **⚠ `vibe_selected` changed shape.** The old row had `value` (str), with the trigger "Vibe tag selected for upcoming window", which reads as one event per tag. It is now **`vibe_ids` + `vibe_count` + `window_id`**, fired **once per accepted Continue** with the whole set, never per tap. An emitter written against the old row still compiles and sends the wrong key, one vibe at a time.
- **`vibe_ids` is one string:** the picked §31 values in **list order** (not tap order), comma-joined, no spaces, the same format as `dating_language` (§1). Example: `"coffee_chat,playful,no_rush"`. The group is never sent; it is a property of the value.
- **`answer_value`** is `"prefilled"` when Continue accepts the pre-selected set unchanged (same values, in any tap order), and `"picked"` otherwise.
- **"Accepted" means the step moved on.** A dimmed tap fires nothing. Neither toast is an event.
- **`time_to_answer_ms`** runs from this screen's `screen_viewed` to the accepted Continue.
- **`referrer_screen_id`** is `checkin_motivation` (forward) or Filters' screen (back). Set by navigation, never hard-coded.
- **Emit order:** `check_in_step_answered` → `vibe_selected` → navigation → the next `screen_viewed`.
- `sensitivity_class` is **0** on every row.

**Events that must NOT fire here.**

- **Anything per tap.** No event for a chip tap or a deselect. Do not invent `vibe_tapped`, `vibe_toggled` or `vibe_deselected`, and do not fire `vibe_selected` per chip.
- **Nothing for the max toast.** Do not invent `vibe_limit_reached`. Whether five is too few is answered by the share of `vibe_selected` with `vibe_count: 5`.
- **`profile_step_viewed` / `profile_step_completed`.** These are not profile steps.
- **`check_in_created` / `check_in_completed`.** The commit is on Uplift (06).

**Screen-to-screen conversion.**

| Edge | Numerator | Denominator |
|---|---|---|
| Motivation → Vibe | `screen_viewed` · `checkin_vibe` · referrer `checkin_motivation` | `check_in_step_answered` · `step_id: "motivation"` |
| Vibe → Filters | `check_in_step_answered` · `step_id: "vibe"` | `screen_viewed` · `checkin_vibe` |
| Users at the limit | `vibe_selected` · `vibe_count: 5` | all `vibe_selected` |
| Returning users who change their set | `check_in_step_answered` · `vibe` · `answer_value: "picked"` on a non-first Check-In | all `check_in_step_answered` · `vibe` on a non-first Check-In |
| Closed on Vibe | `check_in_closed` · `step_id: "vibe"` | `screen_viewed` · `checkin_vibe` |

**Registry changes made for this ticket (1.4.23 / taxonomy 1.4.14):** §31 `check_in_vibe` **NEW** (20 values, three groups) · §11 `checkin_vibe` **NEW** · §28 `vibe` confirmed · §9 `vibe` now points at §31 · `vibe_selected` **respecified** (`value` → `vibe_ids` + `vibe_count`, per accepted Continue, Class 0) · `check_in_step_answered` trigger extended for `vibe`.

**Missing from the current build:** every event above is `implemented: false`. `vibe_selected` is marked backend *Live* under its old shape. **Confirm with the backend before building** what the server currently accepts.

## Out of scope

Filters and Uplift (05–06). Search's *vibe today* card and Instant's vibe display, beyond using the §31 labels. The "only during an active Check-In" rule for showing vibes on the profile (the ticket that builds the profile view). Editing a committed Check-In.

## Dependencies

- **Check-In 03:** `CoachNote`.
- **Check-In 02:** the step shell and `CheckInFooter`.
- **Check-In 01:** `CheckInCloseButton` and `CheckInArrow`.
- **Profile 22:** `LifePill` (with `multi`).
- **The last-answer store** (epic *Dependencies*), for Motivation, Vibe and Filters.
- **A `window_id`** from the server window opened at profile completion.
- **Tracking registry 1.4.23 / taxonomy 1.4.14.**

## Open (not blocking)

- **The live backend `vibe_selected`.** It is marked *Live* with a single `value` string. Whether it accepts `vibe_ids` + `vibe_count`, or needs a server change first, is for the backend to confirm (see *Tracking*).
- **Instant's `current_vibe`** on `instant_go_live_started` is still a free string. It takes §31 values when Instant is ticketed; it is not changed here.
