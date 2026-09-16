import Foundation
import Sentry

/// Crash and error reporting, off unless it is switched on and given a destination.
///
/// WHY THE SWITCH, AND WHY OFF IS THE DEFAULT
///
/// The same shape as the backend's `initSentry` (Epic 16) and Android's `Crashes`: with reporting
/// disabled, or with no DSN, nothing is started and nothing is ever sent — so the app builds and
/// runs with no account, no credentials and no calls to a third party. Anyone can clone this
/// repository and build it without asking for a key.
///
/// WHAT IS DELIBERATELY NOT COLLECTED
///
/// A crash reporter is a pipe off the device, and this is a dating app: the data within reach of a
/// stack trace includes phone numbers, one-time codes, coordinates and message text. So:
///
/// - `sendDefaultPii` is off, so the SDK never attaches an IP address or device identifiers of its
///   own accord.
/// - `beforeSend` redacts any field whose NAME is prohibited, using the same list as the backend and
///   Android. Names, not values — a value-based check cannot recognise a phone number in a format it
///   has not seen.
/// - breadcrumbs are dropped entirely. They are the most useful feature here and the most dangerous:
///   they capture UI interactions and network URLs automatically, which on the phone screen means
///   the number being typed. Reinstate only with an explicit allowlist.
/// - `enableAutoSessionTracking` is off, because release health needs an installation identifier.
/// - screenshots and view hierarchies are off. Both would capture a half-typed phone number, and
///   both are opt-in features that exist precisely because they are so useful — which is why they
///   have to be turned down explicitly rather than left at whatever the SDK defaults to next.
enum Crashes {

    /// Starts reporting if configured, and returns whether it did.
    ///
    /// - Parameter start: injectable so the gating is testable without launching the real SDK, the
    ///   same way the backend's version is.
    @discardableResult
    static func start(
        enabled: Bool,
        dsn: String,
        environment: String,
        release: String,
        start: (Options) -> Void = { SentrySDK.start(options: $0) }
    ) -> Bool {
        guard enabled, !dsn.trimmingCharacters(in: .whitespaces).isEmpty else { return false }

        let options = Options()
        configure(options, dsn: dsn, environment: environment, release: release)
        start(options)
        return true
    }

    /// Applies every setting.
    ///
    /// Separate from ``start(enabled:dsn:environment:release:start:)`` on purpose: this is the part
    /// with the rules in it, and a rule nobody can test cheaply is a rule that quietly stops being
    /// true.
    static func configure(
        _ options: Options,
        dsn: String,
        environment: String,
        release: String
    ) {
        options.dsn = dsn
        options.environment = environment
        options.releaseName = release

        options.sendDefaultPii = false
        options.enableAutoSessionTracking = false
        options.maxBreadcrumbs = 0
        options.enableUserInteractionTracing = false
        options.attachScreenshot = false
        options.attachViewHierarchy = false

        options.beforeSend = { event in Crashes.scrub(event) }
    }

    /// Redacts prohibited fields from an event.
    ///
    /// Returns the event rather than nil: dropping the whole report would lose the crash as well as
    /// the personal data, and the crash is the reason any of this exists.
    static func scrub(_ event: Event) -> Event {
        // A user object here would carry an id, an email or an IP. None of it is needed to fix a
        // crash, and all of it is personal data leaving the device.
        event.user = nil

        if let extra = event.extra {
            event.extra = extra.reduce(into: [String: Any]()) { out, pair in
                out[pair.key] = ProhibitedFields.isProhibited(pair.key) ? redacted : pair.value
            }
        }

        if let tags = event.tags {
            event.tags = tags.reduce(into: [String: String]()) { out, pair in
                out[pair.key] = ProhibitedFields.isProhibited(pair.key) ? redacted : pair.value
            }
        }

        // Breadcrumbs are configured off, but an SDK integration can still add one before this
        // runs, so they are cleared here too rather than trusted to stay empty.
        event.breadcrumbs = []

        return event
    }

    static let redacted = "[redacted]"
}

/// Whether this build reports crashes, and where to.
///
/// Both are compile-time constants and both are off in the repository, so no credential is
/// committed and the app builds for anyone. The release pipeline supplies them when there is one,
/// and the DSN must be an EU-region ingest host.
enum CrashReporting {
    static let enabled = false
    static let dsn = ""
}
