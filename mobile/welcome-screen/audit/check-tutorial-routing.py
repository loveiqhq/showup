# -*- coding: utf-8 -*-
"""SHOWUP-146 -- the tutorial is shown to new accounts and to nobody else.

    python audit/check-tutorial-routing.py

Both platforms now run the rule against the real cases -- TutorialRoutingTest on Android and
TutorialRoutingTests on iOS. This file is the third guard, for the thing neither suite can see:
the two are three-line functions on separate platforms, and they would drift silently while both
suites stayed green.

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
#
# The RULE is unchanged -- Connect belongs to account creation, so someone who already has an
# account must not pass through it. What changed on 10 September 2026 is how the flow knows.
#
# It used to ask which button the user tapped (`entry == Entry.LogIn`). It now asks the SERVER
# whether the account already has a usable profile, because intent and reality can disagree:
# tapping "create account" with a number that already exists used to send a returning member
# through Connect and the whole tutorial. Completeness also survives an interrupted signup,
# where an "is this account new" flag would send a half-registered user straight to Home.
check("android: a verified returning member leaves before Connect",
      "if (profileComplete) {" in kt_flow and "onFinished(outcomeOf(Entry.LogIn, null))" in kt_flow,
      "Connect belongs to account creation, so re-login must not pass through it")
check("ios: a verified returning member leaves before Connect",
      "if profileComplete {" in sw_flow and "onFinished(outcomeOf(.logIn, nil))" in sw_flow)
# The decision has to come from the profile, not from a flag the auth response does not carry.
check("android: the routing decision reads the profile",
      "profileComplete" in kt_flow, "must not reintroduce an isNewUser flag")
check("ios: the routing decision reads the profile", "profileComplete" in sw_flow)

# ── the host sends the two outcomes to two different places ─────────────────
check("android: the host branches on showsTutorial", "showsTutorial(it)" in kt_host)
check("ios: the host branches on showsTutorial", "showsTutorial(o)" in sw_host)
check("android: there is somewhere for a returning member to land",
      "HomePlaceholderScreen" in kt_host)
check("ios: there is somewhere for a returning member to land",
      "HomePlaceholderView" in sw_host)

# ── the far end of the tutorial ─────────────────────────────────────────────
# It used to restart the tour, which was honest while there was nowhere to go and is wrong now.
# The destination was the literal 7 until 8 September 2026, when the Int scheme became FlowScreen.
# The claim is unchanged and the assertion now reads as the claim does: finishing goes HOME.
# The DESTINATION changed on 9 September 2026 -- profile creation now sits between the tutorial's
# end and the app, which is the order the profile epic specifies ("entered from the app tutorial").
# SHOWUP-146's rule is unchanged and is what is asserted: finishing does not restart the tour.
check("android: finishing the tutorial does not restart it",
      "onFinish = { screen = FlowScreen.ProfileName }" in kt_host
      and "onFinish = { screen = FlowScreen.TutorialWelcome }" not in kt_host,
      "SHOWUP-146 connects the tutorial forwards, not back to card 1")
check("ios: finishing the tutorial does not restart it",
      "onFinish: { go(to: .profileName) }" in sw_host
      and "onFinish: { go(to: .tutorialWelcome) }" not in sw_host)
# And the first card is still where a fresh tour starts, so "does not restart" means something.
check("android: the tour starts at card 1", "FlowScreen.TutorialWelcome" in kt_host)
check("ios: the tour starts at card 1", ".tutorialWelcome" in sw_host)

# ── the rule is actually tested somewhere ───────────────────────────────────
check("the android rule is tested", "class TutorialRoutingTest" in kt_tests)
check("the ios rule is tested too",
      "AT PARITY" in sw_rule and "NOT AT PARITY" not in sw_rule,
      "the Swift twin is covered now; the header must say so where the next reader will see it")

ios_tests = read(os.path.join(ROOT, "ios-app/ShowUpWelcomeTests"), "TutorialRoutingTests.swift")
check("the ios tests are written", "final class TutorialRoutingTests" in ios_tests)

# The one that actually bit: the file sat in the repo for a week in no target, so `cmd-U` had
# nothing to run and eight passing-looking tests guarded nothing. Present is not the same as run.
pbx = read(os.path.join(ROOT, "ios-app/ShowUpWelcome.xcodeproj"), "project.pbxproj")
check("the ios tests are wired into a test bundle",
      "com.apple.product-type.bundle.unit-test" in pbx
      and "TutorialRoutingTests.swift in Sources" in pbx,
      "a test file in no target is indistinguishable from no test at all")
scheme = read(os.path.join(ROOT, "ios-app/ShowUpWelcome.xcodeproj/xcshareddata/xcschemes"),
              "ShowUpWelcome.xcscheme")
check("and the scheme runs them", "ShowUpWelcomeTests.xctest" in scheme,
      "a test bundle the scheme does not reference never runs on cmd-U or in CI")
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
