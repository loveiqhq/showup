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
val Border = Color(0x1F1D1129)         // --liq-border      · rgba(29,17,41,.12)
val Danger = Color(0xFFFB323B)         // --liq-danger      · invalid borders, the ! glyph
val DangerFg = Color(0xFFB71F26)       // --liq-danger-fg   · error helper text
val DangerDigit = Color(0xFF7A1F26)    // mismatch digit colour — a one-off, not a token
val Lavender = Color(0xFFA78BFA)       // --liq-lavender-400

/** --su-grad-sunset · 135°, midpoint at 38%. Not an even three-stop ramp. */
val SunsetStops = listOf(0.00f to Color(0xFFFE6839), 0.38f to Color(0xFFD05976), 1.00f to Color(0xFF812AEC))

/** --su-grad-wordmark · 96°. The "Up" in the wordmark is filled with this, never flat violet. */
val WordmarkStops = listOf(0.00f to Color(0xFF812AEC), 0.55f to Color(0xFFD05976), 1.00f to Color(0xFFFE6839))

/**
 * `--su-grad-lilac`, the age-confirmation card's fill (SHOWUP-154).
 *
 * Lavender and not green, which is the whole point of the card: it asks the user to confirm a
 * value that is about to be locked, so it is a question rather than a success message.
 */
val LilacStops = listOf(0.00f to Color(0xFFF2EAFB), 1.00f to Color(0xFFF8F2FB))

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

val Manrope = FontFamily(
    Font(R.font.manrope_medium,   FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold,     FontWeight.Bold),
)
