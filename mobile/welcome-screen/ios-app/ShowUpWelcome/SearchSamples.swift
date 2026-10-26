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

    // MARK: - the Premium grid and the Likes tab (SHOWUP-185, 186)

    /// `HH:MM` in `zone` on 22 Oct 2026 (today) or the 23rd (tomorrow): the reference's meet times.
    private static func meet(_ hour: Int, _ minute: Int = 0, tomorrow: Bool = false) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        let parts = DateComponents(year: 2026, month: 10, day: tomorrow ? 23 : 22, hour: hour, minute: minute)
        return calendar.date(from: parts) ?? meetAt
    }

    /// A card opened as the full profile: Leonie's sections, the card's own fields (`premiumProfile`).
    private static func person(_ id: String, _ name: String, _ age: Int?, km: Int, motivation: String,
                               at hour: Int, _ minute: Int = 0, tomorrow: Bool, attended: Int) -> SearchProfile {
        var p = full
        p.userId = id
        p.name = name
        p.age = age
        p.distanceKm = km
        p.motivation = motivation
        p.meetAt = meet(hour, minute, tomorrow: tomorrow)
        p.meetDay = tomorrow ? "tomorrow" : "today"
        p.attendedCount = attended
        return p
    }

    /// `PREMIUM_GRID`, in its order: Leonie and Anna liked the viewer. Distances as the server rounds
    /// the reference's 1.2 / 2.4 / 0.6 / 1.8 / 4.0 / 2.0 km.
    static let grid: [GridCard] = [
        GridCard(profile: person("sample-leonie", "Leonie", 28, km: 1, motivation: "long_term", at: 19, tomorrow: false, attended: 4),
                 likedYou: true),
        GridCard(profile: person("sample-sofia", "Sofia", 31, km: 2, motivation: "casual", at: 20, tomorrow: false, attended: 2),
                 likedYou: false),
        GridCard(profile: person("sample-maya", "Maya", nil, km: 0, motivation: "non_monogamy", at: 18, tomorrow: true, attended: 0),
                 likedYou: false),
        GridCard(profile: person("sample-anna", "Anna", 27, km: 2, motivation: "long_term", at: 18, tomorrow: false, attended: 9),
                 likedYou: true),
        GridCard(profile: person("sample-emma", "Emma", 32, km: 4, motivation: "friends", at: 21, tomorrow: false, attended: 1),
                 likedYou: false),
        GridCard(profile: person("sample-liv", "Liv", 30, km: 2, motivation: "short_term", at: 17, 30, tomorrow: true, attended: 0),
                 likedYou: false),
    ]

    /// Leonie's message, as `LIKES_RECEIVED` writes it.
    static let leonieMessage = "I love that you're into cooking too. Coffee at Bonanza?"

    /// `likesOrder(LIKES_RECEIVED)`: earliest meet time first (Anna 18:00, Leonie 19:00), then Clara's
    /// closed like -- she matched someone else. The badge counts the two open ones.
    static let likes: [LikeReceived] = {
        let anna = grid[3].profile
        let leonie = grid[0].profile
        return [
            LikeReceived(userId: anna.userId, name: anna.name, age: anna.age, photo: nil, closed: nil, message: nil, profile: anna),
            LikeReceived(userId: leonie.userId, name: leonie.name, age: leonie.age, photo: nil, closed: nil,
                         message: leonieMessage, profile: leonie),
            LikeReceived(userId: "sample-clara", name: "Clara", age: 29, photo: nil, closed: .matched, message: nil, profile: nil),
        ]
    }()

    /// The grid as the model holds it after a load.
    static func gridCards() -> [PremiumCard] {
        grid.map { c in
            PremiumCard(userId: c.profile.userId, name: c.profile.name, age: c.profile.age,
                        photo: c.profile.photos.first ?? nil, profile: c.profile, likedYou: c.likedYou)
        }
    }

    /// The likes list as the model holds it after a load.
    static func likeCards() -> [PremiumCard] {
        likes.map { l in
            PremiumCard(userId: l.userId, name: l.name, age: l.age, photo: l.photo, profile: l.profile,
                        likedYou: true, message: l.message, closed: l.closed)
        }
    }

    /// The grid at rest (A), for previews and the fit sweep.
    static func gridRest(likes: Int = 2) -> SearchUiState {
        var s = SearchUiState()
        s.loading = false
        s.likesOpen = likes
        s.premium = true
        s.viewer = viewer
        s.layout = .grid
        s.grid = PremiumList(loading: false, cards: gridCards())
        return s
    }

    /// The Likes tab at rest (A).
    static func likesRest() -> SearchUiState {
        var s = SearchUiState()
        s.loading = false
        s.likesOpen = 2
        s.premium = true
        s.viewer = viewer
        s.layout = .grid
        s.likes = PremiumList(loading: false, cards: likeCards())
        return s
    }
}
