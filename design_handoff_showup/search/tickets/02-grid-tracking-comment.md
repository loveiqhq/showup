# [Search 02] Premium grid — Tracking (first comment, pinned)

Part of the ticket, split out by length only. Updated 21 Oct 2026 with the sorry sheet (⑱), the availability re-check (⑲) and the removal fly-out (⑳): no new events, no registry change.


Event names come from `design_handoff_showup/tracking/events.json` (family **I — Search & Liking**, and `screen_viewed` in family A). Use them exactly. Do not invent names, and do not restate a payload the registry already defines. Registry **1.4.28**, taxonomy **1.4.19**.

| What | Event | Payload |
|---|---|---|
| Grid shown | `screen_viewed` | `screen_id: "search_grid"`, `screen_name: "Search - Grid"`, `referrer_screen_id` — **NEW §11 row** |
| Queue loaded | `search_viewed` | `layout: "grid"`, `results_count`, `filters_active` |
| Card opened (photo or heart) | `screen_viewed` | `screen_id: "search_profile"`, `referrer_screen_id: "search_grid"` |
| The opened profile was seen | `profile_card_viewed` | as Search 01; `position` = the card's position in the grid |
| Photo / voice / video in the opened profile | `profile_photo_opened` / `voice_prompt_played` / `video_prompt_played` | as Search 01 |
| Like / Confirm date on the opened profile | `like_tapped` | `position`, `surface: "profile"` — **`surface` NEW (§35)** |
| Be sure shown | `like_confirm_shown` | `target_attended_count`, `is_mutual` — **`is_mutual` NEW** |
| Like flow left unsent | `like_confirm_abandoned` | `stage` (§33), `target_attended_count`, `is_mutual` — **`is_mutual` NEW** |
| Message sent with the like | `like_message_attached` | `char_count` |
| Like saved (server) | `like_sent` | as Search 01, plus `surface: "profile"` — **NEW** |
| Not now / Not interested on the opened profile | `pass_sent` | `position`, `pass_type` (§34), `surface: "profile"` — **`surface` NEW (1.4.19, Search 03)** · on a `Liked you` profile, `not_now` is sent at once and **does not close their like** (21 Oct 2026) |
| Undo after a liked-you decline | `pass_undone` | `position`, `surface: "profile"` — **NEW (1.4.19)** |
| Confirm refused | `like_confirm_failed` | `reason` (§36), `surface: "profile"` — **NEW (1.4.19)** |
| Mutual like → match (server) | `match_created` | as registered |
| Like closed with no decision (server) | `like_withdrawn` | `reason` (§36) — **`reason` NEW (1.4.19)** |

Rules that are easy to get wrong here:

- **Opening a card is a `screen_viewed`, not a new event** — from the photo or the heart alike. `search_profile` with `referrer_screen_id: "search_grid"`. Do not create `grid_card_opened`, `profile_opened` or `grid_heart_tapped`.
- **Returning to the grid fires `screen_viewed` `search_grid` again**, with `referrer_screen_id: "search_profile"`. `search_viewed` does **not** fire again: the queue did not reload.
- **`profile_card_viewed` fires only for a profile that was opened**, when it closes (back or an action). A card scrolled past in the grid fires nothing.
- **`like_tapped` fires only from the profile, always `surface: "profile"`.** `grid_card` is retired (21 Oct 2026). To split likes by where the profile was opened, use the profile visit's `referrer_screen_id` (`search_grid`). Do not create `grid_like_tapped`.
- **A confirm is a like.** `Confirm date` on the profile fires `like_tapped` (`surface: "profile"`) and the Be sure events with `is_mutual: true`; the server fires `like_sent` (with `has_message: false`) and `match_created`. There is no `like_message_attached` and no `stage: "message"` on a mutual flow.
- **`pass_sent` fires from the profile only.** A grid card has no pass buttons. **On the tap**, except for Not interested on a `Liked you` profile: then it fires **when the 6 s Undo window ends** (or the user leaves the grid). An undone decline fires `pass_undone` and **no** `pass_sent`.
- **`like_confirm_failed` is the client's record of a refusal**; `like_withdrawn` (server) is the record that the like closed. Both fire for a refused confirm. **It fires once, when the sorry sheet opens** (21 Oct 2026); the sheet itself has no `screen_viewed` and its dismissal has no event (the next `screen_viewed` · `search_grid` measures it).
- **The availability re-check fires nothing.** A card closing or its time changing is state, not an act. The server already records the reason (`like_withdrawn`, the match).
- `position` is 1-based in grid order for this visit.
- **`like_sent`, `like_withdrawn` and `match_created` are server-side.** The client fires none of them.
- `sensitivity_class` is **0** on every row.

**Events that must NOT fire here.**

- **`likes_received_viewed`.** That is Search 03's screen.
- **Any Premium lock or upsell event** (`premium_explainer_viewed`, the lock-surface events with `placement: "live_grid"`). They describe what a Freemium user sees; this user has Premium.
- **Anything for the badge, the ring, the marks or the fly-out.** They are state, not acts.
- **`screen_viewed` for a sheet.** Be sure, the message sheet and the sorry sheet are not screens.
- **Any event for the re-check or for dismissing the sorry sheet.** Do not create `grid_refreshed`, `card_closed`, `confirm_refused_dismissed` or `find_someone_else_tapped`.
- **`profile_section_expanded`.** There are no accordions.

**Screen-to-screen conversion.**

| Edge | Numerator | Denominator |
|---|---|---|
| Grid → profile opened | `screen_viewed` · `search_profile` · referrer `search_grid` | `screen_viewed` · `search_grid` |
| Opened profile → Like | `like_tapped` (in a profile visit with referrer `search_grid`) | `profile_card_viewed` with referrer `search_grid` |
| Lost at Be sure | `like_confirm_abandoned` · `stage: "confirm"` | `like_confirm_shown` |
| Liked-you confirm rate | `match_created` | `like_confirm_shown` · `is_mutual: true` |
| Refusals | `like_confirm_failed` (by `reason`) | `like_confirm_shown` · `is_mutual: true` |
| Pass rate in the grid | `pass_sent` (by `pass_type`) | `profile_card_viewed` with referrer `search_grid` |

**Registry changes made for this ticket (1.4.27 / taxonomy 1.4.18):** §11 `search_grid` **NEW** · §11 `search_profile` notes extended (reached from the grid) · §35 `like_surface` **NEW** (`profile` · ~~`grid_card`~~ · `likes_received`; the last is used by Search 03) · `like_tapped` and `like_sent` + `surface` · `like_confirm_shown` and `like_confirm_abandoned` + `is_mutual`.

**Amended 21 Oct 2026 in 1.4.28 / 1.4.19 (Search 03's versions, not bumped):** §35 `grid_card` **RETIRED** (never shipped) · `pass_undone.surface` now `profile` · `likes_received` · `pass_sent`, `pass_undone` and `like_confirm_failed` triggers cover the grid · §11 `search_grid` notes updated.

**Missing from the current build:** every client event above is `implemented: false`. `like_sent` ships; **add `surface`** alongside the Search 01 additions (`position`, `target_attended_count`).
