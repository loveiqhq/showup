# -*- coding: utf-8 -*-
"""Every screen and model that can report events is handed THE app's tracker.

Until 8 October 2026 every one of them defaulted to reporting nowhere: the events were built and
unit-tested, and no host passed a tracker, so nothing anyone could look at showed a single event
fire, and choosing an analytics tool would have meant wiring every call site by hand. The hosts now
pass `AppAnalytics` (Android) / `AppAnalytics.shared` (iOS) to each one.

This keeps it that way. For every class, composable or view on either platform that takes an
`analytics` parameter, each place the HOST builds it must pass the shared tracker. A new view model
with an `analytics` parameter that the host builds without it fails here, rather than shipping a
screen whose events go nowhere.

    python audit/check-tracker-wiring.py
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KT = os.path.join(ROOT, "android-preview-project", "app", "src", "main", "java", "com", "showup")
SW = os.path.join(ROOT, "ios-app", "ShowUpWelcome")

problems = []


def read(path):
    return io.open(path, encoding="utf-8").read()


def call_args(src, start):
    """The text between the parenthesis at `start` and its match, strings and nesting respected."""
    depth, i, in_str = 0, start, None
    while i < len(src):
        c = src[i]
        if in_str:
            if c == "\\":
                i += 2
                continue
            if c == in_str:
                in_str = None
        elif c == '"':
            in_str = c
        elif c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
            if depth == 0:
                return src[start + 1:i]
        i += 1
    return src[start + 1:]


def takers(directory, ext, pattern):
    """Names of the types and functions in `directory` whose parameters include a tracker."""
    names = set()
    for base, _, files in os.walk(directory):
        for name in files:
            if not name.endswith(ext):
                continue
            src = read(os.path.join(base, name))
            for m in pattern.finditer(src):
                names.add(m.group(1))
    return names


def check(host_path, names, needle, label):
    host = read(host_path)
    checked = 0
    for name in sorted(names):
        for m in re.finditer(r"(?<![\w.])" + re.escape(name) + r"\(", host):
            args = call_args(host, m.end() - 1)
            checked += 1
            if needle not in args:
                line = host.count("\n", 0, m.start()) + 1
                problems.append("%s:%d  %s is built without the shared tracker (%s)"
                                % (os.path.basename(host_path), line, name, needle))
    print("  %-8s %2d constructions checked across %d trackable names" % (label, checked, len(names)))
    return checked


print("Tracker wiring: every screen and model the host builds reports through the shared tracker")

# Android: `class X(... analytics: AnalyticsTracker? = ...` and `fun X(... analytics: AnalyticsTracker`.
kt_pattern = re.compile(
    r"(?:class|fun)\s+(\w+)\s*\((?:[^()]|\([^()]*\))*?\banalytics:\s*AnalyticsTracker", re.S)
kt_names = takers(KT, ".kt", kt_pattern) - {"AppAnalytics"}
kt_checked = check(os.path.join(KT, "MainActivity.kt"), kt_names, "analytics = AppAnalytics", "Android")

# iOS: an initialiser parameter `analytics: (any AnalyticsTracking)?` or a stored `var analytics`.
sw_names = set()
for base, _, files in os.walk(SW):
    for name in files:
        if not name.endswith(".swift"):
            continue
        src = read(os.path.join(base, name))
        for m in re.finditer(r"(?:final class|struct|class)\s+(\w+)[^{]*\{", src):
            body_start = m.end()
            # The type's own declarations up to the next top-level type: enough to find its tracker.
            nxt = re.search(r"\n(?:final class|struct|class|enum|extension|protocol)\s", src[body_start:])
            body = src[body_start: body_start + (nxt.start() if nxt else len(src))]
            if re.search(r"\banalytics:\s*\(?\s*(?:any\s+)?AnalyticsTracking", body):
                sw_names.add(m.group(1))
sw_names -= {"AppAnalytics", "RecordingAnalytics"}
sw_checked = check(os.path.join(SW, "ShowUpWelcomeApp.swift"), sw_names, "analytics: AppAnalytics.shared", "iOS")

print()
if kt_checked == 0 or sw_checked == 0:
    problems.append("found no constructions to check -- has the host moved?")
if problems:
    print("FOUND %d problem(s):" % len(problems))
    for p in problems:
        print("  " + p)
    sys.exit(1)
print("every construction passes the shared tracker.")
