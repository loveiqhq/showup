# -*- coding: utf-8 -*-
"""Write Android's five Manrope files: the originals plus one lookup that curls the apostrophe at a word end.

Why: Manrope's `calt` feature draws the straight apostrophe (U+0027, `quotesingle`) as the curly one
(`quoteright`, the U+2019 glyph) in contexts. Two of those contexts need to see a SPACE after them:
"'s " and "em' ". Chrome and Core Text shape a whole run, see the space, and curl "It's now closed.";
Android's Minikin shapes word by word -- it cuts the text before and after every space -- so HarfBuzz
never has the space in its buffer, and those two rules never match on Android. The spec sheet and iOS
draw "It’s", Android drew "It's" (Date 08, SHOWUP-199, 27 Nov 2026). See README-android-apostrophe.md.

What it does: for every `calt` rule whose context ends in a space (and has no other space in it), it
appends ONE new chained-context lookup to `calt` holding two subtables per rule, in this order:

  1. ignore: the same context with the space replaced by "any glyph", and no substitution. Inside a
     Minikin word a following glyph is never a space, so "it's," or "it's." stays straight -- exactly
     as Chrome draws it, where no space follows either.
  2. substitute: the same context with the space dropped, through the font's own nested lookup. This
     only matches when nothing follows: the end of the Minikin word, which is where the space was.

Everything else in the font is kept: the GSUB table is edited in place (never rebuilt from a feature
file, which would drop liga, locl and the numeral features), and every table but GSUB, `name` (the
version string says it is patched) and `head` (its checksum) is written back byte for byte. The script
proves that on every run, against the original, before it writes anything.

The originals are iOS's copies (ios-app/ShowUpWelcome/Fonts/), which keep the upstream files: Core
Text already applies the space rules. Reading from there makes a re-run idempotent.

Run with fontTools in a throwaway virtualenv, from anywhere:

    python -m venv .venv && .venv/Scripts/pip install fonttools      (bin/pip on macOS / Linux)
    .venv/Scripts/python mobile/welcome-screen/fonts/_patch_manrope_android_apostrophe.py
"""
import argparse
import copy
import io
import os
import sys

from fontTools.ttLib import TTFont
from fontTools.ttLib.tables import otTables as ot

HERE = os.path.dirname(os.path.abspath(__file__))
WELCOME = os.path.dirname(HERE)
SOURCE = os.path.join(WELCOME, "ios-app", "ShowUpWelcome", "Fonts")
TARGET = os.path.join(WELCOME, "android-preview-project", "app", "src", "main", "res", "font")

FONTS = [
    ("Manrope-Regular.ttf", "manrope_regular.ttf"),
    ("Manrope-Medium.ttf", "manrope_medium.ttf"),
    ("Manrope-SemiBold.ttf", "manrope_semibold.ttf"),
    ("Manrope-Bold.ttf", "manrope_bold.ttf"),
    ("Manrope-ExtraBold.ttf", "manrope_extrabold.ttf"),
]

# The space rules Manrope 4.504 / 4.505 has, in reading order with the space left off. Anything else
# means upstream changed its rules: stop and have a person read the new ones before Android ships them.
EXPECTED = {
    ((), ("quotesingle",), ("s",)),          # "it's " -> it’s
    (("e", "m"), ("quotesingle",), ()),      # "...em' " -> em’
}

VERSION_NOTE = "; ShowUp Android: calt apostrophe at a word end"


def glyph_names(coverages):
    return tuple(tuple(c.glyphs) for c in coverages)


def reading_order(rule):
    """(backtrack, input, lookahead) as a person reads them -- the font stores backtrack nearest-first."""
    return (tuple(g for c in reversed(rule.BacktrackCoverage) for g in c.glyphs),
            tuple(g for c in rule.InputCoverage for g in c.glyphs),
            tuple(g for c in rule.LookAheadCoverage[:-1] for g in c.glyphs))


def calt_records(gsub):
    return [r for r in gsub.FeatureList.FeatureRecord if r.FeatureTag == "calt"]


def subtables(lookup):
    for st in lookup.SubTable:
        yield st.ExtSubTable if lookup.LookupType == 7 else st


def space_rules(gsub, space):
    """Every calt chain rule whose only space is the last glyph it looks ahead to."""
    found = []
    for li in sorted({i for r in calt_records(gsub) for i in r.Feature.LookupListIndex}):
        lookup = gsub.LookupList.Lookup[li]
        for st in subtables(lookup):
            if type(st).__name__ != "ChainContextSubst" or st.Format != 3:
                continue
            ahead = st.LookAheadCoverage
            if not ahead or ahead[-1].glyphs != [space]:
                continue
            others = st.BacktrackCoverage + st.InputCoverage + ahead[:-1]
            if any(space in c.glyphs for c in others):
                # A space before the input too ("A -> B" arrows): Minikin isolates that glyph
                # entirely, and no word-end trick can see both sides.
                continue
            found.append((lookup, st))
    return found


def already_patched(gsub, glyph_count):
    for r in calt_records(gsub):
        for li in r.Feature.LookupListIndex:
            for st in subtables(gsub.LookupList.Lookup[li]):
                if (type(st).__name__ == "ChainContextSubst" and st.Format == 3 and st.LookAheadCoverage
                        and len(st.LookAheadCoverage[-1].glyphs) == glyph_count and not st.SubstLookupRecord):
                    return True
    return False


def chain(back, inp, ahead, records):
    st = ot.ChainContextSubst()
    st.Format = 3
    st.BacktrackGlyphCount, st.BacktrackCoverage = len(back), back
    st.InputGlyphCount, st.InputCoverage = len(inp), inp
    st.LookAheadGlyphCount, st.LookAheadCoverage = len(ahead), ahead
    st.SubstCount, st.SubstLookupRecord = len(records), records
    return st


def patch(font):
    """Append the word-end lookup to calt. Returns (new lookup index, rules in reading order)."""
    gsub = font["GSUB"].table
    order = font.getGlyphOrder()
    cmap = font.getBestCmap()
    space = cmap[0x20]
    if getattr(gsub, "FeatureVariations", None) is not None:
        sys.exit("GSUB has FeatureVariations; calt may be swapped per instance. Patch by hand.")
    if already_patched(gsub, len(order)):
        sys.exit("This font already carries the word-end lookup. Point the script at iOS's originals.")

    rules = space_rules(gsub, space)
    found = {reading_order(st) for _, st in rules}
    if found != EXPECTED:
        sys.exit(f"Manrope's space rules changed: expected {sorted(EXPECTED)}, found {sorted(found)}.")
    flags = {(lk.LookupFlag, getattr(lk, "MarkFilteringSet", None)) for lk, _ in rules}
    if len(flags) != 1:
        sys.exit(f"The space rules sit in lookups with different flags {flags}; patch by hand.")
    flag, mark_set = flags.pop()

    any_glyph = ot.Coverage()
    any_glyph.glyphs = list(order)  # glyph order is glyph-ID order, as a coverage must be

    new_subtables = []
    for _, st in rules:
        ahead = copy.deepcopy(st.LookAheadCoverage[:-1])
        new_subtables.append(chain(copy.deepcopy(st.BacktrackCoverage), copy.deepcopy(st.InputCoverage),
                                   ahead + [any_glyph], []))
        new_subtables.append(chain(copy.deepcopy(st.BacktrackCoverage), copy.deepcopy(st.InputCoverage),
                                   copy.deepcopy(ahead), copy.deepcopy(st.SubstLookupRecord)))

    lookup = ot.Lookup()
    lookup.LookupType = 6
    lookup.LookupFlag = flag
    if mark_set is not None:
        lookup.MarkFilteringSet = mark_set
    lookup.SubTable = new_subtables
    lookup.SubTableCount = len(new_subtables)
    gsub.LookupList.Lookup.append(lookup)
    gsub.LookupList.LookupCount = len(gsub.LookupList.Lookup)
    index = gsub.LookupList.LookupCount - 1

    # Every language system lists calt by feature index, so editing the feature record reaches all
    # of them. The new index is the highest, so the list stays ascending as the spec requires, and
    # HarfBuzz applies it straight after calt's own lookup: every lookup between them is nested.
    for r in calt_records(gsub):
        r.Feature.LookupListIndex.append(index)
        r.Feature.LookupCount = len(r.Feature.LookupListIndex)

    for rec in font["name"].names:
        if rec.nameID == 5:
            rec.string = rec.toUnicode() + VERSION_NOTE

    # Reading GSUB decompiled post and cmap for the glyph names; fontTools recompiles every table it
    # has decompiled, which may reorder bytes it was never asked to change. Unloading them makes save
    # copy the original bytes, which prove_only_the_lookup_changed then checks.
    for tag in list(font.tables):
        if tag not in ("GSUB", "name"):
            del font.tables[tag]
    return index, sorted(found)


def gsub_xml(font):
    out = io.StringIO()
    from fontTools.misc.xmlWriter import XMLWriter
    writer = XMLWriter(out, newlinestr="\n")
    font["GSUB"].toXML(writer, font)
    return out.getvalue()


def prove_only_the_lookup_changed(original_path, patched_bytes, index):
    """Re-read both files and compare: the patch minus its lookup must decompile to the original GSUB."""
    before = TTFont(original_path)
    after = TTFont(io.BytesIO(patched_bytes))
    if sorted(before.keys()) != sorted(after.keys()):
        sys.exit("The patched font has a different set of tables.")
    for tag in before.keys():
        if tag in ("GlyphOrder", "GSUB", "name", "head"):
            continue
        if before.reader[tag] != after.reader[tag]:
            sys.exit(f"Table {tag} changed; only GSUB, name and head may.")
    head_b, head_a = before["head"].__dict__.copy(), after["head"].__dict__.copy()
    for h in (head_b, head_a):
        h.pop("checkSumAdjustment", None)
    if head_b != head_a:
        sys.exit("head changed beyond its checksum.")
    names_b = {(r.platformID, r.platEncID, r.langID, r.nameID): r.toUnicode() for r in before["name"].names}
    names_a = {(r.platformID, r.platEncID, r.langID, r.nameID): r.toUnicode() for r in after["name"].names}
    for key in names_b:
        want = names_b[key] + (VERSION_NOTE if key[3] == 5 else "")
        if names_a.get(key) != want:
            sys.exit(f"name record {key} is {names_a.get(key)!r}, expected {want!r}.")
    if set(names_a) != set(names_b):
        sys.exit("The name table gained or lost records.")

    gsub = after["GSUB"].table
    if len(gsub.LookupList.Lookup) != index + 1:
        sys.exit("The new lookup is not the last one.")
    for r in calt_records(gsub):
        if r.Feature.LookupListIndex[-1] != index:
            sys.exit("calt does not end with the new lookup.")
        r.Feature.LookupListIndex.pop()
        r.Feature.LookupCount -= 1
    del gsub.LookupList.Lookup[index]
    gsub.LookupList.LookupCount -= 1
    if gsub_xml(after) != gsub_xml(before):
        sys.exit("GSUB changed beyond the appended lookup.")


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--out", default=TARGET, help="where to write (default: Android's res/font)")
    args = parser.parse_args()
    for source_name, target_name in FONTS:
        source = os.path.join(SOURCE, source_name)
        # The same input must give the same bytes, or every re-run is a diff: recalcTimestamp off keeps
        # head's date, and the repacker switch keeps GSUB's layout from depending on whether uharfbuzz
        # happens to be installed -- fontTools packs with HarfBuzz's repacker when it can import it.
        font = TTFont(source, recalcTimestamp=False)
        font.cfg["fontTools.ttLib.tables.otBase:USE_HARFBUZZ_REPACKER"] = False
        index, rules = patch(font)
        buf = io.BytesIO()
        font.save(buf)
        prove_only_the_lookup_changed(source, buf.getvalue(), index)
        target = os.path.join(args.out, target_name)
        with open(target, "wb") as f:
            f.write(buf.getvalue())
        print(f"{target_name}: calt += lookup {index}, {len(rules)} word-end rules {rules}; "
              f"nothing else in GSUB changed")


if __name__ == "__main__":
    main()
