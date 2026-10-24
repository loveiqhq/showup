/*
 * SearchSamples.kt
 * ShowUp · the reference's two profiles, as the app's own types (SHOWUP-184)
 *
 * `SEARCH_V2_PROFILE` (every section) and `SEARCH_V2_PROFILE_MIN` (only the mandatory fields: age
 * hidden, four photos, one prompt, motivation, one vibe, tomorrow, no dates yet) from
 * `screen-search-profile-reference.jsx`, with the reference's display strings read back to the ids
 * the server sends -- so a sample drawn here goes through exactly the label tables a real profile
 * does. Previews, the fit sweep, the evidence frames and the dev store all read these.
 *
 * Photos are empty URLs: the slot draws the kit's placeholder, as the sheet does.
 */
package com.showup.search

import java.time.Instant
import java.time.ZoneId

object SearchSamples {
    /** The zone the samples' meet hour reads `19:00` in. */
    val ZONE: ZoneId = ZoneId.of("Europe/Berlin")

    /** 19:00 in [ZONE] on 22 Oct 2026. */
    val MEET_AT: Instant = Instant.parse("2026-10-22T17:00:00Z")

    /** The viewer: their Check-In vibes and profile interests, for the overlap (`SEARCH_VIEWER`). */
    val VIEWER = SearchViewer(
        vibes = setOf("coffee_chat", "sunset", "no_rush"),
        // The reference's "Climbing" is not a §3 tag; bouldering stands in, shared with nobody here.
        interests = setOf("hiking", "live_music", "reading", "board_games", "bouldering"),
    )

    val FULL = SearchProfile(
        userId = "sample-leonie",
        name = "Leonie",
        age = 28,
        distanceKm = 1,
        district = "Neukölln, Berlin",
        meetAt = MEET_AT,
        meetDay = "today",
        attendedCount = 4,
        motivation = "long_term",
        vibes = listOf("coffee_chat", "sunset", "deep_talk", "playful", "no_rush"),
        uplift = "I'll take you on a rafting tour on Wannsee — picnic included",
        photos = List(6) { "" },
        prompts = listOf(
            SearchPrompt(
                "first_date_usually",
                "Talk about anything real. Not jobs, not pets, not the weather. The thing actually on your mind this week. Bring it. I'll listen.",
            ),
            SearchPrompt("cross_town_for", "A proper conversation. An old cinema. The 8pm walk after a long day."),
            SearchPrompt("hill_to_die_on", "Showing up. Cancelling last minute isn't a scheduling problem, it's an answer."),
        ),
        voice = SearchMedia(url = "", durationMs = 14_000, promptId = "relaxing_sound"),
        video = SearchMedia(url = "", durationMs = 10_000, promptId = "ideal_sunny_morning"),
        essentials = EssentialAnswers(
            heightCm = 172,
            gender = "woman",
            orientation = "straight",
            datingLanguages = listOf("german", "english", "french"),
            education = "university_degree",
            religion = "spiritual_other",
            politics = "mid_left",
        ),
        howILive = mapOf(
            "activity" to "active", "diet" to "flexitarian", "alcohol" to "socially",
            "smoking" to "no", "rhythm" to "early_bird", "social" to "balanced",
        ),
        interests = listOf(
            "yoga_pilates", "hiking", "cooking", "brunch", "street_food_markets", "live_music",
            "museums_exhibitions", "reading", "board_games", "city_breaks",
        ),
        // No source exists for charms; the sample carries the reference's so frame E shows the card.
        charms = listOf(
            Charm("Lovely smile (4)", star = true),
            Charm("From Tom", star = false),
            Charm("5★ by Anna", star = true),
        ),
    )

    val MINIMUM = SearchProfile(
        userId = "sample-leonie-min",
        name = "Leonie",
        age = null,
        distanceKm = 1,
        district = "Neukölln, Berlin",
        meetAt = MEET_AT,
        meetDay = "tomorrow",
        attendedCount = 0,
        motivation = "long_term",
        vibes = listOf("coffee_chat"),
        uplift = null,
        photos = List(4) { "" },
        prompts = listOf(SearchPrompt("cross_town_for", "A proper conversation. An old cinema. The 8pm walk after a long day.")),
        voice = null,
        video = null,
        essentials = EssentialAnswers(),
        howILive = emptyMap(),
        interests = emptyList(),
    )

    /** A second and third person, so the queue has somewhere to go after an action. */
    val NEXT = listOf(
        FULL.copy(userId = "sample-mara", name = "Mara", age = 31, distanceKm = 4, district = "Kreuzberg", attendedCount = 0),
        MINIMUM.copy(userId = "sample-ana", name = "Ana", meetDay = "today"),
    )

    /** A loaded state at rest, for previews and the evidence frames. */
    fun rest(profile: SearchProfile = FULL, likes: Int = 2) = SearchUiState(
        loading = false,
        queue = listOf(profile) + NEXT,
        likesOpen = likes,
        viewer = VIEWER,
    )
}
