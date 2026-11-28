/*
 * PartnerEndedRulesTest.kt
 * ShowUp · Date 08: each PartnerScreen draws its own payload, and the layout values the three share are one set
 * (SHOWUP-199)
 */
package com.showup.date.partnerend

import androidx.compose.ui.unit.dp
import com.showup.date.PartnerScreen
import com.showup.designsystem.Orange
import com.showup.designsystem.Purple
import com.showup.designsystem.SuccessFg
import com.showup.profile.ProfileScreen
import com.showup.welcome.BrandIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartnerEndedRulesTest {
    @Test
    fun `each screen maps to its payload`() {
        val a = partnerEndedPayload(PartnerScreen.YouNoShowed)
        assertEquals(HeroTone.Blush, a.tone)
        assertEquals(30.dp, a.heroPadV); assertEquals(24.dp, a.heroPadH)
        assertEquals(18.dp, a.avatarGap); assertEquals(18.dp, a.rowsTop)
        assertEquals(BrandIcon.Clock, a.badgeIcon); assertEquals(16.dp, a.badgeIconSize); assertEquals(2.6f, a.badgeStroke)
        assertEquals(PartnerBadgeTone.Orange, a.badgeTone); assertTrue(a.usesPhoto)
        assertEquals(listOf(BrandIcon.UserX, BrandIcon.MessageCircle, BrandIcon.ShieldCheck), a.rowIcons)
        assertEquals(3, a.rows.size); assertEquals(RowAccent.Orange, a.accent)

        val b = partnerEndedPayload(PartnerScreen.DateEndedDiscomfort)
        assertEquals(HeroTone.Blush, b.tone)
        assertEquals(28.dp, b.heroPadV); assertEquals(22.dp, b.heroPadH)
        assertEquals(16.dp, b.avatarGap); assertEquals(16.dp, b.rowsTop)
        assertEquals(BrandIcon.Hand, b.badgeIcon); assertEquals(15.dp, b.badgeIconSize); assertEquals(2.6f, b.badgeStroke)
        assertEquals(PartnerBadgeTone.Orange, b.badgeTone); assertTrue(b.usesPhoto)
        assertEquals(listOf(BrandIcon.Hand, BrandIcon.MessageCircle, BrandIcon.ShieldCheck), b.rowIcons)
        assertEquals(3, b.rows.size); assertEquals(RowAccent.Orange, b.accent)

        val c = partnerEndedPayload(PartnerScreen.DateClosedPositive)
        assertEquals(HeroTone.Lilac, c.tone)
        assertEquals(30.dp, c.heroPadV); assertEquals(24.dp, c.heroPadH)
        assertEquals(18.dp, c.avatarGap); assertEquals(18.dp, c.rowsTop)
        assertEquals(BrandIcon.Check, c.badgeIcon); assertEquals(15.dp, c.badgeIconSize); assertEquals(3f, c.badgeStroke)
        assertEquals(PartnerBadgeTone.Success, c.badgeTone); assertFalse(c.usesPhoto)
        assertEquals(listOf(BrandIcon.Heart, BrandIcon.Search), c.rowIcons)
        assertEquals(2, c.rows.size); assertEquals(RowAccent.Violet, c.accent)

        val d = partnerEndedPayload(PartnerScreen.DateTimedOut)
        assertEquals(HeroTone.Lilac, d.tone)
        assertEquals(30.dp, d.heroPadV); assertEquals(24.dp, d.heroPadH)
        assertEquals(18.dp, d.avatarGap); assertEquals(18.dp, d.rowsTop)
        assertEquals(BrandIcon.Clock, d.badgeIcon); assertEquals(15.dp, d.badgeIconSize); assertEquals(2.6f, d.badgeStroke)
        assertEquals(PartnerBadgeTone.Violet, d.badgeTone); assertTrue(d.usesPhoto)
        assertEquals(listOf(BrandIcon.Heart, BrandIcon.Search), d.rowIcons)
        assertEquals(2, d.rows.size); assertEquals(RowAccent.Violet, d.accent)
        assertEquals(ProfileScreen.DateTimedOut, partnerEndedScreenId(PartnerScreen.DateTimedOut))
    }

    @Test
    fun `layout values shared by all four are equal`() {
        assertEquals(28.dp, PartnerEndedLayout.HeroRadius)
        assertEquals(76.dp, PartnerEndedLayout.AvatarSize)
        assertEquals(30.dp, PartnerEndedLayout.BadgeSize)
        assertEquals(36.dp, PartnerEndedLayout.TileSize)
        assertEquals(12.dp, PartnerEndedLayout.TileRadius)
        assertEquals(10.dp, PartnerEndedLayout.RowGap)
        PartnerScreen.entries.forEach { s ->
            val p = partnerEndedPayload(s)
            assertEquals(p.rowIcons.size, p.rows.size)
        }
    }

    @Test
    fun `the badge disc takes its colour from the tone, one token each`() {
        assertEquals(Orange, partnerBadgeColor(PartnerBadgeTone.Orange))
        assertEquals(SuccessFg, partnerBadgeColor(PartnerBadgeTone.Success))
        assertEquals(Purple, partnerBadgeColor(PartnerBadgeTone.Violet))
    }
}
