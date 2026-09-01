# -*- coding: utf-8 -*-
"""Swift 6 concurrency rules that can be enforced WITHOUT a compiler.

    python audit/check-swift-concurrency.py

WHAT THIS CAN AND CANNOT DO

It cannot tell you the code is free of data races. Swift 6's checking is whole-module analysis:
whether one line is safe depends on the isolation of every type it touches and on Sendable
conformances declared in other files. No text search decides that. Only the compiler does.

What it CAN do is close the gap that actually causes trouble. The realistic failure is not somebody
writing subtly racy code -- it is somebody silencing the compiler to make a build go green. Swift
gives three ways to do that, and all three are a single searchable token:

    @unchecked Sendable        "trust me, this type is safe to share"
    nonisolated(unsafe)        "trust me, this global is fine"
    @preconcurrency import     "stop checking anything from this module"

Those are forbidden outright here. With them gone, a warning cannot be dismissed -- it can only be
fixed or left visible. That turns "please follow the Swift 6 rules" from an instruction someone
chooses to honour into something mechanical.

Two further patterns are checked because they are the ones Swift 6 rejects most often and both are
greppable: mutable global state, and DispatchQueue in place of structured concurrency.

KNOWN FINDINGS are listed at the bottom. They are reported on every run and do not fail the build,
because fixing concurrency code that has never been compiled means guessing at errors nobody has
read. They are the first entries on the list for the day a Mac exists.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SW = os.path.join(ROOT, "ios-app/ShowUpWelcome")
GEN = os.path.join(ROOT, "ios-app/gen_pbxproj.py")
PBX = os.path.join(ROOT, "ios-app/ShowUpWelcome.xcodeproj/project.pbxproj")

# Already present, already understood, and NOT fixed blind. Each is (file, pattern fragment).
# The shake animation on a wrong code: seven steps at absolute 68ms offsets. Converting it to
# Task.sleep changes real behaviour -- sequential sleeps accumulate drift where absolute deadlines
# do not, and task cancellation on view teardown is not the same as a dispatch that always fires.
# Whether the result still looks right can only be judged by watching it, so it waits for the Mac.
KNOWN = [
    ("PhoneVerificationView.swift", "DispatchQueue.main.asyncAfter"),
]

fails = []
notes = []
checks = 0


def check(name, ok, detail=""):
    global checks
    checks += 1
    if not ok:
        fails.append(name + ((" -- " + detail) if detail else ""))


def read(p):
    return io.open(p, encoding="utf-8").read() if os.path.exists(p) else ""


def swift_files():
    if not os.path.isdir(SW):
        return []
    return sorted(f for f in os.listdir(SW) if f.endswith(".swift"))


def is_known(fname, line):
    return any(f == fname and frag in line for f, frag in KNOWN)


print("Swift 6 concurrency rules (no compiler required)")

# ── 1. the setting is still on ──────────────────────────────────────────────
# Weakening this would make every warning below disappear, which is the quietest possible way to
# lose the whole migration. Checked in the generator AND the generated file.
check("strict concurrency is on in the generator",
      "SWIFT_STRICT_CONCURRENCY = complete" in read(GEN))
check("strict concurrency is on in the project file",
      "SWIFT_STRICT_CONCURRENCY = complete" in read(PBX))

# ── 2. the escape hatches are forbidden ─────────────────────────────────────
FORBIDDEN = [
    ("@unchecked Sendable", "asserts a type is safe to share without the compiler agreeing"),
    ("nonisolated(unsafe)", "asserts a global is safe without the compiler agreeing"),
    ("@preconcurrency import", "switches off checking for an entire module"),
]
for token, why in FORBIDDEN:
    hits = []
    for f in swift_files():
        for i, line in enumerate(read(os.path.join(SW, f)).splitlines(), 1):
            if token in line:
                hits.append("%s:%d" % (f, i))
    check("no %s" % token, not hits, "%s -- %s" % (why, ", ".join(hits[:4])))

# ── 3. mutable global state ─────────────────────────────────────────────────
# Swift 6 rejects nonisolated global mutable state outright. `@MainActor` on it is a real fix, so a
# declaration carrying that annotation (on the line, or the line above) is accepted.
glob = re.compile(r"^\s*(?:private\s+|fileprivate\s+|internal\s+|public\s+)?static\s+var\s|^var\s")
for f in swift_files():
    lines = read(os.path.join(SW, f)).splitlines()
    for i, line in enumerate(lines):
        if not glob.match(line):
            continue
        prev = lines[i - 1] if i else ""
        if "@MainActor" in line or "@MainActor" in prev:
            continue
        where = "%s:%d" % (f, i + 1)
        if is_known(f, line):
            notes.append("%-46s mutable global state, not isolated" % where)
        else:
            check("no unisolated mutable global state (%s)" % where, False,
                  "Swift 6 rejects this; isolate it or make it an actor")

# ── 4. structured concurrency instead of queue hopping ──────────────────────
for f in swift_files():
    for i, line in enumerate(read(os.path.join(SW, f)).splitlines(), 1):
        if "DispatchQueue" not in line:
            continue
        where = "%s:%d" % (f, i)
        if is_known(f, line):
            notes.append("%-46s DispatchQueue instead of Task/await" % where)
        else:
            check("no DispatchQueue (%s)" % where, False,
                  "use Task and await; a queue hop leaves the isolation the compiler tracks")

# ── 5. Combine is not used in new code ──────────────────────────────────────
combine = ["%s" % f for f in swift_files() if "import Combine" in read(os.path.join(SW, f))]
check("no Combine in new code", not combine,
      "async/await is the target; mixing both is the migration nobody finishes: %s" % combine)

print("  %d swift files, %d checks" % (len(swift_files()), checks))

if notes:
    print()
    print("  KNOWN, not failing -- see the note at the top of this file:")
    for n in notes:
        print("    - " + n)

print()
if fails:
    print("FAILED %d:" % len(fails))
    for f in fails:
        print("  x " + f)
    sys.exit(1)
print("  all passed")
print()
print("  This proves no warning has been SILENCED. It does not prove the code is race-free --")
print("  only `xcodebuild` on a Mac can do that.")
