package com.nyora.shared.repository

import com.nyora.shared.model.Library
import com.nyora.shared.model.Manga
import com.nyora.shared.model.MangaSource

interface LibraryRepository {
    fun load(): Library
    fun save(library: Library)
    fun upsertManga(manga: Manga)
    fun upsertSource(source: MangaSource)

    /** Last-read manga, newest first. */
    fun history(limit: Int = 100): List<HistoryRow> = emptyList()

    /** Upsert a per-manga reading checkpoint. */
    fun recordHistory(
        mangaId: String,
        chapterId: String,
        chapterTitle: String,
        page: Int,
        percent: Float,
    ) {}

    /** Manga marked as favourite, newest-favourited first. */
    fun favourites(): List<Manga> = emptyList()

    fun isFavourited(mangaId: String): Boolean = false

    /** Add/remove. Returns the new state (true = now favourited). */
    fun toggleFavourite(mangaId: String): Boolean = false

    /** All bookmarks, newest first. */
    fun bookmarks(): List<BookmarkRow> = emptyList()

    fun bookmarksForChapter(mangaId: String, chapterId: String): List<BookmarkRow> = emptyList()

    fun isPageBookmarked(mangaId: String, chapterId: String, page: Int): Boolean = false

    fun addBookmark(mangaId: String, chapterId: String, chapterTitle: String, page: Int, note: String) {}

    fun removeBookmark(id: Long) {}

    fun removeBookmarkForPage(mangaId: String, chapterId: String, page: Int) {}
}

data class HistoryRow(
    val manga: Manga,
    val chapterId: String,
    val chapterTitle: String,
    val page: Int,
    val percent: Float,
    val updatedAt: Long,
)

data class BookmarkRow(
    val id: Long,
    val mangaId: String,
    val mangaTitle: String,
    val mangaCoverUrl: String,
    val chapterId: String,
    val chapterTitle: String,
    val page: Int,
    val note: String,
    val createdAt: Long,
)
