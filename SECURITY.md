# Security

PocketBucket is an early foundation, not a security-audited production client. Never post credentials, corporate URLs, raw API responses or source code in public issues.

Use GitHub's private vulnerability reporting for this repository when enabled. If it is unavailable, ask a maintainer for a private reporting channel without disclosing exploit details in public. Do not open a public issue containing a vulnerability or secrets.

The app connects directly over HTTPS using system trust and any existing device VPN. Basic credentials and PATs are stored in iOS Keychain or as authenticated ciphertext with an Android Keystore key. TLS verification is never disabled. Redirect following, logging, cookies, telemetry and automatic HTTP retry policies are absent. No credentials are included in the source or fixtures. Revoke tokens at the server if a device/account is compromised.

Platform stores have not yet been exercised on iOS/Android hardware. Locked-device, reinstall/key invalidation, backup exclusion, certificate trust and VPN behavior need platform testing before production use. JVM/Swift managed strings can remain in memory until reclaimed; UI secrets are not persisted in saved instance state, and no perfect memory zeroization is claimed. Android ciphertext is kept in app-private preferences; preferences never contain plaintext connection data. Android backup is disabled, and the iOS Keychain item is device-only.
