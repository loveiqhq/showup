# [Profile 09] Notifications — permission ask

**Parent:** Profile creation flow (SHOWUP-TBD — epic `profile/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** the handoff folder as a **zip** · `profile-09-notifications-spec-sheet.png`

## Description

The notification permission ask. It sits after media (08) and before Stay reachable (10), and it is the last thing profile creation asks for that is not a profile attribute.

**It exists because the product does not work without push.** Show Up binds two people to a 30-minute meeting at a fixed time; the match, the details change, the reminder and the cancellation all arrive as a notification. A user who misses the "your date is in an hour" push does not miss a message — they miss the date, and their **Show-up Rate** drops for it. So the app asks once, in context, with the five things it will send listed on screen, **before** the OS sheet is raised.

**One state, no input, nothing that can fail.** There is no denied state on this screen, no recovery banner, no "asking" state and no error card. Profile creation runs once, on a **fresh install**, so the permission is always *not determined* when the user gets here — the screen has one path, plus two skip cases that are a guard rather than a design (see the status matrix).

Entered from media (08) on Continue or Skip. **One exit:**

- **CTA "Enable notifications"** → the OS sheet → **Stay reachable (10) on any outcome, granted or denied**

**There is no "Not now", no skip and no close, and that is deliberate** — the OS sheet's own "Don't allow" is the decline, and the flow advances either way. See *Open* for the one thing worth measuring before that is made permanent.

## Decided before build

Two properties of this screen break the profile flow's own rules, and both are the **same named exceptions screen 05 carries**. Build them as drawn.

- **It carries the ambient backdrop**, where flow README rule 5 says every profile screen is flat `--liq-bg`. That rule exists because every other screen in the flow has the keyboard open. This one has no keyboard and no input — orange orb top-right, violet orb bottom-left, 360-tall peach wash, the first-run Startup recipe.
- **Its CTA is full-width sunset**, where rule 7 reserves `--su-grad-sunset` for commitment beats. Granting push is a commitment beat.

**Extend the exception in the flow README rather than re-deriving it here:** rules 5 and 7 currently name "the two bridges only". As of 21 Sep 2026 they read *the two bridges and the notifications ask*. Do not carry either into name, email, verify email, date of birth, photos, prompts or media.

**One difference from screen 05: there is no trailing arrow icon on this CTA.** 05's button ends in an 18px arrow-right; this one does not. The label is the whole button.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** StatusBar (54, fixed) → headline block (`flex: none`, padding `40 28 0`) → body block (`flex: 1`, padding `20 28 0`, column, `min-height: 0`) → HomeIndicator (28, fixed).

**One `flex: 1` spacer, not two.** Inside the body block the lead paragraph and the five-row list are top-anchored, then a single spacer (`min-height: 12`), then the CTA wrapper at `margin-bottom: 18`.

⚠ **This screen has the most content of any non-scrolling screen in the flow** — a 32px headline, a lead paragraph and five two-line rows. **It does not scroll.** At 375 × 667 the spacer collapses to its 12 minimum and the rows are what has to be checked; if it does not fit, the fix is agreed in this ticket (see *Open*), **not** an ad-hoc type shrink at build time.

⚠ **The only absolutely-positioned elements are the three decorative backdrop layers.** Nothing in the content column is positioned. The orbs are sized in percentages and deliberately overflow the frame (top −15% / right −25%, bottom −20% / left −30%) so they scale with the device instead of needing per-size values.

**The gutter is 28, not the group's 24** — same reason as screen 05: no input field to align to.

**Stacking:** backdrop `zIndex 0` · content `zIndex 1` · status bar and home indicator `zIndex 2`. The orbs run full-bleed behind both.

📎 All specs are on the attached **profile-09-notifications-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–⑮, with the two named exceptions and the two OS-owned boundaries keyed in violet. The sheet also carries the *when this screen is not shown at all* matrix. Note the design system has colour and type tokens only — there is no spacing scale, so the raw px values are intentional, not an oversight.

> **Reference implementation:** `design_handoff_showup/profile/screen-notifications-reference.jsx` — the code that renders this screen. Read values from it rather than measuring the PNG. State prop: **there is only one state** — `<ScreenProfileNotifications/>`. `onEnable` is a callback, not a variant.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **The OS permission sheet** — iOS `UNUserNotificationCenter.requestAuthorization`, Android 13+ `POST_NOTIFICATIONS`. Its copy, type, height, buttons, button order and animation are the platform's. **Never spec a height for it, never draw it in a mock, never restyle it, and never build a look-alike.**
- **The Settings app**, if the user ever reaches it from screen 10. We choose to open it; we do not choose what it shows or where the notifications row sits.
- **The status bar and the home indicator.** `StatusBar` and `HomeIndicator` in the reference file are stylized mocks, drawn so the artboard shows the true content height. Ship the platform chrome and anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)` — not to the mock's 54 and 28.

**Our surface stops at the CTA.** Everything above it is specified here; everything after the tap is the OS's.

## Every permission status, and what to do with it

**In practice there is one status.** Profile creation runs once, on a **fresh install**, and a fresh install has no notification permission on either platform — uninstalling clears it, and there is no earlier ask in the flow. So the normal path is *not determined*, every time, and the screen is shown.

Two cases survive, and neither is a returning user:

| Case | What we show | What the CTA does | Notes |
|---|---|---|---|
| **Not determined** — iOS, and Android 13+ (API 33+) | **The screen.** This is the path | Raises the OS sheet. On **granted** and on **denied** alike → Stay reachable (10) | The real path, and the only one worth designing for |
| **Android ≤ 12** | **Nothing — the screen is skipped**, silently and before mount | — | There is no runtime notification permission before API 33; notifications are on by default. An ask with nothing to ask for is the bug |
| **Guard: the status is already determined** — restored from an iCloud / device-transfer backup, or the app was killed while the sheet was up and relaunched onto this position | **Nothing — the screen is skipped**, silently and before mount | — | **Not a designed state — a guard.** The system dialog is shown **once per install**, so a screen whose only button raises a dialog that will not appear is a dead end. Read the status before pushing the screen and go straight to 10 |

**Skipping is silent.** No toast, no "notifications are already on" confirmation, no flash of the screen before it navigates — the decision is taken *before* the screen is pushed, so the user never sees it mount.

**A denial is not recovered here.** It is recovered on **Stay reachable (10)**, which owns the notifications consent row and is the screen where a Settings deep link belongs. That is why this ticket has no denied state, no recovery banner and no `Open Settings` button — adding one would put the same recovery in two places, one of which cannot re-prompt.

**Do not ask for provisional authorization** (iOS `.provisional`) as a way around the sheet. It delivers quietly to Notification Centre with no banner and no sound, which is exactly what a date reminder must not be.

## Copy — final strings

Headline: `Never miss a date with Notifications!`
Emphasis: `a date` — the single italic em, with the orange underline wash from the shared `.su-underlined em` rule
Lead: `No spam! Every notification is about your dates and helps you to never miss one.`

The five rows, in this order:

| # | Icon | Title | Tag | Line |
|---|---|---|---|---|
| 1 | `heart` | `Match alert` | — | `Get instant notifications when you receive a match and never miss out on a date.` |
| 2 | `sparkles` | `Like received` | `Premium` | `Don't miss your chance to meet — we surface it the second it arrives.` |
| 3 | `message-circle` | `Meeting details change` | — | `Get notified if your date asks — e.g. meet time change, running late.` |
| 4 | `clock` | `Date reminder` | — | `A heads-up an hour out — never arrive late.` |
| 5 | `x` | `Date cancelled` | — | `Never waste time waiting — get notified, look for someone else instead.` |

CTA: `Enable notifications`

**Five rows, not six.** An earlier draft had a sixth; it was cut. Do not re-add one, and do not re-order — row 1 is the reason the user is in the flow at all and row 5 is the one that protects their time.

Em dashes are spaced em dashes, not hyphens. `e.g.` is lower case with both points.

Product terms capitalise exactly: **Show Up** · **Show-up Rate** · **Instant Mode** · **Match Mate** · **Check-In**. `Premium` is the tag's own label, always capitalised, always that one word.

## Behaviour

- **The screen does nothing on arrival.** No animation, no auto-advance, no timer, and it never raises the OS sheet by itself. The sheet appears **only** on the tap.
- **The CTA is the only interactive element.** Press feedback only, no hover: scale to 0.98 on press-down, `transform 180ms cubic-bezier(.22,1,.36,1)`.
- **The CTA is never disabled**, and it is **not tappable twice** — disable input for the lifetime of the sheet, then navigate. A second request no-ops silently and a double tap must not look like a hang.
- **Both outcomes advance.** Granted → Stay reachable. Denied → Stay reachable. The user never lands back on this screen.
- **Nothing is written to the profile here.** The screen updates the saved flow position only.

**The one stuck state, and the three lines that prevent it.** If the app dies while the OS sheet is up — the user answers and the OS reclaims the backgrounded app, or they swipe it away, or memory pressure kills it — the saved position still says screen 09 while the permission is now answered. iOS shows that dialog **once per install**, so on relaunch the CTA would raise nothing, and this screen has no skip, no back and no close. It is the only hard stuck state in the profile flow. Build all three:

1. **Advance the saved flow position when the sheet is raised, not when it returns.** The tap is the commitment; the answer is the OS's business. A kill mid-sheet then relaunches onto Stay reachable whatever happened, with no status read involved.
2. **The status guard before the screen is pushed** (the matrix above) — the general rule, and what catches a position written by an older build or restored from a backup.
3. **Re-read on foreground while the screen is mounted**, and if the status has become determined, **advance silently to Stay reachable** — do not leave a mounted screen with a dead button. This is the user who backgrounds the sheet, turns notifications on in Settings by hand, and comes back.

**Screen 09 is never a terminal state.** If it is ever reached or left mounted with a determined status, it advances immediately rather than waiting for a tap.

**The cost of (1), and why it is accepted:** a user killed mid-sheet *without answering* lands on Stay reachable never having been asked, and the OS status is still *not determined*. They are not lost — **Stay reachable's notifications row raises the same sheet**, because the permission was never requested. That screen is the recovery for both this case and a denial, which is the reason it is the recovery and this screen is not.
- **The `Premium` tag is a label, not an upsell.** It is not tappable, opens nothing, and has no paywall behind it on this screen.
- **Backwards navigation is blocked** — no chevron, no iOS swipe-back, no Android hardware or gesture back, exactly as on screens 01 and 05.
- The backdrop layers are decorative: `pointer-events: none`, and not announced to assistive technology.
- Nothing on the screen is a link. No legal copy, no "learn more".

**Accessibility:** each row is one group — title and line read together, with the icon `aria-hidden`. The `Premium` tag reads as part of row 2's title, not as a separate control. The CTA's accessible name is its visible label.

## Build inventory — the pixel-less work this screen gates

A path whose item is not done ships **hidden**, not disabled.

- **A notification-permission status reader** — not determined / granted / denied / restricted, on both platforms, re-read on foreground. Cheap, and it is what makes the two skip cases safe. **The API-level branch (skip below API 33) is the one that actually fires in production.**
- **The Android 13+ `POST_NOTIFICATIONS` request**, and the API-level branch that skips the screen below 33.
- **Notifications treated as on below API 33** — the Android ≤ 12 path never sees this screen and must still register for push and receive all five categories.
- **Push registration on grant** — APNs / FCM token acquisition and upload. Granting the permission and never registering the device is a silent failure that looks exactly like success on this screen.
- **The five notification categories as real templates** — the rows are a promise. `template_id` already exists on `notification_sent`; these five have to map to it.
- The saved flow position (shared by the flow).

## Acceptance criteria

- [ ] **There is no `AppHeader` on this screen** — no title, no back chevron, no skip, no close, and no reserved 36 slots
- [ ] **There is no `StepProgress` on this screen** — it appears in neither progress bar
- [ ] **There is no way backwards out of this screen:** no chevron, no iOS swipe-back, no Android hardware or gesture back
- [ ] Headline block top padding is `40`, body block top padding is `20`, gutter is `28` on both
- [ ] Headline is Lora 700 / **32** / 1.1 / −0.015em in `--liq-fg`, `text-wrap: balance`, reading `Never miss a date with Notifications!`
- [ ] `a date` is the single italic em carrying the orange radial wash via the shared `.su-underlined em` rule — not re-derived per screen, and it never breaks across lines
- [ ] Lead paragraph is Manrope 500 / **15** / 1.55 in `--liq-neutral-200`, `text-wrap: pretty`
- [ ] **Five rows, in the order and with the strings above**, list `gap: 16`, `margin-top: 22` from the lead paragraph
- [ ] Each row is `display: flex`, `gap: 14`, `align-items: flex-start`, with a **40 × 40 pip at radius 12** in `--su-grad-lilac`, icon `currentColor` in `--liq-primary-500` at size 20 / stroke 1.8 — the same pip recipe as the media slot cards, not a new one
- [ ] Row title is Lora **700** / 16 / 1.2 / −0.005em in `--liq-fg`; row line is Manrope 500 / **13.5** / 1.4 in `--liq-neutral-200`, `margin-top: 3`, `text-wrap: pretty`
- [ ] The `Premium` tag is a sunset-gradient pill — `padding: 3px 10px`, radius 9999, `--su-grad-sunset`, white Manrope **800** / 10, uppercase, letter-spacing 0.06, nudged `translateY(-1px)` — on row 2 only, **not tappable**
- [ ] The ambient backdrop is three decorative `pointer-events: none` layers at `zIndex 0` with the exact gradients, sizes and offsets in the reference file, running **full-bleed behind the status bar and home indicator**
- [ ] CTA is a **full-width sunset** `Button` (`variant="sunset" size="lg" fullWidth`): h56, radius 9999, `--su-grad-sunset`, `--liq-shadow-violet`, label `Enable notifications` in Manrope 700 / 16 white, **with no trailing icon and no `→` character**
- [ ] The CTA wrapper's `margin-bottom` is `18`, and **a single `flex: 1` spacer** (`min-height: 12`) is the only flexible element on the screen
- [ ] No absolute Y positioning anywhere in the content column
- [ ] **The status is read before the screen is pushed, not after it mounts** — on Android ≤ 12, and on the guard case of an already-determined status, the screen is skipped **silently**: no flash, no toast, straight to Stay reachable
- [ ] **The full status → behaviour matrix above is implemented**, and the status is **re-read on foreground** — a user who backgrounds mid-sheet and returns must not be looking at an ask that can no longer raise a dialog
- [ ] **Tapping the CTA raises the OS sheet and nothing else** — no in-app dialog, no look-alike sheet, no pre-sheet confirmation
- [ ] **Both outcomes advance to Stay reachable**, and the CTA cannot be tapped twice while the sheet is up
- [ ] **The saved flow position is advanced when the sheet is raised, not when it returns** — killing the app mid-sheet and relaunching lands on Stay reachable, never back on this screen
- [ ] **The screen advances by itself if the status becomes determined while it is mounted** (foreground re-read), so a dead CTA is unreachable
- [ ] **Explicitly tested:** tap `Enable notifications`, answer the sheet, force-kill the app before it navigates, relaunch — the user lands on Stay reachable. Repeat force-killing **without** answering: the user lands on Stay reachable and the notifications row there can still raise the sheet
- [ ] **On grant, the device registers for push** (APNs / FCM) and the token reaches the backend — verified, not assumed
- [ ] **There is no `Open Settings` path on this screen** in any state; recovery from a denial is Stay reachable's job
- [ ] Copy matches the strings above exactly, including the em dashes and `e.g.`
- [ ] **Evidence of done:** attach screenshots at **375 × 667, 390 × 844 and 430 × 932** — three images. Each must show all five rows, the lead paragraph, the headline and the CTA fully visible, nothing clipped or truncated, and **no scroll**. On 375 × 667, state what the spacer collapsed to. Plus one screenshot of the OS sheet as raised on each platform (to prove it is the platform's own, unstyled), and a screen recording or log line showing a denied user landing on Stay reachable.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **G — Permissions & Consent**, plus `screen_viewed` in family A). Use them exactly — do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_notifications"`, `screen_name: "ProfileNotifications"`, `referrer_screen_id` |
| Our pre-permission surface shown | `permission_prompted` | `type: "notifications"` |
| OS sheet raised (on the tap) | `permission_os_sheet_shown` | `type: "notifications"` |
| The user answers the sheet | `permission_result` | `type: "notifications"`, `result: "granted" \| "denied"` |
| The status changed outside our app and the reconciler noticed | `permission_status_changed` | `type: "notifications"`, `from`, `to` (`enums.json` §24), `detected_on`, `in_flow: true` here — **NEW, added to `events.json` 21 Sep 2026** |

The `screen_id` / `screen_name` pair comes from the registry row for this screen in `tracking/enums.json` §11 — **added 21 Sep 2026, before this ticket was written**. `type` is the closed set on `permission_prompted`; `permission_os_sheet_shown.type` is typed `str` in the registry and takes the **same value**, `notifications`.

`permission_result.result` is typed `granted | denied | limited` for the whole family. **`limited` cannot occur for notifications** — do not map a provisional or partial state onto it.

**Events that must NOT fire here.**

- **`profile_step_viewed` / `profile_step_completed` / `profile_step_skipped`** — this screen is not a step. `enums.json` §2 does hold a `notifications` `step_id`, and that is **not** a licence to fire one: its `step_index` is `—`, `profile_step_viewed` requires an `int`, and the id exists so that a future skip has a stable value. A phantom step here is invisible until someone reads the completion funnel. Recorded in §2's note, 21 Sep 2026.
- **`permission_denied_recovery_shown`** — there is no recovery surface on this screen. It belongs to Stay reachable (10).
- **`permission_settings_opened`** — there is no Settings path on this screen.
- **`consent_changed`** — the OS grant is not our consent record. `channel: "push"` belongs to the Stay reachable toggles (`surface: "profile_creation"`), and firing it here would double-count the same consent from two surfaces.

`permission_status_changed` is emitted by the **shared reconciler**, not by this screen, but it can fire while this screen is mounted — that is the foreground re-read that makes the screen advance itself. It never fires for the answer to our own sheet; that is `permission_result`, and the two must not both fire for one act.

**Missing from the current build:** every event in this table is `implemented: false`. `permission_prompted`, `permission_os_sheet_shown` and `permission_result` are all `backend_status: "Client only"` and **none of them ship today** — this screen is the first surface that needs them, so they are part of this ticket, not a follow-up.

**What is deliberately not measured:** the users who never see this screen because the status was already determined. There is no *ask skipped* event, and one must not be invented at the call site. If that rate is wanted — and it is worth wanting, because it is the denominator of the whole ask — it gets **defined in `events.json` first** and this ticket is updated.

## What this ticket requires of Stay reachable (10) — not built here

This ticket specifies **screen 09 only**: one screen, one CTA, one OS sheet. It does, however, place three requirements on screen 10, and they belong in **10's ticket** rather than being inherited from this paragraph. Carry them over when it is written:

- **10's notifications row must be able to raise the OS sheet itself**, when the status is still *not determined* — the never-answered case this ticket hands it. When the status is *denied*, the same row deep-links to Settings instead. Two behaviours, never one label (the standing permission rule).
- **Our consent and the OS permission are two different things, and 10 holds both.** The six toggles are our consent record (`consent_changed`, `channel: "push"`, `surface: "profile_creation"`); the OS grant is not a consent and fires no `consent_changed` here. A push toggle switched **on** while the OS permission is **off** is a consent we cannot honour — 10 decides what that row looks like, and it is the reason 10 must read the OS status at all.
- **10 is reached on every path out of 09** — granted, denied, never asked, and the two skip cases. It can assume nothing about the permission state and must read it.
- **10 does not exit optimistically.** Consent is committed to the server and confirmed before the flow position advances, so a user who is past 10 is guaranteed to have a consent record that exists. A crash mid-10 resumes onto 10 by the flow's ordinary resume rule — that is the guarantee, and it needs no new mechanism.

The three-fact rule behind all of this — OS permission, our consent, a registered push token — is in the **flow README and the epic** as a flow-level rule, not only here.

## Out of scope

Stay reachable (10) and its six consent toggles — including everything above, which is stated as a requirement on that screen and is **not designed, specified or built in this ticket** — the phone-book concierge card (11), the location ask (12), the notification **templates** themselves and their copy, in-app notification settings, quiet hours, per-category opt-out, any push that is not one of the five named categories, and any experiment on the ask's position in the flow.

## Dependencies

- **Media (08)** — the only origin, via Continue or Skip
- **Stay reachable (10)** — the only destination, on every outcome, **and the recovery for both a denial and a never-answered sheet**. Its notifications row must be able to raise the OS sheet when the status is still *not determined*, and deep-link to Settings when it is not
- **The notification-permission status reader**, re-read on foreground — the screen cannot decide whether to exist without it
- **Push registration** (APNs / FCM) and a backend that accepts the token
- **The saved flow position** — shared by the flow
- **The ambient-backdrop component** — Startup, screen 05, the sibling bridge and this screen are the fourth use. If it is still three pasted gradients, extract it now
- Lora + Manrope in the app font set, and `.su-underlined em` as a shared rule

## Open (not blocking)

- **`Notifications` is capitalised mid-sentence in the headline, and it is not a product term.** The capitalisation rule names five terms and this is not one of them. It reads as a deliberate nod to the OS sheet the user is about to see, so it is kept for now — but if the voice guidelines tighten, the fix is in the kit first, then the sheet, then this ticket. Same question for the two exclamation marks (headline and lead); they are the only two in the profile flow.
- **The ask has no decline of its own.** The OS sheet's "Don't allow" is the only way past, which is fine on iOS but means the user cannot leave the screen without summoning a system dialog. A `Not now` would cost a row of vertical space we do not have at 375 × 667 — the trade is deliberate, and worth revisiting only if the grant rate on 375-class devices is materially lower.
- **Five two-line rows is the tightest content in the flow at 375 × 667.** If it overflows on a real device, the agreed order of sacrifice is: spacer → list `gap` 16 → 14 → row line to 13 → **then** come back and cut a row. Never shrink the headline and never let it scroll.
- **Row 2 names Premium inside profile creation.** It is the first and only mention before the user has a profile. Correct as information, but it is also the flow's only upsell adjacency — watch it if the paywall moves earlier.
- **The ask sits at the end of the flow, after the user's most expensive step (media).** Worth an experiment once the events above exist: here vs. immediately after the first match. Measured on grant rate **and** on 30-day retained-grant, not on tap-through.
