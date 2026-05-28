package com.nyora.shared.extension

import com.nyora.shared.model.MangaSource
import com.nyora.shared.parser.JvmMangaLoaderContext
import com.nyora.shared.parser.ParserMangaExtensionService
import org.koitharu.kotatsu.parsers.model.MangaParserSource

class JvmExtensionRuntime(
    private val loaderContext: JvmMangaLoaderContext = JvmMangaLoaderContext(),
) : MangaExtensionRuntime {

    /** Cache parser instances by their identifier to keep cookies / config alive across calls. */
    private val parserCache = mutableMapOf<String, ParserMangaExtensionService>()

    private val delegate = CommonMangaExtensionRuntime(
        jsFactory = { source -> JavaScriptExtensionService(source) },
        mihonFactory = { source ->
            UnsupportedExtensionService(
                source = source,
                reason = "Mihon APK is installed, but macOS execution needs a JVM/proxy compatibility layer.",
            )
        },
        parserFactory = { source ->
            parserCache.getOrPut(source.id) {
                val enumName = source.id.removePrefix("parser:")
                val parserSource = runCatching { MangaParserSource.valueOf(enumName) }.getOrNull()
                    ?: error("Unknown parser source: ${source.id}")
                ParserMangaExtensionService(loaderContext.newParserInstance(parserSource))
            }
        },
    )

    override fun create(source: MangaSource) = delegate.create(source)
}
