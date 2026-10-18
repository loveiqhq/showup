# [Check-In 03] Motivation

**Parent:** [Epic] 48hrs Check-In flow (`checkin/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `checkin-03-motivation-attachment.zip` (handoff folder for this ticket) · `checkin-03-motivation-spec-sheet.png`
**Blocked by:** **Check-In 02** (the step shell, `CheckInFooter`) and **Check-In 01** (`CheckInCloseButton`, `CheckInArrow`). **Blocks:** nothing structurally. Vibe (04) reuses `CoachNote`, which is built here.

## Description

Step 3 of 6 in the 48hrs Check-In (`step_id: motivation`). The user picks **one** motivation to date from five. It is shown on their profile while the Check-In is active, and it is the vocabulary other people filter on (`dating_intent`).

**Required:** exactly one. **Single-select.**

Three frames on the sheet: **A** first Check-In, nothing selected · **B** empty, with the toast · **C** one motivation picked (also how a returning user lands).

Entered from:

- Tomorrow **Continue** (Check-In 02 D) → Motivation.
- **Back** from Vibe (04) → Motivation, with the selection intact.

Exits:

- **Continue** (one selected) → Check-In 04 (Vibe).
- **Back** → Tomorrow, with Tomorrow's picks intact.
- **✕** → the gate (date search not available). **The selection is not saved.**

## Decided before build

All decided 13 Oct 2026. The kit already matches every point below.

- **Single-select** (callout ⑨, violet). The kit drew multi-select; Instant asked for one; the profile hero carries one `MotivationPill`. One wins everywhere. A tap selects, a tap on another row **replaces** it, a tap on the selected row does **nothing**. There is no way to clear the selection on this screen.
- **Stored values are a new value set, `enums.json` §30 `check_in_motivation`**: `long_term` · `non_monogamy` · `short_term` · `casual` · `friends`, in list order. The kit's old ids (`long-term`, `enm`, `short`) are gone, in both the Check-In and Instant. Nothing had shipped, so nothing migrates.
- **One vocabulary.** §30 is also the `dating_intent` filter (§10) and Instant's motivation. Do not build a mapping table.
- **Class 1.** `non_monogamy` discloses a relationship practice. The value travels only on `motivation_selected` (Class 1), never in a generic payload. See *Tracking*.
- **The profile shows the motivation only while a Check-In is active.** With no active Check-In, the profile shows no motivation. The saved last answer is used only to pre-select this step. Building the profile pill is **out of scope here**; this rule is for the ticket that builds it.
- **Pre-selection** (epic rule 3). A first Check-In starts with nothing selected (A). Every later Check-In pre-selects the **last accepted answer**, saved on each accepted Continue, whether or not that flow was later closed with ✕ or committed. No message mentions it.
- **The option list scrolls on short screens** (⑦, violet). The kit had no flexible element, so at 375 × 667 the list ran under the CTA. The list is now the screen's only `flex: 1` element.
- **The toast copy changes** from `Pick at least one motivation to date.` to `Pick your motivation to date.`. "At least one" is wrong for single-select.

## Layout — read this before building

⚠ **Layout is flex, not absolute.**

**Step shell (from Check-In 02, never copied):** StatusBar (54) → AppHeader (52: back leading, title `Check-in`, `CheckInCloseButton` trailing) → content (`flex: 1`, padding `4 24 0`: StepProgress `steps={6} current={3}` mb 22 → step body `flex: 1` → `CheckInFooter` mb 22) → HomeIndicator (28).

**Step body:** header block (`flex: none`: headline mt 8 → CoachNote mt 14) → **option list (`flex: 1`, `min-height: 0`, `overflow-y: auto`, mt 14, gap 10, pb 12)**.

- **The list is the only flexible element and the only thing that scrolls.** The headline, note and CTA never move.
- At 390 × 844 the five rows fit. At 375 × 667 the list scrolls inside itself. **Never shrink the rows, the CTA or the headline to avoid it.**
- The toast sits inside `CheckInFooter` above the button. Its height comes out of the list.

📎 All specs are on the attached **checkin-03-motivation-spec-sheet.png**: frames A–C and callouts ①–⑫. Violet ⑦ ⑨ are the 13 Oct decisions; red ⑪ ⑫ are the failure state. The design system has colour and type tokens only and no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/checkin/screen-checkin-motivation-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. States: `<MotivationScreen intent={null}/>` (A) · `<MotivationScreen intent={null} toast={MOTIVATION_TOAST}/>` (B) · `<MotivationScreen intent="long_term"/>` (C). Builds `IntentStep`, `CHECKIN_MOTIVATIONS` and `CoachNote`. **Consumes** the step shell and `CheckInFooter` from Check-In 02, and `CheckInCloseButton` and `CheckInArrow` from Check-In 01. A second copy of any of these is a bug.
> Shell components: `design_handoff_showup/components/shared.jsx` (`StatusBar`, `AppHeader`, `StepProgress`, `Button`, `Icon`, `HomeIndicator`) · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **Status bar and home indicator** are stylized mocks. Ship the platform chrome; anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.

## Copy — final strings

Header: `Check-in` · ✕ accessible label `Close Check-In`
Headline: `What is your motivation to date?` — em on **motivation**
Coach note: `This will be highly visible on your profile and a key decision point for someone to date you.`

Options, in this order (title · subline · icon · §30 value):

1. `Long-term partner` · `Looking for a meaningful connection` · `heart-filled` · `long_term`
2. `Long-term Non-Monogamy` · `Non-monogamous lifestyle` · `compass` · `non_monogamy`
3. `Short-term fun` · `Physical chemistry, no commitment` · `flame` · `short_term`
4. `Casual dating` · `Light, fun, non-committal` · `sparkles` · `casual`
5. `New friends` · `Strictly platonic connections` · `users` · `friends`

CTA: `Continue` + arrow icon
Toast: `Pick your motivation to date.`

## Behaviour

**Selection**
- Single-select, radio semantics. A tap on an unselected row selects it and deselects the other. A tap on the selected row does nothing.
- Selected: border `--liq-primary-500`, icon tile filled `--liq-primary-500` with a white glyph. No checkmark and no second indicator.

**Continue**
- **Dimmed** (opacity .45, saturate .4) while nothing is selected. That only happens on a first Check-In. **A tap on the dimmed button still registers** and shows the toast for 2600 ms; a repeat tap restarts the timer. It is never a dead button.
- With one selected → Vibe. **The selection is saved server-side on every accepted Continue** as the last answer, keyed to the user (not to `window_id`). It is also written to the Check-In on `Find my date` (06).

**Re-entry** (epic rules 3 and 5)
- **First Check-In:** nothing pre-selected.
- **Every later Check-In:** the last accepted answer is pre-selected, including after a flow that was closed with ✕.
- **✕** saves nothing. A row tapped but not Continued is lost, and the previous last answer stands.
- **Back and forward within one flow keep the selection.** Vibe → back shows the current pick.

**Accessibility**
- The list is a radio group (`Motivation to date`); each row announces its title, subline and checked state.
- The toast is announced as a polite live region.

## Build inventory

- **Client:** `IntentStep`, `CHECKIN_MOTIVATIONS` (one constant, shared with Instant), `CoachNote`.
- **Last-answer store, server-side:** one §30 value per user, written on each accepted Continue and read when the step mounts. Never written on ✕ or on a tap.
- **Check-In record:** `motivation` (§30) on the committed Check-In, written on `Find my date` (06).
- **Instant:** its motivation chips use the same §30 ids. No UI change in this story.

## Acceptance criteria

- [ ] Renders from the Check-In 02 step shell with content props only. StepProgress is `steps={6} current={3}`
- [ ] Headline Lora 700 / **30** / 1.15, one em on **motivation**
- [ ] Five rows in the order above, with the exact strings and icons
- [ ] **Single-select:** at most one row is ever selected; a tap on another row moves the selection; a tap on the selected row changes nothing
- [ ] Stored and tracked values are §30 exactly (`long_term` · `non_monogamy` · `short_term` · `casual` · `friends`), with no kit ids (`long-term`, `enm`, `short`) anywhere, including Instant
- [ ] A first Check-In shows nothing selected and Continue dimmed. A tap shows `Pick your motivation to date.` for 2600 ms
- [ ] A later Check-In pre-selects the last **accepted** answer. A pick followed by ✕ (no Continue) does not change it
- [ ] ✕ → the gate. Back → Tomorrow, with Tomorrow's picks intact. Vibe → back → Motivation, with the selection intact
- [ ] The option list is the **only element that scrolls**. At 375 × 667 it scrolls inside itself; headline, note and CTA never move
- [ ] No absolute Y in the content; one `flex: 1` element (the list)
- [ ] The motivation value appears in **no** tracking payload other than `motivation_selected`
- [ ] Copy matches the strings above exactly
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**
- [ ] **Evidence of done:** screenshots of **A, B and C** at **375 × 667, 390 × 844 and 430 × 932** (nine images). Each shows the header, the note and the CTA fully visible and nothing clipped; on 375 × 667 state how many rows are visible before scrolling. Check 375 × 667 first.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **H — 48hr Check-In**, and `screen_viewed` in family A). Use them exactly. Do not invent names, and do not restate a payload the registry already defines. Registry **1.4.22**, taxonomy **1.4.13**.

| What | Event | Payload |
|---|---|---|
| Motivation shown | `screen_viewed` | `screen_id: "checkin_motivation"`, `screen_name: "CheckinMotivation"`, `referrer_screen_id` — **NEW §11 row** |
| Continue accepted | `check_in_step_answered` | `step_id: "motivation"` (§28), `answer_value: "prefilled"` or `"picked"`, `time_to_answer_ms` |
| Continue accepted | `motivation_selected` | `motivation_id` (§30), `window_id` — **RESPECIFIED, taxonomy 1.4.13 · Class 1** |
| ✕ | `check_in_closed` | `step_id: "motivation"` (§28), `window_id` |

Rules that are easy to get wrong here:

- **⚠ `motivation_selected` changed shape.** The old row had `value` (str), with the trigger "Dating motivation tag set". It is now **`motivation_id`** (§30) + `window_id`, fired **once per accepted Continue**, never per tap. An emitter written against the old row still compiles and sends the wrong key.
- **`answer_value` never carries the motivation.** It is `"prefilled"` when Continue accepts the pre-selected last answer unchanged, and `"picked"` otherwise (first Check-In, or the user changed it). The value is Class 1 and travels only on `motivation_selected`.
- **"Accepted" means the step moved on.** A dimmed tap fires nothing; the toast is not an event.
- **`time_to_answer_ms`** runs from this screen's `screen_viewed` to the accepted Continue.
- **`referrer_screen_id`** is `checkin_tomorrow` (forward) or Vibe's screen (back). Set by navigation, never hard-coded.
- **Emit order:** `check_in_step_answered` → `motivation_selected` → navigation → the next `screen_viewed`.
- `sensitivity_class` is **1** on `motivation_selected`, and 0 on the other rows.

**Events that must NOT fire here.**

- **Anything per tap.** No event for a row tap. Do not invent `motivation_tapped` or `motivation_changed`, and do not keep firing `motivation_selected` on tap.
- **`profile_step_viewed` / `profile_step_completed`.** These are not profile steps.
- **`check_in_created` / `check_in_completed`.** The commit is on Uplift (06).

**Screen-to-screen conversion.**

| Edge | Numerator | Denominator |
|---|---|---|
| Tomorrow → Motivation | `screen_viewed` · `checkin_motivation` · referrer `checkin_tomorrow` | `check_in_step_answered` · `step_id: "tomorrow"` |
| Motivation → Vibe | `check_in_step_answered` · `step_id: "motivation"` | `screen_viewed` · `checkin_motivation` |
| Returning users who change their answer | `check_in_step_answered` · `motivation` · `answer_value: "picked"` on a non-first Check-In | all `check_in_step_answered` · `motivation` on a non-first Check-In |
| Closed on Motivation | `check_in_closed` · `step_id: "motivation"` | `screen_viewed` · `checkin_motivation` |

**Registry changes made for this ticket (1.4.22 / taxonomy 1.4.13):** §30 `check_in_motivation` **NEW** · §11 `checkin_motivation` **NEW** · §28 `motivation` confirmed · §9 `motivation` and §10 `dating_intent` now point at §30 · `motivation_selected` **respecified** (`value` → `motivation_id`, per accepted Continue, Class 1) · `check_in_step_answered` trigger extended for `motivation`.

**Missing from the current build:** every event above is `implemented: false`. `motivation_selected` is marked backend *Live* under its old shape. **Confirm with the backend before building** what the server currently accepts.

## Out of scope

Vibe, Filters and Uplift (04–06). The profile's `MotivationPill` and its "only during an active Check-In" rule (the ticket that builds the profile view). The `dating_intent` filter UI. Instant's own flow, beyond using the §30 ids. Editing a committed Check-In.

## Dependencies

- **Check-In 02:** the step shell and `CheckInFooter`.
- **Check-In 01:** `CheckInCloseButton` and `CheckInArrow`.
- **The last-answer store** (epic *Dependencies*), for Motivation, Vibe and Filters.
- **A `window_id`** from the server window opened at profile completion.
- **Tracking registry 1.4.22 / taxonomy 1.4.13.**

## Open (not blocking)

- **The live backend `motivation_selected`.** It is marked *Live* with a `value` string. Whether it accepts the new `motivation_id` key, or needs a server change first, is for the backend to confirm (see *Tracking*).
