//
//  SearchPhotoViewer.swift
//  ShowUp · the fullscreen photo viewer (Search 01: "A photo tap opens the fullscreen viewer")
//
//  The Swift twin of `search/PhotoViewer.kt`. "Reuse the platform's own media viewer if the build has
//  one" -- it has none, so this is the smallest one that does the job: it opens at the photo tapped,
//  swipes through every photo in profile order, and closes with its X, back to the same scroll
//  position (the stack underneath never moved). Ink, not black: the design has no pure black.
//

import SwiftUI

struct SearchPhotoViewer: View {
    let name: String
    let photos: [URL?]
    let page: Int
    let onPage: (Int) -> Void
    let onClose: () -> Void

    var body: some View {
        ZStack(alignment: .top) {
            Color.liqFg.ignoresSafeArea()
            TabView(selection: Binding(get: { page }, set: { onPage($0) })) {
                ForEach(photos.indices, id: \.self) { i in
                    slide(i)
                        .accessibilityLabel(Text("Photo \(i + 1) of \(photos.count)"))
                        .tag(i)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .ignoresSafeArea()

            Text("\(page + 1) / \(photos.count)")
                .font(F.manrope(14, .bold))
                .monospacedDigit()
                .foregroundColor(.white)
                .padding(.top, 18)

            HStack {
                Spacer(minLength: 0)
                // Draws 36 and answers at 44, like every close control in the app.
                Button(action: onClose) {
                    ZStack {
                        // `rgba(255,255,255,.16)` -- the close disc on the dark viewer.
                        Circle().fill(Color.white.opacity(0.16)).frame(width: 36, height: 36)
                        BrandIconView(icon: .close, size: 18, stroke: 2.2, tint: .white)
                    }
                    .minTapTarget(alignment: .center)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("Close photos"))
            }
            .padding(.top, 8)
            .padding(.trailing, 12)
        }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityLabel(Text("\(name)'s photos"))
    }

    @ViewBuilder private func slide(_ i: Int) -> some View {
        let placeholder = CSSLinearGradient(angle: 155, colors: photoPlaceholders[i % photoPlaceholders.count],
                                            stops: [0, 0.5, 1])
        if let url = photos[i] {
            AsyncImage(url: url) { phase in
                if let image = phase.image { image.resizable().scaledToFit() } else { placeholder }
            }
        } else {
            placeholder
        }
    }
}
