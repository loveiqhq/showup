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
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(HERE)
KT = os.path.join(MOBILE, "android-preview-project", "app", "src", "main", "java", "com", "showup")
SW = os.path.join(MOBILE, "ios-app", "ShowUpWelcome")

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

# Rule 5's named exception: the ONE profile screen with the ambient backdrop, and it gets it from
# the shared component rather than redrawing the orbs.
check("155 uses the shared backdrop scaffold (kotlin)", "WelcomeScaffold" in embrace_kt)
check("155 uses the shared backdrop scaffold (swift)", "WelcomeScaffold" in embrace_sw)
check("155 gutter 28 (kotlin)", "gutter = 28.dp" in embrace_kt)
check("155 gutter 28 (swift)", "gutter: 28" in embrace_sw)
check("155 top 64 (kotlin)", "topPadding = 64.dp" in embrace_kt)
check("155 top 64 (swift)", "topPadding: 64" in embrace_sw)

# Rule 7's named exception: full-width SUNSET, not the round orange NextButton.
check("155 CTA is sunset (kotlin)", "PrimaryButtonVariant.Sunset" in embrace_kt)
check("155 CTA is sunset (swift)", "variant: .sunset" in embrace_sw)
check("155 CTA is not the round NextButton (kotlin)", "NextButton" not in embrace_kt)
check("155 CTA is not the round NextButton (swift)", "NextButton" not in embrace_sw)

# Headline: Lora 700 / 34 / 1.1 / -0.015em, ONE italic em.
check("155 headline 34 (kotlin)", "fontSize = 34.sp" in embrace_kt)
check("155 headline 34 (swift)", "fontSize: 34" in embrace_sw)
check("155 headline tracking (kotlin)", "(-0.015).em" in embrace_kt)
check("155 headline tracking (swift)", "trackingEm: -0.015" in embrace_sw)

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
    check("158 registry stamp is 1.4.2 (%s)" % label, '"1.4.2"' in text)
    check("158 no stale registry stamp (%s)" % label, '"1.3.0"' not in text)

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

    # 18, and the rule that arrived with 1.4.1: `prompt_topic_selected` is scoped to suggestion
    # and browse. Enforced as a TYPE with no edit case, because prose is not enforcement.
    check("158 entry_point set (%s)" % label,
          '"suggestion"' in text and '"browse"' in text and '"edit"' in text)
    check("158 topic selection cannot carry an edit (%s)" % label, "TopicEntryPoint" in text)

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
    ("cta", u"Enable notifications"),
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

# THE SKIPPED USER STILL NEEDS A TOKEN. The spec sheet's Android <= 12 row says so outright --
# "These users still need push registration and all five categories" -- and `register()` had
# exactly one call site: the grant callback on a screen those users never see. minSdk is 30, so
# API 30-32 would every one of them have been a device the backend has no token for.
_model_kt = code_only(read(KT, "profile", "NotificationsViewModel.kt"))
_model_sw = code_only(read(SW, "NotificationsModel.swift"))
check("162 a skipped user registers for push (kotlin)", "fun skipped(" in _model_kt)
check("162 a skipped user registers for push (swift)", "func skipped(" in _model_sw)
check("162 only a GRANTED skip registers (kotlin)",
      "status != NotificationPermission.Granted" in _model_kt)
check("162 only a GRANTED skip registers (swift)", "status == .granted" in _model_sw)
check("162 the skip path is wired from the host (kotlin)",
      "notifyModel.skipped(status)" in code_only(read(KT, "MainActivity.kt")))
check("162 the skip path is wired from the host (swift)",
      "notifications.skipped(status)" in code_only(read(SW, "ShowUpWelcomeApp.swift")))

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

# ── report ──────────────────────────────────────────────────────────────────
print("profile creation conformance: %d checks" % count)
if failures:
    print("FAILED %d:" % len(failures))
    for f in failures:
        print("  x %s" % f)
    sys.exit(1)
print("all pass")
