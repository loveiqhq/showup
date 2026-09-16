import XCTest
import ShowUpAPI
@testable import ShowUpWelcome

/// Proves the APP TARGET can reach and use the generated client.
///
/// The package has its own tests, and they pass. This is a different claim: that the local package
/// reference in project.pbxproj resolves, the OpenAPI build plugin runs as part of an Xcode build,
/// and the module links into this app. A green `swift build` says nothing about any of that.
///
/// It is small on purpose. No screen is connected to an endpoint here, and nothing hits the
/// network -- the point is the wiring, not the behaviour.
final class APIAccessTests: XCTestCase {

    func testTheAppTargetCanReachTheGeneratedClient() {
        // If the local package were not wired into the target, this line would not compile -- which
        // is the assertion. Everything below is a bonus.
        XCTAssertNotNil(APIAccess.client.client)
    }

    func testTheAppPointsAtTheDevelopmentBackend() {
        // Guards against the mistake this indirection exists to prevent: a build that quietly talks
        // to the wrong server. Change this test deliberately when the environment changes.
        XCTAssertEqual(
            APIEnvironment.development.baseURL.absoluteString,
            "http://localhost:3000"
        )
    }

    func testGeneratedOperationsAreCallableFromTheAppTarget() async {
        // Reaching a generated operation by name from the app target proves the plugin's output is
        // visible here, not merely inside the package. It is expected to FAIL at runtime: nothing
        // is listening on localhost during a test run, and that is fine -- a thrown transport error
        // means the call was made, encoded and dispatched.
        do {
            _ = try await APIAccess.client.client.startPhoneVerification(
                body: .json(.init(phone: "+4917612345678"))
            )
            // A success would mean something really is listening. Not a failure of this test.
        } catch {
            // Any error is acceptable. The assertion is that the line above compiles and runs.
        }
    }
}
