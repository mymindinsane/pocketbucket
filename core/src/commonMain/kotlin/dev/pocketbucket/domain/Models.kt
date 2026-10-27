package dev.pocketbucket.domain

data class Project(val key: String, val name: String, val description: String?)
data class Repository(val id: Long, val slug: String, val name: String, val project: Project)
data class Person(val name: String, val displayName: String)
data class Reviewer(val user: Person, val status: String)
data class Branch(val id: String, val displayName: String, val repository: Repository)
data class PullRequest(
    val id: Long,
    val version: Int,
    val title: String,
    val description: String?,
    val state: String,
    val author: Person,
    val from: Branch,
    val to: Branch,
    val reviewers: List<Reviewer>,
)

/** One server page. Consumers explicitly request nextStart; no eager traversal. */
data class Page<T>(val values: List<T>, val nextStart: Int?)

interface BitbucketRepository {
    suspend fun validateConnection()
    suspend fun projects(start: Int = 0): Page<Project>
    suspend fun repositories(projectKey: String, start: Int = 0): Page<Repository>
    suspend fun pullRequests(projectKey: String, repositorySlug: String, start: Int = 0): Page<PullRequest>
    suspend fun pullRequest(projectKey: String, repositorySlug: String, id: Long): PullRequest
}
