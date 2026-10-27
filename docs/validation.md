# Milestone 1 validation

Executed in the Linux cloud workspace on 2026-10-27, with Temurin JDK 21.0.13, the Gradle 8.11.1 wrapper and Android SDK 35 / build-tools 35.0.0.

```sh
./gradlew :core:jvmTest :composeApp:testDebugUnitTest :composeApp:assembleDebug :composeApp:lintDebug --console=plain
```

- `:core:jvmTest`: **19 passed**, 0 failures/errors/skips. Includes Basic/Bearer authorization, URL validation, fictional Data Center DTO mapping, dashboard validation, exact paths/queries, server cursor pagination, malformed pages/responses, HTTP failures, timeout, transport errors, redirects and cancellation.
- `:composeApp:testDebugUnitTest`: **6 passed**, 0 failures/errors/skips. Covers validation before persistence, rejected authentication/client cleanup, refresh/page state, restore, invalid URLs and serialization for both authentication methods. These are host unit tests, not Android UI/device tests.
- `:composeApp:lintDebug`: **passed**, 0 errors and 2 stylistic `UseKtx` warnings. Direct `SharedPreferences.Editor.commit()` is intentional: its Boolean result is checked to detect storage failure; the KTX edit helper returns Unit. The final report was regenerated in a fresh Gradle process after deleting generated lint caches; it no longer reports backup/icon warnings.
- `:composeApp:assembleDebug`: **passed**. APK: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.
- `apksigner verify --verbose`: **passed**, APK Signature Scheme v2. This is a debug-signed APK, not a production release.
- `aapt2 dump badging` and `dump xmltree`: verified application ID, min SDK 26 / target 35, launch Activity, `allowBackup=false`, `fullBackupContent=false`, `usesCleartextTraffic=false` and the data extraction rules resource in the packaged manifest.
- The saved cloud installation/activation script was executed successfully, including a cached repeat run. It uses the retained JDK/SDK and regenerates Java proxy settings outside the repository. System trust roots remain enabled; TLS verification was never disabled.
- Gradle wrapper JAR matches the official published SHA-256; distribution SHA-256 is pinned in wrapper properties. `sh -n gradlew`, Info.plist parsing and Xcode scheme XML parsing passed. XML parsing is not a native build.

## Not executed / remaining verification

- **iOS build, simulator launch and native tests:** not executed; this machine is Linux and has no Apple SDK/Xcode. Use the README's Xcode 16.3 / JDK 21 instructions. Native sources and the Xcode project require verification on macOS.
- **Android device/emulator UI and platform secure-store tests:** not executed. JVM tests do not establish Keystore/Keychain behavior, device VPN access, enterprise certificate trust, rotation, reinstall or locked-device behavior.
- **Real Bitbucket integration:** not executed or attempted. All test payloads and accounts are fictional and all HTTP tests use MockEngine.
- **Fresh documentation cross-check:** direct requests to the official Atlassian 8.19 references were denied by the environment egress proxy (CONNECT 403). See `bitbucket-api.md`. Required documentation domains were added to the saved environment draft; that does not apply the network policy to this running instance. Endpoint mocks are not proof of live 8.19.26 compatibility.
- **GitHub Actions:** workflow added. The results above are local checks; remote workflow results were not verified in this report and must be checked on GitHub.

The foundation supports host development, offline unit testing and Android APK builds. No claim of iOS or production readiness is made. PR diffs and review actions remain later milestones.
