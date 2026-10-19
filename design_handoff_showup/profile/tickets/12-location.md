# [Profile 12] Location — permission ask

**Parent:** Profile creation flow (SHOWUP-TBD — epic `profile/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** the handoff folder as a **zip** · `profile-12-location-spec-sheet.png`

## Description

The location permission ask. It sits after Stay reachable (10) and before Embrace 2, and it is the last permission profile creation asks for.

**It exists because the product does not work without location.** Search and Instant show people nearby, the venue is picked halfway between two people, and the date day routes the user to the door. So the app asks once, in context, with the three uses on screen, **before** the OS dialog is raised.

**Location can be deferred here.** Sign-up does not block on it: from a recovery state the user can carry on, and the app-wide location gate asks again when Search or Instant opens. Search, Instant and active dates do block on it — that is the gate's ticket, not this one.

**Three states, one screen** (one ticket and one sheet, per the multi-state rule):

- **A · Ask** — status *not determined*, Location switched on. One CTA: `Allow location access`.
- **B · Denied** — after the OS "Don't Allow", or arrived denied / restricted. `Open Settings` + `Not now — ask me when I search`.
- **C · Phone's Location switch off** — device-wide, any permission status. Same layout and buttons as B; only the copy differs.

Entered from Stay reachable (10) on Save preferences. **Exits:**

- **Granted** (any precision, incl. iOS *Allow Once* / Android *Only this time*) → **Embrace 2**
- **Denied** on the OS dialog → **B, in place** (same screen, no navigation)
- **`Not now — ask me when I search`** (B, C) → **Embrace 2**, without location
- **`Open Settings`** (B, C) → Settings; back in the app the status is re-read and a grant advances silently to Embrace 2

## Decided before build

- **Tapping `Allow location access` triggers the OS location dialog** — decided 5 Oct 2026. Nothing else on A is tappable, and the screen never raises the dialog by itself.
- **While-in-use only.** We never ask for background ("Always") location.
- **Precise and approximate are both accepted.** There is no approximate state, no approximate button, and no second ask for precision.
- **One denied state on Android**, not two. `Open Settings` goes to the closest page possible (see the platform table).
- **The saved flow position advances after the user answers**, not when the dialog is raised (the opposite of 09's old rule, deliberately). A kill mid-dialog relaunches onto 12, and the arrival matrix makes that safe: not answered → A with a working button · granted → skipped · denied → B.
- **iOS and Android share one screen.** Only one sentence of copy (B, C) and the Settings destination differ.
- **The ambient backdrop and the full-width sunset CTA** are the same named exceptions screen 09 carries (callouts ① and ⑧, violet). The flow README's rules 5 and 7 now read *the two bridges, the notifications ask and the location ask*. Do not carry either into any other screen.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** StatusBar (54) → radar block (A only, `flex: none`) → headline block (`flex: none`) → body block (`flex: 1`, column, `min-height: 0`) → HomeIndicator (28).

**One `flex: 1` spacer, not two** (`min-height: 12`), between the last content block and the footer. **The CTA sits at the same Y in all three states** — B and C drop the radar, rows and privacy note, and the spacer takes up the difference. Do not build B or C as a separate screen.

**Fixed gaps:** gutter 28 · A: radar wrapper padding-top 16, headline padding-top 12, rows mt 22 / gap 14, privacy note mt 22 · B, C: headline padding-top 56, body padding-top 16 · footer margin-bottom 6, button gap 10.

⚠ **It does not scroll.** At 375 × 667, A is the tight one. If it overflows on a real device the agreed order of sacrifice is: spacer → radar block 160 → 120 → row gap 14 → 10 → **then** come back to this ticket. Never shrink the headline and never let it scroll.

**The only absolutely-positioned elements are the three backdrop layers.** Stacking: backdrop 0 · content 1 · status bar and home indicator 2.

📎 All specs are on the attached **profile-12-location-spec-sheet.png** — three states side by side, keyed ①–⑭: black for ours, violet for the two named exceptions and the OS-owned boundary, red for the recovery states. The sheet also carries the arrival matrix and the iOS / Android table. The design system has colour and type tokens only — the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/profile/screen-location-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. State props: **A** `<ScreenProfileLocation state="default"/>` · **B** `state="denied"` · **C** `state="services-off"`, each with `platform="ios" | "android"`. **Do not build** `state="requesting"` / `SystemSheetMock` (a design-review mock of the OS dialog), `onApprox`, or the unused `line` field on `LOCATION_BENEFITS`.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **The OS location dialog** — iOS `CLLocationManager.requestWhenInUseAuthorization`, Android `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` requested together. Its copy, buttons, button order, precision toggle, map preview and animation are the platform's. **Never spec a height, never draw it, never build a look-alike.** The kit's "Location · system sheet up" frame is a design-review mock and is not to be built.
- **One string inside it is ours, on iOS only:** `NSLocationWhenInUseUsageDescription` (copy below). Android's dialog shows no developer text — there is nothing to write for it.
- **The Settings app.** We choose which page opens; we do not choose what it shows.
- **The status bar and home indicator** are stylized mocks. Ship the platform chrome and anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.

**Our surface stops at the CTA.**

## Status on arrival — decided before the screen is pushed

| Status | Shows | Notes |
|---|---|---|
| **Location switched off for the whole phone** — any permission status | **C** | Checked **first**: with the switch off the OS dialog cannot help. iOS `CLLocationManager.locationServicesEnabled()`, Android `LocationManager.isLocationEnabled()` |
| **Not determined**, Location on — fresh install | **A** | The normal path. Also the relaunch after a kill mid-dialog without an answer |
| **Granted** — restore from backup, or answered then killed | Nothing — **skipped silently** | Straight to Embrace 2. No flash, no toast |
| **Denied / restricted** — restore, or answered "Don't Allow" then killed | **B** | A designed state with two working buttons, not a guard. `restricted` is treated as `denied` |

**Re-read on every foreground while mounted** (the shared permission reconciler): Location turned on in C → A (or skip if already granted) · permission granted in B or C → advance silently to Embrace 2. The classic bug is a user who granted access in Settings returning to the denied screen.

## iOS and Android — one screen, two differences

| | iOS | Android |
|---|---|---|
| **B** sentence | `Turn it on in Settings › Show Up › Location, or finish your profile first and we'll ask again when you start searching.` | `Turn it on in Permissions › Location, or finish your profile first and we'll ask again when you start searching.` |
| **B** Open Settings | `UIApplication.openSettingsURLString` — our app page | `ACTION_APPLICATION_DETAILS_SETTINGS` — our app page |
| **C** sentence | names `Settings › Privacy & Security › Location Services` | the path sentence is dropped |
| **C** Open Settings | our app page — iOS cannot open Location Services directly | `ACTION_LOCATION_SOURCE_SETTINGS` — the system Location page |

## Copy — final strings

**A · Ask**
Headline: `Find people nearby.` — em on `nearby`
Rows, in this order:

| # | Icon | Title | Sub |
|---|---|---|---|
| 1 | `compass` | `Set a search radius around your location` | — |
| 2 | `map-pin` | `We pick a fair halfway venue for you both` | `No planning, no home advantage — a busy public spot that's neutral ground for both of you.` |
| 3 | `navigation` | `Walking directions on the day` | — |

Privacy note: `Your exact location is never shown on your profile. Other people only see the city you're in and an approximate distance from themselves e.g. 1.5km.`
CTA: `Allow location access`

**iOS purpose string** (`NSLocationWhenInUseUsageDescription`, in `Info.plist`): `We use your location to find people nearby and choose a halfway meeting spot for your date.`

**B · Denied**
Headline: `We can't find dates without location.` — em on `without`
Body: `Location access is off for Show Up. Without it we can't show you anyone nearby — every date happens in the real world.` + the platform sentence above (path in bold)
CTAs: `Open Settings` · `Not now — ask me when I search`

**C · Phone's Location switch off**
Headline: `Your phone has location switched off.` — em on `switched off`
Body, iOS: `Location Services is off for every app on this phone, not just Show Up. Turn it on in Settings › Privacy & Security › Location Services, or finish your profile first and we'll ask again when you start searching.`
Body, Android: `Location is off for every app on this phone, not just Show Up. Turn it on in Settings now, or finish your profile first and we'll ask again when you start searching.`
CTAs: as B

Em dashes are spaced em dashes. `›` is the single right-pointing angle quote, not `>`. Product terms capitalise exactly: **Show Up** · **Show-up Rate** · **Instant Mode** · **Match Mate** · **Check-In**.

## Behaviour

- **The screen does nothing on arrival.** No animation, no timer, no auto-raise. The radar is static.
- **A:** `Allow location access` raises the OS dialog **and nothing else**. Input is locked while it is up — the CTA is **never disabled** and **not tappable twice**. Granted → save position → Embrace 2. Denied → save position → **B in place**.
- **B, C:** `Open Settings` opens the page in the platform table and **stays on this screen**. `Not now — ask me when I search` saves the position and goes to Embrace 2 with no location.
- **A has no skip, close or "Not now".** The OS dialog's own decline is the way past, and it leads to B, which has one.
- **Nothing is written to the profile here.** No location is uploaded by this screen; the first fix belongs to the consumer that needs it (Search, venue pick).
- **Backwards navigation is blocked** — no chevron, no iOS swipe-back, no Android back, as on 09 and 10.
- Press feedback only on both buttons: scale 0.98, `transform 180ms cubic-bezier(.22,1,.36,1)`.
- Backdrop layers and the radar are decorative: `pointer-events: none`, `aria-hidden`.

**Accessibility:** each benefit row is one group (title + sub), icon `aria-hidden`. The privacy note is static text. Button accessible names are their visible labels. The bold Settings path is read inline, not as a link.

## Build inventory — the pixel-less work this screen gates

A path whose item is not done ships **hidden**, not disabled.

- **A location-status reader** on both platforms — permission (§24: not_determined / granted / denied / restricted) **and** the device-wide Location switch — read before push and on every foreground. The device switch is not a permission status; it is the reason C exists.
- **The iOS purpose string** in `Info.plist`. Without it iOS crashes on the request — this is the one item that cannot ship hidden.
- **Android manifest:** `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`, both requested in one call (Android 12+ requires coarse alongside fine).
- **The two Settings intents per platform** in the table above.
- The saved flow position (shared by the flow) — advanced **after** the answer.

## Acceptance criteria

- [ ] **There is no `AppHeader` and no `StepProgress` on this screen**, and no way backwards out of it
- [ ] Three states render from **one component** with a `state` prop; **the CTA is at the same Y in A, B and C** on every device size
- [ ] **Tapping `Allow location access` raises the OS location dialog and nothing else** — no in-app dialog, no look-alike, no auto-raise on arrival
- [ ] The CTA cannot be tapped twice while the dialog is up
- [ ] Granted at **any precision**, including iOS *Allow Once* and Android *Only this time*, advances to Embrace 2; denied shows **B without navigating**
- [ ] Only while-in-use location is requested — **no background / "Always" request** anywhere in this flow
- [ ] **The arrival matrix is implemented before push**: Location switch off → C · not determined → A · granted → skipped silently (no flash, no toast) · denied / restricted → B
- [ ] **Re-read on foreground:** granting in Settings from B or C advances silently to Embrace 2; turning the phone's Location on from C shows A
- [ ] The saved flow position advances **after** the answer. Explicitly tested: tap Allow, force-kill before answering, relaunch → A with a working button. Answer Deny, force-kill, relaunch → B
- [ ] `Open Settings` opens the page in the platform table for each state and platform, and leaves the user on this screen
- [ ] `Not now — ask me when I search` advances to Embrace 2 without location, and **A has no equivalent**
- [ ] B and C carry **one platform sentence** each, per the table — nothing else differs between iOS and Android
- [ ] `NSLocationWhenInUseUsageDescription` reads exactly as above
- [ ] Headline Lora 700 / 32 / 1.1 / −0.015em, `text-wrap: balance`, one em via the shared `.su-underlined em` rule
- [ ] Benefit row pips are 40 × 40, radius 12, `--su-grad-lilac`; titles Lora 700 / 17 / 1.25 — values from the reference file, rows in the order above
- [ ] Ambient backdrop and full-width sunset CTA are the 09 recipe, not a re-derivation
- [ ] **A single `flex: 1` spacer** (`min-height: 12`) is the only flexible element; no absolute Y positioning in the content column
- [ ] Copy matches the strings above exactly, including em dashes and `›`
- [ ] **Evidence of done:** screenshots of **A, B and C** at **375 × 667, 390 × 844 and 430 × 932**, on iOS and on Android for B and C — all content visible, nothing clipped, no scroll, both CTAs fully visible. On 375 × 667, state what the spacer collapsed to in A. Plus a screen recording of A → OS dialog → Don't Allow → B → Open Settings → grant → back → Embrace 2

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **G — Permissions & Consent**, `screen_viewed` in family A, `profile_step_skipped` in family E). Use them exactly — do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview — once per mount, not per state | `screen_viewed` | `screen_id: "profile_location"`, `screen_name: "ProfileLocation"`, `referrer_screen_id` |
| A shown | `permission_prompted` | `type: "location"` — **A only**, never on B or C |
| `Allow location access` tapped, dialog raised | `permission_os_sheet_shown` | `type: "location"` |
| Dialog answered | `permission_result` | `type: "location"`, `result: "granted" \| "denied"` — approximate and Allow Once are `granted`; **`limited` never applies** |
| B or C shown | `permission_denied_recovery_shown` | `type: "location"`, `reason` (`enums.json` §26: `denied` \| `services_off`) — **NEW payload, 5 Oct 2026** |
| `Open Settings` tapped | `permission_settings_opened` | `type: "location"` |
| `Not now — ask me when I search` tapped | `profile_step_skipped` | `step_id: "location"` (§2), `screen_id: "profile_location"` |
| Status changed outside the app, noticed on foreground | `permission_status_changed` | `type: "location"`, `from`, `to` (§24), `detected_on`, `in_flow: true` — emitted by the **shared reconciler**, not this screen |

The `screen_id` / `screen_name` pair is the §11 registry row **added 5 Oct 2026, before this ticket was written**. §26 `location_unavailable_reason` is **new in registry 1.4.7** (taxonomy 1.4.6).

**Events that must NOT fire here.**

- **`profile_step_viewed` / `profile_step_completed`** — this screen is not a step. The §2 `location` id exists **only** for `profile_step_skipped`.
- **`location_gate_shown`** — that is the app-wide gate on Search / Instant, a different screen. Sharing §26 does not make them one surface.
- **`consent_changed`** — the OS grant is not our consent record.
- **`permission_result` for a grant noticed on foreground** — that is `permission_status_changed`. The two never both fire for one act.

**Missing from the current build:** every event in this table is `implemented: false`. `reason` on `permission_denied_recovery_shown` is **missing from the current build** and required when `type` is `location`.

**Not measured, deliberately:** precise vs approximate. `properties.json` `location_permission` does not match §24 yet and has no precision value — do not send precision until that is fixed in the registry first.

## Out of scope

The app-wide location gate on Search and Instant (header + message + tab bar redesign, its own ticket), location asks on active dates (Likes-back, Order Ride, Directions), the first location fix and its upload, the radar animation, Embrace 2 itself, and background location of any kind.

## Dependencies

- **Stay reachable (10)** — the only origin
- **Embrace 2** — the only destination, on every outcome except a denial (B)
- **The shared permission reconciler**, extended to read the device-wide Location switch
- **The app-wide location gate** — `Not now` relies on it to ask again; until it ships, a deferred user has no location on Search. Ship them in the same release, or hide `Not now`
- **The ambient-backdrop component** (05, 09, 10, this screen) and `.su-underlined em`

## Open (not blocking)

- **Android first denial can still re-prompt in-app**, which the 9 Sep 2026 permission rule says should not need a Settings trip. Decision 5 keeps one denied state with `Open Settings` for simplicity. Worth revisiting if Android Settings round-trips convert badly.
- **The privacy note differs from the gate's**: this screen says "approximate distance e.g. 1.5km", the gate does not. To be settled when the gate is ticketed; this screen's string is final as written.
- **Only row 2 has a sub-line.** Correct as drawn; if rows 1 and 3 gain one, A at 375 × 667 needs re-checking first.
- **Radar component is duplicated** between this screen and the gate in the kit. Extract one when the gate is built — do not build two.
