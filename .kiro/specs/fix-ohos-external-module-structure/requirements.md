# Requirements Document

## Introduction

This specification addresses the structural issue in the IntelliJ IDEA/Android Studio plugin for HarmonyOS development. Currently, when users configure an external OHOS project path via `local.ohos.path` in `local.properties`, the plugin incorrectly adds it as a Content Root to the main module, creating a nested structure. The requirement is to create a separate, parallel module for the external OHOS project that appears at the same hierarchical level as the main project.

## Glossary

- **OHOS**: HarmonyOS Operating System, the target platform for this plugin
- **Content Root**: IntelliJ's mechanism for adding source directories to an existing module
- **Module**: IntelliJ's project structure unit that can contain source code, dependencies, and build configuration
- **Gradle Sync**: The process of synchronizing IntelliJ's project structure with Gradle build configuration
- **OhosPostSyncExtension**: The plugin component that executes after Gradle sync completes
- **local.properties**: A Gradle configuration file containing local machine-specific settings
- **Project View**: The tree structure displayed in IntelliJ showing project modules and files
- **ModuleRootManager**: IntelliJ API for managing module content roots and dependencies
- **ModuleManager**: IntelliJ API for creating and managing modules in a project
- **EDT**: Event Dispatch Thread, IntelliJ's UI thread where write operations must occur

## Requirements

### Requirement 1: Read External OHOS Path Configuration

**User Story:** As a HarmonyOS developer, I want the plugin to read my external OHOS project path from local.properties, so that the plugin knows which external project to integrate.

#### Acceptance Criteria

1. WHEN Gradle sync completes successfully, THE OhosPostSyncExtension SHALL read the `local.ohos.path` property from the `local.properties` file
2. WHEN the `local.ohos.path` property is empty or missing, THE OhosPostSyncExtension SHALL clear any stored path and skip module creation
3. WHEN the `local.ohos.path` property contains a non-existent path, THE OhosPostSyncExtension SHALL throw an ExternalSystemException with a descriptive error message
4. WHEN the `local.ohos.path` property contains a valid path, THE OhosPostSyncExtension SHALL store the path in OhosPathService

### Requirement 2: Create Separate Module for External OHOS Project

**User Story:** As a HarmonyOS developer, I want the external OHOS project to appear as a separate module at the same level as my main project, so that I can navigate both projects independently without nesting.

#### Acceptance Criteria

1. WHEN a valid OHOS path is configured, THE OhosPostSyncExtension SHALL create a new module for the external OHOS project
2. THE Module SHALL be named in a way that clearly identifies it as an external OHOS project
3. THE Module SHALL appear at the same hierarchical level as the main project module in the Project View
4. THE Module SHALL NOT be created as a Content Root of the main module
5. THE Module SHALL have its content root set to the external OHOS project path

### Requirement 3: Configure Module to Prevent Compilation Errors

**User Story:** As a HarmonyOS developer, I want the external OHOS module to be properly configured, so that I don't encounter "Compilation is not supported" errors when working with my project.

#### Acceptance Criteria

1. THE Module SHALL be configured with an appropriate module type that prevents compilation attempts
2. THE Module SHALL NOT trigger Gradle build processes for the external project
3. WHEN the module is created, THE OhosPostSyncExtension SHALL mark the content root as excluded from compilation
4. THE Module SHALL allow file browsing and navigation without triggering build errors

### Requirement 4: Clean Up Legacy Content Root Entries

**User Story:** As a HarmonyOS developer upgrading from the old implementation, I want the plugin to remove old Content Root entries, so that I don't have duplicate or conflicting project structures.

#### Acceptance Criteria

1. WHEN processing the OHOS path, THE OhosPostSyncExtension SHALL identify and remove any existing Content Root entries pointing to OHOS paths
2. WHEN processing the OHOS path, THE OhosPostSyncExtension SHALL identify and remove any legacy OHOS modules created by previous implementations
3. THE Cleanup SHALL occur before creating the new module structure
4. THE Cleanup SHALL handle cases where no legacy entries exist without errors

### Requirement 5: Handle Idempotent Module Creation

**User Story:** As a HarmonyOS developer, I want repeated Gradle syncs to not create duplicate modules, so that my project structure remains clean and consistent.

#### Acceptance Criteria

1. WHEN an OHOS module already exists with the same name, THE OhosPostSyncExtension SHALL reuse the existing module instead of creating a duplicate
2. WHEN an existing OHOS module points to a different path, THE OhosPostSyncExtension SHALL update the module's content root to the new path
3. WHEN the OHOS path is cleared (empty), THE OhosPostSyncExtension SHALL remove the OHOS module if it exists
4. THE Module creation SHALL be idempotent across multiple Gradle sync operations

### Requirement 6: Refresh Virtual File System

**User Story:** As a HarmonyOS developer, I want the external OHOS project files to be immediately visible in the IDE, so that I can start browsing and editing them without manual refresh.

#### Acceptance Criteria

1. WHEN the OHOS module is created or updated, THE OhosPostSyncExtension SHALL refresh the Virtual File System for the external project path
2. THE Refresh SHALL be performed asynchronously to avoid blocking the UI thread
3. THE Refresh SHALL include all subdirectories of the external project
4. WHEN the refresh completes, THE Project View SHALL display the external project files

### Requirement 7: Execute Operations on Correct Thread

**User Story:** As a plugin developer, I want all write operations to execute on the EDT and all I/O operations on background threads, so that the plugin follows IntelliJ platform threading requirements.

#### Acceptance Criteria

1. WHEN reading the local.properties file, THE OhosPostSyncExtension SHALL execute on a background thread (IO dispatcher)
2. WHEN creating or modifying modules, THE OhosPostSyncExtension SHALL execute on the EDT using invokeLater
3. WHEN performing write operations, THE OhosPostSyncExtension SHALL wrap them in runWriteAction
4. THE Threading model SHALL prevent deadlocks and UI freezes

### Requirement 8: Log Operations for Debugging

**User Story:** As a plugin developer, I want comprehensive logging of all operations, so that I can debug issues reported by users.

#### Acceptance Criteria

1. WHEN processing the OHOS path, THE OhosPostSyncExtension SHALL log the path value and validation result
2. WHEN creating or updating modules, THE OhosPostSyncExtension SHALL log the module name and operation type
3. WHEN cleaning up legacy entries, THE OhosPostSyncExtension SHALL log each removed entry
4. WHEN errors occur, THE OhosPostSyncExtension SHALL log the error with full stack trace
5. THE Logging SHALL use appropriate log levels (info, warn, error) based on severity
