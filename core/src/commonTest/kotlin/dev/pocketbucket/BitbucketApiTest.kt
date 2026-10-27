package dev.pocketbucket

import dev.pocketbucket.auth.*
import dev.pocketbucket.data.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class BitbucketApiTest {
    private val connection = Connection(ServerUrl.parse("https://code.example.org/bitbucket"), Credentials.Token("fictional-token"))
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test fun projectsDeserializeAndMapOnePage() = runTest {
        var calls = 0
        val api = BitbucketApi(MockEngine { request ->
            calls++
            assertEquals("/bitbucket/rest/api/1.0/projects", request.url.encodedPath)
            assertEquals("0", request.url.parameters["start"])
            assertEquals("25", request.url.parameters["limit"])
            assertEquals("Bearer fictional-token", request.headers[HttpHeaders.Authorization])
            respond(Fixtures.page(Fixtures.project, last = false, next = 73), headers = jsonHeaders)
        }, connection)
        try {
            val page = RestBitbucketRepository(api).projects()
            assertEquals(1, calls) // No eager traversal, even though the server has more pages.
            assertEquals(73, page.nextStart)
            assertEquals("ORBIT", page.values.single().key)
            assertEquals("Fictional spacecraft tooling", page.values.single().description)
        } finally { api.close() }
    }
    @Test fun usesOpaqueServerOffsetAndStopsOnLastPage() = runTest {
        val offsets = mutableListOf<String?>()
        val api = BitbucketApi(MockEngine { request ->
            offsets += request.url.parameters["start"]
            respond(if (offsets.size == 1) Fixtures.page(Fixtures.project, false, 73)
                else Fixtures.page("", last = true, next = 1000), headers = jsonHeaders)
        }, connection)
        try {
            val repo = RestBitbucketRepository(api)
            val first = repo.projects()
            val last = repo.projects(first.nextStart!!)
            assertNull(last.nextStart)
            assertTrue(last.values.isEmpty())
            assertEquals(listOf<String?>("0", "73"), offsets)
        } finally { api.close() }
    }
    @Test fun rejectsMissingOrNonAdvancingOffsets() = runTest {
        for (next in listOf(null, 0, -1)) {
            val api = BitbucketApi(MockEngine { respond(Fixtures.page(Fixtures.project, false, next), headers = jsonHeaders) }, connection)
            try { assertFailsWith<ApiFailure.InvalidResponse> { RestBitbucketRepository(api).projects() } }
            finally { api.close() }
        }
    }
    @Test fun repositoriesAndPullRequestsMapNestedFields() = runTest {
        val paths = mutableListOf<String>()
        val api = BitbucketApi(MockEngine { request ->
            paths += request.url.encodedPath
            val value = if (paths.size == 1) Fixtures.repository else Fixtures.pullRequest
            if (paths.size == 2) assertEquals("OPEN", request.url.parameters["state"])
            respond(if (paths.size < 3) Fixtures.page(value) else value, headers = jsonHeaders)
        }, connection)
        try {
            val repo = RestBitbucketRepository(api)
            val repository = repo.repositories("ORBIT").values.single()
            assertEquals("Orbital Tools", repository.project.name)
            assertEquals(42L, repository.id)
            val pr = repo.pullRequests("ORBIT", repository.slug).values.single()
            val detail = repo.pullRequest("ORBIT", repository.slug, pr.id)
            assertEquals(pr, detail)
            assertEquals("feature/chart", detail.from.displayName)
            assertEquals("main", detail.to.displayName)
            assertEquals("Alex Demo", detail.author.displayName)
            assertEquals("APPROVED", detail.reviewers.single().status)
            assertEquals(listOf("/bitbucket/rest/api/1.0/projects/ORBIT/repos",
                "/bitbucket/rest/api/1.0/projects/ORBIT/repos/flight-console/pull-requests",
                "/bitbucket/rest/api/1.0/projects/ORBIT/repos/flight-console/pull-requests/12"), paths)
        } finally { api.close() }
    }
    @Test fun validationUsesAuthenticatedDashboardWithSmallPage() = runTest {
        val api = BitbucketApi(MockEngine { request ->
            assertEquals("/bitbucket/rest/api/1.0/dashboard/pull-requests", request.url.encodedPath)
            assertEquals("1", request.url.parameters["limit"])
            respond(Fixtures.page(""), headers = jsonHeaders)
        }, connection)
        try { RestBitbucketRepository(api).validateConnection() } finally { api.close() }
    }
    @Test fun basicHeaderActuallySentToApi() = runTest {
        val api = BitbucketApi(MockEngine { request ->
            assertEquals("Basic YWxleDpwQHNz", request.headers[HttpHeaders.Authorization])
            respond(Fixtures.page(""), headers = jsonHeaders)
        }, Connection(connection.server, Credentials.Basic("alex", "p@ss")))
        try { RestBitbucketRepository(api).validateConnection() } finally { api.close() }
    }
    @Test fun mapsHttpFailuresAndDoesNotLeakBodiesOrRetry() = runTest {
        for (status in listOf(401, 403, 404, 429, 500)) {
            var calls = 0
            val api = BitbucketApi(MockEngine {
                calls++
                respond("sensitive-fictional-response", HttpStatusCode.fromValue(status), jsonHeaders)
            }, connection)
            try {
                val error = assertFailsWith<ApiFailure> { RestBitbucketRepository(api).projects() }
                when (status) {
                    401 -> { assertIs<ApiFailure.Unauthorized>(error); assertTrue(error.userMessage.contains("Basic")) }
                    403 -> assertIs<ApiFailure.Forbidden>(error)
                    404 -> assertIs<ApiFailure.NotFound>(error)
                    else -> assertEquals(status, assertIs<ApiFailure.Http>(error).status)
                }
                assertFalse(error.toString().contains("sensitive-fictional"))
                assertNull(error.cause)
                assertEquals(1, calls)
            } finally { api.close() }
        }
    }
    @Test fun refusesRedirectsIncludingCrossOriginLogin() = runTest {
        var calls = 0
        val api = BitbucketApi(MockEngine {
            calls++
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://login.example.net"))
        }, connection)
        try {
            assertFailsWith<ApiFailure.InvalidResponse> { RestBitbucketRepository(api).projects() }
            assertEquals(1, calls)
        } finally { api.close() }
    }
    @Test fun invalidJsonOrHtmlIsNotReportedAsSuccessfulLogin() = runTest {
        for ((body, type) in listOf("{}" to "application/json", "not json" to "application/json", "<html>SSO</html>" to "text/html")) {
            val api = BitbucketApi(MockEngine { respond(body, headers = headersOf(HttpHeaders.ContentType, type)) }, connection)
            try { assertFailsWith<ApiFailure.InvalidResponse> { RestBitbucketRepository(api).validateConnection() } }
            finally { api.close() }
        }
    }
    @Test fun transportErrorsAreSanitized() = runTest {
        val api = BitbucketApi(MockEngine { error("fictional secret transport detail") }, connection)
        try {
            val error = assertFailsWith<ApiFailure.Connection> { RestBitbucketRepository(api).projects() }
            assertFalse(error.toString().contains("secret transport"))
            assertNull(error.cause)
        } finally { api.close() }
    }
    @Test fun timeoutIsActionable() = runTest {
        val api = BitbucketApi(MockEngine { request -> throw HttpRequestTimeoutException(request) }, connection)
        try { assertFailsWith<ApiFailure.Timeout> { RestBitbucketRepository(api).projects() } }
        finally { api.close() }
    }
    @Test fun cancellationPropagates() = runTest {
        val api = BitbucketApi(MockEngine { throw CancellationException("cancelled") }, connection)
        try { assertFailsWith<CancellationException> { RestBitbucketRepository(api).projects() } }
        finally { api.close() }
    }
    @Test fun encodesPathSegmentsAndRejectsInvalidPaging() = runTest {
        var calls = 0
        val api = BitbucketApi(MockEngine { request ->
            calls++
            assertEquals("/bitbucket/rest/api/1.0/projects/ORBIT/repos/a%2Fb/pull-requests", request.url.encodedPath)
            respond(Fixtures.page(""), headers = jsonHeaders)
        }, connection)
        try {
            RestBitbucketRepository(api).pullRequests("ORBIT", "a/b")
            assertFailsWith<IllegalArgumentException> { RestBitbucketRepository(api).projects(-1) }
            assertEquals(1, calls)
        } finally { api.close() }
    }

    @Test fun emptyIntermediatePageKeepsItsCursorUntilTheCallerRequestsMore() = runTest {
        val offsets = mutableListOf<String?>()
        val api = BitbucketApi(MockEngine { request ->
            offsets += request.url.parameters["start"]
            respond(if (offsets.size == 1) Fixtures.page("", last = false, next = 73)
                else Fixtures.page(Fixtures.project), headers = jsonHeaders)
        }, connection)
        try {
            val repository = RestBitbucketRepository(api)
            val empty = repository.projects()
            assertTrue(empty.values.isEmpty())
            assertEquals(73, empty.nextStart)
            assertEquals(listOf<String?>("0"), offsets)
            val final = repository.projects(empty.nextStart!!)
            assertEquals("ORBIT", final.values.single().key)
            assertNull(final.nextStart)
            assertEquals(listOf<String?>("0", "73"), offsets)
        } finally { api.close() }
    }

    @Test fun aLaterPageCannotRepeatOrMoveBackwardsToAnEarlierOffset() = runTest {
        for (next in listOf(0, 72, 73)) {
            val api = BitbucketApi(MockEngine {
                respond(Fixtures.page(Fixtures.project, last = false, next = next), headers = jsonHeaders)
            }, connection)
            try {
                assertFailsWith<ApiFailure.InvalidResponse> { RestBitbucketRepository(api).projects(73) }
            } finally { api.close() }
        }
    }

    @Test fun malformedPageMetadataFailsWithoutInventingAnEmptyOrFinalPage() = runTest {
        val bodies = listOf(
            """{"values":[]}""",
            """{"values":null,"isLastPage":true}""",
            """{"isLastPage":true}""",
            """{"values":{},"isLastPage":true}""",
            """{"values":[],"isLastPage":null}""",
            """{"values":[],"isLastPage":false,"nextPageStart":"fictional-secret"}""",
        )
        for (body in bodies) {
            val api = BitbucketApi(MockEngine { respond(body, headers = jsonHeaders) }, connection)
            try {
                val error = assertFailsWith<ApiFailure.InvalidResponse> { RestBitbucketRepository(api).projects() }
                assertFalse(error.toString().contains("fictional-secret"))
                assertNull(error.cause)
            } finally { api.close() }
        }
    }

    @Test fun absentOrNullDescriptionsAndAbsentReviewersKeepWorkingWithNewServerFields() = runTest {
        val original = Json.parseToJsonElement(Fixtures.pullRequest).jsonObject
        for (explicitNull in listOf(false, true)) {
            val fields = original.toMutableMap().apply {
                remove("reviewers")
                remove("description")
                put("futureServerProperty", buildJsonObject { put("enabled", true) })
                if (explicitNull) put("description", JsonNull)
            }
            var calls = 0
            val api = BitbucketApi(MockEngine {
                calls++
                respond(if (calls == 1)
                    Fixtures.page("""{"key":"ORBIT","name":"Orbital Tools","futureField":42}""")
                    else JsonObject(fields).toString(), headers = jsonHeaders)
            }, connection)
            try {
                val repository = RestBitbucketRepository(api)
                assertNull(repository.projects().values.single().description)
                val detail = repository.pullRequest("ORBIT", "flight-console", 12)
                assertNull(detail.description)
                assertTrue(detail.reviewers.isEmpty())
                assertEquals("Alex Demo", detail.author.displayName)
                assertEquals("flight-console", detail.to.repository.slug)
            } finally { api.close() }
        }
    }

    @Test fun unfamiliarPullRequestAndReviewStatusesArePreservedForDisplay() = runTest {
        val fields = Json.parseToJsonElement(Fixtures.pullRequest).jsonObject.toMutableMap()
        fields["state"] = JsonPrimitive("FUTURE_REVIEW_STATE")
        fields["reviewers"] = buildJsonArray {
            add(buildJsonObject {
                put("user", Json.parseToJsonElement(Fixtures.person))
                put("status", "TEAM_REVIEW_REQUIRED")
            })
        }
        val api = BitbucketApi(MockEngine { respond(JsonObject(fields).toString(), headers = jsonHeaders) }, connection)
        try {
            val detail = RestBitbucketRepository(api).pullRequest("ORBIT", "flight-console", 12)
            assertEquals("FUTURE_REVIEW_STATE", detail.state)
            assertEquals("TEAM_REVIEW_REQUIRED", detail.reviewers.single().status)
        } finally { api.close() }
    }

    @Test fun missingOrInvalidPullRequestIdentityIsNotPartiallyMapped() = runTest {
        val fields = Json.parseToJsonElement(Fixtures.pullRequest).jsonObject
        val malformed = listOf(
            fields - "id",
            fields - "author",
            fields + ("fromRef" to JsonNull),
            fields + ("version" to JsonPrimitive("invalid-version")),
            fields + ("author" to buildJsonObject { put("user", buildJsonObject { put("name", "alex.demo") }) }),
            fields + ("reviewers" to JsonNull),
        )
        for (payload in malformed) {
            val body = JsonObject(payload + ("title" to JsonPrimitive("fictional-sensitive-title"))).toString()
            val api = BitbucketApi(MockEngine { respond(body, headers = jsonHeaders) }, connection)
            try {
                val error = assertFailsWith<ApiFailure.InvalidResponse> {
                    RestBitbucketRepository(api).pullRequest("ORBIT", "flight-console", 12)
                }
                assertFalse(error.toString().contains("fictional-sensitive-title"))
                assertNull(error.cause)
            } finally { api.close() }
        }
    }

    @Test fun invalidIdentifiersAndPagingDoNotSendAnAuthenticatedRequest() = runTest {
        var calls = 0
        val api = BitbucketApi(MockEngine {
            calls++
            respond(Fixtures.page(""), headers = jsonHeaders)
        }, connection)
        try {
            val repository = RestBitbucketRepository(api)
            for (id in listOf(0L, -1L, Long.MIN_VALUE)) {
                assertFailsWith<IllegalArgumentException> { repository.pullRequest("ORBIT", "flight-console", id) }
            }
            assertFailsWith<IllegalArgumentException> { repository.repositories("ORBIT", -1) }
            assertFailsWith<IllegalArgumentException> { repository.pullRequests("ORBIT", "flight-console", -1) }
            for (limit in listOf(0, 101)) {
                assertFailsWith<IllegalArgumentException> {
                    api.get<PageDto<ProjectDto>>(listOf("projects"), start = 0, limit = limit)
                }
            }
            assertEquals(0, calls)
        } finally { api.close() }
    }

    @Test fun rejectedConnectionValidationNeverFallsBackToAnonymousProjectAccess() = runTest {
        for (credentials in listOf(Credentials.Basic("alex.demo", "fictional-password"), Credentials.Token("fictional-token"))) {
            for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden)) {
                val paths = mutableListOf<String>()
                val api = BitbucketApi(MockEngine { request ->
                    paths += request.url.encodedPath
                    assertEquals(credentials.authorizationHeader(), request.headers[HttpHeaders.Authorization])
                    if (request.url.encodedPath.endsWith("/projects")) {
                        respond(Fixtures.page(Fixtures.project), headers = jsonHeaders)
                    } else respond("fictional-sensitive-rejection", status, jsonHeaders)
                }, Connection(connection.server, credentials))
                try {
                    val error = assertFailsWith<ApiFailure> { RestBitbucketRepository(api).validateConnection() }
                    if (status == HttpStatusCode.Unauthorized) assertIs<ApiFailure.Unauthorized>(error)
                    else assertIs<ApiFailure.Forbidden>(error)
                    assertFalse(error.toString().contains("fictional-sensitive-rejection"))
                    assertEquals(listOf("/bitbucket/rest/api/1.0/dashboard/pull-requests"), paths)
                } finally { api.close() }
            }
        }
    }

    @Test fun customHttpsPortAndEncodedContextPathReachTheConfiguredOrigin() = runTest {
        val custom = Connection(ServerUrl.parse("https://code.example.org:8443/tools%20team/bitbucket/"),
            Credentials.Token("fictional-token"))
        val api = BitbucketApi(MockEngine { request ->
            assertEquals(URLProtocol.HTTPS, request.url.protocol)
            assertEquals("code.example.org", request.url.host)
            assertEquals(8443, request.url.port)
            assertEquals("/tools%20team/bitbucket/rest/api/1.0/projects", request.url.encodedPath)
            assertNull(request.url.parameters["state"])
            respond(Fixtures.page(""), headers = jsonHeaders)
        }, custom)
        try { assertTrue(RestBitbucketRepository(api).projects().values.isEmpty()) }
        finally { api.close() }
    }
}
