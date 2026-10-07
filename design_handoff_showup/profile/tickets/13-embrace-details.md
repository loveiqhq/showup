# [Profile 13] Embrace 2 — add profile details

**Parent:** Profile creation flow (SHOWUP-TBD — epic `profile/tickets/00-epic.md`) · **Type:** Story · **Priority:** Medium
**Attachments:** `profile-13-embrace-details-attachment.zip` (handoff folder for this ticket) · `profile-13-embrace-details-spec-sheet.png`

## Description

The second bridge in profile creation. The user has finished the core profile — photos, prompts, media — and answered the three asks (notifications, Stay reachable, location). The next thing the app asks for is optional: the eleven "Share some details" steps. This screen sits between the two: **it celebrates what the user has just done, with a one-shot confetti rain, then asks for more** with a single button.

**The confetti is the reason the screen exists.** Finishing the profile is the longest stretch of the flow, and this is the one moment the product says "well done" before asking for anything else. The rain plays **once, on arrival, for about three seconds**, then the screen sits still.

**It is not a step.** No field, no choice, nothing to validate, nothing that can fail. One state; the only branch is whether a first name exists for the greeting.

Entered from screen 12 (location) on every outcome except a denial. **One exit only:**

- **CTA "Add profile details"** → height, step 1 of "Share some details"

**No header, no progress bar, no back, no skip** — exactly as on screen 05. The asks are done and "Share some details" has not started, so the screen belongs to neither progress bar. Adding an `AppHeader` or `StepProgress` "for consistency" is the most likely mistake here.

## Decided before build

- **Confetti on arrival** (callouts ⑪–⑯). Decided 5 Oct 2026. Specified fully below and in the reference file; the kit's earlier static placeholder (`ConfettiRainStatic`) is **gone** — do not build a static scatter.
- **Same shell as screen 05, different payload.** Ambient backdrop (②) and full-width sunset CTA (⑧) are the bridges' named exceptions to flow README rules 5 and 7, already recorded there. This screen **reuses 05's shell** — do not fork it. What differs is payload: the violet glow is centred rather than bottom-left, the headline has no em and no bullets follow it, and this screen has the confetti layer. If 05's shell does not take those as props yet, add the props; do not copy the screen.
- **One headline size for the bridges: 34.** Decided 5 Oct 2026. The kit drew 36 here; it now matches 05 exactly — Lora 700 / 34 / 1.1 / −0.015em. The headline size is **part of the shared shell, not a prop**, so the two bridges cannot drift apart again. Standing rule: one headline size per screen group (`design_handoff_showup/CLAUDE.md` → *Screen layout*).

## Layout — read this before building

⚠ **Layout is flex, not absolute.** StatusBar (54) → headline block (`flex: none`, padding `64 28 0`) → body block (`flex: 1`, padding `20 28 0`, column) → HomeIndicator (28).

**One `flex: 1` spacer.** Inside the body: the lead paragraph, top-anchored → a single spacer → the CTA wrapper at `margin-bottom: 22`. The spacer resolves to ≈450 at 390 × 844, ≈270 at 375 × 667, ≈535 at 430 × 932.

⚠ **The only absolutely-positioned elements are the three backdrop layers and the confetti layer.** Nothing in the content column is positioned.

**Stacking:** backdrop `zIndex 0` · confetti `zIndex 0`, later in the tree · content `zIndex 1` · status bar and home indicator `zIndex 2`. **The confetti passes behind the copy and the CTA, never over them.**

📎 All specs are on the attached **profile-13-embrace-details-spec-sheet.png** — frame A at rest (①–⑩), frame B frozen 900 ms into the confetti (⑪–⑯, orange), plus a timeline bar. The standalone HTML of the same sheet plays the animation live (*Replay the confetti*). The design system has colour and type tokens only — there is no spacing or motion scale, so the raw px and ms values are intentional.

> **Reference implementation:** `design_handoff_showup/profile/screen-embrace-details-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. The only state: `<ScreenProfileEmbrace2 name="Leo"/>`; the no-name branch is `name=""`. The `confetti="frozen"` / `frozenAt` props exist **for the spec sheet only** — the app plays once.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## The confetti

Every number lives in `ConfettiRain` in the reference file. The table below is what it means.

| | |
|---|---|
| **Starts** | **t = 0 when the push transition has settled** — not on mount, where it would play behind the incoming slide |
| **Pieces** | **32**, from the fixed `CONFETTI_PIECES` table — x position, delay, fall duration, start rotation, spin, sway, shape, colour, size. **Deterministic, not random**, so every screenshot and recording is comparable |
| **Shapes** | strip (size × 0.5 by size × 1.4, radius 1.5) · square (size², radius 2) · dot (round). Size 8–14 |
| **Colours** | tokens only: `--liq-orange-500` `#FE6839` · `--liq-orange-400` `#FF9450` · `--liq-primary-500` `#812AEC` · `--liq-primary-400` `#A855F7` |
| **Fall** | translateY **−40 → screen height + 40**, so no piece appears or vanishes on screen. 1950–2600 ms per piece, `cubic-bezier(.3,.4,.6,1)`. Opacity 1 until 80% of the fall, 0 at 100% |
| **Flutter** | sway ±12–22 px, rotate `rot0 → rot0 + spin` (±360–600°), rotateY 0 → 180°. Ease-in-out, alternate, 700 / 820 / 940 / 1060 ms by index |
| **Stagger** | per-piece delay 0–700 ms |
| **Ends** | last piece off screen at ≈2930 ms; **layer unmounted at 3000 ms**. Never loops |
| **Fall distance** | the screen's own height + 80, **measured at runtime** — not 884. Same durations on every device |

**Rendering:** `transform` and `opacity` only — never `top`, `left`, `width` or `height`. Drive it from the platform's native animation layer, not a per-frame JS loop on the UI thread. No Lottie file, no sound, no haptic.

**Reduce Motion:** with iOS *Reduce Motion* or Android *Remove animations* (animator duration scale 0) the confetti **does not render at all** — the user sees frame A from the start. Not a static scatter: still pieces on a still screen read as debris.

## What is not ours

The status bar and the home indicator — stylized mocks in the reference file. Ship the platform chrome and anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.

## Copy — final strings

Headline, with a name: `You are doing great, {firstName}.`
Headline, without a name: `You are doing great.`
Lead: `You'll see on others' profiles exactly what you choose to share on yours. Let's add a few more details!`
CTA: `Add profile details`

**The headline copy is final** (confirmed 5 Oct 2026) — do not reword it. **No em in the headline** and no orange wash — unlike screen 05. **The CTA label has no arrow character**; the arrow is a trailing SVG icon (arrow-right, 18, stroke 2, `currentColor`).

## Behaviour

- **The confetti plays once per arrival** — on a fresh push of this screen, including a relaunch that resumes here. It does **not** replay when the app returns from the background with this screen already showing.
- **Nothing waits for the confetti.** The CTA is live from the first frame. A tap mid-fall navigates immediately; the confetti leaves with the screen and does not continue on the next one.
- **The CTA is the only interactive element and is never disabled.** Press feedback only: scale 0.98 on press-down, `transform 180ms cubic-bezier(.22,1,.36,1)`.
- **The name comes from screen 01**, trimmed. Missing or whitespace-only → the no-name headline; never `You are doing great, .` and never a placeholder.
- **Tapping the CTA advances to height** and updates the saved flow position, so a user who quits on height relaunches there, not here. Nothing else is written.
- **Backwards navigation is blocked** — no chevron, no iOS swipe-back, no Android hardware or gesture back.
- The backdrop and confetti are decorative: `pointer-events: none`, not announced to assistive technology, and nothing is announced when the confetti starts or ends.

## Acceptance criteria

- [ ] **There is no `AppHeader` and no `StepProgress` on this screen**, and no reserved slots for either
- [ ] **There is no way backwards out of this screen:** no chevron, no iOS swipe-back, no Android back
- [ ] The screen is built from **screen 05's shell** with this screen's payload — not a copy of 05
- [ ] Headline is Lora 700 / **34** / 1.1 / −0.015em in `--liq-fg` — **identical to screen 05, from the shared shell** — `text-wrap: balance`, block padding `64 28 0`, **no em**
- [ ] With an empty or whitespace-only name the headline reads `You are doing great.`
- [ ] Lead is Manrope 500 / **16** / 1.55 in `--liq-neutral-200`, `text-wrap: pretty`, `max-width: 330`, block padding `20 28 0`
- [ ] Backdrop matches the reference: orange orb top-right, **violet glow centred at the bottom**, peach wash 320 tall — decorative, `zIndex 0`, full-bleed behind the status bar and home indicator
- [ ] CTA is full-width sunset, h56, label `Add profile details` with a trailing 18px SVG arrow, wrapper `margin-bottom: 22`, never disabled
- [ ] A single `flex: 1` spacer is the only flexible element; no absolute Y positioning in the content column
- [ ] **The confetti starts when the push transition has settled**, runs from the 32-piece table in the reference file, and **the layer is gone by 3000 ms**
- [ ] Pieces enter from above the top edge and leave below the bottom edge, fading over the last 20% of their fall — none appears or disappears on screen
- [ ] **Confetti passes behind the headline, the lead and the CTA**, never in front of them
- [ ] Fall distance is measured from the screen height at runtime; the timing is identical on all three devices
- [ ] Only `transform` and `opacity` are animated; the animation holds 60 fps on the lowest supported device of each platform
- [ ] **With Reduce Motion / Remove animations on, no confetti renders** and the screen is otherwise identical
- [ ] The confetti plays once per push (including a resume onto this screen) and **does not replay on returning from the background**
- [ ] **Tapping the CTA mid-fall navigates immediately**; no confetti appears on height
- [ ] Tapping the CTA goes to height and updates the saved flow position
- [ ] `screen_viewed` fires on arrival with `screen_id: "profile_embrace_details"`, `screen_name: "ProfileEmbraceDetails"` and `referrer_screen_id: "profile_location"`, and `embrace_bridge_viewed` with `variant: "add_details"`
- [ ] Tapping the CTA produces `screen_viewed` on height with `screen_id: "profile_height"` and `referrer_screen_id: "profile_embrace_details"` — checked in the event log, not assumed
- [ ] Copy matches the strings above exactly
- [ ] **Evidence of done:** attach screenshots of the screen **at rest** at **375 × 667, 390 × 844 and 430 × 932** — all content visible, nothing clipped, no scroll, CTA fully visible; on 375 × 667, state what the spacer collapsed to (≈270). Plus a **screen recording at 390 × 844** of the arrival from location through the full confetti, and **one recording with Reduce Motion on** showing no confetti.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **D — Profile Identity**, `screen_viewed` in family A). Use them exactly — do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_embrace_details"`, `screen_name: "ProfileEmbraceDetails"`, `referrer_screen_id` |
| Bridge shown | `embrace_bridge_viewed` | `variant: "add_details"` — the value set is `enums.json` §16 |

Both come from the §11 registry row `profile_embrace_details`, registered 9 Sep 2026 with screen 05 and marked ticketed by this ticket (5 Oct 2026).

**Events that must NOT fire here.**

- **`profile_step_viewed` / `profile_step_completed`** — not a step, no `step_id`, deliberately no §2 row.
- **`profile_build_started`** — fires once, on screen 01.
- **Anything for the confetti** — no `animation_played`, no `confetti_shown`. It is decoration with no outcome to measure; `embrace_bridge_viewed` already records that the screen was seen.

**No CTA event, on purpose.** The tap is the only exit, so `screen_viewed` on height with `referrer_screen_id: "profile_embrace_details"` measures it.

**Screen-to-screen conversion — both edges of this screen.** Conversion is read from `screen_viewed` alone: each screen's view carries the previous screen's id in `referrer_screen_id`, so every edge of the flow is a ratio of two counts. No conversion event exists or is needed.

| Edge | Numerator | Denominator |
|---|---|---|
| Location → Embrace 2 | `screen_viewed` · `screen_id: "profile_embrace_details"` · `referrer_screen_id: "profile_location"` | `screen_viewed` · `screen_id: "profile_location"` |
| Embrace 2 → height | `screen_viewed` · `screen_id: "profile_height"` · `referrer_screen_id: "profile_embrace_details"` — **NEW row, 5 Oct 2026** | `screen_viewed` · `screen_id: "profile_embrace_details"` |

`profile_height` / `ProfileHeight` is a **new §11 row (registry 1.4.8)**, added with this ticket together with rows for all eleven "Share some details" screens, so the funnel runs unbroken from this bridge to the end of the flow. **`referrer_screen_id` must be the id of the screen the user actually came from** — set by the navigation, never hard-coded per screen.

**Missing from the current build:** `embrace_bridge_viewed` is `implemented: false` (`backend_status: "Client only"`).

## Out of scope

Screen 05 itself (beyond adding the shell props this screen needs), the height screen and the rest of "Share some details", a confetti treatment anywhere else in the app, sound or haptics, and any experiment on the copy.

## Dependencies

- **Screen 12 (location)** — the only origin
- **Height** (Share some details, step 1) — the only destination. Its §11 row `profile_height` was added with this ticket (registry 1.4.8); height's own ticket cites it
- **Screen 05's shell**, with backdrop variant and payload as props
- **The ambient-backdrop component** (Startup, 05, 09, 12, this screen)
- The saved flow position, and the name captured on screen 01
- The platform's Reduce Motion / animator-scale setting, read at arrival

## Open (not blocking)

- **The violet glow is centred here and in the corner on 05.** Intentional per the kit; if the backdrop component cannot take it as a prop cheaply, say so before building a second variant.
