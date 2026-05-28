import SwiftUI
import AppKit

@main
struct NyoraApp: App {
    @StateObject private var appState = AppState()
    @NSApplicationDelegateAdaptor(NyoraAppDelegate.self) private var appDelegate

    init() {
        // Wire the delegate to the appState so it can shut down the helper on quit.
        // (The adaptor instance and our @StateObject are separate; we lift the
        // reference through a static so termination has access without ordering issues.)
        NyoraAppDelegate.shutdownHook = nil // reset between hot reloads
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(appState)
                .frame(minWidth: 1100, minHeight: 720)
                .task {
                    NyoraAppDelegate.shutdownHook = { [appState] in
                        await appState.shutdownHelper()
                    }
                    await appState.bootstrap()
                }
        }
        .windowStyle(.titleBar)
        .windowToolbarStyle(.unified(showsTitle: true))
        .commands {
            CommandGroup(after: .newItem) {
                Button("Refresh Sources") {
                    Task { await appState.refreshSources() }
                }
                .keyboardShortcut("r")
            }
        }
    }
}

final class NyoraAppDelegate: NSObject, NSApplicationDelegate {
    nonisolated(unsafe) static var shutdownHook: (() async -> Void)?

    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        guard let hook = NyoraAppDelegate.shutdownHook else { return .terminateNow }
        NyoraAppDelegate.shutdownHook = nil
        Task.detached {
            await hook()
            await MainActor.run { NSApplication.shared.reply(toApplicationShouldTerminate: true) }
        }
        return .terminateLater
    }
}
