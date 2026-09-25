# Tracking — how to work with these files

> **Reconciled against the shipped code, 7 September 2026 — taxonomy v1.4.1, registry v1.4.2.** For any flow that is built, the code's event names are now the truth and these files were regenerated to match; for unbuilt flows these files remain the truth. Start at **`RECONCILIATION.md`**: it lists the seven logged decisions (20–26), the renamed families, and the 31 events whose payloads the code must still change. It is generated from `events.json`, so regenerate it rather than editing it.

> **v1.4 — media capture pass, 16 September 2026.** Family E gained four events (`media_prompt_selected`, `media_prompt_list_dismissed`, `media_review_shown`, `media_preview_played`) and eight were respecified; `media_inspiration_opened` became `media_prompt_list_opened` when the inspiration sheet was removed from the design. New vocabularies: §20 `media_prompt_id`, §21 media `entry_point`, three rows in §8 and three screens in §11. Decisions 32 and 33 in `requirements.json` — 33 is the ranked card preview: the prompt on each empty card is the most-completed prompt for that medium, not a designer's pick. The ready-to-paste Jira section is `exports/profile-08-media-tracking-section.md`.

> **v1.4.1 — prompts instrumentation pass, 16 September 2026.** Answering the questions raised while the Prompts screens were being built. One new event — `prompt_topic_list_dismissed`, a cancel in the "Choose a topic" sheet, which had no abandonment event while the write sheet did. Three new properties: `selection_index` on `prompt_topic_selected` (which topic was reached for **first**), `prompt_count` on `profile_step_completed` (how many prompts existed when Continue was accepted), and the `prompts_below_minimum` value of `rule` on `form_validation_failed` (Continue pressed with nothing written — the CTA is never disabled, so nothing else records the attempt). `prompt_topic_selected` is now scoped to `suggestion` and `browse` only: an edit is not a fresh choice of topic. And `dismiss_method` is unified into one canonical set for every sheet in the product — §23, `close · backdrop · swipe · system_back` — replacing the write sheet's `scrim` / `back` and the media list's `cancel`. Nothing had shipped, so nothing needed migrating. §2 and §11 were re-verified: `prompts` has been `step_index: 2` of "The real you" since 1.3.1 and needed no change.

Five files. They do two different jobs, and mixing them up is the main way this goes wrong.

| File | Job | You... |
| --- | --- | --- |
| `enums.json` | **The vocabulary.** Every allowed value for every identifier. | **Generate from it.** Never retype it. |
| `requirements.json` | **The rules.** How events must be emitted, and the 33 resolved decisions. | Read before writing an emitter. |
| `events.json` | **The spec.** 296 events, no duplicate names, their payloads, and where each must fire. 35 carry `implemented: true`; 31 carry a `code_delta`. | Work down it as a checklist. |
| `properties.json` | **The spec.** 71 global / user properties. | Same. |
| `../reports/taxonomy-*.html` | **The view.** The five taxonomy pages, **generated from these JSONs** since 16 Sep 2026 (the direction used to be the reverse, and that extractor corrupted values — see §4 / §5 / §6, flagged in place). | Read; never hand-patch. Regenerate after a registry edit. |
| `RECONCILIATION.md` | **The diff.** What the code must change, and why, per event. | Read first. Generated — don't hand-edit. |

---

## 1. `enums.json` — commit it, then generate from it

### The problem it solves

The taxonomy defines vocabularies: nine `field_id`s, the gender values, 23 `step_id`s, the `filter_id` options. Each of those lists needs to exist in **three** places — the TypeScript client, the Postgres schema, and the spec documents.

If a human types them into each, there are three copies. Someone adds a habits option in one place and they disagree. **This failure is silent** — nothing throws, the numbers just quietly stop meaning the same thing, and you find out during an analysis three months later.

### The fix

One file is the truth. Everything else is generated.

1. **Commit this file** somewhere versioned — `analytics/registry/enums.json` or similar. Its path is yours to choose; that it is *in the repo* is the requirement (see `requirements.json` → `sensitivity_model.rules[0]`). A spreadsheet does not work: the two copies drift within a release.

2. **Write a codegen script** that reads it and emits:
   - TypeScript union types or enums for the client
   - a SQL migration creating the matching Postgres enums
   - optionally, a lookup map of `field_id → sensitivity_class` (the client needs this at emit time — see §2)

3. **Generated files carry a `// GENERATED — DO NOT EDIT` header** and are committed alongside the registry, so a diff shows vocabulary changes as code review.

4. **To change a vocabulary:** edit the JSON, bump `registry_version`, re-run codegen. One edit, propagated everywhere. Never hand-patch a generated file.

### Shape

```json
{
  "registry_version": "1.4.0",
  "taxonomy_version": "1.4",
  "sets": [
    { "id": "e1", "num": "1", "name": "field_id registry", "tables": [ { "columns": [...], "rows": [...] } ] },
    { "id": "e6", "name": "...", "values": ["..."] }
  ]
}
```

Most sets carry `tables` (columns + rows, because the source specified more than a bare list — descriptions, sensitivity, status). Two sets (`e6`, `e7`) carry a flat `values` array instead, because the source specified them as plain lists. Handle both shapes.

The two sets that matter most:
- **§1 `field_id`** (`id: "e1"`) — 9 profile fields. **This is the registry the sensitivity stamp resolves against.** It is the load-bearing one.
- **§10 `filter_id`** (`id: "e10"`) — who you want to meet. Note this is **search state, not a profile attribute** (build item 03 in the main README) — it belongs in a discovery-preferences table, over these same enums.

### `registry_version` vs `taxonomy_version`

Two different numbers, deliberately:

- **`registry_version`** (now `1.4.2`) versions **the vocabulary in this file**. Bump it on any change to any value set. This is the string stamped into `field_registry_version` on every attribute event.
- **`taxonomy_version`** (`1.4.1`) versions **the specification document** — the events, their payloads, the decisions. Changes when the spec changes, which is not the same moment.

They move independently. A new gender option bumps the registry, not the taxonomy. A respecified event trigger bumps the taxonomy, not the registry.

Why it matters: a row must keep the classification it was **collected under**. Reclassifying a field later is a one-row edit, and the version stamp is what proves which rule applied when. A downstream join always returns *today's* classification — which is exactly the wrong answer to the question that gets asked.

---

## 2. `requirements.json` — read before writing an emitter

These are binding, not advisory. Full prose in the main README under *Tracking → Requirements*. The short form:

**Stamp sensitivity at emit time.** Every attribute event carries two extra fields:

```
sensitivity_class:      2          // resolved from the registry, NOT joined downstream
field_registry_version: "1.1.0"    // enums.json → registry_version
```

**Fail closed.** An unrecognised `field_id` is treated as **Class 2** until a registry row exists, and counted in an *unclassified attributes* metric. Wrong but safe and visible — better than special-category data landing in an unrestricted table and surfacing in an audit.

**Class 2 is bucketed on device.** Special-category values (gender, orientation, religion, politics, health, substance use) are bucketed *before leaving the client*, so the pipeline never receives a raw string and there is nothing to strip later. **Class 3** (biometric, safety) sends result flags only, never the artefact.

**Free text is never sent.** Only `char_count`, `has_free_text`, or a bucket. Prompts, bios, chat, reports, reviews — no exceptions.

**Tier 2 is guaranteed delivery** — retried, never sampled. It feeds funnels, score and billing; a dropped Tier 2 event is a data incident. Tier 1 may be batched and sampled.

Also inside: the class/tier/status legends, the naming conventions (`object_action` past tense; `_viewed` is user-initiated, `_shown` is system-initiated — don't collapse them), and `decision_log` — 28 resolved conflicts with their outcomes and which side moved. **20–26 are the September code reconciliation**, including the one convention break kept on purpose: `connect_conflict_raised`.

---

## 3. `events.json` / `properties.json` — the build checklist

Not generated *from*. Worked *down*.

- **`code_anchor` is the build list.** Each event names the service that must emit it. Roughly a third are `source: "server"` — those cannot be added by the client later.
- Where a client and a server event both exist for one act (`date_confirm_submitted` / `date_confirmed`), **the server row is the one to count.**
- `backend_status` tells you what is possible today: `Live` (stores it, the event means something now) · `Partial` (stored, but not in the shape the event assumes) · `Client only` (no backend counterpart expected) · `Not built`.

**Optional, worth it if the emitter is TypeScript:** generate a typed `track()` signature from the payload definitions, so a wrong property name fails at compile time instead of in the warehouse three weeks later.

### Before any of it fires

`modules/analytics/analytics.module.ts` is a 175-byte placeholder. PostHog is configured at boot in `config/configuration.ts`, but nothing in this taxonomy is emitted or received yet. **A finished spec is not live tracking.**

---

## Suggested order

1. Commit `enums.json`. Decide its path.
2. Codegen → TypeScript + Postgres enums + the `field_id → sensitivity_class` map.
3. Build the emitter with the stamp and the fail-closed default in it from the start — retrofitting sensitivity handling means reprocessing history.
4. Wire the analytics module and one Tier 2 server event end to end. Prove delivery before breadth.
5. Then work `code_anchor` by `code_anchor`.

Steps 1–3 are the ones that are expensive to defer. The rest is volume.

---

*Generated from the Show Up design project · taxonomy v1.2 · reconciled against `loveiqhq/showup` `main @ c195089`. If the design side changes a vocabulary, these files are regenerated there and re-sent — they are not maintained by hand in two places either.*
