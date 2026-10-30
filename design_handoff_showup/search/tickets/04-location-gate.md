# [Search 04] Location gate — Search without location

**Parent:** [Epic] Date search (`search/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `search-04-location-gate-attachment.zip` (the handoff folder, `design_handoff_showup/`) · `search-04-location-gate-spec-sheet.png`
**Blocked by:** **Profile 12** (built — `ScreenProfileLocation`, the radar, the privacy note, the location-status reader, the OS request, the Settings intents, the iOS purpose string), **Search 01** (`SearchTopBar` `filter` prop, the Search tab), **Search 03** (`SearchTopBar` `chip` / `premium` props, the Likes tab, the profile opened from Likes), **Check-In 01** (the no-Check-In screen, which wins over this one).

## Description

Search shows people near the user, so it can't run without location. When the user **can search** (committed Check-In, no active date) and location is unavailable, **the Search tab shows this screen instead of people**. It explains why and offers one button. The user can still reach **Likes** and **Settings**; the TabBar stays.

**It is Profile 12's location screen in a second context** (`context="app"`), not a new screen. Profile 12 asks once, during sign-up. This screen covers everything after: the user tapped `Not now` there, turned location off later in Settings, an iOS `Allow Once` expired, or turned Location off for the whole phone.

**Three states, one per reason** (`enums.json` §26):

- **A · Never granted** (`not_determined`) — `Allow location access` raises the OS dialog.
- **B · Off for Show Up** (`denied`, incl. restricted) — `Open Settings`.
- **C · Off on the phone** (`services_off`) — `Open Settings`.

**And one entry from Likes:** **D** — a Premium user taps `Confirm date` on a profile opened from the Likes tab while location is unavailable. The gate is pushed over that profile, in whichever of A–C applies, with a back pill. Browsing Likes needs no location; confirming a date does.

Four frames on the sheet: **A** never granted · **B** off for Show Up · **C** off on the phone · **D** from Likes, Confirm date (B shown).

Entered from:

- The **Search** tab (any route to it: app open, TabBar, `Back to search`) when the user can search and location is unavailable.
- **`Confirm date`** on a profile opened from the Likes tab (D).

Exits:

- Location usable (granted on the OS dialog, or re-read on return from Settings) → **Search loads in place** (`search_profile` for Freemium, `search_grid` for Premium). D → **back to the profile**.
- **Likes** / **Settings** via the TabBar. D: the back pill → the profile.

## Decided before build

Decided 22 Oct 2026 (`design_handoff_showup/location/PARKED-location-handoff.md` → *Update 22 Oct 2026*). The kit matches every point below.

- **One component, two contexts** (①). `ScreenProfileLocation` gains `context: "profile" | "app"`; `ScreenLocationGate` is a thin wrapper around `context="app"`. Shared: the radar (③), the privacy note (⑥), the status reader, the OS request, the Settings intents. **Profile 12 is unchanged** — its copy, layout and routing stay as built.
- **No platform-specific copy on the gate** (⑤). The text never names a Settings path; it says `Open Settings` and **the button routes per platform** (table below). Profile 12 keeps its per-platform sentences — it is built.
- **iOS, phone-wide off (C):** `Open Settings` can only open our app's page, not the Location Services switch. **Accepted**; the copy stays platform-neutral.
- **Order on the Search tab:** an active date never reaches Search (07a blocks it) · **no committed Check-In → Check-In 01's screen wins** · otherwise this gate. The 24-hour no-show lockout is **not decided yet** and is not in this order.
- **No filter button** (②). The top bar is the wordmark and the likes chip. The user's only job here is turning location on.
- **Likes stays usable** (⑩). The list and profiles open without location. **`Confirm date` raises the gate** (D). After location is on, the user is back on the profile and **taps `Confirm date` again** — it is never sent automatically.
- **One button per state, no second one** (⑧). No `Not now` (that belongs to sign-up), no "I just enabled it" link: the app **re-reads location on every foreground** and the gate lifts by itself (⑫).
- **Headline 30**, the Search group's size (same as no-results). Profile 12 stays 32 — a different group.
- **Instant** shows the same gate with the Instant header. **Instant is not built**, so it is not in this ticket.

## Layout — read this before building

⚠ **Never hard-code Y positions.** One flex column, one spacer. The CTA sits directly above the TabBar in every state.

**Screen:** StatusBar → `SearchTopBar` (②) → [back pill row, D only (⑪)] → content column (`flex: 1`, min-height 0, padding `4 24 0`, text centred) → TabBar (⑩) → home indicator. **No scroll.**

**Content column:** radar (③, h 170; D 140) → headline (④, mt 10) → body (⑤, mt 12) → [privacy note (⑥), A only, mt 18] → **one `flex: 1` spacer** (⑦, min-height 16) → footer (⑧, pb 22).

**Backdrop** (①): Profile 12's three layers, `pointer-events: none`, zIndex 0. **A** at Profile 12's strength; **B, C, D dialled back** (orbs .16 / .14, wash .28).

**At 375 × 667**, A and D are the tight ones. Order of sacrifice: spacer → radar 170 → 130 (D 140 → 110) → privacy-note margin 18 → 12. Never shrink the headline, never let it scroll.

**Stacking:** backdrop 0 · content 1 · top bar, back pill, TabBar 2.

📎 All specs are on the attached **search-04-location-gate-spec-sheet.png** — four frames, callouts ①–⑫: black for ours, violet for the named exceptions and the OS-owned boundary, red for the blocked states. The design system has colour and type tokens only and no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/search/screen-location-gate-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. States: `<ScreenLocationGate state="ask"/>` (A) · `state="denied"` (B) · `state="services-off"` (C) · `state="denied" tab="likes" onBack` (D). **Builds** the `context="app"` branch of `ScreenProfileLocation` (`tab`, `onBack`, `backLabel`, `onTab`), `ScreenLocationGate`, the `live={false}` variant of `LocationRadarHero`, the `LOCATION_COPY.app` strings. **Consumes** Profile 12's `ScreenProfileLocation`, `LocationRadarHero`, `LocationPrivacyNote`, status reader and Settings intents; Search 01/03's `SearchTopBar` (`filter={false}`; D: `premium`, `chip={false}`) and the shared `TabBar`. **One location screen and one radar:** a second copy of either is a bug.
> Shell components: `design_handoff_showup/components/shared.jsx` (`Phone`, `StatusBar`, `HomeIndicator`, `Icon`, `Button`, `TabBar`) · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## Before you start — check these in the Profile 12 build

1. **Is Profile 12's screen reusable?** This ticket adds `context="app"` to Profile 12's `ScreenProfileLocation`. If the built screen is one fixed screen rather than a component with props, restructure it first so both contexts share one implementation — **no visible change to Profile 12**, and Profile 12 must still pass its own AC afterwards. Estimate the refactor before starting and say if it is large.
2. **Is `Not now — ask me when I search` live?** Profile 12's ticket said: ship it with this gate in the same release, or hide it. If it is **live**, users who tapped it are reaching Search without location today — this ticket closes that gap; note it in the release. If it is **hidden**, switch it on in the same release as this ticket.

## What is not ours

- **The OS location dialog** — the same request Profile 12 makes (while-in-use; Android fine + coarse together). Its copy, buttons, precision toggle and animation are the platform's. **Not drawn, never imitated.** The iOS purpose string is Profile 12's, already shipped.
- **The Settings app.** We choose which page opens; we don't choose what it shows.
- **Status bar and home indicator** are stylized mocks. Ship the platform chrome, anchored to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.

**Our surface stops at the CTA.**

## Which state — decided before the Search tab renders

Checked in this order:

| Condition | Shows | Notes |
|---|---|---|
| Active date | — never reached | Search is blocked in date mode (07a) |
| **No committed Check-In** | Check-In 01's screen | **The Check-In wins.** Location matters only once the user can search |
| Location switched off for the whole phone | **C** | Any permission status — the OS dialog can't help |
| Permission denied or restricted | **B** | restricted is treated as denied (§24) |
| Permission not determined | **A** | `Not now` in Profile 12, a reset, or an expired iOS `Allow Once` |
| Granted, any precision | Search | No gate |

**Re-read on every foreground while mounted** (Profile 12's shared reconciler): usable → the gate lifts; still blocked for a new reason (e.g. C → B after the phone's Location is turned on) → the state swaps in place.

## Open Settings — one label, routed per platform

| State | iOS | Android |
|---|---|---|
| **B** off for Show Up | `UIApplication.openSettingsURLString` — our app page | `ACTION_APPLICATION_DETAILS_SETTINGS` — our app page |
| **C** off on the phone | our app page — iOS can't open Location Services directly (accepted) | `ACTION_LOCATION_SOURCE_SETTINGS` — the system Location page |

The same intents as Profile 12. **Only Profile 12's copy differs by platform; the gate's doesn't.**

## Copy — final strings

**A · Never granted**
Headline: `Allow location to see people nearby.` — em on `nearby`
Body: `We use your location to show you people within your distance, pick a fair halfway venue and guide you there on the day.`
Privacy note: Profile 12's string, unchanged — `Your exact location is never shown on your profile. Other people only see the city you're in and an approximate distance from themselves e.g. 1.5km.`
CTA: `Allow location access`

**B · Off for Show Up**
Headline: `Location is off for Show Up.` — em on `Show Up`
Body: `You turned off location access for Show Up, so we can't show you anyone nearby or plan a date. Open Settings and allow location — you'll pick up right where you left off.`
CTA: `Open Settings`

**C · Off on the phone**
Headline: `Location is off on your phone.` — em on `phone`
Body: `Location is switched off for every app on this phone, not just Show Up. Open Settings and turn it on — you'll pick up right where you left off.`
CTA: `Open Settings`

**D · From Likes** — the strings of whichever state applies, plus the back pill `Back`.

**Identical on iOS and Android.** No string names a Settings path.

## Behaviour

**On arrival**
- The state is decided **before the Search tab renders** (table above) — no flash of an empty Search, no flash of the wrong state.
- Nothing happens on arrival: no animation, no auto-raise of the OS dialog. The radar is static.

**A — Allow location access**
- Raises the OS dialog **and nothing else**. Input is locked while it is up; the CTA is never disabled and can't be tapped twice.
- Granted at any precision (incl. iOS `Allow Once` / Android `Only this time`) → the gate lifts and Search loads in place, no toast. D → back to the profile.
- Denied → **B, in place** (no navigation).

**B, C — Open Settings**
- Opens the page in the routing table and **stays on this screen**.
- Back in the app → foreground re-read → lifts, swaps state, or stays.

**D — from Likes**
- `Confirm date` on a profile opened from the Likes tab checks location **before** opening Be sure. Unavailable → the gate is pushed over the profile in the matching state; Be sure does not open; nothing is sent.
- The back pill, Android back and swipe-back return to the profile. Location now usable → back on the profile too; **the user taps `Confirm date` again**.
- The Likes tab stays active. Browsing the list and opening profiles never checks location.

**TabBar**
- Likes and Settings work as normal. Search shows the gate while location is unavailable.

**Backwards navigation**
- A, B, C are a tab root: no back. D: back returns to the profile.

**Accessibility**
- Headline is the screen's heading; the radar and backdrop are decorative (`aria-hidden`).
- The CTA has its visible label as its accessible name. The leading icon is decorative.
- A state swap in place (A → B, C → B) moves focus to the headline and announces it.
- Press feedback: scale 0.98, `transform 180ms cubic-bezier(.22,1,.36,1)`.

## Build inventory

- **Client:** the `context="app"` branch on Profile 12's screen (`tab`, `onBack`, `backLabel`, `onTab`) and `ScreenLocationGate`; `LocationRadarHero` `live={false}`; `LOCATION_COPY.app`. The Search tab's guard, in the order of the state table. The location check on `Confirm date` from the Likes tab (D).
- **Reused from Profile 12, not rebuilt:** the location-status reader (permission + the phone's Location switch), the foreground reconciler, the OS request, the two Settings intents per platform, the purpose string.
- **Server:** nothing new. No location is uploaded by this screen; the first fix belongs to Search.

## Acceptance criteria

- [ ] **Profile 12 build checked first** (section *Before you start*): refactor done if needed, and `Not now` is visible in the same release as this gate
- [ ] There is **one** location screen: the gate is `ScreenProfileLocation` with `context="app"`. **Profile 12 looks and behaves exactly as before** (copy, buttons, routing, events) — checked against its own AC
- [ ] With a committed Check-In and no location, the Search tab shows the gate; **with no Check-In, Check-In 01's screen shows instead**, whatever the location status
- [ ] The state follows the table: phone-wide off → **C** (checked first) · denied / restricted → **B** · not determined → **A** · granted → Search
- [ ] No frame of empty Search or of the wrong state shows before the gate
- [ ] **A:** `Allow location access` raises the OS dialog and nothing else; it can't be tapped twice while the dialog is up; granted (any precision, incl. Allow Once) loads Search in place; denied shows **B without navigating**
- [ ] **B, C:** `Open Settings` opens the page in the routing table for each state and platform and stays on this screen
- [ ] **Foreground re-read:** granting in Settings lifts the gate with no tap; turning on the phone's Location from C with the app still denied shows B
- [ ] **No string names a Settings path**, and every string is identical on iOS and Android
- [ ] The top bar has **no filter button**; the likes chip is there (A–C); D shows the Premium bar with no chip and no filter, plus the back pill `Back`
- [ ] The TabBar is live: Likes and Settings open normally from every state
- [ ] **D:** `Confirm date` on a Likes-tab profile without location opens the gate, not Be sure, and sends nothing; back returns to the profile; after granting, the user is on the profile and **the date is not confirmed until they tap `Confirm date` again**
- [ ] Browsing the Likes list and opening a profile from it never shows the gate
- [ ] There is no `Not now`, no retry link and no second button in any state
- [ ] The radar is the one `LocationRadarHero`: live in A, muted with the slash in B, C, D; no second radar component exists
- [ ] Headline Lora 700 / 30 / 1.12 / −0.015em, centred, `text-wrap: balance`, one em via `.su-underlined em`
- [ ] **A single `flex: 1` spacer** (min-height 16) is the only flexible element; no absolute Y in the content column; the CTA sits directly above the TabBar in every state
- [ ] Copy matches the strings above exactly, including em dashes and curly apostrophes
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**
- [ ] **Evidence of done:** screenshots of **A–D** at **375 × 667, 390 × 844 and 430 × 932** on iOS and Android (24 images) — all content visible, nothing clipped, no scroll, CTA and TabBar fully visible. Check A and D at 375 × 667 first. Add three screen recordings: A → Allow → granted → Search loads · B → Open Settings → allow in Settings → back → Search loads with no tap · Likes profile → Confirm date → gate (D) → Open Settings → allow → back → profile → Confirm date → Be sure

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **G — Permissions & Consent**, `screen_viewed` in family A). Use them exactly — do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview — once per mount, not per state | `screen_viewed` | `screen_id: "search_location_gate"`, `screen_name: "Search - Location gate"`, `referrer_screen_id` (D: `search_profile`) |
| A state shown — on mount, and again only if the reason changes while mounted | `location_gate_shown` | `reason` (`enums.json` §26) — **trigger respecified, 22 Oct 2026** |
| `Allow location access` tapped, dialog raised | `permission_os_sheet_shown` | `type: "location"`, `screen_id: "search_location_gate"` — **NEW optional payload, 22 Oct 2026** |
| Dialog answered | `permission_result` | `type: "location"`, `result: "granted" \| "denied"`, `screen_id: "search_location_gate"` — approximate and Allow Once are `granted`; **`limited` never applies** |
| `Open Settings` tapped | `permission_settings_opened` | `type: "location"`, `screen_id: "search_location_gate"` |
| Status changed outside the app, noticed on foreground | `permission_status_changed` | `type: "location"`, `from`, `to` (§24), `in_flow: false` — emitted by the **shared reconciler**, not this screen |
| Gate lifts, Search loads | `screen_viewed` on Search | `screen_id: "search_profile"` or `"search_grid"`, `referrer_screen_id: "search_location_gate"` |
| TabBar tap | `tab_switched` | as everywhere (§37) |

The `screen_id` / `screen_name` pair is the §11 registry row **added 22 Oct 2026, before this ticket was written** (registry 1.4.30 / taxonomy 1.4.21).

**Why `screen_id` on the permission events:** Profile 12 and the gate send the same three events. The gate adds `screen_id: "search_location_gate"`; **Profile 12 sends none, and absent means `profile_location`** — so the built Profile 12 needs no change.

**Events that must NOT fire here.**

- **`permission_prompted`** — on the gate, `location_gate_shown` with `not_determined` is that event. One event per act.
- **`permission_denied_recovery_shown`** — on the gate, `location_gate_shown` with `denied` / `services_off` is that event.
- **`profile_step_skipped`, `profile_step_viewed`, `profile_step_completed`** — this is not sign-up.
- **`permission_result` for a grant noticed on foreground** — that is `permission_status_changed`. The two never both fire for one act.
- **`location_gate_shown` on every foreground** — only on mount and on a reason change.

**Missing from the current build:** every event in this table is `implemented: false`. `screen_id` on the three permission events is **missing from the current build**.

**Not measured, deliberately:** precise vs approximate (same as Profile 12).

## Out of scope

Instant (same gate, Instant header — Instant isn't built). Location asks on an active date (Order Ride, Directions, the location-off card on date screens). The 24-hour no-show lockout and its order against this gate. The first location fix and its upload. The radar animation. Background location of any kind. Profile 12 itself.

## Dependencies

- **Profile 12 (built):** `ScreenProfileLocation`, `LocationRadarHero`, `LocationPrivacyNote`, the status reader + reconciler, the OS request, the Settings intents, the purpose string. **Extended, never forked.**
- **Search 01 / 03:** `SearchTopBar` (`filter`, `chip`, `premium`), the Search tab, the profile opened from Likes and its `Confirm date`.
- **Check-In 01:** the no-Check-In screen, checked before this one.
- **The shared `TabBar`.**
- **Tracking registry 1.4.30 / taxonomy 1.4.21.**

## Open (not blocking)

- **The 24-hour no-show lockout** — whether it exists, and if it does, whether it comes before or after this gate. Decide before the lockout is ticketed.
- **Date flow, location off on an active date** — not designed yet; belongs to the date flow's tickets.
- **iOS, phone-wide off:** if Settings round-trips from C convert badly on iOS, revisit a hint in the copy for that one state.
