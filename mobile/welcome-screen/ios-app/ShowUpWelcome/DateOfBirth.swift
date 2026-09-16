//  DateOfBirth.swift
//  ShowUp · parsing, formatting and ageing a date of birth (SHOWUP-154)
//
//  No view in this file, on purpose. Mirrors `profile/DateOfBirth.kt` value for value — the two
//  platforms must agree on what a date means, because the server will only accept one answer.
//
//  TWO DECISIONS THAT OVERRIDE THE REFERENCE FILE
//
//  1. AGE IS COMPUTED IN UTC. The ticket and the reference JSX both say "local time, not UTC". The
//     product side ruled UTC on 10 September 2026, and it is the right call for a reason the
//     ticket could not have known: the server already computes in UTC and REJECTS an under-18 date
//     with a 400. A client computing locally would, for a user west of UTC on their eighteenth
//     birthday, show the age card and then be refused — a failure with no message designed for it.
//
//  2. THE FIELD ORDER FOLLOWS THE DEVICE LOCALE. The ticket hard-codes mm/dd/yyyy and names the
//     consequence in its own open questions: a German user typing 03/04 gets a valid WRONG date,
//     and age is locked afterwards.

import Foundation

/// Which of day and month the user's locale asks for first. The year is always last.
enum DateOrder {
    case monthFirst
    case dayFirst

    /// The placeholder and the mono chip in both error strings are generated from this.
    var pattern: String { self == .monthFirst ? "mm/dd/yyyy" : "dd/mm/yyyy" }

    /// What this device writes first.
    ///
    /// A locale that leads with the year (ja, ko) reads as month-first rather than being silently
    /// mis-parsed — recorded as a gap rather than guessed at.
    static func forCurrentLocale(_ locale: Locale = .current) -> DateOrder {
        let template = DateFormatter.dateFormat(fromTemplate: "yMd", options: 0, locale: locale) ?? "Mdy"
        for ch in template where ch == "d" || ch == "M" {
            return ch == "d" ? .dayFirst : .monthFirst
        }
        return .monthFirst
    }
}

/// What eight digits turned out to be.
enum DobResult: Equatable {
    /// Fewer than eight digits: not an answer yet, and not an error until Continue is pressed.
    case incomplete
    /// Eight digits that do not name a day that exists — 30 February, or month 13.
    case impossible
    /// A real date. `age` is whole years in UTC; `iso` is what the backend wants.
    case valid(age: Int, iso: String)
}

/// The digits behind the display string, at most eight.
func dobDigits(_ display: String) -> String {
    String(display.filter(\.isNumber).prefix(8))
}

/// Group digits for display: `03221998` -> `03/22/1998`.
///
/// Order-independent — the first two digits are whatever the locale asked for first. Only complete
/// groups get a trailing slash, so the caret never sits after a slash the user did not type.
func formatDob(_ digits: String) -> String {
    let d = Array(dobDigits(digits))
    let parts = [
        String(d.prefix(2)),
        String(d.dropFirst(2).prefix(2)),
        String(d.dropFirst(4).prefix(4)),
    ].filter { !$0.isEmpty }
    return parts.joined(separator: "/")
}

/// UTC, and only UTC — the basis the server uses and therefore the only one worth agreeing with.
///
/// A `let` built once, not a computed `var`. Swift 6 rejects unisolated mutable global state and
/// `audit/check-swift-concurrency.py` enforces that ahead of the compiler; a computed property
/// reads as one at file scope even though it stores nothing. `Calendar` is a Sendable value type,
/// so an immutable global is safe to share.
let utcCalendar: Calendar = {
    var c = Calendar(identifier: .gregorian)
    c.timeZone = TimeZone(identifier: "UTC")!
    return c
}()

/// The minimum the product allows, and the same number `profiles.service.ts` refuses below.
let minimumAge = 18

/// Read eight digits as a date, in the order this locale asks for them.
///
/// VALIDITY IS A ROUND TRIP, NOT A PATTERN. A regex accepts 02/30/1990 and a calendar quietly
/// turns it into 2 March, which would lock a user to a birthday they never typed. So the date is
/// built and then asked what it became: if it does not report back the same three numbers, the
/// day did not exist.
func parseDob(_ display: String, order: DateOrder, today: Date = Date()) -> DobResult {
    let d = Array(dobDigits(display))
    guard d.count == 8 else { return .incomplete }

    let first = Int(String(d.prefix(2)))!
    let second = Int(String(d.dropFirst(2).prefix(2)))!
    let year = Int(String(d.dropFirst(4).prefix(4)))!
    let month = order == .monthFirst ? first : second
    let day = order == .monthFirst ? second : first

    let cal = utcCalendar
    let nowParts = cal.dateComponents([.year, .month, .day], from: today)

    // Cheap range guard first, so the calendar is never asked about month 47.
    guard (1...12).contains(month), (1...31).contains(day),
          year >= 1900, year <= (nowParts.year ?? 0) else { return .impossible }

    var comps = DateComponents()
    comps.year = year; comps.month = month; comps.day = day
    guard let built = cal.date(from: comps) else { return .impossible }
    let back = cal.dateComponents([.year, .month, .day], from: built)
    guard back.year == year, back.month == month, back.day == day else { return .impossible }

    return .valid(age: ageOn(today: today, year: year, month: month, day: day),
                  iso: String(format: "%04d-%02d-%02d", year, month, day))
}

/// Whole years, decremented when this year's birthday has not arrived.
///
/// Mirrors the server's `ageFromDateOfBirth` exactly, including its basis. 29 February needs no
/// special case — a leap-day birthday "arrives" on 1 March in a common year, because 2 < 29
/// compares as not-yet.
func ageOn(today: Date, year: Int, month: Int, day: Int) -> Int {
    let parts = utcCalendar.dateComponents([.year, .month, .day], from: today)
    var age = (parts.year ?? 0) - year
    let monthNow = parts.month ?? 1
    let dayNow = parts.day ?? 1
    if monthNow < month || (monthNow == month && dayNow < day) { age -= 1 }
    return age
}
