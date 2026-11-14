// ArrivalGateModelTests.swift
// ShowUp · the arrival gate: confirm, store, close, undo, retries (SHOWUP-195). Android's
// `ArrivalGateViewModelTest.kt`, case for case.
//
// Fake store, fake reader, injected clock and an injected sleep that records the schedule. Waits are real and short
// (tens of ms), as `ChatModelTests`'. No test reads a coordinate out of an event: the payloads are asserted whole.

import XCTest
@testable import ShowUpWelcome

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

    /// `pauseMs` is how long one retry wait really takes: a few ms to run the schedule, longer to hold a retry.
    private func makeModel(pauseMs: Int = 3, timeoutMs: Int = 80) -> ArrivalGateModel {
        let sleeps = self.sleeps
        return ArrivalGateModel(
            store: store, reader: reader, analytics: analytics, clock: { [unowned self] in self.now },
            newId: { [unowned self] in
                self.ids += 1
                return "id-\(self.ids)"
            },
            sleep: { ms in
                await sleeps.add(ms)
                try? await Task.sleep(nanoseconds: UInt64(pauseMs) * 1_000_000)
            },
            fixTimeoutMs: timeoutMs, background: runner)
    }

    private func settle(_ seconds: Double = 0.08) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }

    private func ready(checkedIn: Bool = false, pauseMs: Int = 3, timeoutMs: Int = 80) -> ArrivalGateModel {
        let m = makeModel(pauseMs: pauseMs, timeoutMs: timeoutMs)
        m.bind(date(checkedIn: checkedIn))
        return m
    }

    private func confirmed(_ entry: ArrivalEntry = .dateScreen, pauseMs: Int = 3, timeoutMs: Int = 80) -> ArrivalGateModel {
        let m = ready(pauseMs: pauseMs, timeoutMs: timeoutMs)
        m.open(entry)
        return m
    }

    private func done(pauseMs: Int = 3) async -> ArrivalGateModel {
        let m = confirmed(pauseMs: pauseMs)
        m.yes()
        await settle()
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
        await settle()
        let reads = await reader.reads
        let stored = await store.stored
        XCTAssertEqual(reads, 1)
        XCTAssertEqual(stored, [FakeArrivalStore.Stored(dateId: "d1", fix: arrivalTestFix, entry: .dateScreen, clientId: "id-1")])
        XCTAssertEqual(m.ui.phase, .done(entry: .dateScreen))
        XCTAssertFalse(m.ui.committed)
    }

    func testYesWhileBusyDoesNothing() async {
        await reader.setAnswer {
            try? await Task.sleep(nanoseconds: 150_000_000)
            return arrivalTestFix
        }
        let m = confirmed(timeoutMs: 1_000)
        m.yes()
        m.yes()
        m.notYet()
        await settle(0.03)
        XCTAssertEqual(m.ui.phase, confirm(.dateScreen, busy: true))
        await settle(0.25)
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
        await settle()
        XCTAssertEqual(m.ui.phase, confirm(.dateScreen, locError: true))
        var stored = await store.stored
        XCTAssertEqual(stored.count, 0)
        await reader.setAnswer { arrivalTestFix }
        m.yes()
        await settle()
        XCTAssertEqual(analytics.of("arrival_confirm_submitted").count, 2)
        XCTAssertEqual(analytics.of("arrival_confirm_shown").count, 1)
        stored = await store.stored
        XCTAssertEqual(stored.count, 1)
        XCTAssertEqual(m.ui.phase, .done(entry: .dateScreen))
    }

    func testAFixAfterTheTimeoutIsIgnored() async {
        await reader.setAnswer {
            try? await Task.sleep(nanoseconds: 400_000_000)
            return arrivalTestFix
        }
        let m = confirmed(timeoutMs: 40)
        m.yes()
        await settle(0.2)
        XCTAssertEqual(m.ui.phase, confirm(.dateScreen, locError: true))
        let stored = await store.stored
        XCTAssertEqual(stored.count, 0)
    }

    func testAFailedStoreIsCAndNothingIsCommitted() async {
        await store.queueCheckIns([.failed(.network)])
        let m = confirmed()
        m.yes()
        await settle()
        XCTAssertEqual(m.ui.phase, confirm(.dateScreen, locError: true))
        let commits = await store.commitCalls
        XCTAssertEqual(commits, 0)
    }

    func testAlreadyCheckedInClosesTheSheetAndCountsAsCheckedIn() async {
        await store.queueCheckIns([.alreadyCheckedIn])
        let m = confirmed()
        m.yes()
        await settle()
        XCTAssertEqual(m.ui.phase, .idle)
        XCTAssertTrue(m.ui.committed)
        XCTAssertTrue(m.checkedIn())
    }

    func testEveryTapGetsItsOwnClientId() async {
        await store.queueCheckIns([.failed(.server)])
        let m = confirmed()
        m.yes()
        await settle()
        m.yes()
        await settle()
        let stored = await store.stored
        XCTAssertEqual(stored.map(\.clientId), ["id-1", "id-2"])
    }

    // MARK: close

    func testCloseCommitsOnceAndTheDateCountsAsCheckedIn() async {
        let m = await done()
        m.close()
        await settle()
        XCTAssertEqual(m.ui.phase, .idle)
        XCTAssertTrue(m.ui.committed)
        let commits = await store.commitCalls
        XCTAssertEqual(commits, 1)
        XCTAssertTrue(m.checkedIn())
    }

    func testAPollBeforeTheCommitLandsKeepsTheCheckIn() async {
        await store.queueCommits([.failure(.network)])
        let m = await done(pauseMs: 400)
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
        await settle(0.2)
        let commits = await store.commitCalls
        let delays = await sleeps.delays
        XCTAssertEqual(commits, 3, "landed on the third: no more")
        XCTAssertEqual(delays, [2_000, 4_000])
    }

    func testCloseDoesNothingOutsideD() async {
        let m = confirmed()
        m.close()
        await settle()
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
        await settle()
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
        await settle(0.2)
        var undos = await store.undoCalls
        XCTAssertEqual(undos, 3)
        let delays = await sleeps.delays
        XCTAssertEqual(delays, [2_000, 4_000])
        m.leftApp()
        m.close()
        await settle()
        let commits = await store.commitCalls
        undos = await store.undoCalls
        XCTAssertEqual(commits, 0)
        XCTAssertEqual(undos, 3)
        XCTAssertFalse(m.checkedIn())
    }

    func testAYesAfterAnUndoStoresAgainAndCancelsADeleteStillRetrying() async {
        await store.queueUndos([.failure(.network)])
        let m = await done(pauseMs: 500)
        m.undo()
        await settle(0.05)
        var undos = await store.undoCalls
        XCTAssertEqual(undos, 1)
        XCTAssertTrue(m.open(.dateScreen))
        m.yes()
        await settle()
        let stored = await store.stored
        XCTAssertEqual(stored.count, 2)
        XCTAssertEqual(m.ui.phase, .done(entry: .dateScreen))
        await settle(0.6)
        undos = await store.undoCalls
        XCTAssertEqual(undos, 1, "the retrying delete was cancelled")
    }

    // MARK: leaving the app

    func testLeavingTheAppOnDCommitsOnce() async {
        let m = await done()
        m.leftApp()
        m.leftApp()
        await settle()
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
        await settle()
        XCTAssertEqual(m.ui.phase, confirm(.dateScreen))
        let commits = await store.commitCalls
        XCTAssertEqual(commits, 0)
    }

    func testLeavingDuringTheReadAndAStoreThatLandsAwayCommitsAtOnceWithNoD() async {
        await reader.setAnswer {
            try? await Task.sleep(nanoseconds: 100_000_000)
            return arrivalTestFix
        }
        let m = confirmed(timeoutMs: 1_000)
        m.yes()
        await settle(0.02)
        m.leftApp()
        await settle(0.25)
        let stored = await store.stored
        let commits = await store.commitCalls
        XCTAssertEqual(stored.count, 1)
        XCTAssertEqual(commits, 1)
        XCTAssertEqual(m.ui.phase, .idle)
        XCTAssertTrue(m.ui.committed)
    }

    func testLeavingDuringTheReadAndComingBackBeforeTheStoreLandsShowsDAndCommitsNothing() async {
        await reader.setAnswer {
            try? await Task.sleep(nanoseconds: 100_000_000)
            return arrivalTestFix
        }
        let m = confirmed(timeoutMs: 1_000)
        m.yes()
        await settle(0.02)
        m.leftApp()
        m.foregrounded()
        await settle(0.25)
        let commits = await store.commitCalls
        XCTAssertEqual(commits, 0)
        XCTAssertEqual(m.ui.phase, .done(entry: .dateScreen))
        XCTAssertFalse(m.ui.committed)
    }

    func testEachCommitAndUndoRequestRunsUnderOneBeginEndPair() async {
        await store.queueCommits([.failure(.network), .success(())])
        let m = await done()
        m.close()
        await settle(0.2)
        XCTAssertEqual(runner.events, ["begin:arrival-commit", "end:arrival-commit",
                                       "begin:arrival-commit", "end:arrival-commit"])
        let m2 = ArrivalGateModel(store: store, reader: reader, background: runner)
        m2.bind(date(id: "d2"))
        m2.open(.dateScreen)
        m2.yes()
        await settle(0.2)
        m2.undo()
        await settle(0.1)
        XCTAssertEqual(runner.events.suffix(2), ["begin:arrival-undo", "end:arrival-undo"])
    }

    // MARK: binding

    func testBindingAnotherDateResetsThePhaseAndCommitted() async {
        let m = await done()
        m.close()
        await settle()
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
        await reader.setAnswer {
            try? await Task.sleep(nanoseconds: 200_000_000)
            return arrivalTestFix
        }
        let m = confirmed(timeoutMs: 1_000)
        m.yes()
        await settle(0.03)
        m.bind(date(id: "d2"))
        await settle(0.4)
        let stored = await store.stored
        XCTAssertEqual(stored.count, 0)
        XCTAssertEqual(m.ui.phase, .idle)
    }

    // MARK: the retries outlive the model

    func testARetryingCommitContinuesAfterTheModelIsReleased() async {
        await store.queueCommits([.failure(.network), .failure(.network), .failure(.network)])
        var m: ArrivalGateModel? = await done()
        m?.close()
        await settle(0.01)
        m = nil
        await settle(0.3)
        let commits = await store.commitCalls
        XCTAssertEqual(commits, 4, "landed on the fourth")
    }

    func testBindingAnotherDateStillCancelsARetryingCommit() async {
        await store.queueCommits(Array(repeating: .failure(.network), count: 5))
        let m = await done(pauseMs: 300)
        m.close()
        await settle(0.05)
        var commits = await store.commitCalls
        XCTAssertEqual(commits, 1)
        m.bind(date(id: "d2"))
        await settle(0.5)
        commits = await store.commitCalls
        XCTAssertEqual(commits, 1)
    }
}
