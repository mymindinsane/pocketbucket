# Contributing

Contributions are welcome under the existing Apache 2.0 license. For substantial changes, open a focused issue or proposal first. Keep pull requests small and include the problem, resulting behavior and checks actually run.

Use JDK 21, the Gradle wrapper and the pinned version catalog. Follow Kotlin conventions. Run `:core:jvmTest`, `:composeApp:testDebugUnitTest`, `:composeApp:lintDebug` and `:composeApp:assembleDebug` before submitting shared/Android changes. For native changes also run the iOS framework build/tests on macOS; report when that platform was unavailable. CI currently covers Linux JVM/Android, not iOS device behavior.

Tests must remain offline with fictional fixtures. Mock the REST engine or repository; do not require corporate accounts, VPNs or tokens. Shared networking/domain logic belongs in commonMain. Keep DTOs out of the UI, preserve cancellation and sanitize errors. Do not add custom trust managers, HTTP logging, analytics, a backend or plaintext credential storage.

Do not commit `local.properties`, signing keys, personal Xcode settings, environment files, credentials or corporate data. See SECURITY.md for vulnerability reporting. Keep the existing license intact.
