package com.nyora.shared.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface MangaSourceRef {
    val name: String

    @Serializable
    data object Local : MangaSourceRef {
        override val name: String = "LOCAL"
    }

    @Serializable
    data object Unknown : MangaSourceRef {
        override val name: String = "UNKNOWN"
    }

    @Serializable
    data class Parser(override val name: String) : MangaSourceRef

    @Serializable
    data class Mihon(override val name: String, val sourceId: Long) : MangaSourceRef

    @Serializable
    data class Script(override val name: String) : MangaSourceRef
}

@Serializable
data class MangaSource(
    val id: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val packageName: String = "",
    val sourceCodeUrl: String = "",
    val iconUrl: String = "",
    val version: String = "0.0.1",
    val versionCode: Long = 0,
    val isInstalled: Boolean = false,
    val isPinned: Boolean = false,
    val isNsfw: Boolean = false,
    val isObsolete: Boolean = false,
    val engine: SourceEngine = SourceEngine.Mihon,
    val contentType: SourceContentType = SourceContentType.Manga,
    val notes: String = "",
    val localPath: String = "",
    val installedAt: Long = 0,
    val canUninstall: Boolean = true,
)

@Serializable
enum class SourceEngine {
    Dart,
    JavaScript,
    Mihon,
    Parser,
}

@Serializable
enum class SourceContentType {
    Manga, Manhwa, Manhua, Hentai, Comics, Novel, OneShot, Doujinshi, ImageSet, ArtistCg, GameCg, Other,
}

@Serializable
data class MangaRepo(
    val name: String,
    val indexUrl: String,
)

val defaultRepos: List<MangaRepo> = listOf(
    MangaRepo(
        name = "Keiyoushi",
        indexUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json",
    ),
)
