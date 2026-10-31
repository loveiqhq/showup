"""
verify-filters-v2.py -- Check-In 05b Filters v2 (SHOWUP-189) against its ticket, on both apps and the server.

Reads the ticket's `## Copy -- final strings` section AT RUN TIME (design_handoff_showup/checkin/
tickets/05b-filters-v2.md) and checks every quoted string in `CheckInCopy` on both platforms, so an edit
to the ticket fails both until they follow.

Then the rules a reading can check:
  · ONE GenderCheckPill and ONE DealbreakerRow per platform; the switch is Profile 10's ConsentSwitch,
    never a second one; the Filters step no longer draws LifePill
  · the square's values (16, r 5, 1.5 at .32, a 12 tick at stroke 3) and the row's (mt 12, gap 12)
  · `Pick all that apply` muted (500 / 12) and kept out of the reading order -- the group says it
  · the rows hang off the remote flag: `if dealbreakers` twice, under Age and Distance, none under Gender
  · `filters_changed` in the extended order on both; both booleans sent on every accepted Continue,
    false with the flag off; no invented toggle event
  · the server: the two columns default false, the flag defaults off, absent fields keep the stored value
  · the registry: check_in_filters_changed carries both booleans and is marked implemented
"""
import io
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(MOBILE))
TICKET = os.path.join(ROOT, "design_handoff_showup", "checkin", "tickets", "05b-filters-v2.md")
EVENTS = os.path.join(ROOT, "design_handoff_showup", "tracking", "events.json")
KT = os.path.join(MOBILE, "android-preview-project", "app", "src", "main", "java", "com", "showup")
SW = os.path.join(MOBILE, "ios-app", "ShowUpWelcome")
SERVER = os.path.join(ROOT, "src")

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


def code_only(source):
    """The source without its comment lines, so a rule quoted in a comment cannot satisfy a check."""
    return "\n".join(l for l in source.splitlines() if not l.strip().startswith(("//", "*", "/*", "///")))


kt_rules = read(os.path.join(KT, "checkin", "CheckInRules.kt"))
sw_rules = read(os.path.join(SW, "CheckInRules.swift"))
kt_screen = read(os.path.join(KT, "checkin", "CheckInFiltersScreen.kt"))
sw_screen = read(os.path.join(SW, "CheckInFilters.swift"))
kt_analytics = read(os.path.join(KT, "checkin", "CheckInAnalytics.kt"))
sw_analytics = read(os.path.join(SW, "CheckInAnalytics.swift"))
kt_screen_code = code_only(kt_screen)
sw_screen_code = code_only(sw_screen)

# ── the copy, quoted from the ticket ───────────────────────────────────────────────────────────
ticket = read(TICKET)
section = ticket.split("## Copy", 1)[1].split("\n## ", 1)[0]
strings = re.findall(r"`([^`]+)`", section)
check("the ticket's copy section was read (%d strings)" % len(strings), len(strings) >= 8)
for s in strings:
    check("ticket copy on Android: %r" % s, '"%s"' % s in kt_rules)
    check("ticket copy on iOS: %r" % s, '"%s"' % s in sw_rules)
lits = lambda src, start, end: sorted(re.findall(r'"([^"]+)"', src.split(start, 1)[1].split(end, 1)[0]))
check("the 05b strings are identical on iOS and Android",
      lits(kt_rules, "FILTER_GENDER_HINT", "DEALBREAKER_DISTANCE_LABEL =") + lits(kt_rules, "DEALBREAKER_DISTANCE_LABEL =", "\n")
      == lits(sw_rules, "filterGenderHint", "dealbreakerDistanceLabel =") + lits(sw_rules, "dealbreakerDistanceLabel =", "\n"))

# ── one of each component; the switch consumed ─────────────────────────────────────────────────
kt_all = "\n".join(read(os.path.join(d, f)) for d, _, fs in os.walk(KT) for f in fs if f.endswith(".kt"))
sw_all = "\n".join(read(os.path.join(SW, f)) for f in os.listdir(SW) if f.endswith(".swift"))
check("one GenderCheckPill on Android", len(re.findall(r"fun GenderCheckPill\(", kt_all)) == 1)
check("one GenderCheckPill on iOS", len(re.findall(r"struct GenderCheckPill\b", sw_all)) == 1)
check("one DealbreakerRow on Android", len(re.findall(r"fun DealbreakerRow\(", kt_all)) == 1)
check("one DealbreakerRow on iOS", len(re.findall(r"struct DealbreakerRow\b", sw_all)) == 1)
check("one switch component on Android (Profile 10's ConsentSwitch)",
      len(re.findall(r"fun ConsentSwitch\(", kt_all)) == 1 and "ConsentSwitch(on = on" in kt_screen_code)
check("one switch component on iOS (Profile 10's ConsentSwitch)",
      len(re.findall(r"struct ConsentSwitch\b", sw_all)) == 1 and "ConsentSwitch(on: on" in sw_screen_code)
check("no platform Switch / Toggle drawn on Filters",
      not re.search(r"\bSwitch\(", kt_screen_code.replace("ConsentSwitch(", ""))
      and not re.search(r"\bToggle\(", sw_screen_code))
check("the Filters step no longer draws LifePill (Android)", "LifePill(" not in kt_screen_code)
check("the Filters step no longer draws LifePill (iOS)", "LifePill(" not in sw_screen_code)

# ── the square and the row ─────────────────────────────────────────────────────────────────────
check("square 16, r 5, 1.5 at .32, 12 tick at stroke 3 (Android)",
      "CHECK_SIDE = 16.dp" in kt_screen and "RoundedCornerShape(5.dp)" in kt_screen
      and ".border(1.5.dp, squareStroke, square)" in kt_screen and "Fg.copy(alpha = 0.32f)" in kt_screen
      and "Icon(BrandIcon.Check, 12.dp, tint = Purple, strokeWidth = 3f)" in kt_screen)
check("square 16, r 5, 1.5 at .32, 12 tick at stroke 3 (iOS)",
      ".frame(width: 16, height: 16)" in sw_screen and "cornerRadius: 5" in sw_screen
      and "lineWidth: 1.5" in sw_screen and "opacity(selected ? 0 : 0.32)" in sw_screen
      and "BrandIconView(icon: .check, size: 12, stroke: 3, tint: .liqPurple)" in sw_screen)
check("a checkbox each, in a group named for what it asks (Android)",
      "Role.Checkbox" in kt_screen_code and "CheckInCopy.FILTER_GENDER_GROUP" in kt_screen_code)
check("a checkbox each, in a group named for what it asks (iOS)",
      ".isToggle" in sw_screen_code and "CheckInCopy.filterGenderGroup" in sw_screen_code)
check("DealbreakerRow: margin-top 12, gap 12 (Android)",
      ".padding(top = 12.dp)" in kt_screen and "Arrangement.spacedBy(12.dp)" in kt_screen)
check("DealbreakerRow: margin-top 12, gap 12 (iOS)",
      ".padding(.top, 12)" in sw_screen and "HStack(alignment: .center, spacing: 12)" in sw_screen)
check("the hint muted 500 / 12, out of the reading order (Android)",
      "fontSize = if (hintMuted) 12.sp else 13.sp" in kt_screen and "if (hintMuted) Muted else Purple" in kt_screen
      and "FontWeight.Medium" in kt_screen and "clearAndSetSemantics" in kt_screen)
check("the hint muted 500 / 12, out of the reading order (iOS)",
      "F.manrope(hintMuted ? 12 : 13, hintMuted ? .medium : .semibold)" in sw_screen
      and "hintMuted ? .liqMuted : .liqPurple" in sw_screen and ".accessibilityHidden(hintMuted)" in sw_screen)

# ── the remote flag ────────────────────────────────────────────────────────────────────────────
for code, flag, label in ((kt_screen_code, "if (dealbreakers) {", "Android"), (sw_screen_code, "if dealbreakers {", "iOS")):
    calls = len(re.findall(r"(?<!fun )(?<!struct )\bDealbreakerRow\(", code))
    check("two DealbreakerRows, each behind the flag (%s)" % label, code.count(flag) == 2 and calls == 2)
    gender = code.split("FILTER_GENDER_HINT" if label == "Android" else "filterGenderHint", 1)[1]
    gender = gender.split("FILTER_AGE" if label == "Android" else "filterAge", 1)[0]
    check("no DealbreakerRow under Gender (%s)" % label, "DealbreakerRow" not in gender)
    age, distance = code.split("FILTER_DISTANCE" if label == "Android" else "filterDistance", 1)
    check("one DealbreakerRow under Age and one under Distance (%s)" % label,
          "DEALBREAKER_ON_AGE" in age.replace("dealbreakerOnAge", "DEALBREAKER_ON_AGE")
          and ("DEALBREAKER_ON_DISTANCE" in distance or "dealbreakerOnDistance" in distance))

# ── tracking ───────────────────────────────────────────────────────────────────────────────────
order = ["meet_gender", "age_range", "age_dealbreaker", "distance_km", "distance_dealbreaker"]
for rules, label in ((kt_rules, "Android"), (sw_rules, "iOS")):
    body = code_only(rules.split("filtersChanged(", 1)[1].split("\n}", 1)[0])
    found = re.findall(r'"(meet_gender|age_range|age_dealbreaker|distance_km|distance_dealbreaker)"', body)
    check("filters_changed in the extended order (%s)" % label, found == order)
check("both booleans on every accepted Continue, false with the flag off (Android)",
      'put("age_dealbreaker", dealbreakers && now.ageDealbreaker)' in kt_analytics
      and 'put("distance_dealbreaker", dealbreakers && now.distanceDealbreaker)' in kt_analytics)
check("both booleans on every accepted Continue, false with the flag off (iOS)",
      '"age_dealbreaker": dealbreakers && now.ageDealbreaker' in sw_analytics
      and '"distance_dealbreaker": dealbreakers && now.distanceDealbreaker' in sw_analytics)
# As a string literal in code: a comment naming the event it must not send is not sending it.
for name in ("dealbreaker_toggled", "age_dealbreaker_changed", "distance_dealbreaker_changed", "dealbreaker_changed"):
    check("no invented event %s" % name, '"%s"' % name not in code_only(kt_all) + code_only(sw_all))

# ── the server ─────────────────────────────────────────────────────────────────────────────────
migration = [f for f in os.listdir(os.path.join(SERVER, "database", "migrations")) if "FilterDealbreakers" in f]
check("one migration adds the two columns", len(migration) == 1)
if migration:
    m = read(os.path.join(SERVER, "database", "migrations", migration[0]))
    check("both columns boolean NOT NULL DEFAULT false (existing records migrate to false)",
          m.count("boolean NOT NULL DEFAULT false") == 2 and "age_dealbreaker" in m and "distance_dealbreaker" in m)
check("the remote flag defaults off",
      "FILTERS_DEALBREAKERS: Joi.boolean().default(false)" in read(os.path.join(SERVER, "config", "env.validation.ts"))
      and "process.env.FILTERS_DEALBREAKERS === 'true'" in read(os.path.join(SERVER, "config", "configuration.ts")))
service = read(os.path.join(SERVER, "modules", "check-ins", "check-in-flow.service.ts"))
check("an absent switch keeps the stored value, never resets it",
      "dto.ageDealbreaker ?? before?.ageDealbreaker ?? false" in service
      and "dto.distanceDealbreaker ?? before?.distanceDealbreaker ?? false" in service)

# ── the registry ───────────────────────────────────────────────────────────────────────────────
events = json.loads(read(EVENTS))
entry = next((e for fam in events["families"] for e in fam["events"] if e["name"] == "check_in_filters_changed"), None)
check("check_in_filters_changed is in the registry", entry is not None)
if entry:
    names = [p["name"] for p in entry["payload"]]
    check("its payload carries both booleans", "age_dealbreaker" in names and "distance_dealbreaker" in names)
    check("it is marked implemented", entry.get("implemented") is True)
    check("its sensitivity class stays 0", entry.get("sensitivity_class") == 0)

print("filters v2 conformance: %d checks" % count)
if failures:
    print("FAILED %d:" % len(failures))
    for f in failures:
        print("  x " + f)
    sys.exit(1)
print("all passed")
