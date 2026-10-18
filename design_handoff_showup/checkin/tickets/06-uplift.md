# [Check-In 06] Uplift + commit

**Parent:** [Epic] 48hrs Check-In flow (`checkin/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `checkin-06-uplift-attachment.zip` (handoff folder for this ticket) · `checkin-06-uplift-spec-sheet.png`
**Blocked by:** **Check-In 05** (the step before), **Check-In 03** (`CoachNote`), **Check-In 02** (the step shell, `CheckInFooter`), **Check-In 01** (`CheckInCloseButton`, `CheckInArrow`). **Blocks:** date search — a committed Check-In is what unlocks it (epic rule 1).

## Description

Step 6 of 6 in the 48hrs Check-In (`step_id: uplift`), and the last one. The user can write one short reason to be picked over others. It shows on their profile while the Check-In is active. **`Find my date` commits the whole Check-In**: availability (02), motivation (03), vibes (04), filters (05) and this text.

**Optional.** An empty field commits like any other.

Five frames on the sheet: **A** on entry (always empty) · **B** typing, keyboard open · **C** contact info detected → blocked · **D** saving · **E** save failed.

Entered from:

- Filters **Continue** (Check-In 05) → Uplift.

Exits:

- **Find my date**, saved → **Search, date search unlocked.** No toast and no confirmation screen.
- **Back** → Filters, with the settings intact.
- **✕** → the gate (date search not available). **The text is discarded** and nothing is committed.

## Decided before build

All decided 13 Oct 2026. The kit already matches every point below.

- **100 characters** (⑧, violet). Counted as graphemes, so an emoji is one. The kit had 60. Instant's own uplift field (80) is untouched until Instant is ticketed.
- **Return inserts a new line** (⑤ ⑧). There is no Done key. The keyboard closes on a tap outside the field, and `Find my date` stays reachable above the keyboard (⑪).
- **Inspirations are not tappable** (⑩, violet). They are plain italic lines that only give ideas. A tap does nothing and nothing is copied into the field. They were drawn as pill buttons and are now text.
- **Empty is fine, with no nudge.** No confirm and no "add a reason?" prompt.
- **No emails, phone numbers or links** (⑫ ⑮). They are detected live as the user types and always get the same string: `No emails, phone numbers or links allowed.` The server repeats the check on commit.
- **The commit has two states of its own** (⑬ ⑭): *saving* and *save failed*. If the save fails, everything is kept and the same button retries. Two edge cases get **no UI**: Today's hours passing between step 02 and the commit, and the 48-hour window expiring mid-flow. The server handles both (see *Build inventory*).
- **Success goes straight to Search.**
- **`check_in_created` is the one commit event.** `check_in_completed` is **retired**. Settles the epic's open point.
- **The ambient backdrop stays** (①, violet). Uplift is the only step that has it, as the flow's "moment to shine" (epic, 13 Oct). It is decoration and sits behind the content. It never changes layout.

## Layout — read this before building

⚠ **Layout is flex, not absolute.**

**Step shell (from Check-In 02, never copied):** StatusBar (54) → AppHeader (52: back leading, title `Check-in`, `CheckInCloseButton` trailing) → content (`flex: 1`, padding `4 24 0`: StepProgress `steps={6} current={6}` mb 22 → step body `flex: 1` → `CheckInFooter` mb 22) → HomeIndicator (28), or the keyboard when it is open.

**Step body:** headline mt 8 → CoachNote mt 14 → field mt 14 → counter mt 6 → (contact-info card mt 10) → inspirations mt 22. **All of it is one scroll container** (`flex: 1`, `min-height: 0`, `overflow-y: auto`, pb 12).

- **The step body is the only flexible element and the only thing that scrolls.** Unlike 03–05, the headline scrolls with it: with the keyboard open there is no room to pin it.
- `Find my date` is pinned below the body. With the keyboard open, it sits directly above the keyboard.
- At rest on 390 × 844 nothing scrolls. At 375 × 667, and whenever the keyboard is open, the body scrolls inside itself. **Never shrink the field or the CTA to avoid it.**
- The field starts at 116 and grows with the text. Its focused position is kept in view when the keyboard opens.
- The toast and the save-failed card sit inside `CheckInFooter` above the button. Their height comes out of the body.

📎 All specs are on the attached **checkin-06-uplift-spec-sheet.png**: frames A–E and callouts ①–⑮. Violet ① ⑧ ⑩ ⑪ are the 13 Oct decisions. The violet-ringed ⑤ is OS-owned. Red ⑫ ⑭ ⑮ are failure states. The design system has colour and type tokens only and no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/checkin/screen-checkin-uplift-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. States: `<UpliftScreen/>` (A) · `<UpliftScreen uplift="…" keyboard/>` (B) · `<UpliftScreen uplift="Text me on 0176 2345 6789 …" toast={UPLIFT_CONTACT_ERROR}/>` (C) · `<UpliftScreen uplift="…" commit="saving"/>` (D) · `<UpliftScreen uplift="…" commit="failed"/>` (E). Builds `UpliftStep`, `upliftContact`, `UpliftErrorCard`, the `UPLIFT_*` strings, `CheckInSpinner`, and **adds the `loading` and `error` props to `CheckInFooter`** (a variant on the Check-In 02 component, not a fork). **Consumes** the step shell and `CheckInFooter` from Check-In 02, `CoachNote` from Check-In 03, and `CheckInCloseButton` and `CheckInArrow` from Check-In 01. A second copy of any of these is a bug.
> Shell components: `design_handoff_showup/components/shared.jsx` (`StatusBar`, `AppHeader`, `StepProgress`, `Button`, `Icon`, `HomeIndicator`) · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **The keyboard** (⑤, frame B) is a mock. **Never spec a keyboard height.** The mock's 286 + 28 at 390 × 844 is a budget note only, and the step body absorbs the difference. We request **return = new line** and sentence capitalisation. The suggestion strip, key labels and keyboard chrome are the OS's. Do not implement the mock.
- **Status bar and home indicator** are stylized mocks. Ship the platform chrome; anchor to `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.

## Copy — final strings

Header: `Check-in` · ✕ accessible label `Close Check-In`
Headline: `Give people a reason to date you over others!` — em on **you**
Coach note: `Your place to offer something special and get preferred over others! This will be highly visible on your profile.`
Field placeholder: `e.g. If we meet today, I will take you on a rafting tour on Wannsee with Picnic :)`
Field accessible label: `Your reason to be picked`
Counter: `{n} characters left` — `1 character left` at one
Inspirations label: `Some inspirations that matched well`
Inspirations, in this order, each in “ ” quotes:

1. `have 2x tickets for Eminem today`
2. `will take you out on my boat on Wannsee`
3. `need a co-pilot for my flight training today`
4. `made fresh brownies today, free to share`
5. `still looking for someone to join my dance class`

CTA: `Find my date` + arrow icon
Contact-info error (card and toast): `No emails, phone numbers or links allowed.`
Save failed: `Couldn't save your Check-in. Check your connection and try again.`

## Behaviour

**The field**
- Free text, **max 100 graphemes**. Input past 100 is not accepted, and a paste is cut at 100. The counter counts down live.
- **Return inserts a new line.** The keyboard closes on a tap outside the field.
- On save the text is **trimmed**. Whitespace-only counts as empty. Empty is stored as no uplift (null), never `""`.
- **Always empty on entry** (epic rule 3). Never pre-filled from an earlier Check-In. Back to Filters and forward again in the same flow keeps the text; ✕ discards it.

**Inspirations**
- Static text. **Not tappable** — not a button, no pressed state, nothing copied into the field. Screen readers read them as a list.

**Contact info (frame C)**
- Checked on every change: emails, phone numbers (7 or more digits, with any spaces, dashes, dots, brackets or `+`) and links (`http(s)://`, `www.`, or a bare domain with a common TLD). `upliftContact` in the reference file is the **reference rule set**; the server owns the authoritative one.
- While anything matches: the field turns danger (⑫), the error card shows under the counter, and `Find my date` **dims** (⑮). **A tap on the dimmed button still registers** and shows the same string as the toast for 2600 ms. A repeat tap restarts the timer. It is never a dead button.
- The error clears as soon as the text no longer matches. One string for all three cases; never name which one was found.

**Find my date — the commit**
- Live on an empty field and on any text without contact info.
- **Saving (D):** the spinner replaces the arrow and the label stays. The button, field, back and ✕ are inert, and the button is `aria-busy`. Double-tapping cannot send two requests.
- **Success:** navigate **straight to Search**, with date search unlocked. No toast.
- **Failure (E), network or server:** stay on the screen with **everything kept**, on this step and the earlier ones. The save-failed card shows above the button until the next tap or an edit to the field. The same button retries.
- **Server rejects the text for contact info:** show state C (field, card, dimmed CTA), not E.
- **Today's hours have passed, or the window expired, since step 02:** **no UI.** The server resolves both (see *Build inventory*) and the commit succeeds.

**Accessibility**
- The field has its label and `aria-invalid` while it holds contact info. The counter is a polite live region.
- The contact-info card and the save-failed card are `role="alert"`. The toast is a polite live region.

## Build inventory

- **Client:** `UpliftStep`, `upliftContact`, `UpliftErrorCard` (the profile error card — reuse it if the build already has it shared), the `UPLIFT_*` strings, `CheckInSpinner`, and the `loading` / `error` props on `CheckInFooter`.
- **Commit endpoint, server-side:** writes the Check-In for `window_id` from availability (both tiers per slot, 02), `motivation` (§30), `vibes` (§31), the filter settings in force (05) and `uplift` (text or null). It must be **idempotent per `window_id`**: a retry after a timeout must never create a second Check-In (epic rule 10). It fires `check_in_created`.
- **Server-side contact-info check** on `uplift`, with the same rules as `upliftContact` or stricter. A rejection returns a distinct error the client maps to state C.
- **Server-side time resolution on commit:** Today hours that have already started are **dropped silently**. If Today has none left, the Check-In is saved with Tomorrow only. If the `window_id` has expired, the commit goes into the user's current window. The user sees none of this.
- **Search:** the profile's *Uplift* card shows the text while the Check-In is active and is **hidden when `uplift` is null**. Not built here, but this story depends on it.

## Acceptance criteria

- [ ] Renders from the Check-In 02 step shell with content props only. StepProgress is `steps={6} current={6}`
- [ ] Headline Lora 700 / **30** / 1.15, one em on **you**
- [ ] The ambient backdrop renders behind the content on this step only, and never changes layout
- [ ] The field accepts at most **100 graphemes** (an emoji counts as one, and a paste is cut at 100); the counter reads `n characters left`, and `1 character left` at one
- [ ] Return inserts a new line; a tap outside the field closes the keyboard
- [ ] The field is **empty on every entry**, including a Check-In after an earlier one; ✕ discards the text; back → Filters → forward keeps it
- [ ] The five inspirations are **plain text**: no button role, no pressed state, and a tap changes nothing in the field
- [ ] An email, a phone number or a link in the field shows `No emails, phone numbers or links allowed.` under the counter, turns the field danger, and dims `Find my date`; a tap on it shows the same string as the toast for 2600 ms; the error clears when the text is clean
- [ ] An empty field commits on `Find my date` with **no** prompt or confirm
- [ ] While saving: the spinner shows, and the button, field, back and ✕ are inert; a double tap sends **one** request
- [ ] On success the app lands **directly in Search** with date search unlocked, and no toast
- [ ] On a network or server failure the save-failed card shows above the button, everything entered on 02–06 is kept, and the same button retries; the card clears on the next tap or edit
- [ ] A server-side contact-info rejection shows state C, not the save-failed card
- [ ] A commit whose Today hours have all passed **succeeds** and shows no message
- [ ] A retried commit never creates a second Check-In for the same `window_id`
- [ ] The step body is the **only element that scrolls**. At 375 × 667, and with the keyboard open, it scrolls inside itself; `Find my date` stays visible above the keyboard
- [ ] No absolute Y in the content; one `flex: 1` element (the step body)
- [ ] The keyboard mock is **not** implemented; the platform keyboard is used
- [ ] The uplift text appears in **no** tracking payload
- [ ] Copy matches the strings above exactly
- [ ] Tracking: every row in the table below fires with the payload named there, and **no invented event names**
- [ ] **Evidence of done:** screenshots of **A, B, C, D and E** at **375 × 667, 390 × 844 and 430 × 932** (fifteen images). Each shows the header and `Find my date` fully visible and nothing clipped. B is taken with the real keyboard open. On 375 × 667, say how far the body scrolls in A. Check 375 × 667 first. Add one screen recording of A → Find my date → Search.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **H — 48hr Check-In**, and `screen_viewed` in family A). Use them exactly. Do not invent names, and do not restate a payload the registry already defines. Registry **1.4.25**, taxonomy **1.4.16**.

| What | Event | Payload |
|---|---|---|
| Uplift shown | `screen_viewed` | `screen_id: "checkin_uplift"`, `screen_name: "CheckinUplift"`, `referrer_screen_id` — **NEW §11 row** |
| Commit succeeded | `check_in_step_answered` | `step_id: "uplift"` (§28), `answer_value: "written"` or `"empty"`, `time_to_answer_ms` |
| Commit saved (server) | `check_in_created` | as registered — **shipped; now the one commit event** |
| Commit did not save | `check_in_commit_failed` | `reason` (§32), `attempt`, `window_id` — **NEW, Class 0** |
| ✕ | `check_in_closed` | `step_id: "uplift"` (§28), `window_id` |

Rules that are easy to get wrong here:

- **`check_in_step_answered` fires on a successful commit, not on the tap.** A failed save followed by a retry fires it **once**. This is the one step where "accepted" means "saved".
- **`answer_value`** is `"written"` when the trimmed text is not empty, and `"empty"` otherwise. **The text itself is never sent**, in any event.
- **`check_in_created` is server-side.** The client fires nothing for the commit itself. Do not add a client `check_in_created`, and do not fire `check_in_completed` — it is **retired**.
- **`check_in_commit_failed`** fires once per request that did not save. `reason` is a §32 value: `network`, `server`, or `contact_info` (the server rejected text the client detector let through). `attempt` is 1 for the first tap on this screen view, +1 per retry.
- **The client-side contact-info block sends no request, so it fires nothing.** Neither does the dimmed tap or the toast.
- **`time_to_answer_ms`** runs from this screen's `screen_viewed` to the successful commit.
- **`referrer_screen_id`** is `checkin_filters`. Search's `screen_viewed` after the commit carries `referrer_screen_id: "checkin_uplift"`. Both are set by navigation, never hard-coded.
- **Emit order on success:** commit response → `check_in_step_answered` → navigation → Search's `screen_viewed`.
- `sensitivity_class` is **0** on every row.

**Events that must NOT fire here.**

- **Anything per keystroke.** No `uplift_typed`, `uplift_changed` or character-count event.
- **Anything for an inspiration.** They are not tappable; do not invent `inspiration_tapped`.
- **Anything for the contact-info block.** Do not invent `uplift_blocked` or `contact_info_detected`.
- **`uplift_shown`** — **retired**. It described a streak card no screen draws. Do not reuse the name.
- **`check_in_completed`** — **retired** in favour of `check_in_created`.
- **`profile_step_viewed` / `profile_step_completed`.** These are not profile steps.

**Screen-to-screen conversion.**

| Edge | Numerator | Denominator |
|---|---|---|
| Filters → Uplift | `screen_viewed` · `checkin_uplift` · referrer `checkin_filters` | `check_in_step_answered` · `step_id: "filters"` |
| Uplift → committed | `check_in_created` | `screen_viewed` · `checkin_uplift` |
| Check-Ins with an uplift | `check_in_step_answered` · `step_id: "uplift"` · `answer_value: "written"` | all `check_in_step_answered` · `step_id: "uplift"` |
| Commit failure rate | `check_in_commit_failed` | `check_in_created` + `check_in_commit_failed` |
| Closed on Uplift | `check_in_closed` · `step_id: "uplift"` | `screen_viewed` · `checkin_uplift` |
| Whole flow | `check_in_created` | `check_in_started` + `check_in_gate_tapped` with `intro_passed: true` |

**Registry changes made for this ticket (1.4.25 / taxonomy 1.4.16):** §11 `checkin_uplift` **NEW** · §28 `uplift` confirmed · §32 `check_in_commit_failure` **NEW** (`network` · `server` · `contact_info`) · `check_in_commit_failed` **NEW** · `check_in_step_answered` trigger extended for `uplift` · `check_in_created` named the one commit event · `check_in_completed` **RETIRED** · `uplift_shown` **RETIRED**.

**Missing from the current build:** every client event above is `implemented: false`. `check_in_created` already ships; its trigger is unchanged and it now fires from the commit on this screen. `check_in_completed` was never implemented, so nothing migrates.

## Out of scope

Search itself, beyond landing there and hiding the Uplift card when it is empty. Instant's uplift field (80 characters, its own flow). Editing a committed Check-In. Content moderation beyond contact info (offensive text, reporting). Notifications after a commit.

## Dependencies

- **Check-In 05:** the step before, and the filter settings committed here.
- **Check-In 03:** `CoachNote`.
- **Check-In 02:** the step shell and `CheckInFooter` (this story adds two props).
- **Check-In 01:** `CheckInCloseButton` and `CheckInArrow`.
- **Server: the commit endpoint**, idempotent per `window_id`, with the contact-info check and the time resolution above.
- **Search:** date search unlocked by a committed Check-In; the Uplift card hidden when empty.
- **A `window_id`** from the server window opened at profile completion.
- **Tracking registry 1.4.25 / taxonomy 1.4.16.**

## Open (not blocking)

- ~~**Inspiration copy.**~~ **Kept as written** (13 Oct 2026). The Berlin references (Wannsee) and the artist name are fine for now. Revisit before any other city or language.
- **Social handles and spelled-out numbers** (`@name`, `insta: name`, "zero one seven six…") are not caught by the agreed rule. Extend the detector, or leave them to later moderation?
- **Search's `screen_id`** for the `referrer_screen_id` row comes from the Search ticket.
