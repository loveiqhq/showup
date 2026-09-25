# [Profile 05] Embrace — build your profile

**Parent:** Profile creation flow (SHOWUP-TBD — epic `profile/tickets/00-epic.md`) · **Type:** Story · **Priority:** Medium
**Attachments:** `profile-05-embrace-build-profile-spec-sheet.png`

## Description

The bridge between finishing "The basics" and starting to build the profile. The user has just given their name, email and date of birth; the next thing the app asks for is photos. This screen sits between the two: one warm beat that says what is coming and why it is worth doing, then a single button into it.

**It is not a step.** It has no field, no choice, nothing to validate and nothing that can fail. It is one state, and the only branch in it is whether a first name is available for the greeting.

Entered from screen 04 (date of birth) on a successful Continue. **One exit only:**

- **CTA "Upload my photos"** → photos

**There is no header and no progress bar on this screen, and that is the design.** "The basics" is finished and "Share some details" has not started, so the screen belongs to neither progress bar. Adding an `AppHeader` or a `StepProgress` "for consistency with the other profile screens" is the single most likely mistake here — both were considered and both are deliberately absent. The headline block's 64px top padding is what replaces that chrome.

**There is no back and no skip.** Profile creation is mandatory once entered (flow rule 4b) and the previous step locked the user's age, so there is nothing useful behind this screen. The platform back gesture is suppressed here exactly as it is on screen 01.

## Decided before build

Two properties of this screen deliberately break the profile flow's own rules. Both are settled — build them as drawn, and do not "correct" them to match the other profile screens.

- **It carries the ambient backdrop** (callout ②), where flow README rule 5 says every profile screen is flat `--liq-bg`. That rule exists because every other screen in the flow has the keyboard open and no room for atmosphere. This screen has no keyboard and no input, so it uses the first-run Startup screen's orange/violet orbs and peach wash. **Named exception: this screen and its sibling bridge only.**
- **Its CTA is full-width sunset** (callout ⑪), where flow README rule 7 reserves `--su-grad-sunset` for commitment screens and rule 5b puts the round orange `NextButton` on every screen in "The basics". Agreeing to build the profile is a commitment beat, and it gets the sunset gradient at full width.

Both exceptions are recorded against rules 5 and 7 in `design_handoff_showup/profile/README.md`. Do not carry either of them back into name, email, verify email or date of birth.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** StatusBar (54, fixed) → headline block (`flex: none`, padding `64 28 0`) → body block (`flex: 1`, padding `28 28 0`, column) → HomeIndicator (28, fixed).

**One `flex: 1` spacer, not two.** Inside the body block the lead paragraph, the bullet list and the closing paragraph are top-anchored, then a single spacer, then the CTA wrapper at `margin-bottom: 22`. At 390 × 844 the spacer resolves to ≈240; at 375 × 667 to ≈65.

⚠ **The only absolutely-positioned elements on this screen are the three decorative backdrop layers** — the two orbs and the peach wash. Nothing in the content column is positioned. The orbs are sized in percentages and deliberately overflow the frame (top −15%, right −25%, bottom −20%, left −30%) so they scale with the device instead of needing per-size values.

**The gutter is 28, not the group's 24.** The other profile screens align their gutter to a 64px input field; this one has no field, and the wider gutter is what lets the 34px Lora headline breathe.

**Stacking:** backdrop `zIndex 0` · content `zIndex 1` · status bar and home indicator `zIndex 2`. The orbs run full-bleed behind the status bar and the home indicator — they are not clipped to the gutter.

📎 All specs are on the attached **profile-05-embrace-build-profile-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–⑬, with the two named exceptions keyed in violet. Note the design system has colour and type tokens only — there is no spacing scale, so the raw px values are intentional, not an oversight.

> **Reference implementation:** `design_handoff_showup/profile/screen-embrace-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. State prop: **the only state** `<ScreenProfileEmbrace name="Leo"/>`; the no-name branch is `<ScreenProfileEmbrace name=""/>`.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

The status bar and the home indicator. `StatusBar` and `HomeIndicator` in the reference file are stylized mocks, drawn so the artboard shows the true content height between them. Ship the platform chrome and anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)` — not to the mock's 54 and 28.

## Copy — final strings

Headline, with a name: `Nice to see you, {firstName}.` then a hard line break, then `Time to show the person behind your profile.`
Headline, without a name: `Glad you're here.` then a hard line break, then `Time to show the person behind your profile.`
Emphasis: `behind` — the single italic em, with the orange underline wash
Lead: `You are wonderful as you are. Share what makes you unique so others get a real feel for who they'll meet.`
Bullet 1: `Upload meaningful photos.`
Bullet 2: `Record a voice or video prompt.`
Closing: `More of you means better matches — and more real-life connections.`
CTA: `Upload my photos`

The greeting always owns its own line — the `<br>` is not a wrap, it is the structure. The second sentence is **identical in both name branches**; only the greeting changes. The closing line uses a spaced em dash, not a hyphen.

**The CTA label has no arrow character in it.** The arrow is a trailing SVG icon on the button (arrow-right, 18, stroke 2, `currentColor`). Do not put `→` in the string — the kit did until 9 Sep 2026 and it was a bug against the no-unicode-glyph brand rule.

Product terms capitalise exactly: **Show Up**.

## Behaviour

- **The screen does nothing on arrival.** No animation on the copy, no auto-advance, no timer, no progress ring. The user reads and taps.
- **The CTA is the only interactive element.** Press feedback only, no hover: scale to 0.98 on press-down, `transform 180ms cubic-bezier(.22,1,.36,1)`.
- **The CTA is never disabled** — there is nothing to validate.
- **The name comes from the value captured on screen 01**, already trimmed. If it is missing or whitespace-only, render the no-name headline; never render `Nice to see you, .` and never substitute a placeholder like "there" or "friend".
- **Tapping the CTA advances to photos.** It does not need to write anything: the basics were persisted on screen 04's Continue. It **does** update the saved flow position, so a user who quits here relaunches onto photos rather than back onto this bridge.
- **Backwards navigation is blocked entirely** — no chevron, no iOS swipe-back, no Android hardware or gesture back.
- The backdrop layers are decorative: `pointer-events: none`, and not announced to assistive technology.
- Nothing on the screen is a link. No legal copy, no "learn more", no privacy affordance — those live on the screens that actually collect something.

## Acceptance criteria

- [ ] **There is no `AppHeader` on this screen** — no section title, no back chevron, no skip, no close, and no reserved 36 slots
- [ ] **There is no `StepProgress` on this screen** — the screen appears in neither the "The basics" progress bar nor the "Share some details" one
- [ ] **There is no way backwards out of this screen:** no chevron, no iOS swipe-back, no Android hardware or gesture back. Attempting the gesture leaves the user on the screen
- [ ] The headline block's top padding is `64` and the gutter is `28`
- [ ] Headline is Lora 700 / **34** / 1.1 / −0.015em in `--liq-fg` `#1D1129`, `text-wrap: balance`, with a hard line break after the greeting sentence
- [ ] `behind` is the single italic em, carrying the orange radial wash via the shared `.su-underlined em` rule — **not** re-derived per screen, and it never breaks across lines
- [ ] With an empty or whitespace-only name the headline reads `Glad you're here.` and the second sentence is unchanged
- [ ] Lead and closing paragraphs are Manrope 500 / **15.5** / 1.55 in `--liq-neutral-200` `#4B3B5A`, `text-wrap: pretty`, closing paragraph at `margin-top: 14`
- [ ] The bullet list is `list-style: none` with `gap: 8`, each row `gap: 12`, text Manrope **600** / 15 / 1.45 in `--liq-fg`
- [ ] **Each bullet marker is a 7 × 7 round element in `--liq-orange-500` at `margin-top: 9`** — not a `•`, not an emoji, not `list-style-type`
- [ ] The ambient backdrop is three decorative `pointer-events: none` layers at `zIndex 0` with the exact gradients, sizes and offsets in the reference file — orange orb top-right, violet orb bottom-left, peach wash 360 tall from the top edge
- [ ] The orbs run **full-bleed behind the status bar and the home indicator**, and are not clipped to the gutter
- [ ] CTA is a **full-width sunset** button: h56, radius 9999, `--su-grad-sunset`, `--liq-shadow-violet`, label `Upload my photos` in Manrope 700 / 16 white, with a trailing 18px arrow-right icon at stroke 2 — **not the round orange `NextButton`, and no `→` character in the label**
- [ ] The CTA wrapper's `margin-bottom` is `22` and the CTA is never disabled
- [ ] **A single `flex: 1` spacer** sits between the closing paragraph and the CTA wrapper, and it is the only flexible element on the screen
- [ ] No absolute Y positioning anywhere in the content column — the only positioned elements are the three backdrop layers
- [ ] Nothing animates on arrival, and the screen never auto-advances
- [ ] Tapping the CTA goes to photos and updates the saved flow position, so relaunching lands on photos and not on this bridge
- [ ] Copy matches the strings above exactly, including the line break and the em dash
- [ ] **Evidence of done:** attach screenshots at **375 × 667, 390 × 844 and 430 × 932** — three images. Each must show all content visible, nothing clipped or truncated, no scroll, and the CTA fully visible. On 375 × 667, state what the spacer collapsed to (it should be ≈65) and confirm the headline still fits without shrinking the type.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **D — Profile Identity**). Use them exactly — do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_embrace_build"`, `screen_name: "ProfileEmbraceBuild"`, `referrer_screen_id` |
| Bridge shown | `embrace_bridge_viewed` | `variant: "build_profile"` — the value set is `enums.json` §16 |

Both come from the registry row for this screen in `tracking/enums.json` §11 — added 9 Sep 2026, before this ticket was written.

**Three events that must NOT fire here.**

- **`profile_step_viewed` / `profile_step_completed`** — this screen is not a step and has no `step_id`. §2 has no row for it, deliberately. Firing either would put a phantom step in the completion funnel.
- **`profile_build_started`** — fires once per profile build, on screen 01 (name), with `entry_point`. The screen's name invites a second one; there is exactly one.

**There is no CTA event, on purpose.** The tap is the only act on the screen and it is the screen's only exit, so `screen_viewed` on the photos screen carrying `referrer_screen_id: "profile_embrace_build"` already measures it — a `cta_tapped` here would be a duplicate of that edge. **If a bridge-level drop-off rate is wanted, it gets defined in `events.json` first** and this ticket is updated; do not invent a name at the call site.

`embrace_bridge_viewed` is **not implemented in the current build** — `backend_status: "Client only"`, `implemented: false`.

## Out of scope

The photos screen (the destination), the sibling bridge that appears later in the flow (`variant: "add_details"` — same shell, its own ticket), the confetti treatment that belongs to that sibling and **not** to this screen, the "Share some details" progress bar, the profile-attribute persistence layer beyond updating the saved flow position, and any onboarding-value experiment on this copy.

## Dependencies

- **Screen 04 (date of birth)** — the only origin
- **Photos** — the only destination
- **The saved flow position** — shared by every screen in the flow; this screen updates it and does not otherwise write
- The name captured on screen 01, for the greeting
- Lora + Manrope in the app font set
- `.su-underlined em` shipped as a shared rule, not a per-screen style
- The ambient-backdrop layers as a **reusable component** — this screen and the first-run Startup screen use the same recipe, and the sibling bridge uses a variant of it. Extract it once rather than pasting three gradients into a third screen

## Open (not blocking)

- **The bridge is a candidate for the first thing to cut.** It costs the user a tap and measures nothing. Worth an experiment once `embrace_bridge_viewed` exists: bridge vs. straight to photos, measured on photo-upload completion, not on tap-through.
- **"You are wonderful as you are" is the warmest line in the product.** It is on-brand-adjacent rather than on-brand; the brand voice discourages friendly-app filler. Kept because the intent — one reassuring beat before asking for photos — is right. Flag it if the voice guidelines tighten.
- **Two bullets promise three things.** The copy names photos and a voice-or-video prompt; the flow after this screen also asks for interests, lifestyle, living status and text prompts. Either the bullets undersell the flow or the flow is longer than the bridge admits. Decide which before the flow's length is final.
- **The sibling bridge shares this shell.** `embrace_bridge_viewed.variant` already anticipates two. Build this one as the shell with the copy as props — the same lesson as tutorial card 01 — so the second bridge is a payload and not a fork.
