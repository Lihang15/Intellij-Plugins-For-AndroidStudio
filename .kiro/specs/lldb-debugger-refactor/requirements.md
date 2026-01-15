# Requirements Document: LLDB Debugger Refactoring

## Introduction

This document outlines the requirements for refactoring the LLDB debugger implementation to follow the proven architecture patterns from the Flutter IntelliJ plugin. The current implementation has issues with breakpoint reliability and stepping behavior due to over-complicated threading and event handling.

## Glossary

- **LLDB**: Low Level Debugger - the debugger backend for C/C++ code
- **XDebugProcess**: IntelliJ Platform's debug process interface
- **XDebugSession**: IntelliJ Platform's debug session that manages UI state
- **Isolate**: In LLDB context, refers to a thread being debugged
- **SuspendContext**: The state when debugger pauses execution
- **BreakpointHandler**: Component that manages breakpoint registration and synchronization
- **PositionMapper**: Component that maps between IDE file positions and debugger locations

## Requirements

### Requirement 1: Simplified Architecture

**User Story:** As a developer, I want a clean debugger architecture, so that the code is maintainable and bugs are easier to fix.

#### Acceptance Criteria

1. THE System SHALL follow the Flutter IntelliJ architecture pattern with clear separation of concerns
2. THE System SHALL have a single LLDBDebugProcess extending XDebugProcess as the main entry point
3. THE System SHALL have a LLDBServiceWrapper managing all LLDB communication
4. THE System SHALL have a LLDBListener handling LLDB events
5. THE System SHALL eliminate unnecessary threading complexity

### Requirement 2: Reliable Breakpoint Management

**User Story:** As a developer, I want breakpoints to work reliably, so that I can debug my C++ code effectively.

#### Acceptance Criteria

1. WHEN a breakpoint is registered THEN THE System SHALL cache it until LLDB is ready
2. WHEN LLDB is connected THEN THE System SHALL synchronize all cached breakpoints
3. WHEN a breakpoint is hit THEN THE System SHALL correctly update the UI state
4. WHEN a breakpoint is removed THEN THE System SHALL remove it from LLDB immediately
5. THE System SHALL handle breakpoint path mapping correctly for both absolute and relative paths

### Requirement 3: Proper Event Handling

**User Story:** As a developer, I want debugger events to be handled correctly, so that stepping and execution control work reliably.

#### Acceptance Criteria

1. WHEN LLDB stops at a breakpoint THEN THE System SHALL call session.positionReached() on EDT thread
2. WHEN LLDB stops at a breakpoint THEN THE System SHALL fetch stack trace before updating UI
3. WHEN stepping (over/into/out) THEN THE System SHALL correctly resume execution
4. WHEN continuing execution THEN THE System SHALL clear the suspended state
5. THE System SHALL handle all LLDB events through a single listener interface

### Requirement 4: State Management

**User Story:** As a developer, I want proper state tracking, so that the debugger knows when it's connected, suspended, or running.

#### Acceptance Criteria

1. THE System SHALL track connection state (disconnected, connecting, connected)
2. THE System SHALL track suspended threads with their IDs
3. WHEN a thread suspends THEN THE System SHALL store its ID for stepping operations
4. WHEN a thread resumes THEN THE System SHALL remove it from suspended state
5. THE System SHALL validate state before executing debug commands

### Requirement 5: Asynchronous Request Management

**User Story:** As a developer, I want LLDB commands to be queued properly, so that responses match requests correctly.

#### Acceptance Criteria

1. THE System SHALL use an Alarm-based request scheduler (like Flutter's VmServiceWrapper)
2. WHEN sending LLDB commands THEN THE System SHALL queue them through the scheduler
3. WHEN receiving LLDB responses THEN THE System SHALL match them to pending requests
4. THE System SHALL handle command timeouts gracefully
5. THE System SHALL prevent EDT blocking by using background threads

### Requirement 6: Connection Lifecycle

**User Story:** As a developer, I want the debugger to connect reliably, so that debugging sessions start correctly.

#### Acceptance Criteria

1. WHEN starting debug session THEN THE System SHALL launch LLDB process
2. WHEN LLDB is ready THEN THE System SHALL load the target executable
3. WHEN target is loaded THEN THE System SHALL synchronize breakpoints
4. WHEN breakpoints are set THEN THE System SHALL start program execution
5. THE System SHALL handle connection failures with clear error messages

### Requirement 7: Stack Frame and Variable Inspection

**User Story:** As a developer, I want to inspect variables and stack frames, so that I can understand program state.

#### Acceptance Criteria

1. WHEN debugger pauses THEN THE System SHALL fetch complete stack trace
2. WHEN displaying stack frames THEN THE System SHALL show correct file paths and line numbers
3. WHEN selecting a frame THEN THE System SHALL fetch variables for that frame
4. THE System SHALL parse LLDB output correctly to extract frame information
5. THE System SHALL handle missing source files gracefully

### Requirement 8: Thread Safety

**User Story:** As a developer, I want thread-safe operations, so that concurrent access doesn't cause crashes.

#### Acceptance Criteria

1. THE System SHALL use ConcurrentHashMap for shared state
2. WHEN updating UI THEN THE System SHALL use ApplicationManager.invokeLater()
3. WHEN reading LLDB output THEN THE System SHALL use a dedicated reader thread
4. THE System SHALL synchronize access to pending requests map
5. THE System SHALL avoid blocking the EDT thread

### Requirement 9: Error Handling and Logging

**User Story:** As a developer, I want clear error messages and logs, so that I can diagnose issues quickly.

#### Acceptance Criteria

1. WHEN errors occur THEN THE System SHALL log them with context
2. WHEN LLDB commands fail THEN THE System SHALL show user-friendly error messages
3. THE System SHALL log all LLDB communication for debugging
4. THE System SHALL handle LLDB process crashes gracefully
5. THE System SHALL provide diagnostic information for troubleshooting

### Requirement 10: Stepping Operations

**User Story:** As a developer, I want stepping operations to work correctly, so that I can trace through code execution.

#### Acceptance Criteria

1. WHEN stepping over THEN THE System SHALL execute "next" command with correct thread ID
2. WHEN stepping into THEN THE System SHALL execute "step" command with correct thread ID
3. WHEN stepping out THEN THE System SHALL execute "finish" command with correct thread ID
4. WHEN step completes THEN THE System SHALL update UI with new position
5. THE System SHALL prevent multiple simultaneous step operations
