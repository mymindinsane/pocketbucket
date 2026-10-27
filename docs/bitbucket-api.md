# Bitbucket Data Center API contract

Target: Bitbucket **Data Center 8.19.26**, REST API **1.0**. No Bitbucket Cloud `/2.0` endpoints or Cloud DTOs are used.

Official reference: https://developer.atlassian.com/server/bitbucket/rest/v819/

| Operation | GET path after the optional server context path | Query |
| --- | --- | --- |
| Validate authenticated session | `/rest/api/1.0/dashboard/pull-requests` | `start=0&limit=1` |
| Accessible projects | `/rest/api/1.0/projects` | `start`, `limit=25` |
| Repositories in project | `/rest/api/1.0/projects/{projectKey}/repos` | `start`, `limit=25` |
| Open PRs in repository | `/rest/api/1.0/projects/{projectKey}/repos/{repositorySlug}/pull-requests` | `state=OPEN`, `start`, `limit=25` |
| PR details | `/rest/api/1.0/projects/{projectKey}/repos/{repositorySlug}/pull-requests/{pullRequestId}` | none |

The dashboard is used because it requires an authenticated user; an anonymously accessible projects response does not validate credentials. No project/repository administration permission is requested. Project and repository listings are filtered by the account's access; repository read permission is necessary for PR reads. User PATs use `Authorization: Bearer …`; username/password uses UTF-8 Basic authentication over HTTPS. Project/repository-level tokens are not the supported personal-session authentication type.

Data Center pagination is an object with `values`, `isLastPage`, and (when not last) `nextPageStart`. The server may clamp the requested limit and need not use a next offset equal to `start + values.size`. We use the server-provided cursor, stop on `isLastPage`, reject absent/non-advancing cursors and never load all pages automatically. Empty intermediate pages may still have a next cursor. `size`, `limit`, `start`, links and unrelated DTO fields can be ignored by this read-only adapter.

Repositories contain a nested `project`. PRs contain `author.user`, `fromRef` / `toRef` with `displayId` and nested `repository`, and `reviewers[].user` / `status`. `description` may be absent/null; `reviewers` may be absent on minimal responses. Unknown fields are accepted; required identity fields and `isLastPage` are not silently fabricated. PR `version` is retained for eventual optimistic-concurrency review operations.

401, 403 and 404 have distinct typed failures. Other non-2xx statuses are sanitized HTTP failures; 3xx and HTML login pages are rejected. There is no retry policy or cookie login. A valid HTTPS URL is required, without embedded user information, query or fragment. Segments are URL encoded, including slashes in repository identifiers. Standard certificate verification remains enabled.

## Verification boundary

Endpoint/DTO contracts above are covered by fictional MockEngine fixtures, not an integration test against 8.19.26. During this implementation the network policy denied direct access to `developer.atlassian.com` (HTTP CONNECT 403), so the linked versioned reference could not be freshly inspected in this environment. Before release, cross-check this table and fixtures with the official versioned reference and run an authorized disposable Bitbucket 8.19.26 integration test. Do not treat mock tests as proof of server policy compatibility. No corporate instance was contacted.
