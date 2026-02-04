# Design Document: 清理 HarmonyOS 插件 C++ 编译代码

## Overview

本设计文档描述了如何清理 HarmonyOS 插件中已废弃的 C++ 编译逻辑。当前实现中，HarmonyDebugRunner 包含了编译 my_main.cpp 的代码，但实际的应用构建和部署是通过 `runOhosApp-Mac.sh` 脚本完成的。本次重构将移除所有与 C++ 编译相关的代码，简化代码库并提高可维护性。

## Architecture

### 当前架构问题

当前架构存在以下问题：

1. **HarmonyDebugRunner.doExecute()** 包含两个步骤：
   - 步骤 1：编译 C++ 程序（已废弃，不再需要）
   - 步骤 2：启动 XDebugSession（实际需要的功能）

2. **HarmonyRunConfiguration** 包含与 my_main.cpp 相关的方法：
   - `getHarmonyPath()`: 返回 my_main.cpp 路径
   - `getOutputPath()`: 返回编译输出路径
   - 这些方法仅被 HarmonyDebugRunner 使用，且已不再需要

3. **实际的构建和部署**由 HarmonyRunProfileState 通过调用 `runOhosApp-Mac.sh` 脚本完成

### 目标架构

清理后的架构将：

1. **HarmonyDebugRunner.doExecute()** 直接启动 XDebugSession，不再进行编译
2. **HarmonyRunConfiguration** 移除 `getHarmonyPath()` 和 `getOutputPath()` 方法
3. **保留所有核心功能**：
   - HarmonyRunProfileState 的脚本执行逻辑
   - 设备选择功能
   - hasHarmonyFile() 项目结构检查

## Components and Interfaces

### Component 1: HarmonyDebugRunner

**当前实现：**
```kotlin
class HarmonyDebugRunner : GenericProgramRunner<RunnerSettings>() {
    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        // 步骤 1: 编译 C++ 程序（需要移除）
        val cppFilePath = configuration.getHarmonyPath()
        val outputPath = configuration.getOutputPath()
        val compileSuccess = compile(cppFilePath, outputPath)
        
        // 步骤 2: 启动 XDebugSession（需要保留）
        val debugSession = debuggerManager.startSession(...)
        return debugSession.runContentDescriptor
    }
    
    private fun compile(cppFilePath: String, outputPath: String): Boolean {
        // C++ 编译逻辑（需要移除）
    }
}
```

**目标实现：**
```kotlin
class HarmonyDebugRunner : GenericProgramRunner<RunnerSettings>() {
    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val configuration = environment.runProfile as? HarmonyRunConfiguration
            ?: throw ExecutionException("Invalid configuration")
        
        val project = configuration.project
        
        // 直接启动 XDebugSession
        val debuggerManager = XDebuggerManager.getInstance(project)
        val debugSession = debuggerManager.startSession(
            environment,
            object : com.intellij.xdebugger.XDebugProcessStarter() {
                override fun start(session: XDebugSession): XDebugProcess {
                    // 注意：这里需要确定正确的可执行文件路径
                    // 由于不再编译 my_main.cpp，需要使用脚本部署后的路径
                    return LLDBDebugProcess(session, executablePath)
                }
            }
        )
        
        return debugSession.runContentDescriptor
    }
}
```

**关键变更：**
- 移除 `compile()` 方法及其所有实现
- 移除对 `getHarmonyPath()` 和 `getOutputPath()` 的调用
- 移除所有与 C++ 编译相关的日志输出
- 更新类文档注释，移除对编译步骤的描述
- 简化 `doExecute()` 方法，直接启动调试会话

**注意事项：**
- 需要确定 LLDBDebugProcess 的正确可执行文件路径
- 如果调试功能依赖于编译输出，可能需要调整调试流程

### Component 2: HarmonyRunConfiguration

**当前实现：**
```kotlin
class HarmonyRunConfiguration(...) : RunConfigurationBase<HarmonyRunConfigurationOptions>(...) {
    // 需要保留的方法
    fun hasHarmonyFile(): Boolean { ... }
    fun getSelectedDeviceId(): String? { ... }
    fun setSelectedDeviceId(deviceId: String?) { ... }
    fun getSelectedDevice(): HarmonyDevice? { ... }
    
    // 需要移除的方法
    fun getHarmonyPath(): String? {
        val projectPath = project.basePath ?: return null
        return File(projectPath, "my_main.cpp").absolutePath
    }
    
    fun getOutputPath(): String? {
        val projectPath = project.basePath ?: return null
        return File(projectPath, "Harmony").absolutePath
    }
}
```

**目标实现：**
```kotlin
class HarmonyRunConfiguration(...) : RunConfigurationBase<HarmonyRunConfigurationOptions>(...) {
    // 保留的方法
    fun hasHarmonyFile(): Boolean { ... }
    fun getSelectedDeviceId(): String? { ... }
    fun setSelectedDeviceId(deviceId: String?) { ... }
    fun getSelectedDevice(): HarmonyDevice? { ... }
    
    // getHarmonyPath() 和 getOutputPath() 已移除
}
```

**关键变更：**
- 完全移除 `getHarmonyPath()` 方法
- 完全移除 `getOutputPath()` 方法
- 保留所有其他方法不变

### Component 3: HarmonyRunProfileState

**当前实现：**
```kotlin
class HarmonyRunProfileState(environment: ExecutionEnvironment) : CommandLineState(environment) {
    override fun startProcess(): ProcessHandler {
        // 执行 runOhosApp-Mac.sh 脚本
        // 这部分逻辑完全保留，不做任何修改
    }
}
```

**目标实现：**
- 完全保持不变
- 此类已经使用正确的脚本执行方式，无需修改

## Data Models

本次重构不涉及数据模型的变更。所有现有的数据结构保持不变：
- HarmonyRunConfigurationOptions
- HarmonyDevice
- DeviceService

## Correctness Properties

在编写正确性属性之前，我需要先分析每个验收标准的可测试性。


### Property Reflection

在分析了所有验收标准后，我发现大多数标准都是关于代码结构的具体验证（方法是否存在、字符串是否出现）。这些都是具体的示例验证，而不是通用属性。让我识别是否有冗余：

**潜在冗余分析：**

1. **方法移除验证（1.1, 2.1, 2.2）**：这些都是独立的方法移除验证，每个都验证不同的方法，不存在冗余。

2. **方法调用验证（1.2, 1.3, 1.4）**：这些都验证 doExecute() 不调用特定方法。虽然都是关于 doExecute() 的，但每个验证不同的方法调用，应该保留。

3. **方法保留验证（2.3, 2.4, 3.2, 3.3）**：这些验证不同的方法和功能被保留，不存在冗余。

4. **字符串引用移除（4.1, 4.2, 4.3）**：每个验证不同的字符串引用，不存在冗余。

**结论：**
- 所有可测试的验收标准都提供了独特的验证价值
- 没有发现逻辑冗余的属性
- 所有标准都应该保留

由于这是一个代码清理任务，大多数验收标准都是关于代码结构的具体验证（示例测试），而不是通用属性。这是合理的，因为我们要验证的是特定代码元素的存在或不存在。

## Correctness Properties

*属性是一个特征或行为，应该在系统的所有有效执行中保持为真——本质上是关于系统应该做什么的正式陈述。属性作为人类可读规范和机器可验证正确性保证之间的桥梁。*

对于本次代码清理任务，大多数验收标准都是关于代码结构的具体验证，而不是通用属性。因此，我们将这些验证作为具体的示例测试来实现，而不是属性测试。

### Example Tests (Code Structure Verification)

以下是需要通过示例测试验证的代码结构要求：

**Example 1: HarmonyDebugRunner 不包含 compile() 方法**
验证 HarmonyDebugRunner 类中不存在名为 `compile` 的方法。
**Validates: Requirements 1.1**

**Example 2: doExecute() 不调用已移除的方法**
验证 HarmonyDebugRunner.doExecute() 方法的实现中不包含对 `compile()`、`getHarmonyPath()` 或 `getOutputPath()` 的调用。
**Validates: Requirements 1.2, 1.3, 1.4**

**Example 3: doExecute() 不包含 C++ 编译相关日志**
验证 HarmonyDebugRunner.doExecute() 方法中不包含与 C++ 编译相关的日志输出（如包含 "编译"、"compile"、"clang++" 等关键词的日志语句）。
**Validates: Requirements 1.5**

**Example 4: HarmonyRunConfiguration 不包含废弃方法**
验证 HarmonyRunConfiguration 类中不存在 `getHarmonyPath()` 和 `getOutputPath()` 方法。
**Validates: Requirements 2.1, 2.2**

**Example 5: HarmonyRunConfiguration 保留必要方法**
验证 HarmonyRunConfiguration 类中仍然存在以下方法：
- `hasHarmonyFile()`
- `getSelectedDeviceId()`
- `setSelectedDeviceId()`
- `getSelectedDevice()`
**Validates: Requirements 2.3, 2.4**

**Example 6: HarmonyRunProfileState 保留脚本执行逻辑**
验证 HarmonyRunProfileState 类的 `startProcess()` 方法仍然存在且包含对 `runOhosApp-Mac.sh` 脚本的引用。
**Validates: Requirements 3.1, 3.4**

**Example 7: 代码库不包含 my_main.cpp 引用**
验证整个代码库中不包含字符串 "my_main.cpp" 的引用。
**Validates: Requirements 4.1**

**Example 8: 代码库不包含 clang++ 引用**
验证整个代码库中不包含字符串 "clang++" 的引用。
**Validates: Requirements 4.3**

**Example 9: 代码库不包含 Harmony 编译输出路径引用**
验证代码库中不包含作为编译输出路径的 "Harmony" 文件引用（特别是在 `File(projectPath, "Harmony")` 这样的上下文中）。
**Validates: Requirements 4.2**

### 注意事项

由于这是一个代码清理和重构任务，主要关注点是代码结构的正确性，而不是运行时行为。因此：

1. **大多数验证都是静态的**：通过代码分析或反射来验证代码结构
2. **不需要属性测试**：没有需要跨多个输入验证的通用属性
3. **示例测试足够**：每个验证点都是具体的、明确的代码结构要求
4. **文档更新（Requirement 5）**：这些是代码审查任务，不适合自动化测试

## Error Handling

本次重构不涉及新的错误处理逻辑。现有的错误处理机制保持不变：

1. **HarmonyDebugRunner**：
   - 保留对无效配置的 ExecutionException 处理
   - 移除与编译失败相关的错误处理

2. **HarmonyRunConfiguration**：
   - 保留所有现有的错误处理逻辑

3. **HarmonyRunProfileState**：
   - 完全保持不变

## Testing Strategy

### 测试方法

由于这是一个代码清理任务，测试策略主要关注验证代码结构的正确性：

1. **静态代码分析**：
   - 使用 Kotlin 反射 API 验证类和方法的存在/不存在
   - 使用代码搜索验证特定字符串的存在/不存在

2. **单元测试**：
   - 为每个示例验证编写单元测试
   - 测试应该验证代码结构，而不是运行时行为

3. **手动代码审查**：
   - 验证文档注释的更新（Requirement 5）
   - 确认代码的可读性和可维护性

### 测试工具

- **JUnit 5**：用于编写单元测试
- **Kotlin Reflection API**：用于验证类和方法结构
- **文本搜索工具**：用于验证字符串引用的移除

### 测试覆盖范围

所有可测试的验收标准（Requirements 1-4）都应该有对应的单元测试。文档更新（Requirement 5）通过代码审查验证。

### 测试执行

测试应该在以下时机执行：
1. 代码修改完成后
2. 提交代码前
3. 作为 CI/CD 流程的一部分

### 预期结果

所有测试通过后，应该确认：
- 所有废弃的方法和代码已被移除
- 所有核心功能仍然保留
- 代码库中不再包含对 my_main.cpp 的引用
- 代码更加简洁和易于维护
