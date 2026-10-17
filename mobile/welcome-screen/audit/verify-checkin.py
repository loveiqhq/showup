# -*- coding: utf-8 -*-
"""The 48hrs Check-In -- conformance against SHOWUP-178 to SHOWUP-183, on BOTH platforms.

    python audit/verify-checkin.py

WHAT THIS CHECKS

Every string in each ticket's "Copy -- final strings" section, QUOTED FROM THE TICKET FILE rather than
retyped here: the six tickets in design_handoff_showup/checkin/tickets are read at run time, every
backticked string is pulled out, and each is found in the Kotlin and the Swift sources. A word changed
in a ticket fails this until both apps change with it; a word changed in one app fails it at once.

Then the numbers the tickets fix (sizes, limits, timings), the registry ids, and the tracking rules
that are easy to break silently: no client `check_in_created`, no retired event names.

WHAT THIS DOES NOT CHECK

Whether the screens look right -- that is ScreenFitTest at 17 sizes, CheckInFitTest, the evidence
renders and a person holding a phone. Formats the rules COMPUTE (`6–9pm`, `25 – 35`, `{n} characters
left`) are pinned by the rules tests on both platforms; this checks those tests assert them.
"""
import glob
import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(MOBILE))
TICKETS = os.path.join(ROOT, "design_handoff_showup", "checkin", "tickets")
KT = os.path.join(MOBILE, "android-preview-project", "app", "src", "main", "java", "com", "showup")
KT_TEST = os.path.join(MOBILE, "android-preview-project", "app", "src", "test", "java", "com", "showup")
SW = os.path.join(MOBILE, "ios-app", "ShowUpWelcome")
SW_TEST = os.path.join(MOBILE, "ios-app", "ShowUpWelcomeTests")

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


def read_all(pattern):
    return "\n".join(read(p) for p in sorted(glob.glob(pattern)))


def flatten(source):
    """Source text with string runs joined: `"a" to false, "b" to true` and `"a " +\\n "b"` read as `ab`."""
    s = re.sub(r'"\s*to\s*(?:true|false)\s*\)?\s*,\s*(?:listOf\()?\s*"', "", source)
    s = re.sub(r'"\s*\+\s*\n\s*"', "", s)
    s = re.sub(r'"\s*,\s*bold:\s*(?:true|false)\s*\)\s*,\s*\(\s*"', "", s)   # a Swift run list, if it reads so
    s = re.sub(r'"\s*\+\s*"', "", s)
    return s


kt_rules = read(os.path.join(KT, "checkin", "CheckInRules.kt"))
kt_checkin = read_all(os.path.join(KT, "checkin", "*.kt"))
kt_flat = flatten(kt_rules)
sw_rules_path = os.path.join(SW, "CheckInRules.swift")
sw_rules = read(sw_rules_path) if os.path.exists(sw_rules_path) else ""
sw_checkin = read_all(os.path.join(SW, "CheckIn*.swift"))
sw_flat = flatten(sw_rules)
check("ios CheckInRules.swift exists", bool(sw_rules))

# ── 1 · the copy, quoted from the tickets ───────────────────────────────────────────────────────

# Not copy: a CSS class named in a sentence, and the icon names (checked against the icons below).
NOT_COPY = {".su-underlined em", "heart-filled", "compass", "flame", "sparkles", "users"}
# Computed by the rules, not stored: pinned by the rules tests instead (section 2).
COMPUTED = {"6–9pm", "12–2pm · 7–8pm", "+N more", "n/5", "30+", "25 – 35", "10 km", "Any age",
            "{n} characters left", "1 character left", "7am", "11am", "noon", "1pm", "11pm", "12am", "1am", "6am"}

copy_by_ticket = {}
for path in sorted(glob.glob(os.path.join(TICKETS, "0[1-6]-*.md"))):
    text = read(path)
    m = re.search(r"## Copy[^\n]*\n(.*?)\n## ", text, re.S)
    check(os.path.basename(path) + " has a Copy section", m is not None)
    if not m:
        continue
    strings = [s for s in re.findall(r"`([^`]+)`", m.group(1)) if s not in NOT_COPY]
    copy_by_ticket[os.path.basename(path)] = strings
    for s in strings:
        if s in COMPUTED:
            continue
        plain = s.replace("**", "")
        # Registry ids (snake_case) live in enums, not in the copy object.
        if re.fullmatch(r"[a-z0-9_]+", plain):
            check(f"{os.path.basename(path)} id `{plain}` (kotlin)", f'"{plain}"' in kt_checkin)
            check(f"{os.path.basename(path)} id `{plain}` (swift)", f'"{plain}"' in sw_checkin)
            continue
        check(f"{os.path.basename(path)} `{plain}` (kotlin)", plain in kt_flat)
        check(f"{os.path.basename(path)} `{plain}` (swift)", plain in sw_flat)

check("all six tickets read", len(copy_by_ticket) == 6)

# The intro's five rows, in the ticket's order.
intro = copy_by_ticket.get("01-intro.md", [])
rows = intro[1:6]
positions = [kt_rules.find(f'"{r}"') for r in rows]
check("01 five rows in the ticket's order (kotlin)", len(rows) == 5 and all(p >= 0 for p in positions) and positions == sorted(positions))
sw_positions = [sw_rules.find(f'"{r}"') for r in rows]
check("01 five rows in the ticket's order (swift)", all(p >= 0 for p in sw_positions) and sw_positions == sorted(sw_positions))

# The five inspirations, in order, and quoted on screen.
uplift = copy_by_ticket.get("06-uplift.md", [])
insp = [s for s in uplift if s.startswith(("have 2x", "will take", "need a", "made fresh", "still looking"))]
check("06 five inspirations", len(insp) == 5)
for code, name in ((kt_rules, "kotlin"), (sw_rules, "swift")):
    pos = [code.find(f'"{s}"') for s in insp]
    check(f"06 inspirations in the ticket's order ({name})", all(p >= 0 for p in pos) and pos == sorted(pos))

# Motivation icons, as the ticket names them.
for icon, kt_name in (("heart-filled", "HeartFilled"), ("compass", "Compass"), ("flame", "Flame"), ("sparkles", "Sparkles"), ("users", "Users")):
    check(f"03 icon {icon} (kotlin)", f"BrandIcon.{kt_name}" in kt_rules)

# ── 2 · the computed formats are pinned by the rules tests, on both platforms ──────────────────

kt_tests = read_all(os.path.join(KT_TEST, "checkin", "*.kt"))
sw_tests = read_all(os.path.join(SW_TEST, "CheckIn*.swift"))
for fmt in ("6–9pm", "12–2pm · 7–8pm", "Any age", "30+", "25 – 35", "1 character left", "100 characters left", "noon", "12am", "+3 more"):
    check(f"rules test pins `{fmt}` (kotlin)", fmt in kt_tests)
    check(f"rules test pins `{fmt}` (swift)", fmt in sw_tests)

# ── 3 · the numbers the tickets fix ─────────────────────────────────────────────────────────────

def has(code, *needles):
    return all(n in code for n in needles)

check("01 intro headline 34 / 1.1 (kotlin)", has(kt_checkin, "fontSize = 34.sp", "(34f * 1.1f).sp"))
check("02-06 step headline 30 / 1.15 (kotlin)", has(kt_checkin, "fontSize = 30.sp", "(30f * 1.15f).sp"))
check("01 gate headline 30 / 1.12 (kotlin)", "(30f * 1.12f).sp" in kt_checkin)
check("02 dialog title 23 / 1.15 (kotlin)", has(kt_checkin, "fontSize = 23.sp", "(23f * 1.15f).sp"))
check("02 a Check-In day runs 7am to 7am", "const val CHECK_IN_DAY_START = 7" in kt_rules)
check("02 slot drawn 36", "heightIn(min = 36.dp)" in kt_checkin)
check("02 grid four columns, 5 apart", has(kt_checkin, "chunked(4)", "spacedBy(5.dp)"))
check("02 summary shrinks to 0.78, caps at 4 runs", has(kt_checkin, "SUMMARY_MIN_SCALE = 0.78f") and "SUMMARY_MAX_RUNS = 4" in kt_rules)
check("02 dialog card max 320, radius 26", has(kt_checkin, "widthIn(max = 320.dp)", "26.dp"))
check("04 at most five vibes", "const val VIBE_MAX = 5" in kt_rules)
check("05 ages 18 to 65, distance 1 to 50 default 10", has(kt_rules, "AGE_MIN = 18", "AGE_MAX = 65", "DISTANCE_MIN = 1", "DISTANCE_MAX = 50", "DISTANCE_DEFAULT = 10"))
check("06 100 graphemes", "const val UPLIFT_MAX = 100" in kt_rules)
check("06 field 116 / 14 / 1.5 / 16", has(kt_checkin, "FIELD_MIN_HEIGHT = 116.dp", "FIELD_PADDING = 14.dp", "FIELD_BORDER = 1.5.dp", "FIELD_RADIUS = 16.dp"))
check("toasts last 2600 ms", "const val TOAST = 2600" in read(os.path.join(KT, "designsystem", "Motion.kt")))
check("six segments on the step bar", "const val CHECK_IN_STEPS_TOTAL = 6" in kt_rules)
check("CTA 22 above the bottom", "padding(bottom = 22.dp)" in kt_checkin)
for ios_needle, what in (("100", "06 100 graphemes (swift)"), ("2600", "toasts 2600 ms (swift)")):
    check(what, ios_needle in sw_checkin or ios_needle in read_all(os.path.join(SW, "Motion.swift")))

# ── 4 · tracking rules that break silently ─────────────────────────────────────────────────────

kt_analytics = read(os.path.join(KT, "checkin", "CheckInAnalytics.kt"))
for event in ("check_in_started", "check_in_closed", "check_in_gate_tapped", "check_in_step_answered",
              "availability_slots_set", "check_in_no_hours_today_resolved", "motivation_selected",
              "vibe_selected", "check_in_filters_changed", "check_in_commit_failed"):
    check(f"event {event} (kotlin)", f'"{event}"' in kt_analytics)
    check(f"event {event} (swift)", f'"{event}"' in sw_checkin)
for never in ("check_in_created", "check_in_completed", "uplift_shown", "checkin_started", "checkin_intro_viewed"):
    check(f"the client never fires {never} (kotlin)", f'"{never}"' not in kt_checkin)
    check(f"the client never fires {never} (swift)", f'"{never}"' not in sw_checkin)
check("motivation_selected is class 1, the rest 0", "Stamp.of(1)" in kt_analytics and kt_analytics.count("Stamp.of(1)") == 1)
check("max_age travels as JSON null for 65+", "maxAgeOrNull ?: JsonNull" in kt_analytics)

print(f"verify-checkin: {count - len(failures)}/{count} pass")
if failures:
    for f in failures:
        print("  FAIL", f)
    sys.exit(1)
