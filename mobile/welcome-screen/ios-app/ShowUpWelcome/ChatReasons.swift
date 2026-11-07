// ChatReasons.swift
// ShowUp · the chat's reasons, built once (SHOWUP-194, Date 02)
//
// The reference's `CHAT_REASONS` and `REASON_TONES` (screen-chat-reference.jsx), keyed by the registry's
// §40 `chat_reason` wire values. The Date screen's unread card, the chat's chips and bubbles, and Dates
// 03 and 05 all read this one table: 193 kept a copy keyed by the server's old names, and a copy is how a
// renamed reason silently draws the wrong badge. Android's `ChatReasons.kt`.

import SwiftUI

/// The reference's `REASON_TONES`: the ink, the panel fill and the chip border of each tone.
enum ReasonTone: Equatable, Sendable {
    case violet, orange, danger, darkRed

    var fg: Color {
        switch self {
        case .violet: return .liqPurple
        case .orange: return .liqOrange
        case .danger: return .liqDangerFg
        case .darkRed: return .liqReasonDarkRed
        }
    }

    var bg: Color {
        switch self {
        case .violet: return Color.liqPurple.opacity(0.10)
        case .orange: return Color.liqOrange.opacity(0.11)
        case .danger: return Color.liqDanger.opacity(0.10)
        case .darkRed: return Color.liqReasonDarkRed.opacity(0.09)
        }
    }

    var chipBorder: Color {
        switch self {
        case .violet: return Color.liqPurple.opacity(0.20)
        case .orange: return Color.liqOrange.opacity(0.22)
        case .danger: return Color.liqDanger.opacity(0.22)
        case .darkRed: return Color.liqReasonDarkRed.opacity(0.28)
        }
    }
}

/// One chip, in the grid's order; `rawValue` is its §40 wire value. Three have no draft: the two changes
/// are Date 05's, and "I can't make it today" is written by the person after the guide, never pre-filled.
/// `shown` is false for the chips whose ticket has not shipped -- `arrived` is Date 03's, the two changes
/// Date 05's -- so the grid shows five until they do.
enum ChatReason: String, CaseIterable, Sendable {
    case onMyWay = "on_my_way"
    case arrived = "arrived"
    case cantFind = "cant_find"
    case whenArrive = "when_arrive"
    case changeTime = "change_time"
    case changePlace = "change_place"
    case runningLate = "running_late"
    case cantMakeIt = "cant_make_it"

    var label: String {
        switch self {
        case .onMyWay: return "I'm on my way"
        case .arrived: return "I arrived at the spot"
        case .cantFind: return "I can't spot you"
        case .whenArrive: return "When will you arrive?"
        case .changeTime: return "Change meet time"
        case .changePlace: return "Change location"
        case .runningLate: return "I'm running late"
        case .cantMakeIt: return "I can't make it today"
        }
    }

    var draft: String? {
        switch self {
        case .onMyWay: return "On my way now — see you in a few!"
        case .arrived: return "I'm here at the meeting spot — see you soon!"
        case .cantFind: return "I'm here but can't spot you — where are you sitting?"
        case .whenArrive: return "When do you think you'll get here?"
        case .runningLate: return "Running about 10 min late — sorry! Almost there."
        case .changeTime, .changePlace, .cantMakeIt: return nil
        }
    }

    var icon: BrandIcon {
        switch self {
        case .onMyWay: return .navigation
        case .arrived: return .check
        case .cantFind: return .binoculars
        case .whenArrive: return .walking
        case .changeTime: return .clock
        case .changePlace: return .mapPin
        case .runningLate: return .hourglass
        case .cantMakeIt: return .frown
        }
    }

    var tone: ReasonTone {
        switch self {
        case .changeTime, .changePlace: return .violet
        case .cantMakeIt: return .darkRed
        default: return .orange
        }
    }

    var shown: Bool {
        switch self {
        case .arrived, .changeTime, .changePlace: return false
        default: return true
        }
    }

    /// The chip a wire value names, or nil for none: a message sent with no chip, and a name this build
    /// does not know. Nil, not the reference's first-entry fallback -- a badge saying "I'm on my way"
    /// over a message that never said so is worse than no badge.
    static func fromWire(_ wire: String?) -> ChatReason? {
        wire.flatMap(ChatReason.init(rawValue:))
    }
}

/// The chips the grid shows today, in its order.
let shownReasons: [ChatReason] = ChatReason.allCases.filter(\.shown)
