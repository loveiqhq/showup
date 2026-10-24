# [Search 03] Likes tab — likes received — Tracking (first comment, pinned)

Part of the ticket, split out by length only. Updated 21 Oct 2026 with the sorry sheet (⑱), the availability re-check (⑲), the fly-out (⑳) and the Not now label (㉑): no new events, no registry change.


Event names come from `design_handoff_showup/tracking/events.json` (family **I — Search & Liking**; `screen_viewed` and `tab_switched` in family A). Use them exactly. Do not invent names, and do not restate a payload the registry already defines. Registry **1.4.28**, taxonomy **1.4.19**.

| What | Event | Payload |
|---|---|---|
| Likes tab tapped | `tab_switched` | `tab: "likes"` (§37) — **respecified: §37 `nav_tab` NEW** |
| Tab shown | `screen_viewed` | `screen_id: "likes_received"`, `screen_name: "Search - Likes received"`, `referrer_screen_id` — **NEW §11 row** |
| List loaded | `likes_received_viewed` | `open_count`, `closed_count` — **respecified: `is_locked` removed, `count` → `open_count`, `closed_count` NEW** |
| Photo or View profile on a card | `screen_viewed` | `screen_id: "search_profile"`, `referrer_screen_id: "likes_received"` |
| The opened profile was seen | `profile_card_viewed` | as Search 02; `position` = the card's position on the list |
| Confirm date on the profile | `like_tapped` | `position`, `surface: "profile"` |
| Be sure shown / left | `like_confirm_shown` / `like_confirm_abandoned` | `is_mutual: true`, `stage: "confirm"` |
| Confirm refused | `like_confirm_failed` | `reason` (§36), `surface: "profile"` — **NEW** |
| Not interested on the profile, sent when the Undo window ends | `pass_sent` | `position`, `pass_type: "not_interested"`, `surface: "likes_received"` — **`surface` NEW** |
| Undo | `pass_undone` | `position`, `surface: "likes_received"` — **NEW** |
| Not now on the opened profile | `pass_sent` | `position`, `pass_type: "not_now"`, `surface: "profile"` — sent at once, **does not close the like** (21 Oct 2026) |
| Mutual like → match (server) | `like_sent` · `match_created` | as Search 02 |
| Like closed with no decision (server) | `like_withdrawn` | `reason` (§36) — **`reason` NEW** |

Rules that are easy to get wrong here:

- **The card is navigation only.** `View profile` and the photo fire `screen_viewed` `search_profile` and nothing else; there is no card-level `like_tapped` or `pass_sent`. `like_tapped` fires on the profile's `Confirm date`, with `surface: "profile"`.
- **`pass_sent` fires when the Undo window ends, never on the tap.** An undone decline fires `pass_undone` and **no** `pass_sent`. One sent decline is exactly one `pass_sent`.
- **A decline is a pass.** Do not create `like_declined`, `like_rejected` or `likes_received_declined`.
- **From the opened profile, Not interested uses `surface: "likes_received"`**, because the decline is sent from the list's toast.
- **`tab_switched` is the nav act; `screen_viewed` is the screen.** Both fire on a tab tap. A tap on the already-active tab fires neither.
- **Returning from a profile fires `screen_viewed` `likes_received` again** (referrer `search_profile`). `likes_received_viewed` does **not** fire again: the list did not reload.
- **`like_confirm_failed` is the client's record of a refusal**; `like_withdrawn` is the server's record that the like closed. Both fire for a refused confirm, from different sources. **`like_confirm_failed` fires once, when the sorry sheet opens** (21 Oct 2026); the sheet has no `screen_viewed` and its dismissal has no event.
- **`Not now` fires `pass_sent` · `not_now` only.** The label it adds is state.
- `position` is 1-based in list order for this visit, closed cards included.
- **`like_sent`, `like_withdrawn` and `match_created` are server-side.** The client fires none of them.
- `sensitivity_class` is **0** on every row.

**Events that must NOT fire here.**

- **Anything for the badge changing, a closed card being shown, the toast appearing, the fly-out, the Not now label or the re-check.** They are state, not acts. `closed_count` already carries the closed cards on load. Do not create `likes_refreshed`, `card_closed`, `confirm_refused_dismissed` or `back_to_likes_tapped`.
- **Any Premium lock or upsell event.** This user has Premium.
- **`screen_viewed` for Be sure, the sorry sheet or the toast.** They are not screens.
- **`like_message_attached`, or `stage: "message"`.** A confirm has no message step.
- **`search_viewed`.** That is the grid's queue load.

**Screen-to-screen conversion.**

| Edge | Numerator | Denominator |
|---|---|---|
| Tab → list | `screen_viewed` · `likes_received` | `tab_switched` · `likes` |
| Card → profile | `screen_viewed` · `search_profile` · referrer `likes_received` | `likes_received_viewed` with `open_count > 0` |
| Confirm rate | `match_created` (after `like_confirm_shown` · `is_mutual: true`, from a profile with referrer `likes_received`) | `screen_viewed` · `search_profile` · referrer `likes_received` |
| Refusals | `like_confirm_failed` (by `reason`) | `like_confirm_shown` · `is_mutual: true` |
| Mis-tapped declines | `pass_undone` | `pass_undone` + `pass_sent` · `surface: "likes_received"` |

**Registry changes made for this ticket (1.4.28 / taxonomy 1.4.19):** §11 `likes_received` **NEW** · §11 `search_profile` notes extended · §34 note extended · §35 now also used by `pass_sent`, `pass_undone`, `like_confirm_failed` (and no longer by `like_tapped` for this tab) · §36 `like_close_reason` **NEW** (`matched` · `no_overlap`) · §37 `nav_tab` **NEW** (`search` · `likes` · `instant` · `settings`) · `tab_switched` respecified · `likes_received_viewed` respecified · `pass_sent` + `surface` · `pass_undone` **NEW** · `like_confirm_failed` **NEW** · `like_withdrawn` + `reason`.

**Missing from the current build:** every client event above is `implemented: false`. `pass_sent` ships: **add `surface`**, and move its firing on this tab to the end of the Undo window. `likes_received_viewed` is *Partial* on the backend: **drop `is_locked`, rename `count` → `open_count`, add `closed_count`**. `tab_switched`: **its old tab values (`meet` · `instant` · `score` · `profile`) are gone** — use §37.
