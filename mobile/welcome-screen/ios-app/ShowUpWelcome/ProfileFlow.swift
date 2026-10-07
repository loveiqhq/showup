//
//  ProfileFlow.swift
//  ShowUp · the saved flow position, as the client reads and reports it (SHOWUP-165 to SHOWUP-173)
//
//  The Swift twin of `profile/ProfileFlow.kt`; read that file's header for the argument. In short:
//  resume is derived from facts on the server, which works for every mandatory step and fails for
//  the first skippable one, because a skip "saves nothing for that step". So the server keeps one
//  more fact — the furthest step completed or skipped after prompts, as a §2 `step_id` — and it
//  only ever moves forward.
//
//  A step is reported when it is COMPLETED OR SKIPPED, never when it is merely shown. Location makes
//  the difference visible: `location` is reported on a grant or `Not now`, so a Deny followed by a
//  force-kill relaunches onto the denied state, exactly as the ticket asks.
//

import Foundation
import ShowUpAPI

/// The walk after prompts, in the order the server ranks it. The GENERATED enum, so a step renamed
/// on the server is a compile error here rather than a value the server refuses with 400.
///
/// Media reports `media_video`; its screen carries both §2 ids at one rank.
let flowOrder: [Components.Schemas.FlowPosition] = [
    .media_video, .notifications, .reachability, .location,
    .height, .gender, .orientation, .dating_language, .education, .religion, .politics,
]

/// A stored position's place in the walk, or -1 when none has been reached.
///
/// READ AS A STRING: the progress response carries it untyped so an older app never fails to
/// decode a step a newer server knows about. Anything unrecognised ranks as "nothing reached",
/// which resumes too early rather than too late.
func flowRank(_ stored: String?) -> Int {
    guard let stored else { return -1 }
    let position = stored == Components.Schemas.FlowPosition.media_voice.rawValue
        ? Components.Schemas.FlowPosition.media_video.rawValue
        : stored
    return flowOrder.firstIndex { $0.rawValue == position } ?? -1
}

/// Whether `stored` is short of `step` — i.e. that step has not yet been completed or skipped.
func hasNotReached(_ stored: String?, _ step: Components.Schemas.FlowPosition) -> Bool {
    flowRank(stored) < (flowOrder.firstIndex(of: step) ?? Int.max)
}

/// Where a step's "advance the saved flow position" goes. A protocol, so every model that reports
/// one is tested against a recorder rather than a network.
protocol FlowPositionReporting: Sendable {
    /// Records `position`. True when the server stored it; never throws.
    func report(_ position: Components.Schemas.FlowPosition) async -> Bool
}

/// The real reporter: one PATCH carrying the position and NOTHING ELSE, so it can never touch an
/// answer. The server only moves the position forward, so a late report changes nothing.
struct FlowPositionReporter: FlowPositionReporting {
    let api: ShowUpAPI

    func report(_ position: Components.Schemas.FlowPosition) async -> Bool {
        do {
            let response = try await api.client.updateProfile(body: .json(.init(
                flowPosition: .init(value1: position)
            )))
            if case .ok = response { return true }
            return false
        } catch {
            return false
        }
    }
}
