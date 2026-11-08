/*
 * ThreadScrollTest.kt
 * ShowUp · the thread keeps its end in sight when the keyboard shrinks it (SHOWUP-194)
 *
 * The keyboard takes the thread's height from below. With the scroll held from the top, the newest
 * messages slid out of sight: Read & reply, then a tap on the field, and the message being answered was
 * gone (the SHOWUP-194 review). A thread at its end stays at its end; one the reader scrolled up stays
 * where they left it.
 */
package com.showup.chat

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class ThreadScrollTest {

    private fun shrink(scrollUpFirst: Boolean): Pair<Int, Int> {
        var tall by mutableStateOf(500.dp)
        val state = ScrollState(0)
        var result = 0 to 0
        runComposeUiTest {
            setContent {
                Box(Modifier.height(tall)) {
                    ThreadScroll(state = state, contentPadding = PaddingValues(0.dp), messageCount = 20, footKey = null) {
                        repeat(20) { Box(Modifier.fillMaxWidth().height(60.dp)) }
                    }
                }
            }
            waitForIdle()
            if (scrollUpFirst) {
                runOnIdle { runBlocking { state.scrollTo(state.maxValue / 2) } }
                waitForIdle()
            }
            // The keyboard opens.
            tall = 250.dp
            waitForIdle()
            result = state.value to state.maxValue
        }
        return result
    }

    @Test
    fun `a thread at its end stays at its end when the keyboard shrinks it`() {
        val (value, max) = shrink(scrollUpFirst = false)
        assertTrue("the thread overflows", max > 0)
        assertEquals("still at the end", max, value)
    }

    @Test
    fun `a thread the reader scrolled up stays where they left it`() {
        val (value, max) = shrink(scrollUpFirst = true)
        assertTrue("not pulled to the end", value < max)
    }
}
