package dev.pocketbucket.data

import dev.pocketbucket.domain.*

/** Version-specific DTOs and paths stay here, independent of presentation. */
class RestBitbucketRepository(private val api: BitbucketApi) : BitbucketRepository {
    override suspend fun validateConnection() {
        // Unlike public projects, the personal dashboard requires an authenticated user.
        api.get<PageDto<PullRequestDto>>(listOf("dashboard", "pull-requests"), start = 0, limit = 1)
            .toDomain(0) { it.toDomain() }
    }
    override suspend fun projects(start: Int): Page<Project> =
        api.get<PageDto<ProjectDto>>(listOf("projects"), start).toDomain(start) { it.toDomain() }

    override suspend fun repositories(projectKey: String, start: Int): Page<Repository> =
        api.get<PageDto<RepositoryDto>>(listOf("projects", projectKey, "repos"), start)
            .toDomain(start) { it.toDomain() }

    override suspend fun pullRequests(projectKey: String, repositorySlug: String, start: Int): Page<PullRequest> =
        api.get<PageDto<PullRequestDto>>(listOf("projects", projectKey, "repos", repositorySlug, "pull-requests"),
            start, state = "OPEN").toDomain(start) { it.toDomain() }

    override suspend fun pullRequest(projectKey: String, repositorySlug: String, id: Long): PullRequest {
        require(id > 0)
        return api.get<PullRequestDto>(listOf("projects", projectKey, "repos", repositorySlug,
            "pull-requests", id.toString())).toDomain()
    }
}
