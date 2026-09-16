import Foundation

/// Every event the welcome and sign-up screens report.
///
/// A deliberate mirror of `SignUpAnalytics.kt`. The two catalogues are compared by
/// `audit/check-analytics-parity.py`, because an event added on one platform and forgotten on the
/// other is a funnel that silently reports half its traffic — and nothing fails.
///
/// WHERE THIS COMES FROM
///
/// Each entry is transcribed from the Tracking section of its ticket. Nothing is invented, and
/// where a ticket is ambiguous the ambiguity is recorded in `audit/CONFLICTS-2026-08-27.md` rather
/// than settled quietly.
///
///   SHOWUP-140  Startup / account creation
///   SHOWUP-142  Welcome back (re-login)
///   SHOWUP-143  Phone number and code entry
///   SHOWUP-144  Connect account
///   SHOWUP-145  Re-login SSO
///
/// Names are `snake_case` past tense, matching the backend's taxonomy (`date_confirmed`,
/// `like_sent`) and the tutorial's (`tutorial_card_viewed`). One taxonomy across client and server,
/// or the funnel has to be reassembled by hand in the warehouse.
enum SignUpAnalytics {

    // MARK: - Screenviews

    static let screenViewed = "screen_viewed"

    /// Screen names, verbatim from the tickets.
    ///
    /// They look inconsistent because they ARE inconsistent in the tickets — `Signup - welcomeback`
    /// is lower-case in SHOWUP-142. Normalising them would make this file disagree with the
    /// specification it implements.
    enum Screen {
        /// SHOWUP-140.
        static let createAccount = "Signup - CreateAccount"

        /// SHOWUP-142.
        ///
        /// NOTE: SHOWUP-145 specifies `SSOLogin` for what appears to be the same screen. We have
        /// one WelcomeBackView. Both are defined, this one is wired, and the conflict is recorded.
        static let welcomeBack = "Signup - welcomeback"

        /// SHOWUP-145. Defined, not currently wired — see `welcomeBack`.
        static let ssoLogin = "SSOLogin"

        /// SHOWUP-143, state A/B.
        static let phoneNumber = "Signup - Phonenumber"

        /// SHOWUP-143, state C/D.
        static let codeEntry = "Signup - Codeentry"

        /// SHOWUP-144. One screenview carrying a `state` property, not ten — see CONFLICTS A8.
        static let connectSSO = "ConnectSSO"
    }

    static func screenViewed(
        _ name: String,
        _ properties: [String: any Sendable] = [:]
    ) -> (String, [String: any Sendable]) {
        var merged = properties
        merged["screen_name"] = name
        return (screenViewed, merged)
    }

    // MARK: - SHOWUP-140 · Startup

    static let createAccountTapped = "signup_create_account_tapped"
    static let logInTapped = "signup_log_in_tapped"

    /// The three legal links, as one event with a `link` property.
    ///
    /// The tickets list them as three separate click events. One event carrying which link was
    /// tapped records the same information and matches every other property-bearing event here.
    static let legalLinkTapped = "legal_link_tapped"

    enum Legal {
        static let terms = "terms_and_conditions"
        static let privacy = "privacy_policy"
        static let legalNotice = "legal_notice"
    }

    static func legalLinkTapped(_ link: String, screen: String) -> (String, [String: any Sendable]) {
        (legalLinkTapped, ["link": link, "screen_name": screen])
    }

    // MARK: - SHOWUP-142 / 145 · Welcome back

    static let authMethodTapped = "signup_auth_method_tapped"
    static let getHelpTapped = "signup_get_help_tapped"
    static let useDifferentAccountTapped = "signup_use_different_account_tapped"

    static func authMethodTapped(
        method: String,
        isLastUsed: Bool
    ) -> (String, [String: any Sendable]) {
        (authMethodTapped, ["method": method, "is_last_used": isLastUsed])
    }

    // MARK: - SHOWUP-143 · Phone and code

    /// "Click event: Send me the code".
    static let phoneSubmitted = "signup_phone_submitted"

    /// "Number submitted · validation failed, with a `reason` property".
    ///
    /// THE REASON VOCABULARY DOES NOT MATCH OURS, AND THAT IS NOT RESOLVED HERE.
    ///
    /// SHOWUP-143 names three reasons: `too short`, `not a mobile`, `unsupported country`. Our
    /// validator produces seven outcomes, because it asks the phone metadata rather than measuring
    /// length. Only two overlap; `unsupported country` is unreachable, because there is no
    /// supported-country list and the metadata accepts every region.
    ///
    /// This reports OUR outcome. Reporting a reason the code cannot produce, or collapsing five
    /// distinct failures into one, would make the data describe something that did not happen.
    static let phoneValidationFailed = "signup_phone_validation_failed"

    /// "Click event: Verify code".
    static let codeSubmitted = "signup_code_submitted"

    /// "Code submitted · verify failed, with an `attempt` number".
    static let codeVerifyFailed = "signup_code_verify_failed"

    /// "Resend requested, with `seconds_waited` and whether it followed a mismatch".
    static let resendRequested = "signup_resend_requested"

    /// "Click event: Edit phone number".
    static let editPhoneTapped = "signup_edit_phone_tapped"

    static func phoneValidationFailed(
        reason: String,
        country: String
    ) -> (String, [String: any Sendable]) {
        (phoneValidationFailed, ["reason": reason, "country": country])
    }

    static func codeVerifyFailed(attempt: Int) -> (String, [String: any Sendable]) {
        (codeVerifyFailed, ["attempt": attempt])
    }

    static func resendRequested(
        secondsWaited: Int,
        afterMismatch: Bool
    ) -> (String, [String: any Sendable]) {
        (resendRequested, ["seconds_waited": secondsWaited, "after_mismatch": afterMismatch])
    }

    // MARK: - SHOWUP-144 · Connect account

    static let providerTapped = "connect_provider_tapped"
    static let skipTapped = "connect_skip_tapped"
    static let sheetDismissed = "connect_sheet_dismissed"
    static let linkSucceeded = "connect_link_succeeded"
    static let linkFailed = "connect_link_failed"

    /// The 8-second cap in SHOWUP-144's acceptance criteria. Separate from `linkFailed` even though
    /// it lands on the same state: a timeout and a refusal are different problems.
    static let linkingTimeout = "connect_linking_timeout"

    static let conflictRaised = "connect_conflict_raised"
    static let conflictResolveTapped = "connect_conflict_resolve_tapped"
    static let conflictDifferentAccountTapped = "connect_conflict_different_account_tapped"

    /// "repeat conflicts in one session". Carries `count`, so the second is distinguishable from
    /// the first without the warehouse having to sessionise.
    static let conflictRepeated = "connect_conflict_repeated"

    static func provider(_ event: String, _ provider: String) -> (String, [String: any Sendable]) {
        (event, ["provider": provider])
    }

    static func linkFailed(provider: String, kind: String) -> (String, [String: any Sendable]) {
        (linkFailed, ["provider": provider, "kind": kind])
    }

    static func conflictRepeated(
        count: Int,
        provider: String
    ) -> (String, [String: any Sendable]) {
        (conflictRepeated, ["count": count, "provider": provider])
    }

    /// Every event name this enum can emit, for the parity test and checker.
    static func allEventNames() -> [String] {
        [
            screenViewed,
            createAccountTapped,
            logInTapped,
            legalLinkTapped,
            authMethodTapped,
            getHelpTapped,
            useDifferentAccountTapped,
            phoneSubmitted,
            phoneValidationFailed,
            codeSubmitted,
            codeVerifyFailed,
            resendRequested,
            editPhoneTapped,
            providerTapped,
            skipTapped,
            sheetDismissed,
            linkSucceeded,
            linkFailed,
            linkingTimeout,
            conflictRaised,
            conflictResolveTapped,
            conflictDifferentAccountTapped,
            conflictRepeated,
        ]
    }
}

/// Records what it was asked to report, for tests.
///
/// A class rather than a struct because a value type would be copied into each view and the
/// recordings lost. `@unchecked Sendable` is banned here, so it is an actor-free reference type used
/// only from the main actor, which is where SwiftUI callbacks run.
@MainActor
final class RecordingAnalytics: AnalyticsTracking {
    struct Entry {
        let event: String
        let properties: [String: any Sendable]
    }

    private(set) var entries: [Entry] = []

    nonisolated init() {}

    nonisolated func track(_ event: String, properties: [String: any Sendable]) {
        // SwiftUI callbacks run on the main actor; this hop keeps the protocol's nonisolated
        // signature without needing an unchecked Sendable conformance.
        MainActor.assumeIsolated {
            entries.append(Entry(event: event, properties: properties))
        }
    }

    func names() -> [String] { entries.map(\.event) }

    func of(_ event: String) -> [Entry] { entries.filter { $0.event == event } }
}
