import AuthenticationServices
import AppKit
import Foundation

/// Browser + password OAuth for the tracking services.
///
/// AniList uses the implicit grant (token in the redirect fragment),
/// MyAnimeList and Shikimori use the authorization-code grant (MyAnimeList
/// with PKCE), and Kitsu uses a resource-owner password grant. Client IDs and
/// secrets come from `TrackerService`, which mirrors the nyora-android
/// `constants.xml` values.
///
/// NOTE: the custom-scheme redirect (`nyora://oauth`) must be registered in
/// each provider's developer console (AniList app redirect URL, MyAnimeList
/// app redirect, Shikimori OAuth application redirect) for the browser flow to
/// complete. Until that registration lands, the settings screen also exposes a
/// manual access-token field so linking works today.
@MainActor
final class TrackerOAuth: NSObject, ASWebAuthenticationPresentationContextProviding {
    static let shared = TrackerOAuth()

    static let callbackScheme = "nyora"
    static let redirectURI = "nyora://oauth"

    struct AuthResult: Sendable {
        let accessToken: String
        let refreshToken: String?
    }

    enum AuthError: Error, LocalizedError {
        case cancelled
        case notSupported
        case failed(String)

        var errorDescription: String? {
            switch self {
            case .cancelled:    return "Sign-in was cancelled."
            case .notSupported: return "This tracker does not support browser sign-in."
            case .failed(let m): return m
            }
        }
    }

    private var currentSession: ASWebAuthenticationSession?

    // MARK: - Public entry points

    /// Browser OAuth for the implicit + authorization-code services.
    func login(_ service: TrackerService) async throws -> AuthResult {
        switch service.grantKind {
        case .implicit:          return try await implicitLogin(service)
        case .authorizationCode: return try await codeLogin(service)
        case .password:          throw AuthError.notSupported
        }
    }

    /// Resource-owner password grant (Kitsu).
    func loginWithPassword(_ service: TrackerService,
                           username: String,
                           password: String) async throws -> AuthResult {
        guard service.grantKind == .password, let tokenURL = service.tokenEndpoint else {
            throw AuthError.notSupported
        }
        var form: [String: String] = [
            "grant_type": "password",
            "username": username,
            "password": password,
            "client_id": service.clientId,
        ]
        if let secret = service.clientSecret { form["client_secret"] = secret }
        return try await postToken(tokenURL, form: form, userAgent: service.requiresUserAgent)
    }

    // MARK: - Grant flows

    private func implicitLogin(_ service: TrackerService) async throws -> AuthResult {
        guard let authorize = service.authorizeEndpoint,
              var comps = URLComponents(url: authorize, resolvingAgainstBaseURL: false) else {
            throw AuthError.notSupported
        }
        comps.queryItems = [
            URLQueryItem(name: "client_id", value: service.clientId),
            URLQueryItem(name: "response_type", value: "token"),
            URLQueryItem(name: "redirect_uri", value: Self.redirectURI),
        ]
        guard let url = comps.url else { throw AuthError.failed("Bad authorize URL.") }
        let callback = try await presentAuth(url: url)
        // Implicit tokens are returned in the URL fragment.
        guard let token = fragmentValue(callback, key: "access_token") else {
            throw AuthError.failed("No access token returned.")
        }
        return AuthResult(accessToken: token, refreshToken: fragmentValue(callback, key: "refresh_token"))
    }

    private func codeLogin(_ service: TrackerService) async throws -> AuthResult {
        guard let authorize = service.authorizeEndpoint,
              let tokenURL = service.tokenEndpoint,
              var comps = URLComponents(url: authorize, resolvingAgainstBaseURL: false) else {
            throw AuthError.notSupported
        }
        // MyAnimeList mandates PKCE; it only supports the "plain" method, so the
        // challenge equals the verifier.
        let verifier = Self.randomCodeVerifier()
        var items: [URLQueryItem] = [
            URLQueryItem(name: "client_id", value: service.clientId),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "redirect_uri", value: Self.redirectURI),
        ]
        if service == .myanimelist {
            items.append(URLQueryItem(name: "code_challenge", value: verifier))
            items.append(URLQueryItem(name: "code_challenge_method", value: "plain"))
        }
        if let scope = service.authScope {
            items.append(URLQueryItem(name: "scope", value: scope))
        }
        comps.queryItems = items
        guard let url = comps.url else { throw AuthError.failed("Bad authorize URL.") }

        let callback = try await presentAuth(url: url)
        guard let code = queryValue(callback, key: "code") else {
            throw AuthError.failed(queryValue(callback, key: "error") ?? "No authorization code returned.")
        }

        var form: [String: String] = [
            "grant_type": "authorization_code",
            "client_id": service.clientId,
            "code": code,
            "redirect_uri": Self.redirectURI,
        ]
        if let secret = service.clientSecret { form["client_secret"] = secret }
        if service == .myanimelist { form["code_verifier"] = verifier }
        return try await postToken(tokenURL, form: form, userAgent: service.requiresUserAgent)
    }

    // MARK: - HTTP

    private func postToken(_ url: URL, form: [String: String], userAgent: Bool) async throws -> AuthResult {
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        if userAgent { req.setValue("Nyora", forHTTPHeaderField: "User-Agent") }
        req.httpBody = Self.formEncode(form).data(using: .utf8)

        let (data, response) = try await URLSession.shared.data(for: req)
        let status = (response as? HTTPURLResponse)?.statusCode ?? -1
        guard status < 400 else {
            throw AuthError.failed("Token exchange failed (HTTP \(status)).")
        }
        struct TokenResponse: Decodable {
            let access_token: String?
            let refresh_token: String?
        }
        guard let decoded = try? JSONDecoder().decode(TokenResponse.self, from: data),
              let token = decoded.access_token, !token.isEmpty else {
            throw AuthError.failed("Malformed token response.")
        }
        return AuthResult(accessToken: token, refreshToken: decoded.refresh_token)
    }

    private func presentAuth(url: URL) async throws -> URL {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<URL, Error>) in
            let box = OAuthContinuationBox(cont)
            let session = ASWebAuthenticationSession(
                url: url,
                callbackURLScheme: Self.callbackScheme
            ) { callback, error in
                if let error {
                    if (error as? ASWebAuthenticationSessionError)?.code == .canceledLogin {
                        Task { @MainActor in box.resume(.failure(AuthError.cancelled)) }
                    } else {
                        Task { @MainActor in box.resume(.failure(AuthError.failed(error.localizedDescription))) }
                    }
                    return
                }
                guard let callback else {
                    Task { @MainActor in box.resume(.failure(AuthError.failed("No callback URL."))) }
                    return
                }
                Task { @MainActor in box.resume(.success(callback)) }
            }
            session.presentationContextProvider = self
            session.prefersEphemeralWebBrowserSession = false
            currentSession = session
            NSApplication.shared.activate(ignoringOtherApps: true)
            if !session.start() {
                box.resume(.failure(AuthError.failed("Could not open the sign-in window.")))
            }
        }
    }

    nonisolated func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        MainActor.assumeIsolated {
            NSApplication.shared.keyWindow
                ?? NSApplication.shared.windows.first(where: { $0.isVisible })
                ?? ASPresentationAnchor()
        }
    }

    // MARK: - Utilities

    private func fragmentValue(_ url: URL, key: String) -> String? {
        guard let fragment = URLComponents(url: url, resolvingAgainstBaseURL: false)?.fragment else { return nil }
        for pair in fragment.split(separator: "&") {
            let kv = pair.split(separator: "=", maxSplits: 1)
            if kv.count == 2, kv[0] == key {
                return String(kv[1]).removingPercentEncoding ?? String(kv[1])
            }
        }
        return nil
    }

    private func queryValue(_ url: URL, key: String) -> String? {
        URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .queryItems?
            .first(where: { $0.name == key })?
            .value
    }

    private static func randomCodeVerifier() -> String {
        let charset = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")
        return String((0..<64).map { _ in charset[Int.random(in: 0..<charset.count)] })
    }

    private static func formEncode(_ form: [String: String]) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return form.map { key, value in
            let k = key.addingPercentEncoding(withAllowedCharacters: allowed) ?? key
            let v = value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
            return "\(k)=\(v)"
        }.joined(separator: "&")
    }
}

/// Bridges an `ASWebAuthenticationSession` completion callback back to a
/// checked continuation, guarding against a double-resume.
@MainActor
private final class OAuthContinuationBox {
    private var continuation: CheckedContinuation<URL, Error>?

    init(_ continuation: CheckedContinuation<URL, Error>) {
        self.continuation = continuation
    }

    func resume(_ result: Result<URL, Error>) {
        guard let continuation else { return }
        self.continuation = nil
        continuation.resume(with: result)
    }
}
