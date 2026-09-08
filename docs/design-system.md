# Design system

7 September 2026 — the tokens, and what deliberately is not one.

Two files per platform hold colour and type; five more hold everything else. iOS keeps them **flat**
in `ShowUpWelcome/` because `gen_pbxproj.py` discovers sources with `os.listdir`, not `os.walk` — a
file in a subfolder would compile locally and be silently missing from the target.

| | Android | iOS |
|---|---|---|
| Colour, type families | `designsystem/DesignSystem.kt` | `DesignSystem.swift` |
| Spacing | `designsystem/Spacing.kt` | `Spacing.swift` |
| Radius | `designsystem/Radius.kt` | `Radius.swift` |
| Control sizes | `designsystem/ComponentSizes.kt` | `ComponentSizes.swift` |
| Icon sizes | `designsystem/IconSizes.kt` | `IconSizes.swift` |
| Motion | `designsystem/Motion.kt` | `Motion.swift` |

## Colours

21 named values, mapped to the `--liq-*` properties from the handoff, with the source token named in
a comment on nearly every line. Unchanged by this work.

`Cream` background · `Orange` accents · `Purple` progress and consequences · `Fg` primary text ·
`Neutral` body · `Subtle` / `Muted` / `Faint` · `Elevated` / `Raised` surfaces · `Border` /
`BorderSoft` · `Success` · `Danger` / `DangerFg` · `Lavender` · `EyebrowBg` / `EyebrowOrangeBg` ·
plus the `SunsetStops` and `WordmarkStops` gradients.

**Fourteen raw colours stay hardcoded in screens, and must.** Seven per platform are Google's and
Meta's sign-in marks; their branding rules specify exact values we are not permitted to re-theme, so
a re-themeable token is the wrong container for them. Each carries the rule that mandates it in a
comment.

Twenty-four further non-brand raw colours (12 per platform) are illustration fills and gradient
stops, left for a separate review.

## Typography — deliberately untouched

**No type tokens exist, on purpose.** The audit found **41 distinct combinations** of family, size,
weight and line height across the screens, and only 12 are used more than once. The most-used
appears four times.

That is not a role system with gaps in it; it is per-element settings. Tokenising it would need
about forty roles, which encodes the absence of a system rather than creating one. Revisit when the
design has fewer, more deliberate type styles.

`13.5`pt in particular is real, intentional, and would be the first casualty of any tidy scale.

## Spacing

**Not a 4/8/16/24/32 scale.** Nine values are in genuine repeated use and they do not sit on a
regular step. A tidy scale would have meant changing spacing, which this cleanup was not allowed to
do; a tidy scale plus two dozen exceptions would be worse, reading as authoritative while lying.

| Token | Value | Meaning |
|---|---|---|
| `Spacing.screenGutter` | 24 | content inset from both screen edges |
| `Spacing.xs` | 4 | |
| `Spacing.sm` | 6 | |
| `Spacing.md` | 8 | rule rows on tutorial card 01; mark-to-label gaps |
| `Spacing.lg` | 10 | method list and button stacks |
| `Spacing.xl` | 12 | statement rows on cards 02–04 |
| `Spacing.xxl` | 16 | section gaps |

Ordinal names, because these values genuinely serve unrelated purposes. `screenGutter` has one
meaning, so it is the one with a semantic name.

**Left local:** 1, 2, 3, 5, 7, 9, 11, 14, 18, 20, 22, 28, 32 — mostly dot offsets and ring geometry.
**14 (22 uses) and 18 (9 uses)** are frequent enough to look like tokens and were left out anyway:
no single meaning could be found for either, and a token whose name cannot say what it is for is a
number with extra steps.

## Radius

| Token | Value | Meaning |
|---|---|---|
| `Radius.control` | 14 | inputs, the six code slots, method buttons |
| `Radius.pill` | 28 | the sunset CTA — exactly half of `controlHeight`, which is what makes it a pill |
| `Radius.errorBox` | 12 | the inline error box glued to the code slots |
| `Radius.card` | 16 | the method-list container |

**Left local:** 2 (the drawn flag rectangles — decorative), 18 (one use, iOS only, an unexplained
divergence from Android).

## Component sizes

| Token | Value | Meaning |
|---|---|---|
| `ComponentSizes.controlHeight` | 56 | the sunset CTA and the phone input. SHOWUP-140 fixes the CTA at 56 |
| `ComponentSizes.minTapTarget` | 44 | floor for anything tappable, even where the reference draws smaller |

**The reserved helper regions are NOT tokens**, and that is a decision rather than an omission.

They look like constants — the spec sheet names 20 for states A/B and 42 for C/D — and they are not.
A/B is **36** in code, because an error message takes two lines; 20 was the earlier value and it was
a bug. C/D is 42 because of a **rule**, `reserve = error box + 2`, where the box height follows from
the copy: our one-line string gives 41+2, the reference's two-line string gave 60+2. The call site
says so, and says the two move together if the copy changes.

A token called `helperRegionCD = 42` would freeze a number that is documented as moving, and would
state the spec value rather than the implemented one. The invariant that matters — the CTA does not
move between states — is asserted by `ScreenFitTest` at 17 device sizes, which is a stronger
guarantee than a named number.

## Icon sizes

| Token | Value | Meaning |
|---|---|---|
| `IconSizes.sm` | 20 | provider marks in method buttons, small status glyphs |
| `IconSizes.badge` | 56 | the round status/eyebrow badge |

**Left local:** 84, 104, 120 — all Connect-screen illustration geometry, and 120's six uses are six
references to the same spinner canvas on one screen.

`badge` and `controlHeight` are both 56 and are separate tokens on purpose: one is how tall a
control is, the other how wide a circle is. They agree today by coincidence, not by rule.

## Motion

Android holds milliseconds, iOS seconds. These already agreed across platforms before the tokens
existed, which is worth locking down rather than leaving to coincidence.

| Token | Android | iOS | Meaning |
|---|---|---|---|
| `FAST` / `fast` | 180 | 0.18 | press feedback and fades |
| `SCREEN` / `screen` | 320 | 0.32 | screen-to-screen transition |
| `SHAKE` / `shake` | 480 | 0.48 | the one-shot mismatch shake. **SHOWUP-143 specifies 480ms** |
| `PULSE_SLOW` / `pulseSlow` | 900 | 0.9 | in-flight pulse on a provider button |
| `PULSE_LONG` / `pulseLong` | 1400 | 1.4 | Connect linking animation |

**Left local:** the 500/1200/1600ms delays in `ConnectFlowHost` — the fake provider round trip,
which goes away with the real SDKs.

## PrimaryButton

One primitive per platform since 7 September 2026 — `designsystem/PrimaryButton.kt` and
`PrimaryButton.swift` — with eight call sites across both flows.

```kotlin
PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PrimaryButtonVariant = Sunset,   // Sunset Ghost Plain Apple Google Facebook
    enabled: Boolean = true,
    height: Dp = ComponentSizes.controlHeight,
    labelSize: TextUnit = 16.sp,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
)
```

Swift is the same struct with `leading` and `trailing` as `@ViewBuilder` slots and three
convenience inits, one per slot combination in use. **Name the slot at a Swift call site** rather
than using a trailing closure: with two closure properties an unlabelled one is ambiguous between
two inits, and the compiler error for that names neither.

### Six variants, and why each is required

`Sunset` the gradient CTA · `Ghost` bordered secondary · `Plain` the conflict modal's dismiss, which
has no border precisely so the pair does not read as two equal choices · `Apple` `Google` `Facebook`
each mandated by that provider's own sign-in branding rules, which are a condition of app
verification and cannot be re-themed.

### Two parameters that exist for one call site each

`labelSize` — 16 in the sign-up flow, 17 in the tutorial. A parameter rather than a variant because
the tone is identical; the tutorial's CTA is the same sunset button one step larger. Collapse it if
the design side agrees the two worlds should match.

`trailing` — the tutorial CTA's arrow. It mirrors `leading`, which is what carries the provider
marks and the in-flight spinner.

### What is not a duplicate

`NextButton` stayed where it is. It is a label beside a 56pt circular arrow badge with a drawn
two-layer glow and a spring press on the circle alone — a different silhouette with its own spec
sheet and twelve checks in `verify-spec.py`. Folding it into a pill would change six screens.

## InputField and FieldChrome

**What was duplicated was the chrome, not the input.** There is only one text input in the app. The
number field and the country pill beside it are the same box — one `controlHeight`, `Radius.control`
corners, `Elevated` fill, 1.5 outline — written twice a few lines apart on each platform, and
already differing in their horizontal padding.

So there are two exports, and only one of them is an input:

| | Android | iOS |
|---|---|---|
| The box | `Modifier.fieldChrome(outline, height, horizontalPadding, onClick)` | `FieldChrome(outline:height:horizontalPadding:dangerRing:)` |
| The input | `InputField(value, onValueChange, label, placeholder, …)` | `InputField(label:invalid:field:trailing:)` |

The pill is a **Button** wearing `fieldChrome`, which is why the chrome is separate: a button and a
text input share a visual contract without pretending to be the same widget.

**`onClick` is a chrome parameter rather than something the caller chains on.** `.clickable()
.padding(14)` and `.padding(14).clickable()` render identically and differ by 28dp of live width.
Taking the hook as a parameter means the order is not the caller's to get wrong.

**The field fills its box, and that is not configurable.** Android shipped a number field measuring
23dp inside a 56dp row: it looked right, and only its middle third took a tap. The fix — one
`fillMaxHeight()` / `frame(maxHeight: .infinity)` — now lives inside the primitive with no parameter
to disable it, and `TapTargetTest` / `TapTargetTests` measure the rendered height on both platforms.
Each has a **self-test that builds the defect on purpose and fails if the probe cannot see it**;
that self-test earned its place by catching a wrong prediction about iOS before it became a "fix"
for a defect that does not exist there.

**The six-digit code entry is deliberately not folded in.** It is one text field whose decoration is
six painted slots, drawing no text and no caret of its own. It shares only the corner radius with a
bordered text box, so including it would mean a variant flag for a component with almost no common
surface.

**No `enabled` parameter.** Neither field has a disabled state and no call site wants one.

### Autofill

Both fields declare what they hold on both platforms, since 8 September 2026. iOS had done so since
the screens were written; Android declared nothing on either, so the number never came from the
keychain and an arriving SMS code never appeared above the keyboard — a behaviour difference no
screenshot shows and no layout test measures.

| | Android | iOS |
|---|---|---|
| Number field | `contentType = FieldContent.PhoneNumber` on `InputField` | `.telephoneNumber` inside `PhoneNumberField` |
| Code field | `Modifier.autofill(FieldContent.SmsCode, …)` | `.textContentType(.oneTimeCode)` |

**The declaration sits in a different place on each platform, and that is correct.** iOS's
`InputField` takes the text field as a slot — the number field is a `UIViewRepresentable` for caret
reasons — so the control configures itself. Android's builds the `BasicTextField`, so the primitive
carries the parameter. Both declare it where the text field actually lives.

`FieldContent` is ours rather than Compose's `AutofillType`, which is experimental and would put an
opt-in on both screens. `designsystem/Autofill.kt` holds every experimental import in the app and is
the seam that collapses to a one-line `contentType` semantics property when the Compose BOM reaches
1.8; neither call site changes then.

Four parity assertions in `verify-welcome.py` cover both fields on both platforms, injection-tested.

### Open design decisions — two visible differences, kept on purpose

Both platforms render today exactly what they rendered before the consolidation, because these
screens are in PO Acceptance. Neither difference is drift; both need a design ruling.

| | Android | iOS | |
|---|---|---|---|
| **Invalid field** | red outline + glyph, no ring | red outline + glyph + a 4pt `Danger` ring at 10% outside the outline | carried as an explicit `dangerRing` parameter on `FieldChrome`, iOS-only and documented, so it is a known divergence |
| **Empty code slots** | outline at `Border` (12%) | outline at `Subtle` (46%) | **accessibility, not taste**: `Border` measures **1.28:1** against a 3:1 requirement. Audit finding 7 rejected 12% and fixed it on the field; the slots were missed on Android only |

The slot contrast is the one with a correct answer — 1.28:1 fails WCAG for a non-text control
boundary — but changing it alters a screen in PO Acceptance, so it is raised rather than taken.

## Reusable primitives — the state of play

`CountrySheet`, `PrimaryButton` and `InputField` are shared on both platforms. The rest are still
duplicated:

| Primitive | Today | |
|---|---|---|
| **StatusBadge** | private `Eyebrow` in Connect, plus a pill drawn inline in `TutorialShell` | twice on both platforms, and they diverged |
| **PrimaryButton** | `designsystem/PrimaryButton.kt` · `PrimaryButton.swift` | **done, 7 September 2026** — was three implementations, one of them drifted four ways |
| **InputField** | `designsystem/InputField.kt` · `InputField.swift` | **done, 8 September 2026** — the 23dp tap-target bug lived here |
| **TopBar** | inline in `VerificationFrame` | the wrong-icon bug lived here |
| **SelectPicker** | `CountrySheet` | already fine |

The duplication follows the package split: `com.showup.welcome` and `com.showup.tutorial` were built
as separate worlds and each grew its own version of the same thing.

### What the duplicate cost, since it is the argument for fixing the rest

`SunsetButton` was written six days before `PillButton` existed and never revisited. By the time it
was removed it had drifted four ways from the primitive it duplicated: a two-stop gradient where the
spec puts `D05976` at 38%, no violet shadow though iOS had one, no press feedback at all, and a
Material/SF glyph for its arrow where CLAUDE.md requires a drawn 2px stroke.

Every one of those four is a defect the 23 August audit found and fixed on the other tutorial
cards. It missed these because they lived in a file no verifier read. A duplicate does not stay a
duplicate; it becomes a worse copy nobody is looking at.

Two things had to move before the button could, both for the same reason — the design system must
not depend on a screen. `ShowUpEasing` went into `Motion.kt`, and `rememberMotion()` into
`MotionPreference.kt`, where its return type was renamed from `Motion` so it stops colliding with
the `Motion` durations object.

## How the verifiers still check numbers

`verify-spec.py`, `verify-welcome.py` and `verify-connect.py` assert the numbers a spec sheet
specifies — "gutter 24", "button gap 8", "login hit area 44". Those literals are now token
references, so `audit/tokens.py` expands references back to values before matching, reading the
mapping from the token files themselves.

The distinction is load-bearing. Rewriting those assertions to look for `Spacing.screenGutter` would
have made them assert a **name**: the token could then be redefined to 32 and every "gutter 24"
check would still pass. Verified by injection — redefining `screenGutter` to 32 fails
`spacing gutter 24`.
