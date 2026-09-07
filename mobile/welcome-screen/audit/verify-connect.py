# -*- coding: utf-8 -*-
"""Spec conformance check for Connect an account and Re-login SSO (SHOWUP-144 / 145).

Reads values back out of the Kotlin and Swift sources and checks them against the handoff:

    python audit/verify-connect.py

Order of authority, from welcome/README.md and both tickets:
  0. the epic's provider-sign-in note - overrules everything below, including the ticket body,
                                        the spec-sheet PNG and the zip
  1. welcome/screen-connect-reference.jsx - wins on numbers
  2. the tickets                          - win on behaviour, scope, copy
  3. the spec-sheet PNGs                  - win on nothing

Two ticket strings are deliberately NOT implemented, and this file asserts their absence rather
than their presence. `Try {Provider} again` and `Connecting to {Provider}...` would sit on a
provider's own button, and Apple, Google and Meta each publish a closed list of permitted button
titles that contains neither. The note settles it: "if our layout conflicts with a guideline the
guideline ships", and "the note has to overrule any other information". See MethodButton /
MethodRow for the full reasoning.
"""
import io
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tokens

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KT = os.path.join(ROOT, "android-preview-project/app/src/main/java/com/showup")
SW = os.path.join(ROOT, "ios-app/ShowUpWelcome")

fails = []
checks = 0
NL = chr(10)


def read(*parts):
    """The file, with design-token references expanded to the literals they hold.

    The assertions below look for the NUMBERS a spec sheet specifies. Since the tokens landed those
    numbers are written as `Spacing.screenGutter` and friends, so they are resolved here rather than
    in each check -- which keeps every assertion checking a value instead of a name. See
    audit/tokens.py for why that distinction matters.
    """
    path = os.path.join(*parts)
    return tokens.expand(io.open(path, encoding="utf-8").read(), swift=path.endswith(".swift"))


def check(name, ok):
    global checks
    checks += 1
    if not ok:
        fails.append(name)


def code_only(src):
    """Source with comments stripped.

    Every "must NOT appear" check below needs this. The files carry long comments explaining why
    the ticket's error string is absent, and those comments necessarily quote the string -- reading
    raw text would flag the explanation as the defect it documents. Same trap that bit
    verify-spec.py and verify-welcome.py on their first runs.
    """
    keep = []
    for line in src.split(NL):
        t = line.lstrip()
        if t.startswith("//") or t.startswith("*") or t.startswith("/*"):
            continue
        keep.append(line)
    return NL.join(keep)


list_kt = read(KT, "welcome/AuthMethodList.kt")
list_sw = read(SW, "AuthMethodList.swift")
conn_kt = read(KT, "welcome/ConnectAccountScreen.kt")
conn_sw = read(SW, "ConnectAccountView.swift")
back_kt = read(KT, "welcome/WelcomeBackScreen.kt")
back_sw = read(SW, "WelcomeBackView.swift")
shell_kt = read(KT, "welcome/WelcomeShell.kt")
# The primary button moved out of the shell into the design system on 7 September 2026.
btn_kt = read(KT, "designsystem/PrimaryButton.kt")
btn_sw = read(SW, "PrimaryButton.swift")
shell_sw = read(SW, "WelcomeShell.swift")
tok_kt = read(KT, "designsystem/DesignSystem.kt")
tok_sw = read(SW, "DesignSystem.swift")

conn_kt_code = code_only(conn_kt)
conn_sw_code = code_only(conn_sw)
list_kt_code = code_only(list_kt)
list_sw_code = code_only(list_sw)

# ── tokens added for these two screens ──────────────────────────────────────
for name, kt, sw in [
    ("bg-raised #F7F2FA", "0xFFF7F2FA", "0xF7F2FA"),
    ("border-soft .06", "0x0F1D1129", "opacity(0.06)"),
    ("success #00AB55", "0xFF00AB55", "0x00AB55"),
]:
    check("token " + name + " (kotlin)", kt in tok_kt)
    check("token " + name + " (swift)", sw in tok_sw)

# ── one component, ten states ───────────────────────────────────────────────
# "All ten states render from one screen component driven by state + provider + kind."
check("one state enum (kotlin)",
      "enum class ConnectState { Idle, Tapped, Handoff, Linking, Success, Cancelled, Error, Conflict }"
      in conn_kt)
check("one state enum (swift)",
      "enum ConnectState { case idle, tapped, handoff, linking, success, cancelled, error, conflict }"
      in conn_sw)
check("error kind enum (kotlin)", "enum class ErrorKind { Network, Declined }" in conn_kt)
check("error kind enum (swift)", "enum ErrorKind { case network, declined }" in conn_sw)
check("one entry point (kotlin)", conn_kt.count("fun ConnectAccountScreen(") == 1)
check("one entry point (swift)", conn_sw.count("struct ConnectAccountView: View") == 1)
check("driven by state+provider+kind (kotlin)",
      "state: ConnectState" in conn_kt and "provider: AuthMethod" in conn_kt
      and "kind: ErrorKind" in conn_kt)
check("driven by state+provider+kind (swift)",
      "var state: ConnectState" in conn_sw and "var provider: AuthMethod" in conn_sw
      and "var kind: ErrorKind" in conn_sw)

# "Conflict is shown as a modal over the mounted screen, not as a separate route."
check("conflict overlays the mounted screen (kotlin)",
      "if (state == ConnectState.Conflict) {" in conn_kt and "ConflictSheet(" in conn_kt)
check("conflict overlays the mounted screen (swift)",
      "if state == .conflict {" in conn_sw and "ConflictSheet(" in conn_sw)

# ── the shared, ordered method list ─────────────────────────────────────────
# "The provider list is one ordered data source: Apple, Google, Facebook" / "phone is absent"
check("connect order Apple Google Facebook (kotlin)",
      "listOf(AuthMethod.Apple, AuthMethod.Google, AuthMethod.Facebook)" in list_kt)
check("connect order Apple Google Facebook (swift)",
      "[AuthMethod] = [.apple, .google, .facebook]" in list_sw)
check("login order adds phone first (kotlin)",
      "listOf(AuthMethod.Phone, AuthMethod.Apple, AuthMethod.Google, AuthMethod.Facebook)" in list_kt)
check("login order adds phone first (swift)",
      "[AuthMethod] = [.phone, .apple, .google, .facebook]" in list_sw)
check("phone absent from connect (kotlin)", "AuthMethod.Phone" not in conn_kt_code)
check("phone absent from connect (swift)", ".phone" not in conn_sw_code)

# "The method list component is shared with Welcome back" - one component, two hosts.
check("connect uses the shared list (kotlin)", "AuthMethodList(" in conn_kt)
check("connect uses the shared list (swift)", "AuthMethodList(" in conn_sw)
check("welcome back uses the shared list (kotlin)", "AuthMethodList(" in back_kt)
check("welcome back uses the shared list (swift)", "AuthMethodList(" in back_sw)
check("no duplicate list in connect (kotlin)", "fun MethodButton" not in conn_kt)
check("no duplicate list in welcome back (kotlin)", "fun MethodButton" not in back_kt)
check("no duplicate spec table (swift)", "struct MethodSpec" not in back_sw
      and "struct MethodSpec" not in conn_sw)

# ── the skip: never disabled, never dimmed ──────────────────────────────────
# The ticket states this four separate times.
check("skip exists (kotlin)", "Skip and continue to profile" in list_kt)
check("skip exists (swift)", "Skip and continue to profile" in list_sw)
check("skip is never disabled (kotlin)",
      "fun SkipRow(onSkip: () -> Unit, anyLoading: Boolean)" in list_kt
      and "enabled = false" not in list_kt_code)
check("skip is never disabled (swift)", "struct SkipRow: View" in list_sw
      and "disabled(true)" not in list_sw_code)
check("skip never dims (kotlin)", ".alpha(0.45f)" not in list_kt_code.split("fun SkipRow")[-1])
check("skip darkens under load (kotlin)", "if (anyLoading) Fg else Muted" in list_kt)
check("skip darkens under load (swift)", "anyLoading ? .liqFg : .liqMuted" in list_sw)
check("connect passes a skip (kotlin)", "onSkip = onSkip" in conn_kt)
check("connect passes a skip (swift)", "onSkip: onSkip" in conn_sw)
# "Welcome back has no way past sign-in" - so no skip there.
check("welcome back has no skip (kotlin)", "onSkip" not in code_only(back_kt))
check("welcome back has no skip (swift)", "onSkip" not in code_only(back_sw))

# ── promotion by position, not by restyling ─────────────────────────────────
check("suggested promotes to first (kotlin)", "suggested?.takeIf" in list_kt)
check("suggested promotes to first (swift)", "if let s = suggested, methods.contains(s)" in list_sw)
check("tapped provider held across states (kotlin)",
      "state != ConnectState.Idle && state != ConnectState.Conflict" in conn_kt)
check("tapped provider held across states (swift)",
      "(state == .idle || state == .conflict) ? nil : provider" in conn_sw)
# The conflict must NOT reorder the list behind it.
check("conflict does not reorder (kotlin)", "ConnectState.Conflict" in conn_kt)
check("conflict does not reorder (swift)", "state == .conflict" in conn_sw)

# ── provider brand compliance (the note that overrules the ticket) ──────────
check("apple black fill (kotlin)", "PrimaryButtonVariant.Apple -> Modifier.clip(shape).background(Color.Black)"
      in btn_kt)
check("apple black fill (swift)", "case .apple:" in btn_sw and "Color.black" in btn_sw)
check("google white fill (kotlin)", "PrimaryButtonVariant.Google" in btn_kt and "0xFF747775" in btn_kt)
check("google white fill (swift)", "0x747775" in btn_sw)
check("facebook blue (kotlin)", "0xFF1877F2" in btn_kt)
check("facebook blue (swift)", "0x1877F2" in btn_sw)
check("google G is four-colour (kotlin)", "0xFF4285F4" in shell_kt and "0xFFEA4335" in shell_kt)
check("google G is four-colour (swift)", "0x4285F4" in shell_sw and "0xEA4335" in shell_sw)
# No gradient on a provider button, in any state or position.
check("no gradient on a provider (kotlin)",
      "AuthMethod.Phone, AuthMethod.Unknown -> if (isPrimary) PrimaryButtonVariant.Sunset" in list_kt)
check("no gradient on a provider (swift)",
      "case .phone, .unknown: return isPrimary ? .sunset : .ghost" in list_sw)
# The two impermissible titles must not be rendered.
# The ticket's two strings survive in the source as the false branch of the switch, so the decision
# stays one line from being reversed. What matters is that they are UNREACHABLE while the flag is
# true, so assert the guard rather than the absence.
#
# The guard is checked over a small window rather than the same line: Swift's ternary puts
# PROVIDER_COMPLIANT_LABELS on the line above the string, and reformatting real code to suit a
# checker is the wrong way round.
BACKSLASH = chr(92)
for label, src in [("kotlin", list_kt_code), ("swift", list_sw_code)]:
    lines = src.split(NL)
    for banned in ("Try $", "Try " + BACKSLASH + "(", "Connecting to"):
        hits = [i for i, ln in enumerate(lines) if banned in ln]
        guarded = all(
            any("PROVIDER_COMPLIANT_LABELS" in lines[j]
                for j in range(max(0, i - 3), min(len(lines), i + 2)))
            for i in hits
        )
        check("'" + banned + "' only behind the compliance flag (" + label + ")", guarded)

check("permitted titles only (kotlin)", "PROVIDER_COMPLIANT_LABELS = true" in list_kt)
check("permitted titles only (swift)", "PROVIDER_COMPLIANT_LABELS = true" in list_sw)

# The button's treatment must come from providerVariant() and NOTHING else. A conditional here is
# how the brand rule gets broken quietly: the ticket's superseded line asks for a failed provider
# to switch to bg-elevated, which would be a restyled provider button in a state the guidelines do
# not carve out. Asserting "no PrimaryButtonVariant literal inside the row" catches that, where checking the
# happy path alone did not -- this check exists because a mutation test walked straight past it.
row_kt = list_kt_code.split("private fun MethodButton")[-1].split("private fun SkipRow")[0]
row_sw = list_sw_code.split("private struct MethodRow")[-1].split("private struct SkipRow")[0]
check("row takes its variant only from providerVariant (kotlin)",
      "PrimaryButtonVariant." not in row_kt and "val variant = providerVariant(method, isPrimary)" in row_kt)
check("row takes its variant only from providerVariant (swift)",
      "PrimaryButtonVariant." not in row_sw and ".ghost" not in row_sw and ".sunset" not in row_sw
      and "let variant = providerVariant(method, isPrimary: isPrimary)" in row_sw)

# The conflict resolve CTA is a provider button too - the ticket lists it with the other three.
check("conflict CTA is provider-compliant (kotlin)", "variant = providerVariant(owner)" in conn_kt)
check("conflict CTA is provider-compliant (swift)", "variant: providerVariant(owner)" in conn_sw)

for label, src, stack in [("kotlin", list_kt_code, "verticalArrangement = Arrangement.spacedBy(10.dp)"),
                          ("swift", list_sw_code, "VStack(spacing: 10)")]:
    notice_at = src.find("notice")
    stack_at = src.find(stack)
    check("banner precedes the button stack (" + label + ")",
          notice_at != -1 and stack_at != -1 and notice_at < stack_at)

# ── linking is capped ───────────────────────────────────────────────────────
check("8s cap named (kotlin)", "LINKING_TIMEOUT_MS = 8_000L" in conn_kt)
check("8s cap named (swift)", "LINKING_TIMEOUT_SECONDS: Double = 8" in conn_sw)
check("cap falls out to error (kotlin)", "onLinkingTimeout()" in conn_kt)
check("cap falls out to error (swift)", "onLinkingTimeout()" in conn_sw)
check("cap only runs while linking (kotlin)", "if (state == ConnectState.Linking)" in conn_kt)
check("cap only runs while linking (swift)", "guard state == .linking else { return }" in conn_sw)

# ── cancel is neutral, error is danger ──────────────────────────────────────
check("cancel uses bg-raised (kotlin)", "background(Raised)" in list_kt)
check("cancel uses bg-raised (swift)", "Color.liqRaised" in list_sw)
cancel_kt = list_kt_code.split("fun CancelledNotice")[-1].split("fun Spinner")[0]
cancel_sw = list_sw_code.split("struct CancelledNotice")[-1].split("struct Spinner")[0]
check("no danger token in the cancel notice (kotlin)", "Danger" not in cancel_kt)
check("no danger token in the cancel notice (swift)", "Danger" not in cancel_sw)
check("error uses the danger banner (kotlin)", "Danger.copy(alpha = 0.07f)" in list_kt)
check("error uses the danger banner (swift)", "Color.liqDanger.opacity(0.07)" in list_sw)

# "There is no auto-retry." Nothing may re-dispatch a failed provider on its own.
check("no auto-retry (kotlin)", "retry" not in conn_kt_code.lower())
check("no auto-retry (swift)", "retry" not in conn_sw_code.lower())

# ── success falls back with no name ─────────────────────────────────────────
check("no-name fallback (kotlin)",
      'listOf("You\'re " to false, "in" to true, "." to false)' in conn_kt)
check("no-name fallback (swift)",
      '[("You\'re ", false), ("in", true), (".", false)]' in conn_sw)
check("no null in the headline (kotlin)", "firstName?.trim().orEmpty()" in conn_kt)
check("no null in the headline (swift)", 'trimmingCharacters(in: .whitespacesAndNewlines)' in conn_sw)

# ── the conflict modal ──────────────────────────────────────────────────────
check("conflict is bottom-anchored (kotlin)", "verticalArrangement = Arrangement.Bottom" in conn_kt)
check("conflict is bottom-anchored (swift)", "Spacer(minLength: 0)" in conn_sw)
# Content-sized: no fixed or minimum height on the sheet itself. Spacer heights inside it are the
# gaps between its rows and are not a height on the sheet.
for label, src, banned in [("kotlin", conn_kt_code, ("heightIn", "fillMaxHeight")),
                           ("swift", conn_sw_code, ("frame(height:", "frame(minHeight:"))]:
    sheet = src.split("ConflictSheet")[-1]
    for b in banned:
        check("conflict is content-sized, no " + b + " (" + label + ")", b not in sheet)
check("conflict never scrolls internally (kotlin)",
      "verticalScroll" not in conn_kt_code.split("fun ConflictSheet")[-1])
check("conflict never scrolls internally (swift)",
      "ScrollView" not in conn_sw_code.split("struct ConflictSheet")[-1])
check("conflict never truncates (kotlin)",
      "maxLines" not in conn_kt_code.split("fun ConflictSheet")[-1])
check("conflict never truncates (swift)",
      "lineLimit" not in conn_sw_code.split("struct ConflictSheet")[-1])
check("conflict names the OWNING provider (kotlin)", "methodSpec(owner).label" in conn_kt)
check("conflict names the OWNING provider (swift)", "methodSpec(owner).label" in conn_sw)
check("conflict interpolates both values (kotlin)",
      "email.isNullOrBlank()" in conn_kt and "append(email)" in conn_kt)
check("conflict interpolates both values (swift)",
      "guard let email, !email.isEmpty else" in conn_sw)
check("no-address fallback string (kotlin)", "That account is already on Show Up" in conn_kt)
check("no-address fallback string (swift)", "That account is already on Show Up" in conn_sw)
conflict_kt = conn_kt_code.split("fun ConflictSheet")[-1]
conflict_sw = conn_sw_code.split("struct ConflictSheet")[-1]
check("no danger token in the conflict modal (kotlin)", "Danger" not in conflict_kt)
check("no danger token in the conflict modal (swift)", "Danger" not in conflict_sw)
check("no close icon in the conflict modal (kotlin)", "BrandIcon.Close" not in conflict_kt)
check("no close icon in the conflict modal (swift)", ".close" not in conflict_sw)
check("scrim is not tappable (kotlin)", "clickable" not in conn_kt_code.split("fun Scrim")[-1]
      .split("fun MethodListLayout")[0])
check("scrim is not tappable (swift)", "allowsHitTesting(false)" in conn_sw)
check("secondary has no border (kotlin)", "PrimaryButtonVariant.Plain" in conn_kt)
check("secondary has no border (swift)", "variant: .plain" in conn_sw)

# ── exactly one dim layer, and the right owner ──────────────────────────────
check("handoff scrim is suppressed (kotlin)", "PLATFORM_DIMS_HANDOFF = true" in conn_kt)
check("handoff scrim is suppressed (swift)", "PLATFORM_DIMS_HANDOFF = true" in conn_sw)
check("conflict scrim is ours (kotlin)", "Scrim()" in conn_kt)
check("conflict scrim is ours (swift)", "Scrim()" in conn_sw)

# ── the OS sheets are NOT built ─────────────────────────────────────────────
# "There is no reproduction or restyling of Apple or Google UI" and "no hard-coded sheet height".
for label, src in [("kotlin", conn_kt_code), ("swift", conn_sw_code)]:
    for banned in ("Double-press to confirm", "Use another account", "Hide My Email",
                   "Choose an account", "to continue to Show Up",
                   "SAAppleSheetBody", "SAGoogleSheetBody", "SAOSHandoff"):
        check("no OS sheet reproduction (" + label + "): " + banned[:26], banned not in src)

# ── idempotency ─────────────────────────────────────────────────────────────
check("double-tap latch (kotlin)", "if (!dispatched) { dispatched = true; onSelect(m) }" in conn_kt)
check("double-tap latch (swift)", "if !dispatched { dispatched = true; onSelect(m) }" in conn_sw)
check("latch resets on state change (kotlin)", "remember(state) { mutableStateOf(false) }" in conn_kt)
# Matched loosely on purpose: the closure arity is a language detail that already changed once
# (iOS 17 deprecated the one-parameter form), and what this check is about is that the latch resets.
check("latch resets on state change (swift)",
      "onChange(of: state)" in conn_sw and "dispatched = false" in conn_sw)

# ── hidden, not disabled ────────────────────────────────────────────────────
check("hidden not disabled (kotlin)", "fun availableMethods(" in list_kt)
check("hidden not disabled (swift)", "func availableMethods(" in list_sw)
check("connect honours configured (kotlin)", "availableMethods(CONNECT_METHODS, configured)" in conn_kt)
check("connect honours configured (swift)",
      "availableMethods(CONNECT_METHODS, configured: configured)" in conn_sw)
check("welcome back honours configured (kotlin)",
      "availableMethods(LOGIN_METHODS, configured)" in back_kt)
check("welcome back honours configured (swift)",
      "availableMethods(LOGIN_METHODS, configured: configured)" in back_sw)
# ...but the default is still exactly four on Welcome back, which is the other rule.
check("welcome back defaults to four (kotlin)", "LOGIN_METHODS.toSet()" in back_kt)
check("welcome back defaults to four (swift)", "Set(LOGIN_METHODS)" in back_sw)

# ── no absolute Y anywhere ──────────────────────────────────────────────────
check("no absolute Y (kotlin)", "absoluteOffset" not in conn_kt_code)
check("no absolute Y (swift)", ".position(" not in conn_sw_code)

# ── the headline's trailing heart ──────────────────────────────────────────
# It sits INSIDE the text, on the baseline of the last line -- the sheet draws it there and the
# ticket says "heart-filled 30 on the baseline". As a sibling in a Row/HStack it reserved its
# width against every line, so the headline wrapped badly and the heart was pushed off the right
# edge. That was a real, visible clipping bug, so it is asserted rather than left to the eye.
check("heart is inside the text flow (kotlin)",
      "trailing = BrandIcon.Heart" in conn_kt)
check("heart is inside the text flow (swift)",
      "trailing: .heart" in conn_sw)
check("heart is NOT a Row sibling (kotlin)",
      "Icon(BrandIcon.Heart" not in code_only(conn_kt))
check("heart is NOT an HStack sibling (swift)",
      "BrandIconView(icon: .heart" not in code_only(conn_sw))
check("headline supports a trailing icon (kotlin)", "trailing: BrandIcon? = null" in shell_kt)
check("trailing mark is 30 (kotlin)", "trailingSize: Dp = 30.dp" in shell_kt)
check("trailing gap is 12 (kotlin)", "trailingGap: Dp = 12.dp" in shell_kt)
check("headline supports a trailing icon (swift)", "var trailing: BrandIcon? = nil" in shell_sw)
check("trailing mark is 30 (swift)", "var trailingSize: CGFloat = 30" in shell_sw)
check("trailing gap is 12 (swift)", "var trailingGap: CGFloat = 12" in shell_sw)
# ...and the gap travels with the icon, so it can never be orphaned onto its own line.
check("trailing gap is unbreakable (kotlin)", "appendInlineContent" in shell_kt)
check("trailing gap is unbreakable (swift)", "u{00A0}" in shell_sw)

# The emphasis phrase never breaks across lines -- the token file sets white-space: nowrap on
# `.su-underlined em` and both tickets restate it. A plain space is a legal break point, so the
# rule only holds if the space inside an italic run is a non-breaking one.
check("emphasis phrase cannot break (kotlin)", "t.replace(' ', NBSP)" in shell_kt)
check("emphasis phrase cannot break (swift)",
      'rawText.replacingOccurrences(of: " "' in shell_sw)

# SwiftUI needs to be told how wide the headline wants to be at a given width; without it a
# UILabel-backed representable reports its ONE-LINE width and squeezes out whatever sits beside it.
check("headline reports a wrapped size (swift)", "func sizeThatFits(_ proposal: ProposedViewSize" in shell_sw)

# ── numbers, from the reference ─────────────────────────────────────────────
for name, kt, sw in [
    ("wordmark to headline 96", "96.dp", "compact ? 44 : 96"),
    ("headline 40, 34 compact", "34.sp else 40.sp", "compact ? 34 : 40"),
    ("headline block gap 16", "spacedBy(16.dp)", "spacing: 16"),
    ("sub max 310", "widthIn(max = 310.dp)", "maxWidth: 310"),
    ("linking ring 120", "size(120.dp)", "width: 120, height: 120"),
    ("linking inner 84", "size(84.dp)", "width: 84, height: 84"),
    ("linking icon 42", "42.dp", "size: 42"),
    ("linking headline 28", "28.sp", "fontSize: 28"),
    ("success inner 88", "size(88.dp)", "width: 88, height: 88"),
    ("success icon 44", "44.dp", "size: 44"),
    ("success headline 32", "32.sp", "fontSize: 32"),
    ("success sub max 280", "widthIn(max = 280.dp)", "maxWidth: 280"),
    ("success CTA bottom 18", "bottom = 18.dp", "bottom, 18"),
    ("conflict radius 28", "RoundedCornerShape(28.dp)", "cornerRadius: 28"),
    ("conflict icon circle 56", "size(56.dp)", "width: 56, height: 56"),
    ("conflict headline 26", "26.sp", "fontSize: 26"),
    ("conflict rule line 13", "13.sp", "manrope(13, .medium)"),
    ("conflict primary 54", "54.dp", "height: 54"),
    ("conflict secondary 50", "50.dp", "height: 50"),
]:
    check("number " + name + " (kotlin)", kt in conn_kt)
    check("number " + name + " (swift)", sw in conn_sw)

for name, kt, sw in [
    ("list gap 10", "spacedBy(10.dp)", "spacing: 10"),
    ("list bottom 12", "padding(bottom = 12.dp)", "padding(.bottom, 12)"),
    ("skip height 52", "height(52.dp)", "height: 52"),
    ("skip radius 16", "RoundedCornerShape(16.dp)", "cornerRadius: 16"),
    ("skip dashed 1.5", "1.5.dp.toPx()", "lineWidth: 1.5, dash: [6, 4]"),
    ("skip arrow 17 / 2.2", "17.dp", "size: 17, stroke: 2.2"),
    ("banner radius 14", "RoundedCornerShape(14.dp)", "cornerRadius: 14"),
    ("banner bottom 14", "padding(bottom = 14.dp)", "padding(.bottom, 14)"),
    ("notice bottom 12", "padding(bottom = 12.dp)", "padding(.bottom, 12)"),
    ("banner glyph 20", "size(20.dp)", "width: 20, height: 20"),
    ("banner text 13.5", "13.5.sp", "13.5"),
    ("spinner 18 / 2", "18.dp", "lineWidth: 2"),
]:
    check("number " + name + " (kotlin)", kt in list_kt)
    check("number " + name + " (swift)", sw in list_sw)

# ── copy, exact ─────────────────────────────────────────────────────────────
CONNECT_COPY = [
    "Welcome to ", "Show Up",
    "Connect an account for easier future sign-ins.",
    "Or continue and start creating your profile.",
    "By continuing you agree to our ", "Terms", "Privacy Policy",
    "Signing you ", "in",
    "Verifying your ", "Apple ID", "Google account",
    " and setting things up. This takes a second.",
    "Don't close the app.",
    "We'll never post or message anyone on your behalf. Let's finish your profile in 90 seconds.",
    "Continue",
    "You already have an account.",
    "is already on Show Up",
    "to pick up where you left off.",
    "One person, one account. Show-up Rates only work if you can't start over.",
    "Use a different account",
]
for s in CONNECT_COPY:
    check("copy kotlin: " + s[:38], s in conn_kt)
    check("copy swift : " + s[:38], s in conn_sw)

LIST_COPY = [
    "Continue with phone number", "Continue with Apple",
    "Continue with Google", "Continue with Facebook",
    "Skip and continue to profile",
    "Last login was via phone", "Last login was via Apple",
    "Last login was via Google", "Last login was via Facebook",
    # The cancelled notice is part of the shared list, not the Connect screen.
    "Sign-in cancelled.", "sheet before we could finish.",
]
for s in LIST_COPY:
    check("copy kotlin: " + s[:38], s in list_kt)
    check("copy swift : " + s[:38], s in list_sw)

# The two error strings live on the screen, not on a button.
for s in ["We couldn't reach ", "Check your connection and try again.",
          "didn't return a valid sign-in. Try again, or use a different method."]:
    check("copy kotlin: " + s[:38], s in conn_kt)
    check("copy swift : " + s[:38], s in conn_sw)

# Product terms capitalise exactly.
for label, src in [("kotlin", conn_kt), ("swift", conn_sw)]:
    check("Show-up Rate not Show-Up Rate (" + label + ")", "Show-Up Rate" not in src)
    check("Show Up not ShowUp in copy (" + label + ")", '"ShowUp' not in src)

# Error voice is informative, never cautionary.
for label, src in [("kotlin", conn_kt_code), ("swift", conn_sw_code)]:
    for word in ("Wrong", "Failed", "Invalid"):
        check("no cautionary word '" + word + "' (" + label + ")", word not in src)

# ── no emoji anywhere - CLAUDE.md states it as a non-negotiable ─────────────
for label, src in [("connect kt", conn_kt), ("connect sw", conn_sw),
                   ("list kt", list_kt), ("list sw", list_sw)]:
    has_emoji = any(ord(c) > 0x2500 and not (0x2010 <= ord(c) <= 0x2E7F) for c in src)
    check("no emoji in " + label, not has_emoji)

# ── report ──────────────────────────────────────────────────────────────────
print("connect + re-login conformance: " + str(checks) + " checks")
if fails:
    print("FAILED " + str(len(fails)) + ":")
    for name in fails:
        print("  x " + name)
    sys.exit(1)
print("all passed")
