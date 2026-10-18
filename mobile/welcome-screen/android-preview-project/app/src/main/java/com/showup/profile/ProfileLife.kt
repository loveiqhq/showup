/*
 * ProfileLife.kt
 * ShowUp · Life and Future (Profiles 22 and 23, SHOWUP-175 / 176) -- the six questions and the one
 * rule they share
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ONE TOGGLE, TWO MAPS
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Both screens are three multi-select questions. What differs is which answers stand alone: on Life
 * only `No children`; on Future all three family answers ("one answer at most") and `I want to find
 * out together` in the other two. Profile 22: "the rule lives in one function, `toggleLifePick`,
 * which Future reuses with its own map" -- here the map is each question's [LifeQuestion.exclusive],
 * and [toggleLifePick] is that one function. Profile 23's first AC is "no copy, no fork".
 *
 * MATCHING ONLY. None of these answers is ever shown on a profile or returned to anyone but their
 * owner (`OwnProfileDto`). Partnership and partnership vision are Class 2 -- they reveal sexual life.
 */
package com.showup.profile

import com.showup.welcome.BrandIcon

/**
 * One §4 question: its id, its category label and icon, its class, its options in the ticket's
 * order, and the values that cannot share an answer with anything else.
 *
 * Values come from the GENERATED contract enums, as everywhere in this group.
 */
enum class LifeQuestion(
    val questionId: String,
    val label: String,
    val icon: BrandIcon,
    val sensitivityClass: Int,
    val options: List<DetailOption>,
    val exclusive: Set<String>,
) {
    // ── Life (Profile 22): Partnership · Family · Living ──────────────────────
    Partnership(
        "partnership", "Partnership situation", BrandIcon.Heart, sensitivityClass = 2,
        options = listOf(
            DetailOption(com.showup.api.generated.model.LifePartnership.single.value, "Single"),
            DetailOption(com.showup.api.generated.model.LifePartnership.open_relationship.value, "Open relationship"),
            DetailOption(
                com.showup.api.generated.model.LifePartnership.ethically_non_monogamous.value,
                "Ethically non-monogamous",
            ),
            DetailOption(com.showup.api.generated.model.LifePartnership.divorced.value, "Divorced"),
        ),
        exclusive = emptySet(),
    ),
    Family(
        "family", "Family situation", BrandIcon.Users, sensitivityClass = 1,
        options = listOf(
            DetailOption(com.showup.api.generated.model.LifeFamily.no_children.value, "No children"),
            DetailOption(com.showup.api.generated.model.LifeFamily.has_children.value, "I have children"),
            DetailOption(com.showup.api.generated.model.LifeFamily.single_parent.value, "Single parent"),
            DetailOption(com.showup.api.generated.model.LifeFamily.co_parenting.value, "Co-parenting"),
            DetailOption(com.showup.api.generated.model.LifeFamily.empty_nester.value, "Empty nester"),
        ),
        exclusive = setOf(com.showup.api.generated.model.LifeFamily.no_children.value),
    ),
    // The generator strips the shared `living_` prefix from the CONSTANT names (`alone`); the values
    // on the wire are still `living_alone` and so on, which is all that is stored and reported.
    Living(
        "living", "Living situation", BrandIcon.Home, sensitivityClass = 1,
        options = listOf(
            DetailOption(com.showup.api.generated.model.LifeLiving.alone.value, "Living alone"),
            DetailOption(
                com.showup.api.generated.model.LifeLiving.with_roommates.value, "Living with roommates",
            ),
            DetailOption(com.showup.api.generated.model.LifeLiving.with_family.value, "Living with family"),
            DetailOption(
                com.showup.api.generated.model.LifeLiving.with_children.value, "Living with children",
            ),
        ),
        exclusive = emptySet(),
    ),

    // ── Future (Profile 23): Family · Partnership · Living -- family FIRST here ──
    FamilyAhead(
        "family_ahead", "Family vision", BrandIcon.Users, sensitivityClass = 1,
        options = listOf(
            DetailOption(com.showup.api.generated.model.FutureFamily.want_kids.value, "I want kids"),
            DetailOption(com.showup.api.generated.model.FutureFamily.dont_want_kids.value, "I don't want kids"),
            DetailOption(
                com.showup.api.generated.model.FutureFamily.find_out_together.value, "I want to find out together",
            ),
        ),
        // ALL THREE: wanting and not wanting kids cannot both hold, and either replaces "find out
        // together". Family is single-select in effect -- but a tap on the picked one clears it.
        exclusive = com.showup.api.generated.model.FutureFamily.entries.map { it.value }.toSet(),
    ),
    PartnershipAhead(
        "partnership_ahead", "Partnership vision", BrandIcon.Heart, sensitivityClass = 2,
        options = listOf(
            DetailOption(
                com.showup.api.generated.model.FuturePartnership.monogamous_relationship.value,
                "Monogamous relationship",
            ),
            DetailOption(
                com.showup.api.generated.model.FuturePartnership.open_relationship.value, "Open relationship",
            ),
            DetailOption(
                com.showup.api.generated.model.FuturePartnership.ethically_non_monogamous.value,
                "Ethically non-monogamous",
            ),
            DetailOption(
                com.showup.api.generated.model.FuturePartnership.find_out_together.value,
                "I want to find out together",
            ),
        ),
        exclusive = setOf(com.showup.api.generated.model.FuturePartnership.find_out_together.value),
    ),
    LivingAhead(
        "living_ahead", "Living style vision", BrandIcon.Compass, sensitivityClass = 1,
        options = listOf(
            DetailOption(com.showup.api.generated.model.FutureLiving.stay_local.value, "Stay local"),
            DetailOption(com.showup.api.generated.model.FutureLiving.expat_life.value, "Expat life"),
            DetailOption(com.showup.api.generated.model.FutureLiving.move_abroad.value, "Move abroad"),
            DetailOption(
                com.showup.api.generated.model.FutureLiving.find_out_together.value, "I want to find out together",
            ),
        ),
        exclusive = setOf(com.showup.api.generated.model.FutureLiving.find_out_together.value),
    ),
}

/** The questions a step asks, in screen order. Interests asks none of these. */
val PickStep.questions: List<LifeQuestion>
    get() = when (this) {
        PickStep.Interests -> emptyList()
        PickStep.LifeNow -> listOf(LifeQuestion.Partnership, LifeQuestion.Family, LifeQuestion.Living)
        PickStep.LifeAhead -> listOf(LifeQuestion.FamilyAhead, LifeQuestion.PartnershipAhead, LifeQuestion.LivingAhead)
    }

/**
 * The answer to [question] after a tap on [tapped] -- `toggleLifePick` in the reference.
 *
 * A picked value unpicks. An exclusive value replaces the whole answer. Any other value is added and
 * drops the exclusive ones. Nothing else in the question moves, and no other question is touched.
 */
fun toggleLifePick(question: LifeQuestion, current: Set<String>, tapped: String): Set<String> = when {
    tapped in current -> current - tapped
    tapped in question.exclusive -> setOf(tapped)
    else -> current.filterNot { it in question.exclusive }.toSet() + tapped
}

/** The picked values in LIST order, never tap order: what is saved, and what `value_bucketed` joins. */
fun lifeInListOrder(question: LifeQuestion, picked: Set<String>): List<String> =
    question.options.map { it.value }.filter { it in picked }

/** `value_bucketed`: list order, comma-joined, no spaces -- e.g. `single,divorced`. */
fun lifeBucketed(question: LifeQuestion, picked: Set<String>): String =
    lifeInListOrder(question, picked).joinToString(",")

/**
 * Everything picked on Life and Future, by §4 `question_id`. A question with no entry has nothing
 * picked. ONE DRAFT FOR BOTH SCREENS, for the reason the detail group has one: Future's back shows
 * Life's saved answers, and arriving on either resets only its own three.
 */
data class LifeDraft(val answers: Map<String, Set<String>> = emptyMap()) {
    fun picked(question: LifeQuestion): Set<String> = answers[question.questionId].orEmpty()

    fun with(question: LifeQuestion, picked: Set<String>): LifeDraft =
        LifeDraft(answers + (question.questionId to picked))

    /** Whether Continue on [step] saves anything: a pick in any of its questions. */
    fun anything(step: PickStep): Boolean = step.questions.any { picked(it).isNotEmpty() }

    /** [question]'s picks in list order, or null when it has none -- "not supplied" on the wire. */
    fun answered(question: LifeQuestion): List<String>? =
        lifeInListOrder(question, picked(question)).takeIf { it.isNotEmpty() }
}

/**
 * [current] with [step]'s three questions reset from what the account holds.
 *
 * Only values this build knows survive, and a saved set that breaks its own exclusive rule (which
 * the server refuses, so it can only be older data) is read through [toggleLifePick] one value at a
 * time, which leaves a set the screen could have produced.
 */
fun lifeOnArrival(step: PickStep, current: LifeDraft, saved: SavedDetails): LifeDraft =
    step.questions.fold(current) { draft, question ->
        val known = saved.life[question.questionId].orEmpty().distinct()
            .filter { v -> question.options.any { it.value == v } }
        draft.with(question, known.fold(emptySet()) { picked, v -> toggleLifePick(question, picked, v) })
    }

/**
 * Whether [question]'s saved answer holds a value this build cannot draw -- one a newer server added.
 * An untouched question holding one is not sent, so Continue cannot replace it with the part the
 * screen could show (the same rule as `interestsHoldUnknown`).
 */
fun lifeHoldsUnknown(question: LifeQuestion, saved: SavedDetails): Boolean =
    saved.life[question.questionId].orEmpty().any { v -> question.options.none { it.value == v } }

/**
 * [current] once the account's answers land LATE -- after the user started on [step].
 *
 * Each question is one part: a question the user touched keeps their answer, every other one takes
 * the saved answer. Without this, Life's Continue saved an untouched question as `[]` over an
 * answer the screen had never shown (see `interestsOnLateRead`).
 *
 * @param touched the question ids the user changed since arriving.
 */
fun lifeOnLateRead(step: PickStep, current: LifeDraft, saved: SavedDetails, touched: Set<String>): LifeDraft {
    val fresh = lifeOnArrival(step, current, saved)
    return step.questions.fold(current) { draft, question ->
        if (question.questionId in touched) draft else draft.with(question, fresh.picked(question))
    }
}
