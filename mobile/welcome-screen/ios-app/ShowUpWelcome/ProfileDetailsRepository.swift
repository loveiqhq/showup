//
//  ProfileDetailsRepository.swift
//  ShowUp · the "Share some details" answers, read and written (SHOWUP-167 to SHOWUP-173)
//
//  The Swift twin of `profile/ProfileDetailsRepository.kt`. Two calls on the owner's own profile:
//  `GET /me/profile` to pre-fill, and `PATCH /me/profile` to store an answer, its visibility and the
//  flow position in ONE request — so "persist the value and the visibility flag, then the flow
//  position" cannot half-happen.
//

import Foundation
import ShowUpAPI

protocol ProfileDetailsStoring: Sendable {
    /// What the account holds, or nil when it could not be read.
    func load() async -> SavedDetails?
    /// Stores `body`. What the server now holds on success, nil on any failure.
    func save(_ body: Components.Schemas.UpsertProfileDto) async -> SavedDetails?
}

struct ProfileDetailsRepository: ProfileDetailsStoring {
    let api: ShowUpAPI

    func load() async -> SavedDetails? {
        do {
            if case .ok(let ok) = try await api.client.getProfile() {
                return SavedDetails(try ok.body.json)
            }
            return nil
        } catch {
            return nil
        }
    }

    func save(_ body: Components.Schemas.UpsertProfileDto) async -> SavedDetails? {
        do {
            if case .ok(let ok) = try await api.client.updateProfile(body: .json(body)) {
                return SavedDetails(try ok.body.json)
            }
            return nil
        } catch {
            return nil
        }
    }
}

extension SavedDetails {
    /// The owner's view. The answers arrive as STRINGS by design — a newer server may add values,
    /// and an installed app must still load its own profile. Unknown values match no row.
    init(_ dto: Components.Schemas.OwnProfileDto) {
        self.init(
            heightCm: dto.heightCm,
            gender: dto.gender,
            orientation: dto.orientation,
            datingLanguages: dto.datingLanguages ?? [],
            education: dto.education,
            religion: dto.religion,
            politics: dto.politics,
            hiddenFields: Set(dto.hiddenFields)
        )
    }
}
