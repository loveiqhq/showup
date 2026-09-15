//
//  ProfileProgressRepository.swift
//  ShowUp · the one read that answers "where did this account get to" (flow rule 4a)
//
//  One call, one job. Separate from `BasicsRepository` because it spans both profile groups and
//  belongs to neither.
//
//  Returns nil when the call did not succeed — including a 401, which is correct: an account that
//  cannot be read has no progress to resume onto, and the flow starts at its own entry point rather
//  than at a step chosen from missing facts.
//

import Foundation
import ShowUpAPI

protocol ProfileProgressReading: Sendable {
    func fetch() async -> ProfileProgress?
}

struct ProfileProgressRepository: ProfileProgressReading {
    let api: ShowUpAPI

    func fetch() async -> ProfileProgress? {
        do {
            let response = try await api.client.getProfileProgress()
            switch response {
            case .ok(let ok):
                let json = try ok.body.json
                return ProfileProgress(
                    displayName: json.displayName,
                    email: json.email,
                    emailVerified: json.emailVerified,
                    hasDateOfBirth: json.hasDateOfBirth,
                    photoCount: json.photoCount,
                    promptCount: json.promptCount)
            default:
                return nil
            }
        } catch {
            return nil
        }
    }
}
