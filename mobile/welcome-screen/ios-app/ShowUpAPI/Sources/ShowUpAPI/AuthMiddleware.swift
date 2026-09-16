import Foundation
import HTTPTypes
import OpenAPIRuntime

/// Attaches the bearer token to every request, and retries a 401 once with a refreshed one.
///
/// WHY MIDDLEWARE AND NOT A PARAMETER
///
/// The generated client is rebuilt from the contract on every build, so nothing added to it
/// survives. More importantly, auth as a parameter means every call site can forget it -- and a
/// forgotten token does not fail loudly, the endpoint just behaves as though nobody were signed in.
/// Here it cannot be forgotten, and there is one place to change when it changes.
public struct AuthMiddleware: ClientMiddleware {

    private let tokens: any TokenStoring
    private let refresher: TokenRefresher?

    /// - Parameter refresher: omit to get injection without retry, which is what the sign-in
    ///   endpoints want -- they are public, and a 401 from them is a real answer rather than an
    ///   expired session.
    public init(tokens: any TokenStoring, refresher: TokenRefresher? = nil) {
        self.tokens = tokens
        self.refresher = refresher
    }

    public func intercept(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID: String,
        next: (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?)
    ) async throws -> (HTTPResponse, HTTPBody?) {

        let token = await tokens.accessToken()

        // No token is a normal state, not an error: the sign-in routes are public and are called
        // precisely when there is nothing to attach. Send as-is and let the backend decide.
        var authorised = request
        if let token {
            authorised.headerFields[.authorization] = "Bearer \(token)"
        }

        let (response, responseBody) = try await next(authorised, body, baseURL)

        guard response.status == .unauthorized, let refresher else {
            return (response, responseBody)
        }

        // A retry re-sends the request body, and an HTTPBody is not always re-readable: a `.single`
        // body is a one-pass stream, so the first attempt consumes it and the retry would send an
        // empty or truncated body. That would turn an expired-token 401 into a confusing 400, which
        // is worse than the 401 -- so a non-replayable body is not retried.
        //
        // In practice the generated client encodes JSON into a buffer, which is `.multiple`, so the
        // common case does retry. This guard is for the ones that are not, such as an upload
        // streamed from disk.
        if let body, body.iterationBehavior != .multiple {
            return (response, responseBody)
        }

        // The token this request actually carried. Passing it on is what collapses concurrent 401s
        // into a single refresh.
        guard let fresh = await refresher.validToken(after: token) else {
            // Session genuinely over. Returning the original 401 is correct -- the UI needs to see
            // it and route to sign-in.
            return (response, responseBody)
        }

        // Retried exactly once. A second 401 with a token that was just minted is not a token
        // problem, and retrying again would be a loop.
        var retried = request
        retried.headerFields[.authorization] = "Bearer \(fresh)"
        return try await next(retried, body, baseURL)
    }
}
