# [Profile 04] Date of birth — inline age confirmation

**Parent:** Profile creation flow (epic) · **Type:** Story · **Priority:** High
**Attachments:** `profile-04-date-of-birth-spec-sheet.png`

## Decided before build

**The visibility band stays on this screen, with the copy exactly as drawn — decided 7 Sep 2026.** `Don't display on my profile`, unchecked by default. **Checked means the age is not shown on the profile; it is still used for the matching algorithm.** The choice hides a value, it never excludes the user from matching.

This is a **named exception to flow README rule 8** (`ProfileVisibility` otherwise belongs to the nine detail steps), and it supersedes the caveat on callout ⑪ of the sheet. The reason it earns the exception: this is the only step whose value is *locked*, so the one moment the user can decide what happens to it is the moment they hand it over.

## Description

The last screen of "The basics", and the only one that writes a value the user cannot change afterwards. One field, a numeric keyboard, and — instead of a separate confirm screen — an **inline age card** that shows the computed age and says it is about to be locked.

**Step 3 of 3.** `StepProgress` is `steps={3} current={3}` — the only screen in the group where all three segments are filled. Do not animate the fill on arrival.

Exits:

- **Continue** with a complete, real, 18+ date → date and age persisted, step complete → the **embrace bridge** screen
- **back** (header) → Verify email, already verified — it must not re-send or re-verify anything
- **Edit** (inside the age card) → stays here, field cleared, back to state A

**Four states, one component, one ticket.** A empty · B inline age confirm · C invalid date · D under 18. They share one layout column; a fifth body — the incomplete error — is state A after Continue is pressed. Splitting any of them into its own screen is how the CTA ends up at a different Y per state.

## The one thing that matters most

**The age card is the confirmation, and it is inline.** Age is locked after this step, so the value, the age it produces, and the warning that it is permanent are one glance apart — no confirm screen, no modal, no toast. And the card leads with the **computed age**, not the date the user typed: a user re-reading "03/22/1998" checks their own input, a user reading "28" checks the thing the app will actually use. Off-by-one-year typos are caught here or never.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** Status bar (54, fixed) → AppHeader (52, fixed) → content (`flex: 1`, padding `4 24 0`, overflow hidden) → numeric keypad (fixed) → home indicator (28, fixed).

⚠ **Bottom-anchored, one spacer.** Content column: StepProgress → headline → sub → field → status region → **a single `flex: 1` spacer** → visibility band → CTA row. (Screen 03 is the group's bottom-slack exception; this screen is not it.)

⚠ **The status region under the field is reserved at `min-height: 84`** — `margin-top: 10`, `aria-live="polite"`. 84 is the height of the **age card**, the tallest of the four bodies, so the band, the CTA and the keyboard sit at the identical Y in all four states. Never drop it to fit a failure state.

⚠ **This is the tightest screen in the group.** At 390 × 844 the spacer holds ~25px of slack: two-line headline, two-line sub, 64 field, 84 region, 56 band, 56 CTA, over a keyboard. At 375 × 667 collapse in this order — 1. the sub copy's line-height, 2. the CTA row's `margin-top` 26 → 14, 3. the headline 32 → 30. Never shrink the field or the CTA below 56, never scroll content under the keyboard, never drop the reserved 84.

📎 All specs are on the attached **profile-04-date-of-birth-spec-sheet.png**, keyed ①–㉗.

> **Reference implementation:** `design_handoff_showup/profile/screen-dob-reference.jsx`. State props: **A** `<ScreenProfileDoB/>` · **B** `<ScreenProfileDoB state="confirm"/>` · **C** `<ScreenProfileDoB state="invalid"/>` · **D** `<ScreenProfileDoB state="underage"/>`. The `state` prop only seeds a value — the screen derives its real state from what is typed.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

The keyboard. `NumericKeypad` in the reference file is a mock of the iOS dialer. **Request the numeric keyboard and ship the platform's** — never spec its height, never build the mock. It is drawn only so the artboard shows the true content height above it (214 + 28 home indicator → 496 of content at 390 × 844).

The field is a **text input with a numeric keyboard** — not three pickers, not a date wheel, not the OS date picker. Typing eight digits beats spinning three wheels back to 1998, and it is the same 64-high field the name and email steps use.

## Copy — final strings

Section title (AppHeader): `The basics`
Headline: `What's your date of birth?` — "date of birth" italic with the orange wash
Sub copy: `Be honest — it helps us find the right matches. You must be 18 or older.`
Field label: `Date of birth` · placeholder: `mm/dd/yyyy`
Helper (A): `Your age appears on your profile — not your date of birth.` — "age" inline at 700
Age card (B): `You're {age}. Look right?` + `Locked after this step.` + link `Edit`
Incomplete (A + Continue): `Enter your full date of birth to continue — mm/dd/yyyy.`
Invalid (C): `That date doesn't exist. Please check the format — mm/dd/yyyy.`
Under 18 (D): `You must be at least 18 to use Show Up. Please check the date you entered.`
CTA: `Continue`

`mm/dd/yyyy` in the two error strings renders as the **mono chip** step 2 uses for a missing email fragment: ui-monospace 700 / 13, `rgba(251,50,59,0.10)`, pad `1 5`, radius 4.

**The 18+ rule is stated before the field**, in the sub copy — so state D is a reminder rather than a surprise, and D's copy can stay short.

**The helper line is why the field is not scary.** Asking for a full date of birth reads as an ID check; saying what is actually published — the age — and what is not — the date — is what earns the honest answer the sub copy asks for.

**The failure copy names the date, never the user.** "That date doesn't exist" is a fact about 30 February; "invalid date" is a verdict about the person typing. Under 18 states the rule and then points at the input, because the overwhelmingly common cause is a mistyped year, not a minor signing up. No "sorry", no "you are not eligible", no "too young".

**No age numeral appears in state D.** Printing "You're 15" back at a 15-year-old adds nothing they don't know and hands a rejection a number.

## Behaviour

- Digits auto-format to `mm/dd/yyyy` as they are typed; slashes are inserted, never typed. Backspace deletes a **digit** and re-formats, so the slashes never have to be deleted.
- **Validity is a round-trip check, not a regex:** build the date, then confirm it reports back the same month, day and year. `02/30/1990` otherwise silently becomes 2 March. Range guard: month 1–12, day 1–31, year 1900 → this year.
- **Age = whole years since the date, decremented if this year's birthday has not passed.** Computed from a single "today" passed into the screen, in **local time, not UTC** — and **re-derived server-side on submit**; the client's clock is not evidence.
- State derivation: 8 digits + parses + ≥ 18 → **B** · 8 digits + impossible date → **C** · 8 digits + parses + < 18 → **D** · fewer than 8 digits → **A**, or the incomplete error once Continue has been pressed.
- **The CTA is never disabled** (group rule 2c). Pressing it on an empty or impossible date produces the inline error plus a one-shot `su-shake` 480ms; only state B advances.
- The incomplete error appears **only after a press** — never mid-typing. It clears the moment the eighth digit lands and does not re-fire until Continue is pressed again.
- Errors clear **when the value becomes valid** — not on blur, not on re-submit. The shake re-keys per transition *into* an error, so a second failure shakes again but typing inside an error state does not.
- **Never clear the field on a failure.** The fix is usually one digit.
- The age card enters with `su-confirm-in` 240ms (opacity 0→1, translateY 6→0), once, on entering the state. It is **lavender, not green** — a question to answer, not a success message.
- **Edit clears the field** and returns to state A. It is a clear, not a cursor placement: a wrong date is nearly always wrong in the year, and re-typing eight digits beats hunting a caret.
- **Nothing about the failure states is submitted** — a rejected date never becomes a stored one.
- The visibility band is **unchecked by default** (age shown). Checking it sets *display*, not eligibility: the age is still passed to matching. It is persisted with the step, and — unlike the age — remains changeable afterwards.
- Errors are inline, never a modal. Press feedback only, no hover.

## Persistence

Persist on a **successful** Continue, before navigating — not per keystroke. A user who quits here relaunches **here**, with an **empty field** and **no error on arrival**: an unconfirmed date is not a value, and a resumed step never opens in a failure state (flow README rule 4a — resuming is silent).

**Once written, age is not editable in-app** — that is the point of an 18+ rule. A correction after this step is a support path, not a settings row. The screen says so before the fact, at ⑱.

## Acceptance criteria

- [ ] `StepProgress` is `steps={3} current={3}` — all three segments filled, no arrival animation
- [ ] Headline is Lora 700 / **32** / 1.1 with "date of birth" as the single italic em carrying the orange wash, allowed to wrap to two lines at 390
- [ ] Field is the group's 64-high box, radius 14, border 1.5px, with the floating `Date of birth` label notching over the border (top −8, left 14, pad `0 6`, Manrope 600 / 12) and the `mm/dd/yyyy` placeholder
- [ ] Value is Manrope 500 / 17 with **`tabular-nums`** and 0.4 tracking; digits auto-format to `mm/dd/yyyy` and backspace deletes a digit, not a slash
- [ ] Four field states render per the sheet — idle / focus / valid (green border + 22px tick) / error (danger border, 4% wash, `#7A1F26` value, 22px "!" glyph) — each transitioning at 180ms, with **no change in height or Y**
- [ ] The caret (1.5 × 20, `--liq-primary-500`, `su-caret-blink`) renders only while focused **and** empty, never in an error state
- [ ] The status region is reserved at **`min-height: 84`** with `aria-live="polite"`, and the visibility band, CTA and keyboard sit at the **identical Y in all four states** (verify by overlaying the four screenshots)
- [ ] `02/30/1990` produces state C — validated by a **round-trip date check**, not a regex
- [ ] Age is computed as whole years with the birthday-not-yet-passed decrement, in local time, and is **re-derived server-side on submit**
- [ ] Age card: radius 16, `--su-grad-lilac`, 1px `rgba(129,42,236,0.22)`, age numeral **Lora 700 / 44** in `--liq-primary-500` with `tabular-nums`, entering once with `su-confirm-in` 240ms
- [ ] Age card carries the lock line and the `Edit` link; **Edit clears the field** and returns to state A
- [ ] The CTA is **live in all four states**; pressing it in A / C / D produces the inline error and a one-shot `su-shake` 480ms and submits nothing
- [ ] The incomplete error appears only after a Continue press, clears on the eighth digit, and does not re-fire until Continue is pressed again
- [ ] A failed attempt **preserves the entered digits**
- [ ] The error card is the **same component** steps 1–3 use, unchanged in geometry between C and D, announced via `aria-live` and rendered **inline, never a modal**
- [ ] State D shows **no age numeral** anywhere
- [ ] Copy matches the strings above exactly, including the mono chip on both format errors
- [ ] Only state B advances; it persists date + age and routes to the embrace bridge
- [ ] Header back returns to Verify email **without** re-sending or re-verifying a code
- [ ] Quitting here and relaunching returns to **this** screen with an empty field and no error state
- [ ] The platform numeric keyboard is used — `NumericKeypad` is not implemented, no date picker or wheel is substituted
- [ ] Visibility band renders per ⑪ — `ProfileVisibility`, checkbox on the **left**, label `Don't display on my profile` verbatim, **unchecked by default**, 56 row, hit area capped to switch + label and never overlapping the CTA's x-range
- [ ] Checking the band hides the age **from the profile only** — the age is still sent to the matching algorithm, and the value is persisted with the step
- [ ] No absolute Y positioning; exactly one `flex: 1` spacer, above the visibility band
- [ ] All seven events in the Tracking table fire with the payloads named there, `step_id: "dob"` / `field_id: "age"`, and **no invented event names**; `form_validation_failed` fires on the refused Continue, not on render; `field_display_opted_out` fires on tick only; no payload carries a raw date of birth
- [ ] **Evidence of done:** screenshots of **all four states** at **375 × 667, 390 × 844 and 430 × 932** — twelve images. Nothing clipped, no scroll, CTA fully visible above the keyboard in every one. On 375 × 667, state what collapsed to make room.

## Tracking — named events, from the taxonomy

Emit the events already specified in `design_handoff_showup/tracking/events.json`. **Do not invent event names for this screen** — the vocabulary is generated from `tracking/enums.json`, and the age-confirmation events that *used* to exist here (`age_confirm_shown` / `age_confirm_accepted`) were **deleted** in taxonomy decision 15 because they described a popup this screen does not have. The inline card is not tracked as a card; `dob_submitted` is its conversion signal.

`step_id` for this screen is **`dob`** · `field_id` for the value and its visibility control is **`age`** (both from `enums.json` §1 / §2).

**Screen name: `ProfileDoB`** · **`screen_id`: `profile_dob`** (`enums.json` §11). `screen_name` is the readable label — what you search for in the analytics tool; `screen_id` is the stable key a saved funnel binds to. **`step_id` stays `dob`:** it is the flow-position vocabulary, not a screen name. Putting `ProfileDoB` in `step_id` would collapse two vocabularies into one and break the step sequence, where `email_verify` deliberately shares step 2 with `email`.

**`screen_viewed` sends three properties here, not five:** `screen_id`, `screen_name`, `referrer_screen_id`. The registry also defines `last_used` and `state`, and **both are omitted on this screen.** `last_used` means something only on a screen offering a sign-in method list. `state` is for screens whose one registry row covers many states — and it could not carry this screen's four states in any case, because `screen_viewed` fires once on mount: the four are carried by `dob_validation_failed`, `age_gate_failed` and `form_validation_failed` (decision 29).

**`step_index` is 3 on this screen**, and the sequence is `name`=1, `email`=2, `email_verify`=2, `dob`=3. `email_verify` shares 2 deliberately — the progress bar does not advance for it. Key funnels on `step_id`, never on `step_index`.

| Event | Fires when | Payload |
| --- | --- | --- |
| `screen_viewed` | screen mounts | `screen_id: "profile_dob"`, `screen_name: "ProfileDoB"`, `referrer_screen_id` |
| `profile_step_viewed` | screen mounts (including a silent resume) | `step_id: "dob"`, `step_index` |
| `dob_submitted` | Continue accepted — state B only | `age: int` |
| `profile_step_completed` | same submit, after validation | `step_id: "dob"`, `time_on_step_s` |
| `dob_validation_failed` | 8 digits that are not a real date (state C) | `rule: "impossible"` (`"format"` if the parse fails outright) |
| `age_gate_failed` | a real date under 18 (state D) | `age: int` |
| `form_validation_failed` | Continue pressed with an incomplete date | `field_id: "age"`, `rule: "required_missing"`, `screen_id`, `step_id: "dob"` |
| `field_display_opted_out` | the visibility box is **ticked** | `field_id: "age"` |

Rules that are easy to get wrong here:

- **`form_validation_failed` fires on the refused action, not on render** — the Continue press, not the appearance of the error card. Typing three digits emits nothing.
- **`dob_validation_failed` and `age_gate_failed` are the two failure branches, and they are not interchangeable.** An impossible date is a format problem; an under-18 date is a policy outcome. Both fire on the transition into the state, once per transition — not per keystroke while the state holds.
- **`field_display_opted_out` is opt-out only** — no event on unticking. Absence of the event for `field_id: "age"` means the age is displayed. Current-state reporting comes off the `hidden_fields` user property (Properties §P3), which is written in **both** directions.
- **The age travels as `age` on `dob_submitted`; the stored user property is `age_band`** (`18-24 | 25-34 | 35-44 | 45-54 | 55+`). Do not add a raw date of birth to any payload.
- Every attribute event carries `sensitivity_class` (0 for all of the above) **and** `field_registry_version`, stamped at emit time — see the emitter rules in `design_handoff_showup/CLAUDE.md`.

What these answer, and why each one is here: whether the step converts (`dob_submitted` ÷ `profile_step_viewed`), whether `mm/dd/yyyy` is the wrong format for a locale (`dob_validation_failed` rate), whether state D catches typos or minors (`age_gate_failed` followed by a `dob_submitted` in the same session), how long the slowest field in the flow takes (`time_on_step_s`), and how many users hide their age at the moment they give it (`field_display_opted_out`).

**Not tracked, deliberately:** the `Edit` tap in the age card, and the shake. Edit is a correction inside an unsubmitted step — the signal that matters is whether the eventual `dob_submitted` differs from what was first typed, and that is not worth a T1 event.

## Out of scope

Any age verification beyond self-declaration, the embrace bridge screen, the nine detail steps, the profile settings row that changes the hidden-age choice later, how a hidden age renders on the profile and in match surfaces, and any in-app path to change a locked age.

## Open (not blocking, but settle before build)

- **Where the hidden-age choice is changed later.** The age is locked; the *visibility* of it is not, so it needs a row in profile settings — which screen it lives on is not yet designed.
- **What a hidden age looks like to other users.** Whether the profile simply omits the age line, or shows something in its place, is not specified — and the same question applies to the match alert, the date card and Instant Mode surfaces.
- **`mm/dd/yyyy` is US-ordered.** Every string, the placeholder and the auto-formatter assume it. A locale that reads `dd/mm/yyyy` will mistype dates silently — 03/22 is impossible, but 03/04 is not, and that one is unrecoverable because age is locked. Decide whether the order follows device locale (and if so, whether the mono chip is generated from it).
- **Repeated under-18 attempts.** Nothing is blocked, logged or rate-limited by design. Whether a second or third under-18 date should be recorded — or the device held for a period — is a policy question, not a design one.
- **A locked age with no in-app correction needs a named support path.** The screen promises the lock; someone has to be able to fix a genuine mistake.
- **Field-level date entry vs. the platform date picker** was decided in favour of typing (eight digits beats three wheels, and the field matches its siblings). Recorded so it is not "fixed" during build.

## Dependencies

- **Story 03 (Verify email)** — the origin, and the destination of back
- The **embrace bridge** screen — the destination of a successful Continue
- The error card, the header shell, the field primitive and the flow state machine from story 01
- Profile storage for the date and derived age, with server-side age re-derivation
