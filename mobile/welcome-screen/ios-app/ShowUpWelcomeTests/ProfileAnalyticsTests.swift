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
        // enums.json bumped 1.2.0 -> 1.3.0 when the identifiers were corrected, and 1.3.1 ->
        // 1.4.2 on 16 September 2026, which unified `dismiss_method` across every bottom sheet,
        // added the `prompts_below_minimum` rule, scoped `prompt_topic_selected` to suggestion and
        // browse, and re-verified `prompts` at step_index 2. Stamping the old string would claim a
        // vocabulary these payloads are not using — and this test is the thing that noticed, which
        // is exactly what it is for.
        XCTAssertEqual(Stamp.fieldRegistryVersion, "1.4.2")
    }

    // MARK: - consent_changed, unblocked by registry 1.3.0
    //
    // The unblocking was 1.3.0's; the stamp on the payload is whatever the CURRENT bundle is, and
    // those are two different facts. A test that pinned the stamp to the version that unblocked
    // the event would fail on every later bump for no reason.

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
        XCTAssertEqual(payload["field_registry_version"] as? String, "1.4.2")
    }

    // MARK: - the rule the ticket calls a bug to break

    func testEmailValidationOnlyEverReportsFormatNeverDisposable() {
        // "disposable is reserved in the enum and deliberately not implemented... emitting it today
        // is a bug" — SHOWUP-152.
        let (_, payload) = ProfileAnalytics.emailValidationFailed()
        XCTAssertEqual(payload["rule"] as? String, "format")
    }

    // MARK: - SHOWUP-155 · the bridge is a screen and NOT a step

    func testTheBridgeHasAScreenRowAndReportsTheBuildVariant() {
        let (name, payload) = ProfileAnalytics.embraceBridgeViewed()
        XCTAssertEqual(name, "embrace_bridge_viewed")
        XCTAssertEqual(payload["variant"] as? String, "build_profile")
        XCTAssertEqual(payload["sensitivity_class"] as? Int, 0)
    }

    func testTheBridgeScreenCarriesBothRegistryVocabularies() {
        let (_, payload) = ProfileAnalytics.screenViewed(.embraceBuild)
        // §11: screen_id is the stable key, screen_name the human label. Two vocabularies, and
        // collapsing them breaks one of the two uses.
        XCTAssertEqual(payload["screen_id"] as? String, "profile_embrace_build")
        XCTAssertEqual(payload["screen_name"] as? String, "ProfileEmbraceBuild")
    }

    func testNoBasicsStepExistsForTheBridgeSoNoStepEventCanNameIt() {
        // The registry's own words: the §2 row is deliberately absent "because firing
        // profile_step_viewed on it would put a phantom step in the completion funnel". The rule
        // is carried by the type system — `stepViewed` takes a BasicsStep and there is none — and
        // this test is what would notice somebody adding one.
        XCTAssertEqual(BasicsStep.allCases.map(\.stepId),
                       ["name", "email", "email_verify", "dob"])
    }

    func testTheSiblingBridgeVariantIsRegisteredButUnused() {
        // §16 is a closed pair. It lives in one place so the sibling bridge does not arrive with
        // its value typed into a second ticket, which is how a vocabulary drifts.
        XCTAssertEqual(EmbraceVariant.addDetails, "add_details")
        let (_, payload) = ProfileAnalytics.embraceBridgeViewed(variant: EmbraceVariant.addDetails)
        XCTAssertEqual(payload["variant"] as? String, "add_details")
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
