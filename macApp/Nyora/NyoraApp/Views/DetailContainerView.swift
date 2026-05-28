import SwiftUI

struct DetailContainerView: View {
    let destination: NavDestination

    var body: some View {
        Group {
            switch destination {
            case .history: HistoryView()
            case .favourites: FavouritesView()
            case .explore: ExploreView()
            case .feed: FeedView()
            case .local: LocalView()
            case .suggestions: SuggestionsView()
            case .bookmarks: BookmarksView()
            case .updates: UpdatesView()
            case .reader: ReaderView()
            case .settings: SettingsView()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
