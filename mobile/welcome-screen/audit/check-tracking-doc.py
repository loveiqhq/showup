# -*- coding: utf-8 -*-
"""Check that docs/tracking-events.md lists exactly the events the code defines.

WHY THIS EXISTS

The tracking handout is the sheet the product side reconciles Jira against, which makes it the one
document in this repo whose being out of date is actively harmful: a stale row does not look stale,
it looks like a specification.

The first version of this check (7 September 2026) read the two catalogues that existed then,
sign-up and tutorial. Profile creation and the Check-In then added seventy events in two new files
it never opened, and it went on passing. So it no longer keeps its own list of files: it asks
gen-tracking-catalogue.py, which reads every catalogue, and fails if the generated tables, the CSV,
or the hand-written numbers around them no longer match the code.

It still asserts both directions. An event in the code and not the doc means the handout is
incomplete. An event in the doc and not the code means the handout describes tracking that does not
exist, which is the worse of the two -- somebody would plan a funnel on it.

    python audit/check-tracking-doc.py
"""
import importlib.util
import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

spec = importlib.util.spec_from_file_location("gen", os.path.join(HERE, "gen-tracking-catalogue.py"))
gen = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gen)

problems = []
checks = 0


def check(label, ok, detail=""):
    global checks
    checks += 1
    if not ok:
        problems.append(label + ((" -- " + detail) if detail else ""))


events = gen.build()
names = {e["event"] for e in events}
client = [e for e in events if e["source"] == "client"]
doc = io.open(gen.DOC, encoding="utf-8").read()
csv_now = io.open(gen.CSV, encoding="utf-8").read()

check("client catalogues parsed", len(client) > 90, "found %d" % len(client))
check("server catalogue parsed", sum(1 for e in events if e["source"] == "server") >= 8)

# The generated blocks and the CSV are what the code says today.
fresh_doc = gen.splice(doc, gen.render(events))
check("the generated tables are up to date", fresh_doc == doc,
      "run: python mobile/welcome-screen/audit/gen-tracking-catalogue.py")
check("the CSV is up to date", gen.csv_text(events) == csv_now,
      "run: python mobile/welcome-screen/audit/gen-tracking-catalogue.py")

# Both directions, on the event NAMES -- the strings a warehouse groups by.
documented = set(re.findall(r"`([a-z][a-z0-9_]{4,})`", doc))
missing = sorted(names - documented)
check("every event in the code is in the handout", not missing, "not documented: " + ", ".join(missing))
invented = sorted(d for d in documented if d in gen.REGISTRY_EVENTS and d not in names)
check("the handout names no event the code lacks", not invented,
      "documented but not in the code: " + ", ".join(invented))

# The hand-written numbers in the prose are load-bearing: somebody reads "106" and counts rows.
m = re.search(r"\*\*35 events became (\d+)\.\*\*", doc)
check("the prose states the total", m is not None and int(m.group(1)) == len(events),
      "prose says %s, the code has %d" % (m.group(1) if m else "nothing", len(events)))
both = sum(1 for e in client if e["android"] == "fires" and e["ios"] == "fires")
m = re.search(r"Of the (\d+) client events, (\d+) are sent on both", doc)
check("the prose states the client and sent-on-both counts",
      m is not None and (int(m.group(1)), int(m.group(2))) == (len(client), both),
      "prose says %s, the code has %d and %d" % (m.groups() if m else "nothing", len(client), both))

print("Tracking handout vs code")
print("  %d client events, %d server events, %d checks" % (len(client), len(events) - len(client), checks))
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
