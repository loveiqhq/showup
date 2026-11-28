// PartnerEndedSamples.swift
// ShowUp · Date 08's three payloads drawn still, with nothing wired -- one copy for the previews
// (`PartnerEndedPreviews`), the debug States page's fallback picture and the evidence harness (SHOWUP-199). Android's
// `PartnerEndedSamples.kt`. The name is the kit's: Leonie.

import SwiftUI

enum PartnerEndedSamples {
    private static let name = DateSamples.date.match.name

    /// `scrollToEnd` is the evidence harness's: the column comes to rest at its end, where the CTA is the last item.
    static func frame(_ screen: PartnerScreen, scrollToEnd: Bool = false) -> some View {
        PartnerEndedScreen(screen: screen, name: name, photoURL: nil)
            .environment(\.partnerEndedScrollsToEnd, scrollToEnd)
    }
}
