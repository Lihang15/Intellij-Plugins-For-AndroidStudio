# Design Document: LLDB Debugger Refactoring

## Overview

This design refactors the LLDB debugger implementation to follow the proven architecture from the Flutter IntelliJ plugin. The key insight is to **simplify the threading model** and **centralize event handling** to eliminate race conditions and timing issues.

### Current Problems

1. **Over-complicated threading**: Multiple threads with manual synchronization using `Thread.sleep()`
2. **Inconsistent event handling**: Stop events sometimes trigger callbacks, sometimes don't
3. **Breakpoint timing issues**: Breakpoints registered before LLDB is ready get lost
4. **State management**: No clear tracking of connection/suspension state

### Solution Approach

Follow Flutter's architecture:
- **Single entry point**: `LLDBDebugProcess` extends `XDebugProcess`
- **Centralized communication**: `LLDBServiceWrapper` manages all LLDB interaction
- **Event-driven**: `LLDBListener` handles all LLDB events
- **Async request queue**: Use IntelliJ's `Alarm` for request scheduling
- **Proper state tracking**: Track connection and suspension state explicitly

## Architecture

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    IntelliJ Platform                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              XDebugSession                            │  │
│  │  (Manages UI state, breakpoints, stepping)           │  │
│  └──────────────────────────────────────────────────────┘  │
│                          │                                   │
│                          ▼                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           LLDBDebugProcess                            │  │
│  │  - Extends XDebugProcess                             │  │
│  │  - Main entry point                                  │  │
│  │  - Delegates to LLDBServiceWrapper                   │  │
│  └──────────────────────────────────────────────────────┘  │
│           │                    │                             │
│           ▼                    ▼                             │
│  ┌──────────────────┐  ┌──────────────────────────────┐   │
│  │ LLDBBreakpoint   │  │   LLDBServiceWrapper          │   │
│  │ Handler          │  │  - Manages LLDB connection    │   │
│  │  - Caches BPs    │  │  - Async request queue        │   │
│  │  - Syncs to LLDB │  │  - Command/response matching  │   │
│  └──────────────────┘  └──────────────────────────────┘   │
│                                  │                           │
│                                  ▼                           │
│                         ┌──────────────────┐                │
│                         │  LLDBListener    │                │
│                         │  - Event handler │                │
│                         │  - Callbacks     │                │
│                         └──────────────────┘                │
└─────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
                         ┌──────────────────┐
                         │   LLDB Process   │
                         │  (External)      │
                         └──────────────────┘
```

## Components and Interfaces

### 1. LLDBDebugProcess

**Purpose**: Main debug process that integrates with IntelliJ's XDebugger framework.

**Key Responsibilities**:
- Extend `XDebugProcess`
- Initialize LLDB connection
- Provide breakpoint handlers
- Implement stepping operations (step over/into/out)
- Manage debug session lifecycle

**Key Methods**:
```kotlin
class LLDBDebugProcess(
    session: XDebugSession,
    private val executablePath: String
) : XDebugProcess(session) {
    
    private val serviceWrapper: LLDBServiceWrapper
    private val breakpointHandler: LLDBBreakpointHandler
    private val positionMapper: LLDBPositionMapper
    
    // Lifecycle
    override fun sessionInitialized()
    override fun stop()
    
    // Stepping
    override fun startStepOver(context: XSuspendContext?)
    override fun startStepInto(context: XSuspendContext?)
    override fun startStepOut(context: XSuspendContext?)
    override fun resume(context: XSuspendContext?)
    override fun startPausing()
    
    // Configuration
    override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>>
    override fun getEditorsProvider(): XDebuggerEditorsProvider
    
    // Internal
    fun isolateSuspended(threadId: Int)
    fun isolateResumed(threadId: Int)
    fun getCurrentThreadId(): Int?
}
```

**Initialization Sequence** (like Flutter's `onConnectSucceeded`):
```kotlin
override fun sessionInitialized() {
    // 1. Start LLDB process
    serviceWrapper.start()
    
    // 2. Wait for connection (async)
    serviceWrapper.onConnected = {
        // 3. Load target
        serviceWrapper.loadTarget(executablePath) {
            // 4. Sync breakpoints
            breakpointHandler.syncAllBreakpoints {
                // 5. Start execution
                serviceWrapper.run()
            }
        }
    }
}
```

### 2. LLDBServiceWrapper

**Purpose**: Centralized LLDB communication manager (like Flutter's `VmServiceWrapper`).

**Key Responsibilities**:
- Manage LLDB process lifecycle
- Queue and execute LLDB commands asynchronously
- Match responses to requests
- Handle LLDB events
- Maintain connection state

**Key Methods**:
```kotlin
class LLDBServiceWrapper(
    private val debugProcess: LLDBDebugProcess
) : Disposable {
    
    private val requestScheduler: Alarm
    private val pendingRequests: ConcurrentHashMap<Int, (String) -> Unit>
    private val listener: LLDBListener
    
    private var lldbConnected = false
    private var currentThreadId: Int? = null
    
    // Lifecycle
    fun start()
    fun loadTarget(path: String, callback: () -> Unit)
    fun run(callback: () -> Unit)
    override fun dispose()
    
    // Breakpoints
    fun setBreakpoint(file: String, line: Int, callback: (Boolean) -> Unit)
    fun removeBreakpoint(file: String, line: Int, callback: () -> Unit)
    
    // Execution control
    fun resumeThread(threadId: Int, stepOption: StepOption?)
    fun pauseThread(threadId: Int)
    
    // Stack inspection
    fun getStackTrace(threadId: Int, callback: (List<StackFrame>) -> Unit)
    fun getVariables(frameId: Int, callback: (List<Variable>) -> Unit)
    
    // Internal
    private fun addRequest(runnable: Runnable)
    private fun sendCommand(command: String, callback: (String) -> Unit)
}
```

**Request Scheduling** (like Flutter's `addRequest`):
```kotlin
private fun addRequest(runnable: Runnable) {
    if (!requestScheduler.isDisposed) {
        requestScheduler.addRequest(runnable, 0)
    }
}

fun setBreakpoint(file: String, line: Int, callback: (Boolean) -> Unit) {
    addRequest {
        val command = "breakpoint set --file \"$file\" --line $line"
        sendCommand(command) { response ->
            val success = !response.contains("error:")
            callback(success)
        }
    }
}
```

### 3. LLDBListener

**Purpose**: Handle all LLDB events (like Flutter's `DartVmServiceListener`).

**Key Responsibilities**:
- Listen to LLDB output stream
- Parse stop events (breakpoint, step, exception)
- Trigger callbacks to `LLDBDebugProcess`
- Handle program termination

**Key Methods**:
```kotlin
class LLDBListener(
    private val debugProcess: LLDBDebugProcess,
    private val serviceWrapper: LLDBServiceWrapper
) {
    
    var onStopped: ((threadId: Int, reason: String) -> Unit)? = null
    var onOutput: ((message: String) -> Unit)? = null
    var onTerminated: (() -> Unit)? = null
    
    fun handleLldbOutput(line: String) {
        when {
            isStopEvent(line) -> handleStopEvent(line)
            isOutputEvent(line) -> handleOutputEvent(line)
            isTerminationEvent(line) -> handleTermination()
            else -> bufferResponse(line)
        }
    }
    
    private fun handleStopEvent(line: String) {
        val threadId = extractThreadId(line)
        val reason = extractStopReason(line)
        
        // Fetch stack trace BEFORE notifying UI
        serviceWrapper.getStackTrace(threadId) { stackFrames ->
            // Notify on EDT thread
            ApplicationManager.getApplication().invokeLater {
                onStopped?.invoke(threadId, reason)
            }
        }
    }
}
```

### 4. LLDBBreakpointHandler

**Purpose**: Manage breakpoint registration and synchronization (like Flutter's `DartVmServiceBreakpointHandler`).

**Key Responsibilities**:
- Cache breakpoints before LLDB is ready
- Sync breakpoints when LLDB connects
- Handle breakpoint add/remove
- Map IDE breakpoints to LLDB breakpoints

**Key Methods**:
```kotlin
class LLDBBreakpointHandler(
    private val debugProcess: LLDBDebugProcess
) : XBreakpointHandler<XLineBreakpoint<*>>(XLineBreakpointType::class.java) {
    
    private val registeredBreakpoints = ConcurrentHashMap<String, XLineBreakpoint<*>>()
    private val pendingBreakpoints = CopyOnWriteArrayList<XLineBreakpoint<*>>()
    
    @Volatile
    private var lldbReady = false
    
    override fun registerBreakpoint(breakpoint: XLineBreakpoint<*>) {
        val key = getBreakpointKey(breakpoint)
        registeredBreakpoints[key] = breakpoint
        
        if (!lldbReady) {
            pendingBreakpoints.add(breakpoint)
        } else {
            syncBreakpointToLldb(breakpoint)
        }
    }
    
    override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<*>, temporary: Boolean) {
        val key = getBreakpointKey(breakpoint)
        registeredBreakpoints.remove(key)
        pendingBreakpoints.remove(breakpoint)
        
        if (lldbReady) {
            removeBreakpointFromLldb(breakpoint)
        }
    }
    
    fun onLldbReady() {
        lldbReady = true
        syncAllBreakpoints()
    }
    
    private fun syncAllBreakpoints(callback: () -> Unit = {}) {
        val breakpointsToSync = pendingBreakpoints.toList()
        pendingBreakpoints.clear()
        
        var remaining = breakpointsToSync.size
        if (remaining == 0) {
            callback()
            return
        }
        
        breakpointsToSync.forEach { bp ->
            syncBreakpointToLldb(bp) {
                remaining--
                if (remaining == 0) callback()
            }
        }
    }
}
```

### 5. LLDBPositionMapper

**Purpose**: Map between IDE file positions and LLDB locations (like Flutter's `FlutterPositionMapper`).

**Key Responsibilities**:
- Convert IDE file paths to LLDB-compatible paths
- Parse LLDB stack frames to IDE source positions
- Handle relative vs absolute paths
- Map line numbers (0-based vs 1-based)

**Key Methods**:
```kotlin
class LLDBPositionMapper(
    private val project: Project
) {
    
    fun getBreakpointPath(file: VirtualFile): String {
        // Convert IDE file to LLDB path
        return file.canonicalPath ?: file.path
    }
    
    fun parseSourcePosition(lldbFrame: String): XSourcePosition? {
        // Parse LLDB frame output to IDE position
        val (file, line) = extractFileAndLine(lldbFrame)
        val virtualFile = findVirtualFile(file) ?: return null
        return XSourcePositionImpl.create(virtualFile, line - 1) // LLDB is 1-based
    }
    
    private fun findVirtualFile(path: String): VirtualFile? {
        // Try absolute path first
        LocalFileSystem.getInstance().findFileByPath(path)?.let { return it }
        
        // Try relative to project
        val projectPath = project.basePath ?: return null
        return LocalFileSystem.getInstance().findFileByPath("$projectPath/$path")
    }
}
```

## Data Models

### StackFrame
```kotlin
data class StackFrame(
    val id: Int,
    val name: String,
    val file: String,
    val line: Int,
    val column: Int = 0
)
```

### Variable
```kotlin
data class Variable(
    val name: String,
    val value: String,
    val type: String,
    val variablesReference: Int = 0
)
```

### StepOption
```kotlin
enum class StepOption {
    Over,      // next
    Into,      // step
    Out        // finish
}
```

### ConnectionState
```kotlin
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Breakpoint Caching Before LLDB Ready
*For any* breakpoint registered before LLDB is ready, it should be stored in the pending breakpoints list and synced after LLDB connects.
**Validates: Requirements 2.1, 2.2**

### Property 2: Breakpoint Removal Synchronization
*For any* breakpoint removal operation, if LLDB is ready, the corresponding LLDB breakpoint should be deleted immediately.
**Validates: Requirements 2.4**

### Property 3: Path Mapping Consistency
*For any* file path (absolute or relative), the position mapper should correctly resolve it to a valid LLDB path and back to an IDE source position.
**Validates: Requirements 2.5**

### Property 4: UI Updates on EDT Thread
*For any* UI update operation (positionReached, breakpointVerified), it should execute on the EDT thread.
**Validates: Requirements 3.1, 8.2**

### Property 5: Stack Trace Before UI Update
*For any* stop event, the stack trace should be fetched and available before calling session.positionReached().
**Validates: Requirements 3.2**

### Property 6: Correct Step Commands
*For any* step operation (over/into/out), the correct LLDB command (next/step/finish) should be sent with the current thread ID.
**Validates: Requirements 3.3, 10.1, 10.2, 10.3**

### Property 7: Suspended State Tracking
*For any* thread suspension, the thread ID should be added to the suspended threads map, and removed when the thread resumes.
**Validates: Requirements 4.2, 4.3, 4.4**

### Property 8: State Validation Before Commands
*For any* debug command (step, continue, pause), it should only execute if the debugger is in a valid state (connected and thread is suspended for step/continue).
**Validates: Requirements 4.5**

### Property 9: Request-Response Matching
*For any* LLDB command sent, the response should be matched to the correct pending request callback.
**Validates: Requirements 5.3**

### Property 10: Non-blocking EDT Operations
*For any* LLDB command execution, it should not block the EDT thread.
**Validates: Requirements 5.5, 8.5**

### Property 11: Stack Frame Parsing
*For any* valid LLDB stack trace output, the parser should extract all frames with correct file paths and line numbers.
**Validates: Requirements 7.2, 7.4**

### Property 12: Variable Fetch Per Frame
*For any* frame selection, variables should be fetched for that specific frame ID.
**Validates: Requirements 7.3**

### Property 13: LLDB Communication Logging
*For any* LLDB command sent or response received, it should be logged with timestamp and sequence number.
**Validates: Requirements 9.3**

### Property 14: Single Active Step Operation
*For any* time during debugging, at most one step operation should be active (no concurrent steps).
**Validates: Requirements 10.5**

## Error Handling

### Connection Errors
- **LLDB not found**: Show clear error message with installation instructions
- **Target load failure**: Display LLDB error message to user
- **Connection timeout**: Retry with exponential backoff, max 3 attempts

### Breakpoint Errors
- **Invalid file path**: Mark breakpoint as invalid with error icon
- **Line not executable**: Show warning, suggest nearby executable line
- **LLDB rejection**: Log error, mark breakpoint as unverified

### Runtime Errors
- **LLDB crash**: Detect process termination, show error dialog, stop session
- **Command timeout**: Log warning, continue (don't block user)
- **Parse errors**: Log error with context, use fallback values

## Testing Strategy

### Unit Tests
- Test LLDB output parsing with various formats
- Test path mapping with different path types
- Test state transitions (disconnected → connecting → connected)
- Test breakpoint key generation
- Test thread ID extraction from LLDB output

### Property-Based Tests
- **Property 1**: Generate random breakpoints, register before LLDB ready, verify all synced after connection
- **Property 3**: Generate random file paths, verify round-trip (IDE → LLDB → IDE)
- **Property 6**: Generate random step operations, verify correct LLDB commands
- **Property 7**: Generate random suspend/resume sequences, verify state consistency
- **Property 11**: Generate various LLDB stack trace formats, verify parsing correctness

### Integration Tests
- Test full debug session lifecycle (start → breakpoint → step → continue → stop)
- Test breakpoint hit with UI update
- Test stepping through multiple frames
- Test variable inspection at different frames
- Test error recovery (LLDB crash, invalid breakpoint)

### Manual Testing Scenarios
1. Set breakpoint before starting debug → verify it hits
2. Set breakpoint while running → verify it hits
3. Step over/into/out → verify correct behavior
4. Remove breakpoint while paused → verify it's removed
5. Inspect variables at different stack frames → verify correct values

## Implementation Notes

### Key Differences from Current Implementation

1. **No more `Thread.sleep()`**: Use callbacks and async request queue
2. **Single event handler**: All LLDB events go through `LLDBListener`
3. **Explicit state tracking**: Use enums for connection/suspension state
4. **Breakpoint caching**: Store breakpoints until LLDB is ready
5. **EDT safety**: All UI updates use `ApplicationManager.invokeLater()`

### Migration Strategy

1. **Phase 1**: Create new classes (LLDBServiceWrapper, LLDBListener) alongside existing code
2. **Phase 2**: Refactor LLDBDebugProcess to use new classes
3. **Phase 3**: Simplify LLDBBreakpointHandler to use caching pattern
4. **Phase 4**: Remove old LLDBDebugSession class
5. **Phase 5**: Add comprehensive logging and error handling

### Performance Considerations

- Use `ConcurrentHashMap` for thread-safe state without locks
- Use `Alarm` for efficient request scheduling (no busy waiting)
- Parse LLDB output incrementally (don't buffer entire output)
- Cache file path mappings to avoid repeated filesystem lookups

### Debugging Tips

- Enable verbose logging for LLDB communication
- Log all state transitions with timestamps
- Log thread names for all operations
- Add sequence numbers to all requests/responses
- Log call stacks for critical operations (stop events, UI updates)
