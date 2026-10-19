# Show Up — project memory

Drop this at the repo root so Claude Code picks it up automatically. It is a condensed standing brief; the full detail is in `design_handoff_showup/README.md`.

## What this app is

A dating app where **a match is a binding date**. No open-ended texting. On a match, the app schedules a 30-minute date at a mutually available time and a halfway-point public location. Every profile carries a public **Show-up Rate**; miss dates and it drops, drop too low and you stop getting dates.

Product surface is **iOS-first**, drawn at 390×844. Backend is **NestJS + TypeORM + PostGIS**, API only — no frontend exists yet.

## Non-negotiables

- **No emoji.** Anywhere.
- **No pure white, no pure black, no neutral grey.** Canvas `#FFFBF7`, ink `#1D1129`, borders are warm-ink alpha `rgba(29,17,41,0.12)`.
- **Buttons and chips are always pills** (9999). No rounded rectangles for interactive controls. **One exception: third-party sign-in buttons** — see *Provider sign-in buttons* below. Those follow Apple's / Google's / Facebook's own specs, not ours.
- **Lora italics get the orange underline wash.** One italicised word per headline, always with the wash. Manrope italics are never used.
- **Icons are inline stroke-only SVG**, Lucide geometry, 24×24, `stroke-width: 1.7`, currentColor. No icon font, no PNGs, no unicode glyphs as icons.
- **You / your.** Never "users" or "members".
- **Proper nouns capitalise as shown:** Show Up · Show-up Rate · Instant Mode · Match Mate · Check-In.
- **Shadows are violet- or orange-tinted, never grey.** No inner shadows. No accent left-border cards.
- **No hover states** — mobile-first. Press scales to `0.98`, snaps back.

## Provider sign-in buttons — binding, decided 26 Aug 2026

**Compliance wins over our visual system. Every time, without asking.** Where an Apple, Google or Facebook sign-in button appears, it is built to *that provider's* current published button specification — their asset, their mark, their permitted fills, their title text, their minimum size, their corner radius, their font — and **not** to Show Up's pill / gradient / token rules.

- **Never restyle, re-tint, recolour, crop or redraw a provider mark or button.** Use the asset or the SDK-supplied button the provider ships. If a spec sheet shows a provider button in the sunset gradient, **the sheet is wrong and the guideline wins** — build compliant and tell design.
- **All three providers render at equal prominence.** No provider is promoted, enlarged, gradient-filled or given a shadow the others do not have. Apple in particular requires its button be no less prominent than any other third-party sign-in option — so "promote the last-used / tapped provider" must never be expressed by restyling a provider button.
- **"Last used" and "in flight" are signalled without touching provider styling** — the hint row above the stack, the ordering, the disabled state, or a spinner in the provider's own permitted style. Never a fill swap.
- **The sunset gradient is reserved for Show Up's own CTAs** — phone sign-in, Continue, Next, the skip. Never for a provider button.
- **Verify against the guidelines published at build time**, not against our spec sheets and not against this file: Apple *Sign in with Apple* HIG + brand guidelines, Google *Sign in with Google* branding guidelines, Facebook Login brand guidelines. They change; our PNGs do not.
- **Where our layout and a guideline conflict, the guideline ships and the conflict is raised** on that screen's ticket. Do not split the difference.

Affected screens: **welcome 02** (re-login, four methods) and **welcome 04** (connect an account, three providers plus the conflict modal's resolve CTA). Their spec sheets are **indicative on provider-button styling only** — every other number on them still holds.

## Hidden age — binding, decided 7 Sep 2026

Profile creation step 3 (date of birth) carries a **`Don't display on my profile`** checkbox, unchecked by default. **Checked hides the age from the profile. The age is still used by the matching algorithm** — it is a display choice, never an eligibility or matching one, and no code path may treat a hidden age as a missing one.

The **age itself is locked** after that step (self-declared 18+, re-derived server-side on submit); the **visibility choice is not** — it stays changeable in profile settings. Copy on the screen is final: do not reword it, and do not add the band to any other "The basics" screen.

## Tokens

`design_handoff_showup/tokens/colors_and_type.css` is authoritative. Key values:

- Canvas `#FFFBF7` · elevated `#FFFFFF` · raised `#F7F2FA` · ink `#1D1129` · muted `rgba(29,17,41,0.62)`
- Primary violet `#812AEC` · lavender `#A78BFA` · **CTA orange `#FE6839`** · success `#00AB55` · danger `#FB323B`
- Sunset gradient `linear-gradient(135deg, #FE6839, #D05976 38%, #812AEC)`
- Type: **Lora** (headlines, wordmark, all large numerals) + **Manrope** (everything else)
- 8px spacing base · default card radius 20 · easing `cubic-bezier(.22,1,.36,1)` · 180ms state, 280ms progress, 600ms score ring

Every screen carries the ambient wash — orange glow top-right, violet bottom-left — as one component with two intensities (`hero` 0.26/0.22, `form` 0.13/0.11). Never hand-rolled per screen.

## Vocabulary is generated, not retyped

`design_handoff_showup/tracking/enums.json` holds the agreed option sets, including §1 `field_id` (9 profile fields) and §10 `filter_id`. **Generate the Postgres and TypeScript enums from it** — or commit it into the repo as the shared source both the docs and the code read. Hand-copying guarantees drift on the first edit.

## Tracking

270 events / 20 families / 71 properties, in `tracking/events.json` + `properties.json`, reconciled against `main @ c195089`. **`tracking/requirements.json` holds the rules they must be emitted under — read it before writing an emitter.**

- Each event's `code_anchor` names the service that must emit it — **that is the build list**.
- Roughly a third are `source: server`. Those cannot be added by the client later. Where a client and a server event both exist (`date_confirm_submitted` / `date_confirmed`), **the server row is the one to count.**
- `modules/analytics/analytics.module.ts` is still a 175-byte placeholder. PostHog is configured at boot; nothing is emitted yet. A finished spec is not live tracking.

### Emitter rules — binding

- **Stamp sensitivity at emit time.** Every attribute event carries `sensitivity_class` (0–3, resolved from the `field_id` registry) **and** `field_registry_version`. Never join the class downstream — a join returns today's classification, not the one the row was collected under.
- **Fail closed:** an unrecognised `field_id` is treated as **Class 2** until a registry row exists, and counted in an *unclassified attributes* metric.
- **Class 2 (special-category — gender, orientation, religion, politics, health, substance use) is bucketed on device.** The raw value never leaves the client, so there is nothing to strip later. **Class 3 (biometric/safety) sends result flags only, never the artefact.**
- **Free text is never sent.** Only `char_count`, `has_free_text`, or a bucket — prompts, bios, chat, reports, reviews, no exceptions.
- **Tier 2 is guaranteed delivery**, retried, never sampled — it feeds funnels, score and billing. Tier 1 may be batched and sampled.
- **Naming:** `object_action`, past tense, lower snake case — an event names something that happened, never an intention. `_viewed` is user-initiated arrival; `_shown` is system-initiated presentation. Don't collapse them.
- **Multi-select** fires once per change with a running total, never one event per final set.
- The `field_id` registry is **one versioned file in this repo**, compiled into both client and pipeline. Not a spreadsheet — two copies drift within a release. `tracking/README.md` has the mechanics.
- `registry_version` (the vocabulary in `enums.json`) and `taxonomy_version` (the spec document) are **different numbers and move independently**. The stamp uses `registry_version`.

## Screen layout — binding

- **No absolute Y positioning on any full-screen flow.** Screens are one top-anchored flex column, a bottom-anchored action row, and a single `flex: 1` spacer between them that absorbs the height difference. Offsets that look right at 390×844 break on 375×667.
- **Illustrations are the flexible element.** When content does not fit, the artwork shrinks — never the type, never the action row's safe-area margin, and never into a scroll.
- **Every screen is checked at 375 × 667, 390 × 844 and 430 × 932.** All content visible, nothing clipped, no scroll, primary action always reachable. 375 × 667 is where screens fail.
- **Progress bars and top chrome anchor to `env(safe-area-inset-top)`,** never to a fixed status-bar height.
- **Shared chrome is built once as a shell with variants.** A second copy of a progress bar or nav row is a bug, not a screen.
- **Failure states reserve their space and preserve their input.** Every input screen carries a status region under the field with a fixed `min-height` sized to its tallest card, `aria-live="polite"`, empty in the calm state. It is reserved, not conditional — that is what stops the action row moving when validation fails. Never clear what the user typed.
- **No CTA on a free-text input screen is ever disabled.** Pressing it while empty or invalid produces the inline error plus a one-shot `su-shake` 480ms; a dead button cannot explain itself, and it means the specific error strings are never seen for an empty field. Recovery is the same everywhere: the error clears when the value becomes valid — not on blur, not on re-submit — and does not re-fire until the CTA is pressed again. Whitespace-only counts as empty. Settled on the date-of-birth screen and binding across the profile flow. The one exception is a **fixed-length code entry**, where the CTA waits for a complete code because a partial one has nothing to validate.
- **Empty-field copy has one shape:** “Enter your &lt;thing&gt; to continue.” Do not vary the sentence per screen. Generic messages like “Please enter a valid email” are banned — name the missing piece so the fix is one keystroke.
- **The inline error card is one component.** Pad 10 / 14, radius 12, bg `rgba(251,50,59,0.07)`, 1px `rgba(251,50,59,0.18)`, 18px round `--liq-danger` glyph, message Manrope 500 / 13.5 / 1.4 in `--liq-danger-fg`, glued to its field — never a toast, never re-styled per screen.

The onboarding tutorial (5 cards) is specced in `design_handoff_showup/tutorial/` — reference source, tickets and spec sheets. Card 01 builds the shell; 02–05 consume it.

The pre-account screens are in `design_handoff_showup/welcome/`, and profile creation in `design_handoff_showup/profile/`. Each flow folder carries its own README with the rules that flow does not share with the others — read it before touching a screen in it.

## Rules the backend already enforces — don't relitigate

Photos min 4 / max 6 · verification states `none · pending · verified · rejected` · biometric consent mandatory, raw capture discarded, one-way template kept · one active check-in at a time · no continuous location tracking · a match needs mutual interest **and** both live, likes persist · both sides confirm before a date completes, with a permanent status history · blocks hide people in both directions.

## Known gaps — read before touching entities

Full detail and ownership in the handoff README. Short form:

1. **Six of nine profile steps have no column** (height, gender, orientation, habits, interests max 10, prompts, living situation, age, per-field visibility). Interests and prompts are multi-row and capped — they want tables.
2. **"Don't display on my profile" has no persisted state.** Completion-time state must be authoritative over the event stream.
3. **Discovery has no filters**, and `lookingFor` is search state sitting on the profile — it belongs in a discovery-preferences table over the same enums.
4. **The 10-minute grace rule does not exist** in the date lifecycle. It needs new states, not a field.
5. **Show-up Rate is computed nowhere.** Inputs are stored; no formula exists. **Product decision — do not guess it**, a wrong placeholder is visible to users immediately.
6. **OTP is six digits** — decided 6 Aug 2026, design is the truth. Still four in `otp.service.ts`; not yet developed. Applies to **both** the phone SMS OTP and the email OTP.
7. **Email is required and must be verified** — decided 6 Aug 2026. Requested during *profile creation* (not sign-in — that is phone), verified by a **6-digit code**. Needs an email column, a mail sender, and a **second OTP path distinct from the SMS one**, plus a verification state. Five Family D events already specced; they go `Live` once it exists.
8. `isProfileComplete` ignores all nine detail steps.

**Decide 1, 2 and 3 as one piece of work.** They are the same decision seen three times: where profile attributes and search preferences live, over which vocabulary. Together they are one migration. Separately they are three, and the third contradicts the first.

**Re-run the design-vs-backend diff at every commit that touches the profile, date or match entities.** Shape decisions are cheap before the migration and expensive after.
