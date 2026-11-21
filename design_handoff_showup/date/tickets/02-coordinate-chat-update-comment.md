# [Date 02] Update 9 Nov 2026 — answers to the build questions

**This comment is the change list, not the spec.** The description has been updated in place (every change is marked **(9 Nov)**), and the spec sheet re-exported with three new frames. Build and test from the description.

## Your questions

**a. Three error messages — yes, with these placements:**
- **Chat couldn't load (frame I):** `Couldn't load your chat. Check your connection and try again.` · `Try again`. Replaces the thread; the header stays; no dock until the chat has loaded.
- **Message didn't send (frame J):** `Couldn't send. Check your connection and try again.` The message stays in the thread, dimmed, with no Delivered / Read line. **The error line is the resend button** — same reason, never sent twice, nothing reaches the match until it sends.
- **End date didn't go through (frame K):** `Couldn't end the date. Check your connection and try again.` Inside the sheet, above the buttons. The sheet stays open, the buttons don't move, `End date` retries. The date has not ended until the server confirms. Same box as Date 03's no-location error.

**b. Dock chips — not 36.** **38 high, 44 tap area** on iOS and Android: 3 px hit slop on every side into the 6 px gap (38 + 6 = 44, so the slops meet without overlapping). Not 48 on Android — four rows would cost 32 px more with the keyboard up. B's large tiles are unchanged.

**c. Cancel guide at ~130 % text on the smallest iPhone — accepted.** The 375 × 667 AC applies at default text size. At larger sizes the thread scrolls to the guide's **top** and the user scrolls a little to `Got it`. The field stays locked until then.

**d. Header stops growing at 165 % — yes**, on both platforms.

**e. Yes** — `Change meet time` and `Change location` stay hidden until Date 05 ships, here and on Date 01's locked card.

## What changed in the description

- **Banner** under the header lines
- **Frames table:** I, J, K added
- **Decided before build:** chips 38 / 44 · three error states · larger text · header cap · change chips confirmed hidden
- **Copy:** new *Errors* block (three strings + `Try again`)
- **Behaviour:** new *When something fails* section
- **Acceptance criteria:** chips line rewritten (was "36 high"); guide line scoped to default text size; four new lines (header cap, I, J, K); evidence adds I, J, K and D at the largest text size
- **Tracking:** no new events or properties. One rule added: failures fire nothing new — `message_sent` only on the actual send, `date_ended_in_chat` only when the server ended the date, `chat_opened` only once loaded

## Spec sheet — re-exported 9 Nov

New frames **I**, **J**, **K** (callouts ⑰ ⑱ ⑲). Callout ⑨ now reads min-h 38 / tap area 44. Values: `ScreenChatOpen` (`loadFailed`, `initialEndError`, `failed` on a seed message), `EndDateSheet` (`error`) and `BigActionChip` (compact hit slop) in `date/screen-chat-reference.jsx`.
