package com.nyora.shared.proxy

import com.nyora.shared.extension.CheckSourceFilter
import com.nyora.shared.extension.JavaScriptExtensionService
import com.nyora.shared.extension.SelectSourceFilter
import com.nyora.shared.extension.SourceFilter
import com.nyora.shared.extension.TextSourceFilter
import com.nyora.shared.model.MangaChapter
import com.nyora.shared.model.MangaPage
import com.nyora.shared.model.MangaSource
import com.nyora.shared.model.SourceEngine
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class MihonProxyServer(
    private val host: InetAddress = InetAddress.getLoopbackAddress(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var server: HttpServer? = null

    val baseUrl: String
        get() = server?.address?.port?.let { "http://127.0.0.1:$it" }.orEmpty()

    val isRunning: Boolean
        get() = server != null

    fun start(): String {
        if (server != null) return baseUrl

        val httpServer = HttpServer.create(InetSocketAddress(host, 0), 0).apply {
            createContext("/") { exchange ->
                if (exchange.requestMethod.equals("GET", ignoreCase = true)) {
                    respondText(exchange, 200, "Nyora proxy server")
                } else {
                    respondText(exchange, 405, "Method not allowed")
                }
            }
            createContext("/dalvik") { exchange ->
                if (!exchange.requestMethod.equals("POST", ignoreCase = true)) {
                    respondText(exchange, 405, "Method not allowed")
                    return@createContext
                }
                handleDalvik(exchange)
            }
            executor = Executors.newCachedThreadPool()
            start()
        }
        server = httpServer
        return baseUrl
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun handleDalvik(exchange: HttpExchange) {
        runCatching {
            val payload = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
            val request = json.parseToJsonElement(payload).jsonObject
            val method = request.string("method")
            val sourceCode = request.string("data")
            if (method.isBlank()) {
                respondJson(exchange, 400, jsonObjectError("Missing method"))
                return
            }
            if (sourceCode.isBlank()) {
                respondJson(exchange, 400, jsonObjectError("Missing source code"))
                return
            }

            val sourcePreferencesJson = request["preferences"]?.toString() ?: "[]"
            val source = SourceMetadataParser.parse(sourceCode)
            val service = JavaScriptExtensionService(
                source = source,
                sourceCodeOverride = sourceCode,
                sourcePreferencesJson = sourcePreferencesJson,
            )

            when (method) {
                "headersManga" -> respondJson(exchange, 200, headersToJson(service.getHeaders()))
                "supportLatestManga" -> respondText(exchange, 200, service.supportsLatest.toString())
                "filtersManga" -> respondJson(exchange, 200, toJsonElement(service.getFilterList()))
                "preferencesManga" -> respondJson(exchange, 200, toJsonElement(service.getSourcePreferences()))
                "getPopularManga" -> {
                    val page = request["page"]?.jsonPrimitive?.intOrNull ?: 1
                    respondJson(exchange, 200, mangaPageToJson(runBlocking { service.getPopular(page) }))
                }
                "getLatestManga" -> {
                    val page = request["page"]?.jsonPrimitive?.intOrNull ?: 1
                    respondJson(exchange, 200, mangaPageToJson(runBlocking { service.getLatest(page) }))
                }
                "getSearchManga" -> {
                    val page = request["page"]?.jsonPrimitive?.intOrNull ?: 1
                    val query = request.string("search")
                    val filters = parseFilters(request["filterList"])
                    respondJson(exchange, 200, mangaPageToJson(runBlocking { service.search(query, page, filters) }))
                }
                "getDetailsManga" -> {
                    val url = request.primaryUrl()
                    val details = runBlocking { service.getDetails(url) }
                    respondJson(exchange, 200, mangaDetailsToJson(details))
                }
                "getChapterList" -> {
                    val url = request.primaryUrl()
                    val details = runBlocking { service.getDetails(url) }
                    respondJson(exchange, 200, chaptersToJson(details.chapters))
                }
                "getPageList" -> {
                    val chapterUrl = request.chapterUrl()
                    val chapter = MangaChapter(id = chapterUrl, title = chapterUrl, url = chapterUrl)
                    respondJson(exchange, 200, pagesToJson(runBlocking { service.getPageList(chapter) }))
                }
                else -> respondJson(exchange, 400, jsonObjectError("Unsupported method: $method"))
            }
        }.onFailure { error ->
            respondJson(exchange, 500, jsonObjectError(error.message ?: "Proxy error"))
        }
    }

    private fun parseFilters(element: JsonElement?): List<SourceFilter> {
        val array = element?.jsonArray ?: return emptyList()
        return array.mapNotNull { filter ->
            val obj = filter.jsonObject
            when (obj.string("type_name")) {
                "TextFilter" -> TextSourceFilter(name = obj.string("name"), value = obj.string("state"))
                "SelectFilter" -> SelectSourceFilter(
                    name = obj.string("name"),
                    selectedIndex = obj["state"]?.jsonPrimitive?.intOrNull ?: 0,
                )
                "CheckBox", "TriState" -> CheckSourceFilter(
                    name = obj.string("name"),
                    checked = when (val state = obj["state"]) {
                        is JsonPrimitive -> state.booleanOrNull ?: (state.intOrNull ?: 0) != 0
                        else -> false
                    },
                )
                else -> null
            }
        }
    }

    private fun JsonObject.string(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonObject.primaryUrl(): String =
        this["mangaData"]?.jsonObject?.string("url")
            ?: this["chapterData"]?.jsonObject?.string("url")
            ?: ""
    private fun JsonObject.chapterUrl(): String =
        this["chapterData"]?.jsonObject?.string("url") ?: primaryUrl()

    private fun mangaPageToJson(page: com.nyora.shared.extension.MangaSearchPage): JsonElement = buildJsonObject {
        putJsonArray("list") {
            page.entries.forEach { manga ->
                add(buildJsonObject {
                    putValue("name", manga.title)
                    putValue("link", manga.id)
                    putValue("imageUrl", manga.coverUrl)
                    putValue("author", manga.authors.joinToString(", "))
                    putJsonArray("genre") { manga.tags.forEach { addValue(it.title) } }
                })
            }
        }
        putValue("hasNextPage", page.hasNextPage)
    }

    private fun mangaDetailsToJson(details: com.nyora.shared.extension.MangaDetails): JsonElement =
        buildJsonObject {
            val manga = details.manga
            putValue("title", manga.title)
            putValue("url", manga.id)
            putValue("imageUrl", manga.coverUrl)
            putValue("author", manga.authors.joinToString(", "))
            putValue("description", manga.description)
            putJsonArray("genre") { manga.tags.forEach { addValue(it.title) } }
            putJsonArray("chapters") {
                details.chapters.forEach { chapter ->
                    add(buildJsonObject {
                        putValue("name", chapter.title)
                        putValue("url", chapter.url.ifBlank { chapter.id })
                    })
                }
            }
        }

    private fun chaptersToJson(chapters: List<MangaChapter>): JsonElement = buildJsonArray {
        chapters.forEach { chapter ->
            add(buildJsonObject {
                putValue("name", chapter.title)
                putValue("url", chapter.url.ifBlank { chapter.id })
            })
        }
    }

    private fun pagesToJson(pages: List<MangaPage>): JsonElement = buildJsonArray {
        pages.forEach { page ->
            add(buildJsonObject {
                putValue("url", page.url)
                if (page.headers.isNotEmpty()) {
                    put("headers", toJsonElement(page.headers))
                }
            })
        }
    }

    private fun respondText(exchange: HttpExchange, status: Int, text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun respondJson(exchange: HttpExchange, status: Int, body: JsonElement) {
        val bytes = json.encodeToString(JsonElement.serializer(), body).toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun headersToJson(headers: Map<String, String>): JsonElement = buildJsonArray {
        headers.forEach { (key, value) ->
            addValue(key)
            addValue(value)
        }
    }

    private fun jsonObjectError(message: String): JsonElement =
        buildJsonObject { putValue("error", message) }
}

private fun JsonObjectBuilder.putValue(name: String, value: Any?) {
    put(name, toJsonElement(value))
}

private fun JsonArrayBuilder.addValue(value: Any?) {
    add(toJsonElement(value))
}

private fun toJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Double -> JsonPrimitive(value)
    is Float -> JsonPrimitive(value)
    is Map<*, *> -> buildJsonObject {
        value.forEach { (k, v) -> put(k.toString(), toJsonElement(v)) }
    }
    is Iterable<*> -> buildJsonArray {
        value.forEach { add(toJsonElement(it)) }
    }
    else -> JsonPrimitive(value.toString())
}

private object SourceMetadataParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val sourceBlock = Regex("""const\s+mangayomiSources\s*=\s*(\[[\s\S]*?]);""")

    fun parse(sourceCode: String): MangaSource {
        val parsed = sourceBlock.find(sourceCode)?.groupValues?.getOrNull(1)?.let { rawArray ->
            runCatching {
                val arr = json.parseToJsonElement(rawArray).jsonArray
                arr.firstOrNull()?.jsonObject
            }.getOrNull()
        }
        val name = parsed?.let { it["name"]?.jsonPrimitive?.contentOrNull }.orEmpty().ifBlank { "Proxy Source" }
        val lang = parsed?.let { it["lang"]?.jsonPrimitive?.contentOrNull }.orEmpty().ifBlank { "all" }
        val baseUrl = parsed?.let { it["baseUrl"]?.jsonPrimitive?.contentOrNull }.orEmpty()
        val iconUrl = parsed?.let { it["iconUrl"]?.jsonPrimitive?.contentOrNull }.orEmpty()
        val version = parsed?.let { it["version"]?.jsonPrimitive?.contentOrNull }.orEmpty().ifBlank { "0.0.1" }

        val hash = "proxy:${(name + lang + baseUrl + sourceCode.length).hashCode().toUInt().toString(16)}"
        return MangaSource(
            id = hash,
            name = name,
            lang = lang,
            baseUrl = baseUrl,
            iconUrl = iconUrl,
            version = version,
            isInstalled = true,
            engine = SourceEngine.JavaScript,
            sourceCodeUrl = "inline:$hash",
            localPath = "",
            canUninstall = true,
        )
    }
}
