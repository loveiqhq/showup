# [Profile 08] Media — Tracking (paste as the first comment on the ticket)

Jira caps a description at **32,767 characters**; the Media ticket is over it, so this section lives as the ticket's first comment. Paste it **1:1** — the tables are the specification, not decoration — and do not run Jira's "improve description" over it. Description: `exports/profile-08-media-ticket.md` · committed at `design_handoff_showup/profile/tickets/08-media.md`.

This is the consumed form of `exports/profile-08-media-tracking-section.md` (written 16 Sep 2026, ahead of the ticket). If tracking changes, change it here and in that file together.

---

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **E — Profile Photos/Media**). Use them exactly — do not invent names, and do not restate a payload the registry already defines. Everything below was defined in the registry first — `events.json` family E, `enums.json` §8 / §11 / §20 / §21 / §22, decisions 32 and 33 — on **16 September 2026, before this ticket**.

**One screen carries two media, so `type: "video"|"voice"` is required on every event below.** Without it a tap on the voice card and a tap on the video card are the same row, and nothing about this screen can be answered. The screen view is the one exception: it describes the screen, not a medium.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "profile_media"`, `screen_name: "ProfileMedia"`, `referrer_screen_id` — §11 |
| **Screen state on entry** | `media_screen_viewed` | `screen_id`, `has_video`, `has_voice`, `preview_video_prompt_id`, `preview_voice_prompt_id`, `preview_source` (§22) — **on every mount**, including the return from an accepted take |
| Step entered | `profile_step_viewed` | `step_id: "media_video"` / `"media_voice"`, `step_index: 3` — §2 |
| **"See the prompts" tapped** | `media_prompt_list_opened` | `type`, `entry_point` (§21), `has_existing`, `screen_id` |
| **Prompt chosen** | `media_prompt_selected` | `type`, `media_prompt_id` (§20), `position`, `is_own_prompt`, `selections_before`, `was_previewed` — fires on the commit CTA, not on every row tap |
| **Prompt list cancelled** | `media_prompt_list_dismissed` | `type`, `dismiss_method` (§8), `had_selection`, `time_on_sheet_s` |
| Recording starts | `video_recording_started` / `voice_recording_started` | `media_prompt_id`, `is_own_prompt`, `attempt`, `is_retake` |
| **Stop → review screen** | `media_review_shown` | `type`, `media_prompt_id`, `duration_s`, `attempt`, `stop_reason` (§8) |
| Play on the review screen | `media_preview_played` | `type`, `attempt`, `play_count` — per play, with a running count |
| **Take accepted** | `video_prompt_recorded` / `voice_prompt_recorded` | `media_prompt_id`, `is_own_prompt`, `duration_s`, `retakes`, `plays_before_accept` — on **"Use this clip"**, never on Stop |
| **Retake** | `media_retaken` | `type`, `from: "review"\|"media_card"` (§8), `media_prompt_id`, `attempt`, `prior_duration_s`, `had_video`, `had_voice` |
| **Delete** | `media_deleted` | `type`, `media_prompt_id`, `duration_s`, `had_video`, `had_voice` — state **before** the deletion |
| **Skip** | `profile_step_skipped` | `step_id`, `screen_id` (**newly required**, v1.4), `has_video`, `has_voice` |
| Preview prompt changed (server) | `media_prompt_ranking_published` | `type`, `media_prompt_id`, `previous_media_prompt_id`, `completions`, `sample_size`, `window_days`, `preview_source` — once per medium per publish, only when the value changes |
| Continue | `profile_step_completed` | `step_id`, `time_on_step_s` — Continue is always enabled; this step is optional |

**What the instrumentation has to answer, and what answers it:**

1. **Is the ask understood before the camera opens?** `media_prompt_list_opened` → `media_prompt_selected` → `*_recording_started`, per `type`. Opened-and-dismissed (`media_prompt_list_dismissed`) is the drop-off the eleven prompts are accountable for, and `had_selection` splits "read it and left" from "picked one and lost their nerve".
2. **Which prompts are worth keeping?** `media_prompt_id` (§20) on selection versus on `*_prompt_recorded` gives a per-prompt completion rate. A prompt everyone picks and nobody finishes is a bad prompt that looks popular. Read video and voice separately — the same eleven are offered to both and they will not rank the same.
3. **Does the review screen earn its place?** `media_review_shown` is the denominator: of the takes that reached it, how many were played (`media_preview_played`), retaken (`media_retaken{from:"review"}`) or kept (`*_prompt_recorded`). `stop_reason: "max_length"` dominating means the cap is too short.
4. **What happens to media a user already has?** `media_screen_viewed{has_video, has_voice}` establishes the entry state on every mount, and `media_retaken{from:"media_card"}` / `media_deleted` carry the same pair. That is the difference between a user polishing a first take and one removing something already on their profile — and deleting is not retaking: there is no replacement take, so the two must never be collapsed.

**The previewed prompt is ranked, not designed — decision 33.** Each empty card shows the most-**completed** prompt for its medium, published by the server; `MEDIA_PREVIEW` in the reference file is only the cold-start fallback. The rules the emitter and the job have to respect:

- **Rank on completions, not selections.** `video_prompt_recorded` / `voice_prompt_recorded` by `media_prompt_id`, trailing 30 days, per medium. A prompt that is easy to pick and hard to answer is the worst thing to put on the card.
- **Count only `was_previewed: false` takes.** The previewed prompt is far more visible than the other ten; counting its own selections would make it win because it was shown. This is the single rule that keeps the ranking honest, and it is why `was_previewed` exists on `media_prompt_selected`.
- **`own_idea` never ranks** — it is an escape hatch, not a prompt.
- **Minimum 50 qualifying completions** for that medium in the window, else `preview_source: "fallback"` and the §20 cold-start prompt. Tie-break: higher completion rate, then the fixed §20 position.
- **Publish at most weekly per medium, and only on a >10% relative lead.** Card copy that flickers every week is worse than a slightly stale prompt.
- **The client never ranks.** It renders the two prompts the server hands it (`previewVideo` / `previewVoice` on the reference component) and falls back locally if it has none.
- **The list order never changes** — only the preview does. A list that reorders itself makes a returning user hunt for the row they wanted.
- **Video and voice rank separately and will not agree.** Never publish one number for both.

**Every view records what it displayed.** `preview_video_prompt_id`, `preview_voice_prompt_id` and `preview_source` on `media_screen_viewed` — the ranking moves, so a view that does not name the prompts it showed cannot be attributed afterwards, and a drop in recording rate cannot be told apart from a bad prompt.

**Rules for this screen's payloads:**

- **`media_prompt_id` is a stable id from §20, never the display string.** The prompts are copy and will be edited; an edit must not orphan the clips recorded under them. `own_idea` is the escape hatch and always carries `is_own_prompt: true`.
- **`media_prompt_id` is not `prompt_id`.** `prompt_id` / `topic_id` (§6, §17) are the *written* profile prompts — a different registry for a different screen. The recording events carried `prompt_id` until v1.4 and were renamed for exactly this reason.
- **`from` is required on `media_retaken`.** `review` is a take not yet kept; `media_card` is an artefact already on the profile. One number for both means neither.
- **`dismiss_method`, not `method`** (§8) — `method` already carries `phone · apple · google` for auth. `system_back` is Android only and is not the same act as the X.
- **Never send the recording, a transcript, a frame, or a waveform.** `duration_s` and the prompt id are the whole payload. Media of a user's face and voice is the most sensitive artefact in profile creation and none of it belongs in the analytics pipeline.
- **`attempt` starts at 1** and counts takes of the same prompt, so a retake loop is readable without diffing timestamps.

**Added when the ticket was written** — three points the registry settles but the pre-written section did not spell out:

- **Three screens, three `screen_viewed` rows.** §11 registers `profile_media`, `profile_media_record` and `profile_media_review`. The record and review views each fire their own `screen_viewed` with `referrer_screen_id` — otherwise the two most abandonable views in the flow are invisible.
- **`caption_added` and `caption_skipped` are registered but unreachable.** Caption authoring was removed on 16 Sep 2026. They stay in the registry so the profile-editing flow cannot invent a second name for the same act; **nothing on this screen may fire them**.
- **The permission surface has no events, exactly as on screen 06.** *Row shown*, *which mode*, *granted / refused*, *returned from Settings* — none exist in family G against camera or microphone. They get **defined in `events.json` first**, with a closed `access_status` set in `enums.json`, and then this table is updated. Do not invent names at the call site. Until then, the likeliest hard drop-off on the screen is unmeasured.
- **Replays of saved media are not tracked** (decided 25 Sep 2026). The saved card's play control fires no event; `media_preview_played` is review-screen plays only, so its ratio to `media_review_shown` stays clean. Do not add `media_artefact_played`.

**Currently in the build: none of it.** Family E's media rows are `Not built` in `events.json` — `media_screen_viewed`, `media_prompt_list_opened`, `media_prompt_selected`, `media_prompt_list_dismissed`, both `*_recording_started`, `media_review_shown`, `media_preview_played`, both `*_prompt_recorded`, `media_retaken` and `media_deleted` all ship with this ticket. `media_prompt_ranking_published` is **server-side and ships with the ranking job**, not with the screen — the screen reads a config value and works without it (falling back to §20). `screen_id` on `profile_step_skipped` is **missing from the current build** and is added here.
