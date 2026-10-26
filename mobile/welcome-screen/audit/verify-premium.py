# -*- coding: utf-8 -*-
"""Date search 02 and 03 -- conformance against SHOWUP-185 and SHOWUP-186, on BOTH platforms.

    python audit/verify-premium.py

WHAT THIS CHECKS

Every string in each ticket's "Copy -- final strings" section, QUOTED FROM THE TICKET FILES rather
than retyped here: design_handoff_showup/search/tickets/02-grid.md and 03-likes-received.md are read
at run time, every backticked string in those sections is pulled out, and each of its literal runs --
the text between `{placeholders}` -- is found in the Kotlin and the Swift sources. A word changed in
a ticket fails this until both apps change with it; a word changed in one app fails it at once.

Strings assembled from runs (a styled emphasis, a name between two runs) are checked as their runs;
formats the rules COMPUTE are pinned by PremiumRulesTest / PremiumRulesTests, and this checks those
tests assert them. Then the tracking rules both tickets' first comments make binding: the event and
screen names are the registry's, the client fires no server event, `grid_card` stays retired, and the
two `like_surface` values a decline can carry are both there. Then the numbers the tickets fix.

WHAT THIS DOES NOT CHECK

Whether the screens look right -- PremiumFitTest on 23 phones at three type sizes, the evidence frames
and a person holding a phone.
"""
import glob
import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(MOBILE))
TICKETS = os.path.join(ROOT, "design_handoff_showup", "search", "tickets")
KT = os.path.join(MOBILE, "android-preview-project", "app", "src", "main", "java", "com", "showup")
KT_TEST = os.path.join(MOBILE, "android-preview-project", "app", "src", "test", "java", "com", "showup")
SW = os.path.join(MOBILE, "ios-app", "ShowUpWelcome")
SW_TEST = os.path.join(MOBILE, "ios-app", "ShowUpWelcomeTests")

failures = []
notes = []
count = 0


def check(name, ok):
    global count
    count += 1
    if not ok:
        failures.append(name)


def read(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()


def read_all(paths):
    return "\n".join(read(p) for p in sorted(paths) if os.path.exists(p))


def flatten(source):
    """Source text with a literal split over lines joined: `"a " +\\n "b"` reads as `"a b"`."""
    return re.sub(r'"\s*\+\s*"', "", source)


kt = read_all(
    glob.glob(os.path.join(KT, "search", "*.kt"))
    + [os.path.join(KT, "designsystem", f) for f in ("ShowUpCount.kt", "TabBar.kt", "PremiumTag.kt")]
)
sw = read_all(
    glob.glob(os.path.join(SW, "Search*.swift")) + glob.glob(os.path.join(SW, "Premium*.swift"))
    + glob.glob(os.path.join(SW, "Likes*.swift"))
    + [os.path.join(SW, f) for f in ("ShowUpCount.swift", "TabBar.swift", "CardFlyOut.swift")]
)
kt_tests = read_all(glob.glob(os.path.join(KT_TEST, "search", "*.kt")))
sw_tests = read_all(glob.glob(os.path.join(SW_TEST, "Search*.swift")) + glob.glob(os.path.join(SW_TEST, "Premium*.swift")))
check("Kotlin Premium sources found", "PremiumCopy" in kt)
check("Swift Premium sources found", "PremiumCopy" in sw)
kt, sw = flatten(kt), flatten(sw)

# Not copy: the placeholders each section names in its first sentence.
NOT_COPY = {"{name}", "{day}", "today", "tomorrow"}
# Computed by the rules from runs, not stored whole: pinned by the rules tests instead (below).
COMPUTED = {
    "{name}, {age}", "{HH:MM} {day}", "{HH:MM} {day} · < 1 km", "{HH:MM} {day} · ~{n} km",
    "· < 1 km", "· ~{n} km",
    "{name}. Too late — {name} has a date with someone else.",
    "{name}. Out of time — you and {name} have no time left to meet.",
}
# Assembled from runs, one of them styled or a name between them: checked as their runs, below.
ASSEMBLED = {
    "We're really sorry, {name} confirmed a date just before you did.",
    "Sorry, you and {name} ran out of time.",
    "Confirm, and you and {name} have a confirmed, binding date {day}.",
    "confirmed, binding date {day}",
    "People who want to meet you",
    "No likes yet",
}

for ticket_file in ("02-grid.md", "03-likes-received.md"):
    ticket = read(os.path.join(TICKETS, ticket_file))
    section = ticket.split("## Copy — final strings", 1)[1].split("\n## ", 1)[0]
    quoted = re.findall(r"`([^`]+)`", section)
    check("%s has a copy section with strings" % ticket_file, len(quoted) > 20)
    runs_checked = set()
    for q in quoted:
        if q in NOT_COPY or q in COMPUTED or q in ASSEMBLED:
            continue
        for run in re.split(r"\{[^}]+\}", q):
            if len(run.strip()) < 3 or run in runs_checked:
                continue
            runs_checked.add(run)
            check("%s copy run in Kotlin: %r" % (ticket_file, run), run in kt)
            check("%s copy run in Swift: %r" % (ticket_file, run), run in sw)

# The assembled ones, as each platform stores their runs.
for label, kt_runs, sw_runs in [
    ("sorry headline, matched", ['"We\'re really sorry, %s confirmed a date "', '"just before you did."'],
     ['"We\'re really sorry, \\(name) confirmed a date "', '"just before you did."']),
    ("sorry headline, no overlap", ['"Sorry, you and %s "', '"ran out of time."'],
     ['"Sorry, you and \\(name) "', '"ran out of time."']),
    ("mutual Be sure line", ['"Confirm, and you and %s have a "', '"confirmed, binding date %s"'],
     ['"Confirm, and you and \\(name) have a "', '"confirmed, binding date \\(day)"']),
    ("Likes intro", ['"People who want to "', '"meet you"'], ['"People who want to "', '"meet you"']),
    ("empty state", ['"No likes "', '"yet"'], ['"No likes "', '"yet"']),
]:
    check("%s (Kotlin)" % label, all(r in kt for r in kt_runs))
    check("%s (Swift)" % label, all(r in sw for r in sw_runs))

# ── computed formats, pinned by the rules tests on both platforms ───────────────────────────────
for fmt, assertion in [
    ("grid meta line", '"19:00 today · ~1 km"'),
    ("grid meta line, under 1 km", '"18:00 tomorrow · < 1 km"'),
    ("Likes distance", '"· ~4 km"'),
    ("closed grid card's name", '"Anna. Too late — Anna has a date with someone else."'),
    ("closed grid card's name, no overlap", '"Anna. Out of time — you and Anna have no time left to meet."'),
    ("quoted message", '"\\"Coffee at Bonanza?\\""'),
]:
    check("rules test pins %s (Kotlin)" % fmt, assertion in kt_tests)
    check("rules test pins %s (Swift)" % fmt, assertion in sw_tests)

# ── tracking (both tickets' first comments) ─────────────────────────────────────────────────────
for server_event in ["like_sent", "match_created", "like_withdrawn"]:
    check("client never fires %s (Kotlin)" % server_event, '"%s"' % server_event not in kt)
    check("client never fires %s (Swift)" % server_event, '"%s"' % server_event not in sw)
# As literals: the analytics files name the retired values in comments, to say they are retired.
for retired in ["grid_card", "grid_card_opened", "profile_opened", "grid_heart_tapped", "grid_like_tapped",
                "like_declined", "like_rejected", "likes_received_declined", "grid_refreshed", "card_closed",
                "confirm_refused_dismissed", "find_someone_else_tapped", "back_to_likes_tapped", "likes_refreshed",
                "is_locked"]:
    check("no invented or retired %s (Kotlin)" % retired, '"%s"' % retired not in kt)
    check("no invented or retired %s (Swift)" % retired, '"%s"' % retired not in sw)
for name in ["search_viewed", "profile_card_viewed", "like_tapped", "like_confirm_shown", "like_confirm_abandoned",
             "pass_sent", "pass_undone", "like_confirm_failed", "likes_received_viewed"]:
    check("event %s (Kotlin)" % name, '"%s"' % name in kt)
    check("event %s (Swift)" % name, '"%s"' % name in sw)
for prop in ["is_mutual", "surface", "open_count", "closed_count", "reason"]:
    check("property %s (Kotlin)" % prop, '"%s"' % prop in kt)
    check("property %s (Swift)" % prop, '"%s"' % prop in sw)
for value in ["grid", "likes_received", "profile"]:
    check("value %s (Kotlin)" % value, '"%s"' % value in kt)
    check("value %s (Swift)" % value, '"%s"' % value in sw)
kt_screens = read(os.path.join(KT, "profile", "ProfileAnalytics.kt"))
sw_screens = read(os.path.join(SW, "ProfileAnalytics.swift"))
for screen_id, screen_name in [("search_grid", "Search - Grid"), ("likes_received", "Search - Likes received")]:
    check("screen %s (Kotlin)" % screen_id, '"%s"' % screen_id in kt_screens and '"%s"' % screen_name in kt_screens)
    check("screen %s (Swift)" % screen_id, '"%s"' % screen_id in sw_screens and '"%s"' % screen_name in sw_screens)

# ── the numbers the tickets fix ─────────────────────────────────────────────────────────────────
kt_motion = read(os.path.join(KT, "designsystem", "Motion.kt"))
sw_motion = read(os.path.join(SW, "Motion.swift"))
for label, kt_token, sw_token in [
    ("GRID_LEAVE_HOLD_MS 150", "GRID_LEAVE_HOLD = 150", "gridLeaveHold: TimeInterval = 0.15"),
    ("GRID_LEAVE_MS 800", "GRID_LEAVE = 800", "gridLeave: TimeInterval = 0.8"),
    ("GRID_REFLOW_MS 250", "GRID_REFLOW = 250", "gridReflow: TimeInterval = 0.25"),
    ("reduced-motion fade 200", "GRID_LEAVE_REDUCED = 200", "gridLeaveReduced: TimeInterval = 0.2"),
    ("LIKES_UNDO_MS 6000", "LIKES_UNDO = 6000", "likesUndo: TimeInterval = 6"),
]:
    check("%s (Kotlin)" % label, kt_token in kt_motion)
    check("%s (Swift)" % label, sw_token in sw_motion)
check("fly-out scale .12 and drop 72% (Kotlin)", "FLY_OUT_SCALE = 0.12f" in kt and "FLY_OUT_DROP = 0.72f" in kt)
check("fly-out scale .12 and drop 72% (Swift)", "flyOutScale: CGFloat = 0.12" in sw and "flyOutDrop: CGFloat = 0.72" in sw)
check("fly-out easing cubic-bezier(.55,0,.8,.2) (Kotlin)", "CubicBezierEasing(0.55f, 0f, 0.8f, 0.2f)" in kt)
check("fly-out easing cubic-bezier(.55,0,.8,.2) (Swift)", "timingCurve(0.55, 0, 0.8, 0.2" in sw)
check("reflow easing cubic-bezier(.2,.7,.2,1) (Kotlin)", "CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)" in kt)
check("reflow easing cubic-bezier(.2,.7,.2,1) (Swift)", "timingCurve(0.2, 0.7, 0.2, 1" in sw)
check("grid card 248 (Kotlin)", "GRID_CARD_HEIGHT = 248.dp" in kt)
check("grid card 248 (Swift)", "gridCardHeight: CGFloat = 248" in sw)

# ── no raw colour in the new screens: tokens, or a token's tint ──────────────────────────────────
premium_kt = read_all([os.path.join(KT, "search", f) for f in (
    "SearchGridScreen.kt", "LikesReceivedScreen.kt", "PremiumParts.kt", "PremiumRules.kt", "CardFlyOut.kt")])
premium_sw = read_all(glob.glob(os.path.join(SW, "Premium*.swift")) + glob.glob(os.path.join(SW, "Likes*.swift"))
                      + [os.path.join(SW, f) for f in ("SearchGrid.swift", "CardFlyOut.swift")])
check("no raw Color(0x...) in the Premium screens (Kotlin)", not re.findall(r"Color\(0x", premium_kt))
check("no raw Color(hex:) in the Premium screens (Swift)", not re.findall(r"Color\(hex:", premium_sw))

# ── proposed, not ticketed: listed, so nobody mistakes them for the tickets' ───────────────────
for proposed in ["We couldn't load your likes just now. Please try again."]:
    notes.append("PROPOSED copy, not in any ticket: %r" % proposed)

print("premium conformance: %d checks" % count)
for n in notes:
    print("  note: " + n)
if failures:
    print("FAILED %d:" % len(failures))
    for f in failures:
        print("  x " + f)
    sys.exit(1)
print("all passed")
