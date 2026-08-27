# -*- coding: utf-8 -*-
"""Check that every call site passes arguments in declaration order.

Swift builds a struct's memberwise initialiser from its stored properties **in the order they are
declared**, and call sites must match that order. A parameter's position in the struct is therefore
part of its contract, not a style choice.

Kotlin does not work this way — named arguments may appear in any order — so a script that inserts a
parameter into both platforms at the same textual position will produce valid Kotlin and invalid
Swift. That is exactly the bug this was written for: `underlineWidth` was inserted after `eyebrow`
at every call site while being declared after `showBack`. Android compiled it happily; Xcode
rejected it, and the structural checker could not see it because the brackets all balanced.

    python audit/check-swift-arg-order.py
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "ios-app", "ShowUpWelcome")

problems = []
NL = chr(10)


def strip_comments(src):
    out = []
    for line in src.split(NL):
        t = line.lstrip()
        if t.startswith("//") or t.startswith("*") or t.startswith("/*"):
            continue
        out.append(line)
    return NL.join(out)


def declarations(src):
    """{struct name: [parameter names, in declaration order]} for memberwise-initialised structs."""
    found = {}
    for m in re.finditer(r"^(?:public )?struct (\w+)[^{]*\{", src, re.M):
        name = m.group(1)
        # walk the body to its closing brace
        depth, i = 1, m.end()
        while i < len(src) and depth:
            if src[i] == "{":
                depth += 1
            elif src[i] == "}":
                depth -= 1
            i += 1
        body = src[m.end():i - 1]
        params = []
        for line in body.split(NL):
            t = line.strip()
            # stored properties at the top level of the struct, before any computed member
            d = re.match(r"^(?:@ViewBuilder\s+)?(?:let|var) (\w+)\s*:", t)
            if d and "{" not in t and "func " not in t:
                params.append(d.group(1))
        if params:
            found[name] = params
    return found


def call_sites(src, struct_names):
    """(struct, [labels passed], line) for each call that uses argument labels."""
    calls = []
    for name in struct_names:
        for m in re.finditer(r"\b" + name + r"\(", src):
            depth, i = 1, m.end()
            while i < len(src) and depth:
                if src[i] in "([{":
                    depth += 1
                elif src[i] in ")]}":
                    depth -= 1
                i += 1
            inner = src[m.end():i - 1]
            # labels at this call's own nesting level only
            labels, d = [], 0
            for tok in re.finditer(r"[([{]|[)\]}]|(\w+)\s*:", inner):
                t = tok.group(0)
                if t and t[0] in "([{":
                    d += 1
                elif t and t[0] in ")]}":
                    d -= 1
                elif d == 0 and tok.group(1):
                    labels.append(tok.group(1))
            if labels:
                calls.append((name, labels, src[:m.start()].count(NL) + 1))
    return calls


all_decls = {}
sources = {}
for fn in sorted(os.listdir(SRC)):
    if fn.endswith(".swift"):
        raw = strip_comments(io.open(os.path.join(SRC, fn), encoding="utf-8").read())
        sources[fn] = raw
        all_decls.update(declarations(raw))

print("Argument-order check")
print("  structs with a memberwise init: %d" % len(all_decls))
print()

checked = 0
for fn, src in sources.items():
    for struct, labels, line in call_sites(src, all_decls):
        order = all_decls[struct]
        known = [l for l in labels if l in order]
        if len(known) < 2:
            continue
        checked += 1
        positions = [order.index(l) for l in known]
        if positions != sorted(positions):
            # name the first label that arrives too early
            bad = next(known[i] for i in range(1, len(positions))
                       if positions[i] < positions[i - 1])
            problems.append((fn, line, struct, bad,
                             " -> ".join(known), " -> ".join(order)))

print("  call sites checked: %d" % checked)
print()
if problems:
    print("FOUND %d ordering problem(s):" % len(problems))
    for fn, line, struct, bad, got, want in problems:
        print("  %s:%d  %s(...)" % (fn, line, struct))
        print("      '%s' is passed out of order" % bad)
        print("      passed:   %s" % got)
        print("      declared: %s" % want)
    sys.exit(1)

print("Every call site passes its arguments in declaration order.")
