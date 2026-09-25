# [Welcome 03] Phone verification — enter number, invalid number, enter code, code mismatch

**Parent:** 01 · Welcome & sign-up (epic) · **Type:** Story · **Priority:** High
**Attachments:** `welcome-03-phone-verification-spec-sheet.png`

## Description

The phone path that turns a visitor into an authenticated member. Two screens, two states each, one shared layout column:

| | Screen | State |
| --- | --- | --- |
| **A** | Phone — enter number | default: country pill + number field, keypad open, CTA enabled |
| **B** | Phone — invalid number | validation failed on submit: danger field, helper text names the fix, CTA disabled |
| **C** | Verify — enter code | six slots, first active, resend on a 24s cooldown |
| **D** | Verify — code mismatch | wrong code: digits preserved, inline error glued to the slots, cooldown released |

One ticket because A–D are one column with four content states — building them separately is how the CTA ends up jumping between them.

**Routing.** Entered from the phone method on Welcome back, or from sign-up on Startup. A → C on a successful send. C → the app on a successful verify. Back on A returns to the entry screen; back on C (and "Edit phone number") returns to A **with the number still in the field**.

## Behaviour — the part that isn't visual

**Validation (A → B).** Validate on submit, not per keystroke. Failure keeps the digits, marks the field, replaces the helper line, and disables the CTA until the value changes. The error names the fix — an example number — not the failure.

**Send (A → C).** The number shown on C must be the number actually submitted, formatted with the country code. This is the user's only chance to catch a typo before waiting for an SMS that will never arrive.

**Code entry (C).** Six digits, one per slot, filled left to right; the active slot carries the caret. The CTA is disabled until all six are present. Backspace clears the last filled slot. Support OS SMS autofill / paste of a 6-digit code as a single action.

**Mismatch (D).** On a failed verify:

- **the digits stay** — the user edits the one wrong digit and resubmits; never auto-clear
- the inline error appears in the region that was already reserved, so nothing below it moves
- the CTA stays **enabled** (six digits are still present)
- the resend **cooldown is released to 0** — a stale or mistyped code must not cost another 24s
- the slot row does **one** 480ms shake, then holds still
- the message is announced via `aria-live="polite"`; **focus stays in the input**

**Resend (C, D).** 24s cooldown, rendered as text (`Send a new code in 0:24`) until 0:00, then as a live button. Never a silent resend. Rate-limit server-side; surface the limit in the same helper region if it trips.

**The keypad is the system keyboard.** The mock draws one only to show the true content height (≈214 + 28 home indicator). Ship the platform numeric keypad — do not build the component in the reference file.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** Status bar (54, fixed) → AppHeader (back only, empty title) → content (`flex: 1`, padding `4 24 0`) → keypad → home indicator (28, fixed). The content column is **top-anchored throughout** with a **single `flex: 1` spacer at the bottom** — unlike Startup and Welcome back, nothing floats here.

**Both helper regions are reserved, not conditional** — `min-height: 20` on A / B, `min-height: 62` on C / D — 62 because the verify error box is 60 tall at the 342 content width. The error state must not push the CTA down. This is the single most important rule in the ticket: it is what makes the four states read as one screen.

**Short frames.** At 375 × 667 the keyboard leaves ≈250px of content. The **slot row shrinks from 49 × 62 toward 44 × 56 with gap 6**, and the eyebrow → headline margin collapses first. Never shrink the CTA below 56, never scroll content under the keyboard, never hide the helper region, never drop the secondary actions.

**The background is 2 layers, top-weighted** (orange orb top-right, violet orb top-left; C and D at lower alpha) — no peach wash on these screens, because the keypad owns the bottom half. Full-bleed at `z 0` behind the status bar; chrome and keypad at `z 2`. Anchor real chrome to `env(safe-area-inset-*)`.

📎 All specs are on the attached **welcome-03-phone-verification-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–⑱ across the four states (danger-red numbers are the failure states). Note the design system has colour and type tokens only — there is no spacing scale, so the raw px values are intentional, not an oversight.

> **Reference implementation:** `design_handoff_showup/welcome/screen-phone-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. **States: `<ScreenPhoneNumber/>`, `<ScreenPhoneNumber error/>`, `<ScreenVerifyCode/>`, `<ScreenVerifyCode error/>`.**
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## Copy — final strings

Eyebrow (all four): `Phone verification`
Headline A / B: `What's your **number**?` — "number" italic with the orange underline wash
Sub A / B: `We'll send a 6-digit code to verify it is you.`
Helper A: `Standard message rates may apply.`
CTA A / B: `Send me the code`
Error B: `Please enter a valid number e.g. 176 123 45 678`
Headline C / D: `Enter your **code**.` — "code" italic with the underline wash
Sub C / D: `We just sent a 6-digit code to **{phone}**.`
CTA C / D: `Verify code`
Error D: `Code doesn't match. Please check or request a new code.`
Resend, cooling: `Send a new code in 0:{ss}` · Resend, ready: `Send a new code`
Resend prompt: `Didn't receive a code?` · Edit: `Edit phone number`

Error voice is informative, never cautionary — no "Wrong", "Failed", "Error", "Invalid". Product terms capitalise exactly: **Show Up**.

## Type — the italic emphasis in headlines

The italicised word in a headline does **not** inherit the headline's weight. It is a separate spec, and it is the same on every screen in the product:

| | Value |
|---|---|
| Family | `--liq-font-serif` (Lora), italic |
| Weight | **500** — deliberately lighter than the headline's 700 |
| Size / line-height / tracking | inherited from the headline, never overridden |
| Colour | inherited (`--liq-fg`), except where the whole headline is coloured |
| Wrapping | `white-space: nowrap` — an emphasis phrase never breaks across lines |

The wash underneath is a **radial glow, not an underline**: no border, no text-decoration, no solid highlighter bar.

| | Value |
|---|---|
| Shape | `radial-gradient(ellipse at 50% 100%, rgba(254,104,57,0.55) 0%, rgba(254,104,57,0) 70%)` |
| Height | `0.32em` (scales with the headline) |
| Position | `left: -2px · right: -2px · bottom: -0.08em` |
| Stacking | behind the glyphs — `z-index: -1` on the pseudo-element, `isolation: isolate` on the span |

**Do not re-derive these.** Implement the two rules once as `.su-underlined em` and reuse — they are in `design_handoff_showup/tokens/colors_and_type.css` (search `.su-underlined`), which is the authority. Markup is `<h1 class="su-underlined">Welcome to <em>Show Up</em></h1>`; one emphasis span per headline, never two.

## Acceptance criteria

- [ ] All four states render from **one** screen column each — A / B share a component, C / D share a component; the failure state is a state, not a second screen
- [ ] **The CTA does not move between the default and error state on either screen** (reserved helper regions: 20 on A / B, 62 on C / D — the reserve must match the *error* box's height, not the empty one's) — verify by toggling the error with the field in place
- [ ] B keeps the entered digits, shows the 1.5px `--liq-danger` border + 4px halo + inline "!" glyph, replaces the helper line with the example-number message, and disables the CTA until the value changes
- [ ] Validation runs on submit, not per keystroke
- [ ] Country pill defaults from device locale (DE / +49 is the mock only), shows the flag as drawn rects — **no emoji flags** — and opens a country list (list itself out of scope)
- [ ] C shows the number actually submitted on A, with the country code, in Manrope 700
- [ ] Slots: 49 × 62, radius 14, Lora 700 30, tabular-nums; empty / filled / active borders and the violet caret + halo per the sheet; row has `aria-label="Enter your 6-digit verification code"`
- [ ] `Verify code` is disabled until all six digits are present
- [ ] OS SMS autofill and pasting a 6-digit code both fill the row in one action
- [ ] D **preserves the digits**, applies the danger border + 4% wash + `#7A1F26` digits, shows the inline error box glued to the slots (not a toast), keeps the CTA enabled, and **releases the resend cooldown to 0**
- [ ] D shakes the slot row **once** (480ms) and then holds still — no looping animation
- [ ] The verify screen's reserved region is at least as tall as the error box on the narrowest supported width — if the message wraps to a third line at 375, raise the reserve rather than letting the CTA shift
- [ ] Both helper regions are `aria-live="polite"`; the error is announced and focus stays in the input
- [ ] Resend renders as text while cooling (tabular-nums, no width jitter) and as a live button at 0:00; no silent resend; server-side rate limit surfaces in the same region
- [ ] `Edit phone number` and back on C both return to A with the number kept
- [ ] The platform numeric keypad is used — the mock `NumericKeypad` is not shipped
- [ ] No absolute Y positioning anywhere in the layout
- [ ] Copy matches the strings above exactly
- [ ] **Evidence of done:** attach screenshots at **375 × 667, 390 × 844 and 430 × 932**, for **all four states** (12 images), with the keyboard open. Each must show all content visible, nothing clipped or truncated, no scroll under the keyboard, and the CTA plus the secondary actions fully visible. On 375 × 667, state the slot size and gap used, and confirm the CTA sits at the same Y in the default and error state.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json`. Use them exactly — do not invent names, and do not restate a payload here that the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview, number entry | `screen_viewed` | `screen_id: "signup_phone_number"`, `screen_name: "Signup - Phonenumber"` |
| Screenview, code entry | `screen_viewed` | `screen_id: "signup_code_entry"`, `screen_name: "Signup - Codeentry"` — a separate screenview, same event |
| Send me the code | `signup_phone_submitted` | `country_code` — **missing from the current build**, so submitted-by-country is unavailable |
| Number rejected | `signup_phone_validation_failed` | `reason` (7 values, `enums.json` §12), `country` |
| Verify code | `signup_code_submitted` | — |
| Wrong code | `signup_code_verify_failed` | `attempt` — the attempt distribution is what tells us whether the mismatch UX works |
| Resend | `signup_resend_requested` | `seconds_waited`, `after_mismatch` |
| Edit phone number | `signup_edit_phone_tapped` | — |
| SMS dispatched (server) | `sms_code_sent` | `provider`, `latency_ms` — **not built**; delivery latency is unmeasured |

**The three validation reasons this ticket used to name are replaced by the validator’s seven** (`empty`, `notANumber`, `tooShort`, `tooLong`, `invalidLength`, `unrecognised`, `notMobile`). The code asks libphonenumber rather than measuring length, and only `tooShort` and `notMobile` overlapped with the old three. "Unsupported country" can never fire — there is no supported-country list and libphonenumber accepts every region. Registered in `enums.json` §12.

**Still undefined, not implemented:** country-pill opened, and autofill vs manual entry. No event exists for either; if they are wanted, they need defining in `events.json` first.

## Out of scope

The country list screen, SMS delivery and provider integration, the code's server-side validation and rate limits, account creation after a successful verify, the Startup and Welcome back screens, WhatsApp / email as alternative delivery channels, and lockout after N failed attempts (needs its own ticket — see Open).

## Dependencies

- Welcome back (welcome 02) — entry point for the phone method
- SMS provider and the 6-digit code service, including the resend rate limit
- Device locale → default country code mapping, and the supported-country list
- Platform SMS autofill entitlements (iOS: one-time-code AutoFill; Android: SMS Retriever)
- Lora + Manrope in the app font set

## Open (not blocking)

- **No lockout state is designed.** After N failed codes something has to happen — a cooldown, a lockout, a support path. Decide the rule, then this flow needs a fifth state.
- **A disabled CTA cannot explain itself.** On B the button greys out while the error line does the explaining; if the user reads bottom-up they see a dead button first. Worth testing "keep it enabled and re-validate on tap" instead.
- **No delivery-channel fallback.** If the SMS never arrives, the only exits are resend and edit. WhatsApp or email as a second channel would cut the drop-off here — separate ticket, but decide before launch.
- **Country list is unspecified** — search, favourites, ordering, and which countries we actually support.
- **The mismatch error does not say how many attempts remain.** Once the lockout rule exists, decide whether to expose the count.
- **`e.g. 176 123 45 678` is a German example** hard-coded into the error string. It must follow the selected country, or the message will be wrong for every other locale.
