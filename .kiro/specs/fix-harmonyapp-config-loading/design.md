# Design Document: Fix HarmonyApp Configuration Loading

## Overview

This design addresses the race condition between project file creation and VFS synchronization that prevents the harmonyApp run configuration from appearing on first project open. The solution replaces the fixed 1-second delay with a robust VFS-aware approach that waits for actual file system readiness before attempting configuration creation.

The core strategy is to:
1. Use VFS APIs instead of direct File I/O for all file system checks
2. Implement a VFS readiness check that polls until required files are visible
3. Add a VFS listener as a fallback mechanism for delayed file appearance
4. Ensure all operations are properly sequenced on the correct threads (EDT vs background)

## Architecture

### Current Flow (Problematic)

```
Project Open
    ↓
ProjectActivity.execute()
    ↓
delay(1000ms) ← Fixed delay, may be insufficient
    ↓
isHarmonyOSProject() ← Uses File I/O, may not see files yet
    ↓
autoCreateharmonyAppConfiguration()
    ↓
invokeLater { create config } ← May execute before VFS refresh completes
```

### New Flow (Robust)

```
Project Open
    ↓
ProjectActivity.execute()
    ↓
waitForVfsReady() ← Polls VFS until files are visible (with timeout)
    ↓
isHarmonyOSProject() ← Uses VFS APIs
    ↓
autoCreateharmonyAppConfiguration()
    ↓
invokeAndWait { create config } ← Synchronous to ensure completion
    ↓
[Fallback] Register VFS listener for delayed file appearance
```

## Components and Interfaces

### 1. VfsReadinessChecker

A utility component that waits for VFS to be synchronized with the file system.

```kotlin
interface VfsReadinessChecker {
    /**
     * Waits for VFS to contain the specified paths.
     * Returns true if all paths are found within timeout, false otherwise.
     */
    suspend fun waitForPaths(
        project: Project,
        paths: List<String>,
        timeoutMs: Long = 5000,
        pollIntervalMs: Long = 100
    ): Boolean
    
    /**
     * Checks if a path exists in VFS (non-blocking).
     */
    fun existsInVfs(project: Project, relativePath: String): Boolean
}

class DefaultVfsReadinessChecker : VfsReadinessChecker {
    override suspend fun waitForPaths(
        project: Project,
        paths: List<String>,
        timeoutMs: Long,
        pollIntervalMs: Long
    ): Boolean {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val allFound = paths.all { path ->
                existsInVfs(project, path)
            }
            
            if (allFound) {
                return true
            }
            
            delay(pollIntervalMs)
        }
        
        return false
    }
    
    override fun existsInVfs(project: Project, relativePath: String): Boolean {
        val basePath = project.basePath ?: return false
        val fullPath = "$basePath/$relativePath"
        
        // Must be called from read action
        return ApplicationManager.getApplication().runReadAction<Boolean> {
            LocalFileSystem.getInstance().findFileByPath(fullPath) != null
        }
    }
}
```

### 2. Enhanced MyProjectActivity

Modified to use VFS-aware checking and proper synchronization.

```kotlin
class MyProjectActivity : ProjectActivity {
    private val logger = thisLogger()
    private val syncExecutor: SyncExecutor = DefaultSyncExecutor()
    private val vfsChecker: VfsReadinessChecker = DefaultVfsReadinessChecker()
    
    override suspend fun execute(project: Project) {
        logger.info("[HarmonyOS] MyProjectActivity starting for project: ${project.name}")
        
        // Wait for VFS to be ready before checking project structure
        val vfsReady = waitForVfsReady(project)
        
        if (vfsReady) {
            // VFS is ready, proceed with configuration creation
            try {
                autoCreateharmonyAppConfiguration(project)
            } catch (e: Exception) {
                logger.error("[HarmonyOS] Failed to create harmonyApp configuration", e)
            }
        } else {
            // VFS not ready within timeout, register listener as fallback
            logger.warn("[HarmonyOS] VFS not ready within timeout, registering fallback listener")
            registerVfsFallbackListener(project)
        }
        
        // Start project sync in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                syncProjectData(project)
                LocalPropertiesListener(project).register()
            } catch (e: Exception) {
                logger.error("Sync failed but project will continue to open", e)
            }
        }
        
        logger.info("[HarmonyOS] MyProjectActivity completed for project: ${project.name}")
    }
    
    /**
     * Waits for VFS to contain HarmonyOS project markers.
     * Returns true if VFS is ready, false if timeout.
     */
    private suspend fun waitForVfsReady(project: Project): Boolean {
        val basePath = project.basePath
        if (basePath == null) {
            logger.warn("[HarmonyOS] Project basePath is null")
            return false
        }
        
        logger.info("[HarmonyOS] Waiting for VFS to be ready...")
        
        // Check for either harmonyApp directory or local.properties
        val pathsToCheck = listOf(
            "harmonyApp",
            "local.properties"
        )
        
        val ready = vfsChecker.waitForPaths(
            project = project,
            paths = pathsToCheck,
            timeoutMs = 5000,
            pollIntervalMs = 100
        )
        
        if (ready) {
            logger.info("[HarmonyOS] VFS is ready, found project markers")
        } else {
            logger.warn("[HarmonyOS] VFS not ready within timeout")
        }
        
        return ready
    }
    
    /**
     * Registers a VFS listener as fallback for delayed file appearance.
     */
    private fun registerVfsFallbackListener(project: Project) {
        val listener = object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                // Check if any event involves harmonyApp directory or local.properties
                val relevantEvent = events.any { event ->
                    val path = event.path
                    path.contains("harmonyApp") || path.endsWith("local.properties")
                }
                
                if (relevantEvent) {
                    logger.info("[HarmonyOS] VFS event detected, attempting configuration creation")
                    
                    // Unregister this listener to prevent multiple attempts
                    project.messageBus.connect().disconnect()
                    
                    // Attempt configuration creation
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            autoCreateharmonyAppConfiguration(project)
                        } catch (e: Exception) {
                            logger.error("[HarmonyOS] Fallback configuration creation failed", e)
                        }
                    }
                }
            }
        }
        
        project.messageBus.connect().subscribe(
            VirtualFileManager.VFS_CHANGES,
            listener
        )
        
        logger.info("[HarmonyOS] VFS fallback listener registered")
    }
    
    /**
     * Creates harmonyApp run configuration using VFS-aware checks.
     */
    private suspend fun autoCreateharmonyAppConfiguration(project: Project) {
        val basePath = project.basePath
        if (basePath == null) {
            logger.warn("[HarmonyOS] Project basePath is null, cannot create configuration")
            return
        }
        
        logger.info("[HarmonyOS] Checking if HarmonyOS project...")
        
        // Check using VFS APIs
        if (!isHarmonyOSProjectVfs(project)) {
            logger.info("[HarmonyOS] Not a HarmonyOS project, skipping configuration")
            return
        }
        
        logger.info("[HarmonyOS] ✅ Detected HarmonyOS project, creating configuration")
        
        // Use invokeAndWait for synchronous execution to ensure completion
        ApplicationManager.getApplication().invokeAndWait {
            try {
                createConfigurationOnEdt(project)
            } catch (e: Exception) {
                logger.error("[HarmonyOS] ❌ Failed to create configuration on EDT", e)
            }
        }
    }
    
    /**
     * Checks if project is HarmonyOS using VFS APIs.
     */
    private fun isHarmonyOSProjectVfs(project: Project): Boolean {
        return ApplicationManager.getApplication().runReadAction<Boolean> {
            val basePath = project.basePath ?: return@runReadAction false
            val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath)
                ?: return@runReadAction false
            
            // Rule 1: Check for harmonyApp directory
            val harmonyAppDir = baseDir.findChild("harmonyApp")
            if (harmonyAppDir != null && harmonyAppDir.isDirectory) {
                logger.info("[HarmonyOS] ✅ Found harmonyApp directory in VFS")
                return@runReadAction true
            }
            
            // Rule 2: Check local.properties for local.ohos.path
            val localPropsFile = baseDir.findChild("local.properties")
            if (localPropsFile != null && !localPropsFile.isDirectory) {
                try {
                    val content = String(localPropsFile.contentsToByteArray())
                    val properties = java.util.Properties()
                    properties.load(content.byteInputStream())
                    
                    val ohosPath = properties.getProperty("local.ohos.path")?.trim()
                    if (!ohosPath.isNullOrEmpty()) {
                        val ohosDir = LocalFileSystem.getInstance().findFileByPath(ohosPath)
                        if (ohosDir != null && ohosDir.isDirectory) {
                            logger.info("[HarmonyOS] ✅ Found valid local.ohos.path in VFS")
                            return@runReadAction true
                        }
                    }
                } catch (e: Exception) {
                    logger.error("[HarmonyOS] Error reading local.properties", e)
                }
            }
            
            false
        }
    }
    
    /**
     * Creates the run configuration on EDT thread.
     * Must be called from EDT.
     */
    private fun createConfigurationOnEdt(project: Project) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        
        val runManager = RunManager.getInstance(project)
        logger.info("[HarmonyOS] RunManager obtained")
        
        // Check if configuration already exists (idempotent)
        val existingConfig = runManager.allSettings.find { settings ->
            settings.type is HarmonyConfigurationType && settings.name == "harmonyApp"
        }
        
        if (existingConfig != null) {
            logger.info("[HarmonyOS] Configuration already exists, skipping")
            return
        }
        
        logger.info("[HarmonyOS] Creating new configuration...")
        
        // Create configuration
        val configurationType = HarmonyConfigurationType.getInstance()
        val factory = configurationType.configurationFactories.firstOrNull()
        
        if (factory == null) {
            logger.error("[HarmonyOS] ❌ Configuration factory not found")
            return
        }
        
        val settings = runManager.createConfiguration("harmonyApp", factory)
        runManager.addConfiguration(settings)
        
        // Set as selected if no other configuration is selected
        if (runManager.selectedConfiguration == null) {
            runManager.selectedConfiguration = settings
            logger.info("[HarmonyOS] Set as selected configuration")
        }
        
        logger.info("[HarmonyOS] ✅ Configuration created successfully")
        logger.info("[HarmonyOS] Total configurations: ${runManager.allSettings.size}")
    }
    
    private suspend fun syncProjectData(project: Project) {
        when (val result = syncExecutor.syncProject(project)) {
            is Result.Success -> {
                logger.info("Project sync completed: version ${result.data.version}")
            }
            is Result.Failure -> {
                logger.error("Project sync failed: ${result.message}", result.error)
            }
        }
    }
}
```

### 3. Configuration Creation Guard

A utility to ensure idempotent configuration creation.

```kotlin
object ConfigurationCreationGuard {
    private val creationAttempts = ConcurrentHashMap<String, AtomicBoolean>()
    
    /**
     * Attempts to acquire creation lock for a project.
     * Returns true if this is the first attempt, false if already attempted.
     */
    fun tryAcquire(projectPath: String): Boolean {
        val flag = creationAttempts.computeIfAbsent(projectPath) {
            AtomicBoolean(false)
        }
        return flag.compareAndSet(false, true)
    }
    
    /**
     * Releases the creation lock (for testing or retry scenarios).
     */
    fun release(projectPath: String) {
        creationAttempts[projectPath]?.set(false)
    }
}
```

## Data Models

### VfsReadinessResult

```kotlin
data class VfsReadinessResult(
    val ready: Boolean,
    val foundPaths: List<String>,
    val missingPaths: List<String>,
    val elapsedMs: Long
)
```

### ConfigurationCreationResult

```kotlin
sealed class ConfigurationCreationResult {
    object Success : ConfigurationCreationResult()
    object AlreadyExists : ConfigurationCreationResult()
    data class Failed(val reason: String, val exception: Exception?) : ConfigurationCreationResult()
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*


### Property 1: VFS Synchronization Before Detection
*For any* project opening, the system should not attempt to detect HarmonyOS project markers until VFS reports that the relevant paths (harmonyApp directory or local.properties) are visible in the virtual file system.
**Validates: Requirements 1.1, 1.3**

### Property 2: VFS API Usage for File Checks
*For any* file system check during project detection, the system should use VFS APIs (LocalFileSystem, VirtualFile) rather than direct File I/O (java.io.File) to ensure consistency with IntelliJ's cached file system state.
**Validates: Requirements 1.2**

### Property 3: Project Detection Correctness
*For any* project that contains either a `harmonyApp` directory in VFS or a valid `local.ohos.path` in local.properties, the system should detect it as a HarmonyOS project.
**Validates: Requirements 2.1, 2.2**

### Property 4: Retry on VFS Not Ready
*For any* project where VFS has not yet indexed the project structure, the system should retry detection after waiting for VFS readiness or register a VFS listener to retry when files appear.
**Validates: Requirements 2.3**

### Property 5: Configuration Created on EDT
*For any* detected HarmonyOS project, when creating the run configuration, the system should execute the RunManager operations on the EDT thread.
**Validates: Requirements 3.1**

### Property 6: Configuration Appears in UI
*For any* successfully created harmonyApp configuration, querying RunManager.getInstance(project).allSettings should return a configuration with type HarmonyConfigurationType and name "harmonyApp".
**Validates: Requirements 3.2**

### Property 7: Idempotent Configuration Creation
*For any* project, regardless of how many times configuration creation is triggered, at most one harmonyApp configuration should exist in RunManager.
**Validates: Requirements 3.3, 6.1**

### Property 8: Selected Configuration Behavior
*For any* project where RunManager.selectedConfiguration is null before creation, after successfully creating the harmonyApp configuration, it should be set as the selected configuration.
**Validates: Requirements 3.4**

### Property 9: Graceful Error Handling
*For any* error during VFS refresh, project detection, or configuration creation, the system should catch the exception, log it with diagnostic information, and allow project opening to continue without throwing exceptions to the caller.
**Validates: Requirements 4.1, 4.2, 4.3, 4.4**

### Property 10: VFS Event Triggers Creation
*For any* VFS event that indicates the appearance of harmonyApp directory or local.properties file, the system should trigger configuration creation if it hasn't been created yet.
**Validates: Requirements 5.2, 5.3**

### Property 11: Listener Cleanup After Success
*For any* successful configuration creation via VFS listener, the listener should be unregistered from the message bus to prevent duplicate creation attempts.
**Validates: Requirements 5.4**

### Property 12: Atomic Configuration Creation
*For any* concurrent attempts to create the harmonyApp configuration (e.g., from multiple threads or VFS events), the atomic guard should ensure only one attempt proceeds with actual creation.
**Validates: Requirements 6.4**

### Property 13: Comprehensive Diagnostic Logging
*For any* VFS operation, project detection attempt, or configuration creation step, the system should produce log entries that include: operation type, timestamp, VFS state (which markers found/missing), and timing information.
**Validates: Requirements 2.4, 7.1, 7.2, 7.3, 7.4**

## Error Handling

### VFS Timeout Handling

If VFS is not ready within the timeout period (default 5 seconds):
1. Log a warning with diagnostic information (which paths were checked, how long waited)
2. Register a VFS listener as fallback mechanism
3. Continue with project opening (don't block)
4. The VFS listener will trigger configuration creation when files appear

### Configuration Creation Failures

If configuration creation fails:
1. Catch the exception at the top level
2. Log the full stack trace with context (project name, VFS state)
3. Do not retry automatically (to avoid infinite loops)
4. User can manually create configuration if needed

### Race Condition Prevention

Use ConfigurationCreationGuard to prevent multiple simultaneous creation attempts:
1. Before creating, check if already attempted for this project path
2. Use atomic compareAndSet to ensure only one thread proceeds
3. Other threads/attempts will see the flag and skip creation

### EDT Thread Safety

All RunManager operations must be on EDT:
1. Use ApplicationManager.getApplication().assertIsDispatchThread() to verify
2. Wrap in invokeAndWait when called from background thread
3. Log error if assertion fails (helps catch threading bugs)

## Testing Strategy

### Unit Tests

Unit tests should focus on specific components and edge cases:

1. **VfsReadinessChecker Tests**
   - Test timeout behavior when paths never appear
   - Test successful detection when paths appear immediately
   - Test polling behavior with delayed path appearance
   - Test with empty path list
   - Test with null project basePath

2. **Project Detection Tests**
   - Test detection with harmonyApp directory present
   - Test detection with local.ohos.path present
   - Test detection with both markers present
   - Test detection with neither marker present
   - Test detection with invalid local.ohos.path

3. **Configuration Creation Tests**
   - Test creation when no configuration exists
   - Test skipping when configuration already exists
   - Test selected configuration behavior
   - Test with null RunManager (error case)

4. **ConfigurationCreationGuard Tests**
   - Test first attempt returns true
   - Test second attempt returns false
   - Test concurrent attempts (only one succeeds)
   - Test release and re-acquire

### Property-Based Tests

Property tests should verify universal correctness properties across many generated inputs. Each test should run a minimum of 100 iterations.

1. **Property Test: VFS Synchronization Before Detection**
   - Generate random project structures with/without harmonyApp
   - Simulate VFS delays
   - Verify detection never runs before VFS reports readiness
   - **Tag: Feature: fix-harmonyapp-config-loading, Property 1: VFS Synchronization Before Detection**

2. **Property Test: Project Detection Correctness**
   - Generate random projects with various combinations of markers
   - Verify all projects with valid markers are detected
   - Verify projects without markers are not detected
   - **Tag: Feature: fix-harmonyapp-config-loading, Property 3: Project Detection Correctness**

3. **Property Test: Idempotent Configuration Creation**
   - Generate random number of creation attempts (1-100)
   - Verify exactly one configuration exists after all attempts
   - **Tag: Feature: fix-harmonyapp-config-loading, Property 7: Idempotent Configuration Creation**

4. **Property Test: Graceful Error Handling**
   - Generate random error scenarios (VFS failures, null pointers, etc.)
   - Verify no exceptions propagate to caller
   - Verify project opening continues
   - **Tag: Feature: fix-harmonyapp-config-loading, Property 9: Graceful Error Handling**

5. **Property Test: Atomic Configuration Creation**
   - Generate random number of concurrent creation attempts
   - Verify only one attempt proceeds with actual creation
   - Verify final state has exactly one configuration
   - **Tag: Feature: fix-harmonyapp-config-loading, Property 12: Atomic Configuration Creation**

6. **Property Test: Comprehensive Diagnostic Logging**
   - Generate random sequences of operations
   - Verify all operations produce log entries
   - Verify logs contain required information (timestamps, state, markers)
   - **Tag: Feature: fix-harmonyapp-config-loading, Property 13: Comprehensive Diagnostic Logging**

### Integration Tests

Integration tests should verify the complete flow:

1. **End-to-End Project Opening**
   - Create a test project with harmonyApp directory
   - Trigger ProjectActivity
   - Verify configuration appears in RunManager
   - Verify configuration is selected

2. **VFS Listener Fallback**
   - Create project without triggering immediate VFS refresh
   - Verify VFS listener is registered
   - Simulate VFS event with harmonyApp appearance
   - Verify configuration is created
   - Verify listener is unregistered

3. **Multiple Project Opens**
   - Open project multiple times
   - Verify configuration is created only once
   - Verify no duplicate configurations

### Test Configuration

All property-based tests must:
- Run minimum 100 iterations (due to randomization)
- Use appropriate PBT library for Kotlin (e.g., Kotest property testing)
- Include clear failure messages with counterexamples
- Reference the design document property number in test tags

## Implementation Notes

### Thread Safety Considerations

1. **VFS Operations**: Must be in read action
   ```kotlin
   ApplicationManager.getApplication().runReadAction<T> {
       // VFS operations here
   }
   ```

2. **RunManager Operations**: Must be on EDT
   ```kotlin
   ApplicationManager.getApplication().invokeAndWait {
       // RunManager operations here
   }
   ```

3. **Background Work**: Use coroutines with Dispatchers.IO
   ```kotlin
   CoroutineScope(Dispatchers.IO).launch {
       // Background work here
   }
   ```

### Performance Considerations

1. **Polling Interval**: 100ms provides good balance between responsiveness and CPU usage
2. **Timeout**: 5 seconds is generous for VFS refresh on most systems
3. **VFS Listener**: Minimal overhead, only checks relevant events
4. **Atomic Guard**: ConcurrentHashMap provides O(1) lookup with minimal contention

### Backward Compatibility

This change is backward compatible:
- No API changes to public interfaces
- No changes to configuration format
- Existing configurations are preserved
- Only affects timing of automatic configuration creation

### Migration Path

No migration needed:
- Users with existing configurations: No change
- Users without configurations: Will get automatic creation with improved reliability
- No user action required
