//  DevMenu.swift
//  ShowUp · the developer menu: jump to any screen, watch every event as it fires
//
//  Android twin: `dev/DevJump.kt` and `dev/DevMenu.kt`.
//
//  DEBUG BUILDS ONLY. The whole file is inside `#if DEBUG`, so a release build does not compile any
//  of it — a store build cannot contain it. Plain on purpose: it is a tool, not a screen, drawn from
//  the design-system tokens rather than from any design, and it is not in ScreenFitTest.

#if DEBUG
import SwiftUI

// MARK: - The rules

/// The menu's groups, in flow order — the same groups the flow itself has.
enum DevSection: String, CaseIterable {
    case signUp = "Sign-up"
    case tutorial = "Tutorial"
    case basics = "The basics"
    case realYou = "The real you"
    case reachable = "Reachability and location"
    case details = "Share some details"
    case checkIn = "48hrs Check-In"
    case home = "Home"
}

extension FlowScreen {
    /// Where a screen sits in the menu.
    ///
    /// EXHAUSTIVE ON PURPOSE. A screen added to `FlowScreen` does not compile until it has a section
    /// here, which is what makes "new screens show up in the list automatically" true: the list is
    /// `FlowScreen.allCases`, and this is the one decision a new screen forces.
    var devSection: DevSection {
        switch self {
        case .signUp: return .signUp
        case .tutorialWelcome, .meetInRealLife, .matchOnAvailability, .matchMeansMeet,
             .thirtyMinutes, .showUpEveryTime:
            return .tutorial
        case .profileName, .profileEmail, .profileVerifyEmail, .profileDob, .profileEmbrace:
            return .basics
        case .profilePhotos, .profilePrompts, .profileMedia:
            return .realYou
        case .profileNotifications, .profileReachability, .profileLocation:
            return .reachable
        case .profileEmbraceDetails, .profileHeight, .profileGender, .profileOrientation,
             .profileDatingLanguage, .profileEducation, .profileReligion, .profilePolitics,
             .profileInterests, .profileLifeNow, .profileLifeAhead:
            return .details
        case .checkInGate, .checkInIntro, .checkInToday, .checkInTomorrow, .checkInMotivation,
             .checkInVibe, .checkInFilters, .checkInUplift, .checkInSearch, .searchFilters, .likesReceived,
             .checkInSettings:
            return .checkIn
        case .home: return .home
        }
    }

    /// The name a person would use for a screen. Exhaustive for the same reason as `devSection`.
    var devLabel: String {
        switch self {
        case .signUp: return "Startup (create account / log in)"
        case .tutorialWelcome: return "1 · Welcome"
        case .meetInRealLife: return "2 · Meet in real life"
        case .matchOnAvailability: return "3 · Match on availability"
        case .matchMeansMeet: return "4 · A match means a meet"
        case .thirtyMinutes: return "5 · Thirty minutes"
        case .showUpEveryTime: return "6 · Show up every time"
        case .profileName: return "First name"
        case .profileEmail: return "Email"
        case .profileVerifyEmail: return "Verify email (code)"
        case .profileDob: return "Date of birth"
        case .profileEmbrace: return "Bridge into The real you"
        case .profilePhotos: return "Photos"
        case .profilePrompts: return "Prompts"
        case .profileMedia: return "Video and voice"
        case .profileNotifications: return "Notifications ask"
        case .profileReachability: return "Stay reachable"
        case .profileLocation: return "Location"
        case .profileEmbraceDetails: return "Embrace 2 (bridge into details)"
        case .profileHeight: return "Height"
        case .profileGender: return "Gender"
        case .profileOrientation: return "Orientation"
        case .profileDatingLanguage: return "Dating language"
        case .profileEducation: return "Education"
        case .profileReligion: return "Religion"
        case .profilePolitics: return "Politics"
        case .profileInterests: return "Interests"
        case .profileLifeNow: return "Life (current)"
        case .profileLifeAhead: return "Future (last step)"
        case .checkInGate: return "Date search not available (gate)"
        case .checkInIntro: return "Intro"
        case .checkInToday: return "1 · Today"
        case .checkInTomorrow: return "2 · Tomorrow"
        case .checkInMotivation: return "3 · Motivation"
        case .checkInVibe: return "4 · Vibe"
        case .checkInFilters: return "5 · Filters"
        case .checkInUplift: return "6 · Uplift + Find my date"
        case .checkInSearch: return "Search 01 · Profile"
        case .searchFilters: return "Search · Filters"
        case .likesReceived: return "Likes tab (Premium)"
        case .checkInSettings: return "Settings (placeholder)"
        case .home: return "Home (placeholder)"
        }
    }

    /// Whether the screen reads or writes the account. Signed out, every save on it fails.
    var needsAccount: Bool {
        switch devSection {
        case .basics, .realYou, .reachable, .details, .checkIn: return true
        case .signUp, .tutorial, .home: return false
        }
    }
}

/// Where a jump really lands.
enum DevJump: Equatable {
    /// Exactly where it was asked.
    case lands(FlowScreen)
    /// The app skips this screen on this phone, so the jump lands where the walk would — "behave
    /// like real", decided 8 October 2026 — and the menu says why.
    case skipped(lands: FlowScreen, why: String)
    /// Signed out, and the screen needs an account.
    case needsLogIn
    /// A Check-In screen, signed out: it opens on the in-memory test server (`DevCheckInStore`)
    /// instead of refusing, so the whole flow can be looked at and walked with no backend and no
    /// account.
    case testServer(FlowScreen)
}

/// Where a jump to `target` lands, from the same facts the walk decides on: `shouldShowAsk` for the
/// notification ask, and the location arrival matrix. iOS has no "older OS never asks" case.
func devJump(_ target: FlowScreen, signedIn: Bool, notificationsAsk: Bool,
             location: LocationArrival) -> DevJump {
    if target.devSection == .checkIn && !signedIn { return .testServer(target) }
    if target.needsAccount && !signedIn { return .needsLogIn }
    switch target {
    case .profileNotifications:
        return notificationsAsk
            ? .lands(target)
            : .skipped(lands: .profileReachability, why: "Skipped: already answered on this phone")
    case .profileLocation:
        switch location {
        case .skip: return .skipped(lands: .profileEmbraceDetails, why: "Skipped: location already allowed")
        case .show: return .lands(target)
        }
    default:
        return .lands(target)
    }
}

/// A property's value as the log shows it. A key on the prohibited list — email, phone, a code, a
/// token — is withheld even here, except `ownKeys`: names that list holds for crash reports and
/// that mean something harmless in this app's events (`card` is the tutorial card number).
func devShownValue(_ key: String, _ value: any Sendable) -> String {
    if !devOwnKeys.contains(key) && ProhibitedFields.isProhibited(key) { return "[withheld]" }
    return "\(value)"
}

private let devOwnKeys: Set<String> = ["card"]

// MARK: - The menu

/// Everything the menu shows, decided by the host.
struct DevMenuFacts {
    let current: FlowScreen
    /// Nil while it is still being read.
    let signedIn: Bool?
    let notificationsAsk: Bool
    let location: LocationArrival
    /// The account's plan, as Search last read it -- `plan_tier`, Freemium until payments exist.
    var premium: Bool = false
}

/// The small "DEV" tab on the right edge.
///
/// NARROWER THAN THE GUTTER, so it can never cover a control: on Android a horizontal pill sat on
/// the gender step's last radio. Every screen keeps `Spacing.screenGutter` clear at its edges; this
/// stays inside that strip, with the letters stacked.
struct DevTab: View {
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            Text("D\nE\nV")
                .font(F.manrope(9, .bold))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .frame(width: Spacing.screenGutter - Spacing.sm, height: ComponentSizes.controlHeight)
                .background(
                    UnevenRoundedRectangle(topLeadingRadius: Radius.errorBox, bottomLeadingRadius: Radius.errorBox)
                        .fill(Color.liqFg.opacity(0.82))
                )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Developer menu")
    }
}

struct DevMenu: View {
    let facts: DevMenuFacts
    let events: [EventLog.Entry]
    let onJump: (FlowScreen) -> Void
    /// A Check-In screen opened on the in-memory test server -- signed out, see `DevJump.testServer`.
    let onTestServer: (FlowScreen) -> Void
    /// One ticket frame, opened in its exact state -- the States page.
    let onOpenState: (DevState) -> Void
    let onClearEvents: () -> Void
    let onHideTab: () -> Void
    let onClose: () -> Void
    /// The plan switch: the server's dev-only route, a 404 in production.
    var onPremium: (Bool) -> Void = { _ in }

    private enum Page { case screens, states, events }
    @State private var page = Page.screens

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Developer menu")
                    .font(F.manrope(20, .bold)).foregroundColor(.liqFg)
                    .accessibilityAddTraits(.isHeader)
                Spacer()
                Button("Close", action: onClose)
                    .font(F.manrope(14, .bold)).foregroundColor(.liqPurple)
                    .frame(minWidth: ComponentSizes.minTapTarget, minHeight: ComponentSizes.minTapTarget)
            }
            .padding(.top, Spacing.xl)
            Text("Only in builds run from Xcode. App Store builds do not contain it.")
                .font(F.manrope(12, .regular)).foregroundColor(.liqMuted)
            HStack(spacing: Spacing.md) {
                pageTab("Screens", selected: page == .screens) { page = .screens }
                pageTab("States", selected: page == .states) { page = .states }
                pageTab("Events (\(events.count))", selected: page == .events) { page = .events }
            }
            .padding(.vertical, Spacing.lg)
            switch page {
            case .screens: screensPage
            case .states: statesPage
            case .events: eventsPage
            }
        }
        .padding(.horizontal, Spacing.screenGutter)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.liqCream.ignoresSafeArea())
    }

    private var screensPage: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                Text(signedInText)
                    .font(F.manrope(14, .semibold)).foregroundColor(.liqFg)
                Button(action: onHideTab) {
                    Text("Hide the DEV tab until the app is next opened")
                        .font(F.manrope(13, .semibold)).foregroundColor(.liqPurple)
                        .frame(minHeight: ComponentSizes.minTapTarget)
                }
                // The account's plan, until payments exist (decided 21 Oct 2026). Signed in only: it
                // is a write to this account on this server.
                if facts.signedIn == true {
                    Toggle(isOn: Binding(get: { facts.premium }, set: { onPremium($0) })) {
                        Text("Premium plan (date search)").font(F.manrope(13, .semibold)).foregroundColor(.liqMuted)
                    }
                    .tint(.liqPurple)
                    .frame(minHeight: ComponentSizes.minTapTarget)
                }
                ForEach(DevSection.allCases, id: \.self) { section in
                    Text(section.rawValue.uppercased())
                        .font(F.manrope(11, .bold)).foregroundColor(.liqSubtle)
                        .padding(.top, Spacing.xl).padding(.bottom, Spacing.sm)
                        .accessibilityAddTraits(.isHeader)
                    ForEach(FlowScreen.allCases.filter { $0.devSection == section }, id: \.self) { screen in
                        row(screen)
                    }
                }
            }
            .padding(.bottom, Spacing.xxl)
        }
    }

    private var signedInText: String {
        switch facts.signedIn {
        case nil: return "Checking sign-in..."
        case true?: return "Signed in. Every screen saves to your real account."
        case false?: return "Not signed in. Log in on Startup to reach the profile screens."
        }
    }

    private func row(_ screen: FlowScreen) -> some View {
        let here = screen == facts.current
        let jump: DevJump? = (facts.signedIn == nil && screen.needsAccount)
            ? nil
            : devJump(screen, signedIn: facts.signedIn == true,
                      notificationsAsk: facts.notificationsAsk, location: facts.location)
        let note: String? = {
            if here { return "You are here" }
            switch jump {
            case nil: return "Checking sign-in..."
            case .needsLogIn?: return "Log in first"
            case .skipped(_, let why)?: return why
            case .testServer?: return "Opens on a test server -- no account, nothing saved"
            case .lands?: return nil
            }
        }()
        let enabled = !here && jump != nil && jump != .needsLogIn
        return Button {
            if case .testServer? = jump { onTestServer(screen) } else { onJump(screen) }
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text(screen.devLabel)
                    .font(F.manrope(15, .semibold))
                    .foregroundColor(enabled || here ? .liqFg : .liqSubtle)
                if let note {
                    Text(note).font(F.manrope(12, .regular)).foregroundColor(here ? .liqPurple : .liqMuted)
                }
            }
            .frame(maxWidth: .infinity, minHeight: ComponentSizes.minTapTarget, alignment: .leading)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.md)
            .background(RoundedRectangle(cornerRadius: Radius.errorBox)
                .fill(here ? Color.liqSelectedRowWash : .clear))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    /// Every ticket frame, grouped by ticket, each opened in its exact state over the app.
    private var statesPage: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                Text("Open any ticket frame in its exact state. Close returns here.")
                    .font(F.manrope(14, .semibold)).foregroundColor(.liqFg)
                ForEach(devStateTickets, id: \.self) { ticket in
                    Text(ticket.uppercased())
                        .font(F.manrope(11, .bold)).foregroundColor(.liqSubtle)
                        .padding(.top, Spacing.xl).padding(.bottom, Spacing.sm)
                        .accessibilityAddTraits(.isHeader)
                    ForEach(devStates.filter { $0.ticket == ticket }) { state in
                        Button { onOpenState(state) } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(state.label).font(F.manrope(15, .semibold)).foregroundColor(.liqFg)
                                Text(state.id).font(F.manrope(12, .regular)).foregroundColor(.liqMuted)
                            }
                            .frame(maxWidth: .infinity, minHeight: ComponentSizes.minTapTarget, alignment: .leading)
                            .padding(.horizontal, Spacing.md)
                            .padding(.vertical, Spacing.md)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(.bottom, Spacing.xxl)
        }
    }

    private var eventsPage: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            HStack {
                Text("Newest first. Nothing is sent anywhere: no analytics tool is connected yet.")
                    .font(F.manrope(12, .regular)).foregroundColor(.liqMuted)
                Spacer()
                Button("Clear", action: onClearEvents)
                    .font(F.manrope(14, .bold)).foregroundColor(.liqPurple)
                    .frame(minWidth: ComponentSizes.minTapTarget, minHeight: ComponentSizes.minTapTarget)
            }
            if events.isEmpty {
                Text("No events yet. Tap through the app and every event appears here as it fires.")
                    .font(F.manrope(14, .regular)).foregroundColor(.liqFg)
                    .padding(.top, Spacing.xl)
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: Spacing.md) {
                        ForEach(events) { entry in eventCard(entry) }
                    }
                    .padding(.bottom, Spacing.xxl)
                }
            }
        }
    }

    private func eventCard(_ entry: EventLog.Entry) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(entry.event).font(.system(size: 13, weight: .bold, design: .monospaced))
                    .foregroundColor(.liqFg)
                Spacer()
                Text(entry.at, format: .dateTime.hour().minute().second())
                    .font(.system(size: 11, design: .monospaced)).foregroundColor(.liqSubtle)
            }
            ForEach(entry.properties.keys.sorted(), id: \.self) { key in
                Text("\(key): \(devShownValue(key, entry.properties[key] ?? ""))")
                    .font(.system(size: 12, design: .monospaced)).foregroundColor(.liqMuted)
            }
        }
        .padding(Spacing.lg)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Radius.errorBox).fill(Color.liqElevated))
    }

    private func pageTab(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(F.manrope(13, .semibold))
                .foregroundColor(selected ? .white : .liqFg)
                .padding(.horizontal, Spacing.xl)
                .frame(minHeight: ComponentSizes.minTapTarget)
                .background(Capsule().fill(selected ? Color.liqFg : Color.liqElevated))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
#endif
