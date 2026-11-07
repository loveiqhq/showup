// DateRules.swift
// ShowUp · the Date screen's rules, as plain functions (SHOWUP-193)
//
// Which state the screen is in, the minutes every event carries, where a launch opens, and every
// string built from values. No view, so each is tested without one (`DateRulesTests`); Android has
// the same functions under the same names (`DateRules.kt`, `DateRouting.kt`).

import Foundation

/// The three time states. D, the full scroll, is not a state -- it is the same screen, scrolled.
///
/// UNREAD ONLY ONCE OPEN. A message cannot arrive before the chat opens, and if one somehow had, the
/// locked card has nothing to hoist: it is not tappable and has no reply.
enum DateState: Equatable, Sendable { case locked, available, unread }

/// `coordinateOpen` is the server's word at the last read: the device clock flips the card at
/// `unlockAt`, and the foreground re-read corrects a clock that is behind (spec section 3).
func dateState(now: Date, unlockAt: Date, unread: DateUnread?, coordinateOpen: Bool = false) -> DateState {
    if !coordinateOpen && now < unlockAt { return .locked }
    return unread != nil ? .unread : .available
}

/// Whole minutes from `now` to the latest agreed meet time, negative after it, rounded toward zero --
/// the server's `minutesBeforeStart`, so the client's and the server's events agree.
func minutesBeforeStart(meetAt: Date, now: Date) -> Int {
    let seconds = Int(meetAt.timeIntervalSince1970.rounded(.down)) - Int(now.timeIntervalSince1970.rounded(.down))
    return seconds / 60
}

/// `Messaging available from 17:30`, or `… tomorrow from …` "if the date is tomorrow" -- by `meetDay`,
/// the date's own day that the headline shows, so the two never disagree.
func lockedLine(meetDay: String, unlockAt: Date, zone: TimeZone) -> String {
    let time = meetTime(unlockAt, zone: zone)
    return meetDay == "tomorrow" ? DateCopy.coordLockedLineTomorrow(time) : DateCopy.coordLockedLine(time)
}

/// `{place}, {address}` -- what Copy writes, and where Calendar says the event is.
func placeText(_ place: DatePlace) -> String {
    place.address.map { "\(place.name), \($0)" } ?? place.name
}

/// The Share Date sentence. Without a place it ends at the time.
func shareText(_ date: ActiveDate, zone: TimeZone) -> String {
    let time = meetTime(date.meetAt, zone: zone)
    if let place = date.place {
        return DateCopy.shareText(date.match.name, date.meetDay, time, placeText(place))
    }
    return DateCopy.shareTextNoPlace(date.match.name, date.meetDay, time)
}

/// What the OS calendar sheet is pre-filled with. One hour, as the ticket says.
struct CalendarEvent: Equatable, Sendable {
    let title: String
    let start: Date
    let end: Date
    let location: String?
}

func calendarEvent(_ date: ActiveDate) -> CalendarEvent {
    CalendarEvent(
        title: DateCopy.calendarTitle(date.match.name),
        start: date.meetAt,
        end: date.meetAt.addingTimeInterval(3600),
        location: date.place.map(placeText))
}

/// The unread card's `{time}`: `now` within the minute, then the clock time it was sent.
func unreadTime(sentAt: Date, now: Date, zone: TimeZone) -> String {
    now.timeIntervalSince(sentAt) < 60 ? DateCopy.now : meetTime(sentAt, zone: zone)
}

/// `New message` for one, `{n} new` for more.
func unreadTag(_ count: Int) -> String { count == 1 ? DateCopy.unreadOne : DateCopy.unreadMany(count) }

/// About's line: `{area} · {distance} away`. The ticket's `{job}` has no source -- no profile question
/// asks for one -- so the line starts at the area; whatever is missing is left out, never a placeholder.
func aboutLine(district: String?, distanceKm: Int?) -> String? {
    let parts = [district, distanceKm.map { "\(searchDistance($0)) away" }].compactMap { $0 }
    return parts.isEmpty ? nil : parts.joined(separator: " · ")
}

/// Only what matches is shown: the intent line only when both motivations are the same.
func intentShared(_ you: String?, _ them: String?) -> Bool { you != nil && you == them }

/// ⑬'s chips in order -- the shared motivation, then the shared interests -- each with the icon the sheet
/// draws: the kit's `heart` on the motivation; none on an interest, whose reference names (`reading`,
/// `brunch`, `hiking`, `festivals`) are not in the kit's icon set and render nothing.
struct CommonChip: Equatable, Sendable {
    let label: String
    let icon: BrandIcon?
}

func commonChips(_ date: ActiveDate) -> [CommonChip] {
    var chips: [CommonChip] = []
    if intentShared(date.motivationYou, date.motivationThem) {
        chips.append(CommonChip(label: motivationOf(date.motivationThem)?.title ?? date.motivationThem ?? "", icon: .heart))
    }
    chips += date.sharedInterests.map { CommonChip(label: interestLabel($0) ?? $0, icon: nil) }
    return chips
}

/// ⑩ `You both want`: the icon the reference gives a vibe's chip. It names four -- `coffee`,
/// `navigation`, `flame`, and `reading` for Deep conversation, which the kit does not have and so
/// draws nothing -- and no other vibe has one. No icon is invented for the rest.
func vibeIcon(_ id: String) -> BrandIcon? {
    switch id {
    case "coffee_chat": return .coffee
    case "nature_walk": return .navigation
    case "playful": return .flame
    default: return nil
    }
}

/// How the date works, row 1: the agreed place and time, or the time alone with no place.
func worksMeetLine(_ date: ActiveDate, zone: TimeZone) -> String {
    let time = meetTime(date.meetAt, zone: zone)
    return date.place.map { DateCopy.worksMeet($0.name, time) } ?? DateCopy.worksMeetNoPlace(time)
}

/// The time + place card's headline, split so the day can be set italic and violet.
func meetingHeadline(name: String, day: String) -> (lead: String, day: String) { (DateCopy.meetingLead(name), day) }

// MARK: - routing

/// Where a finished profile opens: the Date screen while a date is active, else the Search tab's rule.
///
/// "Signed in → profile complete → active date? → Date screen → otherwise the existing Check-In / Search
/// resume." The date wins over the Check-In gate because an active date blocks Search anyway (the
/// server's `dateLocksUser`). A date read that failed is no date: the launch never opens on a guess.
func launchTarget(date: Result<DateRead, CallFailure>, checkInActive: Bool?) -> FlowScreen {
    if activeDate(date) != nil { return .date }
    return checkInActive == true ? .checkInSearch : .checkInGate
}

/// The date this read found, or nil for none and for a failed read.
func activeDate(_ read: Result<DateRead, CallFailure>) -> ActiveDate? {
    if case .success(.active(let date)) = read { return date }
    return nil
}

/// `s` with a zero-width space after each hyphen, so a long hyphenated name or district may break after
/// its hyphen, as CSS breaks it -- Android's twin (`DateRules.kt`), where the line breaker would not
/// (the 4 Nov 2026 device audit). Invisible, and silent to VoiceOver.
func breakAfterHyphens(_ s: String) -> String {
    s.replacingOccurrences(of: "-(?!\u{200B})", with: "-\u{200B}", options: .regularExpression)
}

/// `date` with its match's name and district breakable after their hyphens (`breakAfterHyphens`).
func withBreakableNames(_ date: ActiveDate) -> ActiveDate {
    var shown = date
    shown.match.name = breakAfterHyphens(date.match.name)
    shown.match.district = date.match.district.map(breakAfterHyphens)
    return shown
}
