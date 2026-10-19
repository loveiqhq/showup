# [Profile 07] Prompts — tracking update (paste as a comment on the ticket)

Answers to the three questions from the Prompts build, plus the registry changes that came out of them. **The ticket description has already been updated** — its Tracking section is the specification and this comment is the change note, so implement from the description and use this to see what moved. Description: `exports/profile-07-prompts-ticket.md` · committed at `design_handoff_showup/profile/tickets/07-prompts.md`.

Registry: taxonomy **1.4.1** / `enums.json` `registry_version` **1.4.2**, 16 Sep 2026. Pull the files before you start — `design_handoff_showup/tracking/`.

---

## 1 · Suggested topic vs. "Browse all topics" — yes, and it was already separable

`entry_point` (§18) on every topic event: **`suggestion`** = one of the three cards on the screen, **`browse`** = a row in the "Choose a topic" sheet, **`edit`** = the pencil on a saved prompt. Closed set, passed through from the control that was tapped — never inferred from whether a sheet happened to be open. `prompt_topic_list_opened` is the counterweight: how often the curated three were not enough.

Two things were missing and are now in the registry:

- **`selection_index`** on `prompt_topic_selected` — 1-based count of topic selections in one visit to the step. **`selection_index: 1` is the topic the user reached for first**, which is a different question from which topic they saved, and it is the one the three cards are accountable for. Does not reset when a sheet closes; an edit does not increment it.
- **`prompt_topic_selected` no longer fires on an edit.** Reopening a saved prompt is not a fresh choice of topic, and firing it there inflated topic demand with re-edits. The edit tap is `prompt_editor_opened{entry_point:"edit", is_edit:true}`, and that is now the only event an edit fires at open.

## 2 · Opened the sheet and closed it without saving — yes, and there are now two of those

- **Write sheet** — `prompt_editor_dismissed`, defined 13 Sep, with `had_draft` and `draft_length_bucket`. This is the drop-off the screen was designed against: *opened and closed empty* is a topic problem, *wrote half and left* is a writing problem, and they are different fixes.
- **Topic sheet — NEW: `prompt_topic_list_dismissed`.** The browse sheet had no abandonment event while the write sheet did, so a user who opened all fifteen topics and backed out was invisible. Payload `dismiss_method` (§23), `used_count`, `time_on_sheet_s`, `screen_id`. It fires **only** when no topic was picked — picking one replaces the sheet with the write sheet and fires `prompt_topic_selected` instead. The two are mutually exclusive, so `prompt_topic_list_opened` = selected + dismissed and the sheet's funnel closes.

**`dismiss_method` values changed — this one will bite if you have already written it.** One canonical set for every sheet in the product, §23: **`close · backdrop · swipe · system_back`**. The write sheet's `scrim` → **`backdrop`**, `back` → **`system_back`**; the media prompt list's `cancel` → **`close`**. Three spellings of the same four acts had drifted into the registry. Nothing had shipped, so nothing in the warehouse needed migrating — but a sheet-close value outside this set is now a bug at the call site.

## 3 · Prompts screen and step indexes — already correct, nothing for you to change

- **§11** registers `profile_prompts` / `ProfilePrompts` — **one `screen_id` for all eight states, both sheets included.** The sheets have no entry point of their own and are not separate screens.
- **§2** has `prompts` at **`step_index: 2`** of "The real you": **photos 1 · prompts 2 · `media_voice` / `media_video` 3.** It was mis-slotted at 10 under "Share some details" and was corrected in registry 1.3.1, before this ticket; re-verified against the current flow today. **If your code reads `step_index` for prompts as 10, it is reading the pre-1.3.1 registry** — pull `enums.json` at `registry_version: 1.4.2`.
- Verify profile is out of the MVP, which is why the group is three steps and the bar is `steps={3} current={2}`. Photos moves to `steps={3} current={1}` in the same sprint, from the one shared shell.

Read `step_id`, `step_index`, `screen_id` and `screen_name` from the registry row, never from a literal at the call site — they are four separate vocabularies and `screen_name` is the only one that may ever be edited.

---

## The seven things you asked to be able to see in the funnel, and the row that gives each

| # | Question | Event and payload |
|---|---|---|
| 1 | Which prompt was clicked **initially** | `prompt_topic_selected{selection_index: 1, entry_point, position, topic_id}` |
| 2 | Continue pressed with **no prompt yet** | `form_validation_failed{field_id:"prompts", rule:"prompts_below_minimum", screen_id, step_id}` — **new `rule` value**, on the refused press |
| 3 | Which topic came out of "Choose a topic" | `prompt_topic_selected{entry_point:"browse", position, topic_group}` |
| 4 | **Cancel** in "Choose a topic" | `prompt_topic_list_dismissed{dismiss_method, used_count, time_on_sheet_s}` — **new event** |
| 5 | **Save** in the write sheet | `prompt_saved{topic_id, entry_point, is_edit, length_bucket, prompt_count}` |
| 6 | **Edit** after a prompt was added | `prompt_editor_opened{entry_point:"edit", is_edit:true}` → `prompt_saved{is_edit:true}` or `prompt_editor_dismissed{entry_point:"edit"}` |
| 7 | **How many prompts** at Continue | `profile_step_completed{step_id:"prompts", prompt_count}` — **new property**, 1–3, required on this step |

Two notes on that table:

- **`form_validation_failed` is the only record that a user tried to leave.** Continue is never disabled on this screen — the press fires a toast — so if this event is not emitted, "pressed Continue and could not" does not exist in the data.
- **`prompt_count` on Continue is the funnel number.** `prompt_saved` carries the same count per save, but the step completing said nothing about how much had been written until this property existed. `is_edit` must be honest: an edit does not consume a slot, and `prompt_count` is always the count *after* the action.

## Unchanged, and non-negotiable

- **`topic_id` is a stable id from §17, never the display string.** It is what the prompt row stores in the database; a copy edit to a topic must not orphan the prompts written under it or split one topic into two rows in the analytics tool.
- **Never send the answer text and never send the draft.** Length goes as `length_bucket` / `draft_length_bucket` (§19) and nothing else. These are sentences a user wrote about themselves for a public profile; none of it belongs in the analytics pipeline.
- **`prompts_minimum_met` fires once**, on the first save, carrying the topic and entry point that got the user there. That pairing is the single most useful row on the screen.

## One more thing found while updating the registry — a duplicate event name

`prompt_topic_selected` was defined **twice** in `events.json`: the family E row this ticket specifies, and a family F row from the v1.0 taxonomy with `topic_id` only. Family F also carried a parallel vocabulary for the same screen — `prompt_topic_picker_opened`, `prompt_answered`, `prompt_edited`, `prompt_removed` — the mirror image of `prompt_topic_list_opened`, `prompt_saved`, `prompt_editor_opened`, `prompt_deleted`.

**Resolved: family E wins.** It is the set this ticket cites and the one with §17 / §18 / §19 behind it. The duplicate row is **deleted** from family F — a name cannot mean two things. The other four are kept, emptied of their payloads, and marked **SUPERSEDED — DO NOT FIRE**, each naming its family E replacement, so the name resolves to a notice instead of being built from a stale spec.

**What this means for you:** if you grepped `prompt_topic_selected` before today you may have seen the one-property version. The correct payload is `topic_id`, `topic_group`, `entry_point`, `position`, `selection_index`. Fire nothing from family F on this screen. The registry now has **no duplicate event name anywhere** — 296 events, and the count went *down* by one because a duplicate was removed while an event was added.

## Registry delta, for the record

**`events.json`** — taxonomy 1.4.1, 296 events (297 minus the deleted duplicate):

1. `prompt_topic_list_dismissed` — new.
2. `prompt_topic_selected` — `selection_index` added; scoped to `suggestion` / `browse`.
3. `prompt_editor_opened` — trigger now states it is the edit event.
4. `profile_step_completed` — `prompt_count` added, optional and screen-scoped, required on `step_id: "prompts"`.
5. `form_validation_failed` — `rule` gains `prompts_below_minimum`; trigger names the prompts case.
6. `prompt_editor_dismissed`, `media_prompt_list_dismissed` — `dismiss_method` migrated to §23.
7. Family F — duplicate `prompt_topic_selected` deleted; `prompt_topic_picker_opened`, `prompt_answered`, `prompt_edited`, `prompt_removed` emptied and marked superseded.

**`enums.json`** — registry 1.4.2: **§23 sheet `dismiss_method`** created as the canonical set; §8 `rule` gains `prompts_below_minimum` and its `dismiss_method` row now points at §23; §18 records that `edit` is carried by the editor events only; §2 / §11 re-verified, unchanged.

**`properties.json`**: `selection_index` and `used_count` added; `dismiss_method` and `prompt_count` rows rewritten.

**Currently in the build: none of it.** Every `prompt_*` row is `Not built` in `events.json` and ships with this ticket, as does `form_validation_failed` for this screen.
