# Manrope-Regular.ttf — the official 400, instanced from the variable font

The specs set body copy in Manrope at **weight 400** — Check-In 02's subline, Check-In 03's
sublines, Check-In 06's field, counter and inspirations, the legal lines on Startup, Welcome back and
Connect, Location's benefit sublines. The handoff shipped static Manrope at 500, 600, 700 and 800
only, so until 14 October 2026 every one of those runs rendered in **Medium** on both platforms:
Android resolved `FontWeight.Normal` to the nearest weight in the family, and iOS's `F.manrope`
sent `.regular` to `Manrope-Medium` by its `default` case.

That is a step heavier than drawn and about 3% wider. Measured against headless Chrome rendering
the reference CSS, Check-In 02's subline came out 9.7pt wider in Compose than in the browser, which
is the difference between the spec's line breaks and different ones.

## Where it comes from

Exactly as `Lora-MediumItalic.ttf` (see `README-medium-italic.md`): the design system's stylesheet
asks Google Fonts for `Manrope:wght@300..800`, a variable font in which 400 is a named instance,
"Regular". `_source-Manrope-variable.ttf` is that file — `ofl/manrope/Manrope[wght].ttf` from
google/fonts, version 4.505 — and `Manrope-Regular.ttf` is it instanced at `wght=400` by
`_make_manrope_regular.py`. Same bytes the mockups render with.

## Checked

| | Result |
|---|---|
| The bundled 500 / 600 / 700 / 800 against the variable font at the same weights | every ASCII advance identical |
| Vertical metrics of the new cut | hhea 2132 / -600 / 0, typo the same, `USE_TYPO_METRICS` set — identical to the other cuts, so no line moves |
| PostScript name | `Manrope-Regular` |

## Licence

SIL Open Font License 1.1, same as the other Manrope files — see `OFL-Manrope.txt`. Instancing a
variable font is permitted; the family name stays "Manrope".
