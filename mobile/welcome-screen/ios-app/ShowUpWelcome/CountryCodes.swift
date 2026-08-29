//  CountryCodes.swift
//  ShowUp · the country list behind the dial-code pill (SHOWUP-143)
//
//  NOTHING HERE COSTS MONEY.
//
//  Country calling codes are public assignments published by the ITU (recommendation E.164). They
//  are not licensed, not metered, and not behind anyone's API — the table below is just data, and
//  it works offline. The paid service in this flow is Twilio, and it is paid for *delivering the
//  SMS*, not for knowing that Germany is +49.
//
//  The one thing worth buying later is deeper validation — "is this a real, reachable mobile number
//  on a live carrier". Even that has a free answer first: Google's libphonenumber (Apache 2.0,
//  free, offline) knows every country's real number formats and is what production should use.
//  Twilio's Lookup API is charged per query and only earns its keep for carrier and portability
//  checks.
//
//  Until libphonenumber is added, `validate` applies the plain length and prefix rules below. They
//  are deliberately simple and explainable rather than clever: rejecting a number a real user holds
//  is a much worse failure than accepting one that later bounces.

import SwiftUI

/// How a flag is drawn. No emoji anywhere, flags included — CLAUDE.md states it as a rule.
enum FlagArt {
    /// Equal or weighted stripes. `horizontal: false` means vertical stripes.
    case bands(horizontal: Bool, stripes: [(UInt32, Int)])
    /// The Nordic cross — offset left, not centred. `inner` draws a second, thinner cross.
    case cross(bg: UInt32, arm: UInt32, inner: UInt32? = nil, centred: Bool = false)
    /// The ISO code on a neutral chip, for flags we cannot draw honestly.
    ///
    /// A Union Jack or a Stars and Stripes approximated out of rectangles is worse than no flag: it
    /// reads as a bug, and for some countries an inaccurate flag is a genuine offence. The code is
    /// unambiguous and always correct.
    case code
}

struct Country: Identifiable, Equatable {
    let iso: String
    let name: String
    let dial: String
    /// Length of a MOBILE national significant number — the digits after the dial code, with any
    /// national trunk "0" already stripped.
    ///
    /// Mobile, not "any number in that country". This screen exists to send an SMS, so a number
    /// that cannot receive one is not valid input however real it is. German landlines start at six
    /// digits, and while this table said 6 a number like 49 6 12345 was accepted and would have sat
    /// waiting for a code that could never arrive.
    let nsnMin: Int
    let nsnMax: Int
    let flag: FlagArt
    /// Placeholder shown in the empty field, in that country's own habits.
    let sample: String

    var id: String { iso }
    static func == (a: Country, b: Country) -> Bool { a.iso == b.iso }
}

private func bandsH(_ c: UInt32...) -> FlagArt { .bands(horizontal: true, stripes: c.map { ($0, 1) }) }
private func bandsV(_ c: UInt32...) -> FlagArt { .bands(horizontal: false, stripes: c.map { ($0, 1) }) }

/// Sorted by name. Weighted toward the launch market and its neighbours; every entry is real data
/// rather than a placeholder, so adding a country is one line.
let COUNTRIES: [Country] = [
    .init(iso: "AT", name: "Austria", dial: "+43", nsnMin: 10, nsnMax: 13,
          flag: bandsH(0xED2939, 0xFFFFFF, 0xED2939), sample: "664 1234567"),
    .init(iso: "AU", name: "Australia", dial: "+61", nsnMin: 9, nsnMax: 9,
          flag: .code, sample: "412 345 678"),
    .init(iso: "BA", name: "Bosnia and Herzegovina", dial: "+387", nsnMin: 8, nsnMax: 8,
          flag: .code, sample: "61 123 456"),
    .init(iso: "BE", name: "Belgium", dial: "+32", nsnMin: 9, nsnMax: 9,
          flag: bandsV(0x000000, 0xFAE042, 0xED2939), sample: "470 12 34 56"),
    .init(iso: "BG", name: "Bulgaria", dial: "+359", nsnMin: 8, nsnMax: 9,
          flag: bandsH(0xFFFFFF, 0x00966E, 0xD62612), sample: "48 123 456"),
    .init(iso: "CA", name: "Canada", dial: "+1", nsnMin: 10, nsnMax: 10,
          flag: .code, sample: "506 234 5678"),
    .init(iso: "CH", name: "Switzerland", dial: "+41", nsnMin: 9, nsnMax: 9,
          flag: .cross(bg: 0xDA291C, arm: 0xFFFFFF, centred: true), sample: "78 123 45 67"),
    .init(iso: "CZ", name: "Czechia", dial: "+420", nsnMin: 9, nsnMax: 9,
          flag: .code, sample: "601 123 456"),
    .init(iso: "DE", name: "Germany", dial: "+49", nsnMin: 10, nsnMax: 11,
          flag: bandsH(0x000000, 0xDD0000, 0xFFCE00), sample: "176 123 45 678"),
    .init(iso: "DK", name: "Denmark", dial: "+45", nsnMin: 8, nsnMax: 8,
          flag: .cross(bg: 0xC8102E, arm: 0xFFFFFF), sample: "32 12 34 56"),
    .init(iso: "EE", name: "Estonia", dial: "+372", nsnMin: 7, nsnMax: 8,
          flag: bandsH(0x0072CE, 0x000000, 0xFFFFFF), sample: "5123 4567"),
    .init(iso: "ES", name: "Spain", dial: "+34", nsnMin: 9, nsnMax: 9,
          flag: .bands(horizontal: true, stripes: [(0xAA151B, 1), (0xF1BF00, 2), (0xAA151B, 1)]),
          sample: "612 34 56 78"),
    .init(iso: "FI", name: "Finland", dial: "+358", nsnMin: 9, nsnMax: 10,
          flag: .cross(bg: 0xFFFFFF, arm: 0x003580), sample: "41 2345678"),
    .init(iso: "FR", name: "France", dial: "+33", nsnMin: 9, nsnMax: 9,
          flag: bandsV(0x002395, 0xFFFFFF, 0xED2939), sample: "6 12 34 56 78"),
    .init(iso: "GB", name: "United Kingdom", dial: "+44", nsnMin: 10, nsnMax: 10,
          flag: .code, sample: "7400 123456"),
    .init(iso: "GR", name: "Greece", dial: "+30", nsnMin: 10, nsnMax: 10,
          flag: .code, sample: "691 234 5678"),
    .init(iso: "HR", name: "Croatia", dial: "+385", nsnMin: 8, nsnMax: 9,
          flag: .code, sample: "91 234 5678"),
    .init(iso: "HU", name: "Hungary", dial: "+36", nsnMin: 9, nsnMax: 9,
          flag: bandsH(0xCD2A3E, 0xFFFFFF, 0x436F4D), sample: "20 123 4567"),
    .init(iso: "IE", name: "Ireland", dial: "+353", nsnMin: 9, nsnMax: 9,
          flag: bandsV(0x169B62, 0xFFFFFF, 0xFF883E), sample: "85 012 3456"),
    .init(iso: "IT", name: "Italy", dial: "+39", nsnMin: 9, nsnMax: 10,
          flag: bandsV(0x008C45, 0xF4F5F0, 0xCD212A), sample: "312 345 6789"),
    .init(iso: "LT", name: "Lithuania", dial: "+370", nsnMin: 8, nsnMax: 8,
          flag: bandsH(0xFDB913, 0x006A44, 0xC1272D), sample: "612 34567"),
    .init(iso: "LU", name: "Luxembourg", dial: "+352", nsnMin: 9, nsnMax: 9,
          flag: bandsH(0xED2939, 0xFFFFFF, 0x00A1DE), sample: "628 123 456"),
    .init(iso: "LV", name: "Latvia", dial: "+371", nsnMin: 8, nsnMax: 8,
          flag: .bands(horizontal: true, stripes: [(0x9E3039, 2), (0xFFFFFF, 1), (0x9E3039, 2)]),
          sample: "21 234 567"),
    .init(iso: "NL", name: "Netherlands", dial: "+31", nsnMin: 9, nsnMax: 9,
          flag: bandsH(0xAE1C28, 0xFFFFFF, 0x21468B), sample: "6 12345678"),
    .init(iso: "NO", name: "Norway", dial: "+47", nsnMin: 8, nsnMax: 8,
          flag: .cross(bg: 0xBA0C2F, arm: 0xFFFFFF, inner: 0x00205B), sample: "406 12 345"),
    .init(iso: "PL", name: "Poland", dial: "+48", nsnMin: 9, nsnMax: 9,
          flag: bandsH(0xFFFFFF, 0xDC143C), sample: "512 345 678"),
    .init(iso: "PT", name: "Portugal", dial: "+351", nsnMin: 9, nsnMax: 9,
          flag: .code, sample: "912 345 678"),
    .init(iso: "RO", name: "Romania", dial: "+40", nsnMin: 9, nsnMax: 9,
          flag: bandsV(0x002B7F, 0xFCD116, 0xCE1126), sample: "712 345 678"),
    .init(iso: "RS", name: "Serbia", dial: "+381", nsnMin: 8, nsnMax: 9,
          flag: .code, sample: "60 1234567"),
    .init(iso: "SE", name: "Sweden", dial: "+46", nsnMin: 9, nsnMax: 9,
          flag: .cross(bg: 0x006AA7, arm: 0xFECC00), sample: "70 123 45 67"),
    .init(iso: "SI", name: "Slovenia", dial: "+386", nsnMin: 8, nsnMax: 8,
          flag: .code, sample: "31 234 567"),
    .init(iso: "SK", name: "Slovakia", dial: "+421", nsnMin: 9, nsnMax: 9,
          flag: .code, sample: "912 123 456"),
    .init(iso: "TR", name: "Türkiye", dial: "+90", nsnMin: 10, nsnMax: 10,
          flag: .code, sample: "501 234 56 78"),
    .init(iso: "UA", name: "Ukraine", dial: "+380", nsnMin: 9, nsnMax: 9,
          flag: bandsH(0x0057B7, 0xFFDD00), sample: "50 123 4567"),
    .init(iso: "US", name: "United States", dial: "+1", nsnMin: 10, nsnMax: 10,
          flag: .code, sample: "201 555 0123"),
]

/// Germany is the launch market, so it is the fallback when the device locale says nothing useful.
let DEFAULT_COUNTRY: Country = COUNTRIES.first { $0.iso == "DE" }!

/// The country for a device region, or Germany.
///
/// SHOWUP-143 asks the pill to default from device locale. The region code the platform reports is
/// exactly the ISO key used above, so this is a lookup rather than a guess.
func countryForRegion(_ region: String?) -> Country {
    guard let region else { return DEFAULT_COUNTRY }
    return COUNTRIES.first { $0.iso.caseInsensitiveCompare(region) == .orderedSame } ?? DEFAULT_COUNTRY
}

/// How many digits past the country's maximum the field accepts before it stops taking input.
///
/// Not zero, and that is the point. The field used to cap at exactly the maximum, which silently
/// ate every extra keystroke — so a too-long number could not be typed, `.tooLong` could never
/// fire, and the user watched their own digits disappear with no explanation.
let OVERTYPE_ALLOWANCE = 4

/// Why a number was rejected. Each maps to one message, and each is something the user can act on.
enum PhoneError {
    case empty, tooShort, tooLong, leadingZero, notANumber

    func message(_ country: Country) -> String {
        switch self {
        case .empty: return "Enter your phone number to continue."
        case .notANumber: return "Numbers only, please. For example \(country.sample)."
        // Names the fix rather than the rule.
        case .leadingZero: return "Leave out the first 0 — \(country.dial) already covers it."
        case .tooShort: return "That looks too short for \(country.name). For example \(country.sample)."
        case .tooLong: return "That looks too long for \(country.name). For example \(country.sample)."
        }
    }
}

/// Validation, run on submit rather than per keystroke — SHOWUP-143 requires that, and it is also
/// the kinder behaviour: nobody wants to be told their number is wrong while they are still typing.
func validate(_ raw: String, _ country: Country) -> PhoneError? {
    let digits = raw.filter(\.isNumber)
    // Letters first. Stripping them and then reporting "empty" would answer a question the user
    // did not ask -- they typed something, it was just the wrong something.
    //
    // The field filters to digits as they are entered, so today nothing can reach this. It stays
    // as the guard for a value arriving from somewhere that does not filter: a paste, an autofill
    // suggestion, or a caller that has not been written yet.
    if raw.contains(where: \.isLetter) { return .notANumber }
    if digits.isEmpty { return .empty }
    // Every country in this list uses 0 as a national trunk prefix, and it is dropped when the dial
    // code is supplied separately. Catching it explicitly is worth it: writing 0176… is the single
    // most common way a German user gets this wrong.
    if digits.hasPrefix("0") { return .leadingZero }
    if digits.count < country.nsnMin { return .tooShort }
    if digits.count > country.nsnMax { return .tooLong }
    return nil
}

/// Digits only, grouped the way that country's sample is grouped, so typing looks familiar.
func formatNational(_ digits: String, _ country: Country) -> String {
    let groups = country.sample.split(separator: " ").map(\.count)
    var out = ""
    var i = digits.startIndex
    for g in groups {
        if i >= digits.endIndex { break }
        let end = digits.index(i, offsetBy: g, limitedBy: digits.endIndex) ?? digits.endIndex
        if !out.isEmpty { out += " " }
        out += digits[i..<end]
        i = end
    }
    // Anything past the sample's shape runs on unbroken rather than being invented into groups.
    if i < digits.endIndex {
        if !out.isEmpty { out += " " }
        out += digits[i...]
    }
    return out
}
