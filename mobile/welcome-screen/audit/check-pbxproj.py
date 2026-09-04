# -*- coding: utf-8 -*-
"""Structural checks on the generated Xcode project.

    python audit/check-pbxproj.py

WHAT THIS CAN AND CANNOT DO

It cannot tell you the project builds. Only xcodebuild does that. What it CAN do is catch the
errors that make a hand-generated pbxproj wrong in ways that are tedious to diagnose from Xcode's
side: a referenced object id that was never defined, a package product that no target depends on,
or a local package whose relativePath points nowhere.

Those failures present badly. Xcode reports "missing package product" or refuses to open the
project at all, with nothing pointing at the line responsible -- and gen_pbxproj.py writes the file
from six separate places, so a new dependency needs an entry in all of them and any one can be
forgotten.

Written when ShowUpAPI was wired in as a local package, at a moment when CI could not run and this
was the strongest verification available. It is worth keeping regardless: it runs in a second and
it checks things a compile would only reveal after several minutes of setup.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PBX = os.path.join(ROOT, "ios-app/ShowUpWelcome.xcodeproj/project.pbxproj")
IOS = os.path.join(ROOT, "ios-app")

fails = []
checks = 0


def check(name, ok, detail=""):
    global checks
    checks += 1
    if not ok:
        fails.append(name + ((" -- " + detail) if detail else ""))


if not os.path.exists(PBX):
    print("no project.pbxproj -- run gen_pbxproj.py first")
    raise SystemExit(1)

text = io.open(PBX, encoding="utf-8").read()

print("Xcode project structure (no compiler required)")

# ── 1. every referenced id is defined ───────────────────────────────────────
# An id used in a list but never given a body is the single most common hand-generation mistake,
# and Xcode's error for it names the container rather than the missing object.
defined = set(re.findall(r"^\t\t([0-9A-F]{24}) /\* .* \*/ = \{", text, re.M))
defined |= set(re.findall(r"^\t\t([0-9A-F]{24}) = \{", text, re.M))
referenced = set(re.findall(r"([0-9A-F]{24})", text))
dangling = sorted(referenced - defined)
check("every referenced object id is defined", not dangling,
      "undefined: %s" % ", ".join(dangling[:5]))

# ── 2. the local package reference ──────────────────────────────────────────
check("an XCLocalSwiftPackageReference section exists",
      "XCLocalSwiftPackageReference" in text,
      "ShowUpAPI is a local package and needs one")

paths = re.findall(r"isa = XCLocalSwiftPackageReference;\s*\n\s*relativePath = ([^;]+);", text)
check("the local package declares a relativePath", bool(paths))

for raw in paths:
    rel = raw.strip().strip('"')
    manifest = os.path.join(IOS, rel, "Package.swift")
    check("local package %r has a Package.swift" % rel, os.path.exists(manifest),
          "looked for %s" % manifest)

# objectVersion 55 is the floor for XCLocalSwiftPackageReference. Below it, Xcode reports a
# missing package rather than an unsupported project format, which sends you looking in the
# wrong place entirely.
version = re.search(r"objectVersion = (\d+);", text)
check("objectVersion is at least 55", version and int(version.group(1)) >= 55,
      "found %s" % (version.group(1) if version else "none"))

# ── 3. a local product dependency must NOT carry a package reference ────────
# This is the detail that makes the project fail to OPEN, and it looks like an omission rather
# than a rule: a remote product says `package = <ref>`, a local one says only productName.
blocks = re.findall(r"\t\t[0-9A-F]{24} /\* ([^*]+) \*/ = \{\s*\n\s*isa = XCSwiftPackageProductDependency;(.*?)\n\t\t\};",
                    text, re.S)
for name, body in blocks:
    name = name.strip()
    if name == "ShowUpAPI":
        check("the ShowUpAPI product dependency has no `package =` line",
              "package =" not in body,
              "a local package product is identified by productName alone")

# ── 4. the product is actually depended on and linked ───────────────────────
# A product that exists but is in no target's packageProductDependencies compiles nothing, and
# `import ShowUpAPI` then fails with "no such module" -- which reads like a package problem.
api_prod_ids = re.findall(
    r"\t\t([0-9A-F]{24}) /\* ShowUpAPI \*/ = \{\s*\n\s*isa = XCSwiftPackageProductDependency;", text)
check("at least two ShowUpAPI product dependencies exist (app and tests)",
      len(api_prod_ids) >= 2, "found %d" % len(api_prod_ids))

dep_lists = re.findall(r"packageProductDependencies = \(\s*(.*?)\);", text, re.S)
depended = sum(1 for block in dep_lists for pid in api_prod_ids if pid in block)
check("ShowUpAPI appears in the targets' packageProductDependencies",
      depended >= 2, "appears in %d list(s)" % depended)

frameworks = re.findall(r"isa = PBXFrameworksBuildPhase;.*?files = \(\s*(.*?)\);", text, re.S)
linked = sum(1 for block in frameworks if "ShowUpAPI in Frameworks" in block)
check("ShowUpAPI is in both Frameworks build phases", linked >= 2,
      "found in %d phase(s)" % linked)

pkg_refs = re.search(r"packageReferences = \(\s*(.*?)\);", text, re.S)
check("the local package is listed in the project's packageReferences",
      pkg_refs and "XCLocalSwiftPackageReference" in pkg_refs.group(1))

# ── 5. every source on disk is in the target ────────────────────────────────
# The failure this whole generator exists to prevent: a file in the repo and in no target. It cost
# a week of Swift tests that were counted as passing and had never run.
for folder, label in (("ShowUpWelcome", "app"), ("ShowUpWelcomeTests", "test")):
    directory = os.path.join(IOS, folder)
    if not os.path.isdir(directory):
        continue
    for fn in sorted(f for f in os.listdir(directory) if f.endswith(".swift")):
        check("%s source %s is in the project" % (label, fn), fn in text,
              "present on disk but in no target")

print("  %d checks" % checks)
if fails:
    print("")
    print("FAILED %d:" % len(fails))
    for f in fails:
        print("  x %s" % f)
    sys.exit(1)

print("")
print("  all passed")
print("")
print("  This proves the project's structure is internally consistent. It does NOT prove it")
print("  builds -- only xcodebuild on a Mac can do that.")
