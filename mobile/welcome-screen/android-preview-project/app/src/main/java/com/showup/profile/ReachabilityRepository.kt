/*
 * ReachabilityRepository.kt
 * ShowUp · the push consent write, and the confirmation the flow waits on (SHOWUP-163)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE ORDERING IS THE PRODUCT REQUIREMENT, NOT THE ENDPOINT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The ticket's rule is "not optimistic": the flow position advances only once the server has
 * CONFIRMED the push consent, and on a failure the user stays on 10 with their choices kept. The
 * epic states it as a flow-level rule, and the reason is that the alternative produces accounts
 * past a consent screen with no consent record -- which is the one outcome a consent screen
 * cannot have.
 *
 * That rule lives in [ReachabilityViewModel.savePressed] and is enforced by its tests whatever
 * this returns. What this file decides is only WHERE the confirmation comes from.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THERE IS NO ENDPOINT FOR IT YET, AND THAT IS NOT AN OVERSIGHT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The ticket's own build inventory lists "the push consent write -- server-side, confirmed before
 * advancing" as pixel-less work this screen GATES. It is not built.
 *
 * `PATCH /me/notification-preferences` exists and is NOT it. That route carries three CATEGORIES
 * -- `essential`, `engagement`, `marketing` -- and the entity behind it has exactly those three
 * boolean columns and no channel. A category is what a notification is about; a channel is how it
 * reaches you, and §8 keeps them apart deliberately. Writing `pushOn` into `essential` would put
 * a channel consent in a category field and be wrong in the database rather than merely missing.
 *
 * So the seam is here, the stub is [NoConsentBackend], and the absence is loud -- the same
 * arrangement as [PushRegistration], for the same standing reason: third-party and server work
 * that has not been enabled stays visibly absent rather than faked.
 *
 * WHAT THAT MEANS FOR THE TICKET. "The flow position advances only after the server confirms the
 * push consent" CANNOT BE VERIFIED by this build. The ordering is built and tested against a fake
 * that can fail on demand; the real confirmation is one implementation away. Recorded in
 * `audit/CONFLICTS-2026-08-27.md`.
 */
package com.showup.profile

/** What the screen needs from the server, and the only thing it waits on. */
interface ReachabilityRepository {
    /**
     * Commit the push consent and return whether the server CONFIRMED it.
     *
     * False means the user stays on this screen with their choices intact and the CTA working
     * again. It must never mean "probably fine".
     */
    suspend fun savePushConsent(on: Boolean): Boolean
}

/**
 * The stub that ships today.
 *
 * RETURNS TRUE, and that deserves its own sentence: with no endpoint, blocking the flow would
 * strand every user on screen 10 in every build, which is a worse failure than an unrecorded
 * consent and would make the screen untestable end to end. So it lets the flow through and the
 * gap is carried in the audit log rather than in the runtime.
 *
 * The moment the route exists this is replaced by one implementation and nothing else changes --
 * [ReachabilityViewModel] already waits for the answer and already handles a false.
 */
object NoConsentBackend : ReachabilityRepository {
    override suspend fun savePushConsent(on: Boolean): Boolean = true
}
