package org.jetbrains.plugins.template.projectsync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.delay

/**
 * Default implementation of VfsReadinessChecker that polls VFS until files are visible.
 */
class DefaultVfsReadinessChecker : VfsReadinessChecker {
    private val logger = thisLogger()

    override suspend fun waitForPaths(
        project: Project,
        paths: List<String>,
        timeoutMs: Long,
        pollIntervalMs: Long
    ): Boolean {
        val result = waitForPathsWithResult(project, paths, timeoutMs, pollIntervalMs)
        return result.ready
    }

    override suspend fun waitForPathsWithResult(
        project: Project,
        paths: List<String>,
        timeoutMs: Long,
        pollIntervalMs: Long
    ): VfsReadinessResult {
        val startTime = System.currentTimeMillis()
        val foundPaths = mutableListOf<String>()
        val missingPaths = mutableListOf<String>()

        logger.info("[VfsReadiness] Starting VFS readiness check for ${paths.size} paths, timeout=${timeoutMs}ms")

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            foundPaths.clear()
            missingPaths.clear()

            // Check all paths
            for (path in paths) {
                if (existsInVfs(project, path)) {
                    foundPaths.add(path)
                } else {
                    missingPaths.add(path)
                }
            }

            // If all paths found, return success
            if (missingPaths.isEmpty()) {
                val elapsedMs = System.currentTimeMillis() - startTime
                logger.info("[VfsReadiness] All paths found in VFS after ${elapsedMs}ms")
                return VfsReadinessResult(
                    ready = true,
                    foundPaths = foundPaths.toList(),
                    missingPaths = emptyList(),
                    elapsedMs = elapsedMs
                )
            }

            // Wait before next poll
            delay(pollIntervalMs)
        }

        // Timeout reached
        val elapsedMs = System.currentTimeMillis() - startTime
        logger.warn("[VfsReadiness] ⏱️ Timeout reached after ${elapsedMs}ms. Found: $foundPaths, Missing: $missingPaths")

        return VfsReadinessResult(
            ready = false,
            foundPaths = foundPaths.toList(),
            missingPaths = missingPaths.toList(),
            elapsedMs = elapsedMs
        )
    }

    override fun existsInVfs(project: Project, relativePath: String): Boolean {
        val basePath = project.basePath
        if (basePath == null) {
            logger.warn("[VfsReadiness] Project basePath is null, cannot check path: $relativePath")
            return false
        }

        val fullPath = "$basePath/$relativePath"

        // Must be called from read action
        return ApplicationManager.getApplication().runReadAction<Boolean> {
            val file = LocalFileSystem.getInstance().findFileByPath(fullPath)
            val exists = file != null
            logger.debug("[VfsReadiness] Checking path '$relativePath': ${if (exists) "EXISTS" else "NOT FOUND"}")
            exists
        }
    }
}
