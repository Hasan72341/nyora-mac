package com.nyora.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Manga(
    val id: String,
    val title: String,
    val altTitles: List<String> = emptyList(),
    val url: String = "",
    val publicUrl: String = "",
    val rating: Float = -1f,
    val isNsfw: Boolean = false,
    val contentRating: ContentRating? = null,
    val coverUrl: String = "",
    val largeCoverUrl: String? = null,
    val state: MangaState? = null,
    val authors: List<String> = emptyList(),
    val source: MangaSourceRef = MangaSourceRef.Unknown,
    val description: String = "",
    val tags: List<MangaTag> = emptyList(),
    val chapters: List<MangaChapter> = emptyList(),
    val unread: Int = 0,
    val progress: Float = 0f,
)

@Serializable
data class MangaChapter(
    val id: String,
    val title: String,
    val number: Float = 0f,
    val volume: Int = 0,
    val url: String = "",
    val scanlator: String? = null,
    val uploadDate: Long = 0L,
    val branch: String? = null,
    val pages: List<MangaPage> = emptyList(),
    val index: Int = 0,
)

@Serializable
data class MangaPage(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class MangaTag(
    val key: String,
    val title: String,
)

@Serializable
enum class MangaState {
    ONGOING, FINISHED, ABANDONED, PAUSED, UPCOMING, RESTRICTED,
}

@Serializable
enum class ContentRating {
    SAFE, SUGGESTIVE, ADULT,
}
