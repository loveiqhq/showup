import XCTest
import Sentry
@testable import ShowUpWelcome

/// Verifies the two things that matter about crash reporting: that it stays off unless configured,
/// and that when it is on it cannot carry personal data off the device.
///
/// The Sentry SDK is never started. `Crashes.start` takes an injectable starter for exactly this
/// reason, so the gating is testable with no DSN, no network and no account — the same shape the
/// backend and Android use.
///
/// Deliberately mirrors `CrashesTest.kt` assertion for assertion. Where the two platforms disagree
/// about what is redacted, one of them has a hole, and comparing the tests is how that gets noticed.
final class CrashesTests: XCTestCase {

    private func configured(
        dsn: String = "https://key@example.ingest.de.sentry.io/1",
        environment: String = "production",
        release: String = "org.loveiq.showup@0.1"
    ) -> Options {
        let options = Options()
        Crashes.configure(options, dsn: dsn, environment: environment, release: release)
        return options
    }

    // MARK: - The switch

    func testDisabledMeansNothingIsStarted() {
        var started = false
        let result = Crashes.start(
            enabled: false,
            dsn: "https://key@example.ingest.de.sentry.io/1",
            environment: "test",
            release: "test@1",
            start: { _ in started = true }
        )

        XCTAssertFalse(result, "must not report when switched off")
        XCTAssertFalse(started, "the SDK must not even be started")
    }

    func testNoDSNMeansNothingIsStarted() {
        // The committed default. A release pipeline could flip the flag on before a DSN exists, and
        // that must be inert rather than a crash on launch.
        var started = false
        let result = Crashes.start(
            enabled: true, dsn: "", environment: "test", release: "test@1",
            start: { _ in started = true }
        )

        XCTAssertFalse(result)
        XCTAssertFalse(started)
    }

    func testABlankDSNIsTreatedAsNoDSN() {
        var started = false
        let result = Crashes.start(
            enabled: true, dsn: "   ", environment: "test", release: "test@1",
            start: { _ in started = true }
        )

        XCTAssertFalse(result)
        XCTAssertFalse(started)
    }

    func testTheRepositoryDefaultIsOff() {
        // Guards the thing that keeps this repository buildable by anyone: no credential committed,
        // and no reporting without one. Change this test deliberately, never incidentally.
        XCTAssertFalse(CrashReporting.enabled)
        XCTAssertTrue(CrashReporting.dsn.isEmpty)
    }

    func testEnabledWithADSNStartsWithPIICollectionOff() {
        var startedWith: Options?
        let result = Crashes.start(
            enabled: true,
            dsn: "https://key@example.ingest.de.sentry.io/1",
            environment: "production",
            release: "org.loveiq.showup@0.1",
            start: { startedWith = $0 }
        )

        XCTAssertTrue(result)
        let options = try? XCTUnwrap(startedWith)
        XCTAssertNotNil(options)

        // Each of these is a separate route by which the SDK would otherwise send personal data.
        XCTAssertFalse(options?.sendDefaultPii ?? true, "must not attach IP or device identifiers")
        XCTAssertFalse(options?.enableAutoSessionTracking ?? true, "needs an installation identifier")
        XCTAssertEqual(options?.maxBreadcrumbs, 0, "breadcrumbs capture typed input and URLs")
        XCTAssertFalse(options?.attachScreenshot ?? true, "a screenshot captures a typed number")
        XCTAssertFalse(options?.attachViewHierarchy ?? true)
    }

    func testTheInstalledBeforeSendHookActuallyScrubs() {
        // Asserting the hook exists is not the same as asserting it does anything. This runs the
        // real callback the SDK would run, on an event carrying a phone number.
        let options = configured()
        let hook = options.beforeSend
        XCTAssertNotNil(hook)

        let event = Event()
        event.extra = ["phone": "+4917612345678"]

        let out = hook?(event)
        XCTAssertEqual(out?.extra?["phone"] as? String, Crashes.redacted)
    }

    // MARK: - The scrubber

    func testAUserObjectIsRemovedEntirely() {
        let event = Event()
        let user = User()
        user.email = "someone@example.com"
        user.ipAddress = "203.0.113.4"
        event.user = user

        // None of it is needed to fix a crash, and all of it is personal data leaving the device.
        XCTAssertNil(Crashes.scrub(event).user)
    }

    func testProhibitedExtrasAreRedactedAndSafeLookAlikesSurvive() {
        let event = Event()
        event.extra = [
            "phone": "+4917612345678",
            "code": "123456",
            "refreshToken": "secret-value",
            "screen": "PhoneVerification",
            "age_band": "25-34",
            "error_code": "step_up_required",
            "token_type": "Bearer",
        ]

        let extra = Crashes.scrub(event).extra

        XCTAssertEqual(extra?["phone"] as? String, Crashes.redacted)
        XCTAssertEqual(extra?["code"] as? String, Crashes.redacted)
        XCTAssertEqual(extra?["refreshToken"] as? String, Crashes.redacted)
        // The look-alikes must NOT be redacted. A scrubber that eats everything useful is one
        // somebody eventually switches off, which is worse than a narrower one that stays on.
        XCTAssertEqual(extra?["screen"] as? String, "PhoneVerification")
        XCTAssertEqual(extra?["age_band"] as? String, "25-34")
        XCTAssertEqual(extra?["error_code"] as? String, "step_up_required")
        XCTAssertEqual(extra?["token_type"] as? String, "Bearer")
    }

    func testProhibitedTagsAreRedactedToo() {
        let event = Event()
        event.tags = ["email": "someone@example.com", "build": "release"]

        let tags = Crashes.scrub(event).tags
        XCTAssertEqual(tags?["email"], Crashes.redacted)
        XCTAssertEqual(tags?["build"], "release")
    }

    func testNamingStyleCannotSlipAFieldPastTheScrubber() {
        // The reason matching is on the normalised name: an SDK integration or a future call site
        // may use any casing or separator it likes.
        for variant in ["Phone", "phone_number", "PHONE-NUMBER", "phoneNumber", "e_mail"] {
            let event = Event()
            event.extra = [variant: "sensitive"]
            let extra = Crashes.scrub(event).extra
            XCTAssertEqual(extra?[variant] as? String, Crashes.redacted, "\(variant) should be redacted")
        }
    }

    func testNormalisationStripsNonASCIIToMatchTheOtherPlatforms() {
        // Swift's isLetter accepts `ü`; the backend strips with [^a-z0-9] and Android with an
        // 'a'..'z' range. If this ever diverges, one platform redacts a field the others let
        // through — which is exactly what the shared list exists to prevent.
        XCTAssertEqual(ProhibitedFields.normalise("phöne"), "phne")
        XCTAssertEqual(ProhibitedFields.normalise("PHONE-NUMBER"), "phonenumber")
    }

    func testBreadcrumbsAreClearedEvenIfAnIntegrationAddedOne() {
        let event = Event()
        event.breadcrumbs = [Breadcrumb(level: .info, category: "ui.click")]

        XCTAssertTrue(Crashes.scrub(event).breadcrumbs?.isEmpty ?? true)
    }

    func testAnEventWithNothingInItSurvivesScrubbing() {
        // The commonest real event: a stack trace with no extras and no tags. Scrubbing must not
        // crash on the nils, or the scrubber turns every crash into a lost crash.
        XCTAssertNil(Crashes.scrub(Event()).user)
    }
}
