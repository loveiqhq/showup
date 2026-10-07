/*
 * ProfileFlow.kt
 * ShowUp · the saved flow position, as the client reads and reports it (SHOWUP-165 to SHOWUP-173)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY THERE IS A STORED POSITION AT ALL
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Resume (flow README rule 4a) is DERIVED from facts on the server -- see ProfileResume.kt. That
 * works for every mandatory step and fails for the first skippable one, because rule 0 says a skip
 * "saves nothing for that step". Height, dating language, education, religion and politics can all
 * be skipped, so no fact on the account can say they were passed. Before this existed, a relaunch
 * after prompts landed on Home and silently skipped every screen from media to politics.
 *
 * So the server keeps one more fact: the furthest step reached after prompts, as a §2 `step_id`.
 * It only ever moves forward -- going back a screen never rewinds it (`util/flow-position.ts`).
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT "REACHED" MEANS, PER SCREEN
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * A step is reported when it is COMPLETED OR SKIPPED, never when it is merely shown. Location is
 * the case that makes the difference visible: its ticket says "the saved flow position advances
 * after the user answers", and that a Deny followed by a force-kill relaunches onto the denied
 * state. Reporting `location` only on a grant or on `Not now` gives exactly that -- a Deny leaves
 * the position at Stay reachable, the relaunch lands on location, and the arrival matrix shows B.
 */
package com.showup.profile

import com.showup.api.generated.model.FlowPosition

/**
 * The walk after prompts, in the order the server ranks it.
 *
 * THE GENERATED ENUM, NOT STRINGS: `FlowPosition` comes from the API contract, so a step renamed on
 * the server is a compile error here rather than a value the server quietly refuses with 400.
 *
 * Media reports `media_video`. Its screen carries both §2 ids (`media_voice`, `media_video`, which
 * share step_index 3) and the server accepts either at one rank; one id is enough to say the screen
 * was passed.
 */
val FLOW_ORDER: List<FlowPosition> = listOf(
    FlowPosition.media_video,
    FlowPosition.notifications,
    FlowPosition.reachability,
    FlowPosition.location,
    FlowPosition.height,
    FlowPosition.gender,
    FlowPosition.orientation,
    FlowPosition.dating_language,
    FlowPosition.education,
    FlowPosition.religion,
    FlowPosition.politics,
)

/**
 * A stored position's place in the walk, or -1 when none has been reached.
 *
 * READ AS A STRING, deliberately: the progress response carries it untyped so an older app never
 * fails to decode a step a newer server knows about. Anything unrecognised ranks as "nothing
 * reached", which resumes too early rather than too late -- showing a screen twice is recoverable;
 * skipping one silently is the bug this file exists to prevent.
 */
fun flowRank(stored: String?): Int {
    if (stored == null) return -1
    val position = if (stored == FlowPosition.media_voice.value) FlowPosition.media_video.value else stored
    return FLOW_ORDER.indexOfFirst { it.value == position }
}

/** Whether [stored] is short of [step] -- i.e. that step has not yet been completed or skipped. */
fun hasNotReached(stored: String?, step: FlowPosition): Boolean =
    flowRank(stored) < FLOW_ORDER.indexOf(step)

/**
 * Where a step's "advance the saved flow position" goes.
 *
 * An interface so every view model that reports one -- media, notifications, Stay reachable,
 * location, and the detail steps on a skip -- is tested against a recorder rather than a network.
 */
fun interface FlowPositionSink {
    /** Records [position]. True when the server stored it; never throws. */
    suspend fun report(position: FlowPosition): Boolean
}

/**
 * The real sink: one PATCH carrying the position and nothing else.
 *
 * NOTHING ELSE IN THE BODY, so this can never touch an answer -- a skip "saves nothing for that
 * step". The server only ever moves the position forward (`advanceFlowPosition`, under a row
 * lock), so a report that arrives late, or after a later one, changes nothing.
 */
class FlowPositionReporter(private val api: com.showup.api.ShowUpApi) : FlowPositionSink {
    override suspend fun report(position: FlowPosition): Boolean = runCatching {
        api.profiles.updateProfile(
            com.showup.api.generated.model.UpsertProfileDto(flowPosition = position),
        ).isSuccessful
    }.getOrDefault(false)
}
