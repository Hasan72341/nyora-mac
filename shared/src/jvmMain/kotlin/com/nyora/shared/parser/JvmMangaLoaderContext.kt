package com.nyora.shared.parser

import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Response
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * JVM-side MangaLoaderContext for the macOS helper.
 *
 * Capabilities:
 *  - OkHttp client (works on the JVM exactly like Android).
 *  - In-memory cookie jar (sources that need cookies share state across calls).
 *  - Base64 via java.util.Base64.
 *  - Default User-Agent string (no WebView introspection available).
 *  - In-memory per-source config (no SharedPreferences equivalent yet).
 *
 * Limitations (will surface as runtime errors for sources that need them):
 *  - No WebView: evaluateJs / interceptWebViewRequests / captureWebViewUrls throw.
 *  - No bitmap manipulation: createBitmap / redrawImageResponse throw.
 *    Sources that re-stitch scrambled pages (rare) will fail.
 *  - No interactive browser actions: requestBrowserAction throws.
 *
 * Future: wire GraalVM JS (already a dep) into evaluateJs for JS-required sources.
 */
class JvmMangaLoaderContext(
    private val userAgent: String = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
    httpClient: OkHttpClient? = null,
) : MangaLoaderContext() {

    private val cookieStore = SimpleCookieJar()

    override val httpClient: OkHttpClient = httpClient ?: OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .cookieJar(cookieStore)
        .build()

    override val cookieJar: CookieJar = cookieStore

    private val configsBySource = mutableMapOf<MangaSource, InMemoryConfig>()

    override fun getDefaultUserAgent(): String = userAgent

    override fun getConfig(source: MangaSource): MangaSourceConfig =
        configsBySource.getOrPut(source) { InMemoryConfig() }

    override fun encodeBase64(data: ByteArray): String =
        Base64.getEncoder().encodeToString(data)

    override fun decodeBase64(data: String): ByteArray =
        Base64.getDecoder().decode(data)

    override fun getPreferredLocales(): List<Locale> = listOf(
        Locale.getDefault(),
        Locale.ENGLISH,
    ).distinct()

    @Deprecated("Provide a base url")
    override suspend fun evaluateJs(script: String): String? =
        throw UnsupportedOperationException("JavaScript evaluation is not wired in the helper yet.")

    override suspend fun evaluateJs(baseUrl: String, script: String, timeout: Long): String? =
        throw UnsupportedOperationException("JavaScript evaluation is not wired in the helper yet.")

    override fun requestBrowserAction(parser: MangaParser, url: String): Nothing =
        throw InteractiveSourceUnsupportedException(parser.source.toString(), url)

    override fun redrawImageResponse(
        response: Response,
        redraw: (Bitmap) -> Bitmap,
    ): Response = throw UnsupportedOperationException(
        "Bitmap manipulation is not supported in the macOS helper.",
    )

    override fun createBitmap(width: Int, height: Int): Bitmap =
        throw UnsupportedOperationException("Bitmap creation is not supported in the macOS helper.")
}

class InteractiveSourceUnsupportedException(
    val sourceName: String,
    val url: String,
) : RuntimeException("Source '$sourceName' needs an interactive browser ($url) — not supported on macOS helper yet.")

private class InMemoryConfig : MangaSourceConfig {
    private val values = mutableMapOf<String, Any?>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> get(key: ConfigKey<T>): T {
        val stored = values[key.key]
        @Suppress("UNCHECKED_CAST")
        if (stored != null) return stored as T
        return key.defaultValue
    }

    fun <T : Any?> set(key: ConfigKey<T>, value: T) {
        values[key.key] = value
    }
}
