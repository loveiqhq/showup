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

21 named values per platform, mapped to the `--liq-*` properties from the handoff, with the source
token named in a comment on nearly every line. One was added on 9 September 2026 — `SuccessFg`.

`Cream` background · `Orange` accents · `Purple` progress and consequences · `Fg` primary text ·
`Neutral` body · `Subtle` / `Muted` / `Faint` · `Track` · `Elevated` / `Raised` surfaces · `Border` /
`BorderSoft` · `Success` / `SuccessFg` · `Danger` / `DangerFg` / `DangerDigit` · `Lavender` ·
`EyebrowBg` / `EyebrowOrangeBg` · plus the `SunsetStops` and `WordmarkStops` gradients.

**`Success` and `SuccessFg` are two different values, not a shade of one another.** `--liq-success`
`#00AB55` is the badge fill; `--liq-success-fg` `#0A7A47` is the darker ink a success glyph needs on
a light ground. Only the badge one had been ported until the profile email screen's helper tick
needed the other. A ticket asking for "the success green" has to say which.

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

It gained one parameter on 9 September 2026: **`arrowSize`**, defaulting to the variant's own value
(22 for `Sunset`, 20 otherwise). The profile screens draw an orange `NextButton` with a 22 arrow,
which no existing variant produced. A parameter on the primitive was the alternative to a second
button, which is the mistake this section exists to record.

**The profile screens' CTA is `NextButton`, not `PrimaryButton`** — the spec sheet draws a label
beside a circular arrow, which is `NextButton`'s silhouette. Worth stating in a ticket, because
"primary button" in prose means the pill.

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

## FloatingField

Added 9 September 2026 for the profile flow. `designsystem/FloatingField.kt` and, on iOS,
`FloatingField.swift`.

An outlined 64-tall input whose label rides up and **notches the border** when the field is focused
or non-empty. Valid and error states each get an affordance in the trailing slot.

| | Android | iOS |
|---|---|---|
| Value | `value` + `onValueChange` | `@Binding var value` |
| Label / placeholder | `label`, `placeholder` | same |
| States | `valid`, `error` | same |
| Keyboard | `keyboardType`, `capitalization`, `imeAction` | `keyboard`, `capitalization` |
| Autofill | via `Modifier.autofill` | `contentType: UITextContentType?` |
| Submit | `onSubmit` | `onSubmit` |
| Focus | `focusRequester` | `@FocusState` internal |

`label` is not optional on either platform. An input with no accessibility label is a control
VoiceOver and TalkBack cannot name.

### Why this is not `InputField`

They share a rounded outlined box and nothing else. `InputField` is 56 tall, has a placeholder and
no label of its own, and exists to make the control fill its box. `FloatingField` is 64, carries a
label that cuts the stroke, and has valid/error affordances the phone field never has — different
height, different label model, different states. Merging them means a variant flag on each of those,
which is the "everything input" the system is careful to avoid.

What they *do* share is the rule `InputField` was built to enforce: the text is measured to the full
height of the box, so the whole 64 takes a tap rather than the middle third.

**The label notches the stroke, it does not break it.** The border is drawn unbroken and the label
sits on top with a patch of the screen colour behind. In the error state that patch switches from
white to `Cream`, because the field's fill becomes a 4% danger wash and a white patch would read as
a hole punched in it.

## Glyphs — CheckGlyph and DangerGlyph

`designsystem/Glyphs.kt` on Android; on iOS both live in `FloatingField.swift` rather than a file of
their own. That asymmetry is real and is recorded here so nobody hunts for `Glyphs.swift`.

| | Signature | Notes |
|---|---|---|
| `CheckGlyph` | `size`, `color`, `strokeWidth` (default 2) | drawn on a 24 grid and scaled, so it is crisp at any size |
| `DangerGlyph` | `size`, `glyphSize` (default `size * 0.59`) | a tinted circle with the `!` inside it |

Four hand-rolled copies of the danger circle existed across the two platforms before this. They are
now one primitive per platform, called from `FloatingField`, the phone field, `InlineErrorCard` and
`ErrorBanner`.

**The two call sites that pass 13 and 14 are not drift.** `FloatingField` draws its `!` at 13 inside
a 22 circle, the phone field at 14 inside the same 22, and each matches its own spec sheet. The
implementation is shared; the deliberate difference is preserved. Do not "normalise" them without a
design ruling.

## InlineErrorCard

`designsystem/InlineErrorCard.kt` on Android; inside `FloatingField.swift` on iOS.

The message card that sits under a field: `Radius.errorBox`, 14 horizontal / 10 vertical padding,
`Danger` at 7% fill on an 18% border, an 18 `DangerGlyph` nudged 1 down, and Manrope 500 / 13.5 in
`DangerFg`.

Two overloads on each platform — one taking a plain string, one taking rich text — because the email
screen renders `@` and `.com` as monospace chips inside the sentence.

**`ErrorBanner` is deliberately NOT this component.** It has a different radius, padding and glyph
size, and it sits at the top of a sheet rather than under a field. Only the glyph is genuinely
shared, so only the glyph was consolidated. Two things that both say something went wrong are not
automatically the same component.

## Profile chrome — AppHeader, BasicsScaffold, BasicsStep

Added 9 September 2026 for SHOWUP-150 / SHOWUP-152. **These live in `profile/`, not `designsystem/`**
— `BasicsChrome.kt` and `BasicsChrome.swift`.

That is a compromise, not a preference. `AppHeader` needs `Icon` and `BrandIcon`, which are still in
`welcome/WelcomeShell.kt`, and the design system must not depend on a screen. Moving the header into
`designsystem` means moving the icon set first. **Recorded as follow-up work**, and the reason it is
worth doing is the whole argument of the last section of this document.

- **`AppHeader(title, leading, onBack, backLabel)`** — a centred title with a fixed 36 slot on each
  side. `HeaderLeading.None` still occupies its 36: removing the slot would un-centre the title, and
  the title is the same string on all three steps, so it must not shift as the user advances. The
  back control's touch area is `ComponentSizes.minTapTarget`, not the 36 it draws.
- **`BasicsScaffold(title, leading, onBack, content, cta)`** — the header, one flexible spacer, and a
  CTA slot. Flat `Cream`, no ambient orbs: the keyboard owns the bottom half of these screens.
- **`BasicsStep`** — `Name`, `Email`, `EmailVerify`, `Dob`, carrying `progressSegment`, `hasBack`,
  `stepId` and `stepIndex`. `EmailVerify` reports **segment 2, not 3**: the user is on the email step
  until the code is confirmed, and a bar that advanced before that would claim progress not made.

**On iOS, name the `content:` slot at the call site.** `BasicsScaffold` has three function-typed
parameters (`onBack`, `content`, `cta`) and Swift's forward scan binds an unlabelled trailing closure
to the first unmatched one — a default argument does not exempt it — so an unlabelled closure lands
on `onBack`. This broke the build once on 9 September 2026.

`StepProgress` is reused as-is from `tutorial/TutorialShell`; the profile screens did not grow their
own.

## StatusBadge

One primitive per platform since 8 September 2026 — `designsystem/StatusBadge.kt` and
`StatusBadge.swift` — with four call sites each.

```kotlin
enum class BadgeTone { Orange, Lavender }

@Composable
fun StatusBadge(
    label: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Orange,
)
```

Swift is the same shape: `StatusBadge(label:tone:)` with `BadgeTone.orange` / `.lavender`.

### What was duplicated

Three implementations per platform, six in all, drawing one pill:

| | Tone | Fill it used |
|---|---|---|
| `Eyebrow` in Connect | orange | `Orange.copy(alpha = 0.12f)` / `.liqOrange.opacity(0.12)` — **raw** |
| `EyebrowPill` in `TutorialShell` | lavender | `EyebrowBg` |
| `Eyebrow` in the phone screens | orange | `EyebrowOrangeBg`, label hardcoded |

**The fill is the one that mattered.** `Orange` is `0xFE6839` and `EyebrowOrangeBg` is `0x1FFE6839`,
where `0x1F` is 31 — which is `0.12 x 255` rounded. So Connect and the phone screens painted the
same pixels by two routes, one of them a token and one of them a value re-derived at a call site.
Nothing looked wrong, and the token had quietly stopped being the single definition of the value.

### Two tones, and nothing else

`Orange` for the sign-up flow, `Lavender` for the tutorial cards. This is the case both `CLAUDE.md`
files already name — "a component's tone is per screen, and both tones stay" — so it is a variant
rather than a token one screen could redefine and break the other.

The tone sets the fill and the label colour and **not the dot**, which is `Orange` in every tone.
All three originals drew it that way; it is preserved rather than tidied into the variant.

### It does not position itself

Two of the three were `ColumnScope` extensions calling `.align(Alignment.Start)` on themselves, and
that is precisely what stopped Connect from reusing one: Connect's badge is **centred**, in a Column
with `horizontalAlignment = CenterHorizontally`. A pill that insists on its own alignment cannot go
there, so a third copy got written. Alignment now reaches the badge through `modifier` from the
parent, and the two leading call sites pass `Modifier.align(Alignment.Start)` themselves.

### What was normalised, and the one thing that was not

Normalised, all measured as pixel-identical: the fill (raw expression to the token it equals), the
letter spacing (written `0.08.em` twice and `0.88.sp` once — the same number at 11sp), the
uppercasing, and the hardcoded label.

**`lineHeight = 13.sp` was dropped rather than made a parameter.** The tutorial pill set it and the
two orange ones did not, which looked like the one difference that might be intentional. Measured
first: the label renders exactly **15.00dp tall either way**, because 13sp is below what this font
needs at 11sp and Compose was already ignoring it. A parameter for it would have been a knob that
changes nothing.

### Still raw, and out of scope

`Orange.copy(alpha = 0.12f)` survives once per platform, for the **56pt round shield** in Connect's
conflict modal (`IconSizes.badge`). That is a different component and was not in this task. The
conformance check counts occurrences rather than forbidding the expression, so the shield does not
have to be flagged forever and a second badge painting its own fill still fails.

## Tap targets

`Modifier.minTapTarget(min)` on Android, `.minTapTarget(_:alignment:)` on iOS, since 8 September
2026. `ComponentSizes.minTapTarget` is the source of truth and the floor is **clamped upward**:
asking for 48 gives 48, asking for 20 gives 44. A plain default would let a caller pass a smaller
number and quietly reintroduce the 23dp defect.

### Why this is a modifier and not a BackControl component

The two back controls were audited for consolidation and deliberately **not** merged:

| | Phone screens | Tutorial |
|---|---|---|
| Draws | `chevron-left` icon, 24, stroke 2, `Fg` | the **word "Back"**, Manrope SemiBold 14, `Subtle` |
| Position | top of screen | bottom nav row, beside `NextButton` |
| Shape | none | rounded clip (Android) |
| Visibility | always | `showBack` — hidden and cleared from semantics on card 01 |
| iOS press | `PressScale()` | `.plain` |

They share an `onBack` callback and nothing that is drawn. A `BackControl` covering both would have
been a component whose two variants share only a lambda. A `TopBar` is further off still: the
tutorial has no top bar at all, its top is `StepProgress`.

What they genuinely share is the rule — a control has to be big enough to hit — so the rule is what
was extracted. The four call sites keep their own icon, text, shape, position and visibility.

### 44 or 48 on Android — unresolved, on purpose

The numbers rendered today, all of them previously hardcoded at the call site:

| | Android | iOS |
|---|---|---|
| Phone-screen back | 44 | 44 |
| Tutorial back | **48** | 44 |

**The project's own rule says 48 on Android.** Both `CLAUDE.md` files state it — "44pt / 48dp minimum
for anything tappable" — which matches Material's 48dp against Apple's HIG 44pt. Three things
disagree with that rule:

* `ComponentSizes.minTapTarget` is **44 on both platforms**, so the token does not encode the split;
* the fit harness flags below **44** on Android, so it does not enforce the stated Android floor;
* the phone screens' back control renders **44** on Android, which the rule says is too small.

**Nothing was resized here**, because changing a hit area is a behaviour change and not a
consolidation. The recommendation, for its own task:

`ComponentSizes.minTapTarget` **should become platform-specific — 44 on iOS, 48 on Android** — rather
than the primitive hardcoding 48 for Android. The token is the thing that is currently wrong: it
presents a mirrored value where the rule is deliberately not mirrored, which is why three of four
call sites bypassed it with a literal. Making the token honest fixes the phone-screen back control,
the tutorial's explicit 48 becomes the default and can be dropped, and the harness floor can rise
with it.

That change grows two Android hit areas from 44 to 48. Both are invisible — neither control has a
fill — but it needs measuring at 17 sizes, because `heightIn` on a link inside a row can push a
column. Hence a separate task.

## Reusable primitives — the state of play

**Every shared primitive is now one implementation per platform.** As of 9 September 2026 there are
no known duplicated components left in the built screens — the retrofit that closed the last of them
is described below.

| Primitive | Where it lives | Status |
|---|---|---|
| **PrimaryButton** | `designsystem/PrimaryButton.kt` · `PrimaryButton.swift` | **done, 7 Sep 2026** — was three implementations, one drifted four ways |
| **InputField / FieldChrome** | `designsystem/InputField.kt` · `InputField.swift` | **done, 8 Sep 2026** — the 23dp tap-target bug lived here |
| **StatusBadge** | `designsystem/StatusBadge.kt` · `StatusBadge.swift` | **done, 8 Sep 2026** — was three per platform, one bypassing its own token |
| **Tap-target floor** | `designsystem/TapTarget.kt` · `TapTarget.swift` | **done, 8 Sep 2026** — a modifier, deliberately not a component |
| **FloatingField** | `designsystem/FloatingField.kt` · `FloatingField.swift` | **new, 9 Sep 2026** — the profile flow's input |
| **Glyphs** | `designsystem/Glyphs.kt` · in `FloatingField.swift` | **new, 9 Sep 2026** — replaced four hand-rolled danger circles |
| **InlineErrorCard** | `designsystem/InlineErrorCard.kt` · in `FloatingField.swift` | **new, 9 Sep 2026** — replaced two hand-rolled cards |
| **Autofill** | `designsystem/Autofill.kt` · native `contentType` | **done, 8 Sep 2026** |
| **TopBar** | — | **resolved as "no component"** — the two back controls share no visual contract; only the tap-target floor was shared, and that became the modifier above |
| **SelectPicker** | `CountrySheet` | already fine |
| **Profile chrome** | `profile/BasicsChrome.*` | **new, 9 Sep 2026** — outside `designsystem` until the icon set moves; see that section |

The duplication had followed the package split: `com.showup.welcome` and `com.showup.tutorial` were
built as separate worlds and each grew its own version of the same thing.

### The retrofit — the primitives were applied backwards as well as forwards

Applying primitives only to new screens leaves the old ones as the duplicates, which is how
`SunsetButton` drifted. On 9 September 2026 all built screens were audited and eleven remaining
sites migrated: four danger circles, two mismatch cards, and five tap-target floors. One of those
was a real defect rather than a duplicate — the iOS resend row had no minimum height at all, so its
hit area was whatever the text happened to measure.

**It was measured, not assumed.** 267 layout measurements across six screen states at four widths,
captured before and re-measured after: 263 identical, 4 changed — all four the same +1dp shift of
the mismatch glyph, because `InlineErrorCard` carries the `margin-top: 1` the spec specifies and the
hand-rolled copy had dropped. The refactor moved that pixel toward the design.

### Orientation

**Portrait only, both platforms**, product decision of 9 September 2026. Nothing in this document is
designed for landscape and no component has a landscape variant. The platform configuration and the
Android 16 large-screen caveat live in `mobile-client-architecture-spike.md` 1.5.2 rather than here,
so the rule has one home.

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
