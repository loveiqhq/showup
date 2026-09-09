/*
 * ProfileAnalytics.kt
 * ShowUp · the events "The basics" emits, and the two that are still BLOCKED
 *
 * Names and payloads come from design_handoff_showup/tracking/events.json, family D, and the
 * vocabularies from enums.json §11 (screen registry) and §2 (step_id). Nothing here is invented and
 * nothing is a literal at a call site -- which is the rule the tracking sections state twice.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * RESOLVED in registry 1.3.0 (9 September 2026)
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The design side re-exported the registry and rewrote the tracking sections. Four things that
 * were blocked or corrupted are now settled, and are implemented here rather than worked around:
 *
 *   · consent_changed has a real vocabulary.  `surface` is now a closed enum
 *     ("profile_creation"|"settings") and `channel` gained "marketing_email", which is a DIFFERENT
 *     value from "email" -- the ticket is explicit that the marketing box, the later Stay reachable
 *     email toggle and transactional mail are three uses of one address, told apart by the
 *     vocabulary. The vocabulary reason for withholding [consentChanged] is gone; it now
 *     carries the right channel and surface and fires in BOTH directions. Like all ten builders
 *     here it still reaches no sink, because neither app has one -- that is brief Step 2 and its
 *     own ticket, and it applies to every event equally rather than to this one.
 *
 *   · step_id and field_id are clean.  They previously carried a concatenated version badge
 *     ("emailv1.2", "agev1.2"); the exporter was fixed. [BasicsStep.stepId] already used the clean
 *     values and now agrees with the registry rather than merely with the ticket.
 *
 *   · step_index is populated.  §2 gives 1 / 2 / 2 / 3, which is exactly what
 *     [BasicsStep.progressSegment] derives -- email_verify holding at 2 is now registry-backed.
 *
 *   · email_validation_failed rule="disposable" is decided: not implemented, and no detection is
 *     being built. The ticket states that emitting it today would be a bug. "format" only.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * STILL BLOCKED — two, marked rather than guessed
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * B1 · referrer_screen_id, and screen_viewed's `last_used` / `state`.  The former arrives with the
 *      alignment brief's Step 3, which is unbuilt. The latter two are in the registry's payload for
 *      screen_viewed but named by no profile ticket, and the shipped code sends three properties.
 *      Whether they are required on profile screens is an open question with the design side.
 *
 * B2 · sensitivity_class + field_registry_version.  The brief's Step 2 requires both stamped at
 *      EMIT time from the registry, failing closed to class 2 for an unknown field_id. That emitter
 *      does not exist in this repo. Profile creation is, in the brief's own words, "nothing but
 *      attribute events", so this is a real prerequisite -- see [Stamp].
 */
package com.showup.profile

import com.showup.analytics.AnalyticsTracker

/**
 * The §11 screen registry, as constants.
 *
 * Both values ship: `screenId` is the stable key a saved funnel binds to and is never edited;
 * `screenName` is the human label searched for in the analytics tool and may be. They are separate
 * vocabularies and collapsing them breaks one of the two uses.
 */
enum class ProfileScreen(val screenId: String, val screenName: String) {
    Name("profile_name", "ProfileName"),
    Email("profile_email", "ProfileEmail"),
    EmailVerification("profile_email_verification", "ProfileEmailVerification"),
    Dob("profile_dob", "ProfileDoB"),
}

/** `field_id` from §1. */
object ProfileField {
    const val FIRST_NAME = "first_name"
    const val EMAIL = "email"
}

/** The `rule` vocabulary shared by the validation events. */
object ValidationRule {
    const val REQUIRED_MISSING = "required_missing"
    const val FORMAT = "format"
}

/**
 * `channel` from enums.json §8.
 *
 * **`marketing_email` is not `email`.** One address, three uses, told apart by this vocabulary:
 * this box governs marketing mail only; the `email` toggle on the later Stay reachable screen is a
 * preference about being contacted regarding a match; transactional mail (the verification code,
 * password resets, a support reply) has no consent to withdraw and is never tracked as one.
 * Sending "email" here would file a marketing opt-in against the wrong channel.
 */
object ConsentChannel {
    const val MARKETING_EMAIL = "marketing_email"
}

/**
 * `surface` from enums.json §8 — which screen a consent was changed on.
 *
 * A closed pair as of registry 1.3.0. `PROFILE_CREATION` is the one-off ask inside the sign-up
 * flow; `SETTINGS` is the same control revisited later. The distinction is what makes a consent
 * record auditable, which is why the registry marks it required.
 */
object ConsentSurface {
    const val PROFILE_CREATION = "profile_creation"
    const val SETTINGS = "settings"
}

/**
 * The emit-time stamp every attribute event must carry.
 *
 * **BLOCKED (B3).** The brief requires `sensitivity_class` resolved from the registry at emit time,
 * failing closed to 2 for an unrecognised field_id, plus `field_registry_version`. Neither the
 * registry file nor the codegen is in this repo yet, so this object is the seam and not the
 * implementation: it carries the version the handoff was generated against and a hard-coded class
 * per event, which is exactly what the brief says NOT to do long-term.
 *
 * When the registry lands: delete the constants, read both from the generated lookup, and count
 * unknown field_ids in the *unclassified attributes* metric.
 */
object Stamp {
    /** enums.json → registry_version at the time of writing. */
    const val FIELD_REGISTRY_VERSION = "1.3.0"

    fun of(sensitivityClass: Int): Map<String, Any> = mapOf(
        "sensitivity_class" to sensitivityClass,
        "field_registry_version" to FIELD_REGISTRY_VERSION,
    )
}

/**
 * Family D events for "The basics".
 *
 * Each function returns the name and payload; nothing here reaches a sink. The app still has no
 * analytics sink and no consent gate -- the brief is explicit that those are separate work and that
 * "the apps have no sink at all, which is not the same thing as a gate".
 */
object ProfileAnalytics {

    const val SCREEN_VIEWED = "screen_viewed"
    const val PROFILE_STEP_VIEWED = "profile_step_viewed"
    const val PROFILE_BUILD_STARTED = "profile_build_started"
    const val PROFILE_STEP_COMPLETED = "profile_step_completed"
    const val PROFILE_STEP_SKIPPED = "profile_step_skipped"
    const val NAME_SUBMITTED = "name_submitted"
    const val EMAIL_SUBMITTED = "email_submitted"
    const val EMAIL_VALIDATION_FAILED = "email_validation_failed"
    const val FORM_VALIDATION_FAILED = "form_validation_failed"
    const val CONSENT_CHANGED = "consent_changed"

    /** T1, class 0. `referrer_screen_id` is B2 and travels as null until Step 3 lands. */
    fun screenViewed(screen: ProfileScreen, referrer: ProfileScreen? = null) =
        SCREEN_VIEWED to buildMap {
            put("screen_id", screen.screenId)
            put("screen_name", screen.screenName)
            put("referrer_screen_id", referrer?.screenId ?: "")
            putAll(Stamp.of(0))
        }

    /**
     * T2, class 0. Not a duplicate of [screenViewed]: that one answers WHICH SCREEN, this answers
     * WHERE IN THE FLOW. EmailVerify is the case that proves they differ -- its own screen, holding
     * at step 2 of 3.
     */
    fun stepViewed(step: BasicsStep) = PROFILE_STEP_VIEWED to buildMap {
        put("step_id", step.stepId)
        put("step_index", step.stepIndex)
        putAll(Stamp.of(0))
    }

    /** T2, class 0. Once per profile build, on the first step only. */
    fun buildStarted(entryPoint: String) = PROFILE_BUILD_STARTED to buildMap {
        put("entry_point", entryPoint)
        putAll(Stamp.of(0))
    }

    /** T2, class 0. The name itself is free text and is NEVER sent -- char_count only. */
    fun nameSubmitted(charCount: Int) = NAME_SUBMITTED to buildMap {
        put("char_count", charCount)
        putAll(Stamp.of(0))
    }

    /** T2, class 0. The address is never sent, only its domain. */
    fun emailSubmitted(domain: String) = EMAIL_SUBMITTED to buildMap {
        put("domain", domain)
        putAll(Stamp.of(0))
    }

    /** T2, class 0. Only [ValidationRule.FORMAT] is emitted -- "disposable" is B4. */
    fun emailValidationFailed(rule: String = ValidationRule.FORMAT) =
        EMAIL_VALIDATION_FAILED to buildMap {
            put("rule", rule)
            putAll(Stamp.of(0))
        }

    /** T2, class 0. */
    fun stepCompleted(step: BasicsStep, timeOnStepSeconds: Int) =
        PROFILE_STEP_COMPLETED to buildMap {
            put("step_id", step.stepId)
            put("time_on_step_s", timeOnStepSeconds)
            putAll(Stamp.of(0))
        }

    /**
     * T1, class 0. Fires on the REFUSED PRESS, not on render.
     *
     * Typing three characters and stopping emits nothing; only pressing Continue with the
     * requirement unmet does.
     */
    fun formValidationFailed(fieldId: String, screen: ProfileScreen, step: BasicsStep) =
        FORM_VALIDATION_FAILED to buildMap {
            put("field_id", fieldId)
            put("rule", ValidationRule.REQUIRED_MISSING)
            put("screen_id", screen.screenId)
            put("step_id", step.stepId)
            putAll(Stamp.of(0))
        }

    /**
     * T2, class 1. Fires in **both** directions.
     *
     * Not opt-out only, unlike [fieldDisplayOptedOut]: a consent record has to show the withdrawal
     * as well as the grant, or it cannot answer "was this person opted in on date X". The ticket
     * states it outright -- "fires in both directions, never opt-out only".
     *
     * Unblocked by registry 1.3.0, which gave `surface` a closed vocabulary. Before that this was
     * written and deliberately not called, because a guessed surface would have put an unowned
     * string into a consent record.
     */
    fun consentChanged(
        on: Boolean,
        surface: String = ConsentSurface.PROFILE_CREATION,
    ) = CONSENT_CHANGED to buildMap {
        put("channel", ConsentChannel.MARKETING_EMAIL)
        put("on", on)
        put("surface", surface)
        putAll(Stamp.of(1))
    }

    /** T2, class 0. **BLOCKED** — the skip path itself is an open question in ticket 02. */
    fun stepSkipped(step: BasicsStep) = PROFILE_STEP_SKIPPED to buildMap {
        put("step_id", step.stepId)
        putAll(Stamp.of(0))
    }
}

/** Reports a catalogue-built event. Mirrors the welcome flow's helper exactly. */
fun AnalyticsTracker.report(pair: Pair<String, Map<String, Any>>) = track(pair.first, pair.second)
