# [Onboarding 04] 30 minutes — tutorial card 04

**Parent:** App Onboarding / Tutorial flow (SHOWUP-118) · **Type:** Story · **Priority:** Medium
**Attachments:** `04-thirty-minutes-spec-sheet.png`

## Description

Card 04 of the 5-card tutorial, reached from card 03 (Match means meet) via **Next**. Card 03 tells the user a match is a real, booked date; this card removes the fear that follows from it. A date is thirty minutes, leaving after them is socially sanctioned, and the product supplies icebreakers so nobody has to carry the conversation alone.

**This card consumes the card shell built in card 01** — step progress, eyebrow pill, headline treatment, rule-list scale, illustration block and the Back/Next nav row are inherited unchanged. Only the content and the Back target differ.

Content notes:

- **No body paragraphs** — the three rule rows carry the card; the body slot collapses.
- **Rule-row gap is 12**, rows may wrap (as cards 02–03).
- **Shortest headline in the set** — "Just *thirty minutes*." is a single line at 390, where cards 01–02 run to three. This card therefore has the largest spacer of the five: the strongest regression case that the `flex: 1` spacer, not fixed margins, absorbs the difference.

📎 All specs are on the attached **04-thirty-minutes-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–⑧ to the screen. Note the design system has colour and type tokens only — there is no spacing scale, so the raw px values are intentional, not an oversight.

⚠ **Layout is flex, not absolute.** Progress → eyebrow → headline → rule list → illustration is one top-anchored column; the nav row is bottom-anchored; a single `flex: 1` spacer between the illustration and the nav row absorbs every height difference. Do not hard-code Y positions — they break on any frame that isn't 844 tall. Anchor the progress bar to `env(safe-area-inset-top) + 8`, not to the mock's 54px status bar.

## Acceptance criteria

- [ ] Built on the shared card shell from card 01 — no forked copy of the chrome
- [ ] Step progress shows segments 1–4 filled
- [ ] Body-paragraph slot is empty and its gap collapses — no reserved height
- [ ] Rule rows use gap 12 and are allowed to wrap; three rows render without clipping
- [ ] Back is active and returns to card 03; Next advances to card 05; no skip affordance
- [ ] Headline renders "thirty minutes" in Lora italic with the underline accent, on one line at 390
- [ ] Illustration matches the spec: r84 track (stroke 4) with a **50% sweep** progress arc (stroke 6, sunset→violet, from 12 o'clock, round caps), gradient cup with 11r handle and paper crema ellipse, two steam curls with the heart between them, "30 min" label
- [ ] The arc is exactly 50% — it is read as "half an hour", so an approximate sweep is a fail
- [ ] Illustration renders without visible banding on device (radial glow + blur)
- [ ] Screen shows all content fully visible in viewport on 375 × 667, 390 × 844 and 430 × 932 — nothing overlaps or clips; the Next CTA is always visible and accessible
- [ ] With the one-line headline, the extra height lands in the spacer — the illustration stays centred in its block and the nav row stays 24 above the content floor on all three device sizes
- [ ] Headline stays on one line at 430 width and is allowed to wrap at 375 without pushing the illustration off-screen

## Tracking

Event names come from `design_handoff_showup/tracking/events.json`. Use them exactly — do not invent names, and do not restate a payload here that the registry already defines.

⚠ **This screen is card 5 in the data, not card 04.** The Welcome screen (SHOWUP-117) is card 1, so every shipped card number is one higher than its Jira card number — decided 7 Sep 2026, mapping in `tracking/enums.json` §14. A funnel filtered on the Jira number returns the wrong screen.

| What | Event | Payload |
|---|---|---|
| Screenview | `tutorial_card_viewed` | `card: 5`, `card_name: "thirty_minutes"` |
| Next | `tutorial_cta_tapped` | `card: 5`, `card_name`, `dwell_ms` — time on card, **missing from the current build** |
| Back | `tutorial_back_tapped` | `card: 5`, `card_name` |


## Out of scope

Card 05 content, the icebreaker feature itself, the 30-minute date timer / end-of-date flow (this is explanatory copy only), analytics event implementation, swipe-between-cards gesture, and the spacing-scale refactor.

## Dependencies

- Card shell from card 01 (blocking)
- Coffee-and-arc illustration export from design (200 × 200 svg)
- Lora + Manrope in the app font set

## Open (not blocking)

- **Eyebrow width.** Same as cards 01–03: reference render stretches the eyebrow to full content width; the design-system component is a hug-width pill. Fix belongs in the shell (`align-self: flex-start`).
- **"30 minutes up — stay if you're vibing."** The copy implies the 30 minutes is a floor, not a cap, while the headline and the dial imply a fixed length. Confirm which the product actually does before this ships as the user's mental model.
- **Icebreakers.** Row 3 promises built-in prompts. Confirm they exist in the date experience at tutorial launch, or cut the row — promising a feature in onboarding that isn't there is worse than a two-row card.
