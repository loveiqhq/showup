# -*- coding: utf-8 -*-
"""Check that no SwiftUI API newer than the deployment target is used ungated.

The third real Xcode error was `scrollBounceBehavior(.basedOnSize)`: introduced in iOS 16.4, used
in a project targeting iOS 16.0. It is invisible from Windows — the code is perfectly valid Swift,
it simply requires a newer OS than the project claims to support.

This is a **curated list, not a database.** It knows only about the APIs this codebase actually
uses. Adding a new modifier means adding a row here if it has a minimum version above the target.
That is a real limitation and the reason the list is short and commented rather than pretending to
be exhaustive.

    python audit/check-ios-availability.py
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "ios-app", "ShowUpWelcome")
PBX = os.path.join(ROOT, "ios-app", "ShowUpWelcome.xcodeproj", "project.pbxproj")

# API -> (major, minor) it was introduced in. Only APIs used in this codebase.
MINIMUM = {
    "scrollBounceBehavior":        (16, 4),   # the one that broke the build
    "contentMargins":              (17, 0),
    "scrollTargetBehavior":        (17, 0),
    "scrollPosition":              (17, 0),
    "Locale.current.region":       (16, 0),
    "symbolEffect":                (17, 0),
    "containerRelativeFrame":      (17, 0),
    "scrollIndicatorsFlash":       (17, 0),
    "defaultScrollAnchor":         (17, 0),
    "ContentUnavailableView":      (17, 0),
    "TextRenderer":                (18, 0),
    "onScrollGeometryChange":      (18, 0),
    "scrollBounceBehavior(_:axes:)": (16, 4),
    "presentationBackground":      (16, 4),
    "AnyLayout":                   (16, 0),
    "Grid(":                       (16, 0),
    "ShareLink":                   (16, 0),
    # NOT in this list, deliberately: `#Preview`. The macro is declared @available(iOS 17), but its
    # expansion carries that attribute itself, so it compiles against a lower deployment target —
    # confirmed by the 35 existing uses in this target, none of which appeared in the Xcode build
    # log that did report `scrollBounceBehavior`. Adding it here produces false positives.
}

NL = chr(10)


def deployment_target():
    src = io.open(PBX, encoding="utf-8").read()
    versions = set(re.findall(r"IPHONEOS_DEPLOYMENT_TARGET = ([\d.]+);", src))
    if not versions:
        return None, "no IPHONEOS_DEPLOYMENT_TARGET found"
    if len(versions) > 1:
        return None, "configurations disagree: " + ", ".join(sorted(versions))
    raw = versions.pop()
    parts = raw.split(".")
    return (int(parts[0]), int(parts[1]) if len(parts) > 1 else 0), raw


def gated_ranges(src):
    """Character ranges inside an `if #available` or an @available-marked declaration."""
    spans = []
    for m in re.finditer(r"if\s+#available\s*\([^)]*\)\s*\{", src):
        depth, i = 1, m.end()
        while i < len(src) and depth:
            if src[i] == "{":
                depth += 1
            elif src[i] == "}":
                depth -= 1
            i += 1
        spans.append((m.start(), i))
    for m in re.finditer(r"@available\s*\([^)]*\)", src):
        # crude but adequate: the declaration that follows, to the end of its body
        depth, i, started = 0, m.end(), False
        while i < len(src):
            if src[i] == "{":
                depth += 1
                started = True
            elif src[i] == "}":
                depth -= 1
                if started and depth == 0:
                    break
            i += 1
        spans.append((m.start(), i))
    return spans


target, raw = deployment_target()
print("iOS availability check")
if target is None:
    print("  cannot read the deployment target: " + raw)
    sys.exit(1)
print("  deployment target: iOS " + raw)
print("  APIs in the list : %d  (curated, not exhaustive — see the note at the top)" % len(MINIMUM))
print()

problems = []

# ── shape checks: APIs whose NAME is fine but whose form is version-gated ────
#
# A name-only scan cannot see these. `onChange(of:)` exists from iOS 14, but the two-parameter
# closure `{ old, new in }` is a separate iOS 17 overload -- so the call compiles or does not
# depending on how many arguments the closure takes, not on the modifier's name. This is the same
# class of error as scrollBounceBehavior and equally invisible from Windows.
SHAPE = []
ONCHANGE = re.compile(r"\.onChange\(of:[^)]*\)\s*\{([^}\n]*?)\bin\b")
for fn in sorted(os.listdir(SRC)):
    if not fn.endswith(".swift"):
        continue
    src = io.open(os.path.join(SRC, fn), encoding="utf-8").read()
    for m in ONCHANGE.finditer(src):
        if "," in m.group(1) and (17, 0) > target:
            line_no = src[:m.start()].count(NL) + 1
            SHAPE.append((fn, line_no, "onChange two-parameter closure", (17, 0),
                          src.split(NL)[line_no - 1].strip()[:74]))
problems.extend(SHAPE)

for fn in sorted(os.listdir(SRC)):
    if not fn.endswith(".swift"):
        continue
    src = io.open(os.path.join(SRC, fn), encoding="utf-8").read()
    spans = gated_ranges(src)
    # ignore comments so a mention in prose is not a use
    lines = src.split(NL)
    for api, need in MINIMUM.items():
        if need <= target:
            continue
        for m in re.finditer(re.escape(api), src):
            line_no = src[:m.start()].count(NL) + 1
            text = lines[line_no - 1].strip()
            if text.startswith("//") or text.startswith("*") or text.startswith("///"):
                continue
            if any(a <= m.start() < b for a, b in spans):
                continue                      # properly gated
            problems.append((fn, line_no, api, need, text[:74]))

if problems:
    print("FOUND %d ungated use(s) of an API newer than the target:" % len(problems))
    for fn, line, api, need, text in problems:
        print("  %s:%d" % (fn, line))
        print("      %s needs iOS %d.%d, target is iOS %s" % (api, need[0], need[1], raw))
        print("      %s" % text)
    print()
    print("  Fix by wrapping in `if #available(iOS x.y, *)`, or raise the deployment target.")
    sys.exit(1)

print("No ungated use of anything newer than iOS " + raw + ".")
