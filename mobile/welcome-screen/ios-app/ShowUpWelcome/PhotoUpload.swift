//
//  PhotoUpload.swift
//  ShowUp · the rules a picked photo has to satisfy before it is sent (SHOWUP-156)
//
//  The Swift port of `profile/PhotoUpload.kt`. Read that file's header for what it fixes; the
//  short version is that a full-resolution photo came back 413 and a HEIC one came back 415, and
//  both rendered as `Upload failed` with a Retry that re-sent exactly the same bytes.
//
//  The decode lives at the call site, where the platform APIs are. What lives here is the
//  arithmetic, because a rule that can be checked without a device is a rule that gets checked.
//

import UIKit

/// The longest edge an uploaded photo may have.
///
/// 2048 is generous for what a profile card ever draws — the widest phone in the fit matrix is 440
/// points, which is 1320 physical pixels at 3x — and it keeps a re-encode well inside the server's
/// 8 MB limit without being visibly soft if the photo is ever shown larger.
///
/// THE SAME NUMBER ON BOTH PLATFORMS. A photo that looked fine on one and soft on the other would
/// be a difference nobody chose.
let uploadMaxEdge = 2048

/// Visually lossless at the sizes a profile card uses, and roughly a third of the bytes of 1.0.
let uploadJPEGQuality: CGFloat = 0.9

/// The size to decode a picked photo at, or nil when it is already small enough.
///
/// NIL RATHER THAN THE ORIGINAL SIZE, so the caller can skip resizing entirely. Upscaling a small
/// photo would add bytes and no detail, which is the opposite of the point.
///
/// ASPECT RATIO IS PRESERVED and the short edge never rounds to zero: a panorama at 8000 x 200
/// scales to 2048 x 51, and a degenerate 8000 x 1 would otherwise scale to a zero-height image
/// that the encoder refuses. Both are absurd as profile photos and neither should be a crash.
func uploadTargetSize(width: Int, height: Int,
                      maxEdge: Int = uploadMaxEdge) -> (width: Int, height: Int)? {
    guard width > 0, height > 0 else { return nil }
    let longest = max(width, height)
    guard longest > maxEdge else { return nil }
    let ratio = Double(maxEdge) / Double(longest)
    return (max(1, Int(Double(width) * ratio)), max(1, Int(Double(height) * ratio)))
}
