import SwiftUI

struct RootView: View {
    @EnvironmentObject var appState: AppState
    @State private var selectedDestination: NavDestination = .history

    var body: some View {
        NavigationSplitView {
            SidebarView(selection: $selectedDestination)
                .navigationSplitViewColumnWidth(min: 200, ideal: 220, max: 260)
        } detail: {
            DetailContainerView(destination: selectedDestination)
                .navigationSplitViewColumnWidth(min: 700, ideal: 1000)
        }
        .navigationTitle(selectedDestination.title)
        .overlay(alignment: .bottom) {
            if let message = appState.statusMessage {
                StatusBanner(message: message) {
                    appState.clearMessage()
                }
                .padding()
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut, value: appState.statusMessage)
        .onChange(of: appState.pendingNavigation) { _, new in
            if let new {
                selectedDestination = new
                _ = appState.consumeNavigation()
            }
        }
        .sheet(isPresented: $appState.isCatalogPresented) {
            CatalogSheet()
                .environmentObject(appState)
        }
    }
}

struct StatusBanner: View {
    let message: String
    let onDismiss: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "info.circle")
            Text(message).lineLimit(2)
            Spacer()
            Button(action: onDismiss) { Image(systemName: "xmark") }
                .buttonStyle(.borderless)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 14))
        .frame(maxWidth: 720)
    }
}
