package com.nyora.shared.extension

import com.nyora.shared.model.Manga
import com.nyora.shared.model.MangaChapter
import com.nyora.shared.model.MangaPage

interface MangaExtensionService {
    val supportsLatest: Boolean

    fun getHeaders(): Map<String, String> = emptyMap()

    suspend fun getPopular(page: Int): MangaSearchPage

    suspend fun getLatest(page: Int): MangaSearchPage

    suspend fun search(
        query: String,
        page: Int,
        filters: List<SourceFilter> = emptyList(),
    ): MangaSearchPage

    suspend fun getDetails(url: String): MangaDetails

    suspend fun getPageList(chapter: MangaChapter): List<MangaPage>

    fun getFilterList(): List<SourceFilterDescriptor> = emptyList()

    fun getSourcePreferences(): List<SourcePreferenceDescriptor> = emptyList()
}

data class MangaSearchPage(
    val entries: List<Manga>,
    val hasNextPage: Boolean,
)

data class MangaDetails(
    val manga: Manga,
    val chapters: List<MangaChapter>,
)

sealed interface SourceFilter {
    val name: String
}

data class TextSourceFilter(
    override val name: String,
    val value: String,
) : SourceFilter

data class SelectSourceFilter(
    override val name: String,
    val selectedIndex: Int,
) : SourceFilter

data class CheckSourceFilter(
    override val name: String,
    val checked: Boolean,
) : SourceFilter

data class SourceFilterDescriptor(
    val name: String,
    val typeName: String,
    val values: List<String> = emptyList(),
)

data class SourcePreferenceDescriptor(
    val key: String,
    val title: String,
    val summary: String,
    val typeName: String,
)
