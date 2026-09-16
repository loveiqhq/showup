# -*- coding: utf-8 -*-
"""The prohibited-field list must be identical on all three platforms.

    python audit/check-privacy-parity.py

WHY THIS EXISTS

One list of field names decides what never leaves the system in a form a human or an outside
service can read. It is written three times -- TypeScript for the backend's logs and analytics,
Kotlin for Android crash reports, Swift for iOS -- because there is no shared language between
them and generating it would mean a build step in three toolchains to keep ~44 strings in step.

Three copies of a privacy rule is a rule that drifts. The failure is quiet and one-directional: a
name added to the backend and forgotten on mobile means the apps keep sending something the backend
has decided is too sensitive to record, and nothing anywhere fails. Nobody finds out until a phone
number appears in a crash report.

So the copies are not trusted to match. They are compared.

WHAT THIS DOES NOT CHECK

That the lists are correct or complete -- that is a judgement about our data, not something a
script can decide. It checks only that the three agree, which is the part that rots.
"""
import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(HERE)
REPO = os.path.dirname(os.path.dirname(MOBILE))

SOURCES = {
    "backend (TypeScript)": os.path.join(REPO, "src/common/privacy/prohibited-fields.ts"),
    "Android (Kotlin)": os.path.join(
        MOBILE, "android-preview-project/app/src/main/java/com/showup/observability/ProhibitedFields.kt"),
    "iOS (Swift)": os.path.join(MOBILE, "ios-app/ShowUpWelcome/ProhibitedFields.swift"),
}

fails = []
checks = 0


def check(name, ok, detail=""):
    global checks
    checks += 1
    if not ok:
        fails.append(name + ((" -- " + detail) if detail else ""))


def read(path):
    return io.open(path, encoding="utf-8").read() if os.path.exists(path) else ""


def names_in(text):
    """Every quoted lower-case identifier inside the list literal.

    Comments are stripped FIRST, for two reasons. They mention `error_code`, `age_band` and
    `token_type` by name -- deliberately, to record why those safe look-alikes are not on the list
    -- and picking those up would invent entries. And they contain parentheses: the comment
    "(must be bucketed into age_band instead)" ends with one, which truncated the literal at 19 of
    44 names on the first run of this checker.
    """
    text = "\n".join(line.split("//")[0] for line in text.splitlines())

    start = None
    for marker in ("new Set<string>([", "= setOf(", "Set<String> = ["):
        idx = text.find(marker)
        if idx != -1:
            start = idx + len(marker)
            break
    if start is None:
        return set()

    # With comments gone, the first closer really is the end of the literal: the entries are plain
    # quoted strings separated by commas and contain no brackets.
    end = len(text)
    for i in range(start, len(text)):
        if text[i] in ("]", ")"):
            end = i
            break

    return set(re.findall(r"['\"]([a-z0-9]+)['\"]", text[start:end]))


print("Prohibited-field parity across backend, Android and iOS")

missing_file = False
for label, path in SOURCES.items():
    present = os.path.exists(path)
    check("%s list exists" % label, present, path)
    if not present:
        missing_file = True

if missing_file:
    print("  %d checks" % checks)
    print("")
    print("FAILED %d:" % len(fails))
    for f in fails:
        print("  x %s" % f)
    sys.exit(1)

lists = {label: names_in(read(path)) for label, path in SOURCES.items()}

for label, names in lists.items():
    # A parse that silently returns nothing would make every comparison below pass. Guard against
    # the checker breaking quietly when somebody reformats a list.
    check("%s list parsed" % label, len(names) > 20, "found %d names" % len(names))

if not fails:
    reference_label = "backend (TypeScript)"
    reference = lists[reference_label]

    for label, names in lists.items():
        if label == reference_label:
            continue
        only_ref = sorted(reference - names)
        only_this = sorted(names - reference)
        check("%s matches the backend list" % label,
              not only_ref and not only_this,
              "missing here: %s | extra here: %s"
              % (", ".join(only_ref) or "none", ", ".join(only_this) or "none"))

    check("all three lists are the same size",
          len({len(n) for n in lists.values()}) == 1,
          ", ".join("%s=%d" % (l, len(n)) for l, n in lists.items()))

print("  %d fields, %d checks" % (len(lists["backend (TypeScript)"]), checks))

if fails:
    print("")
    print("FAILED %d:" % len(fails))
    for f in fails:
        print("  x %s" % f)
    sys.exit(1)

print("")
print("  all passed")
print("")
print("  This proves the three lists agree. Whether they are COMPLETE is a judgement about our")
print("  data, and no script can make it.")
