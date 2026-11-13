"""
verify-chat.py -- the coordinate chat (SHOWUP-194) against its ticket, on both apps.

Reads the ticket's `## Copy -- final strings` section AT RUN TIME (design_handoff_showup/date/tickets/
02-coordinate-chat.md) and checks every quoted string in the Kotlin and the Swift, so an edit to the ticket
fails both platforms until they follow. A string with placeholders (`{name}`, `{h}` ...) is checked as its
literal runs, because each app fills them its own way. A headline whose italic word the ticket quotes
beside it (`Must read` in `Must read -- how messaging works`) is held in the apps as its runs, and is checked
as those runs. Apostrophes compare as one character.

Then the rules a reading can check:
  - the six chips shown today, in the vocabulary's order, on both apps (`arrived` joined with SHOWUP-195,
    design ruling R13); the two Date 05 owns (`change_time`, `change_place`) hidden on both
  - no heading line above the chips: no `Tap a chip` anywhere in the chat
  - none of the events this ticket retires (`coordinate_viewed`, `date_status_sent`, `date_status_undone`)
    sent by either app

And it proves it can fail: the copy check is run once more against an Android source with one ticket string
removed, and that run must report it.
"""
import glob
import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(MOBILE))
TICKET = os.path.join(ROOT, "design_handoff_showup", "date", "tickets", "02-coordinate-chat.md")
KT_ROOT = os.path.join(MOBILE, "android-preview-project", "app", "src", "main", "java", "com", "showup")
KT = os.path.join(KT_ROOT, "chat")
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
    for esc, ch in (("\\u{2019}", "'"), ("\\u{2014}", "—"), ("\\u2019", "'"), ("\\u2014", "—")):
        text = text.replace(esc, ch)
    return text.replace("’", "'")


def joined_literals(source):
    """The source with literals split over lines joined: `"a " +\\n "b"` reads as `"a b"`."""
    return re.sub(r'"\s*\+\s*"', "", re.sub(r'"\s*\+\s*\n\s*"', "", source))


kt_files = sorted(glob.glob(os.path.join(KT, "*.kt")))
sw_names = ["ChatCopy.swift", "ChatReasons.swift", "ChatRules.swift", "ChatParts.swift", "ChatScreen.swift",
            "ChatTutorialScreen.swift", "CancelGuide.swift", "EndDateSheet.swift", "DateEndedScreen.swift"]
sw_files = [os.path.join(SW, n) for n in sw_names if os.path.exists(os.path.join(SW, n))]
check("the Android chat package was found", len(kt_files) >= 10)
check("the iOS chat files were found", len(sw_files) == len(sw_names))
kt = norm(joined_literals("\n".join(read(p) for p in kt_files)))
sw = norm(joined_literals("\n".join(read(p) for p in sw_files)))

# ── the copy, quoted from the ticket ───────────────────────────────────────────────────────────
ticket = read(TICKET)
section = ticket.split("## Copy", 1)[1].split("\n## ", 1)[0]
quoted = [norm(q) for q in re.findall(r"`([^`]+)`", section)]
# Not copy: the reference's component names the copy section mentions.
NOT_COPY = {"CHAT_REASONS"}
strings = [q for q in quoted if q not in NOT_COPY]
check("the ticket's copy section was read (%d strings)" % len(strings), len(strings) >= 40)


def runs(s):
    """The literal runs of a ticket string around its `{placeholders}`, the ones worth matching."""
    parts = re.split(r"\{[^}]*\}", s)
    return [p for p in parts if len(p.strip(" ·,.?…")) >= 2]


def pieces(s):
    """`s` cut around every other quoted string inside it -- a headline held as its italic and plain runs.
    A bare placeholder (`{name}`) is not a run of its own and never cuts."""
    inner = sorted((q for q in strings if q != s and len(q) >= 3 and q in s and not re.fullmatch(r"\{[^}]*\}", q)),
                   key=len, reverse=True)
    for q in inner:
        at = s.index(q)
        return [x for part in (s[:at], s[at + len(q):]) if part for x in pieces(part)] + [q]
    return [s]


def missing_from(source, label):
    out = []
    for s in strings:
        whole = all(r in source for r in runs(s))
        if whole:
            continue
        split = all(r in source for p in pieces(s) for r in runs(p))
        if not split:
            out.append("ticket copy on %s: %r" % (label, s))
    return out


for name in missing_from(kt, "Android") + missing_from(sw, "iOS"):
    check(name, False)
count += len(strings) * 2

# ── it can fail: one ticket string taken out of the Android source must be reported ────────────
victim = "We log which reason you pick — never what you type."
check("the mutation check has a string to remove", victim in kt)
mutated = kt.replace(victim, "")
check("the copy check reports a string removed from the source (mutation)",
      any(victim in m for m in missing_from(mutated, "Android")))

# ── the chips: six shown in order (SHOWUP-195 R13 added `arrived`), two hidden, on both ───────────────────────────────────────
ORDER = ["on_my_way", "arrived", "cant_find", "when_arrive", "change_time", "change_place", "running_late", "cant_make_it"]
SHOWN = ["on_my_way", "arrived", "cant_find", "when_arrive", "running_late", "cant_make_it"]
HIDDEN = {"change_time", "change_place"}

kt_reasons = read(os.path.join(KT, "ChatReasons.kt"))
kt_entries = re.findall(r'^\s*\w+\("([a-z_]+)",.*,\s*(true|false)\),?\s*$', kt_reasons, re.M)
check("Android: the vocabulary is the registry's eight, in order", [w for w, _ in kt_entries] == ORDER)
check("Android: the six shown chips, in order (SHOWUP-195 R13)", [w for w, s in kt_entries if s == "true"] == SHOWN)
check("Android: the two Date 05 chips hidden (SHOWUP-195 R13)", {w for w, s in kt_entries if s == "false"} == HIDDEN)

sw_reasons = read(os.path.join(SW, "ChatReasons.swift"))
sw_cases = re.findall(r'^\s*case \w+ = "([a-z_]+)"', sw_reasons, re.M)
check("iOS: the vocabulary is the registry's eight, in order", sw_cases == ORDER)
hidden_case = re.search(r"var shown: Bool \{.*?case ([^:]+): return false", sw_reasons, re.S)
sw_case_names = dict(re.findall(r'^\s*case (\w+) = "([a-z_]+)"', sw_reasons, re.M))
sw_hidden = {sw_case_names.get(c.strip().lstrip(".")) for c in hidden_case.group(1).split(",")} if hidden_case else set()
check("iOS: the two Date 05 chips hidden (SHOWUP-195 R13)", sw_hidden == HIDDEN)

# ── no heading line above the chips ─────────────────────────────────────────────────────────────
check("Android: no `Tap a chip` line", "Tap a chip" not in kt)
check("iOS: no `Tap a chip` line", "Tap a chip" not in sw)

# ── the retired events are sent by neither app ──────────────────────────────────────────────────
kt_all = "\n".join(read(p) for p in glob.glob(os.path.join(KT_ROOT, "**", "*.kt"), recursive=True))
sw_all = "\n".join(read(p) for p in glob.glob(os.path.join(SW, "*.swift")))
for event in ("coordinate_viewed", "date_status_sent", "date_status_undone"):
    check("Android never sends %s" % event, '"%s"' % event not in kt_all)
    check("iOS never sends %s" % event, '"%s"' % event not in sw_all)

print("chat conformance: %d checks" % count)
if failures:
    for name in failures:
        print("  x " + name)
    sys.exit(1)
print("all passed")
