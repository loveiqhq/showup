# -*- coding: utf-8 -*-
"""Check that docs/tracking-events.md lists exactly the events the code defines.

WHY THIS EXISTS

The tracking handout is the sheet the product side reconciles Jira against, which makes it the one
document in this repo whose being out of date is actively harmful: a stale row does not look stale,
it looks like a specification. A hand-maintained catalogue of 35 events is a catalogue that drifts.

So this asserts both directions. An event in the code and not the doc means the handout is
incomplete. An event in the doc and not the code means the handout describes tracking that does not
exist, which is the worse of the two -- somebody would plan a funnel on it.

It reads the event NAMES, the strings that would appear in the warehouse, not the constant
identifiers. A rename of a Kotlin constant is invisible to a dashboard; a rename of the string is
not.

    python audit/check-tracking-doc.py
"""
import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(HERE)
REPO = os.path.dirname(os.path.dirname(MOBILE))

DOC = os.path.join(REPO, "docs", "tracking-events.md")
KT = os.path.join(MOBILE, "android-preview-project", "app", "src", "main", "java", "com", "showup")
SW = os.path.join(MOBILE, "ios-app", "ShowUpWelcome")
SERVER = os.path.join(REPO, "src", "modules", "analytics", "events")

problems = []
checks = 0


def check(label, ok, detail=""):
    global checks
    checks += 1
    if not ok:
        problems.append(label + ((" -- " + detail) if detail else ""))


def read(path):
    return io.open(path, encoding="utf-8").read()


def client_events():
    """Event-name strings from both mobile catalogues.

    Only names that look like events: the catalogues also hold screen names ("Signup - Codeentry")
    and link values ("privacy_policy"), and those are documented in their own tables rather than as
    events. Events are the ones a warehouse would group by, and every one of ours is snake_case
    with a verb in it.
    """
    out = set()
    for path in (os.path.join(KT, "analytics", "SignUpAnalytics.kt"),
                 os.path.join(KT, "tutorial", "TutorialAnalytics.kt"),
                 os.path.join(SW, "SignUpAnalytics.swift"),
                 os.path.join(SW, "TutorialAnalytics.swift")):
        src = read(path)
        for value in re.findall(r'(?:const val|static let)\s+\w+\s*=\s*"([a-z][a-z_]+)"', src):
            out.add(value)
    # the three `link` property values are not events
    return out - {"terms_and_conditions", "privacy_policy", "legal_notice"}


def server_events():
    out = set()
    for fn in sorted(os.listdir(SERVER)):
        if not fn.endswith(".ts") or fn.endswith(".spec.ts"):
            continue
        for value in re.findall(r"export const \w+ = '([a-z][a-z_]+)';", read(os.path.join(SERVER, fn))):
            out.add(value)
    return out


doc = read(DOC)
# Event names in the doc are always in backticks, which is what keeps prose mentions of a word
# like "state" or "provider" from being read as an event name.
documented = set(re.findall(r"`([a-z][a-z_]{4,})`", doc))

client = client_events()
server = server_events()
code = client | server

check("client catalogue parsed", len(client) > 20, "found %d" % len(client))
check("server catalogue parsed", len(server) == 8, "found %d" % len(server))

missing_from_doc = sorted(code - documented)
check("every event in the code is in the handout", not missing_from_doc,
      "not documented: " + ", ".join(missing_from_doc))

# The reverse direction, restricted to things that look like OUR event names, so ordinary
# backticked prose (`snake_case`, `screen_name`, a file name) is not mistaken for an event.
PREFIXES = ("signup_", "connect_", "tutorial_", "screen_viewed", "legal_link_tapped",
            "account_created", "like_sent", "match_created", "check_in_created",
            "notification_sent", "date_confirmed", "date_completed", "date_cancelled")
# A bare "date_" prefix here matched date_id, which is a PROPERTY. The three date events are
# named in full so a property can never be mistaken for an event.
claimed = set(d for d in documented
              if d.startswith(PREFIXES) and not d.endswith(("_py", "_md")))
invented = sorted(claimed - code)
check("the handout invents nothing", not invented,
      "documented but not in the code: " + ", ".join(invented))

# The totals in the handout are load-bearing: somebody reads "23" and reconciles 23 rows.
for label, count, needle in (("client total", len(client), "**35**"),
                             ("sign-up total", 23, "| **23** |"),
                             ("tutorial total", 4, "| **4** |"),
                             ("server total", len(server), "| **8** |")):
    check("handout states the " + label, needle in doc, "expected " + needle)

check("handout total is client + server",
      len(client) + len(server) == 35,
      "code has %d client + %d server = %d, handout says 35"
      % (len(client), len(server), len(client) + len(server)))

print("Tracking handout vs code")
print("  %d client events, %d server events, %d checks" % (len(client), len(server), checks))
print()
if problems:
    print("FAILED %d:" % len(problems))
    for p in problems:
        print("  x " + p)
    sys.exit(1)
print("  all passed")
print()
print("  This proves the handout lists what the code does. Whether the code matches the TICKETS")
print("  is the reconciliation section of the handout, and a person has to do that.")
