# 07a · Check-in at meeting location — decisions (2 Nov 2026)

Input for the 07a ticket. Kit: `ui_kits/show-up/screens-date-chat.jsx` → `CheckInBar`, `CheckInConfirmSheet`, `CheckInDoneSheet`; artboards Check-in · 1–3.

## Flow
1. Check-in bar (deep ink) replaces the End Date bar until the user checks in.
2. Tap **Check-in** → guard pop-up: "Have you arrived at the meeting location for your date with {name}?" · **Not yet** (top, secondary) / **Yes, I've arrived** (bottom, gradient) — order is deliberate so a quick tap on the first button never confirms. Not yet stores nothing.
3. **Yes** → location + timestamp locked → "You are amazing for showing up!" · **Close** → End Date bar (existing flow).

## Rules
- **Cancel date** link under the Check-in bar → existing Cancel this date flow (penalty guard). ⚠ *Superseded 2 Nov 2026:* the link is **End date** — it does what the End Date bar does (wrap-up sheet), available before check-in. **Cancel this date** stays as its own link at the end of the date screen.
- **Match sees in-app:** "{name} has arrived at the meeting location" in the same slot and style as a new chat message (artboard Check-in · 4). CTA Open chat.
- **Chat stays evidence:** when check-ins are missing or > 200 m apart, the chat (chips + sent/replied) is reviewed to decide what happened.
- **"I arrived at the spot" chip** (chat): not checked in yet → runs the same check-in flow (guard → done); nothing is sent to the chat. Already checked in → opens the chat with the chip's prefill, as today.
- **Stored on Yes:** the device's current location + timestamp, against this date. Internal only — never shown to either user, never sent to analytics.
- **Match:** within **200 m** of each other's check-in.
- **Available:** always while the date is active (meet time can be changed in the chat).
- **Location off:** show the existing location-off screen (as Search 04). No check-in without a location. Mention in the ticket — not drawn in 07a.
- **Match notified:** push to the other person that {name} has arrived at the meeting location. Copy TBD.

- **Report a problem → "My date isn't showing up"**, not checked in yet → check-in flow first ("Check in first, so we can confirm you were there."). Confirmed → the guidance continues. Not yet → back to the menu.

## Change meet time / location (chat)
- The "Change meet time" and "Change location" chips open pickers, not free text.
- **Time:** later the same day only, within the match's availability. 15-min slots.
- **Place:** public places only (cafés, bars, restaurants — Google Maps places). No private addresses.
- Sending posts a suggestion card. The main screen and chat header show the new value at once, tagged "Waiting for {name}" with the old value struck through.
- **Binding rule** (picker, sent card, main screen): "Until {name} agrees, {time} at {place} stays binding. Not showing up there counts as a no-show."
- The match answers **I agree** or **Suggest another** (a new suggestion replaces the open one).
- Agreed → tag "Agreed", system line "Meet time is now 19:30". Check-in distance (200 m) is compared against the agreed place.

## Show-up Rate (new logic)
- Both checked in within 200 m → date happened.
- No check-ins, both confirm in the review → date happened.
- One checked in and reports the other absent → no-show for the other. The other can claim they were there → chat happened during the date? yes = no no-show · no = no-show.

## Decided 2 Nov (round 2)
- 07a is the only version — no separate section 07.
- UI copy says **Check-in**. Tickets and tracking say **arrival check-in** (keeps it apart from the 48 h Check-In).
- **Auto-switch to flow 10:** a date still open 2 h after the agreed meet time moves both users into flow 10 Review your date. Search stays blocked until each has done the review. Anchor = latest *agreed* time (a pending suggestion doesn't move it).
- **Suggestions expire** at the binding meet time. Unanswered → expired, the original stays binding.
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

## Open
- Review deadline: if one person never reviews, when does the Show-up Rate outcome become final?
- "I was there" claim — decide with flow 10 / Show-up Rate logic. Includes B disputing "You didn't show up" inside the review.
- Notification copy.
