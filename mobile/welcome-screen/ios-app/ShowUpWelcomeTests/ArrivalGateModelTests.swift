// ArrivalGateModelTests.swift
// ShowUp · the arrival gate: confirm, store, close, undo, retries (SHOWUP-195). Android's
// `ArrivalGateViewModelTest.kt`, case for case.
//
// Fake store, fake reader, injected clock and an injected sleep that records the schedule and returns at once (or, for
// a test that needs a retry still waiting, holds until the model cancels it). No test sleeps for a fixed time: each
// waits for the state its assertion needs (`eventually`), and a read that must stay in flight is held on a gate the test
// opens. No test reads a coordinate out of an event: the payloads are asserted whole.

import XCTest
@testable import ShowUpWelcome

/// Polls every 10 ms until `condition` holds; fails the test with `what` after `timeout`. A loaded runner makes the
/// wait longer, never the test wrong: nothing here is a guess at how long async work takes.
@MainActor
func eventually(_ what: String, timeout: TimeInterval = 5, file: StaticString = #filePath, line: UInt = #line,
                _ condition: () async -> Bool) async {
    let deadline = Date().addingTimeInterval(timeout)
    while Date() < deadline {
        if await condition() { return }
        try? await Task.sleep(nanoseconds: 10_000_000)
    }
    if await condition() { return }
    XCTFail("timed out after \(timeout) s waiting for: \(what)", file: file, line: line)
}

/// Lets already-runnable work (a cancelled task unwinding) run before a test asserts that something did NOT happen.
@MainActor
func letCancelledWorkUnwind() async {
    for _ in 0..<50 { await Task.yield() }
}

/// A read held in flight until the test opens it. Not cancellable on purpose: the test always opens it.
actor ReadGate {
    private var isOpen = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    func wait() async {
        if isOpen { return }
        await withCheckedContinuation { waiters.append($0) }
    }

    func open() {
        isOpen = true
        let held = waiters
        waiters = []
        for waiter in held { waiter.resume() }
    }
}

/// The fix every test reads, at file scope so the fake reader's nonisolated closures can reach it.
let arrivalTestFix = LocationFix(latitude: 52.5186, longitude: 13.3761, accuracyM: 8)

actor FakeArrivalStore: ArrivalStoring {
    struct Stored: Equatable { let dateId: String; let fix: LocationFix; let entry: ArrivalEntry; let clientId: String }

    private(set) var stored: [Stored] = []
    private var checkIns: [CheckInResult] = []
    private var undos: [Result<Void, CallFailure>] = []
    private var commits: [Result<Void, CallFailure>] = []
    private(set) var undoCalls = 0
    private(set) var commitCalls = 0

    func queueCheckIns(_ values: [CheckInResult]) { checkIns += values }
    func queueUndos(_ values: [Result<Void, CallFailure>]) { undos += values }
    func queueCommits(_ values: [Result<Void, CallFailure>]) { commits += values }

    func checkIn(dateId: String, fix: LocationFix, entry: ArrivalEntry, clientId: String) async -> CheckInResult {
        stored.append(Stored(dateId: dateId, fix: fix, entry: entry, clientId: clientId))
        return checkIns.isEmpty ? .stored : checkIns.removeFirst()
    }

    func undo(dateId: String) async -> Result<Void, CallFailure> {
        undoCalls += 1
        return undos.isEmpty ? .success(()) : undos.removeFirst()
    }

    func commit(dateId: String) async -> Result<Void, CallFailure> {
        commitCalls += 1
        return commits.isEmpty ? .success(()) : commits.removeFirst()
    }
}

actor FakeFixReader: LocationFixReading {
    private(set) var reads = 0
    private var answer: @Sendable () async -> LocationFix? = { arrivalTestFix }

    func setAnswer(_ value: @escaping @Sendable () async -> LocationFix?) { answer = value }

    func fix() async -> LocationFix? {
        reads += 1
        return await answer()
    }
}

/// The delays the model asked to wait, in ms.
actor RecordedSleeps {
    private(set) var delays: [Int] = []
    func add(_ ms: Int) { delays.append(ms) }
}

/// Records each begin / end pair the model asks for, in order, and runs the work between them.
@MainActor
final class FakeBackgroundRunner: BackgroundTaskRunning {
    private(set) var events: [String] = []

    func run<T: Sendable>(named name: String, _ work: @escaping @Sendable () async -> T) async -> T {
        events.append("begin:\(name)")
        let result = await work()
        events.append("end:\(name)")
        return result
    }
}

@MainActor
final class ArrivalGateModelTests: XCTestCase {
    private let meet = Date(timeIntervalSince1970: 1_793_728_800) // 2026-11-03T18:00:00Z
    private var now: Date { meet.addingTimeInterval(-5 * 60) } // 5 minutes before
    private let analytics = RecordingAnalytics()
    private let store = FakeArrivalStore()
    private let reader = FakeFixReader()
    private let sleeps = RecordedSleeps()
    private let gate = ReadGate()
    private let runner = FakeBackgroundRunner()
    private var ids = 0

    private func date(id: String = "d1", checkedIn: Bool = false) -> ActiveDate {
        ActiveDate(id: id, meetAt: meet, meetDay: "today", unlockAt: meet.addingTimeInterval(-90 * 60), coordinateOpen: true,
                   place: DatePlace(name: "Cafe Krone", address: nil),
                   match: DateMatch(userId: "u2", name: "Leonie", age: 28, district: nil, distanceKm: 1, photoUrl: nil,
                                    attendedCount: 4),
                   motivationYou: nil, motivationThem: nil, sharedVibes: [], sharedInterests: [], unread: nil,
                   checkedIn: checkedIn, chatTutorialSeen: true, arrival: nil)
    }

    /// A retry wait returns at once, after recording the delay asked for. `holdRetries` makes it wait until the model
    /// cancels it (a bind or a new Yes), so a test can look at the state between two tries.
    private func makeModel(holdRetries: Bool = false, timeoutMs: Int = 80) -> ArrivalGateModel {
        let sleeps = self.sleeps
        return ArrivalGateModel(
            store: store, reader: reader, analytics: analytics, clock: { [unowned self] in self.now },
            newId: { [unowned self] in
                self.ids += 1
                return "id-\(self.ids)"
            },
            sleep: { ms in
                await sleeps.add(ms)
                if holdRetries { try? await Task.sleep(nanoseconds: 60_000_000_000) }
            },
            fixTimeoutMs: timeoutMs, background: runner)
    }

    /// The reader answers once the test opens the gate.
    private func holdTheRead() async {
        let gate = self.gate
        await reader.setAnswer {
            await gate.wait()
            return arrivalTestFix
        }
    }

    private func ready(checkedIn: Bool = false, holdRetries: Bool = false, timeoutMs: Int = 80) -> ArrivalGateModel {
        let m = makeModel(holdRetries: holdRetries, timeoutMs: timeoutMs)
        m.bind(date(checkedIn: checkedIn))
        return m
    }

    private func confirmed(_ entry: ArrivalEntry = .dateScreen, holdRetries: Bool = false, timeoutMs: Int = 80) -> ArrivalGateModel {
        let m = ready(holdRetries: holdRetries, timeoutMs: timeoutMs)
        m.open(entry)
        return m
    }

    private func done(holdRetries: Bool = false) async -> ArrivalGateModel {
        let m = confirmed(holdRetries: holdRetries)
        m.yes()
        await eventually("D is up") { m.ui.phase == .done(entry: .dateScreen) }
        XCTAssertEqual(m.ui.phase, .done(entry: .dateScreen))
        return m
    }

    private func confirm(_ entry: ArrivalEntry, locError: Bool = false, busy: Bool = false, note: String? = nil) -> GatePhase {
        .confirm(entry: entry, locError: locError, busy: busy, note: note)
    }

    // MARK: opening

    func testOpenOnACheckedInDateReturnsFalseAndFiresNothing() {
        let m = ready(checkedIn: true)
        XCTAssertTrue(m.checkedIn())
        XCTAssertFalse(m.open(.dateScreen))
        XCTAssertEqual(m.ui.phase, .idle)
        XCTAssertEqual(analytics.names(), [])
    }

    func testOpenShowsBAndFiresArrivalConfirmShownWithTheRegistryPayloadAndNoCoordinate() {
        let m = ready()
        XCTAssertTrue(m.open(.chatChip, note: "Check in first"))
        XCTAssertEqual(m.ui.phase, confirm(.chatChip, note: "Check in first"))
        let shown = analytics.of("arrival_confirm_shown")[0].properties
        XCTAssertEqual(shown["date_id"] as? String, "d1")
        XCTAssertEqual(shown["entry"] as? String, "chat_chip")
        XCTAssertEqual(shown["minutes_vs_start"] as? Int, -5)
        XCTAssertFalse(shown.keys.contains { $0.contains("lat") || $0.contains("lon") || $0.contains("accuracy") || $0.contains("location") })
    }

    func testASecondOpenWhileASheetIsUpDoesNothingAndReturnsTrue() {
        let m = confirmed()
        XCTAssertTrue(m.open(.chatChip))
        XCTAssertEqual(analytics.of("arrival_confirm_shown").count, 1)
        XCTAssertEqual(m.ui.phase, confirm(.dateScreen))
    }

    func testNotYetClosesBWithNoEventAndNoStoreCall() async {
        let m = confirmed()
        let before = analytics.entries.count
        m.notYet()
        XCTAssertEqual(m.ui.phase, .idle)
        XCTAssertEqual(analytics.entries.count, before)
        let stored = await store.stored
        XCTAssertEqual(stored.count, 0)
    }

    // MARK: yes

    func testYesFiresSubmittedGoesBusyReadsOnceStoresAndShowsD() async {
        let m = confirmed()
        m.yes()
        XCTAssertEqual(m.ui.phase, confirm(.dateScreen, busy: true))
        let submitted = analytics.of("arrival_confirm_submitted")[0].properties
        XCTAssertEqual(submitted["date_id"] as? String, "d1")
        XCTAssertEqual(submitted["entry"] as? String, "date_screen")
        XCTAssertEqual(submitted["minutes_vs_start"] as? Int, -5)
        await eventually("D is up") { m.ui.phase == .done(entry: .dateScreen) }
        let reads = await reader.reads
        let stored = await store.stored
        XCTAssertEqual(reads, 1)
        XCTAssertEqual(stored, [FakeArrivalStore.Stored(dateId: "d1", fix: arrivalTestFix, entry: .dateScreen, clientId: "id-1")])
        XCTAssertEqual(m.ui.phase, .done(entry: .dateScreen))
        XCTAssertFalse(m.ui.committed)
    }

    func testYesWhileBusyDoesNothing() async {
        await holdTheRead()
        let m = confirmed(timeoutMs: 60_000)
        m.yes()
        m.yes()
        m.notYet()
        XCTAssertEqual(m.ui.phase, confirm(.dateScreen, busy: true))
        await gate.open()
        await eventually("D is up") { m.ui.phase == .done(entry: .dateScreen) }
        let reads = await reader.reads
        let stored = await store.stored
        XCTAssertEqual(reads, 1)
        XCTAssertEqual(stored.count, 1)
        XCTAssertEqual(analytics.of("arrival_confirm_submitted").count, 1)
        XCTAssertEqual(m.ui.phase, .done(entry: .dateScreen))
    }

    func testNoFixIsCNothingStoredAndTryAgainFiresSubmittedAgainAndNoNewShown() async {
        await reader.setAnswer { nil }
        let m = confirmed()
        m.yes()
        await eventually("C is up") { m.ui.phase == confirm(.dateScreen, locError: true) }
        var stored = await store.stored
        XCTAssertEqual(stored.count, 0)
        await reader.setAnswer { arrivalTestFix }
        m.yes()
        await eventually("D is up") { m.ui.phase == .done(entry: .dateScreen) }
        XCTAssertEqual(analytics.of("arrival_confirm_submitted").count, 2)
        XCTAssertEqual(analytics.of("arrival_confirm_shown").count, 1)
        stored = await store.stored
        XCTAssertEqual(stored.count, 1)
        XCTAssertEqual(m.ui.phase, .done(entry: .dateScreen))
    }

    func testAFixAfterTheTimeoutIsIgnored() async {
        // The reader outlasts any timeout; the model cancels it, and the fix it then returns is ignored.
        await reader.setAnswer {
            try? await Task.sleep(nanoseconds: 60_000_000_000)
            return arrivalTestFix
        }
        let m = confirmed(timeoutMs: 40)
        m.yes()
        await eventually("C is up") { m.ui.phase == confirm(.dateScreen, locError: true) }
        XCTAssertEqual(m.ui.phase, confirm(.dateScreen, locError: true))
        let stored = await store.stored
        XCTAssertEqual(stored.count, 0)
    }

    func testAFailedStoreIsCAndNothingIsCommitted() async {
        await store.queueCheckIns([.failed(.network)])
        let m = confirmed()
        m.yes()
        await eventually("C is up") { m.ui.phase == confirm(.dateScreen, locError: true) }
        let commits = await store.commitCalls
        XCTAssertEqual(commits, 0)
    }

    func testAlreadyCheckedInClosesTheSheetAndCountsAsCheckedIn() async {
        await store.queueCheckIns([.alreadyCheckedIn])
        let m = confirmed()
        m.yes()
        await eventually("the date counts as checked in") { m.ui.committed }
        XCTAssertEqual(m.ui.phase, .idle)
        XCTAssertTrue(m.checkedIn())
    }

    func testEveryTapGetsItsOwnClientId() async {
        await store.queueCheckIns([.failed(.server)])
        let m = confirmed()
        m.yes()
        await eventually("C is up") { m.ui.phase == confirm(.dateScreen, locError: true) }
        m.yes()
        await eventually("both taps stored") { await store.stored.count == 2 }
        let stored = await store.stored
        XCTAssertEqual(stored.map(\.clientId), ["id-1", "id-2"])
    }

    // MARK: close

    func testCloseCommitsOnceAndTheDateCountsAsCheckedIn() async {
        let m = await done()
        m.close()
        await eventually("the commit landed") { await store.commitCalls == 1 }
        XCTAssertEqual(m.ui.phase, .idle)
        XCTAssertTrue(m.ui.committed)
        let commits = await store.commitCalls
        XCTAssertEqual(commits, 1)
        XCTAssertTrue(m.checkedIn())
    }

    func testAPollBeforeTheCommitLandsKeepsTheCheckIn() async {
        await store.queueCommits([.failure(.network)])
        let m = await done(holdRetries: true)
        m.close()
        m.bind(date(checkedIn: false))
        XCTAssertTrue(m.checkedIn())
        XCTAssertEqual(m.ui.phase, .idle)
        XCTAssertFalse(m.open(.dateScreen))
    }

    func testAFailedCommitIsRetriedOnTheScheduleUntilItLands() async {
        await store.queueCommits([.failure(.network), .failure(.server)])
        let m = await done()
        m.close()
        await eventually("the third commit attempt") { await store.commitCalls >= 3 }
        let commits = await store.commitCalls
        let delays = await sleeps.delays
        XCTAssertEqual(commits, 3, "landed on the third: no more")
        XCTAssertEqual(delays, [2_000, 4_000])
    }

    func testCloseDoesNothingOutsideD() async {
        let m = confirmed()
        m.close()
        XCTAssertEqual(m.ui.phase, confirm(.dateScreen))
        let commits = await store.commitCalls
        XCTAssertEqual(commits, 0)
    }

    // MARK: undo

    func testUndoClosesDAtOnceDeletesOnceAndTheDateIsNotCheckedIn() async {
        let m = await done()
        m.undo()
        XCTAssertEqual(m.ui.phase, .idle)
        XCTAssertTrue(m.ui.undone)
        await eventually("the delete landed") { await store.undoCalls == 1 }
        let undos = await store.undoCalls
        let commits = await store.commitCalls
        XCTAssertEqual(undos, 1)
        XCTAssertEqual(commits, 0)
        XCTAssertFalse(m.checkedIn())
        m.consumeUndone()
        XCTAssertFalse(m.ui.undone)
    }

    func testAnUndoThatFailsIsRetriedAndNeverCommitted() async {
        await store.queueUndos([.failure(.network), .failure(.network)])
        let m = await done()
        m.undo()
        await eventually("the third delete attempt") { await store.undoCalls >= 3 }
        var undos = await store.undoCalls
        XCTAssertEqual(undos, 3)
        let delays = await sleeps.delays
        XCTAssertEqual(delays, [2_000, 4_000])
        m.leftApp()
        m.close()
        await letCancelledWorkUnwind()
        let commits = await store.commitCalls
        undos = await store.undoCalls
        XCTAssertEqual(commits, 0)
        XCTAssertEqual(undos, 3)
        XCTAssertFalse(m.checkedIn())
    }

    func testAYesAfterAnUndoStoresAgainAndCancelsADeleteStillRetrying() async {
        await store.queueUndos([.failure(.network)])
        let m = await done(holdRetries: true)
        m.undo()
        await eventually("the first delete attempt") { await store.undoCalls == 1 }
        var undos = await store.undoCalls
        XCTAssertEqual(undos, 1)
        XCTAssertTrue(m.open(.dateScreen))
        m.yes()
        await eventually("D is up again") { m.ui.phase == .done(entry: .dateScreen) }
        let stored = await store.stored
        XCTAssertEqual(stored.count, 2)
        // The held retry ends only by being cancelled, so a delete after this point could only be a missed cancel.
        await letCancelledWorkUnwind()
        undos = await store.undoCalls
        XCTAssertEqual(undos, 1, "the retrying delete was cancelled")
    }

    // MARK: leaving the app

    func testLeavingTheAppOnDCommitsOnce() async {
        let m = await done()
        m.leftApp()
        m.leftApp()
        await eventually("the commit landed") { await store.commitCalls == 1 }
        let commits = await store.commitCalls
        XCTAssertEqual(commits, 1)
        XCTAssertEqual(m.ui.phase, .idle)
        XCTAssertTrue(m.ui.committed)
    }

    func testLeavingTheAppOnIdleOrBDoesNothing() async {
        let m = ready()
        m.leftApp()
        m.open(.dateScreen)
        m.leftApp()
        XCTAssertEqual(m.ui.phase, confirm(.dateScreen))
        let commits = await store.commitCalls
        XCTAssertEqual(commits, 0)
    }

    func testLeavingDuringTheReadAndAStoreThatLandsAwayCommitsAtOnceWithNoD() async {
        await holdTheRead()
        let m = confirmed(timeoutMs: 60_000)
        m.yes()
        m.leftApp()
        await gate.open()
        await eventually("the commit landed") { await store.commitCalls == 1 }
        let stored = await store.stored
        let commits = await store.commitCalls
        XCTAssertEqual(stored.count, 1)
        XCTAssertEqual(commits, 1)
        XCTAssertEqual(m.ui.phase, .idle)
        XCTAssertTrue(m.ui.committed)
    }

    func testLeavingDuringTheReadAndComingBackBeforeTheStoreLandsShowsDAndCommitsNothing() async {
        await holdTheRead()
        let m = confirmed(timeoutMs: 60_000)
        m.yes()
        m.leftApp()
        m.foregrounded()
        await gate.open()
        await eventually("D is up") { m.ui.phase == .done(entry: .dateScreen) }
        let commits = await store.commitCalls
        XCTAssertEqual(commits, 0)
        XCTAssertEqual(m.ui.phase, .done(entry: .dateScreen))
        XCTAssertFalse(m.ui.committed)
    }

    func testEachCommitAndUndoRequestRunsUnderOneBeginEndPair() async {
        await store.queueCommits([.failure(.network), .success(())])
        let m = await done()
        m.close()
        await eventually("both commit attempts ran") { runner.events.count == 4 }
        XCTAssertEqual(runner.events, ["begin:arrival-commit", "end:arrival-commit",
                                       "begin:arrival-commit", "end:arrival-commit"])
        let m2 = ArrivalGateModel(store: store, reader: reader, background: runner)
        m2.bind(date(id: "d2"))
        m2.open(.dateScreen)
        m2.yes()
        await eventually("D is up on the second date") { m2.ui.phase == .done(entry: .dateScreen) }
        m2.undo()
        await eventually("the undo ran") { runner.events.count == 6 }
        XCTAssertEqual(runner.events.suffix(2), ["begin:arrival-undo", "end:arrival-undo"])
    }

    // MARK: binding

    func testBindingAnotherDateResetsThePhaseAndCommitted() async {
        let m = await done()
        m.close()
        await eventually("the commit landed") { await store.commitCalls == 1 }
        XCTAssertTrue(m.ui.committed)
        m.bind(date(id: "d2"))
        XCTAssertEqual(m.ui, GateUi())
        XCTAssertFalse(m.checkedIn())
        XCTAssertTrue(m.open(.dateScreen))
    }

    func testBindingTheSameDateAgainKeepsTheSheet() {
        let m = confirmed()
        m.bind(date())
        XCTAssertEqual(m.ui.phase, confirm(.dateScreen))
    }

    func testBindingAnotherDateCancelsAReadInFlight() async {
        await holdTheRead()
        let m = confirmed(timeoutMs: 60_000)
        m.yes()
        await eventually("the read is in flight") { await reader.reads == 1 }
        m.bind(date(id: "d2"))
        await gate.open()
        await letCancelledWorkUnwind()
        let stored = await store.stored
        XCTAssertEqual(stored.count, 0)
        XCTAssertEqual(m.ui.phase, .idle)
    }

    // MARK: the retries outlive the model

    func testARetryingCommitContinuesAfterTheModelIsReleased() async {
        await store.queueCommits([.failure(.network), .failure(.network), .failure(.network)])
        var m: ArrivalGateModel? = await done()
        m?.close()
        m = nil
        await eventually("the fourth commit attempt") { await store.commitCalls >= 4 }
        let commits = await store.commitCalls
        XCTAssertEqual(commits, 4, "landed on the fourth")
    }

    func testBindingAnotherDateStillCancelsARetryingCommit() async {
        await store.queueCommits(Array(repeating: .failure(.network), count: 5))
        let m = await done(holdRetries: true)
        m.close()
        await eventually("the first commit attempt") { await store.commitCalls == 1 }
        var commits = await store.commitCalls
        XCTAssertEqual(commits, 1)
        m.bind(date(id: "d2"))
        // The held retry ends only by being cancelled, so a second attempt could only be a missed cancel.
        await letCancelledWorkUnwind()
        commits = await store.commitCalls
        XCTAssertEqual(commits, 1)
    }
}
