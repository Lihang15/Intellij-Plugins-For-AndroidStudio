# Implementation Plan: OHOS External Project Structure Fix

## Overview

This implementation plan transforms the OHOS external project integration from a Content Root approach (which creates nested structure) to a separate Module approach (which creates parallel structure). The key changes involve modifying `OhosPostSyncExtension` to create an independent module instead of adding a content root to the main module.

## Tasks

- [x] 1. Implement module creation logic
  - Replace `attachToExistingModule()` with `createOrUpdateOhosModule()` that creates a separate module
  - Use `ModuleManager.newModule()` with `ModuleType.EMPTY` to prevent compilation
  - Set module name to `OHOS-External-{projectName}` for clear identification
  - Store module IML file in `.idea/modules/` directory
  - _Requirements: 2.1, 2.2, 3.1_

- [x] 2. Configure module content root and exclusions
  - Add the OHOS path as the module's content root using `ModuleRootManager`
  - Mark the entire content root as excluded from compilation using `addExcludeFolder()`
  - Ensure content root URL is properly formatted using `VfsUtil.pathToUrl()`
  - _Requirements: 2.5, 3.3_

- [x] 3. Implement idempotent module management
  - Check if OHOS module already exists using `findModuleByName()`
  - If module exists, update its content root instead of creating duplicate
  - If path is empty/null, remove existing OHOS module if present
  - Handle path changes by updating existing module's content root
  - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [x] 4. Implement cleanup of legacy entries
  - Remove old content roots from main module that point to OHOS paths
  - Remove legacy OHOS modules with old naming patterns (`z-OHOS-External`, `OHOS-External-Project`, `OHOS-External`)
  - Perform cleanup before creating new module structure
  - Handle cases where no legacy entries exist without errors
  - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [x] 5. Verify module independence from main module
  - Ensure main module's content roots do not include OHOS path after processing
  - Verify OHOS module appears as separate entry in module list
  - _Requirements: 2.4_

- [x] 6. Checkpoint - Ensure module structure is correct
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Enhance error handling and validation
  - Validate path existence and throw `ExternalSystemException` for non-existent paths
  - Handle empty/null paths by clearing service and removing module
  - Catch and log module creation/update errors without propagating
  - Provide descriptive error messages for configuration issues
  - _Requirements: 1.2, 1.3, 1.4_

- [x] 8. Add comprehensive logging
  - Log path validation results with path value
  - Log module creation/update operations with module name and operation type
  - Log cleanup operations with details of removed entries
  - Log errors with full stack traces
  - Use appropriate log levels (info, warn, error) based on severity
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

- [x] 9. Implement VFS refresh for external project
  - Refresh Virtual File System for OHOS path after module creation/update
  - Use `VfsUtil.findFile()` with recursive flag enabled
  - Perform refresh asynchronously to avoid blocking UI
  - _Requirements: 6.1, 6.3_

- [x] 10. Ensure proper threading model
  - Keep file I/O operations on IO dispatcher (Dispatchers.IO)
  - Execute module operations on EDT using `invokeLater`
  - Wrap write operations in `runWriteAction`
  - _Requirements: 7.1, 7.2, 7.3_

- [x] 11. Final checkpoint - Integration testing
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- User explicitly requested NO test cases, so all test-related tasks are excluded
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Focus is on transforming from Content Root to Module approach
- Threading model must follow IntelliJ platform requirements (EDT for write operations, background for I/O)
