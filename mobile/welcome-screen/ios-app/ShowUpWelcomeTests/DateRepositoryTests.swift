// DateRepositoryTests.swift
// ShowUp · `GET /me/date` read into the app's types, through the generated client (SHOWUP-193)
//
// Android's `DateRepositoryTest.kt`: a date maps field for field, `{ date: null }` is no date, a failure
// is the server's, and a missing place or unread message is nil -- never a placeholder.

import XCTest
import OpenAPIRuntime
import HTTPTypes
import ShowUpAPI
@testable import ShowUpWelcome

final class DateRepositoryTests: XCTestCase {

    /// Returns a canned response.
    private struct StubTransport: ClientTransport {
        let status: Int
        let responseBody: String

        func send(_ request: HTTPRequest, body: HTTPBody?, baseURL: URL,
                  operationID: String) async throws -> (HTTPResponse, HTTPBody?) {
            (HTTPResponse(status: .init(code: status), headerFields: [.contentType: "application/json"]),
             HTTPBody(responseBody))
        }
    }

    private func repo(status: Int, body: String) -> DateRepository {
        DateRepository(api: ShowUpAPI(tokens: InMemoryTokenStore(accessToken: "t"),
                                      transport: StubTransport(status: status, responseBody: body)))
    }

    private let full = """
    {"date":{"id":"d1","meetAt":"2026-11-03T18:00:00Z","meetDay":"today","unlockAt":"2026-11-03T16:30:00Z",\
    "coordinateOpen":false,"place":{"name":"Cafe Krone","address":"Oranienstrasse 42, Kreuzberg"},\
    "match":{"userId":"u2","name":"Leonie","age":28,"district":"Neukölln","distanceKm":1,\
    "photoUrl":"/uploads/leonie.jpg","attendedCount":4},"motivations":{"you":"long_term","them":"long_term"},\
    "sharedVibes":["coffee_chat","playful"],"sharedInterests":["reading"],\
    "unread":{"count":2,"reason":"running_late","body":"Ten minutes late!","sentAt":"2026-11-03T17:10:00Z"},\
    "checkedIn":false}}
    """

    func testADateMapsFieldForField() async {
        guard case .success(.active(let date)) = await repo(status: 200, body: full).active(zone: "Europe/Berlin") else {
            return XCTFail("expected an active date")
        }
        XCTAssertEqual(date.id, "d1")
        XCTAssertEqual(date.meetAt, Date(timeIntervalSince1970: 1_793_728_800))
        XCTAssertEqual(date.unlockAt, Date(timeIntervalSince1970: 1_793_728_800 - 90 * 60))
        XCTAssertEqual(date.meetDay, "today")
        XCTAssertEqual(date.place, DatePlace(name: "Cafe Krone", address: "Oranienstrasse 42, Kreuzberg"))
        XCTAssertEqual(date.match.name, "Leonie")
        XCTAssertEqual(date.match.age, 28)
        XCTAssertEqual(date.match.distanceKm, 1)
        XCTAssertEqual(date.match.attendedCount, 4)
        XCTAssertTrue(date.match.photoUrl?.absoluteString.hasSuffix("/uploads/leonie.jpg") == true,
                      "the photo path becomes a loadable URL")
        XCTAssertEqual(date.motivationYou, "long_term")
        XCTAssertEqual(date.sharedVibes, ["coffee_chat", "playful"])
        XCTAssertEqual(date.sharedInterests, ["reading"])
        XCTAssertEqual(date.unread?.count, 2)
        XCTAssertEqual(date.unread?.reason, "running_late")
        XCTAssertEqual(date.unread?.body, "Ten minutes late!")
        XCTAssertFalse(date.checkedIn)
    }

    func testNoDateIsNone() async {
        guard case .success(.none) = await repo(status: 200, body: #"{"date":null}"#).active(zone: "Europe/Berlin") else {
            return XCTFail("expected no date")
        }
    }

    func testAServerErrorIsTheServers() async {
        let body = #"{"statusCode":500,"message":"Internal server error","error":"Internal Server Error"}"#
        guard case .failure(.server) = await repo(status: 500, body: body).active(zone: "Europe/Berlin") else {
            return XCTFail("expected a server failure")
        }
    }

    func testAMissingPlaceAndNoUnreadAreNilNeverAPlaceholder() async {
        let sparse = full
            .replacingOccurrences(of: #""place":{"name":"Cafe Krone","address":"Oranienstrasse 42, Kreuzberg"}"#, with: #""place":null"#)
            .replacingOccurrences(of: #""unread":{"count":2,"reason":"running_late","body":"Ten minutes late!","sentAt":"2026-11-03T17:10:00Z"}"#,
                                  with: #""unread":null"#)
        guard case .success(.active(let date)) = await repo(status: 200, body: sparse).active(zone: "Europe/Berlin") else {
            return XCTFail("expected an active date")
        }
        XCTAssertNil(date.place)
        XCTAssertNil(date.unread)
    }
}
