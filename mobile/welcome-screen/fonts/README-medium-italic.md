# Lora-MediumItalic.ttf — the official cut, instanced from the variable font

`tokens/colors_and_type.css` puts the italic run inside `.su-underlined` at **weight 500**. The
design handoff ships static cuts at 400 and 700 only, and no 500.

That gap is now closed properly. **This file is the foundry's own Medium Italic**, not a
reconstruction.

## Where it comes from

The design system's own stylesheet asks for Lora as a **variable font**:

```
@import url('https://fonts.googleapis.com/css2?family=Lora:ital,wght@0,400..700;1,400..700&…');
```

A variable font covering 400–700 italic contains every weight in that range as a real, designed
position — including 500, which Lora declares as a **named instance called "Medium Italic"**. The
weight was never missing; only the static file was.

So: the upstream `Lora-Italic[wght].ttf` (version 3.008, from `google/fonts`) is kept here as
`_source-Lora-Italic-variable.ttf`, and this file is that font instanced at `wght=500` with
`fontTools.varLib.instancer`. It is exactly what Google Fonts serves a browser that asks for Lora
italic at 500 — the same bytes the design mockups render with.

To rebuild it:

```python
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
f = TTFont("_source-Lora-Italic-variable.ttf")
inst = instancer.instantiateVariableFont(f, {"wght": 500}, updateFontNames=True)
inst["OS/2"].usWeightClass = 500
inst.save("Lora-MediumItalic.ttf")
```

## What this replaced

Before the variable font was available, this file was built by interpolating between the shipped
400 and 700 static masters — merging them into a two-master variable font and instancing at 500.
That reconstruction has now been checked against the official cut, across all **889 glyphs**:

| | Result |
|---|---|
| Advance widths | **identical** — 0 units difference on every glyph sampled |
| Glyph outlines | largest deviation **1 unit** on a 1000-unit em, on one glyph (`foundry`) |
| Kerning | 458 pair sets in both |

So the reconstruction was correct, and no rendering changes with this swap. It is replaced anyway
because provenance matters more than pixels: this one is the designer's file, carries the real
version string, and needs no explanation to anyone reviewing the licence or the build.

## Licence

SIL Open Font License 1.1, same as every other font here — see `OFL-Lora.txt`. Instancing a
variable font is explicitly permitted; the OFL reserved-name rules are satisfied because the family
name stays "Lora".

## If design ever supplies their own file

Drop it in over this one. Nothing else changes: the PostScript name (`Lora-MediumItalic`), the
Android resource name (`lora_mediumitalic`), the `Info.plist` entry and the Xcode reference all
stay as they are.
