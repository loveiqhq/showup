// ArrivalRepositoryTests.swift
// ShowUp · what the arrival routes answer and how the app reads them (SHOWUP-195). Android's
// `ArrivalRepositoryTest.kt`: every assertion is made against bytes the generated client sent or parsed.

import XCTest
import OpenAPIRuntime
import HTTPTypes
import ShowUpAPI
@testable import ShowUpWelcome

final class ArrivalRepositoryTests: XCTestCase {

    /// What the transport saw, one request at a time.
    private actor Seen {
        struct Request { let method: HTTPRequest.Method; let path: String?; let body: String }
        private(set) var requests: [Request] = []
        func add(_ request: Request) { requests.append(request) }
    }

    /// Answers every request with one status, recording it; or with no answer at all.
    private struct StubTransport: ClientTransport {
        let status: Int?
        let seen: Seen

        func send(_ request: HTTPRequest, body: HTTPBody?, baseURL: URL,
                  operationID: String) async throws -> (HTTPResponse, HTTPBody?) {
            var text = ""
            if let body { text = try await String(collecting: body, upTo: 1 << 20) }
            await seen.add(Seen.Request(method: request.method, path: request.path, body: text))
            guard let status else { throw URLError(.notConnectedToInternet) }
            let answer = status == 201
                ? #"{"checkedInAt":"2026-11-03T17:58:00.000Z","committed":false}"#
                : #"{"statusCode":\#(status)}"#
            return (HTTPResponse(status: .init(code: status), headerFields: [.contentType: "application/json"]),
                    HTTPBody(answer))
        }
    }

    private let id = "0b7d4a1e-2c3f-4e5a-8b6c-7d8e9f0a1b2c"
    private let fix = LocationFix(latitude: 52.5186, longitude: 13.3761, accuracyM: 12.5)

    private func repo(_ status: Int?, _ seen: Seen = Seen()) -> ArrivalRepository {
        ArrivalRepository(api: ShowUpAPI(tokens: InMemoryTokenStore(accessToken: "t"),
                                         transport: StubTransport(status: status, seen: seen)))
    }

    private func isOk(_ result: Result<Void, CallFailure>) -> Bool {
        if case .success = result { return true }
        return false
    }

    private func failure(_ result: Result<Void, CallFailure>) -> CallFailure? {
        if case .failure(let why) = result { return why }
        return nil
    }

    func testACheckInPostsTheFixTheEntryAndTheClientIdAnd201IsStored() async {
        let seen = Seen()
        let result = await repo(201, seen).checkIn(dateId: "d1", fix: fix, entry: .chatChip, clientId: id)
        XCTAssertEqual(result, .stored)
        let request = await seen.requests[0]
        XCTAssertEqual(request.method, .post)
        XCTAssertEqual(request.path, "/dates/d1/arrival")
        XCTAssertTrue(request.body.contains("\"entry\":\"chat_chip\""), request.body)
        XCTAssertTrue(request.body.contains("\"clientId\":\"\(id)\""), request.body)
        XCTAssertTrue(request.body.contains("\"latitude\":52.5186"), request.body)
        XCTAssertTrue(request.body.contains("\"accuracyM\":12.5"), request.body)
    }

    func testAFixWithoutAnAccuracySendsNone() async {
        let seen = Seen()
        _ = await repo(201, seen).checkIn(dateId: "d1", fix: LocationFix(latitude: 1, longitude: 2, accuracyM: nil),
                                          entry: .dateScreen, clientId: id)
        let request = await seen.requests[0]
        XCTAssertFalse(request.body.contains("accuracyM"), request.body)
    }

    func testA409IsAlreadyCheckedInAnythingElseAFailureNoAnswerANetworkOne() async {
        let again = await repo(409).checkIn(dateId: "d1", fix: fix, entry: .dateScreen, clientId: id)
        XCTAssertEqual(again, .alreadyCheckedIn)
        let broken = await repo(500).checkIn(dateId: "d1", fix: fix, entry: .dateScreen, clientId: id)
        XCTAssertEqual(broken, .failed(.server))
        let offline = await repo(nil).checkIn(dateId: "d1", fix: fix, entry: .dateScreen, clientId: id)
        XCTAssertEqual(offline, .failed(.network))
    }

    func testACheckInRefusedWithAnother4xxIsAFailureNotAlreadyCheckedIn() async {
        let refused = await repo(403).checkIn(dateId: "d1", fix: fix, entry: .dateScreen, clientId: id)
        XCTAssertEqual(refused, .failed(.server))
    }

    func testUndoDeletesAnd409AlreadyCommittedIsNothingLeftToRetry() async {
        let seen = Seen()
        let first = await repo(204, seen).undo(dateId: "d1")
        XCTAssertTrue(isOk(first))
        let request = await seen.requests[0]
        XCTAssertEqual(request.method, .delete)
        XCTAssertEqual(request.path, "/dates/d1/arrival")
        let committed = await repo(409).undo(dateId: "d1")
        XCTAssertTrue(isOk(committed))
        let broken = await repo(500).undo(dateId: "d1")
        XCTAssertEqual(failure(broken), .server)
        let offline = await repo(nil).undo(dateId: "d1")
        XCTAssertEqual(failure(offline), .network)
    }

    func testEvery4xxOnUndoAndCommitIsSettledWithOneRequestNeverRetried() async {
        let seen = Seen()
        for code in [400, 401, 403, 429] {
            let undone = await repo(code, seen).undo(dateId: "d1")
            XCTAssertTrue(isOk(undone), "undo \(code)")
            let committed = await repo(code, seen).commit(dateId: "d1")
            XCTAssertTrue(isOk(committed), "commit \(code)")
        }
        let count = await seen.requests.count
        XCTAssertEqual(count, 8)
    }

    func testCommitPostsAnd404NothingToCommitIsNothingLeftToRetry() async {
        let seen = Seen()
        let first = await repo(204, seen).commit(dateId: "d1")
        XCTAssertTrue(isOk(first))
        let request = await seen.requests[0]
        XCTAssertEqual(request.method, .post)
        XCTAssertEqual(request.path, "/dates/d1/arrival/commit")
        let missing = await repo(404).commit(dateId: "d1")
        XCTAssertTrue(isOk(missing))
        let unavailable = await repo(503).commit(dateId: "d1")
        XCTAssertEqual(failure(unavailable), .server)
        let offline = await repo(nil).commit(dateId: "d1")
        XCTAssertEqual(failure(offline), .network)
    }
}
