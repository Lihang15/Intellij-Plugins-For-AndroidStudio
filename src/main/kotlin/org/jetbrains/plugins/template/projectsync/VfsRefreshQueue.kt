package org.jetbrains.plugins.template.projectsync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Batches and debounces VFS refresh operations to minimize file system overhead.
 * 
 * Thread-safe: Uses mutex for synchronization.
 */
interface VfsRefreshQueue {
    /**
     * Queues a single file for refresh.
     */
    fun queueRefresh(file: VirtualFile)
    
    /**
     * Queues multiple files for refresh.
     */
    fun queueRefresh(files: Collection<VirtualFile>)
    
    /**
     * Queues a file by its path.
     */
    fun queueRefreshByPath(file: File)
    
    /**
     * Flushes all queued refresh operations.
     * Returns true if refresh was successful.
     */
    suspend fun flush(): Boolean
}

/**
 * Default implementation with 500ms debounce delay.
 */
class DefaultVfsRefreshQueue(
    private val debounceDelayMs: Long = 500
) : VfsRefreshQueue {
    
    private val mutex = Mutex()
    private val queuedFiles = mutableSetOf<VirtualFile>()
    private val queuedPaths = mutableSetOf<File>()
    
    override fun queueRefresh(file: VirtualFile) {
        // Non-blocking add to queue
        synchronized(queuedFiles) {
            queuedFiles.add(file)
        }
    }
    
    override fun queueRefresh(files: Collection<VirtualFile>) {
        synchronized(queuedFiles) {
            queuedFiles.addAll(files)
        }
    }
    
    override fun queueRefreshByPath(file: File) {
        synchronized(queuedPaths) {
            queuedPaths.add(file)
        }
    }
    
    override suspend fun flush(): Boolean {
        // Debounce: wait for operations to settle
        delay(debounceDelayMs)
        
        return mutex.withLock {
            try {
                val filesToRefresh = synchronized(queuedFiles) {
                    queuedFiles.toList().also { queuedFiles.clear() }
                }
                
                val pathsToRefresh = synchronized(queuedPaths) {
                    queuedPaths.toList().also { queuedPaths.clear() }
                }
                
                if (filesToRefresh.isEmpty() && pathsToRefresh.isEmpty()) {
                    return@withLock true
                }
                
                // Execute refresh on EDT
                var success = true
                ApplicationManager.getApplication().invokeAndWait {
                    try {
                        // Refresh VirtualFiles
                        if (filesToRefresh.isNotEmpty()) {
                            VfsUtil.markDirtyAndRefresh(
                                false, // async
                                true,  // recursive
                                true,  // reload from disk
                                *filesToRefresh.toTypedArray()
                            )
                        }
                        
                        // Refresh by paths
                        pathsToRefresh.forEach { file ->
                            VfsUtil.markDirtyAndRefresh(
                                false,
                                true,
                                true,
                                file
                            )
                        }
                    } catch (e: Exception) {
                        success = false
                        throw e
                    }
                }
                
                success
            } catch (e: Exception) {
                // Log error but don't crash
                println("VfsRefreshQueue: Error during flush: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }
}
