# [Date 08] What the other side sees — the three screens B gets when A ends the date

**Parent:** [Epic] 07a Match & date confirmation (`date/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `date-08-partner-end-attachment.zip` (the handoff folder, `design_handoff_showup/`) · `date-08-partner-end-spec-sheet.png`
**Blocks:** nothing else in 07a.
**Blocked by:** Date 06 End date and report a problem (`end_reason`, the end endpoint, the ended state) · flow 10 Review your date (`ScreenRating`, incl. the `claimedNoShow` headline) — see *Dependencies*

## Description

When one person (**A**) ends the date from Date 06, the other person (**B**) is moved straight to one full screen that says how A ended it. Each screen has one way out, **Proceed to review**, into flow 10.

| A ended it with | A goes to (Date 06 / flow 10) | B sees (this ticket) | B then goes to (flow 10) |
|---|---|---|---|
| We met | normal review | **C** `date-closed-positive` | normal review |
| My date isn't showing up | normal review, no-show branch | **A** `you-no-showed` — **whether or not B checked in** | normal review with the **claim headline** |
| I don't feel comfortable → Head out now | `rating-uncomfortable` (no penalty) | **B** `date-ended-discomfort` | normal review |

**"You didn't show up" is A's claim, not a verdict.** A no-show end needs A's stored check-in (Date 06 server rule). After both reviews an automated check in flow 10 compares the two check-ins (150 m circles overlapping, similar time) and the chat contact. B's Show-up Rate only drops if the no-show is proven. This screen states no rate change.

**Three frames, one screen component:**

| | Frame | `end_reason` | Reference |
|---|---|---|---|
| A | You didn't show up | `partner_no_show` | `ScreenNoShowClosed` |
| B | Your date ended it — uncomfortable | `uncomfortable` | `ScreenDateEndedDiscomfort` |
| C | Your date ended it — we met | `met` | `ScreenDateClosed` |

## Decided before build (17 Nov 2026)

- **The server picks the screen from `end_reason` alone.** B's check-in state no longer matters here. ⚠ Supersedes Date 06's 10 Nov rule *"no-show end, match checked in → `date-closed-positive`"*. Date 06 is updated to match.
- **A is a claim.** Row 1 is unchanged. Rows 2 and 3 send B to the review and explain the automated check. **Removed:** "Show-up Rate dropped to 2 of 3 dates" and "less visible… lose App access".
- **B is gender-neutral** ("saying they…") and names **uncomfortable only**. *Unsafe* is out of MVP; the kit's `rating-unsafe` artboards are parked.
- **C is for We met only.**
- **No way around the review.** No back, no close, no swipe-down. **Proceed to review** is the only tappable element, and nothing else in the app opens until B's review is sent.
- **Shown at once.** The screen replaces whatever B has open (the Date screen, the chat, any sheet) the moment the server confirms A's end.
- **Simultaneous ends are not handled specially.** The server serialises ends per date (Date 06). The first one wins, and the second request returns *already ended*.
- **Push copy is deferred** (all push copy is written in one pass at the end of the project).

## Layout — read this before building

⚠ **One screen component, three payloads.** Badge icon, hero gradient, avatar or heart disc, headline, sub and rows change. Layout, sizes and the CTA do not. Never three copies of the layout.

⚠ **Full screen, not a sheet.** Column: hero card → rows → **flex spacer** → CTA. The CTA sits at the bottom via the spacer — never absolute Y. On 375 × 667 the column scrolls and the CTA is the last item.

⚠ **B's hero is tighter on purpose** (padding 28 22, avatar → headline 16, rows margin-top 16) because its headline runs three lines. Values are in the reference file.

📎 All specs are on the attached **date-08-partner-end-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–⑨ across three frames. Black callouts are built here; **violet callouts open another ticket's UI**. The design system has colour and type tokens only, with no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/date/screen-partner-end-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. Frame components are in the table above; `name` is A's first name.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **How the date ends** — Date 06 stores `end_reason`, who ended it and both check-ins, and owns the end endpoint.
- **Flow 10** — `ScreenRating`, its `claimedNoShow` headline (`{name} said you *didn't show up*.`), `rating-uncomfortable` for A, and the automated no-show check. This ticket only routes into it.
- **Status bar and home indicator** are mocks. Anchor to the safe-area insets.

## Copy — final strings

`{name}` is A's (the match's) first name.

**A · You didn't show up:** `{name} ended the date, stating you *didn't show up.*` · `{name} **ended the date** after waiting for you.` / `It's now closed.`
Rows: `**Not showing up** to a date — or being **very late** without reaching out to your date — shows **no respect for each other's time.**` · `To clear this up, **proceed to the review** and **give us your side.**` · `Once you've completed the review, we run an **automated check**: did you both check in, and did you message each other? Your **Show-up Rate only drops** if the no-show is **proven.**`

**B · Uncomfortable:** `{name} ended the date, saying they *didn't feel comfortable* in the situation.` · `The date is now closed.`
Rows: `**Respect is non-negotiable** at Show Up. Behaviour that leaves someone feeling **uncomfortable** is not accepted here.` · `So far we only have **one side of the story.** In the next step, tell us **what happened from your point of view.**` · `We **review every report** and **act on it where it's needed.**`

**C · We met:** `{name} ended the date, stating you both have *met.*` · `Thank you for being real and showing up to your date.`
Rows: `We hope it was a good one. **Head into the review** and tell us how it went — it takes a minute.` · `Once the review is done, you're free to **search for your next date.**`

**CTA (all three):** `Proceed to review`

Product terms capitalise exactly: **Show Up · Show-up Rate**.

## Behaviour

**Trigger**

- On server success of any Date 06 end, the server marks B's date state **ended, review pending** with A's `end_reason`, and notifies B's client (realtime + push trigger with a placeholder string).
- B's client shows the screen for that `end_reason` **at once**, over whatever is open. Open sheets and the chat close; an unsent chat draft is discarded.
- If B's app is closed, the screen is the first thing shown on the next open.

**On the screen**

- No back, no close, no swipe, and the Android back button does nothing. The TabBar is not shown.
- `Proceed to review` → flow 10: A → `ScreenRating claimedNoShow` · B, C → `ScreenRating`.
- Until B's review is sent, every app open returns to this screen, or to the review if B has started it. Search, Likes and Instant stay blocked (epic rule 5).

**Edge cases**

- **A's end arrives while B is mid-way through their own end (Date 06 sheet open):** B's sheet closes and B sees A's screen. B's request, if sent, returns *already ended* and stores nothing.
- **B was offline at the moment of the end:** the screen shows on reconnect or next open, from the server state — never from a cached Date screen.

## Data stored

- Nothing new. The screen reads `end_reason` from Date 06's record. B's review state is flow 10's.

## Build inventory

- **Client:** one `PartnerEndedScreen` with three payloads (`ScreenNoShowClosed` · `ScreenDateEndedDiscomfort` · `ScreenDateClosed` in the reference); the routing from B's ended state to the right payload; the forced presentation over any screen or sheet; the no-exit rule; routing `Proceed to review` into flow 10 with `claimedNoShow` for A.
- **Server:** B's *ended, review pending* state with `end_reason` (written by Date 06's end transaction); the realtime notification to B; the push trigger with a placeholder string.
- **Not built here:** Date 06's sheets and end endpoint · flow 10 and its `claimedNoShow` headline · the automated no-show check · push copy.

## Acceptance criteria

- [ ] After a Date 06 end, B sees the screen for A's `end_reason`: met → C · partner_no_show → A · uncomfortable → B
- [ ] A is shown for every no-show end, **whether or not B had checked in**
- [ ] The screen appears at once over the Date screen, the chat or an open sheet; if the app was closed it is the first screen on the next open
- [ ] There is no back, close or swipe-down; Android back does nothing; `Proceed to review` is the only tappable element
- [ ] `Proceed to review` opens flow 10 — with the claim headline from A, the normal review from B and C
- [ ] Until B's review is sent, every app open returns to this screen or to the started review; Search, Likes and Instant stay blocked
- [ ] A contains no Show-up Rate value and no "less visible / lose App access" line
- [ ] B uses no gendered pronoun and does not mention "unsafe"
- [ ] The three frames share one screen component; switching payloads changes no layout value
- [ ] Copy matches the strings above exactly
- [ ] **Evidence of done:** screenshots of **A, B and C** at **375 × 667, 390 × 844 and 430 × 932** — 9 images. On 375 × 667, state that the CTA is reached by scrolling. Plus one recording on two phones: A ends with *My date isn't showing up* while B is in the chat → B's chat closes and A appears → Proceed to review → flow 10 with the claim headline → kill and reopen B's app before sending → B lands back in the review.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json`. Use them exactly — do not invent names, and do not restate a payload the registry already defines. **Registry 1.4.39 / taxonomy 1.4.31** — three §11 rows **NEW**, no new events.

| What | Event | Payload |
|---|---|---|
| A shown | `screen_viewed` | `screen_id: "date_partner_ended_no_show"`, `screen_name: "Date - Partner ended no-show"` (§11 — **NEW row**), `referrer_screen_id` |
| B shown | `screen_viewed` | `screen_id: "date_partner_ended_uncomfortable"`, `screen_name: "Date - Partner ended uncomfortable"` (§11 — **NEW row**), `referrer_screen_id` |
| C shown | `screen_viewed` | `screen_id: "date_partner_ended_met"`, `screen_name: "Date - Partner ended met"` (§11 — **NEW row**), `referrer_screen_id` |

**Rules for this screen**

- `screen_viewed` fires each time the screen is shown, including when B reopens the app before starting the review.
- `referrer_screen_id` is the screen B was on when it appeared (e.g. `date_screen`, the chat); absent on a cold start.
- `Proceed to review` fires nothing — flow 10's `screen_viewed` with `referrer_screen_id` set to the screen's id measures it.
- **Must NOT fire from this ticket:** `date_ended` (Date 06, server, fires once for A's end) · `date_cancelled` (Date 07) · `date_ended_in_chat` (Date 02) · any flow 10 review event.
- **Never in a payload:** the match's ID, check-in state, coordinates, free text.

**Tracking acceptance criteria**

- [ ] Each frame fires `screen_viewed` with its §11 `screen_id` and `screen_name`
- [ ] Proceed to review fires no event of its own
- [ ] No `date_ended` is emitted from B's client

**Currently in the build: none of it.** Every row above is `Not built`.

## Out of scope

Date 06's sheets, the end endpoint and `end_reason` (Date 06). Cancel this date and what the match sees there (Date 07). The chat and its End the date (Date 02). Flow 10, the claim headline and the automated no-show check. The Show-up Rate formula and its display. The parked *unsafe* route. Push, SMS, WhatsApp and email copy.

## Dependencies

- **Date 06:** `end_reason` (§43), the end transaction (writes B's ended state), per-date serialisation
- **Flow 10:** `ScreenRating` and its `claimedNoShow` headline. **If flow 10 isn't built when this ships, Date 06's end CTAs ship hidden** — an end with nowhere for B to land is a dead end.
- **Shared:** `Atmosphere`, `StatusBar`, `HomeIndicator`, `Avatar`, `Button`, `Icon`
- **Tracking registry 1.4.39 / taxonomy 1.4.31**

## Open before the sprint

1. **B's row 2 promises "tell us what happened from your point of view", but B goes to the normal review**, which has no field for that. Either flow 10's normal review gains a "your side" field when `end_reason` is `uncomfortable`, or the row is reworded. To decide in the flow 10 ticket. This ticket ships the copy above until then.
2. **Push copy** for B — deferred: all push copy is written at the end of the project. Build the trigger now with a placeholder string.
3. **Show-up Rate display format** — still open project-wide (`JIRA-HANDOFF.md` → *Open decisions every ticket must carry*). A names the rate with no value, so it is unaffected.
