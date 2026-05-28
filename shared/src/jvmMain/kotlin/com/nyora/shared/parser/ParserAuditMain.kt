package com.nyora.shared.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.io.PrintWriter
import java.util.concurrent.atomic.AtomicInteger

/**
 * Run every Kotatsu parser through getPopular(1) and report what works.
 *
 *   ./gradlew :shared:auditParsers
 *
 * Output: build/parser-audit.tsv (one row per source) + a summary on stdout.
 *
 * Per-call timeout is 25s. Network failures are tagged separately from
 * "this source needs WebView" or "interactive action required" so we can
 * tell capability gaps from infrastructure gaps.
 */
object ParserAuditMain {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val limit = args.firstOrNull { it.startsWith("--limit=") }
            ?.removePrefix("--limit=")
            ?.toIntOrNull()
        val outPath = args.firstOrNull { it.startsWith("--out=") }
            ?.removePrefix("--out=")
            ?: "build/parser-audit.tsv"

        val concurrency = args.firstOrNull { it.startsWith("--concurrency=") }
            ?.removePrefix("--concurrency=")
            ?.toIntOrNull()
            ?: 20

        val context = JvmMangaLoaderContext()
        val all = MangaParserSource.entries.toList()
        val toRun = if (limit != null) all.take(limit) else all
        val total = toRun.size
        val done = AtomicInteger(0)

        val startNanos = System.nanoTime()
        val semaphore = Semaphore(concurrency)
        val writer = PrintWriter(outPath).apply { println("name\tstatus\tentries\tdurationMs\terror") }
        val writeLock = Any()

        val results = coroutineScope {
            toRun.map { source ->
                async(Dispatchers.IO) {
                    val row = semaphore.withPermit { audit(context, source) }
                    val n = done.incrementAndGet()
                    synchronized(writeLock) {
                        writer.println(listOf(source.name, row.status, row.entries, row.durationMs, row.error.orEmpty().take(140)).joinToString("\t"))
                        writer.flush()
                    }
                    if (n % 50 == 0 || n == total) {
                        val pct = (n * 100.0 / total)
                        val elapsedS = (System.nanoTime() - startNanos) / 1_000_000_000.0
                        println("[$n/$total ${"%5.1f".format(pct)}%]  elapsed ${"%.1fs".format(elapsedS)}")
                    }
                    row
                }
            }.awaitAll()
        }
        writer.close()

        val byStatus = results.groupingBy { it.status }.eachCount()
        println()
        println("=== Summary ===")
        byStatus.toSortedMap().forEach { (status, count) ->
            println("  ${status.padEnd(28)}  $count")
        }
        println("  TOTAL ${results.size}")
        println()
        println("Wrote $outPath")
    }

    private data class AuditRow(
        val status: String,
        val entries: Int,
        val durationMs: Long,
        val error: String?,
    )

    private suspend fun audit(
        context: JvmMangaLoaderContext,
        source: MangaParserSource,
    ): AuditRow {
        val start = System.nanoTime()
        return try {
            withTimeout(15_000L) {
                val parser = context.newParserInstance(source)
                val sortOrders = parser.availableSortOrders
                val sortOrder = listOf(SortOrder.POPULARITY, SortOrder.UPDATED, SortOrder.NEWEST)
                    .firstOrNull { it in sortOrders }
                    ?: sortOrders.firstOrNull()
                    ?: SortOrder.POPULARITY
                val list = parser.getList(1, sortOrder, MangaListFilter())
                AuditRow(
                    status = if (list.isEmpty()) "OK_EMPTY" else "OK",
                    entries = list.size,
                    durationMs = (System.nanoTime() - start) / 1_000_000,
                    error = null,
                )
            }
        } catch (t: Throwable) {
            AuditRow(
                status = classifyFailure(t),
                entries = 0,
                durationMs = (System.nanoTime() - start) / 1_000_000,
                error = t.message,
            )
        }
    }

    private fun classifyFailure(t: Throwable): String {
        // Unwrap up to 4 levels of caused-by.
        var cause: Throwable? = t
        for (i in 0..4) {
            cause = cause ?: break
            val msg = cause.message.orEmpty()
            val name = cause::class.qualifiedName.orEmpty()
            when {
                cause is InteractiveSourceUnsupportedException -> return "BROWSER_ACTION"
                cause is UnsupportedOperationException && msg.contains("JavaScript", true) -> return "JS_EVAL"
                cause is UnsupportedOperationException && msg.contains("Bitmap", true) -> return "BITMAP_API"
                cause is UnsupportedOperationException -> return "UNSUPPORTED_OP"
                name.endsWith("AuthRequiredException") -> return "AUTH_REQUIRED"
                name.endsWith("ContentUnavailableException") -> return "CONTENT_UNAVAILABLE"
                name.endsWith("CloudFlareProtectedException") -> return "CLOUDFLARE"
                name.endsWith("TooManyRequestExceptions") -> return "RATE_LIMITED"
                name.endsWith("TimeoutCancellationException") -> return "TIMEOUT"
                name.contains("UnknownHost", true) -> return "DNS"
                name.contains("ConnectException", true) || msg.contains("Connection", true) -> return "NETWORK"
                name.contains("SSL", true) || msg.contains("certificate", true) -> return "SSL"
                msg.contains("403") -> return "HTTP_403"
                msg.contains("404") -> return "HTTP_404"
                msg.contains("503") -> return "HTTP_503"
                msg.contains("502") -> return "HTTP_502"
                msg.contains("500") -> return "HTTP_500"
                msg.contains("429") -> return "RATE_LIMITED"
            }
            cause = cause.cause
        }
        return "OTHER"
    }
}
