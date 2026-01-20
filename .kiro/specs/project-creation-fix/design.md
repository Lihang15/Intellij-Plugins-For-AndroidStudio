# Design Document: Project Creation and Sync Fix

## Overview

This design addresses critical threading violations, file conflicts, and performance issues in the IntelliJ plugin's project creation and synchronization workflow. The solution refactors the `MyProjectActivity` and `KMPApplicationRecipe` to comply with IntelliJ Platform threading rules, implement smart file conflict resolution, and optimize VFS refresh operations.

## Architecture

### Component Structure

```
┌─────────────────────────────────────────────────────────────┐
│                    Plugin Lifecycle                          │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────┐         ┌──────────────────┐          │
│  │ MyProjectActivity│────────▶│  DataSyncService │          │
│  │  (Coroutine)     │         │  (Project-level) │          │
│  └────────┬─────────┘         └──────────────────┘          │
│           │                                                   │
│           │ Dispatchers.IO                                    │
│           ▼                                                   │
│  ┌──────────────────┐         ┌──────────────────┐          │
│  │  SyncExecutor    │────────▶│  VfsRefreshQueue │          │
│  │  (Background)    │         │  (Batched)       │          │
│  └──────────────────┘         └──────────────────┘          │
│                                                               │
├─────────────────────────────────────────────────────────────┤
│                  Project Creation Wizard                      │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────┐         ┌──────────────────┐          │
│  │ RecipeExecutor   │────────▶│ FileConflict     │          │
│  │ (EDT)            │         │ Resolver         │          │
│  └────────┬─────────┘         └──────────────────┘          │
│           │                                                   │
│           │ invokeLater                                       │
│           ▼                                                   │
│  ┌──────────────────┐         ┌──────────────────┐          │
│  │ ProjectGenerator │────────▶│ VfsRefreshQueue  │          │
│  │ (Background Task)│         │ (Batched)        │          │
│  └──────────────────┘         └──────────────────┘          │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### Threading Model

1. **EDT (Event Dispatch Thread)**: UI updates, project opening, wizard UI
2. **Coroutine (Dispatchers.IO)**: File I/O, script execution, JSON parsing
3. **Background Task**: Long-running project generation, template copying
4. **Thread Transitions**: Use `invokeLater` for EDT, `withContext` for coroutines

## Components and Interfaces

### 1. MyProjectActivity (Refactored)

**Purpose**: Project startup activity that initializes run configurations and triggers sync

**Key Changes**:
- Remove sample warning message
- Execute sync on `Dispatchers.IO`
- Add proper error handling
- Don't block project opening

```kotlin
class MyProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // Run configuration setup (fast, can stay on current thread)
        autoCreateMyMainAppConfiguration(project)
        
        // Sync on background thread
        launch(Dispatchers.IO) {
            try {
                syncProjectData(project)
            } catch (e: Exception) {
                // Log but don't crash
                thisLogger().error("Sync failed", e)
            }
        }
    }
}
```

### 2. SyncExecutor

**Purpose**: Handles project data synchronization with proper threading

**Interface**:
```kotlin
interface SyncExecutor {
    suspend fun syncProject(project: Project): Result<SyncDataModel>
    fun validateScript(scriptPath: String): Boolean
    fun validateJson(jsonContent: String): Boolean
}
```

**Implementation Details**:
- Check script existence before execution
- Execute script with timeout
- Validate JSON before parsing
- Store data in DataSyncService
- Batch VFS refresh after file generation

### 3. FileConflictResolver

**Purpose**: Intelligently handles file conflicts during project creation

**Interface**:
```kotlin
interface FileConflictResolver {
    fun shouldCopyFile(source: Path, target: Path): Boolean
    fun mergeSettingsGradle(template: String, existing: String): String
    fun preserveProjectFiles(targetDir: Path): Set<String>
}
```

**Strategy**:
- Check file existence before copying
- Skip copying if target exists (no warnings)
- Merge settings.gradle.kts instead of overwriting
- Preserve user-modified files

### 4. VfsRefreshQueue

**Purpose**: Batches and debounces VFS refresh operations

**Interface**:
```kotlin
interface VfsRefreshQueue {
    fun queueRefresh(file: VirtualFile)
    fun queueRefresh(files: Collection<VirtualFile>)
    suspend fun flush(): Boolean
}
```

**Implementation**:
- Collect refresh requests
- Debounce with 500ms delay
- Execute single async refresh
- Return completion status

### 5. ProjectGenerator (Refactored)

**Purpose**: Generates project structure in background task

**Key Changes**:
- Run as `Task.Backgroundable`
- Use FileConflictResolver
- Batch all file operations
- Single VFS refresh at end
- Open project on EDT

```kotlin
private fun generateProjectStructure(...) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(...) {
        override fun run(indicator: ProgressIndicator) {
            // All file operations
            copyFilesWithConflictResolution(...)
            
            // Single refresh
            ApplicationManager.getApplication().invokeAndWait {
                VfsUtil.markDirtyAndRefresh(...)
            }
            
            // Open on EDT
            ApplicationManager.getApplication().invokeLater {
                openProject(projectRoot)
            }
        }
    })
}
```

## Data Models

### SyncDataModel
```kotlin
data class SyncDataModel(
    val version: String,
    val projectData: Map<String, Any>
)
```

### FileConflictResult
```kotlin
sealed class FileConflictResult {
    object Copy : FileConflictResult()
    object Skip : FileConflictResult()
    data class Merge(val content: String) : FileConflictResult()
}
```

### SyncResult
```kotlin
sealed class Result<T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Failure<T>(val error: Throwable, val message: String) : Result<T>()
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Background Thread Execution

*For any* long-running operation (script execution, file I/O, computation), the operation should execute on a background thread (not EDT).

**Validates: Requirements 1.1, 1.2, 5.2**

### Property 2: EDT UI Updates

*For any* UI update operation (dialog display, project opening, UI element modification), the operation should execute on the EDT thread.

**Validates: Requirements 1.4, 5.1, 5.5**

### Property 3: File Existence Handling

*For any* file copy operation where the target file already exists, the system should skip the copy without generating warnings or errors.

**Validates: Requirements 2.1, 2.2, 2.4, 2.5**

### Property 4: Settings Gradle Merge

*For any* project creation where settings.gradle.kts already exists, the generated content should preserve existing module declarations and merge new ones.

**Validates: Requirements 2.3**

### Property 5: VFS Refresh Batching

*For any* sequence of file operations, the system should perform at most one VFS refresh after all operations complete.

**Validates: Requirements 1.5, 3.1, 3.2, 3.4**

### Property 6: Async VFS Refresh

*For any* VFS refresh operation, the system should use asynchronous refresh with proper scope (not blocking).

**Validates: Requirements 3.3**

### Property 7: Script Validation

*For any* sync operation, the system should verify the script exists before attempting execution.

**Validates: Requirements 4.1**

### Property 8: Graceful Script Failure

*For any* script execution failure, the system should catch the exception, log details, and continue without crashing.

**Validates: Requirements 4.2, 4.5**

### Property 9: JSON Validation

*For any* JSON parsing operation, the system should validate the JSON structure before parsing.

**Validates: Requirements 4.3**

### Property 10: Service Data Storage

*For any* successful sync operation, the resulting data should be accessible from the DataSyncService.

**Validates: Requirements 4.4**

### Property 11: Thread Transition Safety

*For any* transition from background thread to EDT, the system should use proper synchronization (invokeLater, invokeAndWait).

**Validates: Requirements 5.3**

### Property 12: Error Logging

*For any* exception that occurs, the system should log the full stack trace for debugging.

**Validates: Requirements 4.5, 6.5**

### Property 13: File Error Details

*For any* file operation failure, the error message should include the specific file path that caused the problem.

**Validates: Requirements 6.3**

### Property 14: No Duplicate Run Configurations

*For any* run configuration creation, if a configuration with the same name and type already exists, no duplicate should be created.

**Validates: Requirements 7.2**

### Property 15: Non-Blocking Activity

*For any* MyProjectActivity execution, the activity should complete without blocking the project opening process.

**Validates: Requirements 7.4**

### Property 16: Error Resilience

*For any* error in MyProjectActivity, the project should still open successfully.

**Validates: Requirements 7.5**

## Error Handling

### Error Categories

1. **Script Errors**: Missing script, execution failure, timeout
2. **File Errors**: Permission denied, disk full, path not found
3. **JSON Errors**: Invalid format, missing fields, parse failure
4. **Threading Errors**: EDT violations, deadlocks
5. **VFS Errors**: Refresh failure, file not found

### Error Handling Strategy

```kotlin
try {
    // Operation
} catch (e: IOException) {
    thisLogger().error("File operation failed: ${file.path}", e)
    // Show user-friendly message
} catch (e: JsonSyntaxException) {
    thisLogger().error("Invalid JSON in sync output", e)
    // Provide troubleshooting steps
} catch (e: Exception) {
    thisLogger().error("Unexpected error", e)
    // Generic error handling
}
```

### User-Facing Error Messages

- **Missing Template**: "Project template not found. Please reinstall the plugin."
- **Script Failure**: "Sync script failed. Check that /Users/admin/generate.sh exists and is executable."
- **JSON Invalid**: "Sync output is invalid. Check sync-output-data.json format."
- **File Conflict**: (No message - silently skip)

## Testing Strategy

### Unit Tests

- Test FileConflictResolver with various file scenarios
- Test SyncExecutor with mock scripts and JSON
- Test VfsRefreshQueue batching logic
- Test error handling paths
- Test thread context assertions

### Property-Based Tests

Use Kotest property testing framework with minimum 100 iterations per test.

**Test Configuration**:
```kotlin
class ProjectCreationProperties : StringSpec({
    "Property 1: Background Thread Execution" {
        checkAll(100, Arb.string()) { projectPath ->
            // Feature: project-creation-fix, Property 1
            val threadName = captureThreadDuringSync(projectPath)
            threadName shouldNotBe "AWT-EventQueue-0"
        }
    }
})
```

**Key Property Tests**:
1. Background thread execution for all I/O operations
2. EDT usage for all UI updates
3. File existence handling without warnings
4. VFS refresh batching (count refreshes)
5. Script validation before execution
6. JSON validation before parsing
7. Error logging completeness
8. No duplicate run configurations

### Integration Tests

- Test full project creation workflow
- Test sync after project opening
- Test error recovery scenarios
- Test VFS refresh timing
- Test thread transitions

### Manual Testing

- Create new project and verify no EDT violations
- Create project in existing directory
- Test with missing sync script
- Test with invalid JSON output
- Monitor IDE responsiveness

## Implementation Notes

### IntelliJ Platform APIs

- `ApplicationManager.getApplication().invokeLater {}` - EDT execution
- `ApplicationManager.getApplication().invokeAndWait {}` - Blocking EDT execution
- `withContext(Dispatchers.IO) {}` - Background coroutine
- `ProgressManager.getInstance().run(Task.Backgroundable)` - Background task
- `VfsUtil.markDirtyAndRefresh()` - VFS refresh
- `ProjectManager.getInstance().loadAndOpenProject()` - Open project

### Kotlin Coroutines

- Use `Dispatchers.IO` for I/O operations
- Use `Dispatchers.Default` for CPU-intensive work
- Use `launch` for fire-and-forget
- Use `async/await` for results
- Proper exception handling with try-catch

### Performance Considerations

- Batch file operations before VFS refresh
- Use async VFS refresh (non-blocking)
- Debounce refresh requests (500ms)
- Don't block EDT
- Use progress indicators for long operations

### Backward Compatibility

- Maintain existing DataSyncService interface
- Keep MyProjectActivity as ProjectActivity
- Preserve RecipeExecutor extension function signature
- No breaking changes to public APIs
