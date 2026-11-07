/*
 * Devices.kt
 * ShowUp · the phones the app has to fit on
 *
 * Sizes are in dp (Android) / points (iOS), which are the same unit for layout purposes -- density
 * is what differs, and density does not change what fits.
 *
 * [safeTop] and [safeBottom] matter as much as the size. A screen is not 844dp of usable space: the
 * status bar and the gesture bar take a bite out of both ends, and it is precisely the content
 * pushed into those bites that gets clipped on a real phone. The tests render into the SAFE area,
 * so "fits" here means "fits where the user can actually see it".
 *
 * The three sizes the acceptance criteria name -- 375x667, 390x844, 430x932 -- are all present and
 * marked, so the ticket's own evidence set is a subset of this.
 */
package com.showup.fit

data class Device(
    val name: String,
    val width: Int,
    val height: Int,
    val safeTop: Int,
    val safeBottom: Int,
    val inAcceptanceCriteria: Boolean = false,
) {
    /** Usable height once the system bars have taken theirs. */
    val safeHeight: Int get() = height - safeTop - safeBottom
    override fun toString() = "$name (${width}x$height)"
}

/**
 * Ordered smallest-first, because the small ones are where things break and a failure list that
 * starts with the tightest phone is the one that reads usefully.
 */
val DEVICES: List<Device> = listOf(
    // ── the tight end ───────────────────────────────────────────────────────
    //
    // The 320x568 iPhone SE (1st gen) was here and is gone: it cannot run iOS 17, which is the
    // minimum as of 2026-09-01, and no Android device at API 30 is that narrow. Testing a phone
    // the app will not install on produces findings nobody can act on, which is how a useful
    // report turns into noise.
    //
    // The smallest supported screen is now 375x667 -- the iPhone SE (3rd gen), which the product
    // side named explicitly as the floor.
    Device("Galaxy Fold cover screen", 320, 686, 24, 24),
    Device("small Android (HD)", 360, 640, 24, 24),
    // THREE-BUTTON NAVIGATION, the tightest real Android. Every other Android row assumes the 24 dp
    // gesture bar, but the button bar is 48 dp and many Samsungs ship with it on -- so a small phone
    // with buttons has 24 dp less than any row above it. Added 22 Oct 2026 (Search 01 device audit).
    Device("small Android, 3-button nav", 360, 640, 24, 48),
    Device("Galaxy A / common Android", 360, 740, 24, 24),
    Device("Galaxy A, 3-button nav", 360, 740, 24, 48),
    // 360 x 780 is a real Android size. It was labelled "iPhone 12 mini / 13 mini" until the 22 Oct
    // 2026 audit: those phones report 375 x 812 points (row below), not 360 x 780. The tall insets
    // stay -- stricter than an Android's, and the size has always been measured with them.
    Device("compact 360 x 780, tall insets", 360, 780, 50, 34),
    Device("common modern Android", 360, 800, 24, 24),

    // ── the middle ──────────────────────────────────────────────────────────
    Device("iPhone SE (3rd gen)", 375, 667, 20, 0, inAcceptanceCriteria = true),
    Device("iPhone X / XS / 11 Pro", 375, 812, 44, 34),
    Device("iPhone 12 mini / 13 mini", 375, 812, 50, 34),
    // The 384 dp class (1080 px at 2.8125): nothing between 360 and 390 was measured before.
    Device("Galaxy S24 / S25", 384, 832, 24, 24),
    Device("iPhone 12 / 13 / 14", 390, 844, 47, 34, inAcceptanceCriteria = true),
    Device("Pixel 4a / 5", 393, 851, 24, 24),
    Device("iPhone 15 / 16", 393, 852, 59, 34),
    Device("iPhone 16 Pro / 17 / 17 Pro", 402, 874, 62, 34),

    // ── the large end ───────────────────────────────────────────────────────
    Device("Pixel 6 / 7", 411, 891, 24, 24),
    Device("Pixel 7 Pro / 8 Pro", 412, 915, 24, 24),
    // The tallest common phone (22:9). Tall is where a flexible spacer shows how it distributes.
    Device("Galaxy Z Flip 5 / 6", 412, 1006, 24, 24),
    Device("iPhone XR / 11", 414, 896, 48, 34),
    // A top inset of 68 is the conservative end for the Air's Dynamic Island: less room, not more.
    Device("iPhone Air", 420, 912, 68, 34),
    Device("iPhone 12/13/14 Pro Max", 428, 926, 47, 34),
    Device("iPhone 15/16 Pro Max", 430, 932, 59, 34, inAcceptanceCriteria = true),
    Device("iPhone 16 / 17 Pro Max", 440, 956, 62, 34),
)

/**
 * Real screens [DEVICES] does not hold, measured by the Date screen first (the 4 Nov 2026 device
 * audit). Not in [DEVICES] because every suite would then fail on them at once, and whether the other
 * screens fit them is a finding to report to the product side, not one to break CI with mid-ticket.
 *
 * Settings and postures, not models -- each is a size a supported phone really shows:
 */
val EDGE_DEVICES: List<Device> = listOf(
    // DISPLAY ZOOM on the iPhone SE (2nd / 3rd gen): 320 x 568 points, the original SE's size again --
    // the narrowest AND shortest screen a supported iPhone shows. An Android at its largest display
    // size reaches the same 320 dp floor.
    Device("iPhone SE, Display Zoom", 320, 568, 20, 0),
    // SPLIT SCREEN: MainActivity is resizeable, so a phone's top half is a real window.
    Device("split screen, top half", 360, 380, 24, 0),
    // UNFOLDED FOLDABLES keep this portrait app full size (the manifest's restricted-resizability
    // opt-out): ~700 dp wide on a Fold 5 / 6 class inner screen, 984 x 1092 on the Fold 7 at its 2.0
    // density. Estimated from published pixels and densities, not read off a device.
    Device("unfolded foldable, Fold 5 / 6 class", 700, 840, 24, 24),
    Device("unfolded foldable, Fold 7", 984, 1092, 24, 24),
)
