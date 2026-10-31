"""
verify-location-gate.py -- the Search location gate (SHOWUP-188) against its ticket, on both apps.

Reads the ticket's `## Copy -- final strings` section AT RUN TIME (design_handoff_showup/search/
tickets/04-location-gate.md) and checks every quoted string in the Kotlin and the Swift, so an edit to
the ticket fails both platforms until they follow. Apostrophes compare as one character: the ticket's
copy block quotes them straight and its acceptance criteria ask for curly ones (U+2019), which is what
both apps render.

Then the rules a reading can check:
  · no gate string names a Settings path (no `›` in the gate's copy) -- "the button routes instead"
  · ONE location screen and ONE radar: the gate is Profile 12's screen with a context, never a fork
  · the gate's top bar has no filter button; D's has no chip
  · `screen_id: search_location_gate` and its §11 name on both; `location_gate_shown` built on both
  · the events the ticket forbids on the gate are never sent by it
"""
import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(MOBILE))
TICKET = os.path.join(ROOT, "design_handoff_showup", "search", "tickets", "04-location-gate.md")
KT = os.path.join(MOBILE, "android-preview-project", "app", "src", "main", "java", "com", "showup")
SW = os.path.join(MOBILE, "ios-app", "ShowUpWelcome")

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
    text = text.replace("\\u{2019}", "’").replace("\\u{2014}", "—")
    text = text.replace("\\u2019", "’").replace("\\u2014", "—")
    return text.replace("’", "'")


def joined_literals(source):
    """The source with literals split over lines joined: `"a " +\\n "b"` reads as `"a b"`."""
    return re.sub(r'"\s*\+\s*"', "", re.sub(r'"\s*\+\s*\n\s*"', "", source))


kt_screen = read(os.path.join(KT, "profile", "ProfileLocationScreen.kt"))
sw_screen = read(os.path.join(SW, "ProfileLocation.swift"))
kt_model = read(os.path.join(KT, "search", "LocationGateViewModel.kt"))
sw_model = read(os.path.join(SW, "LocationGateModel.swift"))
kt_analytics = read(os.path.join(KT, "profile", "ProfileAnalytics.kt"))
sw_analytics = read(os.path.join(SW, "ProfileAnalytics.swift"))
kt = norm(joined_literals(kt_screen))
sw = norm(joined_literals(sw_screen))

# ── the copy, quoted from the ticket ───────────────────────────────────────────────────────────
ticket = read(TICKET)
section = ticket.split("## Copy", 1)[1].split("\n## ", 1)[0]
quoted = [q for q in re.findall(r"`([^`]+)`", section)]
# Headlines are split around their emphasised word in code; check them as the runs the apps hold.
emphasis = dict(re.findall(r"Headline: `([^`]+)` — em on `([^`]+)`", section))
strings = []
for q in quoted:
    if q in emphasis:
        lead, tail = q.split(emphasis[q], 1)
        strings += [lead, emphasis[q], tail]
    elif q not in emphasis.values():
        strings.append(q)
check("the ticket's copy section was read (%d strings)" % len(strings), len(strings) >= 12)
for s in strings:
    n = norm(s)
    check("ticket copy on Android: %r" % s, '"%s"' % n in kt or n in kt)
    check("ticket copy on iOS: %r" % s, '"%s"' % n in sw or n in sw)

# ── no Settings path on the gate; identical on both ────────────────────────────────────────────
kt_app = kt.split("object App {", 1)[1].split("\n    }", 1)[0]
sw_app = sw.split("enum App {", 1)[1].split("\n    }", 1)[0]
check("no gate string names a Settings path (Kotlin)", "›" not in kt_app)
check("no gate string names a Settings path (Swift)", "›" not in sw_app)
lits = lambda src: sorted(re.findall(r'"([^"]+)"', src))
check("the gate's strings are identical on iOS and Android", lits(kt_app) == lits(sw_app))

# ── one screen, one radar, the right chrome ────────────────────────────────────────────────────
check("one location screen: the gate is Profile 12's with a context (Kotlin)",
      "LocationScreenContext.App" in kt_screen and "fun LocationGateScreen" in kt_screen)
check("one location screen: the gate is Profile 12's with a context (Swift)",
      "case app(LocationAppContext)" in sw_screen and "struct LocationGateView" in sw_screen)
check("one radar component on each platform",
      kt_screen.count("fun LocationRadar(") == 1 and sw_screen.count("struct LocationRadar") == 1
      and "live: Boolean" in kt_screen and "var live: Bool" in sw_screen)
check("no filter button on the gate, no chip in D (Kotlin)",
      "filter = false, chip = !overProfile" in kt_screen)
check("no filter button on the gate, no chip in D (Swift)",
      "filter: false, chip: !overProfile" in sw_screen)
check("headline 30 / 1.12 / -0.015em, centred, balanced (Kotlin)",
      "fontSize = 30.sp" in kt_screen and "30f * 1.12f" in kt_screen and "TextAlign.Center" in kt_screen)
check("headline 30 / 1.12 / -0.015em, centred, balanced (Swift)",
      "fontSize: 30, lineHeightMultiple: 1.12, trackingEm: -0.015" in sw_screen and "centered: true" in sw_screen)
check("D's radar 140 and the gate's 170 on both", "170.dp, 140.dp" in kt_screen and "? 170 : 130" in sw_screen
      and "? 140 : 110" in sw_screen)

# ── tracking ───────────────────────────────────────────────────────────────────────────────────
check("§11 search_location_gate on both",
      'SearchLocationGate("search_location_gate", "Search - Location gate")' in kt_analytics
      and 'case searchLocationGate = "search_location_gate"' in sw_analytics
      and '"Search - Location gate"' in sw_analytics)
check("location_gate_shown on both", '"location_gate_shown"' in kt_analytics and '"location_gate_shown"' in sw_analytics)
check("not_determined reported by the gate on both",
      'NotDetermined("not_determined")' in read(os.path.join(KT, "profile", "LocationAccess.kt"))
      and 'case notDetermined = "not_determined"' in read(os.path.join(SW, "LocationAccess.swift")))
for model, label in ((kt_model, "Kotlin"), (sw_model, "Swift")):
    code = "\n".join(l for l in model.splitlines() if not l.strip().startswith(("//", "*", "/*", "///")))
    check("the gate sends screen_id on its permission events (%s)" % label,
          code.count("SearchLocationGate") + code.count(".searchLocationGate") >= 4)
    for forbidden in ("permissionPrompted", "permissionDeniedRecoveryShown", "locationSkipped", "FlowPosition"):
        check("the gate never sends %s (%s)" % (forbidden, label), forbidden not in code)

print("location gate conformance: %d checks" % count)
if failures:
    print("FAILED %d:" % len(failures))
    for f in failures:
        print("  x " + f)
    sys.exit(1)
print("all passed")
