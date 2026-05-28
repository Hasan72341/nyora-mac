import SwiftUI

struct ExploreView: View {
    @EnvironmentObject var appState: AppState
    @State private var query: String = ""

    var body: some View {
        HSplitView {
            sourceList
                .frame(minWidth: 280, idealWidth: 320, maxWidth: 380)

            VSplitView {
                results
                    .frame(minHeight: 240)
                detailsPane
                    .frame(minHeight: 200)
            }
            .frame(minWidth: 500)
        }
        .toolbar {
            ToolbarItemGroup {
                Button {
                    Task { await appState.openCatalog() }
                } label: {
                    Label("Add Sources", systemImage: "plus.circle")
                }
                Button {
                    Task { await appState.reloadCatalog() }
                } label: {
                    Label("Refresh Catalog", systemImage: "arrow.clockwise")
                }
                Button {
                    Task { await appState.searchActiveSource(query: query) }
                } label: {
                    Label("Search", systemImage: "magnifyingglass")
                }
                .disabled(appState.selectedSourceId == nil)
            }
        }
    }

    private var sourceList: some View {
        VStack(spacing: 0) {
            HStack {
                Text("Sources").font(.title3.bold())
                Spacer()
                if appState.isLoading {
                    ProgressView().controlSize(.small)
                }
            }
            .padding(.horizontal, 18)
            .padding(.top, 16)

            if appState.sources.isEmpty {
                EmptyStateView(
                    icon: "puzzlepiece.extension",
                    title: "No sources yet",
                    message: "Tap Refresh Catalog to pull from configured repos."
                )
            } else {
                List(appState.sources, selection: Binding(
                    get: { appState.selectedSourceId },
                    set: { appState.selectedSourceId = $0 }
                )) { source in
                    SourceRow(source: source,
                              onInstall: { Task { await appState.install(source) } },
                              onUninstall: { Task { await appState.uninstall(source) } })
                        .tag(source.id)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            appState.selectedSourceId = source.id
                            if source.isInstalled {
                                Task { await appState.loadPopular(sourceId: source.id) }
                            }
                        }
                }
                .listStyle(.inset)
            }
        }
    }

    private var results: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                TextField("Search the active source", text: $query)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit {
                        Task { await appState.searchActiveSource(query: query) }
                    }
                Button("Popular") {
                    if let sid = appState.selectedSourceId {
                        Task { await appState.loadPopular(sourceId: sid) }
                    }
                }
                .disabled(appState.selectedSourceId == nil)
            }
            .padding([.top, .horizontal], 18)

            if appState.browseMangas.isEmpty {
                EmptyStateView(
                    icon: "safari",
                    title: appState.selectedSourceId == nil ? "Pick a source" : "Browse a source",
                    message: "Install an extension and tap a source to load popular manga."
                )
            } else {
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(appState.browseMangas) { manga in
                            BrowseResultRow(manga: manga) {
                                Task { await appState.openDetails(manga) }
                            }
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.bottom, 18)
                }
            }
        }
    }

    private var detailsPane: some View {
        Group {
            if let details = appState.activeMangaDetails {
                DetailsContent(details: details) { chapter in
                    Task { await appState.openChapter(chapter) }
                }
            } else {
                EmptyStateView(
                    icon: "book",
                    title: "Pick a manga to see details",
                    message: "Tap any result above to load its chapter list."
                )
            }
        }
    }
}

struct SourceRow: View {
    let source: SourceSummary
    var onInstall: (() -> Void)?
    var onUninstall: (() -> Void)?

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle().fill(Color.accentColor.opacity(0.18))
                Image(systemName: "puzzlepiece.extension").foregroundStyle(.tint)
            }
            .frame(width: 36, height: 36)
            VStack(alignment: .leading) {
                Text(source.name).font(.headline)
                Text("\(source.lang.uppercased()) • \(source.engine)")
                    .font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            if source.isInstalled {
                Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                Menu {
                    if let onUninstall {
                        Button("Uninstall", role: .destructive, action: onUninstall)
                    }
                } label: { Image(systemName: "ellipsis.circle") }
                .menuStyle(.borderlessButton)
            } else if let onInstall {
                Button("Install", action: onInstall)
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)
            }
        }
        .padding(.vertical, 4)
    }
}

struct BrowseResultRow: View {
    let manga: MangaSummary
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .top, spacing: 12) {
                CoverPlaceholder(title: manga.title, accent: manga.accent)
                    .frame(width: 64, height: 92)
                VStack(alignment: .leading, spacing: 4) {
                    Text(manga.title).font(.headline).lineLimit(2)
                    Text(manga.sourceName).font(.caption).foregroundStyle(.secondary)
                    if !manga.tags.isEmpty {
                        Text(manga.tags.prefix(3).joined(separator: " / "))
                            .font(.caption2).foregroundStyle(.secondary)
                    }
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(.tertiary)
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 10).fill(.background.opacity(0.7)))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct DetailsContent: View {
    let details: HelperDetailsResponse
    let onChapter: (HelperChapter) -> Void
    @EnvironmentObject var appState: AppState

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text(details.manga.title).font(.title2.bold())
                    Spacer()
                    Button {
                        Task { await appState.toggleDetailsFavourite() }
                    } label: {
                        Image(systemName: appState.detailsIsFavourited ? "heart.fill" : "heart")
                            .font(.title2)
                            .foregroundStyle(appState.detailsIsFavourited ? Color.pink : Color.secondary)
                    }
                    .buttonStyle(.borderless)
                    .help(appState.detailsIsFavourited ? "Remove from favourites" : "Add to favourites")
                }
                if !details.manga.authors.isEmpty {
                    Text(details.manga.authors.joined(separator: ", "))
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                if !details.manga.tags.isEmpty {
                    HStack {
                        ForEach(details.manga.tags.prefix(8), id: \.title) { tag in
                            Text(tag.title).font(.caption).padding(.horizontal, 8).padding(.vertical, 4)
                                .background(.tertiary, in: Capsule())
                        }
                    }
                }
                if !details.manga.description.isEmpty {
                    Text(details.manga.description).font(.body)
                }
                Divider()
                Text("\(details.chapters.count) chapters").font(.headline)
                ForEach(details.chapters) { chapter in
                    Button { onChapter(chapter) } label: {
                        HStack {
                            Image(systemName: "doc.text")
                            Text(chapter.title).fontWeight(.medium)
                            Spacer()
                            Image(systemName: "chevron.right").foregroundStyle(.tertiary)
                        }
                        .padding(10)
                        .background(RoundedRectangle(cornerRadius: 8).fill(.background.opacity(0.5)))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(18)
        }
    }
}
