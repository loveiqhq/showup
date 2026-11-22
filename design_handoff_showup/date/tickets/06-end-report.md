# [Date 06] End date and report a problem — every way one person ends the date

**Parent:** [Epic] 07a Match & date confirmation (`date/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `date-06-end-report-attachment.zip` (the handoff folder, `design_handoff_showup/`) · `date-06-end-report-spec-sheet.png`
**Blocks:** Date 08 What the other side sees (reads `end_reason`) · Date 03's `End date` link and the `report` gate entry (both hidden / reserved until this ships) · flow 10 (every end here lands there)
**Blocked by:** Date 01 Date screen (`ScreenMatchCoord`, the footer slot, the Report entry card) · Date 03 Arrival check-in (`useArrivedGate`, `CheckInConfirmSheet` `note`, the footer swap)

## Description

One person ends the date from the Date screen. Two sheets lead there:

- **End date** — the End Date bar after check-in, or the `End date` link under the Check-in bar before it. Asks **How did it go?**: *We met* or *My date isn't showing up*.
- **Report a problem** — the entry card on the Date screen. *My date isn't showing up* or *I don't feel comfortable*.

Three endings, each moving **this user to flow 10** and **the match to a Date 08 screen**: we met · the match didn't show · this user didn't feel comfortable. **This ticket stores how the date ended; it decides nothing about anyone's Show-up Rate** (epic rule 6).

**Ten frames, one ticket** — two sheets, one set of shared steps:

| | Frame | When | Reference (`ScreenMatchCoord …`) |
|---|---|---|---|
| A | End Date bar | checked in | `coordOpen` |
| B | How did it go? | End Date / End date tapped | `coordOpen initialWrapOpen` |
| C | We met | B → We met | `… wrapStep="met"` |
| D | Check in first | not showing up / not comfortable, not checked in | `coordOpen checkInFlow initialWrapOpen wrapStep="waiting"` — **Date 03's sheets, consumed** |
| E | Not here yet? — before meet time | checked in, before the binding meet time | `coordOpen initialWrapOpen wrapStep="waiting"` |
| F | Not here yet? — after meet time | checked in, from the binding meet time | `… pastMeet` |
| G | Thanks for waiting | F → end | `coordOpen pastMeet initialWrapOpen wrapStep="noshow"` |
| H | Report a problem | entry card tapped | `coordOpen initialReportOpen` |
| I | I don't feel comfortable | H → option, checked in | `… reportStep="uncomfortable"` |
| J | End didn't go through | any end CTA failed | `coordOpen pastMeet initialWrapOpen wrapStep="noshow" endError` |

## Decided before build (10 Nov 2026)

- **One check-in gate for both problem routes.** *My date isn't showing up* (from either sheet) and *I don't feel comfortable* run Date 03's gate first when this user has not checked in (frame D). Not yet → back to the menu. Yes → done sheet → Close continues to the step. Undo → the Date screen with the Check-in bar. `useArrivedGate` now has **four** entries: Date screen · chat chip · Report · End date.
- **We met needs no check-in.** Two people who both confirm in the review count as met (07a decisions, Show-up Rate logic).
- **The no-show end unlocks at the binding meet time.** Before it, the button is disabled and reads `You can end the date from {time}`. From it, `It’s been 10+ minutes — end date`. **The 10 minutes are guidance, not measured.** `{time}` is the latest **agreed** time (epic rule 2); a pending suggestion moves nothing.
- **The wait guidance exists once.** The kit had two copies (End date and Report) with different copy. Now `NoShowGuide` + `NoShowConfirm`, rendered by both sheets. Copy below is the only version.
- **No verdict on the no-show screen.** "This is on them" is removed — flow 10 may decide otherwise. The screen only promises the ender's own rate.
- **Head out now ends the date.** This user → flow 10 (review); the match → Date 08 `date-ended-discomfort`. What happens next gains `Review your date next…` so the review is no surprise.
- **Stay for now stores an internal flag** against the date (evidence for flow 10). The date continues; nothing is shown to either user.
- **The match's Date 08 screen follows `end_reason` alone** (17 Nov 2026): met → `date-closed-positive` · partner_no_show → `you-no-showed`, **whether or not the match has checked in** — it is this user's claim, and flow 10's automated check (both check-ins + chat contact) decides · uncomfortable → `date-ended-discomfort`.
- **Parked, not built:** *I'm in danger* (AI safety call, 2 Nov 2026) and *Something else* (free text for a safety team). The menu has two options.
- **The End Date bar has one state.** The kit's "Date complete · Browsing's unlocked · Rate" variant is **deleted** — Search stays blocked until the review (epic rule 5).

## Layout — read this before building

⚠ **Sheets over the Date screen.** Date screen: one scroll → sticky footer (Check-in bar or End Date bar ①) → TabBar. Every step is a `ModalShell` bottom sheet, height from content. Steps replace each other inside the sheet; Back links go one step up. Never a separate screen.

⚠ **Shared steps, one copy each:** `NoShowGuide`, `NoShowConfirm`, `UncomfortableStep`, `EndErrorBox`. `DateWrapSheet` and `ReportProblemSheet` both render them. A second copy is a bug.

⚠ **The error box ⑩ sits between the content and the CTA.** Buttons keep their order, size and label.

⚠ **No absolute Y positioning.** On 375 × 667 frames E, F and I scroll inside the sheet; the CTAs stay reachable.

📎 All specs are on the attached **date-06-end-report-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–⑫ across ten frames. Black callouts are built here; **red is the failure state**; **violet callouts open another ticket's UI or are parked**. The design system has colour and type tokens only, with no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/date/screen-wrap-report-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. Frame props are in the table above.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **The check-in gate (D)** — Date 03's `CheckInConfirmSheet` (with `note`) and `CheckInDoneSheet`. Consumed.
- **What the match sees** — Date 08's three screens. This ticket sends the end; 08 draws it.
- **Flow 10 Review your date** — every end here lands there.
- **Status bar and home indicator** are mocks. Anchor to the safe-area insets.

## Copy — final strings

`{name}` is the match's first name. `{time}` is the binding meet time.

**A · End Date bar:** `Your date with {name}` · `Tap End Date - when your date is finished.` · button `End Date`

**B · How did it go?:** `How did it go with {name}?` · `We met — date is finished` / `Only once you’ve actually met in person.` · `My date isn't showing up` / `They're not here yet — here's what to do and how the wait works.` · `Cancel`

**C · We met:** `Thanks for showing up` · `You are wonderful for showing up to {name}.` `We hope you had a great time together.` · What happens next: `Please review your date now.` · `Search for new dates resumes after the review.` · note `This is only for dates that happened. Need to call it off before meeting? Use Cancel date in the chat menu. Ending a date you didn’t attend counts as a no-show and lowers your Show-up Rate.` · `End Date → Review` · `Cancel`

**D · Gate note (Date 03's prop):** `Check in first, so we can confirm you were there.`

**E/F · Not here yet?:** `Back` · `Not here yet?` · ① `Message {name} to ask where they are. A quick “on my way?” clears up most late arrivals.` ② `Please give it at least 10 minutes. Something can always come up.` ③ `Please check you are at the right location.` ④ `If you get no reply and you’re 10 minutes past your meet time — you’re free to end the date and leave.` · `Message {name}` · before the meet time `You can end the date from {time}` (disabled) · from the meet time `It’s been 10+ minutes — end date`

**G · Thanks for waiting:** `Thanks for waiting` · `You gave {name} time. Leaving now won’t lower your Show-up Rate.` · What happens next: `Please give us a little more information in the review now - helps us to determine the consequences for your date partner.` · `No penalty to you — your Show-up Rate stays where it is.` · `Searching for a new date will resume.` · `End date and review` · `Back`

**H · Report a problem:** entry card `Report a problem` `If you feel unsafe or uncomfortable, let us know.` · sheet `Report a problem` · `Everything here is confidential. We've got you.` · `My date isn't showing up` (sub as in B) · `I don't feel comfortable` / `If you don't feel comfortable in the situation - head out now. That's ok!` · `Cancel`

**I · I don't feel comfortable:** `Back` · `We're sorry to hear that.` · `You don't have to explain a thing — you showed up, so you're free to head out now, with no penalty to your Show-up Rate.` · What happens next: `No penalty to you — you showed up, and that's what counts.` · `Head out whenever you're ready — no need to say goodbye.` · `Review your date next. What you tell us stays confidential.` · `Head out now` · `Stay for now`

**J · End failed:** `Couldn't end the date. Check your connection and try again.`

Product terms capitalise exactly: **Show Up · Show-up Rate**. UI says **Check-in**; tickets and tracking say **arrival check-in**.

## Behaviour

**Entry points**

- **End Date** on the End Date bar (after check-in) and the **`End date`** link under the Check-in bar (before) open B. **Un-hide Date 03's link** with this ticket.
- **Report a problem** card on the Date screen opens H. Available the whole time the date is active.
- `Cancel`, the scrim or a swipe-down on B or H closes the sheet; **nothing is stored**.

**B · How did it go?**

- `We met — date is finished` → C. No gate.
- `My date isn't showing up` → not checked in: D, then E/F · checked in: E/F.

**C · We met**

- `End Date → Review` ends the date (`end_reason: met`) → this user to flow 10; the match to Date 08 `date-closed-positive`.
- `Cancel` returns to B.

**D · Check in first** (Date 03's gate, `note` prop)

- `Not yet` / scrim → back to the menu (B or H). Nothing stored.
- `Yes, I've arrived` → Date 03's flow (location read, stored, done sheet). `Close` on the done sheet continues to the step the user picked (E/F or I).
- `Undo check-in` deletes the check-in and lands on the **Date screen with the Check-in bar** (Date 03 rule) — the sheet closes.

**E/F · Not here yet?**

- `Message {name}` closes the sheet and opens the chat (Date 02). **Hidden while the chat is locked** (before meet − 1:30 and before any check-in — Date 03, 16 Nov); the end button moves up.
- The end button is **disabled before the binding meet time** and shows `You can end the date from {time}`. From the binding meet time it reads `It’s been 10+ minutes — end date` and opens G. Re-evaluate when the sheet is opened and when the clock passes the meet time while it is open.
- `Back` → the menu.

**G · Thanks for waiting**

- `End date and review` ends the date (`end_reason: partner_no_show`) → this user to flow 10. The match to Date 08 **`you-no-showed`**, whether or not they have checked in.
- `Back` → E/F.

**I · I don't feel comfortable**

- `Head out now` ends the date (`end_reason: uncomfortable`) → this user to flow 10; the match to Date 08 `date-ended-discomfort`.
- `Stay for now` stores an internal discomfort flag against the date, closes the sheet, the date continues. Tapping it again later stores one more flag.
- `Back` → H.

**J · Ending**

- An end is a server request. Disable both buttons while it runs. **The date is not ended until the server confirms.**
- Failure → the sheet stays on the same step with `EndErrorBox` above the CTA; the CTA keeps its label and retries. Back still works.
- On success: both users' date state is **ended**; Search, Likes and Instant stay blocked for each until their review is done (epic rule 5). The +2 h switch (09) no longer applies to this date.

**After the date has ended** (by either person, any route) the End Date bar, the Check-in bar, the Report card and Cancel this date are gone — the next screen is flow 10 or Date 08.

## Data stored

- **On every end:** `end_reason` (met · partner_no_show · uncomfortable), who ended it, `entry`, timestamp, and both people's check-in state at that moment. Against the date, kept with the rest of the date's evidence.
- **On Stay for now:** a discomfort flag + timestamp + user ID against the date. **Internal only**, never shown to either user.
- No Show-up Rate change is made here. Flow 10 reads this.

## Build inventory

- **Client:** `DateStatusBar` (one state), `DateWrapSheet`, `ReportProblemSheet` (two options), `WrapConfirm` (`secondary`, `error`), `NoShowGuide` (`pastMeet`, `meetTime`, `chatOpen`), `NoShowConfirm`, `UncomfortableStep`, `EndErrorBox`; wiring the gate (`useArrivedGate` with `wrapup` / `report` entries); un-hiding Date 03's `End date` link.
- **Server:** the end endpoint (validates: date active · the no-show end only from the binding meet time · the no-show and uncomfortable ends only with a stored check-in), the ended state for both users, the Date 08 screen choice from `end_reason` + `partner_checked_in`, the discomfort flag store, cancelling the +2 h job for the date.
- **Not built here:** Date 03's gate sheets · Date 08's screens · flow 10 · the parked *I'm in danger* and *Something else* routes.

## Acceptance criteria

- [ ] End Date (bar) and End date (Check-in bar link) both open B; the link is **no longer hidden**
- [ ] The End Date bar has **one** state; there is no "Date complete" or Rate variant anywhere
- [ ] `We met` reaches C with **no** check-in gate
- [ ] `My date isn't showing up` (from B **and** from H) and `I don't feel comfortable` run the check-in gate when not checked in; Not yet returns to the menu and stores nothing
- [ ] After Yes → Close on the gate, the user lands on the step they picked, not the Date screen
- [ ] Before the binding meet time the end button on E is disabled and shows `You can end the date from {time}` with the **agreed** time; from that time it shows `It’s been 10+ minutes — end date` and opens G
- [ ] `Message {name}` is hidden while the chat is locked
- [ ] `NoShowGuide`, `NoShowConfirm`, `UncomfortableStep` and `EndErrorBox` exist **once each** and are rendered by both sheets
- [ ] G contains **no** statement about the match's fault; the copy matches above exactly
- [ ] `End Date → Review`, `End date and review` and `Head out now` end the date only after the server confirms; a failure shows J and the CTA retries
- [ ] After any end, this user is in flow 10; the match sees the Date 08 screen for the `end_reason` (met → `date-closed-positive` · partner_no_show → `you-no-showed` · uncomfortable → `date-ended-discomfort`)
- [ ] `Stay for now` stores a flag, closes the sheet and leaves the date active
- [ ] H shows **two** options; *I'm in danger* and *Something else* are not reachable
- [ ] The server refuses a no-show end before the binding meet time and a no-show / uncomfortable end without a stored check-in, whatever the client sends
- [ ] Copy matches the strings above exactly
- [ ] **Evidence of done:** screenshots of **A–C and E–J** at **375 × 667, 390 × 844 and 430 × 932** — 27 images (D is Date 03's). On 375 × 667, state that the CTAs on E, F and I are reachable. Plus one recording: not checked in → Report → My date isn't showing up → Yes → Close → guide → end disabled → (clock past meet time) → end → End date and review → flow 10, and the match's phone showing the right Date 08 screen.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **N — Wrap-up & Safety**). Use them exactly — do not invent names, and do not restate a payload the registry already defines. **Registry 1.4.36 / taxonomy 1.4.29** — three events **NEW**, three **respecified**, five **retired**, three value sets **NEW**, one value set extended.

| What | Event | Payload |
|---|---|---|
| B opens | `wrapup_opened` | **NEW event** · `date_id`, `entry` (§45 — **NEW**), `checked_in`, `minutes_vs_start` |
| B option tapped | `wrapup_outcome_selected` | **respecified** · `date_id`, **`outcome`** (§43 — **NEW**), **`checked_in`** **NEW** |
| H opens | `problem_report_opened` | **respecified** · `date_id`, **`checked_in`**, **`minutes_vs_start`** **NEW** |
| H option tapped | `problem_category_selected` | **respecified** · `date_id`, **`category`** (§44 — **NEW**), **`checked_in`** **NEW** |
| Gate shown / used | `arrival_confirm_shown` · `arrival_confirm_submitted` · `arrival_confirmed` · `arrival_undone` | Date 03's events · `entry`: **`report`** (live now) or **`wrapup`** (§41 — **NEW value**) |
| Date ended | `date_ended` | **NEW event, server** · `date_id`, `end_reason` (§43), `entry` (§45), `ender_checked_in`, `partner_checked_in`, `minutes_vs_start` |
| Stay for now | `problem_stay_flagged` | **NEW event, server** · `date_id`, `minutes_vs_start` |

There is no `screen_viewed` for B–J — they are sheets on `date_screen`, not screens.

**Rules for this screen**

- `minutes_vs_start` counts from the **latest agreed** meet time; negative = before.
- `wrapup_outcome_selected` and `problem_category_selected` fire **on the tap**, before the gate or the confirm step. Abandoning later is the gap to `date_ended`.
- `Cancel`, scrim, `Back`, `Message {name}` and a tap on the disabled end button fire **nothing**.
- **`date_ended` fires once, on server success only.** A failed end (J) fires nothing new; a retry that succeeds fires it once.
- ⚠ **Renamed value — check any emitter already written.** `problem_category_selected.category` was `unsafe | uncomfortable | waited`; it is now §44 and **`waited` is `partner_no_show`**. `unsafe` and `something_else` are **reserved** (parked).
- ⚠ **Removed payload.** `wrapup_outcome_selected` lost `met`, `left_early`, `partner_no_show` (bools). It now carries `outcome`.
- **Retired — do not fire, do not reuse the names:** `wrapup_prompted` (no 30-min timer) · `wrapup_confirmed` (now `date_ended`) · `grace_period_started` · `grace_period_expired` (nothing measures the 10 minutes) · `no_penalty_exit_granted` (07a stores, flow 10 decides).
- **Must NOT fire from this ticket:** `date_ended_in_chat` (Date 02) · `date_cancelled` (Date 07) · `safety_call_*` (parked) · `user_reported` (parked with Something else) · any flow 10 review event.
- **Never in a payload:** coordinates, distance, place, free text.

**Tracking acceptance criteria**

- [ ] Every family N event above carries `date_id`; `outcome`, `category`, `entry` and `end_reason` use only §43 / §44 / §45 values
- [ ] The gate's arrival events carry `entry: report` from H and `entry: wrapup` from B
- [ ] `date_ended` fires exactly once per ended date and never for a failed attempt
- [ ] No retired event is emitted

**Currently in the build: none of it.** Every row above is `Not built`.

## Out of scope

The Date screen, the footer slot and the Report entry card (Date 01). The chat and its End the date (Date 02). The check-in gate (Date 03). Cancel this date (Date 07). The match's screens (Date 08). The +2 h switch (Date 09). Flow 10 Review your date and every Show-up Rate outcome. *I'm in danger* and *Something else* (parked). Push, SMS, WhatsApp and email copy.

## Dependencies

- **Date 01:** `ScreenMatchCoord`, the footer slot, the Report entry card
- **Date 03:** `useArrivedGate`, `CheckInConfirmSheet` `note`, `CheckInDoneSheet` `onUndo`, the footer swap, the `End date` link
- **Date 08:** the three screens the server routes the match to
- **Shared:** `ModalShell`, `Icon`
- **Server:** the date state machine (active → ended), the end endpoint and its checks, the discomfort flag, cancelling the +2 h job
- **Tracking registry 1.4.36 / taxonomy 1.4.29**

## Open before the sprint

1. **Show-up Rate display format** — still open project-wide (`JIRA-HANDOFF.md` → *Open decisions every ticket must carry*). C, G and I name the rate in copy only, with no value, so they are unaffected.
2. **Copy consistency, not yet decided:** C still says `End Date → Review` while G says `End date and review`; C's note says `Cancel date in the chat menu`, but Cancel this date is a link at the end of the Date screen; the bar says `End Date` while Date 02 / 03 say `End date`. Kept as the kit has them until decided.
