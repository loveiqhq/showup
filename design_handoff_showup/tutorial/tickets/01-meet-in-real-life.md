# [Onboarding 01] Meet in real life — tutorial card 01

**Parent:** App Onboarding / Tutorial flow (SHOWUP-118) · **Type:** Story · **Priority:** Medium
**Attachments:** `01-meet-in-real-life-spec-sheet.png`

## Description

Card 01 of the 5-card tutorial, reached from the Welcome screen (SHOWUP-117) via **Show me how**. It states the product's core promise — matches here are real, committed meetings — and the reliability consequence that backs it. No inputs, no skip; forward via **Next**, and no Back on this card (it is the first card of the tour).

**This card is the reference implementation of the shared card chrome.** The step progress bar, eyebrow pill, headline treatment, body scale, illustration block and the Back/Next nav row built here are reused unchanged by cards 02–05 — build them as the card shell, with per-card content passed in.

📎 All specs are on the attached **01-meet-in-real-life-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–⑨ to the screen. Note the design system has colour and type tokens only — there is no spacing scale, so the raw px values are intentional, not an oversight.

⚠ **Layout is flex, not absolute.** Progress → eyebrow → headline → rule list → body → illustration is one top-anchored column; the nav row is bottom-anchored; a single `flex: 1` spacer between the illustration and the nav row absorbs every height difference. Do not hard-code Y positions — they break on any frame that isn't 844 tall. Anchor the progress bar to `env(safe-area-inset-top) + 8`, not to the mock's 54px status bar.

## Acceptance criteria

- [ ] Built as nested flex columns — no absolute Y positioning; one `flex: 1` spacer is the only element that absorbs height change
- [ ] Nav row bottom-anchored, 24 above the content region floor
- [ ] Step progress sits below the device safe-area top inset; 5 segments, segment 1 filled
- [ ] Back slot is present but invisible and non-interactive on card 01 (reserves layout width); no skip affordance
- [ ] Screen shows all content fully visible in viewport on devices with 375 × 667, 390 × 844 and 430 × 932 — nothing overlaps or clips; the Next CTA is always visible and accessible
- [ ] The three rule rows do not clip or truncate at 375 width (see open question)
- [ ] Type, colour and spacing match the spec sheet, including the three-part rule rows (rule `#1D1129` · “ — ” 24% ink · consequence `#812AEC`)
- [ ] Eyebrow renders as a hug-width pill (`align-self: flex-start`), not a full-width band — the reference render on the spec sheet shows the current full-width behaviour, see open questions
- [ ] Headline renders “actually” in Lora italic with the underline accent
- [ ] Illustration renders without visible banding on device (radial glow + blur)
- [ ] Next → tutorial card 02; no back navigation to Welcome
- [ ] Card chrome is implemented as a reusable shell that cards 02–05 can adopt with content only

## Tracking

Event names come from `design_handoff_showup/tracking/events.json`. Use them exactly — do not invent names, and do not restate a payload here that the registry already defines.

⚠ **This screen is card 2 in the data, not card 01.** The Welcome screen (SHOWUP-117) is card 1, so every shipped card number is one higher than its Jira card number — decided 7 Sep 2026, mapping in `tracking/enums.json` §14. A funnel filtered on the Jira number returns the wrong screen.

| What | Event | Payload |
|---|---|---|
| Screenview | `tutorial_card_viewed` | `card: 2`, `card_name: "meet_in_real_life"` |
| Next | `tutorial_cta_tapped` | `card: 2`, `card_name`, `dwell_ms` — time on card, **missing from the current build** |

No Back on this card, so no `tutorial_back_tapped` — the invisible Back slot must not fire one.


## Out of scope

Tutorial cards 02–05 content, the Welcome screen (SHOWUP-117), analytics event implementation, swipe-between-cards gesture, and the spacing-scale refactor (separate tickets).

## Dependencies

- Shared chrome components: step progress, eyebrow pill, circular-arrow Next button
- Illustration asset export from design (two avatars + sunset heart, 248 × 210)
- Lora + Manrope bundled in the app font set

## Open (not blocking)

- **Eyebrow width.** In the reference render the eyebrow stretches to the full 342 content width (it inherits the column's `align-items: stretch`), while the design-system component is a hug-width pill. Treating the stretch as a bug in the card layout: the shell should set `align-self: flex-start`. Design to confirm before 02–05 inherit it.
- The rule rows are specced `white-space: nowrap` at 13px; the longest row is close to the 342 content width and will be tight at 375. Design to confirm the fallback — allow wrap, or drop to 12.5px on narrow frames.
- Gap between the rule list and the first body paragraph is documented as 16; design to confirm it should not match the 12 used inside cards 02–05.
