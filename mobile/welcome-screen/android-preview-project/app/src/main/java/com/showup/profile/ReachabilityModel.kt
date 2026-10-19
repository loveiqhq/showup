/*
 * ReachabilityModel.kt
 * ShowUp · Profile creation 10 — Stay reachable, the state and the vocabulary (SHOWUP-163)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * TWO JOBS, AND ONLY ONE OF THEM HAS ANYTHING BEHIND IT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * 1. PUSH is the one live channel. A switch, ON by default, and the only consent this screen
 *    writes. Turning it off is a deliberate act that goes through a confirmation.
 *
 * 2. THREE INTEREST CHECKBOXES are a DEMAND TEST for channels that do not exist. They record
 *    interest and do nothing else: no delivery, no consent record, no phone number, no dialog, no
 *    server write. The ticket calls them "the most likely thing in this ticket to be over-built",
 *    which is why the type below cannot express anything more than a boolean per channel.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * INTEREST IS NOT CONSENT, AND THE TYPES SAY SO
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * §25 `interest_channel` is a SEPARATE VOCABULARY from §8 `channel`, and the registry says why in
 * as many words: "deliberately a separate vocabulary from §8 channel so an interest can never be
 * read as a consent". [InterestChannel] is therefore its own enum rather than a subset of the
 * consent channels, and nothing here can be handed to `consentChanged` -- that builder takes §8's
 * vocabulary and this one has no member in it.
 *
 * The ticket's hardest tracking rule falls straight out of that: **no `consent_changed` for a
 * checkbox. Ever.**
 */
package com.showup.profile

/**
 * The three channels the demand test asks about. §25, MVP only.
 *
 * `ai_call` is the phone call from our AI assistant -- which becomes §8 `call` if it is ever
 * built, and is deliberately spelled differently here so the two cannot be confused in a query.
 */
enum class InterestChannel(val trackingValue: String) {
    AiCall("ai_call"),
    WhatsApp("whatsapp"),
    Sms("sms"),
    ;

    companion object {
        /** In the order the reference file lists them, which is the order they are drawn. */
        val ORDER = listOf(AiCall, WhatsApp, Sms)
    }
}

/**
 * Which dialog the deactivation confirm is, if it is up at all.
 *
 * The two differ by ONE LABEL and nothing else, which is exactly why they are one type with a
 * flag rather than two dialogs: a second copy would be a second place for the body copy to drift.
 */
enum class DeactivationPrompt {
    /**
     * State C. The user moved the switch off themselves, so the primary offers to undo that:
     * `Keep active`.
     */
    UserTurnedItOff,

    /**
     * State D. The OS dialog came back with "Don't allow", so keeping it active is not something
     * this app can do -- only Settings can. The primary reads `Open Settings`.
     *
     * DECIDED 25 September 2026, and it is the standing one-label-per-behaviour rule: the button
     * opens Settings, so it must not say `Keep active`, which would promise something it cannot
     * deliver.
     */
    AfterOsDenial,
}

/**
 * Everything the screen renders.
 *
 * ONE VALUE, HOISTED -- but this screen DOES own asynchronous work (the OS dialog, push
 * registration, and a consent write that must be confirmed before the flow advances), so the
 * owner is a ViewModel rather than a `rememberSaveable` at the host. That is `CLAUDE.md`'s rule
 * applied rather than abandoned: photos and prompts hold no async work and stay hoisted values.
 *
 * @param pushOn the switch. TRUE BY DEFAULT -- an opt-out, not an opt-in. The ticket is explicit:
 *   "Push defaults ON. It's an opt-out, not an opt-in: the user switches it off actively, through
 *   the confirm dialog."
 * @param interest the demand test. All three false by default, and the only thing that ever
 *   happens to them is a flip.
 * @param prompt the deactivation confirm, or null. Non-null means the dialog is up AND the switch
 *   has not moved yet -- turning off is not applied until it is confirmed.
 * @param saving true from the tap on Save preferences until navigation or failure. The CTA is
 *   never disabled; it simply stops answering, which is the same rule the media and notification
 *   screens use.
 * @param saveFailed the inline error above the CTA. NOT OPTIMISTIC: the flow advances only once
 *   the server has confirmed the consent, so a failure leaves the user here with their choices.
 */
data class ReachabilityState(
    val pushOn: Boolean = true,
    val interest: Set<InterestChannel> = emptySet(),
    val prompt: DeactivationPrompt? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    fun isInterested(channel: InterestChannel): Boolean = channel in interest

    /**
     * The interest set as the registry wants it on `reachability_saved` -- the §25 values of the
     * checked boxes, in the drawn order so two identical states never serialise two ways.
     */
    val interestValues: List<String>
        get() = InterestChannel.ORDER.filter { it in interest }.map { it.trackingValue }
}
