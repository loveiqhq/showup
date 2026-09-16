/*
 * VerifyLockoutTest.kt
 * ShowUp · what the code screen does once the server has stopped listening
 *
 * WHY THIS EXISTS
 *
 * The server has refused a sixth attempt against one code since Epic 2 -- `otp.service.ts` and
 * `email-otp.service.ts` both read `OTP_MAX_ATTEMPTS` and throw the same message. The screen never
 * knew. A user who mistyped five times was shown "That code didn't match. Try again." and invited
 * to keep trying something that could no longer succeed, with the real recovery -- a new code --
 * unmentioned.
 *
 * These assert the three things that make the state honest: the message names it, the control that
 * cannot work is off, and the one that can is on. Source checks in verify-welcome.py pin the
 * strings and the expressions; this pins the rendered behaviour.
 */
package com.showup.welcome

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// A real screen size, or nothing lays out and every assertion passes vacuously. Learned the hard
// way on ProfileBasicsTest, where a click did nothing until the qualifier was added.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class VerifyLockoutTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the lockout message replaces the mismatch one`() {
        rule.setContent {
            VerifyCodeScreen(digits = "480000", mismatch = true, lockedOut = true)
        }
        rule.onNodeWithText(VerifyCopy.LOCKED_OUT).assertIsDisplayed()
        // Both are true at the cap -- the last submit WAS a mismatch -- so this asserts the
        // precedence, not just the presence. "Try again" is the wrong instruction to leave up.
        rule.onNodeWithText(VerifyCopy.MISMATCH).assertDoesNotExist()
    }

    @Test
    fun `the verify button is off, because the server would refuse it`() {
        rule.setContent {
            VerifyCodeScreen(digits = "480000", mismatch = true, lockedOut = true)
        }
        rule.onNodeWithText("Verify code").assertIsNotEnabled()
    }

    @Test
    fun `a full code is still submittable before the cap`() {
        // The guard must be the cap, not the presence of a mismatch -- otherwise one wrong code
        // would end the flow.
        rule.setContent {
            VerifyCodeScreen(digits = "480000", mismatch = true, lockedOut = false)
        }
        rule.onNodeWithText("Verify code").assertIsEnabled()
    }

    @Test
    fun `the resend is live even with the clock still running`() {
        // The only exit. A countdown with no working action is a dead end, so lockout releases
        // the cooldown regardless of how much of it is left.
        rule.setContent {
            VerifyCodeScreen(digits = "480000", mismatch = true, lockedOut = true, cooldownSeconds = 45)
        }
        rule.onNodeWithText("Send a new code").assertIsDisplayed()
    }

    @Test
    fun `the countdown shows a minute correctly at the sixty second cooldown`() {
        // "0:%02d" was hard-coded until the cooldown moved from 30 to 60, at which point the first
        // tick read "0:60".
        rule.setContent { VerifyCodeScreen(cooldownSeconds = 60) }
        rule.onNodeWithText("Send a new code in 1:00").assertIsDisplayed()
    }
}
