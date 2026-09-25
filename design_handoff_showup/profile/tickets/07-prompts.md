# [Profile 07] Prompts — the written answers

**Parent:** Profile creation flow (SHOWUP-TBD — epic `profile/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `profile-07-prompts-spec-sheet.png`

## Description

The first screen in the flow that asks the user to **write something about themselves**, and the one where a profile-creation funnel typically bleeds. Everything before it is a fact they already know (name, email, date of birth) or a thing they already have (photos). This screen asks them to be interesting on demand, and that is a different kind of ask.

The user writes up to **3 prompts** — a topic plus an answer of at most **160 characters**. **One is enough to continue.**

It is **step 2 of 3 of "The real you"** — photos → prompts → media — entered from screen 06 and leaving to screen 08.

**Eight states, one screen, one ticket.** They are variants of one layout column plus the two sheets it opens; the sheets are folded in here because neither has its own entry point (handoff rule: *fold a modal into its host ticket when it is an ending of the same attempt*).

| | State | Prop |
| --- | --- | --- |
| A | 0 of 3 — arrival | `prompts={[]}` |
| B | 1 of 3 saved | `prompts={[one]}` |
| C | 3 of 3 saved | `prompts={three}` |
| D | Topic sheet — browse all | `sheet="topic"` |
| E | Write sheet — empty | `sheet="write" sheetVariant="empty"` |
| F | Write sheet — mid-draft | `sheet="write" sheetVariant="mid"` |
| G | Write sheet — at the cap | `sheet="write" sheetVariant="cap"` |
| H | Write sheet — Save pressed while empty | `sheet="write" sheetVariant="nudge"` |

## Decided before build — read this first, it changes two other tickets

**1 · The progress bar in this group is 3 segments, not 4.** Verify profile is **out of the MVP**, so "The real you" is **photos (1) → prompts (2) → media (3)**. This screen is `steps={3} current={2}`.

**This is not local to this ticket.** The group's header + progress shell is built once and consumed by all three screens, so:

- **Profile 06 (photos)** changes from `steps={4} current={1}` to `steps={3} current={1}`. Its acceptance criterion naming `steps={4}` is superseded by this ticket.
- **Media** will be `steps={3} current={3}`.
- `ScreenProfileVerify` is **not built** and gets no segment. If profile verification returns post-MVP it comes back as a fourth segment, and the shell takes the new count as a prop — nobody edits three screens.

**2 · The conversion pass of 13 Sep 2026 supersedes the earlier prompts artboards.** Five changes, each marked with a **violet callout** on the spec sheet, each replacing something a previous mock showed. They are the point of the ticket, not polish:

| Element | Was | Is | Callout |
|---|---|---|---|
| Getting to the first word | Tap `Choose your 1st prompt` → pick 1 of 15 → empty box | **Three suggested topics on the screen; tap one and write.** The sheet becomes `Browse all 15 topics` | ⑥ ⑦ |
| Topic list | 15 in one flat scroll | **Three named groups of five**; used topics disabled | ⑯ ⑰ |
| Write sheet | Topic, placeholder, empty box | Topic, **worked example**, box | ⑲ |
| Length framing | `0/160` from the first keystroke | **"One good sentence is enough"** always; counter from 100 | ㉑ ㉒ |
| At 160 characters | Red border, danger card, Save disabled | **Amber line, Save live** | ㉔ ㉕ |
| Save while empty | Greyed out | **Live; the press explains** (state H) | ㉓ ㉖ ㉗ |
| Closing mid-draft | Text discarded silently | **Draft kept, restored on reopen** | ㉓ |

**Do not build the old flow and add the suggestions later.** The suggestion cards are what removes a tap and a sheet from the path to the first word; bolted on afterwards they become a fourth thing on a screen that already had two.

## Layout — read this before building

⚠ **This screen scrolls, and only its middle scrolls.** StatusBar (54, fixed) → AppHeader (52, fixed) → **scrolling middle** (`flex: 1`, `overflow: auto`, padding `4 24 0`) → sticky footer (`flex: none`) → HomeIndicator (28, fixed).

⚠ **The progress bar scrolls with the content here**, unlike photos where it sits in the fixed region. That is deliberate — this screen carries more above the fold and the bar is not worth the 26px. Do not "fix" it into a third fixed row.

⚠ **No absolute Y positioning.** Both sheets dock to the bottom edge; neither is positioned by a top offset.

**The status row inside the write sheet is reserved, not conditional** — `min-height: 34`, `aria-live="polite"`, carrying the floor line in the calm state. The field, Save and the sheet height must not move between calm, cap and empty-submit.

📎 All specs are on the attached **profile-07-prompts-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–㉗ across the eight frames, with the conversion changes in **violet** and the one real failure state in **danger red**. The sheet also carries the full *what changed and what it replaces* table. Note the design system has colour and type tokens only — there is no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/profile/screen-prompts-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. State props are in the table above.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **The keyboard.** It opens with the write sheet and the field takes focus immediately. The artboards draw the sheet **without** a keyboard so the sheet's true content height is visible. **Never spec a keyboard height and never build a mock one** — but do verify that the example, the field, the status row and Save are all above the keyboard at 375 × 667.
- **Dictation.** The mic on the platform keyboard is the OS's; we neither add nor style a voice-input control. Speaking an answer already works because the field is an ordinary text input — do not build a second path to the same thing.
- **Copy/paste and autocorrect**, including the paste that lands over the 160 limit. Slice silently; do not alert.

## Copy — final strings

Section title (AppHeader): `The real you`
Headline: `Your space to share something personal.` — "personal" italic, with the orange underline wash
Sub copy: `One is enough to continue. Add up to 3 if you're enjoying yourself.`
Section label, 0 saved: `Start with one of these`
Section label, 1–2 saved: `Add another · optional`
Suggestion card action: `Write this`
Browse control: `Browse all 15 topics`
Counter, 0 saved: `0/3 prompts`
Counter, 1–2 saved: `1/3 prompts · enough to continue`
Counter, 3 saved: `3/3 prompts`
CTA: `Continue`
Toast (Continue pressed at 0): `Write 1 prompt to continue`

**Topic sheet (D):**
Headline: `Choose a topic.` — "topic" italic with the wash
Sub: `Pick something you'd want a match to actually know.`
Group labels: `Dating me` · `Me in real life` · `Opinions & obsessions`
Used-topic marker: `Used`

**Write sheet (E–H):**
Example eyebrow: `FOR EXAMPLE`
Field placeholder: `Say it like you'd tell it to a friend…`
Floor line (calm): `One good sentence is enough.`
Cap line (G): `That's the full 160 — short and specific lands harder anyway.`
Empty-submit line (H): `Write a few words to save this prompt.`
CTA: `Save`

**The 15 topics and the 15 examples are in the reference file** (`TOPIC_GROUPS`, `PROMPT_EXAMPLES`) — they are content, not layout, and the reference file is the source. Do not retype them into the code; import or copy them wholesale, and keep the group order.

The requirement is stated three times on purpose — as a sentence in the sub copy, as a number in the counter, and in the toast on refusal. Do not remove any of the three. Product terms capitalise exactly: **Show Up**.

## Behaviour

**Getting to a prompt**

- **Tapping a suggestion card opens the write sheet directly on that topic.** No intermediate sheet.
- **`Browse all 15 topics` opens the topic sheet (D).** Picking a topic there replaces the sheet with the write sheet on that topic — the two sheets never stack.
- **Suggestions never repeat a used topic.** At 0 saved the screen shows three; at 1–2 saved it shows two, drawn from the first unused entries of `SUGGESTED_TOPICS`, falling through to group order if all three are used.
- **A used topic is shown disabled in the sheet, not removed.** The list keeps its length and order between visits.
- **A prompt can be edited** via the pencil pip on a saved card: the write sheet reopens on that topic **with the saved text already in the field**, and Save overwrites. Editing does not consume a slot.

**Writing**

- **The example is per sheet, not per session.** Dismissing it hides it for that sheet only; the next prompt shows it again.
- **The counter appears at 100 characters** and not before. Below 100 there is no numeral on screen.
- **The cap is hard at 160**: input is sliced, typing past it does nothing, and there is no alert. A paste over the limit is truncated silently.
- **Reaching 160 is not a failure.** Amber border, amber counter, the cap line, and **Save stays live** — the text saves as-is.
- **Save is never disabled.** Pressing it on an empty field produces state H — danger border, one-shot `su-shake` 480ms, and the message. Whitespace-only counts as empty. The error clears on the **first character typed**, not on blur or re-press, and does not re-fire until Save is pressed again.
- **The draft survives dismissal.** Closing the sheet by X, scrim tap or swipe keeps what was typed and restores it when that topic is reopened. Only Save writes a prompt.
- **Save closes the sheet and the new card appears at the bottom of the list**, not the top — the reading order stays chronological.

**The screen**

- **Continue is never disabled.** Below one prompt the press fires the toast (2600ms, fade + 6px rise over 200ms) and submits nothing. It does not change colour and does not move.
- **The second and third prompts never block anything.** The "Add another · optional" block is a nudge: no colour change, no toast, no gate.
- **At 3 saved the suggestion block and browse button are removed**, not disabled.
- **A saved prompt is never truncated on this screen** — the card grows to fit its answer.
- Press feedback only, no hover: scale to 0.985 on press-down.

## Acceptance criteria

- [ ] **`StepProgress` is `steps={3} current={2}`** — and the photos screen is updated to `steps={3} current={1}` in the same sprint, from the one shared shell
- [ ] `ScreenProfileVerify` is not built, is not routed to, and has no segment anywhere in the group
- [ ] AppHeader title is `The real you` with a back chevron in the leading slot
- [ ] Headline is Lora 700 / **30** / 1.08 / −0.018em with `personal` as the single italic em carrying the orange wash via `.su-underlined em`
- [ ] Sub copy reads exactly `One is enough to continue. Add up to 3 if you're enjoying yourself.` — Manrope 500 / 14.5 / 1.45 in `--liq-neutral-200`, max-width 330
- [ ] **Three suggestion cards are on the screen at 0 saved**, each rendering its topic in **Lora 700 / 16.5** with the `Write this` action row — and **tapping one opens the write sheet directly on that topic**, with no intermediate sheet
- [ ] The section label reads `Start with one of these` at 0 saved and `Add another · optional` at 1–2 saved, Manrope 800 / 10.5 uppercase / .08em in `--liq-fg-subtle`
- [ ] `Browse all 15 topics` is a **transparent 48px outlined pill**, visibly quieter than a suggestion card, and opens the topic sheet
- [ ] The topic sheet renders the 15 topics in **three labelled groups of five** in the order given by `TOPIC_GROUPS`
- [ ] **A used topic renders disabled with the word `Used`** — never hidden, and the list order never changes
- [ ] Counter row reads `0/3 prompts` in `--liq-fg-subtle`, and from the first save `1/3 prompts · enough to continue` in **`--liq-success-fg`**, on one line at 390 (`white-space: nowrap`)
- [ ] Saved prompts render as lilac cards (`--su-grad-lilac`, radius 18) with the topic in Lora 700 / 16 and the answer **never truncated**
- [ ] The edit pip reopens the write sheet **with the saved text in the field**, and saving overwrites that prompt rather than adding one
- [ ] **The write sheet carries a worked example above the field** — `FOR EXAMPLE` eyebrow, lavender card, dismissible — and the example is **not** implemented as placeholder text
- [ ] Dismissing the example affects that sheet only; the next prompt shows it again
- [ ] The field is `rows={4}` / `min-height: 116` so **160 characters fit without an inner scrollbar**
- [ ] **No character counter is rendered below 100 characters**; from 100 it is `--liq-fg-subtle` and at 160 `--liq-orange-500`, tabular-nums
- [ ] The status row is **reserved at `min-height: 34`** with `aria-live="polite"` and carries `One good sentence is enough.` in the calm state — the field, Save and sheet height do not move between the three messages
- [ ] **At 160 the treatment is amber, not danger** — `--liq-orange-500` border, `rgba(254,104,57,0.12)` halo, the cap line — **no danger card, no red, and Save stays live**
- [ ] **Save is never disabled.** An empty press produces the danger border, one-shot `su-shake` 480ms and `Write a few words to save this prompt.`
- [ ] The empty-submit error clears on the first character typed and does not re-fire until Save is pressed again; whitespace-only counts as empty
- [ ] **A draft survives dismissing the sheet** by X, scrim or swipe, and is restored when that topic is reopened
- [ ] Save is the **sunset** `Button` (the screen's own CTA stays **orange**)
- [ ] Both sheets use the shared chrome — radius `32 32 0 0`, 40 × 4 grabber, 36px close X, `sheet-rise` 360ms — over a `rgba(29,17,41,0.42)` scrim with the screen visible behind
- [ ] **Continue is never disabled**; below one prompt the press fires the toast `Write 1 prompt to continue` for 2600ms, positioned at `bottom: 100%` of the footer so the footer's geometry is identical when idle
- [ ] At 3 saved the suggestion block and browse button are **absent from the DOM**
- [ ] Only the middle region scrolls; the CTA is always in the viewport
- [ ] No absolute Y positioning anywhere
- [ ] **The keyboard is not built or specced** — the platform keyboard opens with the sheet and the field takes focus
- [ ] Copy matches the strings above exactly; the topics and examples come from the reference file unchanged
- [ ] **Evidence of done:** attach screenshots of **all eight states** at **375 × 667, 390 × 844 and 430 × 932** — twenty-four images. Each must show nothing clipped and the primary action fully visible. On 375 × 667, show that **the first suggestion card is above the fold** on state A, and that in the write sheet the example, the field, the status row and Save are all **above the keyboard**.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **E — Profile Photos/Media**). Use them exactly — do not invent names, and do not restate a payload the registry already defines. The nine `prompt_*` events were defined on 13 Sep 2026, before this ticket; the **three changes of 16 Sep 2026** (taxonomy 1.4.1 / registry 1.4.2) answer the implementation questions raised on this screen and are marked **NEW** below.

> **Changed 25 Sep 2026 (taxonomy 1.4.4 / registry 1.4.5) — `entry_point` and `position` are removed from every prompt event.** We measure **which topics are chosen** (`topic_id`), not whether the tap came from a suggestion card or the browse sheet. Enums §18 is retired. `prompt_editor_dismissed` gains `is_edit`, the one job `entry_point: "edit"` did that nothing else carried. Nothing that is already built needs removing — the property was never implemented.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_prompts"`, `screen_name: "ProfilePrompts"`, `referrer_screen_id` — §11 |
| Step entered | `profile_step_viewed` | `step_id: "prompts"`, `step_index: 2` — §2 |
| **Topic chosen** — suggestion card or browse sheet, not distinguished | `prompt_topic_selected` | `topic_id` (§17), `topic_group`, **`selection_index`** — **NEW property**; `selection_index: 1` is the topic the user reached for first |
| Write sheet opened | `prompt_editor_opened` | `topic_id`, `is_edit`, `prompt_count` |
| Browse-all opened | `prompt_topic_list_opened` | `used_count`, `screen_id` |
| **Browse sheet cancelled** | `prompt_topic_list_dismissed` | **NEW event** — `dismiss_method` (§23), `used_count`, `time_on_sheet_s`, `screen_id` |
| Prompt saved | `prompt_saved` | `topic_id`, `topic_group`, `is_edit`, `length_bucket` (§19), `prompt_count` |
| Write sheet closed without saving | `prompt_editor_dismissed` | `topic_id`, **`is_edit`** (**NEW**, replaces `entry_point: "edit"`), `had_draft`, `draft_length_bucket`, `dismiss_method` (§23 — **values changed**) |
| Edit pencil tapped | `prompt_editor_opened` | `is_edit: true`, `topic_id`, `prompt_count` — **and NOT `prompt_topic_selected`** |
| Example dismissed | `prompt_example_dismissed` | `topic_id` |
| 160 characters reached | `prompt_char_limit_reached` | `topic_id` — once per editor session, not per keystroke |
| Requirement met | `prompts_minimum_met` | `count`, `topic_id` — fires once, on the **first** save |
| **Continue pressed at 0 prompts** | `form_validation_failed` | `field_id: "prompts"`, `rule: "prompts_below_minimum"` (**NEW value**), `screen_id`, `step_id` — on the **refused press**, not on render |
| **Continue accepted** | `profile_step_completed` | `step_id: "prompts"`, `time_on_step_s`, **`prompt_count`** — **NEW property**, 1–3, required on this step |

`prompt_deleted` is registered too, but **is not reachable from this screen** — there is no delete affordance in profile creation. It exists so the profile-editing flow cannot invent a second name for the same act.

**One name, one act — a collision resolved on 16 Sep 2026, and it matters if you grep.** `events.json` contained **two** definitions of `prompt_topic_selected`: the family E row above, and a family F row from the v1.0 taxonomy carrying `topic_id` only. Family F also held a whole parallel vocabulary for the written prompts — `prompt_topic_picker_opened`, `prompt_answered`, `prompt_edited`, `prompt_removed`. **Family E wins**, because it is the set this ticket specifies and the one with §17 / §19 behind it. The duplicate row was **deleted** from family F; the other four are kept, emptied of their payloads, and marked **SUPERSEDED — DO NOT FIRE**, each naming its family E replacement. **Nothing on this screen may fire a `prompt_*` event from family F**, and the registry now contains no duplicate event name anywhere.

### The seven questions this screen has to answer, and the row that answers each

1. **Which prompt did the user click on first?** `prompt_topic_selected` with **`selection_index: 1`**. Do not answer this by sorting timestamps: the first topic *opened* and the first topic *saved* are different rows and the difference is the point.
2. **Continue pressed with nothing written.** `form_validation_failed{field_id:"prompts", rule:"prompts_below_minimum"}`, on the press, once per press. Continue is never disabled on this screen, so this event is the **only** record that the user tried to leave.
3. **Which topics are chosen.** `prompt_topic_selected{topic_id, topic_group}`, counted across the suggestion cards and the browse sheet together. **Where the tap happened is deliberately not recorded** (25 Sep 2026).
4. **Cancel in "Choose a topic".** `prompt_topic_list_dismissed` — new, because the write sheet had an abandonment event and the browse sheet did not. Picking a topic fires `prompt_topic_selected` instead; the two are **mutually exclusive**, so `prompt_topic_list_opened` = selected + dismissed and the browse sheet's funnel closes.
5. **Save in the write sheet.** `prompt_saved`, carrying `topic_id` and `length_bucket` instead of the text.
6. **Edit after a prompt was added.** `prompt_editor_opened{entry_point:"edit", is_edit:true}` on the pencil, then `prompt_saved{is_edit:true}` on the overwrite or `prompt_editor_dismissed{is_edit:true}` on the back-out. **`prompt_topic_selected` must not fire on an edit** (registry change, 16 Sep) — an edit is not a fresh choice of topic, and firing it there inflates the topic-demand chart with re-edits of prompts already written.
7. **How many prompts on Continue.** `profile_step_completed{step_id:"prompts", prompt_count}` — 1 to 3, the count at the moment Continue was accepted. The step completing said nothing about how much was written before this property existed. `prompt_count` on `prompt_saved` gives the same number per save; the one on Continue is the one that goes in the funnel.

**Registry status of the Prompts screen and step index — checked, nothing to change.** `profile_prompts` / `ProfilePrompts` is registered in §11, and §2 has `prompts` at **`step_index: 2`** of "The real you" (photos 1, prompts 2, `media_voice` / `media_video` 3) — corrected in registry 1.3.1 and re-verified against the current flow on 16 Sep 2026. **If your code reads `step_index` for prompts as 10, it is reading the pre-1.3.1 registry.** Pull `tracking/enums.json` at `registry_version: 1.4.5`.

**`screen_name` / `screen_id` / `step_id` are three separate vocabularies** and all three come from `tracking/enums.json` (§11 and §2) — never from a literal at the call site.

**Rules for this screen's payloads:**

- **`topic_id` is a stable id from §17, never the display string.** A copy edit to a topic must not orphan the prompts written under it or split it into two rows in the analytics tool. The same id is what the prompt row stores in the database.
- **Never send the answer text, and never send the draft.** Length goes as `length_bucket` / `draft_length_bucket` (§19) and nothing else. These are user-written sentences about themselves destined for a public profile; they have no business in the analytics pipeline.
- **No prompt event carries `entry_point` or `position`.** §18 is retired; the app-open `entry_point` on `app_opened` is a different property and is unaffected.
- **`dismiss_method` is now one canonical set for every sheet** — `close · backdrop · swipe · system_back` (§23). The write sheet's values changed: **`scrim` → `backdrop`, `back` → `system_back`**. `close` is the X. `system_back` is Android only and is not the same act as the X.
- **`selection_index` counts topic selections in one visit to the step**, starting at 1, and does not reset when a sheet closes. An edit does not increment it.
- **`is_edit` must be honest.** Editing an existing prompt does not consume a slot and must not be counted as a new prompt; `prompt_count` is the count *after* the action.
- **`prompts_minimum_met` fires once**, on the first save, and carries the topic that got the user there.
- **One event per act.** The topic sheet resolving into the write sheet is one selection, not a dismissal plus a selection; a re-press of Save on an empty field is a second `form_validation_failed`-style refusal only if it is a new press.

**Tracking acceptance criteria**

- [ ] `prompt_topic_selected` carries `selection_index`, 1-based per visit to the step, and **does not fire when the write sheet is opened from the edit pencil**
- [ ] `prompt_topic_list_dismissed` fires on the X, the scrim, a swipe-down and the Android back gesture of the topic sheet, and **never** when a topic was picked
- [ ] `prompt_editor_dismissed` and `prompt_topic_list_dismissed` use the §23 values — no `scrim`, no `back`, no `cancel`
- [ ] Continue at 0 prompts fires `form_validation_failed{field_id:"prompts", rule:"prompts_below_minimum"}` once per refused press, alongside the toast
- [ ] Continue at 1–3 prompts fires `profile_step_completed{step_id:"prompts", prompt_count}` with the true count
- [ ] `profile_step_viewed` carries `step_index: 2`, read from the registry rather than a literal
- [ ] No event on this screen carries answer text or draft text, in any property
- [ ] **No `prompt_*` event carries `entry_point` or `position`**, and `prompt_editor_dismissed` carries `is_edit`

**Currently in the build: none of it.** Every `prompt_*` row is `Not built` in `events.json` and ships with this ticket, as does `form_validation_failed` for this screen.

## Out of scope

Media (video + voice) and the details steps, the profile-verification screen (dropped from the MVP), prompt **editing after profile creation**, moderation or profanity filtering of answers, translation, prompt ordering by drag, and how prompts render on the profile card itself (that belongs to the profile-view ticket).

## Dependencies

- **Screen 06 (photos)** — the only origin, and it changes to `steps={3}` with this ticket
- **Media** — the destination
- **Prompt persistence** — up to 3 rows of `{topic_id, answer}` per account, with the answer capped at 160 characters server-side as well as in the client
- **A stable topic id per topic.** Store the id, not the display string, or every copy edit orphans existing prompts
- **Draft storage for the in-progress answer**, surviving sheet dismissal and app backgrounding within the session
- **The nine `prompt_*` events**, already defined in `tracking/events.json` — no new definitions are needed, but the value sets in §17 and §19 must be read from the registry rather than retyped
- **The group's header + progress shell**, built on screen 06 and consumed here at `current={2}`
- **The saved flow position** — shared by the flow
- Lora + Manrope in the app font set
- `.su-underlined em` shipped as a shared rule

## Open (not blocking)

- **The three suggested topics are fixed, not personalised.** Rotating or randomising them is a later experiment.
- **160 characters is unvalidated.** It is short enough to be answerable and long enough to say something, but nobody has measured completion against it. The counter threshold (100) moves with it if it changes.
- **1 required is deliberately the lowest honest bar.** If prompt count turns out to predict match quality, the nudge at 1 saved is the lever to pull before the requirement is — raising the requirement is the change most likely to cost completions.
- **No profanity or moderation pass exists** for user-written text entering a public profile. Out of scope here, but it has to exist before prompts are shown to other users.
