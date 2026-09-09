package com.showup.api

import com.showup.api.generated.api.ProfilesApi
import com.showup.api.generated.model.UpsertProfileDto
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Per-field profile visibility, over the wire (SHOWUP-154).
 *
 * WHAT THESE PROTECT
 *
 * The product decision is that hiding the age hides a VALUE: the user stays fully discoverable and
 * matchable. The backend has exactly one flag that sounds like it belongs here -- `isVisible` --
 * and it means "appears in discovery at all". Wiring the age control to it would silently remove
 * the user from discovery, which is the one outcome the decision forbids.
 *
 * So every test below asserts on `isVisible` even though none of them is about `isVisible`. That is
 * the point: the assertion that fails first, and loudest, if the two are ever confused.
 *
 * Assertions are made against bytes that crossed a socket, for the reason [GeneratedClientTest]
 * gives -- a generated client that compiles can still send the wrong body.
 */
class ProfileVisibilityTest {
    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    // Goes through ShowUpApi rather than a bare ApiClient: the converter configuration that makes
    // a partial update partial lives there, so a test that built its own client would prove the
    // wrong thing and pass while the app shipped destructive writes.
    private fun api(): ProfilesApi =
        ShowUpApi(
            baseUrl = server.url("/").toString(),
            tokens = InMemoryTokenStore(access = "t"),
        ).profiles

    /** The real ProfileDto shape, with every required field the contract declares. */
    private fun profile(hidden: String, isVisible: Boolean = true) = """
        {"id":"p1","displayName":"Leo","age":28,"gender":null,"lookingFor":null,
         "isVisible":$isVisible,"hiddenFields":$hidden,"isComplete":false,
         "verificationStatus":"none"}
    """.trimIndent()

    private fun json(body: String, code: Int = 200) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    @Test
    fun `checking the box sends hiddenFields and never mentions isVisible`() = runTest {
        server.enqueue(json(profile("""["age"]""")))

        api().updateProfile(UpsertProfileDto(hiddenFields = listOf("age")))

        val body = server.takeRequest().body.readUtf8()
        assertTrue("body should carry the hidden set, was: $body", body.contains("hiddenFields"))
        assertTrue("body should name age, was: $body", body.contains("age"))
        // The load-bearing assertion. An omitted key cannot change discovery eligibility; a key
        // present with any value can.
        assertFalse("isVisible must not appear in the body, was: $body", body.contains("isVisible"))
    }

    @Test
    fun `a hidden age comes back with the user still visible`() = runTest {
        server.enqueue(json(profile("""["age"]""")))

        val body = api().updateProfile(UpsertProfileDto(hiddenFields = listOf("age"))).body()

        assertNotNull("response should decode into the generated model", body)
        assertEquals(listOf("age"), body!!.hiddenFields)
        assertTrue("hiding the age must leave the user discoverable", body.isVisible)
        // The age is still on the wire for the owner, and still reaches matching. Hiding is a
        // rendering instruction, not redaction.
        assertEquals(28, body.age)
    }

    @Test
    fun `unchecking sends an explicit empty set, not an omitted key`() = runTest {
        server.enqueue(json("""[]""".let { profile(it) }))

        api().updateProfile(UpsertProfileDto(hiddenFields = emptyList()))

        val body = server.takeRequest().body.readUtf8()
        // An omitted key means "leave it alone" to the server, so unchecking has to send the empty
        // set explicitly or the choice never clears.
        assertTrue("empty set must be sent, was: $body", body.contains("hiddenFields"))
        assertFalse("isVisible must not appear in the body, was: $body", body.contains("isVisible"))
    }

    @Test
    fun `an invisible profile with a hidden age decodes both independently`() = runTest {
        // The two are orthogonal, and a client reading one must never infer the other.
        server.enqueue(json(profile("""["age"]""", isVisible = false)))

        val body = api().getProfile().body()

        assertNotNull(body)
        assertFalse(body!!.isVisible)
        assertEquals(listOf("age"), body.hiddenFields)
    }

    @Test
    fun `a profile with nothing hidden decodes as an empty set, not null`() = runTest {
        server.enqueue(json(profile("""[]""")))

        val body = api().getProfile().body()

        assertNotNull(body)
        assertEquals(emptyList<String>(), body!!.hiddenFields)
        assertTrue(body.isVisible)
    }
}
