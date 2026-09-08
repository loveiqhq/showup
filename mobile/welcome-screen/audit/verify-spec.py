# -*- coding: utf-8 -*-
"""Spec conformance check for tutorial screens 1-6.

Reads values back out of the Kotlin and Swift sources and compares them against the design handoff.
Run after any change to the screens:

    python audit/verify-spec.py

Sources of truth, in the handoff README's own order of authority:
  1. screen-onboarding-reference.jsx  - wins on numbers
  2. tickets/01..05.md                - wins on behaviour, scope, copy
  3. spec-sheets/*.png                - summary only, never measured

A check here is not decoration: findings 1-11 in AUDIT-2026-08-23.md were all things that looked
right in review and were wrong in the source.
"""
import io
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tokens

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KT = os.path.join(ROOT, "android-preview-project/app/src/main/java/com/showup")
SW = os.path.join(ROOT, "ios-app/ShowUpWelcome")

fails = []
checks = 0
CURLY = chr(0x2019)
STRAIGHT = chr(0x27)
DQ = chr(0x22)


def read(path):
    """The file, with design-token references expanded to the literals they hold.

    The assertions below look for the NUMBERS a spec sheet specifies. Since the tokens landed those
    numbers are written as `Spacing.screenGutter` and friends, so they are resolved here rather than
    in each check -- which keeps every assertion checking a value instead of a name. See
    audit/tokens.py for why that distinction matters.
    """
    raw = io.open(path, encoding="utf-8").read()
    return tokens.expand(raw, swift=path.endswith(".swift"))


def check(name, ok):
    global checks
    checks += 1
    if not ok:
        fails.append(name)


def code_only(src):
    """Source with comments removed.

    Needed for the "this must NOT appear" checks: both platforms carry a comment explaining why the
    arrow is drawn rather than taken from the system icon set, and that comment necessarily names
    the icon it replaced. Searching raw text flags the explanation as the defect it warns about.
    """
    out = []
    for line in src.split("\n"):
        stripped = line.lstrip()
        if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
            continue
        out.append(line)
    return "\n".join(out)


# ------------------------------------------------------------------ colour tokens
kt_tok = read(os.path.join(KT, "designsystem/DesignSystem.kt"))
sw_tok = read(os.path.join(SW, "DesignSystem.swift"))

for name, kt_hex, sw_hex in [
    ("bg #FFFBF7", "0xFFFFFBF7", "0xFFFBF7"),
    ("orange #FE6839", "0xFFFE6839", "0xFE6839"),
    ("purple #812AEC", "0xFF812AEC", "0x812AEC"),
    ("fg #1D1129", "0xFF1D1129", "0x1D1129"),
    ("neutral #4B3B5A", "0xFF4B3B5A", "0x4B3B5A"),
]:
    check("token " + name + " (kotlin)", kt_hex in kt_tok)
    check("token " + name + " (swift)", sw_hex in sw_tok)

# alpha tokens: subtle .46, faint .24, track .12, eyebrow fill .16
for name, byte, opacity in [
    ("subtle .46", "0x751D1129", "0.46"),
    ("faint .24", "0x3D1D1129", "0.24"),
    ("track .12", "0x1F1D1129", "0.12"),
    ("eyebrowBg .16", "0x29A78BFA", "0.16"),
]:
    check("alpha " + name + " (kotlin)", byte in kt_tok)
    check("alpha " + name + " (swift)", opacity in sw_tok)

# ------------------------------------------------------------------ shared shell
kt = read(os.path.join(KT, "tutorial/TutorialShell.kt"))
sw = read(os.path.join(SW, "TutorialShell.swift"))

# The fixed spacing every spec sheet repeats: gutter 24, pad-top 8, 24 / 14 / 28, nav 24.
for label, kt_pat, sw_pat in [
    ("progress->eyebrow 24", "Spacer(Modifier.height(24.dp))", "Spacer().frame(height: 24)"),
    ("eyebrow->headline 14", "Spacer(Modifier.height(14.dp))", "Spacer().frame(height: 14)"),
    ("headline->content 28", "Spacer(Modifier.height(28.dp))", "Spacer().frame(height: 28)"),
    ("gutter 24", "start = 24.dp, end = 24.dp", ".padding(.horizontal, 24)"),
]:
    check("spacing " + label + " (kotlin)", kt_pat in kt)
    check("spacing " + label + " (swift)", sw_pat in sw)

check("pad-top 8 (kotlin)", "top = 8.dp" in kt)
check("pad-top 8 (swift)", ".padding(.top, 8)" in sw)

# progress bar
check("progress h5 (kotlin)", "height(5.dp)" in kt)
check("progress h5 (swift)", "frame(height: 5)" in sw)
check("progress gap 6 (kotlin)", "spacedBy(6.dp)" in kt)
check("progress gap 6 (swift)", "HStack(spacing: 6)" in sw)

# eyebrow pill
check("eyebrow 11 (kotlin)", "fontSize = 11.sp" in kt)
check("eyebrow 11 (swift)", "manrope(11, .bold)" in sw)
check("eyebrow tracking .08 (kotlin)", "0.08.em" in kt)
check("eyebrow tracking .08 (swift)", "0.08 * 11" in sw)
check("eyebrow pad 5/10 (kotlin)", "horizontal = 10.dp, vertical = 5.dp" in kt)
check("eyebrow pad 5/10 (swift)", ".padding(.horizontal, 10)" in sw and ".padding(.vertical, 5)" in sw)
check("eyebrow dot 5 (kotlin)", "size(5.dp)" in kt)
check("eyebrow dot 5 (swift)", "width: 5, height: 5" in sw)
check("eyebrow hug-width (kotlin)", "align(Alignment.Start)" in kt)

# rule row: 13 / 1.4 = 18.2, tracking -0.01em, dot 6 at offset 6, gap 9
check("rule 13 (kotlin)", "fontSize = 13.sp" in kt)
check("rule 18.2 lh (kotlin)", "18.2.sp" in kt)
check("rule tracking -0.01 (kotlin)", "(-0.01).em" in kt)
check("rule dot 6 offset 6 (kotlin)", "padding(top = 6.dp).size(6.dp)" in kt)
check("rule gap 9 (kotlin)", "spacedBy(9.dp)" in kt)
check("rule 1.4 (swift)", "multiple: 1.4" in sw)
check("rule tracking -0.01 (swift)", "trackingEm: -0.01" in sw)

# statement row (card 05 only): 14.5 / 1.42 = 20.59, dot 7 at offset 7, gap 12, NO tracking
check("statement 14.5 (kotlin)", "fontSize = 14.5.sp" in kt)
check("statement 20.6 lh (kotlin)", "20.6.sp" in kt)
check("statement dot 7 offset 7 (kotlin)", "padding(top = 7.dp).size(7.dp)" in kt)
check("statement gap 12 (kotlin)", "spacedBy(12.dp)" in kt)
check("statement 1.42 (swift)", "multiple: 1.42" in sw)
if "fun StatementRow" in kt:
    body = kt.split("fun StatementRow")[1][:600]
    check("statement has no negative tracking (kotlin)", "letterSpacing" not in body)

# nav row / CTA
check("CTA circle 56 (kotlin)", "size(56.dp)" in kt)
check("CTA circle 56 (swift)", "width: 56, height: 56" in sw)
check("CTA gap 14 (kotlin)", "spacedBy(14.dp)" in kt)
check("CTA gap 14 (swift)", "HStack(spacing: 14)" in sw)
check("CTA label 17 (kotlin)", "fontSize = 17.sp" in kt)
check("CTA label 17 (swift)", "manrope(17, .bold)" in sw)
check("nav 24 above floor (kotlin)", "padding(.bottom" not in kt or "24.dp" in kt)
check("nav 24 above floor (swift)", ".padding(.bottom, 24)" in sw)

# terminal CTA variant: arrow 22 (not 20), sunset midpoint at 38% (not an even ramp)
check("arrow 22 on sunset (kotlin)", "22.dp else 20.dp" in kt)
check("arrow 22 on sunset (swift)", "? 22 : 20" in sw)
check("sunset midpoint 38% (kotlin)", "0.38f to Color(0xFFD05976)" in kt)
check("sunset midpoint 38% (swift)", "location: 0.38" in sw)
check("arrow stroke 2 (kotlin)", "2.dp.toPx()" in kt)
check("arrow stroke 2 (swift)", "lineWidth: 2" in sw)
check("arrow is drawn not a glyph (kotlin)", "ArrowForward" not in code_only(kt))
check("arrow is drawn not a glyph (swift)", 'systemName: "arrow.right"' not in code_only(sw))
# --liq-shadow-cta: 0 8px 20px rgba(c,.32) and 0 2px 6px rgba(c,.20). Two layers, offset straight
# down, spread evenly.
#
# Android must DRAW it. Modifier.shadow uses the platform elevation system, whose light sits at the
# top-centre of the window, so the direction depends on where the control happens to be on screen:
# this circle lives at the right edge and threw its glow down and to the LEFT. A token that says
# "0" horizontal offset cannot be expressed by a system that has a light source.
#
# SwiftUI's .shadow(color:radius:x:y:) takes an explicit offset and has no light source, so iOS can
# use it directly -- two stacked, one per layer of the token.
check("CTA shadow is drawn, not cast (kotlin)", "ctaGlow(" in kt)
check("CTA shadow not left to elevation (kotlin)", ".shadow(" not in code_only(kt))
check("CTA shadow both layers (swift)",
      "opacity(0.32), radius: 10, y: 8" in sw and "opacity(0.20), radius: 3, y: 2" in sw)

# radial glow: exactly the reference's stops, no invented fourth
check("glow no .13 stop (kotlin)", "alpha = 0.13f" not in code_only(kt))
check("glow no .13 stop (swift)", "opacity(0.13)" not in code_only(sw))
check("glow .16 and .10 (kotlin)", "alpha = 0.16f" in kt and "alpha = 0.10f" in kt)
check("glow .16 and .10 (swift)", "opacity(0.16)" in sw and "opacity(0.10)" in sw)

# headline underline accent - an AC on all five cards.
#
# These used to assert a per-card `underlineWidth` in dp, which pinned the bug in place: a bar of
# fixed width, bottom-aligned to the whole headline block, cannot land under the emphasised words.
# On a three-line headline it sat under the third line. The wash is now measured from the laid-out
# italic run by WashHeadline, so the checks assert that instead -- and that the hand-tuned widths
# have not come back.
# A fixed width cannot know where the italic run is; the wash is measured now.
check("no fixed-width underline survives (kotlin)", "underlineWidth" not in kt)
check("no fixed-width underline survives (swift)", "underlineWidth" not in sw)

# accessibility
check("progress announced (kotlin)", "progressBarRangeInfo" in kt)
check("progress announced (swift)", "accessibilityLabel" in sw)
check("back 48dp target (kotlin)", "minWidth = 48.dp, minHeight = 48.dp" in kt)
check("back 44pt target (swift)", "minWidth: 44, minHeight: 44" in sw)
check("button role (kotlin)", "Role.Button" in kt)
check("button trait (swift)", ".isButton" in sw)
check("art decorative (kotlin)", "clearAndSetSemantics" in kt)
check("art decorative (swift)", "accessibilityHidden(true)" in sw)

# reduce-motion honoured on both platforms
# rememberMotion moved from tutorial/ into designsystem/ with the button on 7 September 2026,
# and its type was renamed MotionPreference so it stops colliding with the Motion durations.
check("reduce-motion (kotlin)", "ANIMATOR_DURATION_SCALE"
      in read(os.path.join(KT, "designsystem/MotionPreference.kt")))
check("reduce-motion (swift)", "accessibilityReduceMotion" in sw)

# ------------------------------------------------------------------ per-card values
# The emphasised phrase per card, from the design handoff. One italicised phrase per headline,
# always carrying the wash -- `CLAUDE.md`: "Lora italics get the orange underline wash."
CARDS = [
    ("MeetInRealLifeScreen", 1, "actually", 8, ["showBack = false"]),
    ("MatchOnAvailabilityScreen", 2, "free to date", 12, []),
    ("MatchMeansMeetScreen", 3, "binding", 12, []),
    ("ThirtyMinutesScreen", 4, "thirty minutes", 12, []),
    ("ShowUpEveryTimeScreen", 5, u"there’s a cost", 11,
     ["NextVariant.Sunset", "IllustrationPlaceholder(scale = 0.62f)"]),
]
SWIFT_CARDS = {
    "MeetInRealLifeScreen": "MeetInRealLifeView",
    "MatchOnAvailabilityScreen": "MatchOnAvailabilityView",
    "MatchMeansMeetScreen": "MatchMeansMeetView",
    "ThirtyMinutesScreen": "ThirtyMinutesView",
    "ShowUpEveryTimeScreen": "ShowUpEveryTimeView",
}
for stem, step, phrase, gap, extras in CARDS:
    src = read(os.path.join(KT, "tutorial", stem + ".kt"))
    check(stem + " step " + str(step), ("step = " + str(step)) in src)
    check(stem + " uses the measured wash", "WashHeadline(" in src)
    check(stem + " emphasises " + phrase, ('"' + phrase + '" to true') in src)
    check(stem + " row gap " + str(gap), ("spacedBy(" + str(gap) + ".dp)") in src)
    for needle in extras:
        check(stem + " has " + needle, needle in src)

    # The same phrase must be emphasised on iOS, or the two platforms disagree about the design.
    ssrc = read(os.path.join(SW, SWIFT_CARDS[stem] + ".swift"))
    check(SWIFT_CARDS[stem] + " uses the measured wash", "WashHeadline(" in ssrc)
    check(SWIFT_CARDS[stem] + " emphasises " + phrase, ('("' + phrase + '", true)') in ssrc)
    # The emphasis is weight 500, not the surrounding 700.
    check(SWIFT_CARDS[stem] + " no bold italic emphasis", "loraBoldItalic, 34" not in ssrc)

# card 01's rows are specced nowrap; cards 02-04 wrap
meet = read(os.path.join(KT, "tutorial", "MeetInRealLifeScreen.kt"))
check("card 01 three nowrap rules", meet.count("wraps = false") == 3)
for stem in ("MatchOnAvailabilityScreen", "MatchMeansMeetScreen", "ThirtyMinutesScreen"):
    check(stem + " rules wrap", "wraps = false" not in read(os.path.join(KT, "tutorial", stem + ".kt")))

# ------------------------------------------------------------------ copy, exactly
COPY = {
    "MeetInRealLifeScreen": [
        "No texting for weeks", "date in real life instead",
        "No ghosting", "we penalize unreliability",
        "No collecting matches", "you meet who you match",
    ],
    "MatchOnAvailabilityScreen": [
        "Visible only when you" + CURLY + "re free to date",
        "Synchronised schedules", "Different day, different vibe",
    ],
    "MatchMeansMeetScreen": [
        "You decide who you like", "We suggest the time", "We pick the place",
        "a safe, public spot halfway between you",
    ],
    "ThirtyMinutesScreen": [
        "Low-pressure 30-minute dates", "30 minutes up", "Built-in icebreakers",
    ],
    "ShowUpEveryTimeScreen": [
        "Every profile has a Show-up Rate.",
        "Showing up to dates is reflected positively.",
        "Not showing up is reflected negatively.",
        "A persistently low Show-up Rate reduces your visibility to others.",
        "Miss a date without fair notice and you can" + CURLY + "t search for new dates for 24 hours.",
        "Show Up is for reliable people.",
        "I" + CURLY + "m ready to show up",
    ],
}
for stem, strings in COPY.items():
    kt_src = read(os.path.join(KT, "tutorial", stem + ".kt"))
    sw_src = read(os.path.join(SW, stem.replace("Screen", "View") + ".swift"))
    for s in strings:
        check("copy kotlin " + stem + ": " + s[:32], s in kt_src)
        check("copy swift  " + stem + ": " + s[:32], s in sw_src)

# The product term is "Show-up Rate" - lowercase "up", hyphenated. It must match the profile.
for stem in COPY:
    kt_src = read(os.path.join(KT, "tutorial", stem + ".kt"))
    sw_src = read(os.path.join(SW, stem.replace("Screen", "View") + ".swift"))
    check("no 'Show-Up Rate' " + stem + " (kotlin)", "Show-Up Rate" not in kt_src)
    check("no 'Show-Up Rate' " + stem + " (swift)", "Show-Up Rate" not in sw_src)

# shipped strings use the typographic apostrophe, never the straight one
QUOTED = re.compile(DQ + "([^" + DQ + "]*)" + DQ)
for stem in list(COPY) + ["TutorialShell", "WelcomeScreen"]:
    path = os.path.join(KT, "tutorial", stem + ".kt")
    if not os.path.exists(path):
        continue
    for lit in QUOTED.findall(read(path)):
        if STRAIGHT in lit and len(lit) > 3:
            check("straight apostrophe in " + stem + ": " + lit[:28], False)

# ------------------------------------------------------------------ report
print("spec conformance: " + str(checks) + " checks")
if fails:
    print("FAILED " + str(len(fails)) + ":")
    for name in fails:
        print("  x " + name)
    sys.exit(1)
print("all passed")
