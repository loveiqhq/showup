//  ScreenFitTests.swift
//  ShowUp · every phone the app has to run on, measured rather than eyeballed
//
//  The iOS answer to "does it actually fit", and the twin of ScreenFitTest.kt / Devices.kt on
//  Android — same device matrix, same intent. Android has had this since the tutorial screens
//  landed; iOS had nothing, and the cost of that showed up on 2026-09-01, when running the app for
//  the first time found two layout faults that every text-based audit had passed for weeks.
//
//  WHAT IT GUARDS, and why these and not others.
//
//  SHOWUP-143 states one layout rule outright: the CTA does not move when a state goes wrong. Both
//  screens broke it, in different ways and by different amounts, and neither was visible in the
//  source — one was a `frame(minHeight:)` wrapped around a nil view, which reserves nothing at all.
//  A rule that can only be checked by measuring is exactly the rule to measure, so these tests
//  render the real views and compare the CTA's position between the calm state and the failed one.
//
//  Rendering is a hosted window captured through `layer.render(in:)`. Both halves of that were
//  arrived at by trying the alternative and looking at the result:
//
//    · `ImageRenderer` draws the backdrop and nothing else. It does not resolve the GeometryReader
//      and ScrollView these screens are built from, so every probe came back empty — which a less
//      careful harness would have reported as "no problems found".
//    · `drawHierarchy(afterScreenUpdates:)` returns a blank image offscreen, because there are no
//      screen updates to be after.
//
//  Rendering the layer tree of a real hosted window has neither problem, and it draws the number
//  field too, which is a UIViewRepresentable and therefore invisible to the other two.

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class ScreenFitTests: XCTestCase {

    /// Every phone the app has to run on, mirroring Devices.kt.
    ///
    /// The 320-wide Galaxy Fold cover screen is kept even though it is not an iPhone: it is the
    /// narrowest thing either platform has to survive, and a reserve that holds at 320 holds
    /// everywhere. The iOS floor proper is the 375x667 SE, named by the product side.
    // The matrix lives in FitDevices.swift now, so every fit suite in this target measures the
    // same phones -- and `audit/verify-welcome.py` checks that list against Devices.kt row for
    // row, so neither can drift from Android's.
    typealias Device = FitDevice
    var devices: [FitDevice] { fitDevices }

    // MARK: - the instrument
    //
    // A green run means nothing unless the instrument fires, so `testTheProbeFindsAMovedCTA` below
    // moves a CTA on purpose and fails if the probe cannot see it. Same idea as HarnessSelfTest.kt.

    private func render(_ view: some View, on device: Device) throws -> CGImage {
        let controller = UIHostingController(rootView: AnyView(view))
        // The design is a light-mode design; a dark-mode run would probe for a colour that is not
        // there and report every screen as broken.
        controller.overrideUserInterfaceStyle = .light
        // The safe area is REPLACED, not added to. `additionalSafeAreaInsets` is additive, and a
        // window in a test inherits the host simulator's own 54pt top inset -- which showed up as
        // every device reporting 54 less height than it should, uniformly enough to look correct
        // and be wrong everywhere. Overriding the window's own value is the only way to say
        // "this device has exactly these insets".
        let window = FixedInsetWindow(frame: CGRect(x: 0, y: 0, width: device.width, height: device.height))
        window.fixedInsets = UIEdgeInsets(top: device.top, left: 0, bottom: device.bottom, right: 0)
        window.rootViewController = controller
        window.isHidden = false
        window.layoutIfNeeded()
        controller.view.setNeedsLayout()
        controller.view.layoutIfNeeded()

        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1     // one pixel per point, so a row index IS a point offset
        let image = UIGraphicsImageRenderer(size: window.bounds.size, format: format).image { ctx in
            window.layer.render(in: ctx.cgContext)
        }
        return try XCTUnwrap(image.cgImage, "\(device.name): nothing rendered")
    }

    /// The rendered pixels as straight RGBA bytes.
    ///
    /// Read through a context of a known format rather than off the CGImage directly: the renderer
    /// hands back premultiplied-first byte order, so indexing it as RGBA silently reads the alpha
    /// channel as red and finds nothing anywhere.
    private func rgba(_ image: CGImage) -> [UInt8] {
        var buffer = [UInt8](repeating: 0, count: image.width * image.height * 4)
        buffer.withUnsafeMutableBytes { raw in
            let context = CGContext(
                data: raw.baseAddress, width: image.width, height: image.height,
                bitsPerComponent: 8, bytesPerRow: image.width * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
            context?.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        }
        return buffer
    }

    /// Which colour identifies the CTA on a given screen.
    ///
    /// There are two primary actions in this app and they are not the same colour -- something
    /// this probe did not account for. The header below already noted that the tutorial cards were
    /// excluded because "a probe tuned to the gradient would not see" a circular Next control, and
    /// then the profile screens arrived using exactly that control. The probe reported "CTA not
    /// drawn at all" for all five of them, which reads as a layout failure and was a blind spot in
    /// the harness.
    private enum CTATint {
        /// The sunset pill, found by its violet end -- `liqPurple` #812AEC.
        ///
        /// The gradient runs orange to violet, and violet is the half worth probing: the danger
        /// states paint red and pink over the top third of those screens, and orange is close
        /// enough to those to be ambiguous.
        case violet
        /// The circular `NextButton`, found by its fill -- `liqOrange` #FE6839.
        ///
        /// **Green is the channel that does the work.** Danger red #FB323B is r 251 / g 50 / b 59
        /// and orange is r 254 / g 104 / b 57 -- all but identical on red and blue, so a bound on
        /// green is the only thing separating this CTA from an error state. The upper green bound
        /// also rejects the circle glow, which lands far lighter.
        case orange
        /// The sunset pill while DISABLED.
        ///
        /// `PrimaryButton` renders a disabled control at `.opacity(0.45)`, so its violet end
        /// composites against the cream ground to roughly r 197 / g 157 / b 242 — nowhere near
        /// the full-strength predicate, which reads it as "CTA not drawn at all". The control is
        /// on screen; the probe simply could not see it, exactly as it could not see the orange
        /// circle before `.orange` was added.
        ///
        /// The band is wide enough for the ground varying under the button and tight enough to
        /// exclude the two light violets on these screens: cream (g 251) and the lavender badge
        /// (r 237) both fail it.
        case violetDim

        func matches(_ r: Int, _ g: Int, _ b: Int) -> Bool {
            switch self {
            case .violet: return r < 180 && g < 110 && b > 190
            case .orange: return r > 220 && g > 80 && g < 140 && b < 100
            case .violetDim: return r > 170 && r < 220 && g > 130 && g < 190 && b > 215
            }
        }
    }

    /// The rows the CTA occupies in the right-hand gutter.
    ///
    /// Sampled at x = 88% of the width, which falls inside the 56pt circle at both ends of the
    /// device matrix -- 320 wide puts the sample at 281 against a circle spanning 240...296, and
    /// 440 wide at 387 against 360...416 -- as well as inside the pill.
    private func ctaRows(in image: CGImage, tint: CTATint = .violet) -> ClosedRange<Int>? {
        let bytes = rgba(image)
        let x = Int(Double(image.width) * 0.88)
        var rows: [Int] = []
        for y in 0..<image.height {
            let p = (y * image.width + x) * 4
            let (r, g, b) = (Int(bytes[p]), Int(bytes[p + 1]), Int(bytes[p + 2]))
            if tint.matches(r, g, b) { rows.append(y) }
        }
        guard let first = rows.first, let last = rows.last else { return nil }
        return first...last
    }

    // MARK: - the rule SHOWUP-143 states outright

    func testTheCTADoesNotMoveWhenTheNumberIsRejected() throws {
        var moved: [String] = []
        for device in devices {
            let calm = try ctaRows(in: render(PhoneNumberView(value: .constant("201")), on: device))
            // The longest real message, and the one that wraps at every width this ships at.
            let failed = try ctaRows(in: render(
                PhoneNumberView(value: .constant("201"), error: .tooShort), on: device))
            guard let calm, let failed else {
                moved.append("\(device.name): CTA not found at all"); continue
            }
            if calm.lowerBound != failed.lowerBound {
                moved.append("\(device.name): CTA moved \(failed.lowerBound - calm.lowerBound)pt")
            }
        }
        XCTAssertTrue(moved.isEmpty, "the CTA moved on rejection:\n" + moved.joined(separator: "\n"))
    }

    func testTheCTADoesNotMoveWhenTheCodeIsWrong() throws {
        var moved: [String] = []
        for device in devices {
            let calm = try ctaRows(in: render(VerifyCodeView(digits: .constant("482170")), on: device))
            let failed = try ctaRows(in: render(
                VerifyCodeView(digits: .constant("482170"), mismatch: true), on: device))
            guard let calm, let failed else {
                moved.append("\(device.name): CTA not found at all"); continue
            }
            if calm.lowerBound != failed.lowerBound {
                moved.append("\(device.name): CTA moved \(failed.lowerBound - calm.lowerBound)pt")
            }
        }
        XCTAssertTrue(moved.isEmpty, "the CTA moved on mismatch:\n" + moved.joined(separator: "\n"))
    }

    // MARK: - and the rule that makes the first one worth anything

    func testTheCTAIsOnScreenOnEveryDevice() throws {
        // A CTA that never moves because it is off the bottom of the screen would pass the tests
        // above and fail every user.
        //
        // Every screen in the flow that carries a sunset CTA is swept, not just the two above: the
        // primary action being reachable is the one thing that has to hold on all seventeen, and
        // the tight end of the matrix -- a 640pt-tall phone -- is where a bottom band runs out of
        // room. The tutorial cards are absent because their Next control is a circle in the corner
        // rather than a pill, and a probe tuned to the gradient would not see it.
        var offscreen: [String] = []
        for device in devices {
            let screens: [(String, CGImage, CTATint)] = [
                ("Startup", try render(StartupView(), on: device), .violet),
                // Both states of the gated row, as ScreenFitTest.kt does. The row is 32pt of
                // content and the band it floats in is the first thing to run out on a short
                // phone, so testing only the "off" case tests the easy half.
                ("Startup + social proof",
                 try render(StartupView(showSocialProof: true), on: device), .violet),
                ("Welcome back",
                 try render(WelcomeBackView(name: "Alexandra"), on: device), .violet),
                ("Phone",
                 try render(PhoneNumberView(value: .constant("201555")), on: device), .violet),
                ("Phone rejected", try render(
                    PhoneNumberView(value: .constant("201"), error: .tooShort), on: device), .violet),
                ("Code",
                 try render(VerifyCodeView(digits: .constant("482170")), on: device), .violet),
                ("Code mismatch", try render(
                    VerifyCodeView(digits: .constant("482170"), mismatch: true), on: device), .violet),
                // The attempt cap's own state. Its card carries a DIFFERENT string from the
                // mismatch one, and the region under the slots is sized for a single line — a
                // message that wrapped would grow the region and take the CTA with it. This is
                // the sweep that would catch that.
                // .violetDim, not .violet: lockout disables the CTA, and a disabled PrimaryButton
                // is drawn at 45% — see CTATint. Probing this one on the full-strength violet
                // reports the button missing when it is merely dimmed.
                ("Code locked out", try render(
                    VerifyCodeView(digits: .constant("482170"), mismatch: true, lockedOut: true),
                    on: device), .violetDim),
                ("Tutorial card 1", try render(WelcomeView(), on: device), .violet),
                // Profile creation "The basics" — SHOWUP-150 / 152, every state.
                //
                // The email screen's error state is the one that matters: it is the only screen in
                // the group that does NOT reserve its status region, so the consent row and CTA sit
                // ~18 lower there. The acceptance test is that the CTA still clears the keyboard,
                // and this harness is what would catch it stopping.
                // These five draw the circular NextButton, so they are probed on ORANGE. Getting
                // this wrong does not weaken the test, it silently empties it -- a probe that
                // matches nothing returns nil, which this test reads as "CTA not drawn at all".
                ("Profile name", try render(
                    ProfileNameView(value: .constant("")), on: device), .orange),
                ("Profile name typed", try render(
                    ProfileNameView(value: .constant("Leo")), on: device), .orange),
                ("Profile email", try render(
                    ProfileEmailView(value: .constant("leo@hey.com"),
                                     consent: .constant(false)), on: device), .orange),
                ("Profile email invalid", try render(
                    ProfileEmailView(value: .constant("leo@hey"),
                                     consent: .constant(false)), on: device), .orange),
                ("Profile email consent on", try render(
                    ProfileEmailView(value: .constant("leo@hey.com"),
                                     consent: .constant(true)), on: device), .orange),

                // SHOWUP-153. The CTA here is the SUNSET pill, not the circular NextButton — the
                // only screen in the group that uses it — so these probe on violet. The locked
                // out and expired states disable it, which draws at 45%: .violetDim.
                ("Verify arrival", try render(
                    ProfileVerifyEmailView(digits: .constant("")), on: device), .violetDim),
                ("Verify typed", try render(
                    ProfileVerifyEmailView(digits: .constant("4821")), on: device), .violetDim),
                ("Verify mismatch", try render(
                    ProfileVerifyEmailView(digits: .constant("482170"), state: .mismatch),
                    on: device), .violet),
                ("Verify expired", try render(
                    ProfileVerifyEmailView(digits: .constant("482170"), state: .expired),
                    on: device), .violetDim),
                ("Verify locked out", try render(
                    ProfileVerifyEmailView(digits: .constant("482170"), state: .lockedOut),
                    on: device), .violetDim),
                ("Verify long address", try render(
                    ProfileVerifyEmailView(email: "leonardo.buonarroti@a-very-long-domain.example",
                                           digits: .constant("")), on: device), .violetDim),

                // SHOWUP-154. Circular NextButton, always enabled — the CTA is never disabled on
                // this screen, which is the group rule the code screen is the one exception to.
                ("DoB empty", try render(
                    ProfileDobView(value: .constant(""), hideAge: .constant(false)),
                    on: device), .orange),
                ("DoB confirm", try render(
                    ProfileDobView(value: .constant("03/22/1998"), hideAge: .constant(false)),
                    on: device), .orange),
                ("DoB impossible", try render(
                    ProfileDobView(value: .constant("02/30/1990"), hideAge: .constant(false)),
                    on: device), .orange),
                ("DoB under 18", try render(
                    ProfileDobView(value: .constant("05/19/2015"), hideAge: .constant(false)),
                    on: device), .orange),
                ("DoB incomplete after press", try render(
                    ProfileDobView(value: .constant("03/22"), hideAge: .constant(false),
                                   attempted: true), on: device), .orange),
                ("DoB age hidden", try render(
                    ProfileDobView(value: .constant("03/22/1998"), hideAge: .constant(true)),
                    on: device), .orange),

                // SHOWUP-155, the bridge. A FULL-WIDTH SUNSET pill, always enabled — rule 7's
                // named exception — so these probe on violet rather than orange like the four
                // screens above them. The long name is swept because the headline is the only
                // thing on this screen that can reflow, and every point it grows comes out of
                // the single spacer above the CTA.
                // SHOWUP-162. Probed at the sunset ramp's VIOLET end, like the bridge above and
                // for the same reason: the orange end of that gradient cannot be told from the
                // routine orange CTA the other profile screens use.
                //
                // THIS IS THE TIGHTEST NON-SCROLLING CONTENT IN THE FLOW -- a 32 headline, a lead
                // paragraph and five two-line rows -- so "is the CTA on screen at all" is exactly
                // the question worth asking here.
                ("Notifications", try render(ProfileNotificationsView(), on: device), .violet),
                ("Embrace named", try render(
                    ProfileEmbraceView(firstName: "Leo"), on: device), .violet),
                ("Embrace no name", try render(
                    ProfileEmbraceView(), on: device), .violet),
                ("Embrace long name", try render(
                    ProfileEmbraceView(firstName: "Maximiliana-Rose"), on: device), .violet),

                // SHOWUP-156, all seven states. The round ORANGE NextButton, always enabled —
                // Continue is never disabled on this screen, whatever the count says.
                //
                // This is the first screen in the flow that scrolls, so a finding here means
                // something FIXED has overflowed: the header, the progress bar, the footer, or a
                // slot whose 158 has been compromised. The middle region can always scroll.
                ("Photos empty", try render(
                    ProfilePhotosView(), on: device), .orange),
                ("Photos partial", try render(
                    ProfilePhotosView(state: PhotoGridState(photos: fitConfirmedPhotos(2))),
                    on: device), .orange),
                ("Photos uploading", try render(
                    ProfilePhotosView(state: PhotoGridState(photos: [
                        PickedPhoto(localId: 0, uri: nil, status: .confirmed),
                        PickedPhoto(localId: 1, uri: nil, status: .inFlight, progress: 0.62),
                        PickedPhoto(localId: 2, uri: nil, status: .failed),
                    ])), on: device), .orange),
                ("Photos library blocked", try render(
                    ProfilePhotosView(library: .blocked), on: device), .orange),
                ("Photos library can ask", try render(
                    ProfilePhotosView(library: .canAsk), on: device), .orange),
                // The two source-sheet states are NOT here, and that is the probe's limit
                // rather than a gap in the screen. With the sheet up, Continue is
                // deliberately behind a 42% scrim — the user dismisses the sheet to reach
                // it — and the sheet's own actions are rows in `liqRaised`, not a tinted
                // pill this probe can recognise. It reported them as "CTA off screen" on
                // all seventeen devices, which is true and is not a defect. Same reason
                // the tutorial cards are absent from this sweep. Both states ARE measured
                // element by element by the Android harness, which does not depend on
                // recognising a colour.
                ("Photos six revealed", try render(
                    ProfilePhotosView(state: PhotoGridState(photos: fitConfirmedPhotos(6),
                                                            optionalRevealed: true)),
                    on: device), .orange),

                // SHOWUP-158, all eight. The two sheets are the point: the topic sheet is capped
                // at 600 and scrolls inside that, the write sheet has a 560 minimum, and 375 x 667
                // is where those two numbers meet the smallest frame. A write sheet that overflowed
                // would put Save off the bottom on the one screen where the CTA is the only way
                // out — and both sheets cover the screen's own CTA, which is why the sheet states
                // are probed on the SAVE button's violet rather than on the round orange one.
                ("Prompts none", try render(
                    ProfilePromptsView(), on: device), .orange),
                ("Prompts one", try render(
                    ProfilePromptsView(state: PromptsState(prompts: fitOnePrompt)),
                    on: device), .orange),
                ("Prompts three", try render(
                    ProfilePromptsView(state: PromptsState(prompts: fitThreePrompts)),
                    on: device), .orange),
                ("Prompts write empty", try render(
                    ProfilePromptsView(state: PromptsState(
                        sheet: .write(topicId: "first_date_usually", editing: false))),
                    on: device), .violet),
                ("Prompts write mid", try render(
                    ProfilePromptsView(state: PromptsState(
                        sheet: .write(topicId: "first_date_usually", editing: false),
                        drafts: ["first_date_usually": "Talk about anything real. Not the weather."])),
                    on: device), .violet),
                ("Prompts write at cap", try render(
                    ProfilePromptsView(state: PromptsState(
                        sheet: .write(topicId: "first_date_usually", editing: false),
                        drafts: ["first_date_usually": fitFullDraft])),
                    on: device), .violet),
                ("Prompts write nudge", try render(
                    ProfilePromptsView(state: PromptsState(
                        sheet: .write(topicId: "first_date_usually", editing: false), nudge: true)),
                    on: device), .violet),

                // SHOWUP-161, the four card states. Their CTA is the ORANGE NextButton, which is
                // what this probe recognises.
                //
                // THE OTHER SIX ARE DELIBERATELY ABSENT, for the reason already recorded above the
                // photo source sheet. E and F put a SUNSET commit button over the screen's own CTA,
                // and a sunset ramp runs orange to violet, so a colour probe cannot say which
                // control it found. G to J have no tinted pill at all: the shutter is a red circle
                // on a full-bleed ground and the review primary is the same sunset ramp. All six
                // ARE measured, element by element and at seventeen sizes, by the Android harness,
                // which does not depend on recognising a colour -- see `ScreenFitTest.kt`, and
                // `MediaAboveTheFoldTest.kt` for the 390 x 844 fold budget the ticket names.
                ("Media empty", try render(ProfileMediaView(), on: device), .orange),
                ("Media video only", try render(
                    ProfileMediaView(state: MediaState(video: fitMediaVideo)), on: device), .orange),
                ("Media voice only", try render(
                    ProfileMediaView(state: MediaState(voice: fitMediaVoice)), on: device), .orange),
                ("Media both", try render(
                    ProfileMediaView(state: MediaState(video: fitMediaVideo, voice: fitMediaVoice)),
                    on: device), .orange),
            ]
            for (label, image, tint) in screens {
                guard let rows = ctaRows(in: image, tint: tint) else {
                    offscreen.append("\(device.name) / \(label): CTA not drawn at all"); continue
                }
                if rows.upperBound >= Int(device.height) - 1 {
                    offscreen.append("\(device.name) / \(label): CTA runs off the bottom edge")
                }
            }
        }
        // One line, not one per finding. A newline-separated assertion message is truncated
        // to its first line by the CI log renderer, so a failure reported the count and hid
        // every offender — which cost a round trip on a Mac this machine does not have.
        XCTAssertTrue(offscreen.isEmpty, "CTA off screen: " + offscreen.joined(separator: " · "))
    }

    // MARK: - the copy has to fit the room reserved for it

    func testEveryErrorMessageFitsTheReservedRow() throws {
        // The other half of "the CTA does not move". Holding the row at a fixed two lines keeps the
        // CTA still, and would keep it just as still while cutting the end off the message -- which
        // is the trade Android made unknowingly and the one FIT-2026-08-31 section D called the
        // expensive fix. So the reserve is measured against every message it has to hold.
        //
        // Every country, not a sample: the messages interpolate the country's name and its example
        // number, so the longest one belongs to whichever country has the longest pair, and that is
        // not something to guess at. At the narrowest width the app supports.
        let width: CGFloat = 320 - 24 * 2 - 4    // 320 device, the scaffold's 24 each side, 4 lead
        let reserve: CGFloat = 36
        let font = UIFont(name: PS.manropeSemi, size: 13) ?? .systemFont(ofSize: 13, weight: .semibold)
        // What minimumScaleFactor(0.85) allows the text to shrink to before it would truncate.
        let smallest = font.withSize(13 * 0.85)

        var tooTall: [String] = []
        for country in COUNTRIES {
            for error in [PhoneError.empty, .tooShort, .tooLong, .invalidLength,
                          .unrecognised, .notANumber, .notMobile] {
                let message = error.message(country) as NSString
                let box = CGSize(width: width, height: .greatestFiniteMagnitude)
                let height = message.boundingRect(
                    with: box, options: [.usesLineFragmentOrigin, .usesFontLeading],
                    attributes: [.font: smallest], context: nil).height
                if height > reserve {
                    tooTall.append(String(format: "%@ %@: needs %.1fpt of %.0f — \"%@\"",
                                          country.iso, "\(error)", height, reserve, message))
                }
            }
        }
        XCTAssertTrue(tooTall.isEmpty,
                      "messages that cannot fit the reserved row, so they would be cut:\n"
                      + tooTall.prefix(12).joined(separator: "\n")
                      + (tooTall.count > 12 ? "\n…and \(tooTall.count - 12) more" : ""))
    }

    // MARK: - the instrument, checked

    func testTheProbeFindsAMovedCTAWhenThereIsOne() throws {
        // Deliberately moves the CTA by padding the top, and fails if the probe cannot see it. A
        // suite that cannot detect the fault it exists to catch is worse than no suite: it reports
        // success either way.
        let device = devices.first { $0.name == "iPhone SE (3rd gen)" }!
        let base = try ctaRows(in: render(PhoneNumberView(value: .constant("201")), on: device))
        let shifted = try ctaRows(in: render(
            PhoneNumberView(value: .constant("201")).padding(.top, 20), on: device))
        let a = try XCTUnwrap(base, "probe found no CTA in the baseline render")
        let b = try XCTUnwrap(shifted, "probe found no CTA in the shifted render")
        XCTAssertEqual(b.lowerBound - a.lowerBound, 20,
                       "the probe did not see a CTA that moved by exactly 20pt")
    }
}

/// A window whose safe-area insets are whatever the test says they are.
final class FixedInsetWindow: UIWindow {
    var fixedInsets: UIEdgeInsets = .zero
    override var safeAreaInsets: UIEdgeInsets { fixedInsets }
}

// MARK: - fixtures for "The real you"
//
// At file scope rather than inside the test, because the sweep above builds one array literal and
// a `let` inside it would have to be hoisted anyway.

private func fitConfirmedPhotos(_ n: Int) -> [PickedPhoto] {
    (0..<n).map { PickedPhoto(localId: Int64($0), uri: nil, status: .confirmed) }
}

private /// One recorded take per medium, for the media sweep above.
let fitMediaVideo = MediaArtefact(kind: .video, promptId: "relaxed_and_happy", durationMs: 9_400,
                                  localPath: nil, remoteId: "v1", url: nil, status: .confirmed)
let fitMediaVoice = MediaArtefact(kind: .voice, promptId: "relaxing_sound", durationMs: 14_100,
                                  localPath: nil, remoteId: "a1", url: nil, status: .confirmed)

let fitOnePrompt = [SavedPrompt(
    topicId: "first_date_usually",
    answer: "Talk about anything real. Not jobs, not pets, not the weather. The thing actually on "
        + "your mind this week. Bring it. I'll listen."
)]

private let fitThreePrompts = fitOnePrompt + [
    SavedPrompt(topicId: "hill_to_die_on",
                answer: "Showing up. Cancelling last minute isn't a scheduling problem, "
                    + "it's an answer."),
    SavedPrompt(topicId: "cross_town_for",
                answer: "A proper conversation. An old cinema. The 8pm walk after a long day."),
]

private let fitFullDraft = cappedAnswer(
    "Talk about anything real. Not jobs, not pets, not the weather. The thing actually on your "
    + "mind this week. Bring it. I will listen for the entire thirty minutes!")
