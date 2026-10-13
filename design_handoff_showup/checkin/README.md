# 48hrs Check-In flow — handoff

The flow that makes a user searchable: availability for today and tomorrow, motivation, vibe, filters and — last, carrying `Find my date` — a reason to be picked. Point Claude Code at **this folder**.

```
checkin/
  screen-checkin-intro-reference.jsx  ← screen 01: the intro + the "date search not available" Search tab. Read values from here.
  tickets/00-epic.md                  ← flow shape, built-once inventory, flow rules
  tickets/01-intro.md                 ← scope, copy strings, AC
  spec-sheets/01-intro.png            ← annotated visual spec, keyed ①–⑱, 2 frames, violet concept-change callouts
  screen-checkin-availability-reference.jsx ← screen 02: Today + Tomorrow, the step shell, CheckInFooter, HowItWorks
  tickets/02-availability.md          ← scope, copy strings, AC, tracking
  spec-sheets/02-availability.png     ← keyed ①–⑲, 4 frames (A–D), violet 13 Oct decisions, red failure state
  screen-checkin-motivation-reference.jsx ← screen 03: IntentStep, CHECKIN_MOTIVATIONS, CoachNote
  tickets/03-motivation.md            ← scope, copy strings, AC, tracking
  spec-sheets/03-motivation.png       ← keyed ①–⑫, 3 frames (A–C), violet single-select + list scroll, red failure state
  screen-checkin-vibe-reference.jsx   ← screen 04: VibeStep, CHECKIN_VIBE_GROUPS, CHECKIN_VIBE_LABEL; consumes LifePill (Profile 22)
  tickets/04-vibe.md                  ← scope, copy strings, AC, tracking
  spec-sheets/04-vibe.png             ← keyed ①–⑭, 4 frames (A–D), violet 1–5 + groups scroll, red failure states
  screen-checkin-filters-reference.jsx ← screen 05: FiltersStep, CHECKIN_GENDERS, checkinGenderDefault, RangeSlider
  tickets/05-filters.md               ← scope, copy strings, AC, tracking
  spec-sheets/05-filters.png          ← keyed ①–⑭, 3 frames (A–C), violet sexuality removed + gender mandatory, red failure state
  screen-checkin-uplift-reference.jsx ← screen 06: UpliftStep, upliftContact, CheckInFooter loading + error; the commit
  tickets/06-uplift.md                ← scope, copy strings, AC, tracking
  spec-sheets/06-uplift.png           ← keyed ①–⑮, 5 frames (A–E), violet 13 Oct decisions, OS-owned keyboard, red failure + commit states
```

Kit source: `ui_kits/show-up/screens-checkin.jsx` (`ScreenCheckIn` steps 1–7, `ScreenCheckInNeeded`). **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## Flow rules — every step ticket inherits these

**1. Check-In gates date search** (decided 12 Oct 2026, standing rule in `design_handoff_showup/CLAUDE.md`). Reaching the Check-In for the first time means profile creation is done, not that the user is ready to date. The user can close it and use the app without date search.

**2. Every Check-In screen has a close ✕.** `CheckInCloseButton`, built by ticket 01. On the intro it sits in the leading slot of a 52 close row (no back exists); on steps 2–7 it sits in the AppHeader's **trailing** slot and back stays leading. It goes to the Search tab's *date search not available* state and fires `check_in_closed` with that step's `step_id` (enums §28).

**3. Nothing is kept as a draft — decided 13 Oct 2026.** ⚠ *Supersedes the 12 Oct rule "closing mid-flow keeps a draft".* **Availability (Today, Tomorrow) and the Uplift text start empty on every entry**; hours picked in a closed flow are discarded. **Motivation, Vibe and Filters pre-select the last answer given**, saved on every accepted Continue, whether the flow was later closed with ✕ or committed. **Reopening from the gate lands on Today once the user has passed the intro** (tapped its CTA) at least once; before that, on the intro. No message anywhere mentions earlier answers.

**4. ~~Stale draft hours are dropped silently.~~** **Deleted 13 Oct 2026** with the draft (rule 3): no hours are kept, so none can be stale.

**4a. Today starts at the next full hour; a Check-In day runs 7am → 7am** (decided 13 Oct 2026, ticket 02). An hour that has started is never offered (the 1-hour meet buffer means it cannot produce a date). Both grids end at the 6am slot — night hours belong to the evening before.

**5. One headline size per group.** The intro is a bridge: **34** (with Profiles 05 and 13). **Every step is 30** — settled 13 Oct 2026 in ticket 02; Vibe, Filters and Uplift moved from 28 in the kit.

**5a. Every Check-In CTA carries `CheckInArrow`** (SVG), never a `→` text glyph — the step footer and the *No hours today?* confirm included (13 Oct 2026).

**5b. Motivation is single-select** (13 Oct 2026, ticket 03). Values are §30 `check_in_motivation` — one vocabulary with Instant and the `dating_intent` filter. The profile shows the motivation **only while a Check-In is active**.

**5c. Vibe is multi-select, 1–5** (13 Oct 2026, ticket 04). Values are §31 `check_in_vibe` — one vocabulary with Search's *vibe today* card and Instant. At 5/5 a tap on an idle chip selects nothing and raises a toast; chips are never locked. Chips are Profile 22's `LifePill`.

**5d. Filters are gender (mandatory), age range (optional, 18 → 65+) and distance (default 10 km)** (13 Oct 2026, ticket 05). **No sexuality filter** — matching is two-way on gender; orientation stays on the profile. The step edits the user's filter settings, **one store with Search**. A first Check-In pre-fills gender from the profile.

**5e. Uplift is optional, 100 characters, return = new line, and never carries contact info** (13 Oct 2026, ticket 06). Emails, phone numbers and links are blocked live with `No emails, phone numbers or links allowed.`; the server repeats the check. Inspirations are plain text, not tappable. Empty commits with no nudge.

**5f. `Find my date` commits the Check-In** (ticket 06). Saving and save-failed are states of the Uplift screen; success goes straight to Search. Passed Today hours and an expired window are resolved server-side, with no UI.

**6. Instant opens the 48hrs Check-In** until Instant is ticketed. Its own 15-second flow is untouched.

## Tracking

Family **H** in `tracking/events.json` is **`check_in_*`** since taxonomy 1.4.10 (decision 26). `check_in_created` is the one shipped event. `checkin_intro_viewed` was removed (duplicate of `screen_viewed`). Value sets: §27 `check_in_gate_target`, §28 `check_in_step`. Screens: §11 `checkin_intro`, `checkin_search_unavailable`, `checkin_today`, `checkin_tomorrow`. §29 `check_in_no_hours_today_action`. `availability_slots_set` respecified and `check_in_no_hours_today_resolved` added in registry 1.4.21 / taxonomy 1.4.12 (ticket 02). §11 `checkin_motivation`, §30 `check_in_motivation` and `motivation_selected` respecified (Class 1, per accepted Continue) in registry 1.4.22 / taxonomy 1.4.13 (ticket 03). §11 `checkin_vibe`, §31 `check_in_vibe` and `vibe_selected` respecified (Class 0, per accepted Continue, `vibe_ids` + `vibe_count`) in registry 1.4.23 / taxonomy 1.4.14 (ticket 04). §10 `meet_gender` repaired, `meet_orientation` retired, §11 `checkin_filters` and `check_in_filters_changed` respecified (Class 0, per accepted Continue, no gender values) in registry 1.4.24 / taxonomy 1.4.15 (ticket 05). §11 `checkin_uplift`, §32 `check_in_commit_failure` and `check_in_commit_failed` added; `check_in_created` named the one commit event; `check_in_completed` and `uplift_shown` retired in registry 1.4.25 / taxonomy 1.4.16 (ticket 06).

## Screens

| # | Screen | States | Status |
|---|---|---|---|
| 01 | Intro + date search not available | A intro · B closed | ticketed — not built |
| 02 | Availability — Today + Tomorrow (one story) | A Today picked · B Today empty + confirm · C Tomorrow empty + toast · D Tomorrow picked | ticketed — not built |
| 03 | Motivation | A first Check-In, empty · B empty + toast · C picked | ticketed — not built |
| 04 | Vibe | A first Check-In, empty · B empty + toast · C picked · D five picked, sixth tapped (scrolled) | ticketed — not built |
| 05 | Filters | A first Check-In, pre-filled · B no gender + toast · C narrowed | ticketed — not built |
| 06 | Uplift + commit (carries `Find my date`) | A on entry · B typing, keyboard open · C contact info blocked · D saving · E save failed | ticketed — not built |

Stories renumbered 13 Oct 2026 when Today and Tomorrow merged into 02 (were 02–07).

## Backlog

- **Expired Check-In** — what the gate says when a previous window ended without a new Check-In.
- ~~**`check_in_completed` vs `check_in_created`**~~ — **`check_in_created`**, settled in ticket 06 (13 Oct 2026).
- **Uplift detector scope** — social handles and spelled-out numbers are not caught; see ticket 06 → *Open*.
- ~~**Meet-time buffer**~~ — **1 hour, confirmed** 13 Oct 2026 (ticket 02).
- **Back on Today after a gate reopen** — open, not blocking; see ticket 02 → *Open*.
