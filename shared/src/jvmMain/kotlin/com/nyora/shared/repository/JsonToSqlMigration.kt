package com.nyora.shared.repository

import com.nyora.shared.data.JsonStore
import com.nyora.shared.model.Library
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * If a legacy `library.json` exists in `~/Library/Application Support/Nyora/`,
 * load it once and re-insert into the SQLite database, then rename the JSON
 * file to `library.json.migrated`. Safe to call on every boot — it's a no-op
 * after the first time.
 */
object JsonToSqlMigration {
    fun runIfNeeded(repository: SqlDelightLibraryRepository) {
        val jsonPath = JsonStore.defaultStorePath()
        if (!jsonPath.exists()) return
        val migratedMarker = jsonPath.resolveSibling(jsonPath.fileName.toString() + ".migrated")
        if (migratedMarker.exists()) return

        val json = Json { ignoreUnknownKeys = true }
        val library = runCatching {
            json.decodeFromString(Library.serializer(), jsonPath.readText())
        }.getOrNull() ?: return

        if (library.mangas.isEmpty() && library.sources.isEmpty()) {
            // Nothing to import; still mark so we don't re-check next boot.
            Files.move(jsonPath, migratedMarker)
            return
        }

        repository.save(library)

        // Move (not delete) so users can recover from a buggy migration.
        Files.move(jsonPath, migratedMarker)
        println("Migrated ${library.mangas.size} manga and ${library.sources.size} sources from library.json")
    }

    /** Hard reset for tests or buggy boots. Not wired anywhere by default. */
    fun deleteMigratedMarker(): Boolean {
        val jsonPath = JsonStore.defaultStorePath()
        val migratedMarker: Path = jsonPath.resolveSibling(jsonPath.fileName.toString() + ".migrated")
        return Files.deleteIfExists(migratedMarker)
    }
}
