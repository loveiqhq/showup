# [Date 03] Arrival check-in — check in, undo, and what the match sees

**Parent:** [Epic] 07a Match & date confirmation (`date/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `date-03-arrival-checkin-attachment.zip` (the handoff folder, `design_handoff_showup/`) · `date-03-arrival-checkin-spec-sheet.png`
**Blocks:** Date 06 End date and report a problem (`DateStatusBar` fills the footer slot after check-in; Report → "My date isn't showing up" uses the gate) · flow 10 (reads the check-in store)
**Blocked by:** Date 01 Date screen (`ScreenMatchCoord`, the footer slot, `CoordinateModule`) · Date 02 Coordinate chat (the `I arrived at the spot` chip)

## Description

When a person reaches the meeting place, they confirm it in the app. A deliberate **Yes, I've arrived** stores the phone's location, a timestamp and their user ID against the date. Right after, they can **undo** it. Once they close the confirmation, their match is told they have arrived.

The check-ins are evidence. Flow 10 compares the two (150 m radius each) to decide whether the date happened. **This ticket only stores them; it decides nothing.**

**Date 04 "Match notified" is folded into this ticket** (6 Nov 2026). The arrival card on the match's phone is the other end of the same check-in and can't be tested without it.

**Eight frames, one ticket:**

| | Frame | When | Reference |
|---|---|---|---|
| A | Before check-in | date active, this user not checked in | `ScreenMatchCoord coordOpen checkInFlow` |
| B | Have you arrived? | Check-in tapped | `… initialCheckInPopup="confirm"` |
| C | No location fix | Yes tapped, no location returned | `… initialCheckInPopup="locerror"` |
| D | Checked in | check-in stored | `… initialCheckInPopup="done"` |
| E | After Close | D closed | `ScreenMatchCoord coordOpen` |
| F | From the chat chip | `I arrived at the spot` before check-in | `ScreenChatEntry initialCheckInPopup="confirm"` |
| G | Location not usable | location off for Show Up or the phone | `ScreenProfileLocation context="date" state="denied"` — **consumed** |
| H | What the match sees | the other person closed D | `ScreenMatchCoord coordOpen checkInFlow coordUnread={{ arrival: true, time }}` |

## Decided before build

- **No time window.** The Check-in bar shows **the whole time the date is active**. The real meet time can be agreed in free text without the picker, so no clock can be trusted; the guard ("Have you arrived?") is the protection. (6 Nov 2026, confirms epic rule 4)
- **No location fix → retry.** If the phone returns no location (timeout, no signal, services unavailable), **nothing is stored** and the guard stays up with an error and **Try again** (frame C). There is no low-accuracy fallback and no check-in without coordinates. (6 Nov 2026)
- **Undo check-in exists only on D**, under Close. It **deletes** the stored record (no soft delete, no flag), closes the sheet and lands on the **Date screen with the Check-in bar** — also when the check-in started from the chat chip. Once D is closed there is **no undo anywhere**. (6 Nov 2026)
- **The match is told on Close of D, never on Yes.** Closing D (Close, scrim, or the app leaving D — backgrounded, killed) commits the notification. So an undo never reaches the match. (6 Nov 2026)
- **Location off is not a check-in state.** While location is not usable, the location gate (Search 04) blocks the whole Date screen, so Check-in can never be tapped with location off. This ticket builds **no** location-off UI inside the flow. (5 Nov 2026)
- **The `End date` link under the Check-in bar ships hidden** until Date 06 lands — it opens 06's `DateWrapSheet` (epic rule 8). It is not Cancel this date.
- **No 150 m check here.** The overlap is decided by flow 10 (epic rule 6). The client never compares locations, and neither user is ever shown a distance.

## Layout — read this before building

⚠ **Sheets over the Date screen, one footer slot.** Date screen: one scroll → sticky footer (Check-in bar ① or End Date bar ⑦) → TabBar. The footer swaps on `checkedIn`. B, C and D are **one `ModalShell` each**, bottom sheet, height from content — never a separate screen.

⚠ **The error box ④ sits inside the guard**, between the headline and the buttons. The buttons keep their order, size and position in the column.

⚠ **No absolute Y positioning.** Sheets size to their content; chrome sits on the safe-area insets.

📎 All specs are on the attached **date-03-arrival-checkin-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–⑫ across eight frames. Black callouts are built here; **red is the failure state**; **violet callouts open another ticket's UI or are not ours**. The design system has colour and type tokens only, with no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/date/screen-checkin-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. Frame props are in the table above. Only the `unread.arrival` branch of `CoordinateModule` is this ticket; the rest is Date 01 / 05.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **The OS location prompt and its settings.** The gate (G) is Search 04 / Profile 12's screen in `context="date"` — consumed, not rebuilt.
- **Push delivery.** The push to the match fires on Close; its copy is TBD (epic *Open*).
- **Status bar and home indicator** are mocks. Anchor to the safe-area insets.

## Copy — final strings

`{name}` is the match's first name.

**A · Check-in bar:** `Your date with {name}` · `Confirm you arrived at the meeting location, we will let your date know.` · button `Check-in` · link `End date` (hidden until Date 06)

**B · Guard:** `Have you arrived at the meeting location for your date with {name}?` · `Not yet` · `Yes, I've arrived`

**C · No location fix:** `We couldn't find your location. Check your signal, then try again.` · `Not yet` · `Try again`

**D · Checked in:** `You are amazing for showing up!` · `We will let {name} know that you have arrived. Enjoy your date.` · `Close` · `Undo check-in`

**H · Arrival card (match):** `{name}` · tag `Arrived` · `checked in · {time}` · `{name} has arrived at the meeting location.` · `Open chat`

**Report entry (Date 06 passes it as `note`):** `Check in first, so we can confirm you were there.` — the prop is built here, the string is used by 06.

Product terms capitalise exactly: **Show Up · Show-up Rate · Check-In** (the 48 h one). UI says **Check-in**; tickets and tracking say **arrival check-in**.

## Behaviour

**Entry points — one gate (`useArrivedGate`), three entries**

- **Date screen:** `Check-in` on the bar opens B.
- **Chat chip:** `I arrived at the spot` while this user is not checked in opens B over the chat. **Nothing is sent** to the thread. Once checked in, the chip opens the chat with its prefill (Date 02).
- **Report → "My date isn't showing up"** (Date 06): not checked in → B with the `note` prop. Wired by 06; the gate supports it here.

**B · Guard**

- `Not yet` or a scrim tap closes B. **Nothing is stored, nothing is sent.**
- `Yes, I've arrived` reads the phone's current location once (foreground, one-shot). Disable both buttons while reading.
- Fix returned → the server stores the record → D.
- No fix (timeout **10 s**, error, or no coordinates) → C. Nothing is stored.

**C · No location fix**

- Same sheet with the error box; the primary reads `Try again` and repeats the read. `Not yet` closes it. Every retry is a fresh read; there is no cap.

**D · Checked in**

- The record is stored on entry to D: **location + timestamp + user ID**, against this date.
- `Close`, the scrim, or the app leaving D **commits** the check-in: the sheet closes, the footer swaps to the End Date bar (E), and the match is notified (card H + push).
- `Undo check-in` **deletes** the record, closes the sheet and shows the **Date screen with the Check-in bar** (A). The match is never notified. From the chat chip, Undo leaves the chat for the Date screen.
- A user may check in again after an undo; it is a new record.

**After check-in**

- The Check-in bar never shows again for this date, on any entry. The chat chip behaves like any chip.
- There is no undo after D is closed.

**H · What the match sees**

- Once the other person's check-in is committed, the match's Coordinate card takes the **arrival variant**, hoisted under the hero like an unread message (Date 01's unread slot, same orange treatment). `Open chat` opens the chat (Date 02).
- Shown once per check-in. It clears when the match opens the chat or taps it, like an unread message.
- The match's own Check-in bar is unaffected — each person checks in for themselves.
- If the chat is still locked (before meet − 1:30), the card shows anyway; `Open chat` follows Date 01's locked rules.

## Data and legal

- **Stored on Yes:** device location (lat/long), the device's reported accuracy, timestamp, user ID, date ID. **Internal only** — never shown to either user, never in analytics, never in a push payload.
- **Kept at least 1 year** for legal claims, then deleted. On the legal-page backlog (`matching-plan.md` §6 item 8).
- **Undo deletes the row.** It is not kept as evidence.

## Build inventory

- **Client:** `CheckInBar`, `CheckInConfirmSheet` (with `note` and `locError`), `CheckInDoneSheet` (with `onUndo`), `useArrivedGate`, the footer-slot swap on `checkedIn`, the `arrival` variant of `CoordinateModule`, the one-shot location read with the 10 s timeout. The chat chip branch in `ScreenChatEntry` / `ScreenChatOpen`.
- **Server:** the arrival check-in store (create on Yes, delete on Undo, commit on Close), the commit → notify the match (in-app card state + push), `checked_in` on the date payload for both people, the 1-year retention job.
- **Not built here:** `DateStatusBar` and `DateWrapSheet` (06), `ReportProblemSheet` (06), the location gate (Search 04), the 150 m comparison (flow 10), push copy.

## Acceptance criteria

- [ ] The Check-in bar shows the whole time the date is active for a user who has not checked in — there is **no time window**
- [ ] `Not yet` and the scrim store nothing and send nothing
- [ ] `Yes, I've arrived` stores location + timestamp + user ID against the date; the buttons are disabled while the location is read
- [ ] With no location fix within 10 s, **nothing is stored**, C shows the error and `Try again`; a retry that succeeds lands on D
- [ ] There is **no** check-in without coordinates and no low-accuracy fallback
- [ ] `Undo check-in` appears **only** on D, below `Close`; it deletes the record and lands on the Date screen with the Check-in bar — from the Date screen **and** from the chat chip
- [ ] **The match is never notified of a check-in that was undone**; they are notified only once D closes (Close, scrim, or the app leaving D)
- [ ] After D is closed, no undo exists anywhere and the Check-in bar never shows again for this date
- [ ] `I arrived at the spot` before check-in opens B and sends nothing to the thread; after check-in it opens the chat with its prefill
- [ ] The match sees the arrival card (H) in the hoisted unread slot with `Open chat`; their own Check-in bar is unaffected
- [ ] There is **no** location-off state inside the check-in flow; with location not usable, the gate (Search 04) is shown instead of the Date screen
- [ ] The `End date` link is **hidden** until Date 06 ships
- [ ] No coordinates, accuracy, distance or place appear in any analytics payload, push payload or UI
- [ ] Copy matches the strings above exactly
- [ ] **Evidence of done:** screenshots of **A–F and H** at **375 × 667, 390 × 844 and 430 × 932** — 21 images (G is Search 04's). On 375 × 667, state that the guard's buttons and C's error are fully visible. Plus one recording: Check-in → Yes → Undo → Check-in bar again → Check-in → Yes → Close, and the match's phone showing H only after Close.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **M — Live Status & Arrival**). Use them exactly — do not invent names, and do not restate a payload the registry already defines. **Registry 1.4.34 / taxonomy 1.4.27** — two events **NEW**, two **respecified**, one value set **NEW**.

| What | Event | Payload |
|---|---|---|
| Guard shown | `arrival_confirm_shown` | **NEW event** · `date_id`, **`entry`** (§41 — **NEW**), `minutes_vs_start` |
| Yes tapped | `arrival_confirm_submitted` | **respecified** · `date_id` **NEW**, `entry` **NEW**, `minutes_vs_start` |
| Check-in stored | `arrival_confirmed` | **respecified, server** · `date_id`, `entry`, `minutes_vs_start`, **`is_first`** **NEW** |
| Undo check-in | `arrival_undone` | **NEW event, server** · `date_id`, `entry`, `seconds_since_confirm` |

There is no `screen_viewed` for B, C or D — they are sheets on `date_screen` / `date_chat`, not screens.

**Rules for this screen**

- `minutes_vs_start` counts from the **latest agreed** meet time; negative = before.
- **Not yet fires nothing.** `arrival_confirm_shown` without a following `arrival_confirm_submitted` is the Not yet / scrim count.
- **A retry (C) fires `arrival_confirm_submitted` again** and no new `arrival_confirm_shown`. A failed read fires nothing else.
- **An undone check-in is `arrival_confirmed` followed by `arrival_undone`.** Matching's "date happened" label is `arrival_confirmed` with **no** `arrival_undone` after it — say so to whoever reads it.
- ⚠ **Respecified triggers — check any emitter already written.** `arrival_confirmed` no longer means "server verifies arrival via geofence/code"; it means "the record is stored". `arrival_confirm_submitted` was "I Have Arrived button"; it is now `Yes, I've arrived` on the guard.
- `entry` is `date_screen` or `chat_chip` from this ticket; `report` is **reserved** until Date 06 ships.
- `action_chip_used` still fires on the `I arrived at the spot` tap (Date 02); the gate then fires its own events.
- **Never in a payload:** coordinates, accuracy, distance, place, address.
- **Must NOT fire from this ticket:** `message_sent` (the chip sends nothing before check-in), `date_cancelled` (07), any flow 10 review event.

**Tracking acceptance criteria**

- [ ] Every arrival event carries `date_id` and `entry`; `entry` uses only §41 values
- [ ] Not yet and the scrim fire no event
- [ ] Undo fires `arrival_undone` once and never a second `arrival_confirmed`
- [ ] No arrival payload contains coordinates, accuracy or distance

**Currently in the build: none of it.** Every row above is `Not built`.

## Out of scope

The Date screen and the footer slot (Date 01). The chat and its chips (Date 02). The End Date bar, `DateWrapSheet` and Report a problem (Date 06). The location gate (Search 04 / Profile 12). The 150 m overlap and every Show-up Rate outcome (flow 10). Push, SMS, WhatsApp and email copy.

## Dependencies

- **Date 01:** `ScreenMatchCoord`, the footer slot, `CoordinateModule`'s unread slot
- **Date 02:** the `I arrived at the spot` chip, `CHAT_REASONS`
- **Search 04 / Profile 12:** the location gate in `context="date"`
- **Shared:** `ModalShell`, `Card`, `Avatar`, `Icon`
- **Server:** the check-in store with 1-year retention, the notify-on-commit job
- **Legal:** the check-in retention statement on the legal page
- **Tracking registry 1.4.34 / taxonomy 1.4.27**

## Open before the sprint

1. **Push copy** for "{name} has arrived" — epic *Open*. The push fires on commit; the copy can land later.
2. **Show-up Rate display format** — still open project-wide (`JIRA-HANDOFF.md` → *Open decisions every ticket must carry*). This ticket shows no rate, so it is unaffected.
