// BackgroundTaskRunner.swift
// ShowUp · run one network call with iOS's grace time, behind a protocol so the models stay pure (SHOWUP-195).
//
// An arrival commit that starts as the app goes to the background (the person tapped Close, or tapped Yes and
// pocketed the phone) can be suspended before the request leaves the device. `beginBackgroundTask` asks iOS for the
// few seconds it needs; the model asks through this protocol and a test hands it a recorder.

import UIKit

/// Runs `work` and gives it background time while it runs: begin before, end after, whichever way it finishes.
@MainActor
protocol BackgroundTaskRunning {
    func run<T: Sendable>(named name: String, _ work: @escaping @Sendable () async -> T) async -> T
}

/// The one task identifier of one run. Main-actor isolated, so the expiration handler (called on the main queue) and
/// the end after `work` share it without a lock.
@MainActor
private final class BackgroundTaskHandle {
    private var id = UIBackgroundTaskIdentifier.invalid

    func begin(_ name: String) {
        id = UIApplication.shared.beginBackgroundTask(withName: name) { [self] in
            // iOS is out of time: end the task here, before the handler returns, or the app is killed. The
            // request itself is cancelled with the model's task; the retry waits for the next foreground.
            MainActor.assumeIsolated { end() }
        }
    }

    func end() {
        guard id != .invalid else { return }
        UIApplication.shared.endBackgroundTask(id)
        id = .invalid
    }
}

/// The real thing.
@MainActor
struct UIKitBackgroundRunner: BackgroundTaskRunning {
    func run<T: Sendable>(named name: String, _ work: @escaping @Sendable () async -> T) async -> T {
        let handle = BackgroundTaskHandle()
        handle.begin(name)
        let result = await work()
        handle.end()
        return result
    }
}
