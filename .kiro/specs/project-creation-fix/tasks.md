# Implementation Plan: Project Creation and Sync Fix

## Overview

This implementation plan refactors the plugin's project creation and synchronization to fix EDT violations, file conflicts, and performance issues. Tasks are organized to build incrementally, with testing integrated throughout.

## Tasks

- [x] 1. Create core utility classes and interfaces
  - Create `FileConflictResolver` interface and implementation
  - Create `VfsRefreshQueue` for batched refresh operations
  - Create `SyncExecutor` interface and implementation
  - Set up error handling utilities
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.4, 4.1, 4.2, 4.3_

- [ ]* 1.1 Write property test for FileConflictResolver
  - **Property 3: File Existence Handling**
  - **Validates: Requirements 2.1, 2.2, 2.4, 2.5**

- [ ]* 1.2 Write property test for VfsRefreshQueue batching
  - **Property 5: VFS Refresh Batching**
  - **Validates: Requirements 1.5, 3.1, 3.2, 3.4**

- [x] 2. Refactor MyProjectActivity for thread safety
  - [x] 2.1 Remove sample warning message from execute()
    - Delete the `thisLogger().warn()` call
    - _Requirements: 7.1_

  - [x] 2.2 Refactor autoCreateharmonyAppConfiguration for efficiency
    - Optimize duplicate checking logic
    - Add early returns for better performance
    - _Requirements: 7.2_

  - [x] 2.3 Refactor syncProjectData to use SyncExecutor
    - Replace direct script execution with SyncExecutor
    - Add proper error handling with try-catch
    - Ensure execution on Dispatchers.IO
    - _Requirements: 1.1, 1.2, 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x] 2.4 Ensure activity doesn't block project opening
    - Use `launch` instead of blocking calls
    - Wrap sync in try-catch to prevent crashes
    - _Requirements: 7.4, 7.5_

- [ ]* 2.5 Write property test for background thread execution
  - **Property 1: Background Thread Execution**
  - **Validates: Requirements 1.1, 1.2, 5.2**

- [ ]* 2.6 Write property test for non-blocking activity
  - **Property 15: Non-Blocking Activity**
  - **Validates: Requirements 7.4**

- [ ]* 2.7 Write property test for error resilience
  - **Property 16: Error Resilience**
  - **Validates: Requirements 7.5**

- [x] 3. Checkpoint - Verify MyProjectActivity changes
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Refactor KMPApplicationRecipe for proper threading
  - [x] 4.1 Update generateProjectStructure to use FileConflictResolver
    - Replace direct file copy with conflict-aware copying
    - Use FileConflictResolver.shouldCopyFile() before each copy
    - _Requirements: 2.1, 2.2, 2.4_

  - [x] 4.2 Implement settings.gradle.kts merging
    - Update generateSettingsGradle to merge instead of overwrite
    - Use FileConflictResolver.mergeSettingsGradle()
    - _Requirements: 2.3_

  - [x] 4.3 Replace multiple VFS refreshes with single batched refresh
    - Remove VfsUtil.markDirtyAndRefresh() from copyDirectory
    - Add single refresh at end of generateProjectStructure
    - Use VfsRefreshQueue for batching
    - _Requirements: 3.1, 3.2, 3.4_

  - [x] 4.4 Ensure VFS refresh is asynchronous
    - Use async refresh API
    - Don't block on refresh completion
    - _Requirements: 3.3_

  - [x] 4.5 Fix openProject to execute on EDT properly
    - Verify ProjectManager.loadAndOpenProject() is called on EDT
    - Use invokeLater if needed
    - _Requirements: 5.4_

- [ ]* 4.6 Write property test for settings gradle merge
  - **Property 4: Settings Gradle Merge**
  - **Validates: Requirements 2.3**

- [ ]* 4.7 Write unit test for async VFS refresh
  - **Property 6: Async VFS Refresh**
  - **Validates: Requirements 3.3**

- [x] 5. Implement SyncExecutor with proper validation
  - [x] 5.1 Implement script existence validation
    - Check File.exists() before execution
    - Return early with error if missing
    - _Requirements: 4.1_

  - [x] 5.2 Implement graceful script failure handling
    - Wrap ProcessBuilder execution in try-catch
    - Log errors with full stack trace
    - Return Result.Failure instead of throwing
    - _Requirements: 4.2, 4.5_

  - [x] 5.3 Implement JSON validation before parsing
    - Check JSON structure before Gson.fromJson()
    - Validate required fields exist
    - _Requirements: 4.3_

  - [x] 5.4 Implement data storage in DataSyncService
    - Store parsed data in service after validation
    - Ensure data is accessible from service
    - _Requirements: 4.4_

- [ ]* 5.5 Write property test for script validation
  - **Property 7: Script Validation**
  - **Validates: Requirements 4.1**

- [ ]* 5.6 Write property test for graceful script failure
  - **Property 8: Graceful Script Failure**
  - **Validates: Requirements 4.2, 4.5**

- [ ]* 5.7 Write property test for JSON validation
  - **Property 9: JSON Validation**
  - **Validates: Requirements 4.3**

- [ ]* 5.8 Write property test for service data storage
  - **Property 10: Service Data Storage**
  - **Validates: Requirements 4.4**

- [x] 6. Checkpoint - Verify sync executor implementation
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement EDT safety checks
  - [x] 7.1 Add EDT assertions for UI operations
    - Add ApplicationManager.getApplication().assertIsDispatchThread() for UI updates
    - Verify all dialog displays happen on EDT
    - _Requirements: 5.1, 5.5_

  - [x] 7.2 Add background thread assertions for I/O
    - Add assertions that I/O doesn't happen on EDT
    - Use Thread.currentThread() checks in development mode
    - _Requirements: 1.1, 1.2, 5.2_

  - [x] 7.3 Verify thread transitions use proper APIs
    - Audit all invokeLater and withContext calls
    - Ensure proper synchronization
    - _Requirements: 5.3_

- [ ]* 7.4 Write property test for EDT UI updates
  - **Property 2: EDT UI Updates**
  - **Validates: Requirements 1.4, 5.1, 5.5**

- [ ]* 7.5 Write property test for thread transition safety
  - **Property 11: Thread Transition Safety**
  - **Validates: Requirements 5.3**

- [x] 8. Implement comprehensive error handling
  - [x] 8.1 Add error logging with stack traces
    - Ensure all catch blocks log full stack traces
    - Use thisLogger().error(message, exception)
    - _Requirements: 6.5_

  - [x] 8.2 Add file path to error messages
    - Include file.path in all file operation errors
    - Make error messages actionable
    - _Requirements: 6.3_

  - [x] 8.3 Implement user-friendly error dialogs
    - Create error dialog for missing templates
    - Create error dialog for script failures
    - Show script output in error messages
    - _Requirements: 6.1, 6.2_

- [ ]* 8.4 Write property test for error logging
  - **Property 12: Error Logging**
  - **Validates: Requirements 4.5, 6.5**

- [ ]* 8.5 Write property test for file error details
  - **Property 13: File Error Details**
  - **Validates: Requirements 6.3**

- [x] 9. Implement run configuration duplicate prevention
  - [x] 9.1 Optimize duplicate checking in autoCreateharmonyAppConfiguration
    - Use efficient allSettings.any() check
    - Add early return if duplicate found
    - _Requirements: 7.2_

- [ ]* 9.2 Write property test for no duplicate run configurations
  - **Property 14: No Duplicate Run Configurations**
  - **Validates: Requirements 7.2**

- [x] 10. Integration and cleanup
  - [x] 10.1 Wire all components together
    - Integrate FileConflictResolver into KMPApplicationRecipe
    - Integrate SyncExecutor into MyProjectActivity
    - Integrate VfsRefreshQueue into both components
    - _Requirements: All_

  - [x] 10.2 Remove obsolete code
    - Remove old direct file copy logic
    - Remove multiple VFS refresh calls
    - Clean up commented code

  - [x] 10.3 Add documentation comments
    - Document threading requirements
    - Document error handling strategy
    - Add KDoc to public APIs

- [ ]* 10.4 Write integration tests
  - Test full project creation workflow
  - Test sync after project opening
  - Test error recovery scenarios

- [x] 11. Final checkpoint - Comprehensive testing
  - Run all unit tests and property tests
  - Test manually: create new project, verify no EDT violations
  - Test manually: create project in existing directory
  - Test manually: missing sync script scenario
  - Test manually: invalid JSON output scenario
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties with 100+ iterations
- Unit tests validate specific examples and edge cases
- Integration tests validate end-to-end workflows
