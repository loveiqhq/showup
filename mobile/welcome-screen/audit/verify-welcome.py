# -*- coding: utf-8 -*-
"""Spec conformance check for the welcome & sign-up flow (SHOWUP-140 / 142 / 143).

Reads values back out of the Kotlin and Swift sources and checks them against the handoff:

    python audit/verify-welcome.py

Order of authority, from welcome/README.md:
  1. welcome/screen-*-reference.jsx  - wins on numbers
  2. the tickets                     - win on behaviour, scope, copy
  3. the spec-sheet PNGs             - win on nothing

SHOWUP-143 has no reference file, so it is checked against its ticket's acceptance criteria.
"""
import io
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KT = os.path.join(ROOT, "android-preview-project/app/src/main/java/com/showup")
SW = os.path.join(ROOT, "ios-app/ShowUpWelcome")

fails = []
checks = 0
CURLY = chr(0x2019)


def read(*parts):
    return io.open(os.path.join(*parts), encoding="utf-8").read()


def check(name, ok):
    global checks
    checks += 1
    if not ok:
        fails.append(name)


def code_only(src):
    """Source with comments stripped.

    Needed for the "must NOT appear" checks. Both platforms carry a comment saying why there is no
    Terms line on this screen, and that comment necessarily contains the word "Terms" -- searching
    raw text flags the explanation as the defect it documents. Same trap as verify-spec.py.
    """
    NL = chr(10)
    keep = []
    for line in src.split(NL):
        t = line.lstrip()
        if t.startswith("//") or t.startswith("*") or t.startswith("/*"):
            continue
        keep.append(line)
    return NL.join(keep)


shell_kt = read(KT, "welcome/WelcomeShell.kt")
shell_sw = read(SW, "WelcomeShell.swift")
start_kt = read(KT, "welcome/StartupScreen.kt")
start_sw = read(SW, "StartupView.swift")
back_kt = read(KT, "welcome/WelcomeBackScreen.kt")
back_sw = read(SW, "WelcomeBackView.swift")
ver_kt = read(KT, "welcome/PhoneVerificationScreen.kt")
ver_sw = read(SW, "PhoneVerificationView.swift")
tok_kt = read(KT, "designsystem/DesignSystem.kt")
tok_sw = read(SW, "DesignSystem.swift")

# ── tokens added for this flow ──────────────────────────────────────────────
for name, kt, sw in [
    ("elevated #FFFFFF", "0xFFFFFFFF", "0xFFFFFF"),
    ("danger #FB323B", "0xFFFB323B", "0xFB323B"),
    ("danger-fg #B71F26", "0xFFB71F26", "0xB71F26"),
    ("mismatch digit #7A1F26", "0xFF7A1F26", "0x7A1F26"),
]:
    check("token " + name + " (kotlin)", kt in tok_kt)
    check("token " + name + " (swift)", sw in tok_sw)
check("fg-muted .62 (kotlin)", "0x9E1D1129" in tok_kt)
check("fg-muted .62 (swift)", "opacity(0.62)" in tok_sw)
check("border .12 (kotlin)", "val Border" in tok_kt)
check("border .12 (swift)", "liqBorder" in tok_sw)

# ── the underline wash, from tokens/colors_and_type.css ─────────────────────
# radial ellipse at 50% 100%, orange .55 -> transparent at 70%, height .32em, bottom -.08em
check("wash 0.55 alpha (kotlin)", "alpha = 0.55f" in shell_kt)
check("wash 0.55 alpha (swift)", "withAlphaComponent(0.55)" in shell_sw)
check("wash height 0.32em (kotlin)", "0.32f" in shell_kt)
check("wash height 0.32em (swift)", "0.32" in shell_sw)
check("wash offset 0.08em (kotlin)", "0.08f" in shell_kt)
check("wash offset 0.08em (swift)", "0.08" in shell_sw)
check("wash stops at 70% (kotlin)", "0.7f to Color.Transparent" in shell_kt)
check("wash stops at 70% (swift)", "0.7]" in shell_sw or "0.0, 0.7" in shell_sw)
# sized from the laid-out run, never a fixed width
check("wash tracks the run (kotlin)", "getPathForRange" in shell_kt)
check("wash tracks the run (swift)", "enumerateEnclosingRects" in shell_kt or
      "enumerateEnclosingRects" in shell_sw)

# ── button — components/shared.jsx size lg ──────────────────────────────────
check("button height 56 (kotlin)", "height(56.dp)" in shell_kt)
check("button height 56 (swift)", "height: 56" in shell_sw)
check("button pad 28 (kotlin)", "horizontal = 28.dp" in shell_kt)
check("button pad 28 (swift)", ".padding(.horizontal, 28)" in shell_sw)
check("button label 16 bold (kotlin)", "fontSize = 16.sp" in shell_kt)
check("button label 16 bold (swift)", "manrope(16, .bold)" in shell_sw)
check("button gap 8 (kotlin)", "spacedBy(8.dp" in shell_kt)
check("button gap 8 (swift)", "HStack(spacing: 8)" in shell_sw)
# CLAUDE.md: press scales to 0.98, 180ms, cubic-bezier(.22,1,.36,1)
check("press 0.98 (kotlin)", "0.98f" in shell_kt)
check("press 0.98 (swift)", "0.98" in shell_sw)
check("press 180ms (kotlin)", "180" in shell_kt)
check("press 180ms (swift)", "0.18" in shell_sw)
check("brand easing (kotlin)", "CubicBezierEasing(0.22f, 1f, 0.36f, 1f)" in shell_kt)
check("brand easing (swift)", "timingCurve(0.22, 1, 0.36, 1" in shell_sw)
# sunset midpoint at 38%
check("sunset 38% (kotlin)", "0.38f to Color(0xFFD05976)" in tok_kt)
check("sunset 38% (swift)", "location: 0.38" in shell_sw)

# ── wordmark ────────────────────────────────────────────────────────────────
check("wordmark gradient not flat (kotlin)", "WordmarkStops" in shell_kt)
check("wordmark gradient not flat (swift)", "0xD05976" in shell_sw)
check("wordmark gap 0.18em (kotlin)", "0.18f" in shell_kt)
check("wordmark gap 0.18em (swift)", "size * 0.18" in shell_sw)
check("wordmark 26 (kotlin)", "26.sp" in shell_kt)
check("wordmark 26 (swift)", "size: CGFloat = 26" in shell_sw)

# ── backdrop shared between 140 and 142 ─────────────────────────────────────
check("one backdrop component (kotlin)", "WelcomeBackdrop" in shell_kt)
check("one backdrop component (swift)", "struct WelcomeBackdrop" in shell_sw)
check("startup uses the shared scaffold (kotlin)", "WelcomeScaffold" in start_kt)
check("startup uses the shared scaffold (swift)", "WelcomeScaffold" in start_sw)
check("welcome back uses the shared scaffold (kotlin)", "WelcomeScaffold" in back_kt)
check("welcome back uses the shared scaffold (swift)", "WelcomeScaffold" in back_sw)
# neither screen may hand-roll its own orbs
check("startup has no private backdrop (kotlin)", "radial-gradient" not in code_only(start_kt))
check("welcome back has no private backdrop (kotlin)", "radial-gradient" not in code_only(back_kt))
# phone verification: no peach wash, calmer
check("verification drops the peach wash (kotlin)", "peachWash = false" in ver_kt)
check("verification drops the peach wash (swift)", "peachWash: false" in ver_sw)

# ── SHOWUP-140 · Startup ────────────────────────────────────────────────────
check("140 headline line 1 at 32 (kotlin)", "fontSize = 32.sp" in start_kt)
check("140 headline line 1 at 32 (swift)", "size: 32" in start_sw)
check("140 headline line 2 at 44 (kotlin)", "fontSize = 44.sp" in start_kt)
check("140 headline line 2 at 44 (swift)", "fontSize: 44" in start_sw)
check("140 sub copy 19 semibold (kotlin)", "fontSize = 19.sp" in start_kt)
check("140 sub copy 19 semibold (swift)", "manrope(19, .semibold)" in start_sw)
check("140 sub copy max 320 (kotlin)", "widthIn(max = 320.dp)" in start_kt)
check("140 sub copy max 320 (swift)", "maxWidth: 320" in start_sw)
check("140 gap 132 (kotlin)", "132.dp" in start_kt)
check("140 gap 132 (swift)", "132" in start_sw)
check("140 two flex spacers (kotlin)", start_kt.count("Modifier.weight(1f)") >= 2)
check("140 two flex spacers (swift)", start_sw.count("Spacer(minLength: 0)") >= 2)
check("140 login hit area 44 (kotlin)", "heightIn(min = 44.dp)" in start_kt)
check("140 login hit area 44 (swift)", "minHeight: 44" in start_sw)
check("140 social proof is toggleable (kotlin)", "showSocialProof" in start_kt)
check("140 social proof is toggleable (swift)", "showSocialProof" in start_sw)

# ── SHOWUP-142 · Welcome back ───────────────────────────────────────────────
check("142 gap 120 (kotlin)", "120.dp" in back_kt)
check("142 gap 120 (swift)", "120" in back_sw)
check("142 sub 17 medium (kotlin)", "fontSize = 17.sp" in back_kt)
check("142 sub 17 medium (swift)", "manrope(17, .medium)" in back_sw)
check("142 sub max 280 (kotlin)", "widthIn(max = 280.dp)" in back_kt)
check("142 sub max 280 (swift)", "maxWidth: 280" in back_sw)
check("142 canonical order (kotlin)",
      "AuthMethod.Phone, AuthMethod.Apple, AuthMethod.Google, AuthMethod.Facebook" in back_kt)
check("142 canonical order (swift)", "[.phone, .apple, .google, .facebook]" in back_sw)
check("142 unknown falls back to phone (kotlin)", "else AuthMethod.Phone" in back_kt)
check("142 unknown falls back to phone (swift)", "known ? lastUsed : .phone" in back_sw)
check("142 unknown hides the hint (kotlin)", "if (known)" in back_kt)
check("142 unknown hides the hint (swift)", "if known" in back_sw)
check("142 hint 12 semibold (kotlin)", "fontSize = 12.sp" in back_kt)
check("142 hint 12 semibold (swift)", "manrope(12, .semibold)" in back_sw)
check("142 no Terms line", "Terms" not in code_only(back_kt) and "Terms" not in code_only(back_sw))

# ── SHOWUP-143 · Phone verification ─────────────────────────────────────────
check("143 headline 38 (kotlin)", "fontSize = 38.sp" in ver_kt)
check("143 headline 38 (swift)", "fontSize: 38" in ver_sw)
check("143 sub 15 medium (kotlin)", "fontSize = 15.sp" in ver_kt)
check("143 sub 15 medium (swift)", "manrope(15, .medium)" in ver_sw)
check("143 field height 56 (kotlin)", "height(56.dp)" in ver_kt)
check("143 field height 56 (swift)", "height: 56" in ver_sw)
check("143 field radius 14 (kotlin)", "RoundedCornerShape(14.dp)" in ver_kt)
check("143 field radius 14 (swift)", "cornerRadius: 14" in ver_sw)
check("143 border 1.5 (kotlin)", "1.5.dp" in ver_kt)
check("143 border 1.5 (swift)", "lineWidth: 1.5" in ver_sw)
check("143 helper A/B reserved at 20 (kotlin)", "heightIn(min = 20.dp)" in ver_kt)
check("143 helper A/B reserved at 20 (swift)", "minHeight: 20" in ver_sw)
# see finding 1 -- 42 cannot hold the specified copy, so the reserve is the measured height
check("143 helper C/D reserved (kotlin)", "heightIn(min = 42.dp)" in ver_kt)
check("143 helper C/D reserved (swift)", "minHeight: 42" in ver_sw)
check("143 slots 49x62 (kotlin)", "49.dp" in ver_kt and "62.dp" in ver_kt)
check("143 slots 49x62 (swift)", "49" in ver_sw and "62" in ver_sw)
check("143 slots shrink to 44x56 (kotlin)", "44.dp" in ver_kt and "56.dp" in ver_kt)
check("143 slots shrink to 44x56 (swift)", "44 : 49" in ver_sw and "56 : 62" in ver_sw)
check("143 digit Lora 700 30 (kotlin)", "fontSize = 30.sp" in ver_kt)
check("143 digit Lora 700 30 (swift)", "size: 30" in ver_sw)
check("143 caret 2x28 (kotlin)", "width = 2.dp, height = 28.dp" in ver_kt)
check("143 caret 2x28 (swift)", "width: 2, height: 28" in ver_sw)
check("143 mismatch wash 4% (kotlin)", "alpha = 0.04f" in ver_kt)
check("143 mismatch wash 4% (swift)", "opacity(0.04)" in ver_sw)
check("143 shake 480ms once (kotlin)", "480" in ver_kt)
check("143 shake 480ms once (swift)", "0.48" in ver_sw)
check("143 CTA disabled until 6 digits (kotlin)", "digits.length == 6" in ver_kt)
check("143 CTA disabled until 6 digits (swift)", "digits.count == 6" in ver_sw)
check("143 mismatch releases the cooldown (kotlin)", "mismatch || cooldownSeconds <= 0" in ver_kt)
check("143 mismatch releases the cooldown (swift)", "mismatch || cooldownSeconds <= 0" in ver_sw)
check("143 slot row has an aria label (kotlin)", "Enter your 6-digit verification code" in ver_kt)
check("143 slot row has an aria label (swift)", "Enter your 6-digit verification code" in ver_sw)
# the keypad is a mock and must not be shipped
check("143 no keypad built (kotlin)", "NumericKeypad" not in code_only(ver_kt))
check("143 no keypad built (swift)", "NumericKeypad" not in code_only(ver_sw))
# no emoji flag
check("143 flag drawn, not emoji (kotlin)", "GermanFlag" in ver_kt)
check("143 flag drawn, not emoji (swift)", "GermanFlag" in ver_sw)

# ── every named control is actually tappable ───────────────────────────────
# SHOWUP-140: "Terms & Conditions, Privacy Policy, and Legal Notice are real tappable links"
# and "each legal link has its own hit area". They were styled but inert on the first pass.
check("140 CTA tappable (kotlin)", "PillButton(\"Create free account\"" in start_kt)
check("140 CTA tappable (swift)", "PillButton(\"Create free account\"" in start_sw)
check("140 Log in tappable (kotlin)", "onClick = onLogin" in start_kt)
check("140 Log in tappable (swift)", "Button(action: onLogin)" in start_sw)
for target in ("onTerms", "onPrivacy", "onLegalNotice"):
    check("140 " + target + " wired (kotlin)", target in start_kt)
    check("140 " + target + " wired (swift)", target in start_sw)
check("140 legal links are real links (kotlin)", start_kt.count("LinkAnnotation.Clickable") == 3)
check("140 legal links are real links (swift)", start_sw.count("a.link = URL") >= 1
      and "OpenURLAction" in start_sw)
# and they must wrap as one paragraph, not sit in a Row that cannot break
check("140 legal line is one paragraph (kotlin)", "buildAnnotatedString" in start_kt)
check("140 legal line is one paragraph (swift)", "AttributedString" in start_sw)

# SHOWUP-142: Get help / Use a different account / Legal Notice / Privacy Policy
for target in ("onGetHelp", "onUseDifferentAccount", "onLegal", "onPrivacy"):
    check("142 " + target + " wired (kotlin)", target in back_kt)
    check("142 " + target + " wired (swift)", target in back_sw)
check("142 links are real links (kotlin)", back_kt.count("LinkAnnotation.Clickable") == 4)
check("142 links are real links (swift)", "OpenURLAction" in back_sw)
check("142 four method buttons tappable (kotlin)", "onContinue(primary)" in back_kt
      and "onContinue(m)" in back_kt)
check("142 four method buttons tappable (swift)", "onContinue(primary)" in back_sw
      and "onContinue(m)" in back_sw)

# SHOWUP-143
for target in ("onSubmit", "onOpenCountryList", "onBack"):
    check("143 " + target + " wired (kotlin)", target in ver_kt)
    check("143 " + target + " wired (swift)", target in ver_sw)
for target in ("onVerify", "onResend", "onEditNumber"):
    check("143 " + target + " wired (kotlin)", target in ver_kt)
    check("143 " + target + " wired (swift)", target in ver_sw)

# ── contrast · readable text must not use the token that fails WCAG AA ─────
# --liq-fg-subtle is ink 46% = 3.04:1, under the 4.5:1 needed for normal text. --liq-fg-muted is
# ink 62% = 5.03:1 and passes. Body and legal text takes Muted. See audit finding 7.
for label, kt_src, sw_src in [("140", start_kt, start_sw), ("142", back_kt, back_sw),
                              ("143", ver_kt, ver_sw)]:
    check(label + " no readable text on the failing token (kotlin)",
          "color = Subtle" not in code_only(kt_src))
    check(label + " no readable text on the failing token (swift)",
          "foregroundColor = .liqSubtle" not in code_only(sw_src)
          and "foregroundColor(.liqSubtle)" not in code_only(sw_src))

# ── copy, character for character ───────────────────────────────────────────
COPY = [
    (start_kt, start_sw, [
        "Stop texting for days.",
        "Start ", "meeting", " today.",
        "Your availability. Your intent. Your date — today or tomorrow.",
        "234.000 Dates", " already organized",
        "Create free account", "Already have an account? ", "Log in",
        "Terms & Conditions", "Privacy Policy", "Legal Notice",
    ]),
    (back_kt, back_sw, [
        "Welcome back ",
        "Sign back in to check your availability and see who" + CURLY + "s free today.",
        "Continue with phone number", "Continue with Apple",
        "Continue with Google", "Continue with Facebook",
        "Last login was via phone", "Last login was via Apple",
        "Last login was via Google", "Last login was via Facebook",
        "Trouble signing in? ", "Get help", "Use a different account",
        "Legal Notice", "Privacy Policy",
    ]),
    (ver_kt, ver_sw, [
        "What" + CURLY + "s your ", "number",
        "We" + CURLY + "ll send a 6-digit code to verify it is you.",
        "Standard message rates may apply.",
        "Please enter a valid number e.g. 176 123 45 678",
        "Send me the code",
        "Enter your ", "code",
        "We just sent a 6-digit code to ",
        "That code didn" + CURLY + "t match. Try again.",
        "Verify code", "Didn" + CURLY + "t receive a code?",
        "Send a new code", "Edit phone number",
    ]),
]
for kt_src, sw_src, strings in COPY:
    for s in strings:
        check("copy kotlin: " + s[:34], s in kt_src)
        check("copy swift : " + s[:34], s in sw_src)

# no emoji anywhere -- CLAUDE.md states it as a non-negotiable
for label, src in [("kotlin shell", shell_kt), ("swift shell", shell_sw),
                   ("140 kt", start_kt), ("140 sw", start_sw),
                   ("142 kt", back_kt), ("142 sw", back_sw),
                   ("143 kt", ver_kt), ("143 sw", ver_sw)]:
    has_emoji = any(ord(c) > 0x2500 and not (0x2010 <= ord(c) <= 0x2E7F) for c in src)
    check("no emoji in " + label, not has_emoji)

# ── report ──────────────────────────────────────────────────────────────────
print("welcome flow conformance: " + str(checks) + " checks")
if fails:
    print("FAILED " + str(len(fails)) + ":")
    for name in fails:
        print("  x " + name)
    sys.exit(1)
print("all passed")
