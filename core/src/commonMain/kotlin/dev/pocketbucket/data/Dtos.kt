package dev.pocketbucket.data

import dev.pocketbucket.domain.*
import kotlinx.serialization.Serializable

@Serializable
internal data class PageDto<T>(
    val values: List<T>,
    val isLastPage: Boolean,
    val nextPageStart: Int? = null,
)

internal fun <T, R> PageDto<T>.toDomain(start: Int, map: (T) -> R): Page<R> {
    val next = if (isLastPage) null else nextPageStart
        ?.takeIf { it > start } ?: throw ApiFailure.InvalidResponse()
    return Page(values.map(map), next)
}

@Serializable
internal data class ProjectDto(val key: String, val name: String, val description: String? = null) {
    fun toDomain() = Project(key, name, description)
}
@Serializable
internal data class RepositoryDto(val id: Long, val slug: String, val name: String, val project: ProjectDto) {
    fun toDomain() = Repository(id, slug, name, project.toDomain())
}
@Serializable
internal data class PersonDto(val name: String, val displayName: String) {
    fun toDomain() = Person(name, displayName)
}
@Serializable
internal data class ParticipantDto(val user: PersonDto, val status: String = "UNAPPROVED")
@Serializable
internal data class BranchDto(val id: String, val displayId: String, val repository: RepositoryDto) {
    fun toDomain() = Branch(id, displayId, repository.toDomain())
}
@Serializable
internal data class PullRequestDto(
    val id: Long,
    val version: Int,
    val title: String,
    val description: String? = null,
    val state: String,
    val author: ParticipantDto,
    val fromRef: BranchDto,
    val toRef: BranchDto,
    val reviewers: List<ParticipantDto> = emptyList(),
) {
    fun toDomain() = PullRequest(id, version, title, description, state, author.user.toDomain(),
        fromRef.toDomain(), toRef.toDomain(), reviewers.map { Reviewer(it.user.toDomain(), it.status) })
}
