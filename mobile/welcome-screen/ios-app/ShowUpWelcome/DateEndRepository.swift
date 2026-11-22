// DateEndRepository.swift
// ShowUp · End date, Stay for now and Cancel (SHOWUP-197, SHOWUP-198)
//
// Every call goes through the GENERATED client. The 409 refusals carry their reason in the body's `code`; it is
// read here and handed on as a string, so a screen decides what each one says. Android's `DateEndRepository.kt`.

import Foundation
import ShowUpAPI

/// How the viewer ends the date (registry section 43). The raw value is the wire value.
enum EndReason: String, CaseIterable, Sendable { case met, partnerNoShow = "partner_no_show", uncomfortable }

/// Where the end started (registry section 45). The raw value is the wire value.
enum EndEntry: String, CaseIterable, Sendable {
    case endDateBar = "end_date_bar", checkinBarLink = "checkin_bar_link", report
}

/// The code of a refusal that is a 404: the server has the end and cancel routes switched off.
let refusedNotFound = "not_found"

/// The code of a 409 whose body named none.
let refusedUnknown = "unknown"

/// What `End date` came back with. Named apart from the chat's `EndResult`, which is Date 02's.
enum EndDateResult: Equatable, Sendable {
    case ended(EndedOutcome)
    /// 409 `date_not_active`, `before_meet` or `not_checked_in`; or `refusedNotFound` when the switch is off.
    case refused(code: String)
    case failed(CallFailure)
}

/// What `Cancel` came back with.
enum CancelResult: Equatable, Sendable {
    case cancelled(EndedOutcome)
    /// 409 `date_not_active` or `checked_in`; or `refusedNotFound` when the switch is off.
    case refused(code: String)
    case failed(CallFailure)
}

/// End date and Stay for now, behind a protocol so the models are tested with no network.
protocol DateEndStoring: Sendable {
    func end(dateId: String, reason: EndReason, entry: EndEntry) async -> EndDateResult
    /// "Stay for now": an internal flag the date keeps. True when it landed; the caller does not block on it.
    func flagDiscomfort(dateId: String) async -> Bool
}

/// Cancel from the red link, behind a protocol for the same reason.
protocol CancelStoring: Sendable {
    func cancel(dateId: String) async -> CancelResult
}

extension EndedOutcome {
    /// The ended payload, read into the app's own types. The photo path becomes a loadable URL here.
    init(_ dto: Components.Schemas.EndedOutcomeDto, api: ShowUpAPI) {
        self.init(dateId: dto.dateId, name: dto.name, endedByYou: dto.endedByYou,
                  photoUrl: dto.photoUrl.flatMap { api.absoluteURL($0) },
                  reason: OutcomeReason.fromWire(dto.reason.value1.rawValue),
                  partnerScreen: PartnerScreen.fromWire(dto.partnerScreen?.value1.rawValue))
    }
}

/// The refusal code of a 404 or 409: the body's `code`, or its `error`, which carries the same. The body text is
/// read at the call site, so no OpenAPIRuntime type is named here.
private func refusalCode(_ statusCode: Int, text: String) -> String {
    if statusCode == 404 { return refusedNotFound }
    guard let object = try? JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any],
          let code = (object["code"] as? String) ?? (object["error"] as? String), !code.isEmpty
    else { return refusedUnknown }
    return code
}

struct DateEndRepository: DateEndStoring {
    let api: ShowUpAPI

    func end(dateId: String, reason: EndReason, entry: EndEntry) async -> EndDateResult {
        // By wire value, not case name: a regenerated enum case must not change what is sent.
        guard let wireReason = Components.Schemas.DateEndReason(rawValue: reason.rawValue),
              let wireEntry = Components.Schemas.EndEntry(rawValue: entry.rawValue)
        else { return .failed(.server) }
        let dto = Components.Schemas.EndDateDto(entry: .init(value1: wireEntry), reason: .init(value1: wireReason))
        do {
            switch try await api.client.endDate(path: .init(id: dateId), body: .json(dto)) {
            case .ok(let ok):
                return .ended(EndedOutcome(try ok.body.json, api: api))
            case let .undocumented(statusCode, payload) where statusCode == 409 || statusCode == 404:
                var text = ""
                if let body = payload.body { text = (try? await String(collecting: body, upTo: 8 * 1024)) ?? "" }
                return .refused(code: refusalCode(statusCode, text: text))
            default:
                return .failed(.server)
            }
        } catch {
            return .failed(ShowUpAPI.isNoResponse(error) ? .network : .server)
        }
    }

    func flagDiscomfort(dateId: String) async -> Bool {
        guard case .noContent? = try? await api.client.flagDateDiscomfort(path: .init(id: dateId)) else { return false }
        return true
    }
}

struct CancelRepository: CancelStoring {
    let api: ShowUpAPI

    func cancel(dateId: String) async -> CancelResult {
        do {
            switch try await api.client.cancelDate(path: .init(id: dateId), body: .json(.init())) {
            case .ok(let ok):
                return .cancelled(EndedOutcome(try ok.body.json, api: api))
            case let .undocumented(statusCode, payload) where statusCode == 409 || statusCode == 404:
                var text = ""
                if let body = payload.body { text = (try? await String(collecting: body, upTo: 8 * 1024)) ?? "" }
                return .refused(code: refusalCode(statusCode, text: text))
            default:
                return .failed(.server)
            }
        } catch {
            return .failed(ShowUpAPI.isNoResponse(error) ? .network : .server)
        }
    }
}
