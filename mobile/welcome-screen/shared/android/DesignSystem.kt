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
val EyebrowBg = Color(0x29A78BFA)      // rgba(167,139,250,.16) — eyebrow pill fill

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
    Font(R.font.lora_bold_italic, FontWeight.Bold,   FontStyle.Italic),
)

val Manrope = FontFamily(
    Font(R.font.manrope_medium,   FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold,     FontWeight.Bold),
)
