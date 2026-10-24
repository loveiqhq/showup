//  DevMenuRulesTests.swift
//  ShowUp · the developer menu's rules: the event log, the screen list, where a jump lands
//
//  Android twin: `DevMenuRulesTest.kt`. The jump itself reuses the walk's own ways in
//  (`afterMedia`, `enterLocation`), covered where they live; what is pinned here is what the menu
//  PROMISES before the tap — that it names the same skip the walk would make, and refuses what a
//  signed-out user cannot use. The menu exists in debug builds only, and so do these tests.

#if DEBUG
import XCTest
@testable import ShowUpWelcome

@MainActor
final class DevMenuRulesTests: XCTestCase {

    // MARK: - the event log

    func testEventsAreListedNewestFirstWithWhatTheyCarried() {
        var now = Date(timeIntervalSince1970: 1)
        let log = EventLog(clock: { now })
        log.record("screen_viewed", properties: ["screen_id": "profile_height"])
        now = Date(timeIntervalSince1970: 2)
        log.record("detail_answered", properties: ["field_id": "height"])

        XCTAssertEqual(log.entries.map(\.event), ["detail_answered", "screen_viewed"])
        XCTAssertEqual(log.entries.first?.at, Date(timeIntervalSince1970: 2))
        XCTAssertEqual(log.entries.last?.properties["screen_id"] as? String, "profile_height")
    }

    func testTheLogIsCappedDroppingTheOldest() {
        let log = EventLog(capacity: 3)
        for index in 0..<5 { log.record("e\(index)", properties: [:]) }

        XCTAssertEqual(log.entries.map(\.event), ["e4", "e3", "e2"])
    }

    func testClearEmptiesItAndIdsKeepRising() {
        let log = EventLog()
        log.record("a", properties: [:])
        let firstId = log.entries.first?.id ?? 0
        log.clear()
        XCTAssertTrue(log.entries.isEmpty)

        log.record("b", properties: [:])
        XCTAssertGreaterThan(log.entries.first?.id ?? 0, firstId)
    }

    func testAProhibitedKeyIsWithheldEverythingElseIsShown() {
        XCTAssertEqual(devShownValue("email", "someone@example.com"), "[withheld]")
        XCTAssertEqual(devShownValue("phone", "+4917600000000"), "[withheld]")
        XCTAssertEqual(devShownValue("screen_id", "profile_height"), "profile_height")
        // The tutorial's card NUMBER, not a payment card — withheld, it read as a broken event.
        XCTAssertEqual(devShownValue("card", 2), "2")
        XCTAssertEqual(devShownValue("cardnumber", "4111111111111111"), "[withheld]")
    }

    // MARK: - the screen list

    func testEveryScreenIsListedExactlyOnceInFlowOrderWithinItsSection() {
        let listed = DevSection.allCases.flatMap { section in
            FlowScreen.allCases.filter { $0.devSection == section }
        }
        XCTAssertEqual(listed, FlowScreen.allCases)
    }

    func testEveryScreenHasItsOwnName() {
        let labels = FlowScreen.allCases.map(\.devLabel)
        XCTAssertEqual(labels.count, Set(labels).count)
    }

    func testTheProfileScreensNeedAnAccountSignUpTheTutorialAndHomeDoNot() {
        XCTAssertFalse(FlowScreen.signUp.needsAccount)
        XCTAssertFalse(FlowScreen.thirtyMinutes.needsAccount)
        XCTAssertFalse(FlowScreen.home.needsAccount)
        XCTAssertTrue(FlowScreen.profileName.needsAccount)
        XCTAssertTrue(FlowScreen.profilePhotos.needsAccount)
        XCTAssertTrue(FlowScreen.profileLocation.needsAccount)
        XCTAssertTrue(FlowScreen.profileGender.needsAccount)
    }

    // MARK: - where a jump lands

    private func jump(_ target: FlowScreen, signedIn: Bool = true, notificationsAsk: Bool = true,
                      location: LocationArrival = .show(.ask)) -> DevJump {
        devJump(target, signedIn: signedIn, notificationsAsk: notificationsAsk, location: location)
    }

    func testSignedOutAProfileScreenIsRefusedAndTheTutorialIsNot() {
        XCTAssertEqual(jump(.profileHeight, signedIn: false), .needsLogIn)
        XCTAssertEqual(jump(.matchMeansMeet, signedIn: false), .lands(.matchMeansMeet))
    }

    func testNotificationsAlreadyAnsweredSkipToStayReachableAsTheWalkDoes() {
        XCTAssertEqual(jump(.profileNotifications, notificationsAsk: false),
                       .skipped(lands: .profileReachability, why: "Skipped: already answered on this phone"))
        XCTAssertEqual(jump(.profileNotifications), .lands(.profileNotifications))
    }

    func testLocationAlreadyAllowedSkipsToEmbrace2AnyOtherStatusShowsTheScreen() {
        XCTAssertEqual(jump(.profileLocation, location: .skip),
                       .skipped(lands: .profileEmbraceDetails, why: "Skipped: location already allowed"))
        for state in [LocationState.ask, .denied, .servicesOff] {
            XCTAssertEqual(jump(.profileLocation, location: .show(state)), .lands(.profileLocation))
        }
    }

    /// Android's DevStatesTest, the rules half.
    func testSignedOutACheckInRowOpensOnTheTestServerAndOtherAccountScreensStillRefuse() {
        XCTAssertEqual(jump(.checkInToday, signedIn: false), .testServer(.checkInToday))
        XCTAssertEqual(jump(.checkInGate, signedIn: false), .testServer(.checkInGate))
        XCTAssertEqual(jump(.profileHeight, signedIn: false), .needsLogIn)
        XCTAssertEqual(jump(.checkInToday), .lands(.checkInToday))
    }

    func testTheCheckInScreensSitInTheirOwnSectionAndNeedAnAccount() {
        let section = FlowScreen.allCases.filter { $0.devSection == .checkIn }
        XCTAssertEqual(section, FlowScreen.allCases.filter(\.isCheckIn))
        XCTAssertTrue(section.allSatisfy(\.needsAccount))
        XCTAssertEqual(DevSection.checkIn.rawValue, "48hrs Check-In")
        XCTAssertEqual(FlowScreen.checkInGate.devLabel, "Date search not available (gate)")
        XCTAssertEqual(FlowScreen.checkInUplift.devLabel, "6 · Uplift + Find my date")
    }

    /// Every States entry opens a frame of its own -- a row with none would open "Unknown state".
    func testEveryStatesEntryOpensAFrameOfItsOwn() {
        let frames = devStates.map { DevFrame.of($0.id) }
        XCTAssertFalse(frames.contains(nil), "a States row has no frame")
        XCTAssertEqual(Set(frames.compactMap { $0 }).count, devStates.count, "two rows open the same frame")
        XCTAssertEqual(Set(frames.compactMap { $0 }), Set(DevFrame.allCases), "a frame no row opens")
        XCTAssertNil(DevFrame.of("178Z"))
    }

    func testTheStatesPageListsEveryFrameTheSixTicketsNameOnce() {
        XCTAssertEqual(Set(devStates.map(\.id)).count, devStates.count)
        var perTicket: [String: Int] = [:]
        for state in devStates { perTicket[String(state.id.prefix(3)), default: 0] += 1 }
        XCTAssertEqual(perTicket, ["178": 2, "179": 5, "180": 3, "181": 5, "182": 3, "183": 5, "184": 10])
        XCTAssertEqual(devStateTickets.count, 6)
    }

    func testEveryOtherScreenLandsWhereAsked() {
        let special: Set<FlowScreen> = [.profileNotifications, .profileLocation]
        for screen in FlowScreen.allCases where !special.contains(screen) {
            XCTAssertEqual(jump(screen), .lands(screen), screen.rawValue)
        }
    }
}
#endif
