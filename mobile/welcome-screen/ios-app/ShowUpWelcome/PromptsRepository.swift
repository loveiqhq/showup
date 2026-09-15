//
//  PromptsRepository.swift
//  ShowUp · what the prompts step asks the backend, and what the answers mean (SHOWUP-158)
//
//  The Swift twin of `profile/PromptsRepository.kt`. Every call goes through the GENERATED client;
//  nothing here describes a URL or a request body.
//
//  THE API IS KEYED ON THE TOPIC, WHICH IS WHY SAVE IS ONE CALL. `PUT /me/prompts/{topicId}`
//  creates or overwrites, so the first save and every later one are the same request — and it is
//  idempotent, which is what makes a double-tap on Save harmless.
//
//  WHAT 400 MEANS HERE, AND WHY IT IS NOT SHOWN. The server refuses three things: a blank answer,
//  one over 160 characters, and a fourth topic. The screen prevents all three before the call, so a
//  400 means the client and the server disagree about a rule — a bug rather than something to word
//  for a user. It is reported as `.failed` and the sheet keeps its draft.
//

import Foundation
import ShowUpAPI

/// The answer to "store this answer against this topic".
enum SavePromptResult: Equatable, Sendable {
    case saved(SavedPrompt)
    /// The write did not land. One case, not several — see the file header.
    case failed
}

/// The answer to "remove this prompt".
enum RemovePromptResult: Equatable, Sendable {
    case removed
    case failed
}

/// Reads and writes the account's prompts.
///
/// A protocol so the model can be driven from a test with no network.
protocol PromptsRepositoring: Sendable {
    func list() async -> [SavedPrompt]?
    func save(topicId: String, answer: String) async -> SavePromptResult
    func remove(topicId: String) async -> RemovePromptResult
}

struct PromptsRepository: PromptsRepositoring {
    let api: ShowUpAPI

    /// What the account already holds, in reading order.
    ///
    /// Returns nil when the call did not succeed, which the caller reads as "do not touch what is
    /// on screen". An empty list and an unreachable server are different facts, and collapsing
    /// them would wipe prompts the user had just written.
    func list() async -> [SavedPrompt]? {
        do {
            let response = try await api.client.listPrompts()
            switch response {
            case .ok(let ok):
                let json = try ok.body.json
                // Already ordered by the server; the sort is defensive and free at three rows.
                return json
                    .sorted { $0.position < $1.position }
                    .map { SavedPrompt(topicId: $0.topicId, answer: $0.answer) }
            default:
                return nil
            }
        } catch {
            return nil
        }
    }

    /// Creates or overwrites the answer for `topicId`.
    func save(topicId: String, answer: String) async -> SavePromptResult {
        do {
            let response = try await api.client.upsertPrompt(
                path: .init(topicId: topicId),
                body: .json(.init(answer: answer))
            )
            switch response {
            case .ok(let ok):
                let json = try ok.body.json
                return .saved(SavedPrompt(topicId: json.topicId, answer: json.answer))
            default:
                return .failed
            }
        } catch {
            return .failed
        }
    }

    /// Removes the prompt for `topicId`. 204 on success, and deleting nothing is success.
    func remove(topicId: String) async -> RemovePromptResult {
        do {
            let response = try await api.client.deletePrompt(path: .init(topicId: topicId))
            switch response {
            case .noContent:
                return .removed
            default:
                return .failed
            }
        } catch {
            return .failed
        }
    }
}

/// Walking the prompts step with no backend at all.
///
/// The fourth of these, after `DevOfflineAuth`, the email one and `DevOfflinePhotos`. The rules are
/// identical: only reached when nothing answered, never overrides a server that replied.
///
/// This one always succeeds, unlike the photos stand-in. There is no failure state on the prompts
/// screen to make reachable by other means — Save either writes a prompt or nudges about an empty
/// field, and the nudge is reached by pressing Save on an empty field.
actor DevOfflinePrompts: PromptsRepositoring {
    static let shared = DevOfflinePrompts()

    private var stored: [SavedPrompt] = []

    func list() async -> [SavedPrompt]? { stored }

    func save(topicId: String, answer: String) async -> SavePromptResult {
        let prompt = SavedPrompt(topicId: topicId, answer: answer)
        if let existing = stored.firstIndex(where: { $0.topicId == topicId }) {
            stored[existing] = prompt
        } else {
            // At the BOTTOM, which is what the server does and what the screen promises.
            stored.append(prompt)
        }
        return .saved(prompt)
    }

    func remove(topicId: String) async -> RemovePromptResult {
        stored.removeAll { $0.topicId == topicId }
        return .removed
    }

    /// Forgets everything. For tests, so one case cannot leak into the next.
    func reset() { stored.removeAll() }
}
