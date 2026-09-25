# Tutorial flow (5 cards) — handoff

Everything needed to build the Show Up onboarding tutorial. Point Claude Code at **this folder**.

```
tutorial/
  screen-onboarding-reference.jsx   ← the design reference. Read values from here.
  tickets/01…05.md                 ← one ticket per card (scope + AC)
  spec-sheets/01…05.png            ← annotated visual spec, keyed ①–⑧
  ../components/shared.jsx         ← StepProgress, Eyebrow, NextButton, Icon, Phone
  ../tokens/colors_and_type.css    ← authoritative token values
```

## Order of authority

When two sources disagree:

1. **`screen-onboarding-reference.jsx`** — real values, wins on numbers
2. **`tickets/*.md`** — wins on behaviour, scope and copy strings
3. **`spec-sheets/*.png`** — wins on nothing; it is the human-readable summary of 1 and 2

Never measure the PNG. Every number in it is in the reference file.

## Build order — this matters

**Card 01 builds the shell. Cards 02–05 consume it.** The shell is progress bar, eyebrow pill, headline, optional body slot, art block, `flex: 1` spacer, nav row. Building 02 before 01 means building the shell twice, differently.

Ticket 05 adds two variants to the shell (terminal CTA label + gradient) — a variant on the shared nav row, not a fork.

| Card | Content | Differs from the shell how |
| --- | --- | --- |
| 01 | rule list (gap 8, no wrap) + 2 body paragraphs | reference implementation; Back hidden |
| 02 | rule list (gap 12, wraps) | no body paragraphs — body slot collapses |
| 03 | rule list (gap 12) | as 02 |
| 04 | rule list (gap 12) | as 02; shortest headline → largest spacer |
| 05 | 5 statements (14.5/1.42, gap 11) + closing paragraph | terminal CTA; `artScale: 0.62`; tallest content |

## The two rules that break everything if ignored

**1. No absolute Y positioning.** One top-anchored column, a bottom-anchored nav row, and a single `flex: 1` spacer between the art and the nav row. Every card has different content height; the spacer is what absorbs it. Hard-coded offsets look right at 390×844 and break on every other device.

**2. The illustration is the only flexible element.** On short frames the art shrinks — never the type, never the nav row's safe-area margin, and never into a scroll. Card 05 is the proof case: its text block is 269px and needs ~110px more than a 375×667 frame provides.

## Device matrix — every card, every size

375 × 667 · 390 × 844 · 430 × 932. All content visible, nothing clipped, no scroll, CTA always reachable. 375 × 667 is where cards fail; check it first, not last.

## Copy

Ticket 05 carries a **Copy — final strings** section; treat it as the source. Product terms capitalise exactly: **Show Up** · **Show-up Rate** (lowercase "up", hyphenated). Never "Show-Up Rate".
