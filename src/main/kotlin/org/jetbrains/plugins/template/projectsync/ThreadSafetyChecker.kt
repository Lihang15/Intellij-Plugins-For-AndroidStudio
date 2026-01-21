package org.jetbrains.plugins.template.projectsync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger

/**
 * Utility for checking thread safety in development mode.
 * These checks help ensure EDT and background thread rules are followed.
 */
object ThreadSafetyChecker {
    
    private val logger = thisLogger()
    private val isDevelopmentMode = System.getProperty("idea.is.internal")?.toBoolean() ?: false
    
    /**
     * Asserts that the current thread is the EDT.
     * Use this before UI operations.
     */
    fun assertIsEDT(operation: String) {
        if (isDevelopmentMode) {
            try {
                ApplicationManager.getApplication().assertIsDispatchThread()
            } catch (e: Exception) {
                logger.error("EDT violation: $operation must be called on EDT", e)
                throw e
            }
        }
    }
    
    /**
     * Asserts that the current thread is NOT the EDT.
     * Use this before I/O or long-running operations.
     */
    fun assertIsNotEDT(operation: String) {
        if (isDevelopmentMode) {
            if (ApplicationManager.getApplication().isDispatchThread) {
                val error = IllegalStateException("Background thread violation: $operation must not be called on EDT")
                logger.error(error.message, error)
                throw error
            }
        }
    }
    
    /**
     * Logs a warning if called on EDT (for operations that should be on background thread).
     */
    fun warnIfOnEDT(operation: String) {
        if (ApplicationManager.getApplication().isDispatchThread) {
            logger.warn("Performance warning: $operation is running on EDT, consider moving to background thread")
        }
    }
    
    /**
     * Logs a warning if called on background thread (for operations that should be on EDT).
     */
    fun warnIfNotOnEDT(operation: String) {
        if (!ApplicationManager.getApplication().isDispatchThread) {
            logger.warn("Thread safety warning: $operation is running on background thread, should be on EDT")
        }
    }
    
    /**
     * Gets the current thread name for logging.
     */
    fun getCurrentThreadInfo(): String {
        val thread = Thread.currentThread()
        val isEDT = ApplicationManager.getApplication().isDispatchThread
        return "Thread: ${thread.name}, EDT: $isEDT"
    }
}
