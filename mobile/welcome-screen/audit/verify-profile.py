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

# ── report ──────────────────────────────────────────────────────────────────
print("profile creation conformance: %d checks" % count)
if failures:
    print("FAILED %d:" % len(failures))
    for f in failures:
        print("  x %s" % f)
    sys.exit(1)
print("all pass")
