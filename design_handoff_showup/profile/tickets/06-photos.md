# [Profile 06] Photos — the photo grid

**Parent:** Profile creation flow (SHOWUP-TBD — epic `profile/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `profile-06-photos-spec-sheet.png`

## Description

The first real profile-building screen, and the first thing the user is asked to *give* rather than type. A 2 × 2 grid of photo slots with a stated requirement — **four photos to continue, six maximum** — a sticky Continue, and the permission surface that comes with reaching into the device's photo library.

It is **step 1 of 4 of "The real you"** — photos → prompts → media → details — a **different group from "The basics"**, with its own header title and its own 4-segment progress bar. Entered from screen 05, the embrace bridge.

**Seven states, one component, one ticket.** They are variants of one layout column, not separate screens:

| | State | Prop |
| --- | --- | --- |
| A | Empty — nothing added yet | `state="empty"` |
| B | Some added — 2 of 6 | `state="partial"` |
| C | Uploading + failed | `state="uploading"` |
| D | Library blocked — **Android ≤ 12 only** | `state="denied"` |
| E | Library — can still ask | `state="denied" access="ask"` |
| F | Source sheet — what "+" opens | `sheet="default"` |
| G | Source sheet — camera blocked | `sheet="camera-blocked"` |

**Continue is never disabled.** Pressing it below four photos answers with a toast naming the requirement. This is the group-wide pattern (flow rule 2c) and the reason the specific rule is ever seen.

## Decided before build — the picker choice, and what it removes

**Present the system picker — iOS `PHPickerViewController`, Android 13+ photo picker — and do not request a library permission.** This is a build constraint, not an implementation detail, because it is what makes the rest of this ticket short.

The system picker runs out-of-process, lets the user browse their **whole** library, and returns only what they chose. Therefore:

- **iOS needs no library access card at all**, in any status.
- **Android 13+ needs none either.**
- **iOS "Limited access" cannot happen.** There is no `limited` state in this ticket and no partial-library banner. An earlier draft had one; it was **deleted, not redesigned**, because it explained a state we cannot enter.

What survives is genuinely narrow:

- **Library, Android ≤ 12 only** — still gated behind a runtime permission, so **D and E apply there and only there**.
- **Camera, both platforms, always** — the one permission that never goes away, handled in the source sheet (F / G).

**Build an in-app library browser instead and every one of those states comes back**, including the limited-access banner that was just removed. Don't.

## Layout — read this before building

⚠ **This is the first screen in the flow that scrolls, and only its middle scrolls.** StatusBar (54, fixed) → AppHeader (52, fixed) → StepProgress wrapper (fixed) → **scrolling middle** (`flex: 1`, `overflow: auto`, padding `4 24 0`) → sticky footer (`flex: none`) → HomeIndicator (28, fixed). The header, the progress bar and the CTA never move. Do not make the whole screen scroll and do not put the CTA inside the scroll region.

⚠ **Every slot is 158 tall in every state** — filled, empty, uploading, failed. That single number is what stops the grid reflowing mid-upload and what keeps the CTA reachable at the same scroll offset.

⚠ **No absolute Y positioning.** It breaks harder here than anywhere else in the flow: any fixed Y inside the scrolling region is wrong the moment the user drags.

**The toast is absolutely positioned at `bottom: 100%` of the footer**, so the footer keeps identical geometry when it is idle and nothing reflows when the toast appears.

The scrolling region ends with a fixed **24px tail spacer** so the last row clears the footer's gradient mask.

📎 All specs are on the attached **profile-06-photos-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–⑳ across the seven frames, with the permission surface keyed in **violet** and the failure states in **danger red**. The sheet also carries the full **status → behaviour matrix**, the **"What the OS owns"** panel, and a panel explaining **why there is no limited-access state**; all three are reproduced below. Note the design system has colour and type tokens only — there is no spacing scale, so the raw px values are intentional, not an oversight.

> **Reference implementation:** `design_handoff_showup/profile/screen-photos-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. State props are in the table above.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

The OS owns all of the following. **None of them are drawn in the reference file, and none of them are built.**

- **The permission alert** — its copy, type, buttons, height, animation, and which buttons it offers. For the camera it appears at the moment of use; on iOS it can be shown **once**.
- **The system photo picker** — iOS `PHPickerViewController` and the Android 13+ photo picker, including their permission-free access model. **Use them; do not build an in-app library browser.**
- **The camera UI.**
- **The Settings app**, and the page we land on. We choose to open it; we do not choose what it shows or where the row sits.

**Never spec a height, a copy string or an animation for any of them, and never draw them in a mock.** What is ours is the state we are in *before* the sheet appears — the source sheet, F — and the state we show *after* it returns: D, E and G.

## Every permission status, and what to do with it

The app **always knows which status it is in**. It **never** knows which toggle the user will land on, because neither platform lets us deep-link to a single permission row. That asymmetry is what this table settles. **Read the first two rows before anything else** — presenting the system picker removes the library permission entirely on iOS and modern Android, which is what makes the rest of the table short.

| Platform & status | What we show | What the button does | Notes |
|---|---|---|---|
| **iOS** · library, any status | **A / B** — normal grid | Slot tap opens the source sheet, then `PHPickerViewController`. | **No permission is requested, so there is no status to branch on.** This is why there is no limited-access state and no iOS access card. |
| **Android 13+** · library | **A / B** — normal grid | Slot tap opens the source sheet, then the system photo picker. | **No permission required either.** D and E are unreachable here — do not build a gate that never fires. |
| **Android ≤ 12** · not granted, can ask | **E** — access card, ask mode | Requests the permission **in-app**. | The library genuinely is gated here. First refusal is recoverable. |
| **Android ≤ 12** · permanently denied | **D** — access card, blocked mode | Opens the **app details / permissions page** for our package. | Detected by the rationale flag being false after a denial. The row is labelled differently per version — **name the platform's own label**, never hard-code "Photos". |
| **Camera** · can still ask **(both platforms)** | **F** — source sheet, normal row | Tapping **Take a photo** fires the OS prompt. | Camera is the **one permission that always applies**. Nothing is pre-emptively explained — the OS asks at the moment of use. |
| **Camera** · permanently denied **(both)** | **G** — source sheet, quiet camera row | **Settings** pill opens our app's settings / app details page. | **Blocks nothing** — the library row is untouched and the step is still completable. Never a grid replacement. |
| Returning from Settings | **whatever the new status is** | — | **Re-read the status on foreground, every time.** A user who granted access and comes back to the blocked card will not try twice. |

**The two library modes are not one boolean.** *Can still ask* (re-prompt in-app) vs *blocked* (open Settings and name the row). Collapsing them is how a screen ends up telling a user to open Settings when it could simply have asked.

## Copy — final strings

Section title (AppHeader): `The real you`
Headline: `The messy hair, the loud laugh.` — "messy hair" italic, with the orange underline wash
Sub copy: `Show who you actually are — not a polished version. Four photos to continue, six if you've got them.`
Count row, left: `Photos · 4 required, 6 max`
Count row, right: `{filled} of 6`
Slot hints: `Portrait` · `With friends` · `Full body` · `Favourite activity` · `Anything you like` (slots 5–6)
First-slot CTA hint (state A only): `Start with your face`
Optional divider: `Optional · slots 5 & 6`
Add-more control: `Add more`
Reorder hint: `Drag to reorder · the first one is your main photo`
Main badge: `MAIN`
CTA: `Continue`

**Uploading / failed:**
In-flight label: `Uploading…`
Failure label: `Upload failed`
Failure action: `Retry`

**Access card — blocked (D):**
Title: `Photo access is needed to continue`
Body: `Show Up can't see your photos, so there is nothing to add. Open Settings, turn on Photos, then come straight back — nothing you have entered is lost.` — `Photos` in bold, and **replaced by the platform's own row label**
Button: `Open Settings`

**Access card — can still ask (E):**
Title: `Photo access is needed to continue` (identical)
Body: `Allow access so you can pick your photos. Nothing you have entered is lost.`
Button: `Allow photo access`

**Source sheet (F):**
Title: `Add a photo`
Row 1: `Choose from library` / `Pick one or more`
Row 2: `Take a photo` / `Use the camera now`

**Source sheet — camera blocked (G):**
Row 2 sub: `Camera access is off. Turn on Camera in Settings to use it.`
Row 2 action: `Settings`
Row 1 is **unchanged**.

**Toast, three variants:**
Default: `Upload at least 4 photos to continue`
In D: `Turn on Photos in Settings to continue`
In E: `Allow photo access to continue`

The requirement is stated three times on purpose — in the sub copy as a sentence, in the count row as a number, and in the toast on refusal. Do not remove any of the three. Product terms capitalise exactly: **Show Up**.

## Behaviour

- **Slot tap opens the source sheet** (F) — library or camera. It does **not** open the picker directly. On a filled slot the chosen photo replaces that one.
- **The source sheet is ours; what it launches is the OS's.** The scrim dismisses it, and the grid stays visible behind it — the user is choosing a source, not leaving the screen.
- **Camera denial is answered on the camera row**, not on a card and not on a screen the user has to be sent to. The library row stays fully functional.
- **Remove is immediate, no confirmation.** Re-adding is one tap; a dialog here is friction.
- **The count only advances on a confirmed upload.** An in-flight photo has not been added and a failed one never was. Continue still refuses at three in flight.
- **Failure keeps the slot.** Retry re-uploads into the same slot; the user does not re-pick. Never clear the grid on a failure.
- **Drag to reorder** is in scope. The first slot is always the main photo, and reordering into position 1 moves the Main badge.
- **Slots 5–6 are never on first paint** — they are behind "Add more", and the four required slots come first.
- **The reorder hint appears from the second photo**, not the first — with one photo there is nothing to reorder.
- **Continue below four photos** fires the toast (2600ms, fade + 6px rise over 200ms) and submits nothing. It is not disabled, does not change colour, and does not move.
- **Re-read the permission status on every foreground.** Returning from Settings with access granted must land on the working grid, never on the card the user just left.
- **A denied permission never loses work.** Photos already uploaded stay uploaded; the card's copy promises this and it has to be true.
- Press feedback only, no hover: scale to 0.98 on press-down.

## Acceptance criteria

- [ ] AppHeader title is `The real you` with a back chevron in the leading slot — **not** `The basics`, and not the empty slot from screen 01
- [ ] StepProgress is `steps={4} current={1}` — a **new** progress bar for this group, not a fifth segment on "The basics"
- [ ] The group's header + progress shell is built **once** and consumed by photos, prompts, media and details
- [ ] Headline is Lora 700 / **32** / 1.08 / −0.018em with `messy hair` as the single italic em carrying the orange wash via `.su-underlined em`
- [ ] Sub copy is Manrope 500 / 14.5 / 1.45 in `--liq-neutral-200`, max-width 320
- [ ] Count row: left label Manrope 800 / 10.5 uppercase tracking .08 in `--liq-fg-subtle` **on one line at 390** (`white-space: nowrap`); right numeral Manrope 700 / 13 tabular-nums, switching to `--liq-success-fg` at 4 — **the only success affordance on the screen**
- [ ] Required grid is 2 × 2, `gap: 12`, slot radius 20, **every slot exactly 158 tall in every state**
- [ ] Empty slot: `--liq-bg-raised` with a 1.5px dashed `rgba(129,42,236,0.36)` border, 40px round white pip with a violet plus at stroke 2.4, hint Manrope 600 / 13
- [ ] Optional slots 5–6 use the quieter neutral dashed variant, and are reachable **only** via the 44px `Add more` pill — never rendered on first paint
- [ ] State A only: slot 1 takes the lilac wash + solid violet pip and its hint becomes `Start with your face`
- [ ] Filled slot carries the 28px remove pip at top 8 / right 8 with `blur(8px)`, and slot 1 carries the `MAIN` badge at left 8 / bottom 8
- [ ] Removing a photo takes effect immediately with **no confirmation dialog**
- [ ] **Uploading slot shows the picked image**, dimmed under `rgba(29,17,41,0.44)`, with a **determinate** 38px conic-gradient ring and the label `Uploading…` — not a spinner on an empty slot
- [ ] **Failed slot** is `rgba(251,50,59,0.06)` on 1.5px dashed `rgba(251,50,59,0.34)` — **never a solid red fill** — with the 34px danger glyph, `Upload failed`, and a real 32px `Retry` pill that re-uploads into the **same slot**
- [ ] **The count reads `1 of 6` in state C** with three slots occupied-looking, and Continue still refuses
- [ ] `photos_minimum_met` fires on the fourth **confirmed** upload, not the fourth pick
- [ ] Sticky footer: gradient mask `rgba(255,251,247,0) → 0.92 at 22% → 1` with `backdrop-filter: blur(8px)`, NextButton `Continue` with a **52** circle in `--liq-orange-500` — orange, not sunset — and the CTA is **always in the viewport**
- [ ] **Continue is never disabled** in any state — no opacity change, no colour change, no shift
- [ ] The toast is absolutely positioned at `bottom: 100%` of the footer so the footer's geometry is identical when idle, runs 2600ms, and uses the correct one of the **three** copy variants
- [ ] **State D replaces the grid** — the count row, grid, Add more and reorder hint are all absent — with the lilac access card, `Open Settings`, and body copy **naming the platform's own permission row**
- [ ] **State E is pixel-identical to D** except the body drops the toggle sentence and the button reads `Allow photo access` and **re-prompts in-app** with no Settings trip
- [ ] **One card, two modes** — not two screens, and not one label for both behaviours
- [ ] **D and E are reachable on Android ≤ 12 only.** No library permission is requested on iOS or Android 13+, and **no access card can appear there**
- [ ] **There is no limited-access state and no partial-library banner anywhere in the build**
- [ ] **Tapping a slot opens the source sheet**, not the picker directly: `Add a photo` with a `Choose from library` row and a `Take a photo` row, 62 tall, radius 16, over a `rgba(29,17,41,0.42)` scrim, sheet radius `22 22 0 0` — **and the grid stays visible behind the scrim**
- [ ] **The library path requests no permission** — it goes straight to the system picker
- [ ] **Camera denial is shown on the camera row of the source sheet**: quiet lilac row, violet lock glyph, sub `Camera access is off. Turn on Camera in Settings to use it.`, and a 30px `Settings` pill — **never a grid replacement and never a full-screen card**
- [ ] **The library row is untouched when the camera is blocked**, and the step remains completable
- [ ] The full status → behaviour matrix above is implemented, including **no media permission request on Android 13+ or on iOS**
- [ ] **The permission status is re-read on every foreground** — granting access in Settings and returning lands on the working grid (A / B), never on the card
- [ ] Photos already uploaded survive a permission denial
- [ ] Drag-to-reorder works and moving a photo to position 1 moves the `MAIN` badge
- [ ] Only the middle region scrolls; header, progress bar and CTA never move
- [ ] No absolute Y positioning anywhere
- [ ] **The permission alert, the system picker and the camera UI are not implemented** — the platform's own are used, and no height, copy or animation is specified for any of them
- [ ] Copy matches the strings above exactly
- [ ] **Evidence of done:** attach screenshots of **all seven states** at **375 × 667, 390 × 844 and 430 × 932** — twenty-one images. Each must show the CTA fully visible and nothing clipped. On 375 × 667, show that the count row and the first grid row are visible **without scrolling**, that the sticky footer does not overlap the second row's remove pips, and that the source sheet does not cover the headline. Include one iOS and one Android 13+ capture proving **no permission prompt appears on the library path**.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **E — Profile Photos/Media**). Use them exactly — do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_photos"`, `screen_name: "ProfilePhotos"`, `referrer_screen_id` |
| Step entered | `profile_step_viewed` | `step_id: "photos"`, `step_index` — §2 marks photos as **outside** the "Share some details" progress bar |
| Slot tapped | `photo_slot_tapped` | `slot_index` 0–5, `is_optional`, `action: "add"｜"replace"｜"reveal_optional"` |
| Upload confirmed | `photo_added` | `slot_index`, `source`, `filled_count`, `max_slots: 6` |
| Photo removed | `photo_removed` | `slot_index`, `filled_count` |
| Requirement met | `photos_minimum_met` | `count` — fires when `filled_count` first reaches 4, on the **confirmed** upload |
| Continue pressed below 4 | `form_validation_failed` | `field_id`, `rule`, `screen_id`, `step_id` — fires on the **refused press**, not on render |

**Missing from the current build, and needed for this screen:**

- **The permission states have no events.** There is nothing in the registry for *card shown*, *which mode*, *permission granted / refused*, or *returned from Settings* — so today the single most likely drop-off point on the screen is invisible. **These get defined in `events.json` first**, with a closed `access_status` value set in `enums.json`, and then this ticket's table is updated. Do **not** invent names at the call site.
- **Upload failure has no event.** `photo_added` fires on success only; a failed upload and a retry are both unrecorded. Same rule — define first.
- `photo_slot_tapped`, `photo_added`, `photo_removed` and `photos_minimum_met` are all `implemented: false` in the current build.

**`screen_name` / `screen_id` / `step_id` are three separate vocabularies** and all three come from `tracking/enums.json` (§11 and §2) — never from a literal at the call site. The registry row for this screen was added before this ticket was written.

**Never send a photo, a filename, or a library identifier.** `slot_index`, `source` and counts only.

## Out of scope

Prompts, media and details (the other three steps of "The real you"), the OS permission alert / system picker / camera UI, image processing (crop, rotate, filters, compression), face or nudity moderation, the photo-verification step (its own screen, `ScreenProfileVerify`), photo captions, and the `ProfileVisibility` control — photos are always shown.

## Dependencies

- **Screen 05 (the embrace bridge)** — the only origin
- **Prompts** — the destination
- **Photo upload + storage**, with a per-photo status the client can read (queued / in flight / confirmed / failed) — state C is not buildable without it
- **A permission-status reader for the two cases that survive** — Android ≤ 12 library (granted / can-ask / permanently-denied, via the rationale flag) and **camera on both platforms**. One shared reader: the location screen needs the same thing. **No iOS library-status reader is needed** — we never request it
- **The saved flow position** — shared by the flow
- Lora + Manrope in the app font set
- `.su-underlined em` shipped as a shared rule
- The group's header + progress shell, built here and consumed by three more screens

## Open (not blocking)

- **Four required is unvalidated.** It is the highest ask in the flow and it sits on the first screen that requires effort rather than typing. Worth measuring completion at 3 vs 4 before launch — the copy, the count row and the toast all change if it moves.
- **The permission events are the gap that matters.** Without them we cannot tell a user who denied the camera from one who simply left. Define them before the sprint, not after.
- **`PortraitPlaceholder` is placeholder art.** The slots need real photography before any user-facing build.
- **Retry has no backoff or limit specified.** Decide how many automatic retries happen before the user sees the failed slot at all, and whether a repeated failure says something different.
