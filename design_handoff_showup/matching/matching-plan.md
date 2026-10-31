# Matching — plan

Status: launch version, 26 Oct 2026. Not legal advice — see Legal.

## Goal

Optimise **first dates that happen**. North-star: *completed dates per active user per week* (completed = `arrival_confirmed` for both).

Scale assumption: ~500 active users per city is success → the launch rules below run for a long time. One junior developer: everything at launch is database filters + one weighted sum, weights in config (no release to change them).

---

## 1 · Filters — who can appear at all

B can appear in A's search only if **all** apply:

1. **Active Check-In** — B's chosen hours (today/tomorrow) have **≥ 2h remaining** when A searches.
2. **Time overlap** — A's and B's hours overlap by **≥ 1h**.
3. **Profile creation complete** — no verification in MVP.
4. **Gender** — both ways, always hard.
5. **Age** — both ways. Dealbreaker toggle in the search filter.
6. **Distance** — straight-line, within both radii. Dealbreaker toggle in the search filter.
7. **Not blocked**, either direction. Blocked = explicit block · "Not interested" · review "don't match me again" · phone contact (post-MVP).
8. **Not in an active match** — automatic: no search until the review is done.
9. **Incognito (Premium)** — B appears only to people B has liked.

**Dealbreaker toggle off** (age, distance): soft range — age **±3 years**, distance **+25% of the radius**, each side checked against its own setting. Soft matches rank **× 0.8**, mixed in (not after everyone in range). Confirmed 27 Oct 2026; config only. (Earlier 2 years / 20% rejected.)

**Never inputs:** ethnicity, religion, health, photo attractiveness.

**Motivation:** "New friends" only matches "New friends" (hard filter). All romantic motivations are never filtered against each other — mismatches score low (most people pick short-term but are open to more).

---

## 2 · Score

All parts 0–1.

```
Score = 0.55·Fit + 0.20·Time + 0.15·Reliability + 0.10·Distance
Fit   = 0.70·Motivation + 0.20·Vibe + 0.10·Profile
Time  = 0.70·Soon + 0.30·Length
```

Fit split confirmed 26 Oct 2026. Time 20% / Fit 55% (was 15 / 60) and overlap length in Time — decided 27 Oct 2026.

**Motivation** (Check-In step 4, single choice) — symmetric table, confirmed 26 Oct 2026 (Friends cells unused: filtered):

|  | Long-term | Non-mono | Short-term | Casual | Friends |
|---|---|---|---|---|---|
| Long-term | 1.0 | 0.4 | 0.5 | 0.6 | 0.2 |
| Non-monogamy | | 1.0 | 0.6 | 0.7 | 0.2 |
| Short-term fun | | | 1.0 | 0.9 | 0.1 |
| Casual dating | | | | 1.0 | 0.3 |
| New friends | | | | | 1.0 |

**Vibe** — shared vibes today ÷ the smaller of the two vibe counts (Check-In step 5, 1–5 each).

**Profile** — shared interests + matching plans (children, living), share of fields that match.

**Time** — Soon: `1 − hours_until_overlap_starts / 48`, overlap already running = 1.0. Length: `min(overlap_hours_remaining / 4, 1)`, 4 h+ = 1.0.

**Reliability** — from attended dates (both scanned):

| Status | Value |
|---|---|
| No date booked yet (new) | 0.9 |
| 1 attended | 0.9 |
| 2+ attended | 1.0 |
| Dates booked, 0 attended | 0.5 |

New users start high but below 2+, so a first date never lowers anyone. Max penalty = 7.5 score points — this is the visibility floor. Cancellations and no-shows join once the Show-up Rate is decided.

**Distance** — `1 − km / smaller radius`.

**Minimum fit gate** (decided 26 Oct 2026): profiles with Fit < 0.3 are held back. They are shown only once nobody above the gate is left — ranked by score, after everyone else. Search is never empty because of the gate alone.

---

## 3 · Ordering rules — after the score

1. **Liked you**
   - Freemium: × 1.3. A multiplier, not a slot — position must not reveal who liked you.
   - Premium (grid and 1-up): **liked-you profiles first**, by score; then everyone else. Matches the Likes tab and grid badge.
2. **Not now** → × 0.7 at A's next Check-In. Flat; repeats not penalised.
3. **New user** → × 1.3 for their first 2 Check-Ins.
4. **Random picks** → 20% of cards from the filtered pool, never in positions 1–3 (Freemium has 3 likes/day).
5. **Undecided cards** → no rule. The feed re-ranks on every load.
6. **Rematch** → a past date can reappear only if **both** answered "can match again" (`would_meet_again`).

**Stacking** (decided 27 Oct 2026): multipliers multiply; the product is capped at **× 1.5**. The cap limits boosts only — Not now × 0.7 and soft × 0.8 apply in full (lowest × 0.56). Uncapped, a new user who liked you reaches × 1.69 and lands first, which reveals the like.

**Random picks** (decided 27 Oct 2026): only from profiles above the fit gate; positions 5, 10, 15 …

Not at launch: variety rule, popularity cap, pool widening.

**Later:** Instant Mode rules and the "Instant Mode boosts 1/week" Premium line.

---

## 4 · Tracking (taxonomy 1.4.22)

Added to `design_handoff_showup/tracking/events.json`:
- `search_feed_ranked` (server) — every feed load: ranker version, eligible count, and per profile score, components, boosts, random pick, soft match.
- `feed_request_id` on `profile_card_viewed` · `like_sent` · `pass_sent`.
- `like_expired` (server) — likes that close unanswered.

Reused: `arrival_confirmed` (date happened) · `rating_submitted.would_meet_again` · `pass_sent.pass_type`.

The taxonomy HTML pages are views of the JSON — regenerated at 1.4.23 / registry 1.4.31 on 27 Oct 2026.

---

## 5 · Later phases

| Phase | Starts when | Change |
|---|---|---|
| 1 · Statistics | ~300 completed dates per city | Real like rates (smoothed), cohort defaults, blend from Fit to behaviour (`w = n / (n + 30)`), A/B-tuned weights |
| 2 · Learned | ~3,000 completed dates | Models for like, like-back, and both-arrive, trained on `search_feed_ranked` + outcomes. Needs an ML hire or contractor |
| 3 · Allocation | Cities dense | City-wide exposure budgets and Hinge-style daily picks |

Move on when the next phase beats the current one in an A/B test. Filters, never-inputs, the reliability floor and disclosure always stay rules.

**Guardrails** — reject any change that worsens: concentration of received likes · new-user match rate in 7 days · share of users with zero matches in 14 days.

---

## 6 · Legal — backlog for the legal page

To be built: legal page + content. Everything below must be covered there or in onboarding.

1. **How we rank** — plain-language list of the factors above: filters, Fit (motivation, vibe, profile), time, reliability, distance, liked-you, new-user boost, random picks. Required by EU consumer law (Omnibus) and the DSA.
2. **Sensitive data (GDPR Art. 9)** — explicit consent at onboarding. Gender preference reveals sexual orientation; motivations such as non-monogamy or short-term fun concern sex life.
3. **Reliability** — explain it, show users their own status, offer a way to contest (Art. 22).
4. **Premium** — what Premium changes: grid, Likes tab, liked-you first, Incognito.
5. **Incognito** — who can and cannot see you.
6. **Silent decisions** — "Not interested", "Not now" and "don't match me again" are never shown to the other person.
7. **Contact blocking (post-MVP)** — consent, numbers hashed on device, only hashes leave the phone.

---

## Tickets to write

1. ~~Check-In 05 Filters — change ticket~~ → **written 26 Oct 2026:** `exports/checkin-05b-filters-v2-ticket.md` (gender checkboxes + dealbreaker switches, flag `filters_dealbreakers` off until matching ships the soft range).
2. ~~Matching ticket~~ → **written 27 Oct 2026:** `exports/matching-01-ranking-ticket.md` (server, no spec sheet; worked examples are the tests). Turns `filters_dealbreakers` on.

## Open

1. Instant Mode — later
