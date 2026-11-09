# Architecture

Two Gradle modules keep the dependency boundary useful without a module per layer.

- `core/commonMain/auth`: validated HTTPS server URL, Basic/Bearer credentials and the secure-store contract.
- `core/commonMain/domain`: immutable project/repository/PR models, one-page results and the repository interface.
- `core/commonMain/data`: REST 1.0 paths, version-specific serializable DTOs, mappings, sanitized failures and the Ktor client.
- `composeApp/commonMain`: shared Material 3 screens, navigation routes, explicit immutable `AppState` and a lifecycle ViewModel exposing `StateFlow`.
- `composeApp/androidMain`: Activity, OkHttp engine and Android Keystore-backed AES-GCM credential storage.
- `composeApp/iosMain`: Compose view controller, Darwin engine and device-local Keychain storage.
- `iosApp`: small SwiftUI host and shared Xcode scheme.

The core exposes a JVM variant for host tests and consumption by Android, plus iOS variants on macOS. It deliberately needs no Android APIs. The UI module owns platform integrations. A future desktop host can consume the existing JVM variant and supply an engine/store without changing domain models.

The UI sees `BitbucketRepository`, not DTOs or Ktor responses. Future Bitbucket versions can use another adapter or capability negotiation behind that interface. New review operations should be added only when implemented; there are no placeholder methods that pretend diffs or approvals work. Raw state/reviewer status strings intentionally preserve unknown future server values.

Manual constructor injection is sufficient: each platform creates one secure store and a session factory. A session owns its API client, which is closed on failed connection, disconnect and ViewModel disposal. Koin and pass-through use cases add no value at this size.

Connection sequence: validate local URL/credentials → authenticated dashboard GET → save securely → projects page. Restore revalidates once and presents failures for explicit user action. Password fields use `remember`, never persisted Compose saved state. Lists load one page (25 items) with a user-triggered next cursor. Refresh replaces the accumulated list; errors retain already loaded data. Navigation cancels an in-flight request; coroutine cancellation is propagated by the data layer. Back reloads the parent list from its first page rather than retaining an unbounded navigation cache.

Platform storage operates on a background dispatcher and is serialized by a mutex. All server/credential data is inside the Keychain item or encrypted Android payload. Keychain uses `WhenUnlockedThisDeviceOnly`; Android uses a non-exportable Keystore AES key and randomized GCM IVs. Android backup and cleartext networking are disabled. Storage failures never fall back to plaintext.

No HTTP logging, cookie persistence, automatic retries or redirect following is installed. Engines use system TLS validation. Android also disables OkHttp connection retries; Darwin session cookie handling and URL caching are disabled. A 3xx response is an invalid connection instead of allowing credentials to follow a redirect. Transport errors are sanitized without preserving their potentially sensitive causes. The application does not attempt to install trust roots or manage a VPN.

The first milestone prioritizes data flow. Native swipe-back, Markdown rendering, multi-account management, offline caching and polished tablet layouts are deferred. The UI caps content width and scrolls long lists/details. Platform secure-store and device networking tests still need real platform runners.
