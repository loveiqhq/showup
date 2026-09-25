# Tracking sections to paste into Jira — profile 01–04

**What "context" means here:** nothing new to write. Each block below is the finished Tracking section already in that screen's ticket file in `exports/`. This file just puts the four of them in one place so you can copy without hunting.

**What to do in Jira, per ticket:**

1. Open the ticket, edit the description.
2. Select from the `Tracking` heading down to (but not including) the next heading — `Out of scope` on 01, 02 and 04; `Out of scope` on 03 too.
3. Delete that selection and paste the matching block below.
4. On **04 only**, if you would rather not replace the whole section: the single missing row is the `screen_viewed` row. Add it as the first row of the event table and add the `screen_name` paragraph above it. Everything else in 04 is already correct.

Do **not** run Jira's "improve description" on these — it flattens the tables and drops the vocabulary notes, which are the part the developer needs.

---

## Profile 01 — Name

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **D — Profile creation**). Use them exactly — do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_name"`, `screen_name: "ProfileName"`, `referrer_screen_id` |
| Step entered | `profile_step_viewed` | `step_id: "name"`, `step_index: 1` |
| Flow entered (first step only) | `profile_build_started` | `entry_point` — once per profile build, on this screen |
| Continue with a valid name | `name_submitted` | `char_count` — **never the name itself** |
| Step completed | `profile_step_completed` | `step_id: "name"`, `time_on_step_s` |
| Continue pressed while empty | `form_validation_failed` | `field_id: "first_name"`, `rule: "required_missing"`, `screen_id`, `step_id` — fires on the **refused press**, not on render |

**Two events fire on entry, and they are not duplicates.** `screen_viewed` answers *which screen*, for navigation and for searching the analytics tool. `profile_step_viewed` answers *where in the flow*, for the completion funnel. `email_verify` is the case that proves they differ: one screen, and a step that holds at 2 of 3.

**`screen_name` / `screen_id` / `step_id` are three separate vocabularies.** `screen_name` is the readable label you search for; `screen_id` is the stable key a saved funnel binds to; `step_id` is the flow position. All three come from `tracking/enums.json` (§11 and §2) — never from a literal at the call site.

**`screen_viewed` sends three properties here, not five.** `screen_id`, `screen_name`, `referrer_screen_id`. The registry also defines `last_used` and `state` on this event and **both are omitted on every profile screen** — `last_used` means something only on a screen that offers a sign-in method list, and `state` belongs to screens whose one registry row covers many states. Since `screen_viewed` fires once on mount, `state` could only ever report the initial one. Send neither; an empty string is worse than an absent property (decision 29).

**The name is free text and is never sent** — `char_count` only, no exceptions (`requirements.json` → free-text rule).

---

## Profile 02 — Email

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **D — Profile creation**). Use them exactly — do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_email"`, `screen_name: "ProfileEmail"`, `referrer_screen_id` |
| Step entered | `profile_step_viewed` | `step_id: "email"`, `step_index: 2` |
| Continue with a valid address | `email_submitted` | `domain` — **the domain only, never the address** |
| Malformed address submitted | `email_validation_failed` | `rule: "format"` — **`format` only.** `disposable` is reserved in the enum and deliberately not implemented; see below |
| Continue pressed while empty | `form_validation_failed` | `field_id: "email"`, `rule: "required_missing"`, `screen_id`, `step_id` |
| Marketing consent row toggled | `consent_changed` | `channel: "marketing_email"`, `on: bool`, `surface: "profile_creation"` — fires in **both** directions, never opt-out only |
| Step completed | `profile_step_completed` | `step_id: "email"`, `time_on_step_s` |

**Two events fire on entry, and they are not duplicates.** `screen_viewed` answers *which screen*, for navigation and for searching the analytics tool. `profile_step_viewed` answers *where in the flow*, for the completion funnel. `email_verify` is the case that proves they differ: one screen, and a step that holds at 2 of 3.

**`screen_name` / `screen_id` / `step_id` are three separate vocabularies.** `screen_name` is the readable label you search for; `screen_id` is the stable key a saved funnel binds to; `step_id` is the flow position. All three come from `tracking/enums.json` (§11 and §2) — never from a literal at the call site.

**`screen_viewed` sends three properties here, not five.** `screen_id`, `screen_name`, `referrer_screen_id`. The registry also defines `last_used` and `state` on this event and **both are omitted on every profile screen** — `last_used` means something only on a screen that offers a sign-in method list, and `state` belongs to screens whose one registry row covers many states. Since `screen_viewed` fires once on mount, `state` could only ever report the initial one. Send neither; an empty string is worse than an absent property (decision 29).

**The address is never sent, only its domain.** A disposable-domain rate is answerable; a list of addresses is not something we collect.

**`disposable` is not implemented, and no detection is being built.** The user may enter any address they can receive mail at; if they later lose access to it, that is an app-logic problem to solve then, not a signup-time block. The rate is read from `email_submitted{domain}` in the warehouse, which needs no client work. The value stays in the `rule` enum so it need not be re-added — emitting it today is a bug.

**The marketing checkbox is the only consent on this screen, and it governs marketing mail alone.** The address is used for password resets and for support replying about a date whether or not the box is ticked — that mail is transactional, has no consent to withdraw, and is never tracked as one. An unticked box means *no marketing mail*, not *no mail*. `channel: "marketing_email"` is a distinct value from the `email` toggle on the later *Stay reachable* screen, which is a preference about being contacted regarding a match: three different uses of one address, told apart by the vocabulary (`enums.json` §8).

**The step is prefilled, never skipped.** When Apple or Google supplies an address, it is filled in and the screen is still shown, so the user can keep it or type another — so **`profile_step_skipped` never fires for `step_id: "email"`** and has been removed from the table. Whether they keep the provider's address is not tracked: the requirement is that we hold a working address, not whose it is.

**Verification always runs, whatever the origin.** A Google address is already verified by Google and an Apple relay address is deliverable but is not the user's own, so neither is trusted here — every address goes through the code on profile/03. There is no skip path to build.

---

## Profile 03 — Verify email

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **D — Profile creation**). Use them exactly — do not invent names, and do not restate a payload the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_email_verification"`, `screen_name: "ProfileEmailVerification"`, `referrer_screen_id` |
| Step entered | `profile_step_viewed` | `step_id: "email_verify"`, `step_index: 2` — **still step 2**; the bar does not advance |
| Code dispatched (server) | `email_code_sent` | `provider` |
| Code submitted | `email_code_submitted` | `attempt_no`, `result: "pass"\|"fail"` — the attempt distribution is what tells you whether the codes, the template or the copy is at fault |
| Send a new code | `email_code_resent` | `attempt_no` |
| Change email address | `profile_step_viewed` | on arrival back at `step_id: "email"` — no separate change-email event; the return rate to step 2 **is** the signal |
| Step completed (code confirmed) | `profile_step_completed` | `step_id: "email_verify"`, `time_on_step_s` |

**Two events fire on entry, and they are not duplicates.** `screen_viewed` answers *which screen*, for navigation and for searching the analytics tool. `profile_step_viewed` answers *where in the flow*, for the completion funnel. `email_verify` is the case that proves they differ: one screen, and a step that holds at 2 of 3.

**`screen_name` / `screen_id` / `step_id` are three separate vocabularies.** `screen_name` is the readable label you search for; `screen_id` is the stable key a saved funnel binds to; `step_id` is the flow position. All three come from `tracking/enums.json` (§11 and §2) — never from a literal at the call site.

**`screen_viewed` sends three properties here, not five.** `screen_id`, `screen_name`, `referrer_screen_id`. The registry also defines `last_used` and `state` on this event and **both are omitted on every profile screen** — `last_used` means something only on a screen that offers a sign-in method list, and `state` belongs to screens whose one registry row covers many states. Since `screen_viewed` fires once on mount, `state` could only ever report the initial one. Send neither; an empty string is worse than an absent property (decision 29).

**The code is never sent** — not on success, not on failure. `attempt_no` and `result` only.

**Relaunch-into-this-screen** needs no event of its own: `screen_viewed` alongside an `app_opened` with `entry_point: "cold"` in the same session answers it.

---

## Profile 04 — Date of birth

Already correct in Jira apart from the `screen_viewed` row and the `screen_name` paragraph. Full section, if you replace wholesale:

## Tracking — named events, from the taxonomy

Emit the events already specified in `design_handoff_showup/tracking/events.json`. **Do not invent event names for this screen** — the vocabulary is generated from `tracking/enums.json`, and the age-confirmation events that *used* to exist here (`age_confirm_shown` / `age_confirm_accepted`) were **deleted** in taxonomy decision 15 because they described a popup this screen does not have. The inline card is not tracked as a card; `dob_submitted` is its conversion signal.

`step_id` for this screen is **`dob`** · `field_id` for the value and its visibility control is **`age`** (both from `enums.json` §1 / §2).

**Screen name: `ProfileDoB`** · **`screen_id`: `profile_dob`** (`enums.json` §11). `screen_name` is the readable label — what you search for in the analytics tool; `screen_id` is the stable key a saved funnel binds to. **`step_id` stays `dob`:** it is the flow-position vocabulary, not a screen name. Putting `ProfileDoB` in `step_id` would collapse two vocabularies into one and break the step sequence, where `email_verify` deliberately shares step 2 with `email`.

**`screen_viewed` sends three properties here, not five:** `screen_id`, `screen_name`, `referrer_screen_id`. The registry also defines `last_used` and `state`, and **both are omitted on this screen.** `last_used` means something only on a screen offering a sign-in method list. `state` is for screens whose one registry row covers many states — and it could not carry this screen's four states in any case, because `screen_viewed` fires once on mount: the four are carried by `dob_validation_failed`, `age_gate_failed` and `form_validation_failed` (decision 29).

**`step_index` is 3 on this screen**, and the sequence is `name`=1, `email`=2, `email_verify`=2, `dob`=3. `email_verify` shares 2 deliberately — the progress bar does not advance for it. Key funnels on `step_id`, never on `step_index`.

| Event | Fires when | Payload |
| --- | --- | --- |
| `screen_viewed` | screen mounts | `screen_id: "profile_dob"`, `screen_name: "ProfileDoB"`, `referrer_screen_id` |
| `profile_step_viewed` | screen mounts (including a silent resume) | `step_id: "dob"`, `step_index` |
| `dob_submitted` | Continue accepted — state B only | `age: int` |
| `profile_step_completed` | same submit, after validation | `step_id: "dob"`, `time_on_step_s` |
| `dob_validation_failed` | 8 digits that are not a real date (state C) | `rule: "impossible"` (`"format"` if the parse fails outright) |
| `age_gate_failed` | a real date under 18 (state D) | `age: int` |
| `form_validation_failed` | Continue pressed with an incomplete date | `field_id: "age"`, `rule: "required_missing"`, `screen_id`, `step_id: "dob"` |
| `field_display_opted_out` | the visibility box is **ticked** | `field_id: "age"` |

Rules that are easy to get wrong here:

- **`form_validation_failed` fires on the refused action, not on render** — the Continue press, not the appearance of the error card. Typing three digits emits nothing.
- **`dob_validation_failed` and `age_gate_failed` are the two failure branches, and they are not interchangeable.** An impossible date is a format problem; an under-18 date is a policy outcome. Both fire on the transition into the state, once per transition — not per keystroke while the state holds.
- **`field_display_opted_out` is opt-out only** — no event on unticking. Absence of the event for `field_id: "age"` means the age is displayed. Current-state reporting comes off the `hidden_fields` user property (Properties §P3), which is written in **both** directions.
- **The age travels as `age` on `dob_submitted`; the stored user property is `age_band`** (`18-24 | 25-34 | 35-44 | 45-54 | 55+`). Do not add a raw date of birth to any payload.
- Every attribute event carries `sensitivity_class` (0 for all of the above) **and** `field_registry_version`, stamped at emit time — see the emitter rules in `design_handoff_showup/CLAUDE.md`.

What these answer, and why each one is here: whether the step converts (`dob_submitted` ÷ `profile_step_viewed`), whether `mm/dd/yyyy` is the wrong format for a locale (`dob_validation_failed` rate), whether state D catches typos or minors (`age_gate_failed` followed by a `dob_submitted` in the same session), how long the slowest field in the flow takes (`time_on_step_s`), and how many users hide their age at the moment they give it (`field_display_opted_out`).

**Not tracked, deliberately:** the `Edit` tap in the age card, and the shake. Edit is a correction inside an unsubmitted step — the signal that matters is whether the eventual `dob_submitted` differs from what was first typed, and that is not worth a T1 event.
