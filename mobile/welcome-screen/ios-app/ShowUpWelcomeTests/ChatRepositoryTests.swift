// ChatRepositoryTests.swift
// ShowUp · what the chat's routes answer and how the app reads them (SHOWUP-194). Android's
// `ChatRepositoryTest.kt`: every assertion is made against bytes the generated client parsed.

import XCTest
import OpenAPIRuntime
import HTTPTypes
import ShowUpAPI
@testable import ShowUpWelcome

final class ChatRepositoryTests: XCTestCase {

    /// Returns a canned response, whatever was asked.
    private struct StubTransport: ClientTransport {
        let status: Int
        let responseBody: String

        func send(_ request: HTTPRequest, body: HTTPBody?, baseURL: URL,
                  operationID: String) async throws -> (HTTPResponse, HTTPBody?) {
            (HTTPResponse(status: .init(code: status), headerFields: [.contentType: "application/json"]),
             responseBody.isEmpty ? nil : HTTPBody(responseBody))
        }
    }

    private func repo(status: Int, body: String) -> ChatRepository {
        ChatRepository(api: ShowUpAPI(tokens: InMemoryTokenStore(accessToken: "t"),
                                      transport: StubTransport(status: status, responseBody: body)))
    }

    func testTheThreadMapsEveryFieldOldestFirst() async {
        let body = #"[{"id":"m1","reason":"running_late","body":"Ten minutes late!","fromYou":false,"createdAt":"2026-11-03T16:30:00Z","read":null},{"id":"m2","reason":null,"body":"No worries","fromYou":true,"createdAt":"2026-11-03T16:31:00Z","read":true}]"#
        guard case .success(let thread) = await repo(status: 200, body: body).thread(dateId: "d1") else {
            return XCTFail("expected a thread")
        }
        XCTAssertEqual(thread.map(\.id), ["m1", "m2"])
        XCTAssertEqual(thread[0].reason, .runningLate)
        XCTAssertEqual(thread[0].body, "Ten minutes late!")
        XCTAssertFalse(thread[0].fromYou)
        XCTAssertNil(thread[0].read)
        XCTAssertNil(thread[1].reason)
        XCTAssertEqual(thread[1].read, true)
    }

    func testAnOldMessageSentWithAReasonAloneReadsAsItsReasonsLabel() async {
        let body = #"[{"id":"m1","reason":"on_my_way","body":null,"fromYou":false,"createdAt":"2026-11-03T16:30:00Z","read":null}]"#
        guard case .success(let thread) = await repo(status: 200, body: body).thread(dateId: "d1") else {
            return XCTFail("expected a thread")
        }
        XCTAssertEqual(thread.first?.body, "I'm on my way")
    }

    func testSendReadsTheMessageBack() async {
        let body = #"{"id":"m3","reason":"cant_make_it","body":"So sorry","fromYou":true,"createdAt":"2026-11-03T16:40:00Z","read":false}"#
        guard case .success(let sent) = await repo(status: 201, body: body).send(dateId: "d1", reason: .cantMakeIt, body: "So sorry") else {
            return XCTFail("expected the sent message")
        }
        XCTAssertEqual(sent.id, "m3")
        XCTAssertEqual(sent.reason, .cantMakeIt)
        XCTAssertEqual(sent.read, false)
    }

    func testARefusedSendIsTheServers() async {
        guard case .failure(.server) = await repo(status: 409, body: #"{"statusCode":409}"#).send(dateId: "d1", reason: nil, body: "Hi") else {
            return XCTFail("expected a server failure")
        }
    }

    func testEndDateReadsTheOutcomeA409IsAlreadyEndedAndAnythingElseAFailure() async {
        let ended = await repo(status: 200, body: #"{"dateId":"d1","name":"Leonie","endedByYou":true}"#).endInChat(dateId: "d1")
        XCTAssertEqual(ended, .ended(EndedInChat(dateId: "d1", name: "Leonie", endedByYou: true)))
        let again = await repo(status: 409, body: #"{"statusCode":409,"message":"This date has already ended"}"#).endInChat(dateId: "d1")
        XCTAssertEqual(again, .alreadyEnded)
        let broken = await repo(status: 500, body: #"{"statusCode":500}"#).endInChat(dateId: "d1")
        XCTAssertEqual(broken, .failed(.server))
    }
}
