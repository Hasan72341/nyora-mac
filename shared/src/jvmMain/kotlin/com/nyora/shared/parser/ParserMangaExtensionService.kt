package com.nyora.shared.parser

import com.nyora.shared.extension.MangaDetails
import com.nyora.shared.extension.MangaExtensionService
import com.nyora.shared.extension.MangaSearchPage
import com.nyora.shared.extension.SourceFilter
import com.nyora.shared.model.Manga
import com.nyora.shared.model.MangaChapter
import com.nyora.shared.model.MangaPage
import com.nyora.shared.model.MangaSourceRef
import com.nyora.shared.model.MangaState as DomainMangaState
import com.nyora.shared.model.MangaTag as DomainMangaTag
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.ContentRating as ParserContentRating
import org.koitharu.kotatsu.parsers.model.Manga as ParserManga
import org.koitharu.kotatsu.parsers.model.MangaChapter as ParserChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaPage as ParserPage
import org.koitharu.kotatsu.parsers.model.MangaState as ParserState
import org.koitharu.kotatsu.parsers.model.MangaTag as ParserTag
import org.koitharu.kotatsu.parsers.model.SortOrder

/**
 * Adapts a Kotatsu MangaParser to our MangaExtensionService surface.
 *
 * Mangas are identified by their parser `id` (the Long) serialised as a String.
 * The full original Manga is needed for getDetails / getPages, so we cache the
 * last seen list keyed by id. Calls from /manga/details by url fall back to
 * looking up by url too.
 */
class ParserMangaExtensionService(
    private val parser: MangaParser,
) : MangaExtensionService {

    /** mangaId(string) -> ParserManga for `getDetails` lookup */
    private val mangaCache = mutableMapOf<String, ParserManga>()

    /** chapterUrl -> ParserChapter for `getPageList` lookup */
    private val chapterCache = mutableMapOf<String, ParserChapter>()

    override val supportsLatest: Boolean
        get() = parser.availableSortOrders.contains(SortOrder.UPDATED)

    override fun getHeaders(): Map<String, String> {
        // Headers is an okhttp3 type; access via @JvmField getter to dodge a
        // current quirk where the Kotlin property name isn't visible at this layer.
        val headers = parser.javaClass.getMethod("getRequestHeaders").invoke(parser) as okhttp3.Headers
        return buildMap(headers.size) {
            for (i in 0 until headers.size) {
                put(headers.name(i), headers.value(i))
            }
        }
    }

    override suspend fun getPopular(page: Int): MangaSearchPage =
        firstNonEmpty(page, MangaListFilter(), listOf(
            SortOrder.POPULARITY, SortOrder.UPDATED, SortOrder.NEWEST, SortOrder.RATING,
        ))

    override suspend fun getLatest(page: Int): MangaSearchPage =
        firstNonEmpty(page, MangaListFilter(), listOf(
            SortOrder.UPDATED, SortOrder.NEWEST, SortOrder.POPULARITY,
        ))

    override suspend fun search(query: String, page: Int, filters: List<SourceFilter>): MangaSearchPage {
        val filter = MangaListFilter(query = query.takeIf { it.isNotBlank() })
        return firstNonEmpty(page, filter, listOf(
            SortOrder.RELEVANCE, SortOrder.POPULARITY, SortOrder.UPDATED,
        ))
    }

    /**
     * Try each preferred sort order; if all of them return empty, also try
     * every other sort order the parser advertises. Returns the first list
     * that has at least one entry, or the last empty list if nothing works.
     */
    private suspend fun firstNonEmpty(
        page: Int,
        filter: MangaListFilter,
        preferred: List<SortOrder>,
    ): MangaSearchPage {
        val available = parser.availableSortOrders
        val ordered = LinkedHashSet<SortOrder>().apply {
            for (p in preferred) if (p in available) add(p)
            addAll(available)
        }
        if (ordered.isEmpty()) {
            return MangaSearchPage(entries = emptyList(), hasNextPage = false)
        }
        for (order in ordered) {
            val list = runCatching { parser.getList(page, order, filter) }.getOrNull() ?: continue
            if (list.isNotEmpty()) {
                return list.toSearchPage()
            }
        }
        return MangaSearchPage(entries = emptyList(), hasNextPage = false)
    }

    override suspend fun getDetails(url: String): MangaDetails {
        val source = mangaCache[url]
            ?: mangaCache.values.firstOrNull { it.url == url || it.publicUrl == url }
            ?: makeStubManga(url)
        val enriched = parser.getDetails(source)
        // Re-cache chapters so getPageList can find them.
        enriched.chapters?.forEach { ch -> chapterCache[ch.url] = ch }
        return MangaDetails(
            manga = enriched.toDomain(),
            chapters = enriched.chapters?.map { it.toDomain() }.orEmpty(),
        )
    }

    override suspend fun getPageList(chapter: MangaChapter): List<MangaPage> {
        val parserChapter = chapterCache[chapter.url] ?: chapterCache[chapter.id]
            ?: makeStubChapter(chapter)
        val pages = parser.getPages(parserChapter)
        // Resolve final image URL up-front so AsyncImage can fetch via /image.
        return pages.map { page ->
            val url = runCatching { parser.getPageUrl(page) }.getOrDefault(page.url)
            MangaPage(url = url, headers = getHeaders())
        }
    }

    private fun pickSortOrder(vararg preferred: SortOrder): SortOrder {
        val available = parser.availableSortOrders
        for (order in preferred) {
            if (order in available) return order
        }
        return available.firstOrNull() ?: SortOrder.POPULARITY
    }

    private fun List<ParserManga>.toSearchPage(): MangaSearchPage {
        val mapped = map { manga ->
            mangaCache[manga.url] = manga
            mangaCache[manga.id.toString()] = manga
            manga.toDomain()
        }
        return MangaSearchPage(entries = mapped, hasNextPage = mapped.isNotEmpty())
    }

    private fun ParserManga.toDomain(): Manga = Manga(
        id = url, // use the url so /manga/details lookups round-trip
        title = title,
        altTitles = altTitles.toList(),
        url = url,
        publicUrl = publicUrl,
        rating = rating,
        isNsfw = contentRating == ParserContentRating.ADULT,
        contentRating = contentRating?.toDomain(),
        coverUrl = coverUrl.orEmpty(),
        largeCoverUrl = largeCoverUrl,
        state = state?.toDomain(),
        authors = authors.toList(),
        source = MangaSourceRef.Parser(parser.source.toString()),
        description = description.orEmpty(),
        tags = tags.map { it.toDomain() },
        chapters = chapters?.map { it.toDomain() }.orEmpty(),
    )

    private fun ParserChapter.toDomain(): MangaChapter = MangaChapter(
        id = url,
        title = title?.takeIf { it.isNotBlank() } ?: composeChapterTitle(number, volume),
        number = number,
        volume = volume,
        url = url,
        scanlator = scanlator,
        uploadDate = uploadDate,
        branch = branch,
        pages = emptyList(),
        index = 0,
    )

    private fun composeChapterTitle(number: Float, volume: Int): String {
        val nString = when {
            number <= 0f -> null
            number == number.toInt().toFloat() -> number.toInt().toString()
            else -> number.toString()
        }
        val vString = if (volume > 0) volume.toString() else null
        return when {
            vString != null && nString != null -> "Vol. $vString Ch. $nString"
            nString != null -> "Chapter $nString"
            vString != null -> "Volume $vString"
            else -> "Unnamed chapter"
        }
    }

    private fun ParserTag.toDomain(): DomainMangaTag = DomainMangaTag(
        key = key,
        title = title,
    )

    private fun ParserState.toDomain(): DomainMangaState = when (this) {
        ParserState.ONGOING -> DomainMangaState.ONGOING
        ParserState.FINISHED -> DomainMangaState.FINISHED
        ParserState.ABANDONED -> DomainMangaState.ABANDONED
        ParserState.PAUSED -> DomainMangaState.PAUSED
        ParserState.UPCOMING -> DomainMangaState.UPCOMING
        ParserState.RESTRICTED -> DomainMangaState.RESTRICTED
    }

    private fun ParserContentRating.toDomain(): com.nyora.shared.model.ContentRating = when (this) {
        ParserContentRating.SAFE -> com.nyora.shared.model.ContentRating.SAFE
        ParserContentRating.SUGGESTIVE -> com.nyora.shared.model.ContentRating.SUGGESTIVE
        ParserContentRating.ADULT -> com.nyora.shared.model.ContentRating.ADULT
    }

    private fun makeStubManga(url: String): ParserManga = ParserManga(
        id = url.hashCode().toLong(),
        title = url,
        altTitle = null,
        url = url,
        publicUrl = url,
        rating = -1f,
        isNsfw = false,
        coverUrl = "",
        tags = emptySet(),
        state = null,
        author = null,
        largeCoverUrl = null,
        description = null,
        chapters = null,
        source = parser.source,
    )

    private fun makeStubChapter(chapter: MangaChapter): ParserChapter = ParserChapter(
        id = chapter.url.hashCode().toLong(),
        title = chapter.title,
        number = chapter.number,
        volume = chapter.volume,
        url = chapter.url,
        scanlator = chapter.scanlator,
        uploadDate = chapter.uploadDate,
        branch = chapter.branch,
        source = parser.source,
    )
}
