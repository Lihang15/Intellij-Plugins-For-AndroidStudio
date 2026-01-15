# Implementation Plan: LLDB Debugger Refactoring

## Overview

This plan refactors the LLDB debugger to follow the Flutter IntelliJ architecture, eliminating threading complexity and fixing breakpoint/stepping reliability issues.

## Tasks

- [x] 1. Create LLDBServiceWrapper class
  - Create new `LLDBServiceWrapper.kt` file
  - Implement Disposable interface
  - Add Alarm-based request scheduler
  - Add pending requests map with sequence numbers
  - Implement `start()`, `loadTarget()`, `run()` methods
  - Implement `sendCommand()` with async callback
  - Add connection state tracking (DISCONNECTED, CONNECTING, CONNECTED)
  - _Requirements: 1.3, 5.1, 5.2, 5.3, 6.1, 6.2_

- [ ]* 1.1 Write unit tests for LLDBServiceWrapper
  - Test request queuing and sequencing
  - Test command/response matching
  - Test state transitions
  - _Requirements: 5.2, 5.3_

- [x] 2. Create LLDBListener class
  - Create new `LLDBListener.kt` file
  - Implement LLDB output parsing
  - Add stop event detection (breakpoint, step, exception)
  - Add output event handling
  - Add termination event handling
  - Implement `handleStopEvent()` with stack trace fetch before UI update
  - Add callbacks: `onStopped`, `onOutput`, `onTerminated`
  - _Requirements: 1.4, 3.1, 3.2, 3.5_

- [ ]* 2.1 Write unit tests for LLDBListener
  - Test stop event parsing with various LLDB output formats
  - Test thread ID extraction
  - Test stop reason extraction
  - _Requirements: 3.1, 3.2_

- [x] 3. Refactor LLDBDebugProcess
  - Replace `LLDBDebugSession` with `LLDBServiceWrapper`
  - Remove all `Thread.sleep()` calls
  - Implement proper `sessionInitialized()` sequence
  - Add suspended threads tracking with `ConcurrentHashMap`
  - Implement `isolateSuspended()` and `isolateResumed()`
  - Update stepping methods to use `serviceWrapper.resumeThread()`
  - Add state validation before executing commands
  - _Requirements: 1.1, 1.2, 3.3, 3.4, 4.1, 4.2, 4.3, 4.4, 4.5_

- [ ]* 3.1 Write property test for suspended state tracking
  - **Property 7: Suspended State Tracking**
  - **Validates: Requirements 4.2, 4.3, 4.4**

- [x] 4. Refactor LLDBBreakpointHandler
  - Add `lldbReady` flag
  - Add `pendingBreakpoints` list for caching
  - Implement `onLldbReady()` to sync cached breakpoints
  - Update `registerBreakpoint()` to cache if LLDB not ready
  - Update `unregisterBreakpoint()` to handle pending breakpoints
  - Implement `syncAllBreakpoints()` with callback
  - _Requirements: 2.1, 2.2, 2.4_

- [ ]* 4.1 Write property test for breakpoint caching
  - **Property 1: Breakpoint Caching Before LLDB Ready**
  - **Validates: Requirements 2.1, 2.2**

- [ ]* 4.2 Write property test for breakpoint removal
  - **Property 2: Breakpoint Removal Synchronization**
  - **Validates: Requirements 2.4**

- [x] 5. Create LLDBPositionMapper class
  - Create new `LLDBPositionMapper.kt` file
  - Implement `getBreakpointPath()` for IDE → LLDB path conversion
  - Implement `parseSourcePosition()` for LLDB → IDE position conversion
  - Add `findVirtualFile()` with absolute and relative path support
  - Handle 0-based (IDE) vs 1-based (LLDB) line number conversion
  - _Requirements: 2.5, 7.2, 7.4_

- [ ]* 5.1 Write property test for path mapping
  - **Property 3: Path Mapping Consistency**
  - **Validates: Requirements 2.5**

- [ ]* 5.2 Write property test for stack frame parsing
  - **Property 11: Stack Frame Parsing**
  - **Validates: Requirements 7.2, 7.4**

- [x] 6. Update LLDBSuspendContext
  - Remove direct LLDB session dependency
  - Use pre-fetched stack frames from stop event
  - Simplify constructor to accept parsed stack frames
  - _Requirements: 3.2, 7.1_

- [x] 7. Update LLDBExecutionStack
  - Use `LLDBServiceWrapper` for variable fetching
  - Implement proper frame selection with `frame select` command
  - Update `computeStackFrames()` to use parsed frames
  - _Requirements: 7.3_

- [x] 8. Update LLDBStackFrame
  - Use pre-parsed StackFrame objects
  - Remove dependency on LLDBDebugSession
  - Use LLDBPositionMapper for position mapping
  - Update variable fetching to use LLDBServiceWrapper
  - _Requirements: 7.2, 7.3_

- [x] 9. Update LLDBValue
  - Use pre-parsed Variable objects
  - Remove dependency on LLDBDebugSession
  - Simplify presentation logic
  - _Requirements: 7.3_

- [x] 10. Update LLDBEvaluator
  - Use LLDBServiceWrapper.evaluateExpression()
  - Remove dependency on LLDBDebugSession
  - Simplify expression parsing
  - _Requirements: 7.3_

- [x] 11. Update LLDBInlineDebugRenderer
  - Remove old logging dependencies
  - Use proper Logger instance
  - _Requirements: 9.1_

- [ ] 12. Implement EDT safety
  - Wrap all `session.positionReached()` calls with `ApplicationManager.invokeLater()`
  - Wrap all `session.setBreakpointVerified()` calls with `invokeLater()`
  - Wrap all `session.setBreakpointInvalid()` calls with `invokeLater()`
  - Add thread name logging for verification
  - _Requirements: 3.1, 8.2, 8.5_

- [ ]* 12.1 Write property test for EDT thread usage
  - **Property 4: UI Updates on EDT Thread**
  - **Validates: Requirements 3.1, 8.2**

- [ ] 13. Add comprehensive logging
  - Add timestamp and sequence number to all logs
  - Log all LLDB commands sent
  - Log all LLDB responses received
  - Log all state transitions
  - Log thread names for all operations
  - Add call stack logging for critical operations
  - _Requirements: 9.1, 9.3_

- [ ] 14. Implement error handling
  - Add connection error handling with retry logic
  - Add breakpoint error handling with user feedback
  - Add LLDB crash detection and recovery
  - Add command timeout handling
  - Add parse error handling with fallbacks
  - _Requirements: 6.5, 9.2, 9.4_

- [ ] 15. Checkpoint - Test basic debugging workflow
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 16. Integration testing
  - Test full debug session lifecycle
  - Test breakpoint hit with UI update
  - Test stepping through multiple frames
  - Test variable inspection
  - Test error recovery scenarios
  - _Requirements: All_

- [ ]* 16.1 Write integration tests
  - Test start → breakpoint → step → continue → stop
  - Test breakpoint set before/during debug
  - Test stepping operations
  - Test variable inspection

- [ ] 17. Remove old code
  - Delete old `LLDBDebugSession.kt` (replaced by LLDBServiceWrapper)
  - Remove unused threading code
  - Remove all `Thread.sleep()` calls
  - Clean up imports
  - _Requirements: 1.5_

- [ ] 18. Final checkpoint - Verify all functionality
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- Integration tests verify end-to-end workflows
