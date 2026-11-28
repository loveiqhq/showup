/*
 * PartnerEndedRules.kt
 * ShowUp · Date 08: which payload each [PartnerScreen] draws, and every number that differs between them (SHOWUP-199)
 *
 * The four frames share one layout; only these values change (spec §4). The tests pin them so a payload cannot
 * drift into another's layout.
 */
package com.showup.date.partnerend

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.showup.date.PartnerScreen
import com.showup.date.cancel.Run
import com.showup.designsystem.Orange
import com.showup.designsystem.Purple
import com.showup.designsystem.SuccessFg
import com.showup.profile.ProfileScreen
import com.showup.welcome.BrandIcon

enum class HeroTone { Blush, Lilac }

/** The row accent: orange for the two accountability frames, violet for the thank-you. */
enum class RowAccent { Orange, Violet }

/** The badge disc's colour: orange for the two accountability frames, green for C's check, violet for the clock of D. */
enum class PartnerBadgeTone { Orange, Success, Violet }

/** The badge disc's fill for [tone]: orange, the success green, or violet (the `Purple` token). */
fun partnerBadgeColor(tone: PartnerBadgeTone): Color = when (tone) {
    PartnerBadgeTone.Orange -> Orange
    PartnerBadgeTone.Success -> SuccessFg
    PartnerBadgeTone.Violet -> Purple
}

/** What stays equal across the four payloads. */
object PartnerEndedLayout {
    val HeroRadius = 28.dp
    val AvatarSize = 76.dp
    val BadgeSize = 30.dp
    val TileSize = 36.dp
    val TileRadius = 12.dp
    val RowGap = 10.dp
}

data class PartnerEndedPayload(
    val tone: HeroTone,
    val heroPadH: Dp,
    val heroPadV: Dp,
    val avatarGap: Dp,
    /** The match's photo (A, B, D), or (C has none) the white disc with the violet heart. */
    val usesPhoto: Boolean,
    val badgeIcon: BrandIcon,
    val badgeIconSize: Dp,
    val badgeStroke: Float,
    val badgeTone: PartnerBadgeTone,
    val rowsTop: Dp,
    val rowIcons: List<BrandIcon>,
    val rows: List<List<Run>>,
    val accent: RowAccent,
)

/** The §11 screen each payload reports its `screen_viewed` as. */
fun partnerEndedScreenId(screen: PartnerScreen): ProfileScreen = when (screen) {
    PartnerScreen.YouNoShowed -> ProfileScreen.DatePartnerEndedNoShow
    PartnerScreen.DateEndedDiscomfort -> ProfileScreen.DatePartnerEndedUncomfortable
    PartnerScreen.DateClosedPositive -> ProfileScreen.DatePartnerEndedMet
    PartnerScreen.DateTimedOut -> ProfileScreen.DateTimedOut
}

fun partnerEndedPayload(screen: PartnerScreen): PartnerEndedPayload = when (screen) {
    PartnerScreen.YouNoShowed -> PartnerEndedPayload(
        tone = HeroTone.Blush, heroPadH = 24.dp, heroPadV = 30.dp, avatarGap = 18.dp, usesPhoto = true,
        badgeIcon = BrandIcon.Clock, badgeIconSize = 16.dp, badgeStroke = 2.6f, badgeTone = PartnerBadgeTone.Orange,
        rowsTop = 18.dp,
        rowIcons = listOf(BrandIcon.UserX, BrandIcon.MessageCircle, BrandIcon.ShieldCheck),
        rows = PartnerEndedCopy.A_ROWS, accent = RowAccent.Orange,
    )
    PartnerScreen.DateEndedDiscomfort -> PartnerEndedPayload(
        tone = HeroTone.Blush, heroPadH = 22.dp, heroPadV = 28.dp, avatarGap = 16.dp, usesPhoto = true,
        badgeIcon = BrandIcon.Hand, badgeIconSize = 15.dp, badgeStroke = 2.6f, badgeTone = PartnerBadgeTone.Orange,
        rowsTop = 16.dp,
        rowIcons = listOf(BrandIcon.Hand, BrandIcon.MessageCircle, BrandIcon.ShieldCheck),
        rows = PartnerEndedCopy.B_ROWS, accent = RowAccent.Orange,
    )
    PartnerScreen.DateClosedPositive -> PartnerEndedPayload(
        tone = HeroTone.Lilac, heroPadH = 24.dp, heroPadV = 30.dp, avatarGap = 18.dp, usesPhoto = false,
        badgeIcon = BrandIcon.Check, badgeIconSize = 15.dp, badgeStroke = 3f, badgeTone = PartnerBadgeTone.Success,
        rowsTop = 18.dp,
        rowIcons = listOf(BrandIcon.Heart, BrandIcon.Search),
        rows = PartnerEndedCopy.C_ROWS, accent = RowAccent.Violet,
    )
    // D is C's rows and anatomy with A's photo and a violet clock (spec R8); no layout value is its own.
    PartnerScreen.DateTimedOut -> PartnerEndedPayload(
        tone = HeroTone.Lilac, heroPadH = 24.dp, heroPadV = 30.dp, avatarGap = 18.dp, usesPhoto = true,
        badgeIcon = BrandIcon.Clock, badgeIconSize = 15.dp, badgeStroke = 2.6f, badgeTone = PartnerBadgeTone.Violet,
        rowsTop = 18.dp,
        rowIcons = listOf(BrandIcon.Heart, BrandIcon.Search),
        rows = PartnerEndedCopy.D_ROWS, accent = RowAccent.Violet,
    )
}
