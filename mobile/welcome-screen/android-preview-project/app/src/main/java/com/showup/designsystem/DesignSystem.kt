/*
 * DesignSystem.kt
 * ShowUp · shared colour and type tokens
 *
 * Extracted from WelcomeScreen.kt when the second tutorial screen arrived. Two screens cannot each
 * declare their own Cream/Orange/Lora — they would drift, and on iOS the equivalent is a hard
 * compile error. One definition, imported everywhere.
 *
 * Values come from the spec sheets, which carry colour and type tokens only. There is deliberately
 * no spacing scale: the raw px values in each screen are what design specified, not an oversight.
 */
package com.showup.designsystem

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.showup.R

// ── Colour ───────────────────────────────────────────────────────────────
val Cream = Color(0xFFFFFBF7)          // --liq-bg          · screen background
val Orange = Color(0xFFFE6839)         // --liq-orange-500  · accents, dots, Next circle
val Purple = Color(0xFF812AEC)         // --liq-primary-500 · progress fill, consequences
val Fg = Color(0xFF1D1129)             // --liq-fg          · primary text
val Neutral = Color(0xFF4B3B5A)        // --liq-neutral-200 · body text
val Subtle = Color(0x751D1129)         // --liq-fg-subtle   · rgba(29,17,41,.46)
val Faint = Color(0x3D1D1129)          // --liq-fg-faint    · rgba(29,17,41,.24) — the " — " dash
val Track = Color(0x1F1D1129)          // rgba(29,17,41,.12) — unfilled progress segments
val EyebrowBg = Color(0x29A78BFA)      // rgba(167,139,250,.16) — eyebrow pill fill, lavender tone
// The eyebrow has three tones in the handoff and the tone is per screen, not global: the tutorial
// cards use lavender, the phone screens use orange. Two tokens, because one pill cannot be both.
val EyebrowOrangeBg = Color(0x1FFE6839) // rgba(254,104,57,.12) — eyebrow pill fill, orange tone

// Added for the welcome & sign-up flow (SHOWUP-140/142/143). Values are verbatim from
// design_handoff_showup/tokens/colors_and_type.css, which is the authoritative token file.
val Elevated = Color(0xFFFFFFFF)       // --liq-bg-elevated · input and slot fills
val Raised = Color(0xFFF7F2FA)         // --liq-bg-raised   · raised chrome; the neutral notice
val BorderSoft = Color(0x0F1D1129)     // --liq-border-soft · rgba(29,17,41,.06)
val Success = Color(0xFF00AB55)        // --liq-success     · the connected badge
/**
 * --liq-success-fg. A DIFFERENT value from [Success], not a shade of it.
 *
 * #00AB55 is the badge fill; #0A7A47 is the darker ink used for a success GLYPH or label on
 * a light ground, where the badge green does not carry enough contrast. The profile email
 * screen's helper tick is the first use. Both are in the design system's CSS; only the badge
 * one had been ported.
 */
val SuccessFg = Color(0xFF0A7A47)      // --liq-success-fg  · success glyphs on light ground
val Muted = Color(0x9E1D1129)          // --liq-fg-muted    · rgba(29,17,41,.62)
/**
 * `--liq-fg-subtle`, rgba(29, 17, 41, 0.46) — tertiary ink.
 *
 * The register the GDPR fine print is set in on Stay reachable (SHOWUP-163): quieter than
 * [Neutral] so it reads as reassurance rather than as another paragraph to get through. Added
 * with that screen because it is the first to need it; `--liq-fg-muted` and `--liq-fg-faint`
 * are in the token file too and are not used by anything yet, so they are not here.
 */
val FgSubtle = Color(0x751D1129)       // --liq-fg-subtle   · rgba(29,17,41,.46)

val Border = Color(0x1F1D1129)         // --liq-border      · rgba(29,17,41,.12)
val Danger = Color(0xFFFB323B)         // --liq-danger      · invalid borders, the ! glyph
val DangerFg = Color(0xFFB71F26)       // --liq-danger-fg   · error helper text
val DangerDigit = Color(0xFF7A1F26)    // mismatch digit colour — a one-off, not a token
val Lavender = Color(0xFFA78BFA)       // --liq-lavender-400

/**
 * --liq-lavender-50. The quietest lavender in the ramp, and a SURFACE rather than an accent.
 *
 * Nearly white with a violet cast, so a card can read as "ours" without competing with anything
 * on it. Used by the prompts screen's worked-example card and the small pip behind its plus glyph
 * (SHOWUP-158). Distinct from [EyebrowBg], which is lavender-400 at 16% -- that one is a tint OF
 * the accent, this one is a named step in the ramp.
 */
val LavenderWash = Color(0xFFF9F7FF)   // --liq-lavender-50 · the palest surface in the ramp

// ── added for SHOWUP-165 to SHOWUP-173 ─────────────────────────────────────
//
// The two lighter steps the confetti draws in (Profile 13 names them as tokens: "tokens only:
// --liq-orange-500 · --liq-orange-400 · --liq-primary-500 · --liq-primary-400"), and the three
// translucent fills the location ask and the detail rows set.
val Orange400 = Color(0xFFFF9450)      // --liq-orange-400
val Purple400 = Color(0xFFA855F7)      // --liq-primary-400

/**
 * The selected-row wash on `OptionRow` and `CheckRow`, rgba(129,42,236,.06). The reference sets it
 * inline on both rows; it is a token here because two components carry it and must not drift.
 */
val SelectedRowWash = Color(0x0F812AEC)

/** The location ask's privacy-note fill, rgba(167,139,250,.10). */
val PrivacyNoteWash = Color(0x1AA78BFA)

/**
 * The toast's shadow ink, rgba(46,1,71,.22) -- the violet-black every `--liq-shadow-*` token is
 * cast in (46, 1, 71), at the alpha the three toast references set.
 */
val ToastShadow = Color(0x382E0147)

// ── added for SHOWUP-174 to SHOWUP-176 ─────────────────────────────────────
//
// The chip and pill glows Interests and Life set inline. Tokens because each is drawn by a shared
// component two screens consume (Future draws Life's pill), and a glow retyped per screen drifts.

/** The picked interest chip's glow, rgba(129,42,236,.18) -- `0 4px 10px` (Profile 21). */
val ChipGlow = Color(0x2E812AEC)

/** The resting interest chip's hairline lift, rgba(46,1,71,.03) -- `0 1px 2px` (Profile 21). */
val ChipRest = Color(0x082E0147)

/** The picked life pill's glow, rgba(129,42,236,.22) -- `0 2px 8px` (Profile 22, used by 23). */
val PillGlow = Color(0x38812AEC)

/**
 * The interests counter's empty bar, rgba(29,17,41,.08). Not [Track] (.12): the counter's bar is a
 * meter beside a sentence, drawn lighter than the step bar so the two never read as one control.
 */
val MeterTrack = Color(0x141D1129)

// ── added for the 48hrs Check-In (SHOWUP-178 to SHOWUP-183) ────────────────
//
// Values the six Check-In references set inline and that a built-once component draws (the step
// footer's toast, the availability tiers, the coach note, the dialog, the gate's tab bar). Each
// names the reference that sets it. Tints of an existing token (the danger field, the focus ring,
// the number disc's shadow) are written as `Danger.copy(alpha = ...)` where they are drawn, the way
// InlineErrorCard already draws its own, rather than as tokens of their own.

/** --liq-primary-600, #6B1D8F -- the availability summary's ranges (Check-In 02). */
val Primary600 = Color(0xFF6B1D8F)

/** `--liq-primary` #541475, the deepest violet: Search's `Be sure.` (callout 22). */
val PrimaryDeep = Color(0xFF541475)

/** The 2nd-tap "Available" tier and its legend pill: fill, ink and border (Check-In 02). */
val TierAvailableFill = Color(0xFFF1ECF9)
val TierAvailableInk = Color(0xFF7C6BA0)
val TierAvailableBorder = Color(0xFFE3DBF0)

/** CoachNote, lilac -- and How it works' panel, which matches it (Check-In 02, 03, 06). */
val CoachLilacFill = Color(0xFFEFE6FF)
val CoachLilacInk = Color(0xFF4F2B8C)

/** CoachNote, warm -- Motivation, Vibe and Filters (Check-In 03 to 05). */
val CoachWarmFill = Color(0xFFFFF6CF)
val CoachWarmInk = Color(0xFF73510B)

/** CheckInFooter's toast, rgba(29,17,41,.92), and its `0 6px 20px` shadow at .18. */
val CheckInToastFill = Color(0xEB1D1129)
val CheckInToastShadow = Color(0x2E1D1129)

/** The No hours today? dialog: scrim rgba(29,17,41,.44), card shadow rgba(20,12,28,.34). */
val DialogScrim = Color(0x701D1129)
val DialogShadow = Color(0x57140C1C)

/** The tab bar's glass, rgba(255,251,247,.92) (`components/shared.jsx` TabBar). */
val TabBarGlass = Color(0xEBFFFBF7)

// ── added for Search 01 (SHOWUP-184) ───────────────────────────────────────
//
// As with Check-In, a tint of a token (the meet tile, the prompt edge, the coach scrim) is written
// `Orange.copy(alpha = ...)` where it is drawn; only a base value no token held comes here.

/**
 * rgb(46,1,71), the violet-black every `--liq-shadow-*` token is cast in -- the like sheets' upward
 * lift at .18. [ToastShadow] and [ChipRest] are this ink at fixed alphas.
 */
val ShadowInk = Color(0xFF2E0147)

/** --su-grad-sunset · 135°, midpoint at 38%. Not an even three-stop ramp. */
val SunsetStops = listOf(0.00f to Color(0xFFFE6839), 0.38f to Color(0xFFD05976), 1.00f to Color(0xFF812AEC))

/** --su-grad-wordmark · 96°. The "Up" in the wordmark is filled with this, never flat violet. */
val WordmarkStops = listOf(0.00f to Color(0xFF812AEC), 0.55f to Color(0xFFD05976), 1.00f to Color(0xFFFE6839))

/**
 * `--su-grad-lilac`, the age-confirmation card's fill (SHOWUP-154).
 *
 * Lavender and not green, which is the whole point of the card: it asks the user to confirm a
 * value that is about to be locked, so it is a question rather than a success message.
 *
 * CORRECTED 15 September 2026, from #F2EAFB -> #F8F2FB to the values above. The old pair came from
 * the INLINE FALLBACK in the date-of-birth reference --
 *
 *     background: 'var(--su-grad-lilac, linear-gradient(180deg, #F2EAFB 0%, #F8F2FB 100%))'
 *
 * -- and a CSS fallback only applies when the variable is undefined. `tokens/colors_and_type.css`
 * defines `--su-grad-lilac` as `#F1E6FF -> #E8DCF5`, so the fallback never rendered anywhere and
 * the ported pair was a colour nothing in the design actually uses. The token file is the
 * authority the handoff names for exactly this ("../tokens/colors_and_type.css <- authoritative
 * token values"), and the prompts screen's saved card (SHOWUP-158) reads the variable with NO
 * fallback at all, so there is only one correct answer there.
 *
 * What visibly changes: the age-confirmation card on screen 04 gets slightly more saturated and
 * its gradient a little deeper. Nothing moves, and no other screen used this.
 */
val LilacStops = listOf(0.00f to Color(0xFFF1E6FF), 1.00f to Color(0xFFE8DCF5))

/**
 * `--su-grad-lilac` as a brush: the wash under every lilac pip and preview block.
 *
 * IT LIVED IN `MediaCards.kt` UNTIL A SECOND SCREEN NEEDED IT. The rule is that a shared primitive
 * never lives in a screen file, and the reason is on the record: `PillButton` sat in
 * `WelcomeShell.kt`, so whoever wrote the tutorial's CTA had no reason to open the sign-up flow
 * and wrote their own. The notifications ask (SHOWUP-162) is the second screen to want this wash,
 * which is the moment it moves.
 */
val LilacWash: Brush
    // VERTICAL, and that is the whole of the token: `linear-gradient(180deg, ...)`. The obvious
    // spelling is wrong -- `Brush.linearGradient(colorStops)` defaults to Offset.Zero ->
    // Offset.Infinite, which resolves to the drawing area's top-left -> bottom-right and is a 135
    // diagonal. Sunset IS 135 and reads correctly that way, which is what made this easy to carry
    // over; lilac is 180 and did not. iOS had it right on every surface.
    get() = Brush.verticalGradient(colorStops = LilacStops.toTypedArray())

/**
 * `--su-grad-blush`, 180deg (`tokens/colors_and_type.css`): Search's highlight card and Be sure's
 * header panel, and the grid's and Likes' surfaces after them -- four surfaces, one definition.
 */
val BlushStops = listOf(0.00f to Color(0xFFFDE7DC), 1.00f to Color(0xFFF5D9EA))

/** [BlushStops] as a brush, vertical for the same reason [LilacWash] is. */
val BlushWash: Brush
    get() = Brush.verticalGradient(colorStops = BlushStops.toTypedArray())

// ── added for the Premium grid and the Likes tab (SHOWUP-185, 186) ─────────────────────────────

/**
 * rgb(20,12,28), the plum-black the kit lays over photos and under floating surfaces: the grid
 * card's bottom scrim (0 at 42% to .66) and the undo toast's `0 8px 24px` shadow at .22. The No hours
 * today? dialog's shadow ([DialogShadow]) is this ink at .34.
 */
val PhotoInk = Color(0xFF140C1C)

/**
 * `showUpTone(100)`'s dark pill -- `rgba(20,28,24,.78)` with `#3DE08A` ink -- the Show-up count laid
 * over a photo (`ShowUpCount variant="dark"`, the grid card). The soft pill is [SuccessFg] on a
 * green tint.
 */
val ShowUpDarkFill = Color(0xC7141C18)
val ShowUpDarkInk = Color(0xFF3DE08A)

// ── added for the Search location gate (SHOWUP-188) ─────────────────────────────────────────────

/**
 * rgb(60,50,70), the slate the location radar is drawn in when location is off (`live={false}`,
 * the gate's B, C and D): rings at .22 / .14 / .08, the pin's fill at .55 and its outline at .65,
 * the slash at .55. Drawn as tints of this one value, as the live radar's are of [Purple].
 */
val RadarSlate = Color(0xFF3C3246)

/** rgba(245,243,240,.85), the muted pin's centre dot -- the live pin's is white. */
val RadarSlateDot = Color(0xD9F5F3F0)

// ── Type ─────────────────────────────────────────────────────────────────
//
// The .ttf files ship in ../../fonts/. Copy them into app/src/main/res/font/ under these exact
// names — Android resource names allow only lowercase and underscores:
//
//     Lora-Regular.ttf      ->  lora_regular.ttf
//     Lora-Bold.ttf         ->  lora_bold.ttf
//     Lora-Italic.ttf       ->  lora_italic.ttf
//     Lora-BoldItalic.ttf   ->  lora_bold_italic.ttf
//     Manrope-Medium.ttf    ->  manrope_medium.ttf
//     Manrope-SemiBold.ttf  ->  manrope_semibold.ttf
//     Manrope-Bold.ttf      ->  manrope_bold.ttf
//
// Referenced directly rather than falling back to FontFamily.Serif/SansSerif: the fallback compiled
// fine and rendered the wrong typeface with no warning, which is how a screen ships in the wrong
// font. If the files are missing this now fails to build — loudly.
val Lora = FontFamily(
    Font(R.font.lora_regular,     FontWeight.Normal),
    Font(R.font.lora_bold,        FontWeight.Bold),
    Font(R.font.lora_italic,      FontWeight.Normal, FontStyle.Italic),
    // Weight 500 italic — what the token file specifies for the emphasised word in a
    // headline. The handoff shipped 400 and 700 only; this cut was interpolated from those
    // two masters. See fonts/README-medium-italic.md. Replace if design ships a real one.
    Font(R.font.lora_mediumitalic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.lora_bold_italic, FontWeight.Bold,   FontStyle.Italic),
)

/**
 * Manrope, four weights.
 *
 * EXTRABOLD IS NOT DECORATION. The design's eyebrow labels are authored at `fontWeight: 800` and
 * ten call sites ask for [FontWeight.ExtraBold]; without the file, Compose matched them to the
 * nearest weight it had -- Bold, 700 -- and every one of them shipped a step light. iOS did the
 * same thing explicitly, mapping `.heavy` onto `manropeBold`, so the two platforms agreed with
 * each other and neither agreed with the design.
 *
 * Instanced at wght 800 from upstream Manrope 4.505; the three statics beside it are 4.504.
 * Checked before it was added rather than after: identical unitsPerEm, ascender, descender,
 * lineGap and cap height, and the same 742 glyphs, so nothing above a line of text moves.
 */
val Manrope = FontFamily(
    // 400 since 14 Oct 2026: the specs set body copy at 400, and with no Regular in the family
    // every such run resolved to Medium -- a step heavy and ~3% wide. See fonts/README-manrope-regular.md.
    Font(R.font.manrope_regular,   FontWeight.Normal),
    Font(R.font.manrope_medium,    FontWeight.Medium),
    Font(R.font.manrope_semibold,  FontWeight.SemiBold),
    Font(R.font.manrope_bold,      FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

/**
 * `text-transform: uppercase`, which Compose has no equivalent of.
 *
 * The design's eyebrow labels -- Manrope 800 at 10.5 with 0.08em tracking -- are authored in
 * sentence case and transformed by CSS. The ticket's copy section quotes them in sentence case
 * too, which is why the STRINGS stay that way and the transform happens here: the copy a verifier
 * checks against the ticket and the pixels a person sees are two different things, and baking the
 * capitals into the constant would make the first one lie.
 *
 * Two of these were already written in capitals by hand -- `MAIN` and `FOR EXAMPLE` -- which is
 * how the omission hid: some of the eyebrows looked right, so none of them looked wrong.
 *
 * [Locale.ROOT] rather than the device's: these are English product strings, and a Turkish locale
 * uppercases "i" to a dotted capital that is not in our font's Latin set.
 */
fun String.eyebrowCase(): String = uppercase(java.util.Locale.ROOT)
