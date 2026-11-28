"""
verify-partner-end.py -- Date 08, what the other side sees (SHOWUP-199) against its ticket, on both apps.

Reads the ticket's `## Copy -- final strings` section AT RUN TIME (design_handoff_showup/date/tickets/08-partner-end.md)
and checks every backticked string of it, exactly (the ticket's apostrophes are straight and the apps keep them so), in
the Kotlin and the Swift copy file, the literals read in file order. A template (`{name}`) is matched with the app's own
placeholder in its place; the bold / italic marks are stripped, because the apps draw each run in its own style.
Nothing is exempt: EXEMPT and DECIDED are empty, and stay visible so a deviation has to be written down, not absorbed.

Then the rules the ticket's acceptance criteria name that a reading can check:
  - B (uncomfortable) carries no gendered pronoun, whole words (ledgered ruling R-a), and never says `unsafe`
  - A (you did not show up) carries no digit and never says `less visible` / `lose App access`
  - one PartnerEndedScreen per platform
  - the three section 11 screen ids and names in both apps' ProfileScreen
  - neither app's Date 08 code fires the server's date_ended

And it proves each group can fail: each is run once more against a deliberately broken input and must report it.
"""
import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(MOBILE))
TICKET = os.path.join(ROOT, "design_handoff_showup", "date", "tickets", "08-partner-end.md")
KT_ROOT = os.path.join(MOBILE, "android-preview-project", "app", "src", "main", "java", "com", "showup")
SW = os.path.join(MOBILE, "ios-app", "ShowUpWelcome")
KT_DIR = os.path.join(KT_ROOT, "date", "partnerend")

PRONOUN = re.compile(r"\b(she|he|her|his|him|hers)\b", re.I)
B_BANNED = ("unsafe",)
A_BANNED = ("less visible", "lose App access")
IDS = (("date_partner_ended_no_show", "Date - Partner ended no-show"),
       ("date_partner_ended_uncomfortable", "Date - Partner ended uncomfortable"),
       ("date_partner_ended_met", "Date - Partner ended met"))

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


# A line that holds an identifier, not words a person reads: a test tag, an accessibility id, a screen id.
NON_COPY = re.compile(r"testTag|accessibilityIdentifier|screen_?id|\bid\s*[:=]|\bTAG\b", re.I)


def copy_literals(source):
    """The copy's string literals in file order, comments removed, lines of identifiers left out."""
    out = []
    for line in strip_comments(source).split("\n"):
        if not NON_COPY.search(line):
            out += re.findall(r'"((?:[^"\\]|\\.)*)"', line)
    return out


def literal_stream(source):
    """The literals joined with nothing between: a bold run is its own constant, and the pieces read in order are the
    ticket's string."""
    return "".join(copy_literals(source))


def words_stream(source):
    """The literals joined with a newline, for the word rules: a run that is a word on its own reads as one, and two
    literals never fuse into a word neither holds."""
    return "\n".join(copy_literals(source))


def app_files(root, suffix, only=None):
    out = []
    for d, _, names in os.walk(root):
        if os.sep + "build" in d or os.sep + ".build" in d:
            continue
        out += [os.path.join(d, n) for n in names if n.endswith(suffix) and (only is None or only(n))]
    return sorted(out)


PH = r"(?:%s|%d|\$\{[^}]*\}|\$\w+|\\\([^)]*\))"
MARKS = re.compile(r"\*\*|\*")
HOLE = re.compile(r"\{[^}]*\}")


def pattern(s):
    return PH.join(re.escape(part) for part in HOLE.split(s))


def missing_from(strings, source, label):
    return ["ticket copy on %s: %r" % (label, s) for s in strings
            if not re.search(pattern(MARKS.sub("", s)), source)]


def group_of(src, start, end):
    """The literals between two `// X .` section comments of a copy file, cut before comments are stripped."""
    a = src.index(start)
    b = src.index(end, a) if end else len(src)
    return src[a:b]


def banned_in(text, group):
    """`text` is a words_stream, a ticket section (markdown), or the literals fused."""
    if group == "B":
        return [m.group(0) for m in PRONOUN.finditer(text)] + [w for w in B_BANNED if w in text]
    return re.findall(r"\d", text) + [w for w in A_BANNED if w in text]


def rules_hits(region_src, group):
    """Both readings of a copy region: literals apart (newline) and literals fused (as the screen draws them)."""
    return banned_in(words_stream(region_src), group) + banned_in(literal_stream(region_src), group)


def decls(src, kind):
    code = strip_comments(src)
    pat = r"\bfun PartnerEndedScreen\(" if kind == "kt" else r"\bstruct PartnerEndedScreen\b"
    return len(re.findall(pat, code))


def ids_missing(src, kind):
    """Each id and name, as the Kotlin enum writes them (`"id", "name"`) or the Swift case and its name table."""
    out = []
    for sid, name in IDS:
        if kind == "kt":
            if not re.search(r'"%s"\s*,\s*"%s"' % (re.escape(sid), re.escape(name)), src):
                out.append(sid)
        else:
            if '"%s"' % sid not in src:
                out.append(sid)
            if '"%s"' % name not in src:
                out.append(name)
    return out


DATE_ENDED_ANY = re.compile(r"\bdate_?ended\b", re.I)
# In the routing files `.dateEnded(...)` / `AppRoute.DateEnded` is a route to the Date 06 screen, not an event, so only
# the spellings an event takes count there: the literal, UPPER_SNAKE, a builder, or a tracker call that names it
# (a `case dateEnded(` / `class DateEnded(` declaration is the route itself).
DATE_ENDED_EVENT = re.compile(
    r'"date_ended"|\bDATE_ENDED\b|\b(?:fun|func) dateEnded\b|(?-i:(?<![.\w])(?<!case )(?<!class )dateEnded\()'
    r"|(?:Analytics|Events?)\.dateEnded\b|\b(?:report|track|fire|emit|log)\w*\([^)]*\bdate_?ended\b", re.I)


def fires_date_ended(src, strict=True):
    """strict: Date 08's own files, where any spelling of the event name is wrong. Otherwise a routing file."""
    code = strip_comments(src)
    return bool((DATE_ENDED_ANY if strict else DATE_ENDED_EVENT).search(code))


# the sources
kt_copy_src = read(os.path.join(KT_DIR, "PartnerEndedCopy.kt"))
sw_copy_src = read(os.path.join(SW, "PartnerEndedCopy.swift"))
kt_copy = literal_stream(kt_copy_src)
sw_copy = literal_stream(sw_copy_src)
check("both copy files were read (%d / %d literal characters)" % (len(kt_copy), len(sw_copy)),
      len(kt_copy) > 500 and len(sw_copy) > 500)

# the ticket's copy, run by run
ticket = read(TICKET)
section = ticket.split("## Copy", 1)[1].split("\n## ", 1)[0]
quoted = re.findall(r"`([^`]+)`", section)
strings = [q for q in dict.fromkeys(quoted) if not HOLE.fullmatch(q)]
EXEMPT = {}
DECIDED = {}
strings = [s for s in strings if s not in EXEMPT and s not in DECIDED]
check("the ticket's copy section was read (%d strings)" % len(strings), len(strings) >= 16)
for want in ("Proceed to review", "The date is now closed.", "Thank you for being real and showing up to your date."):
    check("the ticket spells %r" % want, want in strings)

kt_missing = missing_from(strings, kt_copy, "Android")
sw_missing = missing_from(strings, sw_copy, "iOS")
check("every ticket string is in Android's copy file, exactly (%d strings)" % len(strings), not kt_missing)
check("every ticket string is in iOS's copy file, exactly (%d strings)" % len(strings), not sw_missing)
failures.extend(kt_missing + sw_missing)

# B and A word rules, on the apps' own sections and the ticket's
t_a = section.split("**A · You didn't show up:**", 1)[1].split("**B · Uncomfortable:**", 1)[0]
t_b = section.split("**B · Uncomfortable:**", 1)[1].split("**C · We met:**", 1)[0]
kt_a = group_of(kt_copy_src, "// A ·", "// B ·")
sw_a = group_of(sw_copy_src, "// A ·", "// B ·")
kt_b = group_of(kt_copy_src, "// B ·", "// C ·")
sw_b = group_of(sw_copy_src, "// B ·", "// C ·")
check("B's sections were found in every source", all(len(x) > 200 for x in (t_b, kt_b, sw_b)))
check("A's sections were found in every source", all(len(x) > 200 for x in (t_a, kt_a, sw_a)))
check("B on the ticket holds no gendered pronoun and no 'unsafe'", not banned_in(t_b, "B"))
check("A on the ticket holds no digit, no 'less visible', no 'lose App access'", not banned_in(t_a, "A"))
for label, text in (("Android", kt_b), ("iOS", sw_b)):
    check("B on %s holds no gendered pronoun and no 'unsafe' (literals apart and fused)" % label,
          not rules_hits(text, "B"))
for label, text in (("Android", kt_a), ("iOS", sw_a)):
    check("A on %s holds no digit, no 'less visible', no 'lose App access' (literals apart and fused)" % label,
          not rules_hits(text, "A"))

# one screen, the section 11 ids, no server event
kt_files = app_files(KT_ROOT, ".kt")
sw_files = app_files(SW, ".swift")
kt_all = "\n".join(read(p) for p in kt_files)
sw_all = "\n".join(read(p) for p in sw_files)
check("the sources were found (%d / %d)" % (len(kt_files), len(sw_files)), len(kt_files) >= 100 and len(sw_files) >= 100)
check("Android: exactly one PartnerEndedScreen (found %d)" % decls(kt_all, "kt"), decls(kt_all, "kt") == 1)
check("iOS: exactly one PartnerEndedScreen (found %d)" % decls(sw_all, "sw"), decls(sw_all, "sw") == 1)

kt_prof = read(os.path.join(KT_ROOT, "profile", "ProfileAnalytics.kt"))
sw_prof = read(os.path.join(SW, "ProfileAnalytics.swift"))
check("Android: the three section 11 ids and names are in ProfileScreen", not ids_missing(kt_prof, "kt"))
check("iOS: the three section 11 ids and names are in ProfileScreen", not ids_missing(sw_prof, "sw"))

kt_d8 = "\n".join(read(p) for p in app_files(KT_DIR, ".kt"))
sw_d8 = "\n".join(read(p) for p in app_files(SW, ".swift", lambda n: n.startswith("PartnerEnded")))
check("Android: Date 08 code was found (%d characters)" % len(kt_d8), len(kt_d8) > 2000)
check("iOS: Date 08 code was found (%d characters)" % len(sw_d8), len(sw_d8) > 2000)
# The routing files send a partner's end here, so a date_ended fired from them is Date 08's too; both sets mirror.
KT_ROUTING = [os.path.join(KT_ROOT, *q.split("/")) for q in
              ("AppRoute.kt", "MainActivity.kt", "chat/ChatRouting.kt", "date/cancel/CancelRules.kt")]
SW_ROUTING = [os.path.join(SW, n) for n in
              ("AppRoute.swift", "ShowUpWelcomeApp.swift", "ChatRouting.swift", "CancelRules.swift")]
kt_rt = chr(10).join(read(q) for q in KT_ROUTING)
sw_rt = chr(10).join(read(q) for q in SW_ROUTING)
check("both routing sets were found (%d / %d characters)" % (len(kt_rt), len(sw_rt)),
      len(kt_rt) > 5000 and len(sw_rt) > 5000)
check("Android: Date 08 never fires date_ended", not fires_date_ended(kt_d8))
check("iOS: Date 08 never fires date_ended", not fires_date_ended(sw_d8))
check("Android: the routing files never fire date_ended", not fires_date_ended(kt_rt, strict=False))
check("iOS: the routing files never fire date_ended", not fires_date_ended(sw_rt, strict=False))

# it can fail: one negative self-test per group
victim = "Thank you for being real"
check("the mutation check has a string to remove", victim in kt_copy and victim in sw_copy)
check("the copy check reports a string removed from the Android copy (mutation)",
      any("being real" in m for m in missing_from(strings, kt_copy.replace(victim, ""), "Android")))
check("the copy check reports a string removed from the iOS copy (mutation)",
      any("being real" in m for m in missing_from(strings, sw_copy.replace(victim, ""), "iOS")))
check("the copy check reports an apostrophe changed to typographic (mutation)",
      any("It's now closed" in m for m in missing_from(strings, kt_copy.replace("It's now", "It’s now"), "Android")))
check("the copy check reports a missing placeholder (mutation)",
      any("ended the date, saying" in m for m in
          missing_from(strings, kt_copy.replace("%s ended the date, saying", "ended the date, saying"), "Android")))
check("the pronoun check reports 'she' but not 'the' (mutation)",
      "she" in rules_hits(kt_b + '\nconst val X = "so she said"\n', "B")
      and not rules_hits('const val X = "the other one"', "B"))
check("the pronoun check reports a pronoun that sits across two literals (mutation)",
      "he" in rules_hits('listOf(p("t"), p("he"))', "B") and "his" in rules_hits('listOf(p("hi"), p("s"))', "B"))
check("the B check reports 'unsafe' (mutation)", "unsafe" in rules_hits(sw_b + '\nlet x = "It was unsafe."\n', "B"))
check("the A check reports a digit and 'less visible' (mutation)",
      "3" in rules_hits(kt_a + '\nconst val X = "in 3 days"\n', "A")
      and "less visible" in rules_hits(sw_a + '\nlet x = "less visible"\n', "A"))
check("the A check ignores a digit in a test tag, an accessibility id or a screen id (mutation)",
      not rules_hits(kt_a + '\nModifier.testTag("a1b2")\n', "A")
      and not rules_hits(sw_a + '\n.accessibilityIdentifier("step3")\n', "A")
      and not rules_hits(kt_a + '\nval screenId = "s9"\n', "A"))
check("the single-screen check reports a second PartnerEndedScreen (mutation)",
      decls(kt_all + "\nfun PartnerEndedScreen(x: Int) {}\n", "kt") == 2
      and decls(sw_all + "\nstruct PartnerEndedScreen: View {}\n", "sw") == 2)
check("the id check reports a missing id (mutation)",
      ids_missing(kt_prof.replace("date_partner_ended_met", "x"), "kt") == ["date_partner_ended_met"]
      and ids_missing(sw_prof.replace("Date - Partner ended met", "x"), "sw") == ["Date - Partner ended met"])
check("the date_ended check reports one fired and ignores a comment (mutation)",
      fires_date_ended(kt_d8 + '\nval e = "date_ended"\n') and fires_date_ended(sw_d8 + "\nfunc dateEnded() {}\n")
      and not fires_date_ended('// "date_ended" is the server\'s\n'))
for spelling in ('val e = "date_ended"', "val e = DATE_ENDED", "tracker.dateEnded(x)", "dateEnded(x)",
                 "func dateEnded() {}", "fun dateEnded() {}", "analytics.report(.dateEnded)", "Events.dateEnded"):
    check("the date_ended check reports %r in Date 08 code (mutation)" % spelling, fires_date_ended(spelling))
for spelling in ('val e = "date_ended"', "val e = DATE_ENDED", "dateEnded(x)", "func dateEnded() {}",
                 "analytics.report(.dateEnded)", "Events.dateEnded"):
    check("the date_ended check reports %r in a routing file (mutation)" % spelling,
          fires_date_ended(spelling, strict=False))
check("the routing check accepts a route case, its constructor and the Android route (mutation)",
      not fires_date_ended("return .dateEnded(outcome, photoURL: u)\ncase .dateEnded, .partnerCancelled:\n"
                           "val r = AppRoute.DateEnded(1)\ndata class DateEnded(\ncase dateEnded(EndedOutcome)\ncomposable<AppRoute.DateEnded>(\n", strict=False))

print("partner-end conformance: %d checks" % count)
if failures:
    for name in failures:
        print("  x " + name)
    sys.exit(1)
print("all passed")
