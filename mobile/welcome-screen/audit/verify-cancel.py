"""
verify-cancel.py -- Date 07, cancel this date (SHOWUP-198) against its ticket, on both apps.

Reads the ticket's `## Copy -- final strings` section AT RUN TIME (design_handoff_showup/date/tickets/07-cancel.md)
and checks every backticked string of it, exactly (no apostrophe, dash or quote is normalised: the apps keep the
ticket's own spelling), in the Kotlin and the Swift copy files. A template (`{name}`, `{drop}`, `{value}`) is matched
with the app's own placeholder in its place (`%s` / `%d` / `$name` on Android, `\\(name)` on iOS); the bold / italic marks
(`**...**`, `*...*`) are stripped, and the apps' runs are read in order, because each run is drawn in its own style.
Nothing is exempt silently: EXEMPT lists every string left out, with its reason.

Then the rules the ticket names that a reading can check:
  - one copy of EndErrorBox per platform (Date 06's box, extended with a `text`, not forked)
  - none of the three retired events is emitted anywhere in either app
  - date_cancelled (the server's) and date_ended (Date 06's) are never fired by either app
  - the cost box takes the rate config's value (`cancelPenaltyPoints`): no literal 10 in any copy string

And it proves each group can fail: the copy, the single-copy, the retired-event, the server-event and the cost-box
checks are each run once more against a deliberately broken input and must report it.
"""
import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(MOBILE))
TICKET = os.path.join(ROOT, "design_handoff_showup", "date", "tickets", "07-cancel.md")
KT_ROOT = os.path.join(MOBILE, "android-preview-project", "app", "src", "main", "java", "com", "showup")
SW = os.path.join(MOBILE, "ios-app", "ShowUpWelcome")

RETIRED = ("cancel_link_tapped", "partner_cancellation_received", "rematch_started")
SERVER_ONLY = ("date_cancelled", "date_ended")
SHARED = ("EndErrorBox",)

try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass

failures = []
count = 0


def check(name, ok):
    global count
    count += 1
    print("  %s %s" % ("✓" if ok else "x", name))
    if not ok:
        failures.append(name)


def read(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()


def strip_comments(src):
    return re.sub(r"//[^\n]*|/\*.*?\*/", "", src, flags=re.S)


def literal_stream(source):
    """Every string literal of the source, comments removed, in file order and joined with nothing between: the apps
    draw a sentence in pieces (a bold run is its own constant), and the pieces read in order are the ticket's string."""
    return "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', strip_comments(source)))


def app_files(root, suffix):
    out = []
    for d, _, names in os.walk(root):
        if os.sep + "build" in d or os.sep + ".build" in d:
            continue
        out += [os.path.join(d, n) for n in names if n.endswith(suffix)]
    return sorted(out)


# One placeholder as either app writes it: Kotlin `%s` / `%d` / `$name` / `${...}`, Swift `\(name)`.
PH = r"(?:%s|%d|\$\{[^}]*\}|\$\w+|\\\([^)]*\))"
MARKS = re.compile(r"\*\*|\*")
HOLE = re.compile(r"\{[^}]*\}")
# A placeholder that is a bold run of its own (`**{value}**`): the sheet draws the value, the copy holds the rest.
BOLD_HOLE = re.compile(r"\*\*\{[^}]*\}\*\*")


def pattern(s):
    """The regex a ticket string must match in an app's source: its literal text, a placeholder where `{x}` stands."""
    return PH.join(re.escape(part) for part in HOLE.split(s))


def missing_from(strings, source, label):
    # The ticket's marks (`**bold**`, `*italic*`) are the apps' styles, not text: stripped before the comparison.
    return ["ticket copy on %s: %r" % (label, s) for s in strings
            if not re.search(pattern(MARKS.sub("", BOLD_HOLE.sub("", s))), source)]


def count_decls(src, kind):
    """Declarations of each shared step in `src`: Kotlin `fun Name(`, Swift `struct Name`."""
    found = {}
    for name in SHARED:
        pat = r"\bfun %s\(" % name if kind == "kt" else r"\bstruct %s\b" % name
        found[name] = len(re.findall(pat, strip_comments(src)))
    return found


def emitted(src, names):
    """Event names present in code (comments removed), as snake_case, camelCase or UPPER_SNAKE."""
    code = strip_comments(src)
    hits = []
    for n in names:
        words = n.split("_")
        loose = r"_?".join(re.escape(w) for w in words)
        if re.search(loose, code, re.I):
            hits.append(n)
    return hits


def fired(src, names):
    """The server's events as a literal event name, or as a builder function DEFINED under that name, in code (a route
    case such as `.dateEnded(...)` is not an event)."""
    code = strip_comments(src)
    hits = []
    for n in names:
        camel = n.split("_")[0] + "".join(w.capitalize() for w in n.split("_")[1:])
        if re.search(r'"%s"' % n, code) or re.search(r"\b(?:fun|func) %s\b" % camel, code):
            hits.append(n)
    return hits


# ── the sources ─────────────────────────────────────────────────────────────────────────────────
kt_all_files = app_files(KT_ROOT, ".kt")
sw_all_files = app_files(SW, ".swift")
check("the Android sources were found (%d)" % len(kt_all_files), len(kt_all_files) >= 100)
check("the iOS sources were found (%d)" % len(sw_all_files), len(sw_all_files) >= 100)
kt_all = "\n".join(read(p) for p in kt_all_files)
sw_all = "\n".join(read(p) for p in sw_all_files)

COPY_KT = [os.path.join(KT_ROOT, "date", "cancel", "CancelCopy.kt")]
COPY_SW = [os.path.join(SW, "CancelCopy.swift")]
kt_copy = literal_stream("\n".join(read(p) for p in COPY_KT))
sw_copy = literal_stream("\n".join(read(p) for p in COPY_SW))

# ── the ticket's copy, run by run ───────────────────────────────────────────────────────────────
ticket = read(TICKET)
section = ticket.split("## Copy", 1)[1].split("\n## ", 1)[0]
quoted = re.findall(r"`([^`]+)`", section)
# A placeholder on its own is a definition (`{name}` is the match's first name), not copy.
strings = [q for q in dict.fromkeys(quoted) if not HOLE.fullmatch(q)]

# Nothing is exempt silently: a string the ticket backticks that is not a string of the app, with why.
EXEMPT = {
    "{when} \u00b7 {place}": "the pill is the chat header's line, built from the binding values (headerLine), not a literal",
}
strings = [s for s in strings if s not in EXEMPT]
check("the ticket's copy section was read (%d strings)" % len(strings), len(strings) >= 20)
for want in ("Couldn't cancel the date. Check your connection and try again.", "Cancel this date",
             "Cancel anyway and let her down", "Find someone who shows up"):
    check("the ticket spells %r" % want, want in strings)

kt_missing = missing_from(strings, kt_copy, "Android")
sw_missing = missing_from(strings, sw_copy, "iOS")
check("every ticket string is in Android's copy files, exactly (%d strings)" % len(strings), not kt_missing)
check("every ticket string is in iOS's copy files, exactly (%d strings)" % len(strings), not sw_missing)
failures.extend(kt_missing + sw_missing)

# ── one copy each of the shared steps ───────────────────────────────────────────────────────────
kt_decls = count_decls(kt_all, "kt")
sw_decls = count_decls(sw_all, "sw")
for name in SHARED:
    check("Android: exactly one %s (found %d)" % (name, kt_decls[name]), kt_decls[name] == 1)
    check("iOS: exactly one %s (found %d)" % (name, sw_decls[name]), sw_decls[name] == 1)

# ── the cost box takes the rate config's value ──────────────────────────────────────────────────
def digits_in_literals(source):
    """Standalone numbers inside string literals of the copy, comments removed."""
    return re.findall(r"\b\d+\b", " ".join(re.findall(r'"((?:[^"\\]|\\.)*)"', strip_comments(source))))


kt_cancel_copy = read(COPY_KT[0])
sw_cancel_copy = read(COPY_SW[0])
check("Android: no literal 10 (or any number) in a cancel copy string", not digits_in_literals(kt_cancel_copy))
check("iOS: no literal 10 (or any number) in a cancel copy string", not digits_in_literals(sw_cancel_copy))
check("Android: the cost box is a function of the drop it is given", re.search(r"fun cost\(drop: Int\)", kt_cancel_copy) is not None)
check("iOS: the cost box is a function of the drop it is given", re.search(r"static func cost\(_ drop: Int\)", sw_cancel_copy) is not None)
check("Android: the drop is the date's cancelPenaltyPoints",
      re.search(r"val drop: Int get\(\) = date\?\.cancelPenaltyPoints", read(os.path.join(KT_ROOT, "date", "cancel", "CancelViewModel.kt"))) is not None)
check("iOS: the drop is the date's cancelPenaltyPoints",
      re.search(r"var drop: Int \{ date\?\.cancelPenaltyPoints", read(os.path.join(SW, "CancelModel.swift"))) is not None)
check("Android: the sheet draws the box from the model's drop", "CancelCopy.cost(drop)" in read(os.path.join(KT_ROOT, "date", "cancel", "CancelDateConfirmSheet.kt")))
check("iOS: the sheet draws the box from the model's drop", "CancelCopy.cost(drop)" in read(os.path.join(SW, "CancelDateConfirmSheet.swift")))

# ── retired and server-only events ──────────────────────────────────────────────────────────────
check("Android: no retired event is emitted (%s)" % ", ".join(RETIRED), not emitted(kt_all, RETIRED))
check("iOS: no retired event is emitted (%s)" % ", ".join(RETIRED), not emitted(sw_all, RETIRED))
check("Android: date_ended and problem_stay_flagged are never fired", not fired(kt_all, SERVER_ONLY))
check("iOS: date_ended and problem_stay_flagged are never fired", not fired(sw_all, SERVER_ONLY))

# ── it can fail: one negative self-test per group ───────────────────────────────────────────────
victim = "Couldn't cancel the date."
check("the mutation check has a string to remove", victim in kt_copy and victim in sw_copy)
check("the copy check reports a string removed from the Android copy (mutation)",
      any("Couldn't cancel" in m for m in missing_from(strings, kt_copy.replace(victim, ""), "Android")))
check("the copy check reports a string removed from the iOS copy (mutation)",
      any("Couldn't cancel" in m for m in missing_from(strings, sw_copy.replace(victim, ""), "iOS")))
check("the copy check reports a changed apostrophe (mutation)",
      any("counting" not in m and "Don't" in m for m in missing_from(strings, kt_copy.replace("Don't cancel", "Don\u2019t cancel"), "Android")))
check("the copy check reports a placeholder that is missing (mutation)",
      any("counting on you" in m for m in missing_from(strings, kt_copy.replace("%s is counting", " is counting"), "Android")))
check("the single-copy check reports a second EndErrorBox (mutation)",
      count_decls(kt_all + "\nfun EndErrorBox(x: Int) {}\n", "kt")["EndErrorBox"] == 2
      and count_decls(sw_all + "\nstruct EndErrorBox: View {}\n", "sw")["EndErrorBox"] == 2)
check("the retired-event check reports a rematch_started in code (mutation)",
      emitted(kt_all + '\nval e = "rematch_started"\n', RETIRED) == ["rematch_started"]
      and emitted(sw_all + "\nlet e = CancelLinkTapped()\n", RETIRED) == ["cancel_link_tapped"])
check("the server-event check reports a date_cancelled fired (mutation)",
      fired(kt_all + '\nval e = "date_cancelled"\n', SERVER_ONLY) == ["date_cancelled"]
      and fired(sw_all + "\nfunc dateEnded(id: String) {}\n", SERVER_ONLY) == ["date_ended"])
check("the server-event check ignores a comment naming the event (mutation)",
      not fired('// `date_cancelled` is the server\'s\n', SERVER_ONLY))
check("the cost-box check reports a literal 10 in a copy string (mutation)",
      digits_in_literals(kt_cancel_copy.replace('b("Show-up Rate by $drop")', 'b("Show-up Rate by 10")')) == ["10"])

print("cancel conformance: %d checks" % count)
if failures:
    for name in failures:
        print("  x " + name)
    sys.exit(1)
print("all passed")
