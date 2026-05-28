import SwiftUI

struct CatalogSheet: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) private var dismiss
    @State private var search: String = ""
    @State private var languageFilter: String = "all"
    @State private var hideBroken: Bool = true

    var body: some View {
        VStack(spacing: 0) {
            header
            Divider()
            if appState.isCatalogLoading && appState.catalog.isEmpty {
                ProgressView("Loading sources…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                list
            }
            Divider()
            footer
        }
        .frame(minWidth: 640, idealWidth: 760, minHeight: 480, idealHeight: 620)
        .task {
            if appState.catalog.isEmpty { await appState.reloadCatalogEntries() }
        }
    }

    private var header: some View {
        HStack(spacing: 10) {
            Text("Add Sources").font(.title2.bold())
            Spacer()
            TextField("Search by name", text: $search)
                .textFieldStyle(.roundedBorder)
                .frame(width: 200)
            Picker("Language", selection: $languageFilter) {
                Text("All languages").tag("all")
                ForEach(languages, id: \.self) { lang in
                    Text(lang.uppercased()).tag(lang)
                }
            }
            .frame(width: 160)
            Toggle("Hide broken", isOn: $hideBroken)
            Button("Close") { dismiss() }
        }
        .padding(14)
    }

    private var list: some View {
        List {
            ForEach(filtered) { entry in
                CatalogRow(entry: entry) {
                    Task { await appState.installFromCatalog(entry) }
                }
            }
        }
        .listStyle(.inset)
    }

    private var footer: some View {
        HStack {
            Text("\(filtered.count) shown · \(installedCount) installed · \(appState.catalog.count) total")
                .font(.caption)
                .foregroundStyle(.secondary)
            Spacer()
            Button {
                Task { await appState.reloadCatalogEntries() }
            } label: {
                Label("Refresh", systemImage: "arrow.clockwise")
            }
            .disabled(appState.isCatalogLoading)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
    }

    private var languages: [String] {
        Array(Set(appState.catalog.map { $0.lang }))
            .filter { !$0.isEmpty && $0 != "all" }
            .sorted()
    }

    private var installedCount: Int {
        appState.catalog.lazy.filter { $0.isInstalled }.count
    }

    private var filtered: [HelperCatalogEntry] {
        let q = search.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return appState.catalog.filter { entry in
            if hideBroken && entry.isBroken { return false }
            if languageFilter != "all" && entry.lang != languageFilter { return false }
            if !q.isEmpty && !entry.name.lowercased().contains(q) { return false }
            return true
        }
        .sorted { lhs, rhs in
            if lhs.isInstalled != rhs.isInstalled { return lhs.isInstalled && !rhs.isInstalled }
            return lhs.name.lowercased() < rhs.name.lowercased()
        }
    }
}

private struct CatalogRow: View {
    let entry: HelperCatalogEntry
    let onInstall: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 8).fill(Color.accentColor.opacity(0.16))
                Image(systemName: entry.isBroken ? "exclamationmark.triangle" : "puzzlepiece.extension")
                    .foregroundStyle(entry.isBroken ? Color.orange : Color.accentColor)
            }
            .frame(width: 32, height: 32)
            VStack(alignment: .leading) {
                HStack {
                    Text(entry.name).font(.headline)
                    if entry.isBroken {
                        Text("BROKEN").font(.caption2.bold())
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(.orange.opacity(0.2), in: Capsule())
                            .foregroundStyle(.orange)
                    }
                }
                Text("\(entry.lang.uppercased()) · \(entry.contentType)")
                    .font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            if entry.isInstalled {
                Label("Installed", systemImage: "checkmark.circle.fill")
                    .labelStyle(.iconOnly)
                    .foregroundStyle(.green)
            } else {
                Button("Install", action: onInstall)
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)
            }
        }
        .padding(.vertical, 4)
    }
}
