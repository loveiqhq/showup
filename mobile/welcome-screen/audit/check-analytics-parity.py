# -*- coding: utf-8 -*-
"""The sign-up event catalogue must be identical on Android and iOS.

    python audit/check-analytics-parity.py

WHY THIS EXISTS

The tracking specification lives in five Jira tickets (SHOWUP-140, 142, 143, 144, 145) and is
implemented twice -- Kotlin and Swift. Two copies of a taxonomy is a taxonomy that drifts, and the
failure is quiet in the worst way: an event added on one platform and forgotten on the other
produces a funnel that reports half its traffic, with nothing anywhere failing. The numbers look
plausible. They are just wrong, by an amount nobody can measure without going back to the code.

So the two catalogues are compared. Same shape as check-privacy-parity.py, and for the same reason.

WHAT THIS DOES NOT CHECK

Whether the catalogue matches the tickets -- that is a reading of prose and a person has to do it.
It checks that the two implementations agree, plus the naming convention shared with the backend
(`date_confirmed`, `like_sent`), which is what makes one warehouse query cover client and server.
"""
import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(HERE)

KOTLIN = os.path.join(
    MOBILE, "android-preview-project/app/src/main/java/com/showup/analytics/SignUpAnalytics.kt")
SWIFT = os.path.join(MOBILE, "ios-app/ShowUpWelcome/SignUpAnalytics.swift")

fails = []
checks = 0


def check(name, ok, detail=""):
    global checks
    checks += 1
    if not ok:
        fails.append(name + ((" -- " + detail) if detail else ""))


def read(path):
    return io.open(path, encoding="utf-8").read() if os.path.exists(path) else ""


def strip_comments(text):
    """Line comments removed.

    The catalogues quote event and screen names inside comments -- deliberately, to record which
    ticket each came from -- so a naive scan would invent entries that do not exist.
    """
    return "\n".join(line.split("//")[0] for line in text.splitlines())


def event_names(text):
    """Every string literal assigned to a `const val` / `static let` whose value is snake_case.

    Anchored on the declaration rather than on any quoted string, so a name mentioned in prose is
    not counted.
    """
    body = strip_comments(text)
    found = set()
    # Kotlin: const val NAME = "value"    Swift: static let name = "value"
    for pattern in (r'const val\s+\w+\s*=\s*"([a-z][a-z0-9_]*)"',
                    r'static let\s+\w+\s*=\s*"([a-z][a-z0-9_]*)"'):
        found |= set(re.findall(pattern, body))
    return found


def screen_names(text):
    """The screenview `screen_name` values, which are verbatim ticket strings and NOT snake_case."""
    body = strip_comments(text)
    found = set()
    for pattern in (r'const val\s+\w+\s*=\s*"([A-Z][^"]*)"',
                    r'static let\s+\w+\s*=\s*"([A-Z][^"]*)"'):
        found |= set(re.findall(pattern, body))
    return found


print("Sign-up analytics parity across Android and iOS")

for label, path in (("Kotlin", KOTLIN), ("Swift", SWIFT)):
    check("%s catalogue exists" % label, os.path.exists(path), path)

if fails:
    print("  %d checks" % checks)
    print("")
    print("FAILED %d:" % len(fails))
    for f in fails:
        print("  x %s" % f)
    sys.exit(1)

kotlin_events = event_names(read(KOTLIN))
swift_events = event_names(read(SWIFT))
kotlin_screens = screen_names(read(KOTLIN))
swift_screens = screen_names(read(SWIFT))

# A parse that silently returned nothing would make every comparison below pass.
check("Kotlin catalogue parsed", len(kotlin_events) > 15, "found %d" % len(kotlin_events))
check("Swift catalogue parsed", len(swift_events) > 15, "found %d" % len(swift_events))
check("Kotlin screen names parsed", len(kotlin_screens) >= 6, "found %d" % len(kotlin_screens))
check("Swift screen names parsed", len(swift_screens) >= 6, "found %d" % len(swift_screens))

if not fails:
    only_kotlin = sorted(kotlin_events - swift_events)
    only_swift = sorted(swift_events - kotlin_events)
    check("the two event catalogues are identical",
          not only_kotlin and not only_swift,
          "Android only: %s | iOS only: %s"
          % (", ".join(only_kotlin) or "none", ", ".join(only_swift) or "none"))

    s_only_kotlin = sorted(kotlin_screens - swift_screens)
    s_only_swift = sorted(swift_screens - kotlin_screens)
    check("the two screen-name lists are identical",
          not s_only_kotlin and not s_only_swift,
          "Android only: %s | iOS only: %s"
          % (", ".join(s_only_kotlin) or "none", ", ".join(s_only_swift) or "none"))

    # Shared with the backend's taxonomy. One convention, or a warehouse query has to know which
    # platform emitted a row before it can name the column.
    convention = re.compile(r"^[a-z][a-z0-9_]*[a-z0-9]$")
    bad = sorted(n for n in kotlin_events | swift_events if not convention.match(n))
    check("every event name is snake_case", not bad, ", ".join(bad))

    # 26 snake_case constants: the 23 events the five tickets define, plus the three `Legal` link
    # VALUES (terms_and_conditions, privacy_policy, legal_notice), which are property values rather
    # than event names but are declared the same way and are worth holding steady too.
    #
    # A magic number, deliberately. If it moves, a ticket's Tracking section moved with it, and
    # somebody should have read the ticket rather than adjusted this line.
    check("the catalogue still holds 26 snake_case constants",
          len(kotlin_events) == 26,
          "found %d -- if a ticket's Tracking section changed, update the count and say why"
          % len(kotlin_events))

print("  %d events, %d screen names, %d checks" % (len(kotlin_events), len(kotlin_screens), checks))

if fails:
    print("")
    print("FAILED %d:" % len(fails))
    for f in fails:
        print("  x %s" % f)
    sys.exit(1)

print("")
print("  all passed")
print("")
print("  This proves the two platforms agree. Whether they match the TICKETS is a reading of")
print("  prose, and a person has to do it.")
