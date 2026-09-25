# [Profile 02] Email — address capture + marketing consent

**Parent:** Profile creation flow (epic) · **Type:** Story · **Priority:** High
**Attachments:** `profile-02-email-spec-sheet.png`

## Description

Step 2 of 3 of "The basics". One field, the same shape as step 1, plus two things step 1 does not have: a **marketing consent row** and a **helper line that pre-announces the next screen**.

Entered from step 1 (name). Two exits:

- **CTA "Continue"** and the keyboard's **Go** key → **Verify email**, which sends the code — *not* straight to step 3
- **Back** → step 1, with the entered name restored

**Three states, one component, one ticket.** A is a format-valid address with the success tick; B is a malformed one submitted; C is what an empty submit produces. A and B seed the *same* address, one of them mistyped, on purpose — the states should read as one field failing, not two screens.

**The CTA is never disabled.** Pressing Continue — or the keyboard's Go — on an empty or malformed value surfaces the inline error and a one-shot shake, rather than doing nothing. Same rule as steps 1 and 4; a disabled button cannot explain itself, and it would mean the specific error strings are never seen for an empty field.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** Status bar (54, fixed) → AppHeader (52, fixed) → content (`flex: 1`, padding `4 24 0`, overflow hidden) → keyboard (fixed) → home indicator (28, fixed). Content children are all `flex: none` and top-anchored, with **one `flex: 1` spacer** (`min-height: 8`) above the CTA row.

⚠ **Every gap on this screen is tighter than step 1's** — progress margin-bottom 18 not 28, field→helper 8 not 10, CTA margin-bottom 10 not 18. That is not drift. The field, helper, consent row and CTA all have to clear a 286px keyboard, and the density is the price of the consent row. Do not "normalise" these to match step 1.

⚠ **This screen is the one place in the group that does not reserve the status region's height.** Steps 1 and 3 reserve it (`min-height` 44 / 30) so nothing moves between states. Here the screen is too dense to reserve the taller height in the calm state without pushing the CTA into the keyboard, so the consent row and CTA sit **~18px lower** in the error state. That is accepted. **What must hold instead: the CTA stays fully visible above the keyboard in both states, at every size in the matrix.** If that fails at 375 × 667, reserve the height and shorten the consent copy — never let the CTA slide under the keys.

📎 All specs are on the attached **profile-02-email-spec-sheet.png**, keyed ①–⑱. The design system has colour and type tokens only — there is no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/profile/screen-email-reference.jsx`. State props: **A** `<ScreenProfileEmail/>` · **B** `<ScreenProfileEmail error={true}/>` · **C** `<ScreenProfileEmail state="empty-submit"/>`.
> `FloatingField` and `QwertyKeyboard` are the **same components** as step 1's, not copies — see `screen-name-reference.jsx`.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

The keyboard, including its **email variant** — the `@` and `.` in row 4, and the suggestion strip. Request the email keyboard type and ship the platform keyboard; both the key row and the predictions are the OS's. `QwertyKeyboard` in the reference file is a mock, drawn only so the artboard shows the true content height above it (286 + 28 home indicator → 424 of content at 390 × 844). **Never spec a keyboard height.**

## Copy — final strings

Section title (AppHeader): `The basics`
Headline: `What's your email?` — "email" italic, with the orange underline wash
Sub copy: `Never shown on your profile. Used for password reset, receipts and support.`
Field label (floating): `Email`
Field placeholder: `you@example.com`
Helper line (valid state): `We'll send a 6-digit code to confirm it's really you.`
Consent row: `Receive curated tips, local event invites, and special offers. No spam, you can opt out whenever you like.`
CTA: `Continue`

**Error strings — chosen by what the user typed, not one generic message:**

| Input | Message |
| --- | --- |
| no `@` at all | `Add an @ — e.g. you@example.com` |
| `@` but no dot after it | `Looks like the domain is missing .com (or similar).` |
| empty | `Enter your email to continue.` — **no mono chip**, there is no missing character to name |
| anything else | `That email doesn't look quite right. Please check it.` |

The named fragment (`@`, `.com`) renders as a **mono chip** inside the message: ui-monospace 700 / 13, bg `rgba(251,50,59,0.10)`, pad `1px 5px`, radius 4.

**"Please enter a valid email" is banned.** It tells the user nothing they don't already know. Naming the missing character makes the fix one keystroke.

The empty-field string deliberately mirrors step 1's `Enter your name to continue.` and step 4's equivalent — **the same sentence shape across the group**, so the requirement reads identically wherever the user meets it.

Two notes on the copy that is there: the sub copy **leads with what the email is not** — email is the field users most expect to be spammed from, so the reassurance goes first and the three real uses second; it is also the answer to the consent row below. And the helper line is the only place in the flow we tell the user a code screen is coming.

## Behaviour

- Field is **focused on arrival** with the keyboard open; the floating label is lifted.
- `valid` is a format check (`something@something.something`). It drives the success tick in the field and the helper line. **It does not gate the CTA.**
- **Go on the keyboard does the same thing as Continue.**
- **The value is preserved on failure.** Never wipe what the user typed — the fix is usually one character, and re-typing an address is the fastest way to lose someone.
- On failure the field wrapper runs a one-shot `su-shake` 480ms, re-keyed per failure so a second bad submit shakes again. **Focus stays in the field.**
- **The CTA is never disabled**, in any state. Pressing it while empty or malformed sets an `attempted` flag, which is what surfaces the error.
- **The error clears as soon as the value becomes format-valid** — not on blur, not on re-submit. The tick and the calm helper line return in the same beat. Editing a valid value back to invalid does **not** re-fire the error until Continue is pressed again.
- A value of only whitespace counts as empty and gets the empty-field string.
- The address is trimmed of whitespace and lower-cased on submit.
- **Marketing consent is opt-IN:** unchecked by default, never pre-ticked, never gates Continue, never styled as an error. The whole row is the hit area. Transactional email (the verification code, password reset, receipts) is unaffected and needs no consent.
- Press feedback only, no hover: scale to 0.98 on press-down.

## Persistence

The address and the consent flag are written **on a successful Continue**, before navigating — not per keystroke. The flow position advances to **Verify email**, so a user who quits here relaunches onto the code screen, not back onto this one. Resuming is silent (flow README rule 4a).

## Acceptance criteria

- [ ] AppHeader title is `The basics`, Manrope 600 / 16, centred, **with a back chevron** in a 36 × 36 target (step 1 has none; this screen does) and an empty trailing slot
- [ ] StepProgress is `steps={3} current={2}` with `margin-bottom: 18`
- [ ] Headline is Lora 700 / **32** / 1.1 / −0.015em with "email" as the single italic em carrying the orange wash via the shared `.su-underlined em` rule
- [ ] Sub copy is Manrope 500 / **14.5** / 1.4 in `--liq-neutral-200`, max-width 320
- [ ] Field is h64 / radius 14, `type="email"`, focused on arrival, with the floating label notching the border
- [ ] **Valid state:** success border + inline tick glyph in the field, and the helper line below reads exactly `We'll send a 6-digit code to confirm it's really you.` — Manrope 500 / 13 in `--liq-fg-muted` with a 16px `--liq-success-fg` check icon
- [ ] **Error state:** danger border 1.5px, `0 0 0 4px rgba(251,50,59,0.10)` halo, `rgba(251,50,59,0.04)` wash — never a solid red fill — the inline "!" glyph **replacing** the tick, and the label lifted in `--liq-danger-fg`
- [ ] The error card is the **same component** the other screens in this group use: pad 10 14, radius 12, bg `rgba(251,50,59,0.07)`, 1px `rgba(251,50,59,0.18)`, 18px round danger glyph, message Manrope 500 / 13.5 in `--liq-danger-fg`
- [ ] **The error message is chosen from the table above based on the input**, and the named fragment renders as the mono chip
- [ ] The typed value survives a failed submit
- [ ] `su-shake` 480ms fires once per failure and focus stays in the field
- [ ] The error is announced via `aria-live="polite"`
- [ ] Consent row: pad 10 12, radius 12, bg `--liq-bg-raised`, 1px `--liq-border-soft`, copy Manrope 500 / 13, 22px box with radius 6 — **unchecked by default**, whole row tappable, and it never blocks Continue
- [ ] CTA is the orange NextButton row at `margin-bottom: 10` — **orange, not sunset**
- [ ] **The CTA is never disabled** — no opacity, no colour change, no shift, in any of the three states
- [ ] Pressing Continue or Go on an **empty** field shows the error card reading exactly `Enter your email to continue.`, with no mono chip
- [ ] Pressing Continue or Go on a **malformed** value shows the matching string from the table, with the named fragment as the mono chip
- [ ] The error clears when the value becomes format-valid, and does not re-fire until Continue is pressed again
- [ ] **The CTA is fully visible above the keyboard in ALL THREE states at all three sizes** — this is the screen's real acceptance test
- [ ] Continue and Go both navigate to **Verify email**, not to step 3
- [ ] Back returns to step 1 with the name restored; the consent state survives both directions
- [ ] The address and consent flag persist on a successful Continue, and the flow position advances to Verify email
- [ ] The platform email keyboard is used — `QwertyKeyboard` is not implemented
- [ ] No absolute Y positioning anywhere
- [ ] Copy matches the strings above exactly
- [ ] **Evidence of done:** screenshots of **all three states** at **375 × 667, 390 × 844 and 430 × 932** — nine images. Each must show the CTA fully clear of the keyboard, nothing clipped, no scroll. On 375 × 667 state what the spacer collapsed to in each state.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **D — Profile creation**). Use them exactly — do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_email"`, `screen_name: "ProfileEmail"`, `referrer_screen_id` |
| Step entered | `profile_step_viewed` | `step_id: "email"`, `step_index: 2` |
| Continue with a valid address | `email_submitted` | `domain` — **the domain only, never the address** |
| Malformed address submitted | `email_validation_failed` | `rule: "format"` — **`format` only.** `disposable` is reserved in the enum and deliberately not implemented; see below |
| Continue pressed while empty | `form_validation_failed` | `field_id: "email"`, `rule: "required_missing"`, `screen_id`, `step_id` |
| Marketing consent row toggled | `consent_changed` | `channel: "marketing_email"`, `on: bool`, `surface: "profile_creation"` — fires in **both** directions, never opt-out only |
| Step completed | `profile_step_completed` | `step_id: "email"`, `time_on_step_s` |

**Two events fire on entry, and they are not duplicates.** `screen_viewed` answers *which screen*, for navigation and for searching the analytics tool. `profile_step_viewed` answers *where in the flow*, for the completion funnel. `email_verify` is the case that proves they differ: one screen, and a step that holds at 2 of 3.

**`screen_name` / `screen_id` / `step_id` are three separate vocabularies.** `screen_name` is the readable label you search for; `screen_id` is the stable key a saved funnel binds to; `step_id` is the flow position. All three come from `tracking/enums.json` (§11 and §2) — never from a literal at the call site.

**`screen_viewed` sends three properties here, not five.** `screen_id`, `screen_name`, `referrer_screen_id`. The registry also defines `last_used` and `state` on this event and **both are omitted on every profile screen** — `last_used` means something only on a screen that offers a sign-in method list, and `state` belongs to screens whose one registry row covers many states. Since `screen_viewed` fires once on mount, `state` could only ever report the initial one. Send neither; an empty string is worse than an absent property (decision 29).

**The address is never sent, only its domain.** A disposable-domain rate is answerable; a list of addresses is not something we collect.

**`disposable` is not implemented, and no detection is being built.** The user may enter any address they can receive mail at; if they later lose access to it, that is an app-logic problem to solve then, not a signup-time block. The rate is read from `email_submitted{domain}` in the warehouse, which needs no client work. The value stays in the `rule` enum so it need not be re-added — emitting it today is a bug.

**The marketing checkbox is the only consent on this screen, and it governs marketing mail alone.** The address is used for password resets and for support replying about a date whether or not the box is ticked — that mail is transactional, has no consent to withdraw, and is never tracked as one. An unticked box means *no marketing mail*, not *no mail*. `channel: "marketing_email"` is a distinct value from the `email` toggle on the later *Stay reachable* screen, which is a preference about being contacted regarding a match: three different uses of one address, told apart by the vocabulary (`enums.json` §8).

**The step is prefilled, never skipped.** When Apple or Google supplies an address, it is filled in and the screen is still shown, so the user can keep it or type another — so **`profile_step_skipped` never fires for `step_id: "email"`** and has been removed from the table. Whether they keep the provider's address is not tracked: the requirement is that we hold a working address, not whose it is.

**Verification always runs, whatever the origin.** A Google address is already verified by Google and an Apple relay address is deliverable but is not the user's own, so neither is trusted here — every address goes through the code on profile/03. There is no skip path to build.

## Out of scope

The Verify email screen (story 03), step 3, the transactional email templates, the marketing-email system behind the consent flag, and any email deliverability or bounce handling.

## Dependencies

- **Somewhere to store the address and the consent flag** — the consent flag needs a timestamp and a source, not just a boolean, if it is to be a usable legal record
- **Story 03 (Verify email)** — the destination of Continue, and the thing the helper line promises
- **Email code sending** (handoff README build item 08) — Continue triggers it
- `FloatingField`, `QwertyKeyboard`, the error card and the header shell from story 01
- Lora + Manrope, and `.su-underlined em` as a shared rule

## Open (not blocking)

- **Users who arrived via Apple / Google already have an email.** The kit's earlier design had a "Skip for now" footer for exactly them; this design has no skip. Whether that path pre-fills the field, skips the step entirely, or still asks is a flow question worth settling with the epic's mandatory-steps decision.
- **Apple's Hide My Email** produces a working but opaque relay address. Confirm it validates and that the copy's "password reset, receipts and support" promise still holds through a relay.
- **The 64-character cap is the mock's.** Real addresses can exceed it; the spec should carry a real max (254 is the RFC ceiling).
- **Where the consent record lives** — a boolean on the profile is not enough for a consent audit. Needs a timestamp, the copy version shown, and the source screen.
