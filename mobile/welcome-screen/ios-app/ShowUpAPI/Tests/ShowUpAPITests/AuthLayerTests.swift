import XCTest
import OpenAPIRuntime
import HTTPTypes
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

    /// Serves 401 to anything carrying a stale token and 200 to anything carrying the new one.
    private struct StubTransport: ClientTransport {
        let counter: Counter
        /// When true the refresh endpoint rejects, standing in for a spent or revoked token.
        let refreshFails: Bool

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
                if refreshFails {
                    return (HTTPResponse(status: .unauthorized), HTTPBody(#"{"statusCode":401,"message":"Invalid","error":"Unauthorized"}"#))
                }
                return (
                    HTTPResponse(status: .ok, headerFields: [.contentType: "application/json"]),
                    HTTPBody(AuthLayerTests.authResponse)
                )
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

    private func makeRefresher(
        tokens: any TokenStoring,
        counter: Counter,
        refreshFails: Bool = false
    ) -> TokenRefresher {
        let bare = Client(
            serverURL: APIEnvironment.development.baseURL,
            transport: StubTransport(counter: counter, refreshFails: refreshFails)
        )
        return TokenRefresher(tokens: tokens) { refreshToken in
            let response = try await bare.refreshAuthToken(
                headers: .init(user_hyphen_agent: "test-agent"),
                body: .json(.init(refreshToken: refreshToken))
            )
            switch response {
            case .ok(let ok):
                let payload = try ok.body.json
                return TokenRefresher.TokenPair(
                    accessToken: payload.accessToken,
                    refreshToken: payload.refreshToken
                )
            default:
                return nil
            }
        }
    }

    private func makeClient(
        tokens: any TokenStoring,
        counter: Counter,
        refreshFails: Bool = false
    ) -> Client {
        Client(
            serverURL: APIEnvironment.development.baseURL,
            transport: StubTransport(counter: counter, refreshFails: refreshFails),
            middlewares: [
                AuthMiddleware(
                    tokens: tokens,
                    refresher: makeRefresher(tokens: tokens, counter: counter, refreshFails: refreshFails)
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

    func testAFailedRefreshClearsTheSession() async throws {
        let counter = Counter()
        let tokens = InMemoryTokenStore(accessToken: "stale-access", refreshToken: "spent-refresh")
        let refresher = makeRefresher(tokens: tokens, counter: counter, refreshFails: true)

        let result = await refresher.validToken(after: "stale-access")

        XCTAssertNil(result)
        // Keeping a dead token only produces a second confusing failure on the next request.
        let access = await tokens.accessToken()
        let refresh = await tokens.refreshToken()
        XCTAssertNil(access, "access token cleared")
        XCTAssertNil(refresh, "refresh token cleared")
    }

    func testA401WithNoRefreshTokenDoesNotAttemptARefresh() async throws {
        let counter = Counter()
        let tokens = InMemoryTokenStore(accessToken: "stale-access", refreshToken: nil)
        let refresher = makeRefresher(tokens: tokens, counter: counter)

        let result = await refresher.validToken(after: "stale-access")

        XCTAssertNil(result)
        let refreshes = await counter.refreshCalls
        XCTAssertEqual(refreshes, 0, "a signed-out user is not an error worth a network call")
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
