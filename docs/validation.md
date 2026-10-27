# Milestone 1 validation

Executed in the Linux cloud workspace on 2026-10-27, with Temurin JDK 21.0.13, the Gradle 8.11.1 wrapper and Android SDK 35 / build-tools 35.0.0.

```sh
./gradlew :core:jvmTest :composeApp:testDebugUnitTest :composeApp:assembleDebug :composeApp:lintDebug --console=plain
```

- `:core:jvmTest`: **30 passed**, 0 failures/errors/skips. Includes Basic/Bearer authorization, credential boundaries, custom HTTPS/context URLs, fictional Data Center DTO mapping, optional/unknown/malformed fields, authenticated dashboard validation, exact paths/queries, server cursor/empty-page pagination, HTTP failures, timeout, transport errors, redirects and cancellation.
- `:composeApp:testDebugUnitTest`: **14 passed**, 0 failures/errors/skips. Covers validation before persistence, restore/load/save/clear failures and cleanup/retry, all navigation routes and Back parameters, cancelled deferred requests, overlapping identities across all lists, detail/list refresh failures, invalid URLs and serialization for both authentication methods. These are host unit tests, not Android UI/device tests.
- `:composeApp:lintDebug`: **passed**, 0 errors and 2 stylistic `UseKtx` warnings. Direct `SharedPreferences.Editor.commit()` is intentional: its Boolean result is checked to detect storage failure; the KTX edit helper returns Unit. The final report was regenerated in a fresh Gradle process after deleting generated lint caches; it no longer reports backup/icon warnings.
- `:composeApp:assembleDebug`: **passed**. APK: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.
- `apksigner verify --verbose`: **passed**, APK Signature Scheme v2. This is a debug-signed APK, not a production release.
- `aapt2 dump badging` and `dump xmltree`: verified application ID, min SDK 26 / target 35, launch Activity, `allowBackup=false`, `fullBackupContent=false`, `usesCleartextTraffic=false` and the data extraction rules resource in the packaged manifest.
- The saved cloud installation/activation script was executed successfully, including a cached repeat run. It uses the retained JDK/SDK and regenerates Java proxy settings outside the repository. System trust roots remain enabled; TLS verification was never disabled.
- Gradle wrapper JAR matches the official published SHA-256; distribution SHA-256 is pinned in wrapper properties. `sh -n gradlew`, Info.plist parsing and Xcode scheme XML parsing passed. XML parsing is not a native build.

## Remote iOS verification

The complete unsigned iOS simulator app and both native suites **passed on macOS / Xcode 16.3** for the pre-review commit `3fe3161`: [PR run](https://github.com/mymindinsane/pocketbucket/actions/runs/40586673649), [push run](https://github.com/mymindinsane/pocketbucket/actions/runs/40586664459). The workflow boots an iOS 18 simulator and requires executed tests in both modules. The expanded 44-test suites and review fixes need their own current-commit CI result; consult [PR checks](https://github.com/mymindinsane/pocketbucket/pull/1/checks) and `ios-check-reports`. Linux results alone do not establish an iOS pass.

## Not executed / remaining verification

- **iOS UI/device behavior and signing:** physical-device signing, native swipe UX and platform-store integration tests remain separate checks. The simulator CI is not a signed distribution IPA or a minimum-iOS-15 runtime test.
- **Android device/emulator UI and platform secure-store tests:** not executed. JVM tests do not establish Keystore/Keychain behavior, device VPN access, enterprise certificate trust, rotation, reinstall or locked-device behavior.
- **Real Bitbucket integration:** not executed or attempted. All test payloads and accounts are fictional and all HTTP tests use MockEngine.
- **Fresh documentation cross-check:** direct requests to the official Atlassian 8.19 references were denied by the environment egress proxy (CONNECT 403). See `bitbucket-api.md`. Required documentation domains were added to the saved environment draft; that does not apply the network policy to this running instance. Endpoint mocks are not proof of live 8.19.26 compatibility.
- **GitHub Actions:** Linux and macOS jobs are configured. The results above record local checks; consult the actual remote job results on GitHub for the current commit.

The foundation supports host development, offline unit testing, Android APK builds and verified simulator builds on the cited commit. No claim of production readiness or complete platform validation is made. PR diffs and review actions remain later milestones.
