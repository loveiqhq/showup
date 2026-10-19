**Tracking change — 25 Sep 2026 · taxonomy 1.4.4 / registry 1.4.5**

`entry_point` is **removed** from the prompt events. We only measure **which topics are chosen** (`topic_id`), not whether the tap came from a suggestion card or the browse sheet.

- **Do not add** `entry_point` to `prompt_topic_selected`, `prompt_editor_opened`, `prompt_saved`, `prompt_editor_dismissed` or `prompts_minimum_met`.
- **Remove `position`** from `prompt_topic_selected` if it is already sent. It only meant something alongside the source.
- **Add `is_edit: bool` to `prompt_editor_dismissed`.** That was the one job `entry_point: "edit"` did that no other property carried.
- Enums §18 is **retired**. The app-open `entry_point` on `app_opened` is a different property and is **unchanged**.
- Pull `tracking/` at **registry_version 1.4.5**. The ticket description is updated to match.
