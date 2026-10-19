# [Onboarding 02] Match on availability — tutorial card 02

**Parent:** App Onboarding / Tutorial flow (SHOWUP-118) · **Type:** Story · **Priority:** Medium
**Attachments:** `02-match-on-availability-spec-sheet.png`

## Description

Card 02 of the 5-card tutorial, reached from card 01 (Meet in real life) via **Next**. It explains the availability model that makes the promise on card 01 possible: you are only visible while you are free to date, you only see people whose windows overlap yours, and what you're in the mood for is per-check-in rather than a static bio.

**This card consumes the card shell built in card 01** — step progress, eyebrow pill, headline treatment, rule-list scale, illustration block and the Back/Next nav row are inherited unchanged. Only the content and the Back state differ. If anything in the shell needs to change to make this card work, change it in the shell and re-verify card 01.

Two content differences from card 01 the shell must accommodate:

- **No body paragraphs.** Card 02 carries the three rule rows only. The body-copy slot must be optional and collapse its gap when empty — not reserve height.
- **Rule-row gap is 12, not 8**, and the rows wrap here (card 01's are single-line). The gap belongs to the card content, not the shell.

📎 All specs are on the attached **02-match-on-availability-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–⑧ to the screen. Note the design system has colour and type tokens only — there is no spacing scale, so the raw px values are intentional, not an oversight.

⚠ **Layout is flex, not absolute.** Progress → eyebrow → headline → rule list → illustration is one top-anchored column; the nav row is bottom-anchored; a single `flex: 1` spacer between the illustration and the nav row absorbs every height difference. Do not hard-code Y positions — they break on any frame that isn't 844 tall. Anchor the progress bar to `env(safe-area-inset-top) + 8`, not to the mock's 54px status bar.

## Acceptance criteria

- [ ] Built on the shared card shell from card 01 — no forked copy of the chrome
- [ ] Step progress shows segments 1–2 filled
- [ ] Body-paragraph slot is empty and its gap collapses — no reserved height
- [ ] Rule rows use gap 12 and are allowed to wrap; three rows render without clipping
- [ ] Back is active and returns to card 01; Next advances to card 03; no skip affordance
- [ ] Headline renders "free to date" in Lora italic with the underline accent (3 lines at 390)
- [ ] Dual-dial illustration matches the spec: outer sunset arc r84 50% sweep at −125°, inner violet arc r67 44% sweep at −45°, both on a `--liq-border` track, heart at the overlap, "48h" centre label with the `WHEN YOU'RE BOTH FREE` caption
- [ ] Illustration renders without visible banding on device (radial glow + blur)
- [ ] Screen shows all content fully visible in viewport on 375 × 667, 390 × 844 and 430 × 932 — nothing overlaps or clips; the Next CTA is always visible and accessible
- [ ] The three-line headline does not push the illustration or nav row off-screen at 375 × 667

## Tracking

Event names come from `design_handoff_showup/tracking/events.json`. Use them exactly — do not invent names, and do not restate a payload here that the registry already defines.

⚠ **This screen is card 3 in the data, not card 02.** The Welcome screen (SHOWUP-117) is card 1, so every shipped card number is one higher than its Jira card number — decided 7 Sep 2026, mapping in `tracking/enums.json` §14. A funnel filtered on the Jira number returns the wrong screen.

| What | Event | Payload |
|---|---|---|
| Screenview | `tutorial_card_viewed` | `card: 3`, `card_name: "match_on_availability"` |
| Next | `tutorial_cta_tapped` | `card: 3`, `card_name`, `dwell_ms` — time on card, **missing from the current build** |
| Back | `tutorial_back_tapped` | `card: 3`, `card_name` |


## Out of scope

Cards 03–05 content, the real availability/check-in feature itself (this is explanatory copy only), analytics event implementation, swipe-between-cards gesture, and the spacing-scale refactor.

## Dependencies

- Card shell from card 01 (blocking — this card should not be started before the shell lands)
- Dual-dial illustration asset export from design (200 × 200 svg)
- Lora + Manrope in the app font set

## Open (not blocking)

- **Eyebrow width.** Same as card 01: the reference render stretches the eyebrow to the full 342 content width, while the design-system component is a hug-width pill. Fix belongs in the shell (`align-self: flex-start`) — confirm once, apply to 01–05.
- **"48h" in the dial.** The centre label states a fixed 48-hour window. Confirm this matches the real matching window before it ships as a factual claim in the tutorial; if the window is configurable, the illustration needs a non-numeric treatment.
- The rule rows wrap to two lines each at 390. Confirm design accepts the wrap here given card 01's rows are specced `nowrap`.
