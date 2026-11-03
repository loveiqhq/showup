# [Epic] 07a Match & date confirmation

**Type:** Epic · **Priority:** High
**Handoff folder:** `design_handoff_showup/date/` — read `README.md` first, then `07a-decisions.md`
**Design source:** Show Up UI kit, section `07a · Match & date confirmation – including Chat` (`ui_kits/show-up/screens-date-chat.jsx`, `screens-date-change.jsx`, `screens-date.jsx`; decisions of 2 Nov 2026)

## What this epic delivers

Everything between **"the two users matched"** and **"the date is over and both are in the review"**. The match is a binding date. This epic covers the screen both people live on until they meet, a limited chat for last-minute coordination, confirming arrival at the spot, moving the time or place by mutual agreement, and every way a date ends: met, no-show, cancelled, reported, or simply never closed.

**One active date at a time.** While a date is active the user cannot search, like or take another date. Every rule below follows from this, and from one more: **the latest agreed meet time and place drive everything.**

Entry: a won confirm in Date search (Search 02 / 03). Exit: **flow 10 Review your date**, for both people.

## Shape of the flow

```
match ──► Date screen (01) ──── meet − 1:30 ────► Coordinate chat opens (02)
              │                                         │
              │  Change time / place (05) ◄─────────────┘
              │
              ├── arrive ──► Check-in (03) ──► match sees "arrived" (04)
              │
              ├── End date ──────────────┐
              ├── Report a problem (06) ─┤
              ├── Cancel this date (07) ─┼──► the other side's screen (08) ──► flow 10
              └── nobody ends it ── +2 h (09) ─────────────────────────────► flow 10
```

## Stories

Each story is one screen (or one logic job) with all its states, a spec sheet and a reference file. Numbering matches the handoff folder.

| # | Story | Kit artboards | What it builds | Status |
| --- | --- | --- | --- | --- |
| 01 | Date screen | `coord-announce` · `-full` · `-full-games` · `coord-available` · `coord-available-unread` | The host screen: A locked · B available · C unread · D full scroll. The slots for 02–08 and 10 | **ticketed — not built** |
| 02 | Coordinate chat | `chat-tutorial` · `chat-entry` | Tutorial (always before the first open), reason chips + editable prefill, the thread | to write |
| 03 | Arrival check-in | Check-in 1–3 · `chat-entry-arrived` | Check-in bar + `End date` link, "Have you arrived?" guard, "You are amazing for showing up!", the stored location, the chat chip branch, location-off case | to write |
| 04 | Match notified | Check-in 4 | "{name} has arrived at the meeting location" in Coordinate's unread slot. Push copy later | to write — may fold into 03 |
| 05 | Change meet time and place | Change 1–6 | Both full-screen pickers, suggestion cards, I agree / Suggest another, Waiting / Agreed tags, binding note, expiry, Google Places | to write |
| 06 | End date and report a problem | `coord-wrap-*` · `coord-report-*` | "How did it go?", the "My date isn't showing up" guidance (shared by both paths), no-penalty exit, "check in first" | to write |
| 07 | Cancel this date | `coord-cancel-2-confirm` · `coord-cancel-4-partner` | Penalty-guard sheet, the confirmed outcome, what the match sees | to write |
| 08 | What the other side sees | `you-no-showed` · `date-ended-discomfort` · `date-closed-positive` | The three screens shown to B when A ends the date. Each leads into flow 10 | to write |
| 09 | Switch to flow 10 | — | Server/client logic, no spec sheet: every date still open at +2 h moves both users to flow 10 | to write |
| 10 | Match Mate | — | Content and UI for the Match Mate slot. **Needs design first** | to design |

**Build order:** 01 → 02 → 03 and 05 in parallel → 06, 07, 08 → 09. 04 rides with 03. 10 can land any time after 01; until then its slot renders nothing.

**Not in this epic:** flow 10 Review your date (incl. the "I was there" claim and disputing "You didn't show up") · the Show-up Rate resolution logic · push / SMS / WhatsApp / email copy · Order Ride (ride-app API) · the parked AI safety call and "I'm in danger".

## Built once, not per screen

A second copy of any of these is a bug.

- **`ScreenMatchCoord`** — the Date screen. Built by 01. Every other story adds a sheet, a bar or a state to it; **extended, never forked**
- **`CoordinateModule`** — locked / available / unread. Built by 01; the **arrival** variant added by 04 (same slot, same treatment)
- **The sticky footer slot** — built by 01, swaps on `checkedIn`. `CheckInBar` by 03, `DateStatusBar` (End Date) by 06
- **`CHAT_REASONS` + `REASON_TONES` + `ReasonBadge`** — the chat's reason vocabulary. Built by 02; 01 reads it for the unread card, 03 for the "I arrived at the spot" chip
- **`useArrivedGate`** — the one check-in gate. Built by 03; used by the date screen, the chat chip and Report → "My date isn't showing up" (06). **Three entry points, one flow**
- **`CheckInConfirmSheet` · `CheckInDoneSheet`** — built by 03
- **`BindingNote` · `ChangeTag` · `ChangeFullSheet`** — built by 05; the note is shown in the picker, on the sent card and in 01's time + place card
- **`DateWrapSheet`** — "How did it go?". Built by 06; opened by the End Date bar **and** by 03's `End date` link
- **`ReportProblemSheet`** — built by 06
- **`CancelDateConfirmSheet` · `CancelDoneSheet`** — built by 07
- **`DateWorksCard`** — built by 01
- **Consumed, not built here:** `ShowUpCount` (Search 01) · the four-tab `TabBar` and its disabled state (Search 03) · `CHECKIN_MOTIVATIONS` §30 · the read-only profile (Search 01's `ScreenSearchProfile`, no action bar) · the location-off screen (Search 04 / Profile 12) · `ModalShell`

**Server, built once:** the date payload (both people, latest **agreed** time + place, kept apart from pending suggestions) · the unlock at meet − 1:30 (`coordinate_unlocked`) · the chat thread with timestamps · the arrival check-in store (03) · suggestions with expiry (05) · the date state machine (active → ended / cancelled / no-show / timed-out) · the +2 h job (09).

## Flow-level rules — every story inherits these

**1. One active date at a time.** From the match until the date ends, **Search, Likes and Instant are disabled**; a tap shows `Available again once your date is finished.` **Settings stays open.** No story draws a way to take a second date.

**2. The latest agreed time and place drive everything** — the time + place card, the chat unlock (meet − 1:30), the check-in distance (200 m from the agreed place), and the +2 h switch. **A pending suggestion moves nothing**; until it is agreed, the original stays binding and not showing up there is a no-show.

**3. Changes happen through the chat, one per type.** One time and one place suggestion can be open at once, answered separately. A new suggestion of the same type replaces the open one. Times: **later the same day only**, within the other person's availability, 15-min slots. Places: **public venues only** (cafés, bars, restaurants), Google Places. A suggestion **expires at the binding meet time**.

**4. Check-in.** UI says **Check-in**; tickets and tracking say **arrival check-in** (kept apart from the 48 h Check-In). Available whenever the date is active. A deliberate "Yes, I've arrived" stores location + timestamp + user ID; **"Not yet" stores nothing**. **No check-in without location.** Both check-ins within **200 m** → the date happened.

**5. Every date ends in flow 10, for both people.**
- **A ends it** (End date, report, cancel) → B sees the matching screen from Date 08 → flow 10.
- **Nobody ends it** → **+2 h after the latest agreed meet time** both move to flow 10. Applies to every open date: no check-in, one check-in, or an open report. With one check-in, that person ending the date themselves is the normal case; the switch is the fallback.
- **Search stays blocked** until each person has done their review.

**6. 07a stores; flow 10 decides.** Show-up Rate outcomes for one-sided reports, mismatched claims and missing check-ins are resolved in flow 10. 07a's job is to **store the evidence**: check-ins, chat messages and chip sends with timestamps, every suggestion and its answer, every cancel, report and end.

**7. Only what matches is shown.** The intent line only when both motivations are equal; vibes only the shared ones (`You both want`); interests only the shared ones. No overlap → no row. **No Show-up percentage anywhere** — `ShowUpCount`.

**8. Hidden until real.** A control whose back end is not built ships **hidden**, not disabled: Order Ride, Match Mate's content, any slot whose story has not landed.

**No absolute Y positioning, anywhere.** One scroll container per screen, nested flex columns, sticky footers, chrome on `env(safe-area-inset-*)`.

**Copy is final and lives in the stories.** Product terms: **Show Up** · **Show-up Rate** · **Match Mate** · **Check-In** (the 48 h one).

## Data and legal

- **Arrival check-in record:** device location + timestamp + user ID, against the date. **Internal only** — never shown to either user, never sent to analytics. **Kept at least 1 year** for legal claims, then deleted. Listed on the legal-page backlog (`matching-plan.md` §6 item 8).
- **Chat:** kept as evidence for the Show-up Rate decision. Retention follows the same rule — confirm with legal.
- **Places:** Google Places (may be swapped during implementation). Public venues only; no private addresses are stored as a meet place.

## Tracking

Family **K — Date Coordination** in `design_handoff_showup/tracking/events.json`, plus `screen_viewed` (family A). Registry **1.4.32** / taxonomy **1.4.25**. Screens §11: `date_screen` (01). Value sets: §38 `date_quick_action`.

- `date_screen_viewed` — once per visit, with `state`, `has_unread`, `checked_in`, `minutes_before_start` (from the latest agreed time)
- `coordinate_unlocked` — server, at meet − 1:30
- `date_quick_action_tapped` — Directions · Share Date · Calendar · Copy address (`ride` reserved)
- `arrival_confirm_submitted` · `arrival_confirmed` — **respecified by 03** for the arrival check-in
- `date_status_sent` · `date_status_undone` — **respecified by 02** for the reason chips
- `coordinate_viewed` — **respecified by 02** (its trigger still describes the old venue-unlock idea)
- `date_cancelled` — server; `hours_notice` and `penalty_points` are required (07)
- `match_mate_tab_opened` · `match_mate_item_used` — **respecified by 10**
- Suggestions (05) and the +2 h switch (09) **have no events yet** — they are defined in `events.json` before their tickets are written

**Never in a payload:** coordinates, distances in metres, chat text, place addresses. Location stays in the check-in store.

Cite event names in every ticket; never describe them. Never run Jira's "improve description" over a pasted ticket.

## Device matrix

375 × 667 · 390 × 844 · 430 × 932. Check 375 × 667 first. The Date screen and the chat scroll on every device; the pickers' footers are fixed.

## Dependencies

- **Date search (Search 02 / 03)** — the won confirm that creates the date
- **Search 01** — `ShowUpCount`, the read-only profile · **Search 03** — the four-tab `TabBar` · **Search 04 / Profile 12** — the location-off screen
- **Check-In 02 / 03 / 04** — availability (for time changes), §30 motivations, §31 vibes
- **Flow 10 Review your date** — every exit lands there
- **Server:** the date payload and state machine · the chat store · the check-in store · suggestions with expiry · the +2 h job · Google Places
- **Legal:** the check-in retention statement on the legal page

## Definition of done — epic level

- [ ] Two users who match can go from the match to flow 10 on all three devices, by **every** ending: met, no-show, cancelled, reported, and nobody ending it (+2 h)
- [ ] While a date is active, Search, Likes and Instant are disabled for **both** users and Settings works
- [ ] Changing the time or place never moves the binding values until the other person agrees; an unanswered suggestion expires at the binding time
- [ ] Two check-ins within 200 m of the agreed place are stored with location, timestamp and user ID, and nothing location-based appears in analytics
- [ ] `ScreenMatchCoord`, `CoordinateModule`, `useArrivedGate`, `DateWrapSheet` and `BindingNote` exist **once each**
- [ ] No Show-up percentage appears anywhere in 07a
- [ ] Every event fires per `events.json`, with no invented names

## Out of scope

Flow 10 Review your date and the "I was there" claim. Show-up Rate resolution rules. Push, SMS, WhatsApp and email copy. Order Ride and any ride-app API. The AI safety call and "I'm in danger" (parked). The 30-minute timer (not in 07a). The "After the date" card (designed with flow 10).

## Open — epic level

None of these blocks a 07a story.

- **Review deadline** — if one person never does the review, when does the other's Show-up Rate outcome become final? (flow 10)
- **"I was there"** — how B disputes "You didn't show up", and how the chat is read as evidence. (flow 10 / Show-up Rate)
- **Notification copy** — "{name} has arrived" and the other date pushes.
- **Match Mate (10)** — needs design. Brief agreed 2 Nov 2026: topics from both profiles · ice breakers · quick games · first-date safety tips · what to do if it's awkward · library + AI picks · always visible · mixed tone. To decide with it: which profile fields the AI may read (exclude GDPR Art. 9 data — orientation, religion, motivations such as non-monogamy).
- **Account deletion or pause during an active date** — proposed: treated as cancelling the date, with the normal penalty. Decide before Date 07 is written.
- **Chat retention** — confirm with legal alongside the check-in record.
