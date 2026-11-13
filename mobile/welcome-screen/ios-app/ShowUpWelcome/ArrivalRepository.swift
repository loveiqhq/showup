// ArrivalRepository.swift
// ShowUp · what the arrival check-in asks the backend (SHOWUP-195). Android's `ArrivalRepository.kt`.
//
// Every call goes through the GENERATED client. The dev partner check-in stays with the Date screen's dev
// routes on `DateStoring`.

import Foundation
import ShowUpAPI

/// What a check-in came back with.
enum CheckInResult: Equatable, Sendable {
    case stored
    /// 409: a committed check-in exists already (or the date is no longer on). The next date read says how.
    case alreadyCheckedIn
    case failed(CallFailure)
}

/// The arrival calls, behind a protocol so the gate is tested with no network.
protocol ArrivalStoring: Sendable {
    /// Stores the fix; the same `clientId` on a resend is stored once. A new one replaces an uncommitted check-in.
    func checkIn(dateId: String, fix: LocationFix, entry: ArrivalEntry, clientId: String) async -> CheckInResult
    /// Deletes an uncommitted check-in. Every 4xx (409: already committed, among them) is success: there is
    /// nothing left to retry. Only no answer or a 5xx is a failure.
    func undo(dateId: String) async -> Result<Void, CallFailure>
    /// Tells the match. Every 4xx (404: nothing to commit) is success for the same reason.
    func commit(dateId: String) async -> Result<Void, CallFailure>
}

struct ArrivalRepository: ArrivalStoring {
    let api: ShowUpAPI

    func checkIn(dateId: String, fix: LocationFix, entry: ArrivalEntry, clientId: String) async -> CheckInResult {
        // By wire value, not case name: a regenerated enum case must not change what is sent.
        guard let wire = Components.Schemas.ArrivalEntry(rawValue: entry.wire), let uuid = UUID(uuidString: clientId) else {
            return .failed(.server)
        }
        let dto = Components.Schemas.CreateArrivalCheckInDto(
            accuracyM: fix.accuracyM,
            clientId: Self.wire(clientId, uuid),
            entry: Components.Schemas.CreateArrivalCheckInDto.entryPayload(value1: wire),
            latitude: fix.latitude,
            longitude: fix.longitude)
        do {
            switch try await api.client.createArrivalCheckIn(path: .init(id: dateId), body: .json(dto)) {
            case .created:
                return .stored
            case let .undocumented(statusCode, _) where statusCode == 409:
                return .alreadyCheckedIn
            default:
                return .failed(.server)
            }
        } catch {
            return .failed(Self.failure(error))
        }
    }

    func undo(dateId: String) async -> Result<Void, CallFailure> {
        do {
            switch try await api.client.undoArrivalCheckIn(path: .init(id: dateId)) {
            case .noContent, .badRequest, .unauthorized:
                return .success(())
            case let .undocumented(statusCode, _):
                return Self.settled(statusCode)
            default:
                return .failure(.server)
            }
        } catch {
            return .failure(Self.failure(error))
        }
    }

    func commit(dateId: String) async -> Result<Void, CallFailure> {
        do {
            switch try await api.client.commitArrivalCheckIn(path: .init(id: dateId)) {
            case .noContent, .badRequest, .unauthorized:
                return .success(())
            case let .undocumented(statusCode, _):
                return Self.settled(statusCode)
            default:
                return .failure(.server)
            }
        } catch {
            return .failure(Self.failure(error))
        }
    }

    /// Success or ANY 4xx is done: a refused request answers the same again (409 already committed, 404 nothing to
    /// commit, 400/401/403/429 no retry will fix), so retrying it forever helps nobody. A 5xx is retried.
    private static func settled(_ statusCode: Int) -> Result<Void, CallFailure> {
        (400...499).contains(statusCode) ? .success(()) : .failure(.server)
    }

    /// No response is `network`; a response that could not be read is the server's.
    private static func failure(_ error: any Error) -> CallFailure {
        ShowUpAPI.isNoResponse(error) ? .network : .server
    }

    /// `clientId` as the generated request types it: the contract says `format: uuid`, which this generator reads
    /// as a string and a later one may read as `UUID` (`ChatRepository.wire`).
    private static func wire(_ text: String, _ uuid: UUID) -> String { text }
    private static func wire(_ text: String, _ uuid: UUID) -> UUID { uuid }
}
