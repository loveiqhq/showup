# Tracking events — the implemented catalogue

**Updated 19 October 2026** (first version 7 September 2026). Every analytics event that exists in
the code today, read out of the source rather than the tickets.

This is the reconciliation sheet, not the specification. Where the code and a Jira ticket disagree,
the disagreement is listed in [What the code and the tickets disagree
about](#what-the-code-and-the-tickets-disagree-about) rather than silently resolved. Where the code
and the tracking registry (`design_handoff_showup/tracking/events.json`) disagree, that is listed in
[The registry and the code](#the-registry-and-the-code).

The totals, the screen names, the event tables and the registry comparison are **generated from the
code** by `mobile/welcome-screen/audit/gen-tracking-catalogue.py`. `audit/check-tracking-doc.py`
fails CI if they fall out of date, so this file cannot drift without someone being told.

## What changed since 7 September

- **35 events became 106.** The first version covered sign-up, the tutorial and the server. Profile
  creation (families D, E, F and G: the basics, photos, prompts, video and voice, notifications,
  Stay reachable, location, the seven details, interests, life and future) and the 48hrs Check-In
  (family H) have been added since, along with `permission_status_changed` from the shared
  permission reconciler.
- **The tables are generated now.** The first version was written by hand, per screen. It drifted
  the moment profile creation started, and its checker did not notice, because it only read the two
  files that existed in September.
- **`legal_link_tapped` changed shape.** It carries `screen_id` (the registry's stable screen key)
  instead of `screen_name`, and its `link` values are `terms`, `privacy` and `legal_notice`. There
  are now 8 legal links, all tracked: Stay reachable's Privacy Policy is new.
- **`signup_code_verify_failed` is no longer sent.** Checking the code moved to the server, and
  nothing calls the event's builder on either app. The registry already says so: `implemented:
  false`, with a `code_delta` asking for the call to be re-added on a refused code, or the event
  retired.
- **The registry's flags were brought up to date on 19 October** (PR #135): the Check-In's ten
  client events and `permission_status_changed` are now marked implemented, 105 in all.
- **The apps record events now.** Since 8 October every screen is handed the app's shared tracker,
  so events fire and show up in the developer menu's Events tab. They still go to no analytics tool.
- **The nine disagreements with Jira were re-checked against the code on 19 October:** unchanged.
  Four settled, five open.

## Totals

<!-- generated:totals:start -->
| | Events | Where |
|---|---|---|
| Sign-up and re-login | **23** | `SignUpAnalytics` · Android + iOS |
| Tutorial | **4** | `TutorialAnalytics` · Android + iOS |
| Profile creation | **61** | `ProfileAnalytics` · Android + iOS |
| 48hrs Check-In | **10** | `CheckInAnalytics` · Android + iOS |
| Server | **8** | `src/modules/analytics/events/` |
| | **106** | |

98 client events, 97 of them fired on both platforms. `screen_viewed` is counted once, under sign-up, though profile creation and the Check-In use it too.
<!-- generated:totals:end -->

## Nothing is being sent yet — and the two halves differ

**Mobile records events, and sends them nowhere.** Every screen is handed the app's shared tracker
(`AppAnalytics` on both platforms), so events fire, and on a debug build they appear live in the
developer menu's **Events** tab. There is no analytics tool behind the tracker yet, and no consent
screen, so nothing leaves the phone. Choosing a tool is one change in one place; no screen changes.

**The server is wired to a real sink but gated three ways.** Events route through
`AnalyticsService.track()`, which drops anything without consent (`isAnalyticsAllowed`), drops
anything with no id to attribute it to, and sends to the PostHog EU adapter **only** when
`posthog.enabled` is true and an API key is present — otherwise to the log sink.

So "implemented" and "flowing" are different questions. Nobody should expect data in a dashboard
from either half today.

## Conventions

`snake_case`, past tense, on both events and properties, matching the taxonomy the server already
uses (`date_confirmed`, `like_sent`, `account_created`). One taxonomy across client and server, or
the funnel has to be reassembled by hand in the warehouse.

**Two screen keys, for two jobs.** `screen_id` is the registry's stable key (§11) that a saved
funnel binds to and is never edited; `screen_name` is the human label searched for in the analytics
tool. Profile creation and the Check-In send both.

**Sign-up's `screen_name` values are verbatim from the tickets**, capitalisation and spacing
included. `Signup - welcomeback` is lower-case in SHOWUP-142 and stays that way. They look
inconsistent because they are inconsistent in the tickets; normalising them in the code would make
the code disagree with its own specification. See disagreements 1 and 6.

---

## Screen names

One event, `screen_viewed`, with a `screen_name` property, rather than one event per screen. That is
what makes a funnel query possible without naming every screen. It fires when the screen becomes
visible, and again on return.

<!-- generated:screens:start -->
| `screen_name` | `screen_id` | Area |
|---|---|---|
| `Signup - CreateAccount` | — | Sign-up |
| `Signup - welcomeback` | — | Sign-up |
| `SSOLogin` | — | Sign-up |
| `Signup - Phonenumber` | — | Sign-up |
| `Signup - Codeentry` | — | Sign-up |
| `ConnectSSO` | — | Sign-up |
| `ProfileName` | `profile_name` | Profile and Check-In |
| `ProfileEmail` | `profile_email` | Profile and Check-In |
| `ProfileEmailVerification` | `profile_email_verification` | Profile and Check-In |
| `ProfileDoB` | `profile_dob` | Profile and Check-In |
| `ProfileEmbraceBuild` | `profile_embrace_build` | Profile and Check-In |
| `ProfilePhotos` | `profile_photos` | Profile and Check-In |
| `ProfilePrompts` | `profile_prompts` | Profile and Check-In |
| `ProfileMedia` | `profile_media` | Profile and Check-In |
| `ProfileNotifications` | `profile_notifications` | Profile and Check-In |
| `ProfileReachability` | `profile_reachability` | Profile and Check-In |
| `ProfileMediaRecord` | `profile_media_record` | Profile and Check-In |
| `ProfileMediaReview` | `profile_media_review` | Profile and Check-In |
| `ProfileLocation` | `profile_location` | Profile and Check-In |
| `ProfileEmbraceDetails` | `profile_embrace_details` | Profile and Check-In |
| `ProfileHeight` | `profile_height` | Profile and Check-In |
| `ProfileGender` | `profile_gender` | Profile and Check-In |
| `ProfileOrientation` | `profile_orientation` | Profile and Check-In |
| `ProfileDatingLanguage` | `profile_dating_language` | Profile and Check-In |
| `ProfileEducation` | `profile_education` | Profile and Check-In |
| `ProfileReligion` | `profile_religion` | Profile and Check-In |
| `ProfilePolitics` | `profile_politics` | Profile and Check-In |
| `ProfileInterests` | `profile_interests` | Profile and Check-In |
| `ProfileLifeNow` | `profile_life_now` | Profile and Check-In |
| `ProfileLifeAhead` | `profile_life_ahead` | Profile and Check-In |
| `CheckinIntro` | `checkin_intro` | Profile and Check-In |
| `CheckinSearchUnavailable` | `checkin_search_unavailable` | Profile and Check-In |
| `CheckinToday` | `checkin_today` | Profile and Check-In |
| `CheckinTomorrow` | `checkin_tomorrow` | Profile and Check-In |
| `CheckinMotivation` | `checkin_motivation` | Profile and Check-In |
| `CheckinVibe` | `checkin_vibe` | Profile and Check-In |
| `CheckinFilters` | `checkin_filters` | Profile and Check-In |
| `CheckinUplift` | `checkin_uplift` | Profile and Check-In |
<!-- generated:screens:end -->

`SSOLogin` is defined and not wired: see disagreement 1.

## Events, by registry family

Grouped by the registry's families (A to T), the same letters the registry and the analytics tickets
use. **When it fires** is the registry's own wording, shortened to its first sentence. **Properties**
and whether the event **fires** are read from the code. "+ stamp" means the event also carries
`sensitivity_class` and `field_registry_version`. "Fires" means something in the app sends it; an
event that is only defined is marked as such.

<!-- generated:events:start -->
### A · Session & Nav (1)

| Event | When it fires (registry wording) | Properties (from the code) | Android | iOS |
|---|---|---|---|---|
| `screen_viewed` | Screen view controller finishes rendering. | `screen_name`, `screen_id`, `referrer_screen_id` + stamp | fires | fires |

### B · Auth (21)

| Event | When it fires (registry wording) | Properties (from the code) | Android | iOS |
|---|---|---|---|---|
| `connect_conflict_different_account_tapped` | Conflict modal "Use a different account" tapped. | — | fires | fires |
| `connect_conflict_raised` | Account exists under different provider UI | `provider` | fires | fires |
| `connect_conflict_repeated` | Second or later conflict modal within one session — the guess-wrong loop. | `count`, `provider` | fires | fires |
| `connect_conflict_resolve_tapped` | Conflict modal resolve CTA tapped — one event per attempt, result set when the owning provider's handoff returns. | `provider` | fires | fires |
| `connect_link_failed` | OAuth callback returns failure state | `provider`, `kind` | fires | fires |
| `connect_link_succeeded` | Valid identity token generated | `provider` | fires | fires |
| `connect_linking_timeout` | Linking state (E) exceeds the timeout and falls back to the error banner. | `provider` | fires | fires |
| `connect_provider_tapped` | Social sign-in button pressed | `provider` | fires | fires |
| `connect_sheet_dismissed` | User dismisses native social auth UI | `provider` | fires | fires |
| `connect_skip_tapped` | Skip tapped on connect account. | — | fires | fires |
| `signup_auth_method_tapped` | User taps one of the four auth method buttons | `method`, `is_last_used` | fires | fires |
| `signup_code_submitted` | User submits 6-digit verification code | — | fires | fires |
| `signup_code_verify_failed` | Submitted code is wrong | `attempt` | defined, not fired | defined, not fired |
| `signup_create_account_tapped` | User taps "Create free account" on Signup - CreateAccount | — | fires | fires |
| `signup_edit_phone_tapped` | User taps "Edit phone number" on Signup - Codeentry | — | fires | fires |
| `signup_get_help_tapped` | User taps "Get help" on Signup - welcomeback | — | fires | fires |
| `signup_log_in_tapped` | User taps "Already have an account? Log in" on Signup - CreateAccount | — | fires | fires |
| `signup_phone_submitted` | User submits phone number form | — | fires | fires |
| `signup_phone_validation_failed` | libphonenumber rejects the submitted number. | `reason`, `country` | fires | fires |
| `signup_resend_requested` | User taps "Resend SMS Code" | `seconds_waited`, `after_mismatch` | fires | fires |
| `signup_use_different_account_tapped` | User taps "Use a different account" on Signup - welcomeback | — | fires | fires |

### C · Onboarding & Tutorial (4)

| Event | When it fires (registry wording) | Properties (from the code) | Android | iOS |
|---|---|---|---|---|
| `tutorial_back_tapped` | Back on cards 3–6. | `card`, `card_name` | fires | fires |
| `tutorial_card_viewed` | Card becomes visible. | `card`, `card_name` | fires | fires |
| `tutorial_completed` | User finishes value declaration deck | `card`, `card_name` | fires | fires |
| `tutorial_cta_tapped` | Forward CTA on cards 1–5. | `card`, `card_name` | fires | fires |

### D · Profile Identity (13)

| Event | When it fires (registry wording) | Properties (from the code) | Android | iOS |
|---|---|---|---|---|
| `age_gate_failed` | System calculates age < 18 | `age` + stamp | fires | fires |
| `dob_submitted` | Date of birth submitted — the conversion signal for the step. | `age` + stamp | fires | fires |
| `dob_validation_failed` | Invalid date entered in DOB screen | `rule` + stamp | fires | fires |
| `email_code_resent` | User requests secondary email code | `attempt_no` + stamp | fires | fires |
| `email_code_submitted` | Email verification code entered | `attempt_no`, `result` + stamp | fires | fires |
| `email_submitted` | Email address form submitted | `domain` + stamp | fires | fires |
| `email_validation_failed` | Client/API rejects email string | `rule` + stamp | fires | fires |
| `embrace_bridge_viewed` | Bridge view displayed | `variant` + stamp | fires | fires |
| `name_submitted` | First name form submitted | `char_count` + stamp | fires | fires |
| `profile_build_started` | User starts basic details intake | `entry_point` + stamp | fires | fires |
| `profile_step_completed` | Step form validated and submitted | `step_id`, `time_on_step_s`, `prompt_count` + stamp | fires | fires |
| `profile_step_skipped` | Optional step bypassed via the "Skip" CTA. | `step_id`, `screen_id`, `has_video`, `has_voice` + stamp | fires | fires |
| `profile_step_viewed` | Specific profile step screen rendered | `step_id`, `step_index` + stamp | fires | fires |

### E · Profile Photos/Media (26)

| Event | When it fires (registry wording) | Properties (from the code) | Android | iOS |
|---|---|---|---|---|
| `media_deleted` | Delete tapped on a filled card on the Media screen — the artefact is removed from the profile and the card returns to empty. | `type`, `media_prompt_id`, `duration_s`, `had_video`, `had_voice` + stamp | fires | fires |
| `media_preview_played` | Play tapped on the review screen. | `type`, `attempt`, `play_count` + stamp | fires | fires |
| `media_prompt_list_dismissed` | The prompt list closed without going to the camera — the X, the backdrop, a swipe-down, or the system back gesture. | `type`, `dismiss_method`, `had_selection`, `time_on_sheet_s` + stamp | fires | fires |
| `media_prompt_list_opened` | "See the prompts" tapped, opening the prompt list for one medium. | `type`, `entry_point`, `has_existing`, `screen_id` + stamp | fires | fires |
| `media_prompt_selected` | The commit CTA ("Film 10 seconds" / "Record 15 seconds") tapped with a row selected — the prompt the user actually took into the camera, not every row they tried on the ... | `type`, `media_prompt_id`, `position`, `is_own_prompt`, `selections_before`, `was_previewed` + stamp | fires | fires |
| `media_retaken` | Retake tapped — either on the review screen, discarding the take just made, or on a filled card on the Media screen, replacing something already saved to the profile. | `type`, `from`, `media_prompt_id`, `attempt`, `prior_duration_s`, `had_video`, `had_voice` + stamp | fires | fires |
| `media_review_shown` | Stop tapped, or the length cap reached, and the review screen mounts — play / retake / keep. | `type`, `media_prompt_id`, `duration_s`, `attempt`, `stop_reason` + stamp | fires | fires |
| `media_screen_viewed` | The Media screen ("Show your face") finishes rendering. | `screen_id`, `has_video`, `has_voice`, `preview_video_prompt_id`, `preview_voice_prompt_id`, `preview_source` + stamp | fires | fires |
| `photo_added` | Image selected and set in slot | `slot_index`, `source`, `filled_count`, `max_slots` + stamp | fires | fires |
| `photo_removed` | Image deleted from slot | `slot_index`, `filled_count` + stamp | fires | fires |
| `photo_reordered` | Photo drag-and-drop position changed | `from`, `to` + stamp | fires | fires |
| `photo_slot_tapped` | Photo slot tapped, or the “Add more” control revealing optional slots 5–6 (action: "reveal_optional") | `slot_index`, `is_optional`, `action` + stamp | fires | fires |
| `photos_minimum_met` | Minimum required photos (4 of 6) uploaded — fires once, on the first crossing | `count`, `max_slots` + stamp | fires | fires |
| `prompt_char_limit_reached` | The answer first reached 160 characters in this editor session. | `topic_id` + stamp | fires | fires |
| `prompt_editor_dismissed` | The write sheet closed without saving. | `topic_id`, `is_edit`, `had_draft`, `draft_length_bucket`, `dismiss_method` + stamp | fires | fires |
| `prompt_editor_opened` | The write sheet mounted — on a suggestion card, on a row of the browse sheet, or on the pencil pip of a saved prompt. | `topic_id`, `is_edit`, `prompt_count` + stamp | fires | fires |
| `prompt_example_dismissed` | The worked example hidden with its X. | `topic_id` + stamp | fires | fires |
| `prompt_saved` | Save pressed with a non-empty answer. | `topic_id`, `topic_group`, `is_edit`, `length_bucket`, `prompt_count` + stamp | fires | fires |
| `prompt_topic_list_dismissed` | The topic sheet closed WITHOUT a topic being picked — the X, the scrim, a swipe-down or the system back gesture. | `dismiss_method`, `used_count`, `time_on_sheet_s`, `screen_id` + stamp | fires | fires |
| `prompt_topic_list_opened` | The “Browse all 15 topics” control tapped, opening the topic sheet. | `used_count`, `screen_id` + stamp | fires | fires |
| `prompt_topic_selected` | A topic chosen — a suggestion card on the screen or a row in the browse sheet. | `topic_id`, `topic_group`, `selection_index` + stamp | fires | fires |
| `prompts_minimum_met` | The first prompt was saved, so the step can be completed — fires once, on the first crossing. | `count`, `topic_id` + stamp | fires | fires |
| `video_prompt_recorded` | "Use this clip" accepted on the review screen — NOT on Stop. | `type`, `media_prompt_id`, `is_own_prompt`, `duration_s`, `retakes`, `plays_before_accept` + stamp | fires | fires |
| `video_recording_started` | Video capture actually begins on the recording screen — permission already granted, timer running. | `type`, `media_prompt_id`, `is_own_prompt`, `attempt`, `is_retake` + stamp | fires | fires |
| `voice_prompt_recorded` | "Use this recording" accepted on the review screen — NOT on Stop. | `type`, `media_prompt_id`, `is_own_prompt`, `duration_s`, `retakes`, `plays_before_accept` + stamp | fires | fires |
| `voice_recording_started` | Voice capture actually begins on the recording screen — permission already granted, timer running. | `type`, `media_prompt_id`, `is_own_prompt`, `attempt`, `is_retake` + stamp | fires | fires |

### F · Profile Attributes (9)

| Event | When it fires (registry wording) | Properties (from the code) | Android | iOS |
|---|---|---|---|---|
| `detail_answered` | Detail dropdown selected (Values bucketed) | `field_id`, `value_bucketed` + stamp | fires | fires |
| `detail_skipped` | Attribute field explicitly skipped | `field_id` + stamp | fires | fires |
| `field_display_opted_out` | “Don't show on my profile” switched on was: opt-out checkbox ticked — opt-out only, no event on untick. | `field_id` + stamp | fires | fires |
| `future_question_answered` | Future screen (Profile 23): accepted Continue with at least one pick — one event per answered question, in list order (family_ahead → partnership_ahead → living_ahead). | `question_id`, `value_bucketed` + stamp | fires | fires |
| `interest_cap_reached` | Tap on a faded chip while 10 "What I'm into" interests are picked — the tap is a no-op. | `cap` + stamp | fires | fires |
| `interest_deselected` | Interest pill deselected | `tag_id`, `total_selected` + stamp | fires | fires |
| `interest_selected` | Interest pill selected | `tag_id`, `total_selected` + stamp | fires | fires |
| `life_question_answered` | Life screen (Profile 22): accepted Continue with at least one pick — one event per answered question, in list order (partnership → family → living). | `question_id`, `value_bucketed` + stamp | fires | fires |
| `show_more_expanded` | Any “Show more” control expanded in place. | `screen_id`, `section_id`, `revealed_count` + stamp | fires | fires |

### G · Permissions & Consent (12)

| Event | When it fires (registry wording) | Properties (from the code) | Android | iOS |
|---|---|---|---|---|
| `channel_interest_changed` | An interest checkbox on Stay reachable (Profile 10) checked or unchecked — every flip, in order. | `channel`, `on`, `screen_id` + stamp | fires | fires |
| `consent_changed` | A consent toggle changed, in either direction — on:false is the opt-out, on:true the opt-in. | `channel`, `on`, `surface` + stamp | fires | fires |
| `consent_deactivation_abandoned` | Opt-out canceled in dialog | `channel` + stamp | fires | fires |
| `consent_deactivation_confirm_shown` | Confirmation modal shown for opt-out | `channel` + stamp | fires | fires |
| `consent_deactivation_confirmed` | Opt-out confirmed in dialog | `channel` + stamp | fires | fires |
| `permission_denied_recovery_shown` | Recovery banner shown after denial. | `type`, `reason` + stamp | fires | fires |
| `permission_os_sheet_shown` | System permission dialog triggers. | `type` + stamp | fires | fires |
| `permission_prompted` | Our own pre-permission surface shown, before any OS sheet — a modal OR a full screen. | `type` + stamp | fires | fires |
| `permission_result` | User responds to OS permission dialog. | `type`, `result` + stamp | fires | fires |
| `permission_settings_opened` | Deep-link to OS App Settings tapped | `type` + stamp | fires | fires |
| `permission_status_changed` | The OS permission status read on launch or foreground differs from the last value the client synced: the user changed it outside our app (Settings, an OS update, a Focus ... | `type`, `from`, `to`, `detected_on`, `in_flow` + stamp | fires | fires |
| `reachability_saved` | “Save preferences” tapped and the save confirmed by the server — the state the user actually leaves the screen in, which per-toggle events cannot reconstruct. | `push_on`, `permission`, `interest` + stamp | fires | fires |

### H · 48hr Check-In (10)

| Event | When it fires (registry wording) | Properties (from the code) | Android | iOS |
|---|---|---|---|---|
| `availability_slots_set` | User taps Continue on checkin_today or checkin_tomorrow and it is accepted — once per accepted Continue, after check_in_step_answered. | `day`, `preferred_hours`, `available_hours`, `window_id` + stamp | fires | fires |
| `check_in_closed` | User taps the ✕ on any Check-In screen and lands on checkin_search_unavailable. | `step_id`, `window_id` + stamp | fires | fires |
| `check_in_commit_failed` | Find my date was tapped on checkin_uplift, the commit request was sent, and it did not save. | `reason`, `attempt`, `window_id` + stamp | fires | fires |
| `check_in_filters_changed` | User taps Continue on checkin_filters and it is accepted — once per accepted Continue, after check_in_step_answered, including settings Continued unchanged ... | `filters_changed`, `min_age`, `max_age`, `distance_km`, `window_id` + stamp | fires | fires |
| `check_in_gate_tapped` | On checkin_search_unavailable (date search not available), the user taps the CTA, the Search tab or the Instant tab — all three reopen the Check-In. | `target`, `intro_passed` + stamp | fires | fires |
| `check_in_no_hours_today_resolved` | User answers the “No hours today?” confirm on checkin_today (Continue tapped with no hours). | `action`, `window_id` + stamp | fires | fires |
| `check_in_started` | User taps “Check-in for a date” on the Check-In intro (checkin_intro). | `window_id` + stamp | fires | fires |
| `check_in_step_answered` | User taps Continue on a Check-In step and it is accepted (never on the intro). | `step_id`, `answer_value`, `time_to_answer_ms` + stamp | fires | fires |
| `motivation_selected` | User taps Continue on checkin_motivation and it is accepted — once per accepted Continue, after check_in_step_answered, including a pre-selected last answer Continued ... | `motivation_id`, `window_id` + stamp | fires | fires |
| `vibe_selected` | User taps Continue on checkin_vibe and it is accepted — once per accepted Continue, after check_in_step_answered, including a pre-selected last answer Continued ... | `vibe_ids`, `vibe_count`, `window_id` + stamp | fires | fires |

### S · Settings & Lifecycle (1)

| Event | When it fires (registry wording) | Properties (from the code) | Android | iOS |
|---|---|---|---|---|
| `legal_link_tapped` | User taps a legal link. | `link`, `screen_id` | fires | fires |

### T · Errors & Diagnostics (1)

| Event | When it fires (registry wording) | Properties (from the code) | Android | iOS |
|---|---|---|---|---|
| `form_validation_failed` | Fires on the refused action, not on render: the primary CTA was pressed while the screen’s requirement was unmet — Continue under four photos, Continue with no prompt ... | `field_id`, `rule`, `screen_id`, `step_id` + stamp | fires | fires |
<!-- generated:events:end -->

## Tutorial cards

Every tutorial event carries `card` (1–6) and `card_name`.

| `card` | `card_name` | Ticket |
|---|---|---|
| 1 | `welcome` | SHOWUP-117 |
| 2 | `meet` | SHOWUP-135 |
| 3 | `match` | SHOWUP-136 |
| 4 | `match_means_meet` | SHOWUP-137 |
| 5 | `thirty_minutes` | SHOWUP-138 |
| 6 | `show_up_rate` | SHOWUP-139 |

Card 6 fires `tutorial_completed` **instead of** `tutorial_cta_tapped`, so a completion is not
double-counted as a card advance. Card 1 has no Back; card 2 hides it.

## Legal links — 8 wirings, and that is all of them

One event, `legal_link_tapped`, carrying `link` and `screen_id`. Every legal link on any screen is
tracked:

| Screen | `screen_id` | Links shown | Tracked |
|---|---|---|---|
| Startup | `signup_create_account` | Terms · Privacy Policy · Legal Notice | 3 / 3 |
| Welcome back | `signup_welcome_back` | Privacy Policy · Legal Notice | 2 / 2 |
| Connect account | `connect_sso` | Terms · Privacy Policy | 2 / 2 |
| Stay reachable | `profile_reachability` | Privacy Policy | 1 / 1 |

`link` values: `terms`, `privacy`, `legal_notice`.

## Server events

Emitted by the backend, not the app. All eight route through the same consent-gated path.

| Event | Emitted by | Properties |
|---|---|---|
| `account_created` | `auth.service` — new phone user, and new social user | `auth_method` |
| `like_sent` | `matching.service` | `has_message` |
| `match_created` | `matching.service` | `match_id` |
| `check_in_created` | `check-ins.service`, and `check-in-flow.service` on "Find my date" | `has_location` |
| `date_confirmed` | `dates.service` | `date_id`, `venue_attached` |
| `date_completed` | `dates.service` | `date_id` |
| `date_cancelled` | `dates.service` | `date_id`, `had_reason` |
| `notification_sent` | `notification-dispatch.service` — push and email | `channel`, `notification_type` |

No property carries a phone number, an email, a verification code or a token. `date_id` and
`match_id` are internal ids; user ids are hashed (`identity/hash-user-id.ts`) and properties pass
through `events/privacy.ts` before leaving.

---

## The registry and the code

The registry's `implemented` flag is the product side's record of what is built. Where it and the
code disagree:

<!-- generated:registry:start -->
**Fired on both platforms, but the registry says `implemented: false` (0):** none

**The registry says `implemented: true`, but no client catalogue defines it (0):** none

**Defined in the code but not fired on one or both platforms (1):** `signup_code_verify_failed` (Android defined, not fired, iOS defined, not fired)
<!-- generated:registry:end -->

## What the code and the tickets disagree about

The nine below were re-checked against the code on 19 October 2026. **Four are settled, five are
open**, exactly as on 7 September.

### 1. Two names for one screen — OPEN

SHOWUP-142 calls it `Signup - welcomeback`. SHOWUP-145 calls it `SSOLogin` and says it "must be
distinguishable from the first-run Startup screenview". **There is one screen**, and neither ticket
says which name wins. Both names are defined in the code; `Signup - welcomeback` is the one wired,
on the grounds that 142 describes the screen that was built.

**Needs:** one name, in one ticket. The other declaration then gets deleted.

### 2. The phone-validation `reason` vocabulary does not exist — OPEN

SHOWUP-143 names three reasons: `too short`, `not a mobile`, `unsupported country`. The validator
produces seven, because it asks libphonenumber rather than measuring length: `empty`,
`notANumber`, `tooShort`, `tooLong`, `invalidLength`, `unrecognised`, `notMobile`.

**Only two overlap.** `unsupported country` is unreachable — there is no supported-country list in
the app and libphonenumber accepts every region. Five of ours have no bucket in the ticket.

The code reports **its own** outcome. Reporting a value the code cannot produce, or collapsing five
distinct failures into one, would make the data describe something that did not happen.

**Needs:** the ticket updated to the seven real outcomes, or a stated mapping.

### 3. Three legal-link events, or one with a property — SETTLED 7 Sep 2026

The tickets list three separate click events. The code ships **one** event with a `link` property.
Confirmed by the product side: keep the one. It answers both questions from one place — which
document, and which screen — whereas three event names record the document and lose the screen.

**Needs:** the three ticket entries replaced by the one event, so Jira stops describing a shape
that was rejected.

### 4. Connect: one screenview or ten — SETTLED

SHOWUP-144 asks, in the ticket text, "is all one screen technically?". Yes: all ten states are one
component driven by `state` + `provider` + `kind`, and the conflict is a modal over it rather than
a route. So it is **one** screenview carrying a `state` property, not ten.

**Needs:** the question removed from the ticket and the answer written into it.

### 5. `state` on the Connect screenview is always `idle` — OPEN

The property exists and is populated, but only ever with `idle`, because the screenview fires when
the step becomes visible and the state has not moved yet. Either it should re-fire on state change,
or the property should be dropped. It currently looks like data and is not.

### 6. Screen names are inconsistent — OPEN

`Signup - CreateAccount`, `Signup - welcomeback`, `Signup - Phonenumber`, `Signup - Codeentry`,
`ConnectSSO`, `SSOLogin`. Three casing styles, two prefix styles. All verbatim from the tickets.
(Profile creation's names follow one style, `ProfileName`, `ProfilePhotos`, because the registry
defines them.)

**Needs:** one convention, applied across the five sign-up tickets. The code follows Jira here on
purpose, so it changes the moment Jira does.

### 7. Tutorial card 1 is the welcome screen — SETTLED

Its spec sheet is `00-welcome-spec-sheet`, which reads like a card 0, but SHOWUP-117's tracking
section names the screen "Tutorial 1 - Welcome". It is card 1, and the tour's five segments are
cards 2–6.

### 8. Card 6 does not fire a CTA event — SETTLED, worth writing down

It fires `tutorial_completed` instead, so a completion is not also counted as an advance. Any
funnel comparing `tutorial_cta_tapped` counts across cards will see five, not six, and that is
correct.

### 9. No client event has a consent gate yet — OPEN

The server has `isAnalyticsAllowed`. The apps have a shared tracker that records events but sends
them nowhere, which is not a consent mechanism — it is the absence of an analytics tool. Before a
tool is connected, the apps need the same gate, and asking the user for consent is not built on
either platform.

**Needs:** a ticket. This is the one item on this list that is a build task rather than a wording
fix.

---

## Both platforms agree, and that is checked

`audit/check-analytics-parity.py` compares the sign-up and profile catalogues name for name on
Android and iOS, and the generator checks that every event is not only defined but **sent** on both
platforms. Of the 98 client events, 97 are sent on both; the one that is not is sent on neither
(`signup_code_verify_failed`, above).

What no checker can do is tell you whether either app matches the tickets. That is a reading of
prose, which is what the list above is for.
