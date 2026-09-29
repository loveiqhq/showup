# [Profile 10] Stay reachable — MVP

**Parent:** Profile creation flow (SHOWUP-TBD — epic `profile/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Linked:** Profile 09 Notifications (**changed by this ticket**, see *Decided before build*) · Profile 10 — later scope (`profile/tickets/10-reachability-later.md`, backlog, **not in this sprint**)
**Attachments:** the handoff folder as a **zip** · `profile-10-reachability-spec-sheet.png` (states A–D, callouts ①–⑯; violet = OS-owned or the demand test)

## ⚠ Updated 28 Sep 2026 — fit above the fold (already built → change these)

Visual only. No change to behaviour, tracking, states or any copy other than the card title.

1. **The push toggle moves into the card head. The separate push row is removed.** The head now reads: violet bell pip (40×40, radius 12, `--liq-primary-500` background, white `bell` 20, stroke 1.8) → `Recommended` pill **above** the title (margin-bottom 4, same pill as before) → title **`Push notifications`** (was `Get notifications`) with the 51×31 toggle to its right in one row (gap 10, vertically centred) → the unchanged card line underneath.
2. **MVP spacing tightened:**
   - Headline block top padding: 32 → **20**
   - Lead top margin: 14 → **10**
   - Scroll body padding, top / bottom: 20 / 8 → **14 / 4**
   - Divider above the interest block, margin-top / padding-top: 10 / 18 → **12 / 12**
   - Interest rows, vertical padding: 12 → **9** (the row stays ≥ 52pt tall)
   - Fine-print top margin: 4 → **0**
   - Footer top padding: 10 → **8**
3. **Goal:** on **393 × 852** (iPhone 15 / 15 Pro) at default text size, the whole screen from the headline to **Save preferences** is visible without scrolling, in all four states. The body still scrolls on smaller devices and at larger Dynamic Type sizes.

The reference file `screen-reachability-reference.jsx` has been updated. The attached spec-sheet PNG still shows the old push row; the reference file wins.

> **Prompt for Claude Code:** *Update Profile 10 "Stay reachable" per the "Updated 28 Sep 2026" section of this ticket. Move the push toggle into the notifications card head, remove the separate push row, rename the card title to "Push notifications" and apply the spacing values. Use `design_handoff_showup/profile/screen-reachability-reference.jsx` (scope="mvp") for the values. Do not change logic, tracking or other copy.*

## Description

The screen that decides whether we can reach the user when a match lands. **In the MVP it does two jobs, and only the first one has anything behind it:**

1. **Push notifications — the one live channel.** A toggle, **on by default**. Tapping **Save preferences** raises the OS notification dialog. Switching the toggle off opens the **deactivation confirm** dialog first.
2. **A demand test for three channels that do not exist yet** — a phone call from our AI assistant, WhatsApp, SMS. Three checkboxes that **record interest and do nothing else.** No sending, no consent record, no phone number, no dialog. We ship them to learn which channel to build next.

Entered from Notifications (09). **One exit:** Save preferences → **Location (12)**. Phone book (11) is **not in the MVP**.

**Four states:** A default (push on, nothing checked) · B interest checked · C push deactivation confirm · D the same confirm after the OS "Don't allow".

## ⚠ Changes to Profile 09 — already built, do these in this ticket

09's Jira description is **left as it was built**; these three changes are specified **here**. The repo copy `profile/tickets/09-notifications.md` is marked ⚠ superseded at every affected point, so Claude Code does not rebuild the old behaviour.

1. **Remove the OS notification request from 09.** Its CTA only navigates to Stay reachable (10). The request moves to **10's Save preferences**, as specified below, together with push registration on grant.
2. **Rename 09's CTA from `Enable notifications` to `Continue`.** Everything else about the button stays: full-width sunset, no trailing icon. The reference file `screen-notifications-reference.jsx` is updated.
3. **Stop firing `permission_prompted`, `permission_os_sheet_shown` and `permission_result` on 09.** All three move to 10. `screen_viewed` on 09 stays.

Unchanged on 09: layout, all other copy, the Android ≤ 12 / already-determined skip guard, and blocked back.

## Decided before build — 25 Sep 2026

- **The OS notification dialog is raised by this screen's Save preferences** — see the 09 changes above.
- **Push defaults ON.** It's an opt-out, not an opt-in: the user switches it off actively, through the confirm dialog.
- **Push is not forced.** Save preferences works with push off. It is the user's responsibility to show up.
- **The three interest rows are a test, not a feature.** See *Non-goals* — they are the most likely thing in this ticket to be over-built.
- **Removed from the MVP screen:** the AI concierge-call card, the add-to-calendar card, the phone-number field, email as a channel, and the "Skip for now" link. They stay in the reference file under `scope="full"` for later. The calendar will be offered at the date-confirmed moment in a later ticket, not here.
- **Screen 11 (phone book) is out of the MVP.** It only existed for the AI call.

## Layout — read this before building

📎 All specs are on the attached **profile-10-reachability-spec-sheet.png** — keyed ①–⑯, with the status-on-arrival matrix. Every value is also in the reference file, which wins on numbers.

> **Reference implementation:** `design_handoff_showup/profile/screen-reachability-reference.jsx` — **build `scope="mvp"` only.** Read the header comment first.
> State A: `<ScreenProfileReachability scope="mvp"/>` · State B: `scope="mvp" initialInterest={{ ai_call: true, whatsapp: true }}` · State C: `scope="mvp" confirmOff="notifications"` · State D: `scope="mvp" confirmOff="notifications" afterDenial`
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

⚠ **Layout is flex, not absolute.** StatusBar → headline block (`flex: none`) → **scrolling body** (`flex: 1`, `min-height: 0`, `overflow: auto`) → **pinned footer** with the CTA → HomeIndicator. Never hard-code a Y position: German copy runs about 30% longer and Dynamic Type can double the card height.

- **The body scrolls; the CTA never does.** The footer is pinned, with its fade from transparent to `--liq-bg`. The scroll content needs bottom padding at least equal to the footer, so the fine print can scroll clear of the CTA.
- **One card.** Card head (violet bell pip, `Recommended` pill, title **with the push toggle**, card line) → a divider → the interest heading and three checkbox rows. **There is no separate push row** (changed 28 Sep 2026). The card lifts to the violet tint while push is on.
- **Above the fold on 393 × 852:** the whole screen fits without scrolling at default text size (28 Sep 2026). Scrolling remains the fallback for smaller devices and larger text sizes.
- The backdrop (orange orb, violet orb, peach wash) matches 09. It is decorative and `pointer-events: none`.
- **Every row is a ≥ 44 × 44 hit target.** The whole interest row toggles its box, not only the 22px square. The switch is 51 × 31 visually, padded to 44 tall.

## What is not ours

- **The OS notification dialog** (iOS `requestAuthorization`, Android 13+ `POST_NOTIFICATIONS`). We decide **when** it appears; its copy, buttons and look belong to the OS. iOS offers no custom explanation text for notifications — the screen is the explanation. **Never draw it, restyle it or build a look-alike.**
- **The Settings app**, when we deep-link to it.
- Status bar and home indicator — ship the platform's, anchored to the safe-area insets.

## Push permission — every status, and what to do

The status is read **on arrival and on every foreground** by the shared permission reconciler (epic rule).

| Status on arrival | Push toggle shows | Save preferences does |
|---|---|---|
| **Not determined** — iOS and Android 13+. **The normal path**, because 09 no longer asks | **On** (default) | Toggle on → **raises the OS dialog**, then continues per the table below. Toggle off → no dialog, saves |
| **Granted** — Android ≤ 12 always (no runtime permission). *Guard:* restore from backup | On | Saves. No dialog |
| **Denied / restricted** — *guard only:* restore from backup | Off. Switching it on **opens Settings** (iOS) or **re-prompts** (Android, if it can still ask) | Saves with push off |

**Label a guard as a guard:** the denied row on arrival isn't a designed state — it's a guard so the toggle is never dead.

**What happens after the OS dialog:**

| Answer | Then |
|---|---|
| **Allow** | Register for push (APNs / FCM) → save → Location (12) |
| **Don't allow** | Show the deactivation confirm **with the primary labelled `Open Settings`** (state D). **Open Settings** → Settings on iOS; on Android, an in-app re-prompt if it can still ask, otherwise Settings. **Confirm deactivation** → push off → save → Location (12) |

**Decided 25 Sep 2026:** after an OS denial the primary reads **`Open Settings`**, because it opens Settings rather than keeping anything on (the standing rule: one label per behaviour). When the user switches the toggle off themselves (state C), it stays **`Keep active`**. The dialog is otherwise identical.

**Coming back from Settings:** the foreground re-read updates the toggle. Nothing auto-advances; the user taps Save preferences again, and no dialog is raised.

**Killed while the OS dialog is up:** the app relaunches onto 10 by the flow's ordinary resume rule. The status is now determined, so the toggle reflects it and Save still works. No special handling is needed, because this screen always has a working CTA (unlike the old 09).

## Behaviour

**The push toggle**
- Default **on**. Switching **on** is immediate. Switching **off** opens the deactivation confirm (state C) and **does not change the toggle until Confirm deactivation**.
- **Keep active** or a tap on the scrim closes the dialog, and push stays on. **Confirm deactivation** sets push off, closes the dialog, and the user stays on this screen.
- No dialog appears when push is switched back on.

**The interest checkboxes**
- Default **unchecked**, all three. The whole row toggles, both directions, instantly.
- **Checking a box does nothing but record it.** No dialog, no field, no "coming soon" toast, no navigation, no server consent write.

**Save preferences**
- **Always labelled `Save preferences`.** No dynamic wording, never disabled.
- Order: (1) if push is on and the status is *not determined*, raise the OS dialog and wait; (2) on Allow, register for push; (3) **commit the push consent to the server and wait for confirmation**; (4) fire `reachability_saved`; (5) advance to Location (12).
- **Not optimistic** (epic rule): on a save failure the user stays on 10, the choices are kept, and the CTA works again. *Open:* the error-card copy.
- **Not tappable twice** — input is locked from the tap until navigation or failure.

**Elsewhere on the screen**
- **Privacy Policy** is the only link. "Settings" in the fine print is plain text, not a link.
- **Back is blocked**, as on 09. There is no header, no progress bar, no skip and no close.
- Accessibility: the toggle is `role="switch"` and each checkbox row is `role="checkbox"`, both with visible-label names. The dialog is `aria-modal`, and focus moves to **Keep active**.

## Non-goals — do not build

- **No delivery logic** for the AI call, WhatsApp or SMS: no provider integration, no templates, no WhatsApp Business setup, no SMS gateway.
- **No phone-number field**, and no phone-number collection or verification for these channels.
- **No consent record for the interest rows.** Interest is **analytics only** — not stored on the profile, not sent to the consent service, never read by any sending code.
- **No `consent_changed` for a checkbox.** Ever.
- **No calendar permission** on this screen, and no calendar code.
- **No email channel** on this screen.
- **No phone book screen (11).**
- Nothing from `scope="full"` in the reference file.

## Copy — final strings

Headline: `Never miss a date and avoid getting a penalty!` — `Never miss` is the italic em with the shared `.su-underlined em` wash
Lead: `Activate notifications so you never miss a date! Missing a date lowers your Show-up Rate, which will lead to a temporary ban or permanent suspension from the app.` — the bold words are exactly as in the reference file
Card pill (above the title): `Recommended`
Card title (with the toggle): `Push notifications` — **changed 28 Sep 2026, was `Get notifications`**
Card line: `We'll let you know about new matches, meet time or location changes and date cancellations.`
Interest heading: `More ways to reach you are coming soon.`
Interest line: `Tell us which ones you'd like.`
Rows: `Phone call from our AI assistant` / sub-line `A short automated call when something changes.` · `WhatsApp` · `SMS`
Fine print: `We use this only to coordinate your dates — never for marketing, and we never sell your data. Turn any of these off whenever you like in Settings.` + link `Read our Privacy Policy`
CTA: `Save preferences`

**Deactivation confirm (state C)**
Title: `Are you sure?` (italic)
Body: `Please remember:` / `Missing a date lowers your Show-up Rate, which will lead to a temporary ban or permanent suspension from the app. Keep notifications active to avoid any chances of missing a date.` — bold words as in the reference file
Channel chip: `Push notifications`
Primary: `Keep active` — **state D: `Open Settings`** · Secondary (danger text): `Confirm deactivation`

Apostrophes are typographic (’). **Show-up Rate** — never "Show-Up Rate".

## Build inventory — the pixel-less work this screen gates

A path whose item is not done ships **hidden**, not disabled.

- **The notification permission request**, moved here from 09 — iOS `requestAuthorization` and Android 13+ `POST_NOTIFICATIONS`.
- **iOS Time Sensitive notifications** — the entitlement plus the `timeSensitive` interruption level on date reminders, so they break through Focus modes. It is on the notification side, but this screen's promise depends on it.
- **Push registration on Allow** — APNs / FCM token acquisition and upload, verified end-to-end. A grant with no token looks exactly like success here.
- **Android notification channels** — `Date updates` (high importance) and `Matches`.
- **The push consent write** — server-side, confirmed before advancing.
- **Settings deep link** (iOS `openSettingsURLString`, Android app notification settings).
- The shared permission reconciler and the saved flow position (built once for the flow).

## Acceptance criteria

- [ ] Built from `scope="mvp"`. **Nothing from `scope="full"` ships**: no concierge-call card, calendar card, phone field, email row, or WhatsApp/SMS toggles
- [ ] One card: head (pill `Recommended`, title `Push notifications` + toggle, card line) → divider → interest heading → three checkbox rows, in the order and with the strings above. **No separate push row**
- [ ] **On 393 × 852 at default text size, the headline → Save preferences is fully visible without scrolling** in states A–D. Spacing matches the "Updated 28 Sep 2026" values
- [ ] **Push is on by default**, and the three checkboxes are **unchecked by default**
- [ ] Switching push off opens the deactivation confirm, and **the toggle stays on until Confirm deactivation**. Keep active and a scrim tap both leave it on
- [ ] **Checking a box fires `channel_interest_changed` and nothing else** — no dialog, no field, no consent write, no network call to the consent service
- [ ] The CTA always reads `Save preferences`, is never disabled, and cannot be tapped twice
- [ ] **Save preferences raises the OS dialog** only when push is on and the status is *not determined*. With push off, or the status already determined, **no dialog appears**
- [ ] After **Don't allow**, the deactivation confirm appears with the primary labelled **`Open Settings`**, which opens Settings (iOS) or re-prompts (Android, when it can still ask). A user-initiated toggle-off shows **`Keep active`**
- [ ] **Profile 09:** no OS dialog, CTA reads `Continue`, and none of the three permission events fire there — verified on both platforms
- [ ] After **Allow**, the device registers for push and the token reaches the backend — verified, not assumed
- [ ] **Not optimistic:** the flow position advances only after the server confirms the push consent. On failure the user stays on 10 with their choices kept
- [ ] The status is re-read on foreground. Returning from Settings with notifications allowed shows the toggle on
- [ ] **Explicitly tested:** force-kill while the OS dialog is up → relaunch onto 10 → the toggle reflects the answer → Save works with no dialog
- [ ] Android ≤ 12 sees no OS dialog and the toggle shows on
- [ ] The body scrolls and the CTA stays pinned. The fine print scrolls fully clear of the CTA. No absolute Y positioning
- [ ] Every toggle and checkbox row is a ≥ 44pt hit target. The switch thumb **animates** (the kit's `justify-content` shortcut is not copied)
- [ ] No header, no progress bar, no skip, no close; back is blocked
- [ ] Copy matches the strings above exactly, including **Show-up Rate**
- [ ] **Evidence of done:** screenshots of states A, B, C and D at **375 × 667, 390 × 844, 393 × 852 and 430 × 932**, with 375 × 667 shown scrolled to the top and to the bottom. Plus: the OS dialog as raised on each platform (unstyled), a recording of Don't allow → confirm → Settings on iOS, and the analytics debug log for one full pass showing every event in the table below

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **G — Permissions & Consent**, plus `screen_viewed`), **taxonomy 1.4.5 / registry 1.4.6**. Use them exactly — do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_reachability"`, `screen_name: "ProfileReachability"`, `referrer_screen_id` — **§11 row NEW 25 Sep 2026** |
| Pre-permission surface shown | `permission_prompted` | `type: "notifications"` — **only when the status is *not determined*. Moved from 09** |
| Push switched off → dialog opens | `consent_deactivation_confirm_shown` | `channel: "push"` |
| Keep active / scrim | `consent_deactivation_abandoned` | `channel: "push"` |
| Open Settings (state D) | `permission_settings_opened` | `type: "notifications"` — **not** `consent_deactivation_abandoned` |
| Confirm deactivation | `consent_deactivation_confirmed` | `channel: "push"` |
| Push toggle changed, either direction | `consent_changed` | `channel: "push"`, `on`, `surface: "profile_creation"` |
| **Interest box checked or unchecked** | `channel_interest_changed` | `channel` (`enums.json` §25), `on`, `screen_id` — **NEW 25 Sep 2026** |
| OS dialog raised | `permission_os_sheet_shown` | `type: "notifications"` — **moved from 09** |
| OS dialog answered | `permission_result` | `type: "notifications"`, `result: "granted" \| "denied"` — **moved from 09** |
| Settings opened from the toggle (denied guard) | `permission_settings_opened` | `type: "notifications"` |
| Save confirmed | `reachability_saved` | `push_on`, `permission` (§24), `interest` (§25) — **payload CHANGED 25 Sep 2026** |
| Privacy Policy tapped | `legal_link_tapped` | `link: "privacy"`, `screen_id` |

**The demand test is read from two events:** `screen_viewed{profile_reachability}` is the denominator, and `reachability_saved.interest` is the final state. `channel_interest_changed` adds every flip, so a check followed by an uncheck is not counted as interest. No new event is needed for the analysis.

**Changed in the registry for this ticket:** `reachability_saved` drops `channels_on`, `count` and `has_phone`. If any of them is already emitted, remove it. `channel_interest_changed` and §25 are new.

**Must NOT fire here:**
- **`consent_changed` for an interest box** — interest is not consent.
- `consent_changed` with any `channel` other than `push` — call, whatsapp, sms, calendar and email are reserved for the later scope.
- `profile_step_viewed` / `_completed` / `_skipped` — not a step, and there is no skip.
- `concierge_contact_*` — screen 11 is not in the MVP.
- `permission_status_changed` for the answer to our own dialog — that is `permission_result`. The reconciler fires `permission_status_changed` only for changes made outside the app (e.g. in Settings).

**Missing from the current build:** every row above except `screen_viewed` and `legal_link_tapped` is `implemented: false`.

## Out of scope

The later scope (backlog ticket `10-reachability-later.md`), phone book (11), the date-confirmed calendar prompt (a future ticket), notification templates and copy, in-app notification settings, quiet hours and per-category opt-out.

## Dependencies

- **Profile 09** — the only origin. **Three changes to it are part of this ticket** (see the top)
- **Location (12)** — the only destination
- The shared permission reconciler, the saved flow position, push registration, and a backend that accepts the consent and the token
- The ambient-backdrop component (shared with 05 and 09)

## Open

- The save-failure error copy. Use the flow's shared inline error card above the CTA.
- The demand test has no end date by design; it is reviewed after launch.
