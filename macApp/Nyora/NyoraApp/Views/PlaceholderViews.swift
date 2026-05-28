import SwiftUI

struct HistoryView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        Group {
            if appState.history.isEmpty {
                EmptyStateView(
                    icon: "clock.arrow.circlepath",
                    title: "No reading history yet",
                    message: "Chapters you open will appear here, newest first."
                )
            } else {
                List(appState.history) { row in
                    HStack(spacing: 12) {
                        AsyncImage(url: URL(string: row.mangaCoverUrl)) { phase in
                            switch phase {
                            case let .success(image):
                                image.resizable().scaledToFill()
                            default:
                                Rectangle().fill(.tertiary)
                            }
                        }
                        .frame(width: 44, height: 60)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                        VStack(alignment: .leading, spacing: 4) {
                            Text(row.mangaTitle).font(.headline).lineLimit(1)
                            Text(row.chapterTitle.isEmpty ? "Chapter" : row.chapterTitle)
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(Date(timeIntervalSince1970: TimeInterval(row.updatedAt) / 1000),
                             format: .relative(presentation: .named))
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                    }
                    .padding(.vertical, 4)
                }
                .listStyle(.inset)
            }
        }
        .toolbar {
            Button {
                Task { await appState.reloadHistory() }
            } label: { Label("Refresh", systemImage: "arrow.clockwise") }
        }
    }
}

struct FeedView: View {
    var body: some View {
        EmptyStateView(icon: "newspaper", title: "Feed", message: "Tracker updates land here. Tracking integration is not wired yet.")
    }
}

struct LocalView: View {
    var body: some View {
        EmptyStateView(icon: "folder", title: "Local", message: "Imported CBZ/CBR folders will appear here.")
    }
}

struct SuggestionsView: View {
    var body: some View {
        EmptyStateView(icon: "sparkles", title: "Suggestions", message: "Discoverable manga based on your library.")
    }
}

struct BookmarksView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        Group {
            if appState.bookmarks.isEmpty {
                EmptyStateView(
                    icon: "bookmark",
                    title: "No bookmarks yet",
                    message: "Tap the bookmark icon in the reader to save a page for later."
                )
            } else {
                List {
                    ForEach(groups, id: \.mangaId) { group in
                        Section(header: Text(group.mangaTitle)) {
                            ForEach(group.bookmarks) { bookmark in
                                BookmarkRow(bookmark: bookmark) {
                                    Task { await appState.openBookmark(bookmark) }
                                } onDelete: {
                                    Task {
                                        try? await appState.helper.removeBookmark(id: bookmark.id)
                                        await appState.reloadBookmarks()
                                    }
                                }
                            }
                        }
                    }
                }
                .listStyle(.sidebar)
            }
        }
        .toolbar {
            Button {
                Task { await appState.reloadBookmarks() }
            } label: { Label("Refresh", systemImage: "arrow.clockwise") }
        }
    }

    private struct Group2 { let mangaId: String; let mangaTitle: String; let bookmarks: [HelperBookmark] }

    private var groups: [Group2] {
        let byManga = Dictionary(grouping: appState.bookmarks, by: \.mangaId)
        return byManga
            .map { Group2(mangaId: $0.key, mangaTitle: $0.value.first?.mangaTitle ?? $0.key, bookmarks: $0.value.sorted { $0.page < $1.page }) }
            .sorted { $0.mangaTitle.lowercased() < $1.mangaTitle.lowercased() }
    }
}

private struct BookmarkRow: View {
    let bookmark: HelperBookmark
    let onOpen: () -> Void
    let onDelete: () -> Void

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 12) {
                AsyncImage(url: URL(string: bookmark.mangaCoverUrl)) { phase in
                    switch phase {
                    case let .success(image): image.resizable().scaledToFill()
                    default: Rectangle().fill(.tertiary)
                    }
                }
                .frame(width: 40, height: 56)
                .clipShape(RoundedRectangle(cornerRadius: 4))

                VStack(alignment: .leading, spacing: 2) {
                    Text(bookmark.chapterTitle.isEmpty ? "Chapter" : bookmark.chapterTitle)
                        .font(.subheadline.weight(.medium))
                    Text("Page \(bookmark.page + 1)")
                        .font(.caption).foregroundStyle(.secondary)
                    if !bookmark.note.isEmpty {
                        Text(bookmark.note).font(.caption).foregroundStyle(.tertiary).lineLimit(2)
                    }
                }
                Spacer()
                Text(Date(timeIntervalSince1970: TimeInterval(bookmark.createdAt) / 1000),
                     format: .relative(presentation: .named))
                    .font(.caption2).foregroundStyle(.tertiary)
            }
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button("Open", action: onOpen)
            Button("Delete", role: .destructive, action: onDelete)
        }
    }
}

struct UpdatesView: View {
    var body: some View {
        EmptyStateView(icon: "arrow.triangle.2.circlepath", title: "Updates", message: "New chapter notifications go here once the tracker runs.")
    }
}

struct ReaderView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        Group {
            if let chapter = appState.activeChapter, !chapter.pages.isEmpty {
                Group {
                    switch appState.readerMode {
                    case .paged: ReaderPagedView(chapter: chapter)
                    case .webtoon: ReaderWebtoonView(chapter: chapter)
                    }
                }
                .toolbar { readerToolbar(chapter: chapter) }
                .onChange(of: appState.readerPageIndex) { _, _ in
                    Task { await appState.refreshCurrentPageBookmarkedFlag() }
                }
                .task {
                    await appState.refreshCurrentPageBookmarkedFlag()
                }
            } else {
                EmptyStateView(
                    icon: "book.pages",
                    title: "Open a chapter to read",
                    message: "Choose a chapter from a manga's details to load its pages."
                )
            }
        }
    }

    @ToolbarContentBuilder
    private func readerToolbar(chapter: ChapterSummary) -> some ToolbarContent {
        ToolbarItemGroup {
            Button {
                Task { await appState.gotoChapterRelative(-1) }
            } label: { Label("Previous Chapter", systemImage: "backward") }
            .disabled(appState.readerChapterIndex <= 0)

            Text(chapter.title.isEmpty ? "Chapter" : chapter.title)
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(minWidth: 120)

            Button {
                Task { await appState.gotoChapterRelative(1) }
            } label: { Label("Next Chapter", systemImage: "forward") }
            .disabled(appState.readerChapterIndex >= appState.readerChapters.count - 1)

            Button {
                Task { await appState.toggleCurrentPageBookmark() }
            } label: {
                Label(
                    appState.currentPageBookmarked ? "Remove Bookmark" : "Bookmark Page",
                    systemImage: appState.currentPageBookmarked ? "bookmark.fill" : "bookmark"
                )
            }
            .help(appState.currentPageBookmarked ? "Remove bookmark from current page" : "Bookmark current page")

            Picker("", selection: $appState.readerMode) {
                ForEach(ReaderMode.allCases) { mode in
                    Label(mode.label, systemImage: mode.systemImage).tag(mode)
                }
            }
            .pickerStyle(.segmented)
            .frame(width: 180)
        }
    }
}

struct ReaderPagedView: View {
    let chapter: ChapterSummary
    @EnvironmentObject var appState: AppState

    var body: some View {
        VStack(spacing: 10) {
            HStack {
                Button {
                    let next = max(0, appState.readerPageIndex - 1)
                    appState.readerPageIndex = next
                    Task { await appState.persistReaderPosition() }
                } label: { Image(systemName: "chevron.left").font(.title2) }
                .disabled(appState.readerPageIndex <= 0)

                Spacer()
                Text("Page \(appState.readerPageIndex + 1) of \(chapter.pages.count)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()

                Button {
                    let last = chapter.pages.count - 1
                    let next = min(last, appState.readerPageIndex + 1)
                    appState.readerPageIndex = next
                    Task { await appState.persistReaderPosition() }
                } label: { Image(systemName: "chevron.right").font(.title2) }
                .disabled(appState.readerPageIndex >= chapter.pages.count - 1)
            }
            .padding(.horizontal, 18)
            .padding(.top, 12)

            if let page = chapter.pages[safe: appState.readerPageIndex] {
                AsyncImage(url: URL(string: page.url)) { phase in
                    switch phase {
                    case .empty: ProgressView()
                    case let .success(image): image.resizable().scaledToFit()
                    case .failure(let err):
                        VStack {
                            Image(systemName: "exclamationmark.triangle").font(.system(size: 40))
                            Text("Page failed to load")
                            Text(err.localizedDescription).font(.caption).foregroundStyle(.secondary)
                        }
                    @unknown default: EmptyView()
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .focusable()
        .onKeyPress(.leftArrow) {
            let next = max(0, appState.readerPageIndex - 1)
            appState.readerPageIndex = next
            Task { await appState.persistReaderPosition() }
            return .handled
        }
        .onKeyPress(.rightArrow) {
            let last = chapter.pages.count - 1
            let next = min(last, appState.readerPageIndex + 1)
            appState.readerPageIndex = next
            Task { await appState.persistReaderPosition() }
            return .handled
        }
    }
}

struct ReaderWebtoonView: View {
    let chapter: ChapterSummary
    @EnvironmentObject var appState: AppState
    @State private var visiblePage: Int = 0

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.vertical) {
                LazyVStack(spacing: 4) {
                    ForEach(Array(chapter.pages.enumerated()), id: \.offset) { idx, page in
                        WebtoonImage(url: page.url, index: idx) { newlyVisible in
                            // Cheap approximation: when an image appears, treat it as current.
                            if newlyVisible > visiblePage {
                                visiblePage = newlyVisible
                                appState.readerPageIndex = newlyVisible
                                Task { await appState.persistReaderPosition() }
                            }
                        }
                        .id(idx)
                    }
                }
                .padding(.horizontal, 4)
            }
            .onAppear {
                let start = max(0, appState.readerPageIndex)
                if start > 0 {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                        proxy.scrollTo(start, anchor: .top)
                    }
                }
            }
        }
        .overlay(alignment: .bottom) {
            Text("\(appState.readerPageIndex + 1) / \(chapter.pages.count)")
                .font(.caption.monospacedDigit())
                .padding(.horizontal, 10).padding(.vertical, 4)
                .background(.thinMaterial, in: Capsule())
                .padding(.bottom, 8)
        }
    }
}

/// One page row that reports when it scrolls into view.
private struct WebtoonImage: View {
    let url: String
    let index: Int
    let onVisible: (Int) -> Void

    var body: some View {
        AsyncImage(url: URL(string: url)) { phase in
            switch phase {
            case .empty:
                Rectangle()
                    .fill(.tertiary)
                    .frame(maxWidth: .infinity).frame(height: 600)
                    .overlay(ProgressView())
            case let .success(image):
                image.resizable().scaledToFit()
                    .frame(maxWidth: .infinity)
                    .onAppear { onVisible(index) }
            case .failure:
                Rectangle()
                    .fill(.red.opacity(0.1))
                    .frame(maxWidth: .infinity).frame(height: 200)
                    .overlay(Label("Page \(index + 1) failed", systemImage: "exclamationmark.triangle"))
            @unknown default: EmptyView()
            }
        }
    }
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}

struct SettingsView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        Form {
            Section("JVM Helper") {
                HStack {
                    Text("Status")
                    Spacer()
                    Text(appState.helperStatus.label).foregroundStyle(appState.helperStatus.color)
                }
                HStack {
                    Text("Listening at")
                    Spacer()
                    Text(appState.helperBaseUrl.isEmpty ? "—" : appState.helperBaseUrl)
                        .font(.caption.monospaced())
                        .foregroundStyle(.secondary)
                }
                Button("Restart Helper") {
                    Task { await appState.restartHelper() }
                }
            }
            Section("About") {
                LabeledContent("Version", value: "0.1.0 (pre-alpha port)")
                LabeledContent("Engine", value: "GraalVM JS (helper sidecar)")
            }
        }
        .formStyle(.grouped)
        .padding(18)
    }
}

struct EmptyStateView: View {
    let icon: String
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: icon).font(.system(size: 44))
                .foregroundStyle(.secondary)
            Text(title).font(.title2.bold())
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 360)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(24)
    }
}
