//  TapTargetTests.swift
//  ShowUp · does the control that LOOKS tappable actually take a tap
//
//  WHY THIS EXISTS
//
//  Android shipped a phone field that measured 23dp inside a 56dp row. It looked exactly right,
//  rendered exactly right, and only the middle third of it responded — so tapping near the top or
//  bottom edge did nothing at all. No screenshot could show it and no reading of the source found
//  it; ScreenFitTest found it by measuring, on 15 of 18 phone sizes.
//
//  iOS has never been measured for the same thing, and iOS is where it is MORE likely, not less:
//  a `UIViewRepresentable` wrapping a `UITextField` reports the intrinsic height of its font, and
//  an HStack centres a child at its own height inside whatever frame the parent was given. The box
//  is told to be 56 by its parent; nothing tells the field inside it to fill that box.
//
//  The screen also auto-focuses on appear, which is what hides it: the keyboard is already up, so
//  nobody taps the field until they have dismissed it once.
//
//  WHAT IT MEASURES
//
//  The real hosted-window layout from ScreenFitTests, then a walk of the UIView tree for the
//  UITextField, comparing its frame against the box drawn around it. This is the view's own
//  geometry after layout, not a pixel probe and not an approximation.

import XCTest
import SwiftUI
import UIKit
@testable import ShowUpWelcome

@MainActor
final class TapTargetTests: XCTestCase {

    /// The three sizes that matter: the narrowest thing shipped, the stated iOS floor, and a
    /// typical modern phone. A tap target does not vary with width, so the whole matrix would be
    /// eighteen runs of the same assertion.
    private struct Size {
        let name: String, width: CGFloat, height: CGFloat, top: CGFloat, bottom: CGFloat
    }
    private let sizes = [
        Size(name: "Galaxy Fold cover screen", width: 320, height: 686, top: 24, bottom: 24),
        Size(name: "iPhone SE (3rd gen)", width: 375, height: 667, top: 20, bottom: 0),
        Size(name: "iPhone 12 / 13 / 14", width: 390, height: 844, top: 47, bottom: 34),
    ]

    /// Lay a view out in a real window at a real device size and hand back the root UIView.
    private func hosted(_ view: some View, _ size: Size) -> UIView {
        let controller = UIHostingController(rootView: AnyView(view))
        controller.overrideUserInterfaceStyle = .light
        let window = FixedInsetWindow(
            frame: CGRect(x: 0, y: 0, width: size.width, height: size.height))
        window.fixedInsets = UIEdgeInsets(top: size.top, left: 0, bottom: size.bottom, right: 0)
        window.rootViewController = controller
        window.isHidden = false
        window.layoutIfNeeded()
        controller.view.setNeedsLayout()
        controller.view.layoutIfNeeded()
        return controller.view
    }

    private func firstTextField(in view: UIView) -> UITextField? {
        if let field = view as? UITextField { return field }
        for child in view.subviews {
            if let found = firstTextField(in: child) { return found }
        }
        return nil
    }

    // MARK: - the number field

    /// The field a person taps must be as tall as the box they can see.
    ///
    /// Asserted against `ComponentSizes.minTapTarget` rather than 56, because 44 is the floor the
    /// design uses on purpose in a few places and the smallest number that is never a bug. A field
    /// inside a 56-tall box should reach 56; anything under 44 is the Android defect again.
    func testTheNumberFieldFillsTheBoxItIsDrawnIn() throws {
        var short: [String] = []
        for size in sizes {
            var digits = ""
            let view = hosted(
                PhoneNumberView(value: Binding(get: { digits }, set: { digits = $0 })),
                size)
            let field = try XCTUnwrap(firstTextField(in: view),
                                      "\(size.name): no UITextField in the hierarchy at all")
            let height = field.bounds.height
            if height < ComponentSizes.minTapTarget {
                short.append(String(
                    format: "%@: the field is %.1fpt tall inside its box, under the %.0fpt floor",
                    size.name, height, ComponentSizes.minTapTarget))
            }
        }
        XCTAssertTrue(short.isEmpty,
                      "the number field is smaller than the control it appears to be:\n"
                      + short.joined(separator: "\n"))
    }

    /// And the box itself has not shrunk: a field that fills a box which is itself too short would
    /// pass the test above while still being untappable at the edges.
    func testTheNumberFieldsBoxIsAFullControlHeight() throws {
        var wrong: [String] = []
        for size in sizes {
            var digits = ""
            let view = hosted(
                PhoneNumberView(value: Binding(get: { digits }, set: { digits = $0 })),
                size)
            let field = try XCTUnwrap(firstTextField(in: view), size.name)
            // The box is the nearest ancestor that is a full control tall.
            var box: UIView? = field.superview
            var found: CGFloat? = nil
            while let candidate = box, found == nil {
                if abs(candidate.bounds.height - ComponentSizes.controlHeight) < 0.5 {
                    found = candidate.bounds.height
                }
                box = candidate.superview
            }
            if found == nil {
                wrong.append(String(
                    format: "%@: no ancestor of the field is %.0fpt tall",
                    size.name, ComponentSizes.controlHeight))
            }
        }
        XCTAssertTrue(wrong.isEmpty, wrong.joined(separator: "\n"))
    }

    // MARK: - the instrument has to fire

    /// A green run means nothing unless the probe can see a short field.
    ///
    /// This builds the defect on purpose — a text field left at its intrinsic height inside a
    /// 56-tall box, which is exactly the shape of the Android bug — and fails if the measurement
    /// reports it as fine.
    func testTheProbeFindsAShortField() throws {
        struct Defective: View {
            var body: some View {
                HStack(spacing: 0) { IntrinsicHeightField() }
                    .padding(.horizontal, 18)
                    .frame(maxWidth: .infinity, minHeight: ComponentSizes.controlHeight,
                           maxHeight: ComponentSizes.controlHeight, alignment: .leading)
            }
        }
        struct IntrinsicHeightField: UIViewRepresentable {
            func makeUIView(context: Context) -> UITextField {
                let field = UITextField()
                field.font = .systemFont(ofSize: 17)
                field.text = "0176 123 45 678"
                return field
            }
            func updateUIView(_ field: UITextField, context: Context) {}
        }

        let view = hosted(Defective(), sizes[2])
        let field = try XCTUnwrap(firstTextField(in: view))
        XCTAssertLessThan(
            field.bounds.height, ComponentSizes.minTapTarget,
            "the probe cannot see a short field, so a green run above proves nothing")
    }
}
