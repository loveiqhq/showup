/*
 * ProfileInterests.kt
 * ShowUp · Interests (Profile 21, SHOWUP-174) -- the groups, the cap, and every rule that is not a
 * pixel
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * TWO BLOCKS, TWO RULES, ONE CHIP
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * "How I live" is six single-select groups, each optional, never capped and never collapsed: a tap
 * on the chosen chip clears it. "What I'm into" is one multi-select set across six groups, capped at
 * ten, each group showing its first four chips and a `Show more`. The chip itself knows none of this
 * -- "the cap and single-select rules are screen logic, not chip props" -- so the rules live here as
 * plain functions, tested with no Compose (ProfileInterestsRulesTest).
 *
 * WHAT IS STORED IS THE REGISTRY VALUE, NEVER THE LABEL. How I live stores the §1 value, What I'm
 * into the §3 `tag_id`, both taken from the GENERATED contract enums so a value renamed on the
 * server is a compile error here rather than a 400 on Continue. `audit/verify-profile.py` checks
 * every label against the ticket, in order.
 */
package com.showup.profile

import com.showup.api.generated.model.FlowPosition

// THE GENERATED ENUMS ARE WRITTEN OUT IN FULL, not imported, for the reason ProfileDetails.kt gives:
// inside an enum below, the simple name `Activity` is the ENTRY, which shadows the type.

/**
 * The three steps after politics -- Interests, Life and Future (Profiles 21 to 23).
 *
 * NOT [DetailStep], and the difference is the point. A detail step is one §1 field with one answer
 * list, a visibility band and the `DetailsScaffold`; these three are chip and pill pickers with
 * several answers each, no band, their own column, and an empty Continue that is always a skip.
 * Folding them into [DetailStep] would give every one of them a `fieldId`, a `hiddenField` and a
 * `mandatory` flag that mean nothing for it.
 *
 * @param stepIndex §2 `step_index`, which is also the filled segment of the group's bar.
 */
enum class PickStep(
    val stepId: String,
    val stepIndex: Int,
    val screen: ProfileScreen,
    val position: FlowPosition,
) {
    Interests("interests", 8, ProfileScreen.Interests, FlowPosition.interests),
    LifeNow("life_now", 9, ProfileScreen.LifeNow, FlowPosition.life_now),

    /** The last step: its Continue and Skip both mark profile creation complete (Profile 23). */
    LifeAhead("life_ahead", 10, ProfileScreen.LifeAhead, FlowPosition.life_ahead),
}

/**
 * One "How I live" group: its §1 field, the label above its chips, its class, and its options in
 * the ticket's order.
 *
 * ALCOHOL AND SMOKING ARE CLASS 2, kept from the retired habits screen, even though this screen
 * shows them publicly (registry 1.4.16). The class only decides how `detail_answered` is stamped.
 */
enum class LifestyleField(
    val fieldId: String,
    val label: String,
    val sensitivityClass: Int,
    val options: List<DetailOption>,
) {
    Activity(
        "activity", "Activity level", sensitivityClass = 1,
        options = listOf(
            DetailOption(com.showup.api.generated.model.Activity.couch_lover.value, "Couch-lover"),
            DetailOption(com.showup.api.generated.model.Activity.moderately_active.value, "Moderately active"),
            DetailOption(com.showup.api.generated.model.Activity.active.value, "Active"),
            DetailOption(com.showup.api.generated.model.Activity.sport_is_my_life.value, "Sport is my life"),
        ),
    ),
    Diet(
        "diet", "Diet", sensitivityClass = 1,
        options = listOf(
            DetailOption(com.showup.api.generated.model.Diet.everything.value, "Everything"),
            DetailOption(com.showup.api.generated.model.Diet.flexitarian.value, "Flexitarian"),
            DetailOption(com.showup.api.generated.model.Diet.vegetarian.value, "Vegetarian"),
            DetailOption(com.showup.api.generated.model.Diet.vegan.value, "Vegan"),
        ),
    ),
    Alcohol(
        "alcohol", "Alcohol", sensitivityClass = 2,
        options = listOf(
            DetailOption(com.showup.api.generated.model.Alcohol.never.value, "Never"),
            DetailOption(com.showup.api.generated.model.Alcohol.rarely.value, "Rarely"),
            DetailOption(com.showup.api.generated.model.Alcohol.socially.value, "Socially"),
            DetailOption(com.showup.api.generated.model.Alcohol.regularly.value, "Regularly"),
        ),
    ),
    Smoking(
        "smoking", "Smoking", sensitivityClass = 2,
        options = listOf(
            DetailOption(com.showup.api.generated.model.Smoking.no.value, "No"),
            DetailOption(com.showup.api.generated.model.Smoking.sometimes.value, "Sometimes"),
            DetailOption(com.showup.api.generated.model.Smoking.yes.value, "Yes"),
        ),
    ),
    Rhythm(
        "rhythm", "Rhythm", sensitivityClass = 0,
        options = listOf(
            DetailOption(com.showup.api.generated.model.Rhythm.early_bird.value, "Early bird"),
            DetailOption(com.showup.api.generated.model.Rhythm.night_owl.value, "Night owl"),
        ),
    ),
    Social(
        "social", "Social battery", sensitivityClass = 0,
        options = listOf(
            DetailOption(com.showup.api.generated.model.SocialBattery.cozy_at_home.value, "Cozy at home"),
            DetailOption(com.showup.api.generated.model.SocialBattery.balanced.value, "Balanced"),
            DetailOption(com.showup.api.generated.model.SocialBattery.always_out.value, "Always out"),
        ),
    ),
}

/**
 * One "What I'm into" group: its §3 `section` (the key of its expand state and the `section_id` on
 * `show_more_expanded`), its label, and its chips in the reference's `INTEREST_GROUPS` order.
 */
enum class InterestSection(
    val sectionId: String,
    val label: String,
    val options: List<DetailOption>,
) {
    Move(
        "move", "Move",
        listOf(
            DetailOption(com.showup.api.generated.model.InterestTag.gym.value, "Gym"),
            DetailOption(com.showup.api.generated.model.InterestTag.running.value, "Running"),
            DetailOption(com.showup.api.generated.model.InterestTag.hyrox_crossfit.value, "Hyrox/CrossFit"),
            DetailOption(com.showup.api.generated.model.InterestTag.yoga_pilates.value, "Yoga/Pilates"),
            DetailOption(com.showup.api.generated.model.InterestTag.bouldering.value, "Bouldering"),
            DetailOption(com.showup.api.generated.model.InterestTag.cycling.value, "Cycling"),
            DetailOption(com.showup.api.generated.model.InterestTag.swimming.value, "Swimming"),
            DetailOption(com.showup.api.generated.model.InterestTag.hiking.value, "Hiking"),
            DetailOption(com.showup.api.generated.model.InterestTag.team_sports.value, "Team sports"),
            DetailOption(com.showup.api.generated.model.InterestTag.tennis_padel.value, "Tennis/Padel"),
            DetailOption(com.showup.api.generated.model.InterestTag.martial_arts.value, "Martial arts"),
            DetailOption(com.showup.api.generated.model.InterestTag.winter_sports.value, "Winter sports"),
        ),
    ),
    Food(
        "food", "Food & Drink",
        listOf(
            DetailOption(com.showup.api.generated.model.InterestTag.cooking.value, "Cooking"),
            DetailOption(com.showup.api.generated.model.InterestTag.baking.value, "Baking"),
            DetailOption(
                com.showup.api.generated.model.InterestTag.trying_new_restaurants.value, "Trying new restaurants",
            ),
            DetailOption(com.showup.api.generated.model.InterestTag.brunch.value, "Brunch"),
            DetailOption(
                com.showup.api.generated.model.InterestTag.street_food_markets.value, "Street food & markets",
            ),
            DetailOption(com.showup.api.generated.model.InterestTag.wine.value, "Wine"),
            DetailOption(com.showup.api.generated.model.InterestTag.craft_beer.value, "Craft beer"),
            DetailOption(com.showup.api.generated.model.InterestTag.coffee_spots.value, "Coffee spots"),
            DetailOption(com.showup.api.generated.model.InterestTag.cocktail_bars.value, "Cocktail bars"),
        ),
    ),
    Culture(
        "culture", "Culture",
        listOf(
            DetailOption(com.showup.api.generated.model.InterestTag.live_music.value, "Live music"),
            DetailOption(
                com.showup.api.generated.model.InterestTag.museums_exhibitions.value, "Museums & exhibitions",
            ),
            DetailOption(com.showup.api.generated.model.InterestTag.theatre.value, "Theatre"),
            DetailOption(com.showup.api.generated.model.InterestTag.cinema.value, "Cinema"),
            DetailOption(com.showup.api.generated.model.InterestTag.reading.value, "Reading"),
            DetailOption(com.showup.api.generated.model.InterestTag.comedy.value, "Comedy"),
            DetailOption(com.showup.api.generated.model.InterestTag.festivals.value, "Festivals"),
            DetailOption(com.showup.api.generated.model.InterestTag.making_art_music.value, "Making art/music"),
        ),
    ),
    Home(
        "home", "Cozy at home",
        listOf(
            DetailOption(com.showup.api.generated.model.InterestTag.tv_series.value, "TV series"),
            DetailOption(com.showup.api.generated.model.InterestTag.movie_nights.value, "Movie nights"),
            DetailOption(com.showup.api.generated.model.InterestTag.gaming.value, "Gaming"),
            DetailOption(com.showup.api.generated.model.InterestTag.board_games.value, "Board games"),
            DetailOption(com.showup.api.generated.model.InterestTag.podcasts.value, "Podcasts"),
            DetailOption(com.showup.api.generated.model.InterestTag.plants_gardening.value, "Plants & gardening"),
        ),
    ),
    Out(
        "out", "Going out",
        listOf(
            DetailOption(com.showup.api.generated.model.InterestTag.bars.value, "Bars"),
            DetailOption(com.showup.api.generated.model.InterestTag.clubbing.value, "Clubbing"),
            DetailOption(com.showup.api.generated.model.InterestTag.dinner_parties.value, "Dinner parties"),
            DetailOption(com.showup.api.generated.model.InterestTag.quiz_nights.value, "Quiz nights"),
            DetailOption(com.showup.api.generated.model.InterestTag.karaoke.value, "Karaoke"),
            DetailOption(
                com.showup.api.generated.model.InterestTag.concerts_with_friends.value, "Concerts with friends",
            ),
            DetailOption(com.showup.api.generated.model.InterestTag.volunteering.value, "Volunteering"),
        ),
    ),
    Travel(
        "travel", "Travel style",
        listOf(
            DetailOption(com.showup.api.generated.model.InterestTag.city_breaks.value, "City breaks"),
            DetailOption(com.showup.api.generated.model.InterestTag.road_trips.value, "Road trips"),
            DetailOption(com.showup.api.generated.model.InterestTag.beach_relax.value, "Beach & relax"),
            DetailOption(com.showup.api.generated.model.InterestTag.all_inclusive.value, "All-inclusive"),
            DetailOption(com.showup.api.generated.model.InterestTag.backpacking.value, "Backpacking"),
            DetailOption(com.showup.api.generated.model.InterestTag.surf_adventure.value, "Surf & adventure"),
            DetailOption(com.showup.api.generated.model.InterestTag.wellness_spa.value, "Wellness & spa"),
            DetailOption(com.showup.api.generated.model.InterestTag.camping.value, "Camping"),
            DetailOption(com.showup.api.generated.model.InterestTag.cruises.value, "Cruises"),
        ),
    ),
    ;

    /** Whether the group starts collapsed behind a `Show more` -- "more than 4 chips" only. */
    val collapsible: Boolean get() = options.size > INTERESTS_VISIBLE

    /** How many chips `Show more` reveals: `revealed_count` on `show_more_expanded`. */
    val hiddenCount: Int get() = (options.size - INTERESTS_VISIBLE).coerceAtLeast(0)
}

/** "Capped at 10" -- What I'm into only. The server holds the same number (`MAX_INTERESTS`). */
const val INTERESTS_MAX = 10

/** The chips a collapsed group shows: `INTEREST_VISIBLE` in the reference. */
const val INTERESTS_VISIBLE = 4

/** Every §3 tag in list order: sections in order, chips in order within each. */
val INTEREST_ORDER: List<String> = InterestSection.entries.flatMap { s -> s.options.map { it.value } }

/**
 * Everything the user has picked on Interests, and which groups are open.
 *
 * [expanded] IS HERE, NOT IN THE SCREEN, because opening a group is a tracked act
 * (`show_more_expanded`) and a screen holds no logic; and because it has to survive a rotation and
 * a process death without surviving an arrival -- "groups open collapsed" on every resume and every
 * back from Life.
 */
data class InterestsDraft(
    /** §1 field id -> §1 value, for the groups answered. An unanswered group has no entry. */
    val lifestyle: Map<String, String> = emptyMap(),
    /** §3 tag_ids. At most [INTERESTS_MAX]. */
    val interests: Set<String> = emptySet(),
    /** §3 section ids of the What I'm into groups that are open. */
    val expanded: Set<String> = emptySet(),
) {
    /** Whether Continue saves anything: any How I live answer or any interest. */
    val anything: Boolean get() = lifestyle.isNotEmpty() || interests.isNotEmpty()

    val capReached: Boolean get() = interests.size >= INTERESTS_MAX
}

/**
 * The answer to a How I live group after a tap on [tapped]. A different chip moves the answer; the
 * chosen chip clears it ("tapping the selected chip clears it").
 */
fun pickLifestyle(current: String?, tapped: String): String? = if (current == tapped) null else tapped

/** What a tap on a What I'm into chip did. Each outcome is the event it reports. */
sealed interface InterestTap {
    data class Selected(val interests: Set<String>) : InterestTap
    data class Deselected(val interests: Set<String>) : InterestTap

    /** A tap on a faded chip at the cap: "the tap is a no-op". Reported, never applied. */
    data object CapReached : InterestTap
}

/** Picks or unpicks [tapped]. At the cap an unpicked chip does nothing; a picked one still unpicks. */
fun tapInterest(current: Set<String>, tapped: String): InterestTap = when {
    tapped in current -> InterestTap.Deselected(current - tapped)
    current.size >= INTERESTS_MAX -> InterestTap.CapReached
    else -> InterestTap.Selected(current + tapped)
}

/** The chips a group draws: all of them when open, the first four when collapsed. */
fun visibleOptions(section: InterestSection, expanded: Boolean): List<DetailOption> =
    if (section.collapsible && !expanded) section.options.take(INTERESTS_VISIBLE) else section.options

/** The counter's sentence. `{N} picked · {M} left`, and `10 of 10 picked` at the cap. */
fun interestsCounterLabel(picked: Int): String =
    if (picked >= INTERESTS_MAX) {
        "$INTERESTS_MAX of $INTERESTS_MAX picked"
    } else {
        "$picked picked · ${INTERESTS_MAX - picked} left"
    }

/** The counter bar's filled fraction, 0 to 1. */
fun interestsFill(picked: Int): Float = (picked.toFloat() / INTERESTS_MAX).coerceIn(0f, 1f)

/** The picked tags in LIST order, never tap order -- the server canonicalises the same way. */
fun interestsInListOrder(picked: Set<String>): List<String> = INTEREST_ORDER.filter { it in picked }

/**
 * The How I live answers to report, in list order, as (field, value) -- one `detail_answered` each,
 * on an accepted Continue. "An unanswered How I live group sends nothing."
 */
fun answeredLifestyle(draft: InterestsDraft): List<Pair<LifestyleField, String>> =
    LifestyleField.entries.mapNotNull { field -> draft.lifestyle[field.fieldId]?.let { field to it } }

/**
 * The draft the screen opens with: the saved answers, every group collapsed.
 *
 * Only values this build knows survive, so every chip drawn as chosen is one it can draw. A value a
 * newer server added simply matches no chip.
 */
fun interestsOnArrival(saved: SavedDetails): InterestsDraft = InterestsDraft(
    lifestyle = LifestyleField.entries.mapNotNull { field ->
        saved.lifestyle[field.fieldId]
            ?.takeIf { v -> field.options.any { it.value == v } }
            ?.let { field.fieldId to it }
    }.toMap(),
    interests = saved.interests.filter { it in INTEREST_ORDER }.take(INTERESTS_MAX).toSet(),
    expanded = emptySet(),
)
