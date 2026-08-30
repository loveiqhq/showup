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
    Device("iPhone SE (1st gen) / iPod touch", 320, 568, 20, 0),
    Device("Galaxy Fold cover screen", 320, 686, 24, 24),
    Device("small Android (HD)", 360, 640, 24, 24),
    Device("Galaxy A / common Android", 360, 740, 24, 24),
    Device("iPhone 12 mini / 13 mini", 360, 780, 50, 34),
    Device("common modern Android", 360, 800, 24, 24),

    // ── the middle ──────────────────────────────────────────────────────────
    Device("iPhone SE (2nd/3rd) / 6 / 7 / 8", 375, 667, 20, 0, inAcceptanceCriteria = true),
    Device("iPhone X / XS / 11 Pro", 375, 812, 44, 34),
    Device("Pixel 4a / 5", 393, 851, 24, 24),
    Device("iPhone 12 / 13 / 14", 390, 844, 47, 34, inAcceptanceCriteria = true),
    Device("iPhone 15 / 16", 393, 852, 59, 34),
    Device("iPhone 16 Pro", 402, 874, 62, 34),

    // ── the large end ───────────────────────────────────────────────────────
    Device("Pixel 6 / 7", 411, 891, 24, 24),
    Device("Pixel 7 Pro / 8 Pro", 412, 915, 24, 24),
    Device("iPhone XR / 11", 414, 896, 48, 34),
    Device("iPhone 12/13/14 Pro Max", 428, 926, 47, 34),
    Device("iPhone 15/16 Pro Max", 430, 932, 59, 34, inAcceptanceCriteria = true),
    Device("iPhone 16 Pro Max", 440, 956, 62, 34),
)
