//
//  CheckInModel.swift
//  ShowUp · the 48hrs Check-In's state machine (SHOWUP-178 to SHOWUP-183)
//
//  The Swift twin of `checkin/CheckInViewModel.kt`; read that file's header. In short:
//
//    · ONE OWNER FOR THE WHOLE FLOW, BUILT BEFORE ITS STEPS (epic, "Built once"): the step order,
//      the back and close routing, the gate's reopen rule, what each step keeps, and every event of
//      family H. Screens take this state and return taps; the host moves between them through the
//      `onX` callbacks.
//    · NOTHING IS KEPT AS A DRAFT (epic rule 3, 13 Oct 2026). Availability and the uplift live here
//      only -- never saved, never pre-filled -- and a close throws them away. Motivation and Vibe
//      pre-select the LAST ACCEPTED answer and Filters the user's filter settings, all from the
//      server; each is saved on its accepted Continue. Back and forward within one flow keep all.
//    · PICKED HOURS ARE KEYED BY THEIR WALL-CLOCK TIME, not their hour number, so a flow left open
//      across 7am sees its picks move into the right grid and a started hour fall out silently.
//    · NOT SAVED ACROSS A PROCESS DEATH, on purpose: "Only the close (or leaving the app long enough
//      for the screen to be destroyed) clears them" (story 02). The host restarts a Check-In whose
//      owner did not survive at Today, where Motivation, Vibe and Filters pre-fill again.
//

import Foundation

/// One availability grid as drawn: its day, and each slot's hour and wall-clock start.
struct AvailabilityGrid: Equatable, Sendable {
    let day: CheckInStep
    let hours: [Int]
    let starts: [CivilDateTime]

    /// The picks inside this grid, by hour -- what the screen draws and the summary reads.
    func picked(_ picks: [CivilDateTime: SlotTier]) -> [Int: SlotTier] {
        var out: [Int: SlotTier] = [:]
        for (hour, start) in zip(hours, starts) {
            if let tier = picks[start] { out[hour] = tier }
        }
        return out
    }
}

/// The grid for `day` at `days`: Today from the next full hour, Tomorrow from 7am, both to the 6am
/// slot.
func gridOf(_ day: CheckInStep, _ days: CheckInDays) -> AvailabilityGrid {
    let date = day == .today ? days.todayDate : days.tomorrowDate
    let hours = availabilityHours(day == .today ? days.todayStartHour : checkInDayStart)
    let starts = hours.map { h in
        CivilDateTime(date: h >= checkInDayStart ? date : date.adding(days: 1), hour: h)
    }
    return AvailabilityGrid(day: day, hours: hours, starts: starts)
}

/// What the Check-In screens draw.
struct CheckInUiState: Equatable, Sendable {
    /// The server's state, once read.
    var server: CheckInState?
    /// Today and Tomorrow at the last evaluation of the clock.
    var days: CheckInDays?
    /// Every picked hour of this flow, both days, by wall-clock start.
    var picks: [CivilDateTime: SlotTier] = [:]
    var motivation: String?
    var vibes: Set<String> = []
    var filters: CheckInFilters?
    var uplift = ""
    /// How it works -- collapsed on every entry, per screen.
    var howItWorksOpen = false
    /// The No hours today? confirm.
    var noHoursDialog = false
    /// The footer's toast: its text and a tick that restarts its 2600 ms on a repeat tap.
    var toast: String?
    var toastTick = 0
    /// Find my date is in flight: the button, field, back and close are inert.
    var saving = false
    /// The save-failed card -- shown until the next tap or an edit.
    var saveFailed = false
    /// The server refused the text for contact info: state C until the text changes.
    var serverContactError = false

    var todayGrid: AvailabilityGrid? { days.map { gridOf(.today, $0) } }
    var tomorrowGrid: AvailabilityGrid? { days.map { gridOf(.tomorrow, $0) } }

    /// Contact info in the field, as the client sees it or as the server reported it.
    var upliftBlocked: Bool { serverContactError || upliftHasContact(uplift) }
}

@MainActor
@Observable
final class CheckInModel {
    private(set) var state = CheckInUiState()

    private let store: any CheckInStoring
    private let analytics: (any AnalyticsTracking)?
    /// The device clock and zone, injected so the 7am day and the next full hour are testable.
    private let clock: () -> CivilDateTime
    private let zone: () -> TimeZone
    /// Milliseconds, for `time_to_answer_ms`.
    private let millis: () -> Int
    /// How long a toast stays: `Motion.toast`. Injected so a test need not wait 2.6 seconds.
    private let toastSeconds: TimeInterval

    /// The step on screen, or nil on the gate or outside the flow.
    private var currentStep: CheckInStep?
    private var lastScreen: ProfileScreen?

    /// When the step on screen was viewed, for `time_to_answer_ms`.
    private var viewedAt = 0

    /// What a pre-filled step showed on entry, for `prefilled` / `picked` and `filters_changed`.
    private var motivationOnEntry: String?
    private var vibesOnEntry: Set<String> = []
    private var filtersOnEntry: CheckInFilters?

    /// Filter saves run one after another, so the server applies them in the order they were made:
    /// a slow first save can never land after a second and leave the server on the older settings.
    private var filtersSave: Task<Void, Never>?

    /// The settings the last save that LANDED sent -- what the server holds, as far as this device
    /// knows. The commit sends the filters again unless they are exactly these: a save that failed,
    /// or was overtaken by a change, is never mistaken for one that landed.
    private var filtersConfirmed: CheckInFilters?

    /// The newest settings handed to a save that has not answered yet. A flow begun while one is in
    /// flight starts from them, not from the older copy the server is about to replace -- or
    /// Continue on that older copy would undo the choice just made.
    private var filtersInFlight: CheckInFilters?

    /// Filter saves that have not answered yet.
    private var filtersSaving = 0

    /// Counts filter saves as they are made, so only the newest one's answer clears
    /// `filtersInFlight`.
    private var filtersSaveSeq = 0

    /// The last filter save that answered: what it sent, and why it failed (nil when it landed).
    private var lastFiltersSave: (filters: CheckInFilters, failure: CallFailure?)?

    /// Continue was accepted on the stand-in: what it showed and what the user had changed on it, at
    /// that moment. Saved, resolved against the server's settings, when a read lands -- the accepted
    /// settings, not whatever the step holds by then: an edit made after going back to Filters is
    /// not confirmed until its own Continue.
    private var standInAccepted: (filters: CheckInFilters, edited: Set<FilterField>)?

    /// A commit in this flow reached the network and its outcome is unknown -- no response, or one
    /// that was not a save. It may have saved: from then on every commit goes to the server, which
    /// answers a saved one with a replay, instead of being stopped on the device for having no hours.
    private var commitMaybeSaved = false

    /// An intro-passed write is in flight, so a resend never doubles it.
    private var marking = false

    /// This device passed the intro: no read can un-pass it, and one that disagrees resends the flag.
    private var introPassedHere = false

    /// `attempt` on `check_in_commit_failed`: 1 for the first tap on this screen view.
    private var commitAttempt = 0

    /// Whether this object holds a flow -- false after a process death, so the host restarts at
    /// Today.
    private(set) var flowAlive = false

    private var loading: Task<Void, Never>?
    private var toastTask: Task<Void, Never>?

    /// The pre-fill of Motivation, Vibe and Filters is still owed: set when a flow begins, paid by
    /// the first server read that lands. Not tied to one request -- the read that begins a flow can
    /// fail, and the next one, on an arrival or a foreground, must still pay it.
    private var prefillPending = false

    /// The user has changed this step's answer in this flow: a late read never overwrites it.
    private var motivationTouched = false
    private var vibesTouched = false
    private var filtersTouched = false

    /// Filters showed the offline stand-in -- no gender ticked, ages and distance at their defaults
    /// -- because no read had landed, and still none has. Nothing built on it is sent: the server's
    /// own settings are unknown. The read that lands resolves it -- what the user set stays, the
    /// rest is the server's (`filtersOverStandIn`) -- and the commit reads first if none has.
    private var filtersStandIn = false

    /// What the user changed on the stand-in.
    private var standInEdits: Set<FilterField> = []

    /// The gate's three ways in may fire once per showing: a second tap during the push does nothing.
    private var gateArmed = false

    /// Bumped by every local change the server will catch up with -- the intro passed, a commit
    /// saved. A read that STARTED before the latest one is stale: it may still say "not passed" or
    /// "not active", and applying it as-is would send a user who has just passed the intro back to
    /// it from the gate, or bounce a Check-In just committed from Search to the gate.
    private var writeSeq = 0

    init(
        store: any CheckInStoring,
        analytics: (any AnalyticsTracking)? = nil,
        clock: @escaping () -> CivilDateTime = { CivilDateTime(Date(), in: .current) },
        zone: @escaping () -> TimeZone = { .current },
        millis: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) },
        toastSeconds: TimeInterval = Motion.toast,
        places: (any PlaceReading)? = nil
    ) {
        self.store = store
        self.analytics = analytics
        self.clock = clock
        self.zone = zone
        self.millis = millis
        self.toastSeconds = toastSeconds
        self.places = places
        evaluateClock()
    }

    /// The phone's place at `Find my date` -- what Search measures every distance from (SHOWUP-184).
    private let places: (any PlaceReading)?
    /// Read once per flow, at its first commit: a retry reuses it rather than waiting on another fix,
    /// and a new flow reads again.
    private var place: CheckInPlace?
    private var placeRead = false

    /// Filters is open from Search, and its exits go back there.
    private var filtersFromSearch = false

    // MARK: the server and the clock

    /// Reads the Check-In state. On every foreground and every arrival: "read on every foreground".
    func refresh() {
        guard loading == nil else { return }
        // The sequence is taken NOW, not when the task first runs: a write made between the two
        // still makes this read stale.
        let seq = writeSeq
        loading = Task { [weak self] in
            _ = await self?.readAt(seq)
            self?.loading = nil
        }
    }

    /// One read, awaited -- for the launch, which must know whether a Check-In is active before it
    /// picks the gate or Search. Nil when the server could not be reached.
    func read() async -> CheckInState? {
        await readAt(writeSeq)
    }

    private func readAt(_ seq: Int) async -> CheckInState? {
        if case .success(let read) = await readOutcome(seq) { return read }
        return nil
    }

    private func readOutcome(_ seq: Int) async -> Result<CheckInState, CallFailure> {
        let loaded: CheckInState
        switch await store.load() {
        case .success(let read): loaded = read
        case .failure(let why): return .failure(why)
        }
        // Cancelled: the caller checks `Task.isCancelled` and reports nothing, whatever this says.
        guard !Task.isCancelled else { return .failure(.network) }
        let prior = state.server
        // The intro flag never goes back: the server only ever sets it. A read saying "not passed"
        // after this device passed it beat the write to the server -- or the write never landed, so
        // it is sent again.
        let passed = introPassedHere || prior?.introPassed == true
        if passed && !loaded.introPassed { markIntroPassed() }
        // Stale: `active` only moves forward between reads, so a read that started before a commit
        // keeps what the commit set.
        let stale = seq != writeSeq && prior != nil
        var read = loaded
        read.introPassed = loaded.introPassed || passed
        read.active = loaded.active || (stale && prior?.active == true)
        applyRead(read)
        return .success(read)
    }

    /// A read landed: it is the server's state, and it pays an owed pre-fill on untouched steps.
    private func applyRead(_ read: CheckInState) {
        let prefill = prefillPending && flowAlive
        if prefill { prefillPending = false }
        let standIn = filtersStandIn
        let before = state
        var s = state
        s.server = read
        if prefill {
            if !motivationTouched { s.motivation = s.motivation ?? read.lastMotivation }
            if !vibesTouched { s.vibes = s.vibes.isEmpty ? read.lastVibes : s.vibes }
        }
        if standIn {
            s.filters = s.filters.map { filtersOverStandIn($0, server: read.filters, edited: standInEdits) }
                ?? read.filters
        } else if prefill && !filtersTouched && s.filters == nil {
            s.filters = read.filters
        }
        state = s
        if standIn {
            filtersStandIn = false
            standInEdits = []
            // Continue was accepted on it: the settings it resolved to are what the user accepted,
            // saved now as any accepted Continue is -- not lost to a later close.
            if let accepted = standInAccepted {
                saveFilters(filtersOverStandIn(accepted.filters, server: read.filters, edited: accepted.edited))
            }
            standInAccepted = nil
        }
        // A pre-fill landing on the step on screen is what that step showed: Continue on it
        // unchanged is `prefilled`, not `picked` against the empty step it replaced.
        switch currentStep {
        case .some(.motivation):
            if s.motivation != before.motivation { motivationOnEntry = s.motivation }
        case .some(.vibe):
            if s.vibes != before.vibes { vibesOnEntry = s.vibes }
        case .some(.filters):
            if standIn { filtersOnEntry = read.filters }
        default:
            break
        }
    }

    /// The device clock, re-read: "re-evaluated on every foreground, and at minimum when the screen
    /// is shown. A slot that has started disappears from the grid and from the selection, silently."
    func evaluateClock() {
        let days = checkInDays(clock())
        let offered = Set(gridOf(.today, days).starts + gridOf(.tomorrow, days).starts)
        var s = state
        s.days = days
        s.picks = s.picks.filter { offered.contains($0.key) }
        state = s
    }

    // MARK: entering and leaving

    /// A NEW flow begins -- from Profile 23 into the intro, or from the gate. Availability and the
    /// uplift start empty; Motivation, Vibe and Filters start from the server's last answers.
    func beginFlow(previewStep: CheckInStep? = nil) {
        flowAlive = true
        place = nil
        placeRead = false
        prefillPending = true
        motivationTouched = false
        vibesTouched = false
        filtersTouched = false
        filtersStandIn = false
        standInEdits = []
        standInAccepted = nil
        commitMaybeSaved = false
        filtersConfirmed = nil
        clearToast()
        let server = state.server
        var s = state
        s.picks = [:]
        s.uplift = ""
        s.motivation = server?.lastMotivation
        s.vibes = server?.lastVibes ?? []
        s.filters = filtersInFlight ?? server?.filters
        s.saving = false
        s.saveFailed = false
        s.serverContactError = false
        s.noHoursDialog = false
        state = s
        evaluateClock()
        #if DEBUG
        if let previewStep, let stepIndex = CheckInStep.allCases.firstIndex(of: previewStep) {
            motivationTouched = [.vibe, .filters, .uplift].contains(previewStep)
            vibesTouched = [.filters, .uplift].contains(previewStep)
            if stepIndex > 2, let start = state.tomorrowGrid?.starts.first {
                state.picks = [start: .preferred]
            }
            if motivationTouched { state.motivation = "long_term" }
            if vibesTouched { state.vibes = ["calm"] }
        }
        #endif
        // The read may land after the flow begins -- or fail, and a later one land: whichever read
        // lands first pre-fills, untouched steps only (`applyRead`).
        refresh()
    }

    /// `step` is on screen. A real arrival reports the view; a redraw of the step already on screen
    /// does neither. `referrer` comes from the navigation, never hard-coded.
    func arrived(_ step: CheckInStep?, screen: ProfileScreen, referrer: ProfileScreen?) {
        if step != nil && currentStep == step { return }
        if step == nil && currentStep == nil && lastScreen == screen { return }
        currentStep = step
        lastScreen = screen
        // The gate is armed while it is the screen shown, and only then.
        gateArmed = step == nil && screen == .checkinSearchUnavailable
        viewedAt = millis()
        clearToast()
        evaluateClock()
        state.howItWorksOpen = false
        state.noHoursDialog = false
        state.saveFailed = false
        switch step {
        case .some(.motivation):
            motivationOnEntry = state.motivation
        case .some(.vibe):
            vibesOnEntry = state.vibes
        case .some(.filters):
            // NO READ HAS LANDED YET (offline since the flow began): the step still works, from a
            // stand-in with no gender ticked, so Continue raises the gender toast rather than doing
            // nothing. A read that lands before the user touches it replaces the stand-in.
            if state.filters == nil {
                filtersStandIn = true
                state.filters = CheckInFilters(genders: [])
            }
            filtersOnEntry = state.filters
        case .some(.uplift):
            commitAttempt = 0
        default:
            break
        }
        refresh()
        analytics?.report(ProfileAnalytics.screenViewed(screen, referrer: referrer))
    }

    /// The flow was left by a route it does not take -- a developer-menu jump. Discards it.
    func leftElsewhere() {
        currentStep = nil
        lastScreen = nil
        gateArmed = false
        discard()
    }

    /// The tabs are shown with no Check-In screen -- Settings, for a user with no Check-In. Its
    /// Instant tab is still the gate, so it arms it; it has no §11 screen, so nothing is reported.
    func atTabs() {
        currentStep = nil
        lastScreen = nil
        gateArmed = true
        clearToast()
    }

    private var windowId: String { state.server?.windowId ?? "" }

    private func onScreen(_ step: CheckInStep) -> Bool { currentStep == step && !state.saving }

    // MARK: the intro and the gate (Check-In 01)

    /// `Check-in for a date`: sets the intro-passed flag and starts the Check-In. Single-fire.
    func introStarted(onAdvance: () -> Void) {
        guard onScreen(.intro) else { return }
        currentStep = nil
        analytics?.report(CheckInAnalytics.started(windowId: windowId))
        writeSeq += 1
        introPassedHere = true
        state.server?.introPassed = true
        markIntroPassed()
        onAdvance()
    }

    /// The intro-passed write, unless one is already in flight. Its result shows in the next read.
    private func markIntroPassed() {
        guard !marking else { return }
        marking = true
        let store = self.store
        Task { [weak self] in
            _ = await store.markIntroPassed()
            self?.marking = false
        }
    }

    /// Where the gate reopens: Today once the intro has been passed, the intro before that.
    func gateDestination() -> CheckInStep {
        (introPassedHere || state.server?.introPassed == true) ? .today : .intro
    }

    /// The gate's CTA, Search tab or Instant tab: reopens the Check-In.
    func gateTapped(_ target: CheckInAnalytics.GateTarget, onReopen: (CheckInStep) -> Void) {
        guard gateArmed else { return }
        gateArmed = false
        let destination = gateDestination()
        analytics?.report(CheckInAnalytics.gateTapped(target, introPassed: destination == .today))
        beginFlow()
        onReopen(destination)
    }

    // MARK: availability (Check-In 02)

    /// A slot tapped: idle -> preferred -> available -> idle. No event.
    func slotTapped(_ step: CheckInStep, start: CivilDateTime) {
        guard onScreen(step) else { return }
        state.picks[start] = cycleSlot(state.picks[start])
    }

    /// `Clear all`: this day only, no confirm. No event.
    func clearAll(_ step: CheckInStep) {
        guard onScreen(step), let grid = gridFor(step) else { return }
        let starts = Set(grid.starts)
        state.picks = state.picks.filter { !starts.contains($0.key) }
    }

    /// How it works, toggled inline. No event.
    func toggleHowItWorks() {
        state.howItWorksOpen.toggle()
    }

    /// Continue on Today or Tomorrow.
    func availabilityContinue(_ step: CheckInStep, onAdvance: () -> Void) {
        guard onScreen(step) else { return }
        evaluateClock()
        guard let grid = gridFor(step) else { return }
        let picked = grid.picked(state.picks)
        if !picked.isEmpty {
            acceptAvailability(step, picked, onAdvance: onAdvance)
        } else if step == .today {
            // Today is optional: an empty Today raises the confirm, never an error.
            state.noHoursDialog = true
        } else {
            // Tomorrow is required: the dimmed tap still registers, and raises the toast.
            showToast(CheckInCopy.tomorrowToast)
        }
    }

    /// The confirm's `Continue to tomorrow`: an empty Today is recorded and the flow moves on.
    func noHoursConfirmed(onAdvance: () -> Void) {
        guard onScreen(.today), state.noHoursDialog else { return }
        analytics?.report(CheckInAnalytics.noHoursResolved(.continueTomorrow, windowId: windowId))
        state.noHoursDialog = false
        acceptAvailability(.today, [:], onAdvance: onAdvance)
    }

    /// `Cancel, pick hours today` -- or, with `dismissed`, a scrim tap or the swipe. Stays on Today.
    func noHoursCancelled(dismissed: Bool) {
        guard state.noHoursDialog else { return }
        analytics?.report(CheckInAnalytics.noHoursResolved(dismissed ? .dismissed : .pickToday, windowId: windowId))
        state.noHoursDialog = false
    }

    private func acceptAvailability(_ step: CheckInStep, _ picked: [Int: SlotTier], onAdvance: () -> Void) {
        analytics?.report(CheckInAnalytics.stepAnswered(
            step, answerValue: picked.isEmpty ? "none" : "hours", timeToAnswerMs: millis() - viewedAt))
        analytics?.report(CheckInAnalytics.slotsSet(
            step,
            preferredHours: tierCount(picked, .preferred),
            availableHours: tierCount(picked, .available),
            windowId: windowId))
        leaveStep()
        onAdvance()
    }

    private func gridFor(_ step: CheckInStep) -> AvailabilityGrid? {
        switch step {
        case .today: return state.todayGrid
        case .tomorrow: return state.tomorrowGrid
        default: return nil
        }
    }

    // MARK: motivation (Check-In 03)

    /// A row tapped. Single-select: another row replaces it; the selected row does nothing.
    func motivationTapped(_ id: String) {
        guard onScreen(.motivation) else { return }
        motivationTouched = true
        state.motivation = id
    }

    func motivationContinue(onAdvance: () -> Void) {
        guard onScreen(.motivation) else { return }
        guard let picked = MotivationOption.of(state.motivation) else {
            showToast(CheckInCopy.motivationToast)
            return
        }
        let answer = picked.id == motivationOnEntry ? "prefilled" : "picked"
        analytics?.report(CheckInAnalytics.stepAnswered(
            .motivation, answerValue: answer, timeToAnswerMs: millis() - viewedAt))
        analytics?.report(CheckInAnalytics.motivationSelected(picked, windowId: windowId))
        // The last accepted answer, saved now -- whatever happens to the flow afterwards.
        motivationTouched = true
        state.server?.lastMotivation = picked.id
        let store = self.store
        let id = picked.id
        Task { _ = await store.saveAnswers(motivation: id, vibes: nil) }
        leaveStep()
        onAdvance()
    }

    // MARK: vibe (Check-In 04)

    /// A chip tapped. At 5/5 an idle chip selects nothing and raises the max toast. No event.
    func vibeTapped(_ id: String) {
        guard onScreen(.vibe) else { return }
        switch tapVibe(state.vibes, id) {
        case .changed(let vibes):
            vibesTouched = true
            state.vibes = vibes
        case .limit:
            showToast(CheckInCopy.vibeToastMax)
        }
    }

    func vibeContinue(onAdvance: () -> Void) {
        guard onScreen(.vibe) else { return }
        let vibes = state.vibes
        if vibes.isEmpty {
            showToast(CheckInCopy.vibeToastEmpty)
            return
        }
        // Same values in any tap order is unchanged.
        let answer = vibes == vibesOnEntry ? "prefilled" : "picked"
        analytics?.report(CheckInAnalytics.stepAnswered(.vibe, answerValue: answer, timeToAnswerMs: millis() - viewedAt))
        analytics?.report(CheckInAnalytics.vibeSelected(vibes, windowId: windowId))
        vibesTouched = true
        state.server?.lastVibes = vibes
        let store = self.store
        Task { _ = await store.saveAnswers(motivation: nil, vibes: vibes) }
        leaveStep()
        onAdvance()
    }

    // MARK: filters (Check-In 05)

    func genderTapped(_ id: String) {
        guard onScreen(.filters) else { return }
        filtersTouched = true
        if filtersStandIn { standInEdits.insert(.genders) }
        if let filters = state.filters {
            var next = filters
            next.genders = toggleGender(filters.genders, id)
            state.filters = next
        }
    }

    /// An age thumb moved; the thumbs may meet but never cross. No event per drag.
    func ageMoved(thumb: Int, value: Int) {
        guard onScreen(.filters) else { return }
        filtersTouched = true
        if filtersStandIn { standInEdits.insert(.ages) }
        state.filters = state.filters.map { moveAgeThumb($0, thumb: thumb, value: value) }
    }

    func distanceMoved(_ km: Int) {
        guard onScreen(.filters) else { return }
        filtersTouched = true
        if filtersStandIn { standInEdits.insert(.distance) }
        state.filters?.distanceKm = min(max(km, distanceMin), distanceMax)
    }

    /// Check-In 05b: whether the Dealbreaker switches show -- the server's remote flag
    /// `filters_dealbreakers`. Off, they are not drawn, not sent, and tracked as false.
    private var dealbreakersShown: Bool { state.server?.filtersDealbreakers == true }

    /// The Age range Dealbreaker switch. The line under it swaps at once; nothing else changes. Saved
    /// with the rest on an accepted Continue -- never on the tap, never on close -- and a tap reports
    /// nothing. Android's `ageDealbreakerToggled`.
    func ageDealbreakerToggled(_ on: Bool) {
        guard onScreen(.filters), dealbreakersShown else { return }
        filtersTouched = true
        if filtersStandIn { standInEdits.insert(.ageDealbreaker) }
        state.filters?.ageDealbreaker = on
    }

    /// The Distance Dealbreaker switch, as `ageDealbreakerToggled`.
    func distanceDealbreakerToggled(_ on: Bool) {
        guard onScreen(.filters), dealbreakersShown else { return }
        filtersTouched = true
        if filtersStandIn { standInEdits.insert(.distanceDealbreaker) }
        state.filters?.distanceDealbreaker = on
    }

    func filtersContinue(onAdvance: () -> Void) {
        guard onScreen(.filters), let filters = state.filters else { return }
        if filters.genders.isEmpty {
            showToast(CheckInCopy.filtersToast)
            return
        }
        let entry = filtersOnEntry ?? filters
        let answer = filtersChanged(entry, filters) == "none" ? "prefilled" : "picked"
        analytics?.report(CheckInAnalytics.stepAnswered(.filters, answerValue: answer, timeToAnswerMs: millis() - viewedAt))
        analytics?.report(CheckInAnalytics.filtersChanged(entry: entry, now: filters, windowId: windowId,
                                                          dealbreakers: dealbreakersShown))
        filtersTouched = true
        // Built on the stand-in, nothing is sent yet: the read that lands resolves and sends it, or
        // the commit reads first.
        if filtersStandIn { standInAccepted = (filters, standInEdits) } else { saveFilters(filters) }
        leaveStep()
        onAdvance()
    }

    /// One filter save, after any still in flight. The server's copy changes only when it lands.
    private func saveFilters(_ filters: CheckInFilters) {
        let previous = filtersSave
        let store = self.store
        let withDealbreakers = dealbreakersShown
        filtersInFlight = filters
        filtersSaving += 1
        filtersSaveSeq += 1
        let seq = filtersSaveSeq
        filtersSave = Task { [weak self] in
            await previous?.value
            let outcome = await store.saveFilters(filters, withDealbreakers: withDealbreakers)
            guard let self else { return }
            self.filtersSaving -= 1
            // Only the newest save's answer: an earlier one answering, even with the same settings,
            // leaves later saves still out.
            if seq == self.filtersSaveSeq { self.filtersInFlight = nil }
            switch outcome {
            case .success:
                self.lastFiltersSave = (filters, nil)
                self.filtersLanded(filters)
            case .failure(let why):
                self.lastFiltersSave = (filters, why)
            }
        }
    }

    private func filtersLanded(_ filters: CheckInFilters) {
        filtersConfirmed = filters
        state.server?.filters = filters
    }

    // MARK: Filters opened from Search (SHOWUP-184)
    //
    // "The filter button opens Check-In 05 Filters. Returning applies the filters to the queue." The
    // same screen on the user's saved settings, outside any Check-In, so not one of its steps. Read
    // `CheckInViewModel.kt` for the decisions: Continue saves and only then returns; back and the
    // close return without saving and never discard the Check-In; the funnel's step events never fire.

    /// Search's filter button: Filters on the saved settings, never a half-made Check-In's edits.
    func searchFiltersOpened(referrer: ProfileScreen = .searchProfile) {
        if filtersFromSearch && currentStep == .filters { return }
        filtersFromSearch = true
        if let saved = state.server?.filters { state.filters = saved }
        arrived(.filters, screen: .checkinFilters, referrer: referrer)
    }

    /// Continue on Filters from Search: saved, then `onSaved` -- Search, reading its queue again.
    func searchFiltersContinue(onSaved: @escaping () -> Void) {
        guard filtersFromSearch, onScreen(.filters), let shown = state.filters else { return }
        if shown.genders.isEmpty {
            showToast(CheckInCopy.filtersToast)
            return
        }
        state.saving = true
        let store = self.store
        Task { [weak self] in
            guard let self else { return }
            // A STAND-IN IS NEVER SAVED FROM HERE: its untouched settings are defaults. The read
            // resolves it -- their edits over the server's -- or, with no network, the save fails.
            if self.filtersStandIn { _ = await self.read() }
            let filters = self.filtersStandIn ? nil : self.state.filters
            await self.filtersSave?.value
            let saved: Result<Void, CallFailure>
            if let filters, filters == self.state.server?.filters {
                saved = .success(())
            } else if let filters {
                saved = await store.saveFilters(filters, withDealbreakers: self.dealbreakersShown)
            } else {
                saved = .failure(.network)
            }
            self.state.saving = false
            guard case .success = saved, let filters else {
                self.showToast(CheckInCopy.filtersSaveErrorProposed)
                return
            }
            self.filtersLanded(filters)
            self.analytics?.report(CheckInAnalytics.filtersChanged(entry: self.filtersOnEntry ?? filters,
                                                                   now: filters, windowId: self.windowId, openedFrom: "search",
                                                                   dealbreakers: self.dealbreakersShown))
            self.leaveSearchFilters()
            onSaved()
        }
    }

    /// Back, the swipe or the close on Filters from Search: the edits dropped, Search shown again.
    func searchFiltersLeft(onLeft: () -> Void) {
        guard filtersFromSearch, onScreen(.filters) else { return }
        state.filters = state.server?.filters ?? filtersOnEntry
        leaveSearchFilters()
        onLeft()
    }

    private func leaveSearchFilters() {
        filtersFromSearch = false
        filtersStandIn = false
        standInEdits = []
        leaveStep()
    }

    // MARK: uplift and the commit (Check-In 06)

    /// The field changed: cut at 100 graphemes; an edit clears the save-failed card and a server
    /// refusal.
    func upliftChanged(_ text: String) {
        guard onScreen(.uplift) else { return }
        state.uplift = clampUplift(text)
        state.saveFailed = false
        state.serverContactError = false
    }

    /// `Find my date`. Blocked text raises the toast and sends nothing; otherwise the whole Check-In
    /// commits. Async, as `PickModel.continuePressed` is: the host awaits it in a task, and every
    /// tap after the first finds `saving` set and does nothing.
    func findMyDate(onCommitted: () -> Void) async {
        guard onScreen(.uplift) else { return }
        let s = state
        if s.upliftBlocked {
            showToast(CheckInCopy.contactError)
            return
        }
        // Unreachable through the walk -- Motivation's Continue needs one -- but never a dead
        // button: the save-failed card, which is the truth, rather than a tap that does nothing.
        guard let motivation = s.motivation else {
            state.saveFailed = true
            return
        }
        commitAttempt += 1
        let attempt = commitAttempt
        state.saving = true
        state.saveFailed = false
        clearToast()
        let result = await commit(motivation)
        // The task ended: not a failed commit, and nothing may be reported as one.
        guard !Task.isCancelled else {
            state.saving = false
            return
        }
        let window = state.server?.windowId ?? ""
        switch result {
        case .saved(let saved):
            writeSeq += 1
            analytics?.report(CheckInAnalytics.stepAnswered(
                .uplift, answerValue: upliftAnswerValue(s.uplift), timeToAnswerMs: millis() - viewedAt))
            state.server = saved
            state.saving = false
            leaveStep()
            discard()
            onCommitted()
        case .contactInfo:
            analytics?.report(CheckInAnalytics.commitFailed(.contactInfo, attempt: attempt, windowId: window))
            state.saving = false
            state.serverContactError = true
        case .noHours:
            // Every picked hour has started: not a failed save and not the network -- the user
            // needs to pick again, which is what Tomorrow's own toast says.
            state.saving = false
            showToast(CheckInCopy.tomorrowToast)
        case .network, .server:
            analytics?.report(CheckInAnalytics.commitFailed(
                result == .network ? .network : .server, attempt: attempt, windowId: window))
            state.saving = false
            state.saveFailed = true
        }
    }

    /// The commit itself: a read first if none has landed (the window id, and the server's filters
    /// under a stand-in), the filter settings first unless a save of exactly these landed, then
    /// every upcoming picked hour with its instant on the device's zone. A failure on the way is
    /// reported as what it was -- no response, or the server's.
    private func commit(_ motivation: String) async -> CommitResult {
        // Nothing left to commit -- known before any call is made, unless an earlier attempt may
        // have saved: then the server decides, and answers a saved one with a replay.
        evaluateClock()
        if state.picks.isEmpty && !commitMaybeSaved { return .noHours }
        var server = filtersStandIn ? nil : state.server
        if server == nil {
            switch await readOutcome(writeSeq) {
            case .success(let read): server = read
            case .failure(let why): return why.commitResult
            }
        }
        guard !Task.isCancelled else { return .network }
        // No window: profile creation is not complete on the server. Its state, not the network.
        guard let windowId = server?.windowId else { return .server }
        // Saves still in flight finish first, so the one compared with is the last.
        let waited = filtersSaving > 0
        await filtersSave?.value
        if let filters = state.filters, filters != filtersConfirmed {
            // The save just waited for sent exactly these and found no network: sending them again
            // now would only double the wait. The next tap tries again.
            if waited, let last = lastFiltersSave, last.filters == filters, last.failure == .network {
                return .network
            }
            switch await store.saveFilters(filters, withDealbreakers: dealbreakersShown) {
            case .success: filtersLanded(filters)
            case .failure(let why): return why.commitResult
            }
            guard !Task.isCancelled else { return .network }
        }
        let s = state
        evaluateClock()
        let zone = self.zone()
        let slots = state.picks
            .sorted { $0.key < $1.key }
            .map { CommitSlot(startsAt: $0.key.instant(in: zone), tier: $0.value) }
        // The last hour may have started while the calls above were out.
        if slots.isEmpty && !commitMaybeSaved { return .noHours }
        let trimmed = s.uplift.trimmingCharacters(in: .whitespacesAndNewlines)
        if !placeRead {
            place = await places?.here()
            placeRead = true
            guard !Task.isCancelled else { return .network }
        }
        let result = await store.commit(CommitRequest(
            windowId: windowId, slots: slots, motivation: motivation, vibes: s.vibes,
            uplift: trimmed.isEmpty ? nil : trimmed, place: place))
        if result == .network || result == .server { commitMaybeSaved = true }
        return result
    }

    // MARK: back and close

    /// The chevron and the swipe. Nothing is lost: back and forward keep the picks.
    func backPressed(_ step: CheckInStep, onBack: () -> Void) {
        guard onScreen(step) else { return }
        leaveStep()
        onBack()
    }

    /// The close. Discards the flow and lands on the gate. No confirm.
    func closePressed(_ step: CheckInStep, onClosed: () -> Void) {
        guard onScreen(step) else { return }
        analytics?.report(CheckInAnalytics.closed(step, windowId: windowId))
        leaveStep()
        discard()
        onClosed()
    }

    // MARK: internals

    /// The toast, for `toastSeconds`; a repeat restarts it.
    private func showToast(_ text: String) {
        toastTask?.cancel()
        state.toast = text
        state.toastTick += 1
        let nanoseconds = UInt64(toastSeconds * 1_000_000_000)
        toastTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: nanoseconds)
            guard !Task.isCancelled else { return }
            self?.state.toast = nil
        }
    }

    private func clearToast() {
        toastTask?.cancel()
        toastTask = nil
        if state.toast != nil { state.toast = nil }
    }

    private func leaveStep() {
        currentStep = nil
        clearToast()
    }

    /// Availability and the uplift are gone; the answers return to the server's on the next flow.
    private func discard() {
        flowAlive = false
        prefillPending = false
        filtersStandIn = false
        standInEdits = []
        standInAccepted = nil
        commitMaybeSaved = false
        var s = state
        s.picks = [:]
        s.uplift = ""
        s.motivation = nil
        s.vibes = []
        s.filters = nil
        s.noHoursDialog = false
        s.saving = false
        s.saveFailed = false
        s.serverContactError = false
        state = s
    }
}

private extension CallFailure {
    var commitResult: CommitResult { self == .network ? .network : .server }
}
