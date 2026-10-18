# -*- coding: utf-8 -*-
"""Rebuild Manrope-Regular.ttf (weight 400) from the upstream variable font.

The design system asks for Manrope as a variable font (`wght@300..800` from Google Fonts), and the
specs set body copy at weight 400 throughout. The handoff shipped static cuts at 500, 600, 700 and
800 only, so every weight-400 run in both apps fell back to Medium: a step heavier than drawn, and
about 3% wider -- enough to move a line break (`audit` note, 14 Oct 2026, Check-In 02's subline).

`_source-Manrope-variable.ttf` is `ofl/manrope/Manrope[wght].ttf` from google/fonts, version 4.505,
the file Google Fonts serves a browser. Its named instance "Regular" is wght=400. The static cuts
already bundled were checked against it at 500, 600, 700 and 800: every ASCII advance is identical.

Run from this folder, then copy the output to android-preview-project/app/src/main/res/font/
manrope_regular.ttf and ios-app/ShowUpWelcome/Fonts/Manrope-Regular.ttf.
"""
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

font = TTFont("_source-Manrope-variable.ttf")
regular = instancer.instantiateVariableFont(font, {"wght": 400}, updateFontNames=True)
regular["OS/2"].usWeightClass = 400
regular.save("Manrope-Regular.ttf")
print(regular["name"].getDebugName(6), regular["name"].getDebugName(5))
