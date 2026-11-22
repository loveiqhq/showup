// DateEndRepositoryTests.swift
// ShowUp · the end, Stay for now and cancel calls, and the ended payload they and `GET /me/date` share (SHOWUP-197, 198)
//
// Android's `DateEndRepositoryTest.kt`: 200 / 409 codes / 404 / failures map to results, every reason maps, and a
// reason from an older server reads as `.chat`.

import XCTest
import OpenAPIRuntime
import HTTPTypes
import ShowUpAPI
@testable import ShowUpWelcome

final class DateEndRepositoryTests: XCTestCase {

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

    /// No response at all: the phone is offline.
    private struct DeadTransport: ClientTransport {
        func send(_ request: HTTPRequest, body: HTTPBody?, baseURL: URL,
                  operationID: String) async throws -> (HTTPResponse, HTTPBody?) {
            throw URLError(.notConnectedToInternet)
        }
    }

    private func api(status: Int, body: String) -> ShowUpAPI {
        ShowUpAPI(tokens: InMemoryTokenStore(accessToken: "t"), transport: StubTransport(status: status, responseBody: body))
    }

    private func dead() -> ShowUpAPI {
        ShowUpAPI(tokens: InMemoryTokenStore(accessToken: "t"), transport: DeadTransport())
    }

    private func ends(_ status: Int, _ body: String) -> DateEndRepository { DateEndRepository(api: api(status: status, body: body)) }
    private func cancels(_ status: Int, _ body: String) -> CancelRepository { CancelRepository(api: api(status: status, body: body)) }

    private func outcome(_ reason: String, screen: String? = nil) -> String {
        let extra = screen.map { ",\"partnerScreen\":\"\($0)\"" } ?? ""
        return "{\"dateId\":\"d1\",\"name\":\"Leonie\",\"endedByYou\":true,\"reason\":\"\(reason)\"\(extra)}"
    }

    private func refusal(_ code: String) -> String {
        "{\"statusCode\":409,\"code\":\"\(code)\",\"error\":\"\(code)\",\"message\":\"no\"}"
    }

    func testAnEndReadsTheOutcomeWithItsReasonAndTheMatchsScreen() async {
        let result = await ends(200, outcome("partner_no_show", screen: "you_no_showed"))
            .end(dateId: "d1", reason: .partnerNoShow, entry: .checkinBarLink)
        guard case .ended(let ended) = result else { return XCTFail("expected an outcome, got \(result)") }
        XCTAssertEqual(ended.reason, .partnerNoShow)
        XCTAssertEqual(ended.partnerScreen, .youNoShowed)
        XCTAssertEqual(ended.name, "Leonie")
        XCTAssertTrue(ended.endedByYou)
    }

    func testEveryEndReasonAndEntryHasTheServersWireValue() {
        XCTAssertEqual(EndReason.allCases.map(\.rawValue), ["met", "partner_no_show", "uncomfortable"])
        XCTAssertEqual(EndEntry.allCases.map(\.rawValue), ["end_date_bar", "checkin_bar_link", "report"])
    }

    func testAnEndRefusedWith409CarriesTheServersCode() async {
        for code in ["date_not_active", "before_meet", "not_checked_in"] {
            let result = await ends(409, refusal(code)).end(dateId: "d1", reason: .met, entry: .endDateBar)
            XCTAssertEqual(result, .refused(code: code))
        }
    }

    func testAnEndWithTheSwitchOffIsARefusalNotAFailure() async {
        let result = await ends(404, "{\"statusCode\":404,\"message\":\"Not Found\"}")
            .end(dateId: "d1", reason: .met, entry: .endDateBar)
        XCTAssertEqual(result, .refused(code: refusedNotFound))
    }

    func testAnEndThatFailsIsTheServersAndADeadNetworkIsTheNetworks() async {
        let server = await ends(500, "{}").end(dateId: "d1", reason: .met, entry: .endDateBar)
        XCTAssertEqual(server, .failed(.server))
        let network = await DateEndRepository(api: dead()).end(dateId: "d1", reason: .met, entry: .endDateBar)
        XCTAssertEqual(network, .failed(.network))
    }

    func testStayForNowReportsWhetherTheFlagLanded() async {
        let landed = await ends(204, "").flagDiscomfort(dateId: "d1")
        XCTAssertTrue(landed)
        let failed = await ends(500, "{}").flagDiscomfort(dateId: "d1")
        XCTAssertFalse(failed)
    }

    func testACancelReadsTheOutcome() async {
        let result = await cancels(200, outcome("cancelled")).cancel(dateId: "d1")
        guard case .cancelled(let cancelled) = result else { return XCTFail("expected an outcome, got \(result)") }
        XCTAssertEqual(cancelled.reason, .cancelled)
    }

    func testACancelRefusedWith409CarriesTheCode404IsTheSwitchTheRestFail() async {
        for code in ["date_not_active", "checked_in"] {
            let result = await cancels(409, refusal(code)).cancel(dateId: "d1")
            XCTAssertEqual(result, .refused(code: code))
        }
        let off = await cancels(404, "{}").cancel(dateId: "d1")
        XCTAssertEqual(off, .refused(code: refusedNotFound))
        let server = await cancels(500, "{}").cancel(dateId: "d1")
        XCTAssertEqual(server, .failed(.server))
        let network = await CancelRepository(api: dead()).cancel(dateId: "d1")
        XCTAssertEqual(network, .failed(.network))
    }

    func testTheEndedObjectMapsEveryReasonAndTheMatchsScreen() async {
        let screens = ["met": "date_closed_positive", "partner_no_show": "you_no_showed",
                       "uncomfortable": "date_ended_discomfort"]
        let expected: [(String, OutcomeReason)] = [("chat", .chat), ("cancelled", .cancelled), ("met", .met),
                                                   ("partner_no_show", .partnerNoShow), ("uncomfortable", .uncomfortable)]
        for (wire, reason) in expected {
            let extra = screens[wire].map { ",\"partnerScreen\":\"\($0)\"" } ?? ""
            let body = "{\"date\":null,\"ended\":{\"dateId\":\"d1\",\"name\":\"Leonie\",\"endedByYou\":false,\"reason\":\"\(wire)\"\(extra)}}"
            let read = await DateRepository(api: api(status: 200, body: body)).active(zone: "Europe/Berlin")
            guard case .success(.ended(let ended)) = read else { return XCTFail("expected an ended outcome for \(wire)") }
            XCTAssertEqual(ended.reason, reason, wire)
            XCTAssertEqual(ended.partnerScreen, PartnerScreen.fromWire(screens[wire]), wire)
        }
    }

    func testAReasonFromAnOlderServerOrNoneReadsAsChat() {
        XCTAssertEqual(OutcomeReason.fromWire(nil), .chat)
        XCTAssertEqual(OutcomeReason.fromWire("something_new"), .chat)
    }

    func testAnActiveDateCarriesTheEndsSwitchAndTheCancelPenaltyFromTheServer() async {
        let body = """
        {"date":{"id":"d1","meetAt":"2026-11-03T18:00:00Z","meetDay":"today","unlockAt":"2026-11-03T16:30:00Z",\
        "coordinateOpen":false,"place":null,\
        "match":{"userId":"u2","name":"Leonie","age":null,"district":null,"distanceKm":null,"photoUrl":null,"attendedCount":0},\
        "motivations":{"you":null,"them":null},"sharedVibes":[],"sharedInterests":[],"unread":null,\
        "checkedIn":false,"arrival":null,"chatTutorialSeen":true,"endsEnabled":true,"cancelPenaltyPoints":7},"ended":null}
        """
        guard case .success(.active(let date)) = await DateRepository(api: api(status: 200, body: body)).active(zone: "UTC") else {
            return XCTFail("expected an active date")
        }
        XCTAssertTrue(date.endsEnabled)
        XCTAssertEqual(date.cancelPenaltyPoints, 7)
    }
}
