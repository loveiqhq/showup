"""
verify-end.py -- Date 06, end the date and report a problem (SHOWUP-197) against its ticket, on both apps.

Reads the ticket's `## Copy -- final strings` section AT RUN TIME (design_handoff_showup/date/tickets/06-end-report.md)
and checks every backticked string of it, exactly (no apostrophe, dash or quote is normalised: the ticket spells some
straight and some typographic, and the apps keep each as written), in the Kotlin and the Swift copy files. A template
(`{name}`, `{time}`) is matched with the app's own placeholder in its place (`%s` / `$name` on Android, `\\(name)` on
iOS); the bold / italic marks (`**...**`, `*...*`) are stripped, and a marked string is checked as its runs, because the
apps draw each run in its own style. Nothing is exempt silently: EXEMPT lists every string left out, with its reason.

Then the rules the ticket names that a reading can check:
  - one copy each of NoShowGuide, NoShowConfirm, UncomfortableStep and EndErrorBox, per platform
  - none of the five retired events is emitted anywhere in either app
  - date_ended and problem_stay_flagged (the server's) are never fired by either app

And it proves each group can fail: the copy, the single-copy, the retired-event and the server-event checks are each
run once more against a deliberately broken input and must report it.
"""
import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(MOBILE))
TICKET = os.path.join(ROOT, "design_handoff_showup", "date", "tickets", "06-end-report.md")
KT_ROOT = os.path.join(MOBILE, "android-preview-project", "app", "src", "main", "java", "com", "showup")
SW = os.path.join(MOBILE, "ios-app", "ShowUpWelcome")

RETIRED = ("wrapup_prompted", "wrapup_confirmed", "grace_period_started", "grace_period_expired",
           "no_penalty_exit_granted")
SERVER_ONLY = ("date_ended", "problem_stay_flagged")
SHARED = ("NoShowGuide", "NoShowConfirm", "UncomfortableStep", "EndErrorBox")

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


def pattern(s):
    """The regex a ticket string must match in an app's source: its literal text, a placeholder where `{x}` stands."""
    return PH.join(re.escape(part) for part in HOLE.split(s))


def missing_from(strings, source, label):
    # The ticket's marks (`**bold**`, `*italic*`) are the apps' styles, not text: stripped before the comparison.
    return ["ticket copy on %s: %r" % (label, s) for s in strings
            if not re.search(pattern(MARKS.sub("", s)), source)]


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

COPY_KT = [os.path.join(KT_ROOT, "date", "end", "DateEndCopy.kt"), os.path.join(KT_ROOT, "arrival", "ArrivalCopy.kt")]
COPY_SW = [os.path.join(SW, "DateEndCopy.swift"), os.path.join(SW, "ArrivalCopy.swift")]
kt_copy = literal_stream("\n".join(read(p) for p in COPY_KT))
sw_copy = literal_stream("\n".join(read(p) for p in COPY_SW))

# ── the ticket's copy, run by run ───────────────────────────────────────────────────────────────
ticket = read(TICKET)
section = ticket.split("## Copy", 1)[1].split("\n## ", 1)[0]
quoted = re.findall(r"`([^`]+)`", section)
# A placeholder on its own is a definition (`{name}` is the match's first name), not copy.
strings = [q for q in dict.fromkeys(quoted) if not HOLE.fullmatch(q)]

# Nothing is exempt silently: a string the ticket backticks that is not a string of the app, with why.
EXEMPT = {}
strings = [s for s in strings if s not in EXEMPT]

# Decided after the ticket, so the apps say something else on purpose: ticket string -> (what the apps say, a run of
# the old wording that must be gone, who decided). Checked both ways, so neither a revert nor a half-edit passes.
OPEN_ITEM_2 = "ticket Open item 2, decided by Esma 22 Nov 2026"
DECIDED = {
    "Tap End Date - when your date is finished.": (
        "Tap End date - when your date is finished.", "Tap End Date", OPEN_ITEM_2 + ": End date, small d"),
    "End Date": ("End date", "End Date", OPEN_ITEM_2 + ": End date, small d"),
    "End Date → Review": ("End date → Review", "End Date →", OPEN_ITEM_2 + ": End date, small d"),
    "This is only for dates that happened. Need to call it off before meeting? Use Cancel date in the chat menu. "
    "Ending a date you didn’t attend counts as a no-show and lowers your Show-up Rate.": (
        "This is only for dates that happened. Need to call it off before meeting? Use Cancel this date at the bottom "
        "of the Date screen. Ending a date you didn’t attend counts as a no-show and lowers your Show-up Rate.",
        "in the chat menu", OPEN_ITEM_2 + ": the chat menu has no Cancel date; point to the Date screen's link"),
}
for old, (new, gone, why) in DECIDED.items():
    check("the ticket still spells %r (else drop the decision)" % old, old in strings)
    check("both apps say %r (%s)" % (new, why),
          not missing_from([new], kt_copy, "Android") and not missing_from([new], sw_copy, "iOS"))
    check("neither app still says %r" % gone, gone not in kt_copy and gone not in sw_copy)
strings = [s for s in strings if s not in DECIDED]
check("the ticket's copy section was read (%d strings)" % len(strings), len(strings) >= 40)
for want in ("Couldn't end the date. Check your connection and try again.", "Head out now", "Stay for now",
             "Check in first, so we can confirm you were there."):
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

# ── retired and server-only events ──────────────────────────────────────────────────────────────
check("Android: no retired event is emitted (%s)" % ", ".join(RETIRED), not emitted(kt_all, RETIRED))
check("iOS: no retired event is emitted (%s)" % ", ".join(RETIRED), not emitted(sw_all, RETIRED))
check("Android: date_ended and problem_stay_flagged are never fired", not fired(kt_all, SERVER_ONLY))
check("iOS: date_ended and problem_stay_flagged are never fired", not fired(sw_all, SERVER_ONLY))

# ── it can fail: one negative self-test per group ───────────────────────────────────────────────
victim = "Please check you are at the right location."
check("the mutation check has a string to remove", victim in kt_copy and victim in sw_copy)
check("the copy check reports a string removed from the Android copy (mutation)",
      any("right location" in m for m in missing_from(strings, kt_copy.replace(victim, ""), "Android")))
check("the copy check reports a string removed from the iOS copy (mutation)",
      any("right location" in m for m in missing_from(strings, sw_copy.replace(victim, ""), "iOS")))
check("the copy check reports an apostrophe changed from typographic to straight (mutation)",
      any("actually met" in m for m in missing_from(strings, kt_copy.replace("you’ve actually met", "you've actually met"), "Android")))
check("the copy check reports a placeholder that is missing (mutation)",
      any("Message" in m for m in missing_from(strings, kt_copy.replace("Message %s", "Message"), "Android")))
check("the single-copy check reports a second EndErrorBox (mutation)",
      count_decls(kt_all + "\nfun EndErrorBox(x: Int) {}\n", "kt")["EndErrorBox"] == 2
      and count_decls(sw_all + "\nstruct EndErrorBox: View {}\n", "sw")["EndErrorBox"] == 2)
check("the retired-event check reports a wrapup_prompted in code (mutation)",
      emitted(kt_all + '\nval e = "wrapup_prompted"\n', RETIRED) == ["wrapup_prompted"]
      and emitted(sw_all + "\nlet e = WrapupPrompted()\n", RETIRED) == ["wrapup_prompted"])
check("the server-event check reports a date_ended fired (mutation)",
      fired(kt_all + '\nval e = "date_ended"\n', SERVER_ONLY) == ["date_ended"]
      and fired(sw_all + "\nfunc problemStayFlagged(id: String) {}\n", SERVER_ONLY) == ["problem_stay_flagged"])
check("the server-event check ignores a comment naming the event (mutation)",
      not fired('// `date_ended` is the server\'s\n', SERVER_ONLY))

print("end conformance: %d checks" % count)
if failures:
    for name in failures:
        print("  x " + name)
    sys.exit(1)
print("all passed")
