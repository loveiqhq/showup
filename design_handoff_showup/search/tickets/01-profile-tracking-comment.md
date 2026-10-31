# [Search 01] Freemium profile + Like — Tracking (first comment, pinned)

Part of the ticket, split out by length only (Jira caps a description at 32,767 characters). Its rules are acceptance criteria. Updated 27 Oct 2026 with the developer answers (SHOWUP-184), taxonomy 1.4.24.

Event names come from `design_handoff_showup/tracking/events.json` (family **I — Search & Liking**, and `screen_viewed` in family A). Use them exactly. Do not invent names, and do not restate a payload the registry already defines. Registry **1.4.31**, taxonomy **1.4.24** (this ticket's own changes: 1.4.26 / 1.4.17, the coach 1.4.29 / 1.4.20, the developer answers taxonomy 1.4.24; see the end of this section).

| What | Event | Payload |
|---|---|---|
| Search tab shown | `screen_viewed` | `screen_id: "search_profile"`, `screen_name: "Search - Profile"`, `referrer_screen_id` — **NEW §11 row** |
| Queue loaded | `search_viewed` | `layout: "one_up"`, `results_count`, `filters_active` — **defined 1.4.24, gender included** |
| No results shown | `search_empty_shown` | `reason: "no_results"` |
| Filters opened from Search | `screen_viewed` | `screen_id: "checkin_filters"`, `referrer_screen_id: "search_profile"` |
| Filters saved from Search | `check_in_filters_changed` | as Check-In 05/05b, plus `opened_from: "search"` — **NEW 1.4.24** |
| A profile was seen | `profile_card_viewed` | `position`, `dwell_ms`, `photos_swiped`, `sections_scrolled`, `shared_vibe_count`, `shared_interest_count` — **last two NEW** |
| Photo opened fullscreen | `profile_photo_opened` | `photo_index` (1–6) — **NEW** |
| Voice / video played | `voice_prompt_played` / `video_prompt_played` | `position`, `played_pct` |
| Like tapped | `like_tapped` | `position`, `surface: "profile"` — **`surface` NEW (§35, 1.4.18)** |
| Be sure shown | `like_confirm_shown` | `target_attended_count` — **replaces `target_showup_band`** |
| Like flow left unsent | `like_confirm_abandoned` | `stage` (§33), `target_attended_count` — **`stage` NEW** |
| Message sent with the like | `like_message_attached` | `char_count` |
| Like saved (server) | `like_sent` | `position`, `target_attended_count`, `has_message`, `surface: "profile"` — **`target_attended_count` replaces `showup_band_of_target`; `surface` NEW (1.4.18)** |
| Not now / Not interested | `pass_sent` | `position`, `pass_type` (§34), `surface: "profile"` — **RESPECIFIED; `surface` NEW (1.4.19)** |
| Other likes withdrawn (server) | `like_withdrawn` | `match_id`, `reason: "matched"` (§36) — **NEW, one per withdrawn like; `reason` NEW (1.4.19)** |
| Actions coach shown (F) | `search_coach_shown` | — **NEW (1.4.20)** |
| Actions coach closed (F) | `search_coach_dismissed` | `dismiss_method` (§23: `close` = Got it or Don't show again · `backdrop` = scrim · `system_back` = Android back), `dont_show_again` (true only for that button), `visible_ms` — **NEW (1.4.20)** |

Rules that are easy to get wrong here:

- **`screen_viewed` fires once per visit to the Search tab, not per profile.** The next profile is the same screen. Per-profile reach is `profile_card_viewed`.
- **`profile_card_viewed` fires when the profile leaves the screen** (an action, a tab change or backgrounding), so `dwell_ms` is known. `position` is 1-based in this visit's queue and resets on each `screen_viewed`.
- **`voice_prompt_played` / `video_prompt_played` fire when playback stops** (end, pause, scrolled out, profile changed), with `played_pct` at that moment. One per play.
- **`like_confirm_abandoned`:** `stage: "confirm"` for Cancel, scrim or swipe-down on Be sure; `stage: "message"` for scrim or swipe-down on the message sheet. **`Skip message` is not an abandon.** It leads to `like_sent` with `has_message: false`.
- **`like_message_attached` fires on `Send message` only**, and only when the trimmed text is not empty. **The text itself is never sent**, in any event.
- **`pass_sent.pass_type`** is `not_now` or `not_interested` (§34). One event, two values. Do not create `not_now_tapped` or `not_interested_tapped`. Here it fires **on the tap** (no undo on this screen).
- **`surface` is always `"profile"`** on this screen. The only other value, `likes_received`, is Search 03's decline (`grid_card` was retired 21 Oct 2026).
- **`like_sent`, `match_created` and `like_withdrawn` are server-side.** The client fires none of them.
- **Emit order for a like:** `like_tapped` → `like_confirm_shown` → (`like_message_attached`) → `like_sent` (server) → `profile_card_viewed` for the profile that left.
- **The coach is an overlay, not a screen.** It has no §11 row and fires no `screen_viewed`. `search_coach_shown` fires each time it is visible (at most once per visit); `search_coach_dismissed` fires once per close. A scrim tap or Android back always sends `dont_show_again: false`. `swipe` is never sent (the coach has no drag). Backgrounding while it shows fires neither.
- **`profile_card_viewed.dwell_ms` for the coached profile counts from the coach's dismissal**, not from when the profile loaded.
- **`filters_active`** lists the §10 ids whose saved value differs from the start value, in the order `meet_gender,age_range,age_dealbreaker,distance_km,distance_dealbreaker`: `meet_gender` vs `checkinGenderDefault` of the current profile · `age_range` vs 18 → 65+ · `distance_km` vs 10 · each dealbreaker when true. Empty array when none. Gender values are never sent.
- **Filters from Search:** `check_in_filters_changed` fires once per accepted save with `opened_from: "search"`, and `filters_changed` against what the screen showed on entry. Back without saving fires nothing.
- **The return from Filters** is a new visit: `screen_viewed` with `referrer_screen_id: "checkin_filters"`, then `search_viewed`; `position` restarts at 1. No `search_coach_shown`.
- `sensitivity_class` is **0** on every row.

**Events that must NOT fire here.**

- **`check_in_step_answered` / `check_in_closed` from Filters opened from Search.** It is not a Check-In.
- **`filters_relaxed` / `radius_widened`.** `Widen my search` only opens Filters until the no-results story.

- **`profile_section_expanded`.** This screen has no accordions.
- **Anything for the overlap.** No `shared_vibes_shown` or `overlap_viewed`; the counts ride on `profile_card_viewed`.
- **Anything per keystroke** in the message field.
- **Any tap on the inert buttons while the coach shows.** No `like_tapped`, no `pass_sent`.
- **`search_coach_shown` after `Don't show again`**, or twice in one visit.
- **Anything for the likes chip tap.** The upsell story defines what its screen fires.
- **`search_lockout_shown` / `search_lockout_expired`.** No lockout state exists in this story.
- **`profile_step_viewed` / `profile_step_completed`.** These are not profile steps.

**Screen-to-screen conversion.**

| Edge | Numerator | Denominator |
|---|---|---|
| Check-In → Search | `screen_viewed` · `search_profile` · referrer `checkin_uplift` | `check_in_created` |
| Profile → Like tapped | `like_tapped` | `profile_card_viewed` |
| Like tapped → sent | `like_sent` | `like_tapped` |
| Lost at Be sure | `like_confirm_abandoned` · `stage: "confirm"` | `like_confirm_shown` |
| Likes with a message | `like_sent` · `has_message: true` | `like_sent` |
| Pass rate | `pass_sent` (by `pass_type`) | `profile_card_viewed` |

**Registry changes made for this ticket (1.4.26 / taxonomy 1.4.17):** §11 `search_profile` **NEW** · §33 `like_abandon_stage` **NEW** (`confirm` · `message`) · §34 `search_pass_type` **NEW** (`not_now` · `not_interested`) · `profile_photo_opened` **NEW** · `like_withdrawn` **NEW** · `pass_sent` **RESPECIFIED** (`reason_if_given` removed) · `target_showup_band` / `showup_band_of_target` **REPLACED** by `target_attended_count` · `profile_card_viewed` +2 properties.

**Registry changes for the coach (1.4.29 / taxonomy 1.4.20, 21 Oct 2026):** `search_coach_shown` **NEW** · `search_coach_dismissed` **NEW** (`dismiss_method`, `dont_show_again`, `visible_ms`) · §23 `dismiss_method` note extended to the coach (values unchanged).

**Registry changes from the developer answers (taxonomy 1.4.24, 27 Oct 2026):** `check_in_filters_changed.opened_from` **NEW** · `search_viewed.filters_active` **DEFINED**.

**Later registry changes that touch this screen:** 1.4.18 (Search 02) added `surface` to `like_tapped` and `like_sent`; 1.4.19 (Search 03) added `surface` to `pass_sent` and `reason` (§36 `matched` · `no_overlap`) to `like_withdrawn`. All are in the table above.

**Missing from the current build:** every client event above is `implemented: false`, the two coach events included, as are `search_viewed.filters_active` and `check_in_filters_changed.opened_from`. `like_sent` ships; **add `position`, `target_attended_count` and `surface`** (the band field never shipped, so nothing migrates). `match_created.both_showup_bands` still assumes bands; it is respecified with the Match story, not here.
