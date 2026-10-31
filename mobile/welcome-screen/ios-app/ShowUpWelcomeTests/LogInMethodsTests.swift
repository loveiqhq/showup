//
//  LogInMethodsTests.swift
//  ShowUp · Log in offers only what can sign somebody in (SHOWUP-191 item 9, SHOWUP-145)
//
//  The Swift half of `LogInMethodsTest.kt`. Apple, Google and Facebook were drawn on Log in and did
//  nothing when tapped: none has an SDK sign-in yet (SHOWUP-144's sub-tasks), and SHOWUP-145 hides a
//  provider whose setup is not finished. The view had the switch; the flow never set it. The flow's
//  use of `SIGN_IN_READY` is in `SignUpFlow.swift`, and the fit sweep draws the result at every size.
//

import XCTest
@testable import ShowUpWelcome

final class LogInMethodsTests: XCTestCase {

    func testLogInOffersThePhoneNumberAndNothingThatCannotSignInYet() {
        XCTAssertEqual(availableMethods(LOGIN_METHODS, configured: SIGN_IN_READY), [.phone])
    }

    func testAHiddenProviderTakesItsLastUsedHintWithIt() {
        // A device that last signed in with Google is offered the phone number, and is not told
        // "Last login was via Google" above a button that is not there.
        let methods = availableMethods(LOGIN_METHODS, configured: SIGN_IN_READY)
        XCTAssertFalse(methods.contains(.google))
    }
}
