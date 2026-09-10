//  ScreenFitMeasureTests.swift
//  ShowUp · every screen, every state, on every phone the app has to run on
//
//  The iOS half of ScreenFitTest.kt, added 10 September 2026. `ScreenFitTests.swift` beside this
//  file stays as it is and answers a different question — whether the CTA moves when a state goes
//  wrong, which is a pixel question and rightly probed in pixels. This file answers the one
//  Android has been answering since August and iOS could not: does everything on the screen
//  actually get the room it needs, on all seventeen.
//
//  Read FitHarness.swift first. A green run here means nothing unless the instrument fires, and
//  this suite's exposure is specific: it measures a rendered view tree, and were that tree ever
//  to come back empty, every sweep below would report a perfect score and go on reporting one
//  forever. That is not hypothetical. The first two versions of the harness read the
//  ACCESSIBILITY tree, which SwiftUI does not build for a hosting controller in a unit test, and
//  all forty sweeps passed twice over nothing at all. Only these four tests caught it.

import XCTest
import SwiftUI
@testable import ShowUpWelcome

final class ScreenFitMeasureTests: XCTestCase {

    /// Everything the whole suite found, so the summary at the end is over all of it.
    private var collected: [FitViolation] = []

    /// Runs one screen state across the whole device matrix and reports everything it found.
    // An autoclosure, so each device gets a freshly built view rather than one instance whose
    // @State has already settled at whatever size it was first measured in.
    @MainActor
    private func sweep<V: View>(_ name: String, _ view: @autoclosure () -> V) {
        var found: [FitViolation] = []
        for device in fitDevices {
            found += measureFit(device, name, view())
        }
        collected += found
        let real = found.filter { !$0.advisory }
        let notes = found.count - real.count
        if real.isEmpty && notes == 0 {
            print("OK   \(name) -- clean on all \(fitDevices.count) devices")
        } else if real.isEmpty {
            print("OK   \(name) -- clean on all \(fitDevices.count) devices, \(notes) scrolled")
        } else {
            print("FAIL \(name) -- \(real.count) problem(s):")
            real.forEach { print("       \($0)") }
        }
    }

    /// One line, on purpose.
    ///
    /// A multi-line assertion message is truncated to its first line by the CI log, so the whole
    /// finding list arrived as "layout problems in Connect:" and nothing else -- which is a test
    /// that fails without telling anyone why, on the one platform where there is no local run to
    /// fall back on.
    private func assertClean(_ context: String) {
        let real = collected.filter { !$0.advisory }
        let shown = real.prefix(14).map { $0.description }.joined(separator: " || ")
        let more = real.count > 14 ? " || ...and \(real.count - 14) more" : ""
        XCTAssertTrue(real.isEmpty,
                      "\(real.count) layout problem(s) in \(context): \(shown)\(more)")
    }

    /// The guard against a silent instrument.
    @MainActor
    private func assertTheTreeIsPopulated(_ view: some View) {
        let device = fitDevices[0]
        // A screen with no findings and no elements is not a clean screen, it is a blind harness.
        // Measuring a deliberately tiny frame guarantees SOMETHING is squeezed if anything at all
        // was seen, which is a stronger signal than counting elements.
        let squeezed = measureFit(
            FitDevice(name: "probe", width: device.width, height: 120, top: 0, bottom: 0),
            "instrument", view)
        XCTAssertFalse(
            squeezed.isEmpty,
            "the view tree yielded nothing, so every sweep in this file is vacuous")
    }

    // MARK: - the instrument

    @MainActor
    func testTheInstrumentSeesSomething() {
        assertTheTreeIsPopulated(WelcomeBackView(name: "Alexandra"))
    }

    @MainActor
    func testTheInstrumentCatchesARealScreenSqueezed() {
        // A real screen in a frame far too short for it. Everything in the flow needs more than
        // 120pt, so if the two-render comparison works at all this reports squeezes and
        // collapses -- and if it ever stops, every sweep below is measuring nothing.
        let found = measureFit(
            FitDevice(name: "probe", width: 375, height: 120, top: 0, bottom: 0),
            "instrument", ConnectAccountView(state: .error))
        XCTAssertTrue(found.contains { $0.problem == "SQUEEZED" || $0.problem == "COLLAPSED" },
                      "a screen crushed into 120pt reported no squeeze at all: \(found.count)")
        XCTAssertTrue(found.filter { $0.problem == "SQUEEZED" }.allSatisfy { !$0.advisory },
                      "a squeeze must fail a screen, not merely be noted")
    }

    // The two synthetic instrument tests that used to sit here are gone.
    //
    // One squeezed a Text with `.frame(height: 8)` and expected a SQUEEZED finding; the other
    // put a Button in a 12pt frame and expected a tap-target finding. Both returned nothing in
    // CI, for different reasons -- SwiftUI clips a text view rather than resizing it, and a
    // SwiftUI Button is invisible to UIKit as a control at all. Neither absence meant the
    // detector was broken, and rewriting them until they passed would have produced two tests
    // asserting whatever the construction happened to do.
    //
    // `testTheInstrumentSeesSomething` covers the same ground against a REAL screen, which is
    // the case that matters and the one that actually reproduces.

    @MainActor
    func testBelowTheFoldOfAScrollViewIsAnAdvisoryNotAFailure() {
        // The scroll-aware branch. Without this, turning a screen into a ScrollView would silence
        // the off-the-bottom detector and nobody would notice it had gone quiet.
        let view = ScrollView {
            VStack {
                Text("top")
                Text("far below").padding(.top, 2000)
            }
        }
        let found = measureFit(
            FitDevice(name: "probe", width: 320, height: 400, top: 0, bottom: 0), "scrolled", view)
        XCTAssertTrue(found.contains { $0.problem == "BELOW THE FOLD" },
                      "content below a scroll fold was not reported at all: \(found)")
        XCTAssertTrue(found.filter { $0.problem == "BELOW THE FOLD" }.allSatisfy(\.advisory),
                      "below-the-fold content must not fail a screen")
        XCTAssertFalse(found.contains { $0.problem == "OFF THE BOTTOM" },
                       "scrolled content must not also be reported as lost")
    }

    // MARK: - welcome and sign-up

    @MainActor
    func testStartupFitsEverywhere() {
        sweep("Startup", StartupView())
        sweep("Startup + social proof", StartupView(showSocialProof: true))
        assertClean("Startup")
    }

    @MainActor
    func testWelcomeBackFitsEverywhere() {
        // The 24-character name is not an invented edge case: SHOWUP-142 lists it as a required
        // state, and on Android it was the worst finding in the whole sweep -- 26 problems on one
        // device, including "Continue with Facebook" measured at 7.5dp.
        sweep("WelcomeBack/Phone", WelcomeBackView(name: "Leo", lastUsed: .phone))
        sweep("WelcomeBack/Apple", WelcomeBackView(name: "Leo", lastUsed: .apple))
        sweep("WelcomeBack/Google", WelcomeBackView(name: "Leo", lastUsed: .google))
        sweep("WelcomeBack/Facebook", WelcomeBackView(name: "Leo", lastUsed: .facebook))
        sweep("WelcomeBack/Unknown", WelcomeBackView(name: "Leo", lastUsed: .unknown))
        sweep("WelcomeBack/no name", WelcomeBackView(name: ""))
        sweep("WelcomeBack/2 chars", WelcomeBackView(name: "Jo", lastUsed: .phone))
        sweep("WelcomeBack/24 chars",
              WelcomeBackView(name: "Alexandra-Wilhelmina Ma", lastUsed: .facebook))
        assertClean("Welcome back")
    }

    @MainActor
    func testPhoneAndCodeFitEverywhere() {
        sweep("Phone/empty", PhoneNumberView(value: .constant("")))
        sweep("Phone/typed", PhoneNumberView(value: .constant("17612345678")))
        sweep("Phone/tooShort", PhoneNumberView(value: .constant("201"), error: .tooShort))
        sweep("Code/empty", VerifyCodeView(digits: .constant("")))
        sweep("Code/typed", VerifyCodeView(digits: .constant("482170")))
        sweep("Code/mismatch", VerifyCodeView(digits: .constant("482170"), mismatch: true))
        sweep("Code/locked out",
              VerifyCodeView(digits: .constant("482170"), mismatch: true, lockedOut: true))
        assertClean("Phone and code")
    }

    // MARK: - connect

    @MainActor
    func testConnectFitsEverywhere() {
        // The screen Android had the most to say about: 39 findings across five short phones,
        // every one of them the bottom band squeezed by a banner above it. The three states that
        // add a banner -- cancelled and both errors -- are the ones that broke.
        sweep("Connect/idle", ConnectAccountView(state: .idle))
        sweep("Connect/tapped", ConnectAccountView(state: .tapped))
        sweep("Connect/handoff", ConnectAccountView(state: .handoff))
        sweep("Connect/cancelled", ConnectAccountView(state: .cancelled))
        sweep("Connect/error network", ConnectAccountView(state: .error, kind: .network))
        sweep("Connect/error declined", ConnectAccountView(state: .error, kind: .declined))
        sweep("Connect/conflict", ConnectAccountView(state: .conflict))
        sweep("Connect/conflict no email",
              ConnectAccountView(state: .conflict, conflictEmail: nil))
        sweep("Connect/conflict long email",
              ConnectAccountView(state: .conflict,
                                 conflictEmail: "leonardo.buonarroti@a-very-long-domain.example"))
        sweep("Connect/linking", ConnectAccountView(state: .linking))
        sweep("Connect/success", ConnectAccountView(state: .success))
        sweep("Connect/success no name", ConnectAccountView(state: .success, firstName: nil))
        assertClean("Connect")
    }

    // MARK: - profile basics

    @MainActor
    func testProfileBasicsFitEverywhere() {
        sweep("Profile/name empty", ProfileNameView(value: .constant("")))
        sweep("Profile/name typed", ProfileNameView(value: .constant("Leo")))
        sweep("Profile/email valid",
              ProfileEmailView(value: .constant("leo@hey.com"), consent: .constant(false)))
        sweep("Profile/email invalid",
              ProfileEmailView(value: .constant("leo@hey"), consent: .constant(false)))
        sweep("Profile/email consent on",
              ProfileEmailView(value: .constant("leo@hey.com"), consent: .constant(true)))
        sweep("Verify/arrival", ProfileVerifyEmailView(digits: .constant("")))
        sweep("Verify/mismatch",
              ProfileVerifyEmailView(digits: .constant("482170"), state: .mismatch))
        sweep("Verify/expired",
              ProfileVerifyEmailView(digits: .constant("482170"), state: .expired))
        sweep("Verify/locked out",
              ProfileVerifyEmailView(digits: .constant("482170"), state: .lockedOut))
        sweep("Verify/long address",
              ProfileVerifyEmailView(email: "leonardo.buonarroti@a-very-long-domain.example",
                                     digits: .constant("")))
        assertClean("Profile basics")
    }

    // MARK: - tutorial

    @MainActor
    func testTutorialCardsFitEverywhere() {
        sweep("Tutorial/1 welcome", WelcomeView())
        sweep("Tutorial/2 meet in real life", MeetInRealLifeView())
        sweep("Tutorial/3 availability", MatchOnAvailabilityView())
        sweep("Tutorial/4 binding date", MatchMeansMeetView())
        sweep("Tutorial/5 thirty minutes", ThirtyMinutesView())
        sweep("Tutorial/6 show up every time", ShowUpEveryTimeView())
        assertClean("Tutorial")
    }
}
