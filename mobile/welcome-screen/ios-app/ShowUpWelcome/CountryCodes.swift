//  CountryCodes.swift
//  ShowUp · every country, and the rules for each (SHOWUP-143)
//
//  NOTHING HERE COSTS MONEY.
//
//  Country calling codes are public ITU assignments (recommendation E.164) — not licensed, not
//  metered, not behind anyone's API. The rules come from Google's libphonenumber metadata, carried
//  here by PhoneNumberKit, which is MIT: it ships inside the app, so there is no server to meter
//  and nothing to bill. The paid service in this flow is Twilio, and it is paid for *delivering the
//  SMS*.
//
//  WHY IT REPLACED A HAND-WRITTEN TABLE
//
//  This file used to carry 35 countries with hand-written length rules — a third of the world, and
//  the rules were wrong twice over. Both were shipped bugs: the lengths described every kind of
//  number rather than mobiles (so a six-digit German landline passed as a number we could text),
//  and 250 countries could never have been maintained this way. Writing those rules by hand means
//  guessing, and a wrong guess REJECTS A REAL PERSON'S REAL NUMBER, which is the worst failure this
//  screen has.
//
//  This is the deliberate twin of CountryCodes.kt, which has been on libphonenumber since it was
//  written. Android had every country and iOS had 35; that was the last real gap between the two
//  platforms on this screen, and it is closed. Both now derive the list, the names, the lengths,
//  the type, the trunk prefix and the grouping from the same metadata.

import Foundation
import PhoneNumberKit

// `PhoneNumberUtility` is a final class with no Sendable conformance, so a plain global would be a
// data-race error the day the language mode moves to 6. It is on the main actor instead, which is
// where every caller already is: this screen and its tests. Parsing the metadata is not cheap, so
// it is built once rather than per call.
@MainActor let phoneUtility = PhoneNumberUtility()

/// One country in the picker.
///
/// Everything is derived: `name` from the device's own locale data, so it appears in the user's
/// language; `dial` and every rule from the phone metadata. The flag is looked up by `iso` against
/// the bundled artwork — see CountryPicker.swift.
struct Country: Identifiable, Equatable {
    let iso: String
    let name: String
    let dial: String

    var id: String { iso }

    /// An example mobile number for this country, shown in the empty field.
    ///
    /// Fetched on demand rather than stored: building 250 of these up front costs real time at
    /// launch, and only the selected country's example is ever shown.
    @MainActor var sample: String { exampleMobile(iso) }
}

/// Every region the metadata knows, sorted by name in the user's own language.
///
/// Sorting is locale-aware — an alphabetical sort of German names is not the same order as English
/// ones, and a list sorted by the wrong alphabet is hard to scan.
@MainActor let COUNTRIES: [Country] = {
    phoneUtility.allCountries()
        // "001" and friends are non-geographic entries in the metadata — satellite and shared
        // ranges with no flag, no name and nobody to text. Regions only.
        .filter { $0.count == 2 && $0.allSatisfy(\.isLetter) }
        .map { iso in
            Country(iso: iso,
                    name: Locale.current.localizedString(forRegionCode: iso) ?? iso,
                    dial: "+\(phoneUtility.countryCode(for: iso) ?? 0)")
        }
        .sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
}()

/// Germany is the launch market, so it is the fallback when the device locale says nothing useful.
@MainActor let DEFAULT_COUNTRY: Country = countryForRegion("DE")

/// The country for a device region, or Germany.
///
/// SHOWUP-143 asks the pill to default from device locale. The region the platform reports is the
/// same key the metadata uses, so this is a lookup rather than a guess.
@MainActor func countryForRegion(_ region: String?) -> Country {
    if let region, let hit = COUNTRIES.first(where: { $0.iso.caseInsensitiveCompare(region) == .orderedSame }) {
        return hit
    }
    // Force-unwrapped deliberately: DE missing would mean the metadata itself failed to load, and
    // a country picker with no countries is not a state worth limping along in.
    return COUNTRIES.first { $0.iso == "DE" }!
}

/// How many digits past a country's longest valid number the field will accept.
///
/// Not zero, and that is the point. The field used to cap at exactly the maximum, which silently
/// ate the extra keystrokes — so a too-long number could not be typed, `.tooLong` could never fire,
/// and the user watched their own digits disappear with no explanation.
let OVERTYPE_ALLOWANCE = 4

/// The most digits a national number can have anywhere, from E.164: fifteen including the country
/// code. Used only to bound the input; the metadata does the real rejecting.
let E164_MAX_DIGITS = 15

/// Why a number was rejected. Each maps to one message, and each is something the user can act on.
///
/// There is no `leadingZero` case any more, and its absence is the fix rather than an omission. The
/// old message — "Leave out the first 0, +49 already covers it" — was scolding people for writing
/// their own number the way their country writes it, and it was simply wrong in the NANP, where
/// there is no trunk prefix at all and +1 covers nothing of the sort. Parsing with a region strips
/// the trunk prefix per that country's real dialling rules, so a German typing 0176… is correct now
/// and nobody is told off for it.
enum PhoneError: Equatable {
    case empty, tooShort, tooLong, invalidLength, unrecognised, notANumber, notMobile

    @MainActor func message(_ country: Country) -> String {
        switch self {
        case .empty: return "Enter your phone number to continue."
        case .notANumber: return "Numbers only, please. For example \(country.sample)."
        case .tooShort: return "That looks too short for \(country.name). For example \(country.sample)."
        case .tooLong: return "That looks too long for \(country.name). For example \(country.sample)."
        // The metadata distinguishes "wrong length" from "too short" — some countries have valid
        // lengths with gaps in between, and "too short" would be a lie for a number in one of them.
        case .invalidLength: return "That is not a valid length for \(country.name). For example \(country.sample)."
        // Right length, wrong number -- almost always a prefix that country does not issue.
        case .unrecognised: return "That doesn’t look like a mobile number in \(country.name). For example \(country.sample)."
        // The one rule the metadata knows and a length check never could.
        case .notMobile: return "That looks like a landline. We need a mobile number to text the code to."
        }
    }
}

/// Validation, run on submit rather than per keystroke — SHOWUP-143 requires that, and it is the
/// kinder behaviour: nobody wants to be told their number is wrong while they are still typing.
///
/// Deliberately the same shape and the same order as `validate` in CountryCodes.kt.
@MainActor func validate(_ raw: String, _ country: Country) -> PhoneError? {
    // Letters first. Stripping them and then reporting "empty" would answer a question the user
    // did not ask -- they typed something, it was just the wrong something.
    if raw.contains(where: \.isLetter) { return .notANumber }
    let digits = raw.filter(\.isNumber)
    if digits.isEmpty { return .empty }

    // TYPE FIRST, then length. A German landline is eight digits and a German mobile is ten or
    // eleven, so a length check reaches it first and reports "too short" — true, but useless to
    // someone who has correctly typed the landline they own. Asking "is this a real number here,
    // and what kind?" before "is it the right length for a mobile?" produces the message that
    // actually helps: we need a mobile.
    if let parsed = try? phoneUtility.parse(digits, withRegion: country.iso) {
        switch parsed.type {
        // fixedOrMobile is allowed: in several countries the ranges overlap and the metadata
        // genuinely cannot tell them apart. Refusing there would reject people holding perfectly
        // good mobiles.
        case .mobile, .fixedOrMobile: return nil
        default: return .notMobile
        }
    }

    // Not a real number for this country. Now say why, measured against a MOBILE — that is what the
    // user is being asked for, so it is the only comparison that means anything to them.
    return lengthVerdict(for: digits, in: country)
}

/// Why a number that failed to parse is the wrong length, or `.unrecognised` if the length is fine.
///
/// The national significant number, not the raw digits: a German who typed 0176… has a trunk prefix
/// in there, and measuring that against the mobile lengths would call a correct number too long.
@MainActor private func lengthVerdict(for digits: String, in country: Country) -> PhoneError {
    let formatter = PartialFormatter(utility: phoneUtility, defaultRegion: country.iso, withPrefix: false)
    let national = formatter.nationalNumber(from: digits).filter(\.isNumber)
    let lengths = phoneUtility
        .possiblePhoneNumberLengths(forCountry: country.iso, phoneNumberType: .mobile, lengthType: .national)
        .sorted()
    // A country with no published mobile lengths tells us nothing, so neither do we.
    guard let shortest = lengths.first, let longest = lengths.last else { return .unrecognised }

    if national.count < shortest { return .tooShort }
    if national.count > longest { return .tooLong }
    // Inside the range but not one of the listed lengths -- the gap case InvalidLength exists for.
    if !lengths.contains(national.count) { return .invalidLength }
    // The right length for a mobile, but not a number this country issues — almost always a prefix
    // that does not exist.
    return .unrecognised
}

/// Groups the digits the way that country writes them, as they are typed.
///
/// `withPrefix: false` rather than formatting an international number and stripping the dial code
/// back off: the pill already shows +49, and a national format that included the country's own
/// trunk prefix would put `0` and `+49` on screen at once, which is a number that dials nowhere.
@MainActor func formatNational(_ digits: String, _ country: Country) -> String {
    guard !digits.isEmpty else { return "" }
    let formatter = PartialFormatter(utility: phoneUtility, defaultRegion: country.iso, withPrefix: false)
    return formatter.formatPartial(digits)
}

/// An example mobile number for a region, grouped and without the trunk prefix — the same shape the
/// user is being asked to type.
@MainActor private func exampleMobile(_ iso: String) -> String {
    guard let example = phoneUtility.getExampleNumber(forCountry: iso, ofType: .mobile) else { return "" }
    let dial = "+\(example.countryCode)"
    return phoneUtility.format(example, toType: .international)
        .replacingOccurrences(of: dial, with: "")
        .trimmingCharacters(in: .whitespaces)
}
