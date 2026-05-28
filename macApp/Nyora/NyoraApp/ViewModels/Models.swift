import Foundation
import SwiftUI

struct MangaSummary: Identifiable, Hashable, Codable {
    let id: String
    let title: String
    let sourceName: String
    let unread: Int
    let progress: Float
    let tags: [String]

    var accent: Color {
        let palette: [Color] = [.blue, .orange, .green, .yellow, .purple, .pink, .teal]
        let h = abs(id.hashValue)
        return palette[h % palette.count]
    }
}

struct SourceSummary: Identifiable, Hashable, Codable {
    let id: String
    let name: String
    let lang: String
    let engine: String
    let isInstalled: Bool
    let isPinned: Bool
}

struct ChapterSummary: Identifiable, Hashable, Codable {
    let id: String
    let title: String
    let pages: [PageSummary]
}

struct PageSummary: Identifiable, Hashable, Codable {
    let url: String
    var id: String { url }
}
