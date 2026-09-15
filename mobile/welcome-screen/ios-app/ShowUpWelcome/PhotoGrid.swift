//
//  PhotoGrid.swift
//  ShowUp · what the photo step knows, with no UI attached (SHOWUP-156)
//
//  The Swift port of `profile/PhotoGrid.kt`. Read that file's header for the argument.
//
//  THE ONE RULE MOST OF THIS FILE ENFORCES: the count only advances on a CONFIRMED upload. A photo
//  that has been picked but not yet stored has not been added, and one whose upload failed never
//  was. The ticket's own artboard is the test case — three slots look occupied, the count reads
//  "1 of 6", and Continue still refuses.
//

import Foundation

/// Where a photo came from. `source` on `photo_added`, from the registry's closed pair.
enum PhotoSource: String {
    case library
    case camera

    /// The registry value. Lower-case, and never the case name by accident — `rawValue` is it.
    var trackingValue: String { rawValue }
}

/// How far a picked photo has got.
///
/// Three states and not two, because the gap between picking and storing is ours to explain. A
/// binary "has a photo / does not" would draw an in-flight upload as an empty slot, so the user
/// would not know which photo was being uploaded, or that anything was.
enum UploadStatus {
    /// Stored. The only status that counts towards the requirement.
    case confirmed
    /// Picked and on its way. Occupies its slot, shows the picked image dimmed under a ring.
    case inFlight
    /// The upload came back an error. Keeps its slot so Retry re-uploads without re-picking.
    case failed
}

/// One photo the user has chosen, wherever it has got to.
///
/// `localId` is a client-side identity that survives the whole lifecycle. The server's id does not
/// exist until the upload is confirmed, so keying a slot on it would mean an in-flight photo had
/// no stable identity and a re-order mid-upload could move the ring onto a different picture.
struct PickedPhoto: Identifiable, Equatable {
    let localId: Int64
    var uri: String?
    var status: UploadStatus
    var remoteId: String? = nil
    /// 0…1 while in flight. A REAL value: the ring is determinate because an indeterminate spinner
    /// on a large upload says nothing about whether it is moving.
    var progress: Double = 0

    var id: Int64 { localId }

    static func == (a: PickedPhoto, b: PickedPhoto) -> Bool {
        a.localId == b.localId && a.uri == b.uri && a.remoteId == b.remoteId
            && a.progress == b.progress && a.status == b.status
    }
}

extension UploadStatus: Equatable {}

/// Four required, six maximum. Stated in three places on the screen, on purpose.
let photosRequired = 4

/// The most a profile can carry.
let photosMax = 6

/// Every slot is this tall, in every state — filled, empty, uploading, failed.
///
/// That single number is what stops the grid reflowing mid-upload and keeps the CTA reachable at
/// the same scroll offset. A slot that sized itself to its content would change height the moment
/// an upload started, and every row below it would move under the user's finger.
let photoSlotHeight: CGFloat = 158

/// The state of the photo step.
struct PhotoGridState: Equatable {
    /// In slot order. Index 0 is the main photo.
    var photos: [PickedPhoto] = []
    /// Whether slots 5 and 6 are on screen. They are never on first paint.
    var optionalRevealed = false
    /// Whether `photos_minimum_met` has already fired. Once per build, on the first crossing.
    var minimumReported = false

    /// How many are stored. The number the screen shows and the only one Continue reads.
    var confirmedCount: Int { photos.filter { $0.status == .confirmed }.count }

    /// Whether the step is satisfied. In-flight and failed photos do not count.
    var meetsMinimum: Bool { confirmedCount >= photosRequired }

    /// Whether the drag hint is shown. From the SECOND photo: with one there is nothing to reorder.
    var canReorder: Bool { confirmedCount >= 2 }

    /// What occupies a slot, or nil if it is empty.
    func at(_ index: Int) -> PickedPhoto? {
        index >= 0 && index < photos.count ? photos[index] : nil
    }

    /// Whether this upload is the one that crosses the line.
    ///
    /// Asked BEFORE the state is updated, which is why it takes the count it is about to become.
    func crossesMinimum(nextConfirmed: Int) -> Bool {
        !minimumReported && nextConfirmed >= photosRequired
    }
}

/// Slots 5 and 6 are the optional ones.
func isOptionalSlot(_ index: Int) -> Bool { index >= photosRequired }

/// What an empty slot asks for.
///
/// The four required slots each name a KIND of photo, because "add a photo" four times gets four
/// versions of the same photo. Slot 1 on a completely empty grid is the exception: it drops its
/// category for `Start with your face`, the one instruction that gets somebody started.
func slotHint(index: Int, confirmedCount: Int) -> String {
    if confirmedCount == 0 && index == 0 { return PhotosCopy.firstSlotCtaHint }
    return index < PhotosCopy.slotHints.count
        ? PhotosCopy.slotHints[index]
        : (PhotosCopy.slotHints.last ?? "")
}

/// What `action` a slot tap reports.
///
/// "replace" and "add" are told apart by whether the slot already holds something — a distinction
/// that exists because a filled slot IS tappable, and a user replacing their main photo is doing
/// something different from one adding their fourth.
func slotTapAction(occupied: Bool) -> String { occupied ? "replace" : "add" }

// MARK: - drag to reorder
//
// THE ARITHMETIC IS HERE, NOT IN THE VIEW, and it is only possible because every slot is exactly
// `photoSlotHeight` tall in every state. A grid whose rows resized with their contents would have
// no closed form for "which slot is under the finger" — it would need a position map rebuilt on
// every measurement, and the answer would change mid-drag when an upload finished.

/// Which slot a drag has landed on.
///
/// Returns `from` when the finger has not travelled far enough to mean anything, or when the
/// computed target is outside the grid. Never returns an index a photo cannot be dropped on.
func reorderTarget(
    from: Int,
    dx: CGFloat,
    dy: CGFloat,
    cellWidth: CGFloat,
    cellHeight: CGFloat,
    gap: CGFloat,
    count: Int
) -> Int {
    guard cellWidth > 0, cellHeight > 0 else { return from }
    let fromColumn = from % 2
    let fromRow = from / 2
    // Rounded, not truncated: the target changes when the dragged slot's CENTRE crosses into the
    // next cell, which is what the eye expects.
    let column = fromColumn + Int((dx / (cellWidth + gap)).rounded())
    let row = fromRow + Int((dy / (cellHeight + gap)).rounded())
    guard column >= 0, column <= 1 else { return from }
    let target = row * 2 + column
    return (target >= 0 && target < count) ? target : from
}

/// Moves one entry, keeping everything else in order.
///
/// A remove-then-insert rather than a swap. Swapping would mean dragging the fourth photo to the
/// front pushed the FIRST one to position four, which is not what a drag looks like: the user is
/// inserting, and everything between the two positions shifts by one.
///
/// REORDERING INTO POSITION 0 IS HOW THE MAIN PHOTO CHANGES. There is no separate "make main"
/// control and there does not need to be — "the first one is your main photo" is the rule the hint
/// under the grid states, so moving a photo there IS the gesture.
func moved<T>(_ list: [T], from: Int, to: Int) -> [T] {
    guard from != to, list.indices.contains(from), list.indices.contains(to) else { return list }
    var out = list
    let item = out.remove(at: from)
    out.insert(item, at: to)
    return out
}
