# Testing and verification

The review expanded the common suites from 25 to **44 tests**: 30 in `core`, 14 in `composeApp`. Each test may exercise several response/credential variants. All fixtures are fictional. No corporate server, VPN or account is required.

## Executable checks

```sh
./gradlew :core:jvmTest :composeApp:testDebugUnitTest :composeApp:lintDebug :composeApp:assembleDebug
```

On macOS with Xcode 16.3 and a suitable simulator:

```sh
./gradlew :core:iosSimulatorArm64Test :composeApp:iosSimulatorArm64Test
```

Use `iosX64Test` on Intel. CI selects and boots an available iOS 18 simulator, builds the complete unsigned Swift host, and verifies that both native XML reports contain executed tests without failures/errors. See `.github/scripts/ios-checks.sh` and the current commit's Actions results. Native targets are not registered on Linux.

| Area | Covered behavior |
| --- | --- |
| Credentials/URL | UTF-8 Basic and Bearer headers, redaction, invalid usernames/tokens/password, preservation of password whitespace/colons, HTTPS/context paths/custom port |
| API requests | Exact Data Center paths, opaque cursors, query filters, headers actually sent, invalid inputs cause no request |
| Responses/mapping | Nested project/repository/PR mapping, absent/null descriptions, absent reviewers, unknown fields/statuses, malformed required data |
| Pagination | Last page, empty intermediate page, missing/repeated/regressing cursor, explicit next request without eager fetching |
| Failures | 401/403/404/429/500, no anonymous fallback, no retries, redirects/HTML/invalid JSON, sanitized transport/response errors, timeout and cancellation |
| Session/state | Validate before save, restore without rewriting, failure of load/save/clear, candidate/client cleanup, explicit recovery |
| Navigation | Route parameters and Back chain, cancelled deferred response cannot populate another screen |
| Lists/details | Refresh retains data on failure, retry, next-page cursor, overlapping identities across projects/repositories/PR, details errors |
| Stored format | Basic and token round-trip through the persisted schema |

The suites use `kotlin.test`, Ktor `MockEngine`, `coroutines-test` and small in-memory fakes. They assert contracts and observable state; no coverage-percentage target or additional test framework was introduced.

## Boundaries and manual platform checklist

The common tests do **not** exercise real `AndroidKeyStore` or Keychain. The existing Kotlin/Native runner executes a simulator command-line test executable, not a signed app-host with application Keychain entitlements. A fake crypto provider would not establish storage security. A separate XCTest/app-host and Android instrumentation setup should be introduced when these platform tests can actually be run.

Before production use, check on supported devices:

- Save Basic, restart and restore; replace with a PAT and restore again; clear, restart and verify no session returns; repeat clear.
- Reject invalid credentials without persisting them; report secure-store access/write/removal failure without plaintext fallback.
- Lock/unlock, reinstall and key invalidation behavior; backup and device-transfer exclusion. Do not assume iOS reinstall deletes Keychain data.
- Route changes start at the top, refresh remains usable, Android system Back, iOS explicit Back, keyboard, large text and screen reader behavior.
- Minimum iOS 15 / Android API 26, plus currently supported newer OS versions. CI's modern simulator does not validate the minimum OS.
- Authorized disposable Bitbucket 8.19.26 integration; server policy for Basic/PAT; device VPN and organization-approved TLS roots. Never disable TLS validation.

These remaining checks are explicit limitations, not claimed passes. See [validation](validation.md), [API contract](bitbucket-api.md), [review](review.md) and the [Russian guide](guide.ru.md).
