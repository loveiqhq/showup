# [Epic] 48hrs Check-In flow

**Type:** Epic · **Priority:** High
**Handoff folder:** `design_handoff_showup/checkin/` — read `README.md` first
**Design source:** Show Up UI kit, section `04 · 48hrs Check-In` (`ui_kits/show-up/screens-checkin.jsx`)

## What this epic delivers

Everything between "the user has a profile" and "the user can be found". A completed Check-In says when the user can date in the next 48 hours (today and tomorrow), what they are looking for, and who they want to meet. It is what puts them into date search.

**Profile creation done ≠ ready to date** (decided 12 Oct 2026, binding in `design_handoff_showup/CLAUDE.md` → *Check-In gates date search*). The first Check-In opens straight after Profile 23, but the user may close it and use the app **without date search**. Until a Check-In is committed, the Search tab is the *date search not available* screen.

## Shape of the flow

One intro, then six steps on one shared progress bar (`StepProgress steps={6}`; the intro does not count).

| # | Screen | `step_id` (§28) | Required to continue |
| --- | --- | --- | --- |
| Story | Screen | `step_id` (§28) | Required to continue |
| --- | --- | --- | --- |
| 01 | Intro — bridge, no progress bar | `intro` | — |
| 02 | Today availability | `today` | optional — empty raises the *no hours today* confirm |
| 02 | Tomorrow availability | `tomorrow` | ≥ 1 hour |
| 03 | Motivation | `motivation` | exactly 1 — single-select (13 Oct 2026) |
| 04 | Vibe | `vibe` | ≥ 1, max **5** — multi-select over §31 (⚠ superseded from "max 3" on 13 Oct 2026 by Check-In 04) |
| 05 | Filters | `filters` | ≥ 1 gender (mandatory); age and distance always valid — sexuality filter removed (⚠ superseded from "always pre-set" on 13 Oct 2026 by Check-In 05) |
| 06 | Uplift — "why should someone date you over others?" — **last step, carries `Find my date`** | `uplift` | optional — ≤ 100 characters, no emails, phone numbers or links (13 Oct 2026, Check-In 06) |

**Uplift is the last step** (13 Oct 2026 — the heads-up time step was removed). `Find my date` is its CTA, so the Check-In is committed on 06.

⚠ **Stories renumbered 13 Oct 2026:** Today and Tomorrow are one story (02 Availability), so Motivation → 03, Vibe → 04, Filters → 05, Uplift → 06 (were 04–07). `step_id` values do not change. Uplift keeps its ambient backdrop as the flow's "moment to shine".

Plus one screen outside the steps: **date search not available** — the Search tab for a user with no committed Check-In (ticket 01, state B).

## Stories

Each story is one screen with all its states, a spec sheet and a reference file. Numbering matches the handoff folder.

| # | Story | States | Status |
| --- | --- | --- | --- |
| 01 | Intro + date search not available | 2 — A intro · B closed (Search tab) | ticketed — not built |
| 02 | Availability — Today + Tomorrow | 4 — A Today picked · B Today empty + *no hours today* confirm · C Tomorrow empty + toast · D Tomorrow picked | ticketed — not built |
| 03 | Motivation | 3 — A first Check-In, empty · B empty + toast · C picked | ticketed — not built |
| 04 | Vibe | 4 — A first Check-In, empty · B empty + toast · C picked · D five picked, sixth tapped (scrolled) | ticketed — not built |
| 05 | Filters | 3 — A first Check-In, pre-filled from the profile · B no gender + toast · C narrowed | ticketed — not built |
| 06 | Uplift + commit | 5 — A on entry, empty · B typing, keyboard open · C contact info blocked · D saving · E save failed | ticketed — not built |

State counts for 03–06 are confirmed in each story's ticket.

## Built once, not per screen

A second copy of any of these is a bug.

- **`CheckInCloseButton`** — the ✕ on every Check-In screen. Built by 01. Leading slot on the intro; **trailing** slot on 02–06, with back leading
- **`CheckInArrow`** and **`NumberedRow`** — built by 01. `CheckInArrow` is on **every** Check-In CTA, step footers included (13 Oct 2026)
- **The step shell** — StatusBar → `AppHeader` title `Check-in`, back + ✕ → `StepProgress steps={6}` → content → `CheckInFooter`. Built by 02, consumed by 03–06
- **`CheckInFooter`** — a **full-width** sunset Button with `CheckInArrow`, plus the floating validation toast. Label is a prop (`Continue` / `Find my date`). Built by 02. ⚠ *Superseded 13 Oct 2026 (Check-In 02): this line said "small bottom-right `NextButton`", which the kit never drew.*
- **`HowItWorks`** — the expandable explainer. Built by 02
- **The availability grid** — one `AvailabilityStep` for Today and Tomorrow; `day` and `startHour` are props. Built by 02
- **`CoachNote`** — the warm / lilac guidance box. Built by 03, consumed by 04 and 06
- **`CHECKIN_MOTIVATIONS`** — the five §30 motivations. Built by 03; Instant reads the same constant
- **`CHECKIN_VIBE_GROUPS` / `CHECKIN_VIBE_LABEL`** — the twenty §31 vibes in three groups. Built by 04; Search's *vibe today* card and Instant read the labels from it, never their own strings
- **`LifePill`** — the multi-select chip. Built by **Profile 22**, consumed by 04 and 05 as is; the Check-In builds no chip of its own
- **`RangeSlider` / `SliderThumb`** — the age and distance sliders (44 hit target, adjustable). Built by 05; Search's filter sheet consumes them
- **The filter-settings record** — one per user, shared by the Check-In Filters step and Search's filter sheet. Built with 05
- **`CheckInFooter` `loading` + `error`** — the commit's saving spinner and save-failed card. Added by 06 to the 02 component; a variant, not a fork
- **The commit endpoint** — one Check-In per `window_id`, idempotent, with the server-side contact-info check and time resolution. Built with 06
- **The Check-In state machine** — step order, re-entry routing (the intro-passed flag) and the gate (see rules). Build before 02

## Flow-level rules — every story inherits these

**1. Check-In gates date search.** Search and Instant are unavailable until a Check-In is committed. The tabs are **not disabled**: tapping either reopens the Check-In.

**2. Every Check-In screen has a ✕.** It lands on the Search tab's *date search not available* state and fires `check_in_closed` with that screen's `step_id`. No confirmation.

**3. Nothing is kept as a draft — decided 13 Oct 2026.** ⚠ *Supersedes the 12 Oct rule "closing mid-flow keeps a draft".* **Availability (Today, Tomorrow) and the Uplift text start empty on every entry**; hours picked in a closed flow are discarded. **Motivation, Vibe and Filters pre-select the last answer given**, saved on every accepted Continue, whether the flow was later closed with ✕ or committed. **Reopening from the gate lands on Today once the user has passed the intro** (tapped its CTA) at least once; before that, on the intro. No message anywhere mentions earlier answers.

**4. ~~Stale draft hours are dropped silently.~~** **Deleted 13 Oct 2026** with the draft (rule 3): no hours are kept, so none can be stale.

**5. Returning users start pre-filled — on Motivation, Vibe and Filters only.** Their last answer is pre-selected (rule 3). Availability and Uplift are never pre-filled.

**6. Continue explains itself.** On an invalid step the button dims and a tap raises the toast naming what is missing. Today is the exception: empty is valid and raises the confirm instead.

**7. Today starts at the next full hour** (13 Oct 2026). An hour that has started is never offered: with the 1-hour meet buffer it cannot produce a date. ⚠ *Superseded 13 Oct 2026 (Check-In 02): was "starts at the current hour".*

**7a. A Check-In day runs 7am → 7am** (13 Oct 2026). Both grids end at the 6am slot; night hours belong to the evening before. "Today" is the 7am block that contains the next full hour.

**8. One headline size per group** (`CLAUDE.md`). The intro is a bridge: **34**. **Steps are 30** — settled 13 Oct 2026 in story 02; Vibe, Filters and Uplift move from 28.

**9. Instant opens the 48hrs Check-In** until Instant is ticketed. Its own 15-second flow is untouched.

**9a. Motivation is single-select and shows on the profile only while a Check-In is active** (13 Oct 2026, story 03). Values §30, one vocabulary with Instant and the `dating_intent` filter.

**9b. Uplift is optional, 100 characters, and never carries contact info** (13 Oct 2026, story 06). Emails, phone numbers and links are blocked live with one string; the server repeats the check. Inspirations are not tappable.

**9c. `Find my date` commits; success goes straight to Search** (13 Oct 2026, story 06). A failed save keeps everything and retries from the same button. Today's hours passing or the window expiring mid-flow are resolved server-side with no UI.

**10. One active Check-In at a time** — already enforced by the backend. Don't relitigate.

**No absolute Y positioning, anywhere.** Nested flex columns, one `flex: 1` element per screen, chrome on `env(safe-area-inset-*)`.

**Copy is final and lives in the stories.** The UI string is `Check-in` (as drawn, by decision); the product term in docs is **Check-In**.

## Tracking

Family **H — 48hr Check-In** in `design_handoff_showup/tracking/events.json`, named **`check_in_*`** since taxonomy 1.4.10. Value sets: §27 `check_in_gate_target`, §28 `check_in_step` (in flow order). Screens: §11 `checkin_intro`, `checkin_search_unavailable`, `checkin_today`, `checkin_tomorrow`; `checkin_motivation` (03); each step story adds its own §11 row before its ticket. §30 `check_in_motivation` (03). §29 `check_in_no_hours_today_action` (02).

- `check_in_started` — intro CTA only. A gate reopen after the intro has been passed skips the intro and does not fire it
- `check_in_closed` — every ✕, with `step_id` (`has_draft` removed 13 Oct 2026)
- `check_in_step_answered` — per step Continue; never carries `intro`
- `check_in_gate_tapped` — every reopen from the gate, with `intro_passed` (renamed from `has_draft` 13 Oct 2026)
- `availability_slots_set` — per accepted Continue on Today / Tomorrow (respecified in 02, taxonomy 1.4.12)
- `check_in_no_hours_today_resolved` — the *No hours today?* answer (02)
- `motivation_selected` — per accepted Continue on Motivation, `motivation_id` (§30), Class 1 (respecified in 03, taxonomy 1.4.13)
- **Commit** — `check_in_created` (server, shipped) is **the one commit event**; `check_in_completed` and `uplift_shown` are **retired** (06, taxonomy 1.4.16). `check_in_commit_failed` — once per failed save, `reason` §32. §11 `checkin_uplift`

Cite event names in every ticket; never describe them. Never run Jira's "improve description" over a pasted ticket.

## Device matrix

375 × 667 · 390 × 844 · 430 × 932. Check 375 × 667 first. The intro never scrolls; steps scroll only where their ticket says so (Vibe's chip groups scroll on every device — ticket 04).

## Dependencies

- **Profile 23 (Future)** — sets the profile-creation-complete flag and navigates to the intro
- **Server: a Check-In window opens when profile creation completes**, so `window_id` is never null
- **Saved last answers for Motivation and Vibe**, server-side, written on each accepted Continue. **Filters** are the user's filter settings, shared with Search (Check-In 05)
- **Server: two-way gender matching** — the sexuality filter was removed on that basis (Check-In 05)
- **The intro-passed flag**, server-side, set by the intro CTA and read by the gate
- Shared `TabBar` with an `onTab` handler; the date search surface that a committed Check-In unlocks

## Definition of done — epic level

- [ ] A user finishing profile creation can commit a Check-In end to end on all three devices and land in date search
- [ ] Closing at any screen and reopening lands on Today (intro passed) with **empty availability** and Motivation, Vibe and Filters pre-selected from the last answer; no message about earlier answers
- [ ] Without a committed Check-In, Search and Instant always open the Check-In, and Settings works
- [ ] `CheckInCloseButton`, the step shell, `CheckInFooter`, `HowItWorks` and the availability grid exist **once each**
- [ ] Steps 02–06 share one headline size (30)
- [ ] Every event fires per `events.json`, with no invented names

## Out of scope

Instant's own 15-second flow. Date search itself once unlocked. Notifications that prompt a Check-In (`check_in_prompted`). Editing a committed Check-In.

## Open — epic level

- **Expired Check-In.** When a 48-hour window ends with no new Check-In, does the gate show the first-time copy or "Check in again…"?
- ~~**Step headline size**~~ — **30**, settled in story 02 (13 Oct 2026).
- ~~**`check_in_completed` vs `check_in_created`**~~ — **`check_in_created`**, settled in story 06 (13 Oct 2026); `check_in_completed` retired.
- ~~**Meet-time buffer**~~ — **1 hour, confirmed** 13 Oct 2026 (story 02). The *How it works* example stands.
- ~~**Time zones**~~ — moot since 13 Oct 2026: no hours are kept between sessions.
- **`quick_checkin_completed`** (Instant) keeps the old prefix until Instant is ticketed.
