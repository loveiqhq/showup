//  PhoneValidationTests.swift
//  ShowUp · the phone rules, checked against the metadata's own data (SHOWUP-143)
//
//  The Swift twin of PhoneValidationTest.kt, deliberately case for case and name for name, so the
//  two can be read side by side and a difference is obvious rather than buried.
//
//  The bar this file exists to hold: the previous hand-written table shipped two bugs, and both were
//  "the rule was wrong", not "the code was wrong". So the interesting assertions are not that the
//  function runs, but that it accepts numbers real people hold and rejects ones that cannot receive
//  an SMS.

import XCTest
import PhoneNumberKit
@testable import ShowUpWelcome

@MainActor
final class PhoneValidationTests: XCTestCase {

    private var util: PhoneNumberUtility { phoneUtility }
    private func country(_ iso: String) -> Country { countryForRegion(iso) }

    /// A country's example number of a given type, written the way this screen asks for it:
    /// international, with the dial code taken back off.
    private func example(_ iso: String, _ type: PhoneNumberType) -> String? {
        guard let number = util.getExampleNumber(forCountry: iso, ofType: type) else { return nil }
        return util.format(number, toType: .international)
            .replacingOccurrences(of: "+\(number.countryCode)", with: "")
            .trimmingCharacters(in: .whitespaces)
    }

    // MARK: - the list

    func testEveryCountryTheMetadataKnowsIsOffered() {
        XCTAssertTrue(COUNTRIES.count > 200, "expected the full world, got \(COUNTRIES.count)")
    }

    func testEveryCountryHasANameAndADialCode() {
        for c in COUNTRIES {
            XCTAssertFalse(c.name.isEmpty, "\(c.iso) has no name")
            XCTAssertTrue(c.dial.hasPrefix("+") && c.dial.dropFirst().allSatisfy(\.isNumber),
                          "\(c.iso) has a malformed dial code: \(c.dial)")
            XCTAssertNotEqual(c.dial, "+0", "\(c.iso) has no dial code at all")
        }
    }

    func testTheListIsSortedByName() {
        for (a, b) in zip(COUNTRIES, COUNTRIES.dropFirst()) {
            XCTAssertTrue(a.name.localizedStandardCompare(b.name) != .orderedDescending,
                          "\(a.name) sorts after \(b.name)")
        }
    }

    func testTheLaunchMarketIsPresentAndIsTheFallback() {
        XCTAssertEqual(DEFAULT_COUNTRY.iso, "DE")
        XCTAssertEqual(DEFAULT_COUNTRY.dial, "+49")
        // An unknown or absent device region must not crash or pick something arbitrary.
        XCTAssertEqual(countryForRegion(nil).iso, "DE")
        XCTAssertEqual(countryForRegion("ZZ").iso, "DE")
        XCTAssertEqual(countryForRegion("").iso, "DE")
    }

    // MARK: - the two bugs that shipped

    func testAGermanLandlineIsRejectedBecauseItCannotReceiveAText() throws {
        // The original bug: the table allowed six digits for Germany because landlines can be that
        // short, so this passed and the user waited for a code that could never arrive.
        //
        // The metadata's own fixed-line example is used rather than a number picked by hand: a
        // short landline is caught by the length rule anyway, which would let this test pass for
        // the wrong reason. A real landline of full length can only be caught by knowing the type.
        let landline = try XCTUnwrap(example("DE", .fixedLine))
        XCTAssertEqual(validate(landline, country("DE")), .notMobile)
    }

    func testLandlinesAreRejectedInEveryCountryThatDistinguishesThem() {
        // Skipped where the ranges genuinely overlap: in those countries the metadata cannot tell a
        // landline from a mobile either, and refusing would reject people holding good numbers.
        var accepted: [String] = []
        for c in COUNTRIES {
            guard let fixed = util.getExampleNumber(forCountry: c.iso, ofType: .fixedLine),
                  fixed.type == .fixedLine,
                  let national = example(c.iso, .fixedLine)
            else { continue }
            if validate(national, c) == nil { accepted.append("\(c.iso) \(c.name): \(national)") }
        }
        XCTAssertTrue(accepted.isEmpty, "landlines accepted:\n" + accepted.joined(separator: "\n"))
    }

    func testATooLongNumberIsRejected() {
        // The other original bug: the field capped at the maximum, so this could not be typed and
        // the error was unreachable. The cap now allows over-typing so the rule can fire.
        XCTAssertEqual(validate("176123456789012", country("DE")), .tooLong)
    }

    func testATooShortNumberIsRejected() {
        XCTAssertEqual(validate("1761", country("DE")), .tooShort)
    }

    // MARK: - the cases a real person hits

    func testARealGermanMobileIsAccepted() {
        XCTAssertNil(validate("17612345678", country("DE")))
    }

    func testTheNationalTrunkZeroIsAcceptedNotScolded() {
        // Typing 0176... is how a German writes their own number. The metadata strips the trunk
        // prefix per that country's dialling rules, so this is simply correct now -- and this is
        // the test that stands in for the deleted `leadingZero` error.
        XCTAssertNil(validate("01761234567", country("DE")))
    }

    func testSpacesAndPunctuationAreIgnored() {
        XCTAssertNil(validate("176 123 456 78", country("DE")))
    }

    func testLettersAreReportedAsLettersNotAsAnEmptyField() {
        XCTAssertEqual(validate("abcdefghij", country("DE")), .notANumber)
    }

    func testAnEmptyFieldSaysSo() {
        XCTAssertEqual(validate("", country("DE")), .empty)
        XCTAssertEqual(validate("   ", country("DE")), .empty)
    }

    // MARK: - the whole world, not just the launch market

    func testTheMetadatasOwnExampleMobileIsAcceptedForEveryCountry() {
        // The strongest check available: every country's canonical mobile number, taken from the
        // metadata itself, must pass our validation. If any fails, the rules reject a number the
        // authority on the subject says is valid.
        var rejected: [String] = []
        for c in COUNTRIES {
            guard let number = util.getExampleNumber(forCountry: c.iso, ofType: .mobile) else { continue }
            let national = util.format(number, toType: .national)
            if let verdict = validate(national, c) {
                rejected.append("\(c.iso) \(c.name): \(national) -> \(verdict)")
            }
        }
        XCTAssertTrue(rejected.isEmpty, "rejected valid mobiles:\n" + rejected.joined(separator: "\n"))
    }

    func testTheExampleShownInTheEmptyFieldPassesThatCountrysOwnRule() {
        // The placeholder is the number we invite the user to copy. If it fails validation the
        // screen is asking for something it will then reject.
        let bad = COUNTRIES
            .filter { !$0.sample.isEmpty }
            .compactMap { c in validate(c.sample, c).map { "\(c.iso): '\(c.sample)' -> \($0)" } }
        XCTAssertTrue(bad.isEmpty, "bad placeholders:\n" + bad.joined(separator: "\n"))
    }

    // MARK: - formatting

    func testDigitsAreGroupedTheWayTheCountryWritesThem() {
        let formatted = formatNational("17612345678", country("DE"))
        XCTAssertTrue(formatted.contains(" "), "expected grouping, got '\(formatted)'")
        XCTAssertEqual(String(formatted.filter(\.isNumber)), "17612345678")
    }

    func testFormattingAnEmptyStringDoesNotCrash() {
        XCTAssertEqual(formatNational("", country("DE")), "")
    }

    func testEveryCountryCanFormAPartialNumberWithoutThrowing() {
        for c in COUNTRIES {
            XCTAssertFalse(formatNational("12345", c).isEmpty, "\(c.iso) formatted to nothing")
        }
    }

    // MARK: - the caret, which is why the field is a UITextField

    func testTheCaretIsMappedByDigitsNotByCharacters() {
        // Android does this with an OffsetMapping; this is the same map. "201 555 0123" -- three
        // digits in, the caret belongs after "201" at offset 3, not at 4 where a naive
        // character count would put it once the space appears.
        let shown = "201 555 0123"
        XCTAssertEqual(PhoneNumberField.Coordinator.offset(afterDigits: 0, in: shown), 0)
        XCTAssertEqual(PhoneNumberField.Coordinator.offset(afterDigits: 3, in: shown), 3)
        XCTAssertEqual(PhoneNumberField.Coordinator.offset(afterDigits: 4, in: shown), 5)
        XCTAssertEqual(PhoneNumberField.Coordinator.offset(afterDigits: 6, in: shown), 7)
        // Past the end is clamped rather than crashing.
        XCTAssertEqual(PhoneNumberField.Coordinator.offset(afterDigits: 99, in: shown), shown.count)
    }
}
