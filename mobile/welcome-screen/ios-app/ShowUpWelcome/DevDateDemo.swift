//  DevDateDemo.swift
//  ShowUp · an in-memory date server, so SHOWUP-196's frames open live on a phone with no backend (debug only)
//
//  The Swift twin of `dev/DevDateDemo.kt`. The States page draws every frame as a still picture: right pixels, nothing
//  wired. Date 05's frames are a flow -- pick a time, suggest it, agree, check in -- and a picture of a flow reads as a
//  broken app. This stands in for the date, chat, change and arrival calls so each frame opens in its state with every
//  tap working: the real host routing, the real models, only the server swapped, as `DevCheckInStore` does.
//
//  The values are the kit's (ChangeSamples): Leonie, Cafe Krone, the slots, the five places. The TIMES are the kit's
//  moved to today: the sheet's clock (17:42, or 15:55 while the chat is locked) is now, so 19:00 is 1h18 away and every
//  rule that counts from the clock -- the unlock, the 30-minute cutoff, the expiry -- runs for real.
//
//  DEBUG BUILDS ONLY. The whole file is inside `#if DEBUG`. The rules are the server's (`change-suggestions.service.ts`),
//  mirrored loosely.

#if DEBUG
import Foundation

/// Where a frame opens once its date is read: the screen the sheet draws it on.
enum DemoLanding: Equatable { case dateScreen, chat, timePicker, placePicker }

func demoLanding(_ frame: String) -> DemoLanding {
    switch frame {
    case "A", "K": return .timePicker
    case "B", "M", "N": return .placePicker
    case "C", "F", "H": return .chat
    default: return .dateScreen
    }
}

/// The search a frame's picker opens on: N is drawn as a search for "sushi" that finds nothing. Android's `demoQuery`.
func demoQuery(_ frame: String) -> String? { frame == "N" ? "sushi" : nil }

/// The frame letter a States row opens LIVE, or nil for a picture. Date 05's frames are a flow, and a still picture of
/// one reads as a broken app, so they open on `DevDateDemo` with every tap working. The other tickets' frames stay
/// pictures. Android's `devLiveFrame`.
func devLiveFrame(_ state: DevState) -> String? {
    guard state.id.hasPrefix("196") else { return nil }
    let letter = String(state.id.dropFirst(3))
    return ChangeSamples.letters.contains(letter) ? letter : nil
}

actor DevDateDemo: DateStoring, ChatStoring, ChangeStoring, ArrivalStoring, LocationFixReading {
    static let demoDateId = "demo-date"

    let frame: String
    private let clock: @Sendable () -> Date
    /// Milliseconds a write takes: long enough to see the sending state, short enough to walk.
    private let latencyMs: Int

    /// The kit's binding meet time, 19:00 Berlin on the sample day.
    private static let kitMeet = DateSamples.meet
    private static let minute: TimeInterval = 60

    private let start: Date
    private let shift: TimeInterval

    private var meetAt: Date
    private var place: DatePlace
    private var timeWas: Date?
    private var placeWas: String?
    /// Leonie's availability ends at the kit's 22:00, so the last slot is 21:30.
    private let availableUntil: Date

    private var suggestions: [ChangeSuggestion] = []
    private var clientIds: [String: String] = [:]
    private var messages: [ChatMessage] = []
    private var readAt: Date
    /// The server's `coordinate_unlocked_at`: once the chat has opened it stays open, even when a later time is agreed.
    /// Every frame but I and J is drawn with the chat open, so it has opened already (G's at the original 19:00).
    private var unlockStamped: Bool
    private var tutorialSeen = true
    private var myCheckIn = MyCheckIn.none
    private var partnerArrivedAt: Date?
    private var partnerArrivalSeen = false
    private var ended: EndedInChat?
    private var endSeen = false
    private var gone = false
    private var nextId = 0

    // The failure frames fail once, so the retry is walked too.
    private var failSuggest: Bool
    private var failAgree: Bool
    private var failPlaces: Bool

    private enum MyCheckIn { case none, stored, committed }

    init(frame: String, clock: @escaping @Sendable () -> Date = { Date() }, latencyMs: Int = 600) {
        self.frame = frame
        self.clock = clock
        self.latencyMs = latencyMs
        let start = clock()
        self.start = start
        // The sheet's clock for this frame: 15:55 while the chat is locked (I, J), past the 18:30 deadline in H.
        let kitNow: Date
        switch frame {
        case "I", "J": kitNow = DateSamples.aNow
        case "H": kitNow = Self.kitMeet.addingTimeInterval(-29 * Self.minute)
        default: kitNow = DateSamples.bNow
        }
        let shift = start.timeIntervalSince(kitNow)
        self.shift = shift
        func kit(_ t: Date) -> Date { t.addingTimeInterval(shift) }
        meetAt = kit(Self.kitMeet)
        place = DateSamples.date.place ?? DatePlace(name: "Cafe Krone", address: nil)
        availableUntil = kit(Self.kitMeet.addingTimeInterval(3 * 3600))
        // Read up to two minutes ago: Leonie's earlier message is read, the one E and L show under her request is not.
        readAt = start.addingTimeInterval(-2 * Self.minute)
        unlockStamped = !["I", "J"].contains(frame)
        failSuggest = frame == "K"
        failAgree = frame == "L"
        failPlaces = frame == "M"

        func moved(_ s: ChangeSuggestion) -> ChangeSuggestion {
            var m = s
            m.time = s.time.map(kit)
            m.wasTime = kit(s.wasTime)
            m.deadline = kit(s.deadline)
            m.createdAt = kit(s.createdAt)
            m.answeredAt = s.answeredAt.map(kit)
            m.expiredAt = s.expiredAt.map(kit)
            m.expiredBindingTime = s.expiredBindingTime.map(kit)
            return m
        }
        if !["I", "J"].contains(frame) {
            var seed = ChangeSamples.seed
            seed.sentAt = kit(seed.sentAt)
            messages = [seed]
        }
        // E and L draw her running-late message, unread, under the request (ChangeSamples' E date). J's chat is still
        // locked, so nothing could have been sent yet.
        if ["E", "L"].contains(frame) {
            messages.append(ChatMessage(id: "demo-unread", reason: .runningLate, body: DateSamples.unread.body ?? "",
                                        fromYou: false, sentAt: start.addingTimeInterval(-Self.minute), read: nil))
        }
        switch frame {
        case "C": suggestions = [moved(ChangeSamples.mineTime)]
        case "D": suggestions = [moved(ChangeSamples.mineTime), moved(ChangeSamples.minePlace)]
        case "E", "F", "J", "L": suggestions = [moved(ChangeSamples.theirsTime)]
        case "G":
            var agreed = moved(ChangeSamples.mineTime)
            agreed.status = .agreed
            agreed.answeredAt = start.addingTimeInterval(-Self.minute)
            suggestions = [agreed]
            timeWas = meetAt
            meetAt = kit(ChangeSamples.slots[1])
        case "H": suggestions = [moved(ChangeSamples.expiredTime)]
        default: break
        }
    }

    private func newId(_ prefix: String) -> String {
        nextId += 1
        return "demo-\(prefix)-\(nextId)"
    }

    private func pause(_ ms: Int) async {
        try? await Task.sleep(nanoseconds: UInt64(ms) * 1_000_000)
    }

    // ── the rules the server applies on every read ──────────────────────────────────────────

    private var cutoff: Date { meetAt.addingTimeInterval(-30 * Self.minute) }
    private var unlockAt: Date { meetAt.addingTimeInterval(-90 * Self.minute) }
    private var committed: Bool { myCheckIn == .committed }

    /// New suggestions close at the cutoff and after either person's check-in.
    private func allowed(_ now: Date) -> Bool { now < cutoff && !committed && partnerArrivedAt == nil }

    /// Every 15 minutes after the binding time, until 30 minutes before Leonie's availability ends.
    private func slots() -> [Date] {
        var out: [Date] = []
        var t = meetAt.addingTimeInterval(15 * Self.minute)
        let last = availableUntil.addingTimeInterval(-30 * Self.minute)
        while t <= last {
            out.append(t)
            t = t.addingTimeInterval(15 * Self.minute)
        }
        return out
    }

    private func expired(_ s: ChangeSuggestion, at: Date) -> ChangeSuggestion {
        var e = s
        e.status = .expired
        e.expiredAt = at
        e.expiredBindingTime = meetAt
        e.expiredBindingPlaceName = place.name
        return e
    }

    /// The expiry job: an open suggestion past its deadline expires, with the binding values then.
    private func tick() -> Date {
        let now = clock()
        suggestions = suggestions.map { $0.status == .pending && now >= $0.deadline ? expired($0, at: $0.deadline) : $0 }
        return now
    }

    private func pending() -> [ChangeSuggestion] { suggestions.filter { $0.status == .pending } }

    private func dateNow(_ now: Date) -> ActiveDate {
        let open = pending()
        let theirs = messages.filter { !$0.fromYou && $0.sentAt > readAt }
        if now >= unlockAt { unlockStamped = true }
        var d = DateSamples.date
        d.id = Self.demoDateId
        d.meetAt = meetAt
        d.meetDay = "today"
        d.unlockAt = unlockAt
        d.coordinateOpen = unlockStamped || committed
        d.place = place
        d.unread = theirs.last.map { DateUnread(count: theirs.count, reason: $0.reason, body: $0.body, sentAt: $0.sentAt) }
        d.checkedIn = committed
        d.chatTutorialSeen = tutorialSeen
        d.arrival = partnerArrivalSeen ? nil : partnerArrivedAt.map { DateArrival(checkedInAt: $0) }
        d.change = DateChange(cutoffAt: cutoff, allowed: allowed(now), slots: slots(), timeWas: timeWas,
                              placeWas: placeWas, pending: open)
        d.changeRequest = open.filter { !$0.byYou }.min { $0.deadline < $1.deadline }
        return d
    }

    @discardableResult
    private func agreed(_ s: ChangeSuggestion, _ now: Date) -> ChangeSuggestion {
        if let t = s.time {
            if timeWas == nil { timeWas = meetAt }
            meetAt = t
        }
        if let p = s.place {
            if placeWas == nil { placeWas = place.name }
            place = DatePlace(name: p.name, address: p.address)
        }
        var done = s
        done.status = .agreed
        done.answeredAt = now
        suggestions = suggestions.map { $0.id == s.id ? done : $0 }
        return done
    }

    /// A new suggestion replaces the same person's open one of that kind (one open per kind, the newest).
    private func add(_ kind: ChangeKind, byYou: Bool, time: Date?, placeOf: ChangePlace?, _ now: Date) -> ChangeSuggestion {
        suggestions = suggestions.map {
            guard $0.status == .pending, $0.kind == kind, $0.byYou == byYou else { return $0 }
            var r = $0
            r.status = .replaced
            return r
        }
        let s = ChangeSuggestion(id: newId("s"), kind: kind, byYou: byYou, time: time, place: placeOf, wasTime: meetAt,
                                 wasPlaceName: kind == .place ? place.name : nil, deadline: cutoff, createdAt: now)
        suggestions.append(s)
        return s
    }

    private static func changePlace(_ o: PlaceOption) -> ChangePlace {
        ChangePlace(placeId: o.placeId, name: o.name, type: o.type, address: o.address)
    }

    // ── DateStoring ─────────────────────────────────────────────────────────────────────────

    func active(zone: String) async -> Result<DateRead, CallFailure> {
        await pause(latencyMs / 3)
        let now = tick()
        if gone { return .success(.none) }
        if let ended { return .success(endSeen ? .none : .ended(ended)) }
        return .success(.active(dateNow(now)))
    }

    func partnerProfile(zone: String) async -> Result<SearchProfile, CallFailure> {
        await pause(latencyMs / 3)
        return .success(SearchSamples.full)
    }

    func createTestDate(minutes: Int) async -> Bool {
        let now = tick()
        meetAt = now.addingTimeInterval(TimeInterval(minutes) * Self.minute)
        place = DateSamples.date.place ?? place
        timeWas = nil
        placeWas = nil
        suggestions = []
        messages = []
        myCheckIn = .none
        partnerArrivedAt = nil
        partnerArrivalSeen = false
        unlockStamped = false
        ended = nil
        endSeen = false
        gone = false
        return true
    }

    func messageFromMatch(reason: ChatReason?, body: String) async -> Bool {
        messages.append(ChatMessage(id: newId("m"), reason: reason, body: body, fromYou: false, sentAt: tick(), read: nil))
        return true
    }

    func endTestDate() async -> Bool {
        gone = true
        return true
    }

    func matchEndsInChat() async -> Bool {
        ended = EndedInChat(dateId: Self.demoDateId, name: DateSamples.date.match.name, endedByYou: false)
        endSeen = false
        return true
    }

    func markArrivalSeen(dateId: String) async { partnerArrivalSeen = true }

    func partnerChecksIn() async -> Bool {
        partnerArrivedAt = tick()
        partnerArrivalSeen = false
        return true
    }

    func matchSuggests(kind: ChangeKind) async -> Bool {
        let now = tick()
        guard allowed(now) else { return false }
        switch kind {
        case .time:
            let offered = slots()
            guard let at = offered.count > 1 ? offered[1] : offered.first else { return false }
            _ = add(.time, byYou: false, time: at, placeOf: nil, now)
        case .place:
            guard let o = ChangeSamples.places.first(where: { $0.name != place.name }) else { return false }
            _ = add(.place, byYou: false, time: nil, placeOf: Self.changePlace(o), now)
        }
        return true
    }

    func matchAgrees() async -> Bool {
        let now = tick()
        guard let mine = pending().filter(\.byYou).min(by: { $0.deadline < $1.deadline }) else { return false }
        agreed(mine, now)
        return true
    }

    func expireSuggestions() async -> Bool {
        let now = tick()
        suggestions = suggestions.map { $0.status == .pending ? expired($0, at: now) : $0 }
        return true
    }

    // ── ChatStoring ─────────────────────────────────────────────────────────────────────────

    func thread(dateId: String) async -> Result<[ChatMessage], CallFailure> {
        await pause(latencyMs / 3)
        return .success(messages.sorted { $0.sentAt < $1.sentAt })
    }

    func send(dateId: String, reason: ChatReason?, body: String, clientId: String) async -> Result<ChatMessage, CallFailure> {
        await pause(latencyMs)
        if let sent = messages.first(where: { $0.clientId == clientId }) { return .success(sent) }
        let m = ChatMessage(id: newId("m"), reason: reason, body: body.trimmingCharacters(in: .whitespacesAndNewlines),
                            fromYou: true, sentAt: tick(), read: false, clientId: clientId)
        messages.append(m)
        return .success(m)
    }

    func markRead(dateId: String) async { readAt = tick() }

    func markTutorialSeen(dateId: String) async { tutorialSeen = true }

    func endInChat(dateId: String) async -> EndResult {
        await pause(latencyMs)
        let outcome = EndedInChat(dateId: Self.demoDateId, name: DateSamples.date.match.name, endedByYou: true)
        ended = outcome
        endSeen = false
        return .ended(outcome)
    }

    func markEndSeen(dateId: String) async { endSeen = true }

    // ── ChangeStoring ───────────────────────────────────────────────────────────────────────

    func places(dateId: String, query: String?) async -> Result<PlacesList, CallFailure> {
        await pause(latencyMs)
        if failPlaces {
            failPlaces = false
            return .failure(.network)
        }
        let q = (query ?? "").trimmingCharacters(in: .whitespaces).lowercased()
        let found = ChangeSamples.places.filter { o in
            o.name != place.name
                && (q.isEmpty || [o.name, placeTypeLabel(o.type), o.address].contains { $0.lowercased().contains(q) })
        }
        return .success(PlacesList(google: true, places: found))
    }

    func suggest(dateId: String, request: SuggestRequest) async -> SuggestResult {
        await pause(latencyMs)
        let now = tick()
        if let sentId = clientIds[request.clientId], let sent = suggestions.first(where: { $0.id == sentId }) {
            return .sent(sent)
        }
        guard allowed(now) else { return .stale }
        if failSuggest {
            failSuggest = false
            return .failed(.network)
        }
        let s: ChangeSuggestion
        switch request.kind {
        case .time:
            guard let at = request.time, slots().contains(at) else { return .stale }
            s = add(.time, byYou: true, time: at, placeOf: nil, now)
        case .place:
            guard let o = ChangeSamples.places.first(where: { $0.placeId == request.placeId && $0.name != place.name }) else {
                return .stale
            }
            s = add(.place, byYou: true, time: nil, placeOf: Self.changePlace(o), now)
        }
        clientIds[request.clientId] = s.id
        return .sent(s)
    }

    func agree(dateId: String, suggestionId: String, surface: AgreeSurface) async -> AgreeResult {
        await pause(latencyMs)
        let now = tick()
        let s = suggestions.first { $0.id == suggestionId }
        if let s, s.status == .agreed, !s.byYou { return .agreed(s) }
        guard let s, s.status == .pending, !s.byYou, !committed, partnerArrivedAt == nil else { return .stale }
        if failAgree {
            failAgree = false
            return .failed(.network)
        }
        return .agreed(agreed(s, now))
    }

    func suggestions(dateId: String) async -> Result<[ChangeSuggestion], CallFailure> {
        await pause(latencyMs / 3)
        _ = tick()
        return .success(suggestions.sorted { $0.createdAt < $1.createdAt })
    }

    // ── ArrivalStoring and the fix ──────────────────────────────────────────────────────────

    func checkIn(dateId: String, fix: LocationFix, entry: ArrivalEntry, clientId: String) async -> CheckInResult {
        await pause(latencyMs)
        if committed { return .alreadyCheckedIn }
        myCheckIn = .stored
        return .stored
    }

    func undo(dateId: String) async -> Result<Void, CallFailure> {
        await pause(latencyMs / 3)
        if myCheckIn == .stored { myCheckIn = .none }
        return .success(())
    }

    func commit(dateId: String) async -> Result<Void, CallFailure> {
        await pause(latencyMs / 3)
        if myCheckIn == .stored { myCheckIn = .committed }
        return .success(())
    }

    /// Standing at the meeting place: the demo has no phone location to read.
    func fix() async -> LocationFix? {
        await pause(latencyMs)
        return LocationFix(latitude: 52.5008, longitude: 13.4194, accuracyM: 12)
    }
}
#endif
