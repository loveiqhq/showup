/*
 * DateProfileViewModelTest.kt
 * ShowUp · "See {name}'s full profile", read-only (SHOWUP-193)
 *
 * The match's profile on Search's own screen with nothing to decide: it fires `screen_viewed`
 * `search_profile` with the referrer `date_screen`, and no other Search event -- browsing its
 * photos included.
 */
package com.showup.date

import com.showup.analytics.RecordingAnalytics
import com.showup.api.CallFailure
import com.showup.api.Outcome
import com.showup.search.SearchSamples
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DateProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val analytics = RecordingAnalytics()
    private val store = FakeDateStore()

    private fun vm() = DateProfileViewModel(store, analytics, players = null, zone = { ZoneId.of("Europe/Berlin") })

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `opening reads the match's profile and fires screen_viewed for search_profile from date_screen, nothing else`() = runTest(dispatcher) {
        store.profile = Outcome.Ok(SearchSamples.FULL)
        val vm = vm()
        vm.open()
        assertTrue("loading until the read lands", vm.state.value!!.loading)
        advanceUntilIdle()
        assertEquals(SearchSamples.FULL, vm.state.value!!.current)
        assertEquals(listOf("screen_viewed"), analytics.names())
        val view = analytics.of("screen_viewed").single().properties
        assertEquals("search_profile", view["screen_id"])
        assertEquals("date_screen", view["referrer_screen_id"])
    }

    @Test
    fun `a failed read shows the profile's failed state, and Try again reads again with no second view`() = runTest(dispatcher) {
        store.profile = Outcome.Failed(CallFailure.Server)
        val vm = vm()
        vm.open()
        advanceUntilIdle()
        assertEquals(CallFailure.Server, vm.state.value!!.failure)
        assertNull(vm.state.value!!.current)

        store.profile = Outcome.Ok(SearchSamples.FULL)
        vm.retry()
        assertTrue(vm.state.value!!.loading)
        advanceUntilIdle()
        assertEquals(SearchSamples.FULL, vm.state.value!!.current)
        assertEquals("one visit, one view", 1, analytics.of("screen_viewed").size)
    }

    @Test
    fun `its photos open and page with no event`() = runTest(dispatcher) {
        store.profile = Outcome.Ok(SearchSamples.FULL)
        val vm = vm()
        vm.open()
        advanceUntilIdle()
        vm.openPhoto(1)
        assertEquals(1, vm.state.value!!.viewerAt)
        vm.viewerPage(2)
        assertEquals(2, vm.state.value!!.viewerAt)
        vm.closeViewer()
        assertNull(vm.state.value!!.viewerAt)
        assertEquals(listOf("screen_viewed"), analytics.names())
    }

    @Test
    fun `closing clears it, and a second open is a new visit`() = runTest(dispatcher) {
        store.profile = Outcome.Ok(SearchSamples.FULL)
        val vm = vm()
        vm.open()
        advanceUntilIdle()
        vm.open()
        assertEquals("an open profile is not opened twice", 1, analytics.of("screen_viewed").size)
        vm.close()
        assertNull(vm.state.value)
        vm.open()
        advanceUntilIdle()
        assertEquals(2, analytics.of("screen_viewed").size)
    }
}
