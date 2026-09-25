# Profile creation flow — handoff

The screens a verified account fills in to become a profile. Point Claude Code at **this folder**.

```
profile/
  screen-name-reference.jsx        ← design reference, screen 01. Read values from here.
  screen-email-reference.jsx       ← screen 02
  screen-verify-email-reference.jsx ← screen 03
  screen-dob-reference.jsx         ← screen 04
  screen-embrace-reference.jsx     ← screen 05, the bridge
  screen-photos-reference.jsx      ← screen 06, first of “The real you”
  screen-prompts-reference.jsx     ← screen 07, prompts
  screen-media-reference.jsx       ← screen 08, media (video + voice) + the two capture views
  screen-notifications-reference.jsx ← screen 09, the notification permission ask
  tickets/00-epic.md               ← the epic: flow shape, story list, what is built once
  tickets/01-name.md               ← scope, copy strings, AC
  spec-sheets/01-name.png          ← annotated visual spec, keyed ①–⑮
  spec-sheets/02-email.png         ← keyed ①–⑱
  spec-sheets/03-verify-email.png  ← keyed ①–⑰
  spec-sheets/04-date-of-birth.png ← keyed ①–㉗
  spec-sheets/05-embrace-build-profile.png ← keyed ①–⑬, two violet exception callouts
  spec-sheets/06-photos.png        ← keyed ①–⑳, 7 frames, violet permission callouts
  spec-sheets/07-prompts.png       ← keyed ①–㉗, 8 frames, violet conversion callouts
  spec-sheets/08-media.png         ← keyed ①–㉞, 10 frames, violet capture views + permission matrix
  ../components/shared.jsx         ← StatusBar, AppHeader, StepProgress, NextButton, HomeIndicator, Icon
  ../tokens/colors_and_type.css    ← authoritative token values
```

## Order of authority

1. **`screen-<name>-reference.jsx`** — real values, wins on numbers
2. **`tickets/*.md`** — wins on behaviour, scope and copy strings
3. **`spec-sheets/*.png`** — wins on nothing; it is the human-readable summary of 1 and 2

Never measure the PNG. Every number in it is in the reference file.

## The epic

`tickets/00-epic.md` is the parent of every story here. It carries the flow's shape, the full story list with state counts, the **built-once** inventory, and the epic-level dependencies and open decisions. Read it before picking up a story — several of the things it names (the header shell, the screen scaffold, the error card, the flow state machine) are shared, and a second copy of any of them is a bug.

## Screens in this flow

**"The basics" — a 3-step sequence, one progress bar:**

| # | Screen | Status |
| --- | --- | --- |
| 01 | Name — empty · value entered · empty submit · **no back button** · **entry point only when no saved progress** | speccd + ticketed |
| 02 | Email — valid · invalid format · empty submit | speccd + ticketed |
| 03 | Verify email — enter code · code mismatch | speccd + ticketed |
| 04 | Date of birth — empty · inline age confirm · invalid date · under 18 | speccd + ticketed |

**The bridge out of "The basics":**

| # | Screen | Status |
| --- | --- | --- |
| 05 | Embrace — build your profile · **one state, no input, no header, no progress bar** | speccd + ticketed |

**Screen 05 is not a step.** It is a bridge: "The basics" is finished and "Share some details" has not started, so it appears in neither progress bar and has no `AppHeader`. It fires `screen_viewed` and `embrace_bridge_viewed`, and deliberately fires no `profile_step_viewed` — it has no row in `enums.json` §2 and it must not get one. A sibling bridge (`variant: "add_details"`) sits later in the flow, shares this screen's shell, and is registered in §11 but not yet ticketed.

**"The real you" — a 3-step group with its own progress bar:**

| # | Screen | Status |
| --- | --- | --- |
| 06 | Photos — empty · some added · uploading + failed · library blocked · library can-still-ask · source sheet · source sheet camera-blocked | speccd + ticketed |
| 07 | Prompts — 0/1/3 saved · topic sheet · write sheet ×4 (empty, mid, at the cap, empty submit) | speccd + ticketed |
| 08 | Media — empty · video only · voice only · both · prompt list ×2 · recording ×2 · review ×2 | speccd + ticketed |

**The asks after the profile — outside every progress bar:**

| # | Screen | Status |
| --- | --- | --- |
| 09 | Notifications — permission ask · **one state, no header, no progress bar** | speccd + ticketed |
| 10 | Stay reachable — six consent toggles + phone · deactivation confirm | not yet ticketed — **carries three requirements from 09** |

**Screen 09 has one path, because profile creation runs once on a fresh install.** A fresh install has no notification permission on either platform, so the status here is always *not determined*: the CTA raises the OS sheet and both outcomes advance. Two skip cases, decided **before the screen is pushed** and never after it mounts — **Android ≤ 12** (no `POST_NOTIFICATIONS` before API 33, notifications on by default) and the **guard** of an already-determined status (restored from a backup, or the app killed while the sheet was up). The system dialog is shown **once per install**; a screen whose only button raises a dialog that will not appear is a dead end, which is why there is no denied state and no `Open Settings` here. **Recovery from a denial belongs to Stay reachable (10)**, which owns the notifications consent row. Re-read the status on every foreground. Both outcomes of the sheet — granted and denied — advance to 10, and **the saved flow position advances when the sheet is raised rather than when it returns**, so an app killed mid-sheet relaunches onto 10 and never onto a screen whose button can no longer do anything. **Screen 10 is the recovery** for a denial and for a sheet that was never answered — its notifications row raises the sheet when the status is still *not determined* and deep-links to Settings when it is denied, and it must not conflate **our consent record** (the six toggles, `consent_changed`) with **the OS permission**. Those three requirements are written at the foot of `tickets/09-notifications.md`; move them into 10's ticket when it is written. Full matrix in `tickets/09-notifications.md`.

**This is a different group, with a different header title and a different progress bar.** `AppHeader` title is `The real you` with a **back chevron**, and `StepProgress` is **`steps={3}`** — not a fifth segment on "The basics". Build the group's shell once here; the other screens consume it.

**Decided 13 Sep 2026: the group is 3 steps, not 4.** Profile **verification is out of the MVP**, so "The real you" is **photos (1) → prompts (2) → media (3)**. Screen 06 is `current={1}`, screen 07 is `current={2}`, media is `current={3}`. `ScreenProfileVerify` stays in the kit but is **not built and gets no segment**; the artboard is kept so the screen can return post-MVP, at which point the shell takes a new count as a **prop** and no screen is edited. Screen 06's ticket says `steps={4}` — **that criterion is superseded by screen 07's ticket**, which carries the decision.

**Screen 08 is the only screen in the flow that captures video and audio, and the only one whose capture UI is ours.** Photos hands off to the system picker; media runs through our own session, because the prompt has to sit under the lens and the 10s / 15s cap has to be enforced — so there is no system camera UI anywhere in it. Both slots are optional, `Optional · you can skip this` sits **above** the headline (it was read fourth in the old build and the step read as mandatory), the card CTA is `See the prompts` rather than `Record`, there is **no upload path and no caption field**, and the chosen prompt *is* the caption on the profile. Stop always lands on a review screen; only `Use this clip` / `Use this recording` creates an artefact. The previewed prompt on each empty card is **ranked server-side from completions** (decision 33) — the client never ranks. Permission behaviour is settled in the ticket (video needs camera *and* mic; a blocked camera leaves the voice card working; a blocked mic is the only state that blocks the step) but the **blocked row is the one state the sheet does not draw**. Full detail in `tickets/08-media.md`.

**Screen 07 is where the flow first asks the user to write.** Its rules are its own: three suggested topics sit on the screen so the first tap lands in the write sheet; the 15-topic sheet is the "Browse all" escape hatch; the write sheet carries a worked example; the 160-character cap is **amber, not danger**, because reaching it is not a failure; and Save is never disabled (rule 2c, applied inside a sheet). Full detail in `tickets/07-prompts.md`.

**Screen 06 is the first screen in the flow that scrolls**, and only its middle region scrolls — the header, the progress bar and the sticky CTA never move. Every other screen in `profile/` is a single non-scrolling column, so do not copy this screen's shell to them.

**The photos screen requests no library permission, and that is what keeps its permission surface small.** We present the **system picker** (iOS `PHPickerViewController`, Android 13+ photo picker), which runs out-of-process over the user's whole library and needs no permission — so there is **no iOS access card** and **no limited-access state at all** (an earlier draft had a partial-library banner; it was deleted, not redesigned, because it explained a state we cannot enter). What survives is narrow: the library access card applies on **Android ≤ 12 only**, in two modes — *can still ask* (re-prompt in-app) and *blocked* (open Settings and name the platform's own row) — and **camera**, the one permission that always applies on both platforms, answered on the camera row of the source sheet because a denied camera blocks nothing. Re-read every status on foreground. The full matrix is in `tickets/06-photos.md`.

**Screen 06 owns the source sheet** (`PhotoSourceSheet`) — the "+" choice between library and camera. It is ours; what it launches is the OS's. Media (screen 08) will need the same component.

**The progress bar does not advance on screen 03.** Both the email step and its verification carry `current={2}` — the user is on the email step until the code is confirmed. Verification is not a fourth step; do not add a segment.

The nine **detail** steps after "The basics" — photos, prompts, interests, lifestyle, living status, embrace — are a separate group with their own progress and their own rules, and are not part of this three-screen sequence.

## The rules that break everything if ignored

**1. No absolute Y positioning.** Status bar / AppHeader / content / keyboard / home indicator is one flex column. Inside the content column everything is top-anchored, with a **single `flex: 1` spacer** between the last input and the CTA row.

**2. One spacer, not two.** Unlike the welcome flow, nothing on these screens floats in the middle. The spacer is the only flexible element and it absorbs every device and keyboard difference.

**2b. The status region under the field is reserved, not conditional.** Every screen in this group carries one — `min-height` sized to its tallest card, `aria-live="polite"` — empty in the calm state and filled in the failure state. It is what keeps the CTA and the keyboard at the same Y across a screen's states. **44** on name, **30** on verify email, **84** on DoB.

**With one deliberate exception: screen 02, email.** That screen is too dense to reserve the taller height without pushing the CTA into the keyboard, so its consent row and CTA shift ~18px in the error state. What holds instead is that the CTA stays fully visible above the keyboard in **both** states, at every size. If that ever fails, reserve the height and shorten the consent copy — never let the CTA slide under the keys.

**2c. No text-input CTA in this group is ever disabled.** Pressing Continue on an empty or invalid field produces an inline error and a one-shot `su-shake` 480ms; a dead button teaches nothing, and it means the specific error strings are never seen for an empty field. Applies to **name, email and date of birth**. Recovery is the same everywhere: the error clears when the value becomes valid — not on blur, not on re-submit — and does not re-fire until Continue is pressed again. Whitespace-only counts as empty.

**The one exception is 03 Verify email**, whose `Verify code` button *is* disabled until all six digits are entered. That is a deliberate exception, not drift: a partial code has nothing to validate, and the six slots already say "six digits" visually. Every screen with a free-text field follows 2c.

**The error card is one component across the whole group** — pad 10 14, radius 12, `rgba(251,50,59,0.07)` on `rgba(251,50,59,0.18)`, 18px round danger glyph, Manrope 500 / 13.5 in `--liq-danger-fg`. Do not re-style it per screen.

**The empty-field string has one shape across the group:** `Enter your name to continue.` · `Enter your email to continue.` · and the same for date of birth. The requirement should read identically wherever the user meets it.

**3. The keyboard is not ours.** Every screen in this group renders a mocked keyboard in the reference file so the artboard shows the true content height (286 + 28 home indicator → 424 of content at 390 × 844). Ship the platform keyboard. Never spec a keyboard height, never build the mock, never assume it.

**4. All three steps share one chrome.** AppHeader title `The basics` (centred, empty trailing slot) + a 3-segment `StepProgress` with `current` as the difference. Build it once as the group's shell; three copies drift. This is the same lesson as tutorial card 01.

**4a. The flow resumes where the user left off.** Progress is persisted **per completed step** — values plus the position. On launch, an account with an incomplete profile routes straight to its **last incomplete step**, with everything already entered still present. Screen 01 is the flow's entry point only when no saved progress exists.

Resuming is **silent**: no prompt, no toast, no "welcome back" — the user lands on the step with the keyboard open and the progress bar showing their real position. A resumed step behaves like a freshly-reached one: no error state on arrival. Persist on a successful Continue, before navigating — not per keystroke, not only at the end.

**This is what makes rule 4b acceptable.** Mandatory *and* unforgiving would be a bad flow.

**4b. Step 1 has no back button; steps 2 and 3 do.** Profile creation is **mandatory once entered** — the user arrives from the app tutorial and there is nothing behind step 1 to return to. So the header's leading slot is a **variant of the shared shell**, not a constant: empty on name, back chevron on email and date of birth. The empty slot still occupies its 36 so the centred title does not shift. On step 1 the iOS swipe-back gesture and the Android hardware / gesture back are also suppressed — a header with no chevron that the OS can still dismiss is worse than no rule at all.

**5. No ambient backdrop.** Flat `--liq-bg`. The welcome flow's orange/violet orbs stop at the door — the keyboard owns the bottom half of every screen here.

**Named exception, decided 9 Sep 2026, extended 21 Sep 2026 to screen 09.** Screen 05, its sibling bridge and the notifications ask **do** carry the backdrop — the orange orb, the violet orb and the 360-tall peach wash, exactly the first-run Startup screen's recipe. The rule exists because every screen with a keyboard has no room for atmosphere; a bridge has no keyboard and no input, and the backdrop is what makes it read as a beat rather than another form. The exception is the two bridges and screen 09 only — none of them has a keyboard — do not carry it into name, email, verify email, date of birth, photos, prompts or media. Extract the backdrop as one component: Startup, screen 05, the sibling bridge and screen 09 all use it.

**5b. Two CTA shapes, and only one screen gets the second.** Every screen in this group uses the round orange `NextButton` bottom-right — except **03 Verify email**, which uses a **full-width sunset** `Button`. Sunset is reserved for commitment screens, and confirming a code is the only irreversible act in "The basics". Do not use it anywhere else here, and do not swap 03 back to the round button.

**5c. Two layout shapes.** Screens 01, 02 and 04 are **bottom-anchored** — the spacer sits above the CTA row and pushes it to the base. Screen 03 is **bottom-slack** — one top-anchored stack with the spacer at the very bottom, so the CTA sits directly under the slots it belongs to. Both are correct; do not normalise one into the other.

**6. Headlines are 34, not 38.** The keyboard is always open in this group, so the headline is one step smaller than the welcome flow's. One italic em per headline, with the orange wash from the shared `.su-underlined em` rule.

**7. The CTA is orange, not sunset.** These are routine screens. `--su-grad-sunset` is reserved for commitment screens.

**Named exception, decided 9 Sep 2026: screen 05 — extended 21 Sep 2026 to screen 09**, whose CTA is the same full-width sunset `Button` with the label `Enable notifications` and **no trailing arrow icon**. The bridge's CTA is a **full-width sunset** `Button` (`variant="sunset" size="lg" fullWidth`, label `Upload my photos` with a trailing SVG arrow), not the round orange `NextButton`. Agreeing to build the profile is a commitment beat, and so is granting push. Same scope as the rule 5 exception: the two bridges and screen 09.

**8. `ProfileVisibility` is not on these three screens.** Name, email and date of birth are not per-field-hideable at this step. It belongs to the nine detail steps — do not add it here for consistency.

**One named exception, decided 7 Sep 2026: screen 04, date of birth.** The band *is* on that screen, with the copy as drawn — `Don't display on my profile`, unchecked by default. **Checked means the age is not shown on the profile; it is still used for the matching algorithm.** It earns the exception because date of birth is the only step whose value is locked, so the one moment the user can decide what happens to it is the moment they give it. The exception is screen 04 only — do not add the band to name, email or verify email.

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

375 × 667 · 390 × 844 · 430 × 932. All content visible, nothing clipped, no scroll, CTA always fully visible above the keyboard. 375 × 667 is where it gets tight; check it first.

## Error copy is specific, never generic

"Please enter a valid email" is banned, and so is every message like it. Screen 02 picks its message from what the user actually typed — a missing `@`, a missing `.com` — and renders the named fragment as a mono chip so the fix is one keystroke. Screen 03's message names **both** recoveries (re-read the code, or request a new one) because at that moment the user does not know which they need.

**Never clear what the user entered on a failure.** Screen 02 keeps the address; screen 03 keeps all six digits. Wiping a six-slot code on a near-miss is the single most hostile thing this group could do.

## Copy

Each ticket carries a **Copy — final strings** section; treat it as the source. Product terms capitalise exactly: **Show Up** · **Show-up Rate** · **Instant Mode** · **Match Mate** · **Check-In**.
