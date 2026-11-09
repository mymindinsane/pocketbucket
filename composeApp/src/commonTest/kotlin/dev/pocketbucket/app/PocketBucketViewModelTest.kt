@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.pocketbucket.app

import dev.pocketbucket.auth.*
import dev.pocketbucket.data.ApiFailure
import dev.pocketbucket.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class PocketBucketViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private class MemoryStore : CredentialStore {
        var value: Connection? = null
        var saves = 0
        var clears = 0
        var loadError: Exception? = null
        var saveError: Exception? = null
        var clearError: Exception? = null
        override suspend fun load(): Connection? {
            loadError?.let { throw it }
            return value
        }
        override suspend fun save(connection: Connection) {
            saveError?.let { throw it }
            value = connection
            saves++
        }
        override suspend fun clear() {
            clears++
            clearError?.let { throw it }
            value = null
        }
    }
    private class FakeRepository : BitbucketRepository {
        var authenticationError: ApiFailure? = null
        var projectsError: ApiFailure? = null
        var detailsError: ApiFailure? = null
        val requestedStarts = mutableListOf<Int>()
        val requests = mutableListOf<String>()
        override suspend fun validateConnection() { authenticationError?.let { throw it } }
        override suspend fun projects(start: Int): Page<Project> {
            requests += "projects:$start"
            projectsError?.let { throw it }
            requestedStarts += start
            return if (start == 0) Page(listOf(projectFixture), 73)
            else Page(listOf(Project("LUNAR", "Lunar Tools", null)), null)
        }
        override suspend fun repositories(projectKey: String, start: Int): Page<Repository> {
            requests += "repositories:$projectKey:$start"
            return Page(listOf(repositoryFixture), null)
        }
        override suspend fun pullRequests(projectKey: String, repositorySlug: String, start: Int): Page<PullRequest> {
            requests += "pullRequests:$projectKey:$repositorySlug:$start"
            return Page(listOf(pullRequestFixture), null)
        }
        override suspend fun pullRequest(projectKey: String, repositorySlug: String, id: Long): PullRequest {
            requests += "pullRequest:$projectKey:$repositorySlug:$id"
            detailsError?.let { throw it }
            return pullRequestFixture
        }
    }
    @Test fun connectionValidatesBeforeSavingAndLoadsOnePage() = runTest(dispatcher) {
        val store = MemoryStore()
        val repository = FakeRepository()
        val model = PocketBucketViewModel(store) { Session(repository) {} }
        runCurrent()
        model.connect("https://code.example.org", "alex.demo", "fictional-password", false)
        advanceUntilIdle()
        assertEquals(1, store.saves)
        assertEquals(Route.Projects, model.state.value.route)
        assertEquals(listOf(0), repository.requestedStarts)
        assertEquals(73, model.state.value.nextStart)
        model.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(0, 73), repository.requestedStarts)
        assertEquals(listOf("ORBIT", "LUNAR"), model.state.value.projects.map { it.key })
        assertNull(model.state.value.nextStart)
        model.disconnect()
        advanceUntilIdle()
        assertNull(store.value)
    }
    @Test fun rejectedAuthenticationIsNotPersistedAndClientIsClosed() = runTest(dispatcher) {
        val store = MemoryStore()
        var closed = false
        val repository = FakeRepository().apply { authenticationError = ApiFailure.Unauthorized() }
        val model = PocketBucketViewModel(store) { Session(repository) { closed = true } }
        runCurrent()
        model.connect("https://code.example.org", "alex.demo", "fictional-password", false)
        advanceUntilIdle()
        assertEquals(0, store.saves)
        assertTrue(closed)
        assertEquals(Route.Connection, model.state.value.route)
        assertTrue(model.state.value.error!!.contains("401"))
        assertFalse(model.state.value.loading)
    }
    @Test fun refreshReplacesPagesAndPreservesDataOnFailure() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = PocketBucketViewModel(MemoryStore()) { Session(repository) {} }
        runCurrent()
        model.connect("https://code.example.org", "", "fictional-token", true)
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        repository.projectsError = ApiFailure.Connection()
        model.refresh()
        advanceUntilIdle()
        assertEquals(2, model.state.value.projects.size)
        assertNotNull(model.state.value.error)
        repository.projectsError = null
        model.refresh()
        advanceUntilIdle()
        assertEquals(1, model.state.value.projects.size)
        assertNull(model.state.value.error)
        model.disconnect(); advanceUntilIdle()
    }
    @Test fun restoresSavedSessionWithoutRewritingSecret() = runTest(dispatcher) {
        val store = MemoryStore().apply {
            value = Connection(ServerUrl.parse("https://code.example.org"), Credentials.Token("fictional-token"))
        }
        val model = PocketBucketViewModel(store) { Session(FakeRepository()) {} }
        advanceUntilIdle()
        assertEquals(Route.Projects, model.state.value.route)
        assertEquals(0, store.saves)
        assertFalse(model.state.value.restoring)
        model.disconnect(); advanceUntilIdle()
    }
    @Test fun invalidUrlNeverConstructsSession() = runTest(dispatcher) {
        var created = false
        val model = PocketBucketViewModel(MemoryStore()) { created = true; Session(FakeRepository()) {} }
        runCurrent()
        model.connect("http://code.example.org", "", "fictional-token", true)
        assertFalse(created)
        assertNotNull(model.state.value.error)
    }
    @Test fun leavingPendingDestinationCancelsItsRequestAndDoesNotPublishLateData() = runTest(dispatcher) {
        val response = CompletableDeferred<Page<Repository>>()
        var cancelled = false
        val repository = object : BitbucketRepository by FakeRepository() {
            override suspend fun repositories(projectKey: String, start: Int): Page<Repository> {
                assertEquals(projectFixture.key, projectKey)
                assertEquals(0, start)
                try { return response.await() }
                finally { cancelled = !currentCoroutineContext().isActive }
            }
        }
        val model = PocketBucketViewModel(MemoryStore()) { Session(repository) {} }
        runCurrent()
        model.connect("https://code.example.org", "", "fictional-token", true)
        advanceUntilIdle()
        model.open(Route.Repositories(projectFixture))
        runCurrent()
        assertTrue(model.state.value.loading)
        model.back()
        advanceUntilIdle()
        assertTrue(cancelled)
        response.complete(Page(listOf(repositoryFixture), null))
        runCurrent()
        assertEquals(Route.Projects, model.state.value.route)
        assertEquals(listOf(projectFixture), model.state.value.projects)
        assertTrue(model.state.value.repositories.isEmpty())
        assertFalse(model.state.value.loading)
        assertNull(model.state.value.error)
        model.disconnect(); advanceUntilIdle()
    }
    @Test fun selectedRouteParametersAndBackChainLoadTheirMatchingScreens() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = PocketBucketViewModel(MemoryStore()) { Session(repository) {} }
        runCurrent()
        model.connect("https://code.example.org", "", "fictional-token", true)
        advanceUntilIdle()
        model.open(Route.Repositories(projectFixture)); advanceUntilIdle()
        assertEquals(listOf(repositoryFixture), model.state.value.repositories)
        model.open(Route.PullRequests(repositoryFixture)); advanceUntilIdle()
        assertEquals(listOf(pullRequestFixture), model.state.value.pullRequests)
        model.open(Route.Details(repositoryFixture, pullRequestFixture.id))
        assertTrue(model.state.value.loading)
        advanceUntilIdle()
        assertEquals(pullRequestFixture, model.state.value.detail)
        assertEquals(listOf("projects:0", "repositories:ORBIT:0", "pullRequests:ORBIT:flight-console:0",
            "pullRequest:ORBIT:flight-console:12"), repository.requests)
        model.back(); advanceUntilIdle()
        assertEquals(Route.PullRequests(repositoryFixture), model.state.value.route)
        assertNull(model.state.value.detail)
        model.back(); advanceUntilIdle()
        assertEquals(Route.Repositories(projectFixture), model.state.value.route)
        model.back(); advanceUntilIdle()
        assertEquals(Route.Projects, model.state.value.route)
        assertEquals(listOf("pullRequests:ORBIT:flight-console:0", "repositories:ORBIT:0", "projects:0"),
            repository.requests.takeLast(3))
        val callsAtRoot = repository.requests.size
        model.back(); runCurrent()
        assertEquals(callsAtRoot, repository.requests.size)
        assertFalse(model.state.value.loading)
        model.disconnect(); advanceUntilIdle()
    }
    @Test fun failedDetailsRefreshRetainsDescriptionAndCanBeRetried() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = PocketBucketViewModel(MemoryStore()) { Session(repository) {} }
        runCurrent()
        model.connect("https://code.example.org", "", "fictional-token", true)
        advanceUntilIdle()
        model.open(Route.Details(repositoryFixture, pullRequestFixture.id)); advanceUntilIdle()
        repository.detailsError = ApiFailure.Connection()
        model.refresh(); advanceUntilIdle()
        assertEquals(pullRequestFixture, model.state.value.detail)
        assertNotNull(model.state.value.error)
        assertFalse(model.state.value.loading)
        repository.detailsError = null
        model.refresh(); advanceUntilIdle()
        assertEquals(pullRequestFixture, model.state.value.detail)
        assertNull(model.state.value.error)
        model.disconnect(); advanceUntilIdle()
    }
    @Test fun unreadableSecureStoreFinishesRestoreWithSanitizedRecoveryError() = runTest(dispatcher) {
        val store = MemoryStore().apply {
            value = connectionFixture
            loadError = IllegalStateException("sensitive fictional storage details")
        }
        var sessions = 0
        val model = PocketBucketViewModel(store) { sessions++; Session(FakeRepository()) {} }
        advanceUntilIdle()
        assertEquals(0, sessions)
        assertEquals(Route.Connection, model.state.value.route)
        assertFalse(model.state.value.restoring)
        assertFalse(model.state.value.loading)
        assertFalse(assertNotNull(model.state.value.error).contains("sensitive"))
        assertSame(connectionFixture, store.value)
        model.disconnect(); advanceUntilIdle()
        assertNull(store.value)
    }
    @Test fun rejectedSavedCredentialsCloseCandidateAndRequireExplicitRemoval() = runTest(dispatcher) {
        val store = MemoryStore().apply { value = connectionFixture }
        val repository = FakeRepository().apply { authenticationError = ApiFailure.Unauthorized() }
        var closes = 0
        val model = PocketBucketViewModel(store) { Session(repository) { closes++ } }
        advanceUntilIdle()
        assertEquals(Route.Connection, model.state.value.route)
        assertFalse(model.state.value.restoring)
        assertFalse(model.state.value.loading)
        assertTrue(assertNotNull(model.state.value.error).contains("401"))
        assertEquals(1, closes)
        assertTrue(repository.requestedStarts.isEmpty())
        assertEquals(0, store.saves)
        assertSame(connectionFixture, store.value)
        model.disconnect(); advanceUntilIdle()
        assertNull(store.value)
        assertEquals(1, closes)
    }
    @Test fun failedCredentialSaveClosesCandidateBeforeOpeningProjectsAndAllowsRetry() = runTest(dispatcher) {
        val store = MemoryStore().apply { saveError = IllegalStateException("sensitive fictional storage details") }
        val repository = FakeRepository()
        var closes = 0
        val model = PocketBucketViewModel(store) { Session(repository) { closes++ } }
        runCurrent()
        model.connect("https://code.example.org", "", "fictional-token", true)
        advanceUntilIdle()
        assertEquals(Route.Connection, model.state.value.route)
        assertFalse(model.state.value.loading)
        assertFalse(assertNotNull(model.state.value.error).contains("sensitive"))
        assertEquals(1, closes)
        assertTrue(repository.requestedStarts.isEmpty())
        assertNull(store.value)
        store.saveError = null
        model.connect("https://code.example.org", "", "fictional-token", true)
        advanceUntilIdle()
        assertEquals(Route.Projects, model.state.value.route)
        assertEquals(1, store.saves)
        assertNull(model.state.value.error)
        model.disconnect(); advanceUntilIdle()
        assertEquals(2, closes)
    }
    @Test fun failedCredentialRemovalClosesSessionAndAllowsExplicitRetry() = runTest(dispatcher) {
        val store = MemoryStore()
        var closes = 0
        val model = PocketBucketViewModel(store) { Session(FakeRepository()) { closes++ } }
        runCurrent()
        model.connect("https://code.example.org", "", "fictional-token", true)
        advanceUntilIdle()
        val saved = assertNotNull(store.value)
        store.clearError = IllegalStateException("sensitive fictional storage details")
        model.disconnect(); advanceUntilIdle()
        assertEquals(Route.Connection, model.state.value.route)
        assertFalse(model.state.value.loading)
        assertFalse(assertNotNull(model.state.value.error).contains("sensitive"))
        assertSame(saved, store.value)
        assertEquals(1, closes)
        store.clearError = null
        model.disconnect(); advanceUntilIdle()
        assertNull(store.value)
        assertNull(model.state.value.error)
        assertFalse(model.state.value.loading)
        assertEquals(2, store.clears)
        assertEquals(1, closes)
    }
    @Test fun overlappingPagesKeepUniqueIdentitiesInServerOrderAcrossAllLists() = runTest(dispatcher) {
        val projects = listOf(
            Project("ORBIT", "Orbital Tools", null),
            Project("LUNAR", "Lunar Tools", null),
            Project("MARS", "Mars Tools", null),
        )
        val repositories = listOf(
            Repository(41, "flight-console", "Flight Console", projects[0]),
            Repository(42, "telemetry", "Telemetry", projects[0]),
            Repository(43, "mission-planner", "Mission Planner", projects[0]),
        )
        val author = Person("alex.demo", "Alex Demo")
        val pullRequests = (11L..13L).map { id ->
            PullRequest(id, 0, "Improve fictional flight view #$id", null, "OPEN", author,
                Branch("refs/heads/review-$id", "review-$id", repositories[0]),
                Branch("refs/heads/main", "main", repositories[0]), emptyList())
        }
        fun <T> page(start: Int, first: List<T>, last: List<T>): Page<T> {
            assertTrue(start == 0 || start == 73)
            return if (start == 0) Page(first, 73) else Page(last, null)
        }
        // Offset pagination can overlap when server-side lists change between requests.
        // Changed names/titles ensure deduplication uses identity, not object equality.
        val repository = object : BitbucketRepository by FakeRepository() {
            override suspend fun projects(start: Int) = page(start,
                projects.take(2) + projects[0].copy(name = "Updated Orbital Tools"),
                listOf(projects[1].copy(name = "Updated Lunar Tools"), projects[2]))
            override suspend fun repositories(projectKey: String, start: Int) = page(start,
                repositories.take(2), listOf(repositories[1].copy(name = "Updated Telemetry"), repositories[2]))
            override suspend fun pullRequests(projectKey: String, repositorySlug: String, start: Int) = page(start,
                pullRequests.take(2), listOf(pullRequests[1].copy(title = "Updated flight view"), pullRequests[2]))
        }
        val model = PocketBucketViewModel(MemoryStore()) { Session(repository) {} }
        runCurrent()
        model.connect("https://code.example.org", "", "fictional-token", true)
        advanceUntilIdle()
        assertEquals(listOf("ORBIT", "LUNAR"), model.state.value.projects.map { it.key })
        model.loadMore(); advanceUntilIdle()
        assertEquals(projects.map { it.key }, model.state.value.projects.map { it.key })
        assertNull(model.state.value.nextStart)
        model.open(Route.Repositories(projects[0])); advanceUntilIdle()
        model.loadMore(); advanceUntilIdle()
        assertEquals(repositories.map { it.id }, model.state.value.repositories.map { it.id })
        assertNull(model.state.value.nextStart)
        model.open(Route.PullRequests(repositories[0])); advanceUntilIdle()
        model.loadMore(); advanceUntilIdle()
        assertEquals(pullRequests.map { it.id }, model.state.value.pullRequests.map { it.id })
        assertNull(model.state.value.nextStart)
        model.refresh(); advanceUntilIdle()
        assertEquals(pullRequests.take(2).map { it.id }, model.state.value.pullRequests.map { it.id })
        assertEquals(73, model.state.value.nextStart)
        model.disconnect(); advanceUntilIdle()
    }
    @Test fun storedConnectionRoundTripsBothMethods() {
        val server = ServerUrl.parse("https://code.example.org/bitbucket")
        val basic = decodeConnection(Connection(server, Credentials.Basic("alex.demo", "fictional-password")).encode())
        assertEquals(server.value, basic.server.value)
        assertEquals("alex.demo", assertIs<Credentials.Basic>(basic.credentials).username)
        assertEquals("fictional-password", (basic.credentials as Credentials.Basic).password)
        val token = decodeConnection(Connection(server, Credentials.Token("fictional-token")).encode())
        assertEquals("fictional-token", assertIs<Credentials.Token>(token.credentials).token)
    }
    private companion object {
        val projectFixture = Project("ORBIT", "Orbital Tools", "Fictional spacecraft tooling")
        val repositoryFixture = Repository(41, "flight-console", "Flight Console", projectFixture)
        val pullRequestFixture = PullRequest(12, 0, "Improve flight chart", "Explain fictional chart behavior", "OPEN",
            Person("alex.demo", "Alex Demo"),
            Branch("refs/heads/feature/chart", "feature/chart", repositoryFixture),
            Branch("refs/heads/main", "main", repositoryFixture), emptyList())
        val connectionFixture = Connection(ServerUrl.parse("https://code.example.org"), Credentials.Token("fictional-token"))
    }

}
