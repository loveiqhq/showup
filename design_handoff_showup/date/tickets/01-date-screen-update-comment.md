# [Date 01] Update — Change meet time · Change location on the locked Coordinate card

**This comment is the diff, not the spec.** The ticket description has been reissued (5 Nov 2026) and carries the full specification. Build from the description; use this to see what moved since the 2 Nov version.

## What is new

The **locked Coordinate card (state A)** gets two buttons, so time and place can be changed from the match on, before the chat opens (decided 3 Nov 2026, kit artboard Change · 12).

Under `Messaging available from {meet − 1:30}`:

- Line: `Request to change the location or meet time.` — 13.5 / 1.4, muted, margin-top 4
- Two tiles, `Change meet time` · `Change location` — grid 2 columns, gap 8, margin-top 14 · tile padding 14 / 8, radius 16, #fff, 1px `--liq-border-soft` · violet icon 18 (`clock` / `map-pin`) · 13 / 600

📎 Callout ⑥ on the re-exported **date-01-date-screen-spec-sheet.png**. Values: `CoordinateModule` in `design_handoff_showup/date/screen-date-reference.jsx` (locked branch).

## Behaviour

- Each tile opens the **Date 05** picker (time or place) — the same pickers as the chat chips.
- **The card is still not tappable.** Only the two tiles are.
- Tiles appear **only in state A**. In B and C the chat's chips do the same job.
- **Hidden until Date 05 ships** (epic rule 8): the line and both tiles are not rendered until the pickers exist. Build them now behind that switch, so turning Date 05 on needs no Date 01 change.
- A change request **received** while the chat is locked (Change · 14–15) is the unread slot's request variant — **Date 05**, not this update.

## Tracking — one new row

| What | Event | Payload |
|---|---|---|
| Change tile tapped | `action_chip_used` | `date_id`, `reason` (`change_time` / `change_place`, §40), `surface: "coordinate_card"` |

Same event as the chat chips, so "change requested" is one funnel whatever the surface. **Not** `date_quick_action_tapped`. Taxonomy **1.4.26**.

Also changed in the registry since 2 Nov: `coordinate_viewed` and `date_status_*` are **retired** (Date 02) — they were already on this ticket's *must NOT fire* list; now they must not exist at all.

## Acceptance criteria — rewritten in place

- [ ] **A** shows the locked Coordinate card with `Messaging available from {meet − 1:30}`, the line `Request to change the location or meet time.` and the two tiles. The card is not tappable; only the tiles are, each opening the Date 05 picker. Until Date 05 ships, the line and tiles are not rendered.
- [ ] A tile tap fires `action_chip_used` with `surface: "coordinate_card"`, and no `date_quick_action_tapped`

**Evidence:** the existing A screenshots at 375 × 667, 390 × 844 and 430 × 932, taken once with the tiles switched on.

## If you already built state A

Nothing else on the screen moves. Add the line and the tiles inside the locked card, after the `Messaging available` line. The card grows by its content — no fixed height, no Y offsets.
