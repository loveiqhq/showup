# -*- coding: utf-8 -*-
"""Profile creation, "The basics" — conformance against SHOWUP-150/152/153/154.

    python audit/verify-profile.py

WHY THIS EXISTS

The four screens in this group share a shell, a reserved-region discipline and a copy deck, and
three of those are the kind of thing a refactor silently changes. The reserved heights in
particular: each one is a specific number chosen for a specific tallest state, and dropping any of
them to "fit" a failure state is the single change that would undo the group's one guarantee --
that the CTA does not move between states.

WHAT THIS DOES NOT CHECK

Whether the screens look right. That is ScreenFitTest at 17 sizes, the previews, and a person
holding a phone. This checks that the numbers and strings the tickets fix are the ones in the
source, on BOTH platforms, which is the part reading cannot be trusted with.
"""
import io
import os
import json
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(HERE)
KT = os.path.join(MOBILE, "android-preview-project", "app", "src", "main", "java", "com", "showup")
SW = os.path.join(MOBILE, "ios-app", "ShowUpWelcome")

# THE REGISTRY VERSION, READ -- never typed into a check. Two checks below used to compare the app
# constants to a literal written in this file, so neither could notice the registry moving.
_REGISTRY = os.path.join(os.path.dirname(os.path.dirname(MOBILE)), "design_handoff_showup", "tracking", "enums.json")
_REGISTRY_VERSION = json.load(io.open(_REGISTRY, encoding="utf-8"))["registry_version"]

failures = []
count = 0


def check(name, ok):
    global count
    count += 1
    if not ok:
        failures.append(name)


def read(*parts):
    with io.open(os.path.join(*parts), encoding="utf-8") as f:
        return f.read()


name_kt = read(KT, "profile", "ProfileNameScreen.kt")
email_kt = read(KT, "profile", "ProfileEmailScreen.kt")
verify_kt = read(KT, "profile", "ProfileVerifyEmailScreen.kt")
dob_kt = read(KT, "profile", "ProfileDobScreen.kt")
dobcalc_kt = read(KT, "profile", "DateOfBirth.kt")
step_kt = read(KT, "profile", "BasicsStep.kt")

basics_sw = read(SW, "ProfileBasics.swift")
verify_sw = read(SW, "ProfileVerifyEmail.swift")
dob_sw = read(SW, "ProfileDob.swift")
dobcalc_sw = read(SW, "DateOfBirth.swift")
step_sw = read(SW, "BasicsStep.swift")

# ── the reserved regions ────────────────────────────────────────────────────
#
# One number per screen, each the height of that screen's TALLEST body. These are the group's
# CTA-does-not-move guarantee expressed as four integers, and every one of them has been wrong at
# least once on some screen in this project.
#
# The EMAIL ADDRESS screen is the deliberate exception and has none: it is too dense to reserve
# the taller height without pushing the CTA into the keyboard, so its consent row and CTA sit ~18
# lower in the error state. That is accepted, and the acceptance test is that the CTA stays clear
# of the keys -- ScreenFitTest's job, not this file's.
#
# The VERIFY screen was never meant to be part of that exception and was treated as one by
# accident. It reserves properly now.
check("150 name reserves 44 (kotlin)", "44.dp" in name_kt)
# 153's region is FIXED, not a floor, since 11 September 2026. It was `heightIn(min = 30)` and
# this file asserted that -- codifying the defect rather than catching it. A minimum reserves
# nothing: the card grew past 30 the moment the copy wrapped, and a device screenshot showed the
# CTA, the resend row and the change-address link all sliding down when a code was refused.
#
# The behaviour itself is covered by VerifyEmailStabilityTest, which measures the laid-out Y of
# each of those three in every state and is injection-tested both ways. What this check adds is
# the one thing a behavioural test cannot say: that nobody has quietly turned the reserve back
# into a floor on a screen size the test does not sweep.
def code_only(text):
    """The file with comment lines removed.

    A "not in" assertion cannot read raw source: the comment explaining what a value used to be
    contains the value it used to be, so the check fails on its own explanation. That happened
    here the moment the reserve was fixed and documented in the same edit.
    """
    out = []
    for line in text.split("\n"):
        stripped = line.lstrip()
        if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
            continue
        out.append(line)
    return "\n".join(out)


check("153 verify reserves a FIXED height, not a floor (kotlin)",
      ".height(80.dp)" in verify_kt and "heightIn(min = 30.dp)" not in code_only(verify_kt))


check("153 verify reserves a FIXED height, not a floor (swift)",
      ("minHeight: 80, maxHeight: 80" in verify_sw
       and "minHeight: 30" not in code_only(verify_sw)))
check("154 dob reserves 84 (kotlin)", "heightIn(min = 84.dp)" in dob_kt)
check("154 dob reserves 84 (swift)", "minHeight: 84" in dob_sw)

# ── the progress bar holds at 2 on the code screen ──────────────────────────
#
# The single most quotable rule in 153: verification is not a fourth step. Both platforms derive
# it from BasicsStep rather than passing a literal, so this checks the derivation exists AND that
# the enum still says 2.
check("153 progress comes from BasicsStep (kotlin)",
      "BasicsStep.EmailVerify.progressSegment" in verify_kt)
check("153 progress comes from BasicsStep (swift)",
      "BasicsStep.emailVerify.progressSegment" in verify_sw)
check("email_verify holds at segment 2 (kotlin)", "Email, EmailVerify -> 2" in step_kt)
check("email_verify holds at segment 2 (swift)", "case .email, .emailVerify: return 2" in step_sw)
check("154 is segment 3 (kotlin)", "Dob -> 3" in step_kt)
check("154 is segment 3 (swift)", "case .dob: return 3" in step_sw)

# ── the code row ────────────────────────────────────────────────────────────
check("153 slots are the shared component (kotlin)", "CodeSlotRow(" in verify_kt)
check("153 slots are the shared component (swift)", "CodeSlotRow(" in verify_sw)
check("153 slots draw the halo (kotlin)", "halo = true" in verify_kt)
check("153 slots draw the halo (swift)", "halo: true" in verify_sw)
check("153 caret blinks (kotlin)", "caretBlinks = true" in verify_kt)
check("153 caret blinks (swift)", "caretBlinks: true" in verify_sw)
check("153 slot row is labelled (kotlin)",
      "Enter your 6-digit verification code" in verify_kt)
check("153 slot row is labelled (swift)",
      "Enter your 6-digit verification code" in verify_sw)

# ── the CTA rules, which differ per screen on purpose ───────────────────────
#
# 153 is the group's ONE disabled CTA and it is a settled exception, not drift: a partial code has
# nothing to validate. 154 follows the group rule and is never disabled -- pressing it on a bad
# date produces the error instead.
check("153 CTA is gated on canSubmitCode (kotlin)", "canSubmitCode(digits, state)" in verify_kt)
check("153 CTA is gated on canSubmitCode (swift)", "canSubmitCode(digits, state: state)" in verify_sw)
check("154 CTA is never disabled (kotlin)", "enabled" not in dob_kt.split("NextButton(")[1][:80])
check("154 CTA is never disabled (swift)", "enabled" not in dob_sw.split("NextButton(")[1][:120])

# ── errors are the shared card, everywhere ──────────────────────────────────
#
# Decided 10 September 2026: one error style in the app, the red card, never bare red text.
for label, src in [("verify kotlin", verify_kt), ("verify swift", verify_sw),
                   ("dob kotlin", dob_kt), ("dob swift", dob_sw)]:
    check("errors use the shared card (%s)" % label, "InlineErrorCard" in src)

# ── copy, character for character ───────────────────────────────────────────
#
# Quoted from the tickets. The two PROPOSED strings are checked too -- if the design side changes
# them, this is where that lands rather than in a screenshot review.
COPY_153 = [
    "We sent a 6-digit code to ",
    "Verify code",
    "Send a new code",
    "Change email address",
    "That code doesn’t match. Check your inbox or request a new one.",
    "That code has expired. Send a new one to try again.",
    "Too many tries. Send a new code.",
]
for s in COPY_153:
    check("copy 153 kotlin: %s" % s[:40], s in verify_kt)
    check("copy 153 swift: %s" % s[:40], s in verify_sw)

COPY_154 = [
    "Be honest — it helps us find the right matches. You must be 18 or older.",
    "Date of birth",
    "Locked after this step.",
    "Don't display on my profile",
    "You must be at least 18 to use Show Up. Please check the date you entered.",
]
for s in COPY_154:
    check("copy 154 kotlin: %s" % s[:40], s in dob_kt)
    check("copy 154 swift: %s" % s[:40], s in dob_sw)

# ── the two product decisions that override the tickets ─────────────────────
#
# Both were ruled on 10 September 2026 and both CONTRADICT the ticket text, so a reader checking
# the code against the ticket would think these were bugs. They are pinned here, with the reason,
# so the next person finds the decision rather than "fixing" it back.
#
# UTC: the server computes age in UTC and rejects under-18 with a 400. A client computing locally
# would show the age card and then be refused, for a user west of UTC on their birthday.
check("154 age is computed in UTC (kotlin)", "UTC" in dobcalc_kt and "utcToday" in dobcalc_kt)
check("154 age is computed in UTC (swift)", "utcCalendar" in dobcalc_sw)
check("154 no local-time age (kotlin)", "Calendar.getInstance()" not in dobcalc_kt)

# Locale order: mm/dd/yyyy is US-ordered, and 03/04 is a valid WRONG date for a German user --
# unrecoverable, because age is locked.
check("154 order follows the locale (kotlin)", "DateOrder" in dob_kt and "rememberDateOrder" not in dob_kt)
check("154 order is a parameter (kotlin)", "order: DateOrder" in dob_kt)
check("154 order follows the locale (swift)", "DateOrder.forCurrentLocale" in dob_sw or
      "forCurrentLocale" in dob_sw)
check("154 both patterns exist (kotlin)", "mm/dd/yyyy" in dobcalc_kt and "dd/mm/yyyy" in dobcalc_kt)
check("154 both patterns exist (swift)", "mm/dd/yyyy" in dobcalc_sw and "dd/mm/yyyy" in dobcalc_sw)

# ── validity is a round trip, not a regex ───────────────────────────────────
#
# 02/30/1990 passes any regex and a lenient calendar turns it into 2 March. The user would be
# locked to a birthday they never typed, on the one screen whose value cannot be changed.
check("154 round-trip check (kotlin)", "roundTrips" in dobcalc_kt)
check("154 round-trip check (swift)", "back.year == year" in dobcalc_sw)

# ── the 18 gate matches the server's ────────────────────────────────────────
check("154 minimum age is 18 (kotlin)", "MINIMUM_AGE = 18" in dobcalc_kt)
check("154 minimum age is 18 (swift)", "minimumAge = 18" in dobcalc_sw)

# ── expired vs wrong, without a backend change ──────────────────────────────
#
# /auth/email/verify returns the SAME 401 for both, so the response cannot distinguish them. The
# client uses expiresAt from /auth/email/start instead. If this ever collapses back to one state,
# the approved expired copy becomes unreachable.
check("153 has four distinct states (kotlin)",
      all(s in read(KT, "profile", "EmailVerification.kt")
          for s in ["Calm", "Mismatch", "Expired", "LockedOut"]))
check("153 has four distinct states (swift)",
      all(s in read(SW, "EmailVerification.swift")
          for s in ["calm", "mismatch", "expired", "lockedOut"]))

# ── the age visibility control writes to hidden_fields, never isVisible ─────
#
# The one mistake with real consequences on this screen: isVisible means "appears in discovery at
# all", so wiring the age toggle to it would remove the user from matching -- the exact outcome
# the product decision forbids.
check("154 visibility never mentions isVisible (kotlin)", "isVisible" not in dob_kt)
check("154 visibility never mentions isVisible (swift)", "isVisible" not in dob_sw)

# ── SHOWUP-155 · the embrace bridge ─────────────────────────────────────────
#
# THE ABSENCES ARE THE DESIGN, so most of this section is "not in". The reference file names
# adding a header or a progress bar "the single most likely mistake on this screen", and both are
# exactly the kind of thing a later consistency pass adds without malice. A "not in" assertion is
# the only way to hold a decision that is expressed by something not being there.
#
# `code_only` throughout, because the comments in both files explain WHY there is no AppHeader --
# and therefore contain the word.
embrace_kt = code_only(read(KT, "profile", "ProfileEmbraceScreen.kt"))
embrace_sw = code_only(read(SW, "ProfileEmbrace.swift"))

check("155 no AppHeader (kotlin)", "AppHeader" not in embrace_kt)
check("155 no AppHeader (swift)", "AppHeader" not in embrace_sw)
check("155 no StepProgress (kotlin)", "StepProgress" not in embrace_kt)
check("155 no StepProgress (swift)", "StepProgress" not in embrace_sw)
check("155 no BasicsScaffold (kotlin)", "BasicsScaffold" not in embrace_kt)
check("155 no BasicsScaffold (swift)", "BasicsScaffold" not in embrace_sw)

# The bridge is a SCREEN but NOT A STEP. §11's naming rules say the §2 row is deliberately absent
# "because firing profile_step_viewed on it would put a phantom step in the completion funnel".
check("155 fires no step event (kotlin)", "stepViewed" not in embrace_kt)
check("155 fires no step event (swift)", "stepViewed" not in embrace_sw)
check("155 the bridge event exists (kotlin)",
      "embrace_bridge_viewed" in read(KT, "profile", "ProfileAnalytics.kt"))
check("155 the variant vocabulary is registry-backed (kotlin)",
      'BUILD_PROFILE = "build_profile"' in read(KT, "profile", "ProfileAnalytics.kt"))
check("155 the screen row is registry-backed (kotlin)",
      '"profile_embrace_build", "ProfileEmbraceBuild"' in read(KT, "profile", "ProfileAnalytics.kt"))

# THE BRIDGE SHELL, SINCE SHOWUP-166. Everything the two bridges share -- the backdrop scaffold, the
# gutter, the top, the fixed headline, the sunset CTA and the swallowed back -- moved into
# BridgeShell on both platforms, so the layout numbers are asserted THERE, once, and each bridge is
# asserted to be built on it. A bridge that stopped using the shell would fail here before it could
# drift.
bridge_kt = code_only(read(KT, "profile", "BridgeShell.kt"))
bridge_sw = code_only(read(SW, "BridgeShell.swift"))
check("155 is built on the bridge shell (kotlin)", "EmbraceBridgeShell(" in embrace_kt)
check("155 is built on the bridge shell (swift)", "EmbraceBridgeShell(" in embrace_sw)
check("155 uses the corner backdrop (kotlin)", "BridgeBackdrop.Corner" in embrace_kt)
check("155 uses the corner backdrop (swift)", "backdrop: .corner" in embrace_sw)

# Rule 5's named exception: the ambient backdrop, from the shared component rather than redrawn.
check("bridge shell uses the shared backdrop scaffold (kotlin)", "WelcomeScaffold(" in bridge_kt)
check("bridge shell uses the shared backdrop scaffold (swift)", "WelcomeScaffold(" in bridge_sw)
check("bridge shell gutter 28 (kotlin)", "gutter = 28.dp" in bridge_kt)
check("bridge shell gutter 28 (swift)", "gutter: 28" in bridge_sw)
check("bridge shell top 64 (kotlin)", "topPadding = 64.dp" in bridge_kt)
check("bridge shell top 64 (swift)", "topPadding: 64" in bridge_sw)

# Rule 7's named exception: full-width SUNSET, not the round orange NextButton.
check("bridge shell CTA is sunset (kotlin)", "PrimaryButtonVariant.Sunset" in bridge_kt)
check("bridge shell CTA is sunset (swift)", "variant: .sunset" in bridge_sw)
for label, text in (("kotlin", bridge_kt), ("swift", bridge_sw),
                    ("155 kotlin", embrace_kt), ("155 swift", embrace_sw)):
    check("bridge has no round NextButton (%s)" % label, "NextButton" not in text)
    check("bridge has no AppHeader (%s)" % label, "AppHeader" not in text)
    check("bridge has no StepProgress (%s)" % label, "StepProgress" not in text)

# Headline: Lora 700 / 34 / 1.1 / -0.015em -- the same for both bridges.
check("bridge headline 34 / 1.1 (kotlin)", "fontSize = 34.sp" in bridge_kt and "(34f * 1.1f).sp" in bridge_kt)
check("bridge headline 34 / 1.1 (swift)", "fontSize: 34, lineHeightMultiple: 1.1" in bridge_sw)
check("bridge headline tracking (kotlin)", "(-0.015).em" in bridge_kt)
check("bridge headline tracking (swift)", "trackingEm: -0.015" in bridge_sw)

# NOT A STEP, SO THERE IS NO BACK. Android swallows the system back; iOS has no NavigationStack and
# therefore no pop gesture to suppress (ProfileEmbrace.swift explains why that is not a gap).
check("bridge back is swallowed (kotlin)", "BackHandler(enabled = true)" in bridge_kt)
check("bridge has no back control (swift)", "onBack" not in bridge_sw)

# The bullet dots are ELEMENTS, not glyphs -- no unicode bullet, no emoji, no list marker.
for label, text in (("kotlin", embrace_kt), ("swift", embrace_sw)):
    check("155 no bullet glyph (%s)" % label, "•" not in text)
check("155 dot is 7 round orange (kotlin)", ".size(7.dp)" in embrace_kt and "Orange" in embrace_kt)
check("155 dot is 7 round orange (swift)",
      "width: 7, height: 7" in embrace_sw and "liqOrange" in embrace_sw)

# Copy — final strings, both platforms, quoted from the ticket.
for label, text in (("kotlin", embrace_kt), ("swift", embrace_sw)):
    check("155 anonymous greeting (%s)" % label, "Glad you're here." in text)
    check("155 named greeting (%s)" % label, "Nice to see you," in text)
    check("155 headline em is 'behind' (%s)" % label, '"behind"' in text)
    check("155 lead copy (%s)" % label, "You are wonderful as you are." in text)
    check("155 bullet 1 (%s)" % label, "Upload meaningful photos." in text)
    check("155 bullet 2 (%s)" % label, "Record a voice or video prompt." in text)
    check("155 closing copy (%s)" % label,
          "More of you means better matches" in text)
    check("155 CTA copy (%s)" % label, '"Upload my photos"' in text)

# ── "The real you" · the group shell ────────────────────────────────────────
#
# BUILT ONCE AND CONSUMED THREE TIMES. Both tickets say it in the same words, and SHOWUP-158 says
# why it matters: the group went from four segments to three when verify profile was dropped, and
# a second copy of the shell would have meant editing three screens instead of one constant.
chrome_kt = read(KT, "profile", "RealYouChrome.kt")
chrome_sw = read(SW, "RealYouChrome.swift")
photos_kt = read(KT, "profile", "ProfilePhotosScreen.kt")
photos_sw = read(SW, "ProfilePhotos.swift")
prompts_kt = read(KT, "profile", "ProfilePromptsScreen.kt")
prompts_sw = read(SW, "ProfilePrompts.swift")
grid_kt = read(KT, "profile", "PhotoGrid.kt")
grid_sw = read(SW, "PhotoGrid.swift")
topics_kt = read(KT, "profile", "PromptTopics.kt")
topics_sw = read(SW, "PromptTopics.swift")
analytics_kt = read(KT, "profile", "ProfileAnalytics.kt")
analytics_sw = read(SW, "ProfileAnalytics.swift")
prompts_vm_kt = read(KT, "profile", "PromptsViewModel.kt")
prompts_vm_sw = read(SW, "PromptsModel.swift")
access_kt = read(KT, "profile", "PhotoAccess.kt")
access_sw = read(SW, "PhotoAccess.swift")

check("group header title (kotlin)", 'SECTION = "The real you"' in chrome_kt)
check("group header title (swift)", 'section = "The real you"' in chrome_sw)

# THREE SEGMENTS, NOT FOUR. SHOWUP-158 supersedes SHOWUP-156's `steps={4}` acceptance criterion in
# as many words, and the count lives in one place so profile verification returning is one edit.
check("group is three segments (kotlin)", "const val COUNT = 3" in chrome_kt)
check("group is three segments (swift)", "static let count = 3" in chrome_sw)
check("no screen hardcodes a segment count (kotlin)",
      "steps = 4" not in code_only(photos_kt) and "steps = 4" not in code_only(prompts_kt))
check("no screen hardcodes a segment count (swift)",
      "steps: 4" not in code_only(photos_sw) and "steps: 4" not in code_only(prompts_sw))
check("both screens read the shared count (kotlin)",
      "RealYouStep.COUNT" in chrome_kt and "RealYouStep.COUNT" in prompts_kt)
check("both screens read the shared count (swift)",
      "RealYouStep.count" in chrome_sw and "RealYouStep.count" in prompts_sw)

# THE BAR IS FIXED ON PHOTOS AND SCROLLS ON PROMPTS. Deliberate, and the thing most likely to be
# "fixed" into a third fixed row later, so the parameter and its one false caller are both asserted.
check("the bar's placement is a parameter (kotlin)", "progressFixed" in chrome_kt)
check("the bar's placement is a parameter (swift)", "progressFixed" in chrome_sw)
check("prompts scrolls its bar (kotlin)", "progressFixed = false" in prompts_kt)
check("prompts scrolls its bar (swift)", "progressFixed: false" in prompts_sw)

# ── SHOWUP-156 · photos ─────────────────────────────────────────────────────

# EVERY SLOT IS 158 IN EVERY STATE. One constant, because the grid's geometry -- and therefore the
# reorder arithmetic -- is only a closed form while the cell height does not depend on its content.
check("slot height is one constant (kotlin)", "PHOTO_SLOT_HEIGHT = 158" in grid_kt)
check("slot height is one constant (swift)", "photoSlotHeight: CGFloat = 158" in grid_sw)
check("four required, six max (kotlin)",
      "PHOTOS_REQUIRED = 4" in grid_kt and "PHOTOS_MAX = 6" in grid_kt)
check("four required, six max (swift)",
      "photosRequired = 4" in grid_sw and "photosMax = 6" in grid_sw)

# THE COUNT ONLY ADVANCES ON A CONFIRMED UPLOAD.
check("the count reads confirmed only (kotlin)",
      "status == UploadStatus.Confirmed }" in grid_kt)
check("the count reads confirmed only (swift)", "$0.status == .confirmed" in grid_sw)
check("three upload statuses (kotlin)",
      all(v in grid_kt for v in ["Confirmed", "InFlight", "Failed"]))
check("three upload statuses (swift)",
      all(v in grid_sw for v in ["confirmed", "inFlight", "failed"]))

# THERE IS NO LIMITED-ACCESS STATE ANYWHERE IN THE BUILD. An earlier draft had a partial-library
# banner; it was DELETED, not redesigned, because it explained a state the system picker makes
# unreachable. A "not in" check is the only way to hold a decision expressed by an absence.
for label, text in (("kotlin", code_only(access_kt)), ("swift", code_only(access_sw))):
    check("156 no limited-access state (%s)" % label, "limited" not in text.lower())
check("156 iOS never reads a library status",
      "PHPhotoLibrary" not in code_only(access_sw)
      and "authorizationStatus(for: .video)" in access_sw)
# And no library permission is declared on either platform.
manifest = read(MOBILE, "android-preview-project", "app", "src", "main", "AndroidManifest.xml")
# The manifest's own comment names both permissions to say they are absent, and an XML comment is
# not something `code_only` strips -- so the element is what gets checked, not the word.
check("156 no library permission (android)",
      'android.permission.READ_EXTERNAL_STORAGE"' not in manifest
      and 'android.permission.READ_MEDIA_IMAGES"' not in manifest)
check("156 camera permission is declared (android)", "permission.CAMERA" in manifest)
plist = read(SW, "Info.plist")
check("156 no library usage string (ios)",
      "<key>NSPhotoLibraryUsageDescription</key>" not in plist)
check("156 camera usage string (ios)", "NSCameraUsageDescription" in plist)

# ONE CARD, TWO MODES.
for label, text in (("kotlin", photos_kt), ("swift", photos_sw)):
    check("156 access title (%s)" % label, "Photo access is needed to continue" in text)
    check("156 access ask body (%s)" % label,
          "Allow access so you can pick your photos." in text)
    check("156 access blocked names the row (%s)" % label,
          "Open Settings, turn on " in text)
    check("156 access ask button (%s)" % label, '"Allow photo access"' in text)
    check("156 access blocked button (%s)" % label, '"Open Settings"' in text)

# Copy -- final strings, both platforms.
for label, text in (("kotlin", photos_kt), ("swift", photos_sw)):
    check("156 headline em (%s)" % label, '"messy hair"' in text)
    check("156 sub copy (%s)" % label, "Show who you actually are" in text)
    check("156 count label (%s)" % label, "required, " in text and "max" in text)
    check("156 first-slot hint (%s)" % label, '"Start with your face"' in text)
    check("156 optional divider (%s)" % label, "Optional · slots 5 & 6" in text)
    check("156 add more (%s)" % label, '"Add more"' in text)
    check("156 reorder hint (%s)" % label,
          "Drag to reorder · the first one is your main photo" in text)
    check("156 main badge (%s)" % label, '"MAIN"' in text)
    check("156 uploading label (%s)" % label, "Uploading…" in text)
    check("156 failure label (%s)" % label, '"Upload failed"' in text)
    check("156 retry (%s)" % label, '"Retry"' in text)
    check("156 sheet title (%s)" % label, '"Add a photo"' in text)
    check("156 sheet library row (%s)" % label, '"Choose from library"' in text)
    check("156 sheet library sub (%s)" % label, '"Pick one or more"' in text)
    check("156 sheet camera row (%s)" % label, '"Take a photo"' in text)
    check("156 sheet camera sub (%s)" % label, '"Use the camera now"' in text)
    check("156 camera blocked sub (%s)" % label,
          "Camera access is off. Turn on Camera in Settings to use it." in text)
    # The default toast interpolates the requirement rather than restating it, so the two halves
    # are matched either side of the number -- which is also what proves the number is not typed
    # twice.
    check("156 three toast variants (%s)" % label,
          "Upload at least " in text and " photos to continue" in text
          and "Turn on Photos in Settings to continue" in text
          and "Allow photo access to continue" in text)

# ── SHOWUP-158 · prompts ────────────────────────────────────────────────────

check("158 fifteen topics (kotlin)", topics_kt.count("PromptTopic(") >= 15)
check("158 fifteen topics (swift)", topics_sw.count("PromptTopic(id:") >= 15)
check("158 three groups (kotlin)", topics_kt.count("label = \"") == 3)
check("158 three groups (swift)", topics_sw.count("TopicGroup(id:") == 3)
for label, text in (("kotlin", topics_kt), ("swift", topics_sw)):
    check("158 group labels (%s)" % label,
          '"Dating me"' in text and '"Me in real life"' in text
          and '"Opinions & obsessions"' in text)
    check("158 cap is 160 (%s)" % label, "160" in text)
    check("158 counter starts at 100 (%s)" % label, "100" in text)
    check("158 one required, three max (%s)" % label,
          ("PROMPTS_REQUIRED = 1" in text and "PROMPTS_MAX = 3" in text)
          or ("promptsRequired = 1" in text and "promptsMax = 3" in text))
    # THE IDS ARE THE REGISTRY'S, not ours. enums.json 17 is the dictionary and it carries
    # `topic_group` as well. Six of the fifteen were invented against registry 1.3.0, which had no
    # 17, and six were wrong -- and these ids are both the analytics join key and what the
    # `profile_prompts` rows store, so a drift orphans data on both sides at once.
    for topic_id in ("first_date_usually", "ideal_30_min", "cross_town_for",
                     "spontaneous_plan", "thirty_min_feels", "in_real_life_more"):
        check("158 canonical id %s (%s)" % (topic_id, label), '"%s"' % topic_id in text)
    for group_id in ("dating_me", "real_life", "opinions"):
        check("158 group id %s (%s)" % (group_id, label), '"%s"' % group_id in text)
    # The six spellings they replaced must be gone entirely, not merely unused.
    for stale in ('"first_date"', '"ideal_thirty"', '"cross_town"', '"spontaneous"',
                  '"thirty_feels"', '"real_life_more"'):
        check("158 stale id %s is gone (%s)" % (stale, label), stale not in text)
    # `prompt_id` is a family F concept. No family E event carries it, so the helper that built it
    # is gone rather than left to be called by accident.
    check("158 prompt_id is gone with family F (%s)" % label, "__slot" not in text)

for label, text in (("kotlin", prompts_kt), ("swift", prompts_sw)):
    check("158 headline em (%s)" % label, '"personal"' in text)
    check("158 sub copy (%s)" % label,
          "One is enough to continue. Add up to 3 if you're enjoying yourself." in text)
    check("158 section label, none saved (%s)" % label, '"Start with one of these"' in text)
    check("158 section label, some saved (%s)" % label, '"Add another · optional"' in text)
    check("158 suggestion action (%s)" % label, '"Write this"' in text)
    check("158 browse control (%s)" % label, '"Browse all 15 topics"' in text)
    check("158 toast (%s)" % label, '"Write 1 prompt to continue"' in text)
    check("158 topic sheet headline em (%s)" % label, '"topic"' in text)
    check("158 topic sheet sub (%s)" % label,
          "Pick something you'd want a match to actually know." in text)
    check("158 used marker (%s)" % label, '"Used"' in text)
    check("158 example eyebrow (%s)" % label, '"FOR EXAMPLE"' in text)
    check("158 field placeholder (%s)" % label, "Say it like you'd tell it to a friend…" in text)
    check("158 floor line (%s)" % label, '"One good sentence is enough."' in text)
    check("158 cap line (%s)" % label, "That's the full 160" in text)
    check("158 empty-submit line (%s)" % label,
          '"Write a few words to save this prompt."' in text)
    check("158 save (%s)" % label, '"Save"' in text)

# THE CAP IS AMBER, NOT DANGER. The single thing in the write sheet most likely to be "corrected"
# later: red says you did something wrong, and writing to the end of the box is not wrong.
check("158 cap is amber not danger (kotlin)",
      "atCap -> Orange" in prompts_kt and "atCap -> Danger" not in prompts_kt)
check("158 cap is amber not danger (swift)",
      "if atCap { return .liqOrange }" in prompts_sw)

# THE EXAMPLE IS NOT A PLACEHOLDER. A placeholder vanishes at the first keystroke, which is exactly
# when the user still wants it -- so the two strings are different things and both must exist.
check("158 example is not the placeholder (kotlin)",
      "EXAMPLE_EYEBROW" in prompts_kt and "PLACEHOLDER" in prompts_kt)
check("158 example is not the placeholder (swift)",
      "exampleEyebrow" in prompts_sw and "placeholder" in prompts_sw)

# ── SHOWUP-158 · the tracking registry, at 1.4.2 ────────────────────────────
#
# Added 16 September 2026, when the ticket's tracking section was rewritten. Every check here is a
# rule that a reasonable implementation gets WRONG by default, which is the only kind worth a
# checker: the compiler cannot see any of them and neither can a screenshot.

for label, text in (("kotlin", analytics_kt), ("swift", analytics_sw)):
    # THE PAYLOAD STAMP. A payload stamped with a version its values did not come from is worse
    # than an unstamped one, because it looks checked.
    # 1.4.6 SINCE SHOWUP-163. enums.json's registry_version, not events.json's
    # taxonomy_version -- they are different numbers on the same day and the Profile 10 ticket
    # quotes both: "taxonomy 1.4.5 / registry 1.4.6".
    check("158/163 registry stamp is the current enums.json version (%s)" % label,
          'FIELD_REGISTRY_VERSION = "%s"' % _REGISTRY_VERSION in text
          or 'fieldRegistryVersion = "%s"' % _REGISTRY_VERSION in text)
    check("158 no stale registry stamp (%s)" % label,
          'FIELD_REGISTRY_VERSION = "1.4.2"' not in text
          and 'fieldRegistryVersion = "1.4.2"' not in text
          and '"1.3.0"' not in text)

    # FAMILY E, NOT FAMILY F. The four v1.0 names are marked SUPERSEDED -- DO NOT FIRE in
    # events.json, emptied of their payloads so the name resolves to the notice. Nothing on this
    # screen may emit one, so the constants are gone rather than merely uncalled.
    for dead in ("prompt_topic_picker_opened", "prompt_answered", "prompt_edited",
                 "prompt_removed"):
        check("158 family F %s is gone (%s)" % (dead, label), '"%s"' % dead not in text)
    for live in ("prompt_topic_list_opened", "prompt_topic_list_dismissed",
                 "prompt_topic_selected", "prompt_editor_opened", "prompt_editor_dismissed",
                 "prompt_saved", "prompt_example_dismissed", "prompt_char_limit_reached",
                 "prompts_minimum_met"):
        check("158 family E %s (%s)" % (live, label), '"%s"' % live in text)

    # 23, THE CANONICAL SHEET-CLOSE VOCABULARY. One set for every bottom sheet in the product.
    # The write sheet used to say close|scrim|swipe|back; a value outside the four is now a bug.
    for value in ("close", "backdrop", "swipe", "system_back"):
        check("158 dismiss_method %s (%s)" % (value, label), '"%s"' % value in text)
    check("158 no pre-1.4.2 scrim (%s)" % label, '"scrim"' not in text)

    # 18 IS RETIRED (registry 1.4.5, 25 September 2026): "which topics are chosen matters, where
    # they were chosen from does not." This file used to assert the value set and the type that
    # enforced it; both are gone, and what is worth holding is that neither comes back.
    #
    # The values are checked as STRINGS rather than as a type, because the failure to guard
    # against is somebody re-adding `put("entry_point", "suggestion")` by hand after the enums
    # that would have made it a compile error no longer exist.
    _code = code_only(text)
    check("158 no retired entry_point type (%s)" % label,
          "TopicEntryPoint" not in _code and "PromptEntryPoint" not in _code)
    for _retired in ('"suggestion"', '"browse"'):
        check("158 no 18 value %s (%s)" % (_retired, label), _retired not in _code)

    # `is_edit` is what 1.4.5 kept when it retired the rest, and the NEW property on the
    # abandonment event -- the one job `entry_point: "edit"` was doing that nothing else carried.
    check("158 is_edit survives 18 (%s)" % label, '"is_edit"' in text)

    # 8. `prompts_below_minimum` is the sibling of `photos_below_minimum`; `nothing_selected`
    # belongs to a chooser where nothing was ticked, not a screen where nothing was written.
    check("158 prompts_below_minimum (%s)" % label, '"prompts_below_minimum"' in text)
    check("158 nothing_selected is gone (%s)" % label, '"nothing_selected"' not in text)

    # 11. The human label, which is what a person searches for in the analytics tool.
    check("158 screen_name is ProfilePrompts (%s)" % label, '"ProfilePrompts"' in text)
    check("158 old screen_name is gone (%s)" % label, '"Profile - Prompts"' not in text)

    # NEVER THE ANSWER AND NEVER THE DRAFT. Every builder takes a LENGTH, so no signature can
    # carry the text even by accident; the bucket is computed inside.
    check("158 length is bucketed, not sent (%s)" % label,
          "length_bucket" in text and "promptLengthBucket" in text)
    check("158 no answer or draft property (%s)" % label,
          '"answer"' not in text and '"draft"' not in text)

    # `selection_index`, and the rule it is most often got wrong by: it counts per VISIT TO THE
    # STEP, not per sheet.
    check("158 selection_index (%s)" % label, '"selection_index"' in text)
    # `prompt_count` on the accepted Continue -- 1 to 3, screen-scoped.
    check("158 prompt_count on step completed (%s)" % label, '"prompt_count"' in text)

# THE TESTS PIN THE REGISTRY TOO, and that is where this bit twice.
#
# The source files were corrected to 1.4.2 and the SWIFT tests still asserted 1.3.0 and
# "Profile - Prompts" -- so the Kotlin suite went green locally, the Swift suite failed on a macOS
# runner ten minutes later, and the only thing that knew were the assertions themselves. A version
# string pinned in a test is as much a declaration of the vocabulary as the constant it checks.
KT_TESTS = os.path.join(MOBILE, "android-preview-project", "app", "src", "test", "java",
                        "com", "showup", "profile")
SW_TESTS = os.path.join(MOBILE, "ios-app", "ShowUpWelcomeTests")
for label, folder in (("kotlin", KT_TESTS), ("swift", SW_TESTS)):
    if not os.path.isdir(folder):
        continue
    for fn in sorted(os.listdir(folder)):
        if not fn.endswith((".kt", ".swift")):
            continue
        body = code_only(io.open(os.path.join(folder, fn), encoding="utf-8").read())
        check("158 %s pins no stale registry (%s)" % (fn, label), '"1.3.0"' not in body)
        check("158 %s pins no stale screen_name (%s)" % (fn, label),
              '"Profile - Prompts"' not in body)

for label, text in (("kotlin", prompts_vm_kt), ("swift", prompts_vm_sw)):
    # THE COUNTER IS NOT RESET BY A SHEET. It lives in the persisted state, so neither a sheet
    # closing nor a process death restarts it.
    check("158 selection count is persisted state (%s)" % label, "topicSelections" in text)
    # AN EDIT FIRES ONLY THE EDITOR EVENT. If `promptTopicSelected` appears in the edit path the
    # topic-demand chart is inflated with re-edits.
    check("158 edit reports only the editor (%s)" % label,
          "promptTopicSelected" not in text.split("editPrompt")[-1].split("draftChanged")[0])
    # The cap is reported once per editor session, not per keystroke.
    check("158 cap reported once per session (%s)" % label, "charLimitReportedFor" in text)
    # The refused Continue is the ONLY record that a user tried to leave -- Continue is never
    # disabled, so there is no dead button to infer it from.
    check("158 refused Continue is recorded (%s)" % label, "continuePressed" in text)

# ── SHOWUP-158 · the sheets can be closed four ways, and say which ──────────
#
# The ticket asks for swipe twice -- "closing the sheet by X, scrim tap or swipe keeps what was
# typed", and again in the tracking criteria -- and neither platform had it. The scrim and the X
# also called one lambda, so the two acts were indistinguishable at the call site.
#
# READ FROM THE SHARED CHROME SINCE 17 SEPTEMBER 2026. These lived as private types inside the two
# prompt-screen files and were extracted into `designsystem/SheetScaffold.kt` and
# `ShowUpWelcome/SheetScaffold.swift` when SHOWUP-161's media sheet became their second user --
# which is the rule about shared primitives, not a refactor of convenience. The behaviour did not
# change; only where it lives did, so these checks follow it rather than being deleted.
sheet_kt = read(KT, "designsystem", "SheetScaffold.kt")
sheet_sw = read(SW, "SheetScaffold.swift")

for label, text in (("kotlin", sheet_kt), ("swift", sheet_sw)):
    check("158 the scrim reports backdrop (%s)" % label, "Backdrop" in text or ".backdrop" in text)
    check("158 the X reports close (%s)" % label, "Close)" in text or ".close)" in text)
    check("158 the sheet can be swiped down (%s)" % label,
          "Swipe" in text or ".swipe" in text)
check("158 the Android back gesture closes the sheet", "BackHandler" in sheet_kt)
check("158 swipe has a threshold (kotlin)", "SWIPE_DISMISS_PX" in sheet_kt)
check("158 swipe has a threshold (swift)", "sheetSwipeDismiss" in sheet_sw)

# THE CHROME IS SHARED, AND THAT IS ITSELF THE CHECK. A second copy of a sheet surface is the
# `PillButton` mistake, which cost this project four silent drifts over three weeks; both media
# files must consume the shared one rather than growing their own.
check("161 the media sheet uses the shared chrome (kotlin)",
      "SheetScaffold" in read(KT, "profile", "ProfileMediaScreen.kt"))
check("161 the media sheet uses the shared chrome (swift)",
      "SheetScaffold" in read(SW, "ProfileMedia.swift"))
check("161 no second sheet surface (kotlin)",
      "private fun SheetScaffold" not in read(KT, "profile", "MediaPromptSheet.kt"))
check("161 no second sheet surface (swift)",
      "struct SheetScaffold" not in read(SW, "MediaPromptSheet.swift"))

# ════════════════════════════════════════════════════════════════════════════
# SHOWUP-161 · the media step
# ════════════════════════════════════════════════════════════════════════════
#
# The copy is QUOTED from the ticket's "Copy -- final strings" section, not read back out of the
# code. A check that compared the code to itself would pass on any string at all, which is the one
# thing a copy verifier must not do.

media_kt = read(KT, "profile", "MediaCards.kt")
media_sw = read(SW, "MediaCards.swift")
prompts161_kt = read(KT, "profile", "MediaPrompts.kt")
prompts161_sw = read(SW, "MediaPrompts.swift")
screen161_kt = read(KT, "profile", "ProfileMediaScreen.kt")
screen161_sw = read(SW, "ProfileMedia.swift")
capture_kt = read(KT, "profile", "MediaCaptureScreen.kt")
capture_sw = read(SW, "MediaCaptureView.swift")
model161_kt = read(KT, "profile", "MediaViewModel.kt")
model161_sw = read(SW, "MediaModel.swift")
state161_kt = read(KT, "profile", "MediaModel.kt")
state161_sw = read(SW, "MediaState.swift")
access161_kt = read(KT, "profile", "MediaAccess.kt")
access161_sw = read(SW, "MediaAccess.swift")

# ── copy, verbatim from the ticket ─────────────────────────────────────────
for label, text in (("kotlin", media_kt), ("swift", media_sw)):
    for name, literal in (
        ("optional pill", "Optional \u00b7 you can skip this"),
        ("headline em", "hear you"),
        ("footer skip", "Skip for now"),
        ("video title", "A 10-second video"),
        ("video hint", "Filmed here in the app. Ten seconds, one prompt."),
        ("voice title", "A 15-second voice note"),
        ("voice hint", "Just your voice, answering one prompt."),
        ("preview eyebrow", "One of 11 prompts"),
        ("card CTA", "See the prompts"),
        ("caption eyebrow", "Prompt \u00b7 shown on your profile"),
        ("video row label", "Your video"),
        ("saved chip", "Saved"),
        ("sheet em", "answer"),
        ("sheet sub video", "Ten seconds is short on purpose."),
        ("sheet sub voice", "Fifteen seconds, just your voice."),
        ("own-idea sub", "Say or show whatever you like"),
        ("commit empty", "Choose a prompt to continue"),
        ("commit video", "Film 10 seconds"),
        ("commit voice", "Record 15 seconds"),
        ("cancel pill", "Cancel"),
        ("rec chip", "REC"),
        ("voice hint recording", "Listening \u00b7 keep going"),
        ("voice hint review", "Hear it back before you keep it"),
        ("review primary video", "Use this clip"),
        ("review primary voice", "Use this recording"),
    ):
        check("161 copy: %s (%s)" % (name, label), literal in text)

# The headline is built from three parts, so it is checked where it is assembled.
for label, text in (("kotlin", media_kt), ("swift", media_sw)):
    check("161 headline lead (%s)" % label, "Show your face. Let them " in text)

# ── the eleven prompts, verbatim ───────────────────────────────────────────
#
# "Do not retype them, keep the order." Two of the eleven are quoted here in full -- the one with
# an apostrophe and the one with a number, which are the two a retype gets wrong -- plus the
# escape hatch's em dash.
for label, text in (("kotlin", prompts161_kt), ("swift", prompts161_sw)):
    check("161 prompt 7 verbatim (%s)" % label,
          "What I usually look like when I'm relaxed and happy" in text)
    check("161 prompt 10 verbatim (%s)" % label,
          "My favorite way to spend an easy 30 minutes outside" in text)
    check("161 escape hatch verbatim (%s)" % label, "Something else \u2014 my own idea" in text)
    check("161 the escape hatch id (%s)" % label, "own_idea" in text)
    check("161 cold start video (%s)" % label, "relaxed_and_happy" in text)
    check("161 cold start voice (%s)" % label, "relaxing_sound" in text)

# ── the 16 September pass, which is the point of the ticket ────────────────
for label, text in (("kotlin", code_only(media_kt + screen161_kt)),
                    ("swift", code_only(media_sw + screen161_sw))):
    # NO UPLOAD PATH. Media is captured in the app or not at all.
    check("161 no upload-from-library path (%s)" % label,
          "PickVisualMedia" not in text and "PHPicker" not in text)
    # NO CAPTION FIELD. The chosen prompt IS the caption.
    check("161 no caption field (%s)" % label,
          "TextField" not in text and "BasicTextField" not in text)

# COMMENTS STRIPPED, and that is not a convenience. Every one of these is a "must NOT appear"
# check, and each capture file opens with a header explaining exactly why the thing must not
# appear -- so the better the file is documented, the more of these fail. The same reason
# `check-analytics-parity.py` strips comments before scanning for event names.
for label, text in (("kotlin", code_only(capture_kt)), ("swift", code_only(capture_sw))):
    # CAPTURE RUNS THROUGH OUR OWN SESSION.
    check("161 no system camera UI (%s)" % label,
          "UIImagePickerController" not in text and "ACTION_IMAGE_CAPTURE" not in text)
    # THE CHROME FALLS AWAY.
    check("161 capture has no header (%s)" % label, "AppHeader" not in text)
    check("161 capture has no progress bar (%s)" % label, "StepProgress" not in text)
    check("161 capture has no skip link (%s)" % label, "SkipLink" not in text)

# ── the caps and the thresholds ────────────────────────────────────────────
for label, text in (("kotlin", state161_kt), ("swift", state161_sw)):
    check("161 video cap is 10 seconds (%s)" % label, "10_000" in text)
    check("161 voice cap is 15 seconds (%s)" % label, "15_000" in text)
    check("161 the interruption threshold is 2 seconds (%s)" % label, "2_000" in text)
    check("161 48 deterministic bars (%s)" % label, "48" in text)

# ── the rules that are wrong by default ────────────────────────────────────
for label, text in (("kotlin", model161_kt), ("swift", model161_sw)):
    # The artefact is created by ACCEPT, never by Stop.
    check("161 recorded fires on accept (%s)" % label,
          "mediaPromptRecorded" in text and "acceptTake" in text)
    # Reaching the cap does what Stop does.
    check("161 the cap stops the take (%s)" % label, "MaxLength" in text or "maxLength" in text)
    # Retake on review does NOT reopen the list.
    check("161 retake from review keeps the prompt (%s)" % label, "retakeFromReview" in text)
    # Retake on a filled card DOES reopen it, preselected.
    check("161 retake from a card reopens the list (%s)" % label, "retakeFromCard" in text)
    # Permissions are asked on the commit CTA.
    check("161 permissions are asked on commit (%s)" % label, "commitPrompt" in text)
    # Continue is never gated: no validation event on this screen.
    check("161 no validation event on continue (%s)" % label,
          "formValidationFailed" not in code_only(text)
          and "FORM_VALIDATION_FAILED" not in code_only(text))

# ── the permission matrix ──────────────────────────────────────────────────
for label, text in (("kotlin", access161_kt), ("swift", access161_sw)):
    check("161 a blocked mic blocks both cards (%s)" % label, "Microphone" in text)
    check("161 nothing is drawn before the first refusal (%s)" % label,
          "NotDetermined" in text or "notDetermined" in text)
    check("161 can-ask and blocked are two behaviours (%s)" % label,
          ("CanAsk" in text or "canAsk" in text) and ("Blocked" in text or "blocked" in text))
    check("161 the platform's own row label (%s)" % label, "platformLabel" in text)

# The blocked copy, quoted from the ticket. The row label itself is substituted at runtime, so the
# tails are what can be checked.
for label, text in (("kotlin", media_kt), ("swift", media_sw)):
    check("161 blocked camera copy (%s)" % label, "in Settings to film." in text)
    check("161 blocked mic copy (%s)" % label, "in Settings to record." in text)
    check("161 can-ask camera copy (%s)" % label,
          "Allow camera access to film your 10 seconds." in text)
    check("161 can-ask mic copy (%s)" % label, "Allow microphone access to record." in text)

# ── entitlements ───────────────────────────────────────────────────────────
manifest = read(os.path.join(MOBILE, "android-preview-project", "app", "src", "main",
                             "AndroidManifest.xml"))
plist = read(SW, "Info.plist")
check("161 android declares RECORD_AUDIO", "android.permission.RECORD_AUDIO" in manifest)
check("161 android declares CAMERA", "android.permission.CAMERA" in manifest)
check("161 android does not require a microphone",
      'android:name="android.hardware.microphone" android:required="false"' in manifest)
check("161 ios declares NSMicrophoneUsageDescription",
      "NSMicrophoneUsageDescription" in plist)
check("161 the camera string covers video too", "film your 10-second video" in plist)

# ── the step ids, which are the one thing two platforms get wrong separately ─
for label, text in (("kotlin", prompts161_kt), ("swift", prompts161_sw)):
    check("161 step id media_video (%s)" % label, "media_video" in text)
    check("161 step id media_voice (%s)" % label, "media_voice" in text)

# ── SHOWUP-156 · a picked photo is normalised before it is sent ─────────────
#
# Two real failures, both of which rendered as the same `Upload failed` with a Retry that re-sent
# identical bytes: Android reported the picker's own MIME type, which is `image/heic` on a modern
# phone and not in the server's allowed set (415); and neither platform downscaled, so a
# full-resolution photo ran past the 8 MB cap (413).
upload_kt = read(KT, "profile", "PhotoUpload.kt")
upload_sw = read(SW, "PhotoUpload.swift")
picker_kt = read(KT, "MainActivity.kt")
picker_sw = read(SW, "PhotoPicker.swift")

for label, text in (("kotlin", upload_kt), ("swift", upload_sw)):
    # THE SAME NUMBERS ON BOTH PLATFORMS. A photo that looked fine on one and soft on the other
    # would be a difference nobody chose, and nothing else in the build would notice.
    check("156 upload cap is 2048 (%s)" % label, "2048" in text)
    check("156 upload quality (%s)" % label,
          "UPLOAD_JPEG_QUALITY = 90" in text or "uploadJPEGQuality: CGFloat = 0.9" in text)
    # Null/nil when the photo is already small enough, so a small photo is never upscaled.
    check("156 no upscaling (%s)" % label, "maxEdge" in text)

for label, text in (("kotlin", picker_kt), ("swift", picker_sw)):
    # ALWAYS JPEG, whatever came in -- the one type every path can produce and the server accepts.
    check("156 uploads are jpeg (%s)" % label, '"image/jpeg"' in text)
    # The picker's own MIME type must not reach the server again.
    check("156 the picker's mime is not forwarded (%s)" % label,
          "resolver.getType" not in text)
    check("156 the decode is downscaled (%s)" % label, "uploadTargetSize" in text)

# The decode never materialises the full-resolution image: `ImageDecoder` sizes while it reads and
# `CGImageSourceCreateThumbnailAtIndex` decodes once at the size asked for. A 12 MP photo is about
# 48 MB of pixels and this screen can have six in flight.
check("156 android decodes downsampled", "ImageDecoder.decodeBitmap" in picker_kt)
check("156 android can compress the result", "ALLOCATOR_SOFTWARE" in picker_kt)
check("156 ios decodes through ImageIO", "CGImageSourceCreateThumbnailAtIndex" in picker_sw)
# Re-encoding drops the EXIF orientation with the container, so it has to be applied on the way in
# or every portrait photo uploads on its side.
check("156 ios applies the exif transform",
      "kCGImageSourceCreateThumbnailWithTransform" in picker_sw)
check("156 ios does not return the embedded thumbnail",
      "kCGImageSourceCreateThumbnailFromImageAlways" in picker_sw)

# ── a pill's outline follows its own height ─────────────────────────────────
#
# The browse sheet's topic rows are `borderRadius: 9999` with `minHeight: 50` -- a pill that grows
# when its question wraps to two lines. Android clipped them with `CircleShape`, whose radius is
# half the height, but STROKED them at a fixed 25: correct at exactly 50 tall, and on a taller row
# the parts of the stroke outside the clip were cut away, leaving a purple outline with pieces
# missing on the longest questions. iOS never had it -- `Capsule()` is a pill by construction.
check("158 the topic row's outline follows its height (kotlin)", "outlinePill" in prompts_kt)
check("158 the topic row is a capsule (swift)", "Capsule()" in prompts_sw)

# ── the two platforms test the same photo-grid behaviour ────────────────────
#
# The grid's boxes are POSITIONAL: emptying one leaves it empty and the next photo goes back into
# it. That rule was changed on both platforms at once and the Kotlin suite was updated while the
# Swift one was not, so CI caught it on a macOS runner ten minutes later -- the second time this
# session that a behaviour change landed in one test suite and not its mirror.
#
# A checker cannot compare assertions. It can insist the mirrored SCENARIO exists on both sides,
# which is what actually went missing.
_photo_tests_kt = io.open(os.path.join(
    MOBILE, "android-preview-project", "app", "src", "test", "java", "com", "showup",
    "profile", "PhotosUploadStateTest.kt"), encoding="utf-8").read()
_photo_tests_sw = io.open(os.path.join(
    MOBILE, "ios-app", "ShowUpWelcomeTests", "PhotosReorderTests.swift"), encoding="utf-8").read()
for scenario, kt_frag, sw_frag in (
    ("deleting the second photo leaves the others in place",
     "leaves the others exactly where they were", "LeavesTheOthersExactlyWhereTheyWere"),
    ("a new photo goes into the box that was emptied",
     "goes into the box that was emptied", "GoesIntoTheBoxThatWasEmptied"),
    ("the first free box is the gap, not the end",
     "first free box", "FirstFreeBoxIsTheGap"),
):
    check("156 %s (kotlin)" % scenario, kt_frag in _photo_tests_kt)
    check("156 %s (swift)" % scenario, sw_frag in _photo_tests_sw)

# ── the eyebrow labels are UPPERCASE, as the CSS transforms them ────────────
#
# The reference marks these `textTransform: 'uppercase'`. CSS applies that at render, so the
# strings are authored in sentence case and the ticket quotes them that way -- which is why the
# constants stay sentence case and the transform happens at the render site. Nothing did it, so
# six labels shipped in sentence case; two more (`MAIN`, `FOR EXAMPLE`) had the capitals typed
# into the constant by hand, which is exactly how the omission hid: some eyebrows looked right, so
# none of them looked wrong.
for label, text in (("kotlin", prompts_kt), ("swift", prompts_sw)):
    check("158 section label is uppercased (%s)" % label, "eyebrowCase" in text)
for label, text in (("kotlin", photos_kt), ("swift", photos_sw)):
    check("156 counter row is uppercased (%s)" % label,
          text.count("eyebrowCase") >= 2)

# ── every drawn icon speaks the 24-grid, not Dp ─────────────────────────────
#
# `Icon` scales its paths with `withTransform { scale(s, s) }`. A stroke converted to pixels
# OUTSIDE that transform is multiplied by the scale a SECOND time inside it, which is the bug this
# guards: every icon in the app came out `density` times too heavy -- 2.6x on a 2.625x phone, fat
# enough that the embrace CTA's arrowhead merged into a solid triangle. It looked right at exactly
# one density, 1.0, so every preview agreed with it.
#
# `IconStrokeTest` measures the rendered ink. This keeps the CALL SITES from reintroducing the
# unit that caused it.
import glob as _glob
_kt_sources = _glob.glob(os.path.join(KT, "**", "*.kt"), recursive=True)
for _f in _kt_sources:
    _body = io.open(_f, encoding="utf-8").read()
    for _line in _body.splitlines():
        if "Icon(" in _line and "strokeWidth" in _line and "CheckGlyph" not in _line:
            check("icon stroke is grid units, not dp (%s)" % os.path.basename(_f),
                  ".dp" not in _line.split("strokeWidth")[1])

# ── both screens · Continue is never disabled ───────────────────────────────
#
# The group-wide rule, and the reason the specific requirement is ever read: a dead button cannot
# say what is missing. Neither screen's CTA takes an `enabled` argument at all.
check("156 Continue is never disabled (kotlin)", "enabled = false" not in code_only(photos_kt))
check("156 Continue is never disabled (swift)", "enabled: false" not in code_only(photos_sw))
check("158 Continue and Save are never disabled (kotlin)",
      "enabled = false" not in code_only(prompts_kt))
check("158 Continue and Save are never disabled (swift)",
      "enabled: false" not in code_only(prompts_sw))

# The CTA is ORANGE on both screens and 52 rather than the 56 everything else draws.
for label, text in (("kotlin", photos_kt), ("swift", photos_sw)):
    check("156 CTA circle 52 (%s)" % label, "circleSize" in text and "52" in text)
for label, text in (("kotlin", prompts_kt), ("swift", prompts_sw)):
    check("158 CTA circle 52 (%s)" % label, "circleSize" in text and "52" in text)


# ── SHOWUP-162 · the notifications permission ask ─────────────────────────
#
# THE ABSENCES ARE THE DESIGN, and the ticket makes four of them acceptance criteria: no
# AppHeader, no StepProgress, no way backwards, no `Open Settings`. Each is the kind of thing a
# later consistency pass adds without malice, and a "not in" assertion is the only way to hold a
# decision that is expressed by something not being there.
notify_kt = code_only(read(KT, "profile", "ProfileNotificationsScreen.kt"))
notify_sw = code_only(read(SW, "ProfileNotifications.swift"))
analytics_kt = read(KT, "profile", "ProfileAnalytics.kt")
analytics_sw = read(SW, "ProfileAnalytics.swift")

check("162 no AppHeader (kotlin)", "AppHeader" not in notify_kt)
check("162 no AppHeader (swift)", "AppHeader" not in notify_sw)
check("162 no StepProgress (kotlin)", "StepProgress" not in notify_kt)
check("162 no StepProgress (swift)", "StepProgress" not in notify_sw)
check("162 no SkipLink (kotlin)", "SkipLink" not in notify_kt)
check("162 no SkipLink (swift)", "SkipLink" not in notify_sw)

# BACKWARDS IS BLOCKED, and on each platform that is one specific line. Android needs an enabled
# BackHandler or the gesture pops the screen; iOS needs the nav bar's own back hidden.
check("162 back is blocked (kotlin)", "BackHandler(enabled = true)" in notify_kt)
check("162 back is blocked (swift)", "navigationBarBackButtonHidden(true)" in notify_sw)

# NOT A STEP. §2 holds a `notifications` step_id whose index is a dash, and the note added on
# 21 September 2026 says that is not a licence to fire `profile_step_viewed` -- the id exists so a
# future skip has a stable value, and this screen has no skip CTA.
check("162 fires no step event (kotlin)", "stepViewed" not in notify_kt)
check("162 fires no step event (swift)", "stepViewed" not in notify_sw)

# NO RECOVERY HERE. A denial is recovered on Stay reachable (10), which owns the notifications row
# and is where a Settings deep link belongs. The same recovery in two places, one of which cannot
# re-prompt, is the failure the ticket names.
check("162 no Settings path (kotlin)", "Settings" not in notify_kt)
check("162 no Settings path (swift)", "Settings" not in notify_sw)

# Rule 5's named exception -- the ambient backdrop, from the shared component rather than three
# pasted gradients, which the ticket asks for by name in its dependencies.
check("162 uses the shared backdrop scaffold (kotlin)", "WelcomeScaffold" in notify_kt)
check("162 uses the shared backdrop scaffold (swift)", "WelcomeScaffold" in notify_sw)
check("162 headline block top padding 40 (kotlin)", "topPadding = 40.dp" in notify_kt)
check("162 headline block top padding 40 (swift)", "topPadding: 40" in notify_sw)
check("162 gutter 28, not the group's 24 (kotlin)", "gutter = 28.dp" in notify_kt)
check("162 gutter 28, not the group's 24 (swift)", "gutter: 28" in notify_sw)
check("162 body block top padding 20 (kotlin)", "padding(top = 20.dp)" in notify_kt)
check("162 body block top padding 20 (swift)", "padding(.top, 20)" in notify_sw)
check("162 list gap 16 (kotlin)", "spacedBy(16.dp)" in notify_kt)
check("162 list gap 16 (swift)", "spacing: 16" in notify_sw)
check("162 list margin-top 22 (kotlin)", "padding(top = 22.dp)" in notify_kt)
check("162 list margin-top 22 (swift)", "padding(.top, 22)" in notify_sw)

# Rule 7's named exception, and the ONE difference from the bridge: no trailing arrow. 05's button
# ends in an 18 arrow-right and this one does not -- the label is the whole button.
check("162 CTA is sunset (kotlin)", "PrimaryButtonVariant.Sunset" in notify_kt)
check("162 CTA is sunset (swift)", "variant: .sunset" in notify_sw)
check("162 CTA has NO trailing slot (kotlin)", "trailing" not in notify_kt)
check("162 CTA has NO trailing slot (swift)", "trailing:" not in notify_sw)
check("162 CTA wrapper margin-bottom 18 (kotlin)", "padding(bottom = 18.dp)" in notify_kt)
check("162 CTA wrapper margin-bottom 18 (swift)", "padding(.bottom, 18)" in notify_sw)

# ONE flex spacer with a 12 floor, and it is `required` on Android. `weight` hands down a FIXED
# height, so an ordinary `heightIn(min =)` after it is clamped to whatever is left -- measured on
# an iPhone SE, where the 12 became 9.
check("162 one spacer, floor 12 (kotlin)", notify_kt.count("requiredHeightIn(min = 12.dp)") == 1)
check("162 one spacer, floor 12 (swift)", notify_sw.count("Spacer(minLength: 12)") == 1)

# THE CTA IS PINNED BELOW THE SCROLL. Scrolling alone left it below the fold on the Galaxy Fold
# cover screen, which the iOS sweep caught as a CTA that was simply not drawn. See E20.
check("162 the CTA is pinned, not scrolled (kotlin)", "footer = {" in notify_kt)
check("162 the CTA is pinned, not scrolled (swift)", "footer: {" in notify_sw)

# FIVE ROWS, NOT SIX, and the order is content rather than layout: row 1 is why the user is in the
# flow at all and row 5 is the one that protects their time.
for _n, _title in enumerate(
    ["Match alert", "Like received", "Meeting details change", "Date reminder", "Date cancelled"], 1
):
    check("162 row %d title (kotlin)" % _n, '"%s"' % _title in notify_kt)
    check("162 row %d title (swift)" % _n, '"%s"' % _title in notify_sw)

# THE COPY, QUOTED BACK FROM THE TICKET. Spaced em dashes, never hyphens, and `e.g.` lower case
# with both points -- the ticket says so outright, so the assertion carries the characters.
for _label, _txt in [
    ("headline lead", u"Never miss "),
    ("headline em", u"a date"),
    ("headline tail", u" with Notifications!"),
    ("lead", u"No spam! Every notification is about your dates and helps you to never miss one."),
    # RENAMED BY SHOWUP-163: the button no longer enables anything, it goes to the screen
    # that does. The old label is asserted ABSENT in the 163 section, because a copy pass
    # restoring "Enable notifications" would read as a fix rather than as a regression.
    ("cta", u"Continue"),
    ("row 1", u"Get instant notifications when you receive a match and never miss out on a date."),
    ("row 2", u"Don't miss your chance to meet — we surface it the second it arrives."),
    ("row 3", u"Get notified if your date asks — e.g. meet time change, running late."),
    ("row 4", u"A heads-up an hour out — never arrive late."),
    ("row 5", u"Never waste time waiting — get notified, look for someone else instead."),
    ("premium tag", u"Premium"),
]:
    check("162 copy: %s (kotlin)" % _label, _txt in notify_kt)
    check("162 copy: %s (swift)" % _label, _txt in notify_sw)

# The Premium tag is a LABEL. Not tappable, opens nothing, no paywall behind it -- and it reads as
# part of row 2's title rather than as a control of its own.
_tag_kt = notify_kt.split("fun PremiumTag")[1].split("\n}")[0]
_tag_sw = notify_sw.split("struct PremiumTag")[1].split("\n}")[0]
check("162 Premium tag is not a control (kotlin)",
      "clickable" not in _tag_kt and "onClick" not in _tag_kt)
check("162 Premium tag is not a control (swift)",
      "Button" not in _tag_sw and "onTapGesture" not in _tag_sw)

# The registry row, from §11 and never typed at a call site.
check("162 the screen row is registry-backed (kotlin)",
      'Notifications("profile_notifications", "ProfileNotifications")' in analytics_kt)
check("162 the screen row is registry-backed (swift)",
      'case notifications = "profile_notifications"' in analytics_sw)

# The four family G events, all four new, and the three that must NOT fire here.
for _ev in ["permission_prompted", "permission_os_sheet_shown", "permission_result",
            "permission_status_changed"]:
    check("162 %s exists (kotlin)" % _ev, '"%s"' % _ev in analytics_kt)
    check("162 %s exists (swift)" % _ev, '"%s"' % _ev in analytics_sw)

for _ev in ["permission_denied_recovery_shown", "permission_settings_opened", "consent_changed"]:
    check("162 %s must not fire here (kotlin)" % _ev, _ev not in notify_kt)
    check("162 %s must not fire here (swift)" % _ev, _ev not in notify_sw)

# `limited` is a photo-library state and cannot occur for notifications. Both builders take a
# Boolean, so there is no third value to pass by mistake.
check("162 permission_result cannot be limited (kotlin)",
      "granted: Boolean" in analytics_kt and '"limited"' not in code_only(analytics_kt))
check("162 permission_result cannot be limited (swift)",
      "granted: Bool" in analytics_sw and '"limited"' not in code_only(analytics_sw))


# ── SHOWUP-162 · the row, against the reference file rather than the ticket ──
#
# "Order of authority: reference file wins on numbers." Reading it found three defects the ticket
# prose could not have: `alignItems: 'baseline'` on the title row, which Android had as centre; the
# tag's `transform: translateY(-1px)`, which Android had as a bottom padding and is not the same
# thing; and the title's `lineHeight: 1.2`, which iOS did not set at all. Each was correct on
# exactly one platform, which is the shape a parity check exists to catch.

# The pip. THE MEDIA CARD'S RECIPE at this screen's own numbers -- 40 at radius 12 against the
# card's 42 at 13 -- because "same recipe" is about the vocabulary and the reference wins on size.
check("162 pip is 40 (kotlin)", "size(40.dp)" in notify_kt)
check("162 pip is 40 (swift)", "frame(width: 40, height: 40)" in notify_sw)
check("162 pip radius 12 (kotlin)", "RoundedCornerShape(12.dp)" in notify_kt)
check("162 pip radius 12 (swift)", "cornerRadius: 12" in notify_sw)
check("162 pip is the lilac wash (kotlin)", "background(LilacWash)" in notify_kt)
check("162 pip is the lilac wash (swift)", "Gradients.lilac()" in notify_sw)

# `--su-grad-lilac` IS 180 DEGREES, and Compose's default linear gradient is the 135 diagonal --
# which is right for sunset and wrong for this, and was wrong on most Android surfaces until E25.
# One definition, vertical, and no call site allowed to re-spell the brush.
_ds_kt = code_only(read(KT, "designsystem", "DesignSystem.kt"))
check("162 the lilac wash is VERTICAL, per the 180deg token (kotlin)",
      "Brush.verticalGradient(colorStops = LilacStops.toTypedArray())" in _ds_kt)
check("162 the lilac wash is VERTICAL, per the 180deg token (swift)",
      "startPoint: .top, endPoint: .bottom" in code_only(read(SW, "DesignSystem.swift")))
for _screen in ["ProfilePhotosScreen.kt", "ProfileDobScreen.kt", "ProfilePromptsScreen.kt",
                "MediaCards.kt", "MediaPromptSheet.kt", "ProfileNotificationsScreen.kt"]:
    check("162 no second spelling of the lilac brush in %s" % _screen,
          "LilacStops" not in code_only(read(KT, "profile", _screen)))
check("162 icon 20 at stroke 1.8 in primary-500 (kotlin)",
      "20.dp, tint = Purple, strokeWidth = 1.8f" in notify_kt)
check("162 icon 20 at stroke 1.8 in primary-500 (swift)",
      "size: 20, stroke: 1.8, tint: .liqPurple" in notify_sw)

# The row itself: gap 14, items top-aligned.
check("162 row gap 14 (kotlin)", "spacedBy(14.dp)" in notify_kt)
check("162 row gap 14 (swift)", "spacing: 14" in notify_sw)
check("162 row aligns to the top (kotlin)", "verticalAlignment = Alignment.Top" in notify_kt)
check("162 row aligns to the top (swift)", "alignment: .top" in notify_sw)

# The title: Lora 700 / 16 / 1.2 / -0.005em, and BASELINE-aligned with the tag beside it.
check("162 title is Lora 700 at 16 (kotlin)",
      "fontFamily = Lora, fontWeight = FontWeight.Bold" in notify_kt and "fontSize = 16.sp" in notify_kt)
check("162 title is Lora 700 at 16 (swift)", "F.lora(16, bold: true)" in notify_sw)
check("162 title line height 1.2 (kotlin)", "lineHeight = (16f * 1.2f).sp" in notify_kt)
check("162 title line height 1.2 (swift)", "lineSpacing(16 * 0.2)" in notify_sw)
check("162 title tracking -0.005em (kotlin)", "letterSpacing = (-0.005).em" in notify_kt)
check("162 title tracking -0.005em (swift)", "tracking(-0.005 * 16)" in notify_sw)
check("162 title and tag align on the BASELINE (kotlin)", "alignByBaseline()" in notify_kt)
check("162 title and tag align on the BASELINE (swift)",
      "alignment: .firstTextBaseline" in notify_sw)
check("162 title-to-tag gap 8 (kotlin)", "spacedBy(8.dp)" in notify_kt)
check("162 title-to-tag gap 8 (swift)", "spacing: 8" in notify_sw)

# THE PILL IS MEASURED BEFORE THE TITLE, and this is a bug no fit sweep can see. The spec sheet
# has the row at `flex-wrap: wrap` and neither platform wraps -- they share the width, and an
# unweighted Text is measured first at the full width. At 2.0x type on a 320 frame that left the
# pill 25dp of the 123 it needed, with PREMIUM ellipsised inside a stub capsule and nothing
# overflowing. `NotificationsFitTest.the premium pill is never squeezed` measures it; these two
# hold the spelling that makes it true.
check("162 the title yields to the pill, not the reverse (kotlin)",
      "weight(1f, fill = false)" in notify_kt)
check("162 the pill keeps its own width (swift)",
      "fixedSize(horizontal: true, vertical: false)" in notify_sw)

# THE SKIPPED USER STILL NEEDS A TOKEN, AND 163 MOVED WHERE THEY GET IT.
#
# The spec sheet's Android <= 12 row says so outright -- "These users still need push registration
# and all five categories" -- and when 09 was built, `register()` had exactly one call site: the
# grant callback on a screen those users never see. 162 answered that with `skipped()` here.
#
# SHOWUP-163 routes every user through Stay reachable (10) instead, whose `Save preferences` is
# the only way off it and which registers when push is on. So the need is met one screen later,
# and keeping `skipped()` as well would register twice -- worse, it registered on a GRANTED status
# REGARDLESS OF CONSENT, so a user who reached 10 and switched push off would already have had a
# token filed. These checks now assert the absence on both platforms, because a helpful
# re-addition of `skipped()` is exactly the kind of thing that looks like a bug fix.
_model_kt = code_only(read(KT, "profile", "NotificationsViewModel.kt"))
_model_sw = code_only(read(SW, "NotificationsModel.swift"))
check("162/163 09 no longer registers on the skip path (kotlin)", "fun skipped(" not in _model_kt)
check("162/163 09 no longer registers on the skip path (swift)", "func skipped(" not in _model_sw)
check("162/163 nothing on 09 reaches push registration (kotlin)",
      "push.register()" not in _model_kt)
check("162/163 nothing on 09 reaches push registration (swift)",
      "push.register()" not in _model_sw)
check("162/163 the host no longer calls it (kotlin)",
      "notifyModel.skipped(" not in code_only(read(KT, "MainActivity.kt")))
check("162/163 the host no longer calls it (swift)",
      "notifications.skipped(" not in code_only(read(SW, "ShowUpWelcomeApp.swift")))
# AND THE REGISTRATION REALLY IS ON 10, which is what makes the deletion safe rather than a loss.
check("162/163 Stay reachable registers on a grant (kotlin)",
      "push.register()" in read(KT, "profile", "ReachabilityViewModel.kt"))
check("162/163 Stay reachable registers on a grant (swift)",
      "push.register()" in read(SW, "ReachabilityModel.swift"))

# The line: Manrope 500 / 13.5 / 1.4, 3 below the title.
check("162 row line is Manrope 500 at 13.5 (kotlin)", "fontSize = 13.5.sp" in notify_kt)
check("162 row line is Manrope 500 at 13.5 (swift)", "F.manrope(13.5, .medium)" in notify_sw)
check("162 row line height 1.4 (kotlin)", "lineHeight = (13.5f * 1.4f).sp" in notify_kt)
check("162 row line height 1.4 (swift)", "lineSpacing(13.5 * 0.4)" in notify_sw)
check("162 row line sits 3 below the title (kotlin)", "padding(top = 3.dp)" in notify_kt)
check("162 row line sits 3 below the title (swift)", "padding(.top, 3)" in notify_sw)

# The Premium pill: 3 x 10, capsule, sunset, white Manrope 800 / 10 uppercase at 0.06em, nudged up
# 1 by a TRANSFORM. A padding moves it half as far and makes the row taller; the reference's
# `translateY` takes part in no layout at all.
check("162 tag padding 3 x 10 (kotlin)", "padding(horizontal = 10.dp, vertical = 3.dp)" in notify_kt)
check("162 tag padding 3 x 10 (swift)",
      "padding(.horizontal, 10)" in notify_sw and "padding(.vertical, 3)" in notify_sw)
check("162 tag is a capsule (kotlin)", "RoundedCornerShape(percent = 50)" in notify_kt)
check("162 tag is a capsule (swift)", "in: Capsule()" in notify_sw)
check("162 tag is sunset (kotlin)", "SunsetStops" in notify_kt)
check("162 tag is sunset (swift)", "Gradients.sunset()" in notify_sw)
check("162 tag is white Manrope 800 at 10 (kotlin)",
      "FontWeight.ExtraBold" in notify_kt and "fontSize = 10.sp" in notify_kt)
check("162 tag is white Manrope 800 at 10 (swift)", "F.manrope(10, .heavy)" in notify_sw)
check("162 tag is uppercase (kotlin)", ".uppercase()" in notify_kt)
check("162 tag is uppercase (swift)", ".uppercased()" in notify_sw)
check("162 tag tracking 0.06em (kotlin)", "letterSpacing = 0.06.em" in notify_kt)
check("162 tag tracking 0.06em (swift)", "tracking(0.06 * 10)" in notify_sw)
check("162 tag is nudged by a TRANSFORM, not a padding (kotlin)",
      "offset(y = (-1).dp)" in notify_kt)
check("162 tag is nudged by a TRANSFORM, not a padding (swift)", "offset(y: -1)" in notify_sw)

# The headline and the lead.
check("162 headline is 32 (kotlin)", "fontSize = 32.sp" in notify_kt)
check("162 headline is 32 (swift)", "fontSize: 32" in notify_sw)
check("162 headline line height 1.1 (kotlin)", "(32f * 1.1f).sp" in notify_kt)
check("162 headline line height 1.1 (swift)", "lineHeightMultiple: 1.1" in notify_sw)
check("162 headline tracking -0.015em (kotlin)", "letterSpacing = (-0.015).em" in notify_kt)
check("162 headline tracking -0.015em (swift)", "trackingEm: -0.015" in notify_sw)
check("162 headline is balanced (kotlin)", "balance = true" in notify_kt)
check("162 headline is balanced (swift)", "balance: true" in notify_sw)
check("162 lead is Manrope 500 at 15 (kotlin)", "fontSize = 15.sp" in notify_kt)
check("162 lead is Manrope 500 at 15 (swift)", "F.manrope(15, .medium)" in notify_sw)
check("162 lead line height 1.55 (kotlin)", "(15f * 1.55f).sp" in notify_kt)
check("162 lead line height 1.55 (swift)", "lineSpacing(15 * 0.55)" in notify_sw)

# ── SHOWUP-163 · Stay reachable ─────────────────────────────────────────────
#
# THE FIRST ACCEPTANCE CRITERION IS A LIST OF ABSENCES: "Built from `scope="mvp"`. Nothing from
# `scope="full"` ships: no concierge-call card, calendar card, phone field, email row, or
# WhatsApp/SMS toggles." The reference file carries both scopes behind one prop, so every one of
# those is a component sitting in the file somebody built this from -- which is exactly the kind
# of thing a later "finish the screen" pass adds back without malice.
reach_kt = code_only(read(KT, "profile", "ProfileReachabilityScreen.kt"))
reach_sw = code_only(read(SW, "ProfileReachability.swift"))
reach_model_kt = read(KT, "profile", "ReachabilityModel.kt")
reach_vm_kt = read(KT, "profile", "ReachabilityViewModel.kt")
reach_model_sw = read(SW, "ReachabilityModel.swift")
switch_kt = code_only(read(KT, "designsystem", "ConsentSwitch.kt"))
switch_sw = code_only(read(SW, "ConsentSwitch.swift"))

for label, forbidden in [
    ("concierge card", "oncierge"),
    ("calendar card", "alendar"),
    ("phone number field", "honeNumberField"),
    ("email channel row", "mailRow"),
    ("skip link", "kip for now"),
]:
    check("163 no %s from scope=full (kotlin)" % label, forbidden not in reach_kt)
    check("163 no %s from scope=full (swift)" % label, forbidden not in reach_sw)

# NO HEADER, NO PROGRESS BAR, NO SKIP, NO CLOSE; BACK IS BLOCKED. Same four absences as 09, and
# the ticket repeats them because this screen is longer and looks more like a settings page.
check("163 no AppHeader (kotlin)", "AppHeader" not in reach_kt)
check("163 no AppHeader (swift)", "AppHeader" not in reach_sw)
check("163 no StepProgress (kotlin)", "StepProgress" not in reach_kt)
check("163 no StepProgress (swift)", "StepProgress" not in reach_sw)
check("163 no SkipLink (kotlin)", "SkipLink" not in reach_kt)
check("163 no SkipLink (swift)", "SkipLink" not in reach_sw)
check("163 back is blocked (kotlin)", "BackHandler(enabled = true)" in reach_kt)
check("163 back is blocked (swift)", "navigationBarBackButtonHidden(true)" in reach_sw)

# NOT A STEP AND NO SKIP. §2 gives `reachability` a dash index, and the ticket lists all three
# step events under "Must NOT fire here".
check("163 fires no step event (kotlin)", "stepViewed" not in reach_vm_kt
      and "stepSkipped" not in reach_vm_kt)
check("163 fires no step event (swift)", "stepViewed" not in reach_model_sw
      and "stepSkipped" not in reach_model_sw)

# ── the copy, verbatim from the ticket ──────────────────────────────────────
#
# Quoted here rather than referenced, because "copy matches the ticket exactly" is a criterion and
# a verifier that reads the copy out of the source cannot fail.
REACH_COPY = [
    ("headline em", "Never miss"),
    ("headline tail", " a date and avoid getting a penalty!"),
    # CHANGED 28 Sep 2026. The separate push row was folded into the card head, so its title
    # became the card's and there is no second title to check -- see the structure checks below,
    # which are what stop the row coming back.
    ("card title", "Push notifications"),
    ("pill", "Recommended"),
    ("interest heading", "More ways to reach you are coming soon."),
    ("interest line", u"Tell us which ones you\u2019d like."),
    ("ai call row", "Phone call from our AI assistant"),
    ("ai call sub", "A short automated call when something changes."),
    ("whatsapp row", "WhatsApp"),
    ("sms row", "SMS"),
    ("privacy link", "Read our Privacy Policy"),
    ("cta", "Save preferences"),
    ("confirm title", "Are you sure?"),
    ("confirm lead", "Please remember:"),
    ("confirm keep", "Keep active"),
    ("confirm settings", "Open Settings"),
    ("confirm deactivate", "Confirm deactivation"),
]
for label, text in REACH_COPY:
    check("163 copy %s (kotlin)" % label, text in reach_kt)
    check("163 copy %s (swift)" % label, text in reach_sw)

# ── the head holds the switch, and there is no second row (28 Sep 2026) ─────
#
# THE COPY CHECK ABOVE CANNOT SEE THIS. "Push notifications" appears whether the string labels a
# card head or a row of its own, so the one criterion that actually changed -- "No separate push
# row" -- is invisible to a substring search. These read the structure instead.
#
# Checked as an ABSENCE of the old row's own markers rather than a presence of the new head,
# because a rebuild would reintroduce the row alongside the head rather than instead of it, and
# that is the failure worth catching.
# THE FIRST VERSION OF THIS CHECK COULD NOT FAIL, and it is worth saying why rather than just
# replacing it: it searched for the phrase "the push row", which only ever appeared in a COMMENT,
# against `code_only()` output, which strips comments. It passed on a file that still had the row
# and would have passed on one that grew it back.
#
# The code-level marker is the label. `PUSH_ROW` / `pushRow` named the removed row's visible text
# and now names nothing but the switch's accessibility name, so ONE use is the structure: a second
# is a second place the words are drawn, which is the row returning.
# SCOPED TO THE CARD. The words appear a third time in the deactivation dialog, as the channel
# chip the ticket asks for -- so a whole-file count is not the question. Inside the card, between
# its declaration and the first interest row, ONE use is the structure: the switch's name. A
# second is the words drawn again, which is the row coming back.
for _name, _src, _decl, _use in [
    ("kotlin", reach_kt, "private fun ReachCard", "ReachCopy.PUSH_ROW"),
    ("swift", reach_sw, "private struct ReachCard", "ReachCopy.pushRow"),
]:
    _card = _src.split(_decl)[-1].split("InterestRow(")[0]
    check("163 no separate push row (%s)" % _name, _card.count(_use) == 1)

# The small pip belonged to the row. One head means one bell, and it is the head's 20.
#
# KEYED ON THE BELL AT 17, not on the 34pt box: the three interest rows draw 34pt pips of their
# own and always did, so a size alone matches them too. A 17pt bell existed in exactly one place.
check("163 one bell pip, and it is the head's (kotlin)",
      "Icon(BrandIcon.Bell, 17.dp" not in reach_kt)
check("163 one bell pip, and it is the head's (swift)",
      "icon: .bell, size: 17" not in reach_sw)

# The switch sits in the card head now, so it is above the divider rather than below the title
# block. `ConsentSwitch` appearing before "the demand test" is that, and it is the whole point of
# the change: the thing being switched and the switch are one row.
# SPLIT ON CODE, NOT ON A COMMENT. This too was vacuous: it split on "the demand test", a
# comment heading, in comment-stripped source -- so the split returned the whole file and the
# check reduced to "the switch exists somewhere". It would have passed with the switch back in a
# row below the divider, which is the one thing it is supposed to forbid.
#
# The card's own body is the region that matters, and `InterestRow(` is the first thing after the
# head, so the switch has to appear before it.
for name, src, decl in [("kotlin", reach_kt, "private fun ReachCard"),
                        ("swift", reach_sw, "private struct ReachCard")]:
    body = src.split(decl)[-1]
    check("163 the switch is in the card head (%s)" % name,
          "ConsentSwitch(" in body.split("InterestRow(")[0])

# The pill moved ABOVE the title, which retired the wrap workaround on both platforms. Its cap on
# type went with the constraint that justified it -- if either comes back, the layout reason for
# it has to come back too, and this is where that argument gets had.
check("163 the pill no longer caps its own type (kotlin)",
      "min(fontScale" not in reach_kt)
check("163 the pill no longer caps its own type (swift)",
      "dynamicTypeSize(...DynamicTypeSize.large)" not in reach_sw)

# ── the spacing the 28 Sep update names, on both platforms ──────────────────
#
# EVERY ONE OF THESE IS A NUMBER IN THE TICKET. They are checked because the whole change is
# "make it fit on a 393 x 852 at default type", and a single value quietly reverted takes the
# fine print back below the fold without breaking anything a test would otherwise notice.
REACH_SPACING = [
    ("headline top padding is 20", "topPadding = 20.dp", "topPadding: 20"),
    # THE WHOLE CALL, because `top = 10.dp` on its own also matches the dialog's own padding and
    # the lead could revert to 14 with the check still green.
    ("lead top margin is 10",
     "padding(start = 4.dp, end = 4.dp, top = 10.dp)", ".padding(.top, 10)"),
    # THE WHOLE BODY PADDING, both ends: `padding: '14px 24px 4px'` in the reference. The 4 is
    # the body's own bottom and is separate from the pinned-footer reservation under it.
    ("body padding is 14 over 4",
     "Modifier.padding(top = 14.dp, bottom = 4.dp)", ".padding(.top, 14)"),
    ("interest rows are padded 9", "vertical = 9.dp", ".padding(.vertical, 9)"),
    # THE FLOOR IS THE SHARED TOKEN, and the ticket's "(>= 52pt)" is asserted by measurement
    # in `ReachabilityFitTest` instead -- it is what the row comes out at, not what it is set to.
    ("interest rows keep the shared tap floor",
     "heightIn(min = ComponentSizes.minTapTarget)", "minHeight: ComponentSizes.minTapTarget"),
    ("the body has the reference's 4 at the bottom",
     "bottom = 4.dp", ".padding(.bottom, 4)"),
    ("footer top padding is 8", "top = 8.dp", ".padding(.top, 8)"),
]
for label, kt, sw in REACH_SPACING:
    check("163 %s (kotlin)" % label, kt in reach_kt)
    check("163 %s (swift)" % label, sw in reach_sw)

# The divider is even now -- 12 above the rule and 12 below, where it was 10 and 18.
check("163 the interest divider is even (kotlin)",
      "padding(top = 12.dp).topHairline().padding(top = 12.dp)" in reach_kt)
check("163 the interest divider is even (swift)",
      "offset(y: -12)" in reach_sw)

# `Show-up Rate`, NEVER "Show-Up Rate". The ticket says so twice and it is a product term. It
# appears in the lead AND in the dialog body, so a single-place fix would leave the other wrong.
for name, src in [("kotlin", reach_kt), ("swift", reach_sw)]:
    check("163 Show-up Rate is spelled the product way (%s)" % name,
          src.count("Show-up Rate") == 2 and "Show-Up Rate" not in src)

# The em dash in the fine print is a SPACED EM DASH, not a hyphen.
check("163 fine print uses a spaced em dash (kotlin)",
      u"your dates \u2014 never for marketing" in reach_kt)
check("163 fine print uses a spaced em dash (swift)",
      u"your dates \u2014 never for marketing" in reach_sw)

# "Settings" in the fine print is PLAIN TEXT, not a link -- the ticket says so outright, and a
# second link here would be a second thing to tap that goes nowhere.
check("163 Privacy Policy is the only link (kotlin)",
      reach_kt.count("onPrivacy") >= 1 and "onSettingsLink" not in reach_kt)
check("163 Privacy Policy is the only link (swift)",
      reach_sw.count("onPrivacy") >= 1 and "onSettingsLink" not in reach_sw)

# ── the defaults ────────────────────────────────────────────────────────────
#
# "Push is on by default, and the three checkboxes are unchecked by default." An opt-out, not an
# opt-in, and it is one word in each file -- exactly the kind of default a later refactor flips.
check("163 push defaults on (kotlin)", "val pushOn: Boolean = true" in reach_model_kt)
check("163 push defaults on (swift)", "var pushOn = true" in reach_model_sw)
check("163 interest defaults empty (kotlin)",
      "val interest: Set<InterestChannel> = emptySet()" in reach_model_kt)
check("163 interest defaults empty (swift)",
      "var interest: Set<InterestChannel> = []" in reach_model_sw)

# ── §25, and the wall between interest and consent ──────────────────────────
#
# "Deliberately a separate vocabulary from §8 channel so an interest can never be read as a
# consent." The enums are checked for their exact values, and the models for the absence of the
# one call that would collapse the wall.
for name, src in [("kotlin", reach_model_kt), ("swift", reach_model_sw)]:
    check("163 §25 ai_call (%s)" % name, '"ai_call"' in src)
    check("163 §25 whatsapp (%s)" % name, '"whatsapp"' in src)
    # `sms` is spelled differently in each language and that is not drift: Swift infers a
    # String raw value from the case name, so `case sms` IS "sms" and writing it out is the
    # redundancy the compiler warns about. What matters is the WIRE value, which
    # `ReachabilityRulesTests` asserts by reading the payload back.
    check("163 §25 sms (%s)" % name, '"sms"' in src or "case sms" in src)

check("163 no consent_changed for an interest box (kotlin)",
      "consentChanged" not in reach_vm_kt.split("fun interestToggled")[1].split("fun ")[0])
check("163 no consent_changed for an interest box (swift)",
      "consentChanged" not in reach_model_sw.split("func interestToggled")[1].split("\n    func ")[0])

# ── the registry stamp ──────────────────────────────────────────────────────
#
# TAXONOMY 1.4.5 / REGISTRY 1.4.6, and the ticket quotes both together. The stamp is the SECOND
# one: enums.json's `registry_version`, which "versions the vocabulary, not the taxonomy
# document". Reading the wrong file is the obvious mistake and both platforms would make it the
# same way, so a parity check alone would not catch it.
#
# READ FROM THE REGISTRY, NOT TYPED HERE. Until 5 October 2026 this compared both constants to the
# literal "1.4.6" written in this file, so the check could not tell when the registry moved -- and
# it did, to 1.4.15 across nine tickets, with every check still green. A check that pins a value it
# should be reading is a second copy of that value, and it drifts silently with the first.
check("stamp is the enums.json registry version, %s (kotlin)" % _REGISTRY_VERSION,
      'FIELD_REGISTRY_VERSION = "%s"' % _REGISTRY_VERSION
      in read(KT, "profile", "ProfileAnalytics.kt"))
check("stamp is the enums.json registry version, %s (swift)" % _REGISTRY_VERSION,
      'fieldRegistryVersion = "%s"' % _REGISTRY_VERSION in read(SW, "ProfileAnalytics.swift"))

# ── legal_link_tapped, corrected against events.json ────────────────────────
#
# The payload is typed `"terms"|"privacy"|"legal_notice"` and carries `screen_id`, not
# `screen_name` -- a code_delta the registry states outright. Both platforms shipped the longer
# spellings and the label, identically, which is how a taxonomy error survives a parity check.
signup_kt = read(KT, "analytics", "SignUpAnalytics.kt")
signup_sw = read(SW, "SignUpAnalytics.swift")
for name, src in [("kotlin", signup_kt), ("swift", signup_sw)]:
    check("163 legal link value is privacy (%s)" % name,
          '"privacy_policy"' not in src and '"privacy"' in src)
    check("163 legal link value is terms (%s)" % name,
          '"terms_and_conditions"' not in src and '"terms"' in src)
    check("163 legal_link_tapped carries screen_id (%s)" % name,
          '"screen_id"' in src and '"screen_name" to screen' not in src)

# ── the two dialogs ─────────────────────────────────────────────────────────
#
# "After Don't allow, the deactivation confirm appears with the primary labelled `Open Settings`
# ... A user-initiated toggle-off shows `Keep active`." One label per behaviour: the button opens
# Settings, so it must not promise to keep anything on.
check("163 state D primary is Open Settings (kotlin)",
      "AfterOsDenial" in reach_kt and "CONFIRM_SETTINGS" in reach_kt)
check("163 state D primary is Open Settings (swift)",
      "afterOsDenial" in reach_sw and "confirmSettings" in reach_sw)

# `Open Settings` is `permission_settings_opened`, NOT `consent_deactivation_abandoned`. The
# ticket bolds the distinction, and the two answer different questions.
# Asserted as a PAIR rather than by slicing one function body: what the ticket bolds is that
# these two events do not swap places, and each appearing exactly where the other does not is
# what says so.
# `openSettings()` WITH THE PARENTHESES, because `openSettingsOrReprompt()` on the host protocol
# is declared earlier in both files and a prefix match lands on that instead -- a slice of the
# wrong function that happens to contain neither event, so the check fails for a reason that has
# nothing to do with what it is asking.
for _n, _src, _kw in [("kotlin", reach_vm_kt, "fun "), ("swift", reach_model_sw, "func ")]:
    _opened = _src.split(_kw + "openSettings()")[1].split(_kw)[0]
    _kept = _src.split(_kw + "keepActive()")[1].split(_kw)[0]
    check("163 Open Settings is not an abandonment (%s)" % _n,
          "permissionSettingsOpened" in _opened
          and "consentDeactivationAbandoned" not in _opened)
    check("163 Keep active is not a Settings trip (%s)" % _n,
          "consentDeactivationAbandoned" in _kept
          and "permissionSettingsOpened" not in _kept)

# ── the switch ──────────────────────────────────────────────────────────────
#
# "The switch thumb ANIMATES (the kit's `justify-content` shortcut is not copied)." The kit moves
# its thumb by realigning the flex child and then declares a transform transition on it; those
# never meet, so the transition is dead code and the thumb jumps.
check("163 the thumb is placed by an animated offset (kotlin)",
      "animateDpAsState" in switch_kt and "thumbX" in switch_kt)
check("163 the thumb is placed by an animated offset (swift)",
      "timingCurve" in switch_sw and "offset(x:" in switch_sw)
check("163 the track crossfades too (kotlin)", "animateColorAsState" in switch_kt)
check("163 51x31 drawn (kotlin)",
      "TrackWidth = 51.dp" in switch_kt and "TrackHeight = 31.dp" in switch_kt)
check("163 51x31 drawn (swift)",
      "trackWidth: CGFloat = 51" in switch_sw and "trackHeight: CGFloat = 31" in switch_sw)

# EVERY ROW IS A HIT TARGET, and the checked state is announced. `clickable(role = Checkbox)`
# names the control type and says nothing about whether it is checked; only `toggleable` does.
check("163 the interest row is toggleable, not merely clickable (kotlin)",
      "role = Role.Checkbox," in reach_kt and "toggleable(" in reach_kt)
check("163 the interest row announces its state (swift)",
      "accessibilityValue(on ?" in reach_sw)
check("163 the switch is padded to the tap floor (kotlin)", "minTapTarget(48.dp)" in switch_kt)
check("163 the switch is padded to the tap floor (swift)",
      "ComponentSizes.minTapTarget" in switch_sw)

# ── the pinned footer ───────────────────────────────────────────────────────
#
# "The body scrolls and the CTA stays pinned. The fine print scrolls fully clear of the CTA."
# The clearance is RESERVED rather than hoped for -- the same lesson 09 learned when a Galaxy Fold
# opened on a permission screen with no button drawn on it.
check("163 the CTA is in the pinned footer (kotlin)",
      "footer = {" in reach_kt and "REACH_CTA_TAG" in reach_kt)
check("163 the CTA is in the pinned footer (swift)",
      "footer: {" in reach_sw and "reachCtaId" in reach_sw)
check("163 the footer clearance is reserved (kotlin)", "FooterClearance" in reach_kt)
check("163 the footer clearance is reserved (swift)", "footerClearance" in reach_sw)

# ── the failure state ───────────────────────────────────────────────────────
#
# "Not optimistic: the flow position advances only after the server confirms." A failure the user
# cannot see is a CTA that appears to do nothing, so the card is in the PINNED footer rather than
# at the end of a body that may be scrolled anywhere.
check("163 the save failure is drawn (kotlin)", "InlineErrorCard" in reach_kt)
check("163 the save failure is drawn (swift)", "InlineErrorCard" in reach_sw)
check("163 the save failure uses the shared card (kotlin)",
      "com.showup.designsystem.InlineErrorCard" in read(KT, "profile",
                                                        "ProfileReachabilityScreen.kt"))

# ── 09's three changes, which this ticket owns ──────────────────────────────
#
# "Profile 09: no OS dialog, CTA reads `Continue`, and none of the three permission events fire
# there -- verified on both platforms."
notify_kt2 = code_only(read(KT, "profile", "ProfileNotificationsScreen.kt"))
notify_sw2 = code_only(read(SW, "ProfileNotifications.swift"))
notify_vm_kt = read(KT, "profile", "NotificationsViewModel.kt")
notify_vm_sw = read(SW, "NotificationsModel.swift")

check("163/09 CTA reads Continue (kotlin)", 'CTA = "Continue"' in notify_kt2)
check("163/09 CTA reads Continue (swift)", 'cta = "Continue"' in notify_sw2)
check("163/09 the old label is gone (kotlin)", '"Enable notifications"' not in notify_kt2)
check("163/09 the old label is gone (swift)", '"Enable notifications"' not in notify_sw2)

for name, src in [("kotlin", notify_vm_kt), ("swift", notify_vm_sw)]:
    body = code_only(src)
    check("163/09 fires no permission_prompted (%s)" % name, "permissionPrompted(" not in body)
    check("163/09 fires no permission_os_sheet_shown (%s)" % name,
          "permissionOsSheetShown(" not in body)
    check("163/09 fires no permission_result (%s)" % name, "permissionResult(" not in body)

# AND 09 RAISES NO DIALOG. The event absence above would still pass if the request were made
# silently, which is the worse version of the bug: a sheet with no record of it.
#
# LOOKED FOR WHERE THE DIALOG IS ACTUALLY RAISED, which on Android is not the view model. The
# first version of this check searched `NotificationsViewModel.kt` for `askNotifications.launch`
# -- a launcher that only ever exists in `MainActivity`, so the string could not appear there and
# the check could not fail. Proved by putting the request back on 09's CTA: all 956 checks passed.
#
# The branch is the unit that matters. `code_only` has already removed the comments between the
# two arms, so the slice is exactly 09's wiring.
_main_kt = code_only(read(KT, "MainActivity.kt"))
_09_branch_kt = (_main_kt.split("FlowScreen.ProfileNotifications ->")[-1]
                 .split("FlowScreen.ProfileReachability ->")[0])
check("163/09 raises no OS dialog (kotlin)", "askNotifications.launch" not in _09_branch_kt)

# iOS keeps the ask ON the model -- held and deliberately unused -- so the model is the right
# place to look there, and this one was never vacuous. The screen's own wiring is checked too,
# because a request added at the call site would bypass the model entirely.
_app_sw = code_only(read(SW, "ShowUpWelcomeApp.swift"))


def _balanced_call(src, opener):
    """The whole of a call beginning with `opener`, to its MATCHING close paren.

    `split(")")[0]` stops at the first close paren, which in a SwiftUI call is almost never the
    call's own: `onEnable: { if notifications.continuePressed() { ... } }` ends the slice inside
    the first closure. That left a few characters to search and a check that would have missed a
    request added anywhere after it -- raised in review, and true.
    """
    i = src.find(opener)
    if i < 0:
        return ""
    j, depth = i + len(opener), 1
    while j < len(src) and depth:
        if src[j] == "(":
            depth += 1
        elif src[j] == ")":
            depth -= 1
        j += 1
    return src[i:j]


_09_screen_sw = _balanced_call(_app_sw, "ProfileNotificationsView(")
check("163/09 raises no OS dialog (swift)",
      "ask.request()" not in code_only(notify_vm_sw) and "request(" not in _09_screen_sw)

# UNCHANGED ON 09: the ALREADY-DETERMINED SKIP itself, which the ticket lists under what this
# ticket must not touch. What went with 163 is the push registration that used to hang off it --
# see the 162/163 block above for why that is a deletion rather than a loss.
check("163/09 the already-determined skip survives (kotlin)",
      "shouldShowAsk(status)" in code_only(read(KT, "MainActivity.kt")))
check("163/09 the already-determined skip survives (swift)",
      "shouldShowAsk(status)" in code_only(read(SW, "ShowUpWelcomeApp.swift")))

# ── the sheet backdrop, callout 15 ──────────────────────────────────────────
#
# "scrim rgba(29,17,41,.42) + backdrop blur(1.5px) -- the screen stays visible behind it". We
# shipped the scrim and not the blur, on both platforms, for as long as the sheets have existed.
#
# THE BLUR CANNOT BE ASSERTED BY LOOKING. `backdrop-filter` has no equivalent in either toolkit:
# a view draws its own subtree and has no handle on the pixels beneath it, so the blur has to be
# applied by the SCREEN and the scrim drawn over it. That makes it two edits in two files that
# must agree, which is exactly the shape a checker is for -- and it is the only check available,
# because Robolectric does not rasterise `RenderEffect` at any radius, so no screenshot in this
# repo can show the blur is there. A device is the only proof.
sheet_kt = read(KT, "designsystem", "SheetScaffold.kt")
sheet_sw = read(SW, "SheetScaffold.swift")
check("sheet backdrop blur is 1.5 (kotlin)", "SheetBackdropBlur = 1.5.dp" in sheet_kt)
check("sheet backdrop blur is 1.5 (swift)", "sheetBackdropBlur: CGFloat = 1.5" in sheet_sw)
check("sheet scrim is 0.42 (kotlin)", "alpha = 0.42f" in sheet_kt)
check("sheet scrim is 0.42 (swift)", "opacity(0.42)" in sheet_sw)

# BOTH SCREENS THAT RAISE A SHEET APPLY IT. A blur on one of the two is the drift this checker
# exists to catch -- the sheets look identical and only one would be right.
for name, kt, sw in [
    ("media (08)", "ProfileMediaScreen.kt", "ProfileMedia.swift"),
    ("prompts (07)", "ProfilePromptsScreen.kt", "ProfilePrompts.swift"),
]:
    check("%s blurs behind its sheet (kotlin)" % name,
          "blurBehindSheet(state.sheet != null)" in read(KT, "profile", kt))
    check("%s blurs behind its sheet (swift)" % name,
          "blurBehindSheet(state.sheet != nil)" in read(SW, sw))

# ── no machine name may reach a sentence ────────────────────────────────────
#
# A real user on a real Pixel was shown "Android.permission-group.UNDEFINED access is off. Turn
# on Android.permission-group.UNDEFINED in Settings to film."
#
# `PermissionInfo.group` is DEPRECATED SINCE API 29 and answers `android.permission-group.
# UNDEFINED`; resolving that gives a group with no label resource, and `PackageItemInfo.loadLabel`
# falls back to the item's own `name` INSTEAD OF THROWING -- so the `runCatching` around it never
# fired. This checks the two halves of the fix, because the failure mode is a valid String rather
# than an exception and nothing else in this repo would notice it.
media_access_kt = code_only(read(KT, "profile", "MediaAccess.kt"))
check("the deprecated permission-group lookup is gone",
      ".group" not in media_access_kt or "getPermissionInfo" not in media_access_kt)
check("the platform label is validated before it reaches copy",
      "usablePermissionLabel(platform, ours)" in media_access_kt)
check("the validator refuses a dotted identifier", "label.contains('.')" in media_access_kt)

# AND IOS HARD-CODES ITS TWO, which is correct rather than a shortcut: Apple names these rows in
# Settings and exposes no API for them, so there is nothing to ask. The check is that the words
# still live behind the same function on both platforms, so a localised build has one place to
# correct on each.
media_access_sw = code_only(read(SW, "MediaAccess.swift"))
check("iOS resolves its labels through platformLabel too",
      "func platformLabel" in media_access_sw)

# ── the fine print, callout 12 ──────────────────────────────────────────────
#
# "Manrope 500 - 11.5 / 1.5 - --liq-fg-subtle. `Settings` 600 - --liq-fg, plain text not a link.
# `Read our Privacy Policy` - primary-500 - 600 - underline, the only link on the screen."
#
# THREE THINGS WERE WRONG AND ONE OF THEM WAS INVISIBLE TO EVERY CHECK HERE. `Settings` was bold
# and still grey, because the `emphasised` helper carries weight and not colour; the link sat on
# its own line behind a 44dp target instead of inside the sentence; and iOS set the whole thing at
# 12 rather than 11.5. All three came back from a person looking at the screen.
check("163 the fine print is 11.5 (kotlin)", "fontSize = 11.5.sp" in reach_kt)
check("163 the fine print is 11.5 (swift)", "F.manrope(11.5, .medium)" in reach_sw)

# `Settings` IS DARK, which is the half that was wrong. Asserted on the colour, not the weight.
check("163 Settings is the foreground colour, not subtle (kotlin)",
      "SpanStyle(color = Fg, fontWeight = FontWeight.SemiBold)" in reach_kt)
check("163 Settings is the foreground colour, not subtle (swift)",
      "settings.foregroundColor = .liqFg" in reach_sw)

# AND IT IS NOT A LINK. The ticket says so outright -- a second link here would be a second thing
# to tap that goes nowhere.
check("163 Settings is not a link (kotlin)",
      "LinkAnnotation.Clickable" in reach_kt and "Settings\") {" not in reach_kt)

# THE PRIVACY LINK IS INSIDE THE PARAGRAPH, not a block under it. `withLink` and
# `AttributedString.link` are the only mechanisms that make a RUN tappable, which is what makes
# this checkable at all: a separate Button would be a different construct entirely.
check("163 the privacy link is inline (kotlin)", "withLink(LinkAnnotation.Clickable" in reach_kt)
check("163 the privacy link is inline (swift)", "link.link = URL(string:" in reach_sw)
check("163 the paragraph ends with a full stop after the link (kotlin)",
      'append(".")' in reach_kt)
check("163 the paragraph ends with a full stop after the link (swift)",
      'out.append(AttributedString("."))' in reach_sw)

# ── SHOWUP-165 to SHOWUP-173 · location, Embrace 2, "Share some details" ─────
#
# Quoted from the tickets (saved verbatim with the handoff), both platforms. Copy is compared AFTER
# joining adjacent string literals, so a sentence wrapped across two source lines still reads as
# the one sentence the ticket gives -- and a sentence that differs by one character still fails.

def joined(text):
    """Source with `"a" + "b"` (any whitespace or newline around the plus) read as `"ab"`."""
    return re.sub(r'"\s*\+\s*"', "", text)


def in_order(text, quoted):
    """Every string appears, quoted, and in the given order."""
    at = -1
    for s in quoted:
        i = text.find('"%s"' % s, at + 1)
        if i < 0:
            return False
        at = i
    return True


ANDROID_MANIFEST = read(MOBILE, "android-preview-project", "app", "src", "main", "AndroidManifest.xml")
INFO_PLIST = read(SW, "Info.plist")
loc_kt = joined(code_only(read(KT, "profile", "ProfileLocationScreen.kt")))
loc_sw = joined(code_only(read(SW, "ProfileLocation.swift")))
locacc_kt = code_only(read(KT, "profile", "LocationAccess.kt"))
locacc_sw = code_only(read(SW, "LocationAccess.swift"))
locvm_kt = code_only(read(KT, "profile", "LocationViewModel.kt"))
locvm_sw = code_only(read(SW, "LocationModel.swift"))
emb2_kt = joined(code_only(read(KT, "profile", "ProfileEmbraceDetailsScreen.kt")))
emb2_sw = joined(code_only(read(SW, "ProfileEmbraceDetails.swift")))
confetti_kt = code_only(read(KT, "profile", "ConfettiRain.kt"))
confetti_sw = code_only(read(SW, "ConfettiRain.swift"))
det_kt = code_only(read(KT, "profile", "ProfileDetails.kt"))
det_sw = code_only(read(SW, "ProfileDetails.swift"))
chrome_kt = joined(code_only(read(KT, "profile", "DetailsChrome.kt")))
chrome_sw = joined(code_only(read(SW, "DetailsChrome.swift")))
choice_kt = code_only(read(KT, "profile", "ProfileChoiceScreen.kt"))
choice_sw = code_only(read(SW, "ProfileChoice.swift"))
lang_kt = code_only(read(KT, "profile", "ProfileDatingLanguageScreen.kt"))
lang_sw = code_only(read(SW, "ProfileDatingLanguage.swift"))
height_kt = code_only(read(KT, "profile", "ProfileHeightScreen.kt"))
height_sw = code_only(read(SW, "ProfileHeight.swift"))
dvm_kt = code_only(read(KT, "profile", "ProfileDetailsViewModel.kt"))
dvm_sw = code_only(read(SW, "DetailsModel.swift"))
pa_kt = read(KT, "profile", "ProfileAnalytics.kt")
pa_sw = read(SW, "ProfileAnalytics.swift")
vis_kt = read(KT, "designsystem", "ProfileVisibility.kt")
vis_sw = read(SW, "ProfileVisibility.swift")
app_kt = code_only(read(KT, "MainActivity.kt"))
app_sw = code_only(read(SW, "ShowUpWelcomeApp.swift"))

# 165 · Location, three states of ONE screen.
for label, text in (("kotlin", loc_kt), ("swift", loc_sw)):
    for s in ["Find people ", "nearby",
              "We can't find dates ", "without", " location.",
              "Your phone has location ", "switched off",
              "Set a search radius around your location",
              "We pick a fair halfway venue for you both",
              "No planning, no home advantage — a busy public spot that's neutral ground for both of you.",
              "Walking directions on the day",
              "Your exact location is never shown on your profile. Other people only see the city you're "
              "in and an approximate distance from themselves e.g. 1.5km.",
              "Allow location access", "Open Settings", "Not now — ask me when I search",
              "Location access is off for Show Up. Without it we can't show you anyone nearby — every "
              "date happens in the real world. ",
              "Turn it on in ",
              ", or finish your profile first and we'll ask again when you start searching."]:
        check("165 copy (%s): %s" % (label, s[:44]), '"%s"' % s in text or s in text)
    check("165 no progress bar or header (%s)" % label, "StepProgress" not in text and "AppHeader" not in text)
# ONE PLATFORM SENTENCE EACH, and nothing else differs between the two.
check("165 B path, Android", '"Permissions › Location"' in loc_kt)
check("165 B path, iOS", '"Settings › Show Up › Location"' in loc_sw)
check("165 C body, Android -- the path sentence is dropped",
      '"Location is off for every app on this phone, not just Show Up. Turn it on in Settings now, '
      'or finish your profile first and we\'ll ask again when you start searching."' in loc_kt)
check("165 C body, iOS", '"Location Services is off for every app on this phone, not just Show Up. "' in loc_sw)
check("165 C path, iOS", '"Settings › Privacy & Security › Location Services"' in loc_sw)
check("165 iOS purpose string, exactly",
      "<key>NSLocationWhenInUseUsageDescription</key>\n\t<string>We use your location to find people nearby "
      "and choose a halfway meeting spot for your date.</string>" in INFO_PLIST)
check("165 never Always (Info.plist)", "NSLocationAlways" not in INFO_PLIST)
check("165 never Always (swift)", "requestAlwaysAuthorization" not in locacc_sw
      and "requestWhenInUseAuthorization()" in locacc_sw)
check("165 fine AND coarse requested together (manifest)",
      "android.permission.ACCESS_FINE_LOCATION" in ANDROID_MANIFEST
      and "android.permission.ACCESS_COARSE_LOCATION" in ANDROID_MANIFEST)
# Declared permissions only: the manifest's own comment names the one that must not be there.
check("165 never background (manifest)",
      not re.search(r'<uses-permission[^>]*ACCESS_BACKGROUND_LOCATION', ANDROID_MANIFEST))
# THE SWITCH IS CHECKED FIRST: C whatever the permission says.
check("165 services off is checked first (kotlin)", "!status.servicesOn -> LocationArrival.Show(LocationState.ServicesOff)" in locacc_kt)
check("165 services off is checked first (swift)", "guard status.servicesOn else { return .show(.servicesOff) }" in locacc_sw)
check("165 restricted is denied (swift)", "case .denied, .restricted: return .show(.denied)" in locacc_sw)
check("165 iOS reads the switch off the main thread", "Task.detached" in locacc_sw)
check("165 Android back does nothing on location", "BackHandler" in loc_kt)
check("165 the recovery event is registry-backed (kotlin)", '"permission_denied_recovery_shown"' in pa_kt)
check("165 the recovery event is registry-backed (swift)", '"permission_denied_recovery_shown"' in pa_sw)
check("165 the reconciler ignores our own dialog (kotlin)", "requesting" in locvm_kt)
check("165 the reconciler ignores our own dialog (swift)", "guard announced, !ui.requesting else { return }" in locvm_sw)
check("165 reachability exits into location (kotlin)", "enterLocation()" in app_kt)
check("165 reachability exits into location (swift)", "await enterLocation()" in app_sw)

# 166 · Embrace 2.
for label, text in (("kotlin", emb2_kt), ("swift", emb2_sw)):
    check("166 named headline (%s)" % label, "You are doing great, " in text)
    check("166 anonymous headline (%s)" % label, '"You are doing great."' in text)
    check("166 lead (%s)" % label,
          "You'll see on others' profiles exactly what you choose to share on yours. Let's add a few more details!"
          in text)
    check("166 CTA, no arrow character (%s)" % label, '"Add profile details"' in text and "→" not in text)
    check("166 is built on the bridge shell (%s)" % label, "EmbraceBridgeShell(" in text)
    check("166 no header, bar or NextButton (%s)" % label,
          all(x not in text for x in ("AppHeader", "StepProgress", "NextButton")))
check("166 centred backdrop (kotlin)", "BridgeBackdrop.Centred" in emb2_kt)
check("166 centred backdrop (swift)", "backdrop: .centred" in emb2_sw)
check("166 no em in the headline (kotlin)", "to true" not in emb2_kt)
check("166 no em in the headline (swift)", "true)]" not in emb2_sw)
check("166 the variant is registry-backed (kotlin)", 'ADD_DETAILS = "add_details"' in pa_kt)
check("166 the variant is registry-backed (swift)", 'addDetails = "add_details"' in pa_sw)
# The confetti: 32 fixed rows, gone by 3000, nothing under reduced motion, decorative.
check("166 confetti has 32 rows (swift)", confetti_sw.count("        piece(") == 32)
check("166 confetti has 32 rows (kotlin)", len(re.findall(r"^\s+ConfettiPiece\(", confetti_kt, re.M)) == 32
      or len(re.findall(r"^\s+piece\(", confetti_kt, re.M)) == 32)
check("166 confetti layer is 3000 ms (kotlin)", "CONFETTI_TOTAL_MS = 3_000" in confetti_kt
      or "CONFETTI_TOTAL_MS = 3000" in confetti_kt)
check("166 confetti layer is 3000 ms (swift)", "confettiTotalMs: Double = 3_000" in confetti_sw)
check("166 reduced motion shows nothing (kotlin)", "motion.enabled" in confetti_kt)
check("166 reduced motion shows nothing (swift)", "!reduceMotion" in confetti_sw)
check("166 confetti is decorative (kotlin)", "clearAndSetSemantics {}" in confetti_kt)
check("166 confetti is decorative (swift)", ".allowsHitTesting(false)" in confetti_sw
      and ".accessibilityHidden(true)" in confetti_sw)
check("166 a pop does not replay it (kotlin)", "rememberSaveable" in confetti_kt)
check("166 a restore does not replay it (swift)", "@Binding var played: Bool" in confetti_sw
      and 'SceneKey.scoped("embrace2.confettiPlayed"' in app_sw)

# 167 to 173 · the group.
for label, text in (("kotlin", chrome_kt), ("swift", chrome_sw)):
    for s in ["Share some details", "Continue", "Skip for now",
              "Enter height in cm", "e.g. 175", "Be honest — it helps us find the right matches.",
              "Used to find the right matches.", "Select all that apply.", "Select one.",
              "Enter a height between 120 and 230 cm to continue",
              "Pick a gender to continue", "Pick an orientation to continue"]:
        check("167-173 copy (%s): %s" % (label, s[:40]), '"%s"' % s in text)
check("167-173 the band label is the shared one (kotlin)",
      'PROFILE_VISIBILITY_LABEL = "Don\'t display on my profile"' in vis_kt)
check("167-173 the band label is the shared one (swift)",
      'profileVisibilityLabel = "Don\'t display on my profile"' in vis_sw)

# Headlines as RUNS, so the one italic em is checked as well as the words.
HEADLINES = [
    ("How ", "tall", " are you?"),
    ("Which gender describes ", "you", " best?"),
    ("What’s your sexual ", "orientation", "?"),
    ("What’s your preferred dating ", "language", "?"),
    ("What’s your highest level of ", "education", "?"),
    ("What are your ", "religious", " beliefs?"),
    ("What are your ", "political", " beliefs?"),
]
for lead, em, tail in HEADLINES:
    check("headline (kotlin): %s%s%s" % (lead, em, tail),
          '"%s" to false, "%s" to true, "%s" to false' % (lead, em, tail) in chrome_kt)
    sw_lead = lead.replace("’", "\\u{2019}")
    check("headline (swift): %s%s%s" % (lead, em, tail),
          '("%s", false), ("%s", true), ("%s", false)' % (sw_lead, em, tail) in chrome_sw)

# Options: the ticket's labels in the ticket's order, on both platforms.
OPTIONS = {
    "168 gender": ["Woman", "Man", "Non-binary", "Other"],
    "169 orientation": ["Straight", "Gay", "Lesbian", "Bisexual", "Pansexual", "Other"],
    "170 dating language": ["German", "English", "Spanish", "Italian", "French", "Turkish", "Russian", "Arabic"],
    "171 education": ["A-Levels / Abitur", "Apprenticeship", "University degree", "PhD"],
    "172 religion": ["Protestant", "Catholic", "Orthodox", "Muslim", "Jewish", "Buddhist", "Hindu", "Atheist",
                     "Spiritual / other"],
    "173 politics": ["Left", "Mid-left", "Middle", "Mid-right", "Right", "Conservative", "Libertarian",
                     "Apolitical"],
}
for name, labels in OPTIONS.items():
    check("%s options in order (kotlin)" % name, in_order(det_kt, labels))
    check("%s options in order (swift)" % name, in_order(det_sw, labels))

# The numbers the group is built from.
check("the bar is 10 segments (kotlin)", "const val SHARE_STEPS_TOTAL = 10" in det_kt)
check("the bar is 10 segments (swift)", "let shareStepsTotal = 10" in det_sw)
check("167 height is 120-230 (kotlin)", "HEIGHT_CM_MIN = 120" in det_kt and "HEIGHT_CM_MAX = 230" in det_kt)
check("167 height is 120-230 (swift)", "heightCmMin = 120" in det_sw and "heightCmMax = 230" in det_sw)
# Read per ENTRY: each enum entry names its own `mandatory = ...`, so the flag that follows an
# entry's name (before the next entry) is that entry's. A bare "mandatory" anywhere in the file is
# not evidence of which steps it is.
def kotlin_entry_flags(text, flag):
    names = ["Height", "Gender", "Orientation", "DatingLanguage", "Education", "Religion", "Politics"]
    found = {}
    for i, name in enumerate(names):
        start = text.find("    %s(" % name)
        end = text.find("    %s(" % names[i + 1]) if i + 1 < len(names) else len(text)
        m = re.search(r"%s = (true|false)" % flag, text[start:end]) if start >= 0 else None
        found[name] = m.group(1) == "true" if m else None
    return found


check("only gender and orientation are mandatory (kotlin)",
      kotlin_entry_flags(det_kt, "mandatory") == {
          "Height": False, "Gender": True, "Orientation": True, "DatingLanguage": False,
          "Education": False, "Religion": False, "Politics": False})
check("only the skippable three clear on reselect (kotlin)",
      kotlin_entry_flags(det_kt, "clearsOnReselect") == {
          "Height": False, "Gender": False, "Orientation": False, "DatingLanguage": False,
          "Education": True, "Religion": True, "Politics": True})
check("only gender and orientation are mandatory (swift)",
      "var mandatory: Bool { self == .gender || self == .orientation }" in det_sw)
check("only the skippable three clear on reselect (swift)",
      "var clearsOnReselect: Bool { self == .education || self == .religion || self == .politics }" in det_sw)
check("the answer region keeps an 80 floor (kotlin)", "ANSWER_REGION_FLOOR = 80.dp" in chrome_kt)
check("the answer region keeps an 80 floor (swift)", "answerRegionFloor: CGFloat = 80" in chrome_sw)
check("the group shell has the header and the bar (kotlin)",
      "AppHeader(title = DetailsCopy.SECTION" in chrome_kt and "StepProgress(steps = SHARE_STEPS_TOTAL" in chrome_kt)
check("the group shell has the header and the bar (swift)",
      "AppHeader(title: DetailsCopy.section" in chrome_sw and "StepProgress(steps: shareStepsTotal" in chrome_sw)
check("back works on every step (kotlin)", "BackHandler(enabled = true, onBack = onBack)" in chrome_kt)
check("back works on every step, with the swipe (swift)", "EdgeSwipeBack(onBack: onBack)" in chrome_sw)
# No SkipLink on the two mandatory steps -- not hidden, absent.
check("168/169 have no skip (kotlin)", "onSkip = if (step.mandatory) null else onSkip" in choice_kt)
check("168/169 have no skip (swift)", "step.mandatory" in choice_sw)
# The scroll indicator flashes where rows hide, and NOT on education ("do not flash").
check("flash on orientation, religion, politics (kotlin)",
      "step == DetailStep.Orientation" in choice_kt and "DetailStep.Religion" in choice_kt
      and "DetailStep.Politics" in choice_kt and "DetailStep.Education" not in choice_kt.split("flashIndicator")[1][:160])
check("flash on orientation, religion, politics (swift)",
      "flashIndicator: step == .orientation || step == .religion || step == .politics" in choice_sw)
check("170 flashes (kotlin)", "flashIndicator = true" in lang_kt)
check("170 flashes (swift)", "flashIndicator: true" in lang_sw)
check("167 the keypad is numeric (kotlin)", "KeyboardType.Number" in height_kt)
check("167 the keypad is numeric (swift)", ".numberPad" in height_sw or "numberPad" in height_sw)
# ONE request per accepted Continue: the answer, its visibility and the position together.
check("one PATCH carries the position (kotlin)", "flowPosition = step.position" in dvm_kt)
check("one PATCH carries the position (swift)", "flowPositionPayload(value1: step.position)" in dvm_sw)
check("hidden_fields is read fresh for every save (kotlin)", "val current = store.load()?.also { saved = it }" in dvm_kt)
check("hidden_fields is read fresh for every save (swift)", "let current = await store.load()" in dvm_sw)
check("the accepted answer is the one saved (kotlin)", "answer(step, draft, onAdvance)" in dvm_kt)
check("the accepted answer is the one saved (swift)", "await answer(step, draft: draft, onAdvance: onAdvance)" in dvm_sw)
for label, text in (("kotlin", pa_kt), ("swift", pa_sw)):
    for name in ("detail_answered", "detail_skipped", "field_display_opted_out"):
        check("the %s event is registry-backed (%s)" % (name, label), '"%s"' % name in text)
# Politics ends the walk at home until interests (step 8) is built -- one line on each side.
check("173 politics continues home for now (swift)", "choiceScreen(.politics, next: .home" in app_sw)
check("173 politics continues home for now (kotlin)", "FlowScreen.Home" in app_kt)

# ── the session ended (8 October 2026) ──────────────────────────────────────
# Not a ticket: the fix for saves that failed forever once the server had ended the session. The
# sentence is PROPOSED copy, pending Philipp -- this keeps the two platforms saying the same thing.
se_kt = read(KT, "welcome", "SessionEndedNotice.kt")
se_sw = read(SW, "SessionEnded.swift")
SESSION_ENDED = "Your session ended. Please log in again."
check("session ended: the sentence (kotlin)", '"%s"' % SESSION_ENDED in se_kt)
check("session ended: the sentence (swift)", '"%s"' % SESSION_ENDED in se_sw)
check("session ended: it goes on its own (kotlin)", "Motion.NOTICE" in se_kt)
check("session ended: it goes on its own (swift)", "Motion.notice" in se_sw)
tr_kt = read(KT, "api", "TokenRefresher.kt")
tr_sw = read(MOBILE, "ios-app", "ShowUpAPI", "Sources", "ShowUpAPI", "TokenRefresher.swift")
# Offline, a 5xx, a 408 or a 429 must never sign anyone out -- only the server's "no" does.
check("session ended: 408 and 429 are not a refusal (kotlin)",
      "statusCode == 408 || statusCode == 429 -> Unreachable" in tr_kt)
check("session ended: 408 and 429 are not a refusal (swift)", "case 408, 429: return .unreachable" in tr_sw)
check("session ended: only a refusal clears (kotlin)", "Outcome.Unreachable -> null" in tr_kt)
check("session ended: only a refusal clears (swift)",
      "case .unreachable:" in tr_sw and "onSessionEnded()" in tr_sw)
check("session ended: a clean restart (kotlin)", "FLAG_ACTIVITY_CLEAR_TASK" in app_kt)
check("session ended: a clean restart (swift)", ".id(epoch)" in app_sw)

# ── report ──────────────────────────────────────────────────────────────────
print("profile creation conformance: %d checks" % count)
if failures:
    print("FAILED %d:" % len(failures))
    for f in failures:
        print("  x %s" % f)
    sys.exit(1)
print("all pass")
