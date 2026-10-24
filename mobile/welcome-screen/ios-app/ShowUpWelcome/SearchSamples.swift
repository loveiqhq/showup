//
//  SearchSamples.swift
//  ShowUp · the reference's two profiles, as the app's own types (SHOWUP-184)
//
//  The Swift twin of `search/SearchSamples.kt`: `SEARCH_V2_PROFILE` (every section) and
//  `SEARCH_V2_PROFILE_MIN` (only the mandatory fields) from `screen-search-profile-reference.jsx`, with
//  the reference's display strings read back to the ids the server sends -- so a sample drawn here goes
//  through exactly the label tables a real profile does. Previews, the fit sweep and the dev store all
//  read these. Photos are nil: the slot draws the kit's placeholder, as the sheet does.
//

import Foundation

enum SearchSamples {
    /// The zone the samples' meet hour reads `19:00` in.
    static let zone = TimeZone(identifier: "Europe/Berlin") ?? .current

    /// 19:00 in `zone` on 22 Oct 2026 -- 17:00 UTC.
    static let meetAt = Date(timeIntervalSince1970: 1_792_688_400)

    /// The viewer: their Check-In vibes and profile interests, for the overlap (`SEARCH_VIEWER`).
    static let viewer = SearchViewer(
        vibes: ["coffee_chat", "sunset", "no_rush"],
        // The reference's "Climbing" is not a §3 tag; bouldering stands in, shared with nobody here.
        interests: ["hiking", "live_music", "reading", "board_games", "bouldering"])

    static let full = SearchProfile(
        userId: "sample-leonie",
        name: "Leonie",
        age: 28,
        distanceKm: 1,
        district: "Neukölln, Berlin",
        meetAt: meetAt,
        meetDay: "today",
        attendedCount: 4,
        motivation: "long_term",
        vibes: ["coffee_chat", "sunset", "deep_talk", "playful", "no_rush"],
        uplift: "I'll take you on a rafting tour on Wannsee — picnic included",
        photos: Array(repeating: nil, count: 6),
        prompts: [
            SearchPrompt(
                topicId: "first_date_usually",
                answer: "Talk about anything real. Not jobs, not pets, not the weather. The thing actually on your mind this week. Bring it. I'll listen."),
            SearchPrompt(topicId: "cross_town_for", answer: "A proper conversation. An old cinema. The 8pm walk after a long day."),
            SearchPrompt(topicId: "hill_to_die_on", answer: "Showing up. Cancelling last minute isn't a scheduling problem, it's an answer."),
        ],
        voice: SearchMedia(url: nil, durationMs: 14_000, promptId: "relaxing_sound"),
        video: SearchMedia(url: nil, durationMs: 10_000, promptId: "ideal_sunny_morning"),
        essentials: EssentialAnswers(
            heightCm: 172, gender: "woman", orientation: "straight",
            datingLanguages: ["german", "english", "french"], education: "university_degree",
            religion: "spiritual_other", politics: "mid_left"),
        howILive: [
            "activity": "active", "diet": "flexitarian", "alcohol": "socially",
            "smoking": "no", "rhythm": "early_bird", "social": "balanced",
        ],
        interests: [
            "yoga_pilates", "hiking", "cooking", "brunch", "street_food_markets", "live_music",
            "museums_exhibitions", "reading", "board_games", "city_breaks",
        ],
        // No source exists for charms; the sample carries the reference's so frame E shows the card.
        charms: [
            Charm(label: "Lovely smile (4)", star: true),
            Charm(label: "From Tom", star: false),
            Charm(label: "5\u{2605} by Anna", star: true),
        ])

    static let minimum = SearchProfile(
        userId: "sample-leonie-min",
        name: "Leonie",
        age: nil,
        distanceKm: 1,
        district: "Neukölln, Berlin",
        meetAt: meetAt,
        meetDay: "tomorrow",
        attendedCount: 0,
        motivation: "long_term",
        vibes: ["coffee_chat"],
        uplift: nil,
        photos: Array(repeating: nil, count: 4),
        prompts: [SearchPrompt(topicId: "cross_town_for", answer: "A proper conversation. An old cinema. The 8pm walk after a long day.")],
        voice: nil,
        video: nil,
        essentials: EssentialAnswers(),
        howILive: [:],
        interests: [])

    /// A second and third person, so the queue has somewhere to go after an action.
    static let next: [SearchProfile] = {
        var mara = full
        mara.userId = "sample-mara"
        mara.name = "Mara"
        mara.age = 31
        mara.distanceKm = 4
        mara.district = "Kreuzberg"
        mara.attendedCount = 0
        var ana = minimum
        ana.userId = "sample-ana"
        ana.name = "Ana"
        ana.meetDay = "today"
        return [mara, ana]
    }()

    /// A loaded state at rest, for previews and the fit sweep.
    static func rest(_ profile: SearchProfile = full, likes: Int = 2) -> SearchUiState {
        var s = SearchUiState()
        s.loading = false
        s.queue = [profile] + next
        s.likesOpen = likes
        s.viewer = viewer
        return s
    }
}
