# [Onboarding 03] Match means meet — tutorial card 03

**Parent:** App Onboarding / Tutorial flow (SHOWUP-118) · **Type:** Story · **Priority:** Medium
**Attachments:** `03-match-means-meet-spec-sheet.png`

## Description

Card 03 of the 5-card tutorial, reached from card 02 (Match on availability) via **Next**. This is the card that states the product's hardest rule: a match is not a conversation opener, it is a booked date. It sets the division of labour — the user chooses who, the product chooses when and where.

**This card consumes the card shell built in card 01** — step progress, eyebrow pill, headline treatment, rule-list scale, illustration block and the Back/Next nav row are inherited unchanged. Only the content and the Back target differ. If anything in the shell needs to change to make this card work, change it in the shell and re-verify cards 01–02.

Content notes:

- **No body paragraphs** — the three rule rows carry the whole card. The body-copy slot must collapse its gap when empty, not reserve height.
- **Rule-row gap is 12** and rows may wrap (as card 02; card 01 uses 8 with single-line rows).
- **Short headline** (2 lines at 390, against 3 on cards 01–02). The card therefore carries more spacer height than the cards before it — a good regression case that the `flex: 1` spacer, not fixed margins, is doing the work.

📎 All specs are on the attached **03-match-means-meet-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–⑧ to the screen. Note the design system has colour and type tokens only — there is no spacing scale, so the raw px values are intentional, not an oversight.

⚠ **Layout is flex, not absolute.** Progress → eyebrow → headline → rule list → illustration is one top-anchored column; the nav row is bottom-anchored; a single `flex: 1` spacer between the illustration and the nav row absorbs every height difference. Do not hard-code Y positions — they break on any frame that isn't 844 tall. Anchor the progress bar to `env(safe-area-inset-top) + 8`, not to the mock's 54px status bar.

## Acceptance criteria

- [ ] Built on the shared card shell from card 01 — no forked copy of the chrome
- [ ] Step progress shows segments 1–3 filled
- [ ] Body-paragraph slot is empty and its gap collapses — no reserved height
- [ ] Rule rows use gap 12 and are allowed to wrap; three rows render without clipping
- [ ] Back is active and returns to card 02; Next advances to card 04; no skip affordance
- [ ] Headline renders "binding" in Lora italic with the underline accent, 2 lines at 390
- [ ] Woven-rings illustration matches the spec: two r46 rings, stroke 15, centres (93,100) / (141,100), sunset gradient left and violet gradient right, **woven via the 14r mask at (117,61)** so sunset reads over violet at the top and violet over sunset at the bottom, heart seal at the lower link, spark above
- [ ] The weave is preserved in whatever form the asset ships as — a flattened export that stacks one ring fully over the other is a fail
- [ ] Illustration renders without visible banding on device (radial glow + blur)
- [ ] Screen shows all content fully visible in viewport on 375 × 667, 390 × 844 and 430 × 932 — nothing overlaps or clips; the Next CTA is always visible and accessible
- [ ] With the short headline, the extra height lands in the spacer — the illustration stays centred in its block and the nav row stays 24 above the content floor on all three device sizes

## Tracking

Event names come from `design_handoff_showup/tracking/events.json`. Use them exactly — do not invent names, and do not restate a payload here that the registry already defines.

⚠ **This screen is card 4 in the data, not card 03.** The Welcome screen (SHOWUP-117) is card 1, so every shipped card number is one higher than its Jira card number — decided 7 Sep 2026, mapping in `tracking/enums.json` §14. A funnel filtered on the Jira number returns the wrong screen.

| What | Event | Payload |
|---|---|---|
| Screenview | `tutorial_card_viewed` | `card: 4`, `card_name: "match_means_meet"` |
| Next | `tutorial_cta_tapped` | `card: 4`, `card_name`, `dwell_ms` — time on card, **missing from the current build** |
| Back | `tutorial_back_tapped` | `card: 4`, `card_name` |


## Out of scope

Cards 04–05 content, the actual match → date-booking flow (this is explanatory copy only), the safe-place selection logic, analytics event implementation, swipe-between-cards gesture, and the spacing-scale refactor.

## Dependencies

- Card shell from card 01 (blocking)
- Woven-rings illustration export from design (234 × 200 svg, mask intact)
- Lora + Manrope in the app font set

## Open (not blocking)

- **Eyebrow width.** Same as cards 01–02: reference render stretches the eyebrow to full content width; the design-system component is a hug-width pill. Fix belongs in the shell (`align-self: flex-start`).
- **"binding" as user-facing copy.** Legally loaded word for a consumer app — confirm with legal/content that "binding" is the term we want in onboarding, or swap for "committed". This card is the cultural filter, so the strength is deliberate; flagging it rather than softening it.
- **"We pick the place."** The rule promises a safe, public spot halfway between both users. Confirm the tutorial should state this as an absolute before the place-selection feature can guarantee it.
