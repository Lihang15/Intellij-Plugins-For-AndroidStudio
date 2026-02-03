package org.jetbrains.plugins.template.projectsync

import com.intellij.openapi.diagnostic.thisLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Guard to ensure idempotent configuration creation.
 * Prevents multiple simultaneous attempts to create the same configuration.
 */
object ConfigurationCreationGuard {
    private val logger = thisLogger()
    private val creationAttempts = ConcurrentHashMap<String, AtomicBoolean>()

    /**
     * Attempts to acquire creation lock for a project.
     * Returns true if this is the first attempt, false if already attempted.
     *
     * @param projectPath The project path to guard
     * @return true if lock acquired (first attempt), false if already attempted
     */
    fun tryAcquire(projectPath: String): Boolean {
        val flag = creationAttempts.computeIfAbsent(projectPath) {
            AtomicBoolean(false)
        }

        val acquired = flag.compareAndSet(false, true)

        if (acquired) {
            logger.info("[ConfigGuard] ✅ Lock acquired for project: $projectPath")
        } else {
            logger.info("[ConfigGuard] ⏭️ Lock already held for project: $projectPath, skipping")
        }

        return acquired
    }

    /**
     * Releases the creation lock (for testing or retry scenarios).
     *
     * @param projectPath The project path to release
     */
    fun release(projectPath: String) {
        creationAttempts[projectPath]?.set(false)
        logger.info("[ConfigGuard] 🔓 Lock released for project: $projectPath")
    }

    /**
     * Checks if a lock is currently held for a project.
     *
     * @param projectPath The project path to check
     * @return true if lock is held, false otherwise
     */
    fun isHeld(projectPath: String): Boolean {
        return creationAttempts[projectPath]?.get() ?: false
    }

    /**
     * Clears all locks (for testing purposes).
     */
    fun clearAll() {
        creationAttempts.clear()
        logger.info("[ConfigGuard] 🧹 All locks cleared")
    }
}
