# PocketBucket

An open-source mobile repository browser and pull request client for **self-hosted Bitbucket Server / Data Center**, initially targeting **8.19.26**. Shared Kotlin Multiplatform + Compose UI for iOS and Android. Apache 2.0 licensed.

**Unofficial client; not affiliated with or endorsed by Atlassian. Bitbucket Cloud is not supported.**

## Milestone 1

- HTTPS connection with username/password (REST Basic authentication) or a personal access token (Bearer).
- Device-local secure session storage and explicit disconnect/removal.
- Projects → repositories → open pull requests → pull request details, including author, branches and reviewers.
- Explicit page loading, refresh, light/dark appearance, loading/error/empty states.
- Fully local MockEngine tests; no corporate server or credentials required.

Diffs, commits, source browsing, comments, approvals, merge and decline remain future milestones. Descriptions currently render as plain text. One saved connection is supported. iOS uses an explicit Back button; interactive swipe navigation is not implemented yet.

## Requirements and pinned toolchain

| Tool | Version |
| --- | --- |
| JDK | 21 (JVM bytecode targets 17) |
| Gradle | 8.11.1, wrapper with SHA-256 verification |
| Kotlin / Compose compiler plugin | 2.1.21 |
| Compose Multiplatform | 1.8.2 |
| Android Gradle Plugin | 8.9.2 |
| Ktor | 3.1.3 |
| Coroutines / Serialization | 1.10.2 / 1.8.1 |
| Multiplatform Lifecycle | 2.9.1 |

Dependencies are centralized in [the version catalog](gradle/libs.versions.toml). These are pinned stable releases, not a claim to use the newest releases. Ktor 3.1.3 publishes Kotlin 2.1.20 / Coroutines 1.10.2 dependencies, compatible with the selected Kotlin patch line. Gradle/AGP/Kotlin compatibility is additionally checked by the Android build. Official references: [Compose releases](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.8.2), [Kotlin compatibility](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html), [AGP 8.9](https://developer.android.com/build/releases/past-releases/agp-8-9-0-release-notes).

Minimum Android **8.0/API 26**, compile/target SDK **35**; minimum iOS **15.0**. Use **Xcode 16.3** with its iOS 18.4 SDK for the initial Kotlin 2.1.21 toolchain; newer Xcode versions require a compatibility check rather than assuming Kotlin/Native support.

## Android and local checks

Install JDK 21 and Android SDK command-line tools, platform 35 and build-tools 35.0.0. Set `JAVA_HOME` to the JDK and `ANDROID_HOME` to the SDK, or set `sdk.dir` in an ignored `local.properties` file. Do not commit local paths or credentials.

```sh
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
./gradlew :core:jvmTest
./gradlew :composeApp:testDebugUnitTest :composeApp:lintDebug :composeApp:assembleDebug
```

Open the root project in Android Studio and run `composeApp`, or install `composeApp/build/outputs/apk/debug/composeApp-debug.apk` with `adb install -r`. The core JVM tests do not need a device or corporate service; because the root also configures the Android application, an installed Android SDK is recommended for the complete workflow.

## iOS with Xcode

A macOS machine is required; Linux cannot build, sign or run the iOS app.

1. Install Xcode 16.3, its command-line tools and an iOS Simulator runtime. Accept the Xcode license. Install JDK 21 and select it with `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
2. From the repository root, run `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` (Apple Silicon) or `:composeApp:linkDebugFrameworkIosX64` (Intel). Native targets are registered on macOS only.
3. Open `iosApp/iosApp.xcodeproj`, select the shared **iosApp** scheme and an iOS Simulator, then Run. The build phase invokes `:composeApp:embedAndSignAppleFrameworkForXcode` and links the static `PocketBucket` framework. CocoaPods and a backend are not required.
4. For a physical device, choose your signing team in Signing & Capabilities and a unique bundle identifier if necessary. Configure the existing system VPN and install organization-approved trust roots through system/device management. No TLS bypass is provided.
5. Run shared native tests with `./gradlew :core:iosSimulatorArm64Test :composeApp:iosSimulatorArm64Test` on Apple Silicon (use `iosX64Test` on Intel).

The `Checks` workflow includes a `macos-15` (Apple Silicon) job pinned to Xcode 16.3. It builds the complete SwiftUI app for the simulator without signing, then runs `core` and `composeApp` native tests on a booted iOS 18 simulator. The `ios-check-reports` artifact contains build logs, the Xcode result bundle and native test reports. Consult the actual Actions result before claiming a passing iOS build; Linux checks alone do not validate it. Device Keychain, VPN, TLS trust and signing still require platform validation. See [validation](docs/validation.md) for the verification boundary.

## Connect

Enter your server's HTTPS root, including its context path when present, e.g. `https://code.example.org/bitbucket`. Enter either your username/password or personal access token. This example is fictional. The app calls REST directly through the device network and existing VPN; it has no custom proxy, VPN client or backend.

Server policy must permit the selected authentication method. Browser SSO does **not** establish that REST Basic authentication is enabled. A 401 explains this distinction; 403 directs users to token scope, permissions and authentication policy. Invalid URLs, redirects to login pages, malformed responses, timeouts and connection/TLS failures have safe messages. Corporate credentials are never required for development or tests.

The milestone only reads data. A normal authenticated account and read access to the relevant projects/repositories are required; a PAT needs at least repository read permission and must belong to the user. Token permissions never grant more access than the account itself. Global/project administration and write permissions are not needed. Future commenting/review/merge features will document their extra permissions separately. See [API contract](docs/bitbucket-api.md).

## Contributing and security

Start with [CONTRIBUTING.md](CONTRIBUTING.md), [architecture](docs/architecture.md), [API contract](docs/bitbucket-api.md) and [SECURITY.md](SECURITY.md). Do not include real servers, usernames, tokens, repository data or source code in fixtures, screenshots, logs or issues. There is no analytics or telemetry in the application.

[LICENSE](LICENSE) contains the existing Apache 2.0 license.
