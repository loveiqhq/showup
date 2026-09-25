# [Epic] Profile creation flow

**Type:** Epic · **Priority:** High
**Handoff folder:** `design_handoff_showup/profile/` — read `README.md` first
**Design source:** Show Up UI kit, section `03 · Profile creation`

## What this epic delivers

Everything between "the user has a verified identity" and "the user has a profile that can be shown to other people." The user arrives from the app tutorial and works through a sequence of single-question screens until their profile is complete enough to enter the product.

The flow is **mandatory once entered** and **resumable at any point**. Those two properties together are the spine of this epic: the user cannot opt out, so the flow must never lose their work.

## Shape of the flow

Three groups, separated by two full-bleed interstitials that mark the transitions.

**1 · "The basics"** — 3 steps, one shared progress bar. Name → email → date of birth, with email code verification between steps 2 and 3. Mandatory; the account cannot exist without them.

**→ Embrace 1** — "build your profile" interstitial.

**2 · Profile substance — "The real you", 3 steps** — photos (1) → prompts (2) → media (3), then the permission and reachability asks (notifications, phone book, location) which sit outside the bar. This is the group with the platform integrations and the group where most of the engineering risk sits.

**Profile verification is out of the MVP (decided 13 Sep 2026).** Story 07 below is **not built**, and the group's progress bar is **3 segments, not 4**. The bar is one shared shell with the count as a prop, so if verification returns it comes back as a fourth segment without editing three screens.

**→ Embrace 2** — "add profile details" interstitial.

**3 · "Share some details"** — 11 single-question steps: height, gender, orientation, dating language, education, religion, politics, interests, life, future, habits. Individually cheap, collectively the longest group. Each has its own `ProfileVisibility` control.

## Stories

Each story is **one screen with all of its states**, and ships with a spec sheet and a reference implementation. Numbering matches the handoff folder.

| # | Story | States |
| --- | --- | --- |
| 01 | Name | 3 — empty · value entered · empty submit |
| 02 | Email | 3 — valid · invalid format · empty submit |
| 03 | Verify email | 2 — enter code · code mismatch |
| 04 | Date of birth | 4 — empty · inline age confirm · invalid date · under 18 |
| 05 | Embrace 1 — interstitial | 1 |
| 06 | Photos | 2 — none added · some added |
| ~~07~~ | ~~Verify profile~~ | **cut from the MVP 13 Sep 2026 — not built, no progress segment. The number is retired, not re-used** |
| 07 | Prompts | 8 — 0/1/3 saved · topic sheet · write sheet ×4 (empty · mid · at the cap · empty submit) |
| 08 | Media — video & voice | 8 — empty · video · voice · both · recording ×2 · inspiration sheet |
| 09 | Notifications permission | 1 — and it is **not shown at all** unless the OS status is *not determined* (ticketed 21 Sep 2026) |
| 10 | Reachability & consents | 2 — consents on · deactivation confirm |
| 11 | Phone book concierge | 3 — recommend · OS sheet · saved |
| 12 | Location permission | 3 — ask · system sheet · denied recovery |
| 13 | Embrace 2 — interstitial | 1 |
| 14–24 | "Share some details" ×11 | 2–3 each — empty · answered · (cap, where relevant) |

**Story numbers after 06 shifted by one when verify profile was cut.** Prompts is **07**, media is **08**. Nothing past 06 had been ticketed, so no Jira issue changes number — but if you are holding an older copy of this epic, the media story moved.

**01–06 and prompts are specced and ticketed.** The rest have artboards in the kit and need their spec sheets written.

## Built once, not per screen

The reason this is an epic and not 25 unrelated tickets. Each of these is shared infrastructure, and a second copy of any of them is a bug:

- **The group header shell** — `AppHeader` at 52, centred title, with the **leading slot as a variant** (empty on step 1, back chevron elsewhere) and the trailing slot empty
- **The step progress bar** — segment count and current index as props
- **The single-question screen scaffold** — status bar / header / flex-1 content / keyboard / home indicator, with the **one `flex: 1` spacer** that absorbs every device and keyboard difference
- **The reserved status region** — fixed `min-height` under the input, `aria-live="polite"`, empty in the calm state
- **The inline error card** — one component across every screen in the flow: pad 10 / 14, radius 12, `rgba(251,50,59,0.07)` on `rgba(251,50,59,0.18)`, 18px round `--liq-danger` glyph, message Manrope 500 / 13.5 in `--liq-danger-fg`
- **`FloatingField`** — the outlined input with the notching floating label, plus its error affordances
- **`NextButton`** — the orange round CTA. Sunset is reserved for commitment screens and appears nowhere in this flow
- **`ProfileVisibility`** — the per-field visibility control on the group 3 steps
- **The flow state machine** — step order, persistence, and launch-time routing (see below)

Story 01 builds the first four; every story after it consumes them. Sequence the sprint accordingly.

## Flow-level rules — every story inherits these

**No absolute Y positioning, anywhere.** Nested flex columns, one `flex: 1` spacer per screen. Anchor chrome to `env(safe-area-inset-*)`.

**The keyboard is never ours.** The reference files render a mocked keyboard purely so the artboards show the true content height above it. Ship the platform keyboard; never spec its height.

**No text-input CTA is ever disabled.** Pressing Continue on an empty or invalid field produces the inline error and a one-shot `su-shake` 480ms. A dead button cannot explain itself, and it means the specific error strings are never seen for an empty field. The error clears when the value becomes valid, and does not re-fire until Continue is pressed again.

The single exception is story 03's `Verify code` button, disabled until six digits are entered — a partial code has nothing to validate. Every free-text field follows the rule.

**Empty-field copy has one shape across the flow:** "Enter your <thing> to continue." Do not vary the sentence per screen.

**Failure states reserve their space and preserve their input.** The action row must not move when validation fails, and nothing the user typed is ever cleared.

**No back button on step 1; every later step has one.** Profile creation is mandatory once entered, so step 1 also suppresses the iOS swipe-back gesture and the Android hardware / gesture back.

**The flow resumes where the user left off.** Progress is persisted per completed step — values *and* position. On launch, an account with an incomplete profile routes straight to its last incomplete step with everything already entered still present. Resuming is **silent**: no prompt, no toast, no "welcome back", and no error state on arrival. The progress bar shows the real position.

**One question per screen.** Do not consolidate steps to reduce screen count; the flow's length is a deliberate trade for its completion rate.

**Copy is final and lives in the stories.** Product terms capitalise exactly: **Show Up** · **Show-up Rate** · **Instant Mode** · **Match Mate** · **Check-In**.

## Notification state is three facts, not one — flow-level rule, 21 Sep 2026

Screens 09 and 10 together have to leave the app knowing **where it is allowed to send**. That is not one boolean, and only one of the three parts is ever *finished* by these screens:

1. **The OS permission status** (§24 `permission_status`) — authoritative on the device, and it can change at any time **outside our app**: Settings, an OS update, a Focus mode, Android's per-channel switches, a restore. It is therefore **read, never remembered**. Caching it to send to the server is fine; treating it as an onboarding output is not.
2. **Our consent** — the six channels on screen 10 (§8 `channel`, `consent_changed`). This one *is* ours, lives on the server, and only the user changes it in our UI.
3. **A registered push token on the server.** Permission granted with no valid token means we still cannot send. This is the fact that actually answers the question, and it is the one most often missed.

**A push consent of `true` with a permission of `denied` is a real, reachable state** — a consent we cannot honour, not a data error. Anything that reads "can we notify this user" must evaluate all three.

**One shared permission reconciler, not a per-screen check.** On launch and on every foreground, for the life of the app: read the status → if it differs from the last value synced, update the server, register or invalidate the token, and emit `permission_status_changed` (`type`, `from`, `to`, `detected_on`, `in_flow`). One component, used by 09, 10, the camera and microphone asks, and the location ask. A crash, a killed background process or a Settings trip is then covered by the same code path as everything else.

**Screen 10 does not exit optimistically.** Consent is committed to the server and confirmed **before** the flow position advances. A save failure keeps the user on 10; a crash mid-10 resumes onto 10 by the flow's ordinary resume rule (rule 4a) — no special mechanism, and it is what guarantees that a user who is past 10 has a consent record that exists.

**Recovering a permission change mid-app is NOT a replay of screen 10.** 10 is a consent screen; the permission is the device's. When the status changes later, the user is asked again **where they are** — a prompt at the moment it bites — and never by pushing an onboarding screen back at them. **How that prompt looks and when it fires is deliberately not decided yet** (21 Sep 2026); `permission_status_changed` exists so the size of the problem is measurable before it is designed.

## Device matrix

375 × 667 · 390 × 844 · 430 × 932. All content visible, nothing clipped, no scroll, CTA always fully visible above the keyboard. **375 × 667 is where these screens fail** — check it first, on every story.

## Dependencies

- **The flow state machine and its persistence** — per-account profile-creation progress plus launch-time routing. Shared by every story; **build it before story 01**, not alongside
- **Profile attribute persistence** (handoff README build item 01) — the columns behind each step. `displayName` already exists at `main @ c195089`; most others do not
- **Email code verification** (build item 08) — story 03
- **Platform permissions** — notifications, contacts, location, camera, microphone, photo library
- **Media pipeline** — video and voice recording, upload, transcode, playback (story 09 is the largest single engineering item in the epic)
- **Profile verification** — story 07
- Lora + Manrope in the app font set, and `.su-underlined em` shipped as a shared rule

## Definition of done — epic level

- [ ] A verified account can complete the flow end to end on all three device sizes and arrive in the product with a showable profile
- [ ] Killing the app at any step and relaunching returns the user to that step with everything already entered intact
- [ ] The header, progress bar, screen scaffold, status region, error card, field and CTA exist **once each** and are consumed by every screen
- [ ] No screen in the flow uses absolute Y positioning, and none ships a hand-built keyboard
- [ ] No CTA in the flow is ever disabled
- [ ] Every screen's states match its spec sheet, evidenced per story
- [ ] The flow's analytics events fire per `tracking/requirements.json`, including resume-with-step

## Out of scope

The app tutorial and the welcome / sign-up screens before this flow (their own epics), profile **editing** after creation, the discovery and matching surfaces this flow feeds, `Show-up Rate` mechanics, and moderation of any user-supplied content captured here.

## Open — epic level

- **Where the saved position lives.** Local-only resumes fine but a reinstall restarts the flow against a half-populated profile. Server-side avoids it; needs deciding before the migration
- **Whether saved progress expires.** A user returning after six months resumes onto a step in a flow they no longer remember starting
- **Which steps are genuinely mandatory.** Group 1 must be; group 3's 11 steps almost certainly should be skippable, and if so the flow needs a skip affordance that story 01's no-back rule deliberately withholds. This decision changes the shape of the epic and is worth settling early
- **App-store review guidelines dislike unavoidable flows.** Suppressing the Android hardware back is worth a pre-submission check; resumability is the mitigation and should be stated in the review notes
- **`loveiqhq/showup` is backend-only** — there is no app UI repo yet, so no visual adherence check is possible against an implementation. Record the repo when it exists
