# [Date 07] Cancel this date — the red link, the penalty guard and what the match sees

**Parent:** [Epic] 07a Match & date confirmation (`date/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `date-07-cancel-attachment.zip` (the handoff folder, `design_handoff_showup/`) · `date-07-cancel-spec-sheet.png`
**Blocks:** nothing else in 07a. Matching uses the pair block written here (`matching-plan.md` rule 7).
**Blocked by:** Date 01 Date screen (`ScreenMatchCoord`, the end of the scroll) · Date 02 Coordinate chat (`ScreenPartnerCancelled`, the chat) · Date 03 Arrival check-in (the checked-in state that hides the link) · Date 05 Change meet time and place (the pickers) · Date 06 (`EndErrorBox`) · flow 10 `rating-you-noshow` (the canceller's landing; see *Dependencies*)

## Description

There are two ways to call a date off, and this ticket builds the one that costs:

- **The red `Cancel this date` link + confirm (this ticket).** A cancel without reaching out is a **no-show**. The canceller loses **10** Show-up Rate points, goes to flow 10 *YOU didn't show up*, and can search again after that review. The match's rate is untouched. The match gets no review and can search straight away. **The two never meet in search again.**
- **The chat's *I can't make it today* → End the date** (Date 02). The person reached out, so there is no penalty and no review for either. Not built here.

The guard sheet tries to save the date first: a later time or another place (Date 05's pickers), or a message once the chat is open. When the cancel happens makes no difference — reaching out is what counts.

**Nine frames, one ticket:**

| | Frame | When | Reference |
|---|---|---|---|
| A | The red link | date active, this user not checked in | `ScreenMatchCoord coordOpen checkInFlow` (scrolled to the end) |
| B | Guard sheet — chat locked | link tapped, chat locked (before meet − 1:30, nobody checked in) | `ScreenCancelConfirm` |
| C | Guard sheet — chat open | link tapped, chat open (from meet − 1:30, or the match has checked in — 16 Nov) | `ScreenCancelConfirm chatOpen` |
| D | After a change request | a tile → Date 05 picker → sent | `ScreenCancelConfirm initialSent={{kind:'time',value:'20:00'}}` |
| E | Cancel didn't go through | `Cancel anyway` failed | `ScreenCancelConfirm error` |
| I | Guard sheet — from meet − 0:30 | link tapped after Date 05's cutoff | `ScreenCancelConfirm changeClosed` |
| F | After check-in — no link | this user has checked in | `ScreenMatchCoord coordOpen` |
| G | The canceller lands here | cancel confirmed | `ScreenRatingYouNoShow` — **flow 10, consumed** |
| H | What the match sees | the other person cancelled | `ScreenPartnerCancelled` (default variant) |

## Decided before build (12 + 16 Nov 2026)

- **Red link = no-show, regardless of timing.** Three days or ten minutes ahead, the outcome is the same. `hours_notice` is stored for analysis, never used for the penalty.
- **The canceller goes straight to flow 10 `rating-you-noshow`** (locked *No* + penalty), then search. **No done sheet:** *Cancel this date · 3* (`CancelDoneSheet`, "94 → 84 · Back to search") is **retired**. **No 24 h search block in MVP.**
- **Penalty placeholder: one no-show −10, one attended date +10.** Final values come with the Show-up Rate screens. The cost box says `by 10` — the number comes from the **shared rate config**, never a literal in the string. (Supersedes the 4 Nov rule "no value in copy" for this sheet — see `JIRA-HANDOFF.md`.)
- **The match (H):** rate untouched, **no review**, CTA straight to Search. ⚠ Exception to epic rule 5 — the match does not enter flow 10.
- **Never matched again.** After a red-link cancel the two never appear in each other's search, in both directions, permanently. **Server-enforced.** The guard line "you'll never match {name} again" is true and stays.
- **The link shows from the match until this user checks in.** Each person's link follows their own check-in. After check-in, ending is Date 06.
- **The tiles follow Date 05's cutoff (I).** From binding meet − 0:30 (and once either person has checked in) a new time or place could no longer be agreed, so the body line and both tiles are removed; the title stays; `Message {name} instead` stays and is always shown (the chat is open by then) — running late is the only thing left to say. Same check as Date 05's tiles and chips; one function, not a copy.
- **Guard sheet copy is final** (16 Nov): "her" stays; typo fixed to `Sometimes life happens — find a solution with {name}!`; the cost box names the 10.
- **Screen 4 copy:** "are paused" removed — there is no pause in MVP.
- **No pause in MVP, and account deletion is blocked while a date is active** (Settings, designed later). Deleting can therefore never end a date; there is no deletion rule here.

## Layout — read this before building

⚠ **The link is the last item of the Date screen scroll**, below Match Mate: 24 above, 80 below (clears the sticky footer and TabBar). Never pinned, never in the footer.

⚠ **B–E are one `ModalShell` bottom sheet.** Height from content; it scrolls inside on every device, so the choices are reached by scrolling. States replace parts of the sheet in place — never a second sheet.

⚠ **The pickers open over the sheet** (Date 05). Closing a picker returns to the sheet with its state. Sending one shows D in the sheet.

⚠ **The error box ⑩ sits between the cost box and the choices.** Buttons keep their order, size and label.

⚠ **H is a full screen**, not a sheet. The CTA sits at the bottom of the column via a flex spacer — never absolute Y.

📎 All specs are on the attached **date-07-cancel-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–⑫ across eight frames. Black callouts are built here; **red is the failure state**; **violet callouts open another ticket's UI**. The design system has colour and type tokens only, with no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/date/screen-cancel-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. Frame props are in the table above.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **The time and place pickers** — Date 05's `TimeChangeSheet` / `PlaceChangeSheet`, with their rules (deadline, one per type, hiding). Opened from here with `entry: cancel_sheet`.
- **The chat** — Date 02. `Message {name} instead` only opens it.
- **The canceller's landing (G)** — flow 10 `rating-you-noshow`. Its tab hint now reads `Available again once you've submitted your review.` and its penalty row `…your Show-up Rate drops by 10.` (kit fixed 16 Nov; the 24 h clause is gone).
- **The `viaChat` variant of `ScreenPartnerCancelled`** (Date 02's 4b). This ticket builds the default variant on the same component.
- **Status bar and home indicator** are mocks. Anchor to the safe-area insets.

## Copy — final strings

`{name}` is the match's first name. `{when}` · `{place}` are the binding values. `{drop}` is the rate config value (10 in MVP). `{value}` is the requested time or place.

**A · Link:** `Cancel this date`

**B · Guard sheet:** pill `{when} · {place}` · `{name} is counting on you.` · `Are you really ready to *disappoint her?*` · `This isn't a button — it's a real person who cleared her evening, picked something to wear, and is quietly looking forward to meeting *you*. Cancelling lands on her side of the screen.`
Reasons: `Almost every date leaves you **glad you went**. The hard part is only the front door.` · `She chose *you* out of everyone. That's rare — showing up honours it.` · `You'll go to sleep **proud you stayed real** and kept your word.`
Solution box: `Sometimes life happens — find a solution with {name}!` · `Would a later time or another place work? Request it — if {name} agrees, your date goes ahead.` · tiles `Change meet time` · `Change location`
Cost: `Cancelling this date counts as a **no-show** and lowers your **Show-up Rate by {drop}** — a lower rate means fewer likes and matches. Let it stay low and you'll eventually lose access to the app entirely.`
Choices: `Don't cancel my date` · `Cancel anyway and let her down` · `This can't be undone — your date is off and you'll never match {name} again.`

**C · Chat open:** + `Message {name} instead`

**I · From meet − 0:30:** solution box = `Sometimes life happens — find a solution with {name}!` + `Message {name} instead` only

**D · Change requested:** `**{value}** requested. Waiting for {name} to agree — until then your date stays as planned.`

**E · Failed:** `Couldn't cancel the date. Check your connection and try again.`

**H · What the match sees:** `{name} had to *cancel* — we're truly sorry.` · `You made room in your day and you were ready to show up. **Don't be discouraged - someone true as you is just a like away from meeting you.**`
Rows: `Your **Show-up Rate stays untouched.** A date someone else cancels will **never** count against you. You did everything right.` · `Show Up is built on **reliability** and **treasuring each other's time** — real, warm, in-person connection. **This isn't how our community behaves.**` · `People who **repeatedly cancel or don't show up** become less visible, and eventually **lose access.** {name} carries that — not you.`
CTA `Find someone who shows up`

Product terms capitalise exactly: **Show Up · Show-up Rate**.

## Behaviour

**A · The link**

- Shown while the date is active **and this user has not checked in**. It follows **this user's** check-in only — the match's check-in does not hide it.
- Gone after this user's check-in (F), and gone for both once the date has ended or been cancelled.
- Tap → B (or C when the chat is open).

**B / C · Guard sheet**

- `Don't cancel my date`, the scrim or a swipe-down closes the sheet. **Nothing is stored.**
- `Change meet time` / `Change location` open Date 05's picker over the sheet (`entry: cancel_sheet`). The tiles and the body line follow **Date 05's cutoff**: hidden from binding meet − 0:30 and once either person has checked in; Change meet time also hides when there is no later slot.
- `Message {name} instead` (whenever the chat is open, C and I) **closes the cancel flow** and opens the chat (Date 02). Nothing is cancelled.

**I · From meet − 0:30**

- From Date 05's cutoff the solution box shows only its title and `Message {name} instead`. No body line, no tiles. Re-evaluate when the sheet opens and when the clock passes the cutoff while it is open. A picker already open at the cutoff follows Date 05's rule.
- Everything else (cost box, choices, error) is unchanged.

**D · After a change request**

- Sending in the picker posts the suggestion exactly as Date 05 does. The sheet shows the confirmation line in place of the tiles and **stays open**. The binding time and place do not change.
- The choices are unchanged: the user can still keep the date or cancel. A cancel after a request is still a red-link cancel — **the request does not soften the penalty.**

**Cancel anyway**

- Sends the cancel to the server. Both buttons are disabled while it runs. **The date is not cancelled until the server confirms.**
- On success, the server in one transaction:
  - sets the date to **cancelled** for both people and cancels the +2 h job (Date 09)
  - records a **no-show for the canceller** with `penalty_points` from the rate config (10)
  - writes a **permanent pair block** in both directions (`matching-plan.md` rule 7)
  - notifies the match (push + in-app → H)
- **The canceller** → flow 10 `rating-you-noshow`. Search, Likes and Instant open once that review is submitted.
- **The match** → H. Search, Likes and Instant open **immediately**. No review.
- **Both tap Cancel anyway at once:** the server serialises; the first cancel wins. The second request returns *already cancelled*, and that person lands on H as the cancelled-on person.
- **The canceller checked in on another device meanwhile:** the server refuses (link rule). The sheet closes and the Date screen refreshes without the link.

**E · Failed**

- The sheet stays, `EndErrorBox` with the cancel text appears above the choices, `Cancel anyway` keeps its label and retries. `Don't cancel my date` still works.

**H · What the match sees**

- Shown on the next app open or straight from the push. One screen, no back.
- `Find someone who shows up` → Search (the Date screen is gone).

## Data stored

- **On cancel:** cancelled state, who cancelled, timestamp, `hours_notice` (to the latest **agreed** meet time), `penalty_points`, both people's check-in state at that moment, and whether a change was requested from this sheet first. Against the date, with the rest of its evidence.
- **Pair block:** both user IDs, the date ID, timestamp, reason `red_link_cancel`. Permanent; read by the search filter.
- Nothing is stored on open, keep, scrim or Message instead.

## Build inventory

- **Client:** the red link and its visibility rule in `ScreenMatchCoord`; `CancelDateConfirmSheet` (`chatOpen`, `error`, the in-sheet sent state, `drop` from the rate config); wiring the tiles to Date 05's pickers with `entry: cancel_sheet`; `EndErrorBox` with a `text` prop (Date 06's box, extended — one component); `ScreenPartnerCancelled` default variant on Date 02's component; routing the canceller to flow 10 and the match to H.
- **Server:** the cancel endpoint (validates: date active · canceller not checked in · not already ended/cancelled; serialised per date), the cancelled state for both, the no-show record + `penalty_points` from config, the permanent pair block and its search filter, cancelling the +2 h job, the match's push.
- **Not built here:** Date 05's pickers · the chat · flow 10 · the Show-up Rate screen and formula · Settings' blocked deletion · push copy.
- **Retired — do not build:** `CancelDoneSheet` (*Cancel this date · 3*).

## Acceptance criteria

- [ ] The link is the last item of the Date screen scroll and shows only while this user has not checked in; after this user's check-in it is gone, and the match's check-in does not hide it
- [ ] Tapping the link opens the guard sheet; `Don't cancel my date`, scrim and swipe-down close it and store nothing
- [ ] `Message {name} instead` appears only while the chat is open and opens the chat
- [ ] The tiles open Date 05's pickers over the sheet; a sent request shows D and the sheet stays open
- [ ] From binding meet − 0:30 (and after either check-in) the box shows only its title and `Message {name} instead` (I), using the same check as Date 05's tiles and chips; before it, the body line and tiles show
- [ ] `Message {name} instead` closes the cancel flow, opens the chat and cancels nothing
- [ ] The cost box reads `…Show-up Rate by 10…`, with 10 from the shared rate config — changing the config changes the copy, with no string edit
- [ ] `Cancel anyway` cancels only after the server confirms; a failure shows E above the choices and the button retries
- [ ] On success the canceller lands on flow 10 `rating-you-noshow`; **no** done sheet, no "Back to search", no 24 h block
- [ ] On success the match sees H; Search opens for the match immediately and for the canceller after their review
- [ ] After a red-link cancel the two never appear in each other's search (both directions, both Freemium and Premium, including Likes)
- [ ] A cancel after a change request is still penalised
- [ ] The server refuses a cancel from a checked-in user and a second cancel on a cancelled date, whatever the client sends
- [ ] H has no review, no "paused" wording, and its CTA goes to Search
- [ ] Copy matches the strings above exactly
- [ ] **Evidence of done:** screenshots of **A–F, H and I** at **375 × 667, 390 × 844 and 430 × 932** — 24 images (G is flow 10's). On 375 × 667, state that the choices in B–E are reachable by scrolling the sheet. Plus one recording on two phones: link → guard → Change meet time → send → Cancel anyway → canceller on flow 10, match on H → both search: neither appears to the other.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **K — Date Coordination**). Use them exactly — do not invent names, and do not restate a payload the registry already defines. **Registry 1.4.37 / taxonomy 1.4.30** — three events **respecified**, three **retired**, one value set **NEW**, one value set extended, one §11 row **NEW**.

| What | Event | Payload |
|---|---|---|
| Guard sheet shown | `cancel_guard_shown` | **respecified** · `date_id` **NEW**, `chat_open` **NEW**, `penalty_points`, `minutes_before_start` **NEW** |
| Sheet closed without cancelling | `cancel_abandoned_at_guard` | **respecified** · `date_id` **NEW**, `exit` (§46 — **NEW**), `change_requested` **NEW** |
| Picker from a tile | `change_picker_opened` · `change_picker_dismissed` · `change_suggested` | Date 05's events · `entry`: **`cancel_sheet`** (§42 — **NEW value**) |
| Date cancelled | `date_cancelled` | **respecified, server** · `date_id`, `hours_notice`, `penalty_points`, `change_requested` **NEW** · `initiator` and `reason` **REMOVED** |
| H shown | `screen_viewed` | `screen_id: "date_partner_cancelled"`, `screen_name: "Date - Partner cancelled"` (§11 — **NEW row**), `referrer_screen_id` |

**Rules for this screen**

- `cancel_guard_shown` fires each time the sheet opens. There is no separate link-tap event.
- `cancel_abandoned_at_guard` fires once when the sheet closes without a cancel: `exit` = `keep` (Don't cancel my date) · `dismissed` (scrim / swipe) · `message` (Message {name} instead). Opening a picker is **not** an exit.
- **`date_cancelled` fires once, on server success only.** A failed cancel (E) fires nothing new; a retry that succeeds fires it once. The second of two simultaneous cancels fires nothing.
- `hours_notice` = hours from the cancel to the latest **agreed** meet time; negative after it. Stored for analysis; no rule reads it.
- `penalty_points` is the config value at the time (10 in MVP).
- `Find someone who shows up` on H fires nothing — Search's `screen_viewed` with `referrer_screen_id: date_partner_cancelled` measures it.
- ⚠ **Removed payload — check any emitter already written.** The shipped code sends `{date_id, had_reason}`. The target is now `date_id, hours_notice, penalty_points, change_requested`. `initiator` and `reason` are **removed** (one canceller, no reason asked). Supersedes the payload in reconciliation decision 22, not its point: Show-up Rate still needs `hours_notice` + `penalty_points`.
- **Retired — do not fire, do not reuse the names:** `cancel_link_tapped` (the tap always opens the guard; `cancel_guard_shown` measures it) · `partner_cancellation_received` (replaced by `screen_viewed` on `date_partner_cancelled`) · `rematch_started` (a red-link pair is never re-matched; both sides simply search).
- **Must NOT fire from this ticket:** `date_ended_in_chat` (Date 02) · `date_ended` (Date 06) · any flow 10 review event.
- **Never in a payload:** coordinates, place, free text, the match's ID.

**Tracking acceptance criteria**

- [ ] `cancel_guard_shown`, `cancel_abandoned_at_guard` and `date_cancelled` carry `date_id`; `exit` uses only §46 values
- [ ] Pickers opened from the sheet carry `entry: cancel_sheet`
- [ ] `date_cancelled` fires exactly once per cancelled date, never for a failed or losing attempt, and never with `initiator` or `reason`
- [ ] H fires `screen_viewed` with `date_partner_cancelled`
- [ ] No retired event is emitted

**Currently in the build:** `date_cancelled` ships with `{date_id, had_reason}` — **payload change required**. Everything else is `Not built`.

## Out of scope

The Date screen and its scroll (Date 01). The chat, its End the date and the 4b variant (Date 02). Check-in (Date 03). The pickers and their rules (Date 05). End date and Report a problem (Date 06). The +2 h switch (Date 09). Flow 10 and `rating-you-noshow`. The Show-up Rate formula and its display. Settings, including the blocked account deletion. Push, SMS, WhatsApp and email copy.

## Dependencies

- **Date 01:** `ScreenMatchCoord`, the end of the scroll
- **Date 02:** `ScreenPartnerCancelled` (extended, never forked), the chat
- **Date 03:** the per-user checked-in state
- **Date 05:** `TimeChangeSheet`, `PlaceChangeSheet`, §42
- **Date 06:** `EndErrorBox` (gains a `text` prop; its default stays Date 06's copy)
- **Flow 10:** `rating-you-noshow`. **If it isn't built when this ships, the link ships hidden** — a cancel with nowhere to land is a dead end.
- **Matching:** the pair block in the search filter (`matching-plan.md` rule 7)
- **Shared:** `ModalShell`, `PortraitPlaceholder`, `Avatar`, `Atmosphere`, `Button`, `Icon`
- **Tracking registry 1.4.37 / taxonomy 1.4.30**

## Open before the sprint

1. **Push copy** for the match — **deferred (16 Nov 2026):** all push copy is written at the end of the project, in one pass. Build the push trigger now with a placeholder string.
2. **Show-up Rate display and final values** — −10 / +10 are placeholders until the Show-up Rate screens (`JIRA-HANDOFF.md` → *Open decisions every ticket must carry*). Built through the rate config, so the change is one value.
