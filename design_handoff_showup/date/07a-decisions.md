# 07a · Check-in at meeting location — decisions (2 Nov 2026)

Input for the 07a ticket. Kit: `ui_kits/show-up/screens-date-chat.jsx` → `CheckInBar`, `CheckInConfirmSheet`, `CheckInDoneSheet`; artboards Check-in · 1–3.

## Flow
1. Check-in bar (deep ink) replaces the End Date bar until the user checks in.
2. Tap **Check-in** → guard pop-up: "Have you arrived at the meeting location for your date with {name}?" · **Not yet** (top, secondary) / **Yes, I've arrived** (bottom, gradient) — order is deliberate so a quick tap on the first button never confirms. Not yet stores nothing.
3. **Yes** → location + timestamp stored → "You are amazing for showing up!" · **Close** → End Date bar (existing flow) · **Undo check-in** → stored check-in deleted, back to the Check-in bar *(added 6 Nov 2026)*.

## Rules
- **Cancel date** link under the Check-in bar → existing Cancel this date flow (penalty guard). ⚠ *Superseded 2 Nov 2026:* the link is **End date** — it does what the End Date bar does (wrap-up sheet), available before check-in. **Cancel this date** stays as its own link at the end of the date screen.
- **Match sees in-app:** "{name} has arrived at the meeting location" in the same slot and style as a new chat message (artboard Check-in · 4). CTA Open chat.
- **Chat stays evidence:** when check-ins are missing or not overlapping (150 m radius each), the chat (chips + sent/replied) is reviewed to decide what happened.
- **"I arrived at the spot" chip** (chat): not checked in yet → runs the same check-in flow (guard → done); nothing is sent to the chat. Already checked in → opens the chat with the chip's prefill, as today.
- **Stored on Yes:** the device's current location + timestamp, against this date. Internal only — never shown to either user, never sent to analytics.
- **Match:** each check-in has a **150 m** radius; the two count as "together" when the circles overlap. Compared with each other only — the system never knows for sure where they agreed to meet (they may have agreed in free text). *(3 Nov 2026 — was 200 m.)*
- **Available:** always while the date is active (meet time can be changed in the chat).
- **Location off:** show the existing location-off screen (as Search 04). No check-in without a location. Mention in the ticket — not drawn in 07a. ⚠ *Made precise 5 Nov 2026:* while location is not usable, **the Date screen is blocked by the location gate** (Search 04, full screen). Check-in can therefore never be tapped with location off — there is no location-off state inside the check-in flow. No card on the Date screen (parked location questions 4–5 closed). The date gate shows the date (time, place, `Starts in …` / `Started … ago`) and one CTA — **no Report a problem**: the only way forward is giving location access. Kit: Location gate · date (`date-loc-*`).
- **Match notified:** push to the other person that {name} has arrived at the meeting location. Copy TBD.

- **Report a problem → "My date isn't showing up"**, not checked in yet → check-in flow first ("Check in first, so we can confirm you were there."). Confirmed → the guidance continues. Not yet → back to the menu.

## Change meet time / location (chat)
- The "Change meet time" and "Change location" chips open pickers, not free text.
- **Time:** later the same day only, within the match's availability. 15-min slots.
- **Place:** public places only (cafés, bars, restaurants — Google Maps places). No private addresses.
- Sending posts a suggestion card. The main screen and chat header show the new value at once, tagged "Waiting for {name}" with the old value struck through. ⚠ *Superseded 3 Nov 2026 (round 4): the binding value stays big; the suggestion is shown small.*
- **Binding rule** (picker, sent card, main screen): "Until {name} agrees, {time} at {place} stays binding. Not showing up there counts as a no-show."
- The match answers **I agree** or **Suggest another** (a new suggestion replaces the open one).
- Agreed → tag "Agreed", system line "Meet time is now 19:30". Check-ins are compared with each other (150 m radius each, circles overlap), not with the agreed place.

## Show-up Rate (new logic)
- Both check-ins overlap (150 m radius each) → date happened.
- No check-ins, both confirm in the review → date happened.
- One checked in and reports the other absent → no-show for the other. The other can claim they were there → chat happened during the date? yes = no no-show · no = no-show.

## Decided 2 Nov (round 2)
- 07a is the only version — no separate section 07.
- UI copy says **Check-in**. Tickets and tracking say **arrival check-in** (keeps it apart from the 48 h Check-In).
- **Auto-switch to flow 10:** a date still open 2 h after the agreed meet time moves both users into flow 10 Review your date. Search stays blocked until each has done the review. Anchor = latest *agreed* time (a pending suggestion doesn't move it).
- **Suggestions expire** at the binding meet time. Unanswered → expired, the original stays binding. ⚠ *Superseded 3 Nov 2026 (round 4): the deadline is meet − 0:30.*
- **One open suggestion per type:** one time + one place can be open together, answered separately. A new suggestion of the same type replaces the open one. Binding line always combines the agreed values (e.g. agreed time + still-binding place).

- **Places source:** plan with Google Places; may swap during implementation.
- **Retention:** check-in location + timestamp + user ID kept at least 1 year (legal claims). Legal page to state it.
- **Show-up Rate resolution** (one-sided reports, mismatched "I was there" claims, chat evidence such as "where are you?" and whether it was answered) → designed with the Show-up Rate logic / flow 10, not in 07a tickets. 07a only stores the data.
- **Auto-switch applies to every open date** (no check-in, one check-in, open report). Expected normal case with one check-in: that person ends the date via the UI (End Date bar) before +2 h; the switch is the fallback.
- **When A ends the date, B is moved by the existing other-side screens** (not the +2 h switch): "You didn't show up" → review (penalty) · "Your date ended it" uncomfortable/unsafe → tell us your side (review, no verdict) · "Your date ended it" normally → thank-you + review. The +2 h switch only catches dates where nobody ended it.
## Decided 2 Nov 2026 (round 3 — Date 01)
- **No 30-min timer in 07a.** "How the date works" rewritten: Meet at {place} at {time} · Check in when you arrive · 30 minutes, no pressure · End the date in the app.
- **After the date card** removed from the date screen; designed with the review (flow 10).
- **Match Mate** content is not defined — its own design task + ticket (Date 10). Date 01 builds the slot only. Brief: topics from both profiles · ice-breaker questions · quick games · first-date safety tips · what to do if it's awkward. Source: library + AI picks. Visible always on the date screen. Tone: a mix of light and deeper.
- **Search, Likes and Instant are blocked** while a match/date is active; **Settings stays open**. Hint: "Available again once your date is finished."
- **Only what matches is shown.** Different motivations → no intent row. Vibes: only shared ones ("You both want"), none → no row.
- **Order Ride** = a ride-app API (e.g. Uber) later. Hidden until it exists; quick actions are 3 tiles.
- **Locked Coordinate card:** no progress bar, only "Messaging available from {meet − 1:30}".

## Decided 3 Nov 2026 (round 4 — Date 05)
- **Answer deadline = binding meet time − 30 min**, for time and place alike. Until a suggestion is agreed, the original time and place stay binding; not agreed by then → **expired**, the original stays binding.
- The deadline counts from the **binding** time, never the suggested one (19:00 binding, 19:30 suggested → answer by 18:30). After a time is agreed, a still-open place suggestion takes its deadline from the new time.
- Suggestions can therefore only be made and answered from the match until meet − 0:30 — before the chat unlocks through the locked Coordinate card's tiles, after it in the chat. *(Corrected 5 Nov 2026 — was: "between the chat unlock (meet − 1:30) and meet − 0:30 — a 60-minute window", superseded by the 3 Nov decision to allow changes from the match on.)*
- Shown in the binding note ("unless {name} agrees by {deadline}"), on the receiver's card ("Answer by {deadline}"), and on expiry as a system line ("No answer by {deadline} — {time} at {place} stays.") with the card marked *Expired*. Kit: Change · 9.
- **The binding value is the big one.** While a suggestion is pending, the main screen and the chat header keep the binding time / place at full size; the suggestion sits underneath, small: `You suggested {value}` + `Waiting for {name} to agree` (or `{name} suggested {value}` + `Waiting for you to agree`). Only on **Agreed** does the new value become the big one, with `was {old}` struck through.
- **Change requests use the Coordinate card** for the person who has to answer (Change · 10 time, · 11 place): hoisted under the hero like an unread message, tag `Change request`, `{name} suggested {value}` with the old value struck through, `Answer by {deadline}` / `Until you agree, {time} at {place} stays binding. Not being there counts as a no-show.` (explicit — 3 Nov 2026), buttons **`Open chat`** (first, secondary) · **`I agree`** (second, primary) — agreeing is possible right on the card; **Suggest another** lives in the chat (it needs the picker and the conversation). The binding action sits second so a quick tap on the first never agrees. Agreeing on the card posts the same system line in the thread (`Meet time is now {value}`), so the evidence is identical. Outranks an ordinary unread message.
- **The Coordinate card is always orange when it needs a reaction** — new message, change request, arrival — whatever the reason's tone. Users learn: orange = chat, react. Only the reason badge inside keeps its own tone.
- **Purple is REAL, orange is change requests and chat.** The time + place card shows only binding values. A suggestion **from the match** appears only on the orange Coordinate card — no line and no binding note on the purple card. **Your own** waiting suggestion keeps its small line (`You suggested {value}` · `Waiting for {name} to agree`) plus the compact binding note, because nothing on the orange card covers it. Agreed → the new value is big with one quiet hint, `Changed · was {original}` — the **original** value only, never a chain (full history is in the chat), no "Agreed" pill (everything on purple is agreed), hidden after the meet time. Applied per field: a time and a place in opposite directions each follow their own rule; two of the same type cannot both be open (rule 3).
- ~~**To decide:** whether the Change meet time / Change location chips hide after meet − 0:30.~~ ⚠ *Decided 10 Nov 2026 (Date 05): hidden for both from binding meet − 0:30.*

## Decided 3 Nov 2026 (round 5)
- **Check-in radius 150 m**, circles overlap → together. Grey areas (agreed elsewhere in free text, one arrives and leaves) are accepted for MVP: check-ins + review answers + chat usage give a good-enough guess.
- **Exits stay as they are.** Cancel this date = clear no-show. End date / "I'm heading out" = check what happened.
- **Chat tutorial stays** — always before the first open; it explains why the rules matter.
- **"Binding" stays** in UI copy — it is the brand's own word (Welcome: "A match is a *binding* date").
- **Commitment at the source:** the 48 h Check-In availability step should say that a match in that window is a binding date. To check / design.
- **Change time / place from the match on** (not only once the chat opens). Free chat still opens at meet − 1:30. The locked Coordinate card shows `Messaging available from {meet − 1:30}` + two tiles **Change meet time · Change location** (Date-screen quick-action style), opening the same pickers as the chat. A request while the chat is locked shows the orange card with **Suggest another** (opens the picker) · **I agree**, and `Messaging opens at {time}` underneath. Deadline, one-per-type and "original stays binding" unchanged; the suggestion is already in the thread when it opens. Kit: Change · 12–15.
- **Epic rule 3 to update:** "Changes happen through the chat" → through the Coordinate card before unlock, the chat after.

## Decided 5 Nov 2026
- **"I can't make it today" chip** shows a guide in the thread (local, never sent): Show Up is for reliable people · explain why and say sorry · better: move to later today or another place · **a different day can't be set in the app — agree on it between yourselves**. Button **Got it**.
- **Field and send stay locked until Got it.** No prefill for this chip — the user writes the message themselves. After Got it the guide collapses to one line and the field gets focus. Kit: Coordinate · B2 (`chat-open-cancel`).
- Chat dock: the "Tap a chip…" line is removed; compact chips are 36 px high (full labels kept).
- **No move to another day in the app (MVP).** If they want another day, they agree on it in the chat; then one of them ends the date so both can search again. The guide says so.
- **Ending after talking in the chat → no review** (the date didn't happen). The other person sees `{name} had to cancel the date` · "We hope you two found another day to meet. Search is open again" · rate untouched · **Find your next date**. Kit: Cancel this date · 4b (`coord-cancel-4-partner-chat`).
- **Ending in the chat (built 5 Nov 2026):** the chat's **End the date** exists ONLY after an "I can't make it today" message has been sent (either side) — no other chip, message or state shows it. Then both see an **End the date** card in the thread → sheet `End the date with {name}?` · Keep the date · End the date → no review, both can search again. Ender sees `You ended the date` (B5), the other `{name} had to cancel the date` (4b). The card only appears after the apology message, so the chat end always follows an explanation. Kit: Coordinate · B3–B5.
- **Settled 5 Nov 2026:** ending in the chat costs **nobody** Show-up Rate (accepted risk: one message avoids the drop) · the card stays available **until the date has ended**, no cut-off at the meet time · the chat buttons say **End date**, same as the Date screen, to keep copy small.

## Decided 6 Nov 2026 (Date 03 — arrival check-in)
- **Date 04 folds into 03.** "{name} has arrived" on the match's Coordinate card is the other end of the same check-in and can't be tested without it. One ticket, Date 04 retired as a number.
- **No time window.** The Check-in bar shows the whole time the date is active — the guard asks "Have you arrived?", and the real meet time can be agreed in free text without the picker, so no clock can be trusted. Confirms the 2 Nov rule.
- **No GPS fix → retry.** If the device can't return a location, nothing is stored and the user is asked to try again. No low-accuracy fallback, no check-in without coordinates.
- **Undo check-in** — a quiet link under **Close** on "You are amazing for showing up!", and only there. Undo **deletes the stored location + timestamp**, closes the sheet and returns to the **Date screen** with the **Check-in bar** again (also when the check-in started from the chat chip). Once the sheet is closed there is no undo.
- **The match is notified on Close**, not on Yes, so an undo never reaches them. Leaving the app with the sheet open counts as Close.

## Open
- **Show-up Rate "10-min grace" line** (`screens-score.jsx`) — nothing measures lateness against the binding time. Drop it or define it (check-in > binding time + 10 min).
- Review deadline: if one person never reviews, when does the Show-up Rate outcome become final?
- "I was there" claim — decide with flow 10 / Show-up Rate logic. Includes B disputing "You didn't show up" inside the review.
- Notification copy.

## Decided 10 Nov 2026 (Date 05 — change time and place)
- **Tiles and change chips are hidden for both** from binding meet − 0:30, once either person has checked in, and (Change meet time only) while there is no later slot. An open suggestion stays answerable until its deadline.
- **Two requests from the match:** the hoisted card shows the earlier deadline first, time first on a tie; the other follows. Both are in the thread.
- **Chat unlock:** an agreed later time moves a locked chat's unlock; an open chat never re-locks.
- **"Nearby" places** are ordered and measured from the binding meet place.
- **Failure states** use Date 02's patterns: Suggest failed keeps the picker open with the selection · I agree failed shows the red retry line · Places failed → Try again · empty search is not an error. Kit: Change · 16–19.
- **No Decline, no Withdraw** — written down.

## Decided 10 Nov 2026 (Date 06 — End date and report a problem)
- **Check in first** for *My date isn't showing up* (End date sheet **and** Report) and for *I don't feel comfortable* — the same gate as Date 03, with its note. *We met* needs no check-in.
- **No-show end from the binding meet time.** Before it the button is disabled (`You can end the date from {time}`). Nothing measures the 10 minutes. Closes the *Open* "10-min grace" question for the end button; the `screens-score.jsx` line is still open.
- **One guidance, one copy:** "Please give it at least 10 minutes" · CTA `End date and review` · secondary `Back`.
- **Head out now ends the date:** ender → flow 10 (review line added to What happens next); match → `date-ended-discomfort`.
- **Stay for now** stores an internal discomfort flag (evidence for flow 10).
- **No-show end while the match has checked in** → the match sees the neutral `date-closed-positive`; flow 10 decides.
- **"This is on them" removed** from Thanks for waiting — only the ender's rate is promised.
- **Something else** (free text, safety team) — **parked** with *I'm in danger*.
- **End Date bar "Date complete · Rate" variant deleted** (contradicts rule 5).
- Not decided: the copy fixes `End Date` → `End date`, C's "Cancel date in the chat menu" note.

## Decided 12 Nov 2026 (Date 07 — Cancel this date)
- **Two ways to call a date off, two outcomes.** The red `Cancel this date` link + confirm = a clear cancel without reaching out → **penalty**. The chat's *I can't make it today* → End the date (Date 02) = reached out → **no penalty, no review** for either (unchanged).
- **Canceller (red link):** straight to flow 10 `Rate the date · YOU didn't show up` (`rating-you-noshow`) — locked *No* + penalty. **`CancelDoneSheet` is not shown** (its `Back to search` contradicted the search block).
- **Cancelled-on (B):** `Cancel this date · 4` (`ScreenPartnerCancelled`, default variant) — rate untouched, **no review**, CTA goes straight to search. ⚠ Exception to epic rule 5 (B does not enter flow 10).

## Decided 16 Nov 2026 (Date 07 — open questions answered)
- **Canceller (red link, confirmed):** confirms the 12 Nov rule — straight to flow 10 `rating-you-noshow` (locked *No* + penalty), then search opens. **No 24 h search block in MVP.** `CancelDoneSheet` / *Cancel this date · 3* retired. The screen that shows the rate drop is designed later with the Show-up Rate screens.
- **Clear no-show:** the red link without first using Change meet time / Change location counts as a no-show. When the cancel happens makes no difference — notice is not a mitigation; reaching out is.
- **Cancelled-on (B):** *Cancel this date · 4*, rate untouched, no review, CTA to search.
- **Never matched again:** after a red-link cancel the two never appear in each other's search, both directions, permanently. Server-enforced. The guard line "you'll never match {name} again" stays.
- **Penalty (placeholder until the Show-up Rate screens):** one no-show −10, one attended date +10.
- **Screen 4 copy:** "are paused" removed → "People who repeatedly cancel or don't show up become less visible, and eventually lose access."
- **No pause in MVP.** **Account deletion is blocked during an active date** — the option in Settings is unavailable until the date has ended (Settings itself stays open; its full design comes later). So deletion never ends a date and needs no cancel rule. A low Show-up Rate means less visibility, only after at least 4 dates (before that the user is new). Removing access is to be finalised later.
- **Red link visible** from the match until the user checks in (hidden after check-in).
- Kit: `rating-you-noshow` tab hint "Search is paused for 24 hrs…" → "Available again once you've submitted your review."
- **Guard sheet copy (Cancel this date · 2) final.** "her" stays. Typo fixed: "Sometimes life happens — find a solution with {name}!" (was "Sometime… Leonie"). Cost box now states the drop: "lowers your Show-up Rate by 10". Everything else unchanged.
- **Ticket written 16 Nov 2026** (`tickets/07-cancel.md`). Kit: the link is hidden after this user's check-in · `CancelDateConfirmSheet` gains `error` + `initialSent` · `EndErrorBox` gains `text` · artboards Cancel this date · 2b–2d · `rating-you-noshow` penalty row: 24 h clause removed, "Show-up Rate drops by 10". Tracking: registry 1.4.37 / taxonomy 1.4.30.
- **Open:** push copy · the solution box body inside meet − 0:30 (tiles hidden).
- **Push copy deferred (16 Nov 2026):** all notification copy is written at the end of the project, in one pass, so it isn't reworked repeatedly. Tickets build the triggers with placeholder strings.
- **Guard sheet tiles follow Date 05's cutoff (16 Nov 2026):** from binding meet − 0:30 (and after either check-in) a change could no longer be agreed — only running late matters. The solution box loses its body line and both tiles; the title and `Message {name} instead` stay (closes the cancel flow, opens the chat). Kit: `CancelDateConfirmSheet changeClosed`, Cancel this date · 2e.
