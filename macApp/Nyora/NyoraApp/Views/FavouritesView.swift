import SwiftUI

struct FavouritesView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        Group {
            if appState.favourites.isEmpty {
                EmptyStateView(
                    icon: "heart",
                    title: "No favourites yet",
                    message: "Tap the heart on any manga's details to add it here."
                )
            } else {
                grid
            }
        }
        .toolbar {
            ToolbarItem {
                TextField("Search favourites", text: $appState.libraryQuery)
                    .textFieldStyle(.roundedBorder)
                    .frame(width: 200)
            }
            ToolbarItem {
                Button {
                    Task { await appState.reloadFavourites() }
                } label: { Label("Refresh", systemImage: "arrow.clockwise") }
            }
        }
    }

    private var filtered: [HelperManga] {
        let q = appState.libraryQuery.trimmingCharacters(in: .whitespaces).lowercased()
        guard !q.isEmpty else { return appState.favourites }
        return appState.favourites.filter { $0.title.lowercased().contains(q) }
    }

    private var grid: some View {
        ScrollView {
            LazyVGrid(columns: [.init(.adaptive(minimum: 160), spacing: 16)], spacing: 16) {
                ForEach(filtered, id: \.id) { manga in
                    FavouriteCard(manga: manga)
                }
            }
            .padding(20)
        }
    }

}

struct FavouriteCard: View {
    let manga: HelperManga

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            AsyncImage(url: URL(string: manga.coverUrl)) { phase in
                switch phase {
                case let .success(image):
                    image.resizable().scaledToFill()
                default:
                    Rectangle().fill(LinearGradient(
                        colors: [Color.accentColor.opacity(0.6), Color.purple.opacity(0.4)],
                        startPoint: .topLeading, endPoint: .bottomTrailing
                    ))
                    .overlay(Text(manga.title).foregroundStyle(.white).padding(8))
                }
            }
            .aspectRatio(0.72, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 10))

            Text(manga.title).font(.headline).lineLimit(2)
            if !manga.authors.isEmpty {
                Text(manga.authors.joined(separator: ", "))
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding(10)
        .background(RoundedRectangle(cornerRadius: 14).fill(.background.opacity(0.6)))
    }
}

struct CoverPlaceholder: View {
    let title: String
    let accent: Color

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(
                colors: [accent.opacity(0.95), Color.purple.opacity(0.4), Color.black.opacity(0.6)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            Text(title)
                .font(.title3.bold())
                .foregroundStyle(.white)
                .padding(10)
                .lineLimit(3)
        }
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

