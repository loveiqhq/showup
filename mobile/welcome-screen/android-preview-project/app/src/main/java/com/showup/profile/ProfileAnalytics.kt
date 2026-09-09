/*
 * ProfileAnalytics.kt
 * ShowUp · the events "The basics" emits, and the four that are BLOCKED
 *
 * Names and payloads come from design_handoff_showup/tracking/events.json, family D, and the
 * vocabularies from enums.json §11 (screen registry) and §2 (step_id). Nothing here is invented and
 * nothing is a literal at a call site -- which is the rule the tracking sections state twice.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * BLOCKED — four things are NOT decided, and are marked rather than guessed
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * B1 · consent_changed payload.  The ticket says the payload is `on: bool`. The registry defines
 *      THREE properties: channel ("call"|"calendar"|"push"|"whatsapp"|"sms"|"email"), on (bool),
 *      and surface (str) -- and the ticket also says "do not restate a payload the registry already
 *      defines", so the registry governs. But NO `surface` vocabulary exists anywhere in enums.json,
 *      and the registry's own trigger describes a different screen: "One of the six Stay reachable
 *      toggles changed ... surface separates the profile-creation screen from Settings".
 *      => [consentChanged] is written but NOT called. See the call site in ProfileEmailScreen.
 *
 * B2 · referrer_screen_id.  screen_viewed gains it in the alignment brief's Step 3, which is not
 *      built. The property is accepted here and passed through as null until it is.
 *
 * B3 · sensitivity_class + field_registry_version.  The brief's Step 2 requires both stamped at
 *      EMIT time from the registry, failing closed to class 2 for an unknown field_id. That emitter
 *      does not exist. Profile creation is, in the brief's own words, "nothing but attribute
 *      events", so this is a real prerequisite -- see [Stamp].
 *
 * B4 · email_validation_failed rule="disposable".  The enum allows it; nothing specifies a list, a
 *      source or a behaviour. Only "format" is emitted.
 *
 * Two further notes for the design side, found while reading the registry:
 *   - enums.json §2 step_id values read "namev1.2", "emailv1.2", "email_verifyv1.2", "dobv1.2" --
 *     the version marker is concatenated into the value. Codegen would emit those verbatim.
 *   - enums.json §2 gives step_index as "—" for all four, so [BasicsStep.stepIndex] takes the
 *     ticket's numbers instead. The tracking note says never to use a literal; here there is
 *     nothing else to use.
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
    const val FIELD_REGISTRY_VERSION = "1.2.0"

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
     * T2, class 1. **BLOCKED (B1) — written, deliberately not called.**
     *
     * `surface` has no vocabulary in enums.json and the registry's trigger describes the Settings
     * "Stay reachable" toggles rather than this row. Calling it with a guessed surface would put an
     * unowned string into a consent record, which is the one payload where an invented value is
     * least acceptable. The toggle works; only its event is withheld.
     */
    fun consentChanged(on: Boolean, surface: String) = CONSENT_CHANGED to buildMap {
        put("channel", "email")
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
