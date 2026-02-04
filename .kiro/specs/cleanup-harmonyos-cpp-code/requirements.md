# Requirements Document

## Introduction

本规格文档定义了清理 HarmonyOS 插件运行配置代码的需求。当前代码中包含了已废弃的 C++ 编译逻辑（用于编译 my_main.cpp 文件），但实际的应用构建和部署是通过 `runOhosApp-Mac.sh` 脚本完成的。本次清理将移除所有与 my_main.cpp 相关的无用代码，同时保留核心功能。

## Glossary

- **HarmonyDebugRunner**: 负责处理 HarmonyOS 应用调试运行的类
- **HarmonyRunConfiguration**: HarmonyOS 运行配置类，包含配置选项和验证逻辑
- **HarmonyRunProfileState**: 负责实际执行构建和部署脚本的类
- **my_main.cpp**: 已废弃的 C++ 源文件，不再使用
- **compile() method**: HarmonyDebugRunner 中用于编译 C++ 代码的方法，需要移除
- **getHarmonyPath() method**: 返回 my_main.cpp 文件路径的方法，需要移除
- **getOutputPath() method**: 返回 C++ 编译输出路径的方法，需要移除
- **hasHarmonyFile() method**: 检查 HarmonyOS 项目结构的方法，需要保留
- **Device_Selection**: 设备选择功能，需要保留

## Requirements

### Requirement 1: 移除 HarmonyDebugRunner 中的 C++ 编译逻辑

**User Story:** 作为开发者，我希望移除 HarmonyDebugRunner 中已废弃的 C++ 编译代码，以便代码库只包含实际使用的功能。

#### Acceptance Criteria

1. WHEN HarmonyDebugRunner 类被修改后，THE System SHALL 不再包含 compile() 方法
2. WHEN HarmonyDebugRunner.doExecute() 方法被修改后，THE System SHALL 不再调用 compile() 方法
3. WHEN HarmonyDebugRunner.doExecute() 方法被修改后，THE System SHALL 不再调用 getHarmonyPath() 方法
4. WHEN HarmonyDebugRunner.doExecute() 方法被修改后，THE System SHALL 不再调用 getOutputPath() 方法
5. WHEN HarmonyDebugRunner.doExecute() 方法被修改后，THE System SHALL 不再包含与 C++ 编译相关的日志输出

### Requirement 2: 移除 HarmonyRunConfiguration 中的废弃方法

**User Story:** 作为开发者，我希望移除 HarmonyRunConfiguration 中与 my_main.cpp 相关的方法，以便简化配置类的接口。

#### Acceptance Criteria

1. WHEN HarmonyRunConfiguration 类被修改后，THE System SHALL 不再包含 getHarmonyPath() 方法
2. WHEN HarmonyRunConfiguration 类被修改后，THE System SHALL 不再包含 getOutputPath() 方法
3. WHEN HarmonyRunConfiguration 类被修改后，THE System SHALL 保留 hasHarmonyFile() 方法
4. WHEN HarmonyRunConfiguration 类被修改后，THE System SHALL 保留所有设备选择相关的方法

### Requirement 3: 保留核心功能

**User Story:** 作为开发者，我希望在清理代码的同时保留所有核心功能，以便插件继续正常工作。

#### Acceptance Criteria

1. WHEN 代码清理完成后，THE System SHALL 保留 HarmonyRunProfileState 中的脚本执行逻辑
2. WHEN 代码清理完成后，THE System SHALL 保留设备选择功能
3. WHEN 代码清理完成后，THE System SHALL 保留 hasHarmonyFile() 方法用于检查 HarmonyOS 项目结构
4. WHEN 代码清理完成后，THE System SHALL 保留所有与 runOhosApp-Mac.sh 脚本相关的逻辑

### Requirement 4: 移除所有 my_main.cpp 引用

**User Story:** 作为开发者，我希望移除代码中所有对 my_main.cpp 的引用，以便代码库不再包含对已废弃文件的依赖。

#### Acceptance Criteria

1. WHEN 代码清理完成后，THE System SHALL 不再包含任何对 "my_main.cpp" 字符串的引用
2. WHEN 代码清理完成后，THE System SHALL 不再包含任何对 "Harmony" 输出文件的引用（编译输出路径）
3. WHEN 代码清理完成后，THE System SHALL 不再包含任何对 "clang++" 编译器的引用

### Requirement 5: 更新类文档和注释

**User Story:** 作为开发者，我希望更新类的文档注释以反映实际功能，以便代码易于理解和维护。

#### Acceptance Criteria

1. WHEN HarmonyDebugRunner 类被修改后，THE System SHALL 更新类文档注释以移除对 C++ 编译的描述
2. WHEN HarmonyDebugRunner 类被修改后，THE System SHALL 更新类文档注释以准确描述当前的调试流程
3. WHEN 方法被移除后，THE System SHALL 移除相关的方法文档注释
