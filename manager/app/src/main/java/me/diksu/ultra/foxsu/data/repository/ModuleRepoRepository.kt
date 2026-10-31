package me.diksu.ultra.foxsu.data.repository

import me.diksu.ultra.foxsu.data.model.RepoModuleDetail
import me.diksu.ultra.foxsu.data.model.RepoModuleList
import me.diksu.ultra.foxsu.data.registry.UpdateVerification

interface ModuleRepoRepository {
    /** Index + module list; falls back to the last complete cached copy on failure. */
    suspend fun fetchModules(): Result<RepoModuleList>

    /**
     * Detail (and readme) of a module in the current index. Fails with
     * [me.diksu.ultra.foxsu.data.registry.ModuleNotInIndexException] if it is not listed.
     */
    suspend fun fetchDetail(moduleId: String): Result<RepoModuleDetail>

    /** Sanitized changelog HTML of a release (by tag, or the stable release), or null. */
    suspend fun fetchChangelogHtml(moduleId: String, tag: String?): String?

    /**
     * How an `updateJson` update of an installed module must be verified: against the matching
     * registry release, rejected, or legacy (not a registry update).
     */
    suspend fun resolveUpdate(moduleId: String, updateJsonUrl: String, zipUrl: String, versionCode: Int): UpdateVerification

    /** Raw Markdown of a registry-hosted document (e.g. `changelog.md`), or null if not registry-hosted. */
    suspend fun fetchRegistryText(url: String): String?

    /** Whether [url] is served by the registry site (its content must use the restricted renderer). */
    fun isRegistryUrl(url: String): Boolean
}
