package com.nyora.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Library(
    val mangas: List<Manga> = emptyList(),
    val sources: List<MangaSource> = emptyList(),
    val repos: List<MangaRepo> = defaultRepos,
    val categories: List<FavouriteCategory> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
)

@Serializable
data class HistoryEntry(
    val mangaId: String,
    val chapterId: String,
    val page: Int,
    val percent: Float,
    val updatedAt: Long,
)
