# Implementation Plan: 清理 HarmonyOS 插件 C++ 编译代码

## Overview

本实现计划将清理 HarmonyOS 插件中已废弃的 C++ 编译逻辑。主要工作包括：
1. 移除 HarmonyDebugRunner 中的 compile() 方法和相关调用
2. 移除 HarmonyRunConfiguration 中的 getHarmonyPath() 和 getOutputPath() 方法
3. 验证所有核心功能保持不变
4. 确保代码库中不再包含对 my_main.cpp 的引用

## Tasks

- [x] 1. 清理 HarmonyDebugRunner 类
  - [x] 1.1 移除 compile() 方法
    - 删除整个 compile() 私有方法及其实现
    - _Requirements: 1.1_
  
  - [x] 1.2 简化 doExecute() 方法
    - 移除对 getHarmonyPath() 的调用
    - 移除对 getOutputPath() 的调用
    - 移除对 compile() 方法的调用
    - 移除所有与 C++ 编译相关的变量声明（cppFilePath, outputPath, compileSuccess）
    - 移除所有与编译相关的日志输出（包含 "编译"、"compile"、"clang++" 等关键词的 println 语句）
    - _Requirements: 1.2, 1.3, 1.4, 1.5_
  
  - [x] 1.3 更新类文档注释
    - 更新 HarmonyDebugRunner 类的 KDoc 注释
    - 移除对"先编译鸿蒙工程生成 Harmony 可执行文件"的描述
    - 更新为准确描述当前的调试流程（直接启动 XDebugSession + LLDBDebugProcess）
    - _Requirements: 5.1, 5.2_
  
  - [x] 1.4 处理 LLDBDebugProcess 的可执行文件路径
    - 确定 LLDBDebugProcess 构造函数需要的正确可执行文件路径
    - 如果需要，添加注释说明路径的来源（由 runOhosApp-Mac.sh 脚本部署）
    - 如果当前实现有问题，添加 TODO 注释标记需要后续处理
    - _Requirements: 1.2_

- [x] 2. 清理 HarmonyRunConfiguration 类
  - [x] 2.1 移除 getHarmonyPath() 方法
    - 删除整个 getHarmonyPath() 方法及其文档注释
    - _Requirements: 2.1_
  
  - [x] 2.2 移除 getOutputPath() 方法
    - 删除整个 getOutputPath() 方法及其文档注释
    - _Requirements: 2.2_
  
  - [x] 2.3 验证保留的方法
    - 确认 hasHarmonyFile() 方法保持不变
    - 确认 getSelectedDeviceId() 方法保持不变
    - 确认 setSelectedDeviceId() 方法保持不变
    - 确认 getSelectedDevice() 方法保持不变
    - _Requirements: 2.3, 2.4_

- [x] 3. 验证核心功能保留
  - [x] 3.1 检查 HarmonyRunProfileState
    - 确认 HarmonyRunProfileState.kt 文件未被修改
    - 确认 startProcess() 方法中的脚本执行逻辑完整
    - 确认对 runOhosApp-Mac.sh 的引用存在
    - _Requirements: 3.1, 3.4_
  
  - [x] 3.2 验证设备选择功能
    - 确认 DeviceService 相关代码未被修改
    - 确认设备选择相关方法在 HarmonyRunConfiguration 中保留
    - _Requirements: 3.2_

- [x] 4. 全局代码搜索验证
  - [x] 4.1 搜索并移除 my_main.cpp 引用
    - 在整个项目中搜索字符串 "my_main.cpp"
    - 确认所有引用都已被移除
    - _Requirements: 4.1_
  
  - [x] 4.2 搜索并移除 clang++ 引用
    - 在整个项目中搜索字符串 "clang++"
    - 确认所有引用都已被移除
    - _Requirements: 4.3_
  
  - [x] 4.3 搜索并移除 Harmony 编译输出路径引用
    - 在整个项目中搜索 File(projectPath, "Harmony") 或类似的编译输出路径引用
    - 确认所有作为编译输出的 "Harmony" 文件引用都已被移除
    - 注意：不要移除 HarmonyOS、HarmonyDevice 等正常的类名和变量名
    - _Requirements: 4.2_

- [x] 5. Checkpoint - 代码审查和验证
  - 手动审查所有修改的文件
  - 确认没有遗漏的 C++ 编译相关代码
  - 确认所有核心功能保持不变
  - 确认代码可以正常编译
  - 如有问题，请向用户报告

- [ ]* 6. 编写验证测试（可选）
  - [ ]* 6.1 编写代码结构验证测试
    - 使用 Kotlin 反射 API 验证 HarmonyDebugRunner 不包含 compile() 方法
    - 验证 HarmonyRunConfiguration 不包含 getHarmonyPath() 和 getOutputPath() 方法
    - 验证 HarmonyRunConfiguration 仍包含 hasHarmonyFile() 和设备选择方法
    - **Example 1, 4, 5**
    - **Validates: Requirements 1.1, 2.1, 2.2, 2.3, 2.4**
  
  - [ ]* 6.2 编写字符串引用验证测试
    - 编写测试搜索源代码文件中的特定字符串
    - 验证不包含 "my_main.cpp"、"clang++" 等字符串
    - **Example 7, 8, 9**
    - **Validates: Requirements 4.1, 4.2, 4.3**

- [x] 7. Final checkpoint - 完成清理
  - 确认所有任务已完成
  - 确认代码可以正常编译和运行
  - 向用户报告清理结果

## Notes

- 任务标记为 `*` 的是可选的测试任务，可以跳过以加快清理速度
- 本次清理不涉及运行时行为的改变，主要是代码结构的简化
- HarmonyRunProfileState.kt 完全不需要修改
- 清理后的代码应该更简洁、更易于维护
- 如果在清理过程中发现 LLDBDebugProcess 的路径问题，应该添加 TODO 注释而不是尝试修复
