package com.showup.welcome

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every country the picker offers must have a flag.
 *
 * The list comes from libphonenumber and the artwork from a separate set, so the two can disagree —
 * and the only symptom is a grey ISO chip that looks like a bug to whoever scrolls past it.
 */
class FlagCoverageTest {

    private val flagDir = File("src/main/assets/flags")

    @Test
    fun `every country offered has a flag`() {
        val have = flagDir.listFiles { f -> f.extension == "png" }
            .orEmpty().map { it.nameWithoutExtension.uppercase() }.toSet()
        val missing = COUNTRIES.filter { it.iso.uppercase() !in have }
            .map { "${it.iso} ${it.name} (${it.dial})" }
        assertTrue("no flag for:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun `no flag is shipped for a country that is not offered`() {
        val offered = COUNTRIES.map { it.iso.uppercase() }.toSet()
        val have = flagDir.listFiles { f -> f.extension == "png" }
            .orEmpty().map { it.nameWithoutExtension.uppercase() }
        val extra = have.filter { it !in offered }
        assertTrue("shipped but never shown: $extra", extra.isEmpty())
    }
}
