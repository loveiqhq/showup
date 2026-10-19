//
//  ProfileInterests.swift
//  ShowUp · Interests (Profile 21, SHOWUP-174) — the groups, the cap, and every rule that is not a
//  pixel
//
//  The Swift twin of `profile/ProfileInterests.kt`; its header has the argument. Two blocks, two
//  rules, one chip: How I live is six single-select groups, never capped or collapsed; What I'm into
//  is one multi-select set across six groups, capped at ten, four chips per group plus `Show more`.
//  The chip knows none of it — the rules are these plain functions, tested with no view.
//
//  WHAT IS STORED IS THE REGISTRY VALUE, NEVER THE LABEL, taken from the GENERATED contract enums.
//

import Foundation
import ShowUpAPI

/// The three steps after politics — Interests, Life and Future (Profiles 21 to 23). Not a
/// `DetailStep`: no single field, no visibility band, no scaffold, and an empty Continue that is
/// always a skip. Mirrors `PickStep` in Kotlin case for case.
enum PickStep: String, CaseIterable, Sendable {
    case interests
    case lifeNow = "life_now"
    /// The last step: its Continue and Skip both mark profile creation complete (Profile 23).
    case lifeAhead = "life_ahead"

    /// `enums.json` §2 `step_id`.
    var stepId: String { rawValue }

    /// §2 `step_index`, which is also the filled segment of the group's bar.
    var stepIndex: Int {
        switch self {
        case .interests: return 8
        case .lifeNow: return 9
        case .lifeAhead: return 10
        }
    }

    var screen: ProfileScreen {
        switch self {
        case .interests: return .interests
        case .lifeNow: return .lifeNow
        case .lifeAhead: return .lifeAhead
        }
    }

    /// The saved flow position this step advances. `life_ahead` is profile creation complete.
    var position: Components.Schemas.FlowPosition {
        switch self {
        case .interests: return .interests
        case .lifeNow: return .life_now
        case .lifeAhead: return .life_ahead
        }
    }
}

/// One "How I live" group: its §1 field, its label, its class, its options in the ticket's order.
/// Alcohol and smoking are Class 2, kept from the retired habits screen (registry 1.4.16).
enum LifestyleField: String, CaseIterable, Sendable {
    case activity, diet, alcohol, smoking, rhythm, social

    var fieldId: String { rawValue }

    var label: String {
        switch self {
        case .activity: return "Activity level"
        case .diet: return "Diet"
        case .alcohol: return "Alcohol"
        case .smoking: return "Smoking"
        case .rhythm: return "Rhythm"
        case .social: return "Social battery"
        }
    }

    var sensitivityClass: Int {
        switch self {
        case .activity, .diet: return 1
        case .alcohol, .smoking: return 2
        case .rhythm, .social: return 0
        }
    }

    var options: [DetailOption] {
        switch self {
        case .activity:
            return [
                .init(value: Components.Schemas.Activity.couch_lover.rawValue, label: "Couch-lover"),
                .init(value: Components.Schemas.Activity.moderately_active.rawValue, label: "Moderately active"),
                .init(value: Components.Schemas.Activity.active.rawValue, label: "Active"),
                .init(value: Components.Schemas.Activity.sport_is_my_life.rawValue, label: "Sport is my life"),
            ]
        case .diet:
            return [
                .init(value: Components.Schemas.Diet.everything.rawValue, label: "Everything"),
                .init(value: Components.Schemas.Diet.flexitarian.rawValue, label: "Flexitarian"),
                .init(value: Components.Schemas.Diet.vegetarian.rawValue, label: "Vegetarian"),
                .init(value: Components.Schemas.Diet.vegan.rawValue, label: "Vegan"),
            ]
        case .alcohol:
            return [
                .init(value: Components.Schemas.Alcohol.never.rawValue, label: "Never"),
                .init(value: Components.Schemas.Alcohol.rarely.rawValue, label: "Rarely"),
                .init(value: Components.Schemas.Alcohol.socially.rawValue, label: "Socially"),
                .init(value: Components.Schemas.Alcohol.regularly.rawValue, label: "Regularly"),
            ]
        case .smoking:
            return [
                .init(value: Components.Schemas.Smoking.no.rawValue, label: "No"),
                .init(value: Components.Schemas.Smoking.sometimes.rawValue, label: "Sometimes"),
                .init(value: Components.Schemas.Smoking.yes.rawValue, label: "Yes"),
            ]
        case .rhythm:
            return [
                .init(value: Components.Schemas.Rhythm.early_bird.rawValue, label: "Early bird"),
                .init(value: Components.Schemas.Rhythm.night_owl.rawValue, label: "Night owl"),
            ]
        case .social:
            return [
                .init(value: Components.Schemas.SocialBattery.cozy_at_home.rawValue, label: "Cozy at home"),
                .init(value: Components.Schemas.SocialBattery.balanced.rawValue, label: "Balanced"),
                .init(value: Components.Schemas.SocialBattery.always_out.rawValue, label: "Always out"),
            ]
        }
    }
}

/// One "What I'm into" group: its §3 `section`, its label, its chips in `INTEREST_GROUPS` order.
enum InterestSection: String, CaseIterable, Sendable {
    case move, food, culture, home, out, travel

    var sectionId: String { rawValue }

    var label: String {
        switch self {
        case .move: return "Move"
        case .food: return "Food & Drink"
        case .culture: return "Culture"
        case .home: return "Cozy at home"
        case .out: return "Going out"
        case .travel: return "Travel style"
        }
    }

    var options: [DetailOption] {
        typealias T = Components.Schemas.InterestTag
        switch self {
        case .move:
            return [
                .init(value: T.gym.rawValue, label: "Gym"),
                .init(value: T.running.rawValue, label: "Running"),
                .init(value: T.hyrox_crossfit.rawValue, label: "Hyrox/CrossFit"),
                .init(value: T.yoga_pilates.rawValue, label: "Yoga/Pilates"),
                .init(value: T.bouldering.rawValue, label: "Bouldering"),
                .init(value: T.cycling.rawValue, label: "Cycling"),
                .init(value: T.swimming.rawValue, label: "Swimming"),
                .init(value: T.hiking.rawValue, label: "Hiking"),
                .init(value: T.team_sports.rawValue, label: "Team sports"),
                .init(value: T.tennis_padel.rawValue, label: "Tennis/Padel"),
                .init(value: T.martial_arts.rawValue, label: "Martial arts"),
                .init(value: T.winter_sports.rawValue, label: "Winter sports"),
            ]
        case .food:
            return [
                .init(value: T.cooking.rawValue, label: "Cooking"),
                .init(value: T.baking.rawValue, label: "Baking"),
                .init(value: T.trying_new_restaurants.rawValue, label: "Trying new restaurants"),
                .init(value: T.brunch.rawValue, label: "Brunch"),
                .init(value: T.street_food_markets.rawValue, label: "Street food & markets"),
                .init(value: T.wine.rawValue, label: "Wine"),
                .init(value: T.craft_beer.rawValue, label: "Craft beer"),
                .init(value: T.coffee_spots.rawValue, label: "Coffee spots"),
                .init(value: T.cocktail_bars.rawValue, label: "Cocktail bars"),
            ]
        case .culture:
            return [
                .init(value: T.live_music.rawValue, label: "Live music"),
                .init(value: T.museums_exhibitions.rawValue, label: "Museums & exhibitions"),
                .init(value: T.theatre.rawValue, label: "Theatre"),
                .init(value: T.cinema.rawValue, label: "Cinema"),
                .init(value: T.reading.rawValue, label: "Reading"),
                .init(value: T.comedy.rawValue, label: "Comedy"),
                .init(value: T.festivals.rawValue, label: "Festivals"),
                .init(value: T.making_art_music.rawValue, label: "Making art/music"),
            ]
        case .home:
            return [
                .init(value: T.tv_series.rawValue, label: "TV series"),
                .init(value: T.movie_nights.rawValue, label: "Movie nights"),
                .init(value: T.gaming.rawValue, label: "Gaming"),
                .init(value: T.board_games.rawValue, label: "Board games"),
                .init(value: T.podcasts.rawValue, label: "Podcasts"),
                .init(value: T.plants_gardening.rawValue, label: "Plants & gardening"),
            ]
        case .out:
            return [
                .init(value: T.bars.rawValue, label: "Bars"),
                .init(value: T.clubbing.rawValue, label: "Clubbing"),
                .init(value: T.dinner_parties.rawValue, label: "Dinner parties"),
                .init(value: T.quiz_nights.rawValue, label: "Quiz nights"),
                .init(value: T.karaoke.rawValue, label: "Karaoke"),
                .init(value: T.concerts_with_friends.rawValue, label: "Concerts with friends"),
                .init(value: T.volunteering.rawValue, label: "Volunteering"),
            ]
        case .travel:
            return [
                .init(value: T.city_breaks.rawValue, label: "City breaks"),
                .init(value: T.road_trips.rawValue, label: "Road trips"),
                .init(value: T.beach_relax.rawValue, label: "Beach & relax"),
                .init(value: T.all_inclusive.rawValue, label: "All-inclusive"),
                .init(value: T.backpacking.rawValue, label: "Backpacking"),
                .init(value: T.surf_adventure.rawValue, label: "Surf & adventure"),
                .init(value: T.wellness_spa.rawValue, label: "Wellness & spa"),
                .init(value: T.camping.rawValue, label: "Camping"),
                .init(value: T.cruises.rawValue, label: "Cruises"),
            ]
        }
    }

    /// Whether the group starts collapsed behind a `Show more` — "more than 4 chips" only.
    var collapsible: Bool { options.count > interestsVisible }

    /// How many chips `Show more` reveals: `revealed_count` on `show_more_expanded`.
    var hiddenCount: Int { max(options.count - interestsVisible, 0) }
}

/// "Capped at 10" — What I'm into only. The server holds the same number (`MAX_INTERESTS`).
let interestsMax = 10

/// The chips a collapsed group shows: `INTEREST_VISIBLE` in the reference.
let interestsVisible = 4

/// Every §3 tag in list order: sections in order, chips in order within each.
let interestOrder: [String] = InterestSection.allCases.flatMap { $0.options.map(\.value) }

/// Everything picked on Interests, and which groups are open. `expanded` lives here, not in the
/// view, because opening a group is a tracked act and has to survive a restore but not an arrival.
struct InterestsDraft: Equatable, Sendable, Codable {
    /// §1 field id -> §1 value, for the groups answered.
    var lifestyle: [String: String] = [:]
    /// §3 tag_ids. At most `interestsMax`.
    var interests: Set<String> = []
    /// §3 section ids of the What I'm into groups that are open.
    var expanded: Set<String> = []

    /// Whether Continue saves anything: any How I live answer or any interest.
    var anything: Bool { !lifestyle.isEmpty || !interests.isEmpty }

    var capReached: Bool { interests.count >= interestsMax }
}

/// The answer to a How I live group after a tap: a different chip moves it, the chosen one clears it.
func pickLifestyle(current: String?, tapped: String) -> String? {
    current == tapped ? nil : tapped
}

/// What a tap on a What I'm into chip did. Each outcome is the event it reports.
enum InterestTap: Equatable {
    case selected(Set<String>)
    case deselected(Set<String>)
    /// A tap on a faded chip at the cap: "the tap is a no-op". Reported, never applied.
    case capReached
}

/// Picks or unpicks `tapped`. At the cap an unpicked chip does nothing; a picked one still unpicks.
func tapInterest(_ current: Set<String>, tapped: String) -> InterestTap {
    if current.contains(tapped) { return .deselected(current.subtracting([tapped])) }
    if current.count >= interestsMax { return .capReached }
    return .selected(current.union([tapped]))
}

/// The chips a group draws: all of them when open, the first four when collapsed.
func visibleOptions(_ section: InterestSection, expanded: Bool) -> [DetailOption] {
    section.collapsible && !expanded ? Array(section.options.prefix(interestsVisible)) : section.options
}

/// The counter's sentence. `{N} picked · {M} left`, and `10 of 10 picked` at the cap.
func interestsCounterLabel(_ picked: Int) -> String {
    picked >= interestsMax
        ? "\(interestsMax) of \(interestsMax) picked"
        : "\(picked) picked \u{00B7} \(interestsMax - picked) left"
}

/// The counter bar's filled fraction, 0 to 1.
func interestsFill(_ picked: Int) -> CGFloat {
    min(max(CGFloat(picked) / CGFloat(interestsMax), 0), 1)
}

/// The picked tags in LIST order, never tap order — the server canonicalises the same way.
func interestsInListOrder(_ picked: Set<String>) -> [String] {
    interestOrder.filter { picked.contains($0) }
}

/// The How I live answers to report, in list order — one `detail_answered` each on Continue.
func answeredLifestyle(_ draft: InterestsDraft) -> [(LifestyleField, String)] {
    LifestyleField.allCases.compactMap { field in draft.lifestyle[field.fieldId].map { (field, $0) } }
}

/// The draft the screen opens with: the saved answers this build knows, every group collapsed.
func interestsOnArrival(_ saved: SavedDetails) -> InterestsDraft {
    var lifestyle: [String: String] = [:]
    for field in LifestyleField.allCases {
        if let value = saved.lifestyle[field.fieldId], field.options.contains(where: { $0.value == value }) {
            lifestyle[field.fieldId] = value
        }
    }
    let known = saved.interests.filter { interestOrder.contains($0) }
    return InterestsDraft(lifestyle: lifestyle, interests: Set(known.prefix(interestsMax)), expanded: [])
}

/// How a What I'm into chip is recorded among the touched parts of the step on screen.
func interestTouchKey(_ tagId: String) -> String { interestTouchPrefix + tagId }

private let interestTouchPrefix = "tag:"

/// Whether any What I'm into chip was touched since arriving.
func interestsTouched(_ touched: Set<String>) -> Bool { touched.contains { $0.hasPrefix(interestTouchPrefix) } }

/// Whether the account holds an interest this build cannot draw — one a newer server added. An
/// UNTOUCHED list holding one is not sent, or Continue would delete it (the Kotlin twin's comment).
func interestsHoldUnknown(_ saved: SavedDetails) -> Bool {
    saved.interests.contains { !interestOrder.contains($0) }
}

/// `draft` once the account's answers land LATE — after the user started on the screen. The
/// Kotlin twin's comment (`interestsOnLateRead`) is the long form: every untouched part takes the
/// saved answer, a touched one keeps what the user did, the interest list is merged chip by chip
/// with the user's own picks kept first, and the open groups stay open.
///
/// - Parameter touched: what the user changed since arriving: field ids, and `interestTouchKey`s.
func interestsOnLateRead(_ draft: InterestsDraft, saved: SavedDetails, touched: Set<String>) -> InterestsDraft {
    let fresh = interestsOnArrival(saved)
    var lifestyle: [String: String] = [:]
    for field in LifestyleField.allCases {
        let id = field.fieldId
        lifestyle[id] = touched.contains(id) ? draft.lifestyle[id] : fresh.lifestyle[id]
    }
    let mine = interestsInListOrder(draft.interests.filter { touched.contains(interestTouchKey($0)) })
    let kept = interestsInListOrder(fresh.interests).filter { !touched.contains(interestTouchKey($0)) }
    return InterestsDraft(
        lifestyle: lifestyle,
        interests: Set((mine + kept).prefix(interestsMax)),
        expanded: draft.expanded)
}

/// Whether `interestsOnLateRead` had to CUT a saved interest to stay at ten — a saved tag nobody
/// touched that is not in `merged`. Saving it would delete that tag unseen, so the caller stops and
/// lets the screen show the merged ten first (the Kotlin twin, `interestsCutOnLateRead`).
func interestsCutOnLateRead(_ saved: SavedDetails, merged: InterestsDraft, touched: Set<String>) -> Bool {
    interestsOnArrival(saved).interests.contains {
        !touched.contains(interestTouchKey($0)) && !merged.interests.contains($0)
    }
}
