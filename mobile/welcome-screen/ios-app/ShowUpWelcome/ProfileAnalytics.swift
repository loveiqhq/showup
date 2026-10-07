//  ProfileAnalytics.swift
//  ShowUp · the events "The basics" emits, and the two that are still BLOCKED
//
//  Names and payloads come from design_handoff_showup/tracking/events.json, family D, and the
//  vocabularies from enums.json §11 (screen registry) and §2 (step_id). Nothing here is invented
//  and nothing is a literal at a call site. Mirrors `profile/ProfileAnalytics.kt`.
//
//  ─────────────────────────────────────────────────────────────────────────────
//  BLOCKED — four things are NOT decided, and are marked rather than guessed
//  ─────────────────────────────────────────────────────────────────────────────
//
//  B1 · consent_changed payload.  The ticket says `on: bool`. The registry defines THREE
//       properties: channel ("call"|"calendar"|"push"|"whatsapp"|"sms"|"email"), on (bool) and
//       surface (str) — and the ticket says "do not restate a payload the registry already
//       defines", so the registry governs. But NO `surface` vocabulary exists in enums.json, and
//       the registry's own trigger describes a different screen: "One of the six Stay reachable
//       toggles changed ... surface separates the profile-creation screen from Settings".
//       => `consentChanged` is written but NOT called.
//
//  B2 · referrer_screen_id.  screen_viewed gains it in the alignment brief's Step 3, unbuilt.
//
//  B3 · sensitivity_class + field_registry_version, stamped at EMIT time from the registry and
//       failing closed to class 2. That emitter does not exist; `Stamp` is the seam, not it.
//
//  B4 · email_validation_failed rule="disposable". The enum allows it; nothing specifies a list,
//       a source or a behaviour. Only "format" is emitted.
//
//  Two notes for the design side, found while reading the registry:
//    - enums.json §2 step_id values read "namev1.2", "emailv1.2", "email_verifyv1.2", "dobv1.2" —
//      the version marker is concatenated into the value. Codegen would emit those verbatim.
//    - enums.json §2 gives step_index as "—" for all four, so BasicsStep.stepIndex takes the
//      ticket's numbers. The tracking note says never to use a literal; here there is nothing else.

import Foundation

/// The §11 screen registry, as constants.
///
/// Both values ship: `screenId` is the stable key a saved funnel binds to and is never edited;
/// `screenName` is the human label searched for in the analytics tool and may be. Two vocabularies,
/// and collapsing them breaks one of the two uses.
enum ProfileScreen: String {
    case name = "profile_name"
    case email = "profile_email"
    case emailVerification = "profile_email_verification"
    case dob = "profile_dob"

    /// The bridge out of "The basics" (SHOWUP-155).
    ///
    /// A SCREEN BUT NOT A STEP, and §11's naming rules say so in as many words: this row exists
    /// and the matching §2 row deliberately does not, "because firing profile_step_viewed on it
    /// would put a phantom step in the completion funnel". That is why there is no `BasicsStep`
    /// for it and why `ProfileAnalytics.stepViewed` cannot be called with it — the type system
    /// carries the rule rather than a comment asking people to remember it.
    case embraceBuild = "profile_embrace_build"

    /// "The real you", step 1 (SHOWUP-156). The §11 row was added by the design side before the
    /// ticket was written.
    case photos = "profile_photos"

    /// "The real you", step 2 (SHOWUP-158).
    ///
    /// **NOT IN §11 AT REGISTRY 1.3.0.** The ticket's tracking section says the row "must be added
    /// before the ticket is picked up", and it has not been — §11 carries `profile_photos` and
    /// stops. The two values here are the ones SHOWUP-158 quotes verbatim, so when the row lands
    /// they should match and nothing changes; if the design side chooses differently, this is the
    /// single place to correct. Recorded rather than silently invented.
    case prompts = "profile_prompts"

    /// "The real you", step 3 (SHOWUP-161). Registered in §11 on 16 September 2026.
    ///
    /// ONE screen_id FOR ALL SIX OF ITS STATES, and §11 says so in its own note: empty, video
    /// only, voice only, both — and the prompt-list sheet. A sheet is not a screen here; it is a
    /// state of this one, and giving it a second id would split the screen's funnel in half.
    case media = "profile_media"

    /// The full-bleed capture screen (states G and I).
    ///
    /// ONE ROW FOR VIDEO AND VOICE — "the medium is `type` on the events, not a second id". It
    /// fires its own `screen_viewed` with `referrer_screen_id`, because without it the two most
    /// abandonable views in the flow are invisible.
    case mediaRecord = "profile_media_record"

    /// The post-Stop review screen (states H and J): play, retake, keep. One row for both media.
    case mediaReview = "profile_media_review"

    /// The notification permission ask (SHOWUP-162).
    ///
    /// A SCREEN BUT NOT A STEP. §11's note for this row says it "sits OUTSIDE both progress bars
    /// and is not a step": §2 holds a `notifications` step_id whose index is a dash, and that
    /// exists so a future SKIP has a stable value — it is explicitly not a licence to fire
    /// `profile_step_viewed`. This screen has no skip CTA, so it fires no step event at all.
    ///
    /// Registry row added by the design side on 21 September 2026, before the ticket was written.
    case notifications = "profile_notifications"

    /// Stay reachable (SHOWUP-163).
    ///
    /// ALSO A SCREEN AND NOT A STEP, for the same reason as the row above -- §2 gives
    /// `reachability` a dash index. §11's note adds the part specific to this one: the
    /// deactivation confirm "has no entry point of its own and is not a separate screen", so the
    /// dialog does not get an id and state C is not a screenview.
    ///
    /// Registry row added 25 September 2026, before the Profile 10 ticket was written.
    case reachability = "profile_reachability"

    /// The location ask (SHOWUP-165). A screen and not a step, like 09 and 10: §2's `location` id
    /// exists only for `profile_step_skipped` on `Not now`. A, B and C are one mount.
    case location = "profile_location"

    /// The second bridge (SHOWUP-166). A screen and not a step, like `embraceBuild`.
    case embraceDetails = "profile_embrace_details"

    // "Share some details" (SHOWUP-167 to SHOWUP-173). Rows added in registry 1.4.8 for all eleven
    // steps at once; only the seven with a screen are spelled, the rest join with their screens.
    case height = "profile_height"
    case gender = "profile_gender"
    case orientation = "profile_orientation"
    case datingLanguage = "profile_dating_language"
    case education = "profile_education"
    case religion = "profile_religion"
    case politics = "profile_politics"

    var screenId: String { rawValue }

    var screenName: String {
        switch self {
        case .name: return "ProfileName"
        case .email: return "ProfileEmail"
        case .emailVerification: return "ProfileEmailVerification"
        case .dob: return "ProfileDoB"
        case .embraceBuild: return "ProfileEmbraceBuild"
        case .photos: return "ProfilePhotos"
        case .prompts: return "ProfilePrompts"
        case .media: return "ProfileMedia"
        case .mediaRecord: return "ProfileMediaRecord"
        case .mediaReview: return "ProfileMediaReview"
        case .notifications: return "ProfileNotifications"
        case .reachability: return "ProfileReachability"
        case .location: return "ProfileLocation"
        case .embraceDetails: return "ProfileEmbraceDetails"
        case .height: return "ProfileHeight"
        case .gender: return "ProfileGender"
        case .orientation: return "ProfileOrientation"
        case .datingLanguage: return "ProfileDatingLanguage"
        case .education: return "ProfileEducation"
        case .religion: return "ProfileReligion"
        case .politics: return "ProfilePolitics"
        }
    }
}

/// `variant` from enums.json §16 — which of the two bridges fired `embraceBridgeViewed`.
///
/// Two screens, one shell, two copy payloads. The set maps onto §11 one-for-one:
/// `build_profile` -> `profile_embrace_build` · `add_details` -> `profile_embrace_details`.
/// `addDetails` is the sibling bridge, registered and not yet ticketed; it is listed here because
/// the value set has one home by design, not because anything calls it yet.
enum EmbraceVariant {
    static let buildProfile = "build_profile"
    static let addDetails = "add_details"
}

/// `field_id` from §1.
enum ProfileField {
    static let firstName = "first_name"
    static let email = "email"

    /// The photo grid, as one field. A refused Continue is about the set, not about a slot.
    static let photos = "photos"

    /// The prompt list, as one field. Same reason.
    static let prompts = "prompts"
}

/// `entry_point` from the registry's 18, in full.
///
// §18 prompt `entry_point` IS RETIRED, and the two enums that expressed it are gone with it.
//
// `PromptEntryPoint` (suggestion | browse | edit) and `TopicEntryPoint` (the same minus edit, a
// separate type so `prompt_topic_selected` could not report an edit even by mistake) both lived
// here. Registry 1.4.5, 25 September 2026: "which topics are chosen matters, where they were
// chosen from does not." The section is kept and emptied so numbering and citations stay stable;
// the types are deleted, because an enum nothing sends is a trap for the next person.
//
// `is_edit` survives them. It was the one fact `edit` carried that nothing else did, and it now
// rides on `prompt_editor_opened`, `prompt_saved` and - new in 1.4.5 - `prompt_editor_dismissed`.
//
// NOT AFFECTED, and easy to conflate: §21 `MediaEntryPoint` (see_the_prompts | retake) on the
// media prompt list, and the app-open `entry_point` on `profile_build_started`. Different
// sections, different questions, both still sent.

/// `dismiss_method` from 23. THE CANONICAL SHEET-CLOSE VOCABULARY, and the only one.
///
/// Every bottom sheet in the product reports its dismissal with these four, created on 16
/// September 2026 by unifying three drifted spellings of the same four acts. This screen's write
/// sheet used to say `close | scrim | swipe | back`; the media prompt list said `cancel | ...`.
/// Neither had shipped, so nothing in the warehouse needed migrating - but a value outside this
/// set is now a bug at the call site rather than a local dialect.
///
/// `systemBack` IS NOT `close`. The Android gesture and the X are different acts by different
/// intentions and must not be folded together. iOS has no system back gesture of its own, so that
/// case is declared and never produced here - it exists so the two platforms share one vocabulary
/// rather than two that agree by inspection.
enum SheetDismissMethod: String, Sendable {
    /// The X, or a Cancel control.
    case close = "close"
    /// A tap on the scrim.
    case backdrop = "backdrop"
    /// The drag-down gesture.
    case swipe = "swipe"
    /// The Android back gesture or hardware key. Android only.
    case systemBack = "system_back"

    var trackingValue: String { rawValue }
}

/// The `rule` vocabulary shared by the validation events.
enum ValidationRule {
    static let requiredMissing = "required_missing"
    static let format = "format"

    /// Continue pressed with fewer than four CONFIRMED photos (SHOWUP-156).
    static let photosBelowMinimum = "photos_below_minimum"

    /// Continue pressed with no prompt saved (SHOWUP-158).
    ///
    /// `prompts_below_minimum`, added by registry 1.4.2 as "the sibling of photos_below_minimum".
    /// This screen previously reported `nothing_selected`, which is a different act - it belongs
    /// to a chooser where nothing was ticked, not to a screen where nothing was written.
    static let promptsBelowMinimum = "prompts_below_minimum"

    /// A value that cannot be true - a height outside 120-230 (SHOWUP-167). "§8 has no range
    /// value. If analytics wants `out_of_range`, it gets defined in `enums.json` first."
    static let impossible = "impossible"
}

/// `channel` from enums.json §8.
///
/// **`marketing_email` is not `email`.** One address, three uses, told apart by this vocabulary:
/// this box governs marketing mail only; the `email` toggle on the later Stay reachable screen is a
/// preference about being contacted regarding a match; transactional mail has no consent to
/// withdraw and is never tracked as one.
enum ConsentChannel {
    static let marketingEmail = "marketing_email"

    /// The ONE live consent channel on Stay reachable, and the only §8 value that screen may use.
    ///
    /// events.json, 1.4.5: "MVP (25 Sep 2026): Stay reachable fires this for channel:"push" ONLY."
    /// `call`, `whatsapp`, `sms`, `calendar` and `email` are reserved for the later scope and are
    /// deliberately absent here - an interest in one of them is `InterestChannel`, a separate
    /// vocabulary, so nothing in this enum can be handed to a checkbox by mistake.
    static let push = "push"
}

/// `surface` from enums.json §8 — which screen a consent was changed on.
///
/// A closed pair as of registry 1.3.0. `profileCreation` is the one-off ask inside the sign-up
/// flow; `settings` is the same control revisited later.
enum ConsentSurface {
    static let profileCreation = "profile_creation"
    static let settings = "settings"
}

/// The emit-time stamp every attribute event must carry.
///
/// **BLOCKED (B3).** The brief requires `sensitivity_class` resolved from the registry at emit
/// time, failing closed to 2 for an unrecognised field_id, plus `field_registry_version`. Neither
/// the registry file nor the codegen is in this repo, so this is the seam and not the
/// implementation: a version constant and a hard-coded class per event, which is exactly what the
/// brief says not to do long-term.
enum Stamp {
    /// enums.json -> registry_version at the time of writing.
    ///
    /// 1.4.15 (5 October 2026) closes the nine "Share some details" tickets, one version each:
    /// 1.4.7 location (§26 `location_unavailable_reason`), 1.4.8 the `add_details` bridge, then
    /// one per detail step from height (1.4.9) to politics (1.4.15) - `muslim` added to religion in
    /// 1.4.14, the politics order made linear in 1.4.15. Read `changed_in_1_4_*` in enums.json for
    /// each step's exact delta.
    ///
    /// 1.4.6 (25 September 2026) added §25 `interest_channel` - the Stay reachable demand test -
    /// and the `profile_reachability` row in §11. It sits on 1.4.5, the same day, which RETIRED
    /// §18 prompt `entry_point`; on 1.4.4 (§24 permission_status, for the notifications ask); and
    /// on 1.4.2 (§23 dismiss_method unified across every bottom sheet).
    ///
    /// THIS IS enums.json's `registry_version`, NOT events.json's `taxonomy_version`. They are
    /// different numbers on the same day - events.json says 1.4.5 and enums.json 1.4.6 - and the
    /// Profile 10 ticket quotes both together: "taxonomy 1.4.5 / registry 1.4.6". enums.json says
    /// which one belongs here: "the string stamped into field_registry_version on every attribute
    /// event - it versions the vocabulary, not the taxonomy document".
    ///
    /// READ IT FROM HERE AND NOWHERE ELSE - a payload stamped with a version the values did not
    /// come from is worse than an unstamped one, because it looks checked.
    static let fieldRegistryVersion = "1.4.15"

    static func of(_ sensitivityClass: Int) -> [String: any Sendable] {
        ["sensitivity_class": sensitivityClass, "field_registry_version": fieldRegistryVersion]
    }
}

/// Family D events for "The basics".
///
/// Each function returns a name and a payload; nothing here reaches a sink. The app still has no
/// analytics sink and no consent gate — the brief is explicit that those are separate work, and
/// that "the apps have no sink at all, which is not the same thing as a gate".
enum ProfileAnalytics {
    static let screenViewedName = "screen_viewed"
    static let profileStepViewed = "profile_step_viewed"
    static let profileBuildStarted = "profile_build_started"
    static let profileStepCompleted = "profile_step_completed"
    static let profileStepSkipped = "profile_step_skipped"
    static let nameSubmitted = "name_submitted"
    static let emailSubmitted = "email_submitted"
    static let emailValidationFailed = "email_validation_failed"
    static let formValidationFailed = "form_validation_failed"
    static let consentChangedName = "consent_changed"
    static let embraceBridgeViewedName = "embrace_bridge_viewed"
    static let photoSlotTappedName = "photo_slot_tapped"
    static let photoAddedName = "photo_added"
    static let photoRemovedName = "photo_removed"
    static let photoReorderedName = "photo_reordered"
    static let photosMinimumMetName = "photos_minimum_met"
    static let promptTopicListOpenedName = "prompt_topic_list_opened"
    static let promptTopicListDismissedName = "prompt_topic_list_dismissed"
    static let promptTopicSelectedName = "prompt_topic_selected"
    static let promptEditorOpenedName = "prompt_editor_opened"
    static let promptEditorDismissedName = "prompt_editor_dismissed"
    static let promptSavedName = "prompt_saved"
    static let promptExampleDismissedName = "prompt_example_dismissed"
    static let promptCharLimitReachedName = "prompt_char_limit_reached"
    static let promptsMinimumMetName = "prompts_minimum_met"

    // Family E, media half (SHOWUP-161). Every one of these ships with this ticket — they are all
    // `Not built` in events.json. `media_prompt_ranking_published` is deliberately absent: it is
    // server-side and ships with the ranking job, not with the screen.
    static let mediaScreenViewedName = "media_screen_viewed"
    static let mediaPromptListOpenedName = "media_prompt_list_opened"
    static let mediaPromptSelectedName = "media_prompt_selected"
    static let mediaPromptListDismissedName = "media_prompt_list_dismissed"
    static let videoRecordingStartedName = "video_recording_started"
    static let voiceRecordingStartedName = "voice_recording_started"
    static let mediaReviewShownName = "media_review_shown"
    static let mediaPreviewPlayedName = "media_preview_played"
    static let videoPromptRecordedName = "video_prompt_recorded"
    static let voicePromptRecordedName = "voice_prompt_recorded"
    static let mediaRetakenName = "media_retaken"

    // ── family G, permissions and consent (SHOWUP-162) ──────────────────────
    //
    // ALL FOUR ARE NEW. Every one is `implemented: false` in events.json and the first three are
    // `backend_status: "Client only"` — the notification ask is the first surface in the product
    // that needs them.
    static let permissionPromptedName = "permission_prompted"
    static let permissionOsSheetShownName = "permission_os_sheet_shown"
    static let permissionResultName = "permission_result"
    static let permissionStatusChangedName = "permission_status_changed"

    // MARK: family G · Stay reachable (SHOWUP-163)

    /// The Settings deep link was taken - from the denied-guard toggle, or from state D.
    static let permissionSettingsOpenedName = "permission_settings_opened"

    /// The push deactivation confirm opened, either because the user reached for the switch or
    /// because the OS said "Don't allow".
    static let consentDeactivationConfirmShownName = "consent_deactivation_confirm_shown"

    /// `Keep active`, or a tap on the scrim. Identical acts, one event.
    static let consentDeactivationAbandonedName = "consent_deactivation_abandoned"

    static let consentDeactivationConfirmedName = "consent_deactivation_confirmed"

    /// An interest box flipped. NEW 25 September 2026, and NOT a consent.
    static let channelInterestChangedName = "channel_interest_changed"

    /// The state the user actually leaves Stay reachable in, after the server confirms it.
    static let reachabilitySavedName = "reachability_saved"

    // MARK: family G · the location ask, registry 1.4.7 (SHOWUP-165)

    /// NEW for this screen. The other permission events are shared with notifications and take
    /// `type: "location"` here.
    static let permissionDeniedRecoveryShownName = "permission_denied_recovery_shown"

    /// `permission_*.type` for the location ask.
    static let permissionLocation = "location"

    // MARK: family F · profile attributes (SHOWUP-167 to SHOWUP-173)

    /// `detail_answered` and `detail_skipped` carry the FIELD's own §1 class, stamped at emit time.
    static let detailAnsweredName = "detail_answered"
    static let detailSkippedName = "detail_skipped"
    static let fieldDisplayOptedOutName = "field_display_opted_out"

    /// The closed set on `permission_prompted.type`, from `enums.json` §8.
    ///
    /// `permission_os_sheet_shown.type` and `permission_result.type` are typed `str` in the
    /// registry and take the SAME value — the registry is loose there and the ticket is not.
    static let permissionNotifications = "notifications"

    /// Where a status change was noticed. The closed pair on `permission_status_changed`.
    static let detectedOnLaunch = "launch"
    static let detectedOnForeground = "foreground"
    static let mediaDeletedName = "media_deleted"

    /// T2, class 1. The location ask's B or C was shown. `reason` is §26
    /// `location_unavailable_reason` - "REQUIRED when type is location, absent otherwise".
    static func permissionDeniedRecoveryShown(
        reason: LocationUnavailableReason
    ) -> (String, [String: any Sendable]) {
        (permissionDeniedRecoveryShownName, [
            "type": permissionLocation, "reason": reason.rawValue,
        ].merging(Stamp.of(1)) { a, _ in a })
    }

    /// T2, class 0. `Not now — ask me when I search`: the ONE event §2's location id exists for.
    static func locationSkipped() -> (String, [String: any Sendable]) {
        (profileStepSkipped, [
            "step_id": "location", "screen_id": ProfileScreen.location.screenId,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    // MARK: "Share some details"

    /// T2, class 0.
    static func detailStepViewed(_ step: DetailStep) -> (String, [String: any Sendable]) {
        (profileStepViewed, [
            "step_id": step.stepId, "step_index": step.stepIndex,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. Continue accepted and the answer stored.
    static func detailStepCompleted(_ step: DetailStep,
                                    timeOnStepSeconds: Int) -> (String, [String: any Sendable]) {
        (profileStepCompleted, [
            "step_id": step.stepId, "time_on_step_s": timeOnStepSeconds,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. Skip, or a Continue that is a skip. Never from gender or orientation.
    static func detailStepSkipped(_ step: DetailStep) -> (String, [String: any Sendable]) {
        (profileStepSkipped, [
            "step_id": step.stepId, "screen_id": step.screen.screenId,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T1, the field's class. One per ACCEPTED Continue, carrying the final answer - the §1 value,
    /// height's bucket, or the ticked languages in list order. Never a label, never empty.
    static func detailAnswered(_ step: DetailStep,
                               valueBucketed: String) -> (String, [String: any Sendable]) {
        precondition(!valueBucketed.isEmpty, "an empty answer is a skip, not an answer")
        return (detailAnsweredName, [
            "field_id": step.fieldId, "value_bucketed": valueBucketed,
        ].merging(Stamp.of(step.sensitivityClass)) { a, _ in a })
    }

    /// T1, the field's class.
    static func detailSkipped(_ step: DetailStep) -> (String, [String: any Sendable]) {
        (detailSkippedName, ["field_id": step.fieldId]
            .merging(Stamp.of(step.sensitivityClass)) { a, _ in a })
    }

    /// T1, class 0. The visibility box was TICKED - nothing is sent on untick.
    static func fieldDisplayOptedOut(_ step: DetailStep) -> (String, [String: any Sendable]) {
        (fieldDisplayOptedOutName, ["field_id": step.fieldId].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T1, class 0. Continue refused on height, gender or orientation - on the press, not render.
    static func detailValidationFailed(_ step: DetailStep,
                                       rule: String) -> (String, [String: any Sendable]) {
        (formValidationFailed, [
            "field_id": step.fieldId, "rule": rule,
            "screen_id": step.screen.screenId, "step_id": step.stepId,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T1, class 0. `referrer_screen_id` is B2 and travels empty until Step 3 lands.
    static func screenViewed(_ screen: ProfileScreen,
                             referrer: ProfileScreen? = nil) -> (String, [String: any Sendable]) {
        (screenViewedName, [
            "screen_id": screen.screenId,
            "screen_name": screen.screenName,
            "referrer_screen_id": referrer?.screenId ?? "",
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. Not a duplicate of `screenViewed`: that answers WHICH SCREEN, this answers
    /// WHERE IN THE FLOW. `emailVerify` is the case that proves they differ — its own screen,
    /// holding at step 2 of 3.
    static func stepViewed(_ step: BasicsStep) -> (String, [String: any Sendable]) {
        (profileStepViewed, [
            "step_id": step.stepId, "step_index": step.stepIndex,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. The bridge screen was shown.
    ///
    /// Fires ALONGSIDE `screenViewed` and instead of nothing else. SHOWUP-155 is explicit about
    /// the three that must NOT fire here: `stepViewed` and `stepCompleted`, because a bridge is
    /// not a step and would otherwise show up as a phantom stage in the completion funnel; and
    /// `buildStarted`, because the build started four screens ago on the name step. There is also
    /// no CTA event — the screen has one exit and `screenViewed` on photos already records it.
    static func embraceBridgeViewed(
        variant: String = EmbraceVariant.buildProfile
    ) -> (String, [String: any Sendable]) {
        (embraceBridgeViewedName, ["variant": variant].merging(Stamp.of(0)) { a, _ in a })
    }

    // MARK: family E · Profile Photos/Media (SHOWUP-156)
    //
    // NEVER A PHOTO, A FILENAME OR A LIBRARY IDENTIFIER. Slot indices, a source and counts only.
    // What makes that true is that none of the builders below takes anything else.

    /// T2, class 0. A slot tap, or `Add more` revealing slots 5-6.
    static func photoSlotTapped(slotIndex: Int, isOptional: Bool,
                                action: String) -> (String, [String: any Sendable]) {
        (photoSlotTappedName, ["slot_index": slotIndex, "is_optional": isOptional,
                               "action": action].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. Fires on the CONFIRMED upload, never on the pick.
    ///
    /// `filled_count` is the confirmed count after this one landed, which is what makes the funnel
    /// agree with the number on screen.
    static func photoAdded(slotIndex: Int, source: PhotoSource,
                           filledCount: Int) -> (String, [String: any Sendable]) {
        (photoAddedName, ["slot_index": slotIndex, "source": source.trackingValue,
                          "filled_count": filledCount,
                          "max_slots": photosMax].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0.
    static func photoRemoved(slotIndex: Int,
                             filledCount: Int) -> (String, [String: any Sendable]) {
        (photoRemovedName, ["slot_index": slotIndex,
                            "filled_count": filledCount].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. Drag finished somewhere other than where it started.
    static func photoReordered(from: Int, to: Int) -> (String, [String: any Sendable]) {
        (photoReorderedName, ["from": from, "to": to].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. ONCE, on the first crossing of four CONFIRMED uploads.
    ///
    /// Not on the fourth pick, and not again after a removal takes the count below four and back
    /// up — `PhotoGridState.minimumReported` holds that, because a funnel step that can fire twice
    /// for one user cannot be counted.
    static func photosMinimumMet(count: Int) -> (String, [String: any Sendable]) {
        (photosMinimumMetName, ["count": count,
                                "max_slots": photosMax].merging(Stamp.of(0)) { a, _ in a })
    }
    // FAMILY E, AND NOT FAMILY F - A COLLISION THE REGISTRY RESOLVED ON 16 SEPTEMBER 2026
    //
    // This screen used to emit `prompt_topic_picker_opened`, `prompt_topic_selected` (topic_id
    // only), `prompt_answered` and `prompt_edited`. Those are the v1.0 family F names, and at
    // registry 1.3.0 they were the only prompt rows that existed, so they were used as named.
    //
    // `events.json` at 1.4.2 carries all four marked SUPERSEDED - DO NOT FIRE, emptied of their
    // payloads so the name resolves to the notice rather than being re-implemented from a stale
    // spec. The duplicate `prompt_topic_selected` row was deleted outright. Nothing on this screen
    // may fire a family F prompt event.
    //
    // NEVER THE ANSWER AND NEVER THE DRAFT. Every builder below takes a LENGTH rather than a
    // string, so there is no signature here that can carry the text even by accident.

    /// T2, class 0. `Browse all 15 topics` was pressed.
    static func promptTopicListOpened(usedCount: Int,
                                      screen: ProfileScreen = .prompts)
    -> (String, [String: any Sendable]) {
        (promptTopicListOpenedName,
         ["used_count": usedCount, "screen_id": screen.screenId].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. The topic sheet closed WITHOUT a topic being picked.
    ///
    /// MUTUALLY EXCLUSIVE WITH `promptTopicSelected`: picking a topic replaces the sheet with the
    /// write sheet and fires that instead. Together they close the browse sheet's funnel, so
    /// `prompt_topic_list_opened` = selected + dismissed, and a gap in that sum is a bug rather
    /// than a behaviour.
    static func promptTopicListDismissed(method: SheetDismissMethod, usedCount: Int,
                                         timeOnSheetSeconds: Int,
                                         screen: ProfileScreen = .prompts)
    -> (String, [String: any Sendable]) {
        (promptTopicListDismissedName,
         ["dismiss_method": method.trackingValue, "used_count": usedCount,
          "time_on_sheet_s": timeOnSheetSeconds,
          "screen_id": screen.screenId].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. A topic was chosen from a suggestion card or from a row of the browse sheet.
    ///
    /// NEVER ON AN EDIT. The registry change of 16 September 2026 - reopening a saved prompt is
    /// not a fresh choice of topic, and firing this there inflated topic demand with re-edits of
    /// prompts already written. An edit fires `promptEditorOpened` alone.
    ///
    /// WHERE THE TAP CAME FROM IS DELIBERATELY NOT RECORDED, from registry 1.4.5 (25 September
    /// 2026): "which topics are chosen matters, where they were chosen from does not". §18 is
    /// retired and `position` went with it, so a suggestion card and a row of the browse sheet
    /// produce the same event and the type that used to tell them apart is gone.
    ///
    /// `topic_group` is looked up rather than passed, so a caller cannot file a topic under a
    /// group it is not in.
    static func promptTopicSelected(topicId: String,
                                    selectionIndex: Int) -> (String, [String: any Sendable]) {
        (promptTopicSelectedName,
         ["topic_id": topicId, "topic_group": topicGroupFor(topicId),
          "selection_index": selectionIndex].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. The write sheet mounted - on a card, on a browse row, or on the pencil.
    ///
    /// `isEdit` is the whole of what §18 used to carry here: the pencil is a different act from a
    /// first write, and it is the one distinction 1.4.5 kept when it retired the rest.
    static func promptEditorOpened(topicId: String, isEdit: Bool,
                                   promptCount: Int) -> (String, [String: any Sendable]) {
        (promptEditorOpenedName,
         ["topic_id": topicId, "is_edit": isEdit,
          "prompt_count": promptCount].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. The write sheet closed without saving.
    ///
    /// THE ABANDONMENT EVENT THIS SCREEN IS DESIGNED AGAINST. `had_draft` separates "changed their
    /// mind" from "could not finish"; `draftLength` is bucketed on the way in and the draft itself
    /// never leaves the device.
    ///
    /// `isEdit` ARRIVED WITH 1.4.5 and is the reason retiring §18 lost nothing here. The old
    /// `entry_point: "edit"` was carrying one fact this event actually needed - abandoning a
    /// rewrite of something already written is not the same as abandoning a blank one - so the
    /// fact stayed and the enum went.
    static func promptEditorDismissed(topicId: String, isEdit: Bool,
                                      draftLength: Int, method: SheetDismissMethod)
    -> (String, [String: any Sendable]) {
        (promptEditorDismissedName,
         ["topic_id": topicId, "is_edit": isEdit,
          "had_draft": draftLength > 0,
          "draft_length_bucket": promptLengthBucket(draftLength),
          "dismiss_method": method.trackingValue].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. Save pressed with a non-empty answer.
    ///
    /// `answerLength` rather than the answer: there is no signature here that can carry the text.
    /// `promptCount` is the number the user holds AFTER this save, and an edit does not raise it.
    static func promptSaved(topicId: String, isEdit: Bool,
                            answerLength: Int, promptCount: Int)
    -> (String, [String: any Sendable]) {
        (promptSavedName,
         ["topic_id": topicId, "topic_group": topicGroupFor(topicId),
          "is_edit": isEdit,
          "length_bucket": promptLengthBucket(answerLength),
          "prompt_count": promptCount].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. The worked example hidden with its X. High volume means it is in the way.
    static func promptExampleDismissed(topicId: String) -> (String, [String: any Sendable]) {
        (promptExampleDismissedName, ["topic_id": topicId].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. 160 reached. ONCE PER EDITOR SESSION, not per keystroke.
    static func promptCharLimitReached(topicId: String) -> (String, [String: any Sendable]) {
        (promptCharLimitReachedName, ["topic_id": topicId].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. The first prompt was saved, so the step can be completed.
    ///
    /// ONCE, on the first crossing, carrying the topic that got the user there.
    ///
    /// It used to carry the entry point with it, and the registry called that pairing "the single
    /// most useful row on the screen". 1.4.5 decided otherwise and retired §18; the topic is what
    /// is left, and it is the half the ranking actually reads.
    static func promptsMinimumMet(count: Int, topicId: String)
    -> (String, [String: any Sendable]) {
        (promptsMinimumMetName,
         ["count": count, "topic_id": topicId].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. A step of "The real you" was reached.
    ///
    /// `step_index` comes from `RealYouStep` - photos 1, prompts 2, media 3 - and 2 of registry
    /// 1.4.2 now says exactly that, re-verified on 16 September 2026. It did not at 1.3.0, where
    /// prompts read 10: the code was right and the registry has caught up.
    static func realYouStepViewed(_ step: RealYouStep) -> (String, [String: any Sendable]) {
        (profileStepViewed,
         ["step_id": step.stepId, "step_index": step.stepIndex].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. Continue was ACCEPTED on a step of "The real you".
    ///
    /// `promptCount` is OPTIONAL AND SCREEN-SCOPED - 1 to 3 on the prompts step and omitted
    /// everywhere else, which is what the registry's `applies_to` means. An empty property is
    /// worse than an absent one, so nil omits the key rather than writing a zero.
    static func realYouStepCompleted(_ step: RealYouStep, timeOnStepSeconds: Int,
                                     promptCount: Int? = nil)
    -> (String, [String: any Sendable]) {
        var payload: [String: any Sendable] = [
            "step_id": step.stepId, "time_on_step_s": timeOnStepSeconds,
        ]
        if let promptCount { payload["prompt_count"] = promptCount }
        return (profileStepCompleted, payload.merging(Stamp.of(0)) { a, _ in a })
    }

    /// T1, class 0. The refused press on a step outside "The basics".
    ///
    /// A second builder rather than a widened first one: `formValidationFailed` takes a
    /// `BasicsStep` and there is none for a photo or a prompt. The payload is identical and both
    /// come from the same registry row - what differs is which step vocabulary the caller can
    /// offer.
    static func realYouValidationFailed(fieldId: String, rule: String, screen: ProfileScreen,
                                        step: RealYouStep) -> (String, [String: any Sendable]) {
        (formValidationFailed, ["field_id": fieldId, "rule": rule, "screen_id": screen.screenId,
                                "step_id": step.stepId].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. Once per profile build, on the first step only.
    static func buildStarted(entryPoint: String) -> (String, [String: any Sendable]) {
        (profileBuildStarted, ["entry_point": entryPoint].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. The name is free text and is NEVER sent — char_count only.
    static func nameSubmitted(charCount: Int) -> (String, [String: any Sendable]) {
        (nameSubmitted, ["char_count": charCount].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. The address is never sent, only its domain.
    static func emailSubmitted(domain: String) -> (String, [String: any Sendable]) {
        (emailSubmitted, ["domain": domain].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. Only `format` is emitted — "disposable" is B4.
    static func emailValidationFailed(
        rule: String = ValidationRule.format
    ) -> (String, [String: any Sendable]) {
        (emailValidationFailed, ["rule": rule].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0.
    static func stepCompleted(_ step: BasicsStep,
                              timeOnStepSeconds: Int) -> (String, [String: any Sendable]) {
        (profileStepCompleted, [
            "step_id": step.stepId, "time_on_step_s": timeOnStepSeconds,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T1, class 0. Fires on the REFUSED PRESS, not on render — typing three characters and
    /// stopping emits nothing.
    static func formValidationFailed(fieldId: String, screen: ProfileScreen,
                                     step: BasicsStep) -> (String, [String: any Sendable]) {
        (formValidationFailed, [
            "field_id": fieldId,
            "rule": ValidationRule.requiredMissing,
            "screen_id": screen.screenId,
            "step_id": step.stepId,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 1. Fires in **both** directions.
    ///
    /// Not opt-out only, unlike `fieldDisplayOptedOut`: a consent record has to show the withdrawal
    /// as well as the grant, or it cannot answer "was this person opted in on date X". The ticket
    /// states it outright — "fires in both directions, never opt-out only".
    ///
    /// Unblocked by registry 1.3.0, which gave `surface` a closed vocabulary.
    ///
    /// TWO SCREENS FIRE THIS AND THEY DIFFER ONLY IN `channel`, which is why it is a parameter
    /// with the Email screen's value as the default rather than two builders: the profile Email
    /// screen's marketing checkbox is `marketing_email`, and Stay reachable's push toggle is
    /// `push`. An interest checkbox is NEITHER - it fires `channelInterestChanged` and must never
    /// reach this function, which is enforced by `InterestChannel` having no member here.
    static func consentChanged(
        on: Bool,
        channel: String = ConsentChannel.marketingEmail,
        surface: String = ConsentSurface.profileCreation
    ) -> (String, [String: any Sendable]) {
        (consentChangedName, [
            "channel": channel, "on": on, "surface": surface,
        ].merging(Stamp.of(1)) { a, _ in a })
    }

    // MARK: family G builders · Stay reachable (SHOWUP-163)

    /// T2, class 1. The Settings deep link was taken.
    ///
    /// NOT an abandonment, and the ticket names the distinction: state D's primary opens Settings
    /// rather than keeping anything on, so it is this event and not
    /// `consentDeactivationAbandoned`. They answer different questions.
    static func permissionSettingsOpened(
        type: String = permissionNotifications
    ) -> (String, [String: any Sendable]) {
        (permissionSettingsOpenedName, ["type": type].merging(Stamp.of(1)) { a, _ in a })
    }

    /// T2, class 1. The deactivation confirm was raised.
    static func consentDeactivationConfirmShown(
        channel: String = ConsentChannel.push
    ) -> (String, [String: any Sendable]) {
        (consentDeactivationConfirmShownName,
         ["channel": channel].merging(Stamp.of(1)) { a, _ in a })
    }

    /// T2, class 1. `Keep active`, or the scrim.
    static func consentDeactivationAbandoned(
        channel: String = ConsentChannel.push
    ) -> (String, [String: any Sendable]) {
        (consentDeactivationAbandonedName,
         ["channel": channel].merging(Stamp.of(1)) { a, _ in a })
    }

    /// T2, class 1. `Confirm deactivation`.
    static func consentDeactivationConfirmed(
        channel: String = ConsentChannel.push
    ) -> (String, [String: any Sendable]) {
        (consentDeactivationConfirmedName,
         ["channel": channel].merging(Stamp.of(1)) { a, _ in a })
    }

    /// T2, class 1. An interest box was checked or unchecked. EVERY FLIP, in order.
    ///
    /// THE DEMAND TEST, and it is not a consent: nothing is stored on the profile, nothing is
    /// sent to the consent service, and no sending code ever reads it. The registry keeps §25 a
    /// separate vocabulary from §8 "so an interest can never be read as a consent", and the
    /// signature takes `InterestChannel` so a §8 string cannot be passed here either.
    static func channelInterestChanged(
        _ channel: InterestChannel,
        on: Bool
    ) -> (String, [String: any Sendable]) {
        (channelInterestChangedName, [
            "channel": channel.trackingValue,
            "on": on,
            "screen_id": ProfileScreen.reachability.screenId,
        ].merging(Stamp.of(1)) { a, _ in a })
    }

    /// T2, class 1. Save confirmed BY THE SERVER - never on the tap.
    ///
    /// The denominator-side row of the demand test: the share of `screen_viewed` on this screen
    /// whose `reachability_saved.interest` contains a channel. `channels_on`, `count` and
    /// `has_phone` were REMOVED from this payload on 25 September 2026 for the MVP scope; if any
    /// of them reappears here, the registry is being contradicted rather than extended.
    static func reachabilitySaved(
        pushOn: Bool,
        permission: PermissionStatus,
        interest: [String]
    ) -> (String, [String: any Sendable]) {
        (reachabilitySavedName, [
            "push_on": pushOn,
            "permission": permission.trackingValue,
            "interest": interest,
        ].merging(Stamp.of(1)) { a, _ in a })
    }

    /// The Privacy Policy link, which is the only link on Stay reachable.
    ///
    /// DELEGATES rather than redefining: `legal_link_tapped` is one event across every screen
    /// that draws one of the three documents, and a second definition here would be a second
    /// place for its payload to drift. This one only supplies the §11 key.
    static func legalLinkTapped(
        _ link: String,
        screen: ProfileScreen
    ) -> (String, [String: any Sendable]) {
        SignUpAnalytics.legalLinkTapped(link, screenId: screen.screenId)
    }

    /// T2, class 0. **BLOCKED** — the skip path itself is an open question in ticket 02.
    ///
    /// `screen_id` is NEWLY REQUIRED AT v1.4 and was missing from the build until SHOWUP-161.
    /// Without it every skippable step in the product reports the same shape and the funnel cannot
    /// say WHERE a user opted out, which is the only question the event is asked.
    static func stepSkipped(_ step: BasicsStep,
                            screen: ProfileScreen) -> (String, [String: any Sendable]) {
        (profileStepSkipped, [
            "step_id": step.stepId, "screen_id": screen.screenId,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    // MARK: family E · media (SHOWUP-161)
    //
    // `type` IS REQUIRED ON EVERY EVENT BELOW except the two screen views, and it is not optional
    // in the way a nilable field is optional: one screen carries two media, so without it "a tap
    // on the voice card and a tap on the video card are the same row, and nothing about this
    // screen can be answered". `MediaKind` carries it, so the type system supplies it rather than
    // a call site remembering to.
    //
    // NOTHING HERE EVER CARRIES THE RECORDING. No frame, no transcript, no waveform, no file path.
    // `duration_s` and the prompt id are the whole payload: media of a user's face and voice is
    // the most sensitive artefact in profile creation and none of it belongs in analytics.

    /// T1, class 0. The screen's entry state — fires on EVERY mount, including the return from an
    /// accepted take.
    ///
    /// NAMES THE PROMPTS IT SHOWED, which is why it is separate from `screenViewed`. The ranking
    /// moves, so a view that does not record which prompts were on the cards cannot be attributed
    /// afterwards, and a drop in recording rate cannot be told apart from a bad prompt.
    ///
    /// No `type`: it describes the screen, not a medium.
    static func mediaScreenViewed(_ state: MediaState) -> (String, [String: any Sendable]) {
        (mediaScreenViewedName, [
            "screen_id": ProfileScreen.media.screenId,
            "has_video": state.hasVideo,
            "has_voice": state.hasVoice,
            "preview_video_prompt_id": state.preview(.video).id,
            "preview_voice_prompt_id": state.preview(.voice).id,
            "preview_source": state.previewSource.trackingValue,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. One screen carries two steps, so this fires once per medium.
    ///
    /// §2 gives `media_video` and `media_voice` the same `step_index` 3 for exactly this reason.
    /// Collapsing them into one event would give the group a step-3 funnel that cannot be read per
    /// medium, which is the thing every other decision on this screen is built to avoid.
    static func mediaStepViewed(_ kind: MediaKind) -> (String, [String: any Sendable]) {
        (profileStepViewed, [
            "step_id": kind.stepId, "step_index": MediaKind.stepIndex,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. Continue pressed with this medium recorded.
    static func mediaStepCompleted(_ kind: MediaKind,
                                   timeOnStepSeconds: Int) -> (String, [String: any Sendable]) {
        (profileStepCompleted, [
            "step_id": kind.stepId, "time_on_step_s": timeOnStepSeconds,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T1, class 0. This medium was left empty.
    ///
    /// Fires from `Skip for now` AND from a Continue with nothing in the slot: "an empty Continue
    /// is a skip that the user did not call one". Carries the pair so a skip can be read against
    /// what the user did record.
    static func mediaStepSkipped(_ kind: MediaKind,
                                 state: MediaState) -> (String, [String: any Sendable]) {
        (profileStepSkipped, [
            "step_id": kind.stepId,
            "screen_id": ProfileScreen.media.screenId,
            "has_video": state.hasVideo,
            "has_voice": state.hasVoice,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T1, class 0. `See the prompts`, or `Retake` over something already recorded.
    ///
    /// `entry_point` (§21) and `has_existing` answer different questions and both ship: one is
    /// intent, one is state. The registry note is explicit that `entry_point` is "never inferred
    /// from whether an artefact exists".
    static func mediaPromptListOpened(
        _ kind: MediaKind,
        entryPoint: MediaEntryPoint,
        hasExisting: Bool
    ) -> (String, [String: any Sendable]) {
        (mediaPromptListOpenedName, [
            "type": kind.trackingValue,
            "entry_point": entryPoint.trackingValue,
            "has_existing": hasExisting,
            "screen_id": ProfileScreen.media.screenId,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T1, class 0. Fires on the COMMIT CTA, not on every row tap.
    ///
    /// `was_previewed` IS THE FIELD THAT KEEPS THE RANKING HONEST. The previewed prompt is far
    /// more visible than the other ten, so counting its own selections would make it win because
    /// it was shown. The job counts only `was_previewed: false` takes.
    static func mediaPromptSelected(
        _ kind: MediaKind,
        prompt: MediaPrompt,
        selectionsBefore: Int,
        wasPreviewed: Bool
    ) -> (String, [String: any Sendable]) {
        (mediaPromptSelectedName, [
            "type": kind.trackingValue,
            // The §20 id, never the display string: an edit to the copy must not orphan clips.
            "media_prompt_id": prompt.id,
            "position": prompt.position,
            "is_own_prompt": prompt.isOwn,
            "selections_before": selectionsBefore,
            "was_previewed": wasPreviewed,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T1, class 0. The list was closed without committing.
    ///
    /// `dismiss_method`, NOT `method` (§23) — `method` already carries `phone · apple · google`.
    static func mediaPromptListDismissed(
        _ kind: MediaKind,
        method: SheetDismissMethod,
        hadSelection: Bool,
        timeOnSheetSeconds: Int
    ) -> (String, [String: any Sendable]) {
        (mediaPromptListDismissedName, [
            "type": kind.trackingValue,
            "dismiss_method": method.rawValue,
            "had_selection": hadSelection,
            "time_on_sheet_s": timeOnSheetSeconds,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T1, class 0. The viewfinder started capturing.
    ///
    /// Two event NAMES rather than one with `type`, because that is what family E registers. The
    /// name carries the medium here and the payload carries it everywhere else; both are the
    /// registry's choice, not ours.
    static func mediaRecordingStarted(
        _ kind: MediaKind,
        prompt: MediaPrompt,
        attempt: Int,
        isRetake: Bool
    ) -> (String, [String: any Sendable]) {
        let name = kind == .video ? videoRecordingStartedName : voiceRecordingStartedName
        return (name, [
            "type": kind.trackingValue,
            "media_prompt_id": prompt.id,
            "is_own_prompt": prompt.isOwn,
            "attempt": attempt,
            "is_retake": isRetake,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T1, class 0. Stop, or the cap, produced a take and the review screen is up.
    ///
    /// THE DENOMINATOR FOR THE WHOLE REVIEW SCREEN. `stop_reason: max_length` dominating means the
    /// cap is too short — the measurement that decides whether 10 and 15 were the right numbers.
    static func mediaReviewShown(
        _ kind: MediaKind,
        prompt: MediaPrompt,
        durationMs: Int,
        attempt: Int,
        stopReason: MediaStopReason
    ) -> (String, [String: any Sendable]) {
        (mediaReviewShownName, [
            "type": kind.trackingValue,
            "media_prompt_id": prompt.id,
            "duration_s": durationSeconds(durationMs),
            "attempt": attempt,
            "stop_reason": stopReason.trackingValue,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T1, class 0. One play on review, with a running count. Multiple plays are expected.
    static func mediaPreviewPlayed(_ kind: MediaKind, attempt: Int,
                                   playCount: Int) -> (String, [String: any Sendable]) {
        (mediaPreviewPlayedName, [
            "type": kind.trackingValue, "attempt": attempt, "play_count": playCount,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 1. OUR OWN pre-permission surface was shown, before any OS sheet.
    ///
    /// Fired on view, and NOT fired when the screen is skipped — events.json says so in as many
    /// words. The users who never see the ask are deliberately unmeasured; there is no
    /// `ask skipped` event and one must not be invented at a call site.
    static func permissionPrompted(
        type: String = permissionNotifications
    ) -> (String, [String: any Sendable]) {
        (permissionPromptedName, ["type": type].merging(Stamp.of(1)) { a, _ in a })
    }

    /// T2, class 1. The platform's own dialog was raised — on the tap, never on arrival.
    static func permissionOsSheetShown(
        type: String = permissionNotifications
    ) -> (String, [String: any Sendable]) {
        (permissionOsSheetShownName, ["type": type].merging(Stamp.of(1)) { a, _ in a })
    }

    /// T2, class 1. The user answered the sheet.
    ///
    /// `result` is typed `granted | denied | limited` for the whole family and **`limited` cannot
    /// occur for notifications** — it is a photo-library state. The signature takes a Bool so
    /// there is no third value to pass by mistake.
    static func permissionResult(
        granted: Bool,
        type: String = permissionNotifications
    ) -> (String, [String: any Sendable]) {
        (permissionResultName, [
            "type": type, "result": granted ? "granted" : "denied",
        ].merging(Stamp.of(1)) { a, _ in a })
    }

    /// T2, class 1. The status changed OUTSIDE our app and the reconciler noticed.
    ///
    /// NOT THE ANSWER TO OUR OWN SHEET. That is `permissionResult`, and the two must never both
    /// fire for one act — this one is for Settings, an OS update, a Focus mode, a restore.
    ///
    /// `from` IS WHAT THE SERVER WAS LAST TOLD, not what the screen last saw: a change the client
    /// never synced is exactly the case the event exists for.
    static func permissionStatusChanged(
        from: PermissionStatus,
        to: PermissionStatus,
        detectedOn: String,
        inFlow: Bool,
        type: String = permissionNotifications
    ) -> (String, [String: any Sendable]) {
        (permissionStatusChangedName, [
            "type": type,
            "from": from.trackingValue,
            "to": to.trackingValue,
            "detected_on": detectedOn,
            "in_flow": inFlow,
        ].merging(Stamp.of(1)) { a, _ in a })
    }

    /// T1, class 0. The take was KEPT.
    ///
    /// ON `Use this clip` / `Use this recording`, NEVER ON STOP. Stopping produces a take; only
    /// accepting produces an artefact, and the gap between the two is the review screen's entire
    /// reason to exist. Firing this on Stop would report a completion for every abandoned take and
    /// make the per-prompt completion rate — what the ranking is built on — meaningless.
    static func mediaPromptRecorded(
        _ kind: MediaKind,
        prompt: MediaPrompt,
        durationMs: Int,
        retakes: Int,
        playsBeforeAccept: Int
    ) -> (String, [String: any Sendable]) {
        let name = kind == .video ? videoPromptRecordedName : voicePromptRecordedName
        return (name, [
            "type": kind.trackingValue,
            "media_prompt_id": prompt.id,
            "is_own_prompt": prompt.isOwn,
            "duration_s": durationSeconds(durationMs),
            "retakes": retakes,
            "plays_before_accept": playsBeforeAccept,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T1, class 0. A take was discarded to record again.
    ///
    /// `from` IS REQUIRED (§8): `review` is a take not yet kept, `media_card` is an artefact
    /// already on the profile. They are different products and one number for both means neither.
    static func mediaRetaken(
        _ kind: MediaKind,
        from: MediaActedFrom,
        prompt: MediaPrompt,
        attempt: Int,
        priorDurationMs: Int,
        state: MediaState
    ) -> (String, [String: any Sendable]) {
        (mediaRetakenName, [
            "type": kind.trackingValue,
            "from": from.trackingValue,
            "media_prompt_id": prompt.id,
            "attempt": attempt,
            "prior_duration_s": durationSeconds(priorDurationMs),
            "had_video": state.hasVideo,
            "had_voice": state.hasVoice,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T1, class 0. An artefact was removed from the profile. State BEFORE the deletion.
    ///
    /// DELETING IS NOT RETAKING and the two must never be collapsed: a retake has a replacement
    /// coming, a delete does not.
    ///
    /// No `from`: §8 lists this event against that key, but the specification's payload table does
    /// not carry it and delete is only reachable from a filled card — a field with one possible
    /// value measures nothing. Recorded in the conflicts log rather than resolved silently.
    static func mediaDeleted(_ kind: MediaKind, artefact: MediaArtefact,
                             state: MediaState) -> (String, [String: any Sendable]) {
        (mediaDeletedName, [
            "type": kind.trackingValue,
            "media_prompt_id": artefact.promptId,
            "duration_s": durationSeconds(artefact.durationMs),
            "had_video": state.hasVideo,
            "had_voice": state.hasVoice,
        ].merging(Stamp.of(0)) { a, _ in a })
    }
}

/// Reports a catalogue-built event. Mirrors the Kotlin helper of the same name exactly.
extension AnalyticsTracking {
    func report(_ event: (String, [String: any Sendable])) {
        track(event.0, properties: event.1)
    }
}
