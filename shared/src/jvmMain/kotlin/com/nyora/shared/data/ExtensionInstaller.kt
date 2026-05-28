package com.nyora.shared.data

import com.nyora.shared.model.MangaSource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

class ExtensionInstaller(
    private val extensionsDir: Path = JsonStore.defaultStorePath().parent.resolve("extensions"),
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    fun install(source: MangaSource): MangaSource {
        require(source.sourceCodeUrl.isNotBlank()) {
            "Source has no downloadable extension artifact"
        }
        Files.createDirectories(extensionsDir)
        val filename = source.sourceCodeUrl
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { "${source.id.replace(':', '_')}.bin" }
        val target = extensionsDir.resolve(filename)
        val request = HttpRequest.newBuilder(URI.create(source.sourceCodeUrl))
            .header("User-Agent", "Nyora/1.0 (macOS)")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) {
            error("Download failed with HTTP ${response.statusCode()}")
        }
        Files.write(target, response.body())
        return source.copy(
            isInstalled = true,
            localPath = target.toAbsolutePath().toString(),
            installedAt = System.currentTimeMillis(),
        )
    }

    fun uninstall(source: MangaSource): MangaSource {
        val path = source.localPath.takeIf { it.isNotBlank() }?.let(Path::of)
        if (path != null && path.exists()) {
            path.deleteIfExists()
        }
        return source.copy(isInstalled = false, localPath = "", installedAt = 0)
    }
}
