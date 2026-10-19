# Tracking reconciliation — code vs spec, 7 September 2026

Taxonomy **v1.3** · registry **v1.2.0** · reconciled against the ShowUp app + server tracking catalogue of 7 Sep 2026 (35 events).

**Generated from `events.json`.** Every row below is a `code_delta` field on an event definition, so this file cannot drift from the spec — regenerate it rather than editing it by hand.

## What happened

The sign-up and tutorial tickets carried their tracking as prose — *"Screenview (card index 1 of 5)"*, *"CTA click"*. `events.json` was never cited by those tickets, so it was never read, and 35 events shipped under invented names. Across both documents exactly one name matched: `account_created`.

The taxonomy was in the handoff bundle the whole time. It was missing from the *tickets*, and a zip does not get read — a ticket line does. Both halves were internally consistent; they had simply never met.

## The decisions — `requirements.json` → `decision_log` 20–27

| # | Decision |
|---|---|
| 20 | **Code names win for the flows that are built.** Unbuilt events keep their spec names until each is implemented. Tickets no longer restate tracking in prose — they cite `events.json`. |
| 21 | **The six same-name/different-payload collisions merge:** union of both payloads, spec property names where they collide (`event_type` over `notification_type`). |
| 22 | **`date_cancelled` adopts the spec payload whole.** The only place the code was overruled: Show-up Rate cannot be computed from `{date_id, had_reason}`. |
| 23 | **The code's tutorial numbering stands** — Welcome is card 1, the tour is cards 2–6, one higher than the Jira card numbers. Mapping in `enums.json` §14. Ticket titles are not renumbered. |
| 24 | **Social auth success/failure fire from both sides, server counted.** A client-fired success is lost when the app dies mid-callback. |
| 25 | **The three emitter rules go in before mobile tracking is switched on** — sensitivity stamp, `enums.json` as the generated registry, T1/T2 tier. |
| 26 | **Check-In keeps two prefixes for now.** `check_in_created` ships; the ten unbuilt `checkin_*` events are renamed when that flow is built. |
| 27 | **Screen identity is two properties, and nothing shipped gets renamed.** `screen_viewed` carries `screen_id` (stable snake_case key, assigned once, never edited) **and** `screen_name` (the human label, verbatim — what a person searches for in the analytics tool). Every other event carries `screen_id` alone. Both come from `enums.json` §11; neither is typed at a call site. Supersedes the `screen_name` → `screen_id` rename in decision 21. |

## Renamed families

| Was (spec v1.2) | Is (shipped) |
|---|---|
| `social_auth_*` (11 events) | `connect_*` |
| `onboarding_*` (4 events) | `tutorial_*` |
| `phone_*`, `auth_method_selected`, `sms_code_*`, `signup_started` | `signup_*` |
| `legal_doc_viewed` | `legal_link_tapped` |
| `onboarding_welcome_viewed` | folded into `tutorial_card_viewed` as card 1 |

## Shipped events that need a change (23)

Live in the apps or on the server and disagreeing with the reconciled spec. **Nothing here is a rename of a shipped event name** — all of it is properties.

| Event | Payload after reconciliation | What the code must change |
|---|---|---|
| `screen_viewed` | `screen_id`, `screen_name`, `referrer_screen_id`, `last_used`, `state` | Keep screen_name exactly as it ships — no rename (decision 27). ADD screen_id alongside it, taken from the enums.json §11 registry, and referrer_screen_id. Both come from the registry row, not from a literal at the call site. |
| `signup_create_account_tapped` | `entry_point` | Add entry_point. |
| `signup_auth_method_tapped` | `method`, `is_last_used` | facebook added to the enum — the screen shows four methods and the spec listed three. |
| `signup_phone_submitted` | `country_code` | Add country_code. |
| `connect_link_succeeded` | `provider`, `is_new_user` | Ships client-side with provider only. Add the server emission (count that row) and is_new_user. |
| `connect_sheet_dismissed` | `provider`, `stage` | Add stage — cancel-rate per stage is what tells us whether the offer is worth making. |
| `connect_link_failed` | `provider`, `kind` | Ships client-side. Add the server emission and count that row. kind is the code’s name for what the spec called reason; kind wins, and ticket welcome/04 needs the same rename. |
| `connect_conflict_raised` | `provider`, `attempted`, `existing` | Ships provider only. Add attempted and existing — the mismatch pairs are the reason this event exists. |
| `connect_conflict_resolve_tapped` | `provider`, `existing`, `result` | Ships provider only. Add existing and result, one event per attempt. |
| `connect_conflict_different_account_tapped` | `existing`, `next_action`, `next_provider` | Ships no payload. All three properties missing; next_action is filled from what the user does after returning to the method list. |
| `connect_conflict_repeated` | `count`, `provider`, `providers_attempted` | Add providers_attempted. Diagnostic only — it triggers no UI. |
| `connect_linking_timeout` | `provider`, `elapsed_ms` | Add elapsed_ms. The 8s cap is a guess; this is what measures it. |
| `connect_skip_tapped` | `from_state`, `provider` | Ships no payload. from_state is required — skip-from-idle and skip-after-error are different signals. |
| `tutorial_cta_tapped` | `card`, `card_name`, `dwell_ms` | Add dwell_ms — per-card attention is what this event exists for. |
| `tutorial_completed` | `card`, `card_name`, `duration_s` | Add duration_s. |
| `check_in_created` | `has_location` | PREFIX CONFLICT: this ships as check_in_created while the ten unbuilt events in this family are checkin_*. When the Check-In flow is built, standardise the family on check_in_ and rename these ten. |
| `like_sent` | `position`, `showup_band_of_target`, `has_message` | Add position and showup_band_of_target. has_message ships already; like_message_attached keeps char_count. |
| `match_created` | `match_id`, `mutual_latency_h`, `both_showup_bands` | Add mutual_latency_h and both_showup_bands. match_id ships already. |
| `notification_sent` | `channel`, `template_id`, `event_type`, `lead_time` | Rename notification_type → event_type; add template_id and lead_time. channel ships already. |
| `date_confirmed` | `time_to_confirm_min`, `date_id`, `venue_attached` | Add time_to_confirm_min. date_id and venue_attached ship already. |
| `date_completed` | `date_id` | Net-new from the code, absorbed here. Confirm the payload Show-up Rate needs before it is counted on. |
| `date_cancelled` | `date_id`, `hours_notice`, `initiator`, `penalty_points`, `reason` | Replace had_reason with the four spec properties. Show-up Rate cannot be computed without hours_notice and penalty_points — this is a requirement, not a preference. |
| `legal_link_tapped` | `link`, `screen_id` | Rename screen_name → screen_id here (this event carries the key only; screen_viewed is where the label lives). |

## Specified for these screens and not built (8)

| Event | Payload after reconciliation | What the code must change |
|---|---|---|
| `sms_code_sent` | `provider`, `latency_ms` | Not implemented. Server-side; SMS delivery latency is unmeasured until it is. |
| `social_auth_os_sheet_shown` | `provider` | Not implemented. |
| `social_auth_linking` | `provider` | Not implemented (server). |
| `returning_user_recognised` | `last_active_days_ago` | Not implemented. Partly inferable from screen_viewed{last_used}, which is not the same signal. |
| `login_succeeded` | `auth_method` | Not implemented. Log-in success is currently unmeasurable end to end. |
| `signup_abandoned` | `last_step` | Not implemented. |
| `tutorial_abandoned` | `last_card`, `last_card_name` | Not implemented. Drop-off point inside the tour is unknown without it. |
| `manifesto_accepted` | `dwell_total_s` | Not implemented. Card 6 is effectively a terms acceptance (see ticket 05) and fires only tutorial_completed today — Legal to confirm whether the acceptance needs its own record. |

## New registries in `enums.json`

| § | Registry | Note |
|---|---|---|
| 11 | **screen registry** | One row per screen: `screen_id` + `screen_name`, plus the naming rules. **A screen gets its row before it gets a ticket** — that is the step that stops a name being invented. Includes the `SSOLogin` duplicate to delete. |
| 12 | `phone_validation_reason` | The validator's seven outcomes, adopted from the code. The tickets' three are gone; "unsupported country" can never fire. |
| 13 | `legal_link` | `terms`, `privacy`, `legal_notice` — the last is new. |
| 14 | `tutorial card` | **The off-by-one, written down.** Read before building any tutorial funnel. |
| 15 | `auth_method` | Four values; `facebook` is the addition. |

## Order of work

1. **Wire the emitter rules first** — sensitivity stamp, tier, codegen from `enums.json`. Retrofitting the stamp means reprocessing history, so the cost only rises. Needs its own ticket (decision 25).
2. **`date_cancelled`** — it blocks Show-up Rate, and it is a payload change, not a rename.
3. **The two source changes** — server emission for `connect_link_succeeded` / `connect_link_failed`.
4. **`screen_id` alongside the existing `screen_name`**, from the §11 registry (decision 27).
5. **The remaining property additions**, screen by screen, from the table above.
6. **The mobile consent gate**, which is a build task with no ticket yet: the server checks consent before every event, the apps have no sink at all — which is not the same thing as a gate.

## Still open

- **`screen_viewed{state}` on ConnectSSO is always `idle`.** Re-fire per state change, or drop the property. It currently looks like data and is not.
- **The mobile consent gate has no ticket.**
- **The app repository is not recorded in this project.** `loveiqhq/showup` is backend-only, so 27 of the 35 events have no source we can read, and no visual adherence check is possible against the built screens.
