package dev.pocketbucket.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(viewModel: PocketBucketViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        val title = when (val route = state.route) {
            Route.Connection -> "PocketBucket"
            Route.Projects -> "Projects"
            is Route.Repositories -> route.project.name
            is Route.PullRequests -> route.repository.name
            is Route.Details -> "Pull request #${route.id}"
        }
        Scaffold(topBar = {
            TopAppBar(title = { Text(title, maxLines = 1) }, navigationIcon = {
                if (state.route != Route.Connection && state.route != Route.Projects) {
                    TextButton(onClick = viewModel::back) { Text("Back") }
                }
            }, actions = {
                if (state.route != Route.Connection) {
                    TextButton(onClick = viewModel::refresh, enabled = !state.loading) { Text("Refresh") }
                    TextButton(onClick = viewModel::disconnect) { Text("Disconnect") }
                }
            })
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 840.dp).fillMaxSize().padding(horizontal = 16.dp)) {
                    state.error?.let { message ->
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                                if (state.route != Route.Connection) {
                                    TextButton(onClick = viewModel::refresh, enabled = !state.loading) { Text("Retry") }
                                }
                            }
                        }
                    }
                    if (state.restoring) {
                        CircularProgressIndicator(Modifier.padding(24.dp))
                    } else if (state.route == Route.Connection) {
                        ConnectionScreen(state.loading, viewModel::connect, viewModel::disconnect)
                    } else {
                        // Start each destination at the top, while preserving scroll on refresh.
                        key(state.route) {
                            PullToRefreshBox(isRefreshing = state.loading, onRefresh = viewModel::refresh) {
                                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 12.dp)) {
                                    when (val route = state.route) {
                                        Route.Projects -> {
                                            if (state.projects.isEmpty() && !state.loading && state.error == null) item { Text("No accessible projects.") }
                                            items(state.projects, key = { it.key }) { project ->
                                                Entry(project.name, project.key) { viewModel.open(Route.Repositories(project)) }
                                            }
                                        }
                                        is Route.Repositories -> {
                                            if (state.repositories.isEmpty() && !state.loading && state.error == null) item { Text("No repositories in this project.") }
                                            items(state.repositories, key = { it.id }) { repo ->
                                                Entry(repo.name, repo.slug) { viewModel.open(Route.PullRequests(repo)) }
                                            }
                                        }
                                        is Route.PullRequests -> {
                                            if (state.pullRequests.isEmpty() && !state.loading && state.error == null) item { Text("No open pull requests.") }
                                            items(state.pullRequests, key = { it.id }) { pr ->
                                                Entry("#${pr.id} ${pr.title}",
                                                    "${pr.author.displayName} · ${pr.state}\n${pr.from.displayName} → ${pr.to.displayName}") {
                                                    viewModel.open(Route.Details(route.repository, pr.id))
                                                }
                                            }
                                        }
                                        is Route.Details -> state.detail?.let { pr ->
                                            item { Text(pr.title, style = MaterialTheme.typography.headlineSmall) }
                                            item { Text("${pr.author.displayName} · ${pr.state}") }
                                            item { Text("${pr.from.repository.project.key}/${pr.from.repository.name}: ${pr.from.displayName} → ${pr.to.repository.project.key}/${pr.to.repository.name}: ${pr.to.displayName}") }
                                            item { Text(pr.description?.takeIf { it.isNotBlank() } ?: "No description.") }
                                            item { Text("Reviewers", style = MaterialTheme.typography.titleMedium) }
                                            if (pr.reviewers.isEmpty()) item { Text("No reviewers assigned.") }
                                            items(pr.reviewers, key = { it.user.name }) { reviewer ->
                                                Text("${reviewer.user.displayName} · ${reviewer.status}")
                                            }
                                        }
                                        Route.Connection -> Unit
                                    }
                                    if (state.loading) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
                                    if (state.nextStart != null) item {
                                        OutlinedButton(onClick = viewModel::loadMore, enabled = !state.loading) { Text("Load more") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Entry(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ConnectionScreen(
    busy: Boolean, connect: (String, String, String, Boolean) -> Unit, clear: () -> Unit,
) {
    // Secrets are memory-only UI state, deliberately not rememberSaveable.
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var token by remember { mutableStateOf(false) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item { Text("Connect to Bitbucket Data Center", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Use your existing device VPN when required. Credentials are stored securely on this device after successful authentication.") }
        item {
            OutlinedTextField(server, { server = it }, label = { Text("HTTPS server URL") },
                placeholder = { Text("https://code.example.org/bitbucket") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(), enabled = !busy)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !token, onClick = { token = false; secret = "" }, label = { Text("Username / password") }, enabled = !busy)
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = token, onClick = { token = true; secret = "" }, label = { Text("Token") }, enabled = !busy)
            }
        }
        if (!token) item {
            OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), enabled = !busy)
        }
        item {
            OutlinedTextField(secret, { secret = it }, label = { Text(if (token) "Personal access token" else "Password") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(), enabled = !busy)
        }
        item {
            Button(onClick = { connect(server, username, secret, token) },
                enabled = !busy && server.isNotBlank() && secret.isNotEmpty() && (token || username.isNotBlank())) {
                Text(if (busy) "Connecting…" else "Connect")
            }
        }
        if (busy) item { CircularProgressIndicator() }
        item { TextButton(onClick = clear, enabled = !busy) { Text("Remove saved connection") } }
        item { Text("Unofficial client. Not affiliated with Atlassian. Bitbucket Cloud is not supported.", style = MaterialTheme.typography.bodySmall) }
    }
}

@Preview
@Composable
private fun FictionalProjectPreview() {
    MaterialTheme {
        Surface { Entry("Orbital Tools", "ORBIT · Fictional project") {} }
    }
}
