package com.nyora.shared.extension

import com.nyora.shared.model.MangaSource
import com.nyora.shared.model.SourceEngine

interface MangaExtensionRuntime {
    fun create(source: MangaSource): MangaExtensionService
}

class CommonMangaExtensionRuntime(
    private val jsFactory: ((MangaSource) -> MangaExtensionService)? = null,
    private val mihonFactory: ((MangaSource) -> MangaExtensionService)? = null,
    private val parserFactory: ((MangaSource) -> MangaExtensionService)? = null,
) : MangaExtensionRuntime {
    override fun create(source: MangaSource): MangaExtensionService {
        require(source.isInstalled) { "${source.name} is not installed" }
        return when (source.engine) {
            SourceEngine.JavaScript -> jsFactory?.invoke(source)
                ?: UnsupportedExtensionService(source, "JavaScript runtime is not wired on this platform.")
            SourceEngine.Mihon -> mihonFactory?.invoke(source)
                ?: UnsupportedExtensionService(source, "Mihon APK runtime is not enabled on this platform.")
            SourceEngine.Dart -> UnsupportedExtensionService(source, "Dart parser runtime is not enabled.")
            SourceEngine.Parser -> parserFactory?.invoke(source)
                ?: UnsupportedExtensionService(source, "Parser runtime is not enabled.")
        }
    }
}
