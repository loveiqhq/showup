import XCTest
import OpenAPIRuntime
import HTTPTypes
@testable import ShowUpAPI

/// Proves the generated iOS client exists, compiles, and can be built and called.
///
/// WHY THESE ASSERTIONS AND NOT A COMPILE CHECK
///
/// Eight Swift tests on this project once sat in no test target for a week while being counted as
/// passing, and "it compiles" has been the whole basis for calling work done more than once. So
/// each test here does something a compile cannot: constructs the client, drives a real request
/// through a stub transport, and asserts on the method, path and body that came out.
///
/// The stub transport means no backend, no network and no simulator device is required -- `swift
/// build` and `swift test` verify this package on any Mac.
final class GeneratedClientTests: XCTestCase {

    /// A transport that records what it was asked to send and returns a canned response.
    private struct StubTransport: ClientTransport {
        let status: Int
        let body: String
        let recorder: Recorder

        final class Recorder: @unchecked Sendable {
            var request: HTTPRequest?
            var bodyText: String?
        }

        func send(
            _ request: HTTPRequest,
            body: HTTPBody?,
            baseURL: URL,
            operationID: String
        ) async throws -> (HTTPResponse, HTTPBody?) {
            recorder.request = request
            if let body {
                recorder.bodyText = try await String(
                    collecting: body, upTo: 64 * 1024
                )
            }
            return (
                HTTPResponse(
                    status: .init(code: status),
                    headerFields: [.contentType: "application/json"]
                ),
                HTTPBody(body)
            )
        }
    }

    func testTheGeneratedClientCanBeConstructedForEachEnvironment() throws {
        // Also asserts the URLs parse. A malformed base URL would otherwise surface much later as
        // an unexplained request failure.
        for environment in [APIEnvironment.development, .staging, .production] {
            let api = try ShowUpAPI(environment: environment)
            XCTAssertNotNil(api.client)
        }
    }

    func testDevelopmentPointsAtLocalhostNotTheAndroidEmulatorAddress() {
        // 10.0.2.2 is the Android emulator's route to the host and is meaningless on iOS. Getting
        // this wrong produces a connection failure that looks like a backend problem.
        XCTAssertEqual(APIEnvironment.development.baseURL.absoluteString, "http://localhost:3000")
    }

    func testStartPhoneVerificationSendsTheContractPathAndBody() async throws {
        let recorder = StubTransport.Recorder()
        let client = Client(
            serverURL: APIEnvironment.development.baseURL,
            transport: StubTransport(
                status: 200,
                body: #"{"expiresAt":"2026-09-04T10:15:30Z","resendAvailableAt":"2026-09-04T10:16:00Z"}"#,
                recorder: recorder
            )
        )

        // The operation is named by the operationId set on the NestJS route, which is the whole
        // point of that work: this reads as the API, not as the controller.
        _ = try await client.startPhoneVerification(
            body: .json(.init(phone: "+4917612345678"))
        )

        XCTAssertEqual(recorder.request?.method, .post)
        XCTAssertEqual(recorder.request?.path, "/auth/phone/start")
        XCTAssertEqual(
            recorder.bodyText?.contains("+4917612345678"), true,
            "body should carry the phone number, was: \(recorder.bodyText ?? "nil")"
        )
    }

    func testTheSuccessResponseDecodesIntoTheGeneratedType() async throws {
        let recorder = StubTransport.Recorder()
        let client = Client(
            serverURL: APIEnvironment.development.baseURL,
            transport: StubTransport(
                status: 200,
                body: #"{"expiresAt":"2026-09-04T10:15:30Z","resendAvailableAt":"2026-09-04T10:16:00Z"}"#,
                recorder: recorder
            )
        )

        let response = try await client.startPhoneVerification(
            body: .json(.init(phone: "+4917612345678"))
        )

        // Decoding is what a compile check cannot reach. The two date-time fields are the part most
        // likely to break, because they need the generator's date handling to match what NestJS
        // actually serialises.
        switch response {
        case .ok(let ok):
            let json = try ok.body.json
            XCTAssertNotNil(json.expiresAt)
        default:
            XCTFail("expected 200, got \(response)")
        }
    }
}
