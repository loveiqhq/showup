//  CountryCodes.swift
//  ShowUp · the country list behind the dial-code pill (SHOWUP-143)
//
//  ⚠️ THIS FILE IS ONE STEP BEHIND ANDROID, AND THE STEP NEEDS A MAC.
//
//  Android now derives every country and every rule from Google's libphonenumber: ~250 countries,
//  real per-country mobile lengths, landline detection, and as-you-type grouping — all covered by
//  app/src/test/java/com/showup/welcome/PhoneValidationTest.kt, which tests against libphonenumber's
//  own data.
//
//  The Swift equivalent is PhoneNumberKit (MIT, same underlying metadata). It installs through
//  Xcode's Swift Package Manager, and resolving a package needs Xcode — it cannot be done or
//  verified from Windows. Writing the integration blind is precisely the fix-by-fix loop this
//  project agreed to avoid, so it is left for the Mac rather than guessed at.
//
//  WHAT THIS MEANS TODAY: iOS offers the 35 hand-written countries below with hand-written length
//  rules. Android offers every country with correct ones. That divergence is deliberate and
//  temporary, and it is the only thing standing between the two platforms on this screen.
//
//  THE JOB, when Xcode is available:
//    1. File > Add Package Dependencies… > https://github.com/PhoneNumberKit/PhoneNumberKit
//    2. Replace COUNTRIES with PhoneNumberKit's allCountries, mapped the way CountryCodes.kt does
//    3. Replace validate() with the same order Android uses — TYPE first, then length, because a
//       short landline otherwise reports "too short" instead of "we need a mobile"
//    4. Replace formatNational() with PartialFormatter
//    5. Port PhoneValidationTest.kt; it is the specification, and it should pass unchanged
//
//  The flag artwork is already done on both sides and needs no package — see CountryPicker.swift.

import SwiftUI

/// How a flag is drawn. No emoji anywhere, flags included — CLAUDE.md states it as a rule.
enum FlagArt {
    /// Equal or weighted stripes. `horizontal: false` means vertical stripes.
    case bands(horizontal: Bool, stripes: [(UInt32, Int)])
    /// The Nordic cross — offset left, not centred. `inner` draws a second, thinner cross.
    case cross(bg: UInt32, arm: UInt32, inner: UInt32? = nil, centred: Bool = false)
    /// An ordered list of shapes on a 0..1 unit square, painted back to front.
    ///
    /// Bands and cross cover the flags that are just stripes; this covers the rest — a triangle
    /// from the hoist, a canton, a crescent, a leaf. Everything is a fraction of the flag rather
    /// than a point, so one description renders correctly at any size and on either platform.
    case layers([FlagShape])
    /// The ISO code on a neutral chip, for the few flags that cannot be drawn honestly.
    ///
    /// Reserved for flags whose identity depends on a coat of arms — Slovakia and Slovenia are
    /// both white-blue-red and are told apart ONLY by their arms, so drawing the stripes alone
    /// would render two different countries identically. Australia needs a Union Jack plus the
    /// Southern Cross at a size where neither survives. A wrong flag is worse than an honest code.
    case code
}

/// One shape in a `.layers` flag. All coordinates are fractions of the flag, 0..1.
enum FlagShape {
    case fill(UInt32)
    /// Equal stripes, painted in order. `horizontal: false` means vertical.
    case stripes(horizontal: Bool, colors: [UInt32])
    case box(CGFloat, CGFloat, CGFloat, CGFloat, UInt32)
    /// A filled polygon — a hoist triangle, a maple leaf, a diagonal of a Union Jack.
    case poly([(CGFloat, CGFloat)], UInt32)
    case disc(CGFloat, CGFloat, CGFloat, UInt32)
    /// The fourth value is the stroke width as a fraction of the flag height.
    case ring(CGFloat, CGFloat, CGFloat, CGFloat, UInt32)
    /// A five-pointed star, point upward. The third value is the outer radius.
    case star(CGFloat, CGFloat, CGFloat, UInt32)
    /// n x n alternating squares — Croatia's shield, which is what tells it from the Dutch.
    case checks(CGFloat, CGFloat, CGFloat, CGFloat, Int, UInt32, UInt32)
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
          flag: .layers([
        .stripes(horizontal: true, colors: [0xFFFFFF, 0xD7141A]),
        .poly([(0, 0), (0.5, 0.5), (0, 1)], 0x11457E),
    ]), sample: "601 123 456"),
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
          flag: .layers([
        .fill(0x012169),
        .poly([(0, 0), (0.16, 0), (1, 1), (0.84, 1)], 0xFFFFFF),
        .poly([(1, 0), (0.84, 0), (0, 1), (0.16, 1)], 0xFFFFFF),
        .poly([(0, 0), (0.09, 0), (1, 1), (0.91, 1)], 0xC8102E),
        .poly([(1, 0), (0.91, 0), (0, 1), (0.09, 1)], 0xC8102E),
        .box(0, 0.33, 1, 0.34, 0xFFFFFF),
        .box(0.39, 0, 0.22, 1, 0xFFFFFF),
        .box(0, 0.40, 1, 0.20, 0xC8102E),
        .box(0.435, 0, 0.13, 1, 0xC8102E),
    ]), sample: "7400 123456"),
    .init(iso: "GR", name: "Greece", dial: "+30", nsnMin: 10, nsnMax: 10,
          flag: .layers([
        .stripes(horizontal: true, colors: [
            0x0D5EAF, 0xFFFFFF, 0x0D5EAF, 0xFFFFFF, 0x0D5EAF,
            0xFFFFFF, 0x0D5EAF, 0xFFFFFF, 0x0D5EAF,
        ]),
        .box(0, 0, 5.0 / 9 * 14 / 22, 5.0 / 9, 0x0D5EAF),
        .box(0, 5.0 / 9 * 0.4, 5.0 / 9 * 14 / 22, 5.0 / 9 * 0.2, 0xFFFFFF),
        .box(5.0 / 9 * 14 / 22 * 0.4, 0, 5.0 / 9 * 14 / 22 * 0.2, 5.0 / 9, 0xFFFFFF),
    ]), sample: "691 234 5678"),
    .init(iso: "HR", name: "Croatia", dial: "+385", nsnMin: 8, nsnMax: 9,
          flag: .layers([
        .stripes(horizontal: true, colors: [0xFF0000, 0xFFFFFF, 0x171796]),
        .box(0.39, 0.22, 0.22, 0.56, 0xFFFFFF),
        .checks(0.39, 0.22, 0.22, 0.56, 4, 0xFF0000, 0xFFFFFF),
    ]), sample: "91 234 5678"),
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
          flag: .layers([
        .stripes(horizontal: false, colors: [0x006600, 0x006600, 0xFF0000, 0xFF0000, 0xFF0000]),
        // The armillary sphere reduces to its ring. A shield drawn inside it at this size reads
        // as a logo rather than a coat of arms, so it is left off.
        .ring(0.40, 0.5, 0.28, 0.10, 0xFFE900),
    ]), sample: "912 345 678"),
    .init(iso: "RO", name: "Romania", dial: "+40", nsnMin: 9, nsnMax: 9,
          flag: bandsV(0x002B7F, 0xFCD116, 0xCE1126), sample: "712 345 678"),
    .init(iso: "RS", name: "Serbia", dial: "+381", nsnMin: 8, nsnMax: 9,
          flag: .layers([
        .stripes(horizontal: true, colors: [0xC6363C, 0x0C4076, 0xFFFFFF]),
    ]), sample: "60 1234567"),
    .init(iso: "SE", name: "Sweden", dial: "+46", nsnMin: 9, nsnMax: 9,
          flag: .cross(bg: 0x006AA7, arm: 0xFECC00), sample: "70 123 45 67"),
    .init(iso: "SI", name: "Slovenia", dial: "+386", nsnMin: 8, nsnMax: 8,
          flag: .code, sample: "31 234 567"),
    .init(iso: "SK", name: "Slovakia", dial: "+421", nsnMin: 9, nsnMax: 9,
          flag: .code, sample: "912 123 456"),
    .init(iso: "TR", name: "Türkiye", dial: "+90", nsnMin: 10, nsnMax: 10,
          flag: .layers([
        .fill(0xE30A17),
        .disc(0.40, 0.5, 0.26, 0xFFFFFF),
        .disc(0.455, 0.5, 0.21, 0xE30A17),
        .star(0.63, 0.5, 0.13, 0xFFFFFF),
    ]), sample: "501 234 56 78"),
    .init(iso: "UA", name: "Ukraine", dial: "+380", nsnMin: 9, nsnMax: 9,
          flag: bandsH(0x0057B7, 0xFFDD00), sample: "50 123 4567"),
    .init(iso: "US", name: "United States", dial: "+1", nsnMin: 10, nsnMax: 10,
          flag: .layers([
        .stripes(horizontal: true, colors: [
            0xB31942, 0xFFFFFF, 0xB31942, 0xFFFFFF, 0xB31942, 0xFFFFFF, 0xB31942,
            0xFFFFFF, 0xB31942, 0xFFFFFF, 0xB31942, 0xFFFFFF, 0xB31942,
        ]),
        .box(0, 0, 0.40, 7.0 / 13, 0x0A3161),
    ]), sample: "201 555 0123"),
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
