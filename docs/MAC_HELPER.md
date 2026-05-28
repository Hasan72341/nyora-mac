# Nyora Mac Helper Sidecar

The macOS SwiftUI app keeps its UI native, but the *manga engine* — GraalVM JavaScript, the Mihon `/dalvik` proxy protocol, source catalog parsing — runs on the JVM. We package those as a small "helper" sidecar process.

## Why a sidecar

GraalVM JS and JVM-based HTML/CSS parsers don't have practical macOS-native equivalents. Trying to call into them from Swift directly would require linking the JVM into the app process, which is fragile (JNI from Swift, shared GC tuning, codesign quirks). Running them as a separate JVM process gives us:

- A clean process boundary — a buggy parser can't crash the SwiftUI window.
- Reuse of the existing `MihonProxyServer` Kotlin code unchanged.
- The option to ship the helper as a LaunchAgent later (so it survives between app sessions).

## Lifecycle (current pre-alpha)

1. User runs `macApp/scripts/launch-helper.sh` (or you `gradle :shared:run`).
2. `HelperMain` boots `MihonProxyServer` on an ephemeral loopback port.
3. The port is written to `~/Library/Application Support/Nyora/helper.port`.
4. SwiftUI's `NyoraHelperBridge.locateHelper()` reads that file and probes `GET http://127.0.0.1:<port>/`.

On shutdown (Ctrl-C, SIGTERM), the shutdown hook deletes the port file and stops the server.

## Lifecycle (planned)

Eventually the SwiftUI app should:

1. Bundle a `jdk/` directory built via `jlink` (just `java.base`, `java.net.http`, `java.naming`, plus the Truffle/GraalJS modules).
2. Launch the helper via `Process` on app open, passing `nyora.helper.port-file` via system property.
3. Watch for unexpected exit and restart up to N times.
4. Pass shutdown via SIGTERM so the port file is cleaned up.

## Protocol surface (target)

In addition to the existing `/dalvik` (Mihon-style JSON-RPC) endpoint, the helper should expose REST sugar that's nicer for SwiftUI to consume:

| Method | Path | Notes |
|---|---|---|
| `GET` | `/sources` | List installed + catalog sources. |
| `POST` | `/sources/refresh` | Re-fetch all configured repos. |
| `POST` | `/sources/{id}/install` | Download extension artifact. |
| `DELETE` | `/sources/{id}` | Uninstall. |
| `GET` | `/sources/{id}/popular?page=` | Popular page. |
| `GET` | `/sources/{id}/search?q=&page=` | Search. |
| `GET` | `/manga/{id}` | Details + chapter list. |
| `GET` | `/manga/{id}/chapters/{cid}/pages` | Page URLs + per-page headers. |
| `GET` | `/image?u=…&h=…` | Image proxy that injects per-source headers for `<AsyncImage>`. |

The image proxy endpoint matters: SwiftUI's `AsyncImage` can't attach arbitrary `Referer` / `User-Agent` headers, but many manga CDNs require them. Routing image fetches through the helper sidesteps that.

## Failure modes to handle

- Port file present but helper not listening → SwiftUI should treat as down and offer "Restart Helper" (already in Settings).
- Helper port file is stale across reboots → check process is alive *and* port responds before considering it healthy.
- Helper crash mid-request → SwiftUI's `URLSession` timeouts surface the error, AppState shows a status banner.
