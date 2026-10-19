/*
 * PermissionLabelTest.kt
 * ShowUp · no machine name ever reaches a sentence (SHOWUP-161)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE BUG THIS EXISTS FOR
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * A real user on a real Pixel was shown:
 *
 *     "Android.permission-group.UNDEFINED access is off. Turn on
 *      Android.permission-group.UNDEFINED in Settings to film."
 *
 * `PermissionInfo.group` has been deprecated since API 29 and now answers
 * `android.permission-group.UNDEFINED`. Resolving THAT gives a group with no label resource, and
 * `PackageItemInfo.loadLabel` falls back to the item's own `name` rather than throwing -- so the
 * `runCatching` wrapped around it never fired. The leading capital was ours, from an `uppercase`
 * meant to tidy a word.
 *
 * WHAT THAT MAKES THIS SUITE ABOUT. Not the Android API, which cannot be exercised here anyway:
 * it is about the shape of the failure. The platform returns a machine name whenever it has no
 * human one, and a machine name is a perfectly valid String that formats into copy without
 * complaint. Nothing but an explicit test of the VALUE can catch it, which is why the check is a
 * plain function and why these are plain assertions.
 */
package com.showup.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionLabelTest {

    // ── what the screenshot showed ──────────────────────────────────────────

    @Test
    fun `the exact string a user was shown is refused`() {
        assertEquals(
            "Camera",
            usablePermissionLabel("android.permission-group.UNDEFINED", "Camera"),
        )
    }

    @Test
    fun `every shape of machine name is refused`() {
        // The UNDEFINED group is the one the platform returns today. The others are the same
        // failure from a different vendor or a different API level, and none of them is a name
        // anybody typed for a person to read.
        val machineNames = listOf(
            "android.permission-group.UNDEFINED",
            "android.permission-group.CAMERA",
            "android.permission.CAMERA",
            "com.oem.permission-group.CAMERA",
            "android.permission-group.MICROPHONE",
        )
        for (name in machineNames) {
            assertEquals("'$name' must never reach the copy", "Microphone",
                usablePermissionLabel(name, "Microphone"))
        }
    }

    @Test
    fun `a description is refused, because it is not a row name`() {
        // `PermissionInfo.loadLabel` for CAMERA is a sentence about what the app may do. It would
        // arrive here if somebody "fixed" the group lookup by falling back to the permission, and
        // "take pictures and record video access is off" is not a sentence.
        assertEquals("Camera", usablePermissionLabel("take pictures and record video", "Camera"))
        assertEquals(
            "Microphone",
            usablePermissionLabel("record audio with the microphone at any time", "Microphone"),
        )
    }

    @Test
    fun `nothing at all is refused`() {
        assertEquals("Camera", usablePermissionLabel(null, "Camera"))
        assertEquals("Camera", usablePermissionLabel("", "Camera"))
        assertEquals("Camera", usablePermissionLabel("   ", "Camera"))
    }

    // ── what the platform is allowed to override ────────────────────────────

    /**
     * THE WHOLE POINT OF ASKING THE PLATFORM, and the reason this is not just a hard-coded word.
     *
     * The ticket is explicit: "Row labels in the blocked strings are replaced by the platform's
     * own label -- never hard-code Camera or Microphone if the OS calls it something else."
     * Localised builds and OEM skins both rename these, and a German phone saying "Camera access
     * is off" is the defect this function must not introduce while fixing the other one.
     */
    @Test
    fun `a real localised label is used, not ours`() {
        assertEquals("Kamera", usablePermissionLabel("Kamera", "Camera"))
        assertEquals("Mikrofon", usablePermissionLabel("Mikrofon", "Microphone"))
        assertEquals("Appareil photo", usablePermissionLabel("Appareil photo", "Camera"))
        assertEquals("Cámara", usablePermissionLabel("Cámara", "Camera"))
    }

    @Test
    fun `a two word row name is a row name`() {
        // Android 13 calls the media row "Photos and videos". Three words, still a row.
        assertEquals("Photos and videos", usablePermissionLabel("Photos and videos", "Photos"))
    }

    @Test
    fun `the label is capitalised for the start of a sentence`() {
        // The copy opens with it -- "Camera access is off" -- so a lower-case platform label
        // would start the sentence in lower case.
        assertEquals("Camera", usablePermissionLabel("camera", "Camera"))
        assertEquals("Kamera", usablePermissionLabel("kamera", "Camera"))
    }

    @Test
    fun `surrounding whitespace is not a reason to refuse a good label`() {
        assertEquals("Kamera", usablePermissionLabel("  Kamera  ", "Camera"))
    }

    // ── the sentence itself ─────────────────────────────────────────────────

    /**
     * The copy is built by interpolation, so the label lands in it twice.
     *
     * Asserted end to end rather than trusting the function alone, because the defect the user
     * saw was a SENTENCE, and a sentence is what has to be right.
     */
    @Test
    fun `the blocked sentence reads as English whatever the platform answers`() {
        for (platform in listOf(null, "", "android.permission-group.UNDEFINED", "Kamera")) {
            val label = usablePermissionLabel(platform, "Camera")
            val sentence = "$label access is off. Turn on $label in Settings to film."
            assertEquals(
                "a machine name reached the copy: $sentence",
                false,
                sentence.contains("permission") || sentence.contains("."+ "permission-group"),
            )
            // And it never opens in lower case, whichever branch produced it.
            assertEquals(true, sentence.first().isUpperCase())
        }
    }
}
