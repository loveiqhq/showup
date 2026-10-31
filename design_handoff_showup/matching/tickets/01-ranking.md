# [Matching 01] Search ranking — filters, score, ordering rules, soft range

**Parent:** [Epic] Date search (`search/tickets/00-epic.md`) · **Type:** Story (server) · **Priority:** High
**Repo:** `loveiqhq/showup` (NestJS + PostGIS). No client work in this ticket.
**Attachments:** `matching-01-ranking-attachment.zip` (handoff folder). **No spec sheet** — this ticket has no screen; the worked examples below are the spec.
**Turns on:** remote flag `filters_dealbreakers` (Check-In 05b), once this ships.
**Blocked by:** nothing. **Blocks:** turning `filters_dealbreakers` on.

## Description

Today the Search feed has no defined order. This ticket builds the launch ranker: who can appear in a user's Search (**filters**), how they are ordered (**score**, then **ordering rules**), and what happens when Age or Distance is a preference rather than a dealbreaker (**soft range**).

Goal: more **first dates that happen**. North-star: completed dates per active user per week (completed = `arrival_confirmed` for both).

The whole ranker is **database filters + one weighted sum + a few multipliers**. Every number in this ticket lives in **config**, versioned as `ranker_version` — changing a weight must never need a release.

Source of the rules: `design_handoff_showup/matching/matching-plan.md` (launch version, 26 Oct 2026). **Order of authority:** this ticket wins on behaviour and scope · config defaults below win on numbers · the plan is background.

Below, **A** is the person searching and **B** is a candidate.

## Decided before build

Decided 26–27 Oct 2026, recorded in `matching-plan.md`.

- **Soft range** (dealbreaker off): age **±3 years** · distance **+25% of the radius** — confirmed 27 Oct 2026. Config `soft_age_years`, `soft_distance_pct`.
- **Soft matches rank via a ×0.8 multiplier**, mixed in with everyone else — not placed after everyone in range. A preference gives way to a clearly better fit.
- **Multipliers multiply, and the product is capped at ×1.5.** The cap limits boosts only; reductions apply in full (lowest possible ×0.56). Without the cap, a new user who liked A reaches ×1.69 and lands first almost every time, which reveals the like on Freemium.
- **Random picks come only from profiles above the fit gate.**
- **Fit split** 0.70 / 0.20 / 0.10 and the **motivation table** below are confirmed.
- **Time weighs 20%, Fit 55%** (was 15 / 60), and Time also counts **how long** the overlap is (30% of Time). Decided 27 Oct 2026: whether the date happens depends on availability, and an 8 h overlap is easier to turn into a date than 1 h.

## Pipeline — in this order

1. **Filter** → the eligible pool (hard filters + soft range).
2. **Score** each eligible B, 0–1.
3. **Multiply** by the ordering multipliers, capped at ×1.5.
4. **Gate:** split into above-gate (Fit ≥ 0.3) and held-back (Fit < 0.3).
5. **Order** (Premium liked-you block first, then above-gate, then held-back).
6. **Insert random picks.**
7. **Emit** `search_feed_ranked`, return the feed with its `feed_request_id`.

The feed is **re-ranked on every load**. No state is kept between loads except what the rules below name.

## 1 · Filters — who can appear at all

B is eligible for A only if **all** apply:

| # | Rule | Detail |
|---|---|---|
| 1 | Active Check-In | B's chosen hours (today/tomorrow) have **≥ 2 h remaining** at the moment A's feed loads. Config `min_remaining_h: 2` |
| 2 | Time overlap | A's and B's hours overlap by **≥ 1 h**. Config `min_overlap_h: 1` |
| 3 | Profile creation complete | No verification check in MVP |
| 4 | Gender — both ways, always hard | B's gender ∈ A's `meet_gender` **and** A's gender ∈ B's `meet_gender` (enums §10). No dealbreaker, no soft range |
| 5 | Age — both ways | B's age within A's `age_range`, **and** A's age within B's. See *Soft range* |
| 6 | Distance — both ways | Straight-line km (PostGIS geography) ≤ A's `distance_km` **and** ≤ B's. See *Soft range* |
| 7 | Not blocked, either direction | Blocked = explicit block · `not_interested` (§34) · review "don't match me again" · phone contact (post-MVP, not built here) |
| 8 | Not in an active match | B is not in a match whose review is still open. (A can't search at all then.) |
| 9 | Not passed "Not now" this Check-In | A passed B with `not_now` during A's current Check-In → hidden until A's next Check-In (Search 01) |
| 10 | Rematch | A past date partner is eligible **only if both** answered `would_meet_again: true` (`rating_submitted`) |
| 11 | Incognito (Premium) | An Incognito B appears only to people B has liked |
| 12 | Motivation — Friends | `friends` matches only `friends` (§30). Romantic motivations are **never** filtered against each other |

**Never inputs** — must not appear in any filter, score or multiplier: ethnicity, religion, health, photo attractiveness. Religion and politics are on the profile; they are not read by the ranker.

### Soft range — when a dealbreaker is off

`age_dealbreaker` and `distance_dealbreaker` (enums §10) are per user and apply to **that user's own** preference.

- **Dealbreaker on:** rules 5 / 6 as written — hard.
- **Dealbreaker off:** the preference widens for eligibility only:
  - Age: `[min_age − 3, max_age + 3]`, floor 18. `max_age = null` (65+) stays unlimited.
  - Distance: `distance_km × 1.25`.
- Each side is checked against **its own** setting. A soft for age, B strict for age → B's range is hard, A's is widened.
- A B who is inside only because of a widening is a **soft match** → ×0.8 (see §3), `soft_match: true` in tracking. Outside on both age and distance is still one ×0.8, not two.
- With `filters_dealbreakers` **off**, every user is treated as dealbreaker **on** (today's behaviour). The soft range only exists once the flag is on.

## 2 · Score — all parts 0–1

```
Score = 0.55·Fit + 0.20·Time + 0.15·Reliability + 0.10·Distance
Fit   = 0.70·Motivation + 0.20·Vibe + 0.10·Profile
Time  = 0.70·Soon + 0.30·Length
```

**Motivation** — from both Check-Ins (step 03, single value, §30). Symmetric:

|  | long_term | non_monogamy | short_term | casual | friends |
|---|---|---|---|---|---|
| long_term | 1.0 | 0.4 | 0.5 | 0.6 | 0.2 |
| non_monogamy | 0.4 | 1.0 | 0.6 | 0.7 | 0.2 |
| short_term | 0.5 | 0.6 | 1.0 | 0.9 | 0.1 |
| casual | 0.6 | 0.7 | 0.9 | 1.0 | 0.3 |
| friends | 0.2 | 0.2 | 0.1 | 0.3 | 1.0 |

The `friends` cells against romantic values are never reached (filter 12); keep them in config anyway.

**Vibe** — today's vibes (Check-In 04, 1–5 each, §31): `shared ÷ min(count_A, count_B)`.

**Profile** — average over the fields **both** people answered:
- Interests: `shared ÷ min(count_A, count_B)`
- `family_ahead` (§4): 1 if the two answers share any value, else 0
- `living_ahead` (§4): 1 if the two answers share any value, else 0

No field answered by both → **Profile = 0.5** (neutral; a skipped question neither helps nor hurts).

**Time** — how soon, and how long, A and B can meet:
- Soon: `1 − hours_until_overlap_starts / 48`. Overlap already running → 1.0. Clamp to 0–1.
- Length: `min(overlap_hours_remaining / 4, 1)`. 4 h or more of shared time left → 1.0. The filter already guarantees ≥ 1 h, so Length is never below 0.25.

**Reliability** — B's past dates (date time already passed):

| B's status | Value |
|---|---|
| No past date booked (new) | 0.9 |
| 1 attended | 0.9 |
| 2+ attended | 1.0 |
| ≥ 1 booked, 0 attended | 0.5 |

Attended = `arrival_confirmed` by both. Cancellations and no-shows are **not** inputs yet (they join with the Show-up Rate). Max penalty is 7.5 score points — that is the visibility floor; do not add a second penalty anywhere.

**Distance** — `1 − km / min(radius_A, radius_B)`, using the **unwidened** radii. Clamp to 0 (a soft-distance match scores 0 here).

## 3 · Ordering rules — after the score

**Multipliers** — multiply all that apply to B, then `min(product, 1.5)`:

| Rule | Multiplier | When | `boosts` value |
|---|---|---|---|
| Liked you (Freemium only) | × 1.3 | B has an open like on A | `liked_you` |
| New user | × 1.3 | B is in their first 2 Check-Ins | `new_user` |
| Not now | × 0.7 | A passed B `not_now` at A's **previous** Check-In. Flat — two Not nows are still × 0.7 | `not_now` |
| Soft match | × 0.8 | B is in only via the soft range | (`soft_match: true`) |

Liked-you is a **multiplier, never a slot** on Freemium — position must not reveal who liked you.

**Fit gate** — B with Fit < 0.3 is held back: placed after every above-gate profile, ranked by final score among themselves. Search is never empty because of the gate alone.

**Order:**
1. **Premium only (grid and 1-up):** every B with an open like on A first, by final score — no ×1.3, and the gate does not apply to this block. Matches the Likes tab and grid badge.
2. Above-gate, by final score, descending.
3. Held-back, by final score, descending.

**Random picks** — positions **5, 10, 15 …** (every 5th = 20%; never 1–3) are filled with a uniformly random above-gate profile not already placed above that slot. The displaced profiles move down. On Premium the count starts after the liked-you block. No above-gate profile left → no more random picks. Config `random_share: 0.2`, `random_first_position: 5`.

**Ties:** equal final score → higher Fit first, then lower `profile_id` (deterministic, so tests are stable).

## Config — one versioned object

```
ranker_version: "launch-2026-10"
filters:      { min_remaining_h: 2, min_overlap_h: 1 }
soft_range:   { soft_age_years: 3, soft_distance_pct: 25 }
weights:      { fit: 0.55, time: 0.20, reliability: 0.15, distance: 0.10 }
fit_weights:  { motivation: 0.70, vibe: 0.20, profile: 0.10 }
motivation_table: <5×5 above>
reliability:  { new: 0.9, attended_1: 0.9, attended_2plus: 1.0, booked_none_attended: 0.5 }
time_weights: { soon: 0.70, length: 0.30 }
time_horizon_h: 48
time_full_overlap_h: 4
profile_neutral: 0.5
fit_gate: 0.3
multipliers:  { liked_you: 1.3, new_user: 1.3, new_user_checkins: 2, not_now: 0.7, soft_match: 0.8, cap: 1.5 }
random:       { share: 0.2, first_position: 5 }
```

Any change to any value → new `ranker_version`. The version is stamped on every `search_feed_ranked`.

## Worked examples — build these as unit tests

**Example 1 — the full sum.**
A: `long_term`, vibes 3, interests 6, `family_ahead` answered, `living_ahead` answered, radius 10 km. B: `casual`, vibes 4 (2 shared), interests 8 (3 shared), `family_ahead` shares a value, `living_ahead` skipped, radius 8 km, 4 km away, overlap starts in 6 h and lasts 3 h, 1 date attended.

- Motivation 0.6 · Vibe 2/3 = 0.667 · Profile (0.5 + 1) / 2 = 0.75
- Fit = 0.7·0.6 + 0.2·0.667 + 0.1·0.75 = **0.628**
- Soon 1 − 6/48 = 0.875 · Length 3/4 = 0.75 · Time = 0.7·0.875 + 0.3·0.75 = **0.838**
- Reliability 0.9 · Distance 1 − 4/8 = 0.5
- Score = 0.55·0.628 + 0.20·0.838 + 0.15·0.9 + 0.10·0.5 = **0.698**

**Example 2 — the cap.** Same B, who liked A (A is Freemium) and is in their first Check-In: 1.3 × 1.3 = 1.69 → capped **× 1.5** → final **1.047**. `boosts: ["liked_you","new_user"]`.

**Example 3 — reductions are not capped.** Same B, no like, A passed B `not_now` last Check-In, B is 37 and A's range is 25–35 with `age_dealbreaker: false`: 0.7 × 0.8 = **× 0.56** → final **0.391**. `soft_match: true`.

**Example 4 — soft range edges.** A's range 25–35, dealbreaker off: B aged 38 eligible (soft), 39 not. A's range 25–65+ (`max_age: null`), off: no upper limit, B aged 22 eligible (soft). A's radius 10 km, off: B at 12.5 km eligible (soft, Distance = 0), 12.6 km not. Same with dealbreaker on: 36 and 10.1 km not eligible.

**Example 5 — gate.** B with Fit 0.25 and final score 0.9 is placed after an above-gate B with final score 0.4. On Premium, if B liked A, B is in the liked-you block regardless.

**Example 6 — random picks.** Feed of 12 above-gate profiles: positions 5 and 10 are random picks; positions 1–4 are the top 4 by final score; no random pick in 1–3 ever.

## Build inventory

- **Ranker service** in `loveiqhq/showup`: steps 1–7 above, config loaded from one versioned object (not env vars scattered per value).
- **Filters as SQL/PostGIS**, so the pool is cut in the database, not in application code.
- **Per-user counters the ranker reads:** Check-In count (for `new_user`), past dates booked / attended (Reliability), last `not_now` per (A, B) with the Check-In it was made in.
- **`feed_request_id`** generated per feed load, returned to the client with the feed. The client sends it back on `profile_card_viewed`, `like_sent`, `pass_sent` (client work is in Search 01–03; the field must be in the response first).
- **`like_expired` emitter** — server job that closes unanswered likes.
- **Flag:** turn `filters_dealbreakers` on only after this is live and the soft-range tests pass.

## Acceptance criteria

- [ ] Filters 1–12 applied exactly as in the table; each has at least one positive and one negative test
- [ ] Gender is two-way and always hard; it ignores `filters_dealbreakers`
- [ ] Ethnicity, religion, politics, health and photo data are not read anywhere in the ranker (code review check)
- [ ] Soft range: worked example 4 passes in full, both dealbreaker states
- [ ] With `filters_dealbreakers` off, no profile is a soft match and nobody outside a range appears
- [ ] Score: worked example 1 gives Fit 0.628, Time 0.838 and Score 0.698 (±0.001)
- [ ] Profile = 0.5 when no field is answered by both
- [ ] Distance and Time are clamped to 0–1
- [ ] Multipliers: worked examples 2 and 3 pass; the cap applies to the product, never to individual rules; nothing is ever above × 1.5
- [ ] Freemium: liked-you is a multiplier only — no fixed position. Premium: liked-you block first, no × 1.3 applied
- [ ] Gate: worked example 5 passes; a feed with only held-back profiles is not empty
- [ ] Random picks: worked example 6 passes; never in positions 1–3; only above-gate profiles
- [ ] Ties break by Fit, then `profile_id`; the same inputs always give the same order apart from the random picks
- [ ] Every number comes from the config object; changing a weight in config changes the order without a deploy
- [ ] Tracking: every row below fires with the payload named there, and **no invented event names**
- [ ] **Evidence of done:** the unit-test run for examples 1–6, and one real `search_feed_ranked` record from staging (Freemium and Premium) with `items` filled in

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **I — Search & Liking**; `arrival_confirmed` is family M, `rating_submitted` family O). Use them exactly. Do not invent names, and do not restate a payload the registry already defines. Taxonomy **1.4.23**.

| What | Event | Payload |
|---|---|---|
| Feed returned (every load, every re-rank) | `search_feed_ranked` | `feed_request_id`, `ranker_version`, `layout`, `is_premium`, `eligible_count`, `items[]` — **missing from the current build (not built)** |
| Card in focus | `profile_card_viewed` | `feed_request_id` — **missing from the current build** |
| Like saved | `like_sent` | `feed_request_id` — **missing from the current build** |
| Pass | `pass_sent` | `feed_request_id` — **missing from the current build** |
| Like closes unanswered | `like_expired` | `hours_open`, `reason` — **missing from the current build (not built)** |

Reused, unchanged: `arrival_confirmed` (Reliability, north-star) · `rating_submitted.would_meet_again` (filter 10) · `pass_sent.pass_type` (filter 7, filter 9, Not now multiplier).

Rules that are easy to get wrong here:

- **`items[].score` is the base score** (the weighted sum, 0–1), **before** multipliers. The final score is reproducible from `score`, `boosts`, `soft_match` and the config of `ranker_version` — do not add a second score field.
- **`eligible_count` is after filters, before the gate** — held-back profiles count.
- **`boosts` lists every rule that applied**, even when the cap removed its effect. On Premium, `liked_you` marks the liked-you block (no multiplier).
- **`is_explore: true`** for random picks; `soft_match: true` for soft-range profiles.
- **Server-side only.** The client never computes or logs a score.
- `sensitivity_class` stays **0**: `items` holds ids and numbers, never motivation or vibe values.

## Out of scope

Instant Mode ranking and the "Instant Mode boosts 1/week" Premium line · cancellations / no-shows in Reliability (waits for the Show-up Rate) · phone-contact blocking · variety rule, popularity cap, pool widening · matching phases 1–3 (`matching-plan.md` §5) · the legal page.

## Dependencies

- **Check-In 05b:** `age_dealbreaker`, `distance_dealbreaker` fields on the filter record; the flag this ticket turns on.
- **Check-In 03 / 04:** motivation (§30) and vibes (§31) stored per Check-In.
- **Profile 21 / 23:** interests, `family_ahead`, `living_ahead` (§4).
- **Search 01–03:** clients send `feed_request_id` back; Not now / Not interested; Premium liked-you block.
- **Legal page (backlog, `matching-plan.md` §6):** the plain-language "how we rank" disclosure (EU Omnibus / DSA) must be live before public launch. Not blocking this build.
- **Tracking:** taxonomy 1.4.23 / registry 1.4.31. The taxonomy HTML pages were regenerated from the JSON on 27 Oct 2026; the JSON stays the source of truth.

## Open (not blocking this build)

- **Legal page** — the ranking disclosure is written with the legal-page content (`matching-plan.md` §6). Must be live before public launch.
