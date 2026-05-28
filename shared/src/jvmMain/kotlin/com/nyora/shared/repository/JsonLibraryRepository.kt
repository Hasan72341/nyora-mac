package com.nyora.shared.repository

import com.nyora.shared.data.JsonStore
import com.nyora.shared.model.Library
import com.nyora.shared.model.Manga
import com.nyora.shared.model.MangaSource

class JsonLibraryRepository(
    private val store: JsonStore = JsonStore(),
) : LibraryRepository {
    private var cached: Library? = null

    override fun load(): Library {
        return cached ?: store.load().also { cached = it }
    }

    override fun save(library: Library) {
        cached = library
        store.save(library)
    }

    override fun upsertManga(manga: Manga) {
        val current = load()
        val updated = current.copy(
            mangas = (listOf(manga) + current.mangas.filterNot { it.id == manga.id }).distinctBy { it.id },
        )
        save(updated)
    }

    override fun upsertSource(source: MangaSource) {
        val current = load()
        val updated = current.copy(
            sources = (listOf(source) + current.sources.filterNot { it.id == source.id }).distinctBy { it.id },
        )
        save(updated)
    }
}
