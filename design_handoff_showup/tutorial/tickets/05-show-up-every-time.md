# [Onboarding 05] Show up, every time — tutorial card 05 (final)

**Parent:** App Onboarding / Tutorial flow (SHOWUP-118) · **Type:** Story · **Priority:** Medium
**Attachments:** `05-show-up-every-time-spec-sheet.png`

## Description

The last card of the 5-card tutorial, reached from card 04 (30 minutes) via **Next**. Cards 01–04 sell the product; this card states how reliability is measured and enforced — every profile carries a Show-up Rate, showing up raises it, no-shows lower it, a persistently low rate reduces visibility, and missing a date without fair notice blocks new searches for 24 hours. It closes by framing the rule as fairness rather than punishment. Tapping the CTA exits the tutorial and is the moment the user accepts these terms, so the card must be complete and legible before anyone can pass it.

**This card consumes the card shell built in card 01**, with three documented variations:

1. **Terminal CTA.** The label becomes **"I'm ready to show up"** (not "Next") and the circle becomes the sunset gradient (`--su-grad-sunset`) with `--liq-shadow-violet`, replacing the flat orange + `--liq-shadow-cta` used on cards 01–04. The shell needs a CTA variant, not a fork of the nav row.
2. **Statement list, not the rule–consequence list.** Five plain statements in a single colour at 14.5 / 1.42, gap 11, 7px dot at offset 7 with a 12px gap to text — deliberately *not* the two-tone `rule — consequence` pattern of cards 02–04. This card states policy rather than selling a benefit, so the violet consequence half is absent by design. Do not "fix" it to match the other cards.
3. **A closing paragraph** below the list — the only body paragraph in cards 02–05. Manrope 500 · 14.5 / 1.5 · `--liq-neutral-200`, gap above 5, with the lead clause "Show Up is for reliable people." in 700 `--liq-fg`.

## Height budget — read this before building

This card's text block is **269px tall**, by far the tallest of the five.

- At **390 × 844** the layout closes with **67px of spacer left** — it fits.
- At **375 × 667 it does not fit**: the column needs roughly **110px more** than the frame gives.

The illustration is therefore **the only flexible element**. It is already drawn at **scale 0.62** (a 143px block instead of the 230px used on cards 01–04) and must shrink further toward zero on short frames, before anything else moves. Never shrink the type, never let the nav row leave the safe area, never introduce scroll. The text block and the nav row keep their sizes on every device.

📎 All specs are on the attached **05-show-up-every-time-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–⑧ to the screen. Note the design system has colour and type tokens only — there is no spacing scale, so the raw px values are intentional, not an oversight.

⚠ **Layout is flex, not absolute.** Progress → eyebrow → headline → statement list → illustration is one top-anchored column; the nav row is bottom-anchored; a single `flex: 1` spacer between the illustration and the nav row absorbs every height difference. Do not hard-code Y positions. Anchor the progress bar to `env(safe-area-inset-top) + 8`, not to the mock's 54px status bar.

## Copy — final strings

Eyebrow: `Show up, every time`
Headline: `If you don't show up, there's a cost.` — "there's a cost" italic

1. `Every profile has a Show-up Rate.`
2. `Showing up to dates is reflected positively.`
3. `Not showing up is reflected negatively.`
4. `A persistently low Show-up Rate reduces your visibility to others.`
5. `Miss a date without fair notice and you can't search for new dates for 24 hours.`

Closing: `**Show Up is for reliable people.** Life happens. Stay fair and show respect for each other, and your Show-up Rate will reflect it.`

CTA: `I'm ready to show up`

Rows 4 and 5 wrap to two lines at 390. The product term is **Show-up Rate** (lowercase "up", hyphenated) everywhere — it must match the profile.

## Acceptance criteria

- [ ] Built on the shared card shell from card 01 — the CTA variant is added to the shell, not forked
- [ ] Step progress shows all 5 segments filled — no empty track remains
- [ ] CTA reads "I'm ready to show up" with the sunset-gradient circle and `--liq-shadow-violet`
- [ ] CTA exits the tutorial into the app; Back is active and returns to card 04; no skip affordance
- [ ] Five statements render as single-colour rows at 14.5 / 1.42, gap 11, 7px dot / offset 7 / gap 12 — not the two-tone pattern of cards 02–04
- [ ] Closing paragraph renders below the list with the lead clause bolded
- [ ] Copy matches the strings above exactly, including "Show-up Rate" casing
- [ ] Headline renders "there's a cost" in Lora italic with the underline accent, 2 lines at 390
- [ ] Show-up Rate ring matches the spec: r82, stroke 9, 92% sweep from 12 o'clock, green `#00C46A → #0A9E5A`, gradient heart inside, paper disc + green check seal at (100,92) — drawn at 0.62 of the size used on cards 01–04
- [ ] The ring green matches the Show-up Rate green used on the profile — same token, not a one-off hex
- [ ] Illustration renders without visible banding on device (radial glow + blur)
- [ ] **375 × 667: all five statements and the closing paragraph are fully visible, the CTA is fully visible and tappable, and nothing scrolls** — the illustration shrinks or disappears to make room
- [ ] 390 × 844 and 430 × 932: nothing overlaps or clips; the illustration stays centred in its block

## Tracking

Event names come from `design_handoff_showup/tracking/events.json`. Use them exactly — do not invent names, and do not restate a payload here that the registry already defines.

⚠ **This screen is card 6 in the data, not card 05.** The Welcome screen (SHOWUP-117) is card 1, so every shipped card number is one higher than its Jira card number — decided 7 Sep 2026, mapping in `tracking/enums.json` §14. A funnel filtered on the Jira number returns the wrong screen.

| What | Event | Payload |
|---|---|---|
| Screenview | `tutorial_card_viewed` | `card: 6`, `card_name: "show_up_every_time"` |
| Final CTA | `tutorial_completed` | `card: 6`, `card_name`, `duration_s` — whole-tour duration, **missing from the current build** |
| Back | `tutorial_back_tapped` | `card: 6`, `card_name` |

`tutorial_completed` replaces `tutorial_cta_tapped` on this card, so a funnel across `tutorial_cta_tapped` sees five cards, not six. That is correct, not a bug.

**Open — Legal.** This card is effectively a terms acceptance (see Open questions). The taxonomy defines `manifesto_accepted { dwell_total_s }` for exactly that and it is not implemented. Legal to confirm whether the acceptance needs its own record; if yes, it is a separate ticket.


## Out of scope

The Show-up Rate feature itself (calculation, profile display, the 24-hour search block, penalties), the post-tutorial destination screen, analytics event implementation, swipe-between-cards gesture, and the spacing-scale refactor.

## Dependencies

- Card shell from card 01 (blocking) + a terminal CTA variant on the shared nav row
- Show-up Rate ring illustration export from design (200 × 200 svg)
- The Show-up Rate green as a shared token, so this card and the profile cannot drift
- Confirmed post-tutorial destination for the CTA
- Lora + Manrope in the app font set

## Open (not blocking)

- **Eyebrow width.** Same as cards 01–04: reference render stretches the eyebrow to full content width; the design-system component is a hug-width pill. Fix belongs in the shell (`align-self: flex-start`).
- **This card is effectively a terms acceptance.** The 24-hour search block and the visibility penalty are stated in onboarding and enforced later. Legal/Trust & Safety to confirm the wording, and whether tapping the CTA needs to be recorded as consent rather than just a tutorial-completion event.
- **"Without fair notice" is undefined.** Statement 5 imposes a penalty on a threshold the user can't see. Either define it here (how much notice counts as fair) or link to the cancellation policy — as written, the rule is unfalsifiable from the user's side.
- **92% ring.** The sweep is decorative but reads as a real number. Confirm design intends a generic healthy rate rather than the user's own (the user has no rate yet at this point in the flow).
- **The card is at its content ceiling.** Any further copy addition breaks 844 as well as 667. If more needs saying, it needs a sixth card, not a longer fifth.
