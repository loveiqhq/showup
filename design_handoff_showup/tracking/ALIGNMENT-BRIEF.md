# Aligning the code with the tracking spec — brief for Claude Code

Paste this whole file to Claude Code, or attach it alongside the refreshed `design_handoff_showup/`.

**Design side, 7 September 2026.** Taxonomy **v1.3**, registry **v1.2.0**. Eight decisions were taken to reconcile the shipped tracking with the design taxonomy; they are logged in `design_handoff_showup/tracking/requirements.json` → `decision_log` 20–27, and the per-event task list is `design_handoff_showup/tracking/RECONCILIATION.md`.

## Ground rules for this pass

- **No shipped event is renamed.** The code's event names won for every built flow — `connect_*`, `tutorial_*`, `signup_*`, `legal_link_tapped` all stand. The spec was rewritten to match them.
- **No tutorial card is renumbered.** Welcome stays card 1 and the tour stays cards 2–6.
- **No screen label is "tidied up".** `"Signup - CreateAccount"` and the other four ship exactly as they are, casing included. They are human labels and they are deliberate.
- Everything below is **properties, plumbing and two server emissions**. No screen changes.

## Step 0 — replace the handoff folder

Drop in the refreshed `design_handoff_showup/` (this brief came with it). `tracking/` inside it is the source of truth for names and payloads. Read `tracking/README.md` and `tracking/RECONCILIATION.md` before writing code.

## Step 1 — make `enums.json` the registry, not a document

Decision 25. This is first because everything after it depends on the generated types.

- [ ] Commit `tracking/enums.json` into the repo (its path is yours — `analytics/registry/enums.json` or similar).
- [ ] Write the codegen: TypeScript unions/enums for the clients, a SQL migration for the matching Postgres enums, and a `field_id → sensitivity_class` lookup map.
- [ ] Generated files carry `// GENERATED — DO NOT EDIT` and are committed, so a vocabulary change shows up as a reviewable diff.
- [ ] To change a vocabulary from now on: edit the JSON, bump `registry_version`, re-run codegen. Never hand-patch a generated file, and never type a value list at a call site.

## Step 2 — build the emitter rules in before the sink is attached

Decision 25. Retrofitting the stamp means reprocessing history, so this only ever gets more expensive. None of the 35 shipped events is an attribute event yet — profile creation is the next flow and is nothing but attribute events.

- [ ] `sensitivity_class` (int 0–3) and `field_registry_version` (the `registry_version` string) resolved **at emit time** from the registry, never joined downstream.
- [ ] **Fail closed:** an unrecognised `field_id` is treated as Class 2 and counted in an *unclassified attributes* metric.
- [ ] **Tier on every event definition.** T2 is guaranteed delivery — retried, never sampled; T1 may be batched and sampled. Of what ships today, `account_created`, `date_confirmed`, `date_cancelled`, `match_created` and `notification_sent` are T2.
- [ ] **Consent gate on mobile.** The server checks consent before every event; the apps have no sink at all, which is not the same thing as a gate. Consent capture is unbuilt on both platforms — this one needs its own ticket before mobile tracking is switched on.

## Step 3 — screen identity: add the key, keep the label

Decision 27. Both values come from the `enums.json` §11 screen registry as constants — not literals at the call site.

- [ ] `screen_viewed` gains `screen_id` (stable snake_case key: `signup_create_account`, `signup_welcome_back`, `signup_phone_number`, `signup_code_entry`, `connect_sso`) **alongside** the existing `screen_name`, which is unchanged.
- [ ] `screen_viewed` gains `referrer_screen_id`.
- [ ] `legal_link_tapped`: rename `screen_name` → `screen_id`. This event carries the key only; the label lives on `screen_viewed`.
- [ ] Delete the unwired `SSOLogin` screen-name declaration. It is a duplicate of `Signup - welcomeback`; there is one screen.

## Step 4 — `date_cancelled` (blocks Show-up Rate)

Decision 22, and the one place the code was overruled. `{date_id, had_reason}` cannot compute a Show-up Rate.

- [ ] Payload becomes `date_id`, `hours_notice` (float), `initiator` (`"self"|"partner"`), `penalty_points` (int), `reason` (str). `had_reason` is dropped — `reason` subsumes it.

## Step 5 — the two source changes

Decision 24. A client-fired success is lost when the app dies mid-callback, which is exactly when a linking failure matters.

- [ ] `connect_link_succeeded` and `connect_link_failed` are also emitted **server-side**, and the server row is the one counted. Keep the client rows for funnel continuity.

## Step 6 — the remaining property additions (18 events)

Straight from `RECONCILIATION.md`. Each is additive.

- [ ] `signup_create_account_tapped` → Add entry_point.
- [ ] `signup_auth_method_tapped` → facebook added to the enum — the screen shows four methods and the spec listed three.
- [ ] `signup_phone_submitted` → Add country_code.
- [ ] `connect_sheet_dismissed` → Add stage — cancel-rate per stage is what tells us whether the offer is worth making.
- [ ] `connect_conflict_raised` → Ships provider only. Add attempted and existing — the mismatch pairs are the reason this event exists.
- [ ] `connect_conflict_resolve_tapped` → Ships provider only. Add existing and result, one event per attempt.
- [ ] `connect_conflict_different_account_tapped` → Ships no payload. All three properties missing; next_action is filled from what the user does after returning to the method list.
- [ ] `connect_conflict_repeated` → Add providers_attempted. Diagnostic only — it triggers no UI.
- [ ] `connect_linking_timeout` → Add elapsed_ms. The 8s cap is a guess; this is what measures it.
- [ ] `connect_skip_tapped` → Ships no payload. from_state is required — skip-from-idle and skip-after-error are different signals.
- [ ] `tutorial_cta_tapped` → Add dwell_ms — per-card attention is what this event exists for.
- [ ] `tutorial_completed` → Add duration_s.
- [ ] `check_in_created` → PREFIX CONFLICT: this ships as check_in_created while the ten unbuilt events in this family are checkin_*. When the Check-In flow is built, standardise the family on check_in_ and rename these ten.
- [ ] `like_sent` → Add position and showup_band_of_target. has_message ships already; like_message_attached keeps char_count.
- [ ] `match_created` → Add mutual_latency_h and both_showup_bands. match_id ships already.
- [ ] `notification_sent` → Rename notification_type → event_type; add template_id and lead_time. channel ships already.
- [ ] `date_confirmed` → Add time_to_confirm_min. date_id and venue_attached ship already.
- [ ] `date_completed` → Net-new from the code, absorbed here. Confirm the payload Show-up Rate needs before it is counted on.

## Step 7 — do NOT build these yet (8 events)

Specified for these screens, unimplemented, and not part of this pass. Listed so they are visible rather than forgotten.

- `sms_code_sent`
- `social_auth_os_sheet_shown`
- `social_auth_linking`
- `returning_user_recognised`
- `login_succeeded`
- `signup_abandoned`
- `tutorial_abandoned`
- `manifesto_accepted`

## Step 8 — make the drift check point at the spec

Today the build check compares the code against the developer-authored catalogue. That catches an out-of-date handout; it does not catch the code and the *design taxonomy* disagreeing, which is what happened here.

- [ ] Point the check at `tracking/events.json`: fail the build if a shipped event name is absent from the spec, or if a spec event marked `implemented: true` is absent from the code.
- [ ] Payload comparison can start as a warning and be tightened once Step 6 lands.

## What comes back to the design side

Nothing, as a routine step — the tickets cite the registry, so the flow is one-directional from here.

Two exceptions:

1. **When the code changes tracking**, send the regenerated catalogue and it gets re-diffed against `events.json`.
2. **Better: record the app repository** in the design project. `loveiqhq/showup` is backend-only, so 27 of the 35 events currently have no source that can be read from the design side, and no visual adherence check is possible against the built screens. With the repo recorded, both diffs are automatic and this document exchange stops being necessary.

## Still needing a decision from the design side

- `screen_viewed{state}` on ConnectSSO is always `idle` — the screenview fires on appearance, before the state moves. Re-fire per state change, or drop the property. **Do not guess; ask.**
- `manifesto_accepted` — tutorial card 6 is effectively a terms acceptance and fires only `tutorial_completed`. Legal to confirm whether the acceptance needs its own record.
