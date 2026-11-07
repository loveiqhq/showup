"""
verify-date.py -- the Date screen (SHOWUP-193) against its ticket, on both apps.

Reads the ticket's `## Copy -- final strings` section AT RUN TIME (design_handoff_showup/date/tickets/
01-date-screen.md) and checks every quoted string in the Kotlin and the Swift, so an edit to the ticket
fails both platforms until they follow. A string with placeholders (`{name}`, `{time}` ...) is checked
as its literal runs, because each app fills them its own way (`%s` in Kotlin, interpolation in Swift).
Apostrophes compare as one character.

Then the rules a reading can check:
  - Order Ride is not drawn: no `Order Ride` and no `ride` action on either app
  - the tabs a date locks are Search, Likes and Instant on both; Settings stays open
  - `screen_id: date_screen` and its section 11 name on both; `date_screen_viewed` and
    `date_quick_action_tapped` built on both, with the section 38 actions
  - the strings each app holds for the screen are the same set

And it proves it can fail: the copy check is run once more against an Android source with one ticket
string removed, and that run must report it.
"""
import glob
import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(MOBILE))
TICKET = os.path.join(ROOT, "design_handoff_showup", "date", "tickets", "01-date-screen.md")
KT = os.path.join(MOBILE, "android-preview-project", "app", "src", "main", "java", "com", "showup")
SW = os.path.join(MOBILE, "ios-app", "ShowUpWelcome")

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

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
    for esc, ch in (("\\u{2019}", "’"), ("\\u{2014}", "—"), ("\\u2019", "’"), ("\\u2014", "—")):
        text = text.replace(esc, ch)
    return text.replace("’", "'")


def joined_literals(source):
    """The source with literals split over lines joined: `"a " +\\n "b"` reads as `"a b"`."""
    return re.sub(r'"\s*\+\s*"', "", re.sub(r'"\s*\+\s*\n\s*"', "", source))


# The chat's vocabulary is the Date screen's too: the unread card's badge and the locked card's tiles
# take their labels from it (SHOWUP-194 builds it once, for Dates 01, 02, 03 and 05).
kt_files = sorted(glob.glob(os.path.join(KT, "date", "*.kt")) + [os.path.join(KT, "chat", "ChatReasons.kt")])
sw_files = sorted(
    glob.glob(os.path.join(SW, "Date*.swift"))
    + [p for p in (os.path.join(SW, n) for n in ("CoordinateModule.swift", "CopyAddress.swift")) if os.path.exists(p)]
    + [os.path.join(SW, "ChatReasons.swift")]
)
check("the Android date package was found", len(kt_files) >= 8)
check("the iOS date files were found", len(sw_files) >= 8)
kt = norm(joined_literals("\n".join(read(p) for p in kt_files)))
sw = norm(joined_literals("\n".join(read(p) for p in sw_files)))

# ── the copy, quoted from the ticket ───────────────────────────────────────────────────────────
ticket = read(TICKET)
section = ticket.split("## Copy", 1)[1].split("\n## ", 1)[0]
quoted = re.findall(r"`([^`]+)`", section)
# Not copy: the component the intent card names, and the hidden action (checked absent below). And the
# locked card's change line: Date 05's, not rendered until Date 05 ships (AC A, rewritten 5 Nov 2026).
NOT_COPY = {"ShowUpCount", "Order Ride", "Request to change the location or meet time."}


def runs(s):
    """The literal runs of a ticket string around its `{placeholders}`, the ones worth matching."""
    parts = re.split(r"\{[^}]*\}", norm(s))
    return [p for p in parts if len(p.strip(" ·,.")) >= 2]


strings = [q for q in quoted if q not in NOT_COPY and runs(q)]
check("the ticket's copy section was read (%d strings)" % len(strings), len(strings) >= 30)


def missing_from(source, label):
    out = []
    for s in strings:
        for r in runs(s):
            if r not in source:
                out.append("ticket copy on %s: %r (run %r)" % (label, s, r))
    return out


for name in missing_from(kt, "Android") + missing_from(sw, "iOS"):
    check(name, False)
count += len(strings) * 2

# ── it can fail: one ticket string taken out of the Android source must be reported ────────────
victim = "Sort last-minute details before you meet"
check("the mutation check has a string to remove", victim in kt)
mutated = kt.replace(victim, "")
check("the copy check reports a string removed from the source (mutation)",
      any(victim in m for m in missing_from(mutated, "Android")))

# ── Order Ride is not drawn ────────────────────────────────────────────────────────────────────
check("no Order Ride on Android", '"Order Ride"' not in kt)
check("no Order Ride on iOS", '"Order Ride"' not in sw)
check("no ride quick action on Android", '"ride"' not in kt)
check("no ride quick action on iOS", '"ride"' not in sw)

# ── the lock: Search, Likes, Instant; Settings open ────────────────────────────────────────────
check("Android locks Search, Likes and Instant",
      "DATE_LOCKED_TABS = setOf(Tab.Search, Tab.Likes, Tab.Instant)" in kt)
check("iOS locks Search, Likes and Instant", re.search(r"dateLockedTabs[^=]*=\s*\[\.search, \.likes, \.instant\]", sw) is not None)

# ── tracking ───────────────────────────────────────────────────────────────────────────────────
kt_analytics = read(os.path.join(KT, "profile", "ProfileAnalytics.kt"))
sw_analytics = read(os.path.join(SW, "ProfileAnalytics.swift"))
check("section 11 date_screen on Android", 'DateScreen("date_screen", "Date - Date screen")' in kt_analytics)
check("section 11 date_screen on iOS",
      'case dateScreen = "date_screen"' in sw_analytics and '"Date - Date screen"' in sw_analytics)
for event in ("date_screen_viewed", "date_quick_action_tapped"):
    check("%s built on Android" % event, '"%s"' % event in kt)
    check("%s built on iOS" % event, '"%s"' % event in sw)
for action in ("directions", "share", "calendar", "copy_address"):
    check("quick action %s on Android" % action, '"%s"' % action in kt)
    check("quick action %s on iOS" % action, '"%s"' % action in sw)

# ── the same strings on both ───────────────────────────────────────────────────────────────────
kt_copy = norm(read(os.path.join(KT, "date", "DateCopy.kt")))
sw_copy_path = os.path.join(SW, "DateCopy.swift")
sw_copy = norm(read(sw_copy_path)) if os.path.exists(sw_copy_path) else ""


def without_comments(src):
    """The code alone: `/* ... */` blocks and `//` line comments taken out, so a quoted word in a
    comment is never counted as a string the screen draws."""
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    return "\n".join(re.sub(r"^\s*//.*$", "", l) for l in src.splitlines())


def literal_words(src):
    """Every string literal, its placeholders (`%s`, `%d`, `\\(x)`) removed: the words alone."""
    lits = re.findall(r'"((?:[^"\\]|\\.)*)"', without_comments(src))
    return sorted({re.sub(r"%[sd@]|\\\([^)]*\)", "", l).strip() for l in lits if len(l) > 1})


kt_words = literal_words(kt_copy)
sw_words = literal_words(sw_copy)
only_kt = [w for w in kt_words if w not in sw_words]
only_sw = [w for w in sw_words if w not in kt_words]
check("DateCopy holds the same strings on both (only Android: %s; only iOS: %s)" % (only_kt[:4], only_sw[:4]),
      not only_kt and not only_sw)

print("date screen conformance: %d checks" % count)
if failures:
    print("FAILED %d:" % len(failures))
    for f in failures:
        print("  x " + f)
    sys.exit(1)
print("all passed")
