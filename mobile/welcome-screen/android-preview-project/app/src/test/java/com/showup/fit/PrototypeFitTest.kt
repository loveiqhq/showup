package com.showup.fit

import com.showup.welcome.StartupScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PrototypeFitTest {

    @Test
    fun `startup across every device`() {
        val t0 = System.currentTimeMillis()
        val all = DEVICES.flatMap { d -> measureFit(d, "Startup") { StartupScreen() } }
        println("=== PROTOTYPE: ${DEVICES.size} devices in ${System.currentTimeMillis() - t0}ms ===")
        if (all.isEmpty()) println("no violations") else all.forEach { println("  $it") }
        println("=== END ===")
    }
}
