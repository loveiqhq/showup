//
//  PromptTopics.swift
//  ShowUp · the fifteen topics, their examples, and the rules about answering them (SHOWUP-158)
//
//  The Swift port of `profile/PromptTopics.kt`. Read that file's header for the argument.
//
//  THE IDS ARE ASSIGNED, NOT DERIVED. A slug computed from the display string would change with a
//  copy edit and orphan every answer saved under the old one. `prompt_id` is built from them:
//  §5's prose says "prompt_id is topic_id plus the slot it occupies — match_me_if_you__slot2 — so
//  a topic moved between slots stays traceable".
//

import Foundation

/// One topic: a stable id and the question the user sees.
struct PromptTopic: Identifiable, Equatable {
    let id: String
    let text: String
}

/// Five topics under a name that answers "what kind of thing do you want to say".
struct TopicGroup: Identifiable, Equatable {
    var id: String { label }
    let label: String
    let topics: [PromptTopic]
}

/// How long an answer may be. A hard cap, and a quiet one — reaching it is not a failure.
let promptMaxChars = 160

/// Where the character counter starts existing.
///
/// NOT ZERO, and that is the single change this screen's revision is most about. "Below 100 it is
/// not information, it is a target" — a counter from the first keystroke turns a floor into a
/// ceiling the user feels they are failing to reach.
let promptCounterFrom = 100

/// How many prompts a profile can carry.
let promptsMax = 3

/// How many are needed to continue. ONE. The lowest honest bar, deliberately.
let promptsRequired = 1

/// The fifteen, in three groups of five.
///
/// GROUPING IS THE WHOLE POINT. "A flat 15 is a scroll, three groups of five is a scan."
let topicGroups: [TopicGroup] = [
    TopicGroup(label: "Dating me", topics: [
        PromptTopic(id: "first_date", text: "On a first date, I usually…"),
        PromptTopic(id: "out_the_door", text: "The easiest way to get me out the door is…"),
        PromptTopic(id: "ideal_thirty", text: "My ideal 30-minute date looks like…"),
        PromptTopic(id: "cross_town", text: "I'd cross town for…"),
        PromptTopic(id: "spontaneous", text: "A spontaneous plan with me usually involves…"),
    ]),
    TopicGroup(label: "Me in real life", topics: [
        PromptTopic(id: "thirty_feels", text: "What 30 minutes with me feels like…"),
        PromptTopic(id: "real_life_more", text: "In real life, I'm way more…"),
        PromptTopic(id: "unsexy_truth", text: "The unsexy truth about me is…"),
        PromptTopic(id: "sunday_energy", text: "My default Sunday energy is…"),
        PromptTopic(id: "weird_habit", text: "A weird or specific habit of mine…"),
    ]),
    TopicGroup(label: "Opinions & obsessions", topics: [
        PromptTopic(id: "talk_for_hours", text: "I'll talk for hours about…"),
        PromptTopic(id: "know_too_much", text: "A random topic I know way too much about…"),
        PromptTopic(id: "hill_to_die_on", text: "The hill I'm willing to die on…"),
        PromptTopic(id: "green_flag", text: "A green flag that always catches my attention…"),
        PromptTopic(id: "hot_take", text: "My hot take on…"),
    ]),
]

/// Every topic, flat, in group order. The order never changes between visits.
let promptTopics: [PromptTopic] = topicGroups.flatMap(\.topics)

/// The three surfaced on the screen itself.
///
/// One from each group, and deliberately the three easiest to answer without thinking: "the job of
/// a suggestion is to be answerable, not to be the best topic".
let suggestedTopicIds = ["first_date", "weird_habit", "talk_for_hours"]

/// One worked example per topic.
///
/// SHORT ON PURPOSE. Every one is under 120 characters, so the example itself says "this length is
/// fine". An example that filled the box would undo the floor framing the whole revision is about.
let promptExamples: [String: String] = [
    "first_date":
        "…talk too fast about something I care about, then apologise for it. Don't let me apologise.",
    "out_the_door":
        "…say the words \"there's a table free at 7\". I'll be there at 6:55.",
    "ideal_thirty":
        "Coffee, a bench, and the good half of a conversation. No menus, no agenda.",
    "cross_town":
        "A proper conversation. An old cinema. The 8pm walk after a long day.",
    "spontaneous":
        "A train, a vague idea of a destination, and somewhere that does chips.",
    "thirty_feels":
        "Fast. I ask a lot of questions and I actually wait for the answers.",
    "real_life_more":
        "…quiet at the start and much louder by minute ten. Give me the ten.",
    "unsexy_truth":
        "I go to bed at 10 and I'm not sorry. Breakfast dates are my best work.",
    "sunday_energy":
        "Long walk, loud kitchen, three podcasts I won't finish.",
    "weird_habit":
        "I read the last page of a book first. It has never once ruined it.",
    "talk_for_hours":
        "Why every good city has a bad river, and why we keep building next to them.",
    "know_too_much":
        "Competitive dog agility. I have opinions about the weave poles.",
    "hill_to_die_on":
        "Showing up. Cancelling last minute isn't a scheduling problem, it's an answer.",
    "green_flag":
        "Being kind to someone who can't do anything for you. Every time.",
    "hot_take":
        "…brunch: it's just a queue with eggs in it.",
]

/// For a topic with no written example. Nothing reaches it today; a new topic would.
let promptExampleFallback = "Say the specific thing, not the safe one. Two lines is plenty."

func topicFor(_ id: String) -> PromptTopic? { promptTopics.first { $0.id == id } }

func topicText(_ id: String) -> String { topicFor(id)?.text ?? id }

func exampleFor(_ id: String) -> String { promptExamples[id] ?? promptExampleFallback }

/// One saved answer. The id, never the display string — see the file header.
struct SavedPrompt: Identifiable, Equatable, Codable {
    var id: String { topicId }
    let topicId: String
    let answer: String
}

/// `prompt_id` for the tracking registry.
///
/// Topic id plus the slot it occupies. Slots are numbered from one, because the value appears in
/// analytics rather than in code and "slot0" reads as a missing value to everybody who is not a
/// programmer. THE ANSWER IS NEVER PART OF IT and never travels at all — only `char_count` and
/// `at_char_limit`.
func promptId(topicId: String, slot: Int) -> String { "\(topicId)__slot\(slot + 1)" }

/// Which topics to suggest, given what is already used.
///
/// "Suggestions never repeat a used topic… falling through to group order if all three are used."
/// The fall-through matters: with all three used the block would otherwise be empty, and an empty
/// section label is worse than a topic the user did not expect.
func suggestionsFor(used: [String], count: Int) -> [PromptTopic] {
    let preferred = suggestedTopicIds.filter { !used.contains($0) }.compactMap(topicFor)
    if preferred.count >= count { return Array(preferred.prefix(count)) }
    let rest = promptTopics.filter { !used.contains($0.id) && !preferred.contains($0) }
    return Array((preferred + rest).prefix(count))
}

/// Whether an answer counts as written.
///
/// WHITESPACE-ONLY COUNTS AS EMPTY. It is the difference between a prompt that reads as blank on a
/// profile and one that was refused at the point it was written.
func promptAnswerIsEmpty(_ answer: String) -> Bool {
    answer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
}

/// The hard cap, applied silently.
///
/// "Input is sliced, typing past it does nothing, and there is no alert. A paste over the limit is
/// truncated silently." Silently is the operative word — a paste that lands at 400 characters is a
/// user who wrote something elsewhere, and an alert at that moment tells them off for it.
func cappedAnswer(_ answer: String) -> String { String(answer.prefix(promptMaxChars)) }
