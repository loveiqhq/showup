# -*- coding: utf-8 -*-
"""SHOWUP-146 -- the tutorial is shown to new accounts and to nobody else.

    python audit/check-tutorial-routing.py

The Android rule is covered properly by TutorialRoutingTest, which runs the real function against
the real cases. This file exists for the half that cannot be: iOS has no test bundle, so the Swift
twin is unguarded, and the two are three-line functions that would drift silently.

So the checks below are about SAMENESS and WIRING, not about logic:

  * both platforms declare the same three enums with the same cases
  * both state the same two rules in outcomeOf
  * both hosts route the two outcomes to two different places
  * both Connect hosts report which exit happened instead of a bare "done"
  * neither flow walks a returning member into Connect
  * the end of the tutorial goes to the app rather than back to card 1

None of this proves the rule is right. TutorialRoutingTest does that. This proves iOS says the same
thing Android says.
"""
import io
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KT = os.path.join(ROOT, "android-preview-project/app/src/main/java/com/showup")
SW = os.path.join(ROOT, "ios-app/ShowUpWelcome")

fails = []
checks = 0


def check(name, ok, detail=""):
    global checks
    checks += 1
    if not ok:
        fails.append(name + ((" -- " + detail) if detail else ""))


def read(*parts):
    p = os.path.join(*parts)
    if not os.path.exists(p):
        return ""
    return io.open(p, encoding="utf-8").read()


print("SHOWUP-146 tutorial routing")

kt_rule = read(KT, "welcome/TutorialRouting.kt")
sw_rule = read(SW, "TutorialRouting.swift")
kt_flow = read(KT, "welcome/SignUpFlow.kt")
sw_flow = read(SW, "SignUpFlow.swift")
kt_host = read(KT, "MainActivity.kt")
sw_host = read(SW, "ShowUpWelcomeApp.swift")
kt_connect = read(KT, "welcome/ConnectFlowHost.kt")
kt_tests = read(KT.replace("/main/", "/test/"), "welcome/TutorialRoutingTest.kt")

check("the rule exists on android", bool(kt_rule))
check("the rule exists on ios", bool(sw_rule))

# ── the same vocabulary on both sides ───────────────────────────────────────
# Written as (kotlin token, swift token) so a rename on one platform fails here rather than
# quietly leaving the other behind.
for kt_name, sw_name, label in [
    ("CreateAccount", "createAccount", "Entry.CreateAccount"),
    ("LogIn", "logIn", "Entry.LogIn"),
    ("Skipped", "skipped", "ConnectExit.Skipped"),
    ("Connected", "connected", "ConnectExit.Connected"),
    ("ResolvedConflict", "resolvedConflict", "ConnectExit.ResolvedConflict"),
    ("NewAccount", "newAccount", "SignUpOutcome.NewAccount"),
    ("ReturningMember", "returningMember", "SignUpOutcome.ReturningMember"),
]:
    check("android declares " + label, kt_name in kt_rule)
    check("ios declares " + label, sw_name in sw_rule)

# ── the same two rules ──────────────────────────────────────────────────────
check("android: logging in means returning member",
      "entry == Entry.LogIn -> SignUpOutcome.ReturningMember" in kt_rule)
check("ios: logging in means returning member",
      "entry == .logIn { return .returningMember }" in sw_rule)
check("android: a resolved conflict means returning member",
      "connectExit == ConnectExit.ResolvedConflict -> SignUpOutcome.ReturningMember" in kt_rule)
check("ios: a resolved conflict means returning member",
      "connectExit == .resolvedConflict { return .returningMember }" in sw_rule)

# ── the Connect host reports WHICH exit, not just THAT it exited ────────────
check("android: Connect reports its exit", "onDone: (ConnectExit) -> Unit" in kt_connect)
check("ios: Connect reports its exit", "onDone: (ConnectExit) -> Void" in sw_host)
for token, plat, src in [("ConnectExit.Skipped", "android", kt_connect),
                         ("ConnectExit.Connected", "android", kt_connect),
                         ("ConnectExit.ResolvedConflict", "android", kt_connect),
                         (".skipped", "ios", sw_host),
                         (".connected", "ios", sw_host),
                         (".resolvedConflict", "ios", sw_host)]:
    check("%s: Connect can report %s" % (plat, token), "onDone(%s)" % token in src)

# ── a returning member never reaches Connect ────────────────────────────────
check("android: a verified returning member leaves before Connect",
      "if (entry == Entry.LogIn) {" in kt_flow and "onFinished(outcomeOf(entry, null))" in kt_flow,
      "Connect belongs to account creation, so re-login must not pass through it")
check("ios: a verified returning member leaves before Connect",
      "if entry == .logIn {" in sw_flow and "onFinished(outcomeOf(entry, nil))" in sw_flow)

# ── the host sends the two outcomes to two different places ─────────────────
check("android: the host branches on showsTutorial", "showsTutorial(it)" in kt_host)
check("ios: the host branches on showsTutorial", "showsTutorial(o)" in sw_host)
check("android: there is somewhere for a returning member to land",
      "HomePlaceholderScreen" in kt_host)
check("ios: there is somewhere for a returning member to land",
      "HomePlaceholderView" in sw_host)

# ── the far end of the tutorial ─────────────────────────────────────────────
# It used to restart the tour, which was honest while there was nowhere to go and is wrong now.
check("android: finishing the tutorial does not restart it",
      "onFinish = { screen = 7 }" in kt_host,
      "SHOWUP-146 connects the tutorial to the app, not back to card 1")
check("ios: finishing the tutorial does not restart it", "onFinish: { go(to: 7) }" in sw_host)

# ── the rule is actually tested somewhere ───────────────────────────────────
check("the android rule is tested", "class TutorialRoutingTest" in kt_tests)
check("the ios gap is declared, not forgotten", "NOT AT PARITY" in sw_rule,
      "iOS has no test bundle; that must be written down where the next reader will see it")

# The Swift tests exist but have never been compiled. Checking they are PRESENT is worth something
# -- it stops them being quietly deleted -- but it is not a substitute for running them, and the
# file says so at the top rather than pretending otherwise.
ios_tests = read(os.path.join(ROOT, "ios-app/ShowUpWelcomeTests"), "TutorialRoutingTests.swift")
check("the ios tests are written, ready for a Mac", "final class TutorialRoutingTests" in ios_tests)
check("and they are honest about never having run", "NOT YET RUN" in ios_tests)
check("they cover the same cases as Android",
      ios_tests.count("func test") == kt_tests.count("fun `"),
      "%d swift vs %d kotlin" % (ios_tests.count("func test"), kt_tests.count("fun `")))

print()
print("  %d checks" % checks)
if fails:
    print("FAILED %d:" % len(fails))
    for f in fails:
        print("  x " + f)
    sys.exit(1)
print("  all passed")
print()
print("  The RULE itself is covered by TutorialRoutingTest, not here:")
print("    cd android-preview-project && ./gradlew :app:testDebugUnitTest")
