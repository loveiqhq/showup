# [Profile 08] Media — the 10-second video and the 15-second voice note

**Parent:** Profile creation flow (SHOWUP-TBD — epic `profile/tickets/00-epic.md`) · **Type:** Story · **Priority:** Medium
**Attachments:** `design_handoff_showup.zip` (the handoff folder — reference file, tokens, shell components, `tracking/`) · `profile-08-media-spec-sheet.png` (10 frames, keyed ①–㉞)

## Description

The last beat of profile creation, and the only one that asks for the user's face and voice. One short prompt answered as a **10-second video** and/or a **15-second voice note**, so a match can feel someone's energy before meeting them.

**Both slots are optional and the whole step is skippable.** That is not a footnote: the previous build put "optional" in a subhead under a 32px display line, where it was the fourth thing read, and users took the step as mandatory. It is now stated **structurally, above the headline**, and `Skip for now` sits in the footer beside an always-enabled Continue.

It is **step 3 of 3 of "The real you"** — photos → prompts → media — entered from screen 07 and the **last screen of the group**.

**Ten states, one ticket.** A–F are variants of one layout column plus the one sheet it opens; G–J are the two capture sub-screens, folded in here because neither has an entry point of its own — the only way to reach a viewfinder is to commit to a prompt on this screen (handoff rule: *fold a modal into its host ticket when it is an ending of the same attempt*).

| | State | Prop |
| --- | --- | --- |
| A | Empty — both slots unrecorded | `state="empty"` |
| B | Video kept, voice empty | `state="video-only"` |
| C | Voice kept, video empty | `state="voice-only"` |
| D | Both kept | `state="both"` |
| E | Prompt list — video | `sheet="prompts-video"` |
| F | Prompt list — voice | `sheet="prompts-voice"` |
| G | Video recording | `<VideoRecordingView phase="recording" elapsed={6}/>` |
| H | Video review — post-Stop | `<VideoRecordingView phase="review" elapsed={9}/>` |
| I | Voice recording | `<VoiceRecordingView phase="recording" elapsed={4}/>` |
| J | Voice review — post-Stop | `<VoiceRecordingView phase="review" elapsed={14}/>` |

**Three screens, three registry rows.** A–F are `profile_media`, G/I are `profile_media_record`, H/J are `profile_media_review` (`enums.json` §11). The record and review views are **full-bleed**: no `AppHeader`, no `StepProgress`, no `SkipLink`.

## Decided before build — read this first

**1 · The 16 Sep 2026 pass supersedes every earlier media artboard.** Six changes, all aimed at the same two problems — users read this step as mandatory, and they stall on *"what do I even say"*. They are the point of the ticket, not polish:

| Element | Was | Is |
|---|---|---|
| Optional | A line in the subhead, read fourth | **A violet pill above the headline**, read first |
| Video length | 15 seconds | **10 seconds** — shorter is the whole point |
| Sources | Record, or upload from library | **Captured in the app or not at all.** No upload path on either card |
| The card's CTA | `Record` | **`See the prompts`** — the prompt list is the brief; recording is the second step |
| Caption | A 50-character caption field after the take | **None. The chosen prompt IS the caption** on the profile |
| "What good looks like" | An inspiration sheet + a manifesto card | **Gone.** The eleven prompts do that job, and the empty state now fits above the fold at 390 × 844 |

**Do not build the old flow and add the prompt list later.** `Record` asks for a performance with no brief; the list *is* the brief, so it comes first. Bolted on afterwards it becomes a third thing on a screen that already had two.

**2 · The progress bar in this group is 3 segments, and this screen is the last.** `steps={3} current={3}` — photos (1) → prompts (2) → media (3). Verify profile is out of the MVP (decided 13 Sep 2026, carried by [Profile 07]); `ScreenProfileVerify` is not built and gets no segment. The group's header + progress shell is **built once on screen 06** and consumed here — the count is a prop, so if verification returns post-MVP nobody edits three screens.

**3 · The prompt previewed on each empty card is ranked by the server, not chosen by design** (decision 33, `requirements.json`). Each card shows the most-**completed** prompt for its medium, passed in as `previewVideo` / `previewVoice`. `MEDIA_PREVIEW` in the reference file is the **cold-start fallback only**. **The client never ranks**, the list order never changes, and video and voice rank separately. The ranking rules the job has to respect are in the Tracking section — they are part of this ticket's scope only in that the screen must render what it is handed and fall back locally when it is handed nothing.

**4 · The commit CTA in the sheet is the group's second deliberate exception to flow rule 2c.** With no row selected it is the quiet `ghost` variant reading `Choose a prompt to continue` and it is **disabled** — the same shape as `Verify code` on screen 03, and for the same reason: there is nothing to validate and the label already names the requirement. Every other CTA in this flow stays live. Do not generalise this to the screen's own Continue, which is **never** disabled.

## Layout — read this before building

⚠ **The screen's middle scrolls; the footer does not.** StatusBar (54, fixed) → AppHeader (52, fixed) → **scrolling middle** (`flex: 1`, `overflow: auto`, padding `4 24 0`) → footer (`flex: none`, padding `12 24 14`) → HomeIndicator (28, fixed).

⚠ **`StepProgress` scrolls with the content here**, as on screen 07 and unlike screen 06. Do not "fix" it into a third fixed row.

⚠ **The empty state must fit above the fold at 390 × 844** — pill, headline, both cards, both CTAs, with no scroll. That budget is why both cards are compact and why the removed manifesto card is not coming back. At 375 × 667 the second card's CTA may fall below the fold; what must not happen is the *first* card's CTA needing a scroll.

⚠ **No absolute Y positioning.** The sheet docks to the bottom edge; the recording views are their own flex columns (status bar → top row → `flex: 1` middle → lower third → home indicator).

⚠ **The two recording views are one behaviour in two media, not one component.** Video is a full-bleed viewfinder on a dark ground with a light status bar; voice is the app's own light ground with the ambient orbs and a centred waveform. Same anatomy — Cancel + REC row, prompt, progress bar, shutter — and the same `phase` prop switching to review. Build the anatomy once and let the medium supply the middle; do not fork them into two screens and do not normalise voice onto the dark ground.

📎 All specs are on the attached **profile-08-media-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed **①–㉞** across the ten frames, with the 16 Sep pass in **violet** and the capture views framed violet as *ours, not the OS*. The sheet also carries the *what changed and what it replaces* table, the permission matrix, and the layout rules — all reproduced below. **The blocked permission row is the one thing the sheet does not draw** (see above). Note the design system has colour and type tokens only — there is no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/profile/screen-media-reference.jsx` — the code that renders the spec sheet. Read values from it rather than measuring the PNG. State props are in the table above.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **The camera and microphone permission alerts** — copy, type, buttons, height, animation, and which buttons they offer. They appear at the moment of use; on iOS each can be shown **once**.
- **The Settings app** and the page we land on. We choose to open it; we do not choose what it shows or where the row sits.
- **The OS recording indicators** — the iOS orange/green dot and status pill, the Android camera/mic chips. Do not imitate them and do not compensate for them.
- **Audio-session interruptions** — an incoming call, an alarm, another app taking the microphone. We handle the callback; we do not control the alert.

**The viewfinder, however, IS ours.** This is the deliberate opposite of screen 06: photos hands off to the system picker, media runs capture through our **own** session so the prompt can sit under the lens and the cap can be enforced. There is no system camera UI anywhere in this flow, and `UIImagePickerController` / an intent to the OS camera app is **not** an acceptable substitute — it loses the prompt, the 10-second cap and the review screen.

## Permissions — every status, and what to do with it

**Video needs camera *and* microphone. Voice needs microphone only.** That single sentence is the whole shape of this table, and it is why a blocked microphone is the only state that blocks the entire step.

The app **always knows which status it is in**. It **never** knows which toggle the user will land on — neither platform deep-links to a single permission row — so *"can still ask"* and *"blocked"* are two behaviours and never one label.

| Permission & status | When we ask | What we show | What the button does |
|---|---|---|---|
| **Camera / mic** · not determined | On the **commit CTA** (`Film 10 seconds` / `Record 15 seconds`), not on screen entry and not on `See the prompts` | The OS alert, over the sheet | Granted → straight into the viewfinder. Denied → the sheet stays open with the blocked row |
| **Camera or mic** · can still ask (Android, after one refusal) | — | The blocked row in the sheet's reserved status region, **ask mode** | **Re-prompts in-app.** No Settings trip, and it names no toggle |
| **Camera or mic** · permanently denied | — | The same row, **blocked mode**, naming the platform's own label | Opens **our app's details / permissions page** |
| **Mic blocked, camera fine** | — | Blocked row on **both** cards | Settings. This is the one case where the step cannot be completed at all |
| **Camera blocked, mic fine** | — | Blocked row on the **video** card only | Settings — **and the voice card stays fully functional**, exactly as the library row stayed functional on screen 06 |
| Returning from Settings | — | **Whatever the new status is** | **Re-read both statuses on every foreground.** A user who granted access and comes back to a blocked row will not try twice |

**Scale the treatment to what is actually blocked.** A blocked camera blocks one card, so it gets a quiet row where the user asked the question — never a screen replacement, never a card over the voice slot, and never a gate on Continue, which is optional in every state.

**Missing from the design: the blocked row has no artboard.** Ten states are drawn; the two permission modes are not. The behaviour above is binding, the visual treatment is not yet specified, and the recommendation is to **reuse the group's existing pattern** — the reserved status region above the sheet's commit CTA, carrying the same quiet lilac row with the violet lock glyph and the 30px `Settings` / `Allow` pill used on screen 06's camera row (state G there). **Do not invent a third treatment**, and do not ship a full-screen permission card. Flag it back for a spec-sheet frame before the sprint; the copy strings are below.

## Copy — final strings

Section title (AppHeader): `The real you`
Optional pill: `Optional · you can skip this`
Headline: `Show your face. Let them hear you.` — `hear you` italic, with the orange underline wash
Footer skip: `Skip for now`
CTA: `Continue`

**Video card, empty:**
Title: `A 10-second video`
Hint: `Filmed here in the app. Ten seconds, one prompt.`
Preview eyebrow: `One of 11 prompts`
CTA: `See the prompts`

**Voice card, empty:**
Title: `A 15-second voice note`
Hint: `Just your voice, answering one prompt.`
Preview eyebrow / CTA: identical to the video card.

**Filled cards:**
Caption eyebrow: `Prompt · shown on your profile`
Video row label: `Your video`
Saved chip: `Saved`
Controls: `Retake` · `Delete`

**Prompt list (E / F):**
Headline: `Pick one to answer.` — `answer` italic with the wash
Sub, video: `Ten seconds is short on purpose. Pick the easiest one — nobody is marking this.`
Sub, voice: `Fifteen seconds, just your voice. Pick the easiest one — nobody is marking this.`
Own-idea row: `Something else — my own idea`
Own-idea sub: `Say or show whatever you like — your own words, your own idea.`
Commit CTA, nothing picked: `Choose a prompt to continue`
Commit CTA, picked: `Film 10 seconds` / `Record 15 seconds`

**Recording (G / I):**
Cancel pill: `Cancel`
Recording chip: `REC`
Voice hint: `Listening · keep going`

**Review (H / J):**
Recorded chip: `0:09 recorded` / `0:14 recorded` — the take's real length
Voice hint: `Hear it back before you keep it`
Secondary: `Retake`
Primary: `Use this clip` (video) / `Use this recording` (voice)

**Permission rows (behaviour above, treatment pending):**
Blocked, camera: `Camera access is off. Turn on Camera in Settings to film.` · action `Settings`
Blocked, mic: `Microphone access is off. Turn on Microphone in Settings to record.` · action `Settings`
Can still ask, camera: `Allow camera access to film your 10 seconds.` · action `Allow camera access`
Can still ask, mic: `Allow microphone access to record.` · action `Allow microphone access`
Row labels in the blocked strings are **replaced by the platform's own label** — never hard-code `Camera` or `Microphone` if the OS calls it something else.

**The eleven prompts and the own-idea row are in the reference file** (`MEDIA_PROMPTS`, `MEDIA_PROMPT_OWN`) — they are content, not layout, and the reference file is the source. Do not retype them, keep the order, and store the stable `media_prompt_id` from `enums.json` §20 rather than the display string.

Product terms capitalise exactly: **Show Up** · **Show-up Rate** · **Match Mate**.

## Behaviour

**Getting to a prompt**

- **`See the prompts` opens the prompt list for that medium** — video and voice open the same eleven, in the same order, with the medium's own sub copy and commit label. The sheet is one component with a `kind` prop; two copies is a bug.
- **Rows are radio-select, not tap-to-launch.** The user can read all eleven and change their mind before anything opens. Only the **commit CTA** leaves the sheet.
- **Nothing is preselected** — not even the previewed prompt. The card previews it; the list does not pick it.
- **The own-idea row is always last and always visually distinct** (dashed border, sans-serif, its own sub line). It never ranks and is never removed.
- **Dismissing the sheet keeps nothing.** There is no draft to lose: the selection is discarded and the card is unchanged.
- **Reopening the list over a filled card** (`Retake`) opens it with the answered prompt **selected**, so keeping the same prompt is one tap and changing it is two.

**Recording**

- **The chrome falls away.** The recording view is full-bleed with no header, no progress bar and no skip; `Cancel` is the only way out and it returns to the media screen with nothing saved.
- **The prompt stays on screen for the whole take** — lower third on video, hero on voice. The user is answering a question, not performing.
- **The cap is hard**: 10 seconds video, 15 seconds voice. The progress bar fills to it, and reaching it **stops the take and shows the review screen** exactly as Stop does — never a cut-off-mid-word alert.
- **Stop always lands on review (H / J), never straight back to the card.** Stopping produces a take; only `Use this clip` / `Use this recording` produces an artefact.
- **Review plays back on demand, not automatically.** Video: the 88px glass play button over the frozen frame. Voice: the 64px sunset play pip beside the finished waveform. Multiple plays are expected and the CTA never moves.
- **The saved card's play control plays the saved clip**, and the readout (`0:08 / 0:14`) follows the playhead. Decided 25 Sep 2026. **Replays of saved media are not tracked** — no event fires, and `media_preview_played` counts plays on the review screen only.
- **`Retake` on review discards the take and returns to the viewfinder on the same prompt** — it does not reopen the prompt list.
- **An interruption ends the take.** A call, an alarm or another app taking the mic stops recording: if at least 2 seconds were captured the review screen is shown, otherwise the take is discarded and we return to the card. Never resume a half-take silently.

**The screen**

- **Accepting a take returns to the media screen** with that card filled, the prompt rendered as the read-only caption, and the other card untouched.
- **The chosen prompt is the caption on the profile.** There is no caption field anywhere in this flow and none is to be added.
- **`Retake` on a filled card** reopens the prompt list (see above) — the existing artefact stays on the profile until a new take is accepted.
- **`Delete` is immediate, no confirmation dialog**, and the card returns to empty with its previewed prompt. Deleting is not retaking: there is no replacement take.
- **Continue is never disabled**, in any state, including empty — no colour change, no toast, no gate. It stays the **orange** 52px `NextButton`; the sunset gradient is reserved for the sheet's commit CTA and the review primaries.
- **`Skip for now` and Continue do the same navigation** and are both always live. They differ only in what they record (see Tracking) — an empty Continue is a skip that the user did not call one.
- **A dropped upload never blocks the flow.** Accepting a take fills the card optimistically and uploads in the background; a failure surfaces on the card, not as a modal, and the user can leave the screen. If it is still uploading when Continue is pressed, the upload continues.
- Press feedback only, no hover: scale to 0.985 on press-down.

## Acceptance criteria

- [ ] AppHeader title is `The real you` with a back chevron in the leading slot, and `StepProgress` is **`steps={3} current={3}`** from the **one shared shell** built on screen 06 — no second copy, no fourth segment
- [ ] **The optional pill sits above the headline**, reading `Optional · you can skip this` — Manrope 800 / 11 uppercase / .07em in `--liq-primary-500` on `rgba(129,42,236,0.10)`, radius 9999, with the 12px check glyph — **not** in the subhead, and not below the cards
- [ ] Headline is Lora 700 / **30** / 1.08 / −0.018em with `hear you` as the single italic em carrying the orange wash via `.su-underlined em`
- [ ] Both empty cards render: 42px lilac icon pip (radius 13), title in Lora 700 / 17, hint in Manrope 500 / 13, the previewed prompt in a `--su-grad-lilac` block (radius 14) under the `One of 11 prompts` eyebrow, and a **full-width sunset `See the prompts` button**
- [ ] **The previewed prompt comes from `previewVideo` / `previewVoice`**, falling back to `MEDIA_PREVIEW` only when the server has sent nothing — **no ranking logic in the client**
- [ ] **There is no upload-from-library path** on either card, in any state, on either platform
- [ ] **There is no caption field anywhere in the flow**; the answered prompt renders read-only under `Prompt · shown on your profile` in Lora italic 15
- [ ] Video card, filled: 16:10 thumbnail (radius 14) with the scrim, the 56px centred play button, the duration pill bottom-left, the `Saved` chip top-right, then the caption, then the controls row
- [ ] Voice card, filled: lilac waveform strip (radius 14) with the 44px violet play pip, **48 deterministic bars** whose heights do not change between renders, the `0:08 / 0:14` tabular readout, then the controls row, then the caption
- [ ] `Retake` and `Delete` are the shared 32px `ControlPip`s — neutral and danger tones — identical on both cards
- [ ] **`Delete` takes effect immediately with no confirmation dialog**, and the card returns to its empty state with the previewed prompt
- [ ] The prompt list is **one component with a `kind` prop**, renders the eleven from `MEDIA_PROMPTS` in order plus the distinct own-idea row last, and **nothing is preselected**
- [ ] Rows are **radio-select**; tapping a row never opens the camera
- [ ] The commit CTA reads `Choose a prompt to continue` as a **disabled ghost** with no selection and `Film 10 seconds` / `Record 15 seconds` as **sunset** with one — and the screen's own Continue is **never** disabled
- [ ] Sheet chrome is the shared one — radius `32 32 0 0`, 40 × 4 grabber, 36px close X, `sheet-rise` 360ms, `max-height: 660` with the scroll mask — over a `rgba(29,17,41,0.42)` scrim with the screen visible behind
- [ ] Opening the list from a filled card's `Retake` **preselects the answered prompt**
- [ ] **The recording and review views have no `AppHeader`, no `StepProgress` and no `SkipLink`** — full-bleed, `Cancel` the only exit, and they fire their own `screen_viewed` with the §11 ids `profile_media_record` / `profile_media_review`
- [ ] **The prompt is visible for the entire take** in both media
- [ ] The cap is hard at **10s video / 15s voice**, the progress bar fills to it, and **reaching it stops the take and shows the review screen** — no alert, no truncated save
- [ ] **Stop always shows the review screen**; the artefact is created only by `Use this clip` / `Use this recording`
- [ ] Playback on review is **on demand**, repeatable, and the CTA row does not move between plays
- [ ] The saved card's play control plays the saved clip with the readout following the playhead, and **fires no tracking event**
- [ ] `Retake` on review returns to the **viewfinder on the same prompt**, not to the prompt list
- [ ] An audio-session interruption ends the take: **≥ 2s shows review, < 2s discards and returns** — never a silent resume
- [ ] **Capture runs through our own session** — no `UIImagePickerController`, no camera-app intent, no system camera UI anywhere in the flow
- [ ] **Camera and microphone are requested on the commit CTA**, not on screen entry and not on `See the prompts`
- [ ] The full permission matrix above is implemented, including **a blocked camera leaving the voice card fully functional** and a blocked microphone blocking both
- [ ] **Can-still-ask re-prompts in-app and names no toggle; blocked opens Settings and names the platform's own row** — never one label for both
- [ ] **Both statuses are re-read on every foreground**; returning from Settings with access granted lands on the working card, never on the blocked row
- [ ] **The permission alerts, the Settings page and the OS recording indicators are not implemented or specced** — no heights, no copy, no animation, no imitation
- [ ] **Continue is never disabled and never gated** — the step is completable with zero media, and pressing Continue empty fires no validation event
- [ ] `Skip for now` is the shared `SkipLink` at its canonical label, bottom-left, and Continue is the **orange** 52px `NextButton` bottom-right
- [ ] Accepting a take fills the card **optimistically**; an upload failure surfaces on the card and never blocks navigation
- [ ] Only the middle region scrolls; the footer and home indicator never move
- [ ] No absolute Y positioning anywhere
- [ ] Copy matches the strings above exactly; the eleven prompts come from the reference file unchanged, stored by `media_prompt_id`
- [ ] **Evidence of done:** attach screenshots of **all ten states** at **375 × 667, 390 × 844 and 430 × 932** — thirty images. Each must show nothing clipped and the primary action fully visible. At **390 × 844 state A must not scroll at all** — pill, headline, both cards and both CTAs visible. On 375 × 667, state what gave way and show that the **first** card's CTA is still above the fold. Include one capture per permission mode once the row is specced.

## Tracking

**The Tracking section is the first comment on this ticket, not part of this description** — Jira caps a description at 32,767 characters and the full ticket is over it. Paste `exports/profile-08-media-tracking-comment.md` as the first comment, **1:1**, and pin it. It is also committed at `design_handoff_showup/profile/tickets/08-media-tracking-comment.md`.

It is a split by length, not by authority: the comment carries the same weight as this description, its event-name table **is** the specification, and the acceptance criteria above assume it. The two rules that survive the split, because they are the ones broken in a hurry:

- **Every event on this screen carries `type: "video"|"voice"`.** Without it a tap on the voice card and a tap on the video card are the same row.
- **Never run Jira's "improve description" over either block.** It drops the tables, the file citations and the bold *missing from the current build* markers — the load-bearing parts.

## Build inventory — what this screen needs that has no pixels

Real work, each item gating one path. **A path whose item is not done ships hidden, not disabled.** It is listed here rather than split out, so it is read with the screen it belongs to.

- **Video capture pipeline** — our own session, 10s hard cap, front camera default, a frozen last frame for review and for the card thumbnail, encode + upload.
- **Audio capture pipeline** — 15s hard cap, level metering for the live waveform, a rendered waveform for the filled card, encode + upload.
- **Permission readers for camera and microphone** on both platforms, with the Android rationale flag distinguishing can-ask from blocked. One shared reader — screen 06 and the location screen need the same thing.
- **Media storage** with a per-artefact status the client can read (queued / in flight / confirmed / failed), plus delete.
- **Info.plist / manifest entitlements** — `NSCameraUsageDescription`, `NSMicrophoneUsageDescription`, `CAMERA`, `RECORD_AUDIO`.
- **The ranking job** for `previewVideo` / `previewVoice` (server) — ships separately from the screen.

## Out of scope

The details steps after this group, the profile-verification screen (dropped from the MVP), editing media after profile creation, captions, trimming / filters / beautification, transcription or subtitles, moderation and face/nudity checks on the uploads, how media renders on the profile card or in chat (that belongs to the profile-view ticket), and the ranking job's own implementation.

## Dependencies

- **Screen 07 (prompts)** — the only origin
- **The end of the group** — the destination is whatever follows "The real you"; confirm before build
- **Media upload + storage** with a readable per-artefact status, and delete
- **Camera + microphone status readers** on both platforms (shared with screen 06)
- **Stable `media_prompt_id`s** (§20) stored with each artefact — never the display string
- **`previewVideo` / `previewVoice` config values** from the server, with a documented local fallback
- **The group's header + progress shell**, built on screen 06, consumed here at `current={3}`
- **The saved flow position** — shared by the flow
- **The permission-row artboard** (see above) — not blocking the two happy paths, blocking the blocked states; every other state is drawn on the sheet
- Lora + Manrope in the app font set · `.su-underlined em` as a shared rule

## Open (not blocking)

- **The permission row is accepted as built** (25 Sep 2026) — built to the recommendation above, reviewed in QA. No spec-sheet frame will be drawn unless QA finds a problem.
- **No minimum take length is specified.** The 2-second interruption threshold above is the only number in the ticket, and it is a proposal. A one-second video is worse than none on a profile — worth deciding whether a take under ~3s can be accepted at all.
- **10 seconds and 15 seconds are unvalidated.** `stop_reason: "max_length"` is the measurement: if most takes hit the cap, the cap is the problem, and both the card titles and the commit CTAs change with it.
- **Front camera by default, with no flip control drawn.** Selfie is the obvious intent for a face clip, but a flip affordance is a one-button addition if takes show otherwise.
- **The eleven are shared between both media and that is an assumption.** Per-medium lists are a later split if the ranking shows the two diverging hard; `type` on every event is what will show it.
- **No moderation pass exists** for user-recorded video and audio entering a public profile. Out of scope here, but it must exist before media is shown to other users.
