# Recipe 数据结构详解

## 概述

这份文档详细解释 `composeMultiplatformProjectRecipe.kt` 中的核心数据结构和代码逻辑。

---

## 1. projectData.rootDir.toVirtualFile()?.apply

### 代码分析

```kotlin
projectData.rootDir.toVirtualFile()?.apply {
    // 在这里 this 指向 VirtualFile
    val logger = thisLogger()
    val fileTemplateManager = FileTemplateManager.getDefaultInstance()
    // ...
}
```

### 详细解释

#### projectData
**类型**: `ProjectTemplateData`
**来源**: `moduleData` 的解构
```kotlin
val (projectData, _, _) = moduleData
```

**包含**:
- `rootDir`: 项目根目录的 `File` 对象
- 其他项目元数据

#### rootDir
**类型**: `File` (java.io.File)
**内容**: 项目根目录的文件系统路径
**示例**: `/Users/username/Projects/MyKMPProject`

#### toVirtualFile()
**作用**: 将 `File` 转换为 IntelliJ Platform 的 `VirtualFile`

**为什么需要转换？**
- `File`: Java 标准库的文件对象
- `VirtualFile`: IntelliJ Platform 的虚拟文件系统对象
- `VirtualFile` 提供了 IDE 特定的功能：
  - 文件监听
  - 缓存管理
  - 跨平台支持
  - 与 IDE 集成

**返回**: `VirtualFile?` (可能为 null)

#### ?.apply
**作用**: 安全调用 + 作用域函数

**等价于**:
```kotlin
val virtualFile = projectData.rootDir.toVirtualFile()
if (virtualFile != null) {
    // 在这里 this 指向 virtualFile
    // 执行文件生成逻辑
}
```

**在 apply 块中**:
- `this` 指向 `VirtualFile` (项目根目录)
- 所有文件操作都基于这个根目录

---

## 2. platforms 数据结构

### 代码

```kotlin
val platforms: List<FileGenerator> = listOfNotNull(
    CommonFileGenerator(config, dataModel, this),
    if (config.isAndroidEnable) AndroidFileGenerator(config) else null,
    if (config.isIOSEnable) IOSFileGenerator(config) else null,
    if (config.isHarmonyEnable) HarmonyFileGenerator(config) else null,
)
```

### 数据结构

```
platforms: List<FileGenerator>
    │
    ├─ CommonFileGenerator (总是存在)
    │   ├─ params: KMPConfigModel
    │   ├─ dataModel: Map<String, Any>
    │   └─ virtualFile: VirtualFile
    │
    ├─ AndroidFileGenerator (条件: isAndroidEnable)
    │   └─ params: KMPConfigModel
    │
    ├─ IOSFileGenerator (条件: isIOSEnable)
    │   └─ params: KMPConfigModel
    │
    └─ HarmonyFileGenerator (条件: isHarmonyEnable)
        └─ params: KMPConfigModel
```

### 示例

**场景**: 用户勾选了 Android 和 iOS

```kotlin
platforms = [
    CommonFileGenerator(...),      // 索引 0
    AndroidFileGenerator(...),     // 索引 1
    IOSFileGenerator(...),         // 索引 2
]
```

**场景**: 用户只勾选了 Android

```kotlin
platforms = [
    CommonFileGenerator(...),      // 索引 0
    AndroidFileGenerator(...),     // 索引 1
]
```

### FileGenerator 接口

```kotlin
abstract class FileGenerator(protected val params: KMPConfigModel) {
    abstract fun generate(
        ftManager: FileTemplateManager, 
        packageName: String
    ): List<GeneratorAsset>
}
```

**每个 Generator 的职责**:
- `CommonFileGenerator`: 生成通用文件 (Gradle 配置、共享代码)
- `AndroidFileGenerator`: 生成 Android 特定文件
- `IOSFileGenerator`: 生成 iOS 特定文件
- `HarmonyFileGenerator`: 生成 Desktop 特定文件

---

## 3. assets 数据结构

### 代码

```kotlin
val assets = mutableListOf<GeneratorAsset>()
assets.addAll(platforms.flatMap { it.generate(fileTemplateManager, config.packageName) })
```

### 生成过程

```
platforms.flatMap { it.generate(...) }
    │
    ├─ CommonFileGenerator.generate()
    │   └─ 返回 List<GeneratorAsset> (10+ 个)
    │
    ├─ AndroidFileGenerator.generate()
    │   └─ 返回 List<GeneratorAsset> (3-4 个)
    │
    ├─ IOSFileGenerator.generate()
    │   └─ 返回 List<GeneratorAsset> (11 个)
    │
    └─ HarmonyFileGenerator.generate()
        └─ 返回 List<GeneratorAsset> (1 个)
    │
    ↓ flatMap 合并所有列表
    │
assets: List<GeneratorAsset> (25-30 个)
```

### GeneratorAsset 类型

**来自**: `com.intellij.ide.starters.local.GeneratorAsset`

**子类型**:
1. `GeneratorTemplateFile` - 从模板生成文件
2. `GeneratorEmptyDirectory` - 创建空目录

### 数据结构详解

```
assets: List<GeneratorAsset>
    │
    ├─ GeneratorTemplateFile
    │   ├─ relativePath: String          ← 相对路径
    │   │   例: "build.gradle.kts"
    │   │   例: "composeApp/src/commonMain/kotlin/com/example/App.kt"
    │   │
    │   └─ template: FileTemplate        ← 模板对象
    │       ├─ name: String              ← 模板名称
    │       │   例: "project_build.gradle.kts"
    │       │
    │       └─ extension: String         ← 扩展名
    │           例: "kt"
    │
    └─ GeneratorEmptyDirectory
        └─ relativePath: String          ← 目录路径
            例: "iosApp/iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/configuration"
```

### 示例数据

**CommonFileGenerator 生成的 assets**:
```kotlin
[
    GeneratorTemplateFile(
        relativePath = "build.gradle.kts",
        template = FileTemplate(name="project_build.gradle.kts", ext="kt")
    ),
    GeneratorTemplateFile(
        relativePath = "settings.gradle.kts",
        template = FileTemplate(name="settings.gradle.kts", ext="kt")
    ),
    GeneratorTemplateFile(
        relativePath = "gradle.properties",
        template = FileTemplate(name="gradle.properties", ext="")
    ),
    GeneratorTemplateFile(
        relativePath = "composeApp/build.gradle.kts",
        template = FileTemplate(name="compose.gradle.kts", ext="kt")
    ),
    GeneratorTemplateFile(
        relativePath = "composeApp/src/commonMain/kotlin/com/example/App.kt",
        template = FileTemplate(name="common_app.kt", ext="kt")
    ),
    // ... 更多文件
]
```

**AndroidFileGenerator 生成的 assets**:
```kotlin
[
    GeneratorTemplateFile(
        relativePath = "composeApp/src/androidMain/kotlin/com/example/MainActivity.kt",
        template = FileTemplate(name="android_main_activity.kt", ext="kt")
    ),
    GeneratorTemplateFile(
        relativePath = "composeApp/src/androidMain/AndroidManifest.xml",
        template = FileTemplate(name="android_manifest.xml", ext="xml")
    ),
    GeneratorTemplateFile(
        relativePath = "composeApp/src/androidMain/res/values/strings.xml",
        template = FileTemplate(name="values.xml", ext="xml")
    ),
]
```

**IOSFileGenerator 生成的 assets**:
```kotlin
[
    GeneratorTemplateFile(
        relativePath = "composeApp/src/iosMain/kotlin/com/example/MainViewController.kt",
        template = FileTemplate(name="compose_ios_main.kt", ext="kt")
    ),
    GeneratorEmptyDirectory(
        relativePath = "iosApp/iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/configuration"
    ),
    GeneratorTemplateFile(
        relativePath = "iosApp/iosApp/ContentView.swift",
        template = FileTemplate(name="contentview.swift", ext="swift")
    ),
    // ... 更多 iOS 文件
]
```

---

## 4. 完整的数据流

### 流程图

```
1. 创建 platforms 列表
    │
    ├─ CommonFileGenerator
    ├─ AndroidFileGenerator (条件)
    ├─ IOSFileGenerator (条件)
    └─ HarmonyFileGenerator (条件)
    │
    ↓
2. 调用每个 generator.generate()
    │
    ├─ CommonFileGenerator.generate()
    │   └─ 返回 [asset1, asset2, asset3, ...]
    │
    ├─ AndroidFileGenerator.generate()
    │   └─ 返回 [asset4, asset5, ...]
    │
    └─ IOSFileGenerator.generate()
        └─ 返回 [asset6, asset7, ...]
    │
    ↓
3. flatMap 合并所有 assets
    │
assets = [asset1, asset2, asset3, asset4, asset5, asset6, asset7, ...]
    │
    ↓
4. 遍历 assets 生成文件
    │
    ├─ asset1 (GeneratorTemplateFile)
    │   └─ Utils.generateFileFromTemplate()
    │       └─ 创建 build.gradle.kts
    │
    ├─ asset2 (GeneratorTemplateFile)
    │   └─ Utils.generateFileFromTemplate()
    │       └─ 创建 settings.gradle.kts
    │
    ├─ asset3 (GeneratorEmptyDirectory)
    │   └─ Utils.createEmptyDirectory()
    │       └─ 创建空目录
    │
    └─ ... 继续处理所有 assets
    │
    ↓
5. 刷新 VFS
    │
generationHelper.flushVfsRefreshSync(rootDir)
```

---

## 5. 代码执行示例

### 场景: 用户勾选 Android + iOS + Koin

```kotlin
// 1. 创建 platforms
val platforms = listOfNotNull(
    CommonFileGenerator(...),      // 总是创建
    AndroidFileGenerator(...),     // isAndroidEnable = true
    IOSFileGenerator(...),         // isIOSEnable = true
    null                           // isHarmonyEnable = false
)
// platforms.size = 3

// 2. 生成 assets
val assets = platforms.flatMap { it.generate(...) }
// CommonFileGenerator → 15 个 assets
// AndroidFileGenerator → 4 个 assets (包括 MainApp.kt，因为 Koin 启用)
// IOSFileGenerator → 11 个 assets
// 总计: 30 个 assets

// 3. 遍历生成文件
assets.forEach { asset ->
    when (asset) {
        is GeneratorTemplateFile -> {
            // 从模板生成文件
            Utils.generateFileFromTemplate(dataModel, rootDir, asset)
        }
        is GeneratorEmptyDirectory -> {
            // 创建空目录
            Utils.createEmptyDirectory(rootDir, asset.relativePath)
        }
    }
}
```

---

## 6. 关键对象详解

### VirtualFile (this)

**在 apply 块中的作用**:
```kotlin
projectData.rootDir.toVirtualFile()?.apply {
    // this: VirtualFile (项目根目录)
    
    // 创建子目录
    val subDir = this.findChild("composeApp") 
        ?: VfsUtil.createDirectoryIfMissing(this, "composeApp")
    
    // 创建文件
    val file = subDir.createChildData(this, "App.kt")
    
    // 写入内容
    VfsUtil.saveText(file, "package com.example\n...")
}
```

**常用方法**:
- `findChild(name)`: 查找子文件/目录
- `createChildData(requestor, name)`: 创建文件
- `delete(requestor)`: 删除文件/目录
- `path`: 获取绝对路径

### FileTemplateManager

**作用**: 管理和加载模板文件

```kotlin
val ftManager = FileTemplateManager.getDefaultInstance()
val template = ftManager.getCodeTemplate("common_app.kt")
// 返回: FileTemplate 对象
```

**模板位置**: `src/main/resources/fileTemplates/code/`

### KMPConfigModel

**作用**: 存储用户的配置选项

```kotlin
class KMPConfigModel {
    var isAndroidEnable: Boolean
    var isIOSEnable: Boolean
    var isHarmonyEnable: Boolean
    var selectedNetworkLibrary: CMPNetworkLibrary
    var isKoinEnable: Boolean
    var isNavigationEnable: Boolean
    var screens: List<String>
    var packageName: String
}
```

---

## 7. 类型层次结构

```
FileGenerator (抽象类)
    │
    ├─ CommonFileGenerator
    │   └─ generate() → List<GeneratorAsset>
    │
    ├─ AndroidFileGenerator
    │   └─ generate() → List<GeneratorAsset>
    │
    ├─ IOSFileGenerator
    │   └─ generate() → List<GeneratorAsset>
    │
    └─ HarmonyFileGenerator
        └─ generate() → List<GeneratorAsset>

GeneratorAsset (接口/抽象类)
    │
    ├─ GeneratorTemplateFile
    │   ├─ relativePath: String
    │   └─ template: FileTemplate
    │
    └─ GeneratorEmptyDirectory
        └─ relativePath: String
```

---

## 8. 数据大小估算

### 典型场景: Android + iOS + Koin + Navigation + 2 Screens

```
platforms: List<FileGenerator>
    └─ 3 个 generator (Common, Android, iOS)

assets: List<GeneratorAsset>
    ├─ CommonFileGenerator: ~20 个
    │   ├─ 基础文件: 9 个
    │   ├─ 架构文件: 9 个
    │   └─ 屏幕文件: 6 个 (2 屏幕 × 3 文件)
    │
    ├─ AndroidFileGenerator: ~4 个
    │   ├─ MainActivity.kt
    │   ├─ MainApp.kt (Koin)
    │   ├─ AndroidManifest.xml
    │   └─ strings.xml
    │
    └─ IOSFileGenerator: ~11 个
        ├─ MainViewController.kt
        ├─ ContentView.swift
        ├─ iOSApp.swift
        └─ ... 其他 iOS 文件

总计: ~35 个 assets
```

---

## 9. 常见问题

### Q1: 为什么使用 VirtualFile 而不是 File？

**A**: 
- `VirtualFile` 与 IDE 集成
- 支持文件监听和缓存
- 跨平台兼容性更好
- 自动处理文件系统事件

### Q2: flatMap 的作用是什么？

**A**: 
```kotlin
// 不使用 flatMap
val allAssets = mutableListOf<GeneratorAsset>()
platforms.forEach { generator ->
    allAssets.addAll(generator.generate(...))
}

// 使用 flatMap (更简洁)
val allAssets = platforms.flatMap { it.generate(...) }
```

### Q3: listOfNotNull 的作用是什么？

**A**: 自动过滤 null 值

```kotlin
// 不使用 listOfNotNull
val platforms = mutableListOf<FileGenerator>()
platforms.add(CommonFileGenerator(...))
if (config.isAndroidEnable) {
    platforms.add(AndroidFileGenerator(...))
}
// ...

// 使用 listOfNotNull (更简洁)
val platforms = listOfNotNull(
    CommonFileGenerator(...),
    if (config.isAndroidEnable) AndroidFileGenerator(...) else null,
    // ...
)
```

### Q4: apply 块中的 this 指向什么？

**A**: 指向 `VirtualFile` (项目根目录)

```kotlin
projectData.rootDir.toVirtualFile()?.apply {
    // this: VirtualFile
    println(this.path)  // 输出: /Users/xxx/Projects/MyProject
}
```

---

## 10. 总结

### 核心数据结构

1. **VirtualFile**: 项目根目录的虚拟文件对象
2. **platforms**: FileGenerator 列表 (3-4 个)
3. **assets**: GeneratorAsset 列表 (25-40 个)

### 数据流

```
platforms (List<FileGenerator>)
    ↓ flatMap { it.generate() }
assets (List<GeneratorAsset>)
    ↓ forEach { generate file }
文件系统
```

### 关键点

- ✅ `apply` 块提供了作用域，`this` 指向 `VirtualFile`
- ✅ `platforms` 根据用户选择动态创建
- ✅ `assets` 是所有要生成的文件的描述
- ✅ 每个 `asset` 包含路径和模板信息

这个设计模式清晰、可扩展，易于添加新平台或新文件类型。
