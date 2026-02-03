package org.jetbrains.plugins.template.projectsync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.delay

/**
 * Result of VFS readiness check with diagnostic information.
 */
data class VfsReadinessResult(
    val ready: Boolean,
    val foundPaths: List<String>,
    val missingPaths: List<String>,
    val elapsedMs: Long
)

/**
 * Interface for checking VFS readiness and waiting for files to appear in VFS.
 */
interface VfsReadinessChecker {
    /**
     * Waits for VFS to contain the specified paths.
     * Returns true if all paths are found within timeout, false otherwise.
     *
     * @param project The project to check
     * @param paths List of relative paths to wait for
     * @param timeoutMs Maximum time to wait in milliseconds (default 5000ms)
     * @param pollIntervalMs Interval between checks in milliseconds (default 100ms)
     * @return true if all paths found, false if timeout
     */
    suspend fun waitForPaths(
        project: Project,
        paths: List<String>,
        timeoutMs: Long = 5000,
        pollIntervalMs: Long = 100
    ): Boolean

    /**
     * Waits for VFS to contain the specified paths with detailed result.
     *
     * @param project The project to check
     * @param paths List of relative paths to wait for
     * @param timeoutMs Maximum time to wait in milliseconds (default 5000ms)
     * @param pollIntervalMs Interval between checks in milliseconds (default 100ms)
     * @return VfsReadinessResult with diagnostic information
     */
    suspend fun waitForPathsWithResult(
        project: Project,
        paths: List<String>,
        timeoutMs: Long = 5000,
        pollIntervalMs: Long = 100
    ): VfsReadinessResult

    /**
     * Checks if a path exists in VFS (non-blocking).
     *
     * @param project The project to check
     * @param relativePath Relative path from project root
     * @return true if path exists in VFS, false otherwise
     */
    fun existsInVfs(project: Project, relativePath: String): Boolean
}
