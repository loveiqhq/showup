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
    -> DevSection.Details
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
    FlowScreen.Home -> "Home (placeholder)"
}

/**
 * Whether the screen reads or writes the account. Signed out, every save on it fails, and a jump
 * there would only reproduce the bug the session-ended notice exists to explain.
 */
val FlowScreen.needsAccount: Boolean
    get() = when (devSection()) {
        DevSection.Basics, DevSection.RealYou, DevSection.Reachable, DevSection.Details -> true
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
