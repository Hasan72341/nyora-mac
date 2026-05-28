package com.nyora.shared.extension

import com.nyora.shared.model.MangaChapter
import com.nyora.shared.model.MangaPage
import com.nyora.shared.model.MangaSource

open class UnsupportedExtensionService(
    private val source: MangaSource,
    private val reason: String,
) : MangaExtensionService {
    override val supportsLatest: Boolean = false

    override suspend fun getPopular(page: Int): MangaSearchPage = unsupported()

    override suspend fun getLatest(page: Int): MangaSearchPage = unsupported()

    override suspend fun search(
        query: String,
        page: Int,
        filters: List<SourceFilter>,
    ): MangaSearchPage = unsupported()

    override suspend fun getDetails(url: String): MangaDetails = unsupported()

    override suspend fun getPageList(chapter: MangaChapter): List<MangaPage> = unsupported()

    private fun unsupported(): Nothing {
        throw IllegalStateException("${source.name}: $reason")
    }
}
