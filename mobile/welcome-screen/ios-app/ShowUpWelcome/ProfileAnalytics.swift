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

    var screenId: String { rawValue }

    var screenName: String {
        switch self {
        case .name: return "ProfileName"
        case .email: return "ProfileEmail"
        case .emailVerification: return "ProfileEmailVerification"
        case .dob: return "ProfileDoB"
        case .embraceBuild: return "ProfileEmbraceBuild"
        case .photos: return "ProfilePhotos"
        case .prompts: return "Profile - Prompts"
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

/// The `rule` vocabulary shared by the validation events.
enum ValidationRule {
    static let requiredMissing = "required_missing"
    static let format = "format"

    /// Continue pressed with fewer than four CONFIRMED photos (SHOWUP-156).
    static let photosBelowMinimum = "photos_below_minimum"

    /// Continue pressed with no prompt saved (SHOWUP-158).
    static let nothingSelected = "nothing_selected"
}

/// `channel` from enums.json §8.
///
/// **`marketing_email` is not `email`.** One address, three uses, told apart by this vocabulary:
/// this box governs marketing mail only; the `email` toggle on the later Stay reachable screen is a
/// preference about being contacted regarding a match; transactional mail has no consent to
/// withdraw and is never tracked as one.
enum ConsentChannel {
    static let marketingEmail = "marketing_email"
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
    /// enums.json → registry_version at the time of writing.
    static let fieldRegistryVersion = "1.3.0"

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
    static let promptTopicPickerOpenedName = "prompt_topic_picker_opened"
    static let promptTopicSelectedName = "prompt_topic_selected"
    static let promptAnsweredName = "prompt_answered"
    static let promptEditedName = "prompt_edited"

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

    // MARK: family · Profile Attributes (SHOWUP-158)
    //
    // THE TICKET'S TRACKING SECTION IS OUT OF DATE, and following it would have meant inventing
    // five names. It says "the whole prompt-authoring funnel is unregistered"; registry 1.3.0
    // carries them under "Profile Attributes", so they are used as named rather than re-minted.
    //
    // STILL GENUINELY MISSING, and NOT invented here: `entry_point` (suggestion | browse | edit),
    // which the ticket calls "the one measurement this revision exists to produce" and whose value
    // set is not in enums.json; and abandonment, a write sheet opened and closed without saving.
    //
    // NEVER THE ANSWER AND NEVER THE DRAFT - `char_count` and `at_char_limit` only.

    /// T2, class 0. `Browse all 15 topics` was pressed.
    static func promptTopicPickerOpened(slotIndex: Int) -> (String, [String: any Sendable]) {
        (promptTopicPickerOpenedName, ["slot_index": slotIndex].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. A topic was chosen, from a suggestion card or from the sheet.
    static func promptTopicSelected(topicId: String) -> (String, [String: any Sendable]) {
        (promptTopicSelectedName, ["topic_id": topicId].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. An answer was saved.
    ///
    /// `prompt_id` is the topic id plus the slot, per §5's own prose and its own example -
    /// `match_me_if_you__slot2` - so a topic moved between slots stays traceable.
    static func promptAnswered(promptId: String, charCount: Int,
                               atCharLimit: Bool) -> (String, [String: any Sendable]) {
        (promptAnsweredName, ["prompt_id": promptId, "char_count": charCount,
                              "at_char_limit": atCharLimit].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. An existing answer was changed rather than a new one added.
    static func promptEdited(promptId: String) -> (String, [String: any Sendable]) {
        (promptEditedName, ["prompt_id": promptId].merging(Stamp.of(0)) { a, _ in a })
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
    static func consentChanged(
        on: Bool,
        surface: String = ConsentSurface.profileCreation
    ) -> (String, [String: any Sendable]) {
        (consentChangedName, [
            "channel": ConsentChannel.marketingEmail, "on": on, "surface": surface,
        ].merging(Stamp.of(1)) { a, _ in a })
    }

    /// T2, class 0. **BLOCKED** — the skip path itself is an open question in ticket 02.
    static func stepSkipped(_ step: BasicsStep) -> (String, [String: any Sendable]) {
        (profileStepSkipped, ["step_id": step.stepId].merging(Stamp.of(0)) { a, _ in a })
    }
}

/// Reports a catalogue-built event. Mirrors the Kotlin helper of the same name exactly.
extension AnalyticsTracking {
    func report(_ event: (String, [String: any Sendable])) {
        track(event.0, properties: event.1)
    }
}
