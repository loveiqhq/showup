//  DevCheckIn.swift
//  ShowUp · the 48hrs Check-In on an in-memory server, and every ticket frame on demand (debug only)
//
//  The Swift twin of `dev/DevCheckInStore.kt`, `DEV_STATES` in `dev/DevJump.kt`, and the debug-only
//  `CheckInGalleryActivity.kt`.
//
//  · `DevCheckInStore` stands in for `/me/check-in` so a signed-out developer-menu jump into the
//    Check-In walks the REAL host routing and the REAL model with only the server swapped -- every
//    screen, transition and failure state, on a phone with no backend and no account.
//  · `devStates` are the frames SHOWUP-178 to 183 name, plus How it works open, each opened over the
//    app in its exact state by the menu's States page. A picture, not a flow.
//
//  DEBUG BUILDS ONLY. The whole file is inside `#if DEBUG`, so a release build compiles none of it;
//  nothing here is product behaviour. The commit's rules are the server's (`check-in-flow.service.ts`),
//  mirrored loosely: idempotent per window, the contact-info check repeated.

#if DEBUG
import SwiftUI

/// How the fake commit answers -- the four ends the Uplift screen draws.
enum DevCommitMode: Sendable { case saves, network, server, contactInfo }

actor DevCheckInStore: CheckInStoring {
    /// Nanoseconds every call takes: long enough to see the saving state, short enough to walk.
    private let latency: UInt64
    private let commitMode: DevCommitMode
    private var state: CheckInState
    private var committedWindow: String?

    /// `active`: already committed -- a jump straight to Search, which the gate would otherwise take back.
    /// `dealbreakers`: Check-In 05b's switches shown, as the server's `filters_dealbreakers` will --
    /// `-dev_dealbreakers` in the launch arguments, as Android's `dev_dealbreakers` extra.
    init(latencyMs: Int = 900, commitMode: DevCommitMode = .saves, introPassed: Bool = false, active: Bool = false,
         dealbreakers: Bool = false) {
        latency = UInt64(max(latencyMs, 0)) * 1_000_000
        self.commitMode = commitMode
        state = CheckInState(
            windowId: UUID().uuidString.lowercased(),
            introPassed: introPassed,
            active: active,
            firstCheckIn: true,
            lastMotivation: nil,
            lastVibes: [],
            // The profile pre-fill a straight man would get: women, any age, 10 km.
            filters: CheckInFilters(genders: ["woman"]),
            filtersDealbreakers: dealbreakers)
    }

    func load() async -> Result<CheckInState, CallFailure> {
        try? await Task.sleep(nanoseconds: latency / 3)
        return .success(state)
    }

    func markIntroPassed() async -> Bool {
        state.introPassed = true
        return true
    }

    func saveAnswers(motivation: String?, vibes: Set<String>?) async -> Bool {
        if let motivation { state.lastMotivation = motivation }
        if let vibes { state.lastVibes = vibes }
        return true
    }

    func saveFilters(_ filters: CheckInFilters, withDealbreakers: Bool) async -> Result<Void, CallFailure> {
        // As the server does: a switch left out keeps what is stored.
        var next = filters
        if !withDealbreakers {
            next.ageDealbreaker = state.filters.ageDealbreaker
            next.distanceDealbreaker = state.filters.distanceDealbreaker
        }
        state.filters = next
        return .success(())
    }

    func commit(_ request: CommitRequest) async -> CommitResult {
        try? await Task.sleep(nanoseconds: latency)
        switch commitMode {
        case .network: return .network
        case .server: return .server
        case .contactInfo: return .contactInfo
        case .saves:
            // The server repeats the client's check.
            if upliftHasContact(request.uplift ?? "") { return .contactInfo }
            // Idempotent per window: a retry answers with the same saved state.
            if committedWindow != request.windowId { committedWindow = request.windowId }
            state.active = true
            state.firstCheckIn = false
            return .saved(state)
        }
    }
}

// MARK: - the States page

/// One frame a ticket names, opened directly in that exact state. `id` is what the gallery draws;
/// `ticket` groups the list.
struct DevState: Identifiable, Equatable, Sendable {
    let id: String
    let ticket: String
    let label: String
}

/// Every frame of SHOWUP-178 to 184's evidence, in ticket order -- plus the states between them.
/// Android's `DEV_STATES`, row for row.
let devStates: [DevState] = [
    DevState(id: "178A", ticket: "SHOWUP-178 Intro + gate", label: "A · Intro"),
    DevState(id: "178B", ticket: "SHOWUP-178 Intro + gate", label: "B · Date search not available"),
    DevState(id: "179A", ticket: "SHOWUP-179 Availability", label: "A · Today, hours picked"),
    DevState(id: "179B", ticket: "SHOWUP-179 Availability", label: "B · Today empty, No hours today?"),
    DevState(id: "179C", ticket: "SHOWUP-179 Availability", label: "C · Tomorrow empty, toast"),
    DevState(id: "179D", ticket: "SHOWUP-179 Availability", label: "D · Tomorrow, hours picked"),
    DevState(id: "179HIW", ticket: "SHOWUP-179 Availability", label: "How it works open"),
    DevState(id: "180A", ticket: "SHOWUP-180 Motivation", label: "A · Nothing selected"),
    DevState(id: "180B", ticket: "SHOWUP-180 Motivation", label: "B · Empty, toast"),
    DevState(id: "180C", ticket: "SHOWUP-180 Motivation", label: "C · One picked"),
    DevState(id: "181A", ticket: "SHOWUP-181 Vibe", label: "A · Nothing selected"),
    DevState(id: "181B", ticket: "SHOWUP-181 Vibe", label: "B · Empty, toast"),
    DevState(id: "181C", ticket: "SHOWUP-181 Vibe", label: "C · Three picked"),
    DevState(id: "181D", ticket: "SHOWUP-181 Vibe", label: "D · Five picked, max toast"),
    DevState(id: "181D-end", ticket: "SHOWUP-181 Vibe", label: "D · Scrolled to the end"),
    DevState(id: "182A", ticket: "SHOWUP-182 Filters", label: "A · Pre-filled"),
    DevState(id: "182B", ticket: "SHOWUP-182 Filters", label: "B · No gender, toast"),
    DevState(id: "182C", ticket: "SHOWUP-182 Filters", label: "C · Narrowed"),
    DevState(id: "183A", ticket: "SHOWUP-183 Uplift", label: "A · On entry, empty"),
    DevState(id: "183B", ticket: "SHOWUP-183 Uplift", label: "B · Typing"),
    DevState(id: "183C", ticket: "SHOWUP-183 Uplift", label: "C · Contact info, blocked"),
    DevState(id: "183D", ticket: "SHOWUP-183 Uplift", label: "D · Saving"),
    DevState(id: "183E", ticket: "SHOWUP-183 Uplift", label: "E · Save failed"),
    DevState(id: "184A", ticket: "SHOWUP-184 Search profile", label: "A · At rest, full profile"),
    DevState(id: "184B", ticket: "SHOWUP-184 Search profile", label: "B · Be sure"),
    DevState(id: "184C", ticket: "SHOWUP-184 Search profile", label: "C · Optional message"),
    DevState(id: "184D", ticket: "SHOWUP-184 Search profile", label: "D · Minimum content"),
    DevState(id: "184F", ticket: "SHOWUP-184 Search profile", label: "F · Actions coach"),
    DevState(id: "184-voice", ticket: "SHOWUP-184 Search profile", label: "Voice note playing"),
    DevState(id: "184-viewer", ticket: "SHOWUP-184 Search profile", label: "Photo viewer, photo 3"),
    DevState(id: "184-loading", ticket: "SHOWUP-184 Search profile", label: "Loading"),
    DevState(id: "184-failed", ticket: "SHOWUP-184 Search profile", label: "Could not load (offline)"),
    DevState(id: "184-empty", ticket: "SHOWUP-184 Search profile", label: "No results (proposed copy)"),
]

/// The tickets, in the order their frames are listed.
let devStateTickets: [String] = devStates.reduce(into: [String]()) { seen, state in
    if !seen.contains(state.ticket) { seen.append(state.ticket) }
}

/// The frame a States id draws -- a value, so `DevMenuRulesTests` can prove every menu row has one
/// without a view. Nil for an id with no frame, which the gallery shows LOUDLY, never as the intro.
enum DevFrame: Equatable, CaseIterable {
    case intro, gate
    case todayPicked, todayConfirm, tomorrowToast, tomorrowPicked, howItWorksOpen
    case motivationEmpty, motivationToast, motivationPicked
    case vibeEmpty, vibeToast, vibeThree, vibeFive, vibeFiveEnd
    case filtersPrefilled, filtersNoGender, filtersNarrowed
    case upliftEmpty, upliftTyping, upliftBlocked, upliftSaving, upliftFailed
    case searchRest, searchBeSure, searchMessage, searchMinimum, searchCoach
    case searchVoice, searchViewer, searchLoading, searchFailed, searchEmpty

    static func of(_ id: String) -> DevFrame? {
        switch id {
        case "178A": return .intro
        case "178B": return .gate
        case "179A": return .todayPicked
        case "179B": return .todayConfirm
        case "179C": return .tomorrowToast
        case "179D": return .tomorrowPicked
        case "179HIW": return .howItWorksOpen
        case "180A": return .motivationEmpty
        case "180B": return .motivationToast
        case "180C": return .motivationPicked
        case "181A": return .vibeEmpty
        case "181B": return .vibeToast
        case "181C": return .vibeThree
        case "181D": return .vibeFive
        case "181D-end": return .vibeFiveEnd
        case "182A": return .filtersPrefilled
        case "182B": return .filtersNoGender
        case "182C": return .filtersNarrowed
        case "183A": return .upliftEmpty
        case "183B": return .upliftTyping
        case "183C": return .upliftBlocked
        case "183D": return .upliftSaving
        case "183E": return .upliftFailed
        case "184A": return .searchRest
        case "184B": return .searchBeSure
        case "184C": return .searchMessage
        case "184D": return .searchMinimum
        case "184F": return .searchCoach
        case "184-voice": return .searchVoice
        case "184-viewer": return .searchViewer
        case "184-loading": return .searchLoading
        case "184-failed": return .searchFailed
        case "184-empty": return .searchEmpty
        default: return nil
        }
    }
}

/// The kit's clock, 4:20pm, so Today starts at 5pm as on every sheet.
private let kitDays = checkInDays(CivilDateTime(date: CivilDate(year: 2026, month: 10, day: 13), hour: 16, minute: 20))
private let fiveVibes: Set<String> = ["coffee_chat", "sunset", "deep_talk", "playful", "no_rush"]
private let typingText = "I have 2 tickets for the Berghain garden tonight and nobody to go with"
private let contactText = "Text me on 0176 2345 6789 and we'll grab a drink tonight"
private let writtenText = "I'll take you on a rafting tour on Wannsee — picnic included"

/// The Search profile at rest with one change -- a sheet, the coach, a playing card.
private func searchFrame(_ change: (inout SearchUiState) -> Void) -> SearchUiState {
    var s = SearchSamples.rest()
    change(&s)
    return s
}

/// The frame for one `devStates` id.
struct CheckInGalleryFrame: View {
    let state: String
    /// The back, the close and a dialog's dismiss all leave the frame, as on Android: left as the
    /// views' no-op defaults they were dead controls in a frame meant to be looked at.
    var leave: () -> Void = {}

    var body: some View {
        let today = gridOf(.today, kitDays)
        let tomorrow = gridOf(.tomorrow, kitDays)
        switch DevFrame.of(state) {
        case .intro?: CheckInIntroView(onClose: leave)
        case .gate?: CheckInGateView()
        case .todayPicked?:
            CheckInAvailabilityView(grid: today, picked: [18: .preferred, 19: .preferred, 20: .available], howItWorksOpen: false, onCancelNoToday: leave, onDismissNoToday: leave, onBack: leave, onClose: leave)
        case .todayConfirm?: CheckInAvailabilityView(grid: today, picked: [:], howItWorksOpen: false, dialogOpen: true, onCancelNoToday: leave, onDismissNoToday: leave, onBack: leave, onClose: leave)
        case .tomorrowToast?:
            CheckInAvailabilityView(grid: tomorrow, picked: [:], howItWorksOpen: false,
                                    toast: CheckInCopy.tomorrowToast, toastTick: 1, onCancelNoToday: leave, onDismissNoToday: leave, onBack: leave, onClose: leave)
        case .tomorrowPicked?:
            CheckInAvailabilityView(grid: tomorrow, picked: [12: .preferred, 13: .preferred, 19: .available], howItWorksOpen: false, onCancelNoToday: leave, onDismissNoToday: leave, onBack: leave, onClose: leave)
        case .howItWorksOpen?: CheckInAvailabilityView(grid: today, picked: [18: .preferred], howItWorksOpen: true, onCancelNoToday: leave, onDismissNoToday: leave, onBack: leave, onClose: leave)
        case .motivationEmpty?: CheckInMotivationView(selected: nil, onBack: leave, onClose: leave)
        case .motivationToast?: CheckInMotivationView(selected: nil, toast: CheckInCopy.motivationToast, toastTick: 1, onBack: leave, onClose: leave)
        case .motivationPicked?: CheckInMotivationView(selected: "long_term", onBack: leave, onClose: leave)
        case .vibeEmpty?: CheckInVibeView(picked: [], onBack: leave, onClose: leave)
        case .vibeToast?: CheckInVibeView(picked: [], toast: CheckInCopy.vibeToastEmpty, toastTick: 1, onBack: leave, onClose: leave)
        case .vibeThree?: CheckInVibeView(picked: ["coffee_chat", "playful", "no_rush"], onBack: leave, onClose: leave)
        case .vibeFive?: CheckInVibeView(picked: fiveVibes, toast: CheckInCopy.vibeToastMax, toastTick: 1, onBack: leave, onClose: leave)
        case .vibeFiveEnd?:
            CheckInVibeView(picked: fiveVibes, toast: CheckInCopy.vibeToastMax, toastTick: 1, groupsStartAtEnd: true, onBack: leave, onClose: leave)
        case .filtersPrefilled?: CheckInFiltersView(filters: CheckInFilters(genders: ["woman"]), onBack: leave, onClose: leave)
        case .filtersNoGender?:
            CheckInFiltersView(filters: CheckInFilters(genders: []), toast: CheckInCopy.filtersToast, toastTick: 1, onBack: leave, onClose: leave)
        case .filtersNarrowed?:
            CheckInFiltersView(filters: CheckInFilters(genders: ["woman", "non_binary"], minAge: 25, maxAge: 35, distanceKm: 15), onBack: leave, onClose: leave)
        case .upliftEmpty?: CheckInUpliftView(text: "", onBack: leave, onClose: leave)
        case .upliftTyping?: CheckInUpliftView(text: typingText, onBack: leave, onClose: leave)
        case .upliftBlocked?:
            CheckInUpliftView(text: contactText, blocked: true, toast: CheckInCopy.contactError, toastTick: 1, onBack: leave, onClose: leave)
        case .upliftSaving?: CheckInUpliftView(text: writtenText, saving: true, onBack: leave, onClose: leave)
        case .upliftFailed?: CheckInUpliftView(text: writtenText, saveFailed: true, onBack: leave, onClose: leave)
        case .searchRest?: SearchProfileView(state: SearchSamples.rest(), zone: SearchSamples.zone)
        case .searchBeSure?:
            SearchProfileView(state: searchFrame { $0.sheet = .confirm }, zone: SearchSamples.zone, onCancelConfirm: leave)
        case .searchMessage?:
            SearchProfileView(state: searchFrame { $0.sheet = .message; $0.draft = messageDraft("Leonie", "today") },
                              zone: SearchSamples.zone, onDismissMessage: leave)
        case .searchMinimum?: SearchProfileView(state: SearchSamples.rest(SearchSamples.minimum), zone: SearchSamples.zone)
        case .searchCoach?:
            SearchProfileView(state: searchFrame { $0.coach = true }, zone: SearchSamples.zone, onCoachDone: { _, _ in leave() })
        case .searchVoice?:
            SearchProfileView(state: searchFrame { $0.playback = SearchPlayback(kind: .voice, positionMs: 6_000, durationMs: 14_000) },
                              zone: SearchSamples.zone)
        case .searchViewer?:
            SearchProfileView(state: searchFrame { $0.viewerAt = 2 }, zone: SearchSamples.zone, onCloseViewer: leave)
        case .searchLoading?: SearchProfileView(state: SearchUiState(), zone: SearchSamples.zone)
        case .searchFailed?:
            SearchProfileView(state: { var s = SearchUiState(); s.loading = false; s.failure = .network; return s }(),
                              zone: SearchSamples.zone)
        case .searchEmpty?:
            SearchProfileView(state: { var s = SearchUiState(); s.loading = false; return s }(), zone: SearchSamples.zone)
        case nil:
            // Loud, never a quiet fallback: a menu entry with no frame must be seen.
            Text("Unknown state \(state)")
                .font(F.manrope(15, .bold))
                .foregroundColor(.liqDanger)
        }
    }
}

/// A frame over the app, with a way back: the States page's full-screen cover.
struct DevStateCover: View {
    let state: DevState
    let onClose: () -> Void

    var body: some View {
        // The close sits where the DEV tab does -- on the right edge, inside the gutter -- so it
        // covers no control of the frame.
        ZStack(alignment: .trailing) {
            CheckInGalleryFrame(state: state.id, leave: onClose)
            Button(action: onClose) {
                Text("C\nL\nO\nS\nE")
                    .font(F.manrope(9, .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .frame(width: Spacing.screenGutter - Spacing.sm, height: ComponentSizes.controlHeight * 1.4)
                    .background(
                        UnevenRoundedRectangle(topLeadingRadius: Radius.errorBox, bottomLeadingRadius: Radius.errorBox)
                            .fill(Color.liqFg.opacity(0.82))
                    )
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("Close the frame \(state.id)"))
        }
    }
}
#endif
