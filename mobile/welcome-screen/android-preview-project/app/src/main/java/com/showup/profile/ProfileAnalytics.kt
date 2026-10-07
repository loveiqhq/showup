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
import com.showup.api.generated.model.FlowPosition

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

    /**
     * The bridge out of "The basics" (SHOWUP-155).
     *
     * A SCREEN BUT NOT A STEP, and §11's naming rules say so in as many words: this row exists and
     * the matching §2 row deliberately does not, "because firing profile_step_viewed on it would
     * put a phantom step in the completion funnel". That is why there is no `BasicsStep` for it and
     * why [ProfileAnalytics.stepViewed] cannot be called with it -- the type system carries the
     * rule rather than a comment asking people to remember it.
     */
    EmbraceBuild("profile_embrace_build", "ProfileEmbraceBuild"),

    /** "The real you", step 1 (SHOWUP-156). §11 row added by the design side before the ticket. */
    Photos("profile_photos", "ProfilePhotos"),

    /**
     * "The real you", step 2 (SHOWUP-158).
     *
     * **NOT IN §11 AT REGISTRY 1.3.0.** The ticket's own tracking section says the row "must be
     * added before the ticket is picked up", and it has not been -- §11 carries `profile_photos`
     * and stops. The two values here are the ones SHOWUP-158 quotes verbatim, so when the row
     * lands they should match and nothing changes; if the design side chooses differently, this is
     * the single place to correct. Recorded rather than silently invented, which is the rule the
     * tracking sections state twice.
     */
    Prompts("profile_prompts", "ProfilePrompts"),

    /**
     * "The real you", step 3 (SHOWUP-161). Registered in §11 on 16 September 2026.
     *
     * ONE screen_id FOR ALL SIX OF ITS STATES, and §11 says so in its own note: empty, video only,
     * voice only, both -- and the prompt-list sheet. A sheet is not a screen here; it is a state of
     * this one, and giving it a second id would split the screen's funnel in half.
     */
    Media("profile_media", "ProfileMedia"),

    /**
     * The notification permission ask (SHOWUP-162).
     *
     * A SCREEN BUT NOT A STEP, like the embrace bridge. §11's own note for this row says so: it
     * "sits OUTSIDE both progress bars and is not a step", and §2 holds a `notifications` step_id
     * whose `step_index` is a dash -- which exists so a future SKIP has a stable value and is
     * explicitly "not a licence to fire profile_step_viewed". This screen has no skip CTA, so it
     * fires no step event of any kind.
     *
     * Registry row added by the design side on 21 September 2026, before the ticket was written.
     */
    Notifications("profile_notifications", "ProfileNotifications"),

    /**
     * Stay reachable (SHOWUP-163).
     *
     * A SCREEN AND NOT A STEP, the same as the notifications ask: no `profile_step_viewed`, no
     * `_completed`, no `_skipped`, and there is no skip here to record.
     *
     * Registry row added 25 September 2026, before the ticket was written -- the standing rule.
     * The same change note supersedes part of `profile_notifications`: 09 no longer raises the OS
     * sheet, and the three permission events moved here.
     */
    Reachability("profile_reachability", "ProfileReachability"),

    /**
     * The full-bleed capture screen (states G and I).
     *
     * ONE ROW FOR VIDEO AND VOICE -- "the medium is `type` on the events, not a second id". It
     * fires its own `screen_viewed` with `referrer_screen_id`, because without it the two most
     * abandonable views in the flow are invisible.
     */
    MediaRecord("profile_media_record", "ProfileMediaRecord"),

    /** The post-Stop review screen (states H and J): play, retake, keep. One row for both media. */
    MediaReview("profile_media_review", "ProfileMediaReview"),

    /**
     * The location ask (SHOWUP-165). Row added 5 October 2026, before the ticket was written.
     *
     * A SCREEN AND NOT A STEP, the same as 09 and 10. §2's `location` id exists for one event
     * only -- `profile_step_skipped` on `Not now` -- and is "not a licence to fire
     * profile_step_viewed". Three states, one row: A, B and C are one mount.
     */
    Location("profile_location", "ProfileLocation"),

    /**
     * The second bridge (SHOWUP-166). Registered 9 September 2026 with screen 05; ticketed 5
     * October 2026. A screen and not a step, like [EmbraceBuild].
     */
    EmbraceDetails("profile_embrace_details", "ProfileEmbraceDetails"),

    // ── "Share some details" (SHOWUP-167 to SHOWUP-173). Rows added in registry 1.4.8 for all
    // eleven steps at once, "so the funnel runs unbroken from this bridge to the end of the flow".
    // Only the seven with a screen are spelled; the other four join when their screens do.
    Height("profile_height", "ProfileHeight"),
    Gender("profile_gender", "ProfileGender"),
    Orientation("profile_orientation", "ProfileOrientation"),
    DatingLanguage("profile_dating_language", "ProfileDatingLanguage"),
    Education("profile_education", "ProfileEducation"),
    Religion("profile_religion", "ProfileReligion"),
    Politics("profile_politics", "ProfilePolitics"),
}

/**
 * `variant` from enums.json §16 — which of the two bridges fired [ProfileAnalytics.embraceBridgeViewed].
 *
 * Two screens, one shell, two copy payloads. The set maps onto §11 one-for-one:
 * `build_profile` -> `profile_embrace_build` (SHOWUP-155) · `add_details` ->
 * `profile_embrace_details` (SHOWUP-166).
 */
object EmbraceVariant {
    const val BUILD_PROFILE = "build_profile"
    const val ADD_DETAILS = "add_details"
}

/** `field_id` from §1. */
object ProfileField {
    const val FIRST_NAME = "first_name"
    const val EMAIL = "email"

    /** The photo grid, as one field. A refused Continue is about the set, not about a slot. */
    const val PHOTOS = "photos"

    /** The prompt list, as one field. Same reason. */
    const val PROMPTS = "prompts"
}

// §18 prompt `entry_point` IS RETIRED, and the two enums that expressed it are gone with it.
//
// `PromptEntryPoint` (suggestion | browse | edit) and `TopicEntryPoint` (the same minus edit, a
// separate type so `prompt_topic_selected` could not report an edit even by mistake) both lived
// here. Registry 1.4.5, 25 September 2026: "which topics are chosen matters, where they were
// chosen from does not." The section is kept and emptied so numbering and citations stay stable;
// the types are deleted, because an enum nothing sends is a trap for the next person.
//
// `is_edit` survives them. It was the one fact `edit` carried that nothing else did, and it now
// rides on `prompt_editor_opened`, `prompt_saved` and -- new in 1.4.5 -- `prompt_editor_dismissed`.
//
// NOT AFFECTED, and easy to conflate: §21 `MediaEntryPoint` (see_the_prompts | retake) on the
// media prompt list, and the app-open `entry_point` on `profile_build_started`. Different
// sections, different questions, both still sent.

/**
 * `dismiss_method` from 23. THE CANONICAL SHEET-CLOSE VOCABULARY, and the only one.
 *
 * Every bottom sheet in the product reports its dismissal with these four, created on 16 September
 * 2026 by unifying three drifted spellings of the same four acts. This screen's write sheet used
 * to say `close | scrim | swipe | back`; the media prompt list said `cancel | ...`. Neither had
 * shipped, so nothing in the warehouse needed migrating -- but a value outside this set is now a
 * bug at the call site rather than a local dialect.
 *
 * [SystemBack] IS NOT [Close]. The Android gesture and the X are different acts by different
 * intentions and must not be folded together.
 */
enum class SheetDismissMethod(val trackingValue: String) {
    /** The X, or a Cancel control. */
    Close("close"),

    /** A tap on the scrim. */
    Backdrop("backdrop"),

    /** The drag-down gesture. */
    Swipe("swipe"),

    /** The Android back gesture or hardware key. Android only. */
    SystemBack("system_back"),
}

/**
 * The `rule` vocabulary shared by the validation events.
 *
 * A closed set in the registry: `required_missing` · `photos_below_minimum` · `nothing_selected` ·
 * `at_char_limit` · `format` · `impossible`. Only the five this flow can produce are named.
 */
object ValidationRule {
    const val REQUIRED_MISSING = "required_missing"
    const val FORMAT = "format"

    /** Continue pressed with fewer than four CONFIRMED photos (SHOWUP-156). */
    const val PHOTOS_BELOW_MINIMUM = "photos_below_minimum"

    /**
     * Continue pressed with no prompt saved (SHOWUP-158).
     *
     * `prompts_below_minimum`, added by registry 1.4.2 as "the sibling of photos_below_minimum".
     * This screen previously reported `nothing_selected`, which is a different act -- it belongs to
     * a chooser where nothing was ticked, not to a screen where nothing was written.
     */
    const val PROMPTS_BELOW_MINIMUM = "prompts_below_minimum"

    /**
     * A value that cannot be true -- a height outside 120-230 (SHOWUP-167).
     *
     * The ticket's own words: "Out of range is `rule: "impossible"`. §8 has no range value. If
     * analytics wants `out_of_range`, it gets defined in `enums.json` first -- not typed here."
     */
    const val IMPOSSIBLE = "impossible"
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

    /**
     * The ONE live Stay-reachable consent in the MVP.
     *
     * §8's note is explicit that `call · whatsapp · sms · calendar · email` are reserved for the
     * later scope, and SHOWUP-163 forbids `consent_changed` with any of them: "consent_changed
     * with any channel other than push" is on its must-NOT-fire list. They are deliberately not
     * spelled here, so a call site cannot reach for one.
     */
    const val PUSH = "push"
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
    /**
     * enums.json -> registry_version at the time of writing.
     *
     * 1.4.15 (5 October 2026) closes the nine "Share some details" tickets, one version each:
     * 1.4.7 location (§26 `location_unavailable_reason`), 1.4.8 the `add_details` bridge, then one
     * per detail step from height (1.4.9) to politics (1.4.15) -- `muslim` added to religion in
     * 1.4.14, the politics order made linear in 1.4.15. Read `changed_in_1_4_*` in enums.json for
     * each step's exact delta.
     *
     * 1.4.6 (25 September 2026) adds §25 `interest_channel` and the Stay reachable events, and
     * the §11 row for that screen. It sits on 1.4.5 of the same day, which RETIRED §18 prompt
     * `entry_point` -- "which
     * topics are chosen matters, where they were chosen from does not" -- taking `position` off
     * every prompt event with it and moving `is_edit` onto `prompt_editor_dismissed`. It sits on
     * 1.4.4 (§24 permission_status, for the notifications ask) and 1.4.2 (§23 dismiss_method
     * unified across every bottom sheet, `prompts_below_minimum`, `prompts` re-verified at
     * step_index 2).
     *
     * READ IT FROM HERE AND NOWHERE ELSE -- a payload stamped with a version the values did not
     * come from is worse than an unstamped one, because it looks checked.
     */
    const val FIELD_REGISTRY_VERSION = "1.4.15"

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
    const val EMBRACE_BRIDGE_VIEWED = "embrace_bridge_viewed"
    const val PHOTO_SLOT_TAPPED = "photo_slot_tapped"
    const val PHOTO_ADDED = "photo_added"
    const val PHOTO_REMOVED = "photo_removed"
    const val PHOTO_REORDERED = "photo_reordered"
    const val PHOTOS_MINIMUM_MET = "photos_minimum_met"
    const val PROMPT_TOPIC_LIST_OPENED = "prompt_topic_list_opened"
    const val PROMPT_TOPIC_LIST_DISMISSED = "prompt_topic_list_dismissed"
    const val PROMPT_TOPIC_SELECTED = "prompt_topic_selected"
    const val PROMPT_EDITOR_OPENED = "prompt_editor_opened"
    const val PROMPT_EDITOR_DISMISSED = "prompt_editor_dismissed"
    const val PROMPT_SAVED = "prompt_saved"
    const val PROMPT_EXAMPLE_DISMISSED = "prompt_example_dismissed"
    const val PROMPT_CHAR_LIMIT_REACHED = "prompt_char_limit_reached"
    const val PROMPTS_MINIMUM_MET = "prompts_minimum_met"

    // Family E, media half (SHOWUP-161). Every one of these ships with this ticket -- they are all
    // `Not built` in events.json. `media_prompt_ranking_published` is deliberately absent: it is
    // server-side and ships with the ranking job, not with the screen.
    const val MEDIA_SCREEN_VIEWED = "media_screen_viewed"
    const val MEDIA_PROMPT_LIST_OPENED = "media_prompt_list_opened"
    const val MEDIA_PROMPT_SELECTED = "media_prompt_selected"
    const val MEDIA_PROMPT_LIST_DISMISSED = "media_prompt_list_dismissed"
    const val VIDEO_RECORDING_STARTED = "video_recording_started"
    const val VOICE_RECORDING_STARTED = "voice_recording_started"
    const val MEDIA_REVIEW_SHOWN = "media_review_shown"
    const val MEDIA_PREVIEW_PLAYED = "media_preview_played"
    const val VIDEO_PROMPT_RECORDED = "video_prompt_recorded"
    const val VOICE_PROMPT_RECORDED = "voice_prompt_recorded"
    const val MEDIA_RETAKEN = "media_retaken"
    const val MEDIA_DELETED = "media_deleted"

    // ── family G, permissions and consent (SHOWUP-162) ──────────────────────
    //
    // ALL FOUR ARE NEW. Every one is `implemented: false` in events.json and the first three are
    // `backend_status: "Client only"` -- the notification ask is the first surface in the product
    // that needs them, so they are part of this ticket rather than a follow-up.
    const val PERMISSION_PROMPTED = "permission_prompted"
    const val PERMISSION_OS_SHEET_SHOWN = "permission_os_sheet_shown"
    const val PERMISSION_RESULT = "permission_result"
    const val PERMISSION_STATUS_CHANGED = "permission_status_changed"

    // ── Stay reachable, registry 1.4.6 (SHOWUP-163) ─────────────────────────
    //
    // Every one is `implemented: false` before this ticket. `permission_prompted`,
    // `permission_os_sheet_shown` and `permission_result` are NOT new -- they MOVED here from the
    // notifications ask, which no longer raises the OS dialog.
    const val PERMISSION_SETTINGS_OPENED = "permission_settings_opened"
    const val CONSENT_DEACTIVATION_CONFIRM_SHOWN = "consent_deactivation_confirm_shown"
    const val CONSENT_DEACTIVATION_ABANDONED = "consent_deactivation_abandoned"
    const val CONSENT_DEACTIVATION_CONFIRMED = "consent_deactivation_confirmed"
    const val CHANNEL_INTEREST_CHANGED = "channel_interest_changed"
    const val REACHABILITY_SAVED = "reachability_saved"

    // ── the location ask, registry 1.4.7 (SHOWUP-165) ───────────────────────
    //
    // NEW for this screen. The other permission events above are shared with notifications and
    // take `type: "location"` here.
    const val PERMISSION_DENIED_RECOVERY_SHOWN = "permission_denied_recovery_shown"

    /** `permission_*.type` for the location ask. §8's closed set on `permission_prompted`. */
    const val PERMISSION_LOCATION = "location"

    // ── family F, profile attributes (SHOWUP-167 to SHOWUP-173) ─────────────
    //
    // `detail_answered` and `detail_skipped` carry the FIELD's own §1 class, stamped at emit time
    // -- the registry leaves their class blank because it differs per field.
    const val DETAIL_ANSWERED = "detail_answered"
    const val DETAIL_SKIPPED = "detail_skipped"
    const val FIELD_DISPLAY_OPTED_OUT = "field_display_opted_out"

    /**
     * The closed set on `permission_prompted.type`, from `enums.json` §8.
     *
     * `permission_os_sheet_shown.type` and `permission_result.type` are typed `str` in the
     * registry and take the SAME value -- the registry is loose there and the ticket is not.
     */
    const val PERMISSION_NOTIFICATIONS = "notifications"

    /**
     * T2, class 1. OUR OWN pre-permission surface was shown, before any OS sheet.
     *
     * Fired on view, and NOT fired when the screen is skipped -- events.json says so in as many
     * words: "it does not fire when the screen is skipped because the OS status is already
     * determined". The users who never see the ask are deliberately unmeasured; there is no
     * `ask skipped` event and one must not be invented at a call site.
     */
    fun permissionPrompted(type: String = PERMISSION_NOTIFICATIONS) =
        PERMISSION_PROMPTED to buildMap<String, Any> {
            put("type", type)
            putAll(Stamp.of(1))
        }

    /** T2, class 1. The platform's own dialog was raised -- on the tap, never on arrival. */
    fun permissionOsSheetShown(type: String = PERMISSION_NOTIFICATIONS) =
        PERMISSION_OS_SHEET_SHOWN to buildMap<String, Any> {
            put("type", type)
            putAll(Stamp.of(1))
        }

    /**
     * T2, class 1. The user answered the sheet.
     *
     * `result` is typed `granted | denied | limited` for the whole family and **`limited` cannot
     * occur for notifications** -- it is a photo-library state. The signature takes a Boolean
     * rather than a string so there is no third value to pass by mistake.
     */
    fun permissionResult(granted: Boolean, type: String = PERMISSION_NOTIFICATIONS) =
        PERMISSION_RESULT to buildMap<String, Any> {
            put("type", type)
            put("result", if (granted) "granted" else "denied")
            putAll(Stamp.of(1))
        }

    /**
     * T2, class 1. The status changed OUTSIDE our app and the reconciler noticed.
     *
     * NOT THE ANSWER TO OUR OWN SHEET. That is [permissionResult], and the two must never both
     * fire for one act -- this one is for Settings, an OS update, a Focus mode, a restore.
     *
     * `from` IS WHAT THE SERVER WAS LAST TOLD, not what the screen last saw. events.json is
     * explicit, and the reason is that a change the client never synced is exactly the case the
     * event exists for.
     */
    fun permissionStatusChanged(
        from: PermissionStatus,
        to: PermissionStatus,
        detectedOn: String,
        inFlow: Boolean,
        type: String = PERMISSION_NOTIFICATIONS,
    ) = PERMISSION_STATUS_CHANGED to buildMap<String, Any> {
        put("type", type)
        put("from", from.trackingValue)
        put("to", to.trackingValue)
        put("detected_on", detectedOn)
        put("in_flow", inFlow)
        putAll(Stamp.of(1))
    }

    /** Where a status change was noticed. The closed pair on `permission_status_changed`. */
    const val DETECTED_ON_LAUNCH = "launch"
    const val DETECTED_ON_FOREGROUND = "foreground"

    /**
     * T2, class 1. The location ask's B or C was shown (SHOWUP-165).
     *
     * `reason` is `enums.json` §26 `location_unavailable_reason` -- "REQUIRED when type is
     * location, absent otherwise". It is the only builder that takes the location type, so it
     * always carries one.
     */
    fun permissionDeniedRecoveryShown(reason: LocationUnavailableReason) =
        PERMISSION_DENIED_RECOVERY_SHOWN to buildMap<String, Any> {
            put("type", PERMISSION_LOCATION)
            put("reason", reason.trackingValue)
            putAll(Stamp.of(1))
        }

    /**
     * T2, class 0. `Not now — ask me when I search` on the location ask.
     *
     * `step_id: "location"` is the ONE event §2's location id exists for. The same event the detail
     * steps send on Skip, from the same vocabulary.
     */
    fun locationSkipped() = PROFILE_STEP_SKIPPED to buildMap<String, Any> {
        put("step_id", FlowPosition.location.value)
        put("screen_id", ProfileScreen.Location.screenId)
        putAll(Stamp.of(0))
    }

    // ── "Share some details" ─────────────────────────────────────────────────

    /** T2, class 0. Step [step] was shown. */
    fun detailStepViewed(step: DetailStep) = PROFILE_STEP_VIEWED to buildMap<String, Any> {
        put("step_id", step.stepId)
        put("step_index", step.stepIndex)
        putAll(Stamp.of(0))
    }

    /** T2, class 0. Continue was accepted and the answer stored. */
    fun detailStepCompleted(step: DetailStep, timeOnStepSeconds: Int) =
        PROFILE_STEP_COMPLETED to buildMap<String, Any> {
            put("step_id", step.stepId)
            put("time_on_step_s", timeOnStepSeconds)
            putAll(Stamp.of(0))
        }

    /** T2, class 0. Skip, or a Continue that is a skip. Never from gender or orientation. */
    fun detailStepSkipped(step: DetailStep) = PROFILE_STEP_SKIPPED to buildMap<String, Any> {
        put("step_id", step.stepId)
        put("screen_id", step.screen.screenId)
        putAll(Stamp.of(0))
    }

    /**
     * T1, the field's class. One per ACCEPTED Continue, carrying the final answer.
     *
     * [valueBucketed] is the §1 value -- for height the bucket, never the cm; for dating language
     * the ticked values in list order, comma-joined. NEVER a display label, and never empty: an
     * empty answer is a skip and goes through [detailSkipped] instead.
     */
    fun detailAnswered(step: DetailStep, valueBucketed: String): Pair<String, Map<String, Any>> {
        require(valueBucketed.isNotEmpty()) { "an empty answer is a skip, not an answer" }
        return DETAIL_ANSWERED to buildMap {
            put("field_id", step.fieldId)
            put("value_bucketed", valueBucketed)
            putAll(Stamp.of(step.sensitivityClass))
        }
    }

    /** T1, the field's class. Fires with [detailStepSkipped], BEFORE it -- the order the tickets give. */
    fun detailSkipped(step: DetailStep) = DETAIL_SKIPPED to buildMap<String, Any> {
        put("field_id", step.fieldId)
        putAll(Stamp.of(step.sensitivityClass))
    }

    /** T1, class 0. The visibility box was TICKED. Nothing is sent on untick, by registry rule. */
    fun fieldDisplayOptedOut(step: DetailStep) =
        FIELD_DISPLAY_OPTED_OUT to buildMap<String, Any> {
            put("field_id", step.fieldId)
            putAll(Stamp.of(0))
        }

    /** T1, class 0. Continue refused on height, gender or orientation -- on the press, not render. */
    fun detailValidationFailed(step: DetailStep, rule: String) =
        FORM_VALIDATION_FAILED to buildMap<String, Any> {
            put("field_id", step.fieldId)
            put("rule", rule)
            put("screen_id", step.screen.screenId)
            put("step_id", step.stepId)
            putAll(Stamp.of(0))
        }

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

    /**
     * T2, class 0. The bridge screen was shown.
     *
     * Fires ALONGSIDE [screenViewed] and instead of nothing else. SHOWUP-155 is explicit about the
     * three that must NOT fire here: [stepViewed] and [stepCompleted], because a bridge is not a
     * step and would otherwise show up as a phantom stage in the completion funnel; and
     * [buildStarted], because the build started four screens ago on the name step. There is also
     * no CTA event -- the screen has one exit and [screenViewed] on photos already records it.
     */
    fun embraceBridgeViewed(variant: String = EmbraceVariant.BUILD_PROFILE) =
        EMBRACE_BRIDGE_VIEWED to buildMap<String, Any> {
            put("variant", variant)
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

    // ── family E · Profile Photos/Media (SHOWUP-156) ─────────────────────────
    //
    // NEVER A PHOTO, A FILENAME OR A LIBRARY IDENTIFIER. Slot indices, a source and counts only.
    // The registry says so and so does the ticket; what makes it true is that none of the
    // builders below takes anything else.

    /** T2, class 0. A slot tap, or `Add more` revealing slots 5-6. */
    fun photoSlotTapped(slotIndex: Int, isOptional: Boolean, action: String) =
        PHOTO_SLOT_TAPPED to buildMap<String, Any> {
            put("slot_index", slotIndex)
            put("is_optional", isOptional)
            put("action", action)
            putAll(Stamp.of(0))
        }

    /**
     * T2, class 0. Fires on the CONFIRMED upload, never on the pick.
     *
     * `filled_count` is the confirmed count after this one landed, which is what makes the funnel
     * agree with the number on screen.
     */
    fun photoAdded(slotIndex: Int, source: PhotoSource, filledCount: Int) =
        PHOTO_ADDED to buildMap<String, Any> {
            put("slot_index", slotIndex)
            put("source", source.trackingValue)
            put("filled_count", filledCount)
            put("max_slots", PHOTOS_MAX)
            putAll(Stamp.of(0))
        }

    /** T2, class 0. */
    fun photoRemoved(slotIndex: Int, filledCount: Int) =
        PHOTO_REMOVED to buildMap<String, Any> {
            put("slot_index", slotIndex)
            put("filled_count", filledCount)
            putAll(Stamp.of(0))
        }

    /** T2, class 0. Drag finished somewhere other than where it started. */
    fun photoReordered(from: Int, to: Int) =
        PHOTO_REORDERED to buildMap<String, Any> {
            put("from", from)
            put("to", to)
            putAll(Stamp.of(0))
        }

    /**
     * T2, class 0. ONCE, on the first crossing of four CONFIRMED uploads.
     *
     * Not on the fourth pick, and not again after a removal takes the count below four and back
     * up -- `PhotoGridState.minimumReported` is what holds that, because a funnel step that can
     * fire twice for one user cannot be counted.
     */
    fun photosMinimumMet(count: Int) = PHOTOS_MINIMUM_MET to buildMap<String, Any> {
        put("count", count)
        put("max_slots", PHOTOS_MAX)
        putAll(Stamp.of(0))
    }

    // ── family · Profile Attributes (SHOWUP-158) ─────────────────────────────
    //
    // FAMILY E, AND NOT FAMILY F -- A COLLISION THE REGISTRY RESOLVED ON 16 SEPTEMBER 2026
    //
    // This screen used to emit `prompt_topic_picker_opened`, `prompt_topic_selected` (topic_id
    // only), `prompt_answered` and `prompt_edited`. Those are the v1.0 family F names, and at
    // registry 1.3.0 they were the only prompt rows that existed, so they were used as named.
    //
    // `events.json` at 1.4.2 carries all four marked SUPERSEDED -- DO NOT FIRE, emptied of their
    // payloads so the name resolves to the notice rather than being re-implemented from a stale
    // spec, each naming its family E replacement. The duplicate `prompt_topic_selected` row was
    // deleted outright, so the name now has exactly one definition. Nothing on this screen may
    // fire a family F prompt event.
    //
    // WHAT FAMILY E BUYS, which is why it wins: `entry_point` (18) -- whether the topic came from
    // a suggestion card or from browse-all -- which the ticket calls "the one measurement this
    // revision exists to produce"; an abandonment event for each sheet; and `selection_index`,
    // which answers "which topic did they reach for FIRST" without anybody sorting timestamps.
    //
    // NEVER THE ANSWER AND NEVER THE DRAFT. Every builder below takes a LENGTH rather than a
    // string, so there is no signature here that can carry the text even by accident.

    /** T2, class 0. `Browse all 15 topics` was pressed. */
    fun promptTopicListOpened(usedCount: Int, screen: ProfileScreen = ProfileScreen.Prompts) =
        PROMPT_TOPIC_LIST_OPENED to buildMap<String, Any> {
            put("used_count", usedCount)
            put("screen_id", screen.screenId)
            putAll(Stamp.of(0))
        }

    /**
     * T2, class 0. The topic sheet closed WITHOUT a topic being picked.
     *
     * MUTUALLY EXCLUSIVE WITH [promptTopicSelected]: picking a topic replaces the sheet with the
     * write sheet and fires that instead. Together they close the browse sheet's funnel, so
     * `prompt_topic_list_opened` = selected + dismissed, and a gap in that sum is a bug rather
     * than a behaviour.
     */
    fun promptTopicListDismissed(
        method: SheetDismissMethod,
        usedCount: Int,
        timeOnSheetSeconds: Int,
        screen: ProfileScreen = ProfileScreen.Prompts,
    ) = PROMPT_TOPIC_LIST_DISMISSED to buildMap<String, Any> {
        put("dismiss_method", method.trackingValue)
        put("used_count", usedCount)
        put("time_on_sheet_s", timeOnSheetSeconds)
        put("screen_id", screen.screenId)
        putAll(Stamp.of(0))
    }

    /**
     * T2, class 0. A topic was chosen from a suggestion card or from a row of the browse sheet.
     *
     * NEVER ON AN EDIT. The registry change of 16 September 2026 -- reopening a saved prompt is
     * not a fresh choice of topic, and firing this there inflated topic demand with re-edits of
     * prompts already written. An edit fires [promptEditorOpened] alone.
     *
     * WHERE THE TAP CAME FROM IS DELIBERATELY NOT RECORDED, from registry 1.4.5 (25 September
     * 2026): "which topics are chosen matters, where they were chosen from does not". §18 is
     * retired and `position` went with it, so a suggestion card and a row of the browse sheet
     * produce the same event and the type that used to tell them apart is gone.
     *
     * `topic_group` is looked up rather than passed, so a caller cannot file a topic under a group
     * it is not in.
     */
    fun promptTopicSelected(
        topicId: String,
        selectionIndex: Int,
    ) = PROMPT_TOPIC_SELECTED to buildMap<String, Any> {
        put("topic_id", topicId)
        put("topic_group", topicGroupFor(topicId))
        put("selection_index", selectionIndex)
        putAll(Stamp.of(0))
    }

    /**
     * T2, class 0. The write sheet mounted -- on a card, on a browse row, or on the pencil.
     *
     * [isEdit] is the whole of what §18 used to carry here: the pencil is a different act from a
     * first write, and it is the one distinction 1.4.5 kept when it retired the rest.
     */
    fun promptEditorOpened(
        topicId: String,
        isEdit: Boolean,
        promptCount: Int,
    ) = PROMPT_EDITOR_OPENED to buildMap<String, Any> {
        put("topic_id", topicId)
        put("is_edit", isEdit)
        put("prompt_count", promptCount)
        putAll(Stamp.of(0))
    }

    /**
     * T2, class 0. The write sheet closed without saving.
     *
     * THE ABANDONMENT EVENT THIS SCREEN IS DESIGNED AGAINST. `had_draft` separates "changed their
     * mind" from "could not finish"; [draftLength] is bucketed on the way in and the draft itself
     * never leaves the device.
     *
     * [isEdit] ARRIVED WITH 1.4.5 and is the reason retiring §18 lost nothing here. The old
     * `entry_point: "edit"` was carrying one fact this event actually needed -- abandoning a
     * rewrite of something already written is not the same as abandoning a blank one -- so the
     * fact stayed and the enum went.
     */
    fun promptEditorDismissed(
        topicId: String,
        isEdit: Boolean,
        draftLength: Int,
        method: SheetDismissMethod,
    ) = PROMPT_EDITOR_DISMISSED to buildMap<String, Any> {
        put("topic_id", topicId)
        put("is_edit", isEdit)
        put("had_draft", draftLength > 0)
        put("draft_length_bucket", promptLengthBucket(draftLength))
        put("dismiss_method", method.trackingValue)
        putAll(Stamp.of(0))
    }

    /**
     * T2, class 0. Save pressed with a non-empty answer.
     *
     * [answerLength] rather than the answer: there is no signature here that can carry the text.
     * [promptCount] is the number the user holds AFTER this save, and an edit does not raise it.
     */
    fun promptSaved(
        topicId: String,
        isEdit: Boolean,
        answerLength: Int,
        promptCount: Int,
    ) = PROMPT_SAVED to buildMap<String, Any> {
        put("topic_id", topicId)
        put("topic_group", topicGroupFor(topicId))
        put("is_edit", isEdit)
        put("length_bucket", promptLengthBucket(answerLength))
        put("prompt_count", promptCount)
        putAll(Stamp.of(0))
    }

    /** T2, class 0. The worked example hidden with its X. High volume means it is in the way. */
    fun promptExampleDismissed(topicId: String) =
        PROMPT_EXAMPLE_DISMISSED to buildMap<String, Any> {
            put("topic_id", topicId)
            putAll(Stamp.of(0))
        }

    /** T2, class 0. 160 reached. ONCE PER EDITOR SESSION, not per keystroke. */
    fun promptCharLimitReached(topicId: String) =
        PROMPT_CHAR_LIMIT_REACHED to buildMap<String, Any> {
            put("topic_id", topicId)
            putAll(Stamp.of(0))
        }

    /**
     * T2, class 0. The first prompt was saved, so the step can be completed.
     *
     * ONCE, on the first crossing, carrying the topic that got the user there.
     *
     * It used to carry the entry point with it, and the registry called that pairing "the single
     * most useful row on the screen". 1.4.5 decided otherwise and retired §18; the topic is what
     * is left, and it is the half the ranking actually reads.
     */
    fun promptsMinimumMet(count: Int, topicId: String) =
        PROMPTS_MINIMUM_MET to buildMap<String, Any> {
            put("count", count)
            put("topic_id", topicId)
            putAll(Stamp.of(0))
        }

    /**
     * T2, class 0. A step of "The real you" was reached.
     *
     * `step_index` comes from [RealYouStep] -- photos 1, prompts 2, media 3 -- and 2 of registry
     * 1.4.2 now says exactly that, re-verified on 16 September 2026. It did not at 1.3.0, where
     * prompts read 10: the code was right and the registry has caught up.
     */
    fun realYouStepViewed(step: RealYouStep) = PROFILE_STEP_VIEWED to buildMap<String, Any> {
        put("step_id", step.stepId)
        put("step_index", step.stepIndex)
        putAll(Stamp.of(0))
    }

    /**
     * T2, class 0. Continue was ACCEPTED on a step of "The real you".
     *
     * [promptCount] is OPTIONAL AND SCREEN-SCOPED -- 1 to 3 on the prompts step and omitted
     * everywhere else, which is what the registry's `applies_to` means. An empty property is worse
     * than an absent one, so null omits the key rather than writing a zero.
     */
    fun realYouStepCompleted(
        step: RealYouStep,
        timeOnStepSeconds: Int,
        promptCount: Int? = null,
    ) = PROFILE_STEP_COMPLETED to buildMap<String, Any> {
        put("step_id", step.stepId)
        put("time_on_step_s", timeOnStepSeconds)
        if (promptCount != null) put("prompt_count", promptCount)
        putAll(Stamp.of(0))
    }

    /**
     * T1, class 0. The refused press on a step outside "The basics".
     *
     * A second builder rather than a widened first one: [formValidationFailed] takes a
     * [BasicsStep] and there is no BasicsStep for a photo or a prompt. The payload is identical
     * and both come from the same registry row -- what differs is which step vocabulary the caller
     * can offer.
     */
    fun realYouValidationFailed(
        fieldId: String,
        rule: String,
        screen: ProfileScreen,
        step: RealYouStep,
    ) = FORM_VALIDATION_FAILED to buildMap<String, Any> {
        put("field_id", fieldId)
        put("rule", rule)
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
        // THE CHANNEL IS A PARAMETER since SHOWUP-163, and it was hard-coded to the marketing
        // email before because that was the only consent in the product. Stay reachable adds
        // `push`, and §8's other values are reserved for the later scope -- a call site reaching
        // for `whatsapp` here is on the ticket's must-NOT-fire list.
        channel: String = ConsentChannel.MARKETING_EMAIL,
        surface: String = ConsentSurface.PROFILE_CREATION,
    ) = CONSENT_CHANGED to buildMap {
        put("channel", channel)
        put("on", on)
        put("surface", surface)
        putAll(Stamp.of(1))
    }

    // ── Stay reachable (SHOWUP-163) ─────────────────────────────────────────

    /**
     * T2, class 1. Settings was opened for a permission we cannot re-ask for.
     *
     * TWO PLACES ON THIS SCREEN, one act: the `Open Settings` primary of the post-denial dialog,
     * and the toggle when the status is already denied. Both are the user leaving for Settings.
     *
     * NOT `consent_deactivation_abandoned`. The ticket calls that out by name, and the reason is
     * that the two answer different questions: abandoning is "they thought better of it and push
     * stayed on", opening Settings is "they left the app to fix it". Counting the second as the
     * first would make the deactivation funnel look healthier than it is.
     */
    fun permissionSettingsOpened(type: String = PERMISSION_NOTIFICATIONS) =
        PERMISSION_SETTINGS_OPENED to buildMap<String, Any> {
            put("type", type)
            putAll(Stamp.of(1))
        }

    /**
     * T2, class 1. The deactivation confirm went up.
     *
     * THE DENOMINATOR of the deactivation funnel: shown = abandoned + confirmed + settings. It
     * fires for BOTH states C and D, because both are the same dialog asking the same question --
     * only the primary's label differs.
     */
    fun consentDeactivationConfirmShown(channel: String = ConsentChannel.PUSH) =
        CONSENT_DEACTIVATION_CONFIRM_SHOWN to buildMap<String, Any> {
            put("channel", channel)
            putAll(Stamp.of(1))
        }

    /** T2, class 1. `Keep active`, or a tap on the scrim. Push stayed on. */
    fun consentDeactivationAbandoned(channel: String = ConsentChannel.PUSH) =
        CONSENT_DEACTIVATION_ABANDONED to buildMap<String, Any> {
            put("channel", channel)
            putAll(Stamp.of(1))
        }

    /** T2, class 1. `Confirm deactivation`. Push is now off, and [consentChanged] follows. */
    fun consentDeactivationConfirmed(channel: String = ConsentChannel.PUSH) =
        CONSENT_DEACTIVATION_CONFIRMED to buildMap<String, Any> {
            put("channel", channel)
            putAll(Stamp.of(1))
        }

    /**
     * T2, class 0. An interest box was checked or unchecked.
     *
     * NOT A CONSENT, AND THE TYPE SAYS SO. [InterestChannel] is §25, a separate vocabulary from
     * §8's `channel` precisely so that "an interest can never be read as a consent" -- so this
     * builder cannot be handed a consent channel and [consentChanged] cannot be handed an
     * interest. The ticket's hardest rule is "no `consent_changed` for a checkbox. Ever."
     *
     * Class 0, not 1: an interest in a channel that does not exist is not personal data. The
     * consent events above are class 1 because a consent record is.
     *
     * EVERY FLIP, not the final state. `reachability_saved.interest` carries the final state; this
     * carries the path, so a check followed by an uncheck is not counted as interest.
     */
    fun channelInterestChanged(channel: InterestChannel, on: Boolean) =
        CHANNEL_INTEREST_CHANGED to buildMap<String, Any> {
            put("channel", channel.trackingValue)
            put("on", on)
            put("screen_id", ProfileScreen.Reachability.screenId)
            putAll(Stamp.of(0))
        }

    /**
     * T2, class 1. The server confirmed the push consent and the flow is about to advance.
     *
     * AFTER THE CONFIRMATION, never on the tap. The screen is "not optimistic" by epic rule: the
     * position advances only once the consent is committed, so an event fired on the tap would
     * count saves that never happened.
     *
     * The payload CHANGED in 1.4.6 -- `channels_on`, `count` and `has_phone` are gone, and the
     * ticket says to remove them if they are already emitted. They never were here; the screen is
     * new. What is left is the three facts the demand test actually reads.
     */
    fun reachabilitySaved(
        pushOn: Boolean,
        permission: PermissionStatus,
        interest: List<String>,
    ) = REACHABILITY_SAVED to buildMap<String, Any> {
        put("push_on", pushOn)
        put("permission", permission.trackingValue)
        put("interest", interest)
        putAll(Stamp.of(1))
    }

    /**
     * T2, class 0. A legal link was tapped. ONE EVENT for all seven links on three screens.
     *
     * Delegates to the shared builder rather than spelling the payload again -- the one in
     * `SignUpAnalytics` is where the §13 vocabulary and the `screen_id` key live, and a second
     * copy is how the two would come to disagree. What this adds is the §11 id, taken from the
     * screen registry instead of typed.
     */
    fun legalLinkTapped(link: String, screen: ProfileScreen) =
        com.showup.analytics.SignUpAnalytics.legalLinkTapped(link, screen.screenId)

    /** T2, class 0. **BLOCKED** — the skip path itself is an open question in ticket 02. */
    fun stepSkipped(step: BasicsStep, screen: ProfileScreen) =
        PROFILE_STEP_SKIPPED to buildMap<String, Any> {
            put("step_id", step.stepId)
            // NEWLY REQUIRED AT v1.4 and missing from the build until SHOWUP-161. Without it every
            // skippable step in the product reports the same shape and the funnel cannot say WHERE
            // a user opted out -- which is the only question the event is asked.
            put("screen_id", screen.screenId)
            putAll(Stamp.of(0))
        }

    // -- family E, media (SHOWUP-161) -----------------------------------------
    //
    // `type` IS REQUIRED ON EVERY EVENT BELOW except the two screen views, and it is not optional
    // in the way a nullable field is optional: one screen carries two media, so without it "a tap
    // on the voice card and a tap on the video card are the same row, and nothing about this
    // screen can be answered". [MediaKind] carries it, so the type system supplies it rather than
    // a call site remembering to.
    //
    // NOTHING HERE EVER CARRIES THE RECORDING. No frame, no transcript, no waveform, no file path.
    // `duration_s` and the prompt id are the whole payload: media of a user's face and voice is
    // the most sensitive artefact in profile creation and none of it belongs in analytics.

    /**
     * T1, class 0. The screen's entry state -- fires on EVERY mount, including the return from an
     * accepted take.
     *
     * NAMES THE PROMPTS IT SHOWED, and that is the whole reason it is separate from `screen_viewed`.
     * The ranking moves, so a view that does not record which prompts were on the cards cannot be
     * attributed afterwards, and a drop in recording rate cannot be told apart from a bad prompt.
     *
     * No `type`: it describes the screen, not a medium.
     */
    fun mediaScreenViewed(state: MediaState) = MEDIA_SCREEN_VIEWED to buildMap<String, Any> {
        put("screen_id", ProfileScreen.Media.screenId)
        put("has_video", state.hasVideo)
        put("has_voice", state.hasVoice)
        put("preview_video_prompt_id", state.preview(MediaKind.Video).id)
        put("preview_voice_prompt_id", state.preview(MediaKind.Voice).id)
        put("preview_source", state.previewSource.trackingValue)
        putAll(Stamp.of(0))
    }

    /**
     * T2, class 0. One screen carries two steps, so this fires once per medium.
     *
     * Section 2 gives `media_video` and `media_voice` the same `step_index` 3 for exactly this
     * reason. Collapsing them into one event would give the group a step-3 funnel that cannot be
     * read per medium, which is the thing every other decision on this screen is built to avoid.
     */
    fun mediaStepViewed(kind: MediaKind) = PROFILE_STEP_VIEWED to buildMap<String, Any> {
        put("step_id", kind.stepId)
        put("step_index", MediaKind.STEP_INDEX)
        putAll(Stamp.of(0))
    }

    /** T2, class 0. Continue pressed with this medium recorded. */
    fun mediaStepCompleted(kind: MediaKind, timeOnStepSeconds: Int) =
        PROFILE_STEP_COMPLETED to buildMap<String, Any> {
            put("step_id", kind.stepId)
            put("time_on_step_s", timeOnStepSeconds)
            putAll(Stamp.of(0))
        }

    /**
     * T1, class 0. This medium was left empty.
     *
     * Fires from `Skip for now` AND from a Continue with nothing in the slot: "an empty Continue is
     * a skip that the user did not call one". Carries the pair so a skip can be read against what
     * the user did record -- somebody who filmed a video and skipped the voice note is not the same
     * user as one who skipped both.
     */
    fun mediaStepSkipped(kind: MediaKind, state: MediaState) =
        PROFILE_STEP_SKIPPED to buildMap<String, Any> {
            put("step_id", kind.stepId)
            put("screen_id", ProfileScreen.Media.screenId)
            put("has_video", state.hasVideo)
            put("has_voice", state.hasVoice)
            putAll(Stamp.of(0))
        }

    /**
     * T1, class 0. `See the prompts`, or `Retake` over something already recorded.
     *
     * `entry_point` (section 21) and `has_existing` answer different questions and both ship: one
     * is intent, one is state. The registry note is explicit that `entry_point` is "never inferred
     * from whether an artefact exists".
     */
    fun mediaPromptListOpened(kind: MediaKind, entryPoint: MediaEntryPoint, hasExisting: Boolean) =
        MEDIA_PROMPT_LIST_OPENED to buildMap<String, Any> {
            put("type", kind.trackingValue)
            put("entry_point", entryPoint.trackingValue)
            put("has_existing", hasExisting)
            put("screen_id", ProfileScreen.Media.screenId)
            putAll(Stamp.of(0))
        }

    /**
     * T1, class 0. Fires on the COMMIT CTA, not on every row tap.
     *
     * `was_previewed` IS THE FIELD THAT KEEPS THE RANKING HONEST. The previewed prompt is far more
     * visible than the other ten, so counting its own selections would make it win because it was
     * shown. The job counts only `was_previewed: false` takes, and this is where that is recorded.
     *
     * `selections_before` counts the rows tried and abandoned before committing -- the difference
     * between a user who knew what they wanted and one who read all eleven.
     */
    fun mediaPromptSelected(
        kind: MediaKind,
        prompt: MediaPrompt,
        selectionsBefore: Int,
        wasPreviewed: Boolean,
    ) = MEDIA_PROMPT_SELECTED to buildMap<String, Any> {
        put("type", kind.trackingValue)
        // The section-20 id, never the display string: an edit to the copy must not orphan clips.
        put("media_prompt_id", prompt.id)
        put("position", prompt.position)
        put("is_own_prompt", prompt.isOwn)
        put("selections_before", selectionsBefore)
        put("was_previewed", wasPreviewed)
        putAll(Stamp.of(0))
    }

    /**
     * T1, class 0. The list was closed without committing.
     *
     * The drop-off the eleven prompts are accountable for. `had_selection` splits "read it and
     * left" from "picked one and lost their nerve", which are two different problems.
     *
     * `dismiss_method`, NOT `method` (section 23) -- `method` already carries `phone, apple, google`.
     */
    fun mediaPromptListDismissed(
        kind: MediaKind,
        method: SheetDismissMethod,
        hadSelection: Boolean,
        timeOnSheetSeconds: Int,
    ) = MEDIA_PROMPT_LIST_DISMISSED to buildMap<String, Any> {
        put("type", kind.trackingValue)
        put("dismiss_method", method.trackingValue)
        put("had_selection", hadSelection)
        put("time_on_sheet_s", timeOnSheetSeconds)
        putAll(Stamp.of(0))
    }

    /**
     * T1, class 0. The viewfinder started capturing.
     *
     * Two event NAMES rather than one with `type`, because that is what family E registers --
     * `video_recording_started` and `voice_recording_started`. The name carries the medium here and
     * the payload carries it everywhere else; both are the registry's choice, not ours.
     */
    fun mediaRecordingStarted(
        kind: MediaKind,
        prompt: MediaPrompt,
        attempt: Int,
        isRetake: Boolean,
    ) = when (kind) {
        MediaKind.Video -> VIDEO_RECORDING_STARTED
        MediaKind.Voice -> VOICE_RECORDING_STARTED
    } to buildMap<String, Any> {
        put("type", kind.trackingValue)
        put("media_prompt_id", prompt.id)
        put("is_own_prompt", prompt.isOwn)
        put("attempt", attempt)
        put("is_retake", isRetake)
        putAll(Stamp.of(0))
    }

    /**
     * T1, class 0. Stop, or the cap, produced a take and the review screen is up.
     *
     * THE DENOMINATOR FOR THE WHOLE REVIEW SCREEN: of the takes that reached it, how many were
     * played, retaken or kept. `stop_reason: max_length` dominating means the cap is too short --
     * which is the measurement that decides whether 10 and 15 seconds were the right numbers.
     */
    fun mediaReviewShown(
        kind: MediaKind,
        prompt: MediaPrompt,
        durationMs: Int,
        attempt: Int,
        stopReason: MediaStopReason,
    ) = MEDIA_REVIEW_SHOWN to buildMap<String, Any> {
        put("type", kind.trackingValue)
        put("media_prompt_id", prompt.id)
        put("duration_s", durationSeconds(durationMs))
        put("attempt", attempt)
        put("stop_reason", stopReason.trackingValue)
        putAll(Stamp.of(0))
    }

    /** T1, class 0. One play on review, with a running count. Multiple plays are expected. */
    fun mediaPreviewPlayed(kind: MediaKind, attempt: Int, playCount: Int) =
        MEDIA_PREVIEW_PLAYED to buildMap<String, Any> {
            put("type", kind.trackingValue)
            put("attempt", attempt)
            put("play_count", playCount)
            putAll(Stamp.of(0))
        }

    /**
     * T1, class 0. The take was KEPT.
     *
     * ON `Use this clip` / `Use this recording`, NEVER ON STOP. Stopping produces a take; only
     * accepting produces an artefact, and the gap between the two is the review screen's entire
     * reason to exist. Firing this on Stop would report a completion for every abandoned take and
     * make the per-prompt completion rate -- the thing the ranking is built on -- meaningless.
     */
    fun mediaPromptRecorded(
        kind: MediaKind,
        prompt: MediaPrompt,
        durationMs: Int,
        retakes: Int,
        playsBeforeAccept: Int,
    ) = when (kind) {
        MediaKind.Video -> VIDEO_PROMPT_RECORDED
        MediaKind.Voice -> VOICE_PROMPT_RECORDED
    } to buildMap<String, Any> {
        put("type", kind.trackingValue)
        put("media_prompt_id", prompt.id)
        put("is_own_prompt", prompt.isOwn)
        put("duration_s", durationSeconds(durationMs))
        put("retakes", retakes)
        put("plays_before_accept", playsBeforeAccept)
        putAll(Stamp.of(0))
    }

    /**
     * T1, class 0. A take was discarded to record again.
     *
     * `from` IS REQUIRED (section 8): `review` is a take not yet kept, `media_card` is an artefact
     * already on the profile. They are different products and one number for both means neither.
     */
    fun mediaRetaken(
        kind: MediaKind,
        from: MediaActedFrom,
        prompt: MediaPrompt,
        attempt: Int,
        priorDurationMs: Int,
        state: MediaState,
    ) = MEDIA_RETAKEN to buildMap<String, Any> {
        put("type", kind.trackingValue)
        put("from", from.trackingValue)
        put("media_prompt_id", prompt.id)
        put("attempt", attempt)
        put("prior_duration_s", durationSeconds(priorDurationMs))
        put("had_video", state.hasVideo)
        put("had_voice", state.hasVoice)
        putAll(Stamp.of(0))
    }

    /**
     * T1, class 0. An artefact was removed from the profile. State BEFORE the deletion.
     *
     * DELETING IS NOT RETAKING and the two must never be collapsed: a retake has a replacement
     * coming, a delete does not. That is the difference between a user polishing a first take and
     * one removing something already on their profile.
     *
     * No `from`: section 8 lists this event against that key, but the specification's payload table
     * does not carry it and delete is only reachable from a filled card -- a field with one
     * possible value measures nothing. Recorded in the conflicts log rather than resolved silently.
     */
    fun mediaDeleted(kind: MediaKind, artefact: MediaArtefact, state: MediaState) =
        MEDIA_DELETED to buildMap<String, Any> {
            put("type", kind.trackingValue)
            put("media_prompt_id", artefact.promptId)
            put("duration_s", durationSeconds(artefact.durationMs))
            put("had_video", state.hasVideo)
            put("had_voice", state.hasVoice)
            putAll(Stamp.of(0))
        }
}

/** Reports a catalogue-built event. Mirrors the welcome flow's helper exactly. */
fun AnalyticsTracker.report(pair: Pair<String, Map<String, Any>>) = track(pair.first, pair.second)
