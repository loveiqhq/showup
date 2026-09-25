# [Profile 03] Verify email — 6-digit code entry

**Parent:** Profile creation flow (epic) · **Type:** Story · **Priority:** High
**Attachments:** `profile-03-verify-email-spec-sheet.png`

## Description

The code screen. Six slots, a numeric keypad, and two ways out for the user who never got the email. Entered from step 2 when Continue triggers the send.

**Still step 2 of 3.** The progress bar is `current={2}`, identical to the email screen's — the user is on the email step until the code is confirmed. Verification is not a fourth step. Do not add a segment, and do not animate the bar on arrival.

Exits:

- **"Verify code"** with six correct digits → step 3, date of birth
- **"Change email address"**, and the header's **back** → the email step, address preserved
- **"Send a new code"** → stays here; slots clear, cooldown re-arms

**Two states, one component, one ticket.** A is arrival — empty slots, caret on slot 1, a fresh 24-second cooldown counting down. B is a wrong code submitted.

## The one thing that matters most

**The entered digits are preserved on failure.** Wiping six slots on a near-miss is hostile: the user almost always mistyped one digit, and clearing the row forces them to re-read the entire code from their inbox. Backspace then corrects from the right, as normal. If one behaviour on this screen gets reviewed, make it this one.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** Status bar (54, fixed) → AppHeader (52, fixed) → content (`flex: 1`, padding `4 24 0`, overflow hidden) → numeric keypad (fixed) → home indicator (28, fixed).

⚠ **This screen is bottom-slack, not bottom-anchored.** Unlike steps 1 and 2 — where the spacer pushes the CTA to the bottom — here everything is one top-anchored stack and the **single `flex: 1` spacer sits at the very bottom** of the content column. The CTA therefore sits directly under the slots it belongs to, not floating at the base of the screen. Do not move the spacer above the CTA to "match" the other screens.

⚠ **Slot arithmetic:** 6 × 49 + 5 × 8 = **334** against a 342 content width, laid out with `justify-content: space-between` so the row sits flush without any slot stretching. **Do not switch the slots to `flex: 1`** — at 375 they would drop below 44px and stop being reliable targets. Let the gap absorb the difference.

⚠ **The status region under the slots is reserved** — `min-height: 30`, `margin-top: 12`, `aria-live="polite"`, empty in state A. That is what keeps the CTA and both secondary actions at the same Y in both states. Same rule as step 1; the email screen is the exception, not this one.

📎 All specs are on the attached **profile-03-verify-email-spec-sheet.png**, keyed ①–⑰.

> **Reference implementation:** `design_handoff_showup/profile/screen-verify-email-reference.jsx`. State props: **A** `<ScreenVerifyEmail/>` · **B** `<ScreenVerifyEmail error={true}/>`.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

The keypad. `NumericKeypad` in the reference file is a mock of the iOS dialer — it lives in that file rather than in shared components because only this screen uses it (phone verification has its own variant). **Request the numeric / one-time-code keyboard and ship the platform's**, including its autofill. Never spec its height. It is drawn here only so the artboard shows the true content height above it.

Worth knowing: the keypad mock is **shorter** than the QWERTY one, which is why this screen can afford a full-width CTA plus two secondary rows where the email step cannot.

## Copy — final strings

Section title (AppHeader): `The basics`
Headline: `Please verify your email.` — "email" italic with the orange wash
Sub copy: `We sent a 6-digit code to ` + **the address, inline and bold** + `.`
CTA: `Verify code`
Resend question: `Didn't receive a code?`
Resend, cooling down: `Send a new code in 0:NN`
Resend, available: `Send a new code`
Change address: `Change email address`
Error: `That code doesn't match. Check your inbox or request a new one.`

The headline is the only one in the group that ends in a **full stop** rather than a question mark — the user has nothing to decide here, only something to do.

**Echoing the address back is the whole point of the sub copy.** "I never got the code" is nearly always a typo one screen back; showing the address lets the user catch their own mistake before they wait, and it is what makes the change-address link findable. The address renders at Manrope 700 in `--liq-fg` against the `--liq-neutral-200` line, is **never truncated**, and wraps to two lines if long.

The error copy names **both** recoveries — re-read it, or get a new one — because at that moment the user does not know which of the two they need.

## Behaviour

- On arrival: six empty slots, blinking caret on slot 1, keypad up, and a **fresh 24-second cooldown** already counting down (a code was just sent).
- Digits fill left to right. Backspace clears the right-most filled slot.
- **One logical input, six boxes.** Pasting a 6-digit string fills all six, and platform email autofill must be able to populate the row in one go.
- **Only one slot is active at a time**, and it carries a real blinking caret (2 × 26, `su-caret-blink` 1s steps(2, end) infinite). A static bar reads as a filled slot at a glance — the one thing this row must never be ambiguous about.
- **In the error state no slot is active and there is no caret.** The whole row is wrong, so highlighting a single position would lie about where the problem is.
- On failure: all six slots take the error treatment at once, the digit colour goes to `#7A1F26`, the row runs a one-shot `su-shake` 480ms re-keyed per failure, and the error card fills the reserved region.
- **The failure releases the cooldown.** State B shows "Send a new code" already live. **Do not re-arm the cooldown on a wrong code** — making the user wait after a failure punishes them for the app's own ambiguity about which recovery they need.
- The CTA is disabled until all six digits are filled in state A, and **stays enabled in the error state** so retry is one tap.
- **Errors are inline, never a modal.** A dialog would cover the slots the user has to fix.
- "Send a new code" clears the slots and re-arms the cooldown.
- Press feedback only, no hover.

## Persistence

**The flow position advances only on a confirmed code.** A user who quits on this screen relaunches **here** — not on step 3, and not back on the email step.

A relaunch must **not** silently re-send a code: show empty slots with the resend link live, because the original code may still be valid in their inbox. Resuming is silent — no prompt, no toast (flow README rule 4a).

## Acceptance criteria

- [ ] StepProgress is `steps={3} current={2}` — **identical to the email screen's**; the bar does not advance on this screen
- [ ] Headline is Lora 700 / 32 / 1.1 with "email" as the single italic em carrying the orange wash, ending in a full stop
- [ ] Sub copy echoes the **actual address** inline at Manrope 700 / `--liq-fg`, never truncated, wrapping if long
- [ ] Slot row: six slots at **49 × 62**, radius 14, `gap: 8`, `justify-content: space-between`, `flex: none`, with `aria-label="Enter your 6-digit verification code"` on the row
- [ ] Digits are Lora 700 / **30** with `tabular-nums`
- [ ] Four slot states render per the sheet — empty / active / filled / error — each with its own border, halo and background, transitioning at 180ms
- [ ] The active slot shows a **blinking** caret, 2 × 26 in `--liq-primary-500`; only one slot is active at a time
- [ ] Pasting six digits fills all six slots, and platform autofill populates the row
- [ ] **A failed attempt preserves all six entered digits**
- [ ] In the error state: every slot takes the danger treatment, digit colour `#7A1F26`, **no active slot and no caret**, and `su-shake` 480ms fires once per failure
- [ ] The error card is the **same component** steps 1 and 2 use, reading exactly `That code doesn't match. Check your inbox or request a new one.`, announced via `aria-live="polite"`, and rendered **inline — never a modal**
- [ ] The status region is reserved at `min-height: 30` so the CTA and both secondary actions sit at the identical Y in both states
- [ ] CTA is `Button variant="sunset" size="lg" fullWidth`, label `Verify code`, h56 — **sunset and full-width, the only such button in this group**; not the round orange NextButton
- [ ] The CTA is disabled until six digits are entered, and enabled in the error state — **the group's one deliberate exception** to the never-disabled rule
- [ ] Resend question and action sit on **one baseline row**; the countdown uses `tabular-nums` so it does not jitter
- [ ] A wrong code **does not** re-arm the cooldown — the resend link is live in the error state
- [ ] `Change email address` returns to the email step with the address preserved, and the header's back does the same
- [ ] `Send a new code` clears the slots and re-arms the cooldown
- [ ] Quitting the app here and relaunching returns to **this** screen, with empty slots and the resend link live, **without** silently sending a new code
- [ ] The platform numeric / one-time-code keyboard is used — `NumericKeypad` is not implemented
- [ ] No absolute Y positioning; the spacer is at the bottom of the content column, not above the CTA
- [ ] Copy matches the strings above exactly
- [ ] **Evidence of done:** screenshots of **both states** at **375 × 667, 390 × 844 and 430 × 932** — six images. Nothing clipped, no scroll, slots never below 44px wide, CTA and both secondary rows fully visible above the keypad. Confirm the CTA and secondaries sit at the same Y in both states.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **D — Profile creation**). Use them exactly — do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_email_verification"`, `screen_name: "ProfileEmailVerification"`, `referrer_screen_id` |
| Step entered | `profile_step_viewed` | `step_id: "email_verify"`, `step_index: 2` — **still step 2**; the bar does not advance |
| Code dispatched (server) | `email_code_sent` | `provider` |
| Code submitted | `email_code_submitted` | `attempt_no`, `result: "pass"\|"fail"` — the attempt distribution is what tells you whether the codes, the template or the copy is at fault |
| Send a new code | `email_code_resent` | `attempt_no` |
| Change email address | `profile_step_viewed` | on arrival back at `step_id: "email"` — no separate change-email event; the return rate to step 2 **is** the signal |
| Step completed (code confirmed) | `profile_step_completed` | `step_id: "email_verify"`, `time_on_step_s` |

**Two events fire on entry, and they are not duplicates.** `screen_viewed` answers *which screen*, for navigation and for searching the analytics tool. `profile_step_viewed` answers *where in the flow*, for the completion funnel. `email_verify` is the case that proves they differ: one screen, and a step that holds at 2 of 3.

**`screen_name` / `screen_id` / `step_id` are three separate vocabularies.** `screen_name` is the readable label you search for; `screen_id` is the stable key a saved funnel binds to; `step_id` is the flow position. All three come from `tracking/enums.json` (§11 and §2) — never from a literal at the call site.

**`screen_viewed` sends three properties here, not five.** `screen_id`, `screen_name`, `referrer_screen_id`. The registry also defines `last_used` and `state` on this event and **both are omitted on every profile screen** — `last_used` means something only on a screen that offers a sign-in method list, and `state` belongs to screens whose one registry row covers many states. Since `screen_viewed` fires once on mount, `state` could only ever report the initial one. Send neither; an empty string is worse than an absent property (decision 29).

**The code is never sent** — not on success, not on failure. `attempt_no` and `result` only.

**Relaunch-into-this-screen** needs no event of its own: `screen_viewed` alongside an `app_opened` with `entry_point: "cold"` in the same session answers it.

## Out of scope

Sending the code, the email template itself, code lifetime and rate limiting (all build item 08), step 3, phone verification (its own screen in the welcome flow, with its own keypad variant), and any account-recovery path for a permanently inaccessible inbox.

## Dependencies

- **Email code sending and validation** (handoff README build item 08) — this screen is unusable without it, making it the story's hard blocker
- **Story 02 (Email)** — the origin, and the destination of both back and change-address
- **Step 3 (date of birth)** — the destination of a confirmed code
- The error card, the header shell and the flow state machine from story 01
- Platform one-time-code autofill support

## Open (not blocking)

- **This screen's disabled CTA is a settled exception, not drift.** Every free-text field in the flow keeps its CTA live and produces an error on press (see flow README rule 2c); `Verify code` is disabled until all six digits are entered because a partial code has nothing to validate and the slots already say "six digits" visually. It is recorded as the group's one exception — do not "fix" it to match the other screens.
- **The 24-second cooldown is the design's number.** It must match the backend's real resend limit; if the backend allows a resend sooner, the wait is invented, and if it allows one later, the live link will fail.
- **Code lifetime is unspecified.** What does a user see when they enter a correct-but-expired code? The current design has one error string, and "expired" needs its own — it points at resend, not at re-reading.
- **No cap on attempts.** Rate limiting is a build-item-08 concern, but the *screen* needs a state for "too many attempts" and there isn't one designed.
- **A user whose inbox is unreachable is stuck** — mandatory flow, no skip, and change-address only helps if they have another address. Worth confirming the support path.
