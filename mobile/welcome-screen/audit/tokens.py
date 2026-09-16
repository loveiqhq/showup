# -*- coding: utf-8 -*-
"""Resolve design-token references back to the literal values they hold.

WHY THIS EXISTS

The conformance verifiers assert the NUMBERS a spec sheet specifies -- "gutter 24", "button gap 8",
"login hit area 44" -- by looking for those literals in the source. That is the right thing for
them to assert: the sheet says 24, and 24 is what has to render.

When the design tokens landed on 7 September 2026, those literals became token references.
`padding(start = 24.dp)` became `padding(start = Spacing.screenGutter)`. Three verifiers failed
immediately, and they were not wrong to: the text they were looking for was gone.

The wrong fix would have been to rewrite the assertions to look for `Spacing.screenGutter`, because
then they would assert a NAME and stop checking the value -- a token could be redefined to 32 and
every "gutter 24" check would still pass. That is the failure mode the architecture spike calls
"checks that pin the mistake".

So this expands references back to literals before matching. The verifiers keep asserting numbers,
the screens keep using tokens, and the mapping is read from the token FILES rather than restated
here, so it cannot drift from them.
"""
import io
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(HERE)

KT_TOKENS = os.path.join(
    MOBILE, "android-preview-project", "app", "src", "main", "java", "com", "showup", "designsystem")
SW_TOKENS = os.path.join(MOBILE, "ios-app", "ShowUpWelcome")

_FILES = ("Spacing", "Radius", "ComponentSizes", "IconSizes", "Motion")


def _kotlin_map():
    """{'Spacing.md': '8.dp', 'Motion.SCREEN': '320', ...} from the Kotlin token files."""
    out = {}
    for stem in _FILES:
        path = os.path.join(KT_TOKENS, stem + ".kt")
        if not os.path.exists(path):
            continue
        text = io.open(path, encoding="utf-8").read()
        # val name = 8.dp        -> "8.dp"
        for name, value in re.findall(r"^\s*val (\w+)\s*=\s*([\d.]+)\.dp", text, re.M):
            out["%s.%s" % (stem, name)] = value + ".dp"
        # const val NAME = 320   -> "320"
        for name, value in re.findall(r"^\s*const val (\w+)\s*=\s*(\d+)", text, re.M):
            out["%s.%s" % (stem, name)] = value
    return out


def _swift_map():
    """{'Spacing.md': '8', 'Motion.screen': '0.32', ...} from the Swift token files."""
    out = {}
    for stem in _FILES:
        path = os.path.join(SW_TOKENS, stem + ".swift")
        if not os.path.exists(path):
            continue
        text = io.open(path, encoding="utf-8").read()
        for name, value in re.findall(
                r"^\s*static let (\w+)\s*:\s*(?:CGFloat|TimeInterval)\s*=\s*([\d.]+)", text, re.M):
            out["%s.%s" % (stem, name)] = value
    return out


_KT = None
_SW = None


def expand(text, swift=False):
    """Return `text` with every design-token reference replaced by its literal value.

    Longest names first, so `Spacing.screenGutter` is not partially matched by a shorter key.
    """
    global _KT, _SW
    if swift:
        if _SW is None:
            _SW = _swift_map()
        table = _SW
    else:
        if _KT is None:
            _KT = _kotlin_map()
        table = _KT

    for ref in sorted(table, key=len, reverse=True):
        text = text.replace(ref, table[ref])
    return text


def table(swift=False):
    """The mapping itself, for a checker that wants to report on it."""
    return dict(_swift_map() if swift else _kotlin_map())
