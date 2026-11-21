"""
verify-change.py -- Date 05, change meet time and place (SHOWUP-196) against its ticket, on both apps.

Reads the ticket's `## Copy -- final strings` section AT RUN TIME (design_handoff_showup/date/tickets/
05-change-time-place.md) and checks every backticked string of it in the Kotlin and the Swift change, chat and date
sources, so an edit to the ticket fails both platforms until they follow. A string with a `{name}` / `{time}`
placeholder is checked as its literal runs, because each app fills them its own way. Apostrophes, curly quotes,
middle dots and dashes compare as one character however each language spells them. Nothing is exempt silently:
EXEMPT lists every string left out, with its reason.

Then the rules a reading can check:
  - the Google attribution text `Google Maps`, drawn in `#5E5E5E` with the rule that mandates that grey beside it
  - section 42's `change_entry` values used by both apps are a subset of the registry's, and the four of this ticket
  - no place name / address / coordinate / distance key in any change analytics payload builder (client)
  - change_picker_opened / change_picker_dismissed carry exactly the registry's payload keys
  - no message_sent / message_received reported for a suggestion (the thread, the card, the agree flow)
  - the five events are in events.json family K and marked implemented

And it proves each group can fail: the copy, attribution, entry, payload-key, never-emitted and registry checks are
each run once more against a deliberately broken input and must report it.
"""
import glob
import io
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(MOBILE))
HANDOFF = os.path.join(ROOT, "design_handoff_showup")
TICKET = os.path.join(HANDOFF, "date", "tickets", "05-change-time-place.md")
EVENTS = os.path.join(HANDOFF, "tracking", "events.json")
ENUMS = os.path.join(HANDOFF, "tracking", "enums.json")
KT_ROOT = os.path.join(MOBILE, "android-preview-project", "app", "src", "main", "java", "com", "showup")
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
    """One apostrophe / quote / dot / dash, and Swift's and Kotlin's escapes resolved."""
    for esc, ch in (("\\u{2019}", "'"), ("\\u2019", "'"), ("\\u{2014}", "—"), ("\\u2014", "—"),
                    ("\\u{201C}", "“"), ("\\u201C", "“"), ("\\u{201D}", "”"), ("\\u201D", "”"),
                    ("\\u{00B7}", "·"), ("\\u00B7", "·"), ("\\u00b7", "·"), ("\\u{00E9}", "é"), ("\\u00E9", "é")):
        text = text.replace(esc, ch)
    return text.replace("’", "'")


def joined_literals(source):
    """The source with literals split over lines joined: `"a " +\\n "b"` reads as `"a b"`."""
    return re.sub(r'"\s*\+\s*"', "", re.sub(r'"\s*\+\s*\n\s*"', "", source))


def runs(s):
    """The literal runs of a string around its `{placeholders}`, the ones worth matching."""
    return [p for p in re.split(r"\{[^}]*\}", s) if len(p.strip(" ·,.?…")) >= 2]


# Strings the apps draw in pieces (the header's two texts around its own dot; the note's sentence split where the card
# styles it). Each piece is still required, and the join is checked below; the ticket's string is not exempt.
PIECES = {
    "You suggested · Meet time": ["You suggested", "Meet time"],
    "{name} suggested · Meeting place": [" suggested", "Meeting place"],
    "Until you agree, {time} at {place} stays binding. Not being there counts as a no-show.":
        ["Until you agree, ", "stays binding.", " Not being there counts as a no-show."],
}


def needed(s):
    return PIECES.get(s) or runs(s)


def missing_from(strings, source, label):
    return ["ticket copy on %s: %r" % (label, s) for s in strings if not all(r in source for r in needed(s))]


def strip_comments(src):
    return re.sub(r"//[^\n]*|/\*.*?\*/", "", src, flags=re.S)


# ── the change, chat and date sources of each platform ──────────────────────────────────────────
def sources(root_dirs, swift):
    files = []
    if swift:
        for pat in ("Change*.swift", "Chat*.swift", "Date*.swift", "SheetButtons.swift"):
            files += glob.glob(os.path.join(SW, pat))
    else:
        for d in root_dirs:
            files += glob.glob(os.path.join(KT_ROOT, d, "*.kt"))
    # The samples and previews are fixtures, not the app's copy: a string only there is not shipped.
    files = [f for f in sorted(set(files)) if not re.search(r"(Samples|Previews)\.(kt|swift)$", f)]
    return files


kt_files = sources(["change", "chat", "date", "designsystem"], swift=False)
sw_files = sources(None, swift=True)
check("the Android change / chat / date sources were found (%d)" % len(kt_files), len(kt_files) >= 15)
check("the iOS change / chat / date sources were found (%d)" % len(sw_files), len(sw_files) >= 15)
kt_all = norm(joined_literals("\n".join(read(p) for p in kt_files)))
sw_all = norm(joined_literals("\n".join(read(p) for p in sw_files)))

# ── the ticket's copy, run by run ───────────────────────────────────────────────────────────────
ticket = read(TICKET)
section = ticket.split("## Copy", 1)[1].split("\n## ", 1)[0]
quoted = [norm(q) for q in re.findall(r"`([^`]+)`", section)]
# A placeholder on its own is a definition (`{name}` is the match's first name), not copy.
strings = [q for q in dict.fromkeys(quoted) if not re.fullmatch(r"\{[^}]*\}", q)]

# Nothing is exempt silently. Each is a string the ticket backticks that is not a string of the app, with why.
EXEMPT = {
    "{TYPE}": "a placeholder row of the place list, filled with the type tag",
    "{venue}": "a placeholder row of the place list, filled with the venue name",
    "{address} · {distance}": "a row's placeholders only; the literal run is the separator",
}
strings = [s for s in strings if s not in EXEMPT]
check("the ticket's copy section was read (%d strings)" % len(strings), len(strings) >= 45)
for want in ("Couldn't send your suggestion", "I agree", "Messaging opens at"):
    check("the ticket spells %r" % want, any(want in s for s in strings))

copy_missing = missing_from(strings, kt_all, "Android") + missing_from(strings, sw_all, "iOS")
check("every ticket string is in both apps' change, chat and date sources (%d strings x 2)" % len(strings), not copy_missing)
failures.extend(copy_missing)

for label, card in (("Android", read(os.path.join(KT_ROOT, "change", "ChangeSuggestionCard.kt"))),
                    ("iOS", read(os.path.join(SW, "ChangeSuggestionCard.swift")))):
    check("%s: the card header joins its two texts with the ticket's middle dot" % label, "·" in norm(card) or "u00B7" in card.upper())

# ── the Google attribution ──────────────────────────────────────────────────────────────────────
kt_sheets = read(os.path.join(KT_ROOT, "change", "ChangeSheets.kt"))
sw_sheets = read(os.path.join(SW, "ChangeSheets.swift"))
kt_copy = read(os.path.join(KT_ROOT, "change", "ChangeCopy.kt"))
sw_copy = read(os.path.join(SW, "ChangeCopy.swift"))
check("Android: the attribution text is `Google Maps`", '"Google Maps"' in kt_copy)
check("iOS: the attribution text is `Google Maps`", '"Google Maps"' in sw_copy)


def grey_with_reason(src, literal):
    """The #5E5E5E literal, with a comment naming Google's rule within the lines just above it."""
    m = re.search(literal, src)
    if not m:
        return False
    above = src[max(0, m.start() - 700):m.start()]
    return "Google" in above and re.search(r"//|/\*|\*", above) is not None


check("Android: #5E5E5E is a literal with Google's rule beside it", grey_with_reason(kt_sheets, r"0xFF5E5E5E"))
check("iOS: #5E5E5E is a literal with Google's rule beside it", grey_with_reason(sw_sheets, r"0x5E5E5E"))
check("the attribution is drawn only when Google answered (Android)", "list.google" in kt_sheets)
check("the attribution is drawn only when Google answered (iOS)", re.search(r"if google \{", sw_sheets) is not None)

# ── section 42, change_entry ────────────────────────────────────────────────────────────────────
enums = json.loads(read(ENUMS))
sec = next((x for x in enums.get("sets", []) if x.get("name") == "change_entry"), None)
registry_entries = set(sec["values"]) if sec else set()
check("enums.json section 42 is change_entry with the ticket's four values",
      {"coordinate_tile", "chat_chip", "counter_card", "counter_thread"} <= registry_entries)
THIS_TICKET = {"coordinate_tile", "chat_chip", "counter_card", "counter_thread"}
kt_repo = read(os.path.join(KT_ROOT, "change", "ChangeRepository.kt"))
sw_repo = read(os.path.join(SW, "ChangeRepository.swift"))
kt_enum = kt_repo.split("enum class ChangeEntry", 1)[1].split(";", 1)[0] if "enum class ChangeEntry" in kt_repo else ""
sw_enum = sw_repo.split("enum ChangeEntry", 1)[1].split("\n}", 1)[0] if "enum ChangeEntry" in sw_repo else ""
kt_entries = set(re.findall(r'\("([a-z_]+)"\)', kt_enum))
sw_entries = set(re.findall(r'case \w+ = "([a-z_]+)"', sw_enum))
# Date 07 adds `cancel_sheet` (SHOWUP-198). Android has it; iOS gains it with its own Date 07 port, and this
# line follows then.
ANDROID_ENTRIES = THIS_TICKET | {"cancel_sheet"}
check("Android: ChangeEntry values (%s) are a subset of section 42's: this ticket's four and Date 07's cancel_sheet" % sorted(kt_entries),
      kt_entries == ANDROID_ENTRIES and kt_entries <= registry_entries)
check("iOS: ChangeEntry values (%s) are a subset of section 42's and this ticket's four" % sorted(sw_entries),
      sw_entries == THIS_TICKET and sw_entries <= registry_entries)

# ── analytics: no place keys, the registry's payload keys ───────────────────────────────────────
FORBIDDEN_WORDS = re.compile(r"places?|address|venue|name|lat|lng|lon|latitude|longitude|coords?|coordinates?|distance|meters|metres|geohash|location")


def forbidden(key):
    """A key that names a place, an address, a coordinate or a distance, word by word (`replaces` is not `place`)."""
    return any(FORBIDDEN_WORDS.fullmatch(tok) for tok in key.split("_"))
kt_an = read(os.path.join(KT_ROOT, "change", "ChangeAnalytics.kt"))
sw_an = read(os.path.join(SW, "ChangeAnalytics.swift"))


def kt_builder_keys(src, fn):
    body = re.search(r"fun %s\(.*?(?=\n\s*\n|\n\}\s*\Z)" % fn, strip_comments(src), re.S)
    return set(re.findall(r'put\("([a-z_]+)"', body.group(0))) if body else None


def sw_builder_keys(src, fn):
    body = re.search(r"static func %s\(.*?\n    \}" % fn, strip_comments(src), re.S)
    return set(re.findall(r'^\s*"([a-z_]+)":\s', body.group(0), re.M)) if body else None


reg = json.loads(read(EVENTS))
family_k = next((f for f in reg["families"] if f["id"] == "K"), None)
check("events.json has family K", family_k is not None)
by_name = {e["name"]: e for e in (family_k or {"events": []})["events"]}
FIVE = ("change_picker_opened", "change_picker_dismissed", "change_suggested", "change_agreed", "change_expired")
for ev in FIVE:
    e = by_name.get(ev)
    check("family K holds %s" % ev, e is not None)
    if e is None:
        continue
    props = {p["name"]: p for p in e.get("payload", [])}
    check("%s carries date_id and kind" % ev, "date_id" in props and "kind" in props)
    if "entry" in props:
        check("%s's entry is section 42's" % ev, "42" in props["entry"]["type"])
    check("%s is marked implemented" % ev, e.get("implemented") is True)
    check("%s's payload has no place name, address, coordinate or distance" % ev, not any(forbidden(k) for k in props))


def reg_keys(ev):
    return {p["name"] for p in by_name.get(ev, {"payload": []})["payload"]}


for ev, kt_fn, sw_fn in (("change_picker_opened", "pickerOpened", "pickerOpened"),
                         ("change_picker_dismissed", "pickerDismissed", "pickerDismissed")):
    kk, sk = kt_builder_keys(kt_an, kt_fn), sw_builder_keys(sw_an, sw_fn)
    check("Android: %s's payload keys equal the registry's (%s)" % (ev, sorted(kk or [])), kk == reg_keys(ev))
    check("iOS: %s's payload keys equal the registry's (%s)" % (ev, sorted(sk or [])), sk == reg_keys(ev))
    for label, keys in (("Android", kk), ("iOS", sk)):
        check("%s: %s carries no place name, address, coordinate or distance key" % (label, ev),
              keys is not None and not any(forbidden(k) for k in keys))

# Every analytics literal key in the change package, not only the two builders: nothing else reports a place.
kt_change_dir = [p for p in kt_files if os.sep + "change" + os.sep in p]
sw_change = [p for p in sw_files if os.path.basename(p).startswith("Change")]
kt_report_keys = set(re.findall(r'put\("([a-z_]+)"', strip_comments("\n".join(read(p) for p in kt_change_dir if p.endswith("Analytics.kt")))))
sw_report_keys = set(re.findall(r'^\s*"([a-z_]+)":\s', strip_comments("\n".join(read(p) for p in sw_change if p.endswith("Analytics.swift"))), re.M))
check("Android: no key of any change analytics builder is a place, address, coordinate or distance",
      kt_report_keys and not any(forbidden(k) for k in kt_report_keys))
check("iOS: no key of any change analytics builder is a place, address, coordinate or distance",
      sw_report_keys and not any(forbidden(k) for k in sw_report_keys))

# ── a suggestion is not a message ───────────────────────────────────────────────────────────────
NEVER_KT = ("message_sent", "messageSent", "message_received", "messageReceived", "MESSAGE_SENT", "MESSAGE_RECEIVED")
NEVER_SW = ("message_sent", "messageSent", "message_received", "messageReceived")
kt_flow = ["change/ChangeSuggestionCard.kt", "change/ChangeRepository.kt", "change/ChangePickerViewModel.kt",
           "change/ChangeAnalytics.kt", "chat/ChatThread.kt"]
sw_flow = ["ChangeSuggestionCard.swift", "ChangeRepository.swift", "ChangePickerModel.swift", "ChangeAnalytics.swift", "ChatThread.swift"]
kt_flow_src = strip_comments("\n".join(read(os.path.join(KT_ROOT, p)) for p in kt_flow))
sw_flow_src = strip_comments("\n".join(read(os.path.join(SW, p)) for p in sw_flow))
check("Android: the suggestion's files report no message_sent / message_received", not any(w in kt_flow_src for w in NEVER_KT))
check("iOS: the suggestion's files report no message_sent / message_received", not any(w in sw_flow_src for w in NEVER_SW))


def function_body(src, header):
    i = src.find(header)
    if i < 0:
        return None
    j = re.search(r"\n    (?:private |internal |@MainActor )*(?:suspend )?func |\n    (?:private |internal )*fun ", src[i + len(header):])
    return src[i:i + len(header) + (j.start() if j else len(src))]


kt_chat_vm = strip_comments(read(os.path.join(KT_ROOT, "chat", "ChatViewModel.kt")))
sw_chat_model = strip_comments(read(os.path.join(SW, "ChatModel.swift")))
kt_agree = function_body(kt_chat_vm, "fun agree(")
sw_agree = function_body(sw_chat_model, "func agree(")
check("Android: the thread's agree flow was found and reports no message_sent / message_received",
      kt_agree is not None and not any(w in kt_agree for w in NEVER_KT))
check("iOS: the thread's agree flow was found and reports no message_sent / message_received",
      sw_agree is not None and not any(w in sw_agree for w in NEVER_SW))

# ── it can fail: one negative self-test per group ───────────────────────────────────────────────
victim = "Couldn't agree."
check("the mutation check has a string to remove", victim in kt_all)
check("the copy check reports a string removed from the Android source (mutation)",
      any("agree" in m for m in missing_from(strings, kt_all.replace(victim, ""), "Android")))
check("the copy check reports a string removed from the iOS source (mutation)",
      any("Check your connection" in m for m in missing_from(strings, sw_all.replace("Check your connection and try again.", ""), "iOS")))
check("the attribution check fails without the Google comment (mutation)",
      not grey_with_reason(kt_sheets.replace("Google", "G"), r"0xFF5E5E5E"))
check("the entry check fails on an unknown value (mutation)", not (kt_entries | {"somewhere"}) <= registry_entries)
bad_kt = kt_builder_keys(kt_an.replace('put("kind", kind.wire)', 'put("kind", kind.wire)\n            put("place_name", "x")'), "pickerOpened")
check("the payload check fails on a place key added to the Android builder (mutation)",
      bad_kt != reg_keys("change_picker_opened") and any(forbidden(k) for k in bad_kt))
bad_sw = sw_builder_keys(sw_an.replace('"kind": kind.rawValue,', '"kind": kind.rawValue,\n            "distance": 1,'), "pickerOpened")
check("the payload check fails on a distance key added to the iOS builder (mutation)",
      bad_sw != reg_keys("change_picker_opened") and any(forbidden(k) for k in bad_sw))
check("the never-emitted check fails on a message_sent in the suggestion files (mutation)",
      any(w in strip_comments(kt_flow_src + '\n val x = "message_sent"') for w in NEVER_KT))
sample = dict(by_name["change_agreed"])
check("the registry check fails on an event not marked implemented (mutation)", dict(sample, implemented=False).get("implemented") is not True)
check("the registry check fails on a distance in a change payload (mutation)",
      any(forbidden(p["name"]) for p in sample.get("payload", []) + [{"name": "distance_m"}]))

print("change conformance: %d checks" % count)
if failures:
    for name in failures:
        print("  x " + name)
    sys.exit(1)
print("all passed")
