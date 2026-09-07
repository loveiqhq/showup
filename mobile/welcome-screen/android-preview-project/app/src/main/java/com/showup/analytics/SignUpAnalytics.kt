package com.showup.analytics

/**
 * Every event the welcome and sign-up screens report.
 *
 * WHERE THIS COMES FROM
 *
 * Each entry is transcribed from the Tracking section of its ticket. Nothing here is invented, and
 * where a ticket is ambiguous the ambiguity is recorded in `audit/CONFLICTS-2026-08-27.md` rather
 * than settled quietly. The five tutorial cards were already instrumented; these five screens were
 * not, which is finding C3 of the August audit.
 *
 *   SHOWUP-140  Startup / account creation
 *   SHOWUP-142  Welcome back (re-login)
 *   SHOWUP-143  Phone number and code entry
 *   SHOWUP-144  Connect account
 *   SHOWUP-145  Re-login SSO
 *
 * NAMING
 *
 * `snake_case`, past tense, matching the backend's existing taxonomy (`date_confirmed`,
 * `like_sent`, `account_created`) and the tutorial's (`tutorial_card_viewed`). Property names are
 * `snake_case` for the same reason. One taxonomy across client and server, or the funnel has to be
 * reassembled by hand in the warehouse.
 *
 * SCREEN NAMES ARE VERBATIM
 *
 * The `screen_name` values below are exactly the strings the tickets specify, capitalisation and
 * spacing included -- `Signup - welcomeback` is lower-case in SHOWUP-142 and stays that way here.
 * They look inconsistent because they ARE inconsistent in the tickets; normalising them would make
 * this file disagree with the specification it is supposed to implement.
 */
object SignUpAnalytics {

    // ── screenviews ─────────────────────────────────────────────────────────
    // One event with a `screen_name` property rather than one event per screen: that is what makes
    // a funnel query possible without listing every screen by name.

    const val SCREEN_VIEWED = "screen_viewed"

    object Screen {
        /** SHOWUP-140. */
        const val CREATE_ACCOUNT = "Signup - CreateAccount"

        /**
         * SHOWUP-142.
         *
         * NOTE: SHOWUP-145 specifies `SSOLogin` for what appears to be the same screen, and says it
         * "must be distinguishable from the first-run Startup screenview". We have one
         * WelcomeBackScreen. Both names are defined here and [WELCOME_BACK] is the one wired, on the
         * grounds that 142 describes the screen we built. Recorded as an open conflict.
         */
        const val WELCOME_BACK = "Signup - welcomeback"

        /** SHOWUP-145. Defined, not currently wired -- see [WELCOME_BACK]. */
        const val SSO_LOGIN = "SSOLogin"

        /** SHOWUP-143, state A/B. */
        const val PHONE_NUMBER = "Signup - Phonenumber"

        /** SHOWUP-143, state C/D. */
        const val CODE_ENTRY = "Signup - Codeentry"

        /**
         * SHOWUP-144.
         *
         * The ticket asks "is all one screen technically?". Answered in CONFLICTS A8: yes -- all ten
         * states are one component driven by `state` + `provider` + `kind`, and the conflict is a
         * modal over it rather than a route. So this is ONE screenview carrying a `state` property,
         * not ten screenviews.
         */
        const val CONNECT_SSO = "ConnectSSO"
    }

    fun screenViewed(name: String, properties: Map<String, Any> = emptyMap()): Pair<String, Map<String, Any>> =
        SCREEN_VIEWED to (mapOf("screen_name" to name) + properties)

    // ── SHOWUP-140 · Startup ────────────────────────────────────────────────

    const val CREATE_ACCOUNT_TAPPED = "signup_create_account_tapped"
    const val LOG_IN_TAPPED = "signup_log_in_tapped"

    /**
     * The three legal links, as one event with a `link` property.
     *
     * The tickets list them as three separate click events. One event carrying which link was
     * tapped records exactly the same information and is the shape every other property-bearing
     * event here uses.
     *
     * Confirmed by the product side on 7 September 2026: keep the one event. It answers both
     * questions from one place -- which document, and which screen -- whereas three event names
     * record the document and lose the screen. Settled; see CONFLICTS E3.
     */
    const val LEGAL_LINK_TAPPED = "legal_link_tapped"

    object Legal {
        const val TERMS = "terms_and_conditions"
        const val PRIVACY = "privacy_policy"
        const val LEGAL_NOTICE = "legal_notice"
    }

    fun legalLinkTapped(link: String, screen: String): Pair<String, Map<String, Any>> =
        LEGAL_LINK_TAPPED to mapOf("link" to link, "screen_name" to screen)

    // ── SHOWUP-142 / 145 · Welcome back ─────────────────────────────────────

    /** Carries `method` and `is_last_used`, both named by the tickets. */
    const val AUTH_METHOD_TAPPED = "signup_auth_method_tapped"
    const val GET_HELP_TAPPED = "signup_get_help_tapped"
    const val USE_DIFFERENT_ACCOUNT_TAPPED = "signup_use_different_account_tapped"

    fun authMethodTapped(method: String, isLastUsed: Boolean): Pair<String, Map<String, Any>> =
        AUTH_METHOD_TAPPED to mapOf("method" to method, "is_last_used" to isLastUsed)

    // ── SHOWUP-143 · Phone and code ─────────────────────────────────────────

    /** "Click event: Send me the code". */
    const val PHONE_SUBMITTED = "signup_phone_submitted"

    /**
     * "Number submitted · validation failed, with a `reason` property".
     *
     * THE REASON VOCABULARY DOES NOT MATCH OURS, AND THAT IS NOT RESOLVED HERE.
     *
     * SHOWUP-143 names three reasons: `too short`, `not a mobile`, `unsupported country`. Our
     * validator produces seven outcomes, because it asks libphonenumber rather than measuring
     * length: empty, notANumber, tooShort, tooLong, invalidLength, unrecognised, notMobile.
     *
     * Only two overlap. `unsupported country` is unreachable -- there is no supported-country list
     * in the app, and libphonenumber accepts every region. Five of ours have no bucket in the
     * ticket.
     *
     * This reports OUR outcome, because reporting a value the code cannot produce, or collapsing
     * five distinct failures into one, would make the data describe something that did not happen.
     * The mismatch is an open question for the product side, not something to paper over here.
     */
    const val PHONE_VALIDATION_FAILED = "signup_phone_validation_failed"

    /** "Click event: Verify code". */
    const val CODE_SUBMITTED = "signup_code_submitted"

    /** "Code submitted · verify failed, with an `attempt` number". */
    const val CODE_VERIFY_FAILED = "signup_code_verify_failed"

    /** "Resend requested, with `seconds_waited` and whether it followed a mismatch". */
    const val RESEND_REQUESTED = "signup_resend_requested"

    /** "Click event: Edit phone number". */
    const val EDIT_PHONE_TAPPED = "signup_edit_phone_tapped"

    fun phoneValidationFailed(reason: String, country: String): Pair<String, Map<String, Any>> =
        PHONE_VALIDATION_FAILED to mapOf("reason" to reason, "country" to country)

    fun codeVerifyFailed(attempt: Int): Pair<String, Map<String, Any>> =
        CODE_VERIFY_FAILED to mapOf("attempt" to attempt)

    fun resendRequested(secondsWaited: Int, afterMismatch: Boolean): Pair<String, Map<String, Any>> =
        RESEND_REQUESTED to mapOf("seconds_waited" to secondsWaited, "after_mismatch" to afterMismatch)

    // ── SHOWUP-144 · Connect account ────────────────────────────────────────

    /** "Click events: <providername> tapped". */
    const val PROVIDER_TAPPED = "connect_provider_tapped"

    /** "Click event: Skip and continue to profile". */
    const val SKIP_TAPPED = "connect_skip_tapped"

    /** "Event: Sheet dismissed" -- the user closed the provider sheet before it finished. */
    const val SHEET_DISMISSED = "connect_sheet_dismissed"

    /** "link succeeded/failed". */
    const val LINK_SUCCEEDED = "connect_link_succeeded"
    const val LINK_FAILED = "connect_link_failed"

    /** "linking timeout hit" -- the 8-second cap in the ticket's acceptance criteria. */
    const val LINKING_TIMEOUT = "connect_linking_timeout"

    /** "conflict raised". */
    const val CONFLICT_RAISED = "connect_conflict_raised"

    /** "conflict resolve tapped". */
    const val CONFLICT_RESOLVE_TAPPED = "connect_conflict_resolve_tapped"

    /** "conflict `Use a different account` tapped". */
    const val CONFLICT_DIFFERENT_ACCOUNT_TAPPED = "connect_conflict_different_account_tapped"

    /**
     * "repeat conflicts in one session".
     *
     * Carries `count` -- the number of conflicts raised in this session so far -- so the second and
     * third are distinguishable from the first without the warehouse having to sessionise.
     */
    const val CONFLICT_REPEATED = "connect_conflict_repeated"

    fun provider(event: String, provider: String): Pair<String, Map<String, Any>> =
        event to mapOf("provider" to provider)

    fun linkFailed(provider: String, kind: String): Pair<String, Map<String, Any>> =
        LINK_FAILED to mapOf("provider" to provider, "kind" to kind)

    fun conflictRepeated(count: Int, provider: String): Pair<String, Map<String, Any>> =
        CONFLICT_REPEATED to mapOf("count" to count, "provider" to provider)

    /**
     * Every event name this object can emit.
     *
     * Exists so a test can assert the catalogue matches iOS, and so
     * `audit/check-analytics-parity.py` has something to read. A list that has to be kept in step by
     * hand is one that drifts, so the test reads THIS rather than a second copy.
     */
    fun allEventNames(): List<String> = listOf(
        SCREEN_VIEWED,
        CREATE_ACCOUNT_TAPPED,
        LOG_IN_TAPPED,
        LEGAL_LINK_TAPPED,
        AUTH_METHOD_TAPPED,
        GET_HELP_TAPPED,
        USE_DIFFERENT_ACCOUNT_TAPPED,
        PHONE_SUBMITTED,
        PHONE_VALIDATION_FAILED,
        CODE_SUBMITTED,
        CODE_VERIFY_FAILED,
        RESEND_REQUESTED,
        EDIT_PHONE_TAPPED,
        PROVIDER_TAPPED,
        SKIP_TAPPED,
        SHEET_DISMISSED,
        LINK_SUCCEEDED,
        LINK_FAILED,
        LINKING_TIMEOUT,
        CONFLICT_RAISED,
        CONFLICT_RESOLVE_TAPPED,
        CONFLICT_DIFFERENT_ACCOUNT_TAPPED,
        CONFLICT_REPEATED,
    )
}
