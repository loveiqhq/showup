# Date — 07a Match & date confirmation

```
date/
  screen-date-reference.jsx   ← Date 01: the Date screen (ScreenMatchCoord). Read values from here.
  screen-chat-reference.jsx   ← Date 02: tutorial, chat, cancel guide, End the date, outcome screens
  screen-checkin-reference.jsx ← Date 03: Check-in bar, guard, done + Undo, gate, arrival card
  tickets/00-epic.md          ← flow shape, built-once inventory, flow-level rules
  tickets/01-date-screen.md   ← scope, copy strings, AC, tracking
  tickets/02-coordinate-chat.md
  tickets/03-arrival-checkin.md
  spec-sheets/01-date-screen.png
  spec-sheets/02-coordinate-chat.png
  spec-sheets/03-arrival-checkin.png
  07a-decisions.md            ← check-in, time/place changes, auto-switch, retention (2 Nov 2026)
```

Kit source: `ui_kits/show-up/screens-date-chat.jsx`, `screens-date-change.jsx`, `screens-date.jsx` · section **07a · Match & date confirmation – including Chat**.

| # | Ticket | Screens | Status |
|---|---|---|---|
| 01 | Date screen | A locked (+ change tiles, reissued 5 Nov) · B available · C unread · D full scroll | ticketed — not built |
| 02 | Coordinate chat | A tutorial · B start · C thread · D cancel guide · E End the date card · F sheet · G you ended · H match sees | ticketed — not built (5 Nov 2026) |
| 03 | Arrival check-in (+ 04) | A before · B guard · C no location fix · D checked in + Undo · E after Close · F chat chip · G location gate (consumed) · H match sees | ticketed — not built (6 Nov 2026) |
| 04 | ~~Match notified~~ | folded into 03 | — |
| 05 | Change meet time and place | Change 1–6 | to write |
| 06 | End date and report a problem | wrap-up · report · no-show exits | to write |
| 07 | Cancel this date | penalty guard · what the match sees | to write |
| 08 | What the other side sees | you didn't show up · ended (uncomfortable) · ended (normally) | to write |
| 09 | Switch to flow 10 | logic only, no sheet | to write |
| 10 | Match Mate | topics from both profiles · ice breakers · quick games · safety tips · if it's awkward — library + AI picks, always visible, mixed tone | to design |
| 00 | Epic | — | written 2 Nov 2026 · `tickets/00-epic.md` |

Order: 01 → 02 → 03 and 05 in parallel → 06, 07, 08 → 09.

## Flow rules (every 07a ticket inherits these)

1. **No second date while one is active.** Search, Likes and Instant are disabled from the match until the date ends; Settings stays open.
2. **The latest agreed time and place drive everything** — the screen, the unlock (meet − 1:30), the check-in comparison (150 m radius each), the +2 h switch. A pending suggestion moves nothing.
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

## Decided 6 Nov 2026 — Date 03

- Date 04 folded into 03. No time window on the Check-in bar. No location fix → nothing stored, Try again (kit: Check-in · 2b).
- **Undo check-in** under Close on the done sheet, only there: deletes the record, back to the Check-in bar on the Date screen. The match is notified on Close, never on Yes.
- Tracking: `arrival_confirm_shown` · `arrival_undone` NEW, `arrival_confirm_submitted` · `arrival_confirmed` respecified, §41 `arrival_entry` — registry 1.4.34 / taxonomy 1.4.27.
