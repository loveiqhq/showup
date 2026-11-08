// ChatParts.swift
// ShowUp · the coordinate chat's parts (SHOWUP-194): `ChatHeader`, `ChatBubble`, `ThreadScroll`,
// `BigActionChip` and the chip grid, from `screen-chat-reference.jsx`, with its numbers. Android's
// `ChatParts.kt`, part for part.
//
// Each part takes values and returns pixels. Where the reference draws a control smaller than the 44 pt
// floor (the back chevron 34), the drawing keeps its size and the touch area is laid over it
// (`overflowTapTarget`). Where CSS counts a border in a box's size and SwiftUI only draws it, the padding
// is the reference's plus the border, as on the Date screen.

import SwiftUI
import UIKit

/// A Lora / Manrope UIFont for `PrettyText`, which lays out through UIKit.
func chatFont(_ name: String, _ size: CGFloat, _ weight: UIFont.Weight) -> UIFont {
    TypeMetrics.uiFont(name, size, fallback: .systemFont(ofSize: size, weight: weight))
}

/// `text` with its last two words joined by a no-break space, so a wrapped line never ends on one word
/// alone -- for a SwiftUI `Text` or a field's prompt, which `PrettyText` cannot draw. The words and the copy
/// are unchanged; only where the line may break is.
func lastWordsTogether(_ text: String) -> String {
    guard let space = text.lastIndex(of: " ") else { return text }
    return text.replacingCharacters(in: space...space, with: "\u{00A0}")
}

/// `[(text, bold)]` runs for `PrettyText` from a paragraph and the parts of it the reference sets bold.
func boldRuns(_ text: String, _ bold: [String]) -> [(String, Bool)] {
    var marks = [(Range<String.Index>)]()
    for run in bold { if let r = text.range(of: run) { marks.append(r) } }
    marks.sort { $0.lowerBound < $1.lowerBound }
    var runs: [(String, Bool)] = []
    var at = text.startIndex
    for r in marks where r.lowerBound >= at {
        if at < r.lowerBound { runs.append((String(text[at..<r.lowerBound]), false)) }
        runs.append((String(text[r]), true))
        at = r.upperBound
    }
    if at < text.endIndex { runs.append((String(text[at...]), false)) }
    return runs
}

/// The back chevron: drawn 34 with `chevron-left` 23 / 2.2, answering at 44.
struct ChatBackButton: View {
    let onBack: () -> Void

    var body: some View {
        Button(action: onBack) {
            BrandIconView(icon: .chevronLeft, size: 23, stroke: 2.2, tint: .liqFg)
                .frame(width: 34, height: 34)
        }
        .buttonStyle(PressScale())
        // Across as well: drawn 34 wide, it answered 34 wide (the SHOWUP-194 fit sweep in CI).
        .overflowTapTarget(wide: true)
        .accessibilityLabel(ChatCopy.back)
    }
}

/// `lock` 12 / 2.3 and a line, 11.5 / 600, subtle, centred: the start grid's and the tutorial's notes.
struct LockLine: View {
    let text: String

    var body: some View {
        HStack(spacing: 6) {
            BrandIconView(icon: .lock, size: 12, stroke: 2.3, tint: .liqSubtle)
            // Never one word alone on the last line: at 320 it ended `... never what you` / `type.` (the 8 Nov
            // 2026 device sweep). Android wraps it `pretty`.
            Text(lastWordsTogether(text)).font(F.manrope(11.5, .semibold)).foregroundColor(.liqSubtle)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - ChatHeader

/// Back, the avatar 40, the name (Lora 700 17) with `DATE ON`, and `{day} · {time} · {place} · {countdown}`
/// on one line, truncated -- always the latest AGREED time and place. Glass cream 92%, a soft rule under it,
/// running up under the status bar.
struct ChatHeader: View {
    let date: ActiveDate
    let now: Date
    let zone: TimeZone
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            ChatBackButton(onBack: onBack)
            MatchPhoto(name: date.match.name, photoURL: date.match.photoUrl, size: 40)
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: 7) {
                    // One line wherever it fits; at the largest type on a narrow phone a name too wide for the
                    // room beside `DATE ON` shrinks, or wraps between its words, rather than be cut.
                    FitWords(text: date.match.name, font: { s in F.lora(17 * s, bold: true) }) { s in
                        Text(date.match.name).font(F.lora(17 * s, bold: true)).foregroundColor(.liqFg)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    dateOn
                }
                // One line wherever it fits, as the reference sets it; at large type it wraps rather than cut the
                // place or the countdown off -- the header always shows the agreed time and place.
                line.font(F.manrope(12, .semibold)).fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(EdgeInsets(top: 4, leading: 10, bottom: 10, trailing: 10))
        // The header's type grows to AX1 -- 165 %, as Android's does -- and no further, as a bar's title
        // does. At AX5 its line wrapped to four and the header took 310 of a short window's 380; at 200 %
        // with the keys up on a 320 x 568 screen it took half of what they leave, and the field no longer
        // fit (the SHOWUP-194 fit and keyboard sweeps). The Date screen shows the same time and place.
        .dynamicTypeSize(...DynamicTypeSize.accessibility1)
        .fitPinnedBar()
        .background(Color.liqTabBarGlass.ignoresSafeArea(edges: .top))
        .overlay(alignment: .bottom) { Rectangle().fill(Color.liqBorderSoft).frame(height: 1) }
    }

    private var line: Text {
        let base = Text(headerLine(date, zone: zone)).foregroundColor(.liqMuted)
        guard let countdown = countdown(now: now, meetAt: date.meetAt) else { return base }
        return base + Text(" · ").foregroundColor(.liqMuted) + Text(countdown).foregroundColor(.liqOrange)
    }

    /// `DATE ON`: success 12% pill, pad 2 7, the 5 dot, 10 / 800 in the success ink.
    private var dateOn: some View {
        HStack(spacing: 4) {
            Circle().fill(Color.liqSuccess).frame(width: 5, height: 5)
            Text(ChatCopy.dateOn).font(F.manrope(10, .heavy)).tracking(0.04).foregroundColor(.liqSuccessFg)
                .lineLimit(1).fixedSize()
        }
        .padding(.horizontal, 7)
        .padding(.vertical, 2)
        .background(Capsule().fill(Color.liqSuccess.opacity(0.12)))
    }
}

// MARK: - ChatBubble

/// One message. Mine: right, sunset, white, corners 18 18 5 18, the violet glow, then the time and the read
/// receipt. Theirs: left, white with a 1 px soft border, corners 18 18 18 5, the small shadow, and the avatar
/// 26 beside the last of a run. A chip message carries its badge above the text. At most 78% of the thread.
struct ChatBubble: View {
    let message: ChatMessage
    let name: String
    let photoURL: URL?
    let showAvatar: Bool
    let zone: TimeZone
    /// The thread's width: the bubble is at most 78% of it.
    let width: CGFloat

    var body: some View {
        let mine = message.fromYou
        HStack(alignment: .bottom, spacing: 8) {
            if mine { Spacer(minLength: 0) }
            if !mine {
                ZStack { if showAvatar { MatchPhoto(name: name, photoURL: photoURL, size: 26) } }
                    .frame(width: 26)
            }
            VStack(alignment: mine ? .trailing : .leading, spacing: 5) {
                bubble(mine)
                HStack(spacing: 5) {
                    Text(messageTime(message.sentAt, zone: zone)).font(F.manrope(11, .semibold)).foregroundColor(.liqSubtle)
                    if mine { receipt }
                }
                .padding(.horizontal, 4)
            }
            .frame(maxWidth: width * 0.78, alignment: mine ? .trailing : .leading)
            if !mine { Spacer(minLength: 0) }
        }
    }

    private func bubble(_ mine: Bool) -> some View {
        let shape = UnevenRoundedRectangle(topLeadingRadius: 18, bottomLeadingRadius: mine ? 18 : 5,
                                           bottomTrailingRadius: mine ? 5 : 18, topTrailingRadius: 18)
        return VStack(alignment: .leading, spacing: 6) {
            if let reason = message.reason { ReasonBadge(reason: reason, onLight: !mine) }
            PrettyText(message.body, font: chatFont(PS.manropeMedium, 14.5, .medium), lineHeight: 14.5 * 1.42,
                       color: mine ? .white : .liqFg, hugs: true)
        }
        // Pad 9 13 11, plus theirs' 1 px border, which CSS counts in the box.
        .padding(mine ? EdgeInsets(top: 9, leading: 13, bottom: 11, trailing: 13)
                      : EdgeInsets(top: 10, leading: 14, bottom: 12, trailing: 14))
        .background {
            if mine {
                ShadowedShape(shape: shape, fill: Gradients.sunset(), shadows: shadowViolet)
            } else {
                ShadowedShape(shape: shape, fill: Color.liqElevated, shadows: shadowSm)
            }
        }
        .overlay { if !mine { shape.strokeBorder(Color.liqBorderSoft, lineWidth: 1) } }
    }

    /// `check` 12 / 3 and `Read` in violet, or `Delivered` in subtle ink: one word to VoiceOver.
    private var receipt: some View {
        let read = message.read == true
        let ink: Color = read ? .liqPurple : .liqSubtle
        let word = read ? ChatCopy.read : ChatCopy.delivered
        return HStack(spacing: 3) {
            BrandIconView(icon: .check, size: 12, stroke: 3, tint: ink)
            Text(word).font(F.manrope(11, .semibold)).foregroundColor(ink)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(word)
    }
}

// MARK: - ThreadScroll

/// The scroll's measures, in its own coordinate space: where the content is and how tall the foot is.
private struct ThreadMetrics: Equatable {
    var contentTop: CGFloat = 0
    var contentHeight: CGFloat = 0
    var footHeight: CGFloat = 0
}

private struct ThreadMetricsKey: PreferenceKey {
    static let defaultValue = ThreadMetrics()
    static func reduce(value: inout ThreadMetrics, nextValue: () -> ThreadMetrics) {
        let next = nextValue()
        if next.contentHeight > 0 { value.contentTop = next.contentTop; value.contentHeight = next.contentHeight }
        if next.footHeight > 0 { value.footHeight = next.footHeight }
    }
}

/// The thread: the only scroll in C to E, white, with the reference's always-visible indicator and its reveal
/// rule. Android's `ThreadScroll`, rule for rule:
///
///   · THE FOOT. The guide and its collapsed line sit at the foot of the thread -- `margin-top: auto`: under
///     the messages when they overflow, at the bottom of the viewport when they do not.
///   · THE REVEAL RULE. When the foot changes (`footKey`) or a message arrives, the thread scrolls to the end
///     -- unless the foot is taller than the viewport, when it scrolls to the foot's top less 8.
///   · THE INDICATOR. Track `rgba(29,17,41,.06)`, thumb `.34`, 6 wide, 3 from the right, inset 8, the thumb at
///     least 30; drawn only while the thread overflows, fading over `Motion.tint`.
struct ThreadScroll<Messages: View, Foot: View>: View {
    let padding: EdgeInsets
    let messageCount: Int
    let footKey: String?
    @ViewBuilder let messages: (CGFloat) -> Messages
    @ViewBuilder let foot: () -> Foot

    @State private var metrics = ThreadMetrics()
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { g in
            let viewport = g.size.height
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: 0) {
                        VStack(spacing: 12) { messages(g.size.width - padding.leading - padding.trailing) }
                            .frame(maxWidth: .infinity)
                        if footKey != nil {
                            Spacer(minLength: 0)
                            VStack(spacing: 0) {
                                Color.clear.frame(height: 0).id("thread.foot")
                                foot()
                            }
                            .padding(.top, messageCount > 0 ? 12 : 0)
                            .background(GeometryReader { f in
                                Color.clear.preference(key: ThreadMetricsKey.self, value: ThreadMetrics(footHeight: f.size.height))
                            })
                        }
                        Color.clear.frame(height: 0).id("thread.end")
                    }
                    .padding(padding)
                    .frame(minHeight: viewport, alignment: .top)
                    .background(GeometryReader { c in
                        let frame = c.frame(in: .named("thread"))
                        Color.clear.preference(key: ThreadMetricsKey.self,
                                               value: ThreadMetrics(contentTop: frame.minY, contentHeight: frame.height))
                    })
                }
                .coordinateSpace(name: "thread")
                .scrollIndicators(.hidden)
                .onPreferenceChange(ThreadMetricsKey.self) { metrics = $0 }
                .onAppear { reveal(proxy, viewport: viewport, animated: false) }
                .onChange(of: messageCount) { _, _ in reveal(proxy, viewport: viewport, animated: true) }
                .onChange(of: footKey) { _, _ in reveal(proxy, viewport: viewport, animated: true) }
                // The keyboard takes the thread's height from below, and with the scroll held from the top the
                // newest messages slid out of sight -- Read & reply, a tap on the field, and the message being
                // answered was gone (the SHOWUP-194 review). A thread at its end stays at its end; one the
                // reader scrolled up stays where they left it.
                .onChange(of: viewport) { old, new in
                    let atEnd = -metrics.contentTop >= metrics.contentHeight - old - 1
                    if new < old, atEnd { proxy.scrollTo("thread.end", anchor: .bottom) }
                }
                .overlay(alignment: .topTrailing) { indicator(viewport: viewport) }
            }
        }
    }

    /// The end, or -- for a foot taller than the thread -- the foot's top less 8, a moment after layout.
    private func reveal(_ proxy: ScrollViewProxy, viewport: CGFloat, animated: Bool) {
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 60_000_000)
            let tall = footKey != nil && metrics.footHeight > viewport
            let go = {
                if tall {
                    // The foot's top (a zero-height marker) meets the point 8 below the thread's top.
                    proxy.scrollTo("thread.foot", anchor: UnitPoint(x: 0.5, y: viewport > 0 ? 8 / viewport : 0))
                } else {
                    proxy.scrollTo("thread.end", anchor: .bottom)
                }
            }
            if animated && !reduceMotion { withAnimation(Motion.brand(Motion.screen)) { go() } } else { go() }
        }
    }

    @ViewBuilder private func indicator(viewport: CGFloat) -> some View {
        let overflow = metrics.contentHeight - viewport
        let shown = overflow > 2
        let inset: CGFloat = 8
        let track = max(0, viewport - inset * 2)
        let thumb = max(30, track * (viewport / max(metrics.contentHeight, 1)))
        let ratio = overflow > 0 ? min(1, max(0, -metrics.contentTop / overflow)) : 0
        ZStack(alignment: .top) {
            Capsule().fill(Color.liqFg.opacity(0.06)).frame(width: 6, height: track)
            Capsule().fill(Color.liqFg.opacity(0.34)).frame(width: 6, height: thumb)
                .offset(y: (track - thumb) * ratio)
        }
        .padding(.top, inset)
        .padding(.trailing, 3)
        .opacity(shown ? 1 : 0)
        .animation(reduceMotion ? nil : .easeInOut(duration: Motion.tint), value: shown)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

// MARK: - BigActionChip

/// The reference's `BigActionChip`: a tile on the start grid (pad 15, radius 18, at least 68, icon box 42 / 13
/// with the icon 21, label 15 / 700) or the dock's compact chip (radius 13, at least 44 -- the touch floor, raised
/// from the ticket's 36 on 9 Nov 2026 -- icon box 30 / 9 with the icon 16, label 13 / 700; was: pad 4 10 4 5, radius
/// 11, at least 36, icon box
/// 26 / 8 with the icon 14, label 11.5 / 700). Selected: the tone's fill and a border in the tone's ink;
/// otherwise white with the tone's chip border. The small shadow either way. The full label always.
struct BigActionChip: View {
    let reason: ChatReason
    let active: Bool
    let compact: Bool
    let onTap: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let tone = reason.tone
        let radius: CGFloat = compact ? 13 : 18
        let shape = RoundedRectangle(cornerRadius: radius)
        let size: CGFloat = compact ? 13 : 15
        Button(action: onTap) {
            HStack(spacing: compact ? 9 : 12) {
                IconTile(size: compact ? 30 : 42, radius: compact ? 9 : 13, fill: tone.bg, icon: reason.icon,
                         iconSize: compact ? 16 : 21, tint: tone.fg, stroke: 2.3)
                // The full label always: it wraps, and a word wider than its column shrinks rather than break,
                // as Android's `fitWordsOf`.
                FitWords(text: reason.label, font: { s in F.manrope(size * s, .bold) }) { s in
                    Text(reason.label)
                        .font(F.manrope(size * s, .bold))
                        .lineSpacing(TypeMetrics.lineSpacing(PS.manropeBold, size: size * s, multiple: 1.16))
                        .foregroundColor(tone == .darkRed || tone == .danger ? tone.fg : .liqFg)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            // The reference's padding plus the 1.5 border, which CSS counts and SwiftUI only draws: tiles come
            // out 75 tall as the sheet renders them. The compact chip takes the border at its sides only, so
            // its label wraps as the reference's and it stays the ticket's 36 high.
            .padding(compact ? EdgeInsets(top: 5, leading: 9.5, bottom: 5, trailing: 13.5)
                             : EdgeInsets(top: 16.5, leading: 16.5, bottom: 16.5, trailing: 16.5))
            // The dock chip at the touch floor every other control keeps: 44, raised from the ticket's 36 on
            // 9 Nov 2026 (Esma).
            .frame(maxWidth: .infinity, minHeight: compact ? 44 : 68, maxHeight: .infinity, alignment: .leading)
            .background(ShadowedShape(shape: shape, fill: active ? tone.bg : Color.liqElevated, shadows: shadowSm))
            .overlay(shape.strokeBorder(active ? tone.fg : tone.chipBorder, lineWidth: 1.5))
            .contentShape(shape)
            .animation(reduceMotion ? nil : .easeInOut(duration: Motion.tint), value: active)
        }
        .buttonStyle(PressScale())
        .fitTapArea()
        .accessibilityAddTraits(active ? .isSelected : [])
    }
}

/// The chips in two columns, in the vocabulary's order: tiles 10 apart on the start grid, compact chips 6
/// apart in the dock. The two in a row are one height, as a CSS grid row stretches them.
///
/// Tiles go to one column where a label's widest word would not fit a two-column tile even at the least
/// `FitWords` shrinks it to -- the largest type on a 320 phone, where the label has 52 and `running` broke
/// (the SHOWUP-194 fit sweep). One column gives each label the tile's width rather than shrink it below
/// the default size. Measured, so it happens there and nowhere else. Android's `ChipGrid`.
struct ChipGrid: View {
    let reasons: [ChatReason]
    let selected: ChatReason?
    let compact: Bool
    let onPick: (ChatReason) -> Void

    /// The type size as a factor of the default, relative to body as `F.manrope` scales.
    @ScaledMetric(relativeTo: .body) private var typeScale: CGFloat = 1

    /// A tile's width that is not its label: the 16.5 padding either side, the icon box 42 and the gap 12.
    private let tileChrome: CGFloat = 16.5 * 2 + 42 + 12

    var body: some View {
        let gap: CGFloat = compact ? 8 : 10
        if compact {
            rows(columns: 2, gap: gap)
        } else {
            ViewThatFits(in: .horizontal) {
                rows(columns: 2, gap: gap).frame(idealWidth: twoColumns(gap: gap), maxWidth: .infinity)
                rows(columns: 1, gap: gap)
            }
        }
    }

    /// The narrowest width two columns of tiles fit: each label room for its widest word at the floor.
    private func twoColumns(gap: CGFloat) -> CGFloat {
        let font = chatFont(PS.manropeBold, 15 * typeScale, .bold)
        let widest = reasons.map { widestWordWidth($0.label, font) }.max() ?? 0
        return 2 * (tileChrome + widest * wordFitFloor(typeScale: typeScale)) + gap
    }

    private func rows(columns: Int, gap: CGFloat) -> some View {
        let rows = stride(from: 0, to: reasons.count, by: columns).map { Array(reasons[$0..<min($0 + columns, reasons.count)]) }
        return VStack(spacing: gap) {
            ForEach(rows, id: \.first) { row in
                HStack(spacing: gap) {
                    ForEach(row, id: \.self) { reason in
                        BigActionChip(reason: reason, active: reason == selected, compact: compact) { onPick(reason) }
                    }
                    ForEach(row.count..<columns, id: \.self) { _ in Color.clear.frame(maxWidth: .infinity) }
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

/// The cream 96% glass a pinned bar sits on (the dock, the tutorial's CTA), with its soft top rule.
struct PinnedGlass: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(Color.liqCream.opacity(0.96).ignoresSafeArea(edges: .bottom))
            .overlay(alignment: .top) { Rectangle().fill(Color.liqBorderSoft).frame(height: 1) }
    }
}
