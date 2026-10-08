import XCTest
import OpenAPIRuntime
import HTTPTypes
import os
@testable import ShowUpAPI

/// Verifies the iOS auth layer: injection, refresh, rotation, clearing, and single-flight.
///
/// The stub transport means no backend, no network, no simulator device and no Keychain
/// entitlement. `KeychainTokenStore` is deliberately NOT exercised here -- see the note on
/// `testKeychainStoreIsNotCoveredHere`.
final class AuthLayerTests: XCTestCase {

    /// Counts what reached each endpoint, so assertions are about calls actually made.
    private actor Counter {
        private(set) var refreshCalls = 0
        private(set) var protectedCalls = 0
        private(set) var lastAuthHeader: String?

        func countRefresh() { refreshCalls += 1 }
        func countProtected(auth: String?) {
            protectedCalls += 1
            lastAuthHeader = auth
        }
    }

    /// A complete AuthResponseDto. UserDto requires eight fields; a partial fixture fails to decode
    /// and produces a confusing assertion about missing tokens rather than about decoding.
    private static let authResponse = """
        {"accessToken":"new-access","refreshToken":"new-refresh","tokenType":"Bearer",\
        "expiresIn":900,"user":{"id":"u1","phone":"+4917612345678","email":null,\
        "displayName":null,"status":"active","phoneVerified":true,"emailVerified":false,\
        "createdAt":"2026-09-04T10:00:00Z"}}
        """

    private static let otpChallenge = """
        {"expiresAt":"2026-09-04T10:15:30Z","resendAvailableAt":"2026-09-04T10:16:00Z"}
        """

    /// How many times the app was told the session is over.
    ///
    /// Counted SYNCHRONOUSLY, under a lock, rather than on the `Counter` actor: the refresher calls
    /// `onSessionEnded` before `validToken` returns, so the count is already final when the
    /// assertion reads it. Hopping to an actor would leave the test waiting on a task it cannot see.
    private final class SessionEndings: Sendable {
        private let count = OSAllocatedUnfairLock(initialState: 0)
        func record() { count.withLock { $0 += 1 } }
        var value: Int { count.withLock { $0 } }
    }

    /// How the refresh endpoint answers.
    private enum RefreshAnswer: Sendable {
        /// A new pair.
        case renewed
        /// This status, with an error body. 401 stands in for a spent or revoked token.
        case status(Int)
        /// No answer at all: the connection drops.
        case dropped
    }

    /// Serves 401 to anything carrying a stale token and 200 to anything carrying the new one.
    private struct StubTransport: ClientTransport {
        let counter: Counter
        let refresh: RefreshAnswer

        func send(
            _ request: HTTPRequest,
            body: HTTPBody?,
            baseURL: URL,
            operationID: String
        ) async throws -> (HTTPResponse, HTTPBody?) {
            let path = request.path ?? ""
            let auth = request.headerFields[.authorization]

            if path.hasSuffix("/auth/refresh") {
                await counter.countRefresh()
                switch refresh {
                case .renewed:
                    return (
                        HTTPResponse(status: .ok, headerFields: [.contentType: "application/json"]),
                        HTTPBody(AuthLayerTests.authResponse)
                    )
                case .status(let code):
                    return (
                        HTTPResponse(status: .init(code: code), headerFields: [.contentType: "application/json"]),
                        HTTPBody(#"{"statusCode":\#(code),"message":"Refused","error":"Refused"}"#)
                    )
                case .dropped:
                    throw URLError(.networkConnectionLost)
                }
            }

            await counter.countProtected(auth: auth)
            if auth == "Bearer new-access" {
                return (
                    HTTPResponse(status: .ok, headerFields: [.contentType: "application/json"]),
                    HTTPBody(AuthLayerTests.otpChallenge)
                )
            }
            return (
                HTTPResponse(status: .unauthorized, headerFields: [.contentType: "application/json"]),
                HTTPBody(#"{"statusCode":401,"message":"Unauthorized","error":"Unauthorized"}"#)
            )
        }
    }

    /// The package's own refresh mapping — not a copy of it — so these tests cover what ships.
    private func makeRefresher(
        tokens: any TokenStoring,
        counter: Counter,
        refresh: RefreshAnswer = .renewed,
        endings: SessionEndings = SessionEndings()
    ) -> TokenRefresher {
        let bare = Client(
            serverURL: APIEnvironment.development.baseURL,
            transport: StubTransport(counter: counter, refresh: refresh)
        )
        return TokenRefresher(tokens: tokens, onSessionEnded: { endings.record() }) { refreshToken in
            try await ShowUpAPI.exchangeRefresh(bare, refreshToken: refreshToken, userAgent: "test-agent")
        }
    }

    private func makeClient(
        tokens: any TokenStoring,
        counter: Counter,
        refresh: RefreshAnswer = .renewed
    ) -> Client {
        Client(
            serverURL: APIEnvironment.development.baseURL,
            transport: StubTransport(counter: counter, refresh: refresh),
            middlewares: [
                AuthMiddleware(
                    tokens: tokens,
                    refresher: makeRefresher(tokens: tokens, counter: counter, refresh: refresh)
                )
            ]
        )
    }


    // MARK: - Injection

    func testNoAuthorizationHeaderWhenNobodyIsSignedIn() async throws {
        let counter = Counter()
        let tokens = InMemoryTokenStore()
        let client = makeClient(tokens: tokens, counter: counter)

        _ = try? await client.startPhoneVerification(body: .json(.init(phone: "+4917612345678")))

        // A public endpoint called with no session must not carry an empty or literal-null bearer.
        let header = await counter.lastAuthHeader
        XCTAssertNil(header)
    }

    func testTheBearerIsAttachedWithoutAnyGeneratedMethodMentioningIt() async throws {
        let counter = Counter()
        let tokens = InMemoryTokenStore(accessToken: "new-access", refreshToken: "r")
        let client = makeClient(tokens: tokens, counter: counter)

        _ = try await client.startPhoneVerification(body: .json(.init(phone: "+4917612345678")))

        let header = await counter.lastAuthHeader
        XCTAssertEqual(header, "Bearer new-access")
    }

    // MARK: - Refresh

    func testA401IsRetriedOnceWithARefreshedToken() async throws {
        let counter = Counter()
        let tokens = InMemoryTokenStore(accessToken: "stale-access", refreshToken: "good-refresh")
        let client = makeClient(tokens: tokens, counter: counter)

        let response = try await client.startPhoneVerification(
            body: .json(.init(phone: "+4917612345678"))
        )

        switch response {
        case .ok: break
        default: XCTFail("the retried call should succeed, got \(response)")
        }

        let refreshes = await counter.refreshCalls
        let attempts = await counter.protectedCalls
        XCTAssertEqual(refreshes, 1, "exactly one refresh")
        XCTAssertEqual(attempts, 2, "the original 401 and the successful retry")
    }

    func testASuccessfulRefreshStoresBothTokensBecauseTheServerRotatesThem() async throws {
        let counter = Counter()
        let tokens = InMemoryTokenStore(accessToken: "stale-access", refreshToken: "good-refresh")
        let client = makeClient(tokens: tokens, counter: counter)

        _ = try await client.startPhoneVerification(body: .json(.init(phone: "+4917612345678")))

        let access = await tokens.accessToken()
        let refresh = await tokens.refreshToken()
        XCTAssertEqual(access, "new-access")
        // The part that is easy to miss: keeping the old refresh token would leave the app holding
        // one the server has already invalidated, and the NEXT refresh would sign the user out.
        XCTAssertEqual(refresh, "new-refresh")
    }

    // MARK: - Single-flight

    func testTenConcurrentRefreshesProduceExactlyOneCall() async throws {
        let counter = Counter()
        let tokens = InMemoryTokenStore(accessToken: "stale-access", refreshToken: "good-refresh")
        // One refresher shared by every caller, which is how ShowUpAPI wires it. A refresher per
        // request would defeat the mechanism entirely, so this mirrors production deliberately.
        let refresher = makeRefresher(tokens: tokens, counter: counter)

        let results = await withTaskGroup(of: String?.self) { group in
            for _ in 1...10 {
                group.addTask { await refresher.validToken(after: "stale-access") }
            }
            var out: [String?] = []
            for await value in group { out.append(value) }
            return out
        }

        XCTAssertEqual(results.compactMap { $0 }.count, 10, "every caller gets a token")
        XCTAssertTrue(results.allSatisfy { $0 == "new-access" }, "and they all get the same one")

        let refreshes = await counter.refreshCalls
        XCTAssertEqual(refreshes, 1, "ten concurrent refreshes, ONE call")
    }

    func testACallerWhoseTokenWasAlreadyRefreshedReusesItWithoutASecondCall() async throws {
        let counter = Counter()
        let tokens = InMemoryTokenStore(accessToken: "stale-access", refreshToken: "good-refresh")
        let refresher = makeRefresher(tokens: tokens, counter: counter)

        _ = await refresher.validToken(after: "stale-access")
        let first = await counter.refreshCalls
        XCTAssertEqual(first, 1)

        // Presents the same stale token it used. The stored token has moved on, so this must be
        // recognised as already-handled rather than refreshed again.
        let reused = await refresher.validToken(after: "stale-access")

        XCTAssertEqual(reused, "new-access")
        let second = await counter.refreshCalls
        XCTAssertEqual(second, 1, "still exactly one refresh")
    }

    // MARK: - Failure and clearing

    func testARefusedRefreshEndsTheSessionAndTellsTheAppOnce() async throws {
        let counter = Counter()
        let tokens = InMemoryTokenStore(accessToken: "stale-access", refreshToken: "spent-refresh")
        let endings = SessionEndings()
        let refresher = makeRefresher(tokens: tokens, counter: counter, refresh: .status(401), endings: endings)

        let result = await refresher.validToken(after: "stale-access")

        XCTAssertNil(result)
        // Keeping a dead token only produces a second confusing failure on the next request.
        let access = await tokens.accessToken()
        let refresh = await tokens.refreshToken()
        XCTAssertNil(access, "access token cleared")
        XCTAssertNil(refresh, "refresh token cleared")
        // THE BUG THIS FIXES: the tokens went and nobody was told, so every save after that failed
        // with "We couldn't save that just now" and no way back to sign-in.
        XCTAssertEqual(endings.value, 1, "the app is told the session is over, once")
    }

    func testTenConcurrentCallersAgainstARefusedSessionTellTheAppOnce() async throws {
        let counter = Counter()
        let tokens = InMemoryTokenStore(accessToken: "stale-access", refreshToken: "spent-refresh")
        let endings = SessionEndings()
        let refresher = makeRefresher(tokens: tokens, counter: counter, refresh: .status(401), endings: endings)

        await withTaskGroup(of: String?.self) { group in
            for _ in 1...10 {
                group.addTask { await refresher.validToken(after: "stale-access") }
            }
            for await _ in group {}
        }

        let refreshes = await counter.refreshCalls
        XCTAssertEqual(refreshes, 1, "one refresh")
        XCTAssertEqual(endings.value, 1, "one notice, not ten")
    }

    func testAServerErrorDuringRefreshKeepsTheUserSignedIn() async throws {
        let counter = Counter()
        let tokens = InMemoryTokenStore(accessToken: "stale-access", refreshToken: "good-refresh")
        let endings = SessionEndings()
        let refresher = makeRefresher(tokens: tokens, counter: counter, refresh: .status(503), endings: endings)

        let result = await refresher.validToken(after: "stale-access")

        XCTAssertNil(result, "no token to give right now")
        let refresh = await tokens.refreshToken()
        XCTAssertEqual(refresh, "good-refresh", "tokens kept: the server being down says nothing about the session")
        XCTAssertEqual(endings.value, 0)
    }

    func testNoConnectionDuringRefreshKeepsTheUserSignedIn() async throws {
        let counter = Counter()
        let tokens = InMemoryTokenStore(accessToken: "stale-access", refreshToken: "good-refresh")
        let endings = SessionEndings()
        let refresher = makeRefresher(tokens: tokens, counter: counter, refresh: .dropped, endings: endings)

        let result = await refresher.validToken(after: "stale-access")

        XCTAssertNil(result)
        let access = await tokens.accessToken()
        let refresh = await tokens.refreshToken()
        XCTAssertEqual(access, "stale-access", "a train in a tunnel does not sign anyone out")
        XCTAssertEqual(refresh, "good-refresh")
        XCTAssertEqual(endings.value, 0)
    }

    func testARateLimitedOrTimedOutRefreshIsNotARefusal() async throws {
        for code in [408, 429] {
            let counter = Counter()
            let tokens = InMemoryTokenStore(accessToken: "stale-access", refreshToken: "good-refresh")
            let endings = SessionEndings()
            let refresher = makeRefresher(tokens: tokens, counter: counter, refresh: .status(code), endings: endings)

            _ = await refresher.validToken(after: "stale-access")

            let refresh = await tokens.refreshToken()
            XCTAssertEqual(refresh, "good-refresh", "\(code) keeps the tokens")
            XCTAssertEqual(endings.value, 0, "\(code) is not a refusal")
        }
    }

    func testTheRefusalLine() {
        // Every 4xx but 408 and 429 refuses; nothing else does.
        for code in [400, 401, 403, 404, 410] {
            XCTAssertEqual(TokenRefresher.Outcome.ofFailedResponse(statusCode: code), .refused, "\(code)")
        }
        for code in [200, 408, 429, 500, 502, 503] {
            XCTAssertEqual(TokenRefresher.Outcome.ofFailedResponse(statusCode: code), .unreachable, "\(code)")
        }
    }

    func testA401WithNoRefreshTokenDoesNotAttemptARefresh() async throws {
        let counter = Counter()
        let tokens = InMemoryTokenStore(accessToken: "stale-access", refreshToken: nil)
        let endings = SessionEndings()
        let refresher = makeRefresher(tokens: tokens, counter: counter, endings: endings)

        let result = await refresher.validToken(after: "stale-access")

        XCTAssertNil(result)
        let refreshes = await counter.refreshCalls
        XCTAssertEqual(refreshes, 0, "a signed-out user is not an error worth a network call")
        // Someone who never signed in has no session to end, so no "log in again".
        XCTAssertEqual(endings.value, 0)
    }

    func testClearingRemovesBothTokens() async throws {
        let tokens = InMemoryTokenStore(accessToken: "a", refreshToken: "b")
        await tokens.clear()

        let access = await tokens.accessToken()
        let refresh = await tokens.refreshToken()
        XCTAssertNil(access)
        XCTAssertNil(refresh)
    }

    // MARK: - What is not covered

    func testKeychainStoreIsNotCoveredHere() {
        // Deliberately only a construction check. Reading and writing the real Keychain from a
        // SwiftPM test bundle needs a signed host application with a keychain-access-group
        // entitlement; without one, SecItemAdd returns errSecMissingEntitlement and the test would
        // assert on the sandbox rather than on our code.
        //
        // So KeychainTokenStore is UNVERIFIED and must be exercised by hand on a device before the
        // first release. It is recorded here rather than left as a silent gap.
        _ = KeychainTokenStore()
    }
}
