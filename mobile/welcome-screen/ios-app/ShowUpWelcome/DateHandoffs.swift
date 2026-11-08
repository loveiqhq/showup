// DateHandoffs.swift
// ShowUp · the quick actions' hand-offs to the phone (SHOWUP-193 ⑪ and ④)
//
// WHY THERE ARE UIViewControllerRepresentABLES IN HERE, which the iOS CLAUDE.md requires a written
// reason for: SwiftUI has no equivalent of either surface the ticket hands off to.
//
// `UIActivityViewController` is the system share sheet. SwiftUI's `ShareLink` is a BUTTON that opens
// it, drawn by SwiftUI -- but Share Date is the kit's own quick-action tile, tracked on the tap before
// the hand-off, so the sheet has to be presented from our button rather than replace it.
//
// `EKEventEditViewController` is the system's new-event sheet, filled in and saved by the user. SwiftUI
// has no calendar UI at all. Since iOS 17 it runs out of process and needs no calendar access to show
// -- nothing is requested; the write-only purpose string in Info.plist is there for the OS to show if
// it ever asks.
//
// WHAT IS NOT OURS, and is not drawn or measured anywhere in this app: the share sheet, the calendar
// sheet and the Maps app. We choose to present them; we do not choose what they look like.

import EventKit
import EventKitUI
import SwiftUI
import UIKit

/// Directions: Apple Maps with the place as the destination -- a universal link, so no URL scheme has to
/// be declared, and it opens in the browser where Maps is not installed.
func directionsURL(_ place: DatePlace) -> URL? {
    var parts = URLComponents(string: "https://maps.apple.com/")
    parts?.queryItems = [URLQueryItem(name: "daddr", value: placeText(place))]
    return parts?.url
}

/// Copy: `{place}, {address}` on the clipboard. The pill's `Copied` is the confirmation -- no toast.
@MainActor
func copyAddress(_ text: String) {
    UIPasteboard.general.string = text
}

/// The system share sheet with the ticket's sentence as plain text.
struct ShareSheet: UIViewControllerRepresentable {
    let text: String

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [text], applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

/// The system's new-event sheet, filled in with the date: the user saves it there, or cancels.
struct CalendarSheet: UIViewControllerRepresentable {
    let event: CalendarEvent
    /// The sheet finished -- saved or cancelled; either way it closes.
    let onDone: () -> Void

    func makeUIViewController(context: Context) -> EKEventEditViewController {
        let store = EKEventStore()
        let new = EKEvent(eventStore: store)
        new.title = event.title
        new.startDate = event.start
        new.endDate = event.end
        new.location = event.location
        let controller = EKEventEditViewController()
        controller.eventStore = store
        controller.event = new
        controller.editViewDelegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ controller: EKEventEditViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onDone: onDone) }

    final class Coordinator: NSObject, EKEventEditViewDelegate {
        private let onDone: () -> Void
        init(onDone: @escaping () -> Void) { self.onDone = onDone }

        func eventEditViewController(_ controller: EKEventEditViewController,
                                     didCompleteWith action: EKEventEditViewAction) {
            onDone()
        }
    }
}
