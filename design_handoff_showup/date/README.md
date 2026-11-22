# Date — 07a Match & date confirmation

```
date/
  screen-date-reference.jsx   ← Date 01: the Date screen (ScreenMatchCoord). Read values from here.
  screen-chat-reference.jsx   ← Date 02: tutorial, chat, cancel guide, End the date, outcome screens
  screen-checkin-reference.jsx ← Date 03: Check-in bar, guard, done + Undo, gate, arrival card
  screen-change-reference.jsx ← Date 05: time + place pickers, suggestion card, request card, expiry
  screen-wrap-report-reference.jsx ← Date 06: End Date bar, How did it go?, Report a problem, shared no-show + uncomfortable steps
  screen-cancel-reference.jsx ← Date 07: the red link, the penalty guard sheet, what the match sees
  tickets/00-epic.md          ← flow shape, built-once inventory, flow-level rules
  tickets/01-date-screen.md   ← scope, copy strings, AC, tracking
  tickets/02-coordinate-chat.md
  tickets/03-arrival-checkin.md
  tickets/05-change-time-place.md
  tickets/06-end-report.md
  tickets/07-cancel.md
  spec-sheets/01-date-screen.png
  spec-sheets/02-coordinate-chat.png
  spec-sheets/03-arrival-checkin.png
  spec-sheets/05-change-time-place.png
  spec-sheets/06-end-report.png
  spec-sheets/07-cancel.png
  07a-decisions.md            ← check-in, time/place changes, auto-switch, retention (2 Nov 2026)
```

Kit source: `ui_kits/show-up/screens-date-chat.jsx`, `screens-date-change.jsx`, `screens-date.jsx` · section **07a · Match & date confirmation – including Chat**.

| # | Ticket | Screens | Status |
|---|---|---|---|
| 01 | Date screen | A locked (+ change tiles, reissued 5 Nov) · B available · C unread · D full scroll · E load failed · F read-only profile (9 Nov) | ticketed — not built |
| 02 | Coordinate chat | A tutorial · B start · C thread · D cancel guide · E End the date card · F sheet · G you ended · H match sees · I load failed · J send failed · K end failed (9 Nov) | ticketed — not built (5 Nov 2026) |
| 03 | Arrival check-in (+ 04) | A before · B guard · C no location fix · D checked in + Undo · E after Close · F chat chip · G location gate (consumed) · H match sees | ticketed — not built (6 Nov 2026) |
| 04 | ~~Match notified~~ | folded into 03 | — |
| 05 | Change meet time and place | A time picker · B place picker · C sent · D yours waiting · E request, chat open · F answering · G agreed · H expired · I locked tiles · J request, chat locked · K suggest failed · L agree failed · M places failed · N no place found | ticketed — not built (10 Nov 2026) |
| 06 | End date and report a problem | A End Date bar · B How did it go? · C we met · D check in first (consumed) · E/F not here yet, before / after meet time · G thanks for waiting · H report · I not comfortable · J end failed | ticketed — not built (10 Nov 2026) |
| 07 | Cancel this date | A link · B guard, chat locked · C chat open · D after a change request · E cancel failed · F after check-in, no link · G canceller → flow 10 (consumed) · H match sees | ticketed — not built (16 Nov 2026) |
| 08 | What the other side sees | you didn't show up (claim) · ended (uncomfortable) · ended (We met) · + flow 10 claim headline `rating-claimed-noshow` | written 17 Nov 2026 · `tickets/08-partner-end.md` |
| 09 | Switch to flow 10 | logic only, no sheet | to write |
| 10 | Match Mate | topics from both profiles · ice breakers · quick games · safety tips · if it's awkward — library + AI picks, always visible, mixed tone | to design |
| 00 | Epic | — | written 2 Nov 2026 · `tickets/00-epic.md` |

Order: 01 → 02 → 03 and 05 in parallel → 06, 07, 08 → 09.

## Flow rules (every 07a ticket inherits these)

1. **No second date while one is active.** Search, Likes and Instant are disabled from the match until the date ends; Settings stays open.
2. **The latest agreed time and place drive everything** — the screen, the unlock (meet − 1:30, or earlier on the first committed check-in), the check-in comparison (150 m radius each), the +2 h switch. A pending suggestion moves nothing.
3. **UI says "Check-in"; tickets and tracking say "arrival check-in"** — kept apart from the 48hrs Check-In.
4. **No Show-up percentage.** `ShowUpCount` everywhere on 07a screens.
6. **Only what matches is shown** — intent only when both motivations are equal, shared vibes only.
5. **Show-up Rate resolution** (one-sided reports, "I was there" claims, chat evidence) is designed with flow 10, not here. 07a only stores the data.

## Decided 2 Nov 2026 — Date 01

- Footer = Check-in bar until check-in, then End Date bar. Kit artboards corrected (they showed End Date before check-in).
- `ShowUpCount` replaces the 94% pill on the intent card and in About {name}.
- Round 3: `End date` link under the Check-in bar (wrap-up sheet, not cancel) · no timer, "How the date works" rewritten · After the date removed · Order Ride hidden · no progress bar on the locked card · Match Mate → Date 10.
- Tracking: §11 `date_screen` / `Date - Date screen`, §38 `date_quick_action`, `date_quick_action_tapped` NEW, `date_screen_viewed` respecified — registry 1.4.32 / taxonomy 1.4.25.

## Decided 5 Nov 2026 — Date 02 + Date 01 reissue

- Date 01 reissued: the locked Coordinate card carries **Change meet time · Change location** tiles (built in 01, open the Date 05 pickers, hidden until 05 ships). Sheet re-exported; AC rewritten in place.
- Date 02: tutorial has 2 steps · chips always a 2-column grid, full labels · "I can't make it today" = guide, field locked until Got it, no prefill · **End the date only after a cant_make_it message**, either side · ending in the chat = no review, both search again · no move to another day in the app.
- Tracking: family L respecified, 3 NEW events, 3 retired, §11 ×3, §39 `chat_entry`, §40 `chat_reason` — registry 1.4.33 / taxonomy 1.4.26.

## Decided 16 Nov 2026 — Date 03 build questions

- Push copy: temporary strings in the ticket; all pushes are finalised together after the main flows.
- Check-in not saved (location read, no connection) → own message, Try again, no cap (kit: Check-in · 2c, frame C2).
- Location gate on the Date screen: Search 04's two events with `screen_id: date_location_gate` (registry 1.4.38 / taxonomy 1.4.31).
- Approximate location is accepted and stored with its accuracy. **Flow 10 backlog:** treat a low-accuracy check-in as unconfirmed; the review answers decide.
- **A committed check-in opens the chat for both**, even before meet − 1:30; it never locks again. So the arrival card's `Open chat` always works. Updated in Date 01, 02, 03, 06, 07 and the epic; `coordinate_unlocked` gains `cause`.

## Decided 6 Nov 2026 — Date 03

- Date 04 folded into 03. No time window on the Check-in bar. No location fix → nothing stored, Try again (kit: Check-in · 2b).
- **Undo check-in** under Close on the done sheet, only there: deletes the record, back to the Check-in bar on the Date screen. The match is notified on Close, never on Yes.
- Tracking: `arrival_confirm_shown` · `arrival_undone` NEW, `arrival_confirm_submitted` · `arrival_confirmed` respecified, §41 `arrival_entry` — registry 1.4.34 / taxonomy 1.4.27.

## Decided 9 Nov 2026 — Date 01 build questions

- Load failed: `Couldn't load your date. Check your connection and try again.` · `Try again`. Profile back pill `Your date`. Calendar: no access request on iOS 17+; iOS 16 string `Show Up only adds your date to your calendar. It never reads your events.`
- Confirmed: no Likes count while a date is active · whole km via Search's formatter · icons only where drawn · Directions to the default maps app · footer bars come from 03 / 06, not the reference.
- Description updated in place; change list in `tickets/01-date-screen-update-2-comment.md`. Tracking unchanged. Kit + sheet updated: frames E (load failed) and F (read-only profile), callouts ⑲ ⑳.

## Decided 9 Nov 2026 — Date 02 build questions

- Errors: chat load (I, replaces the thread), send (J, stays in thread, tap the line to resend), End date (K, inside the sheet, retries; not ended until the server confirms).
- Dock chips 38 high, 44 tap area (3 px slop into the 6 px gap), iOS and Android — was 36.
- Cancel guide AC at default text size; larger sizes scroll to the guide's top. Chat header capped at 165 %. Change chips hidden until Date 05.
- Description updated in place; change list in `tickets/02-coordinate-chat-update-comment.md`. Tracking unchanged (one rule: failures fire nothing new). Kit + sheet re-exported.

## Decided 10 Nov 2026 — Date 05

- Ticket written from the 2–5 Nov decisions; no new design decision. Written down: **no Decline and no Withdraw** — the answers are I agree, Suggest another, or nothing (expires).
- Date 05 **un-hides** the locked card's change tiles (Date 01) and the two change chips (Date 02).
- Kit: the `change-main-*`, `change-request-*` and `change-early-*` artboards now pass `checkInFlow` — they showed the End Date bar before check-in.
- Google Places attribution overrides the sheet for callout ④ only.
- Tracking: `change_picker_opened` · `change_picker_dismissed` · `change_suggested` · `change_agreed` · `change_expired` NEW (family K), §42 `change_entry` NEW — registry 1.4.35 / taxonomy 1.4.28. No §11 row (overlays).
- Settled the same day: tiles + change chips **hidden for both** from binding meet − 0:30, after either check-in, and (time only) with no later slot · two requests → one card, earlier deadline first · an open chat never re-locks · "nearby" = the binding place.
- Failure states drawn (kit Change · 16–19, sheet K–N): Suggest failed keeps the picker open · I agree failed = Date 02's red retry line · Places failed → Try again · empty search, no red. New kit pieces `ChangeError`, `ChangeRetryLine`.

## Decided 10 Nov 2026 — Date 06

- **One check-in gate** for *My date isn't showing up* (both sheets) and *I don't feel comfortable*. *We met* needs none. `useArrivedGate` has four entries now: Date screen · chat chip · Report · End date.
- **The no-show end unlocks at the binding meet time** — before: disabled, `You can end the date from {time}`. The 10 minutes are guidance, not measured.
- **One wait guidance** (`NoShowGuide` + `NoShowConfirm`) for both sheets. Copy: "Please give it at least 10 minutes" · `End date and review` · `Back`. "This is on them" removed — no verdict.
- **Head out now ends the date** → this user to flow 10, the match to Date 08 `date-ended-discomfort`; What happens next gains the review line. **Stay for now** stores an internal flag.
- ~~**No-show end, match checked in** → the match sees the neutral `date-closed-positive`~~ — superseded 17 Nov 2026: every no-show end → `you-no-showed`.
- **Parked:** *Something else* (with *I'm in danger*). **Deleted:** the End Date bar's "Date complete · Rate" variant.
- Tracking: family N respecified — `wrapup_opened` · `date_ended` · `problem_stay_flagged` NEW; `wrapup_outcome_selected` · `problem_report_opened` · `problem_category_selected` respecified (`waited` → `partner_no_show`); `wrapup_prompted` · `wrapup_confirmed` · `grace_period_*` · `no_penalty_exit_granted` RETIRED; §43 `date_end_reason` · §44 `problem_category` · §45 `end_entry` NEW, §41 gains `wrapup` — registry 1.4.36 / taxonomy 1.4.29.
- Kit: `End date · 1–7`, `Report a problem · 1–5` artboards. **Open:** the copy-consistency fixes (`End Date` vs `End date`, C's "chat menu" note) — not applied.

## Decided 16 Nov 2026 — Date 07

- Red link = no-show whatever the timing: −10 (placeholder), canceller → flow 10 `rating-you-noshow` → search; **no done sheet, no 24 h block**. Match → `ScreenPartnerCancelled`, rate untouched, no review, search.
- **Permanent pair block** after a red-link cancel, both directions (`matching-plan.md` rule 7). The link shows from the match until this user's check-in.
- Guard copy final ("her" stays, typo fixed, "by 10" from the rate config). Screen 4: "are paused" removed. No pause in MVP; account deletion blocked during a date (Settings, later).
- Tracking: `cancel_guard_shown` · `cancel_abandoned_at_guard` · `date_cancelled` respecified; `cancel_link_tapped` · `partner_cancellation_received` · `rematch_started` RETIRED; §46 `cancel_guard_exit` NEW, §42 `cancel_sheet`, §11 `date_partner_cancelled` — registry 1.4.37 / taxonomy 1.4.30.
