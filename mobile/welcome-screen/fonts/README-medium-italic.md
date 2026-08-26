# Lora-MediumItalic.ttf — generated, not shipped by the foundry

`tokens/colors_and_type.css` puts the italic run inside `.su-underlined` at **weight 500**. The
design handoff shipped two italic cuts only:

| file | weight |
|---|---|
| `Lora-Italic.ttf` | 400 |
| `Lora-BoldItalic.ttf` | 700 |

No 500, and no variable master to instance one from. That left "use 400" or "wait for design" —
except the two masters turned out to interpolate cleanly.

## How it was made

Verified first: both files carry **889 glyphs**, identical names, identical contour and point
structure in identical order. When two masters are compatible like that, every weight between them
can be constructed — it is the same arithmetic a variable font performs at runtime.

`../../../audit/../scratchpad` is gone, so the procedure, for anyone repeating it:

1. Strip `GSUB` / `GPOS` / `GDEF` from copies of both masters. The bold carries an `rvrn` feature
   the regular does not, and `varLib` refuses to merge masters whose feature lists differ at all.
   `rvrn` only means anything inside a variable font, so it is noise here.
2. Build a two-master variable font on a `wght` axis from 400 to 700.
3. Instance it at **500** and drop the variation tables, leaving a static font.
4. Restore the layout tables from the **400** master, so ligatures and kerning survive.
5. Set the names and `usWeightClass` to 500.

## Evidence it is a real interpolation

The advance width of `n` lands between the two masters rather than on top of either:

| weight | advance |
|---|---|
| 400 | 615 |
| **500** | **613** |
| 700 | 610 |

A synthetic embolden would not do that — it would keep the 400 metrics and thicken the strokes.

## What it is not

It is **not** what the foundry would ship as Lora Medium Italic. A type designer may hand-correct an
intermediate master rather than take the pure interpolation. The difference at headline sizes is not
visible, but it is a difference.

## Kerning

Comes from the 400 master rather than being interpolated, because the layout tables could not be
merged. At the sizes this is used — one italic word in a 38–44pt headline — that is not detectable.

## Replace it

The moment design supplies a real `Lora-MediumItalic.ttf`, drop it in over this file. Nothing else
changes: the PostScript name (`Lora-MediumItalic`), the Android resource name
(`lora_mediumitalic`), the `Info.plist` entry and the Xcode reference all stay the same.
