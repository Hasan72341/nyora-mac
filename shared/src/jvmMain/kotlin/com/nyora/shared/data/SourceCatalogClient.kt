package com.nyora.shared.data

import com.nyora.shared.model.MangaRepo
import com.nyora.shared.model.MangaSource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class SourceCatalogClient(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    fun fetch(repo: MangaRepo): List<MangaSource> {
        val request = HttpRequest.newBuilder(URI.create(repo.indexUrl))
            .header("User-Agent", "Nyora/1.0 (macOS)")
            .GET()
            .build()
        val body = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body()
        return SourceCatalogParser.parseIndex(repo, body)
    }
}
