package com.nyora.shared

import com.nyora.shared.data.ExtensionInstaller
import com.nyora.shared.data.SourceCatalogClient
import com.nyora.shared.model.MangaSource
import com.nyora.shared.model.SourceEngine
import com.nyora.shared.proxy.NyoraRestServer
import com.nyora.shared.reader.PageImageLoader
import com.nyora.shared.repository.JsonToSqlMigration
import com.nyora.shared.repository.SqlDelightLibraryRepository
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

object HelperMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val repository = SqlDelightLibraryRepository()
        JsonToSqlMigration.runIfNeeded(repository)
        seedBuiltInSources(repository)

        val (mangaCount, sourceCount) = repository.count()
        println("DB: $mangaCount manga, $sourceCount sources")

        val facade = NyoraFacade(
            repository = repository,
            runtime = com.nyora.shared.extension.JvmExtensionRuntime(),
        )
        val server = NyoraRestServer(
            facade = facade,
            catalog = SourceCatalogClient(),
            installer = ExtensionInstaller(),
            pageLoader = PageImageLoader(),
        )

        val baseUrl = server.start()
        val port = baseUrl.substringAfterLast(":")
        println("Nyora helper listening at $baseUrl")

        val portFilePath = System.getProperty("nyora.helper.port-file")
            ?: System.getenv("NYORA_HELPER_PORT_FILE")
            ?: defaultPortFile().toString()
        val portFile = Path.of(portFilePath)
        Files.createDirectories(portFile.parent ?: Path.of("."))
        portFile.writeText(port)

        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { Files.deleteIfExists(portFile) }
            server.stop()
        })

        // Parent-PID watchdog: if our launcher (SwiftUI app) dies without a
        // clean termination, exit so we don't linger as a zombie helper.
        startParentWatchdog(args)

        Thread.currentThread().join()
    }

    private fun startParentWatchdog(args: Array<String>) {
        val watchedPid = args.firstNotNullOfOrNull { arg ->
            arg.removePrefix("--watch-pid=").takeIf { it != arg && it.isNotBlank() }?.toLongOrNull()
        } ?: System.getProperty("nyora.watch.pid")?.toLongOrNull()
        ?: System.getenv("NYORA_WATCH_PID")?.toLongOrNull()
        ?: return

        val thread = Thread {
            while (true) {
                if (!ProcessHandle.of(watchedPid).map { it.isAlive }.orElse(false)) {
                    System.err.println("Watched parent pid $watchedPid is gone, shutting down helper.")
                    System.exit(0)
                }
                Thread.sleep(1500)
            }
        }
        thread.isDaemon = true
        thread.name = "nyora-parent-watchdog"
        thread.start()
    }

    private fun defaultPortFile(): Path {
        val home = Path.of(System.getProperty("user.home"))
        val dir = home.resolve("Library").resolve("Application Support").resolve("Nyora")
        Files.createDirectories(dir)
        return dir.resolve("helper.port")
    }

    private fun seedBuiltInSources(repository: SqlDelightLibraryRepository) {
        val existing = repository.load().sources.associateBy { it.id }

        if (DEMO_JS_ID !in existing) {
            repository.upsertSource(MangaSource(
                id = DEMO_JS_ID,
                name = "Demo JS",
                lang = "en",
                baseUrl = "https://demo.nyora",
                sourceCodeUrl = "classpath:extensions/demo-manga.js",
                isInstalled = true,
                engine = SourceEngine.JavaScript,
                notes = "Built-in macOS demo parser.",
                localPath = "classpath:extensions/demo-manga.js",
                canUninstall = false,
                isPinned = true,
            ))
        }

        // Seed a curated set of Kotatsu native parser sources on first boot.
        // These don't require WebView and are widely usable in English.
        for (entry in BUILT_IN_PARSER_SOURCES) {
            if (entry.id in existing) continue
            repository.upsertSource(entry)
        }
    }

    private const val DEMO_JS_ID = "demo:javascript"

    /**
     * First-class native parser sources we ship pre-installed.
     * Pick popular English-language sources that don't need WebView/bitmap APIs.
     */
    private val BUILT_IN_PARSER_SOURCES: List<MangaSource> = listOf(
        nativeParser("MANGADEX", "MangaDex", "en", baseUrl = "https://mangadex.org"),
        nativeParser("MANGAPLUS", "MANGA Plus", "en", baseUrl = "https://mangaplus.shueisha.co.jp"),
        nativeParser("MANGAREADER", "MangaReader", "en", baseUrl = "https://mangareader.to"),
        nativeParser("ASURASCANS", "Asura Scans", "en", baseUrl = "https://asuracomic.net"),
        nativeParser("COMICK_FUN", "ComicK", "en", baseUrl = "https://comick.io"),
    )

    private fun nativeParser(
        enumName: String,
        displayName: String,
        lang: String,
        baseUrl: String,
    ) = MangaSource(
        id = "parser:$enumName",
        name = displayName,
        lang = lang,
        baseUrl = baseUrl,
        isInstalled = true,
        engine = SourceEngine.Parser,
        notes = "Native Kotatsu parser. Cookies and headers are session-only.",
        canUninstall = false,
    )
}
