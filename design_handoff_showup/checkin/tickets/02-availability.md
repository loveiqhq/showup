# [Check-In 02] Availability — Today + Tomorrow

**Parent:** [Epic] 48hrs Check-In flow (`checkin/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `checkin-02-availability-attachment.zip` (handoff folder for this ticket) · `checkin-02-availability-spec-sheet.png`
**Blocked by:** **Check-In 01** (`CheckInCloseButton`, `CheckInArrow`, the gate) and **the Check-In state machine** (step order, re-entry routing, the intro-passed flag — epic *Built once*). **Blocks:** Check-In 03–06, which consume the step shell, `CheckInFooter`, `HowItWorks` and the step headline size settled here.

## Description

The first two steps of the 48hrs Check-In. **Two screens, one component, one story.** Today and Tomorrow are the same `AvailabilityStep` with different `day` and `startHour` props, so they share one layout column and one ticket. Splitting them is how a grid gets built twice.

- **Today** (`step_id: today`, StepProgress 1 of 6). The user taps the hours they can date for the rest of today. **Optional**: an empty Today raises a confirm, not an error.
- **Tomorrow** (`step_id: tomorrow`, StepProgress 2 of 6). The same grid for tomorrow. **Required**: at least one hour.

Four frames on the sheet: **A** Today, hours picked · **B** Today empty, with the *No hours today?* confirm · **C** Tomorrow empty, with the toast · **D** Tomorrow, hours picked.

Entered from:

- **Intro CTA** (Check-In 01 A) → Today.
- **The gate** (Check-In 01 B), **once the user has passed the intro before** → Today, intro skipped. **Empty, like every entry.**

Exits:

- Today **Continue** (≥ 1 hour), or **Continue to tomorrow** on the confirm → Tomorrow.
- Tomorrow **Continue** (≥ 1 hour) → Check-In 03 (Motivation).
- **Back**: Today → intro · Tomorrow → Today.
- **✕** on either → the gate (date search not available). **Picked hours are discarded.**

## Decided before build

All decided 13 Oct 2026. The kit already matches every point below.

- **Availability is never kept** (decided 13 Oct 2026, after the first draft of this ticket). Today and Tomorrow **start empty on every entry**: the first Check-In, a reopen after ✕, and every later Check-In. Hours picked in a flow that was closed are discarded. This **replaces** the epic's 12 Oct draft rule and **deletes** the stale-draft-hours rule: with nothing kept, nothing has to decide whether old hours still make sense. Motivation, Vibe and Filters **do** pre-select the last answer given (stories 03–05); Uplift starts empty (06).
- **Reopening from the gate lands on Today once the user has passed the intro** (tapped its CTA) at least once; before that, on the intro. Check-In 01 is re-issued with this routing.
- **One story for Today and Tomorrow.** The epic's stories are renumbered: **02 Availability · 03 Motivation · 04 Vibe · 05 Filters · 06 Uplift**. `step_id` values (§28) do not change.
- **The step headline is 30** (callout ④, violet). This settles the epic's open point: one size for the step group. Vibe, Filters and Uplift were 28 and move to 30. That change is in the kit and belongs to their stories.
- **Today starts at the next full hour** (⑩, violet). At 4:20 the first slot is 5pm, not 4pm. An hour that has already started is never offered, because the 1-hour meet buffer means it can never produce a date.
- **A Check-In day runs 7am → 7am** (⑮, violet). Both grids end at the **6am** slot: night hours belong to the evening before. "Today" is the 7am → 7am block that contains the next full hour. At 1:20am Today is 2am–6am; at 6:20am Today is the new day, from 7am.
- **Both tiers are stored and used.** Preferred (1st tap) and Available (2nd tap) are separate values per slot; the backend already supports both.
- **Every Check-In CTA uses the `CheckInArrow` SVG** (⑧ ⑰, violet). That covers the step Continue and *Continue to tomorrow*. The `→` text glyph in the kit is gone. This is the same rule as Check-In 01.
- **The "See How it works for more info" sentence in Today's subline stays as drawn.** It is styled text, not a link. The disclosure directly below it is the control.
- **The meet-time buffer is 1 hour** (confirmed). The *How it works* example (Match at 7pm → meet at 8pm) stands, and the epic's open point is closed.
- **The epic's "small bottom-right NextButton" was wrong.** `CheckInFooter` is a **full-width** sunset Button. Corrected in the epic.

## Layout — read this before building

⚠ **Layout is flex, not absolute.**

**Step shell (built here, consumed by 03–06):** StatusBar (54) → AppHeader (52: back leading, title `Check-in`, `CheckInCloseButton` trailing) → content (`flex: 1`, padding `4 24 0`: StepProgress `steps={6}` mb 22 → step body `flex: 1` → `CheckInFooter` mb 22) → HomeIndicator (28).

**Step body:** header block (`flex: none`: headline mt 8 → subline mt 10 → HowItWorks row mt 12) → **grid (`flex: 1`, `min-height: 0`, `overflow-y: auto`, mt 13)** → summary (`flex: none`, mt 8) → legend (`flex: none`, mt 6, pt 9, mb 8).

- **The grid is the only flexible element and the only thing that scrolls.** Opening *How it works* takes its height from the grid; the summary, legend and CTA never move.
- At 390 × 844 both grids fit (Today: 14 slots at 4:20 · Tomorrow: 24). At 375 × 667 both scroll inside the grid. **Never shrink slots, the CTA or the headline to avoid it.**
- The dialog is the only absolutely-positioned layer (scrim + card, centred, `z 40`).

📎 All specs are on the attached **checkin-02-availability-spec-sheet.png**: frames A–D and callouts ①–⑲. Violet ④ ⑧ ⑩ ⑮ ⑰ are the 13 Oct decisions; red ⑱ ⑲ are the failure state. The design system has colour and type tokens only and no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/checkin/screen-checkin-availability-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. Screens: `<AvailabilityScreen day="today" …/>` (A, B with `dialogOpen`) · `<AvailabilityScreen day="tomorrow" …/>` (C with `map={}` + `toast`, D). Builds `AvailabilityStep`, `HowItWorks`, `AvailabilitySummary`, `AvailabilitySlot`, `LegendPill`, `CheckInFooter`, `NoHoursTodayDialog`. **Consumes** `CheckInCloseButton` and `CheckInArrow` from Check-In 01. A second copy of any of these is a bug.
> Shell components: `design_handoff_showup/components/shared.jsx` (`StatusBar`, `AppHeader`, `StepProgress`, `Button`, `Icon`, `HomeIndicator`) · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **Status bar and home indicator** are stylized mocks. Ship the platform chrome; anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.
- **The 4:20 clock** is the kit's fixed device time. Build against the real clock (see *Behaviour → Time*).

## Copy — final strings

**Header (both):** `Check-in` · ✕ accessible label `Close Check-In`

**Today**
Headline: `When can you date today?` — em on **today**
Subline: `Tap each hour you're free. We'll only show you people free at the same times. Can't date today? Just continue to tomorrow. See How it works for more info.` — `How it works` in primary 700, text only

**Tomorrow**
Headline: `When can you date tomorrow?` — em on **tomorrow**
Subline: `Tap each hour you're free to meet. We'll only show people free within your hours.`

**Both**
Disclosure: `How it works` · `Clear all`
Panel, five paragraphs (bold as drawn):
1. `We only show you people who are free to date at the **same times as you**.`
2. `Only pick hours when you can **actually be at a meeting spot** — make sure you have time for the transit.`
3. `When you get a Match, we suggest a meet time based on your **overlapping availability**.`
4. `**Example:** At 7pm you get a Match. Your overlapping availability is 6pm–11pm. We suggest meeting at 8pm, so you both can arrive on time.`
5. `That's the magic of Show Up — **you actually meet someone — fast.**`
Hour labels: `7am` … `11am` · `noon` · `1pm` … `11pm` · `12am` · `1am` … `6am`
Summary: `Your availability` + ranges (`6–9pm`, `12–2pm · 7–8pm`, `+N more`) · empty: `nothing selected yet`
Legend: `Tap an hour to cycle through:` · `1st tap Preferred` · `2nd tap Available` · `3rd tap Clear`
CTA: `Continue` + arrow icon
Toast (Tomorrow): `Pick at least one hour you can date tomorrow.`

**No hours today? (B)**
Title: `No hours today?` — em on **today**
Body: `You didn't add any available hours to date today. Continue to set your availability for tomorrow — or cancel and choose the hours you can date today.`
Primary: `Continue to tomorrow` + arrow icon · Secondary: `Cancel, pick hours today`

## Behaviour

**Grid**
- A tap cycles a slot **idle → preferred → available → idle**. There is no long-press and no drag-select.
- **Clear all** shows only when ≥ 1 slot is picked. It clears **this day only**, with no confirm.
- The **summary** collapses picked slots into ranges on one line. A slot means *h until h+1*, so 6pm + 7pm + 8pm reads `6–9pm`. Runs across midnight stay one range. Tiers are not distinguished in the summary.
- **How it works** is collapsed on every entry. It toggles inline, never as a modal.

**Time**
- **Today's first slot is the next full hour**, from the device's local clock.
- **Re-evaluated on every foreground**, and at minimum when the screen is shown. A slot that has started disappears from the grid and from the selection, silently.
- **The day boundary is 7am.** "Today" is the 7am → 7am block containing the next full hour; "tomorrow" is the block after it. Between 6:00 and 6:59am the next full hour is 7am, so Today is the new day.

**Today — optional**
- Continue is **never disabled**. With ≥ 1 slot → Tomorrow.
- **Empty → the *No hours today?* confirm.** `Continue to tomorrow` → Tomorrow, recording an empty Today. `Cancel, pick hours today`, a scrim tap or Android back → close the dialog and stay on Today. The dialog has no ✕.
- **Back** → the intro (see *Open*).

**Tomorrow — required**
- Continue is **dimmed** (opacity .45, saturate .4) until ≥ 1 slot is picked. **A tap on the dimmed button still registers** and shows the toast for 2600 ms; a repeat tap restarts the timer. It is never a dead button.
- **Back** → Today, with Today's picks intact.

**Re-entry** (epic rules 3 and 5)
- **Both grids start empty on every entry.** Nothing is pre-selected: not from a closed flow, not from the last committed Check-In.
- **✕** discards the hours picked on Today and Tomorrow, with no confirm and no message, and lands on the gate.
- **Back and forward within one flow keep the picks.** Tomorrow → back → Today shows Today's picks; Today → Tomorrow → back → Tomorrow shows Tomorrow's. Only ✕ (or leaving the app long enough for the screen to be destroyed) clears them.
- **Reopening from the gate** lands on Today once the user has passed the intro at least once (server flag, set on the intro CTA); before that, on the intro.

**Accessibility**
- Each slot is a button announcing its hour and state (`5pm, not selected` / `preferred` / `available`).
- The toast is announced as a polite live region. The dialog is modal, with focus trapped on open and returned on close.

## Build inventory

- **Client:** the step shell, `AvailabilityStep`, `HowItWorks`, `AvailabilitySummary`, `AvailabilitySlot`, `LegendPill`, `CheckInFooter`, `NoHoursTodayDialog`.
- **Check-In state machine** (epic *Built once*) — step order and back/✕ routing. Build before this story or with it.
- **Intro-passed flag, server-side** — set on the intro CTA, read by the gate to route to Today or the intro. Survives a reinstall.
- **Availability save** with both tiers per slot (`preferred` | `available`), keyed to `window_id`, written on `Find my date` (06). Nothing is written on ✕.
- **Clock re-evaluation on foreground** for the next-full-hour rule.

## Acceptance criteria

- [ ] The step shell is **one component**: StatusBar → AppHeader (`Check-in`, back leading, `CheckInCloseButton` trailing) → StepProgress `steps={6}` → body → `CheckInFooter` → HomeIndicator. Check-In 03–06 render from it with content props only
- [ ] StepProgress is `current={1}` on Today and `current={2}` on Tomorrow
- [ ] Headline Lora 700 / **30** / 1.15, one em per headline. **30 is the step size** — no Check-In step renders 28
- [ ] Today and Tomorrow render from **one `AvailabilityStep`** with `day` / `startHour` props — no second grid component
- [ ] At 4:20 Today's first slot is **5pm**. At any time, a slot that has started is never shown or kept
- [ ] Both grids end at the **6am** slot; Tomorrow starts at **7am** with 24 slots. Between 6:00 and 6:59am, Today starts at 7am
- [ ] A tap cycles idle → preferred → available → idle, with the colours on the sheet. Both tiers are saved
- [ ] The grid is the **only element that scrolls**; the header, summary, legend and CTA never move, including with *How it works* open
- [ ] Clear all appears only with a selection and clears only that day
- [ ] The summary is one line and never wraps; more than 4 runs shows `+N more`
- [ ] Today's Continue is **never disabled**; empty → the confirm; `Continue to tomorrow` → Tomorrow; Cancel, scrim and Android back stay on Today
- [ ] Tomorrow's Continue is **dimmed and still tappable** when empty; a tap shows the toast with the exact string; ≥ 1 slot → Motivation
- [ ] Every CTA arrow is the `CheckInArrow` SVG — no `→` glyph anywhere in the Check-In
- [ ] **Both grids start empty on every entry.** ✕ on either screen → the gate, and the picked hours are gone: a reopen shows Today and Tomorrow empty. (Superseded 13 Oct 2026: was "draft kept … reopen pre-filled, started slots dropped".)
- [ ] The gate reopens at **Today** once the intro has been passed, at the **intro** before that
- [ ] Back: Tomorrow → Today (picks intact) · Today → intro
- [ ] No absolute Y in the content; one `flex: 1` element (the grid)
- [ ] Copy matches the strings above exactly
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**
- [ ] **Evidence of done:** screenshots of **A, B, C and D** at **375 × 667, 390 × 844 and 430 × 932** (twelve images). Each shows all content outside the grid, nothing clipped, with the CTA fully visible; on 375 × 667 state how many grid rows are visible. Plus **one recording**: pick hours on Today → Tomorrow → back (Today's picks intact) → ✕ → gate → reopen → **Today empty**. Check 375 × 667 first.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **H — 48hr Check-In**, and `screen_viewed` in family A). Use them exactly. Do not invent names, and do not restate a payload the registry already defines. Registry **1.4.21**, taxonomy **1.4.12**.

| What | Event | Payload |
|---|---|---|
| Today shown | `screen_viewed` | `screen_id: "checkin_today"`, `screen_name: "CheckinToday"`, `referrer_screen_id` — **NEW §11 row** |
| Tomorrow shown | `screen_viewed` | `screen_id: "checkin_tomorrow"`, `screen_name: "CheckinTomorrow"`, `referrer_screen_id` — **NEW §11 row** |
| Continue accepted (either) | `check_in_step_answered` | `step_id` (§28: `today` · `tomorrow`), `answer_value: "hours"` or `"none"`, `time_to_answer_ms` |
| Continue accepted (either) | `availability_slots_set` | `day` (§28), `preferred_hours`, `available_hours`, `window_id` — **RESPECIFIED, taxonomy 1.4.12** |
| Confirm answered (B) | `check_in_no_hours_today_resolved` | `action` (§29), `window_id` — **NEW** |
| ✕ (either) | `check_in_closed` | `step_id` (§28), `window_id` — **`has_draft` removed** |

Rules that are easy to get wrong here:

- **"Accepted" means the step moved on.** A dimmed tap on Tomorrow fires nothing (the toast is not an event). Today's Continue on an empty grid fires only when `Continue to tomorrow` is chosen, and then fires `answer_value: "none"` and `availability_slots_set` with **0 / 0**.
- **`answer_value` is `"hours"` or `"none"` only.** The hours live in `availability_slots_set`; never put ranges or slot lists in `answer_value`.
- **`time_to_answer_ms`** runs from that screen's `screen_viewed` to the accepted Continue, including time in the dialog.
- **`referrer_screen_id`** on Today is `checkin_intro`, `checkin_search_unavailable` (gate reopen) or `checkin_tomorrow` (back). On Tomorrow it is `checkin_today`, or Motivation's screen on back. Set by navigation, never hard-coded.
- **`check_in_closed` has no `has_draft`** any more — nothing is kept that it could describe. On the gate, `check_in_gate_tapped` carries **`intro_passed`** (Check-In 01).
- **Emit order:** `check_in_no_hours_today_resolved` (if any) → `check_in_step_answered` → `availability_slots_set` → navigation → the next `screen_viewed`.
- Nothing here is attribute data: `sensitivity_class` 0 on every row.

**Events that must NOT fire here.**

- **Anything per tap** — no event for a slot tap, Clear all, or opening *How it works*. Do not invent `slot_tapped`, `availability_cleared` or `how_it_works_opened`.
- **`check_in_started`** on a gate reopen after the intro has been passed — that path skips the intro (Check-In 01).
- **`availability_updated`** — that is post-commit editing, not this flow.
- **`profile_step_viewed` / `profile_step_completed`** — these are not profile steps.
- **`check_in_created` / `check_in_completed`** — the commit is on Uplift (06).

**Screen-to-screen conversion.**

| Edge | Numerator | Denominator |
|---|---|---|
| Intro → Today | `screen_viewed` · `checkin_today` · referrer `checkin_intro` | `check_in_started` |
| Today → Tomorrow | `check_in_step_answered` · `step_id: "today"` | `screen_viewed` · `checkin_today` |
| Empty Today → confirmed | `check_in_no_hours_today_resolved` · `action: "continue_tomorrow"` | all `check_in_no_hours_today_resolved` |
| Tomorrow → Motivation | `check_in_step_answered` · `step_id: "tomorrow"` | `screen_viewed` · `checkin_tomorrow` |
| Closed on availability | `check_in_closed` · `step_id` today / tomorrow | `screen_viewed` on the same screen |

**Registry changes made for this ticket (1.4.21 / taxonomy 1.4.12):** §11 `checkin_today` and `checkin_tomorrow` **NEW** · §28 `today` and `tomorrow` confirmed · §29 `check_in_no_hours_today_action` **NEW** (`continue_tomorrow` · `pick_today` · `dismissed`) · `availability_slots_set` **respecified** (`slot_count`, `total_hours`, `weekend_share` removed; `day`, `preferred_hours`, `available_hours`, `window_id` added) · `check_in_no_hours_today_resolved` **NEW** · `check_in_step_answered` trigger clarified · **draft removed** (same version, 13 Oct): `check_in_closed` loses `has_draft`, `check_in_gate_tapped` `has_draft` → **`intro_passed`**.

**Missing from the current build:** every event above is `implemented: false`.

## Out of scope

Motivation, Vibe, Filters and Uplift (03–06), and moving their headlines to 30 in code. The commit on `Find my date`. Editing availability after a Check-In is committed. Instant's 15-second flow. The time-of-day variant on the kit canvas (`ScreenCheckInAvailV2`) is an **exploration, not a build target**.

## Dependencies

- **Check-In 01** — `CheckInCloseButton`, `CheckInArrow`, the gate's routing (intro passed → Today).
- **The Check-In state machine and the intro-passed flag** — see *Build inventory*.
- **A `window_id`** from the server window opened at profile completion.
- **Tracking registry 1.4.21 / taxonomy 1.4.12.**

## Open (not blocking)

- **Back on Today after a gate reopen.** The intro was skipped on the way in. As built here, back still opens the intro, and its CTA then fires `check_in_started` a second time for the same `window_id`. The alternative is no back control when Today is the stack root. Default: back → intro, dedupe `check_in_started` by `window_id` in analysis.
- **Vibe's toast says "up to 5"** while the rule is max 3. To fix in story 04.
