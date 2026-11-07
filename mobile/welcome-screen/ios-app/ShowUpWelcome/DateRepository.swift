// DateRepository.swift
// ShowUp · what the Date screen asks the backend (SHOWUP-193)
//
// Every call goes through the GENERATED client. Nothing here describes a URL or a request body.
// Android's `DateRepository.kt`, the same calls and the same reading.

import Foundation
import ShowUpAPI

/// The Date screen's calls, behind a protocol so the model is tested with no network.
protocol DateStoring: Sendable {
    /// The viewer's active date, read in `zone` (for `today` / `tomorrow`).
    func active(zone: String) async -> Result<DateRead, CallFailure>
    /// "See {name}'s full profile": the match, built by the server as Search builds a profile.
    func partnerProfile(zone: String) async -> Result<SearchProfile, CallFailure>
    /// Debug only: a date with the test match Leonie in `minutes`.
    func createTestDate(minutes: Int) async -> Bool
    /// Debug only: a message from Leonie (the chat must be open: 90 minutes or less to go). A nil `reason`
    /// sends it with no chip, as a message typed without one.
    func messageFromMatch(reason: ChatReason?, body: String) async -> Bool
    /// Debug only: ends the viewer's date, the escape from a screen whose exits are later tickets.
    func endTestDate() async -> Bool
    /// Debug only: the test match ends the date in the chat (SHOWUP-194), through the real path -- so it needs
    /// her "I can't make it today" message in the thread, and the viewer lands on H.
    func matchEndsInChat() async -> Bool
}

struct DateRepository: DateStoring {
    let api: ShowUpAPI

    func active(zone: String) async -> Result<DateRead, CallFailure> {
        do {
            if case .ok(let ok) = try await api.client.getActiveDate(query: .init(tz: zone)) {
                let dto = try ok.body.json
                if let date = dto.date?.value1 { return .success(.active(ActiveDate(date, api: api))) }
                if let ended = dto.ended?.value1 {
                    return .success(.ended(EndedInChat(dateId: ended.dateId, name: ended.name,
                                                       endedByYou: ended.endedByYou)))
                }
                return .success(.none)
            }
            return .failure(.server)
        } catch {
            return .failure(Self.failure(error))
        }
    }

    func partnerProfile(zone: String) async -> Result<SearchProfile, CallFailure> {
        do {
            if case .ok(let ok) = try await api.client.getActiveDateProfile(query: .init(tz: zone)) {
                return .success(SearchProfile(try ok.body.json, api: api))
            }
            return .failure(.server)
        } catch {
            return .failure(Self.failure(error))
        }
    }

    func createTestDate(minutes: Int) async -> Bool {
        let body = Components.Schemas.CreateDevDateDto(minutesFromNow: minutes)
        guard case .noContent? = try? await api.client.createDevDate(body: .json(body)) else { return false }
        return true
    }

    func messageFromMatch(reason: ChatReason?, body: String) async -> Bool {
        // By wire value, not case name: a regenerated enum case must not change what is sent.
        var payload: Components.Schemas.DevDateMessageDto.reasonPayload?
        if let reason {
            guard let wire = Components.Schemas.ChatReason(rawValue: reason.rawValue) else { return false }
            payload = .init(value1: wire)
        }
        let dto = Components.Schemas.DevDateMessageDto(body: body, reason: payload)
        guard case .noContent? = try? await api.client.sendDevDateMessage(body: .json(dto)) else { return false }
        return true
    }

    func matchEndsInChat() async -> Bool {
        guard case .noContent? = try? await api.client.endDevDateInChat() else { return false }
        return true
    }

    func endTestDate() async -> Bool {
        guard case .noContent? = try? await api.client.endDevDate() else { return false }
        return true
    }

    /// No response is `network`; a response that could not be read is the server's.
    private static func failure(_ error: any Error) -> CallFailure {
        ShowUpAPI.isNoResponse(error) ? .network : .server
    }
}

extension ActiveDate {
    /// The payload, read into the app's own types. The photo path becomes a loadable URL here.
    init(_ dto: Components.Schemas.ActiveDateDto, api: ShowUpAPI) {
        let place = dto.place?.value1
        let match = dto.match
        let unread = dto.unread?.value1
        self.init(
            id: dto.id,
            meetAt: dto.meetAt,
            meetDay: dto.meetDay.rawValue,
            unlockAt: dto.unlockAt,
            coordinateOpen: dto.coordinateOpen,
            place: place.map { DatePlace(name: $0.name, address: $0.address) },
            match: DateMatch(
                userId: match.userId,
                name: match.name,
                age: match.age,
                district: match.district,
                distanceKm: match.distanceKm,
                photoUrl: match.photoUrl.flatMap { api.absoluteURL($0) },
                attendedCount: match.attendedCount),
            motivationYou: dto.motivations.you,
            motivationThem: dto.motivations.them,
            sharedVibes: dto.sharedVibes,
            sharedInterests: dto.sharedInterests,
            unread: unread.map {
                DateUnread(count: $0.count, reason: ChatReason.fromWire($0.reason?.value1.rawValue), body: $0.body,
                           sentAt: $0.sentAt)
            },
            checkedIn: dto.checkedIn,
            chatTutorialSeen: dto.chatTutorialSeen)
    }
}
