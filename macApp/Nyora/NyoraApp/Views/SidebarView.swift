import SwiftUI

enum NavDestination: String, CaseIterable, Identifiable, Hashable {
    case history
    case favourites
    case explore
    case feed
    case local
    case suggestions
    case bookmarks
    case updates
    case reader
    case settings

    var id: String { rawValue }

    var title: String {
        switch self {
        case .history: return "History"
        case .favourites: return "Favourites"
        case .explore: return "Explore"
        case .feed: return "Feed"
        case .local: return "Local"
        case .suggestions: return "Suggestions"
        case .bookmarks: return "Bookmarks"
        case .updates: return "Updated"
        case .reader: return "Reader"
        case .settings: return "Settings"
        }
    }

    var systemImage: String {
        switch self {
        case .history: return "clock.arrow.circlepath"
        case .favourites: return "heart.text.square"
        case .explore: return "safari"
        case .feed: return "newspaper"
        case .local: return "folder"
        case .suggestions: return "sparkles"
        case .bookmarks: return "bookmark"
        case .updates: return "arrow.triangle.2.circlepath"
        case .reader: return "book.pages"
        case .settings: return "gearshape"
        }
    }
}

struct SidebarView: View {
    @Binding var selection: NavDestination
    @EnvironmentObject var appState: AppState

    var body: some View {
        List(selection: $selection) {
            Section("Library") {
                row(.history)
                row(.favourites)
                row(.local)
                row(.bookmarks)
            }
            Section("Discover") {
                row(.explore)
                row(.suggestions)
                row(.feed)
                row(.updates)
            }
            Section("Reading") {
                row(.reader)
            }
            Section("App") {
                row(.settings)
            }
        }
        .listStyle(.sidebar)
        .navigationTitle("Nyora")
    }

    private func row(_ destination: NavDestination) -> some View {
        Label(destination.title, systemImage: destination.systemImage)
            .tag(destination)
    }
}
