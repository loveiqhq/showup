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

    // ── the Premium grid and the Likes tab (SHOWUP-185, 186) ────────────────────────────────────

    /** `HH:MM` in [ZONE] on 22 Oct 2026 (today) or the 23rd (tomorrow): the reference's meet times. */
    private fun meet(hhmm: String, tomorrow: Boolean = false): Instant =
        java.time.LocalDateTime.parse("2026-10-${if (tomorrow) 23 else 22}T$hhmm").atZone(ZONE).toInstant()

    /** A card opened as the full profile: Leonie's sections, the card's own fields (`premiumProfile`). */
    private fun person(
        id: String, name: String, age: Int?, km: Int, motivation: String, hhmm: String, tomorrow: Boolean, attended: Int,
    ) = FULL.copy(
        userId = id, name = name, age = age, distanceKm = km, motivation = motivation,
        meetAt = meet(hhmm, tomorrow), meetDay = if (tomorrow) "tomorrow" else "today", attendedCount = attended,
    )

    /**
     * `PREMIUM_GRID`, in its order: Leonie and Anna liked the viewer. Distances as the server rounds
     * the reference's 1.2 / 2.4 / 0.6 / 1.8 / 4.0 / 2.0 km.
     */
    val GRID: List<GridCard> = listOf(
        GridCard(person("sample-leonie", "Leonie", 28, 1, "long_term", "19:00", false, 4), likedYou = true),
        GridCard(person("sample-sofia", "Sofia", 31, 2, "casual", "20:00", false, 2), likedYou = false),
        GridCard(person("sample-maya", "Maya", null, 0, "non_monogamy", "18:00", true, 0), likedYou = false),
        GridCard(person("sample-anna", "Anna", 27, 2, "long_term", "18:00", false, 9), likedYou = true),
        GridCard(person("sample-emma", "Emma", 32, 4, "friends", "21:00", false, 1), likedYou = false),
        GridCard(person("sample-liv", "Liv", 30, 2, "short_term", "17:30", true, 0), likedYou = false),
    )

    /** Leonie's message, as `LIKES_RECEIVED` writes it. */
    const val LEONIE_MESSAGE = "I love that you're into cooking too. Coffee at Bonanza?"

    /**
     * `likesOrder(LIKES_RECEIVED)`: earliest meet time first (Anna 18:00, Leonie 19:00), then Clara's
     * closed like -- she matched someone else. The badge counts the two open ones.
     */
    val LIKES: List<LikeReceived> = listOf(
        GRID[3].profile.let { LikeReceived(it.userId, it.name, it.age, "", closed = null, message = null, profile = it) },
        GRID[0].profile.let { LikeReceived(it.userId, it.name, it.age, "", closed = null, message = LEONIE_MESSAGE, profile = it) },
        LikeReceived("sample-clara", "Clara", 29, "", closed = CloseReason.Matched, message = null, profile = null),
    )

    /** The grid as the view model holds it after a load. */
    fun gridCards(): List<PremiumCard> = GRID.map { c ->
        PremiumCard(c.profile.userId, c.profile.name, c.profile.age, c.profile.photos.firstOrNull().orEmpty(), c.profile, c.likedYou)
    }

    /** The likes list as the view model holds it after a load. */
    fun likeCards(): List<PremiumCard> = LIKES.map { l ->
        PremiumCard(l.userId, l.name, l.age, l.photo, l.profile, likedYou = true, message = l.message, closed = l.closed)
    }

    /** The grid at rest (A), for previews and the evidence frames. */
    fun gridRest(likes: Int = 2) = SearchUiState(
        loading = false, likesOpen = likes, premium = true, viewer = VIEWER,
        layout = SearchAnalytics.Layout.Grid,
        grid = PremiumList(loading = false, cards = gridCards()),
    )

    /** The Likes tab at rest (A). */
    fun likesRest() = SearchUiState(
        loading = false, likesOpen = 2, premium = true, viewer = VIEWER,
        layout = SearchAnalytics.Layout.Grid,
        likes = PremiumList(loading = false, cards = likeCards()),
    )
}
