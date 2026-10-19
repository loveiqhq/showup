/*
 * MediaFailureRowTest.kt
 * ShowUp · a failed take shows one sentence, and only that sentence (SHOWUP-161)
 *
 * Asked for on 4 October 2026, from the emulator: the row read "That recording didn't save. Please
 * try again." with a second, smaller line under it -- "no valid data (8), 0ms, 0 bytes" -- which
 * was CameraX's own error code, drawn in debug builds as a diagnostic. It did its job, finding the
 * start-gap bug in one report, but the person reading that screen is judging the PRODUCT, and a
 * grey line of error code reads as part of what users will see.
 *
 * The diagnostic moved to logcat. This pins the screen side: whatever the build, the row renders
 * the sentence for its cause and no other text at all. Rendered rather than inferred from the
 * missing parameter, because the regression worth catching is somebody putting a helpful second
 * line back, and only a render sees that.
 */
package com.showup.profile

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MediaFailureRowTest {

    /** Every piece of text the row draws, in order, from the UNMERGED tree. */
    @OptIn(ExperimentalTestApi::class)
    private fun textsIn(cause: CaptureFailure2): List<String> {
        var texts = emptyList<String>()
        runComposeUiTest {
            setContent { MediaFailureRow(cause, Modifier.testTag(ROW)) }
            waitForIdle()
            fun walk(n: SemanticsNode): List<String> =
                n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                    n.children.flatMap { walk(it) }
            texts = walk(onNodeWithTag(ROW, useUnmergedTree = true).fetchSemanticsNode())
        }
        return texts
    }

    @Test
    fun `a take that saved nothing shows exactly the sentence`() {
        assertEquals(
            listOf("That recording didn't save. Please try again."),
            textsIn(CaptureFailure2.NothingRecorded),
        )
    }

    @Test
    fun `a recorder that never started shows exactly its sentence`() {
        assertEquals(
            listOf(MediaCopy.CAPTURE_NEVER_STARTED),
            textsIn(CaptureFailure2.NeverStarted),
        )
    }

    @Test
    fun `no error code ever reaches the screen`() {
        // The shape the old debug line had: a name, a number in brackets, a duration, a size.
        val code = Regex("""\(\d+\)|\d+ms|\d+ bytes""")
        for (cause in CaptureFailure2.values()) {
            textsIn(cause).forEach { text ->
                assertFalse("'$text' is a diagnostic, not copy", code.containsMatchIn(text))
            }
        }
    }

    private companion object {
        const val ROW = "media-failure-row"
    }
}
