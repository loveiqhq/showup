import Foundation
import OpenAPIRuntime

/// Reads and writes the dates the server speaks.
///
/// WHY NOT THE RUNTIME'S DEFAULT. NestJS serialises every Date with `toISOString()`, which always
/// carries milliseconds (`2026-11-03T17:58:00.000Z`). The runtime's default `.iso8601` transcoder
/// rejects fractional seconds, so every decode of a server date (meetAt, unlockAt, sentAt, chat
/// createdAt, OTP expiry, ...) threw. Tests that stubbed dates without milliseconds hid it.
///
/// Decoding takes both forms; encoding writes milliseconds, as the server does. Built on
/// `Date.ISO8601FormatStyle`, a Sendable value type, rather than an `ISO8601DateFormatter`, which
/// is not Sendable.
public struct ServerDateTranscoder: DateTranscoder, Sendable {

    public init() {}

    public func encode(_ date: Date) throws -> String {
        date.formatted(Date.ISO8601FormatStyle(includingFractionalSeconds: true))
    }

    public func decode(_ dateString: String) throws -> Date {
        if let withFraction = try? Date.ISO8601FormatStyle(includingFractionalSeconds: true).parse(dateString) {
            return withFraction
        }
        if let plain = try? Date.ISO8601FormatStyle().parse(dateString) {
            return plain
        }
        throw DecodingError.dataCorrupted(.init(
            codingPath: [],
            debugDescription: "Expected a date string in ISO 8601 format, with or without fractional seconds."))
    }
}
