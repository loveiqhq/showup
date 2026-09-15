/*
 * PromptTopics.kt
 * ShowUp · the fifteen topics, their examples, and the rules about answering them (SHOWUP-158)
 *
 * CONTENT, NOT LAYOUT. The ticket says the reference file is the source for the topics and the
 * examples and that they are to be copied wholesale rather than retyped, so they are here verbatim
 * and in the reference's group order. Nothing in this file draws anything, so all of it can be
 * checked by a JVM test.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE IDS ARE ASSIGNED, NOT DERIVED, AND THAT IS THE POINT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The ticket's dependency list says it in one line: "a stable topic id per topic. Store the id,
 * not the display string, or every copy edit orphans existing prompts."
 *
 * A slug computed from the display string would satisfy the letter of that and miss the reason.
 * Change "I'll talk for hours about…" to "I could talk for hours about…" -- a copy edit, the kind
 * that happens between a design review and a launch -- and the slug changes with it, so every
 * saved answer under the old slug belongs to a topic that no longer exists. The ids below are
 * short, assigned once, and never recomputed from anything.
 *
 * They are also what `prompt_id` is built from: the registry's own prose says "prompt_id is
 * topic_id plus the slot it occupies -- match_me_if_you__slot2 -- so a topic moved between slots
 * stays traceable". See [promptId].
 */
package com.showup.profile

/** One topic: a stable id and the question the user sees. */
data class PromptTopic(val id: String, val text: String)

/** Five topics under a name that answers "what kind of thing do you want to say". */
data class TopicGroup(val label: String, val topics: List<PromptTopic>)

/** How long an answer may be. A hard cap, and a quiet one — reaching it is not a failure. */
const val PROMPT_MAX_CHARS = 160

/**
 * Where the character counter starts existing.
 *
 * NOT ZERO, and that is the single change this screen's revision is most about. "Below 100 it is
 * not information, it is a target" — a counter from the first keystroke turns a floor ("one good
 * sentence is enough") into a ceiling the user feels they are failing to reach.
 */
const val PROMPT_COUNTER_FROM = 100

/** How many prompts a profile can carry. */
const val PROMPTS_MAX = 3

/** How many are needed to continue. ONE. The lowest honest bar, deliberately. */
const val PROMPTS_REQUIRED = 1

/**
 * The fifteen, in three groups of five.
 *
 * GROUPING IS THE WHOLE POINT. "A flat 15 is a scroll, three groups of five is a scan." The group
 * names are answers to the question the user is actually holding, which is not "which topic" but
 * "what kind of thing am I supposed to say".
 */
val TOPIC_GROUPS: List<TopicGroup> = listOf(
    TopicGroup(
        label = "Dating me",
        topics = listOf(
            PromptTopic("first_date", "On a first date, I usually…"),
            PromptTopic("out_the_door", "The easiest way to get me out the door is…"),
            PromptTopic("ideal_thirty", "My ideal 30-minute date looks like…"),
            PromptTopic("cross_town", "I'd cross town for…"),
            PromptTopic("spontaneous", "A spontaneous plan with me usually involves…"),
        ),
    ),
    TopicGroup(
        label = "Me in real life",
        topics = listOf(
            PromptTopic("thirty_feels", "What 30 minutes with me feels like…"),
            PromptTopic("real_life_more", "In real life, I'm way more…"),
            PromptTopic("unsexy_truth", "The unsexy truth about me is…"),
            PromptTopic("sunday_energy", "My default Sunday energy is…"),
            PromptTopic("weird_habit", "A weird or specific habit of mine…"),
        ),
    ),
    TopicGroup(
        label = "Opinions & obsessions",
        topics = listOf(
            PromptTopic("talk_for_hours", "I'll talk for hours about…"),
            PromptTopic("know_too_much", "A random topic I know way too much about…"),
            PromptTopic("hill_to_die_on", "The hill I'm willing to die on…"),
            PromptTopic("green_flag", "A green flag that always catches my attention…"),
            PromptTopic("hot_take", "My hot take on…"),
        ),
    ),
)

/** Every topic, flat, in group order. The order never changes between visits. */
val PROMPT_TOPICS: List<PromptTopic> = TOPIC_GROUPS.flatMap { it.topics }

/**
 * The three surfaced on the screen itself.
 *
 * One from each group, and deliberately the three easiest to answer without thinking: "the job of
 * a suggestion is to be answerable, not to be the best topic".
 */
val SUGGESTED_TOPIC_IDS: List<String> = listOf("first_date", "weird_habit", "talk_for_hours")

/**
 * One worked example per topic.
 *
 * SHORT ON PURPOSE. Every one is under 120 characters, so the example itself says "this length is
 * fine". An example that filled the box would undo the floor framing the whole revision is about.
 */
val PROMPT_EXAMPLES: Map<String, String> = mapOf(
    "first_date" to
        "…talk too fast about something I care about, then apologise for it. Don't let me apologise.",
    "out_the_door" to
        "…say the words \"there's a table free at 7\". I'll be there at 6:55.",
    "ideal_thirty" to
        "Coffee, a bench, and the good half of a conversation. No menus, no agenda.",
    "cross_town" to
        "A proper conversation. An old cinema. The 8pm walk after a long day.",
    "spontaneous" to
        "A train, a vague idea of a destination, and somewhere that does chips.",
    "thirty_feels" to
        "Fast. I ask a lot of questions and I actually wait for the answers.",
    "real_life_more" to
        "…quiet at the start and much louder by minute ten. Give me the ten.",
    "unsexy_truth" to
        "I go to bed at 10 and I'm not sorry. Breakfast dates are my best work.",
    "sunday_energy" to
        "Long walk, loud kitchen, three podcasts I won't finish.",
    "weird_habit" to
        "I read the last page of a book first. It has never once ruined it.",
    "talk_for_hours" to
        "Why every good city has a bad river, and why we keep building next to them.",
    "know_too_much" to
        "Competitive dog agility. I have opinions about the weave poles.",
    "hill_to_die_on" to
        "Showing up. Cancelling last minute isn't a scheduling problem, it's an answer.",
    "green_flag" to
        "Being kind to someone who can't do anything for you. Every time.",
    "hot_take" to
        "…brunch: it's just a queue with eggs in it.",
)

/** For a topic with no written example. Nothing reaches it today; a new topic would. */
const val PROMPT_EXAMPLE_FALLBACK = "Say the specific thing, not the safe one. Two lines is plenty."

fun topicFor(id: String): PromptTopic? = PROMPT_TOPICS.firstOrNull { it.id == id }

fun topicText(id: String): String = topicFor(id)?.text ?: id

fun exampleFor(id: String): String = PROMPT_EXAMPLES[id] ?: PROMPT_EXAMPLE_FALLBACK

/** One saved answer. The id, never the display string — see the file header. */
@kotlinx.serialization.Serializable
data class SavedPrompt(val topicId: String, val answer: String)

/**
 * `prompt_id` for the tracking registry.
 *
 * Topic id plus the slot it occupies, per §5's own prose and its own example. Slots are numbered
 * from one, because the value appears in analytics rather than in code and "slot0" reads as a
 * missing value to everybody who is not a programmer.
 *
 * THE ANSWER IS NEVER PART OF IT, and never travels at all: only `char_count` and `at_char_limit`.
 */
fun promptId(topicId: String, slot: Int): String = topicId + "__slot" + (slot + 1)

/**
 * Which topics to suggest, given what is already used.
 *
 * "Suggestions never repeat a used topic. At 0 saved the screen shows three; at 1-2 saved it shows
 * two, drawn from the first unused entries of SUGGESTED_TOPICS, falling through to group order if
 * all three are used."
 *
 * The fall-through matters: with all three suggestions used the block would otherwise be empty,
 * and an empty section label is worse than a topic the user did not expect.
 */
fun suggestionsFor(used: List<String>, count: Int): List<PromptTopic> {
    val preferred = SUGGESTED_TOPIC_IDS.filter { it !in used }.mapNotNull(::topicFor)
    if (preferred.size >= count) return preferred.take(count)
    val rest = PROMPT_TOPICS.filter { it.id !in used && it !in preferred }
    return (preferred + rest).take(count)
}

/**
 * Whether an answer counts as written.
 *
 * WHITESPACE-ONLY COUNTS AS EMPTY. The ticket says so for this screen and the group rule says so
 * for every screen in the flow, and it is the difference between a prompt that reads as blank on
 * a profile and one that was refused at the point it was written.
 */
fun promptAnswerIsEmpty(answer: String): Boolean = answer.isBlank()

/**
 * The hard cap, applied silently.
 *
 * "The cap is hard at 160: input is sliced, typing past it does nothing, and there is no alert. A
 * paste over the limit is truncated silently." Silently is the operative word — a paste that
 * lands at 400 characters is a user who wrote something elsewhere, and an alert at that moment
 * tells them off for it.
 */
fun cappedAnswer(answer: String): String = answer.take(PROMPT_MAX_CHARS)
