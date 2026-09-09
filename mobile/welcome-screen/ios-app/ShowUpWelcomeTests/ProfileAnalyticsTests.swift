import XCTest
@testable import ShowUpWelcome

/// The tracking vocabulary, pinned to registry 1.3.0. The iOS half of `ProfileAnalyticsTest.kt`.
///
/// WHY THESE EXIST
///
/// Every value here came from a JSON file that shipped four times with a version badge
/// concatenated into its identifiers — `emailv1.2`, `agev1.2`, `dobv1.2` — and the corruption
/// survived two bundle refreshes before it was fixed. These assertions are what make the next such
/// drift a failing build rather than a payload nobody reads until a funnel is already broken.
///
/// They test pure builders. Neither app has an analytics sink yet (brief Step 2, its own ticket),
/// so nothing here proves an event was delivered — only that if it were, it would carry the
/// vocabulary the registry defines.
final class ProfileAnalyticsTests: XCTestCase {

    // MARK: - the vocabulary that was corrupted

    func testStepIdsCarryNoVersionSuffix() {
        // The exact defect: enums.json §2 read "namev1.2", "emailv1.2", "email_verifyv1.2",
        // "dobv1.2". Fixed in registry 1.3.0; this is what keeps it fixed.
        let ids = BasicsStep.allCases.map(\.stepId)
        XCTAssertEqual(ids, ["name", "email", "email_verify", "dob"])
        for id in ids {
            XCTAssertNil(
                id.range(of: #"v\d+\.\d+$"#, options: .regularExpression),
                "'\(id)' carries a version badge"
            )
        }
    }

    func testStepIndicesMatchTheRegistryWithEmailVerifyHoldingAtTwo() {
        // §2 gives 1 / 2 / 2 / 3. email_verify sharing 2 with email is the whole reason
        // screen_viewed and profile_step_viewed are not duplicates of one another.
        XCTAssertEqual(BasicsStep.allCases.map(\.stepIndex), [1, 2, 2, 3])
    }

    func testTheFieldRegistryVersionMatchesTheBundleThatDefinedTheVocabulary() {
        // enums.json bumped 1.2.0 -> 1.3.0 when the identifiers were corrected. Stamping the old
        // string would claim a vocabulary these payloads are not using.
        XCTAssertEqual(Stamp.fieldRegistryVersion, "1.3.0")
    }

    // MARK: - consent_changed, unblocked by registry 1.3.0

    func testConsentUsesTheMarketingEmailChannelNotEmail() {
        // One address, three uses. "email" is the Stay reachable toggle about match contact;
        // transactional mail has no consent at all.
        let (_, payload) = ProfileAnalytics.consentChanged(on: true)
        XCTAssertEqual(payload["channel"] as? String, "marketing_email")
    }

    func testConsentDefaultsToTheProfileCreationSurface() {
        let (_, payload) = ProfileAnalytics.consentChanged(on: true)
        XCTAssertEqual(payload["surface"] as? String, "profile_creation")
    }

    func testConsentFiresInBothDirections() {
        // Unlike field_display_opted_out, which is opt-out only. A consent record that logs the
        // grant but not the withdrawal cannot answer "was this person opted in on date X".
        let (nameOn, on) = ProfileAnalytics.consentChanged(on: true)
        let (nameOff, off) = ProfileAnalytics.consentChanged(on: false)
        XCTAssertEqual(nameOn, nameOff)
        XCTAssertEqual(on["on"] as? Bool, true)
        XCTAssertEqual(off["on"] as? Bool, false)
    }

    func testConsentIsAClassOneAttributeEvent() {
        let (_, payload) = ProfileAnalytics.consentChanged(on: true)
        XCTAssertEqual(payload["sensitivity_class"] as? Int, 1)
        XCTAssertEqual(payload["field_registry_version"] as? String, "1.3.0")
    }

    // MARK: - the rule the ticket calls a bug to break

    func testEmailValidationOnlyEverReportsFormatNeverDisposable() {
        // "disposable is reserved in the enum and deliberately not implemented... emitting it today
        // is a bug" — SHOWUP-152.
        let (_, payload) = ProfileAnalytics.emailValidationFailed()
        XCTAssertEqual(payload["rule"] as? String, "format")
    }

    // MARK: - privacy

    func testNoProfilePayloadCarriesAnAddressANameOrARawDate() {
        let payloads: [[String: any Sendable]] = [
            ProfileAnalytics.nameSubmitted(charCount: 3).1,
            ProfileAnalytics.emailSubmitted(domain: "hey.com").1,
            ProfileAnalytics.consentChanged(on: true).1,
            ProfileAnalytics.screenViewed(.email).1,
        ]
        for payload in payloads {
            let rendered = payload.values.map { "\($0)" }.joined(separator: " ")
            for secret in ["leo@hey.com", "Leo", "1998-04-23"] {
                XCTAssertFalse(rendered.contains(secret), "payload leaked '\(secret)': \(payload)")
            }
        }
        // The domain is the one part of an address that does travel — a disposable-domain rate is
        // answerable, a list of addresses is not something we collect.
        let (_, submitted) = ProfileAnalytics.emailSubmitted(domain: "hey.com")
        XCTAssertEqual(submitted["domain"] as? String, "hey.com")
    }
}
