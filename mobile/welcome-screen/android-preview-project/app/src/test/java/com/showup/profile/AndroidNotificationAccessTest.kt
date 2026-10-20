/*
 * AndroidNotificationAccessTest.kt
 * ShowUp · the real notification reader, below and from API 33
 *
 * NotificationRulesTest holds the rule as a pure function. This holds the READER to it: that it
 * actually hands the rule the app's Settings switch. Without that, the rule could say the right
 * thing about a switched-off Android 12 phone and the reader would still report `granted`, which
 * is exactly what it did until 20 October 2026 -- the permission reconciler never saw the switch
 * and the server went on pushing to a phone that showed nothing.
 *
 * The API level is the reader's own injected parameter, so the below-33 branch runs here on the
 * project's usual Robolectric SDK; the switch is Robolectric's notification manager.
 */
package com.showup.profile

import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidNotificationAccessTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    private fun switchNotifications(on: Boolean) {
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(on)
    }

    @Test
    fun `android 12 with notifications on in Settings reads granted`() {
        switchNotifications(on = true)
        assertEquals(PermissionStatus.Granted, AndroidNotificationAccess(context, sdkInt = 32).read())
    }

    @Test
    fun `android 12 with notifications switched off in Settings reads denied`() {
        switchNotifications(on = false)
        assertEquals(PermissionStatus.Denied, AndroidNotificationAccess(context, sdkInt = 32).read())
    }

    @Test
    fun `android 11 reads the same switch`() {
        switchNotifications(on = false)
        assertEquals(PermissionStatus.Denied, AndroidNotificationAccess(context, sdkInt = 30).read())
    }
}
