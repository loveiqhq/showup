# -*- coding: utf-8 -*-
"""Write the generated parts of docs/tracking-events.md, and docs/tracking-events.csv, from the code.

    python audit/gen-tracking-catalogue.py           # rewrite both files
    python audit/gen-tracking-catalogue.py --check   # exit 1 if either is out of date

WHY IT IS GENERATED

The catalogue was hand-written on 7 September 2026 with 35 events. Six weeks later the apps had 99
client events, the catalogue still listed 27 of them, and nothing had failed: its checker only read
the two catalogues that existed when it was written. A hand-kept list of a hundred events is a list
that drifts, and a stale row does not look stale -- it looks like a specification.

So the tables are read out of the source. What each event is NAMED, what it CARRIES and WHETHER IT
FIRES come from the code; "when it fires" is the registry's own wording
(design_handoff_showup/tracking/events.json), because that sentence is the specification the code
implements and the product side already reads it there. The prose around the tables is hand-written
and lives between the generated blocks.

WHAT "FIRES" MEANS HERE

An event fires on a platform when something outside its catalogue file uses it: the event's constant,
or a builder that returns it. Defined and never used is reported as such, because a funnel planned
on an event nothing sends reports zero forever.

Properties are read from the Android builders. check-analytics-parity.py already holds the two
platforms to the same names, and this file checks each event is also USED on iOS.
"""
import csv
import io
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(HERE)
REPO = os.path.dirname(os.path.dirname(MOBILE))

KT = os.path.join(MOBILE, "android-preview-project", "app", "src", "main", "java", "com", "showup")
SW = os.path.join(MOBILE, "ios-app", "ShowUpWelcome")
SERVER = os.path.join(REPO, "src", "modules", "analytics", "events")
REGISTRY = os.path.join(REPO, "design_handoff_showup", "tracking", "events.json")
DOC = os.path.join(REPO, "docs", "tracking-events.md")
CSV = os.path.join(REPO, "docs", "tracking-events.csv")

# (area, Kotlin object, Kotlin file, Swift object, Swift file)
CATALOGUES = [
    ("Sign-up and re-login", "SignUpAnalytics", "analytics/SignUpAnalytics.kt",
     "SignUpAnalytics", "SignUpAnalytics.swift"),
    ("Tutorial", "TutorialAnalytics", "tutorial/TutorialAnalytics.kt",
     "TutorialAnalytics", "TutorialAnalytics.swift"),
    ("Profile creation", "ProfileAnalytics", "profile/ProfileAnalytics.kt",
     "ProfileAnalytics", "ProfileAnalytics.swift"),
    ("48hrs Check-In", "CheckInAnalytics", "checkin/CheckInAnalytics.kt",
     "CheckInAnalytics", "CheckInAnalytics.swift"),
]
STAMP_KEYS = ["sensitivity_class", "field_registry_version"]


def read(path):
    return io.open(path, encoding="utf-8").read()


def strip_comments(src):
    """Comments out, strings kept: a constant named in a comment is not a use of it."""
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    return re.sub(r"//[^\n]*", "", src)


def sources(root, ext):
    for dirpath, _, files in os.walk(root):
        for fn in files:
            if fn.endswith(ext):
                yield os.path.join(dirpath, fn)


def walk_registry(node, family=None):
    if isinstance(node, dict):
        if "id" in node and "events" in node and isinstance(node["events"], list):
            family = (node["id"], node.get("name", ""))
        if "name" in node and "implemented" in node:
            yield family, node
        for v in node.values():
            yield from walk_registry(v, family)
    elif isinstance(node, list):
        for v in node:
            yield from walk_registry(v, family)


REGISTRY_EVENTS = {}
for fam, ev in walk_registry(json.load(io.open(REGISTRY, encoding="utf-8"))):
    REGISTRY_EVENTS.setdefault(ev["name"], (fam, ev))


def first_sentence(text, limit=170):
    text = re.sub(r"\s+", " ", (text or "").strip())
    m = re.match(r"(.+?[.;])(\s|$)", text)
    out = m.group(1) if m else text
    if len(out) > limit:
        out = out[:limit].rsplit(" ", 1)[0] + " ..."
    return out.rstrip(";")


# ── Kotlin ────────────────────────────────────────────────────────────────────────────────────────

def kotlin_blocks(src):
    """(name, text) for every `fun` in the file, each running to the next member or the object's end."""
    starts = [m for m in re.finditer(r"^\s*(?:internal |private )?fun (\w+)\(", src, re.M)]
    out = []
    for i, m in enumerate(starts):
        end = len(src)
        # Ends at the next member, or the object's own closing brace at column 0. NOT at a `}` four
        # spaces in: `= when (kind) { ... } to buildMap { ... }` closes its `when` there and goes on.
        nxt = re.search(r"\n\s{0,4}(?:/\*\*|(?:internal |private )?fun |const val |object |enum class |"
                        r"data class |class |@|val )|\n\}", src[m.end():])
        if nxt:
            end = m.end() + nxt.start()
        out.append((m.group(1), src[m.start():end]))
    return out


def kotlin_keys(block, helpers, seen=None):
    seen = seen or set()
    keys = re.findall(r'put\(\s*"([a-z][a-z0-9_]*)"', block)
    keys += re.findall(r'"([a-z][a-z0-9_]*)"\s+to\b', block)
    if "Stamp.of(" in block:
        keys += STAMP_KEYS
    for helper in re.findall(r"putAll\(\s*(\w+)\(", block) + re.findall(r"\+\s*(\w+)\(", block):
        if helper in helpers and helper not in seen:
            seen.add(helper)
            keys += kotlin_keys(helpers[helper], helpers, seen)
    return keys


def ordered(keys):
    out = []
    for k in keys:
        if k not in out:
            out.append(k)
    return out


def parse_kotlin(obj, path):
    raw = read(path)
    src = strip_comments(raw)
    consts = re.findall(r'const val (\w+)\s*=\s*"([^"]+)"', src)
    lower = [(k, v) for k, v in consts if re.fullmatch(r"[a-z][a-z0-9_]+", v)]
    used_as_event = set(re.findall(r"\b([A-Z][A-Z0-9_]+)\s+to\b", src))
    ident_of = {}
    for k, v in lower:
        if k in used_as_event or v in REGISTRY_EVENTS:
            ident_of.setdefault(v, set()).add(k)
    blocks = kotlin_blocks(src)
    helpers = {name: text for name, text in blocks}
    props = {v: [] for v in ident_of}
    builders = {v: set() for v in ident_of}
    generic = {}
    for name, text in blocks:
        # Only a function that returns an event and its payload is a builder. `allEventNames()`
        # names every event and sends none.
        if not re.search(r"\bto\b", text):
            continue
        # Every event the builder names, not only the one before `to`: media picks VIDEO_ or VOICE_
        # in a `when`, Life and Future pick theirs in an `if`, and both are real builders.
        served = [v for v, idents in ident_of.items() if any(re.search(r"\b%s\b" % i, text) for i in idents)]
        g = re.search(r"=\s*(?:\n\s*)?([a-z]\w*)\s+to\s+(?:buildMap|mapOf|emptyMap|mutableMapOf)", text)
        if served:
            for value in served:
                props[value] += kotlin_keys(text, helpers)
                builders[value].add(name)
        elif g:
            generic[name] = kotlin_keys(text, helpers)
    # `val meet = props(2, "meet")`: a ready-made payload the call site pairs with an event.
    payloads = {}
    for val, helper in re.findall(r"^\s*val (\w+)\s*=\s*(\w+)\(", src, re.M):
        if helper in helpers:
            payloads[val] = kotlin_keys(helpers[helper], helpers)
    return ident_of, props, builders, generic, payloads


def kotlin_usage(obj, path, ident_of, builders, generic, props, payloads):
    """Which events something outside the catalogue file uses, and the generic builders' events."""
    others = [p for p in sources(KT, ".kt") if os.path.abspath(p) != os.path.abspath(path)]
    text = "\n".join(strip_comments(read(p)) for p in others)
    fires = {}
    for value, idents in ident_of.items():
        hit = any(re.search(r"\b%s\.%s\b" % (obj, i), text) or re.search(r"\b%s\.\w+\.%s\b" % (obj, i), text)
                  for i in idents)
        hit = hit or any(re.search(r"\b%s\.%s\(" % (obj, b), text) for b in builders[value])
        fires[value] = hit
        # `track(TutorialAnalytics.CARD_VIEWED, TutorialAnalytics.meet)`: the payload is the val.
        for i in idents:
            for val in re.findall(r"\b%s\.%s\s*(?:,|\s+to\s+)\s*(?:%s\.)?(\w+)" % (obj, i, obj), text):
                props[value] += payloads.get(val, [])
    # A generic builder, `provider(event, provider)`: its keys go to every event it is called with.
    for name, keys in generic.items():
        for m in re.finditer(r"\b%s\.%s\(\s*(?:%s\.)?([A-Z][A-Z0-9_]+)" % (obj, name, obj), text):
            for value, idents in ident_of.items():
                if m.group(1) in idents:
                    props[value] += keys
                    fires[value] = True
    return fires


# ── Swift ─────────────────────────────────────────────────────────────────────────────────────────

def swift_usage(obj, path, values):
    src = strip_comments(read(path))
    consts = re.findall(r'static let (\w+)(?:\s*:\s*String)?\s*=\s*"([^"]+)"', src)
    idents = {}
    for k, v in consts:
        idents.setdefault(v, set()).add(k)
    funcs = []
    for m in re.finditer(r"static func (\w+)\(", src):
        # To the function's own closing brace, at its own indent -- not on into the constants
        # after it, which made every builder look like it sent every event.
        end = re.search(r"\n\s{0,4}\}", src[m.end():])
        body = src[m.start():m.end() + (end.start() if end else len(src))]
        # Only a function returning (event, payload) is a builder; `allEventNames()` is a list.
        if re.search(r"->\s*\(String,", body):
            funcs.append((m.group(1), body))
    others = [p for p in sources(SW, ".swift") if os.path.abspath(p) != os.path.abspath(path)]
    text = "\n".join(strip_comments(read(p)) for p in others)
    defined, fires = {}, {}
    for value in values:
        ids = idents.get(value, set())
        defined[value] = bool(ids)
        hit = any(re.search(r"\b%s\.%s\b" % (obj, i), text) for i in ids)
        if not hit:
            for name, body in funcs:
                if any(re.search(r"\b%s\b" % i, body) for i in ids):
                    if re.search(r"\b%s\.%s\(" % (obj, name), text):
                        hit = True
                        break
        fires[value] = hit
    return defined, fires


# ── server ────────────────────────────────────────────────────────────────────────────────────────

def server_events():
    out = []
    for fn in sorted(os.listdir(SERVER)):
        if fn.endswith(".ts") and not fn.endswith(".spec.ts"):
            out += re.findall(r"export const \w+ = '([a-z][a-z_]+)';", read(os.path.join(SERVER, fn)))
    return out


# ── screens ───────────────────────────────────────────────────────────────────────────────────────

def screens():
    rows = []
    signup = strip_comments(read(os.path.join(KT, "analytics", "SignUpAnalytics.kt")))
    block = re.search(r"object Screen \{(.*?)\n    \}", signup, re.S).group(1)
    for ident, value in re.findall(r'const val (\w+)\s*=\s*"([^"]+)"', block):
        rows.append(("Sign-up", value, "", ident))
    profile = strip_comments(read(os.path.join(KT, "profile", "ProfileAnalytics.kt")))
    enum = re.search(r"enum class ProfileScreen\(.*?\)\s*\{(.*?)\n\}", profile, re.S).group(1)
    for ident, sid, name in re.findall(r'^\s*(\w+)\("([a-z0-9_]+)",\s*"([^"]+)"\)', enum, re.M):
        rows.append(("Profile and Check-In", name, sid, ident))
    return rows


# ── build ─────────────────────────────────────────────────────────────────────────────────────────

def build():
    events = []  # dicts
    for area, kobj, kfile, sobj, sfile in CATALOGUES:
        kpath = os.path.join(KT, *kfile.split("/"))
        spath = os.path.join(SW, sfile)
        ident_of, props, builders, generic, payloads = parse_kotlin(kobj, kpath)
        k_fires = kotlin_usage(kobj, kpath, ident_of, builders, generic, props, payloads)
        s_defined, s_fires = swift_usage(sobj, spath, list(ident_of))
        for value in ident_of:
            twin = next((e for e in events if e["event"] == value), None)
            if twin:
                # screen_viewed is in two catalogues: one row, carrying what either one sends.
                twin["properties"] = ordered(twin["properties"] + props.get(value, []))
                continue
            fam, reg = REGISTRY_EVENTS.get(value, (None, None))
            events.append({
                "event": value,
                "area": area,
                "family": fam[0] if fam else "",
                "family_name": fam[1] if fam else "",
                "fires_when": first_sentence(reg.get("trigger")) if reg else "",
                "properties": ordered(props.get(value, [])),
                "android": "fires" if k_fires.get(value) else "defined, not fired",
                "ios": ("fires" if s_fires.get(value) else "defined, not fired") if s_defined.get(value) else "missing",
                "registry_implemented": reg.get("implemented") if reg else None,
                "source": "client",
            })
    for value in server_events():
        fam, reg = REGISTRY_EVENTS.get(value, (None, None))
        events.append({
            "event": value, "area": "Server", "family": fam[0] if fam else "",
            "family_name": fam[1] if fam else "",
            "fires_when": first_sentence(reg.get("trigger")) if reg else "",
            "properties": [], "android": "", "ios": "",
            "registry_implemented": reg.get("implemented") if reg else None, "source": "server",
        })
    return events


def md_cell(text):
    return (text or "").replace("|", "\\|").replace("\n", " ")


def render(events):
    client = [e for e in events if e["source"] == "client"]
    server = [e for e in events if e["source"] == "server"]
    blocks = {}

    # totals
    lines = ["| | Events | Where |", "|---|---|---|"]
    for area, kobj, _, _, _ in CATALOGUES:
        n = sum(1 for e in client if e["area"] == area)
        lines.append("| %s | **%d** | `%s` · Android + iOS |" % (area, n, kobj))
    lines.append("| Server | **%d** | `src/modules/analytics/events/` |" % len(server))
    lines.append("| | **%d** | |" % len(events))
    not_fired = [e for e in client if e["android"] != "fires" or e["ios"] != "fires"]
    lines.append("")
    lines.append("%d client events, %d of them fired on both platforms. `screen_viewed` is counted once, "
                 "under sign-up, though profile creation and the Check-In use it too." %
                 (len(client), len(client) - len(not_fired)))
    blocks["totals"] = "\n".join(lines)

    # screens
    lines = ["| `screen_name` | `screen_id` | Area |", "|---|---|---|"]
    for area, name, sid, _ in screens():
        lines.append("| `%s` | %s | %s |" % (name, "`%s`" % sid if sid else "—", area))
    blocks["screens"] = "\n".join(lines)

    # events, by registry family
    lines = []
    fams = sorted({(e["family"], e["family_name"]) for e in client}, key=lambda f: f[0] or "~")
    for fid, fname in fams:
        rows = [e for e in client if e["family"] == fid]
        lines.append("### %s · %s (%d)" % (fid or "?", fname or "not in the registry", len(rows)))
        lines.append("")
        lines.append("| Event | When it fires (registry wording) | Properties (from the code) | Android | iOS |")
        lines.append("|---|---|---|---|---|")
        for e in sorted(rows, key=lambda r: r["event"]):
            props = ", ".join("`%s`" % p for p in e["properties"] if p not in STAMP_KEYS) or "—"
            if any(p in STAMP_KEYS for p in e["properties"]):
                props += " + stamp"
            lines.append("| `%s` | %s | %s | %s | %s |" % (
                e["event"], md_cell(e["fires_when"]) or "—", props, e["android"], e["ios"]))
        lines.append("")
    blocks["events"] = "\n".join(lines).rstrip()

    # registry flags that disagree with the code
    lines = []
    behind = [e for e in client if e["android"] == "fires" and e["ios"] == "fires" and e["registry_implemented"] is False]
    ahead = [name for name, (fam, ev) in sorted(REGISTRY_EVENTS.items())
             if ev.get("implemented") is True and ev.get("source", "client") == "client"
             and name not in {e["event"] for e in client}]
    dead = [e for e in client if e["android"] != "fires" or e["ios"] != "fires"]
    lines.append("**Fired on both platforms, but the registry says `implemented: false` (%d):** %s" % (
        len(behind), ", ".join("`%s`" % e["event"] for e in behind) or "none"))
    lines.append("")
    lines.append("**The registry says `implemented: true`, but no client catalogue defines it (%d):** %s" % (
        len(ahead), ", ".join("`%s`" % n for n in ahead) or "none"))
    lines.append("")
    lines.append("**Defined in the code but not fired on one or both platforms (%d):** %s" % (
        len(dead), ", ".join("`%s` (Android %s, iOS %s)" % (e["event"], e["android"], e["ios"]) for e in dead)
        or "none"))
    blocks["registry"] = "\n".join(lines)
    return blocks


def splice(doc, blocks):
    for name, body in blocks.items():
        start, end = "<!-- generated:%s:start -->" % name, "<!-- generated:%s:end -->" % name
        if start not in doc or end not in doc:
            raise SystemExit("docs/tracking-events.md has no %s ... %s block" % (start, end))
        head, rest = doc.split(start, 1)
        _, tail = rest.split(end, 1)
        doc = head + start + "\n" + body + "\n" + end + tail
    return doc


def csv_text(events):
    buf = io.StringIO()
    w = csv.writer(buf, lineterminator="\n")
    w.writerow(["event", "area", "family", "family_name", "fires_when", "properties", "android", "ios",
                "registry_implemented"])
    for e in events:
        w.writerow([e["event"], e["area"], e["family"], e["family_name"], e["fires_when"],
                    " ".join(e["properties"]), e["android"], e["ios"],
                    "" if e["registry_implemented"] is None else str(e["registry_implemented"]).lower()])
    return buf.getvalue()


def main():
    events = build()
    doc = read(DOC)
    new_doc = splice(doc, render(events))
    new_csv = csv_text(events)
    if "--check" in sys.argv:
        stale = [p for p, old, new in ((DOC, doc, new_doc), (CSV, read(CSV), new_csv)) if old != new]
        if stale:
            print("Out of date: " + ", ".join(os.path.relpath(p, REPO) for p in stale))
            print("Run: python mobile/welcome-screen/audit/gen-tracking-catalogue.py")
            sys.exit(1)
        print("Tracking catalogue is up to date (%d events)" % len(events))
        return
    io.open(DOC, "w", encoding="utf-8", newline="\n").write(new_doc)
    io.open(CSV, "w", encoding="utf-8", newline="\n").write(new_csv)
    print("Wrote %s and %s: %d events" % (os.path.relpath(DOC, REPO), os.path.relpath(CSV, REPO), len(events)))


if __name__ == "__main__":
    main()
