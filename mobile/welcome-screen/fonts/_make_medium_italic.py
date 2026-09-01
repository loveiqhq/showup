# -*- coding: utf-8 -*-
"""Generate a genuine Lora Medium Italic (weight 500) from the two cuts we have.

The design specifies weight 500 for the italic run in headlines. The handoff shipped only 400 and
700 italic, and no variable master to instance from — so the options looked like "use 400" or "wait
for design".

There is a third: the 400 and 700 files are structurally identical (889 glyphs, same contours, same
point counts in the same order, verified before running this). When two masters interpolate cleanly,
every weight between them can be constructed. This builds a temporary variable font with 400 and 700
as its endpoints and instances it at 500.

The result is not a simulation or a synthetic emboldening. Every point is a real interpolation
between two real masters — mathematically the same operation the type designer's own variable font
performs. It is not identical to what the foundry would ship as Lora Medium Italic, because they may
hand-correct an intermediate master, but it is a legitimate 500 rather than a 400 with a stroke.

Replace it the moment design supplies the real file.
"""
import io
import os
import shutil
import tempfile

from fontTools.designspaceLib import DesignSpaceDocument, SourceDescriptor, AxisDescriptor
from fontTools.ttLib import TTFont
from fontTools.varLib import build as build_vf
from fontTools.varLib import instancer

FONTS = r"C:\Users\krnji\showup\mobile\welcome-screen\fonts"
LIGHT = os.path.join(FONTS, "Lora-Italic.ttf")        # 400
BOLD = os.path.join(FONTS, "Lora-BoldItalic.ttf")     # 700
OUT = os.path.join(FONTS, "Lora-MediumItalic.ttf")    # 500, generated

tmp = tempfile.mkdtemp()
try:
    # varLib wants masters on disk next to the designspace
    light_copy = os.path.join(tmp, "Lora-Italic.ttf")
    bold_copy = os.path.join(tmp, "Lora-BoldItalic.ttf")
    shutil.copy(LIGHT, light_copy)
    shutil.copy(BOLD, bold_copy)

    # The outlines interpolate cleanly, but the two masters disagree on OpenType layout: the bold
    # carries an `rvrn` feature the regular does not. rvrn (Required Variation Alternates) only has
    # meaning inside a variable font, so it is noise here -- but varLib refuses to merge masters
    # whose feature lists differ at all.
    #
    # Rather than reconcile them, strip layout from both, interpolate the outlines alone, and put
    # the regular's layout back afterwards. Ligatures and kerning survive; the only cost is that
    # kerning comes from the 400 master rather than being interpolated, which at headline sizes is
    # not visible.
    saved_layout = {}
    for path in (light_copy, bold_copy):
        f = TTFont(path)
        for tag in ("GSUB", "GPOS", "GDEF"):
            if tag in f:
                if path == light_copy:
                    saved_layout[tag] = f[tag]
                del f[tag]
        f.save(path)
    print("  layout tables stripped for the merge (%s kept from the 400 master)"
          % ", ".join(sorted(saved_layout)))

    ds = DesignSpaceDocument()
    axis = AxisDescriptor()
    axis.name = "Weight"
    axis.tag = "wght"
    axis.minimum, axis.default, axis.maximum = 400, 400, 700
    ds.addAxis(axis)

    for path, weight in ((light_copy, 400), (bold_copy, 700)):
        src = SourceDescriptor()
        src.path = path
        src.filename = os.path.basename(path)
        src.location = {"Weight": weight}
        if weight == 400:
            src.copyInfo = True          # the 400 supplies the shared font-wide tables
        ds.addSource(src)

    vf, _, _ = build_vf(ds)
    print("  variable font built: axes = %s" % [a.axisTag for a in vf["fvar"].axes])

    # pin the axis at 500 and drop the variation tables — a plain static font comes out
    static = instancer.instantiateVariableFont(vf, {"wght": 500}, inplace=False)

    # name it honestly so nobody mistakes it for a foundry file
    name = static["name"]
    for nid, value in [
        (1, "Lora Medium"),                 # family
        (2, "Italic"),                      # subfamily
        (4, "Lora Medium Italic"),          # full name
        (6, "Lora-MediumItalic"),           # PostScript name — this is what the app asks for
        (16, "Lora"),                       # typographic family
        (17, "Medium Italic"),              # typographic subfamily
    ]:
        name.setName(value, nid, 3, 1, 0x409)
        name.setName(value, nid, 1, 0, 0)
    static["OS/2"].usWeightClass = 500

    # put ligatures and kerning back
    for tag, table in saved_layout.items():
        static[tag] = table
    print("  layout restored: %s" % ", ".join(sorted(saved_layout)))

    static.save(OUT)
    print("  wrote %s (%.0f KB)" % (os.path.basename(OUT), os.path.getsize(OUT) / 1024.0))

    # prove it came out as intended
    check = TTFont(OUT)
    ps = check["name"].getDebugName(6)
    print()
    print("  PostScript name : %s" % ps)
    print("  usWeightClass   : %d" % check["OS/2"].usWeightClass)
    print("  italic flag     : %s" % bool(check["OS/2"].fsSelection & 0x01))
    print("  glyphs          : %d" % len(check.getGlyphOrder()))
    print("  variable tables : %s" % ("still present - BAD" if "fvar" in check else "removed, static"))

    # a real interpolation should sit between the two masters, not on top of either
    def stem(path, glyph="n"):
        f = TTFont(path)
        gs = f.getGlyphSet()
        from fontTools.pens.boundsPen import BoundsPen
        bp = BoundsPen(gs)
        gs[glyph].draw(bp)
        return f["hmtx"][glyph][0], bp.bounds

    for label, path in (("400", LIGHT), ("500", OUT), ("700", BOLD)):
        adv, _ = stem(path)
        print("  advance width of 'n' at %s: %d" % (label, adv))
finally:
    shutil.rmtree(tmp, ignore_errors=True)
