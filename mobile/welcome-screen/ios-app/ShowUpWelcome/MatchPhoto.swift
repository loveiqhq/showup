// MatchPhoto.swift
// ShowUp · the kit's `Avatar`: the match's photo in a circle, or their initial on the placeholder
//
// Taken out of the Date screen's unread card with the coordinate chat (SHOWUP-194), whose header, bubbles
// and outcome screens draw the same face at 40, 26 and 72. One drawing, so the initial's size, weight and
// ink cannot drift between them. Android's `MatchPhoto.kt`.

import SwiftUI

/// `size` square, clipped round: the photo when there is one, otherwise the kit's placeholder with the
/// initial -- Lora at 0.42 of the size, white at 92%. Decorative: the name is always beside it.
struct MatchPhoto: View {
    let name: String
    let photoURL: URL?
    let size: CGFloat

    var body: some View {
        ZStack {
            PhotoBackdrop(url: photoURL, index: 0)
            if photoURL == nil {
                Text(String(name.prefix(1)))
                    .font(F.lora(size * 0.42, bold: true))
                    .foregroundColor(Color.white.opacity(0.92))
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityHidden(true)
    }
}
