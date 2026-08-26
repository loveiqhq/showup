# -*- coding: utf-8 -*-
"""A structural sanity check for the Swift sources.

This is NOT a compiler and cannot replace one. SwiftUI and UIKit ship only with Xcode, on macOS, so
nothing on a Windows machine can resolve `import SwiftUI` — no type checking, no API validation, no
overload resolution. The first real compile happens when someone opens the project on a Mac.

What it does catch is the class of error most likely in hand-authored code that has never been near
a compiler: an unbalanced brace, a paren that never closes, an unterminated string. Those are the
errors that produce a wall of confusing messages, because the parser loses its place and every
following line looks wrong too.

    python audit/check-swift-structure.py
"""
import io
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "ios-app", "ShowUpWelcome")

problems = []
checked = 0


def scan(path):
    """Walk the file character by character, tracking whether we are inside a string or comment.

    Naive bracket counting is useless here: a brace inside a string literal or a comment is not a
    brace. Swift also has string interpolation — \\( ... ) — whose parens must balance too, and
    nested block comments, which C-style languages do not have.
    """
    src = io.open(path, encoding="utf-8").read()
    stack = []           # (char, line) of every open bracket
    line = 1
    i = 0
    n = len(src)
    in_line_comment = False
    block_depth = 0
    in_string = False
    in_multiline_string = False
    string_start = 0
    PAIRS = {")": "(", "]": "[", "}": "{"}

    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""

        if c == "\n":
            line += 1
            if in_line_comment:
                in_line_comment = False
            if in_string and not in_multiline_string:
                problems.append((path, string_start, "string literal not closed before end of line"))
                in_string = False
            i += 1
            continue

        if in_line_comment:
            i += 1
            continue

        if block_depth > 0:
            if c == "/" and nxt == "*":
                block_depth += 1
                i += 2
                continue
            if c == "*" and nxt == "/":
                block_depth -= 1
                i += 2
                continue
            i += 1
            continue

        if in_string:
            if c == "\\":
                # string interpolation: the parens inside must balance
                if nxt == "(":
                    depth = 1
                    i += 2
                    while i < n and depth:
                        if src[i] == "(":
                            depth += 1
                        elif src[i] == ")":
                            depth -= 1
                        elif src[i] == "\n":
                            line += 1
                        i += 1
                    continue
                i += 2          # any other escape
                continue
            if in_multiline_string:
                if src[i:i + 3] == '"""':
                    in_string = in_multiline_string = False
                    i += 3
                    continue
            elif c == '"':
                in_string = False
                i += 1
                continue
            i += 1
            continue

        # not in a string or comment
        if c == "/" and nxt == "/":
            in_line_comment = True
            i += 2
            continue
        if c == "/" and nxt == "*":
            block_depth = 1
            i += 2
            continue
        if src[i:i + 3] == '"""':
            in_string = in_multiline_string = True
            string_start = line
            i += 3
            continue
        if c == '"':
            in_string = True
            string_start = line
            i += 1
            continue
        if c in "([{":
            stack.append((c, line))
        elif c in ")]}":
            if not stack:
                problems.append((path, line, "closing '%s' with nothing open" % c))
            elif stack[-1][0] != PAIRS[c]:
                op, ol = stack.pop()
                problems.append((path, line,
                                 "closing '%s' does not match '%s' opened on line %d" % (c, op, ol)))
            else:
                stack.pop()
        i += 1

    if in_string:
        problems.append((path, string_start, "string literal never closed"))
    if block_depth:
        problems.append((path, line, "block comment never closed"))
    for op, ol in stack:
        problems.append((path, ol, "'%s' opened here and never closed" % op))
    return src


# ── run ─────────────────────────────────────────────────────────────────────
print("Structural check of the Swift sources")
print("  NOT a compiler — see the note at the top of this file.")
print()

for name in sorted(os.listdir(SRC)):
    if not name.endswith(".swift"):
        continue
    path = os.path.join(SRC, name)
    src = scan(path)
    checked += 1

    # every View needs a body
    for i, ln in enumerate(src.split(chr(10)), 1):
        t = ln.strip()
        if t.startswith("struct ") and ": View" in t and "{" in t:
            after = src.split(ln, 1)[1]
            head = after[:after.find(chr(10) + "}")] if (chr(10) + "}") in after else after
            if "var body" not in head:
                problems.append((path, i, "a View with no `var body`"))

    print("  %-32s %4d lines" % (name, src.count(chr(10)) + 1))

print()
if problems:
    print("FOUND %d structural problem(s):" % len(problems))
    for path, line, msg in problems:
        print("  %s:%d  %s" % (os.path.basename(path), line, msg))
    sys.exit(1)

print("%d files: brackets, parens, braces, string literals and interpolation all balance." % checked)
print()
print("What this does NOT tell you:")
print("  · whether the SwiftUI APIs are used correctly")
print("  · whether the types line up")
print("  · whether it renders as intended")
print("Only Xcode on a Mac can answer those.")
