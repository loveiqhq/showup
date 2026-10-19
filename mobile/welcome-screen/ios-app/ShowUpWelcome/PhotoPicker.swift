//
//  PhotoPicker.swift
//  ShowUp · the two OS surfaces the photo step launches (SHOWUP-156)
//
//  WHY THERE ARE UIViewRepresentABLES IN HERE, which the iOS CLAUDE.md requires a written reason
//  for: neither of these has a SwiftUI equivalent that does what the ticket requires.
//
//  `PHPickerViewController` is the permission-free system picker, and presenting it is the build
//  constraint the whole ticket rests on — SwiftUI's `.photosPicker` wraps the same controller but
//  hands back a `PhotosPickerItem`, which is a PhotoKit handle rather than bytes, and reading one
//  goes back through the same async transfer this does. Using it would add a dependency on
//  PhotosUI's SwiftUI layer for no saving.
//
//  `UIImagePickerController` is the camera. SwiftUI has no camera at all, at any level, so there is
//  nothing to compare it with.
//
//  WHAT IS NOT OURS, and is not drawn, measured or specced anywhere in this app: the picker's own
//  UI, the camera UI, the permission alert and the Settings app. We choose to present them; we do
//  not choose what they look like.
//

import ImageIO
import PhotosUI
import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// The system photo picker. NO PERMISSION IS REQUESTED, and none is declared in the Info.plist.
///
/// It runs out of process, shows the user their whole library, and returns only what they chose —
/// which is why iOS has no library access card, no limited-access state, and no partial-library
/// banner anywhere in this build.
struct SystemPhotoPicker: UIViewControllerRepresentable {
    /// Called with the picked image's bytes, or with nil if the sheet was dismissed.
    let onPicked: (PickedBytes?) -> Void

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var config = PHPickerConfiguration()
        // One at a time: each photo lands in the slot the user tapped, and a multi-select would
        // have to decide where the other four go.
        config.selectionLimit = 1
        config.filter = .images
        let controller = PHPickerViewController(configuration: config)
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ controller: PHPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onPicked: onPicked) }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        private let onPicked: (PickedBytes?) -> Void

        init(onPicked: @escaping (PickedBytes?) -> Void) { self.onPicked = onPicked }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            guard let provider = results.first?.itemProvider else {
                onPicked(nil)
                return
            }
            // THE ORIGINAL BYTES, NOT A DECODED IMAGE. `loadObject(ofClass: UIImage.self)` hands
            // back a fully decoded photo — around 48 MB for a 12 megapixel shot — and this screen
            // can have six in flight. Asking for the data lets `downscaledUploadJPEG` decode once,
            // already downscaled, and never hold the full-resolution pixels at all.
            //
            // JPEG on the way out whatever came in: a modern iPhone photo is HEIC, and the
            // server's allowed types are jpeg, png and webp.
            provider.loadDataRepresentation(forTypeIdentifier: UTType.image.identifier) {
                [onPicked] original, _ in
                guard let original, let data = downscaledUploadJPEG(from: original) else {
                    Task { @MainActor in onPicked(nil) }
                    return
                }
                Task { @MainActor in
                    onPicked(PickedBytes(bytes: data, mimeType: "image/jpeg",
                                         // A name the server can log and a person can recognise.
                                         // NEVER the library's own display name: that is the
                                         // user's filename and is not ours to send.
                                         fileName: "photo.jpeg"))
                }
            }
        }
    }
}

/// The camera. THE ONE PERMISSION THAT ALWAYS APPLIES.
///
/// The OS asks at the moment of use and asks ONCE, ever — which is why `PhotoAccess` distinguishes
/// `.canAsk` from `.blocked` and the source sheet's camera row changes rather than the screen.
struct SystemCameraPicker: UIViewControllerRepresentable {
    let onPicked: (PickedBytes?) -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let controller = UIImagePickerController()
        // Guarded: the simulator has no camera, and presenting `.camera` where there is none is a
        // crash rather than an empty screen.
        controller.sourceType = UIImagePickerController.isSourceTypeAvailable(.camera)
            ? .camera
            : .photoLibrary
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onPicked: onPicked) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate,
                             UINavigationControllerDelegate {
        private let onPicked: (PickedBytes?) -> Void

        init(onPicked: @escaping (PickedBytes?) -> Void) { self.onPicked = onPicked }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            // Downscaled for the same reason as the library path: a capture off a modern
            // camera is comfortably past the server's 8 MB limit at full resolution, and a 413 is
            // a failed slot whose Retry re-sends exactly the same too-large bytes.
            guard let image = info[.originalImage] as? UIImage,
                  let data = downscaledUploadJPEG(from: image) else {
                onPicked(nil)
                return
            }
            onPicked(PickedBytes(bytes: data, mimeType: "image/jpeg", fileName: "photo.jpeg"))
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onPicked(nil)
        }
    }
}

/// Turns a picked image's original bytes into a downscaled JPEG the server will accept.
///
/// ─────────────────────────────────────────────────────────────────────────────
/// WHY THIS DECODES THROUGH ImageIO RATHER THAN UIImage
/// ─────────────────────────────────────────────────────────────────────────────
///
/// `UIImage(data:)` decodes the whole photo before anything can be done with it: a 12 megapixel
/// shot is about 48 MB of pixels, held while it is re-encoded, and this screen can have six of
/// them in flight. `CGImageSourceCreateThumbnailAtIndex` reads the header, decodes ONCE at the
/// size asked for, and never materialises the full-resolution image at all.
///
/// `kCGImageSourceCreateThumbnailWithTransform` applies the EXIF orientation while it does so,
/// which is not optional here: re-encoding drops the orientation tag along with the container, so
/// without it every photo taken in portrait would upload on its side.
///
/// `...FromImageAlways` because many photos carry an embedded thumbnail of their own, a couple of
/// hundred pixels wide, and returning that instead of the photo is the one failure mode of this
/// API that looks like it worked.
///
/// A smaller image is returned as it is: `MaxPixelSize` is a ceiling, not a target, and upscaling
/// a small photo would add bytes and no detail.
func downscaledUploadJPEG(from data: Data) -> Data? {
    guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
    let options: [CFString: Any] = [
        kCGImageSourceCreateThumbnailFromImageAlways: true,
        kCGImageSourceCreateThumbnailWithTransform: true,
        kCGImageSourceThumbnailMaxPixelSize: uploadMaxEdge,
    ]
    guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary)
    else { return nil }
    return UIImage(cgImage: cgImage).jpegData(compressionQuality: uploadJPEGQuality)
}

/// The same, for a `UIImage` that never had a file behind it — a camera capture.
///
/// `UIGraphicsImageRenderer` draws into a format that matches the image rather than the screen, so
/// there is no second copy at device scale. The image arrives already oriented from the camera, so
/// there is no transform to apply here.
func downscaledUploadJPEG(from image: UIImage) -> Data? {
    // The arithmetic is in `uploadTargetSize`, where an XCTest can reach it, and it is the same
    // function the Kotlin side calls. Nil means the photo is already small enough.
    guard let size = uploadTargetSize(width: Int(image.size.width.rounded()),
                                      height: Int(image.size.height.rounded())) else {
        return image.jpegData(compressionQuality: uploadJPEGQuality)
    }
    let target = CGSize(width: size.width, height: size.height)
    let format = UIGraphicsImageRendererFormat.default()
    format.scale = 1
    format.opaque = true
    let resized = UIGraphicsImageRenderer(size: target, format: format).image { _ in
        image.draw(in: CGRect(origin: .zero, size: target))
    }
    return resized.jpegData(compressionQuality: uploadJPEGQuality)
}

/// Opens this app's settings page.
///
/// THE APP'S PAGE, NOT A PERMISSION TOGGLE. Neither platform can deep-link to a single row, which
/// is exactly why the blocked copy NAMES the row the user has to find — the copy and this function
/// are two halves of one decision.
@MainActor
func openAppSettings() {
    guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
    UIApplication.shared.open(url)
}
