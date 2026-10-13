package com.showup.api

import com.showup.api.generated.model.DatingLanguage
import com.showup.api.generated.model.FlowPosition
import com.showup.api.generated.model.Gender
import com.showup.api.generated.model.UpsertProfileDto
import com.showup.profile.FlowPositionReporter
import com.showup.profile.ProfileDetailsRepository
import com.showup.profile.SavedDetails
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The "Share some details" answers and the saved flow position, over the wire (SHOWUP-165 to 173).
 *
 * Bytes that crossed a socket, through [ShowUpApi] -- the converter configuration that makes a
 * partial update partial lives there, for the reason [ProfileVisibilityTest] gives.
 *
 * ONE OF THESE EXISTS BECAUSE OF AN ANNOTATION. The generator typed the language list
 * `List<@Contextual DatingLanguage>`, and a contextual serializer that is not registered fails at
 * RUNTIME, not at compile time -- the first user to tick a language would have met it. So the
 * list is serialised here, through the real client, before anything relies on it.
 */
class ProfileDetailsWireTest {
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

    private fun api() = ShowUpApi(baseUrl = server.url("/").toString(), tokens = InMemoryTokenStore(access = "t"))

    private fun own(extra: String = "") = """
        {"id":"p1","displayName":"Leo","age":28,"gender":"man","lookingFor":null,
         "isVisible":true,"hiddenFields":["age"],"isComplete":true,"verificationStatus":"none",
         "heightCm":181,"orientation":"gay","datingLanguages":["german","spanish"],
         "education":"phd","religion":"muslim","politics":"middle",
         "activity":"active","diet":null,"alcohol":"socially","smoking":null,"rhythm":null,
         "social":"balanced","interests":["gym","wine"],"partnership":["single","divorced"],
         "family":[],"living":null,"familyAhead":["want_kids"],"partnershipAhead":null,
         "livingAhead":null$extra}
    """.trimIndent()

    private fun json(body: String, code: Int = 200) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    @Test
    fun `the language list serialises as plain section 1 strings, in the order given`() = runTest {
        server.enqueue(json(own()))
        api().profiles.updateProfile(
            UpsertProfileDto(
                datingLanguages = listOf(DatingLanguage.german, DatingLanguage.spanish),
                flowPosition = FlowPosition.dating_language,
            ),
        )
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"datingLanguages\":[\"german\",\"spanish\"]"))
        assertTrue(body, body.contains("\"flowPosition\":\"dating_language\""))
    }

    @Test
    fun `an answer, its visibility and the position go in ONE body, and nothing else does`() = runTest {
        server.enqueue(json(own()))
        api().profiles.updateProfile(
            UpsertProfileDto(
                gender = Gender.non_binary,
                hiddenFields = listOf("age", "gender"),
                flowPosition = FlowPosition.gender,
            ),
        )
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"gender\":\"non_binary\""))
        assertTrue(body, body.contains("\"hiddenFields\":[\"age\",\"gender\"]"))
        assertTrue(body, body.contains("\"flowPosition\":\"gender\""))
        // Not one other answer, and never `isVisible` -- hiding a field is not leaving discovery.
        listOf("heightCm", "orientation", "religion", "politics", "education", "isVisible").forEach {
            assertFalse("$it leaked into $body", body.contains("\"$it\""))
        }
    }

    @Test
    fun `a position report carries the position and nothing else -- a skip saves nothing`() = runTest {
        server.enqueue(json(own()))
        val stored = FlowPositionReporter(api()).report(FlowPosition.media_video)
        assertTrue(stored)
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("{\"flowPosition\":\"media_video\"}", request.body.readUtf8())
    }

    @Test
    fun `a failed position report answers false and never throws`() = runTest {
        server.enqueue(json("{}", code = 500))
        assertFalse(FlowPositionReporter(api()).report(FlowPosition.location))
    }

    @Test
    fun `the owner's view maps onto the saved answers`() = runTest {
        server.enqueue(json(own()))
        val saved = ProfileDetailsRepository(api()).load()
        assertEquals(
            SavedDetails(
                heightCm = 181, gender = "man", orientation = "gay",
                datingLanguages = listOf("german", "spanish"), education = "phd", religion = "muslim",
                politics = "middle", hiddenFields = setOf("age"),
                // Answered groups only: a null is "never answered" and has no entry.
                lifestyle = mapOf("activity" to "active", "alcohol" to "socially", "social" to "balanced"),
                interests = listOf("gym", "wine"),
                // An EMPTY family is an answer ("walked past it") and is kept; a null is not.
                life = mapOf(
                    "partnership" to listOf("single", "divorced"),
                    "family" to emptyList(),
                    "family_ahead" to listOf("want_kids"),
                ),
            ),
            saved,
        )
    }

    @Test
    fun `Interests sends the answered groups and the tags, and nothing it was not given`() = runTest {
        server.enqueue(json(own()))
        api().profiles.updateProfile(
            UpsertProfileDto(
                alcohol = com.showup.api.generated.model.Alcohol.socially,
                interests = listOf(
                    com.showup.api.generated.model.InterestTag.gym,
                    com.showup.api.generated.model.InterestTag.wine,
                ),
                flowPosition = FlowPosition.interests,
            ),
        )
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"alcohol\":\"socially\""))
        assertTrue(body, body.contains("\"interests\":[\"gym\",\"wine\"]"))
        assertTrue(body, body.contains("\"flowPosition\":\"interests\""))
        // An unanswered group is NOT SENT -- not even as null -- so it keeps its earlier value.
        listOf("activity", "diet", "smoking", "rhythm", "social", "partnership").forEach {
            assertFalse("$it leaked into $body", body.contains("\"$it\""))
        }
    }

    @Test
    fun `Life sends an unanswered question as an empty list -- it is not dropped as a default`() = runTest {
        server.enqueue(json(own()))
        api().profiles.updateProfile(
            UpsertProfileDto(
                partnership = listOf(com.showup.api.generated.model.LifePartnership.single),
                family = emptyList(),
                living = emptyList(),
                flowPosition = FlowPosition.life_now,
            ),
        )
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"partnership\":[\"single\"]"))
        assertTrue(body, body.contains("\"family\":[]"))
        assertTrue(body, body.contains("\"living\":[]"))
        assertTrue(body, body.contains("\"flowPosition\":\"life_now\""))
    }

    @Test
    fun `the living values travel as their full registry strings`() = runTest {
        // The generator strips the shared `living_` prefix from the CONSTANT names; the wire value
        // must still be the §4 string, or the server refuses it.
        server.enqueue(json(own()))
        api().profiles.updateProfile(
            UpsertProfileDto(living = listOf(com.showup.api.generated.model.LifeLiving.alone)),
        )
        assertTrue(server.takeRequest().body.readUtf8().contains("\"living\":[\"living_alone\"]"))
    }

    @Test
    fun `a value a newer server adds still decodes -- the answers are strings on the way out`() = runTest {
        server.enqueue(
            json(own().replace("\"religion\":\"muslim\"", "\"religion\":\"pastafarian\"")),
        )
        val saved = ProfileDetailsRepository(api()).load()
        assertEquals("pastafarian", saved?.religion)
    }

    @Test
    fun `a failed read is null, never a guess`() = runTest {
        server.enqueue(json("{}", code = 401))
        assertEquals(null, ProfileDetailsRepository(api()).load())
    }
}
