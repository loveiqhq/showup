# [Date 09] Your date has ended: the +2 h switch to the review

**Parent:** [Epic] 07a Match & date confirmation (`date/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `date-09-timed-out-attachment.zip` (the handoff folder, `design_handoff_showup/`) · `date-09-timed-out-spec-sheet.png`
**Blocks:** nothing. This is the last story in 07a's build order.
**Blocked by:** Date 06 End date and report a problem (the end endpoint, the ended state, per-date serialisation) · Date 08 What the other side sees (`PartnerEndedScreen`) · flow 10 Review your date (`ScreenRating`). See *Dependencies*

## Description

Every date ends in the review (flow 10). Usually one person ends it: End date, a report or a cancel. This story covers the fallback when **nobody does**. **2 hours after the latest agreed meet time**, the server closes the date. Both people then see one full screen, **Your date has ended**, whose only exit is **Proceed to review** into the normal review.

| At the switch | Person 1 sees | Person 2 sees | Both then go to |
|---|---|---|---|
| Both checked in | `date-timed-out` | `date-timed-out` | normal review (`rating`) |
| One checked in | `date-timed-out` | `date-timed-out` | normal review (`rating`) |
| Nobody checked in | `date-timed-out` | `date-timed-out` | normal review (`rating`) |
| Somebody already ended / cancelled the date | — (switch does not run) | — | the existing flows: Date 02, 06, 07, 08 |

**One screen, one frame.** Nobody said anything about the other person, so the switch gives no verdict and no hint of one. What happened is decided by the answers in the two reviews and by flow 10's automated check (check-ins + chat contact).

## Decided before build (17 Nov 2026)

- **Date 09 has a screen.** Previously it was "logic only, no spec sheet". The epic and the date `README.md` are updated to match.
- **Same screen and same review for both people, whatever the check-ins.** Only cases where nobody acted reach the switch; every case where someone acted already has its own flow.
- **Shown at once, like Date 08.** The screen replaces whatever is open (the Date screen, the chat, any sheet). It has no back, close or swipe-down. **Proceed to review** is the only exit.
- **First to the server wins.** An end, report or cancel that reaches the server after the switch is ignored. That user sees this screen like the other, with no error. Likewise, if the switch arrives second, it does nothing.
- **A check-in in flight counts.** If a check-in was stored (Yes tapped) but the done sheet is still open at the switch, the check-in stays as evidence. The match is **not** notified, because the date is over. The sheet closes under this screen and Undo is gone.
- **No last message.** It would count as chat contact after the date, could steer the review, and would give a channel after an uncomfortable date. *Flow 10 backlog:* possibly a short note during the review.
- **No warning before the switch.** Nothing on the Date screen counts down to +2 h (no-timer rule, 2 Nov 2026).
- **Push: not in this story.** Both people get one, but it is built in the notifications pass after the main flows, together with review reminders.

## Layout — read this before building

⚠ **Reuse Date 08's `PartnerEndedScreen` with a fourth payload.** The anatomy is identical to Date 08 C (`ScreenDateClosed`). The payload changes the badge (clock), the headline, the sub and the two rows. Never write a second copy of the layout.

⚠ **Full screen, not a sheet.** Column: hero card → rows → **flex spacer** → CTA. The CTA sits at the bottom via the spacer, never at an absolute Y. On 375 × 667 the column scrolls and the CTA is the last item.

📎 All specs are on the attached **date-09-timed-out-spec-sheet.png**: type, colour, sizing, spacing and layout rules, keyed ①–⑦. Black callouts are built here; **violet callouts open another ticket's UI**. The design system has colour and type tokens only, with no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/date/screen-timed-out-reference.jsx` (`ScreenDateTimedOut`) — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. Props: `name` = the match's first name, `meetTime` = the latest agreed meet time.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **The review:** flow 10 builds `ScreenRating` and the automated no-show check. This ticket only routes into it.
- **The other ways a date ends:** Date 02 (chat), 06 (End date, report), 07 (cancel) and 08 (what the other side sees) own those flows. This ticket only has to stop the switch when one of them got there first.
- **Push copy and review reminders:** built in the notifications pass.
- **Status bar and home indicator** are mocks. Anchor to the safe-area insets.

## Copy — final strings

`{name}` is the match's first name. `{time}` is the latest agreed meet time, HH:mm, in the device's 24 h / 12 h setting.

- Headline: `Your date has *ended.*`
- Sub: `We closed your date with {name} automatically, 2 hours after the meet time ({time}).`
- Row 1 (heart): `If you met, we hope you had a great time. **Either way, tell us how it went in the review.**`
- Row 2 (search): `Once the review is done, you're free to **search for your next date.**`
- CTA: `Proceed to review`

Product terms capitalise exactly: **Show Up · Show-up Rate**.

## Behaviour

**The switch (server)**

- When a date is created, schedule one job at **latest agreed meet time + 2 h**.
- When an agreed time change lands (Date 05 `change_agreed`), **move** the job to the new time + 2 h. A pending suggestion moves nothing. A place change does not move it.
- Any end (Date 02 chat end, Date 06 end, Date 07 cancel) **cancels** the job.
- When the job runs, it re-checks the state inside the same per-date serialisation as Date 06's ends. If the date is still active, it sets the date to **timed out** for both people, stores `end_reason: timed_out`, and both users' state becomes **ended, review pending**. If the date is no longer active, it does nothing and fires nothing.
- It notifies both clients (realtime). The push trigger is **not** built here.
- The job must be idempotent. A retry after a crash never ends a date twice or fires `date_ended` twice.

**The screen (client)**

- On the realtime notification, or on any app open while the user's state is *ended, review pending* with `end_reason: timed_out`, show `date-timed-out` **at once**, over whatever is open. Open sheets and the chat close; an unsent chat draft is discarded.
- No back, no close, no swipe; the Android back button does nothing. The TabBar is not shown.
- `Proceed to review` → flow 10 `ScreenRating` (normal review), for both people.
- Until that person's review is sent, every app open returns to this screen, or to the review if it was started. Search, Likes and Instant stay blocked (epic rule 5).

**Edge cases**

- **An end or cancel reaches the server after the switch:** the server returns *already ended* and stores nothing. That client shows `date-timed-out` with no error message. Date 06's `EndErrorBox` is **not** shown for *already ended*.
- **The switch reaches the server after an end:** the job finds the date ended and does nothing. The users see the Date 06 / 07 / 08 screens as usual.
- **A check-in sheet is open at the switch:**
  - Guard open (nothing stored) → it closes, nothing is stored.
  - Done sheet open (stored, not closed) → the record stays, the match is **not** notified, and Undo is no longer possible.
- **A change picker or an open suggestion at the switch:** closes; the suggestion stays unanswered and is not shown again.
- **Offline at the switch:** the screen shows on reconnect or next open, from the server state, never from a cached Date screen.

## Data stored

- On the date: `end_reason: timed_out`, the end timestamp, and the state *timed out*.
- Nothing new on the users beyond *ended, review pending* (Date 06's state, reused).
- Check-ins, chat and suggestions are kept as they are, as evidence for flow 10 (epic rule 6).

## Build inventory

- **Server:** the +2 h job (schedule on create · move on `change_agreed` · cancel on any end · idempotent); the *timed out* state in the date state machine; the race rule inside Date 06's per-date serialisation; *already ended* returned to a late end or cancel; the realtime notification to both; `date_ended` with `timed_out`.
- **Client:** the fourth payload on `PartnerEndedScreen`; routing *ended, review pending + timed_out* to it; the forced presentation over any screen or sheet; the no-exit rule; mapping *already ended* to this screen instead of an error; closing the check-in sheet without notifying the match; routing `Proceed to review` into flow 10.
- **Not built here:** push and review reminders (notifications pass) · flow 10 · Date 06 / 07 / 08 screens.

## Acceptance criteria

- [ ] A date nobody ended moves both people to `date-timed-out` at **latest agreed meet time + 2 h**: with 0, 1 and 2 check-ins, and with an open discomfort flag
- [ ] An agreed later time moves the switch; a pending suggestion or an agreed place change does not
- [ ] A date ended in the chat, by End date / Report, or by Cancel this date never reaches this screen
- [ ] The screen appears at once over the Date screen, the chat or an open sheet; if the app was closed, it is the first screen on the next open
- [ ] There is no back, close or swipe-down; Android back does nothing; `Proceed to review` is the only tappable element
- [ ] `Proceed to review` opens the normal review (`rating`) for **both** people, never `rating-claimed-noshow` or `rating-you-noshow`
- [ ] Until a person's review is sent, every app open returns to this screen or the started review; Search, Likes and Instant stay blocked
- [ ] An end or cancel that reaches the server after the switch shows `date-timed-out` with no error; a switch after an end does nothing
- [ ] A stored check-in whose done sheet is open at the switch is kept, and the match gets no arrival notification
- [ ] The job runs once per date: a retried job never fires a second `date_ended`
- [ ] The screen is `PartnerEndedScreen` with a fourth payload; switching payloads changes no layout value
- [ ] The Date screen shows no countdown or warning before the switch
- [ ] Copy matches the strings above exactly
- [ ] **Evidence of done:** screenshots at **375 × 667, 390 × 844 and 430 × 932**. On 375 × 667, state that the CTA is reached by scrolling. Plus one recording on two phones, using a test date with a short offset: one checks in, nobody ends → both phones switch at +2 h (one in the chat, one with the app closed) → Proceed to review → normal review on both → kill and reopen before sending → back in the review.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json`. Use them exactly — do not invent names, and do not restate a payload the registry already defines. **Registry 1.4.40 / taxonomy 1.4.32.** No new events.

| What | Event | Payload |
|---|---|---|
| The switch closes the date (server) | `date_ended` — **RESPECIFIED** | `end_reason: "timed_out"` (§43 — **NEW value**), `entry: "auto_switch"` (§45 — **NEW value**), `checked_in_count` (0 / 1 / 2 — **NEW**), `minutes_vs_start`; **no** `ender_checked_in` / `partner_checked_in` |
| Screen shown | `screen_viewed` | `screen_id: "date_timed_out"`, `screen_name: "Date - Timed out"` (§11 — **NEW row**), `referrer_screen_id` |

**Rules for this screen**

- `date_ended` fires **once per date**, from the job, only when it actually closed the date. A job that finds the date already ended fires nothing.
- `checked_in_count` is new for **every** `date_ended`, so Date 06's ends send it too. If Date 06 is already built, add it there.
- `screen_viewed` fires each time the screen is shown, including when the user reopens the app before starting the review. `referrer_screen_id` is the screen they were on; absent on a cold start.
- `Proceed to review` fires nothing — flow 10's `screen_viewed` with `referrer_screen_id: "date_timed_out"` measures it.
- **Must NOT fire from this ticket:** `date_ended` from a client · `date_cancelled` · `date_ended_in_chat` · `arrival_confirmed` for a check-in left open at the switch (it was already fired on Yes; no second one) · any push or reminder event.
- **Never in a payload:** the match's ID, coordinates, free text.

**Tracking acceptance criteria**

- [ ] The job fires `date_ended` with `end_reason: timed_out`, `entry: auto_switch` and `checked_in_count`, once per date
- [ ] Date 06's `date_ended` also carries `checked_in_count`
- [ ] The screen fires `screen_viewed` with `screen_id: date_timed_out` for both people
- [ ] Proceed to review fires no event of its own

**Currently in the build: none of it.** Every row above is `Not built`.

## Out of scope

Push notifications and review reminders (notifications pass). Flow 10, its review deadline and the automated no-show check. The other endings and their screens (Date 02, 06, 07, 08). A last message to the match. Any countdown before the switch.

## Dependencies

- **Date 06:** the end endpoint, per-date serialisation, the *ended, review pending* state, *already ended*
- **Date 08:** `PartnerEndedScreen`, which gets the fourth payload here
- **Date 05:** `change_agreed`, which moves the job
- **Flow 10:** `ScreenRating`. **If flow 10 isn't built when this ships, the switch has nowhere to land** — ship the job disabled until it is
- **Shared:** `Atmosphere`, `StatusBar`, `HomeIndicator`, `Avatar`, `Button`, `Icon`
- **Tracking registry 1.4.40 / taxonomy 1.4.32**

## Open before the sprint

1. **Review deadline** — if one person never sends the review, they stay blocked from Search. Whether the review has a deadline is decided in flow 10; reminders come with the notifications pass.
2. **Push copy** for the switch — deferred to the notifications pass. Nothing is built here.
3. **Show-up Rate display format** — still open project-wide (`JIRA-HANDOFF.md` → *Open decisions every ticket must carry*). This screen does not mention the rate.
