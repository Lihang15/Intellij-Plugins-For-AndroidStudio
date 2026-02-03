# Implementation Plan: Fix HarmonyApp Configuration Loading

## Overview

This implementation plan addresses the race condition between project file creation and VFS synchronization in the HarmonyOS IntelliJ plugin. The approach replaces fixed time delays with a robust VFS-aware mechanism that waits for actual file system readiness before attempting configuration creation.

The implementation follows this sequence:
1. Create VFS readiness checking utilities
2. Implement configuration creation guard for idempotency
3. Enhance MyProjectActivity with VFS-aware logic
4. Add VFS event listener as fallback mechanism
5. Add comprehensive logging throughout
6. Write property-based and unit tests

## Tasks

- [ ] 1. Implement VfsReadinessChecker component
  - [ ] 1.1 Create VfsReadinessChecker interface and implementation
    - Define interface with `waitForPaths()` and `existsInVfs()` methods
    - Implement DefaultVfsReadinessChecker with polling logic
    - Use ApplicationManager.runReadAction for VFS operations
    - Add timeout and polling interval parameters (default 5000ms and 100ms)
    - Return VfsReadinessResult with diagnostic information
    - _Requirements: 1.1, 1.2, 1.3_

  - [ ]* 1.2 Write property test for VFS readiness polling
    - **Property 1: VFS Synchronization Before Detection**
    - **Validates: Requirements 1.1, 1.3**
    - Generate random project structures with/without harmonyApp
    - Simulate VFS delays
    - Verify detection never runs before VFS reports readiness
    - Run minimum 100 iterations

  - [ ]* 1.3 Write unit tests for VfsReadinessChecker
    - Test timeout behavior when paths never appear
    - Test successful detection when paths appear immediately
    - Test polling with delayed path appearance
    - Test with empty path list
    - Test with null project basePath
    - _Requirements: 1.1, 1.3, 1.4_

- [ ] 2. Implement ConfigurationCreationGuard
  - [ ] 2.1 Create ConfigurationCreationGuard singleton
    - Use ConcurrentHashMap with AtomicBoolean for thread-safe guards
    - Implement `tryAcquire()` method with compareAndSet
    - Implement `release()` method for testing/retry scenarios
    - Key by project path for per-project guards
    - _Requirements: 6.1, 6.4_

  - [ ]* 2.2 Write property test for atomic configuration creation
    - **Property 12: Atomic Configuration Creation**
    - **Validates: Requirements 6.4**
    - Generate random number of concurrent creation attempts
    - Verify only one attempt proceeds with actual creation
    - Verify final state has exactly one configuration
    - Run minimum 100 iterations

  - [ ]* 2.3 Write unit tests for ConfigurationCreationGuard
    - Test first attempt returns true
    - Test second attempt returns false
    - Test concurrent attempts (only one succeeds)
    - Test release and re-acquire
    - _Requirements: 6.1, 6.4_

- [ ] 3. Checkpoint - Verify core utilities
  - Ensure all tests pass for VfsReadinessChecker and ConfigurationCreationGuard
  - Ask the user if questions arise

- [ ] 4. Enhance MyProjectActivity with VFS-aware logic
  - [ ] 4.1 Implement waitForVfsReady() method
    - Call vfsChecker.waitForPaths() with harmonyApp and local.properties
    - Add comprehensive logging with timestamps
    - Return boolean indicating VFS readiness
    - Handle null basePath gracefully
    - _Requirements: 1.1, 1.3, 2.3, 7.1_

  - [ ] 4.2 Implement isHarmonyOSProjectVfs() method
    - Use VFS APIs (LocalFileSystem, VirtualFile) instead of File I/O
    - Check for harmonyApp directory in VFS
    - Check for local.ohos.path in local.properties via VFS
    - Wrap all VFS operations in runReadAction
    - Add detailed logging for each detection rule
    - _Requirements: 1.2, 2.1, 2.2, 2.4, 7.2_

  - [ ]* 4.3 Write property test for project detection correctness
    - **Property 3: Project Detection Correctness**
    - **Validates: Requirements 2.1, 2.2**
    - Generate random projects with various combinations of markers
    - Verify all projects with valid markers are detected
    - Verify projects without markers are not detected
    - Run minimum 100 iterations

  - [ ]* 4.4 Write unit tests for project detection
    - Test detection with harmonyApp directory present
    - Test detection with local.ohos.path present
    - Test detection with both markers present
    - Test detection with neither marker present
    - Test detection with invalid local.ohos.path
    - _Requirements: 2.1, 2.2_

- [ ] 5. Implement configuration creation logic
  - [ ] 5.1 Implement createConfigurationOnEdt() method
    - Assert execution on EDT thread
    - Check for existing configuration (idempotent)
    - Create configuration using HarmonyConfigurationType
    - Add configuration to RunManager
    - Set as selected if no other configuration selected
    - Add comprehensive logging for each step
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 6.1, 7.3_

  - [ ] 5.2 Implement autoCreateharmonyAppConfiguration() method
    - Check ConfigurationCreationGuard before proceeding
    - Call isHarmonyOSProjectVfs() for detection
    - Use invokeAndWait to call createConfigurationOnEdt()
    - Wrap in try-catch for graceful error handling
    - Log all steps with diagnostic information
    - _Requirements: 3.1, 4.1, 4.2, 4.4, 7.3_

  - [ ]* 5.3 Write property test for idempotent configuration creation
    - **Property 7: Idempotent Configuration Creation**
    - **Validates: Requirements 3.3, 6.1**
    - Generate random number of creation attempts (1-100)
    - Verify exactly one configuration exists after all attempts
    - Run minimum 100 iterations

  - [ ]* 5.4 Write unit tests for configuration creation
    - Test creation when no configuration exists
    - Test skipping when configuration already exists
    - Test selected configuration behavior
    - Test with null RunManager (error case)
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

- [ ] 6. Checkpoint - Verify configuration creation
  - Ensure all tests pass for configuration creation logic
  - Ask the user if questions arise

- [ ] 7. Implement VFS fallback listener
  - [ ] 7.1 Implement registerVfsFallbackListener() method
    - Create BulkFileListener implementation
    - Check events for harmonyApp or local.properties paths
    - Unregister listener after successful trigger
    - Launch configuration creation in coroutine
    - Add logging for listener registration and triggers
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 7.1_

  - [ ]* 7.2 Write property test for VFS event triggers
    - **Property 10: VFS Event Triggers Creation**
    - **Validates: Requirements 5.2, 5.3**
    - Generate random VFS events
    - Verify configuration creation triggered for relevant events
    - Verify no creation for irrelevant events
    - Run minimum 100 iterations

  - [ ]* 7.3 Write unit tests for VFS listener
    - Test listener triggers on harmonyApp event
    - Test listener triggers on local.properties event
    - Test listener unregisters after success
    - Test listener ignores irrelevant events
    - _Requirements: 5.2, 5.3, 5.4_

- [ ] 8. Update MyProjectActivity.execute() method
  - [ ] 8.1 Refactor execute() to use new VFS-aware flow
    - Call waitForVfsReady() before configuration creation
    - If VFS ready, call autoCreateharmonyAppConfiguration()
    - If VFS not ready, call registerVfsFallbackListener()
    - Keep existing syncProjectData() in background coroutine
    - Wrap all operations in try-catch for graceful error handling
    - Add comprehensive logging with timestamps
    - _Requirements: 1.1, 1.3, 4.1, 4.2, 4.3, 4.4, 5.1, 7.1, 7.4_

  - [ ]* 8.2 Write property test for graceful error handling
    - **Property 9: Graceful Error Handling**
    - **Validates: Requirements 4.1, 4.2, 4.3, 4.4**
    - Generate random error scenarios (VFS failures, null pointers, etc.)
    - Verify no exceptions propagate to caller
    - Verify project opening continues
    - Run minimum 100 iterations

  - [ ]* 8.3 Write integration tests for complete flow
    - Test end-to-end project opening with harmonyApp directory
    - Test VFS listener fallback when VFS not immediately ready
    - Test multiple project opens (configuration created only once)
    - Verify configuration appears in RunManager
    - Verify configuration is selected when appropriate
    - _Requirements: 1.1, 2.1, 3.1, 3.2, 3.3, 5.2, 5.4_

- [ ] 9. Implement comprehensive diagnostic logging
  - [ ] 9.1 Add logging throughout all components
    - Add timestamps to all log entries
    - Log VFS state (which markers found/missing) in waitForVfsReady()
    - Log detection attempts and results in isHarmonyOSProjectVfs()
    - Log each step of configuration creation in createConfigurationOnEdt()
    - Log VFS listener registration and triggers
    - Use structured logging format for easy parsing
    - _Requirements: 2.4, 7.1, 7.2, 7.3, 7.4_

  - [ ]* 9.2 Write property test for comprehensive logging
    - **Property 13: Comprehensive Diagnostic Logging**
    - **Validates: Requirements 2.4, 7.1, 7.2, 7.3, 7.4**
    - Generate random sequences of operations
    - Verify all operations produce log entries
    - Verify logs contain required information (timestamps, state, markers)
    - Run minimum 100 iterations

- [ ] 10. Final checkpoint - Complete system verification
  - Ensure all tests pass (unit, property, and integration tests)
  - Verify no regressions in existing functionality
  - Test with real HarmonyOS projects
  - Ask the user if questions arise

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- All VFS operations must be wrapped in ApplicationManager.runReadAction
- All RunManager operations must be executed on EDT thread
- Use Kotest or similar library for property-based testing in Kotlin
- Each property test must run minimum 100 iterations
- All tests must include clear failure messages with diagnostic information
- Comprehensive logging is critical for diagnosing timing issues in production
- ConfigurationCreationGuard ensures idempotent creation across all code paths
