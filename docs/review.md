# Foundation diff review — 2026-10-27

Reviewed the complete foundation diff against the initial `main`, including auth/API/DTOs, state/navigation, platform stores/engines, Gradle/Xcode/CI and documentation. The starting reviewed head was `3fe3161`. Independent API/security, presentation and platform/build passes were followed by inspection of the combined fixes and tests.

## Findings and fixes

| Priority | Finding | Resolution |
| --- | --- | --- |
| P2 | Offset pages can overlap when server lists change. Appending duplicate identities could produce duplicate stable keys and a Compose `LazyColumn` failure. | Deduplicate each loaded project/repository/PR list by key/ID, preserving first-seen order. One regression scenario covers all three lists, including changed content with the same identity. Refresh replaces the accumulated list. |
| P2 | A shared list callsite retained scroll position across destinations. Opening a repository/PR/details after scrolling could start partway through the new screen. | Key the destination content by `Route`; a new destination gets new local scroll state, while refresh on the same route retains it. Reviewed statically and compiled; no instrumented scroll test is claimed. |

No additional confirmed blocking defects were found in the reviewed auth/API or platform implementations. This is a code review, not a security audit or proof of production readiness.

## Tests

Expanded common suites from 25 to **44 tests**: 30 core, 14 presentation. Coverage includes authentication/request contracts, malformed and forward-compatible DTOs, pagination, cancellation, all navigation destinations, persistence failures, cleanup/retry and the list regression. The tests use fictional fixtures and make no real Bitbucket requests. See [testing](testing.md) for the matrix and commands; [validation](validation.md) records local results and the CI verification boundary.

## Remaining verification

- Fresh official v819 documentation cross-check and authorized disposable-server integration: Atlassian documentation access is blocked by CONNECT 403 in this environment.
- Real Keychain/Keystore tests, locked-device/reinstall/key invalidation/backup behavior, physical-device VPN/TLS and UI/accessibility checks.
- Minimum OS runtime checks: deployment targets alone do not prove iOS 15 / Android API 26 behavior.

Prior green CI for `3fe3161`: [PR run](https://github.com/mymindinsane/pocketbucket/actions/runs/40586673649), [push run](https://github.com/mymindinsane/pocketbucket/actions/runs/40586664459). Review fixes and expanded suites must be checked on their own commit; use the current [PR checks](https://github.com/mymindinsane/pocketbucket/pull/1/checks) and Actions artifacts.
