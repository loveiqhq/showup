package com.showup.dev

import com.showup.profile.LocationArrival
import com.showup.welcome.FlowScreen

/*
 * The developer menu's rules, as plain functions: which section a screen sits in, what it is
 * called, whether it needs an account, and where a jump to it really lands. No UI here, so every
 * rule is tested on the JVM (DevJumpTest).
 *
 * Debug builds only: nothing outside `DevMenu` calls these, and `DevMenu` is reached only behind
 * `BuildConfig.DEBUG`, so R8 drops all of it from a release build.
 */

/** The menu's groups, in flow order -- the same groups the flow itself has. */
enum class DevSection(val title: String) {
    SignUp("Sign-up"),
    Tutorial("Tutorial"),
    Basics("The basics"),
    RealYou("The real you"),
    Reachable("Reachability and location"),
    Details("Share some details"),
    CheckIn("48hrs Check-In"),
    Home("Home"),
}

/**
 * Where a screen sits in the menu.
 *
 * EXHAUSTIVE ON PURPOSE. A screen added to [FlowScreen] does not compile until it has a section
 * here, which is what makes "new screens show up in the list automatically" true: the list is
 * [FlowScreen.entries], and this is the one decision a new screen forces.
 */
fun FlowScreen.devSection(): DevSection = when (this) {
    FlowScreen.SignUp -> DevSection.SignUp
    FlowScreen.TutorialWelcome,
    FlowScreen.MeetInRealLife,
    FlowScreen.MatchOnAvailability,
    FlowScreen.MatchMeansMeet,
    FlowScreen.ThirtyMinutes,
    FlowScreen.ShowUpEveryTime,
    -> DevSection.Tutorial
    FlowScreen.ProfileName,
    FlowScreen.ProfileEmail,
    FlowScreen.ProfileVerifyEmail,
    FlowScreen.ProfileDob,
    FlowScreen.ProfileEmbrace,
    -> DevSection.Basics
    FlowScreen.ProfilePhotos,
    FlowScreen.ProfilePrompts,
    FlowScreen.ProfileMedia,
    -> DevSection.RealYou
    FlowScreen.ProfileNotifications,
    FlowScreen.ProfileReachability,
    FlowScreen.ProfileLocation,
    -> DevSection.Reachable
    FlowScreen.ProfileEmbraceDetails,
    FlowScreen.ProfileHeight,
    FlowScreen.ProfileGender,
    FlowScreen.ProfileOrientation,
    FlowScreen.ProfileDatingLanguage,
    FlowScreen.ProfileEducation,
    FlowScreen.ProfileReligion,
    FlowScreen.ProfilePolitics,
    FlowScreen.ProfileInterests,
    FlowScreen.ProfileLifeNow,
    FlowScreen.ProfileLifeAhead,
    -> DevSection.Details
    FlowScreen.CheckInGate,
    FlowScreen.CheckInIntro,
    FlowScreen.CheckInToday,
    FlowScreen.CheckInTomorrow,
    FlowScreen.CheckInMotivation,
    FlowScreen.CheckInVibe,
    FlowScreen.CheckInFilters,
    FlowScreen.CheckInUplift,
    FlowScreen.CheckInSearch,
    FlowScreen.SearchFilters,
    FlowScreen.CheckInSettings,
    -> DevSection.CheckIn
    FlowScreen.Home -> DevSection.Home
}

/** The name a person would use for a screen. Exhaustive for the same reason as [devSection]. */
fun FlowScreen.devLabel(): String = when (this) {
    FlowScreen.SignUp -> "Startup (create account / log in)"
    FlowScreen.TutorialWelcome -> "1 · Welcome"
    FlowScreen.MeetInRealLife -> "2 · Meet in real life"
    FlowScreen.MatchOnAvailability -> "3 · Match on availability"
    FlowScreen.MatchMeansMeet -> "4 · A match means a meet"
    FlowScreen.ThirtyMinutes -> "5 · Thirty minutes"
    FlowScreen.ShowUpEveryTime -> "6 · Show up every time"
    FlowScreen.ProfileName -> "First name"
    FlowScreen.ProfileEmail -> "Email"
    FlowScreen.ProfileVerifyEmail -> "Verify email (code)"
    FlowScreen.ProfileDob -> "Date of birth"
    FlowScreen.ProfileEmbrace -> "Bridge into The real you"
    FlowScreen.ProfilePhotos -> "Photos"
    FlowScreen.ProfilePrompts -> "Prompts"
    FlowScreen.ProfileMedia -> "Video and voice"
    FlowScreen.ProfileNotifications -> "Notifications ask"
    FlowScreen.ProfileReachability -> "Stay reachable"
    FlowScreen.ProfileLocation -> "Location"
    FlowScreen.ProfileEmbraceDetails -> "Embrace 2 (bridge into details)"
    FlowScreen.ProfileHeight -> "Height"
    FlowScreen.ProfileGender -> "Gender"
    FlowScreen.ProfileOrientation -> "Orientation"
    FlowScreen.ProfileDatingLanguage -> "Dating language"
    FlowScreen.ProfileEducation -> "Education"
    FlowScreen.ProfileReligion -> "Religion"
    FlowScreen.ProfilePolitics -> "Politics"
    FlowScreen.ProfileInterests -> "Interests"
    FlowScreen.ProfileLifeNow -> "Life (current)"
    FlowScreen.ProfileLifeAhead -> "Future (last step)"
    FlowScreen.CheckInGate -> "Date search not available (gate)"
    FlowScreen.CheckInIntro -> "Intro"
    FlowScreen.CheckInToday -> "1 · Today"
    FlowScreen.CheckInTomorrow -> "2 · Tomorrow"
    FlowScreen.CheckInMotivation -> "3 · Motivation"
    FlowScreen.CheckInVibe -> "4 · Vibe"
    FlowScreen.CheckInFilters -> "5 · Filters"
    FlowScreen.CheckInUplift -> "6 · Uplift + Find my date"
    FlowScreen.CheckInSearch -> "Search 01 · Profile"
    FlowScreen.SearchFilters -> "Search · Filters"
    FlowScreen.CheckInSettings -> "Settings (placeholder)"
    FlowScreen.Home -> "Home (placeholder)"
}

/**
 * Whether the screen reads or writes the account. Signed out, every save on it fails, and a jump
 * there would only reproduce the bug the session-ended notice exists to explain.
 */
val FlowScreen.needsAccount: Boolean
    get() = when (devSection()) {
        DevSection.Basics, DevSection.RealYou, DevSection.Reachable, DevSection.Details, DevSection.CheckIn -> true
        DevSection.SignUp, DevSection.Tutorial, DevSection.Home -> false
    }

/** Where a jump really lands. */
sealed interface DevJump {
    /** Exactly where it was asked. */
    data class Lands(val screen: FlowScreen) : DevJump

    /**
     * The app skips this screen on this phone, so the jump lands where the walk would -- "behave
     * like real", decided 8 October 2026 -- and the menu says why.
     */
    data class Skipped(val lands: FlowScreen, val why: String) : DevJump

    /** Signed out, and the screen needs an account. */
    data object NeedsLogIn : DevJump

    /**
     * A Check-In screen, signed out: it opens on the in-memory test server (DevCheckInStore) instead
     * of refusing, so the whole flow can be looked at and walked with no backend and no account.
     */
    data class TestServer(val screen: FlowScreen) : DevJump
}

/**
 * Where a jump to [target] lands, from the same facts the walk decides on.
 *
 * @param notificationsAsk `shouldShowAsk(status)` -- false on Android 12 and older, and once the
 *   status is decided, which is when media's Continue goes straight past the ask.
 * @param oldAndroid whether the phone is on Android 12 or older, only to say which of the two it was.
 * @param location `locationArrival(status)` -- the same matrix as the walk.
 */
fun devJump(
    target: FlowScreen,
    signedIn: Boolean,
    notificationsAsk: Boolean,
    oldAndroid: Boolean,
    location: LocationArrival,
): DevJump {
    if (target.devSection() == DevSection.CheckIn && !signedIn) return DevJump.TestServer(target)
    if (target.needsAccount && !signedIn) return DevJump.NeedsLogIn
    return when (target) {
        FlowScreen.ProfileNotifications ->
            if (notificationsAsk) {
                DevJump.Lands(target)
            } else {
                DevJump.Skipped(
                    FlowScreen.ProfileReachability,
                    if (oldAndroid) "Skipped: Android 12 and older never asks" else "Skipped: already answered on this phone",
                )
            }
        FlowScreen.ProfileLocation -> when (location) {
            LocationArrival.Skip -> DevJump.Skipped(FlowScreen.ProfileEmbraceDetails, "Skipped: location already allowed")
            is LocationArrival.Show -> DevJump.Lands(target)
        }
        else -> DevJump.Lands(target)
    }
}

/**
 * One frame a ticket names, opened directly in that exact state -- the menu's States page. No walk,
 * no account, no backend: what the fixture shows is what the spec sheet shows.
 *
 * [id] is what `CheckInGalleryActivity` (debug source set) draws; [ticket] groups the list.
 */
data class DevState(val id: String, val ticket: String, val label: String)

/** Every frame of SHOWUP-178 to 184's evidence, in ticket order -- plus the states between them. */
val DEV_STATES: List<DevState> = listOf(
    DevState("178A", "SHOWUP-178 Intro + gate", "A · Intro"),
    DevState("178B", "SHOWUP-178 Intro + gate", "B · Date search not available"),
    DevState("179A", "SHOWUP-179 Availability", "A · Today, hours picked"),
    DevState("179B", "SHOWUP-179 Availability", "B · Today empty, No hours today?"),
    DevState("179C", "SHOWUP-179 Availability", "C · Tomorrow empty, toast"),
    DevState("179D", "SHOWUP-179 Availability", "D · Tomorrow, hours picked"),
    DevState("179HIW", "SHOWUP-179 Availability", "How it works open"),
    DevState("180A", "SHOWUP-180 Motivation", "A · Nothing selected"),
    DevState("180B", "SHOWUP-180 Motivation", "B · Empty, toast"),
    DevState("180C", "SHOWUP-180 Motivation", "C · One picked"),
    DevState("181A", "SHOWUP-181 Vibe", "A · Nothing selected"),
    DevState("181B", "SHOWUP-181 Vibe", "B · Empty, toast"),
    DevState("181C", "SHOWUP-181 Vibe", "C · Three picked"),
    DevState("181D", "SHOWUP-181 Vibe", "D · Five picked, max toast"),
    DevState("181D-end", "SHOWUP-181 Vibe", "D · Scrolled to the end"),
    DevState("182A", "SHOWUP-182 Filters", "A · Pre-filled"),
    DevState("182B", "SHOWUP-182 Filters", "B · No gender, toast"),
    DevState("182C", "SHOWUP-182 Filters", "C · Narrowed"),
    DevState("183A", "SHOWUP-183 Uplift", "A · On entry, empty"),
    DevState("183B", "SHOWUP-183 Uplift", "B · Typing"),
    DevState("183C", "SHOWUP-183 Uplift", "C · Contact info, blocked"),
    DevState("183D", "SHOWUP-183 Uplift", "D · Saving"),
    DevState("183E", "SHOWUP-183 Uplift", "E · Save failed"),
    DevState("184A", "SHOWUP-184 Search profile", "A · At rest, full profile"),
    DevState("184B", "SHOWUP-184 Search profile", "B · Be sure"),
    DevState("184C", "SHOWUP-184 Search profile", "C · Optional message"),
    DevState("184D", "SHOWUP-184 Search profile", "D · Minimum content"),
    DevState("184F", "SHOWUP-184 Search profile", "F · Actions coach"),
    DevState("184-voice", "SHOWUP-184 Search profile", "Voice note playing"),
    DevState("184-viewer", "SHOWUP-184 Search profile", "Photo viewer, photo 3"),
    DevState("184-loading", "SHOWUP-184 Search profile", "Loading"),
    DevState("184-failed", "SHOWUP-184 Search profile", "Could not load (offline)"),
    DevState("184-empty", "SHOWUP-184 Search profile", "No results (proposed copy)"),
)

/** The gallery activity, named by string: it exists in debug builds only, so main code cannot import it. */
const val DEV_GALLERY_ACTIVITY = "com.showup.dev.CheckInGalleryActivity"
