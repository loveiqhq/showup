//
//  SearchSections.swift
//  ShowUp · Date search 01's stack, section by section (SHOWUP-184)
//
//  The Swift twin of `search/SearchSections.kt`; read that file for the measurements behind each
//  section. Every section takes values and draws them; an absent source draws nothing.
//
//  LETTER SPACING: the reference's NUMBERS are pixels, its STRINGS ems. React appends `px` to a bare
//  number in an inline style, so `letterSpacing: 0.1` is 0.1pt of `.tracking` here, and only
//  `'-0.01em'` is an em (size x -0.01). Read as ems, every capitals title came out some 15% wider
//  than the sheet's (the 184 evidence review, 24 Oct 2026).
//

import AVFoundation
import SwiftUI

// MARK: - the kit's photo placeholders

/// The kit's six photo placeholders (`SEARCH_V2_PROFILE.photoColors`, 155deg): what a slot shows while
/// its photo loads, if it cannot load, and in every preview -- never a grey box. Illustration data,
/// not tokens: they are the kit's stand-ins for photographs.
let photoPlaceholders: [[Color]] = [
    [Color(hex: 0xFCC8A7), Color(hex: 0xE89B7A), Color(hex: 0xB86B5C)],
    [Color(hex: 0xD6E8E6), Color(hex: 0x99C7C3), Color(hex: 0x4D8F88)],
    [Color(hex: 0xF5C8A2), Color(hex: 0xB2C5B4), Color(hex: 0x7A9A86)],
    [Color(hex: 0xFCDCDC), Color(hex: 0xE69CB5), Color(hex: 0xB85B7C)],
    [Color(hex: 0xEAD9F4), Color(hex: 0xC8A6E8), Color(hex: 0x8B5CF6)],
    [Color(hex: 0xFFE2C5), Color(hex: 0xFFB58A), Color(hex: 0xE07A4C)],
]
let placeholderStops: [[CGFloat]] = [
    [0, 0.40, 1], [0, 0.50, 1], [0, 0.70, 1], [0, 0.50, 1], [0, 0.40, 1], [0, 0.40, 1],
]

/// A CSS `linear-gradient(<angle>deg, ...)` drawn as CSS draws it: the gradient line through the centre
/// at that angle, long enough that its ends touch the box's corners. SwiftUI's unit points run corner
/// to corner, which is right only at 135 degrees on a square.
struct CSSLinearGradient: View {
    let angle: Double
    let colors: [Color]
    let stops: [CGFloat]

    var body: some View {
        GeometryReader { g in
            let a = angle * .pi / 180
            let dx = sin(a)
            let dy = -cos(a)
            let w = max(g.size.width, 1)
            let h = max(g.size.height, 1)
            let half = (abs(w * dx) + abs(h * dy)) / 2
            LinearGradient(
                stops: zip(colors, stops).map { Gradient.Stop(color: $0, location: $1) },
                startPoint: UnitPoint(x: (w / 2 - dx * half) / w, y: (h / 2 - dy * half) / h),
                endPoint: UnitPoint(x: (w / 2 + dx * half) / w, y: (h / 2 + dy * half) / h))
        }
    }
}

// MARK: - the photo slot

/// A photo: `object-fit: cover`, its corner, the kit's washes over it (the highlight at .35, the shade
/// at .40, the bottom 40% darkening that keeps white type legible), and whatever the slot carries on
/// top. The whole slot is the tap that opens the viewer. A nil `url` draws the placeholder alone.
struct SearchPhoto<Overlay: View>: View {
    let url: URL?
    let index: Int
    let height: CGFloat
    let radius: CGFloat
    let name: String
    let onTap: () -> Void
    @ViewBuilder var overlay: () -> Overlay

    var body: some View {
        Button(action: onTap) {
            ZStack(alignment: .topLeading) {
                CSSLinearGradient(angle: 155,
                                  colors: photoPlaceholders[index % photoPlaceholders.count],
                                  stops: placeholderStops[index % placeholderStops.count])
                if let url {
                    AsyncImage(url: url) { phase in
                        if let image = phase.image { image.resizable().scaledToFill() }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()
                }
                PhotoWashes()
                overlay()
            }
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .clipShape(RoundedRectangle(cornerRadius: radius))
            .contentShape(RoundedRectangle(cornerRadius: radius))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text("Photo \(index + 1) of \(name)"))
        .accessibilityHint(Text("Open photo"))
        .accessibilityAddTraits(.isImage)
    }
}

/// The painterly washes, softened for a loaded photo exactly as the kit softens them.
private struct PhotoWashes: View {
    var body: some View {
        GeometryReader { g in
            let w = g.size.width
            let h = g.size.height
            ZStack {
                RadialGradient(colors: [Color.white.opacity(0.30 * 0.35), .clear],
                               center: UnitPoint(x: 0.35, y: 0.28),
                               startRadius: 0, endRadius: max(w * 0.6, h * 0.4) * 0.65)
                RadialGradient(colors: [Color.black.opacity(0.18 * 0.40), .clear],
                               center: UnitPoint(x: 0.70, y: 0.75),
                               startRadius: 0, endRadius: max(w, h) * 0.5 * 0.70)
                LinearGradient(colors: [.clear, Color.black.opacity(0.42)],
                               startPoint: UnitPoint(x: 0.5, y: 0.60), endPoint: .bottom)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

// MARK: - the hero (callouts 4 to 9)

/// Photo 1 at 460 with the distance pill, the name and the motivation pill on it, and the meet card
/// hanging 22 below its bottom edge -- the stack's one overlap. The hero measures 460, so the 18 spacer
/// after it keeps the next section clear. THE NAME SITS 44 ABOVE THE CARD'S REAL TOP, measured, not at
/// a fixed `bottom: 80`: at the largest type the card grows upward and a fixed 80 put the name under it.
struct SearchHero: View {
    let profile: SearchProfile
    let zone: TimeZone
    let onPhoto: (Int) -> Void

    var body: some View {
        HeroLayout {
            SearchPhoto(url: profile.photos.first ?? nil, index: 0, height: 460, radius: 28,
                        name: profile.name, onTap: { onPhoto(0) }) {
                DistancePill(parts: distanceParts(profile.distanceKm, profile.district),
                             label: distanceLine(profile.distanceKm, profile.district))
                    .padding(16)
            }
            HeroName(profile: profile)
            MeetCard(profile: profile, zone: zone)
        }
    }
}

/// Photo, name, card: the photo sets the height; the card hangs 22 below it, 16 in from each side;
/// the name sits 44 above the card, 18 in.
private struct HeroLayout: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard subviews.count == 3 else { return .zero }
        let w = proposal.width ?? 358
        return CGSize(width: w, height: subviews[0].sizeThatFits(ProposedViewSize(width: w, height: nil)).height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count == 3 else { return }
        let w = bounds.width
        let photoHeight = subviews[0].sizeThatFits(ProposedViewSize(width: w, height: nil)).height
        subviews[0].place(at: bounds.origin, proposal: ProposedViewSize(width: w, height: photoHeight))
        let cardWidth = max(0, w - 32)
        let cardHeight = subviews[2].sizeThatFits(ProposedViewSize(width: cardWidth, height: nil)).height
        let cardY = bounds.minY + photoHeight + 22 - cardHeight
        let nameWidth = max(0, w - 36)
        let nameHeight = subviews[1].sizeThatFits(ProposedViewSize(width: nameWidth, height: nil)).height
        subviews[1].place(at: CGPoint(x: bounds.minX + 18, y: cardY - 44 - nameHeight),
                          proposal: ProposedViewSize(width: nameWidth, height: nameHeight))
        subviews[2].place(at: CGPoint(x: bounds.minX + 16, y: cardY),
                          proposal: ProposedViewSize(width: cardWidth, height: cardHeight))
    }
}

/// The name, white over the photo with its shadow, and the motivation pill 8 under it. The name is a
/// CSS line box, 34 tall at `line-height: 1`.
private struct HeroName: View {
    let profile: SearchProfile

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(nameLine(profile.name, profile.age))
                .font(F.lora(34, bold: true))
                .tracking(34 * -0.01)
                .foregroundColor(.white)
                // `text-shadow: 0 2px 12px rgba(0,0,0,.35)`.
                .shadow(color: Color.black.opacity(0.35), radius: 6, y: 2)
                .cssLineBox(34)
                .accessibilityAddTraits(.isHeader)
            if let m = motivationOf(profile.motivation) {
                MotivationPill(label: m.title, icon: m.icon)
            }
        }
    }
}

/// `~1 km · Neukölln, Berlin` on the photo: the three runs 6 apart, as the sheet renders them. The
/// district takes what is left and wraps at the largest type; the distance and the dot never break.
private struct DistancePill: View {
    let parts: [String]
    let label: String

    var body: some View {
        HStack(spacing: 6) {
            BrandIconView(icon: .mapPin, size: 13, stroke: 2.2, tint: .liqFg)
            ForEach(Array(parts.enumerated()), id: \.offset) { i, part in
                let last = i == parts.count - 1
                Text(part)
                    .font(F.manrope(12, .bold))
                    .foregroundColor(.liqFg)
                    .lineLimit(last ? nil : 1)
                    .fixedSize(horizontal: !last, vertical: true)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        // `rgba(255,255,255,.85)`.
        .background(Capsule().fill(Color.white.opacity(0.85)))
        // One phrase for VoiceOver, not three fragments and a lone dot.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(label))
    }
}

private struct MotivationPill: View {
    let label: String
    let icon: BrandIcon

    var body: some View {
        HStack(spacing: 6) {
            BrandIconView(icon: icon, size: 13, stroke: 2.2, tint: .white)
            Text(label).font(F.manrope(12, .bold)).tracking(0.02).foregroundColor(.white)
        }
        .padding(.horizontal, 11)
        .padding(.vertical, 5)
        // `rgba(255,255,255,.22)` with a `.30` edge -- the glass.
        .background(Capsule().fill(Color.white.opacity(0.22)))
        .overlay(Capsule().strokeBorder(Color.white.opacity(0.30), lineWidth: 1))
    }
}

/// Callout 8: `You could meet at:` / `19:00 today`, and the Show-up count. One row as drawn; where the
/// lines and the count do not fit side by side the count moves under the lines -- measured: the drawn
/// row fits from 390 wide; the reference itself overflows at 375 (see `SearchSections.kt`).
private struct MeetCard: View {
    let profile: SearchProfile
    let zone: TimeZone

    var body: some View {
        Card(padding: EdgeInsets(top: 12, leading: 14, bottom: 12, trailing: 14)) {
            HStack(spacing: 12) {
                ZStack {
                    // `rgba(254,104,57,.12)`.
                    RoundedRectangle(cornerRadius: 12).fill(Color.liqOrange.opacity(0.12))
                    BrandIconView(icon: .clock, size: 18, stroke: 2.2, tint: .liqOrange)
                }
                .frame(width: 38, height: 38)
                .accessibilityHidden(true)
                MeetRowLayout {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(SearchCopy.meetLabel).font(F.manrope(12, .semibold)).foregroundColor(.liqSubtle)
                        Text(meetLine(profile.meetAt, day: profile.meetDay, zone: zone))
                            .font(F.manrope(15, .bold)).foregroundColor(.liqFg)
                            .fitAnchor(FitAnchorID.searchMeetLine)
                    }
                    .accessibilityElement(children: .combine)
                    ShowUpCount(count: profile.attendedCount, size: .xs)
                        .fitAnchor(FitAnchorID.searchMeetCount)
                }
            }
        }
    }
}

/// The reference's row, `flex: 1` lines then the count: space-between when both fit with at least the
/// 12 gap, the count under the lines (6 apart) when they do not. Both centred on the row, as
/// `align-items: center` draws them.
private struct MeetRowLayout: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard subviews.count == 2 else { return .zero }
        let w = proposal.width ?? .infinity
        let lines = subviews[0].sizeThatFits(.unspecified)
        let count = subviews[1].sizeThatFits(.unspecified)
        if lines.width + 12 + count.width <= w {
            return CGSize(width: proposal.width ?? lines.width + 12 + count.width, height: max(lines.height, count.height))
        }
        let wrapped = subviews[0].sizeThatFits(ProposedViewSize(width: proposal.width, height: nil))
        return CGSize(width: proposal.width ?? max(wrapped.width, count.width), height: wrapped.height + 6 + count.height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count == 2 else { return }
        let lines = subviews[0].sizeThatFits(.unspecified)
        let count = subviews[1].sizeThatFits(.unspecified)
        if lines.width + 12 + count.width <= bounds.width {
            subviews[0].place(at: CGPoint(x: bounds.minX, y: bounds.midY), anchor: .leading, proposal: .unspecified)
            subviews[1].place(at: CGPoint(x: bounds.maxX, y: bounds.midY), anchor: .trailing, proposal: .unspecified)
        } else {
            let wrapped = subviews[0].sizeThatFits(ProposedViewSize(width: bounds.width, height: nil))
            subviews[0].place(at: bounds.origin, proposal: ProposedViewSize(width: bounds.width, height: nil))
            subviews[1].place(at: CGPoint(x: bounds.minX, y: bounds.minY + wrapped.height + 6), proposal: .unspecified)
        }
    }
}

// MARK: - the overlap (callouts 10, 11, 13, 20)

/// A stored id and the label the app's own table gives it.
private struct LabelledValue: Identifiable {
    let id: String
    let label: String
}

/// A shared vibe or interest: the orange tone selected, with a check, announced as shared.
struct SharedChip: View {
    let label: String

    var body: some View {
        Chip(label: label, selected: true, tone: .orange, leading: { ink in
            BrandIconView(icon: .check, size: 13, stroke: 2.6, tint: ink)
        })
        .accessibilityElement(children: .combine)
        .accessibilityValue(Text("shared"))
    }
}

/// Callout 10: at most three shared vibes and `+N`. Not drawn when nothing is shared.
struct SharedVibesCard: View {
    let name: String
    let row: SharedVibesRow

    var body: some View {
        if !row.shown.isEmpty {
            Card(borderColor: .liqOrange, borderWidth: 1.5, padding: EdgeInsets(top: 14, leading: 16, bottom: 14, trailing: 16)) {
                Text(SearchCopy.sharedVibesTitle(name).uppercased())
                    .font(F.manrope(11, .heavy)).tracking(0.1).foregroundColor(.liqOrange)
                    .padding(.bottom, 10)
                WrapLayout(spacing: 8, lineSpacing: 8) {
                    ForEach(row.shown, id: \.self) { id in SharedChip(label: vibeLabel(id) ?? id) }
                    if row.more > 0 {
                        Text("+\(row.more)").font(F.manrope(13, .bold)).foregroundColor(.liqOrange)
                            .frame(minHeight: ChipSize.md.height)
                    }
                }
            }
        }
    }
}

/// Callout 13: every vibe today, the shared ones marked.
struct VibeTodayCard: View {
    let name: String
    let vibes: [String]
    let mine: Set<String>

    var body: some View {
        let labelled = vibes.compactMap { id in vibeLabel(id).map { LabelledValue(id: id, label: $0) } }
        if !labelled.isEmpty {
            Card {
                CapsTitle(text: SearchCopy.vibeTodayTitle(name))
                WrapLayout(spacing: 8, lineSpacing: 8) {
                    ForEach(labelled) { item in
                        if mine.contains(item.id) { SharedChip(label: item.label) } else { Chip(item.label, tone: .orange) }
                    }
                }
            }
        }
    }
}

/// Callout 20: what they are into, `· k in common` only from one shared.
struct SharedInterestsBlock: View {
    let name: String
    let interests: [String]
    let mine: Set<String>

    var body: some View {
        let labelled = interests.compactMap { id in interestLabel(id).map { LabelledValue(id: id, label: $0) } }
        if !labelled.isEmpty {
            let shared = sharedInterests(labelled.map(\.id), mine: mine)
            Card {
                SectionTitle(title: SearchCopy.interestsTitle(name),
                             sub: interestsSub(name, total: labelled.count, common: shared.count))
                WrapLayout(spacing: 8, lineSpacing: 8) {
                    ForEach(labelled) { item in
                        if shared.contains(item.id) { SharedChip(label: item.label) } else { Chip(item.label, tone: .orange) }
                    }
                }
            }
        }
    }
}

// MARK: - titles

/// The reference's `SectionTitle`: Lora 700 / 18, its sub 12 / 500 four below, 14 under the block.
struct SectionTitle: View {
    let title: String
    var sub: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).font(F.lora(18, bold: true)).tracking(18 * -0.005).foregroundColor(.liqFg)
            if let sub {
                Text(sub).font(F.manrope(12, .medium)).tracking(0.01).foregroundColor(.liqSubtle).padding(.top, 4)
            }
        }
        .padding(.bottom, 14)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}

/// The capitals titles of vibe today and charms: 13 / 700, `letterSpacing: 0.08` (px), 10 under.
struct CapsTitle: View {
    let text: String

    var body: some View {
        Text(text.uppercased())
            .font(F.manrope(13, .bold)).tracking(0.08).foregroundColor(.liqSubtle)
            .padding(.bottom, 10)
            .accessibilityAddTraits(.isHeader)
    }
}

// MARK: - the highlight (callout 12)

struct HighlightCard: View {
    let name: String
    let uplift: String

    var body: some View {
        Card(fill: AnyShapeStyle(LinearGradient(colors: Color.suGradBlush, startPoint: .top, endPoint: .bottom))) {
            HStack(spacing: 6) {
                BrandIconView(icon: .sparkles, size: 14, stroke: 2.4, tint: .liqOrange)
                Text(SearchCopy.highlightTitle(name).uppercased())
                    .font(F.manrope(11, .heavy)).tracking(0.1).foregroundColor(.liqOrange)
            }
            .padding(.bottom, 8)
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
            // Straight quotes, as the reference writes them and the sheet draws them; `text-wrap: pretty`.
            PrettyText("\"\(uplift)\"",
                       font: TypeMetrics.uiFont(PS.loraMediumItalic, 18, fallback: .italicSystemFont(ofSize: 18)),
                       lineHeight: 18 * 1.35, color: .liqFg)
        }
    }
}

// MARK: - prompts (callouts 15)

/// A written prompt: the topic in Lora italic violet (`text-wrap: balance`), the answer under it
/// (`pretty`). The lilac card, no shadow.
struct PromptCard: View {
    let prompt: SearchPrompt

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            PrettyText(topicText(prompt.topicId),
                       font: TypeMetrics.uiFont(PS.loraBoldItalic, 17, fallback: .italicSystemFont(ofSize: 17)),
                       lineHeight: 17 * 1.3, color: .liqPurple, balance: true)
                .accessibilityAddTraits(.isHeader)
            PrettyText(prompt.answer,
                       font: TypeMetrics.uiFont(PS.manropeMedium, 15, fallback: .systemFont(ofSize: 15, weight: .medium)),
                       lineHeight: 15 * 1.5, color: .liqFg)
                .padding(.top, 10)
        }
        .padding(EdgeInsets(top: 18, leading: 20, bottom: 20, trailing: 20))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 20).fill(Gradients.lilac()))
        // `rgba(129,42,236,.10)`, the card's edge.
        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(Color.liqPurple.opacity(0.10), lineWidth: 1))
    }
}

// MARK: - voice and video (callouts 16, 18)

/// The quoted prompt line under a voice or video card: Lora italic 14 / 1.45, 12 above, in the
/// reference's straight quotes, `text-wrap: pretty`.
private struct PromptQuote: View {
    let promptId: String

    var body: some View {
        if let text = MediaPrompts.all.first(where: { $0.id == promptId })?.display {
            PrettyText("\"\(text)\"",
                       font: TypeMetrics.uiFont(PS.loraItalic, 14, fallback: .italicSystemFont(ofSize: 14)),
                       lineHeight: 14 * 1.45, color: .liqNeutral)
                .padding(.top, 12)
        }
    }
}

/// Callout 16: the voice note. Plays on tap only; the played part of the waveform follows the
/// playhead -- and only the playhead. The reference paints bars 0 to 17 violet at rest, a static
/// picture of progress; drawn on a note nobody has played it would say 37% was heard. All grey until
/// it plays, by decision (the 184 evidence review, 24 Oct 2026).
struct VoiceCard: View {
    let name: String
    let media: SearchMedia
    let playing: SearchPlayback?
    let onToggle: () -> Void

    var body: some View {
        Card {
            SectionTitle(title: SearchCopy.voiceTitle(name), sub: voiceSub(media.durationMs))
            HStack(spacing: 14) {
                Button(action: onToggle) {
                    ZStack {
                        Circle().fill(Gradients.sunset())
                        if playing != nil {
                            BrandIconView(icon: .pause, size: 18, stroke: 2, tint: .white)
                        } else {
                            // `margin-left: 2` in the kit: a triangle centred on its box looks left of centre.
                            BrandIconView(icon: .play, size: 18, tint: .white).offset(x: 1)
                        }
                    }
                    .frame(width: 46, height: 46)
                    .background(VioletGlow())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(playing != nil ? "Pause \(name)'s voice" : "Play \(name)'s voice"))
                VoiceBars(progress: playing?.progress ?? 0)
                    .frame(height: 36)
                    .accessibilityHidden(true)
                Text(formatTakeLength(media.durationMs))
                    .font(F.manrope(13, .bold)).monospacedDigit().foregroundColor(.liqSubtle)
            }
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color.liqRaised))
            PromptQuote(promptId: media.promptId)
        }
    }
}

/// `--liq-shadow-violet` under a 46 disc: `0 8px 20px` at .28 and `0 2px 6px` at .18.
private struct VioletGlow: View {
    var body: some View {
        ZStack {
            Circle().fill(Color.liqPurple).shadow(color: Color.liqPurple.opacity(0.28), radius: 10, y: 8)
            Circle().fill(Color.liqPurple).shadow(color: Color.liqPurple.opacity(0.18), radius: 3, y: 2)
        }
    }
}

/// The 48 bars, `gap: 2.5`, each `flex: 1` and its share of the 36, never under 4.
private struct VoiceBars: View {
    let progress: Double

    var body: some View {
        let played = Int(progress * Double(voiceBars.count))
        HStack(alignment: .center, spacing: 2.5) {
            ForEach(Array(voiceBars.enumerated()), id: \.offset) { i, v in
                Capsule()
                    // `rgba(29,17,41,.18)` for a bar not yet played.
                    .fill(i < played ? Color.liqPurple : Color.liqFg.opacity(0.18))
                    .frame(maxWidth: .infinity)
                    .frame(height: max(4, 36 * v))
            }
        }
    }
}

/// Callout 18: the video. The poster is the profile's fourth photo, as the kit draws it; the frames
/// replace it while it plays. Plays with sound, on tap only.
struct VideoCard: View {
    let name: String
    let media: SearchMedia
    let posterURL: URL?
    let playing: SearchPlayback?
    let player: AVPlayer?
    let onToggle: () -> Void

    var body: some View {
        Card {
            SectionTitle(title: SearchCopy.videoTitle(name), sub: videoSub(media.durationMs))
            Button(action: onToggle) {
                ZStack {
                    CSSLinearGradient(angle: 155, colors: photoPlaceholders[3], stops: placeholderStops[3])
                    if let posterURL {
                        AsyncImage(url: posterURL) { phase in
                            if let image = phase.image { image.resizable().scaledToFill() }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .clipped()
                    }
                    if playing != nil, let player {
                        VideoSurface(player: player)
                    } else {
                        RadialGradient(colors: [Color.white.opacity(0.30), .clear],
                                       center: UnitPoint(x: 0.40, y: 0.35), startRadius: 0, endRadius: 220 * 0.6)
                        ZStack {
                            // `rgba(255,255,255,.92)`, lifted `0 8px 24px rgba(0,0,0,.16)`.
                            Circle().fill(Color.white.opacity(0.92))
                                .shadow(color: Color.black.opacity(0.16), radius: 12, y: 8)
                            BrandIconView(icon: .play, size: 22, tint: .liqFg).offset(x: 1.5)
                        }
                        .frame(width: 58, height: 58)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 220)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .contentShape(RoundedRectangle(cornerRadius: 16))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(playing != nil ? "Pause \(name)'s video" : "Play \(name)'s video"))
            PromptQuote(promptId: media.promptId)
        }
    }
}

// MARK: - Essentials, How I live, charms (callouts 17, 19, 21)

struct EssentialsCard: View {
    let name: String
    let rows: [Essential]

    var body: some View {
        if !rows.isEmpty {
            Card {
                SectionTitle(title: SearchCopy.essentialsTitle(name))
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(rows, id: \.row) { r in
                        HStack(spacing: 12) {
                            BrandIconView(icon: r.row.icon, size: 16, stroke: r.row.isKit ? 2 : 1.8, tint: .liqPurple)
                                .frame(width: 18)
                            Text(r.row.label.uppercased())
                                .font(F.manrope(11, .bold)).tracking(0.06).foregroundColor(.liqSubtle)
                                .frame(minWidth: 76, alignment: .leading)
                            Text(r.value)
                                .font(F.manrope(14, .regular)).foregroundColor(.liqFg)
                                .lineSpacing(TypeMetrics.lineSpacing(PS.manropeRegular, size: 14, multiple: 1.35))
                                .cssLineBox(14 * 1.35)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .accessibilityElement(children: .combine)
                    }
                }
            }
        }
    }
}

struct HowILiveCard: View {
    let name: String
    let tiles: [LifestyleTile]

    var body: some View {
        if !tiles.isEmpty {
            Card {
                SectionTitle(title: SearchCopy.everydayTitle(name), sub: SearchCopy.everydaySub)
                // `grid-template-columns: 1fr 1fr; gap: 10`, row by row so the two tiles of a row share
                // its height -- a tall answer must not leave its neighbour short.
                VStack(spacing: 10) {
                    ForEach(Array(stride(from: 0, to: tiles.count, by: 2)), id: \.self) { start in
                        HStack(spacing: 10) {
                            LifestyleTileView(tile: tiles[start])
                            if start + 1 < tiles.count {
                                LifestyleTileView(tile: tiles[start + 1])
                            } else {
                                Color.clear.frame(maxWidth: .infinity)
                            }
                        }
                        .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
    }
}

private struct LifestyleTileView: View {
    let tile: LifestyleTile

    var body: some View {
        let icon = lifestyleIcon(tile.field)
        // The kit's own marks at 15 / stroke 2; Search's section marks at 16 / 1.8 (`SearchIcon`).
        let kit = icon == .zap || icon == .clock || icon == .users
        HStack(spacing: 10) {
            ZStack {
                RoundedRectangle(cornerRadius: 10).fill(Color.liqElevated)
                BrandIconView(icon: icon, size: kit ? 15 : 16, stroke: kit ? 2 : 1.8, tint: .liqPurple)
            }
            .frame(width: 30, height: 30)
            VStack(alignment: .leading, spacing: 0) {
                Text(tile.field.label.uppercased())
                    .font(F.manrope(10.5, .bold)).tracking(0.06).foregroundColor(.liqSubtle)
                Text(tile.value)
                    .font(F.manrope(13.5, .semibold)).foregroundColor(.liqFg)
                    .cssLineBox(13.5 * 1.25)
                    .padding(.top, 1)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.liqRaised))
        .accessibilityElement(children: .combine)
    }
}

struct CharmsCard: View {
    let charms: [Charm]

    var body: some View {
        if !charms.isEmpty {
            Card {
                CapsTitle(text: SearchCopy.charmsTitle)
                WrapLayout(spacing: 8, lineSpacing: 8) {
                    ForEach(Array(charms.enumerated()), id: \.offset) { _, c in
                        HStack(spacing: 6) {
                            BrandIconView(icon: c.star ? .star : .gift, size: 13, stroke: 2, tint: .liqFg)
                            Text(c.label).font(F.manrope(12, .semibold)).foregroundColor(.liqFg)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Capsule().fill(Color.liqRaised))
                    }
                }
            }
        }
    }
}
