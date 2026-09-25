# Handoff: Show Up — profile creation, verification & date lifecycle

Prepared 6 Aug 2026 · **revised 8 Sep 2026** · design system **Show Up** · backend read at `loveiqhq/showup` **main @ c195089** · event taxonomy **v1.2**

> **Bundle revision 8 Sep 2026.** Supersedes any copy dated before this. Changed since the previous zip: the tracking taxonomy was reconciled against the built sign-up and tutorial code (see `tracking/RECONCILIATION.md` and `tracking/ALIGNMENT-BRIEF.md`), and every ticket's Tracking section now cites `tracking/events.json` rather than describing events in prose. Profile screens 01–04 additionally carry a registered `screen_name` and `screen_id` (`tracking/enums.json` §11).

---

## Overview

Show Up is a dating app with one unusual rule: **a match is a binding date.** No open-ended texting. If two people match, the app schedules a 30-minute date at a mutually available time and a halfway-point public location. Every profile carries a public **Show-up Rate**; miss dates and it drops, drop too low and you stop getting dates.

This bundle covers eleven designed screens — the sign-up / verification path, the nine-step profile-creation flow, and the late-cancellation screen — plus the full design token set, the component primitives, the 270-event tracking taxonomy in machine-readable form, and a gap report against the current backend.

## About the design files

**Everything in `designs/` is a design reference, not production code.** They are HTML prototypes that show intended look, copy and behaviour. The task is to **recreate them in the target codebase's own environment** using its established patterns — not to ship the HTML or transplant the JSX.

`components/shared.jsx` is the same thing at component level: the prototype's primitives, included because it is the most precise statement of every measurement and colour. Read it as a spec. Port it; don't import it.

The backend (`loveiqhq/showup`) is **NestJS + TypeORM + PostGIS, API only** — no frontend exists yet. So there is no established client environment to match, and picking one is part of this work. The designs are drawn as iOS (390×844, iPhone-class); the product is iOS-first. React Native / Expo or native SwiftUI both fit; nothing in the designs forces the choice.

## Fidelity

**High fidelity.** Final colours, type, spacing, radii, shadows, copy and interaction timings. Recreate faithfully. Where a value is not stated in this README, `tokens/colors_and_type.css` and `components/shared.jsx` are authoritative — in that order.

Two deliberate exceptions:
- **Photography** is placeholder. The 16 portrait JPEGs in the design project are prototype content, not assets to ship, and are not included here.
- **Hover states do not exist.** The designs are mobile-first; desktop hover is intentionally absent. Press states only (see Interactions).

---

## Design tokens

Import `tokens/colors_and_type.css` verbatim, or transcribe it into the platform's token layer. The names below are the canonical ones — keep them.

### Colour

| Role | Token | Value |
| --- | --- | --- |
| Canvas | `--liq-bg` | `#FFFBF7` — paper white, **never** `#FFF` |
| Card on canvas | `--liq-bg-elevated` | `#FFFFFF` |
| Raised chrome, inputs, time slots | `--liq-bg-raised` | `#F7F2FA` (faintly lavender, never grey) |
| Ink | `--liq-fg` | `#1D1129` — warm violet, not slate or black |
| Secondary copy | `--liq-fg-muted` | `rgba(29,17,41,0.62)` |
| Tertiary / captions | `--liq-fg-subtle` | `rgba(29,17,41,0.46)` |
| Disabled, hairlines | `--liq-fg-faint` | `rgba(29,17,41,0.24)` |
| Border | `--liq-border` | `rgba(29,17,41,0.12)` |
| Border, barely-there | `--liq-border-soft` | `rgba(29,17,41,0.06)` |
| Primary violet | `--liq-primary-500` | `#812AEC` |
| Lavender accent | `--liq-lavender-400` | `#A78BFA` |
| **Primary CTA** | `--liq-orange-500` | `#FE6839` |
| Body text on white | `--liq-neutral-200` | `#4B3B5A` |
| Success | `--liq-success` | `#00AB55` |
| Danger | `--liq-danger` | `#FB323B` |

**Signature gradient** — `--su-grad-sunset`: `linear-gradient(135deg, #FE6839 0%, #D05976 38%, #812AEC 100%)`. Used on the wordmark's "Up", the match celebration, the binding-shield, Instant Mode glow.

**Ambient atmosphere.** Every screen carries the same wash — an orange glow top-right, a violet glow bottom-left — so the canvas is never flat paper. One geometry, two intensities:
- `hero` (expressive screens: prompts, photos, embrace, welcome) — orange `0.26` / violet `0.22`
- `form` (input & verification screens: email, code, the detail steps) — orange `0.13` / violet `0.11`

Implement it once as a component (`<Atmosphere variant="hero|form"/>` in `shared.jsx`) and drop it as the first child of the screen frame. Do not hand-roll orb divs with ad-hoc opacities.

### Type

- **Serif — Lora** (400–700, italic): every headline, the wordmark, pull quotes, and all large numerals (48h, 30 minutes, the Show-up Rate readout).
- **Sans — Manrope** (300–800): body, buttons, labels, captions, tab bar.
- Both ship via Google Fonts in the CSS. To self-host, replace the `@import` with `@font-face` rules; the woff2 binaries were not extracted into the design project.

| Role | Family | Size | Weight | Line-height | Tracking |
| --- | --- | --- | --- | --- | --- |
| `.liq-display` | Lora | `clamp(56px, 6vw+16px, 96px)` | 700 | 1.05 | −0.025em |
| `.liq-h1` | Lora | `clamp(40px, 3vw+20px, 72px)` | 700 | 1.05 | −0.025em |
| `.liq-h2` | Lora | `clamp(32px, 2vw+16px, 60px)` | 700 | 1.20 | −0.015em |
| `.liq-h3` | Lora | `clamp(22px, 1vw+16px, 30px)` | 700 | 1.2 | 0 |
| `.liq-h4` | Lora | 20px | 600 | 1.4 | 0 |
| `.liq-eyebrow` | Manrope | 12px | 700 | — | 0.04em, uppercase |
| `.liq-lead` | Manrope | 20px | 500 | 1.4 | 0 |
| `.liq-body` | Manrope | 16px | 400 | 1.5 | 0 |
| `.liq-body-sm` | Manrope | 14px | 500 | 1.4 | 0 |
| `.liq-caption` | Manrope | 13px | 500 | 1.33 | 0 |
| `.liq-label` | Manrope | 16px | 600 | 1.5 | 0 |
| `.liq-button-text` | Manrope | 16px | 700 | 1.5 | 0 |

**The italic underline is the signature flourish.** One italicised word per Lora headline, with a soft orange radial wash behind it — `.su-underlined em::after` in the CSS: `left:-2px; right:-2px; bottom:-0.08em; height:0.32em`, `radial-gradient(ellipse at 50% 100%, rgba(254,104,57,0.55) 0%, rgba(254,104,57,0) 70%)`, `z-index:-1` under an `isolation:isolate` parent. It appears under nearly every headline in the product. Manrope italics are never used.

### Spacing, radii, shadow

- **8px base**, 4px half-step: `--liq-space-1` 4 · `-2` 8 · `-3` 12 · `-4` 16 · `-5` 20 · `-6` 24 · `-8` 32 · `-10` 40 · `-12` 48 · `-16` 64 · `-24` 96 · `-32` 128. Screen padding is 24px horizontal; 20–28px between blocks.
- **Radii:** `xs` 8 (dense chips) · `sm` 12 (time slots, small inputs) · `md` 16 (small cards) · `lg` 20 (**default card**) · `xl` 24 (hero cards, photo header) · `2xl` 32 (full-bleed portraits) · `3xl` 40 (unused, marketing) · **pill 9999 for every button, chip and avatar**. No square corners anywhere outside iOS status-bar glyphs.
- **Shadows are violet- or orange-tinted, never grey:**
  - `--liq-shadow-md` `0 4px 12px rgba(46,1,71,0.06), 0 1px 3px rgba(46,1,71,0.04)` — resting cards
  - `--liq-shadow-lg` `0 12px 32px rgba(46,1,71,0.10), 0 4px 12px rgba(46,1,71,0.06)` — dialogs
  - `--liq-shadow-cta` `0 8px 20px rgba(254,104,57,0.32), 0 2px 6px rgba(254,104,57,0.20)` — every orange CTA, so it reads as glowing
  - `--liq-shadow-violet` `0 8px 20px rgba(129,42,236,0.28), 0 2px 6px rgba(129,42,236,0.18)` — sunset/violet buttons
- **No inner shadows. No accent left-border cards.** Emphasis is background tint or glow.

---

## Components

Exact specs; `components/shared.jsx` is the reference implementation.

### Button
Pill (`9999`), Manrope 700, `inline-flex`, `gap: 8`, `transition: transform 180ms cubic-bezier(.22,1,.36,1)`.

| Size | Height | Padding-x | Font |
| --- | --- | --- | --- |
| `sm` | 36 | 16 | 14 |
| `md` | 48 | 22 | 15 |
| `lg` | 56 | 28 | 16 |

| Variant | Background | Colour | Shadow / border |
| --- | --- | --- | --- |
| `primary` | `--liq-orange-500` | `#fff` | `--liq-shadow-cta` |
| `sunset` | `--su-grad-sunset` | `#fff` | `--liq-shadow-violet` |
| `violet` | `--liq-primary-500` | `#fff` | `--liq-shadow-violet` |
| `ghost` | transparent | `--liq-fg` | `1px solid --liq-border` |
| `soft` | `--liq-bg-raised` | `--liq-primary-500` | — |
| `danger` | transparent | `--liq-danger` | `1px solid rgba(251,50,59,0.35)` |

Disabled → `opacity: 0.45`, no handler. Minimum touch target 44px — `sm` at 36px is for inline chrome only, never a primary action.

### NextButton — the signature flow CTA
Bottom-right of most flow screens. A row: text label (Manrope 700, 17px, `--liq-fg`) + `gap: 14` + a 56×56 pill-round button, `--liq-orange-500` with `--liq-shadow-cta` (or `--su-grad-sunset` + `--liq-shadow-violet`), containing `arrow-right` at 22px, stroke 2, white. Repeats across onboarding, check-in, sign-up and rating — build it once.

### SkipLink
The one canonical optional-step affordance: Manrope 700, 13.5px, `--liq-primary-500`, underlined at `text-underline-offset: 3px`, thickness 1.5px. Default label "Skip for now". Never restyled per screen.

### Chip
Pill, Manrope. Idle: `#fff` + `1px solid --liq-border` + `--liq-fg`. Selected (`lavender` tone): `--liq-primary-500` background, white text, matching border. Selected (`orange` tone): `rgba(254,104,57,0.10)` background, `--liq-orange-500` text and border. Used for interests, vibes, intent.

### Field
Manrope. `1.5px` border that ramps to `--liq-primary-500` on focus; `--liq-danger` on error, `--liq-success` on success. Label above (`.liq-label`), hint below (`.liq-caption`) — hints are informative, never cautionary.

### TimeSlot
Height 44, min-width 64, padding-x 12, radius 12, Manrope 600 / 14px, `transition: all 180ms`.
- `idle` — `#fff`, `--liq-fg`, `1px solid --liq-border`
- `preferred` — `--liq-orange-500`, white, `--liq-shadow-cta`
- `backup` — `rgba(254,104,57,0.12)`, `--liq-orange-500` text + border
- `past` — transparent, `--liq-fg-faint`, `1px solid --liq-border-soft`

### StepProgress
Row of segmented bars, `flex: 1` each, height 5, radius 999, `gap: 6`. Filled `--liq-primary-500`, unfilled `--liq-border`, `transition: background 280ms`. Sits at the top of every stepped screen.

### ScoreRing / ShowUpPill — the Show-up Rate
The system's most important component. A donut around a numeric percentage, banded:

| Value | Band | Ring | Text | Tint |
| --- | --- | --- | --- | --- |
| ≥ 80 | Trusted | `#00AB55` | `#0A7A47` | `rgba(0,171,85,0.14)` |
| 50–79 | Steady / Watch | `#FE6839` | `#C44A22` | `rgba(254,104,57,0.16)` |
| < 50 | At risk | `#FB323B` | `#B71F26` | `rgba(251,50,59,0.16)` |

`ScoreRing`: SVG rotated `-90deg`, track `--liq-border`, progress `strokeLinecap: round`, `stroke-dashoffset` transition **600ms**. Centre numeral is Lora 700 at `size × 0.32`, `--liq-fg`.

`ShowUpPill` has three variants — `soft` (tinted, for cards and rows), `dark` (translucent capsule for photo overlays, e.g. `rgba(34,22,16,0.78)` + `blur(8px)`), `plain` (text + ring only). Sizes `xs` / `sm` / `md` → ring 18 / 22 / 28, stroke 2.2 / 2.6 / 3, font 11 / 12 / 13.

⚠️ **Nothing computes this number yet** — see build item 05.

### ProfileVisibility
The "don't display this on my profile" control that appears on **all nine** profile-creation steps, unticked by default. Semantics were settled in taxonomy conflict 07c: **display is suppressed, matching is unaffected.** The copy lives in the component so screens stop inventing wording — keep it centralised. ⚠️ No backend state exists for it — build item 02.

### Chrome
Status bar 54px · sticky tab bar ~64px, `rgba(255,251,247,0.92)` + `backdrop-filter: blur(20px)` · home indicator 28px. Bottom-nav destinations: **Meet · Instant · Score · Profile**. Glass surface for floating sheets: `rgba(255,255,255,0.72)` + `blur(12px)`.

### Icons
All inline SVG, **stroke-only, currentColor**, Lucide geometry, 24×24 viewBox, `stroke-width: 1.7`, round caps and joins. ~45 in the set (`ICONS` map in `shared.jsx`). No icon font, no PNG set, no unicode glyphs as icons. Apple and Google brand marks are the only filled exceptions. **No emoji anywhere.**

---

## Screens

All eleven are in `designs/`. Open them in a browser — they are self-contained and link `colors_and_type.css` alongside them.

**Sign-up & verification**
1. **Verify email** — email capture + **6-digit** code confirmation. Note the label: **"03 · Profile creation · Email verification"**, step 2 of 3 of *The basics*. This is **not** a login screen — it runs after phone auth. Hint copy: "We will not show your email on your profile." Failure state is inline, not modal: digits preserved, cooldown released, "Verify code" CTA stays visible, "To edit your email, simply go back" present in both states. ✅ Confirmed as required — build item 08.
2. **Verify code** — ***phone*** verification, flow "01 · Welcome & sign-up". **Six-digit** SMS OTP, per-digit boxes, 24s resend cooldown released on failure, "Edit phone number" escape hatch in both states. ✅ Six digits confirmed — build item 07.
3. **Verify profile** — biometric selfie check with an explicit consent gate. Four states: `none · pending · verified · rejected`. Backend matches; raw capture is discarded and only a one-way template kept, which the privacy copy reflects.
4. **Profile photos** — 6 slots, **minimum 4 required, maximum 6**, first is the primary, reorderable, per-slot remove. Backend agrees.

**Profile creation — nine steps**, each with `StepProgress`, a `ProfileVisibility` checkbox, `SkipLink` where optional, and `NextButton`:
5. **Share some details** (7 sub-steps: name, DOB, gender, orientation, height, …)
6. **Your lifestyle and habits**
7. **Living status**
8. **Interests** — multi-select chips, **cap 10**
9. **Profile prompts** — prompt-plus-answer pairs
10. **Embrace details** — the completion / summary step

**Date lifecycle**
11. **Late cancellation** — the 10-minute grace rule. Past ten minutes the person waiting may leave with **no penalty**, and the cost lands on the person who broke it. This asymmetry is a brand promise, stated on the screen. ⚠️ It does not exist in the backend lifecycle — build item 04.

Copy is final. Follow it exactly, including capitalisation of the proper nouns: **Show Up**, **Show-up Rate**, **Instant Mode**, **Match Mate**, **Check-In**.

---

## Interactions & behaviour

- **Easing:** `cubic-bezier(.22, 1, .36, 1)` everywhere — overshoot-free.
- **Durations:** 180ms colour/state · 280ms progress bars · 600ms ScoreRing reveal (`stroke-dashoffset` lerp). No bounces, no parallax.
- **Press:** scale to `0.98` on press-down, snap back on release. This is the only interactive feedback.
- **No hover.** Mobile-first by design.
- **No page transitions.** Stepped screens swap instantly — the feel is fast and disposable.
- **Validation:** inline, on the field, on blur or submit. Error copy is specific and unapologetic; never a toast. Taxonomy rules include `required_missing`, `photos_below_minimum`.
- **Optional vs required:** any step without a `SkipLink` is required. Photos are the only hard minimum today (4) — see build item 09 for whether detail steps should join it.

## Voice

Intimate, editorial, faintly confrontational — closer to a manifesto than a product.

- **You / your.** Never "users" or "members".
- Short sentences. Paragraphs of one or two. Period-driven, not comma-driven.
- One italicised word per headline, always with the orange wash: *"Stop texting. Start meeting."* · *"A match is a binding date."* · *"Just thirty minutes."*
- Sentence case in headings. Sentence-case buttons.
- CTAs are **first-person commitment** on consequential screens ("I'm ready to show up", "Confirm this date", "I'll be there") and verb-led on routine ones ("Continue", "Next", "Skip for now").
- Numbers stay numeric: "30 minutes", "48 hours", "94%".
- No emoji. No "Welcome!" / "Hi there!" filler — the brand opens with a thesis, not a greeting.

---

## Tracking

`tracking/` holds the taxonomy as JSON, generated from the design project's reference pages (also bundled in `reports/`):

| File | Contents |
| --- | --- |
| `events.json` | **270 events** in 20 families (A–T). Per event: `name`, `source` (client/server), `tier`, `payload[]` with types, `sensitivity_class` (0–2), `backend_status`, `code_anchor`, `trigger`. |
| `properties.json` | **71 properties** — 22 global (on every event), 38 user, 11 additions. |
| `enums.json` | 10 registries, incl. §1 `field_id` (9 fields) and §10 `filter_id` (who you want to meet). |
| `requirements.json` | **The rules the events must be emitted under** — the sensitivity model, the class/tier/status legends, the naming and payload conventions, and the 21-item decision log. Read this before writing an emitter. |
| `README.md` | How to actually work with these four files — what to generate vs. what to work down, and the suggested order. **Start here.** |

### Requirements — these are binding, not advisory

**Sensitivity is inherited, then stamped.** The `field_id` registry owns each field's classification; the **client resolves it at emit time and writes it into the payload**. Two additions on every attribute event:

```
sensitivity_class:      2          // resolved from the registry, NOT joined downstream
field_registry_version: "1.1.0"
```

Neither alternative works: fixing the class on the event forces one event per sensitivity level and destroys the generic attribute family; a pure downstream join fails silently the moment the app ships a field the pipeline has not seen.

1. **The registry is the single source of truth and it lives in the repo** — one versioned file, compiled into both the client and the pipeline. Maintained in a spreadsheet, the two copies drift within a release. (`enums.json` §1 is that registry.)
2. ⚠️ **Fail closed on an unknown `field_id`** — any identifier the pipeline does not recognise is treated as **Class 2** until someone adds the registry row, and counted in an *unclassified attributes* metric. Wrong but safe, and visible — rather than special-category data landing in an unrestricted table and surfacing in an audit.
3. **The stamp is historical truth** — reclassifying a field from 1 to 2 is a one-row edit, and rows collected beforehand still carry the class they were collected under. A downstream join always returns *today's* classification, which is precisely the question a regulator does not ask.
4. **Cost** — one integer and one version string per event, plus the discipline of keeping the registry authoritative.

**Sensitivity classes**

| Class | Meaning | Handling |
| --- | --- | --- |
| 0 | Operational. No personal meaning alone. | Full warehouse access. |
| 1 | Personal, not special-category — consent states, coarse location, permissions, life circumstances. | Access-controlled. |
| 2 | **Special-category, GDPR Art. 9** — gender, orientation, religion, politics, health, substance use. | **Bucketed on device; raw values never transmitted.** Restricted role. |
| 3 | **Biometric or safety-critical.** | **Result flags only, never the artefact.** Named-individual access, audit-logged. |
| `inherit` | Class comes from the `field_id` registry, stamped at emit time. | Used by the generic attribute events. |

**Delivery tiers**

- **Tier 1** — high-volume behavioural. Batched, best-effort, may be sampled. Loss tolerable.
- **Tier 2** — business-critical. **Guaranteed delivery, retried, never sampled.** Feeds funnels, score and billing. Tier 2 is not a hint; a dropped Tier 2 event is a data incident.

**Conventions** — implicit in v1.0, recorded because emitters get them wrong:

- **Naming:** `object_action`, past tense, lower snake case. An event names something that *happened*, never an intention.
- **`_viewed` vs `_shown`:** `_viewed` is user-initiated arrival; `_shown` is system-initiated presentation. Load-bearing for guard and gate modals — do not collapse them.
- **Client vs server:** the client fires intent and UI state; the server fires the committed fact. Where both exist (`date_confirm_submitted` and `date_confirmed`), **the server row is the one to count.**
- **Free text is never sent.** Only `char_count`, `has_free_text`, or a bucket. Without exception — prompts, bios, chat, reports, reviews.
- **Bucketing happens on device.** A Class 2 value is bucketed *before it leaves the client*, so the pipeline never receives a raw special-category string and there is nothing to strip later.
- **Multi-select events** fire once per change with a running total (`interest_selected` carries `total_selected`) — never one event per final set.

**Decision log.** 21 resolved conflicts between the taxonomy and the code, in `requirements.json` → `decision_log` and readable in `reports/taxonomy-conflicts.html`. Each carries its outcome and which side moved. Two that bite immediately: **02** the OTP is six digits, the code changes (build item 07); **07c** the per-field visibility opt-out is tracked as one directional event with **completion-time state authoritative over the event stream** (build item 02).

Where this document and the code disagree, **the taxonomy follows the code and says so** — the same rule the glossary uses.

### Two sequencing facts

1. **`code_anchor` is the build list.** Roughly a third of events are `server` — those cannot be bolted on by the client later. Each names the service that must emit it.
2. **`modules/analytics/analytics.module.ts` is a 175-byte placeholder.** PostHog is configured at boot in `config/configuration.ts`, but no event in the taxonomy is emitted or received yet. The spec being finished does not mean tracking is live.

**Generate, don't retype.** The enum registries in `enums.json` §1 and §10 are agreed vocabulary. If they are hand-copied into TypeScript and Postgres enums they will drift on the first edit. Generate both from the JSON, or commit the JSON into the repo as the shared source both sides read. `tracking/README.md` spells out the mechanics, including the `registry_version` / `taxonomy_version` distinction that the `field_registry_version` stamp depends on.

---

## Build items

Nine gaps between these designs and the backend at `main @ c195089`. Full reasoning in `reports/design-vs-backend.html`; ordered by how much rework each causes if decided late. **Two (07, 08) were decided on 6 August and are now build specs rather than questions.**

**Decide 01, 02 and 03 together.** They are the same decision seen three times — where profile attributes and search preferences live, over which vocabulary. Answered together they are one migration; answered separately they are three, and the third will contradict the first.

| # | Item | Owner |
| --- | --- | --- |
| 01 | **Six of nine profile steps have nowhere to live.** Designed: height, gender, orientation, habits, interests (max 10), prompts (pairs), living situation, age, + a per-field visibility flag. In code: `displayName`, `dateOfBirth`, `bio`, `gender varchar(40)`, `lookingFor`, `isVisible`, `isComplete`, `verificationStatus`, `moderationStanding`. Two calls: **shape** (typed columns / one JSONB blob / child tables — interests and prompts are multi-row and capped, so they want tables regardless) and **vocabulary ownership** (generate Postgres enums from the registry, don't retype). Urgent because matching and discovery read profile attributes: choosing late means rewriting the query layer, not adding a migration. | Eng + Product |
| 02 | **"Don't display on my profile" has no persisted state.** Conflict 07c requires completion-time state to be **authoritative over the event stream** — a user who ticks then unticks must end up displayed. Today it is only inferable from analytics, which the decision explicitly ruled out. Needs a `profile_field_visibility` row per hidden field, or a mask on the profile. Rides along with 01. | Engineering |
| 03 | **Discovery has no filters, and `lookingFor` sits in the wrong place.** Matching centres on the viewer's active check-in and searches a hard-coded `DEFAULT_DISCOVERY_RADIUS_METERS = 50_000` with no filter state at all. Designed: `meet_gender` multi, `meet_orientation` multi, `age_range` 18–99, user-set `distance_km`. The taxonomy already resolved (Enums §10) that `looking_for` is **search state, not a profile attribute** — move it to a discovery-preferences table over the same enums, and have `getDiscovery` read it. | Engineering |
| 04 | **The 10-minute grace rule does not exist.** Backend has one path — `cancel(dateId, userId, reason?)`. Nothing models lateness, the grace window, or the asymmetry. Needs: a "running late" declaration; a timestamp to measure the window from; a distinct terminal state for *closed because one side left inside the rules* vs an ordinary cancellation; and the rule that the waiter's score is untouched. Product confirms ten minutes is fixed and universal; Engineering adds the states. A lifecycle change, not a field. | Product + Eng |
| 05 | **Show-up Rate is displayed everywhere and computed nowhere.** Screens show a concrete number and a concrete movement (94% → 90%). The backend stores the inputs (`aRating`, `bRating`, `aConfirmedHappened`, `bConfirmedHappened`, plus a permanent per-date status history — a good foundation) but derives no score. **Needs the formula, and it is a Product decision:** what counts as a miss, what a late cancellation costs, lifetime or rolling window, new-user starting value, and whether the number is public or owner-only. Every screen quoting a percentage is unbuildable until this is answered — and a placeholder formula becomes visible to users the moment it is wrong. **Do not guess it.** | **Product** |
| 06 | **The 270-event taxonomy has no ingestion point.** Sequencing note, not a conflict — see Tracking above. | Not started |
| 07 | **OTP must be six digits.** ✅ **Decided 6 Aug 2026 — six digits, the design is the truth, the code changes.** Not yet developed; still four digits in `otp.service.ts` at this commit. A two-line change with a test alongside it. Applies to **both** code paths — the phone SMS OTP and the email OTP in item 08. | Decided · build |
| 08 | **Email capture + verification.** ✅ **Decided 6 Aug 2026 — an email address will be requested during profile creation and must be verified by a 6-digit code.** Not yet developed; the design is the target. The two code screens are different things and neither replaces the other: **Verify code** is phone verification (flow "01 · Welcome & sign-up", backed by `auth/otp.service.ts`) and **Verify email** is labelled "03 · Profile creation · Email verification", step 2 of 3 of *The basics*, running **after** phone auth. **To build:** an email column on the account or profile; an outbound mail sender; a **second OTP path distinct from the SMS one**, also 6 digits, with its own resend cooldown and attempt counter; and a verification state so collected-but-unverified is distinguishable from verified. The five Family D events (`email_submitted`, `email_validation_failed`, `email_code_sent`, `email_code_resent`, `email_code_submitted`) already carry their full spec and flip from `Not built` to `Live` once it exists — **no taxonomy change needed.** | Decided · build |
| 09 | **Profile completion ignores the detail steps.** `isProfileComplete` requires a display name, a DOB and four photos — so a profile passes as complete with all nine designed steps skipped, making matching thinner than the designs assume. Decide whether any step is required, or a minimum count across them, or completion stays photo-led on purpose to keep onboarding short. "Leave it" is a legitimate answer, but it should be a decision. | Product |

### Where design and backend already agree

Stated so nobody re-decides them: photos min 4 / max 6 · the four verification states (`none · pending · verified · rejected`, code was right and the taxonomy adopted it) · biometric consent mandatory with raw capture discarded · one active check-in at a time, stacking blocked · no continuous location tracking (coordinates never leave the server, distances rounded to 100m, nothing raw reaches analytics — **stronger than the designs promise**) · a match needs mutual interest *and* both live, with likes persisted so nothing is lost · both sides confirm before a date completes, with a permanent status history · blocks hide people in both directions, enforced in discovery and on likes.

### Keeping it aligned

Re-run the design-vs-backend diff at **every commit that touches an entity**. The expensive gaps here are all shape decisions — where a field lives, what states a lifecycle has. Those are cheap before the migration and expensive after.

---

## Files in this bundle

```
design_handoff_showup/
├── README.md                     this document — self-sufficient
├── CLAUDE.md                     drop into the repo root; project memory for Claude Code
├── designs/                      11 screen prototypes + the full UI kit + tokens
│   └── ui_kits/show-up/          the JSX the prototypes load at runtime (8 files)
├── components/shared.jsx         primitive reference implementation — port, don't import
├── tokens/colors_and_type.css    every token; import verbatim or transcribe
├── tracking/                     README.md · events.json · properties.json · enums.json · requirements.json
└── reports/                      design-vs-backend + the 5 taxonomy pages (open in browser)
```

Not included: the 16 portrait JPEGs (prototype content, not shippable assets) and the Lora/Manrope woff2 binaries (CDN-delivered via the CSS `@import`; ask if you want them self-hosted).

## Open questions for the design side

- **The Show-up Rate formula** (item 05) is not designed yet — the score screens are blocked until it is, and it should not be guessed.
- **Is a verified email ever the *only* way to reach or recover an account?** Email itself is decided (item 08); this narrower question decides whether it is an identity credential needing a recovery path, or notification-only.
- Font delivery: is Google Fonts CDN acceptable, or should the woff2 files be vendored?
