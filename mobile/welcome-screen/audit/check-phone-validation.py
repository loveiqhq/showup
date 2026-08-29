# -*- coding: utf-8 -*-
"""Run the phone validation rules against real cases, on both platforms' tables.

    python audit/check-phone-validation.py

This exists because of a bug that shipped: a too-short and a too-long German number were both
accepted. Two causes, and neither was in the validation function itself.

  1. The length table described EVERY kind of number in a country, not mobile numbers. German
     landlines start at six digits, so 49 6 12345 passed a rule that was correct for landlines and
     useless for a screen whose only job is to send an SMS.

  2. The input field capped at exactly the country maximum, so a too-long number could not be
     typed at all -- the extra keystrokes were swallowed, and the too-long error was dead code.

The table is parsed out of the real source on both platforms rather than duplicated here, so this
cannot pass while the app disagrees with it.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KT = os.path.join(ROOT, "android-preview-project/app/src/main/java/com/showup/welcome/CountryCodes.kt")
SW = os.path.join(ROOT, "ios-app/ShowUpWelcome/CountryCodes.swift")

fails = []
checks = 0


def check(name, ok, detail=""):
    global checks
    checks += 1
    if not ok:
        fails.append(name + ((" -- " + detail) if detail else ""))


def kotlin_table():
    s = io.open(KT, encoding="utf-8").read()
    out = {}
    for m in re.finditer(r'Country\("(\w\w)", "([^"]+)", "(\+\d+)", (\d+), (\d+)', s):
        iso, name, dial, lo, hi = m.groups()
        out[iso] = (name, dial, int(lo), int(hi))
    return out


def swift_table():
    s = io.open(SW, encoding="utf-8").read()
    out = {}
    for m in re.finditer(
            r'\.init\(iso: "(\w\w)", name: "([^"]+)", dial: "(\+\d+)", nsnMin: (\d+), nsnMax: (\d+)', s):
        iso, name, dial, lo, hi = m.groups()
        out[iso] = (name, dial, int(lo), int(hi))
    return out


def validate(raw, lo, hi):
    """The same rules the two apps apply, in the same order."""
    digits = "".join(c for c in raw if c.isdigit())
    if any(c.isalpha() for c in raw):
        return "NotANumber"
    if not digits:
        return "Empty"
    if digits.startswith("0"):
        return "LeadingZero"
    if len(digits) < lo:
        return "TooShort"
    if len(digits) > hi:
        return "TooLong"
    return None


kt = kotlin_table()
sw = swift_table()

print("Phone validation")
print("  countries: %d kotlin, %d swift" % (len(kt), len(sw)))
print()

# ── the two tables must agree, or one platform accepts what the other rejects ──
check("both platforms list the same countries", set(kt) == set(sw),
      "only in one: %s" % (set(kt) ^ set(sw)))
for iso in sorted(set(kt) & set(sw)):
    check("%s ranges agree across platforms" % iso, kt[iso][2:] == sw[iso][2:],
          "kotlin %s, swift %s" % (kt[iso][2:], sw[iso][2:]))

# ── the reported bug, as cases ─────────────────────────────────────────────
# Germany is the launch market and the number the bug was reported with.
DE = kt["DE"]
lo, hi = DE[2], DE[3]
CASES = [
    # (input,               expected verdict,  why it is in this list)
    ("1761234567",          None,          "a real 10-digit German mobile"),
    ("17612345678",         None,          "a real 11-digit German mobile"),
    ("612345",              "TooShort",    "THE BUG: 6 digits passed while the table said landline"),
    ("1761",                "TooShort",    "obviously short"),
    ("176123456",           "TooShort",    "9 digits, one short of a mobile"),
    ("176123456789",        "TooLong",     "THE BUG: too long was untypeable, so never rejected"),
    ("1761234567890123",    "TooLong",     "far too long"),
    ("01761234567",         "LeadingZero", "the trunk 0 people habitually type"),
    ("",                    "Empty",       "nothing entered"),
    ("abcdefghij",          "NotANumber",  "letters"),
    ("176 123 45 678",      None,          "spaces are cosmetic, the value is digits"),
]
print("  Germany, %s, mobile length %d..%d" % (DE[1], lo, hi))
for raw, expected, why in CASES:
    got = validate(raw, lo, hi)
    ok = got == expected
    check("DE %-18r -> %s" % (raw, expected), ok, "got %s" % got)
    print("    %s %-18r %-12s %s" % ("ok " if ok else "FAIL", raw, str(got), why))
print()

# ── no country may accept a number too short to be a mobile ────────────────
# Seven is the shortest mobile NSN in use anywhere in this list (Estonia). Anything below that
# would mean the table has slipped back to describing landlines.
for iso, (name, dial, mn, mx) in sorted(kt.items()):
    check("%s minimum is a mobile length" % iso, mn >= 7,
          "%s allows %d digits, which no mobile network uses" % (name, mn))
    check("%s range is sane" % iso, mn <= mx <= 15,
          "%s is %d..%d" % (name, mn, mx))

# ── the sample number must itself pass that country's own rule ─────────────
# It is shown in the empty field as the example to copy, so if it fails validation the screen is
# telling the user to type something it will then reject.
for iso, (name, dial, mn, mx) in sorted(kt.items()):
    s = io.open(KT, encoding="utf-8").read()
    m = re.search(r'Country\("%s".*?"([\d ]+)"\)' % iso, s, re.S)
    if not m:
        continue
    verdict = validate(m.group(1), mn, mx)
    check("%s sample number is valid" % iso, verdict is None,
          "%s shows %r as the example, which validates as %s" % (name, m.group(1), verdict))

# ── the field must accept more than the maximum, or too-long is unreachable ─
for label, path, needle in [
    ("kotlin", os.path.join(ROOT, "android-preview-project/app/src/main/java/com/showup/welcome/PhoneVerificationScreen.kt"),
     "country.nsnMax + OVERTYPE_ALLOWANCE"),
    ("swift", os.path.join(ROOT, "ios-app/ShowUpWelcome/PhoneVerificationView.swift"),
     "country.nsnMax + OVERTYPE_ALLOWANCE"),
]:
    src = io.open(path, encoding="utf-8").read()
    check("field allows over-typing (%s)" % label, needle in src,
          "capping at the maximum makes TooLong unreachable")

print("  %d checks" % checks)
if fails:
    print()
    print("FAILED %d:" % len(fails))
    for f in fails:
        print("  x " + f)
    sys.exit(1)
print("  all passed")
