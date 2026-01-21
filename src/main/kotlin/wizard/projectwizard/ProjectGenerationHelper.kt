package wizard.projectwizard

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.template.projectsync.DefaultFileConflictResolver
import org.jetbrains.plugins.template.projectsync.DefaultVfsRefreshQueue
import org.jetbrains.plugins.template.projectsync.FileConflictResolver
import org.jetbrains.plugins.template.projectsync.VfsRefreshQueue
import org.jetbrains.plugins.template.projectsync.ThreadSafetyChecker
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Helper class for project generation with conflict resolution and optimized VFS refresh.
 * 
 * Thread-safe: Can be used from background tasks.
 */
class ProjectGenerationHelper(
    private val conflictResolver: FileConflictResolver = DefaultFileConflictResolver(),
    private val refreshQueue: VfsRefreshQueue = DefaultVfsRefreshQueue()
) {
    
    /**
     * Copies a file with conflict resolution.
     * Returns true if file was copied, false if skipped due to conflict.
     */
    fun copyFileWithConflictResolution(source: Path, target: Path): Boolean {
        // Warn if on EDT (file I/O should be on background thread)
        ThreadSafetyChecker.warnIfOnEDT("copyFileWithConflictResolution")
        
        // Check if we should copy this file
        if (!conflictResolver.shouldCopyFile(source, target)) {
            return false
        }
        
        // Ensure parent directory exists
        Files.createDirectories(target.parent)
        
        // Copy the file
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        
        // Queue for VFS refresh (will be batched)
        refreshQueue.queueRefreshByPath(target.toFile())
        
        return true
    }
    
    /**
     * Copies a directory recursively with conflict resolution.
     * Returns the number of files copied.
     */
    fun copyDirectoryWithConflictResolution(source: Path, target: Path): Int {
        var copiedCount = 0
        
        if (!Files.exists(source)) {
            return 0
        }
        
        Files.walk(source).use { paths ->
            paths.forEach { sourcePath ->
                val targetPath = target.resolve(source.relativize(sourcePath))
                
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath)
                } else if (Files.isRegularFile(sourcePath)) {
                    if (copyFileWithConflictResolution(sourcePath, targetPath)) {
                        copiedCount++
                    }
                }
            }
        }
        
        return copiedCount
    }
    
    /**
     * Writes content to a file with conflict resolution.
     * If file exists and should be merged (like settings.gradle.kts), performs merge.
     */
    fun writeFileWithConflictResolution(
        target: Path,
        content: String,
        shouldMerge: Boolean = false
    ): Boolean {
        val targetFile = target.toFile()
        
        if (targetFile.exists() && shouldMerge && targetFile.name == "settings.gradle.kts") {
            // Merge settings.gradle.kts
            val existingContent = targetFile.readText()
            val mergedContent = conflictResolver.mergeSettingsGradle(content, existingContent)
            targetFile.writeText(mergedContent)
            refreshQueue.queueRefreshByPath(targetFile)
            return true
        }
        
        // Check if we should write this file
        if (!conflictResolver.shouldCopyFile(target, target)) {
            return false
        }
        
        // Ensure parent directory exists
        Files.createDirectories(target.parent)
        
        // Write the file
        targetFile.writeText(content)
        refreshQueue.queueRefreshByPath(targetFile)
        
        return true
    }
    
    /**
     * Flushes all queued VFS refresh operations.
     * Should be called after all file operations are complete.
     * Must be called from a coroutine context.
     */
    suspend fun flushVfsRefresh(): Boolean {
        return refreshQueue.flush()
    }
    
    /**
     * Flushes VFS refresh synchronously on EDT with async refresh.
     * Use this when you need to ensure VFS is updated before proceeding.
     */
    fun flushVfsRefreshSync(rootDir: VirtualFile) {
        ApplicationManager.getApplication().invokeAndWait {
            // Assert we're on EDT for VFS operations
            ThreadSafetyChecker.assertIsEDT("flushVfsRefreshSync")
            
            VfsUtil.markDirtyAndRefresh(
                true,  // async - don't block EDT
                true,  // recursive
                true,  // reload from disk
                rootDir
            )
        }
    }
}
