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

import PhotosUI
import SwiftUI
import UIKit

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
            guard let provider = results.first?.itemProvider,
                  provider.canLoadObject(ofClass: UIImage.self) else {
                onPicked(nil)
                return
            }
            provider.loadObject(ofClass: UIImage.self) { [onPicked] object, _ in
                // JPEG at 0.9 rather than the original bytes: a modern iPhone photo is HEIC, which
                // the backend's own pipeline does not commit to accepting, and re-encoding here is
                // one line against a conversion step nobody has written. Quality 0.9 is visually
                // lossless at the sizes a profile card uses.
                guard let image = object as? UIImage,
                      let data = image.jpegData(compressionQuality: 0.9) else {
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
            guard let image = info[.originalImage] as? UIImage,
                  let data = image.jpegData(compressionQuality: 0.9) else {
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
