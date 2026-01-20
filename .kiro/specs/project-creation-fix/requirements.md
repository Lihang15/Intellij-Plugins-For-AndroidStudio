# Requirements Document

## Introduction

This specification addresses critical issues in the IntelliJ plugin's project creation and synchronization workflow. The plugin currently violates IntelliJ Platform threading rules, causes file conflicts during project creation, and triggers excessive file system refresh operations.

## Glossary

- **EDT**: Event Dispatch Thread - IntelliJ's UI thread where all UI operations must occur
- **BGT**: Background Thread - Non-UI thread for long-running operations
- **VFS**: Virtual File System - IntelliJ's abstraction layer for file operations
- **Project_Template**: The KMP-OHOS project template structure
- **Sync_Service**: The DataSyncService that loads project configuration data
- **Recipe_Executor**: The Android Studio wizard component that generates project files

## Requirements

### Requirement 1: Thread-Safe Project Operations

**User Story:** As a plugin developer, I want all long-running operations to execute on background threads, so that the IDE remains responsive and complies with IntelliJ Platform threading rules.

#### Acceptance Criteria

1. WHEN the plugin executes shell scripts, THEN the System SHALL run them on a background thread
2. WHEN the plugin performs file I/O operations, THEN the System SHALL execute them outside the EDT
3. WHEN the plugin calls Gradle JVM path resolution, THEN the System SHALL use background thread execution
4. WHEN background operations complete, THEN the System SHALL switch to EDT only for UI updates
5. WHEN the plugin refreshes the VFS, THEN the System SHALL batch refresh operations to minimize frequency

### Requirement 2: Conflict-Free Project File Generation

**User Story:** As a user creating a new project, I want the wizard to handle existing files gracefully, so that I don't see warnings about file conflicts.

#### Acceptance Criteria

1. WHEN the Recipe_Executor generates project files, THEN the System SHALL check for existing files before copying
2. WHEN a target file already exists, THEN the System SHALL skip copying that file without warnings
3. WHEN generating settings.gradle.kts, THEN the System SHALL merge with existing content rather than overwrite
4. WHEN copying template directories, THEN the System SHALL preserve existing project-specific files
5. WHEN the wizard completes, THEN the System SHALL report only actual errors, not expected file existence

### Requirement 3: Optimized File System Refresh

**User Story:** As a user, I want project creation to complete quickly without excessive file system operations, so that I can start working immediately.

#### Acceptance Criteria

1. WHEN the plugin copies multiple files, THEN the System SHALL perform a single VFS refresh after all copies complete
2. WHEN the plugin generates project structure, THEN the System SHALL batch all file operations before refreshing
3. WHEN the VFS refresh executes, THEN the System SHALL use asynchronous refresh with proper scope
4. WHEN multiple refresh requests occur, THEN the System SHALL debounce them to prevent redundant operations
5. WHEN the project opens, THEN the System SHALL wait for VFS refresh completion before triggering sync

### Requirement 4: Robust Project Synchronization

**User Story:** As a user, I want the plugin to sync project data reliably after project creation, so that all plugin features work correctly.

#### Acceptance Criteria

1. WHEN the project opens, THEN the Sync_Service SHALL verify the sync script exists before execution
2. WHEN the sync script executes, THEN the System SHALL handle script failures gracefully with user-friendly messages
3. WHEN the sync script completes, THEN the System SHALL validate the output JSON before parsing
4. WHEN sync data loads successfully, THEN the System SHALL store it in the project-level service
5. WHEN sync fails, THEN the System SHALL log detailed error information without crashing the IDE

### Requirement 5: Safe EDT Operations

**User Story:** As a plugin developer, I want clear separation between EDT and background operations, so that the plugin never blocks the UI thread.

#### Acceptance Criteria

1. WHEN the plugin needs to update UI, THEN the System SHALL use ApplicationManager.invokeLater on EDT
2. WHEN the plugin performs computation, THEN the System SHALL use coroutines or background tasks
3. WHEN switching from BGT to EDT, THEN the System SHALL use proper synchronization mechanisms
4. WHEN the plugin opens a project, THEN the System SHALL use ProjectManager API on EDT only
5. WHEN background tasks complete, THEN the System SHALL update UI elements only on EDT

### Requirement 6: Improved Error Handling

**User Story:** As a user, I want clear error messages when project creation fails, so that I can understand and fix the problem.

#### Acceptance Criteria

1. WHEN template files are missing, THEN the System SHALL display a user-friendly error dialog
2. WHEN script execution fails, THEN the System SHALL show the script output and error details
3. WHEN file operations fail, THEN the System SHALL indicate which files caused the problem
4. WHEN sync fails, THEN the System SHALL provide actionable troubleshooting steps
5. WHEN errors occur, THEN the System SHALL log full stack traces for debugging

### Requirement 7: Clean Project Activity Lifecycle

**User Story:** As a plugin developer, I want the MyProjectActivity to execute efficiently, so that project startup is fast and reliable.

#### Acceptance Criteria

1. WHEN MyProjectActivity executes, THEN the System SHALL remove the sample code warning message
2. WHEN the activity creates run configurations, THEN the System SHALL check for duplicates efficiently
3. WHEN the activity triggers sync, THEN the System SHALL use proper coroutine context
4. WHEN the activity completes, THEN the System SHALL not block project opening
5. WHEN errors occur in the activity, THEN the System SHALL not prevent the project from opening
