//  BasicsRepositoryTests.swift
//  ShowUp · what the backend's answers mean. The iOS half of BasicsRepositoryTest.kt.
//
//  The mapping covered here is the awkward part of SHOWUP-153: /auth/email/verify answers 401 for
//  a wrong code, an expired one, a superseded one and a missing challenge, and 401 again with a
//  different sentence once the cap is reached. Getting it wrong is not a crash — it is a user
//  told to check their inbox for a code that can no longer work.

import XCTest
import OpenAPIRuntime
import HTTPTypes
import ShowUpAPI
@testable import ShowUpWelcome

final class BasicsRepositoryTests: XCTestCase {

    /// Returns a canned response and records what was sent.
    private struct StubTransport: ClientTransport {
        let status: Int
        let responseBody: String
        let recorder: Recorder

        /// An actor, not `@unchecked Sendable`: the transport crosses isolation domains, and the
        /// three escape hatches are banned by CLAUDE.md and by check-swift-concurrency.py.
        actor Recorder {
            private(set) var request: HTTPRequest?
            private(set) var bodyText: String?
            func record(_ r: HTTPRequest, body: String?) { request = r; bodyText = body }
        }

        func send(_ request: HTTPRequest, body: HTTPBody?, baseURL: URL,
                  operationID: String) async throws -> (HTTPResponse, HTTPBody?) {
            var sent: String?
            if let body { sent = try await String(collecting: body, upTo: 64 * 1024) }
            await recorder.record(request, body: sent)
            return (HTTPResponse(status: .init(code: status),
                                 headerFields: [.contentType: "application/json"]),
                    HTTPBody(responseBody))
        }
    }

    private func repo(status: Int, body: String, recorder: StubTransport.Recorder)
        -> BasicsRepository {
        BasicsRepository(api: ShowUpAPI(
            tokens: InMemoryTokenStore(accessToken: "t"),
            transport: StubTransport(status: status, responseBody: body, recorder: recorder)))
    }

    private let challenge = #"{"expiresAt":"2026-09-10T10:20:30Z","resendAvailableAt":"2026-09-10T10:16:00Z"}"#

    private func apiError(_ status: Int, _ message: String, _ error: String = "Unauthorized") -> String {
        #"{"statusCode":\#(status),"message":"\#(message)","error":"\#(error)"}"#
    }

    private func profileJSON(age: Int, hidden: String) -> String {
        """
        {"id":"p1","displayName":"Leo","age":\(age),"gender":null,"lookingFor":null,\
        "isVisible":true,"hiddenFields":\(hidden),"isComplete":false,\
        "verificationStatus":"none"}
        """
    }

    // MARK: - sending

    func testASentCodeCarriesBothOfTheServersTimestamps() async {
        let rec = StubTransport.Recorder()
        let result = await repo(status: 200, body: challenge, recorder: rec).sendCode(email: "leo@hey.com")
        guard case let .sent(expiresAt, resendAvailableAt) = result else {
            return XCTFail("expected .sent, got \(result)")
        }
        // Both are used: expiresAt decides "expired", resendAvailableAt drives the countdown.
        // This is why the client no longer holds a cooldown constant of its own.
        XCTAssertGreaterThan(expiresAt, resendAvailableAt)
    }

    func testTheRequestCarriesTheAddress() async {
        let rec = StubTransport.Recorder()
        _ = await repo(status: 200, body: challenge, recorder: rec).sendCode(email: "leo@hey.com")
        let sent = await rec.bodyText
        let path = await rec.request?.path
        XCTAssertEqual(path, "/auth/email/start")
        XCTAssertEqual(sent?.contains("leo@hey.com"), true)
    }

    func test429IsTheServersCooldownNotAFailure() async {
        let rec = StubTransport.Recorder()
        let body = apiError(429, "Please wait 22s before requesting another code", "Too Many Requests")
        let result = await repo(status: 429, body: body, recorder: rec).sendCode(email: "leo@hey.com")
        XCTAssertEqual(result, .tooSoon)
    }

    func test400OnSendIsTheAddressBelongingToSomeoneElse() async {
        let rec = StubTransport.Recorder()
        let body = apiError(400, "That email is already in use", "Bad Request")
        let result = await repo(status: 400, body: body, recorder: rec).sendCode(email: "leo@hey.com")
        XCTAssertEqual(result, .emailInUse)
    }

    func testAServerErrorIsAFailureNotAWrongAddress() async {
        let rec = StubTransport.Recorder()
        let body = apiError(500, "Unexpected server error", "Internal Server Error")
        let result = await repo(status: 500, body: body, recorder: rec).sendCode(email: "leo@hey.com")
        XCTAssertEqual(result, .failed)
    }

    // MARK: - verifying: one status, three meanings

    func test204IsAVerifiedCode() async {
        let rec = StubTransport.Recorder()
        let result = await repo(status: 204, body: "", recorder: rec).verifyCode("482170")
        XCTAssertEqual(result, .verified)
    }

    func testTheOrdinary401IsARefusal() async {
        let rec = StubTransport.Recorder()
        let result = await repo(status: 401, body: apiError(401, "Invalid or expired code"),
                                recorder: rec).verifyCode("482170")
        XCTAssertEqual(result, .refused)
    }

    func testTheCapIsToldApartByItsSentence() async {
        // The fragile one, and the reason it is written down: English prose, not a code.
        let rec = StubTransport.Recorder()
        let result = await repo(status: 401, body: apiError(401, "Too many attempts; request a new code"),
                                recorder: rec).verifyCode("482170")
        XCTAssertEqual(result, .tooManyAttempts)
    }

    func testAnUnrecognised401FailsSafeAsARefusal() async {
        // If the server rewords the cap message the user gets "that didn't match" and one more
        // refused attempt — annoying. The opposite default would lock them out of a working code.
        let rec = StubTransport.Recorder()
        let result = await repo(status: 401, body: apiError(401, "Some wording nobody has written"),
                                recorder: rec).verifyCode("482170")
        XCTAssertEqual(result, .refused)
    }

    // MARK: - writing the profile

    func testASavedProfileReturnsTheServersOwnAge() async {
        let rec = StubTransport.Recorder()
        let result = await repo(status: 200, body: profileJSON(age: 28, hidden: "[]"),
                                recorder: rec).saveDateOfBirth(iso: "1998-03-22", hideAge: false)
        XCTAssertEqual(result, .saved(age: 28))
    }

    func test400OnTheProfileWriteIsTheServersOwn18Check() async {
        let rec = StubTransport.Recorder()
        let body = apiError(400, "You must be at least 18 years old", "Bad Request")
        let result = await repo(status: 400, body: body, recorder: rec)
            .saveDateOfBirth(iso: "2015-03-22", hideAge: false)
        XCTAssertEqual(result, .underAge)
    }

    func testHidingTheAgeWritesHiddenFieldsAndNeverIsVisible() async {
        // The one mistake on this screen with real consequences: isVisible means "appears in
        // discovery at all", so writing it here would remove the user from matching.
        let rec = StubTransport.Recorder()
        _ = await repo(status: 200, body: profileJSON(age: 28, hidden: #"["age"]"#), recorder: rec)
            .saveDateOfBirth(iso: "1998-03-22", hideAge: true)

        let body = await rec.bodyText ?? ""
        XCTAssertTrue(body.contains("hiddenFields"), "body should carry the hidden set: \(body)")
        XCTAssertTrue(body.contains("age"), "body should name age: \(body)")
        XCTAssertFalse(body.contains("isVisible"), "isVisible must never appear: \(body)")
    }

    func testTheDateIsSentInTheFormatTheContractDocuments() async {
        let rec = StubTransport.Recorder()
        _ = await repo(status: 200, body: profileJSON(age: 28, hidden: "[]"), recorder: rec)
            .saveDateOfBirth(iso: "1998-03-22", hideAge: false)
        let body = await rec.bodyText ?? ""
        // UpsertProfileDto.dateOfBirth is "YYYY-MM-DD". Sending the display string would 400.
        XCTAssertTrue(body.contains("1998-03-22"), body)
    }

    func testOnePatchCarriesTheDateAndTheVisibilityChoiceTogether() async {
        // Two calls would let the date land while the visibility choice failed, leaving an age
        // displayed that the user asked to hide.
        let rec = StubTransport.Recorder()
        _ = await repo(status: 200, body: profileJSON(age: 28, hidden: #"["age"]"#), recorder: rec)
            .saveDateOfBirth(iso: "1998-03-22", hideAge: true)
        let request = await rec.request
        XCTAssertEqual(request?.method, .patch)
        XCTAssertEqual(request?.path, "/me/profile")
    }
}
