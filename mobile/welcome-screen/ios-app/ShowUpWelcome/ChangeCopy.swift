// ChangeCopy.swift
// ShowUp · every string Date 05 draws, the ticket's "Copy -- final strings" (SHOWUP-196)
//
// One home, so `audit/verify-change.py` quotes the ticket against one file per platform. Android's
// `ChangeCopy.kt`, string for string. `%@` are the ticket's `{name}`, `{time}`, `{place}`, `{deadline}`,
// `{latest}`, `{slot}`, `{venue}` and `{q}`.

import Foundation

enum ChangeCopy {
    // A · time picker
    static let timeTitle = "Suggest a new time"
    static let timeCurrent = "Your current meet time: "
    static func timeChoose(name: String, latest: String) -> String {
        "Choose a time within \(name)'s availability. Latest possible meet time: \(latest)."
    }
    static let pickTime = "Pick a time"

    // B · place picker
    static let placeTitle = "Suggest a new place"
    static let placeCurrent = "Your current meet place: "
    static let placeChoose = "Choose a public place: café, bar or restaurant."
    static let pickPlace = "Pick a place"
    static let searchPlaceholder = "Search cafés, bars, restaurants"
    static let nearby = "Nearby public places"
    static let results = "Results"

    // Both pickers
    static func agreeBy(name: String, deadline: String, binding: String) -> String {
        "\(name) has to agree by \(deadline), so you both still arrive on time. If not, \(binding) stays binding."
    }
    static func suggest(_ value: String) -> String { "Suggest \(value)" }
    static let cancel = "Cancel"

    /// The binding note (A, B, C, D): the bold sentence, then the rest. `unless you agree` when the match suggested it.
    static func bindingBold(time: String, place: String, name: String, deadline: String) -> String {
        "\(time) at \(place) stays binding unless \(name) agrees by \(deadline)."
    }
    static func bindingBoldYou(time: String, place: String, deadline: String) -> String {
        "\(time) at \(place) stays binding unless you agree by \(deadline)."
    }
    static let bindingRest = " Not showing up there counts as a no-show."

    // K · M · N
    static let sendFailed = "Couldn't send your suggestion. Check your connection and try again."
    static let placesFailed = "Couldn't load places. Check your connection and try again."
    static let tryAgain = "Try again"
    static func noPlaces(_ query: String) -> String { "No cafés, bars or restaurants found for “\(query)”." }
    static let tryAnother = "Try another name."

    /// Google's attribution for Places results shown without a map (R2): the text form, not localised.
    static let googleMaps = "Google Maps"

    /// The list area's description while it loads: spoken, never drawn (R17).
    static let loadingPlaces = "Loading places"

    // C · F · H · L · the suggestion card in the thread, and the system lines the server's facts turn into
    static let cardYou = "You suggested"
    static func cardThey(_ name: String) -> String { "\(name) suggested" }
    static let cardTime = "Meet time"
    static let cardPlace = "Meeting place"
    static func waitingFor(_ name: String) -> String { "Waiting for \(name)" }
    static let agreed = "Agreed"
    static func agreedAt(_ time: String) -> String { "Agreed · \(time)" }
    static let replaced = "Replaced by a new suggestion"
    static func expired(_ deadline: String) -> String { "Expired · not agreed by \(deadline)" }
    static func answerBy(_ deadline: String) -> String { "Answer by \(deadline)" }
    static let untilYouAgree = "Until you agree, "
    static func untilBinding(time: String, place: String) -> String { "\(time) at \(place) stays binding." }
    static let untilRest = " Not being there counts as a no-show."
    static let suggestAnother = "Suggest another"
    static let iAgree = "I agree"
    static let agreeFailed = "Couldn't agree. Check your connection and try again."
    static func systemTime(_ time: String) -> String { "Meet time is now \(time)" }
    static func systemPlace(_ venue: String) -> String { "Meeting place is now \(venue)" }
    static func systemExpired(deadline: String, time: String, place: String) -> String {
        "No answer by \(deadline) \u{2014} \(time) at \(place) stays."
    }
}
