# [Profile 01] Name — first-name capture

**Parent:** Profile creation flow (SHOWUP-TBD — epic not yet created) · **Type:** Story · **Priority:** High
**Attachments:** `profile-01-name-spec-sheet.png`

## Description

The first screen of profile creation, and the first thing a verified account is asked for. One question, one field, keyboard open: the user types a first name and continues. It is **step 1 of 3 of "The basics"** — name → email → date of birth — and the section title, progress bar and chrome are shared with the other two.

Entered from the app tutorial, after a completed identity verification (phone code, or a connected Apple / Google / Facebook account). **One exit only:**

- **CTA "Continue"** and the keyboard's **Go** key → step 2, email

**There is no back button on this screen.** Once the user enters profile creation they have to go through it; there is nothing behind step 1 to return to. The header's leading slot renders empty, and the platform back gesture is suppressed. Steps 2 and 3 keep their back button — this is step 1 only.

**This screen is the flow's entry point only when no saved progress exists.** The flow resumes where the user left off: a user who quits mid-flow — backgrounds the app, kills it, loses the device — relaunches onto their **last incomplete step**, with everything they already entered still there. See *Resuming the flow* below.

**Three states, one component, one ticket.** A is the empty field on arrival; B is the same screen with a value in it; C is what an empty submit produces. They share one layout column and nothing moves between them.

**The CTA is never disabled.** Pressing Continue — or the keyboard's Go — on an empty field surfaces an inline error and a one-shot shake, rather than doing nothing. A disabled button cannot explain itself, and this is the pattern already settled on the date-of-birth screen: **the error card is the same card**, so build it once for name, email and DoB.

## Layout — read this before building

⚠ **The header's leading slot is empty, not removed.** It still occupies its 36 so the centred title does not shift. Build the group's header shell with the leading slot as a **variant**, not a constant — three copies of the header is the same bug as three copies of the progress bar.

⚠ **Layout is flex, not absolute.** Status bar (54, fixed) → AppHeader (52, fixed) → content (`flex: 1`, padding `4 24 0`, overflow hidden) → keyboard (fixed) → home indicator (28, fixed).

**One `flex: 1` spacer, not two.** Inside the content column everything is top-anchored — progress, headline, sub, field, status region — then a single spacer, then the CTA row bottom-anchored at `margin-bottom: 18`. Nothing on this screen floats in the middle. At 390 × 844 the spacer resolves to ≈102.

⚠ **The status region under the field is reserved, not conditional** — `min-height: 44`, `margin-top: 10`, `padding-left: 2`, `aria-live="polite"`. It is empty in A and B and holds the error card in C. Rendering it conditionally is how the CTA and the keyboard end up at a different Y in the error state, and that is what makes three states stop reading as one screen.

**The spacer is the only flexible element.** The keyboard height is not ours to choose: it varies by OS, language and prediction settings. Let the spacer absorb every difference. Never shrink the 64px field, never shrink the CTA circle below 56, never scroll content under the keyboard, never move the CTA onto the keyboard.

**No ambient backdrop on this screen.** Flat `--liq-bg` — unlike the welcome flow's orbs. That is intentional and it is the pattern for the whole profile flow.

📎 All specs are on the attached **profile-01-name-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–⑪ to the two states. Note the design system has colour and type tokens only — there is no spacing scale, so the raw px values are intentional, not an oversight.

> **Reference implementation:** `design_handoff_showup/profile/screen-name-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. State props: **A** `<ScreenProfileName/>` · **B** `<ScreenProfileName initialValue="Leo"/>` · **C** `<ScreenProfileName state="empty-submit"/>`.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

The keyboard. `QwertyKeyboard` in the reference file is a **stylized mock of the system keyboard**, drawn only so the artboard shows the true content height above it (286 + 28 home indicator → 424 of content at 390 × 844). Ship the platform keyboard. We do not control its height, its key geometry, its suggestion strip, its emoji/dictation chrome, or the label on its action key — we only request the `Go`/`Done` action and the `words` capitalisation hint. **Never spec a keyboard height.**

## Copy — final strings

Section title (AppHeader): `The basics`
Headline: `What's your name?` — "name" italic, with the orange underline wash
Sub copy: `Your name will be shown on your profile.`
Field label (floating): `Enter first name`
Field placeholder: `First name`
CTA: `Continue`
Empty-submit error: `Enter your name to continue.`

The sub copy is a consequence statement, not a hint — it says where the value lands. Do not soften it to "This helps us…". The suggestion strip's "Suggest / Suggest / Suggest" in the mock is placeholder text, not copy. Product terms capitalise exactly: **Show Up**.

## Resuming the flow — applies to the whole flow, implemented here

The flow is mandatory, so it must also be **forgiving of interruption**. A user who gets a phone call on step 2 and comes back an hour later has not lost their name.

- **Persist on Continue, per step.** The name is written when Continue succeeds, before navigation — not on every keystroke, and not only at the end of the flow.
- **Persist the position too.** Alongside the values, store which step the user is on. On launch, if profile creation is incomplete, route straight to the **last incomplete step** and skip the ones already done.
- **Survives app kill and relaunch**, not just backgrounding. Local persistence is enough for the position; the values should reach the server as they are entered so a lost device does not lose the profile.
- **Resuming is silent.** No "welcome back", no "resume where you left off?" prompt, no toast. The user lands on the step, keyboard open, with the progress bar showing where they are. Explaining it draws attention to the interruption.
- **The progress bar reflects the real position** on resume — a user resumed onto step 3 sees segment 3 active, not segment 1.
- **A resumed step behaves like a freshly-reached one.** No error state on arrival, even if the user left mid-error.
- **Back on a resumed step still works** where that step has one: resuming onto step 3 and pressing back goes to step 2, with the entered value present. It is only step 1 that has no back.
- **This is what makes the no-back rule acceptable.** Mandatory *and* unforgiving would be a bad flow; mandatory but resumable is the pattern users already expect from every app that does this.

## Behaviour

- Field is **focused on arrival** with the keyboard already open — the user can type without tapping. The floating label is therefore lifted in both states.
- The floating label is lifted whenever the field is focused **or** non-empty; it only returns to its resting position (top 22 / left 18 / 500 at 17) if the field is blurred while empty.
- **Go on the keyboard does the same thing as Continue** — in every state, including the empty submit. Neither inserts a newline.
- **Empty submit (state C):** the press sets an `attempted` flag; the field takes the danger border, halo and 4% wash with the inline "!" glyph, the floating label recolours to `--liq-danger-fg` and stays lifted, the error card appears in the reserved region, and the field wrapper runs a one-shot `su-shake` of 480ms. **Focus stays in the field** — do not blur the user out of the input, and do not move focus to the error.
- **The error clears on the first character typed** — not on blur, not on re-submit. After clearing a typed name the error does not re-fire until Continue is pressed again.
- A value of only whitespace counts as empty and produces the same error.
- The value is trimmed of leading and trailing whitespace on submit; the stored value is what appears on the profile.
- **Backwards navigation is blocked entirely** — see the header rules above. Forward navigation must preserve state: returning from step 2 restores the entered name, whether that return happens in the same session or after a relaunch.
- Nothing else on the screen reacts to the value — no tick, no character counter, no colour change, no CTA state change.
- Press feedback only, no hover: scale to 0.98 on press-down.

## Acceptance criteria

- [ ] AppHeader title is `The basics` at Manrope 600 / 16, centred, with **both** the leading and trailing 36 slots empty — no back chevron, no skip, no close — and the slots still occupying their width so the title stays centred
- [ ] **There is no way backwards out of this screen:** no chevron, no iOS swipe-back, no Android hardware or gesture back. Attempting the gesture leaves the user on the screen
- [ ] The header is implemented as the group's shared shell with the leading slot as a variant, so steps 2 and 3 can render their back button from the same component
- [ ] StepProgress is `steps={3} current={1}`: three flex bars, h5, radius 999, gap 6, first filled `--liq-primary-500`, rest `--liq-border`
- [ ] Headline is Lora 700 / **34** / 1.1 / −0.015em, one line at 390, with "name" as the single italic em carrying the orange radial wash via `.su-underlined em` — **not** re-derived per screen
- [ ] Sub copy is Manrope 500 / 15 / 1.45 in `--liq-neutral-200` `#4B3B5A`, max-width 320
- [ ] Field is h64 / radius 14 / bg `#FFFFFF` / border 1.5px `--liq-primary-500` with a `0 0 0 4px rgba(129,42,236,0.10)` halo while focused; value Manrope 500 / 17
- [ ] Floating label notches the border with a white patch at top −8 / left 14, pad 0 6, Manrope 600 / 12 in `--liq-primary-500` — the stroke itself is unbroken
- [ ] **No success affordance ever renders** — no tick, no character counter, no valid state, in any of the three states
- [ ] Pressing Continue or Go on an empty field shows the error card reading exactly `Enter your name to continue.` — Manrope 500 / 13.5 / 1.4 in `--liq-danger-fg`, in a card of pad 10 14 / radius 12 / bg `rgba(251,50,59,0.07)` / 1px `rgba(251,50,59,0.18)` with an 18px round danger glyph — **the same component the DoB screen uses**, not a re-style
- [ ] In the error state the field keeps its 64 height and its Y: danger border 1.5px, `0 0 0 4px rgba(251,50,59,0.10)` halo, `rgba(251,50,59,0.04)` wash — **never a solid red fill** — plus the inline 22px "!" glyph, and the label lifted in `--liq-danger-fg`
- [ ] The field wrapper runs `su-shake` 480ms once on entering the error, and focus stays in the field
- [ ] The error is announced via `aria-live="polite"`
- [ ] The error clears on the first character typed, and does not re-fire until Continue is pressed again
- [ ] **The CTA is never disabled** — no opacity change, no colour change, no shift, in any state
- [ ] The status region is reserved at `min-height: 44` so the CTA and the keyboard sit at the identical Y in all three states
- [ ] CTA is the NextButton row: label "Continue" Manrope 700 / 17 in `--liq-fg` + gap 14 + a 56 circle in `--liq-orange-500` with `--liq-shadow-cta` and `arrow-right` 22 stroke 2 white. **Orange, not sunset.** Label and circle are one hit area
- [ ] Field is focused and the keyboard is open on arrival, without a tap
- [ ] The keyboard's action key submits exactly as Continue does
- [ ] The platform keyboard is used — `QwertyKeyboard` from the reference file is **not** implemented
- [ ] Across states A, B and C, every element other than the field and the status region sits at the identical Y
- [ ] Returning to this screen from step 2 restores the entered name
- [ ] The name is persisted when Continue succeeds, before navigating away
- [ ] **Killing the app after Continue and relaunching lands the user on step 2, not step 1**, with the name already saved
- [ ] Killing the app on step 1 without submitting relaunches onto step 1 — the flow's entry point is only reached when no saved progress exists
- [ ] The progress bar shows the resumed step's real position, not step 1
- [ ] Resuming is silent — no prompt, no toast, no "welcome back" copy, and no error state on arrival
- [ ] No absolute Y positioning anywhere in the layout
- [ ] Copy matches the strings above exactly
- [ ] **Evidence of done:** attach screenshots of **all three states** at **375 × 667, 390 × 844 and 430 × 932** — nine images. Each must show all content visible, nothing clipped or truncated, no scroll, and the CTA fully visible above the keyboard. On 375 × 667, state what the spacer collapsed to, and show that the error state did not push the CTA under the keyboard.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **D — Profile creation**). Use them exactly — do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_name"`, `screen_name: "ProfileName"`, `referrer_screen_id` |
| Step entered | `profile_step_viewed` | `step_id: "name"`, `step_index: 1` |
| Flow entered (first step only) | `profile_build_started` | `entry_point` — once per profile build, on this screen |
| Continue with a valid name | `name_submitted` | `char_count` — **never the name itself** |
| Step completed | `profile_step_completed` | `step_id: "name"`, `time_on_step_s` |
| Continue pressed while empty | `form_validation_failed` | `field_id: "first_name"`, `rule: "required_missing"`, `screen_id`, `step_id` — fires on the **refused press**, not on render |

**Two events fire on entry, and they are not duplicates.** `screen_viewed` answers *which screen*, for navigation and for searching the analytics tool. `profile_step_viewed` answers *where in the flow*, for the completion funnel. `email_verify` is the case that proves they differ: one screen, and a step that holds at 2 of 3.

**`screen_name` / `screen_id` / `step_id` are three separate vocabularies.** `screen_name` is the readable label you search for; `screen_id` is the stable key a saved funnel binds to; `step_id` is the flow position. All three come from `tracking/enums.json` (§11 and §2) — never from a literal at the call site.

**`screen_viewed` sends three properties here, not five.** `screen_id`, `screen_name`, `referrer_screen_id`. The registry also defines `last_used` and `state` on this event and **both are omitted on every profile screen** — `last_used` means something only on a screen that offers a sign-in method list, and `state` belongs to screens whose one registry row covers many states. Since `screen_viewed` fires once on mount, `state` could only ever report the initial one. Send neither; an empty string is worse than an absent property (decision 29).

**The name is free text and is never sent** — `char_count` only, no exceptions (`requirements.json` → free-text rule).

## Open (added by the mandatory-flow decision)

- **Where the saved position lives.** Local-only is enough to resume, but a user who reinstalls or switches device then restarts the flow with a half-populated profile. Storing the position server-side alongside the values avoids that; confirm which is wanted before the migration.
- **How long saved progress is honoured.** A user who returns after six months resumes onto step 7 of a flow they no longer remember starting. Decide whether progress expires, and if so what the user sees.
- **App-store review guidelines dislike unavoidable flows.** Suppressing the Android hardware back in particular is worth a check before submission. Resumability is the mitigation — the user is never trapped *and* losing work — but it is worth stating in the review notes.

## Out of scope

Steps 2 and 3 (email, date of birth), the verification screens before this one, the profile-attribute persistence layer (README build item 01) beyond writing the name and the flow position, the `ProfileVisibility` control (not present on this screen — the name is always shown), surname or display-name handling, and any name-moderation or profanity check.

## Dependencies

- **A place to store the name.** `displayName` exists on the backend at `main @ c195089`, so this one step is *not* blocked by build item 01 — but confirm the name captured here is `displayName` and not a new `firstName` column before writing the migration
- **A place to store the flow position** — a per-account "profile creation progress" value, plus the launch-time routing that reads it. This is new, it is shared by every screen in the flow, and it is the one dependency that should be built before step 1 rather than alongside it
- Step 2 (email) — the only destination out of this screen
- The app tutorial — the origin, and the last point at which the user could still turn back
- Lora + Manrope in the app font set
- `.su-underlined em` shipped as a shared rule, not a per-screen style

## Open (not blocking)

- **The error card wants to be one component across the flow.** It is now identical on name and DoB, and the email screen has its own variant. Extract it once — pad 10 14, radius 12, the two danger tints, the 18px glyph, the `aria-live` region — before a fourth screen invents a fifth version.
- **The 30-character cap is unconfirmed.** The mock slices at 30; no minimum exists. Needs a real min/max, and a decision on non-Latin scripts, spaces and hyphens (Mary-Jane, Jean Luc, 李).
- **The mock force-capitalises the first letter.** That is a demo convenience. Ship `autocapitalize="words"` as a keyboard hint and leave the user's own casing alone — forcing it breaks names that are deliberately lower-case, and it is the wrong layer to enforce presentation.
- **Label and placeholder say different things** ("Enter first name" / "First name") and both ship. Confirm that is intended rather than a leftover; a screen reader will announce both.
- **First name only, no surname anywhere in the flow.** Confirm the product never needs one — adding a second field to "The basics" later re-costs this screen and the progress bar.
- **`ProfileVisibility` appears on all nine detail steps but not here**, because the name is always shown. Worth stating in the flow's rules so nobody adds it for consistency.
