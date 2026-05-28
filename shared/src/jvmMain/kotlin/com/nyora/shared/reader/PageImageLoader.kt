package com.nyora.shared.reader

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path

class PageImageLoader(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    /** Returns raw bytes — the platform UI is responsible for decoding them. */
    fun loadBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray {
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            val builder = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Nyora/1.0 (macOS)")
                .GET()
            headers.forEach { (name, value) -> builder.header(name, value) }
            val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() !in 200..299) {
                error("Image request failed with HTTP ${response.statusCode()}")
            }
            response.body()
        } else {
            Files.readAllBytes(Path.of(url))
        }
    }
}
