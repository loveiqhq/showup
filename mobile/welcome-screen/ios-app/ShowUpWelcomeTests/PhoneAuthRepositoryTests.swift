//  PhoneAuthRepositoryTests.swift
//  ShowUp · signing in for real. The iOS half of PhoneAuthRepositoryTest.kt.
//
//  This covers the two requests that used to be a constant. `DevAuth.testCode` could not fail, so
//  there was nothing to test; /auth/phone/verify can fail five ways, and one of its successes has
//  a side effect — it writes credentials — that nothing on screen would reveal if it were skipped.
//  Every later request in the app depends on that write having happened.
//
//  The transport here answers PER OPERATION rather than returning one canned reply, because a
//  successful verify makes two calls: the verify itself and the profile read that decides where
//  the user goes next. A single-response stub would have answered the profile read with the auth
//  payload and the test would have passed for the wrong reason.

import XCTest
import OpenAPIRuntime
import HTTPTypes
import ShowUpAPI
@testable import ShowUpWelcome

final class PhoneAuthRepositoryTests: XCTestCase {

    /// Returns a canned response per `operationID`, and records every request in order.
    private struct ScriptedTransport: ClientTransport {
        let script: [String: (status: Int, body: String)]
        let recorder: Recorder

        /// An actor, not `@unchecked Sendable`: the transport crosses isolation domains, and the
        /// three escape hatches are banned by CLAUDE.md and by check-swift-concurrency.py.
        actor Recorder {
            private(set) var requests: [(operation: String, request: HTTPRequest, body: String?)] = []
            func record(_ operation: String, _ r: HTTPRequest, body: String?) {
                requests.append((operation, r, body))
            }
            func request(for operation: String) -> HTTPRequest? {
                requests.first { $0.operation == operation }?.request
            }
            func body(for operation: String) -> String? {
                requests.first { $0.operation == operation }?.body
            }
        }

        func send(_ request: HTTPRequest, body: HTTPBody?, baseURL: URL,
                  operationID: String) async throws -> (HTTPResponse, HTTPBody?) {
            var sent: String?
            if let body { sent = try await String(collecting: body, upTo: 64 * 1024) }
            await recorder.record(operationID, request, body: sent)
            guard let canned = script[operationID] else {
                // Loud rather than convenient. An unscripted call is a call the test did not know
                // the code made, which is exactly the thing worth failing on.
                throw UnscriptedOperation(operationID: operationID)
            }
            return (HTTPResponse(status: .init(code: canned.status),
                                 headerFields: [.contentType: "application/json"]),
                    HTTPBody(canned.body))
        }
    }

    private struct UnscriptedOperation: Error { let operationID: String }

    /// A transport that cannot reach anything, for the offline case.
    private struct DeadTransport: ClientTransport {
        struct Unreachable: Error {}
        func send(_ request: HTTPRequest, body: HTTPBody?, baseURL: URL,
                  operationID: String) async throws -> (HTTPResponse, HTTPBody?) {
            throw Unreachable()
        }
    }

    // MARK: - fixtures

    private func repo(_ script: [String: (status: Int, body: String)],
                      recorder: ScriptedTransport.Recorder,
                      tokens: InMemoryTokenStore) -> PhoneAuthRepository {
        PhoneAuthRepository(
            api: ShowUpAPI(tokens: tokens,
                           transport: ScriptedTransport(script: script, recorder: recorder)),
            tokens: tokens)
    }

    private let challenge = #"""
    {"expiresAt":"2026-09-10T10:20:30Z","resendAvailableAt":"2026-09-10T10:16:00Z","devCode":"123456"}
    """#

    private func apiError(_ status: Int, _ message: String,
                          _ error: String = "Unauthorized") -> String {
        #"{"statusCode":\#(status),"message":"\#(message)","error":"\#(error)"}"#
    }

    private func authResponse(access: String = "acc", refresh: String = "ref") -> String {
        """
        {"accessToken":"\(access)","refreshToken":"\(refresh)","tokenType":"Bearer",\
        "expiresIn":900,"user":{"id":"u1","phone":"+4917612345678","email":"leo@hey.com",\
        "displayName":"Leo","status":"registered","phoneVerified":true,"emailVerified":false,\
        "createdAt":"2026-09-10T10:00:00Z"}}
        """
    }

    private func profile(isComplete: Bool) -> String {
        """
        {"id":"p1","displayName":"Leo","age":31,"gender":null,"lookingFor":null,\
        "isVisible":true,"hiddenFields":[],"isComplete":\(isComplete),\
        "verificationStatus":"none"}
        """
    }

    // MARK: - sending

    func testASentCodeCarriesBothTimestampsAndTheDevCode() async {
        let rec = ScriptedTransport.Recorder()
        let repo = repo(["startPhoneVerification": (200, challenge)],
                        recorder: rec, tokens: InMemoryTokenStore())
        let result = await repo.start(phoneE164: "+4917612345678")
        guard case let .sent(expiresAt, resendAvailableAt, devCode) = result else {
            return XCTFail("expected .sent, got \(result)")
        }
        // resendAvailableAt is what the countdown counts to. The client used to hold its own
        // constant and the two could disagree; now there is only the server's number.
        XCTAssertGreaterThan(expiresAt, resendAvailableAt)
        // Present only because AUTH_EXPOSE_OTP is on outside production. It is what makes the
        // whole flow testable against LogSmsSender with no paid provider.
        XCTAssertEqual(devCode, "123456")
    }

    func testAServerThatDoesNotExposeTheCodeStillSendsOne() async {
        let rec = ScriptedTransport.Recorder()
        let body = #"{"expiresAt":"2026-09-10T10:20:30Z","resendAvailableAt":"2026-09-10T10:16:00Z"}"#
        let repo = repo(["startPhoneVerification": (200, body)],
                        recorder: rec, tokens: InMemoryTokenStore())
        guard case let .sent(_, _, devCode) = await repo.start(phoneE164: "+4917612345678") else {
            return XCTFail("expected .sent")
        }
        // Production behaviour: the challenge is valid, there is simply nothing to show a tester.
        XCTAssertNil(devCode)
    }

    func testTheRequestHitsTheContractPathWithTheNumberInE164() async {
        let rec = ScriptedTransport.Recorder()
        let repo = repo(["startPhoneVerification": (200, challenge)],
                        recorder: rec, tokens: InMemoryTokenStore())
        _ = await repo.start(phoneE164: "+4917612345678")
        let sent = await rec.request(for: "startPhoneVerification")
        XCTAssertEqual(sent?.path, "/auth/phone/start")
        XCTAssertEqual(sent?.method, .post)
        let body = await rec.body(for: "startPhoneVerification")
        XCTAssertEqual(body?.contains("+4917612345678"), true)
    }

    func test429IsTheServersCooldownNotAFailure() async {
        let rec = ScriptedTransport.Recorder()
        let body = apiError(429, "Please wait 41s before requesting another code", "Too Many Requests")
        let repo = repo(["startPhoneVerification": (429, body)],
                        recorder: rec, tokens: InMemoryTokenStore())
        // The distinction is the point: .tooSoon leaves the countdown running, .failed would put
        // an error card on a screen where nothing is actually wrong.
        let result = await repo.start(phoneE164: "+4917612345678")
        XCTAssertEqual(result, .tooSoon)
    }

    // MARK: - verifying

    func testACorrectCodeStoresBothTokens() async {
        let rec = ScriptedTransport.Recorder()
        // Deliberately EMPTY. A pre-seeded store would let the profile read pass on a token this
        // flow never produced, which is the bug the next test exists to catch.
        let tokens = InMemoryTokenStore()
        let repo = repo(["verifyPhone": (200, authResponse()),
                         "getProfile": (200, profile(isComplete: false))],
                        recorder: rec, tokens: tokens)
        _ = await repo.verify(phoneE164: "+4917612345678", code: "123456")
        // Both, not only the access token: the refresh token is what survives the access token
        // expiring, and without it the user is signed out fifteen minutes later.
        let access = await tokens.accessToken()
        let refresh = await tokens.refreshToken()
        XCTAssertEqual(access, "acc")
        XCTAssertEqual(refresh, "ref")
    }

    func testTheProfileReadCarriesTheTokenThatWasJustSaved() async {
        let rec = ScriptedTransport.Recorder()
        let tokens = InMemoryTokenStore()
        let repo = repo(["verifyPhone": (200, authResponse(access: "fresh-token")),
                         "getProfile": (200, profile(isComplete: true))],
                        recorder: rec, tokens: tokens)
        _ = await repo.verify(phoneE164: "+4917612345678", code: "123456")
        let profileRequest = await rec.request(for: "getProfile")
        XCTAssertEqual(profileRequest?.path, "/me/profile")
        // The point of the whole change. The middleware reads the store per request, so a token
        // written mid-flow is attached to the very next call with nothing being rebuilt.
        XCTAssertEqual(profileRequest?.headerFields[.authorization], "Bearer fresh-token")
    }

    func testTheVerifyRequestIdentifiesTheDevice() async {
        let rec = ScriptedTransport.Recorder()
        let repo = repo(["verifyPhone": (200, authResponse()),
                         "getProfile": (200, profile(isComplete: false))],
                        recorder: rec, tokens: InMemoryTokenStore())
        _ = await repo.verify(phoneE164: "+4917612345678", code: "123456")
        let sent = await rec.request(for: "verifyPhone")
        XCTAssertEqual(sent?.path, "/auth/phone/verify")
        // Recorded against the session so a person can see where they are signed in. One shared
        // constant, so this row and the one a refresh creates name the same device.
        XCTAssertEqual(sent?.headerFields[.userAgent], ShowUpAPI.userAgent)
    }

    func testACompleteProfileIsReportedAsComplete() async {
        let rec = ScriptedTransport.Recorder()
        let repo = repo(["verifyPhone": (200, authResponse()),
                         "getProfile": (200, profile(isComplete: true))],
                        recorder: rec, tokens: InMemoryTokenStore())
        let result = await repo.verify(phoneE164: "+4917612345678", code: "123456")
        XCTAssertEqual(result, .signedIn(profileComplete: true))
    }

    func testAProfileThatCannotBeReadCountsAsIncompleteAndTheUserStaysSignedIn() async {
        let rec = ScriptedTransport.Recorder()
        let tokens = InMemoryTokenStore()
        let repo = repo(["verifyPhone": (200, authResponse()),
                         "getProfile": (500, apiError(500, "Internal server error",
                                                      "Internal Server Error"))],
                        recorder: rec, tokens: tokens)
        let result = await repo.verify(phoneE164: "+4917612345678", code: "123456")
        // Fails towards onboarding on purpose. Repeating a step is an annoyance; skipping profile
        // creation because one request timed out leaves an account nobody can match.
        XCTAssertEqual(result, .signedIn(profileComplete: false))
        // The sign-in itself still happened -- the tokens are real regardless.
        let access = await tokens.accessToken()
        XCTAssertEqual(access, "acc")
    }

    func testAWrongCodeIsRefusedAndStoresNothing() async {
        let rec = ScriptedTransport.Recorder()
        let tokens = InMemoryTokenStore()
        let repo = repo(["verifyPhone": (401, apiError(401, "Invalid or expired code"))],
                        recorder: rec, tokens: tokens)
        let result = await repo.verify(phoneE164: "+4917612345678", code: "000000")
        XCTAssertEqual(result, .refused)
        let access = await tokens.accessToken()
        let refresh = await tokens.refreshToken()
        XCTAssertNil(access)
        XCTAssertNil(refresh)
    }

    func testTheFifthWrongCodeIsTheCapNotAnotherMismatch() async {
        let rec = ScriptedTransport.Recorder()
        let body = apiError(401, "Too many attempts. Please request a new code.")
        let repo = repo(["verifyPhone": (401, body)],
                        recorder: rec, tokens: InMemoryTokenStore())
        // Same status, different sentence. The screen says something different for each: one asks
        // the user to check the digits, the other tells them to request a new code.
        let result = await repo.verify(phoneE164: "+4917612345678", code: "000000")
        XCTAssertEqual(result, .tooManyAttempts)
    }

    func testTheCapIsRecognisedRegardlessOfTheSentencesCasing() async {
        let rec = ScriptedTransport.Recorder()
        let repo = repo(["verifyPhone": (401, apiError(401, "too many attempts for this challenge"))],
                        recorder: rec, tokens: InMemoryTokenStore())
        let result = await repo.verify(phoneE164: "+4917612345678", code: "000000")
        XCTAssertEqual(result, .tooManyAttempts)
    }

    func testAnUnreachableServerIsAFailureNotARefusal() async {
        let tokens = InMemoryTokenStore()
        let repo = PhoneAuthRepository(
            api: ShowUpAPI(tokens: tokens, transport: DeadTransport()),
            tokens: tokens)
        let result = await repo.verify(phoneE164: "+4917612345678", code: "123456")
        // .refused would tell the user their code was wrong when it may have been perfect.
        XCTAssertEqual(result, .failed)
        XCTAssertNotEqual(result, .refused)
    }
}
