// ChatRepository.swift
// ShowUp · what the coordinate chat asks the backend (SHOWUP-194)
//
// Every call goes through the GENERATED client. Nothing here describes a URL or a request body. The
// dev-only routes (the test match sending, ending) stay with the Date screen's on `DateStoring`.
// Android's `ChatRepository.kt`, the same calls and the same reading.

import Foundation
import ShowUpAPI

/// One message in the thread, in the app's own types.
struct ChatMessage: Equatable, Sendable, Identifiable {
    var id: String
    /// The chip it was sent with; nil for a message typed with none, and for a name this build does not know.
    var reason: ChatReason?
    /// The text. An old message sent with a reason alone (SHOWUP-115) reads as the reason's label.
    var body: String
    var fromYou: Bool
    var sentAt: Date
    /// Mine: whether the other person has read the chat since. Theirs: nil.
    var read: Bool?
    /// Mine: the id this phone gave it, the same on every resend (Date 02 J). Theirs, and before then: nil.
    var clientId: String? = nil
    /// Mine, and not sent: its send failed and it waits for the person to tap its line (Date 02 J).
    var failed = false
}

/// What `End date` came back with.
enum EndResult: Equatable, Sendable {
    case ended(EndedInChat)
    /// 409: the date had already ended -- the other person did it, or a second tap. The next date read says how.
    case alreadyEnded
    case failed(CallFailure)
}

/// The chat's calls, behind a protocol so the model is tested with no network.
protocol ChatStoring: Sendable {
    /// The thread, oldest first.
    func thread(dateId: String) async -> Result<[ChatMessage], CallFailure>
    /// One message: the trimmed text, with the selected chip's reason or none. `clientId` is the same on every
    /// resend, so a message the server already has is answered with that copy, never added twice.
    func send(dateId: String, reason: ChatReason?, body: String, clientId: String) async -> Result<ChatMessage, CallFailure>
    /// The viewer has read the thread up to now. A stamp: a failed one is not reported, the next covers it.
    func markRead(dateId: String) async
    /// `Open the chat` on the tutorial: it does not show again for this date. A stamp, as `markRead`.
    func markTutorialSeen(dateId: String) async
    /// Ends the date for both, from the chat.
    func endInChat(dateId: String) async -> EndResult
    /// The viewer's outcome screen, G or H, has shown. A stamp, as `markRead`.
    func markEndSeen(dateId: String) async
}

struct ChatRepository: ChatStoring {
    let api: ShowUpAPI

    func thread(dateId: String) async -> Result<[ChatMessage], CallFailure> {
        do {
            if case .ok(let ok) = try await api.client.listDateChatMessages(path: .init(id: dateId)) {
                return .success(try ok.body.json.map(ChatMessage.init))
            }
            return .failure(.server)
        } catch {
            return .failure(Self.failure(error))
        }
    }

    func send(dateId: String, reason: ChatReason?, body: String, clientId: String) async -> Result<ChatMessage, CallFailure> {
        // By wire value, not case name: a regenerated enum case must not change what is sent.
        var payload: Components.Schemas.SendChatMessageDto.reasonPayload?
        if let reason {
            guard let wire = Components.Schemas.ChatReason(rawValue: reason.rawValue) else { return .failure(.server) }
            payload = .init(value1: wire)
        }
        let dto = Components.Schemas.SendChatMessageDto(body: body, clientId: clientId, reason: payload)
        do {
            if case .created(let created) = try await api.client.sendDateChatMessage(path: .init(id: dateId), body: .json(dto)) {
                return .success(ChatMessage(try created.body.json))
            }
            return .failure(.server)
        } catch {
            return .failure(Self.failure(error))
        }
    }

    func markRead(dateId: String) async {
        _ = try? await api.client.markDateChatRead(path: .init(id: dateId))
    }

    func markTutorialSeen(dateId: String) async {
        _ = try? await api.client.markChatTutorialSeen(path: .init(id: dateId))
    }

    /// `failure`'s line between "no response" and "a response that is not a success", plus the one answer
    /// that is neither: 409, the date already over, which the caller resolves with a date read.
    func endInChat(dateId: String) async -> EndResult {
        do {
            let response = try await api.client.endDateInChat(path: .init(id: dateId))
            switch response {
            case .ok(let ok):
                let dto = try ok.body.json
                return .ended(EndedInChat(dateId: dto.dateId, name: dto.name, endedByYou: dto.endedByYou))
            case let .undocumented(statusCode, _) where statusCode == 409:
                return .alreadyEnded
            default:
                return .failed(.server)
            }
        } catch {
            return .failed(Self.failure(error))
        }
    }

    func markEndSeen(dateId: String) async {
        _ = try? await api.client.markDateEndSeen(path: .init(id: dateId))
    }

    /// No response is `network`; a response that could not be read is the server's.
    private static func failure(_ error: any Error) -> CallFailure {
        ShowUpAPI.isNoResponse(error) ? .network : .server
    }
}

extension ChatMessage {
    init(_ dto: Components.Schemas.ChatMessageDto) {
        let chip = ChatReason.fromWire(dto.reason?.value1.rawValue)
        self.init(
            id: dto.id,
            reason: chip,
            body: dto.body ?? chip?.label ?? "",
            fromYou: dto.fromYou,
            sentAt: dto.createdAt,
            read: dto.read,
            // Postgres hands a uuid back in lower case; the phone makes its ids lower case to match.
            clientId: dto.clientId?.lowercased())
    }
}
