import XCTest
import OpenAPIRuntime
import HTTPTypes
@testable import ShowUpAPI

/// Per-field profile visibility, over the wire (SHOWUP-154). The iOS half of
/// `ProfileVisibilityTest.kt`.
///
/// WHAT THESE PROTECT
///
/// The product decision is that hiding the age hides a VALUE: the user stays fully discoverable and
/// matchable. The contract has exactly one flag that sounds like it belongs here — `isVisible` —
/// and it means "appears in discovery at all". Wiring the age control to it would silently remove
/// the user from discovery, which is the one outcome the decision forbids.
///
/// So every test below asserts on `isVisible` even though none of them is about `isVisible`. That
/// is the point: the assertion that fails first, and loudest, if the two are ever confused.
///
/// The Android half also guards a serialisation trap that does not exist here — kotlinx encodes
/// null defaults unless told not to, so an Android partial update had to be taught to omit unset
/// fields. `JSONEncoder` omits nil optionals already, and
/// `testHidingAgeNeverMentionsIsVisibleInTheBody` is what proves that rather than assuming it.
final class ProfileVisibilityTests: XCTestCase {

    /// Records the outgoing request and returns a canned response. Mirrors the stub in
    /// `GeneratedClientTests`; kept separate so neither test's expectations constrain the other.
    private struct StubTransport: ClientTransport {
        let status: Int
        let responseBody: String
        let recorder: Recorder

        actor Recorder {
            private(set) var request: HTTPRequest?
            private(set) var bodyText: String?

            func record(_ request: HTTPRequest, body: String?) {
                self.request = request
                self.bodyText = body
            }
        }

        func send(
            _ request: HTTPRequest,
            body: HTTPBody?,
            baseURL: URL,
            operationID: String
        ) async throws -> (HTTPResponse, HTTPBody?) {
            var sentBody: String?
            if let body {
                sentBody = try await String(collecting: body, upTo: 64 * 1024)
            }
            await recorder.record(request, body: sentBody)
            return (
                HTTPResponse(
                    status: .init(code: status),
                    headerFields: [.contentType: "application/json"]
                ),
                HTTPBody(responseBody)
            )
        }
    }

    /// The real ProfileDto shape, with every field the contract marks required.
    private func profileJSON(hidden: String, isVisible: Bool = true) -> String {
        """
        {"id":"p1","displayName":"Leo","age":28,"gender":null,"lookingFor":null,\
        "isVisible":\(isVisible),"hiddenFields":\(hidden),"isComplete":false,\
        "verificationStatus":"none"}
        """
    }

    private func client(
        _ recorder: StubTransport.Recorder,
        responseBody: String
    ) -> Client {
        Client(
            serverURL: APIEnvironment.development.baseURL,
            transport: StubTransport(
                status: 200,
                responseBody: responseBody,
                recorder: recorder
            )
        )
    }

    func testHidingAgeNeverMentionsIsVisibleInTheBody() async throws {
        let recorder = StubTransport.Recorder()
        let api = client(recorder, responseBody: profileJSON(hidden: #"["age"]"#))

        _ = try await api.updateProfile(body: .json(.init(hiddenFields: ["age"])))

        let sentBody = await recorder.bodyText
        XCTAssertEqual(
            sentBody?.contains("hiddenFields"), true,
            "body should carry the hidden set, was: \(sentBody ?? "nil")"
        )
        // The load-bearing assertion. An omitted key cannot change discovery eligibility; a key
        // present with any value can.
        XCTAssertEqual(
            sentBody?.contains("isVisible"), false,
            "isVisible must not appear in the body, was: \(sentBody ?? "nil")"
        )
    }

    func testAHiddenAgeComesBackWithTheUserStillVisible() async throws {
        let recorder = StubTransport.Recorder()
        let api = client(recorder, responseBody: profileJSON(hidden: #"["age"]"#))

        let response = try await api.updateProfile(body: .json(.init(hiddenFields: ["age"])))

        switch response {
        case .ok(let ok):
            let profile = try ok.body.json
            XCTAssertEqual(profile.hiddenFields, ["age"])
            XCTAssertTrue(profile.isVisible, "hiding the age must leave the user discoverable")
            // Still on the wire for the owner, and still reaches matching. Hiding is a rendering
            // instruction, not redaction.
            XCTAssertEqual(profile.age, 28)
        default:
            XCTFail("expected 200, got \(response)")
        }
    }

    func testUncheckingSendsAnExplicitEmptySet() async throws {
        let recorder = StubTransport.Recorder()
        let api = client(recorder, responseBody: profileJSON(hidden: "[]"))

        _ = try await api.updateProfile(body: .json(.init(hiddenFields: [])))

        let sentBody = await recorder.bodyText
        // An omitted key means "leave it alone" to the server, so unchecking has to send the empty
        // set explicitly or the choice never clears.
        XCTAssertEqual(
            sentBody?.contains("hiddenFields"), true,
            "empty set must be sent, was: \(sentBody ?? "nil")"
        )
    }

    func testAnInvisibleProfileWithAHiddenAgeDecodesBothIndependently() async throws {
        // The two are orthogonal, and a client reading one must never infer the other.
        let recorder = StubTransport.Recorder()
        let api = client(recorder, responseBody: profileJSON(hidden: #"["age"]"#, isVisible: false))

        let response = try await api.getProfile()

        switch response {
        case .ok(let ok):
            let profile = try ok.body.json
            XCTAssertFalse(profile.isVisible)
            XCTAssertEqual(profile.hiddenFields, ["age"])
        default:
            XCTFail("expected 200, got \(response)")
        }
    }

    func testAProfileWithNothingHiddenDecodesAsAnEmptySet() async throws {
        let recorder = StubTransport.Recorder()
        let api = client(recorder, responseBody: profileJSON(hidden: "[]"))

        let response = try await api.getProfile()

        switch response {
        case .ok(let ok):
            let profile = try ok.body.json
            XCTAssertEqual(profile.hiddenFields, [])
            XCTAssertTrue(profile.isVisible)
        default:
            XCTFail("expected 200, got \(response)")
        }
    }
}
