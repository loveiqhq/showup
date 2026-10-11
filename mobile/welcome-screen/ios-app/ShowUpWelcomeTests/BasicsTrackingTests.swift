//  BasicsTrackingTests.swift
//  ShowUp · "The basics" reports what its tickets specify (SHOWUP-150 to 155, events.json family D)
//
//  Until 8 October 2026 none of this fired: the builders existed and no call site did. These drive
//  the model through each decision — arrival, accepted and refused presses, the code's verdict, the
//  date's states — and check the event, its payload, and what must NOT be sent.
//  Android twin: `BasicsTrackingTest.kt`.

import XCTest
import os
import OpenAPIRuntime
import HTTPTypes
import ShowUpAPI
@testable import ShowUpWelcome

/// Records events; the model reports on the main actor.
@MainActor
private final class Recorder: AnalyticsTracking {
    private(set) var events: [(String, [String: any Sendable])] = []
    nonisolated init() {}
    nonisolated func track(_ event: String, properties: [String: any Sendable]) {
        MainActor.assumeIsolated { events.append((event, properties)) }
    }
    var names: [String] { events.map(\.0) }
    func of(_ name: String) -> [[String: any Sendable]] { events.filter { $0.0 == name }.map(\.1) }
}

/// A server that answers each operation from a script, in order.
private struct ScriptedTransport: ClientTransport {
    actor Script {
        private var answers: [String: [(Int, String)]] = [:]
        func add(_ operation: String, _ status: Int, _ body: String) {
            answers[operation, default: []].append((status, body))
        }
        func next(_ operation: String) -> (Int, String) {
            guard var queue = answers[operation], !queue.isEmpty else { return (500, "{}") }
            let first = queue.removeFirst()
            answers[operation] = queue
            return first
        }
    }
    let script: Script

    func send(_ request: HTTPRequest, body: HTTPBody?, baseURL: URL,
              operationID: String) async throws -> (HTTPResponse, HTTPBody?) {
        let (status, text) = await script.next(operationID)
        return (HTTPResponse(status: .init(code: status), headerFields: [.contentType: "application/json"]),
                status == 204 ? nil : HTTPBody(text))
    }
}

@MainActor
final class BasicsTrackingTests: XCTestCase {

    private var recorder: Recorder!
    private var script: ScriptedTransport.Script!
    private var model: BasicsModel!
    /// The model's clock, readable from its `@Sendable` closure without an unchecked conformance.
    private let clock = OSAllocatedUnfairLock(initialState: Date(timeIntervalSince1970: 1_000))

    /// The resend window is already open, so the countdown ends at once.
    private var challenge: String {
        let iso = ISO8601DateFormatter()
        return #"{"expiresAt":"\#(iso.string(from: Date().addingTimeInterval(600)))","#
            + #""resendAvailableAt":"\#(iso.string(from: Date().addingTimeInterval(-1)))"}"#
    }

    private func profileJSON(age: Int) -> String {
        """
        {"id":"p1","displayName":"Leo","age":\(age),"gender":null,"lookingFor":null,\
        "isVisible":true,"hiddenFields":[],"isComplete":false,\
        "verificationStatus":"none","heightCm":null,"orientation":null,\
        "datingLanguages":null,"education":null,"religion":null,"politics":null}
        """
    }

    override func setUp() async throws {
        recorder = Recorder()
        script = ScriptedTransport.Script()
        let api = ShowUpAPI(tokens: InMemoryTokenStore(accessToken: "t"),
                            transport: ScriptedTransport(script: script))
        model = BasicsModel(repo: BasicsRepository(api: api), analytics: recorder)
        let clock = self.clock
        model.now = { clock.withLock { $0 } }
    }

    /// Waits for the model's request task to land, bounded.
    private func settle(until condition: () -> Bool) async {
        let deadline = ContinuousClock.now + .seconds(5)
        while !condition(), ContinuousClock.now < deadline, !Task.isCancelled {
            try? await Task.sleep(for: .milliseconds(10))
        }
    }

    // MARK: name

    func testArrivingAtTheNameStepReportsTheScreenTheStepAndStartsTheBuildOnce() {
        model.arrived(.name, referrer: nil)

        XCTAssertEqual(recorder.names, ["screen_viewed", "profile_step_viewed", "profile_build_started"])
        XCTAssertEqual(recorder.of("screen_viewed")[0]["screen_id"] as? String, "profile_name")
        XCTAssertEqual(recorder.of("profile_step_viewed")[0]["step_id"] as? String, "name")
        XCTAssertEqual(recorder.of("profile_build_started")[0]["entry_point"] as? String, "cold")

        model.arrived(.name, referrer: .email)
        XCTAssertEqual(recorder.of("profile_build_started").count, 1, "the build starts once")
        XCTAssertEqual(recorder.of("screen_viewed").last?["referrer_screen_id"] as? String, "profile_email")
    }

    func testAValidNameSendsItsLengthAndTheStepsTimeNeverTheName() {
        model.arrived(.name, referrer: nil)
        clock.withLock { $0 = $0.addingTimeInterval(7.4) }
        model.nameAccepted("  Leonora ")

        let submitted = recorder.of("name_submitted")[0]
        XCTAssertEqual(submitted["char_count"] as? Int, 7)
        XCTAssertFalse(submitted.values.contains { ($0 as? String)?.contains("Leonora") == true })
        XCTAssertEqual(recorder.of("profile_step_completed")[0]["time_on_step_s"] as? Int, 7)
    }

    func testAnEmptyNameReportsTheRefusedPress() {
        model.nameRefused()
        let failed = recorder.of("form_validation_failed")[0]
        XCTAssertEqual(failed["field_id"] as? String, "first_name")
        XCTAssertEqual(failed["rule"] as? String, "required_missing")
        XCTAssertEqual(failed["screen_id"] as? String, "profile_name")
    }

    // MARK: email

    func testADeliveredCodeReportsTheDomainOnlyAndCompletesTheEmailStep() async {
        await script.add("startEmailVerification", 200, challenge)
        model.arrived(.email, referrer: .name)
        model.email = "Leo@Hey.COM"
        var sent = false
        model.sendCode { sent = true }
        await settle { sent }

        XCTAssertTrue(sent)
        let submitted = recorder.of("email_submitted")
        XCTAssertEqual(submitted.first?["domain"] as? String, "hey.com")
        XCTAssertFalse(submitted.first?.values.contains { ($0 as? String)?.contains("@") == true } ?? true)
        XCTAssertEqual(recorder.of("profile_step_completed").first?["step_id"] as? String, "email")
    }

    func testEmptyIsRequiredMissingMalformedIsFormat() {
        model.emailRefused(empty: true)
        model.emailRefused(empty: false)
        XCTAssertEqual(recorder.of("form_validation_failed")[0]["field_id"] as? String, "email")
        XCTAssertEqual(recorder.of("email_validation_failed")[0]["rule"] as? String, "format")
    }

    func testTheMarketingRowReportsBothDirections() {
        model.marketingConsentChanged(true)
        model.marketingConsentChanged(false)
        let changes = recorder.of("consent_changed")
        XCTAssertEqual(changes.map { $0["on"] as? Bool }, [true, false])
        XCTAssertTrue(changes.allSatisfy { $0["channel"] as? String == "marketing_email" })
    }

    // MARK: the code

    private func sendFirstCode() async {
        await script.add("startEmailVerification", 200, challenge)
        model.email = "leo@hey.com"
        var sent = false
        model.sendCode { sent = true }
        await settle { sent }
    }

    func testEachAttemptReportsItsNumberAndVerdictAndAPassCompletesTheStep() async {
        await sendFirstCode()
        // 401 is how the server refuses a wrong code; the repository reads it as `.refused`.
        await script.add("verifyEmail", 401, #"{"statusCode":401,"message":"Invalid code","error":"Unauthorized"}"#)
        await script.add("verifyEmail", 204, "")
        model.arrived(.emailVerify, referrer: .email)

        model.codeDigits = "111111"
        model.verify {}
        await settle { recorder.of("email_code_submitted").count == 1 && !model.busy }
        model.codeDigits = "482913"
        var verified = false
        model.verify { verified = true }
        await settle { verified }

        let attempts = recorder.of("email_code_submitted")
        XCTAssertEqual(attempts.map { $0["attempt_no"] as? Int }, [1, 2])
        XCTAssertEqual(attempts.map { $0["result"] as? String }, ["fail", "pass"])
        XCTAssertFalse(attempts.contains { $0.values.contains { ($0 as? String) == "482913" } })
        XCTAssertEqual(recorder.of("profile_step_viewed").last?["step_id"] as? String, "email_verify")
        XCTAssertEqual(recorder.of("profile_step_completed").last?["step_id"] as? String, "email_verify")
    }

    func testAResendReportsWhichResendItIs() async {
        await sendFirstCode()
        await script.add("startEmailVerification", 200, challenge)
        model.resendCode()
        await settle { recorder.of("email_code_resent").count == 1 && !model.busy }
        await script.add("startEmailVerification", 200, challenge)
        model.resendCode()
        await settle { recorder.of("email_code_resent").count == 2 }

        XCTAssertEqual(recorder.of("email_code_resent").map { $0["attempt_no"] as? Int }, [1, 2])
        XCTAssertEqual(recorder.of("email_submitted").count, 1, "a resend is not a second submit")
    }

    // MARK: date of birth

    func testAnImpossibleDateAndAnUnder18DateEachReportOnceOnTheWayIn() {
        model.dobOrder = .dayFirst
        model.arrived(.dob, referrer: .emailVerification)
        model.dobTyped("3102")
        model.dobTyped("31021998") // 31 February: state C
        model.dobTyped("31021998") // still C — a redraw, not a transition
        model.dobTyped("3102199")  // back to incomplete
        model.dobTyped("01012015") // a real date, under 18: state D

        XCTAssertEqual(recorder.of("dob_validation_failed").count, 1)
        XCTAssertEqual(recorder.of("dob_validation_failed")[0]["rule"] as? String, "impossible")
        let age = recorder.of("age_gate_failed").first?["age"] as? Int ?? -1
        XCTAssertTrue((10...12).contains(age), "an age, not a date")
    }

    func testARestoredDateReportsNothingAndIsTheBaseline() {
        model.dobOrder = .dayFirst
        model.dob = "31021998" // a restore assigns the property directly
        model.hideAge = true
        model.arrived(.dob, referrer: nil)
        model.dobTyped("31021998")

        XCTAssertTrue(recorder.of("dob_validation_failed").isEmpty)
        XCTAssertTrue(recorder.of("field_display_opted_out").isEmpty)
    }

    func testAnIncompleteDateRefusedReportsRequiredMissing() {
        model.dobOrder = .dayFirst
        model.dobTyped("0101")
        model.dobRefused()
        let failed = recorder.of("form_validation_failed")[0]
        XCTAssertEqual(failed["field_id"] as? String, "age")
        XCTAssertEqual(failed["step_id"] as? String, "dob")

        model.dobTyped("31021998")
        model.dobRefused()
        XCTAssertEqual(recorder.of("form_validation_failed").count, 1, "an impossible date adds nothing")
    }

    func testHidingTheAgeReportsOnTheTickOnly() {
        model.hideAgeToggled(true)
        model.hideAgeToggled(true)
        model.hideAgeToggled(false)
        XCTAssertEqual(recorder.of("field_display_opted_out").count, 1)
        XCTAssertEqual(recorder.of("field_display_opted_out")[0]["field_id"] as? String, "age")
    }

    func testAStoredDateReportsTheServersAgeAndCompletesTheStep() async {
        await script.add("updateProfile", 200, profileJSON(age: 28))
        model.arrived(.dob, referrer: nil)
        var saved = false
        model.saveDateOfBirth(iso: "1998-03-22", age: 27) { saved = true }
        await settle { saved }

        XCTAssertEqual(recorder.of("dob_submitted").first?["age"] as? Int, 28, "the server's age wins")
        XCTAssertEqual(recorder.of("profile_step_completed").first?["step_id"] as? String, "dob")
    }

    // MARK: the bridge

    func testTheBridgeReportsTheScreenAndTheBridgeAndIsNotAStep() {
        model.embraceArrived(referrer: .dob)
        XCTAssertEqual(recorder.names, ["screen_viewed", "embrace_bridge_viewed"])
        XCTAssertEqual(recorder.of("screen_viewed")[0]["screen_id"] as? String, "profile_embrace_build")
        XCTAssertEqual(recorder.of("embrace_bridge_viewed")[0]["variant"] as? String, "build_profile")
    }
}
