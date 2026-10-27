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
        override suspend fun load() = value
        override suspend fun save(connection: Connection) { value = connection; saves++ }
        override suspend fun clear() { value = null }
    }
    private class FakeRepository : BitbucketRepository {
        var authenticationError: ApiFailure? = null
        var projectsError: ApiFailure? = null
        val requestedStarts = mutableListOf<Int>()
        override suspend fun validateConnection() { authenticationError?.let { throw it } }
        override suspend fun projects(start: Int): Page<Project> {
            projectsError?.let { throw it }
            requestedStarts += start
            return if (start == 0) Page(listOf(Project("ORBIT", "Orbital Tools", null)), 73)
            else Page(listOf(Project("LUNAR", "Lunar Tools", null)), null)
        }
        override suspend fun repositories(projectKey: String, start: Int) = Page<Repository>(emptyList(), null)
        override suspend fun pullRequests(projectKey: String, repositorySlug: String, start: Int) = Page<PullRequest>(emptyList(), null)
        override suspend fun pullRequest(projectKey: String, repositorySlug: String, id: Long): PullRequest = error("Not used")
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
    @Test fun storedConnectionRoundTripsBothMethods() {
        val server = ServerUrl.parse("https://code.example.org/bitbucket")
        val basic = decodeConnection(Connection(server, Credentials.Basic("alex.demo", "fictional-password")).encode())
        assertEquals(server.value, basic.server.value)
        assertEquals("alex.demo", assertIs<Credentials.Basic>(basic.credentials).username)
        assertEquals("fictional-password", (basic.credentials as Credentials.Basic).password)
        val token = decodeConnection(Connection(server, Credentials.Token("fictional-token")).encode())
        assertEquals("fictional-token", assertIs<Credentials.Token>(token.credentials).token)
    }
}
