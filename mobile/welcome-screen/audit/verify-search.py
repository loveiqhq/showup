# -*- coding: utf-8 -*-
"""Date search 01 -- conformance against SHOWUP-184, on BOTH platforms.

    python audit/verify-search.py

WHAT THIS CHECKS

Every string in the ticket's "Copy -- final strings" section, QUOTED FROM THE TICKET FILE rather than
retyped here: design_handoff_showup/search/tickets/01-profile.md is read at run time, every backticked
string in that section is pulled out, and each of its literal runs -- the text between `{placeholders}`
-- is found in the Kotlin and the Swift sources. A word changed in the ticket fails this until both
apps change with it; a word changed in one app fails it at once.

Three strings are assembled from two runs because one run is styled (the em, the violet emphasis);
those are checked as the two runs joined. Then the tracking rules that are easy to break silently: the
client never fires a server event, and every family I event name is the registry's.

WHAT THIS DOES NOT CHECK

Whether the screen looks right -- that is SearchFitTest on 23 phones, the evidence frames and a person
holding a phone. Formats the rules COMPUTE (`19:00 today`, `~3 km · Kreuzberg`, `{n} / 100`) are
pinned by SearchRulesTest and SearchRulesTests; this checks those tests assert them.
"""
import glob
import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(MOBILE))
TICKET = os.path.join(ROOT, "design_handoff_showup", "search", "tickets", "01-profile.md")
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


kt = read_all(glob.glob(os.path.join(KT, "search", "*.kt")) + [os.path.join(KT, "designsystem", "ShowUpCount.kt")])
sw = read_all(glob.glob(os.path.join(SW, "Search*.swift")) + [os.path.join(SW, "ShowUpCount.swift")])
kt_tests = read_all(glob.glob(os.path.join(KT_TEST, "search", "*.kt")))
sw_tests = read_all(glob.glob(os.path.join(SW_TEST, "Search*.swift")))
check("Kotlin Search sources found", bool(kt))
check("Swift Search sources found", bool(sw))
kt, sw = flatten(kt), flatten(sw)

# Kotlin writes `{name}` as `%s` or `$name`; Swift as `\(name)`. Only the literal runs are compared.
ticket = read(TICKET)
section = ticket.split("## Copy — final strings", 1)[1].split("\n## ", 1)[0]
quoted = re.findall(r"`([^`]+)`", section)

# Not copy: the placeholders the section names in its first sentence, and the States lines' names
# for the kit file and its reference (27 Oct 2026).
NOT_COPY = {
    "{name}", "{day}", "today", "tomorrow", "decide",
    "search-empty", "design_handoff_showup/search/screen-search-empty-reference.jsx",
}
# Named by the States lines to be LEFT OUT -- checked absent below, never as copy.
LEFT_OUT = {"Adjust filters manually"}
# Copy of Filters opened from Search: Check-In's sources hold it, checked there below.
ELSEWHERE = {"Couldn't save your filters. Check your connection and try again."}
# Computed by the rules from runs, not stored whole: pinned by the rules tests instead (below).
COMPUTED = {
    "< 1 km · {district}", "~{n} km · {district}", "{name}, {age}", "{name}", "{HH:MM} {day}",
    "+{n}", "{n} / 100", "{m:ss}", " · {k} in common",
}
# Assembled from two runs, one of them styled: checked joined, below.
ASSEMBLED = {
    "If {name} becomes your match, you two have a confirmed, binding date {day}.",
    "confirmed, binding date {day}",
    "Optional message to {name}",
    "Three ways to decide",
    # The no-results headline, `no people` washed; and its emphasis named alone.
    "Unfortunately there are no people left matching your search filters.",
    "no people",
}

runs_checked = set()
for q in quoted:
    if q in NOT_COPY or q in COMPUTED or q in ASSEMBLED or q in LEFT_OUT or q in ELSEWHERE:
        continue
    for run in re.split(r"\{[^}]+\}", q):
        run = run.strip() if run.strip() in (".",) else run
        if len(run.strip()) < 3 or run in runs_checked:
            continue
        runs_checked.add(run)
        check("copy run in Kotlin: %r" % run, run in kt)
        check("copy run in Swift: %r" % run, run in sw)

# The assembled ones: lead + emphasis as each platform stores them.
check("Be sure line lead (Kotlin)", '"If %s becomes your match, you two have a "' in kt)
check("Be sure line emphasis (Kotlin)", '"confirmed, binding date %s"' in kt)
check("Be sure line lead (Swift)", '"If \\(name) becomes your match, you two have a "' in sw)
check("Be sure line emphasis (Swift)", '"confirmed, binding date \\(day)"' in sw)
check("message title runs (Kotlin)", '"Optional"' in kt and '" message to %s"' in kt)
check("message title runs (Swift)", '"Optional"' in sw and '" message to \\(name)"' in sw)
check("coach title runs (Kotlin)", '"Three ways to "' in kt and '"decide"' in kt)
check("coach title runs (Swift)", '"Three ways to "' in sw and '"decide"' in sw)
for platform, src in (("Kotlin", kt), ("Swift", sw)):
    check("no-results headline runs (%s)" % platform,
          '"Unfortunately there are "' in src and '"no people"' in src
          and '" left matching your search filters."' in src)
check("every Copy string the checker skips is accounted for",
      ELSEWHERE <= set(quoted) and LEFT_OUT <= set(quoted))

# ── computed formats, pinned by the rules tests on both platforms ───────────────────────────────
for fmt, kt_assert, sw_assert in [
    ("< 1 km", '"< 1 km · Neukölln, Berlin"', '"< 1 km · Neukölln, Berlin"'),
    ("~N km · district", '"~3 km · Kreuzberg"', '"~3 km · Kreuzberg"'),
    ("name, age", '"Leonie, 28"', '"Leonie, 28"'),
    ("meet line", '"19:00 today"', '"19:00 today"'),
    ("counter", '"12 / 100"', '"12 / 100"'),
    ("in common", '"10 things Leonie is into · 4 in common"', '"10 things Leonie is into · 4 in common"'),
    ("media sub", '"Voice prompt · 0:14"', '"Voice prompt · 0:14"'),
]:
    check("rules test pins %s (Kotlin)" % fmt, kt_assert in kt_tests)
    check("rules test pins %s (Swift)" % fmt, sw_assert in sw_tests)

# ── tracking: the client fires none of the server's events, and no retired name ─────────────────
for server_event in ["like_sent", "match_created", "like_withdrawn"]:
    check("client never fires %s (Kotlin)" % server_event, '"%s"' % server_event not in kt)
    check("client never fires %s (Swift)" % server_event, '"%s"' % server_event not in sw)
# As literals: the analytics files name the retired values in comments, to say they are retired.
for retired in ["not_now_tapped", "not_interested_tapped", "grid_card", "showup_band", "reason_if_given"]:
    check("no retired %s (Kotlin)" % retired, '"%s"' % retired not in kt)
    check("no retired %s (Swift)" % retired, '"%s"' % retired not in sw)
for name in ["search_viewed", "profile_card_viewed", "profile_photo_opened", "voice_prompt_played",
             "video_prompt_played", "like_tapped", "like_confirm_shown", "like_confirm_abandoned",
             "like_message_attached", "pass_sent", "search_coach_shown", "search_coach_dismissed"]:
    check("event %s (Kotlin)" % name, '"%s"' % name in kt)
    check("event %s (Swift)" % name, '"%s"' % name in sw)
check("screen id search_profile (Kotlin)", '"search_profile"' in read(os.path.join(KT, "profile", "ProfileAnalytics.kt")))
check("screen id search_profile (Swift)", '"search_profile"' in read(os.path.join(SW, "ProfileAnalytics.swift")))

# ── the numbers the ticket fixes ────────────────────────────────────────────────────────────────
check("message max 100 (Kotlin)", "LIKE_MESSAGE_MAX = 100" in kt)
check("message max 100 (Swift)", "likeMessageMax = 100" in sw)
check("three shared vibes, then +N (Kotlin)", "SHARED_VIBES_SHOWN = 3" in kt)
check("three shared vibes, then +N (Swift)", "sharedVibesShown = 3" in sw)

# ── no raw colour in a Search screen: a tint is `Token.copy(alpha = ...)` / `.opacity(...)` ────
# The one exception is the kit's photo placeholder palette, illustration data defined once per
# platform; it moves to the design system when the grid (SHOWUP-185) becomes its second consumer.
def raw_colours(source, pattern, palette_start, palette_end):
    s = source
    if palette_start in s:
        head, rest = s.split(palette_start, 1)
        s = head + rest.split(palette_end, 1)[1]
    return re.findall(pattern, s)

kt_raw = raw_colours(kt, r"Color\(0x[0-9A-Fa-f_]+\)", "internal val PHOTO_PLACEHOLDERS", "\n)\n")
sw_raw = raw_colours(sw, r"Color\(hex:\s*0x[0-9A-Fa-f]+\)", "let photoPlaceholders: [[Color]] = [", "\n]\n")
check("no raw Color(0x...) in Search outside the placeholder palette (Kotlin): %s" % kt_raw[:3], not kt_raw)
check("no raw Color(hex:) in Search outside the placeholder palette (Swift): %s" % sw_raw[:3], not sw_raw)
check("blush is the design-system token on both", "BlushWash" in kt and "suGradBlush" in sw
      and "0xFFFDE7DC" not in kt and "0xFDE7DC" not in sw)

# ── the States lines (27 Oct 2026): what the interim no-results screen leaves out ──────────────
# The kit's normal mode carries a CTA sub-line and `Adjust filters manually`; the ticket drops both,
# and the stand-in `No results` this file noted before the kit's file arrived must be gone.
for gone in ["Adjust filters manually", "We will match your settings to receive the best results.", "No results"]:
    check("no-results screen leaves out %r (Kotlin)" % gone, '"%s"' % gone not in kt)
    check("no-results screen leaves out %r (Swift)" % gone, '"%s"' % gone not in sw)
check("one no-results screen per platform, used by the queue and the grid",
      kt.count("SearchEmptyState(onWiden = onFilters)") == 2 and sw.count("SearchEmptyView(onWiden: onFilters)") == 2)
check("search_empty_shown on both platforms", '"search_empty_shown"' in kt and '"search_empty_shown"' in sw)
# The save that fails on Filters opened from Search lives with the Check-In's copy, not Search's.
save_failed = "Couldn't save your filters. Check your connection and try again."
check("approved filter-save copy on both: %r" % save_failed,
      save_failed in read_all(glob.glob(os.path.join(KT, "checkin", "*.kt")))
      and save_failed in read_all(glob.glob(os.path.join(SW, "CheckIn*.swift"))))

print("search conformance: %d checks" % count)
for n in notes:
    print("  note: " + n)
if failures:
    print("FAILED %d:" % len(failures))
    for f in failures:
        print("  x " + f)
    sys.exit(1)
print("all passed")
