package dev.pocketbucket.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.pocketbucket.auth.*
import dev.pocketbucket.data.ApiFailure
import dev.pocketbucket.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface Route {
    data object Connection : Route
    data object Projects : Route
    data class Repositories(val project: Project) : Route
    data class PullRequests(val repository: Repository) : Route
    data class Details(val repository: Repository, val id: Long) : Route
}
data class AppState(
    val route: Route = Route.Connection,
    val loading: Boolean = false,
    val restoring: Boolean = true,
    val error: String? = null,
    val projects: List<Project> = emptyList(),
    val repositories: List<Repository> = emptyList(),
    val pullRequests: List<PullRequest> = emptyList(),
    val detail: PullRequest? = null,
    val nextStart: Int? = null,
)
class Session(val repository: BitbucketRepository, val close: () -> Unit)

class PocketBucketViewModel(
    private val store: CredentialStore,
    private val sessionFactory: (Connection) -> Session,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = mutableState.asStateFlow()
    private var session: Session? = null
    private var request: Job? = null

    init {
        request = viewModelScope.launch {
            try {
                val saved = store.load()
                mutableState.update { it.copy(restoring = false) }
                if (saved != null) establish(saved, persist = false)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                mutableState.update { it.copy(restoring = false, loading = false,
                    error = "Could not restore the secure session. Reconnect, or remove the saved connection.") }
            }
        }
    }

    fun connect(server: String, username: String, secret: String, token: Boolean) {
        if (state.value.loading || state.value.restoring) return
        val connection = try {
            Connection(ServerUrl.parse(server), if (token) Credentials.Token(secret) else Credentials.Basic(username, secret))
        } catch (e: IllegalArgumentException) {
            mutableState.update { it.copy(error = e.message) }
            return
        }
        request?.cancel()
        request = viewModelScope.launch { establish(connection, persist = true) }
    }
    private suspend fun establish(connection: Connection, persist: Boolean) {
        mutableState.update { it.copy(loading = true, error = null) }
        var candidate: Session? = null
        try {
            candidate = sessionFactory(connection)
            candidate.repository.validateConnection()
            if (persist) store.save(connection)
            session?.close?.invoke()
            session = candidate
            candidate = null
            mutableState.value = AppState(route = Route.Projects, restoring = false, loading = true)
            load(0, append = false)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            mutableState.update { it.copy(loading = false, error = message(e)) }
        } finally { candidate?.close?.invoke() }
    }

    fun open(route: Route) {
        request?.cancel()
        mutableState.value = AppState(route = route, restoring = false, loading = true)
        request = viewModelScope.launch { load(0, append = false) }
    }
    fun back() {
        val parent = when (val route = state.value.route) {
            is Route.Repositories -> Route.Projects
            is Route.PullRequests -> Route.Repositories(route.repository.project)
            is Route.Details -> Route.PullRequests(route.repository)
            else -> return
        }
        open(parent)
    }
    fun refresh() {
        if (state.value.loading) return
        request = viewModelScope.launch { load(0, append = false) }
    }
    fun loadMore() {
        if (state.value.loading) return
        val next = state.value.nextStart ?: return
        request = viewModelScope.launch { load(next, append = true) }
    }
    private suspend fun load(start: Int, append: Boolean) {
        val repository = session?.repository ?: return
        mutableState.update { it.copy(loading = true, error = null) }
        try {
            when (val route = state.value.route) {
                Route.Projects -> {
                    val page = repository.projects(start)
                    mutableState.update {
                        it.copy(
                            projects = ((if (append) it.projects else emptyList()) + page.values)
                                .distinctBy { project -> project.key },
                            nextStart = page.nextStart, loading = false,
                        )
                    }
                }
                is Route.Repositories -> {
                    val page = repository.repositories(route.project.key, start)
                    mutableState.update {
                        it.copy(
                            repositories = ((if (append) it.repositories else emptyList()) + page.values)
                                .distinctBy { item -> item.id },
                            nextStart = page.nextStart, loading = false,
                        )
                    }
                }
                is Route.PullRequests -> {
                    val repo = route.repository
                    val page = repository.pullRequests(repo.project.key, repo.slug, start)
                    mutableState.update {
                        it.copy(
                            pullRequests = ((if (append) it.pullRequests else emptyList()) + page.values)
                                .distinctBy { pr -> pr.id },
                            nextStart = page.nextStart, loading = false,
                        )
                    }
                }
                is Route.Details -> {
                    val pr = repository.pullRequest(route.repository.project.key, route.repository.slug, route.id)
                    mutableState.update { it.copy(detail = pr, loading = false) }
                }
                Route.Connection -> mutableState.update { it.copy(loading = false) }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutableState.update { it.copy(loading = false, error = message(e)) } }
    }
    fun disconnect() {
        request?.cancel()
        session?.close?.invoke()
        session = null
        mutableState.value = AppState(restoring = false, loading = true)
        request = viewModelScope.launch {
            try {
                store.clear()
                mutableState.update { it.copy(loading = false) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                mutableState.update { it.copy(loading = false, error = "Could not remove saved credentials. Retry removing the saved connection.") }
            }
        }
    }
    private fun message(error: Exception): String = when (error) {
        is ApiFailure -> error.userMessage
        else -> "The operation failed. Secure storage may be unavailable; unlock the device and retry."
    }
    override fun onCleared() { session?.close?.invoke() }
}
