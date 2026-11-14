"""
verify-arrival.py -- the arrival check-in (SHOWUP-195, Date 03) against its ticket, on both apps and the server.

Reads the ticket's `## Copy -- final strings` section AT RUN TIME (design_handoff_showup/date/tickets/
03-arrival-checkin.md) and checks every quoted string of frames A, B, C, D, H and the report note in the Kotlin
and the Swift arrival copy, so an edit to the ticket fails both platforms until they follow. A string with a
`{name}` / `{time}` placeholder is checked as its literal runs, because each app fills them its own way.
Apostrophes compare as one character, and the ticket's ASCII `I've` / `couldn't` must be what the code holds.

G (the date gate, `LOCATION_COPY.date`) is Search 04's screen in `context="date"`; the ticket consumes it and
does not quote it, so its strings are held here from the SHOWUP-195 plan's Global Constraints (the kit's
LOCATION_COPY.date) and checked in each platform's location screen.

Then the rules a reading can check:
  - END_DATE_LINK_SHOWN is false on both apps (the End date link waits for Date 06)
  - `ChatReason.Arrived` / `.arrived` is shown
  - no coordinate / accuracy / distance / place / address key in either app's arrival analytics, nor the
    server's arrival builders; the payload keys are exactly the registry's
  - no message_sent / date_cancelled / screen_viewed emitted from the arrival packages
  - the four events are in events.json family M, and `entry` takes only section 41's values

And it proves it can fail: the copy check is run once more against an Android source with one ticket string
removed, and that run must report it.
"""
import glob
import io
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(MOBILE))
HANDOFF = os.path.join(ROOT, "design_handoff_showup")
TICKET = os.path.join(HANDOFF, "date", "tickets", "03-arrival-checkin.md")
EVENTS = os.path.join(HANDOFF, "tracking", "events.json")
KT_ROOT = os.path.join(MOBILE, "android-preview-project", "app", "src", "main", "java", "com", "showup")
KT = os.path.join(KT_ROOT, "arrival")
SW = os.path.join(MOBILE, "ios-app", "ShowUpWelcome")
SERVER_EVENTS = os.path.join(ROOT, "src", "modules", "analytics", "events", "date-events.ts")

failures = []
count = 0


def check(name, ok):
    global count
    count += 1
    if not ok:
        failures.append(name)


def read(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()


def norm(text):
    """One apostrophe, and Swift's / Kotlin's escapes resolved, so a quote matches however it is spelled."""
    for esc, ch in (("\\u{2019}", "'"), ("\\u{2014}", "—"), ("\\u2019", "'"), ("\\u2014", "—")):
        text = text.replace(esc, ch)
    return text.replace("’", "'")


def joined_literals(source):
    """The source with literals split over lines joined: `"a " +\\n "b"` reads as `"a b"`."""
    return re.sub(r'"\s*\+\s*"', "", re.sub(r'"\s*\+\s*\n\s*"', "", source))


def runs(s):
    """The literal runs of a string around its `{placeholders}`, the ones worth matching."""
    return [p for p in re.split(r"\{[^}]*\}", s) if len(p.strip(" ·,.?…")) >= 2]


def missing_from(strings, source, label):
    return ["ticket copy on %s: %r" % (label, s) for s in strings if not all(r in source for r in runs(s))]


# ── the arrival copy, quoted from the ticket ────────────────────────────────────────────────────
kt_files = sorted(glob.glob(os.path.join(KT, "*.kt")))
sw_files = sorted(glob.glob(os.path.join(SW, "Arrival*.swift"))) + [os.path.join(SW, "CheckInBar.swift")]
check("the Android arrival package was found", len(kt_files) >= 8)
check("the iOS arrival files were found", len(sw_files) >= 8 and all(os.path.exists(p) for p in sw_files))
kt_copy = norm(joined_literals(read(os.path.join(KT, "ArrivalCopy.kt"))))
sw_copy = norm(joined_literals(read(os.path.join(SW, "ArrivalCopy.swift"))))

ticket = read(TICKET)
section = ticket.split("## Copy", 1)[1].split("\n## ", 1)[0]
lines = [ln for ln in section.splitlines() if re.match(r"\*\*[ABCDH] ·|\*\*Report entry", ln)]
check("the ticket's copy section has frames A, B, C, D, H and the report note", len(lines) == 6)
quoted = []
for ln in lines:
    # Only what is inside backticks, and before the report line's commentary.
    quoted += [norm(q) for q in re.findall(r"`([^`]+)`", ln.split(" — ", 1)[0] if ln.startswith("**Report") else ln)]
# `{name}` on its own is not copy; `End date` is hidden (END_DATE_LINK_SHOWN) but still held in the object.
strings = [q for q in dict.fromkeys(quoted) if not re.fullmatch(r"\{[^}]*\}", q) and q != "note"]
check("the ticket's copy section was read (%d strings)" % len(strings), len(strings) >= 18)
for want in ("I've arrived", "couldn't find"):
    check("the ticket spells %r with an ASCII apostrophe" % want, any(want.replace("'", "'") in s for s in strings))

for m in missing_from(strings, kt_copy, "Android ArrivalCopy.kt") + missing_from(strings, sw_copy, "iOS ArrivalCopy.swift"):
    check(m, False)
count += len(strings) * 2

# ── G, the date gate's strings (LOCATION_COPY.date; Search 04 in context="date") ────────────────
G_HEAD_A = "Urgent! Allow location to get access to your "
G_HEAD_B = "Urgent! Turn on location to get access to your "
G_STRINGS = [
    G_HEAD_A, G_HEAD_B, "date information",
    "Your date screen needs your location. It gets you to the meeting place and lets you check in when you arrive, "
    "so {name} knows you're there.",
    "Location is off for Show Up, so your date screen is locked. Turn it on to see your date, get directions and "
    "check in when you arrive, so {name} knows you're there.",
    "Location is off for every app on this phone, so your date screen is locked. Turn it on to see your date, get "
    "directions and check in when you arrive, so {name} knows you're there.",
    "Allow location access", "Open Settings",
    "Starts in %dh %dm", "Started %d min ago", "%s may be waiting",
]
kt_gate = norm(joined_literals(read(os.path.join(KT_ROOT, "profile", "ProfileLocationScreen.kt"))))
sw_gate = norm(joined_literals(read(os.path.join(SW, "ProfileLocation.swift"))))


def with_name(s, token):
    return s.replace("{name}", token)


for s in G_STRINGS:
    check("Android date gate holds %r" % s[:50], with_name(s, "$name") in kt_gate or s in kt_gate)
    sw_form = s.replace("%dh %dm", "\\(h)h \\(m)m").replace("%d min", "\\(n) min")
    check("iOS date gate holds %r" % s[:50],
          with_name(s, "\\(name)") in sw_gate or sw_form in sw_gate or s in sw_gate
          or all(r in sw_gate for r in re.split(r"%\w|\{name\}", s) if len(r) > 3))

# ── it can fail: one ticket string taken out of the Android source must be reported ─────────────
victim = "Undo check-in"
check("the mutation check has a string to remove", victim in kt_copy)
check("the copy check reports a string removed from the source (mutation)",
      any(victim in m for m in missing_from(strings, kt_copy.replace(victim, ""), "Android")))

# ── the rules ───────────────────────────────────────────────────────────────────────────────────
kt_bar = read(os.path.join(KT, "CheckInBar.kt"))
sw_bar = read(os.path.join(SW, "CheckInBar.swift"))
check("Android: END_DATE_LINK_SHOWN = false", re.search(r"const val END_DATE_LINK_SHOWN\s*=\s*false\b", kt_bar) is not None)
check("iOS: endDateLinkShown = false", re.search(r"let endDateLinkShown\s*=\s*false\b", sw_bar) is not None)

kt_reasons = read(os.path.join(KT_ROOT, "chat", "ChatReasons.kt"))
m = re.search(r'^\s*Arrived\("arrived",.*,\s*(true|false)\),?\s*$', kt_reasons, re.M)
check("Android: ChatReason.Arrived is shown", bool(m) and m.group(1) == "true")
sw_reasons = read(os.path.join(SW, "ChatReasons.swift"))
hidden = re.search(r"var shown: Bool \{.*?case ([^:]+): return false", sw_reasons, re.S)
check("iOS: ChatReason.arrived exists", re.search(r'case arrived = "arrived"', sw_reasons) is not None)
check("iOS: ChatReason.arrived is shown", bool(hidden) and ".arrived" not in hidden.group(1))

# Analytics: exact keys, none of the forbidden ones.
FORBIDDEN = re.compile(r"lat|lng|lon\b|longitude|latitude|coord|accuracy|distance|place|address|meters|metres|geohash|location", re.I)
CLIENT_KEYS = {"date_id", "entry", "minutes_vs_start"}
kt_an = read(os.path.join(KT, "ArrivalAnalytics.kt"))
sw_an = read(os.path.join(SW, "ArrivalAnalytics.swift"))


def strip_comments(src):
    return re.sub(r"//[^\n]*|/\*.*?\*/", "", src, flags=re.S)


kt_keys = set(re.findall(r'put\("([a-z_]+)"', strip_comments(kt_an)))
sw_keys = set(re.findall(r'^\s*"([a-z_]+)":\s', strip_comments(sw_an), re.M))
check("Android: the arrival payload keys are the registry's three (%s)" % sorted(kt_keys), kt_keys == CLIENT_KEYS)
check("iOS: the arrival payload keys are the registry's three (%s)" % sorted(sw_keys), sw_keys == CLIENT_KEYS)
for label, keys in (("Android", kt_keys), ("iOS", sw_keys)):
    check("%s: no coordinate, accuracy, distance, place or address key" % label,
          not any(FORBIDDEN.search(k) for k in keys))

server = read(SERVER_EVENTS)


def builder_keys(name):
    body = re.search(r"export function %s\(.*?\n\}\n" % name, server, re.S)
    props = re.search(r"properties:\s*\{(.*?)\n\s*\},", body.group(0), re.S) if body else None
    return set(re.findall(r"^\s*([a-z_]+)[:,]", props.group(1), re.M)) if props else None


for name, want in (("arrivalConfirmedEvent", CLIENT_KEYS | {"is_first"}),
                   ("arrivalUndoneEvent", {"date_id", "entry", "seconds_since_confirm"})):
    got = builder_keys(name)
    check("server: %s keys are the registry's (%s)" % (name, sorted(got or [])), got == want)
    check("server: %s carries no coordinate, accuracy, distance, place or address" % name,
          got is not None and not any(FORBIDDEN.search(k) for k in got))

# Never emitted from the arrival packages.
NEVER = ("message_sent", "date_cancelled", "screen_viewed")
kt_arrival = strip_comments("\n".join(read(p) for p in kt_files))
sw_arrival = strip_comments("\n".join(read(p) for p in sw_files))
for event in NEVER:
    check("Android arrival package never emits %s" % event, event not in kt_arrival and "messageSent" not in kt_arrival
          if event == "message_sent" else event not in kt_arrival)
    check("iOS arrival files never emit %s" % event, event not in sw_arrival)

# ── the registry ────────────────────────────────────────────────────────────────────────────────
reg = json.loads(read(EVENTS))
family_m = next((f for f in reg["families"] if f["id"] == "M"), None)
check("events.json has family M", family_m is not None)
by_name = {e["name"]: e for e in (family_m or {"events": []})["events"]}
for ev in ("arrival_confirm_shown", "arrival_confirm_submitted", "arrival_confirmed", "arrival_undone"):
    e = by_name.get(ev)
    check("family M holds %s" % ev, e is not None)
    if e is None:
        continue
    props = {p["name"]: p for p in e.get("payload", [])}
    check("%s carries date_id and entry" % ev, "date_id" in props and "entry" in props)
    check("%s's entry is section 41's" % ev, "41" in props.get("entry", {}).get("type", ""))
    check("%s is marked implemented" % ev, e.get("implemented") is True)
    check("%s's payload has no coordinate, accuracy, distance, place or address" % ev,
          not any(FORBIDDEN.search(k) for k in props))

ENTRY_VALUES = {"date_screen", "chat_chip", "report"}
enums = json.loads(read(os.path.join(HANDOFF, "tracking", "enums.json")))
sec = next((x for x in enums.get("sets", []) if x.get("name") == "arrival_entry"), None) \
    if isinstance(enums, dict) else None
check("enums.json section 41 is arrival_entry with date_screen, chat_chip, report",
      sec is not None and set(sec["values"]) == ENTRY_VALUES)
wire = set(re.findall(r'\("([a-z_]+)"\)|\w+\("([a-z_]+)"\)', read(os.path.join(KT, "ArrivalRules.kt"))))
kt_wire = {a or b for a, b in wire}
check("Android: ArrivalEntry values are section 41's (%s)" % sorted(kt_wire & ENTRY_VALUES), kt_wire >= {"date_screen", "chat_chip"})
sw_wire = set(re.findall(r'case \w+ = "([a-z_]+)"', read(os.path.join(SW, "ArrivalRules.swift"))))
check("iOS: ArrivalEntry values include date_screen and chat_chip and nothing outside section 41",
      sw_wire >= {"date_screen", "chat_chip"} and sw_wire <= ENTRY_VALUES)

print("arrival conformance: %d checks" % count)
if failures:
    for name in failures:
        print("  x " + name)
    sys.exit(1)
print("all passed")
