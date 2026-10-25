//
//  PremiumModelTests.swift
//  ShowUp · the Premium grid (SHOWUP-185) and the Likes tab (SHOWUP-186), driven with no device
//
//  The Swift twin of `PremiumViewModelTest.kt`: every row of both tickets' tracking tables with its
//  payload, and the events that must NOT fire; the plan picking the layout when the tab opens, a card
//  opening the full profile, every action returning to its list, the fly-out's states, the decline's
//  Undo (sent once, at the end, or when the user leaves), the mutual confirm that waits for the server,
//  the sorry sheet and the card closed in its slot, the availability re-check, and a like flow kept
//  across the process ending. The Undo window is the model's injected one, short here.
//

import XCTest
@testable import ShowUpWelcome

@MainActor
private final class Recorder: AnalyticsTracking {
    var events: [(String, [String: any Sendable])] = []

    nonisolated func track(_ name: String, properties: [String: any Sendable]) {
        MainActor.assumeIsolated { events.append((name, properties)) }
    }

    var names: [String] { events.map(\.0) }
    func of(_ name: String) -> [[String: any Sendable]] { events.filter { $0.0 == name }.map(\.1) }
    func clear() { events.removeAll() }
}

/// A server that records every call and can be made to fail or to answer a confirm, a re-check or a
/// list read in a particular way. The Swift twin of `FakeSearchStore.kt`.
private actor PremiumStore: SearchStoring {
    var premium = true
    var coachHidden = false
    var openCount = 2
    private(set) var countReads = 0
    private(set) var queueReads = 0
    private(set) var likes: [(String, String?, Int)] = []
    private(set) var passes: [(String, SearchAnalytics.PassType)] = []

    var grid = SearchSamples.grid
    var gridFailure: CallFailure?
    private(set) var gridReads = 0

    /// What the next re-check answers, per person; anyone not here is unchanged.
    var statuses: [String: CardStatus] = [:]
    var availabilityFails = false
    private(set) var availabilityAsks: [[String]] = []

    var likesList = SearchSamples.likes

    /// The confirm's answer; nil answers nothing at all (offline).
    var answer: ConfirmAnswer? = .won
    private(set) var confirms: [(String, Int)] = []

    func setPremium(value: Bool) { premium = value }
    func setOpenCount(_ value: Int) { openCount = value }
    func setGrid(_ value: [GridCard]) { grid = value }
    func setGridFailure(_ value: CallFailure?) { gridFailure = value }
    func setStatuses(_ value: [String: CardStatus]) { statuses = value }
    func setAvailabilityFails(_ value: Bool) { availabilityFails = value }
    func setLikesList(_ value: [LikeReceived]) { likesList = value }
    func setAnswer(_ value: ConfirmAnswer?) { answer = value }

    func queue(zone: String) async -> Result<SearchQueue, CallFailure> {
        queueReads += 1
        let profiles = [SearchSamples.full] + SearchSamples.next
        return .success(SearchQueue(resultsCount: profiles.count, profiles: profiles, viewer: SearchSamples.viewer))
    }

    func like(targetUserId: String, message: String?, position: Int) async -> Result<Void, CallFailure> {
        likes.append((targetUserId, message, position))
        return .success(())
    }

    func pass(targetUserId: String, type: SearchAnalytics.PassType) async -> Result<Void, CallFailure> {
        passes.append((targetUserId, type))
        return .success(())
    }

    func hideCoach() async -> Result<Void, CallFailure> { .success(()) }

    func likesOpenCount() async -> Result<Int, CallFailure> {
        countReads += 1
        return .success(openCount)
    }

    func session() async -> Result<SearchSession, CallFailure> {
        .success(SearchSession(premium: premium, coachHidden: coachHidden))
    }

    func setPremium(_ premium: Bool) async -> Result<Void, CallFailure> { .success(()) }

    func grid(zone: String) async -> Result<SearchGrid, CallFailure> {
        gridReads += 1
        if let gridFailure { return .failure(gridFailure) }
        return .success(SearchGrid(resultsCount: grid.count, cards: grid, viewer: SearchSamples.viewer))
    }

    func availability(userIds: [String], zone: String) async -> Result<[String: CardStatus], CallFailure> {
        availabilityAsks.append(userIds)
        if availabilityFails { return .failure(.network) }
        return .success(statuses.filter { userIds.contains($0.key) })
    }

    func likesReceived(zone: String) async -> Result<LikesReceived, CallFailure> {
        let open = likesList.filter { $0.closed == nil }.count
        return .success(LikesReceived(openCount: open, closedCount: likesList.count - open, cards: likesList,
                                      viewer: SearchSamples.viewer))
    }

    func confirm(targetUserId: String, position: Int) async -> Result<ConfirmAnswer, CallFailure> {
        confirms.append((targetUserId, position))
        guard let answer else { return .failure(.network) }
        return .success(answer)
    }
}

@MainActor
final class PremiumModelTests: XCTestCase {

    private var store = PremiumStore()
    private var events = Recorder()
    private var ms = 1_000
    /// What scene storage holds -- the like flow a process that ends leaves behind.
    private var stored = ""

    private let leonie = "sample-leonie"
    private let sofia = "sample-sofia"
    private let maya = "sample-maya"
    private let anna = "sample-anna"
    private let emma = "sample-emma"
    private let liv = "sample-liv"
    private let clara = "sample-clara"

    /// The Undo window here: long enough to look inside, short enough to wait out.
    private let window: TimeInterval = 0.3

    override func setUp() {
        super.setUp()
        store = PremiumStore()
        events = Recorder()
        ms = 1_000
        stored = ""
    }

    private func model(realClock: Bool = false) -> SearchModel {
        let m = SearchModel(store: store, analytics: events,
                            millis: realClock ? { Int(Date().timeIntervalSince1970 * 1000) } : { [unowned self] in self.ms },
                            retryDelays: [0.01, 0.02, 0.04], undoWindow: window)
        m.attach(stored: stored) { [unowned self] in self.stored = $0 }
        return m
    }

    /// Lets the model's own tasks -- a read, a send, a re-check -- run.
    private func settle(_ seconds: Double = 0.05) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }

    /// A Premium Search visit, the grid loaded.
    private func onGrid(realClock: Bool = false) async -> SearchModel {
        let m = model(realClock: realClock)
        m.visit(zone: "Europe/Berlin", filtersActive: ["distance_km"], referrer: .checkinUplift)
        await settle()
        return m
    }

    /// A Likes tab visit, the list loaded.
    private func onLikes() async -> SearchModel {
        let m = model()
        m.likesArrived(zone: "Europe/Berlin", referrer: .searchGrid)
        await settle()
        return m
    }

    private func props(_ name: String, _ nth: Int = 0, file: StaticString = #filePath, line: UInt = #line) -> [String: any Sendable] {
        let all = events.of(name)
        guard all.count > nth else {
            XCTFail("\(name) fired \(all.count) times", file: file, line: line)
            return [:]
        }
        return all[nth]
    }

    private func card(_ m: SearchModel, _ from: PremiumSurface, _ id: String) -> PremiumCard? {
        m.state.list(from).card(id)
    }

    /// Opens a card past the coach, if the coach comes.
    private func open(_ m: SearchModel, _ from: PremiumSurface, _ id: String) {
        m.openCard(from, userId: id)
        if m.state.coach { m.dismissCoach(.close, dontShowAgain: false) }
    }

    // MARK: - the plan picks the layout (Search 02 "Who sees it")

    func testAPremiumVisitIsTheGridScreenViewedSearchGridThenSearchViewedWithLayoutGrid() async {
        let m = await onGrid()
        XCTAssertEqual(m.state.layout, .grid)
        XCTAssertEqual(events.names, ["screen_viewed", SearchAnalytics.searchViewedName])
        XCTAssertEqual(props("screen_viewed")["screen_id"] as? String, "search_grid")
        XCTAssertEqual(props("screen_viewed")["screen_name"] as? String, "Search - Grid")
        XCTAssertEqual(props("screen_viewed")["referrer_screen_id"] as? String, "checkin_uplift")
        XCTAssertEqual(props(SearchAnalytics.searchViewedName)["layout"] as? String, "grid")
        XCTAssertEqual(props(SearchAnalytics.searchViewedName)["results_count"] as? Int, 6)
        XCTAssertEqual(props(SearchAnalytics.searchViewedName)["filters_active"] as? [String], ["distance_km"])
        let queueReads = await store.queueReads
        XCTAssertEqual(queueReads, 0)
        XCTAssertEqual(m.state.grid.cards.map(\.userId), SearchSamples.grid.map(\.profile.userId))
        XCTAssertFalse(m.state.coach, "no coach over the grid: it waits for an opened profile")
        XCTAssertTrue(events.of(SearchAnalytics.likesReceivedViewedName).isEmpty)
    }

    func testAFreemiumVisitIsTheQueueUnchanged() async {
        await store.setPremium(value: false)
        let m = await onGrid()
        XCTAssertEqual(m.state.layout, .oneUp)
        XCTAssertEqual(props("screen_viewed")["screen_id"] as? String, "search_profile")
        XCTAssertEqual(props(SearchAnalytics.searchViewedName)["layout"] as? String, "one_up")
        let gridReads = await store.gridReads
        XCTAssertEqual(gridReads, 0)
    }

    func testThePlanChangingMidVisitTakesEffectOnTheNextVisitNeverUnderTheUser() async {
        let m = await onGrid()
        await store.setPremium(value: false)
        open(m, .grid, sofia)
        m.backFromProfile()
        await settle()
        XCTAssertEqual(m.state.layout, .grid)
        m.visit(zone: "Europe/Berlin", filtersActive: [], referrer: nil)
        await settle()
        XCTAssertEqual(m.state.layout, .oneUp)
    }

    func testAGridThatCannotBeReadShowsTheFailedStateAndRetryReadsItAgain() async {
        await store.setGridFailure(.network)
        let m = await onGrid()
        XCTAssertEqual(m.state.grid.failure, .network)
        await store.setGridFailure(nil)
        m.retry(zone: "Europe/Berlin")
        await settle()
        XCTAssertNil(m.state.grid.failure)
        XCTAssertEqual(m.state.grid.cards.count, 6)
        let gridReads = await store.gridReads
        XCTAssertEqual(gridReads, 2)
    }

    // MARK: - a card opens the full profile

    func testOpeningACardIsScreenViewedSearchProfileWithTheGridAsReferrerAndNothingElse() async {
        let m = await onGrid()
        events.clear()
        m.openCard(.grid, userId: leonie)
        XCTAssertEqual(events.names, ["screen_viewed"])
        XCTAssertEqual(props("screen_viewed")["screen_id"] as? String, "search_profile")
        XCTAssertEqual(props("screen_viewed")["referrer_screen_id"] as? String, "search_grid")
        XCTAssertEqual(m.state.current?.userId, leonie)
        XCTAssertTrue(m.state.likedYou)
    }

    func testTheCoachWaitsForTheFirstOpenedProfileWithLikeNeverALikedYouOne() async {
        let m = await onGrid()
        m.openCard(.grid, userId: leonie)
        XCTAssertFalse(m.state.coach, "Leonie liked the viewer: her third button is Confirm date")
        m.backFromProfile()
        await settle()
        m.openCard(.grid, userId: sofia)
        XCTAssertTrue(m.state.coach)
        m.dismissCoach(.close, dontShowAgain: false)
        m.backFromProfile()
        await settle()
        m.openCard(.grid, userId: maya)
        XCTAssertFalse(m.state.coach, "once per Search visit")
        XCTAssertEqual(events.of(SearchAnalytics.searchCoachShownName).count, 1)
    }

    func testBackReturnsToTheGridUnchangedScreenViewedSearchGridCardViewedAndTheReCheck() async {
        let m = await onGrid()
        open(m, .grid, sofia)
        ms += 3_000
        events.clear()
        m.backFromProfile()
        await settle()
        XCTAssertEqual(events.names, [SearchAnalytics.profileCardViewedName, "screen_viewed"])
        XCTAssertEqual(props(SearchAnalytics.profileCardViewedName)["position"] as? Int, 2)
        XCTAssertEqual(props(SearchAnalytics.profileCardViewedName)["dwell_ms"] as? Int, 3_000)
        XCTAssertEqual(props("screen_viewed")["screen_id"] as? String, "search_grid")
        XCTAssertEqual(props("screen_viewed")["referrer_screen_id"] as? String, "search_profile")
        XCTAssertNil(m.state.opened)
        XCTAssertEqual(m.state.grid.cards, SearchSamples.gridCards())
        let asks = await store.availabilityAsks
        XCTAssertEqual(asks, [SearchSamples.grid.map(\.profile.userId)])
        XCTAssertTrue(events.of(SearchAnalytics.searchViewedName).isEmpty, "the grid did not reload")
        XCTAssertTrue(m.takeReturn(.grid), "the grid's reappearance under the popped profile is not an arrival")
        XCTAssertFalse(m.takeReturn(.grid), "asked once")
    }

    // MARK: - the actions, from a profile opened from the grid

    func testNotNowMarksTheCardAndFiresPassSentAtOnceSurfaceProfile() async {
        let m = await onGrid()
        open(m, .grid, sofia)
        m.pass(.notNow)
        await settle()
        let pass = props(SearchAnalytics.passSentName)
        XCTAssertEqual(pass["position"] as? Int, 2)
        XCTAssertEqual(pass["pass_type"] as? String, "not_now")
        XCTAssertEqual(pass["surface"] as? String, "profile")
        XCTAssertEqual(card(m, .grid, sofia)?.notNow, true)
        XCTAssertEqual(card(m, .grid, sofia)?.open, true, "a Not now card still opens")
        let passes = await store.passes
        XCTAssertEqual(passes.map(\.0), [sofia])
        XCTAssertEqual(passes.map(\.1), [.notNow])
        XCTAssertNil(m.state.undo)
    }

    func testNotInterestedOnAProfileThatDidNotLikeYouFliesOutWithNoUndo() async {
        let m = await onGrid()
        open(m, .grid, sofia)
        m.pass(.notInterested)
        await settle()
        XCTAssertEqual(card(m, .grid, sofia)?.leaving, .notInterested)
        XCTAssertEqual(props(SearchAnalytics.passSentName)["pass_type"] as? String, "not_interested")
        XCTAssertNil(m.state.undo)
        m.cardLeft(.grid, userId: sofia)
        XCTAssertEqual(card(m, .grid, sofia)?.gone, true)
        XCTAssertEqual(m.state.grid.shown.map(\.userId), [leonie, maya, anna, emma, liv], "the rest keep their order")
    }

    func testLikeReturnsAfterSkipMessageTheCardFlyingOutWithItsHeartFilled() async {
        let m = await onGrid()
        open(m, .grid, sofia)
        events.clear()
        m.like()
        m.continueToMessage()
        m.skipMessage()
        await settle()
        XCTAssertEqual(events.names, [SearchAnalytics.likeTappedName, SearchAnalytics.likeConfirmShownName,
                                      SearchAnalytics.profileCardViewedName, "screen_viewed"])
        XCTAssertEqual(props(SearchAnalytics.likeTappedName)["position"] as? Int, 2)
        XCTAssertEqual(props(SearchAnalytics.likeTappedName)["surface"] as? String, "profile")
        XCTAssertEqual(props(SearchAnalytics.likeConfirmShownName)["is_mutual"] as? Bool, false)
        XCTAssertEqual(card(m, .grid, sofia)?.leaving, .liked)
        let likes = await store.likes
        XCTAssertEqual(likes.map(\.0), [sofia])
        XCTAssertEqual(likes.map(\.2), [2])
        XCTAssertNil(likes.first?.1)
    }

    func testCancellingASheetStaysOnTheProfileAndSendsNothing() async {
        let m = await onGrid()
        open(m, .grid, sofia)
        m.like()
        m.cancelConfirm()
        XCTAssertEqual(m.state.opened?.userId, sofia)
        m.like()
        m.continueToMessage()
        m.dismissMessage()
        await settle()
        XCTAssertEqual(m.state.opened?.userId, sofia)
        let likes = await store.likes
        XCTAssertTrue(likes.isEmpty)
        XCTAssertEqual(events.of(SearchAnalytics.likeConfirmAbandonedName).compactMap { $0["stage"] as? String },
                       ["confirm", "message"])
    }

    // MARK: - the mutual confirm

    func testConfirmDateOpensMutualBeSureWithNoMessageStep() async {
        let m = await onGrid()
        open(m, .grid, leonie)
        events.clear()
        m.like()
        XCTAssertEqual(m.state.sheet, .confirm)
        XCTAssertEqual(props(SearchAnalytics.likeConfirmShownName)["is_mutual"] as? Bool, true)
        XCTAssertEqual(props(SearchAnalytics.likeTappedName)["surface"] as? String, "profile")
        m.continueToMessage()
        XCTAssertEqual(m.state.sheet, .confirm, "never the message sheet")
        m.cancelConfirm()
        XCTAssertEqual(props(SearchAnalytics.likeConfirmAbandonedName)["is_mutual"] as? Bool, true)
        XCTAssertEqual(props(SearchAnalytics.likeConfirmAbandonedName)["stage"] as? String, "confirm")
    }

    func testWhileTheConfirmIsWithTheServerTheButtonIsInertAndTheSheetDoesNotClose() async {
        let m = await onGrid()
        open(m, .grid, leonie)
        m.like()
        m.confirmDate()
        XCTAssertTrue(m.state.confirming)
        m.confirmDate()
        m.cancelConfirm()
        XCTAssertEqual(m.state.sheet, .confirm)
        await settle()
        let confirms = await store.confirms
        XCTAssertEqual(confirms.map(\.0), [leonie], "one confirm sent")
        XCTAssertEqual(confirms.map(\.1), [1])
    }

    func testAWonConfirmReturnsToTheGridAndTheCardFliesOut() async {
        let m = await onGrid()
        open(m, .grid, leonie)
        m.like()
        m.confirmDate()
        await settle()
        XCTAssertNil(m.state.opened)
        XCTAssertFalse(m.state.confirming)
        XCTAssertEqual(card(m, .grid, leonie)?.leaving, .liked)
        XCTAssertTrue(events.of(SearchAnalytics.likeConfirmFailedName).isEmpty)
        XCTAssertTrue(events.of(SearchAnalytics.likeMessageAttachedName).isEmpty, "no message on a confirm")
    }

    func testARefusedConfirmShowsTheSorrySheetFiresLikeConfirmFailedOnceAndClosesTheCardInItsSlot() async {
        await store.setAnswer(.refused(.matched))
        let m = await onGrid()
        open(m, .grid, leonie)
        m.like()
        m.confirmDate()
        await settle()
        XCTAssertEqual(m.state.sheet, .refused)
        XCTAssertEqual(m.state.refused, .matched)
        XCTAssertEqual(events.of(SearchAnalytics.likeConfirmFailedName).count, 1)
        XCTAssertEqual(props(SearchAnalytics.likeConfirmFailedName)["reason"] as? String, "matched")
        XCTAssertEqual(props(SearchAnalytics.likeConfirmFailedName)["surface"] as? String, "profile")
        events.clear()
        m.refusedDone()
        await settle()
        XCTAssertEqual(events.names, [SearchAnalytics.profileCardViewedName, "screen_viewed"],
                       "the dismissal fires nothing of its own")
        let closed = card(m, .grid, leonie)
        XCTAssertEqual(closed?.closed, .matched)
        XCTAssertEqual(closed?.open, false, "not a tap target")
        XCTAssertNil(closed?.leaving, "never flies out")
        XCTAssertEqual(m.state.grid.positionOf(leonie), 1, "in the same position")
    }

    func testAConfirmWithNoAnswerAtAllStaysOnBeSureAndReportsNothing() async {
        await store.setAnswer(nil)
        let m = await onGrid()
        open(m, .grid, leonie)
        m.like()
        m.confirmDate()
        await settle()
        XCTAssertEqual(m.state.sheet, .confirm)
        XCTAssertFalse(m.state.confirming)
        XCTAssertTrue(events.of(SearchAnalytics.likeConfirmFailedName).isEmpty)
    }

    // MARK: - the decline's Undo window

    func testNotInterestedOnALikedYouProfileIsADeclineSentOnlyWhenTheWindowEnds() async {
        let m = await onGrid()
        open(m, .grid, anna)
        events.clear()
        m.pass(.notInterested)
        XCTAssertTrue(events.of(SearchAnalytics.passSentName).isEmpty, "never on the tap")
        XCTAssertEqual(card(m, .grid, anna)?.leaving, .notInterested)
        XCTAssertEqual(m.state.undo, UndoToast(userId: anna, name: "Anna", from: .grid, position: 4))
        await settle(window / 3)
        let early = await store.passes
        XCTAssertTrue(early.isEmpty)
        await settle(window + 0.15)
        XCTAssertNil(m.state.undo)
        let passes = await store.passes
        XCTAssertEqual(passes.map(\.0), [anna])
        XCTAssertEqual(passes.map(\.1), [.notInterested])
        let pass = props(SearchAnalytics.passSentName)
        XCTAssertEqual(pass["position"] as? Int, 4)
        XCTAssertEqual(pass["pass_type"] as? String, "not_interested")
        XCTAssertEqual(pass["surface"] as? String, "profile")
    }

    func testUndoPutsTheCardBackAndNothingReachesTheServerPassUndoneAndNoPassSent() async {
        let m = await onGrid()
        open(m, .grid, anna)
        m.pass(.notInterested)
        m.cardLeft(.grid, userId: anna)
        XCTAssertEqual(card(m, .grid, anna)?.gone, true)
        m.undo()
        await settle(window + 0.15)
        let back = card(m, .grid, anna)
        XCTAssertEqual(back?.gone, false)
        XCTAssertNil(back?.leaving)
        XCTAssertEqual(m.state.grid.positionOf(anna), 4, "back in its old slot")
        let passes = await store.passes
        XCTAssertTrue(passes.isEmpty)
        XCTAssertTrue(events.of(SearchAnalytics.passSentName).isEmpty)
        XCTAssertEqual(props(SearchAnalytics.passUndoneName)["position"] as? Int, 4)
        XCTAssertEqual(props(SearchAnalytics.passUndoneName)["surface"] as? String, "profile")
    }

    func testUndoDuringTheFlyOutStopsItTheCardStaysInItsSlot() async {
        let m = await onGrid()
        open(m, .grid, anna)
        m.pass(.notInterested)
        m.undo()
        XCTAssertNil(card(m, .grid, anna)?.leaving)
        XCTAssertEqual(card(m, .grid, anna)?.gone, false)
        m.cardLeft(.grid, userId: anna)
        XCTAssertEqual(card(m, .grid, anna)?.gone, false, "a cancelled flight never finishes")
    }

    func testASecondDeclineInsideTheWindowSendsTheFirstAndStartsANewToast() async {
        let m = await onGrid()
        open(m, .grid, anna)
        m.pass(.notInterested)
        open(m, .grid, leonie)
        await settle()
        let first = await store.passes
        XCTAssertEqual(first.map(\.0), [anna], "opening another card already sent it")
        m.pass(.notInterested)
        XCTAssertEqual(m.state.undo?.userId, leonie)
        XCTAssertEqual(events.of(SearchAnalytics.passSentName).count, 1)
    }

    func testLeavingTheGridOrTheAppSendsAWaitingDeclineAtOnceAndOnlyOnce() async {
        let m = await onGrid()
        open(m, .grid, anna)
        m.pass(.notInterested)
        m.leave()
        await settle()
        XCTAssertNil(m.state.undo)
        let once = await store.passes
        XCTAssertEqual(once.count, 1)
        await settle(window + 0.15)
        let still = await store.passes
        XCTAssertEqual(still.count, 1)
        XCTAssertEqual(events.of(SearchAnalytics.passSentName).count, 1)
    }

    func testFocusOnUndoHoldsTheWindowAndLeavingItLetsWhatWasLeftRunOn() async {
        let m = await onGrid(realClock: true)
        open(m, .grid, anna)
        m.pass(.notInterested)
        await settle(window / 3)
        m.undoFocus(true)
        await settle(window * 2)
        XCTAssertNotNil(m.state.undo, "held while focused")
        m.undoFocus(false)
        await settle(window / 6)
        XCTAssertNotNil(m.state.undo, "what was left runs on, not a fresh window's end")
        await settle(window)
        XCTAssertNil(m.state.undo)
        let passes = await store.passes
        XCTAssertEqual(passes.count, 1)
    }

    func testNotNowOnALikedYouProfileLeavesTheLikeOpenNoUndoTheBadgeUnchanged() async {
        let m = await onGrid()
        open(m, .grid, anna)
        m.pass(.notNow)
        await settle()
        XCTAssertNil(m.state.undo)
        XCTAssertEqual(card(m, .grid, anna)?.notNow, true)
        XCTAssertEqual(props(SearchAnalytics.passSentName)["pass_type"] as? String, "not_now")
        XCTAssertEqual(m.state.likesBadge, 2)
    }

    // MARK: - the availability re-check

    func testAReCheckClosesACardInItsSlotAndShowsAMovedMeetTimeNothingReorders() async {
        let m = await onGrid()
        let moved = Date(timeIntervalSince1970: 1_792_771_200)
        await store.setStatuses([sofia: .closed(.matched), emma: .closed(.noOverlap),
                                 anna: .available(meetAt: moved, meetDay: "tomorrow")])
        open(m, .grid, leonie)
        events.clear()
        m.backFromProfile()
        await settle()
        let grid = m.state.grid
        XCTAssertEqual(grid.shown.map(\.userId), SearchSamples.grid.map(\.profile.userId))
        XCTAssertEqual(grid.card(sofia)?.closed, .matched)
        XCTAssertEqual(grid.card(emma)?.closed, .noOverlap)
        XCTAssertEqual(grid.card(anna)?.profile?.meetAt, moved)
        XCTAssertEqual(grid.card(anna)?.profile?.meetDay, "tomorrow")
        XCTAssertEqual(events.names, [SearchAnalytics.profileCardViewedName, "screen_viewed"], "the re-check fires nothing")
    }

    func testAFailedReCheckLeavesTheGridAsItIs() async {
        let m = await onGrid()
        await store.setAvailabilityFails(true)
        open(m, .grid, leonie)
        m.backFromProfile()
        await settle()
        XCTAssertEqual(m.state.grid.cards, SearchSamples.gridCards())
    }

    func testTheReCheckNeverTouchesACardTheUserAnswered() async {
        let m = await onGrid()
        open(m, .grid, sofia)
        m.like()
        m.continueToMessage()
        m.skipMessage()
        await settle()
        let asks = await store.availabilityAsks
        XCTAssertEqual(asks.last?.contains(sofia), false)
    }

    func testNothingReChecksTheGridButAReturnFromAProfile() async {
        let m = await onGrid()
        ms += 600_000
        m.leave()
        m.resume()
        await settle()
        let asks = await store.availabilityAsks
        XCTAssertTrue(asks.isEmpty)
    }

    // MARK: - the Likes tab (Search 03)

    func testALikesVisitFiresScreenViewedLikesReceivedThenLikesReceivedViewedWithWhatItHolds() async {
        let m = await onLikes()
        XCTAssertEqual(events.names, ["screen_viewed", SearchAnalytics.likesReceivedViewedName])
        XCTAssertEqual(props("screen_viewed")["screen_id"] as? String, "likes_received")
        XCTAssertEqual(props("screen_viewed")["screen_name"] as? String, "Search - Likes received")
        XCTAssertEqual(props("screen_viewed")["referrer_screen_id"] as? String, "search_grid")
        XCTAssertEqual(props(SearchAnalytics.likesReceivedViewedName)["open_count"] as? Int, 2)
        XCTAssertEqual(props(SearchAnalytics.likesReceivedViewedName)["closed_count"] as? Int, 1)
        XCTAssertEqual(m.state.likes.cards.map(\.userId), [anna, leonie, clara], "rendered in the server's order")
        XCTAssertEqual(m.state.likesBadge, 2)
        XCTAssertEqual(card(m, .likes, clara)?.open, false, "a closed card cannot be opened")
        XCTAssertTrue(events.of(SearchAnalytics.searchViewedName).isEmpty)
    }

    func testAnEmptyListFiresLikesReceivedViewedWithZeroAndZero() async {
        await store.setLikesList([])
        let m = await onLikes()
        XCTAssertTrue(m.state.likes.empty)
        XCTAssertEqual(props(SearchAnalytics.likesReceivedViewedName)["open_count"] as? Int, 0)
        XCTAssertEqual(props(SearchAnalytics.likesReceivedViewedName)["closed_count"] as? Int, 0)
    }

    func testACardOpensTheProfileWithTheListAsReferrerAndNeverTheCoach() async {
        let m = await onLikes()
        events.clear()
        m.openCard(.likes, userId: anna)
        XCTAssertFalse(m.state.coach)
        XCTAssertEqual(props("screen_viewed")["referrer_screen_id"] as? String, "likes_received")
        m.openCard(.likes, userId: clara)
        XCTAssertEqual(m.state.opened?.userId, anna, "a closed card does not open")
    }

    func testADeclineFromTheListIsSentAtTheWindowsEndWithSurfaceLikesReceivedAndTheBadgeCountsTheList() async {
        let m = await onLikes()
        m.openCard(.likes, userId: anna)
        m.pass(.notInterested)
        XCTAssertEqual(m.state.likesBadge, 2, "the card is still on the list while it flies")
        m.cardLeft(.likes, userId: anna)
        XCTAssertEqual(m.state.likesBadge, 1, "the badge equals the open cards on the list")
        await store.setOpenCount(1)
        await settle(window + 0.15)
        XCTAssertEqual(props(SearchAnalytics.passSentName)["surface"] as? String, "likes_received")
        XCTAssertEqual(props(SearchAnalytics.passSentName)["position"] as? Int, 1)
        XCTAssertEqual(m.state.likesBadge, 1)
        XCTAssertEqual(m.state.likesOpen, 1)
    }

    func testUndoOnTheListIsPassUndoneWithSurfaceLikesReceived() async {
        let m = await onLikes()
        m.openCard(.likes, userId: leonie)
        m.pass(.notInterested)
        m.undo()
        XCTAssertEqual(props(SearchAnalytics.passUndoneName)["surface"] as? String, "likes_received")
        XCTAssertEqual(props(SearchAnalytics.passUndoneName)["position"] as? Int, 2)
    }

    func testNotNowOnTheListLabelsTheCardSendsNotNowAtOnceWithSurfaceProfileAndKeepsTheLikeOpen() async {
        let m = await onLikes()
        m.openCard(.likes, userId: leonie)
        m.pass(.notNow)
        await settle()
        XCTAssertEqual(card(m, .likes, leonie)?.notNow, true)
        XCTAssertEqual(props(SearchAnalytics.passSentName)["surface"] as? String, "profile")
        XCTAssertEqual(m.state.likesBadge, 2)
    }

    func testAWonConfirmFromTheListTakesTheCardOffIt() async {
        let m = await onLikes()
        m.openCard(.likes, userId: anna)
        m.like()
        m.confirmDate()
        await settle()
        XCTAssertEqual(card(m, .likes, anna)?.gone, true)
        let reads = await store.countReads
        XCTAssertEqual(reads, 1, "the badge read again once the match is made")
    }

    func testARefusedConfirmOnTheListClosesTheCardInPlaceWithItsReason() async {
        await store.setAnswer(.refused(.noOverlap))
        let m = await onLikes()
        m.openCard(.likes, userId: leonie)
        m.like()
        m.confirmDate()
        await settle()
        m.refusedDone()
        await settle()
        XCTAssertEqual(card(m, .likes, leonie)?.closed, .noOverlap)
        XCTAssertEqual(m.state.likes.positionOf(leonie), 2, "it moves to the end only on the next load")
    }

    func testTheListsReCheckClosesOpenCardsAndReadsTheBadgeAgain() async {
        let m = await onLikes()
        await store.setStatuses([leonie: .closed(.matched)])
        await store.setOpenCount(1)
        m.openCard(.likes, userId: anna)
        m.backFromProfile()
        await settle()
        let asks = await store.availabilityAsks
        XCTAssertEqual(asks, [[anna, leonie]])
        XCTAssertEqual(card(m, .likes, leonie)?.closed, .matched)
        XCTAssertEqual(m.state.likesBadge, 1)
    }

    func testComingBackToTheTabKeepsTheScrollPositionUnlessTheListReadBackDifferent() async {
        let m = await onLikes()
        let first = m.state.likes.generation
        m.likesArrived(zone: "Europe/Berlin", referrer: .searchGrid)
        await settle()
        XCTAssertEqual(m.state.likes.generation, first)
        await store.setLikesList(Array(SearchSamples.likes.prefix(2)))
        m.likesArrived(zone: "Europe/Berlin", referrer: .searchGrid)
        await settle()
        XCTAssertEqual(m.state.likes.generation, first + 1)
    }

    // MARK: - the process ending

    func testABeSureLeftOpenOnAGridProfileComesBackOverThatProfileAfterTheProcessEnds() async {
        let before = await onGrid()
        open(before, .grid, leonie)
        before.like()
        XCTAssertFalse(stored.isEmpty, "the open flow is kept")
        let after = await onGrid()
        XCTAssertEqual(after.state.opened, Opened(from: .grid, userId: leonie))
        XCTAssertEqual(after.state.sheet, .confirm)
        XCTAssertTrue(after.state.likedYou)
    }

    func testAKeptLikeFlowIsDroppedWhenItsPersonIsNoLongerOnTheList() async {
        let before = await onGrid()
        open(before, .grid, sofia)
        before.like()
        await store.setGrid(SearchSamples.grid.filter { $0.profile.userId != sofia })
        let after = await onGrid()
        XCTAssertNil(after.state.opened)
        XCTAssertNil(after.state.sheet)
        XCTAssertEqual(stored, "", "and forgotten")
    }

    func testTheSorrySheetIsNeverKeptTheNextReadShowsTheCardClosed() async {
        await store.setAnswer(.refused(.matched))
        let before = await onGrid()
        open(before, .grid, leonie)
        before.like()
        before.confirmDate()
        await settle()
        XCTAssertEqual(before.state.sheet, .refused)
        XCTAssertEqual(stored, "")
        let after = await onGrid()
        XCTAssertNil(after.state.opened)
    }

    // MARK: - the host's question

    /// Leaving a profile for somewhere else -- a tab, Filters -- is a real arrival there, and the list
    /// it came from is not waiting to reappear.
    func testAProfileLeftForElsewhereLeavesNoReturnBehind() async {
        let m = await onGrid()
        open(m, .grid, sofia)
        m.closeProfileForElsewhere()
        XCTAssertFalse(m.takeReturn(.grid))
        XCTAssertNil(m.state.opened)
        open(m, .grid, maya)
        m.backFromProfile()
        m.clearReturn()
        XCTAssertFalse(m.takeReturn(.grid), "a move inside the flow forgets it")
    }
}
