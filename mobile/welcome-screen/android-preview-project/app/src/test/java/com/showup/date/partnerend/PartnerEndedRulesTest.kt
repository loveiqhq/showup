/*
 * PartnerEndedRulesTest.kt
 * ShowUp · Date 08: each PartnerScreen draws its own payload, and the layout values the three share are one set
 * (SHOWUP-199)
 */
package com.showup.date.partnerend

import androidx.compose.ui.unit.dp
import com.showup.date.PartnerScreen
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
        assertFalse(a.badgeSuccess); assertTrue(a.usesPhoto)
        assertEquals(listOf(BrandIcon.UserX, BrandIcon.MessageCircle, BrandIcon.ShieldCheck), a.rowIcons)
        assertEquals(3, a.rows.size); assertEquals(RowAccent.Orange, a.accent)

        val b = partnerEndedPayload(PartnerScreen.DateEndedDiscomfort)
        assertEquals(HeroTone.Blush, b.tone)
        assertEquals(28.dp, b.heroPadV); assertEquals(22.dp, b.heroPadH)
        assertEquals(16.dp, b.avatarGap); assertEquals(16.dp, b.rowsTop)
        assertEquals(BrandIcon.Hand, b.badgeIcon); assertEquals(15.dp, b.badgeIconSize); assertEquals(2.6f, b.badgeStroke)
        assertFalse(b.badgeSuccess); assertTrue(b.usesPhoto)
        assertEquals(listOf(BrandIcon.Hand, BrandIcon.MessageCircle, BrandIcon.ShieldCheck), b.rowIcons)
        assertEquals(3, b.rows.size); assertEquals(RowAccent.Orange, b.accent)

        val c = partnerEndedPayload(PartnerScreen.DateClosedPositive)
        assertEquals(HeroTone.Lilac, c.tone)
        assertEquals(30.dp, c.heroPadV); assertEquals(24.dp, c.heroPadH)
        assertEquals(18.dp, c.avatarGap); assertEquals(18.dp, c.rowsTop)
        assertEquals(BrandIcon.Check, c.badgeIcon); assertEquals(15.dp, c.badgeIconSize); assertEquals(3f, c.badgeStroke)
        assertTrue(c.badgeSuccess); assertFalse(c.usesPhoto)
        assertEquals(listOf(BrandIcon.Heart, BrandIcon.Search), c.rowIcons)
        assertEquals(2, c.rows.size); assertEquals(RowAccent.Violet, c.accent)
    }

    @Test
    fun `layout values shared by all three are equal`() {
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
}
