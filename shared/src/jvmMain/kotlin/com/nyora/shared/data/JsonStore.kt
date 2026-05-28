package com.nyora.shared.data

import com.nyora.shared.model.Library
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class JsonStore(
    private val file: Path = defaultStorePath(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun load(): Library {
        if (!file.exists()) return Library()
        return json.decodeFromString(Library.serializer(), file.readText())
    }

    fun save(library: Library) {
        file.parent.createDirectories()
        file.writeText(json.encodeToString(Library.serializer(), library))
    }

    companion object {
        fun defaultStorePath(): Path {
            val home = Path.of(System.getProperty("user.home"))
            val base = home.resolve("Library").resolve("Application Support").resolve("Nyora")
            Files.createDirectories(base)
            return base.resolve("library.json")
        }
    }
}
