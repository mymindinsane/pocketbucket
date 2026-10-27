package dev.pocketbucket

import dev.pocketbucket.auth.*
import dev.pocketbucket.data.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
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
}
