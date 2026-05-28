package com.nyora.shared.extension

import com.nyora.shared.model.Manga
import com.nyora.shared.model.MangaChapter
import com.nyora.shared.model.MangaPage
import com.nyora.shared.model.MangaSource
import com.nyora.shared.model.MangaSourceRef
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.io.path.Path
import kotlin.io.path.readText

class JavaScriptExtensionService(
    private val source: MangaSource,
    private val sourceCodeOverride: String? = null,
    private val sourcePreferencesJson: String = "[]",
) : MangaExtensionService {
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val context: Context by lazy { createContext() }
    private val extension: Value by lazy {
        context.getBindings("js").getMember("__extension")
            ?: error("JS extension entry point was not created")
    }

    override val supportsLatest: Boolean
        get() = extension.getMember("supportsLatest")?.asBoolean() ?: false

    override fun getHeaders(): Map<String, String> {
        val result = runCatching {
            extension.invokeMember("getHeaders", source.baseUrl)
        }.getOrNull() ?: return emptyMap()
        val plain = toPlain(result)
        return (plain as? Map<*, *>)?.mapNotNull { (key, value) ->
            val stringKey = key?.toString() ?: return@mapNotNull null
            stringKey to value?.toString().orEmpty()
        }?.toMap().orEmpty()
    }

    override suspend fun getPopular(page: Int): MangaSearchPage =
        mapMangaPage(invokeAsync("getPopular", page))

    override suspend fun getLatest(page: Int): MangaSearchPage =
        mapMangaPage(invokeAsync("getLatestUpdates", page))

    override suspend fun search(
        query: String,
        page: Int,
        filters: List<SourceFilter>,
    ): MangaSearchPage {
        val jsFilters = filters.map { filter ->
            when (filter) {
                is TextSourceFilter -> mapOf("name" to filter.name, "state" to filter.value, "type_name" to "TextFilter")
                is SelectSourceFilter -> mapOf("name" to filter.name, "state" to filter.selectedIndex, "type_name" to "SelectFilter")
                is CheckSourceFilter -> mapOf("name" to filter.name, "state" to filter.checked, "type_name" to "CheckBox")
            }
        }
        return mapMangaPage(invokeAsync("search", query, page, jsFilters))
    }

    override suspend fun getDetails(url: String): MangaDetails {
        val result = invokeAsync("getDetail", url)
        val manga = mapManga(result) ?: error("Extension returned no manga for details")
        val chapters = result.getMember("chapters")?.let { mapChapters(it) }.orEmpty()
        return MangaDetails(manga = manga, chapters = chapters)
    }

    override suspend fun getPageList(chapter: MangaChapter): List<MangaPage> {
        return mapPages(invokeAsync("getPageList", chapter.url))
    }

    private fun createContext(): Context {
        val sourceText = loadSourceText()
        val prelude = """
            const __source = ${jsonQuote(sourceToJson())};
            const __preferences = $sourcePreferencesJson;
            class MProvider {
                get source() { return __source; }
                get supportsLatest() { return false; }
                getHeaders(url) { return {}; }
                async getPopular(page) { throw new Error("getPopular not implemented"); }
                async getLatestUpdates(page) { throw new Error("getLatestUpdates not implemented"); }
                async search(query, page, filters) { throw new Error("search not implemented"); }
                async getDetail(url) { throw new Error("getDetail not implemented"); }
                async getPageList(url) { throw new Error("getPageList not implemented"); }
                getFilterList() { return []; }
                getSourcePreferences() { return __preferences; }
            }
            class Client {
                async get(url, headers) { return host.http("GET", url, headers, null); }
                async post(url, headers, body) { return host.http("POST", url, headers, body); }
                async head(url, headers) { return host.http("HEAD", url, headers, null); }
            }
            console.log = (...args) => host.log(args.map(String).join(" "));
            console.warn = (...args) => host.log(args.map(String).join(" "));
            console.error = (...args) => host.log(args.map(String).join(" "));
            String.prototype.substringAfter = function(pattern) {
                const index = this.indexOf(pattern);
                return index === -1 ? this.toString() : this.substring(index + pattern.length);
            };
            String.prototype.substringAfterLast = function(pattern) {
                const index = this.lastIndexOf(pattern);
                return index === -1 ? this.toString() : this.substring(index + pattern.length);
            };
        """.trimIndent()

        return Context.newBuilder("js")
            .option("engine.WarnInterpreterOnly", "false")
            .allowAllAccess(true)
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup { true }
            .build()
            .also { ctx ->
                ctx.getBindings("js").putMember("host", HostBridge())
                ctx.eval(
                    "js",
                    prelude + "\n" + sourceText + "\n;globalThis.__extension = typeof extention !== 'undefined' ? extention : (typeof DefaultExtension !== 'undefined' ? new DefaultExtension() : (typeof main === 'function' ? main(__source) : null));",
                )
            }
    }

    private fun invokeAsync(name: String, vararg args: Any?): Value =
        awaitPromise(extension.invokeMember(name, *args))

    private fun awaitPromise(value: Value): Value {
        if (!value.hasMember("then")) return value
        var resolved: Value? = null
        var rejected: String? = null
        value.invokeMember(
            "then",
            ProxyExecutable { args ->
                resolved = args.firstOrNull()
                null
            },
            ProxyExecutable { args ->
                rejected = args.firstOrNull()?.toString() ?: "JavaScript promise rejected"
                null
            },
        )
        repeat(1000) {
            if (resolved != null || rejected != null) return@repeat
            context.eval("js", "undefined")
            Thread.sleep(1)
        }
        rejected?.let { error(it) }
        return resolved ?: error("JavaScript promise did not resolve")
    }

    private fun loadSourceText(): String {
        sourceCodeOverride?.takeIf { it.isNotBlank() }?.let { return it }
        val localPath = source.localPath
        if (localPath.startsWith("classpath:")) {
            val resource = localPath.removePrefix("classpath:")
            val stream = javaClass.classLoader.getResourceAsStream(resource)
                ?: error("Missing built-in extension resource: $resource")
            return stream.bufferedReader().use { it.readText() }
        }
        if (localPath.isNotBlank()) {
            return Path(localPath).readText()
        }
        if (source.sourceCodeUrl.endsWith(".js")) {
            val response = httpClient.send(
                HttpRequest.newBuilder(URI.create(source.sourceCodeUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            if (response.statusCode() !in 200..299) {
                error("Failed to load JavaScript source with HTTP ${response.statusCode()}")
            }
            return response.body()
        }
        error("No JavaScript extension source available for ${source.name}")
    }

    private fun sourceToJson(): String = buildString {
        append("{")
        append("\"id\":\"").append(source.id).append("\",")
        append("\"name\":\"").append(source.name).append("\",")
        append("\"baseUrl\":\"").append(source.baseUrl).append("\",")
        append("\"lang\":\"").append(source.lang).append("\",")
        append("\"sourceCodeUrl\":\"").append(source.sourceCodeUrl).append("\"")
        append("}")
    }

    private fun jsonQuote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun mapMangaPage(value: Value): MangaSearchPage {
        val list = value.getMember("list") ?: value
        val entries = if (list.hasArrayElements()) {
            (0 until list.arraySize).mapNotNull { index -> mapManga(list.getArrayElement(index)) }
        } else emptyList()
        val hasNextPage = value.getMember("hasNextPage")?.asBoolean() ?: false
        return MangaSearchPage(entries = entries, hasNextPage = hasNextPage)
    }

    private fun mapManga(value: Value): Manga? {
        if (value.isNull) return null
        return Manga(
            id = value.getMember("link")?.asString().orEmpty(),
            title = value.getMember("name")?.asString().orEmpty(),
            authors = listOfNotNull(value.getMember("author")?.asString()?.takeIf { it.isNotBlank() }),
            source = MangaSourceRef.Script(source.name),
            coverUrl = value.getMember("imageUrl")?.asString().orEmpty(),
            tags = value.getMember("genre")?.let { member ->
                if (member.hasArrayElements()) {
                    (0 until member.arraySize).map { idx ->
                        val name = member.getArrayElement(idx).asString()
                        com.nyora.shared.model.MangaTag(key = name.lowercase(), title = name)
                    }
                } else emptyList()
            }.orEmpty(),
        )
    }

    private fun mapChapters(value: Value): List<MangaChapter> {
        if (!value.hasArrayElements()) return emptyList()
        return (0 until value.arraySize).mapNotNull { index ->
            val chapter = value.getArrayElement(index)
            if (chapter.isNull) null else MangaChapter(
                id = chapter.getMember("url")?.asString().orEmpty(),
                title = chapter.getMember("name")?.asString().orEmpty(),
                url = chapter.getMember("url")?.asString().orEmpty(),
                index = index.toInt(),
            )
        }
    }

    private fun mapPages(value: Value): List<MangaPage> {
        if (!value.hasArrayElements()) return emptyList()
        return (0 until value.arraySize).mapNotNull { index ->
            val page = value.getArrayElement(index)
            if (page.isNull) return@mapNotNull null
            when {
                page.isString -> MangaPage(page.asString())
                page.hasMembers() -> MangaPage(
                    url = page.getMember("url")?.asString().orEmpty(),
                    headers = page.getMember("headers")?.let { headers ->
                        if (headers.hasMembers()) {
                            headers.memberKeys.associateWith { key ->
                                headers.getMember(key)?.asString().orEmpty()
                            }
                        } else emptyMap()
                    }.orEmpty(),
                )
                else -> null
            }
        }
    }

    private fun toPlain(value: Value): Any? = when {
        value.isNull -> null
        value.isBoolean -> value.asBoolean()
        value.isNumber -> when {
            value.fitsInInt() -> value.asInt()
            value.fitsInLong() -> value.asLong()
            else -> value.asDouble()
        }
        value.isString -> value.asString()
        value.hasArrayElements() -> (0 until value.arraySize).map { index -> toPlain(value.getArrayElement(index)) }
        value.hasMembers() -> value.memberKeys.associateWith { key -> toPlain(value.getMember(key)) }
        else -> value.toString()
    }

    private inner class HostBridge {
        @HostAccess.Export
        fun log(message: String) {
            println("[JS:${source.name}] $message")
        }

        @HostAccess.Export
        fun http(method: String, url: String, headers: Value?, body: Value?): JsHttpResponse {
            val builder = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Nyora/1.0 (macOS)")
            if (headers != null && headers.hasMembers()) {
                headers.memberKeys.forEach { key ->
                    builder.header(key, headers.getMember(key).asString())
                }
            }
            val request = when (method.uppercase()) {
                "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body?.asString() ?: ""))
                "HEAD" -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody())
                else -> builder.GET()
            }.build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            return JsHttpResponse(
                body = response.body(),
                headers = response.headers().map().mapValues { (_, v) -> v.joinToString(",") },
                statusCode = response.statusCode(),
            )
        }
    }
}

data class JsHttpResponse(
    val body: String,
    val headers: Map<String, String>,
    val statusCode: Int,
)
