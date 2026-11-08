// FirstAppear.swift
// ShowUp · run something the first time a view appears in its place, and not again while it stays
//
// For a route's screen view and the events that go with it: once per showing. SwiftUI may call `onAppear`
// more than once for one showing, and each call reported `chat_opened` and `screen_viewed` again and
// restarted the chat's visit, so `chat_closed` undercounted (the SHOWUP-194 review). The flag is the
// view's own state: a new showing -- a route pushed again -- starts with it clear. Android's
// `rememberSaveable` guard on each route is the twin.

import SwiftUI

extension View {
    /// Runs `action` the first time this view appears in its place.
    func onFirstAppear(perform action: @escaping () -> Void) -> some View {
        modifier(FirstAppear(action: action))
    }
}

private struct FirstAppear: ViewModifier {
    let action: () -> Void
    @State private var done = false

    func body(content: Content) -> some View {
        content.onAppear {
            guard !done else { return }
            done = true
            action()
        }
    }
}
