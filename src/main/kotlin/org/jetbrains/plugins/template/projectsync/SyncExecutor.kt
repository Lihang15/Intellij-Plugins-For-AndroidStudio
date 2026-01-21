package org.jetbrains.plugins.template.projectsync

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Result type for operations that can fail.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Failure<T>(val error: Throwable, val message: String) : Result<T>()
}

/**
 * Handles project data synchronization with proper threading and validation.
 * 
 * All methods should be called from background threads (Dispatchers.IO).
 */
interface SyncExecutor {
    /**
     * Synchronizes project data by executing the sync script and parsing output.
     * Must be called from a background thread.
     */
    suspend fun syncProject(project: Project): Result<SyncDataModel>
    
    /**
     * Validates that the sync script exists and is executable.
     */
    fun validateScript(scriptPath: String): Boolean
    
    /**
     * Validates JSON content before parsing.
     */
    fun validateJson(jsonContent: String): Boolean
}

/**
 * Default implementation of SyncExecutor.
 */
class DefaultSyncExecutor(
    private val scriptPath: String = "/Users/admin/generate.sh",
    private val timeoutSeconds: Long = 30
) : SyncExecutor {
    
    private val logger = thisLogger()
    
    override suspend fun syncProject(project: Project): Result<SyncDataModel> {
        return withContext(Dispatchers.IO) {
            // Assert we're not on EDT for I/O operations
            ThreadSafetyChecker.assertIsNotEDT("syncProject")
            
            try {
                val projectPath = project.basePath 
                    ?: return@withContext Result.Failure<SyncDataModel>(
                        IllegalStateException("Project path is null"),
                        "Project path is null"
                    )
                
                // Validate script exists
                if (!validateScript(scriptPath)) {
                    val error = IllegalStateException("Sync script not found: $scriptPath")
                    logger.error("Sync script validation failed", error)
                    return@withContext Result.Failure<SyncDataModel>(
                        error,
                        "Sync script not found at $scriptPath. Please check the script path."
                    )
                }
                
                // Execute script
                val jsonFile = File(projectPath, "sync-output-data.json")
                val scriptResult = executeScript(projectPath, jsonFile)
                
                if (scriptResult is Result.Failure) {
                    return@withContext Result.Failure<SyncDataModel>(
                        scriptResult.error,
                        scriptResult.message
                    )
                }
                
                // Read and validate JSON
                if (!jsonFile.exists()) {
                    val error = IllegalStateException("Sync output file not found")
                    logger.error("Sync output file not found: ${jsonFile.absolutePath}", error)
                    return@withContext Result.Failure<SyncDataModel>(
                        error,
                        "Sync script completed but output file not found: ${jsonFile.absolutePath}"
                    )
                }
                
                val jsonContent = FileUtil.loadFile(jsonFile, "UTF-8")
                
                if (!validateJson(jsonContent)) {
                    val error = JsonSyntaxException("Invalid JSON structure")
                    logger.error("Invalid JSON in sync output", error)
                    return@withContext Result.Failure<SyncDataModel>(
                        error,
                        "Sync output JSON is invalid. Check ${jsonFile.absolutePath}"
                    )
                }
                
                // Parse JSON
                val model = try {
                    Gson().fromJson(jsonContent, SyncDataModel::class.java)
                } catch (e: JsonSyntaxException) {
                    logger.error("Failed to parse sync JSON", e)
                    return@withContext Result.Failure<SyncDataModel>(
                        e,
                        "Failed to parse sync output: ${e.message}"
                    )
                }
                
                // Store in service
                DataSyncService.getInstance(project).dataModel = model
                
                // Refresh VFS for the JSON file
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jsonFile)
                
                logger.info("ProjectSync Success: Data loaded, version: ${model.version}")
                Result.Success(model)
                
            } catch (e: Exception) {
                logger.error("ProjectSync Exception", e)
                Result.Failure(e, "Sync failed: ${e.message}")
            }
        }
    }
    
    override fun validateScript(scriptPath: String): Boolean {
        val scriptFile = File(scriptPath)
        return scriptFile.exists() && scriptFile.canExecute()
    }
    
    override fun validateJson(jsonContent: String): Boolean {
        if (jsonContent.isBlank()) return false
        
        // Basic JSON structure validation
        val trimmed = jsonContent.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return false
        }
        
        // Check for required fields
        return trimmed.contains("\"version\"")
    }
    
    private suspend fun executeScript(
        projectPath: String,
        jsonFile: File
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder("sh", scriptPath, projectPath)
                    .directory(File(projectPath))
                    .redirectErrorStream(true)
                    .start()
                
                // Wait for completion with timeout
                val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                
                if (!completed) {
                    process.destroyForcibly()
                    val error = IllegalStateException("Script execution timed out after $timeoutSeconds seconds")
                    logger.error("Script execution timeout", error)
                    return@withContext Result.Failure(
                        error,
                        "Sync script timed out after $timeoutSeconds seconds"
                    )
                }
                
                val exitCode = process.exitValue()
                if (exitCode != 0) {
                    val output = process.inputStream.bufferedReader().readText()
                    val error = IllegalStateException("Script exited with code $exitCode")
                    logger.error("Script execution failed: $output", error)
                    return@withContext Result.Failure(
                        error,
                        "Sync script failed with exit code $exitCode. Output: $output"
                    )
                }
                
                Result.Success(Unit)
                
            } catch (e: Exception) {
                logger.error("Script execution exception", e)
                Result.Failure(e, "Failed to execute sync script: ${e.message}")
            }
        }
    }
}
