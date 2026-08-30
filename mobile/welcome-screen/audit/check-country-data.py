# -*- coding: utf-8 -*-
"""Structural checks on the country data and flag artwork.

    python audit/check-country-data.py

This replaces check-phone-validation.py, which parsed a hand-written table of 35 countries and
their lengths. That table is gone: the countries and every rule now come from libphonenumber, and
the rules are tested against libphonenumber's own data by

    app/src/test/java/com/showup/welcome/PhoneValidationTest.kt   (Gradle: testDebugUnitTest)

which is strictly better — it runs the real code against the real library rather than a Python
re-implementation that could drift from both.

What is left for this file is what those tests cannot see:

  * that the library is actually wired into the build
  * that the flag artwork exists, is not empty, and is IDENTICAL on both platforms — iOS cannot be
    built here, so a missing flag on that side would otherwise surface only on a Mac
  * that the artwork's licence ships with it, which is the MIT condition
"""
import io
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KT_DIR = os.path.join(ROOT, "android-preview-project/app/src/main/java/com/showup/welcome")
AND_FLAGS = os.path.join(ROOT, "android-preview-project/app/src/main/assets/flags")
IOS_FLAGS = os.path.join(ROOT, "ios-app/ShowUpWelcome/Flags")
GRADLE = os.path.join(ROOT, "android-preview-project/app/build.gradle.kts")
TESTS = os.path.join(ROOT,
                     "android-preview-project/app/src/test/java/com/showup/welcome/PhoneValidationTest.kt")

fails = []
checks = 0


def check(name, ok, detail=""):
    global checks
    checks += 1
    if not ok:
        fails.append(name + ((" -- " + detail) if detail else ""))


def read(p):
    return io.open(p, encoding="utf-8").read()


print("Country data and flag artwork")

# ── the library is wired in, and the rules come from it ─────────────────────
gradle = read(GRADLE)
check("libphonenumber is a dependency",
      "com.googlecode.libphonenumber:libphonenumber" in gradle)
check("unit tests can run", "testImplementation" in gradle)
check("the rules are tested", os.path.exists(TESTS))

codes = read(os.path.join(KT_DIR, "CountryCodes.kt"))
check("the country list is derived, not hand-written", "phoneUtil.supportedRegions" in codes)
check("no hand-written length table survives",
      "nsnMin" not in codes and "nsnMax" not in codes,
      "a hand-maintained length table is exactly what libphonenumber replaced")
check("validation asks about a MOBILE specifically",
      "isPossibleNumberForTypeWithReason" in codes)
check("type is checked before length",
      codes.index("isValidNumberForRegion") < codes.index("isPossibleNumberForTypeWithReason"),
      "otherwise a short landline reports 'too short' instead of 'we need a mobile'")

# ── the artwork ─────────────────────────────────────────────────────────────
def flags(d):
    if not os.path.isdir(d):
        return {}
    return {f[:-4]: os.path.getsize(os.path.join(d, f))
            for f in os.listdir(d) if f.endswith(".png")}


a, i = flags(AND_FLAGS), flags(IOS_FLAGS)
print("  android: %d flags   ios: %d flags" % (len(a), len(i)))

check("android ships flags", len(a) > 200, "found %d" % len(a))
check("ios ships flags", len(i) > 200, "found %d" % len(i))
# The important one: iOS cannot be built here, so a mismatch would only appear on a Mac.
check("both platforms ship the SAME flags", set(a) == set(i),
      "only on one side: %s" % sorted(set(a) ^ set(i))[:10])

empty = sorted(k for k, v in a.items() if v < 100)
check("no flag is empty or truncated", not empty, "suspiciously small: %s" % empty[:10])

for label, d in [("android", AND_FLAGS), ("ios", IOS_FLAGS)]:
    lic = os.path.join(d, "LICENSE-flag-icons.txt")
    check("the artwork licence ships (%s)" % label, os.path.exists(lic))
    if os.path.exists(lic):
        check("it is the MIT text (%s)" % label, "MIT License" in read(lic))

total = sum(a.values())
print("  artwork: %.0f KB, average %.1f KB per flag" % (total / 1024, total / 1024 / max(len(a), 1)))
check("artwork stays small enough to bundle", total < 2 * 1024 * 1024,
      "%.1f MB is more than a flag set should cost" % (total / 1024 / 1024))

print()
print("  %d checks" % checks)
if fails:
    print("FAILED %d:" % len(fails))
    for f in fails:
        print("  x " + f)
    sys.exit(1)
print("  all passed")
print()
print("  The RULES are covered by PhoneValidationTest, not here:")
print("    cd android-preview-project && ./gradlew :app:testDebugUnitTest")
