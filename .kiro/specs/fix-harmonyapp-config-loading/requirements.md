# Requirements Document

## Introduction

This specification addresses a timing issue in the HarmonyOS IntelliJ plugin where the harmonyApp run configuration fails to appear in the UI when a project is first opened after creation through the wizard. The root cause is a race condition between file system operations during project creation and the Virtual File System (VFS) refresh in IntelliJ IDEA.

## Glossary

- **VFS (Virtual File System)**: IntelliJ's abstraction layer over the physical file system that caches file information for performance
- **ProjectActivity**: IntelliJ's startup hook that executes when a project is opened
- **RunManager**: IntelliJ API for managing run configurations
- **EDT (Event Dispatch Thread)**: The UI thread in IntelliJ where UI operations must be performed
- **Run Configuration**: A saved configuration that defines how to run or debug an application
- **HarmonyOS Project**: A project containing a `harmonyApp` directory or `local.ohos.path` in local.properties
- **Project Wizard**: The UI flow for creating new projects in IntelliJ IDEA
- **Configuration UI**: The run configuration dropdown in the IntelliJ toolbar

## Requirements

### Requirement 1: Reliable VFS Synchronization

**User Story:** As a plugin developer, I want the VFS to be fully synchronized before checking for HarmonyOS project markers, so that file system checks are accurate.

#### Acceptance Criteria

1. WHEN the ProjectActivity executes, THE System SHALL ensure VFS is synchronized with the physical file system before checking for HarmonyOS project markers
2. WHEN checking for the `harmonyApp` directory, THE System SHALL use VFS APIs rather than direct File I/O
3. WHEN VFS refresh is incomplete, THE System SHALL wait for refresh completion before proceeding with configuration creation
4. THE System SHALL NOT rely on fixed time delays for VFS synchronization

### Requirement 2: Robust Project Detection

**User Story:** As a user, I want the plugin to reliably detect HarmonyOS projects, so that run configurations are created consistently.

#### Acceptance Criteria

1. WHEN a project contains a `harmonyApp` directory, THE System SHALL detect it as a HarmonyOS project
2. WHEN local.properties contains a valid `local.ohos.path`, THE System SHALL detect it as a HarmonyOS project
3. WHEN VFS has not yet indexed new files, THE System SHALL retry detection after VFS refresh
4. THE System SHALL log detection attempts and results for debugging purposes

### Requirement 3: Configuration Creation Timing

**User Story:** As a user, I want the harmonyApp run configuration to appear immediately after project creation, so that I can start development without manual configuration.

#### Acceptance Criteria

1. WHEN a HarmonyOS project is detected, THE System SHALL create the harmonyApp run configuration on the EDT thread
2. WHEN the configuration is created, THE System SHALL ensure it appears in the Configuration UI
3. WHEN the configuration already exists, THE System SHALL skip creation and log the skip
4. THE System SHALL set the newly created configuration as the selected configuration if no other configuration is selected

### Requirement 4: Error Handling and Resilience

**User Story:** As a user, I want the plugin to handle errors gracefully, so that project opening is not blocked by configuration creation failures.

#### Acceptance Criteria

1. WHEN VFS refresh fails, THE System SHALL log the error and continue project opening
2. WHEN configuration creation fails, THE System SHALL log the error and continue project opening
3. WHEN project detection is ambiguous, THE System SHALL log warnings with diagnostic information
4. THE System SHALL NOT throw exceptions that prevent project opening

### Requirement 5: VFS Event-Driven Approach

**User Story:** As a plugin developer, I want to use VFS events to trigger configuration creation, so that timing issues are eliminated.

#### Acceptance Criteria

1. WHEN the wizard completes file creation, THE System SHALL listen for VFS events indicating file system changes
2. WHEN the `harmonyApp` directory appears in VFS, THE System SHALL trigger configuration creation
3. WHEN VFS events indicate project structure is ready, THE System SHALL proceed with detection
4. THE System SHALL unregister VFS listeners after successful configuration creation to prevent duplicate attempts

### Requirement 6: Idempotent Configuration Creation

**User Story:** As a plugin developer, I want configuration creation to be idempotent, so that multiple triggers do not create duplicate configurations.

#### Acceptance Criteria

1. WHEN configuration creation is triggered multiple times, THE System SHALL create at most one harmonyApp configuration
2. WHEN checking for existing configurations, THE System SHALL verify both configuration type and name
3. WHEN a configuration with the same name but different type exists, THE System SHALL handle it appropriately
4. THE System SHALL use atomic checks to prevent race conditions in configuration creation

### Requirement 7: Diagnostic Logging

**User Story:** As a plugin developer, I want comprehensive logging, so that I can diagnose timing issues in production.

#### Acceptance Criteria

1. WHEN VFS operations occur, THE System SHALL log VFS state and timing information
2. WHEN project detection runs, THE System SHALL log which markers were found and which were missing
3. WHEN configuration creation executes, THE System SHALL log each step of the creation process
4. THE System SHALL include timestamps in logs to help identify timing issues
