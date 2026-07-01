import Foundation
import SwiftUI
import Security

/// The tracking services Nyora can link. Client IDs / secrets mirror the
/// canonical values in nyora-android `app/src/main/res/values/constants.xml`
/// so every platform authenticates against the same registered OAuth apps.
enum TrackerService: String, CaseIterable, Identifiable, Sendable {
    case anilist
    case myanimelist
    case kitsu
    case shikimori

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .anilist:     return "AniList"
        case .myanimelist: return "MyAnimeList"
        case .kitsu:       return "Kitsu"
        case .shikimori:   return "Shikimori"
        }
    }

    /// Path segment used by the local helper's
    /// `/tracker/<slug>/{search,scrobble}` passthrough endpoints.
    var endpointSlug: String {
        switch self {
        case .anilist:     return "anilist"
        case .myanimelist: return "myanimelist"
        case .kitsu:       return "kitsu"
        case .shikimori:   return "shikimori"
        }
    }

    /// Real OAuth client IDs (mirror nyora-android constants.xml).
    var clientId: String {
        switch self {
        case .anilist:     return "9887"
        case .myanimelist: return "66e27ac5d5a1764e944677b42e2c4737"
        case .kitsu:       return "dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd"
        case .shikimori:   return "Mw6F0tPEOgyV7F9U9Twg50Q8SndMY7hzIOfXg0AX_XU"
        }
    }

    /// Client secret where the provider's token exchange requires it.
    /// AniList uses the implicit grant and MyAnimeList is a PKCE public client,
    /// so neither needs a secret here.
    var clientSecret: String? {
        switch self {
        case .anilist:     return nil
        case .myanimelist: return nil
        case .kitsu:       return "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151"
        case .shikimori:   return "euBMt1GGRSDpVIFQVPxZrO7Kh6X4gWyv0dABuj4B-M8"
        }
    }

    enum GrantKind: Sendable {
        /// Token returned directly in the redirect fragment (AniList).
        case implicit
        /// Authorization-code redirect + token exchange (MyAnimeList, Shikimori).
        case authorizationCode
        /// Resource-owner password grant, no browser (Kitsu).
        case password
    }

    var grantKind: GrantKind {
        switch self {
        case .anilist:                 return .implicit
        case .kitsu:                   return .password
        case .myanimelist, .shikimori: return .authorizationCode
        }
    }

    var authorizeEndpoint: URL? {
        switch self {
        case .anilist:     return URL(string: "https://anilist.co/api/v2/oauth/authorize")
        case .myanimelist: return URL(string: "https://myanimelist.net/v1/oauth2/authorize")
        case .shikimori:   return URL(string: "https://shikimori.one/oauth/authorize")
        case .kitsu:       return nil
        }
    }

    var tokenEndpoint: URL? {
        switch self {
        case .anilist:     return URL(string: "https://anilist.co/api/v2/oauth/token")
        case .myanimelist: return URL(string: "https://myanimelist.net/v1/oauth2/token")
        case .shikimori:   return URL(string: "https://shikimori.one/oauth/token")
        case .kitsu:       return URL(string: "https://kitsu.io/api/oauth/token")
        }
    }

    /// Extra OAuth scope a provider needs on the authorize request.
    var authScope: String? {
        self == .shikimori ? "user_rates" : nil
    }

    /// Providers that require a descriptive `User-Agent` header.
    var requiresUserAgent: Bool { self == .shikimori }
}

/// Multi-service tracker settings backed by Keychain (tokens) + UserDefaults
/// (enabled flags, per-manga remote-id links). Keychain storage means tokens
/// survive reinstalls and never land in backup JSON.
///
/// AniList/MyAnimeList/Shikimori link via browser OAuth; Kitsu uses a
/// password grant. Legacy AniList personal-token users are migrated on first
/// launch so nobody has to re-authenticate.
@MainActor
final class TrackerSettings: ObservableObject {
    /// Drives UI reactivity — the real tokens live in the Keychain.
    @Published private var enabledMap: [String: Bool] = [:]
    @Published private var tokenPresent: [String: Bool] = [:]
    @Published private var linksMap: [String: [String: Int]] = [:]

    init() {
        for service in TrackerService.allCases {
            enabledMap[service.rawValue] = Self.ud.bool(forKey: Keys.enabled(service))
            tokenPresent[service.rawValue] = Self.keychainGet(account: Keys.tokenAccount(service)) != nil
            if let data = Self.ud.data(forKey: Keys.links(service)),
               let dict = try? JSONDecoder().decode([String: Int].self, from: data) {
                linksMap[service.rawValue] = dict
            }
        }

        // Migrate the pre-multi-service AniList personal-token keychain account.
        if tokenPresent[TrackerService.anilist.rawValue] != true,
           let legacy = Self.keychainGet(account: Keys.legacyAnilistAccount), !legacy.isEmpty {
            Self.keychainSet(account: Keys.tokenAccount(.anilist), value: legacy)
            tokenPresent[TrackerService.anilist.rawValue] = true
        }
    }

    // MARK: - Generic per-service API

    func isEnabled(_ service: TrackerService) -> Bool {
        enabledMap[service.rawValue] ?? false
    }

    func setEnabled(_ service: TrackerService, _ value: Bool) {
        enabledMap[service.rawValue] = value
        Self.ud.set(value, forKey: Keys.enabled(service))
    }

    func hasToken(_ service: TrackerService) -> Bool {
        tokenPresent[service.rawValue] ?? false
    }

    func token(_ service: TrackerService) -> String {
        Self.keychainGet(account: Keys.tokenAccount(service)) ?? ""
    }

    func setToken(_ service: TrackerService, _ value: String) {
        if value.isEmpty {
            Self.keychainDelete(account: Keys.tokenAccount(service))
            tokenPresent[service.rawValue] = false
        } else {
            Self.keychainSet(account: Keys.tokenAccount(service), value: value)
            tokenPresent[service.rawValue] = true
        }
    }

    func refreshToken(_ service: TrackerService) -> String? {
        Self.keychainGet(account: Keys.refreshAccount(service))
    }

    func setRefreshToken(_ service: TrackerService, _ value: String?) {
        if let value, !value.isEmpty {
            Self.keychainSet(account: Keys.refreshAccount(service), value: value)
        } else {
            Self.keychainDelete(account: Keys.refreshAccount(service))
        }
    }

    /// `mangaId -> remote media id`. Lets the reader scrobble without
    /// re-searching every time.
    func links(_ service: TrackerService) -> [String: Int] {
        linksMap[service.rawValue] ?? [:]
    }

    func setLink(_ service: TrackerService, mangaId: String, mediaId: Int) {
        var dict = linksMap[service.rawValue] ?? [:]
        dict[mangaId] = mediaId
        setLinks(service, dict)
    }

    private func setLinks(_ service: TrackerService, _ dict: [String: Int]) {
        linksMap[service.rawValue] = dict
        if let data = try? JSONEncoder().encode(dict) {
            Self.ud.set(data, forKey: Keys.links(service))
        }
    }

    /// Clear a service's tokens + scrobble flag (sign out).
    func disconnect(_ service: TrackerService) {
        setToken(service, "")
        setRefreshToken(service, nil)
        setEnabled(service, false)
    }

    // MARK: - Backward-compatible AniList accessors (reader scrobbler)

    var anilistEnabled: Bool {
        get { isEnabled(.anilist) }
        set { setEnabled(.anilist, newValue) }
    }

    var anilistToken: String {
        get { token(.anilist) }
        set { setToken(.anilist, newValue) }
    }

    var anilistLinks: [String: Int] {
        get { links(.anilist) }
        set { setLinks(.anilist, newValue) }
    }

    /// True when AniList is linked and scrobbling is enabled.
    var isConfigured: Bool {
        isEnabled(.anilist) && hasToken(.anilist)
    }

    // MARK: - Keychain helpers

    private static func keychainSet(account: String, value: String) {
        let data = Data(value.utf8)
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "com.nyora.tracker",
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(q as CFDictionary)
        var add = q
        add[kSecValueData as String] = data
        SecItemAdd(add as CFDictionary, nil)
    }

    private static func keychainGet(account: String) -> String? {
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "com.nyora.tracker",
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data,
              let str = String(data: data, encoding: .utf8)
        else { return nil }
        return str
    }

    private static func keychainDelete(account: String) {
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "com.nyora.tracker",
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(q as CFDictionary)
    }

    private static let ud = UserDefaults.standard
    private enum Keys {
        static let legacyAnilistAccount = "anilist.personal.token"
        static func enabled(_ s: TrackerService) -> String { "nyora.tracker.\(s.rawValue).enabled" }
        static func links(_ s: TrackerService) -> String { "nyora.tracker.\(s.rawValue).links" }
        static func tokenAccount(_ s: TrackerService) -> String { "\(s.rawValue).oauth.token" }
        static func refreshAccount(_ s: TrackerService) -> String { "\(s.rawValue).oauth.refresh" }
    }
}
