# Wizard 目录清理总结

## 删除的未使用文件

### 1. Gradle 相关（旧的 Android 项目生成器）

#### 删除的文件
- ✅ `src/main/kotlin/wizard/projectwizard/gradle/GradleKts.kt`
- ✅ `src/main/kotlin/wizard/projectwizard/gradle/Library.kt`
- ✅ `src/main/kotlin/wizard/projectwizard/gradle/Plugin.kt`

**原因**: 这些文件是用于旧的 Android 项目生成器功能，没有被 CMP 模板使用。

### 2. Data 类

#### 删除的文件
- ✅ `src/main/kotlin/wizard/projectwizard/data/ImageLibrary.kt`
- ✅ `src/main/kotlin/wizard/projectwizard/data/NetworkLibrary.kt`

**原因**: 只被已删除的 GradleKts.kt 使用，CMP 模板不需要这些枚举。

### 3. 工具类

#### 删除的文件
- ✅ `src/main/kotlin/wizard/common/file/File.kt`

**原因**: 只被 Extensions.kt 中未使用的函数引用。

### 4. Extensions.kt 清理

#### 删除的函数
- `Project.getCurrentlySelectedFile()`
- `Project.rootDirectoryStringDropLast()`
- `Project.rootDirectoryString()`
- `List<File>.refreshFileSystem()`
- `File.toProjectFile()`
- `RecipeExecutor.addRootFile()`
- `RecipeExecutor.addSrcFile()`
- `StringBuilder.addLibsVersion()`
- `StringBuilder.addLibsDependency()`
- `StringBuilder.addLibsPlugin()`
- `StringBuilder.addGradlePlugin()`
- `StringBuilder.addGradleImplementation()`
- `StringBuilder.addGradleDetektImplementation()`
- `StringBuilder.addGradlePlatformImplementation()`
- `StringBuilder.addGradleTestImplementation()`
- `StringBuilder.addGradleAndroidTestImplementation()`
- `StringBuilder.addGradleAndroidTestPlatformImplementation()`
- `StringBuilder.addGradleDebugImplementation()`
- `StringBuilder.addKspImplementation()`
- `StringBuilder.addDetektBlock()`
- `StringBuilder.addAndroidBlock()`

**保留的函数**:
- ✅ `getImage()` - 被 KMPTemplate.kt 使用

**原因**: 这些函数只被已删除的 GradleKts.kt 使用。

---

## 保留的文件（正在使用）

### 核心模板文件
- ✅ `KMPTemplate.kt` - 模板定义
- ✅ `AndroidStudioTemplateProvider.kt` - 模板提供者
- ✅ `ProjectGenerationHelper.kt` - 项目生成辅助类

### Recipe 文件
- ✅ `composeMultiplatformProjectRecipe.kt` - 主要的项目生成逻辑

### FileGenerator 文件
- ✅ `FileGenerator.kt` - 抽象基类
- ✅ `CommonFileGenerator.kt` - 通用文件生成器
- ✅ `AndroidFileGenerator.kt` - Android 文件生成器
- ✅ `IOSFileGenerator.kt` - iOS 文件生成器
- ✅ `HarmonyFileGenerator.kt` - Desktop 文件生成器
- ✅ `Template.kt` - 模板常量定义

### Data 类
- ✅ `KMPConfigModel.kt` - 配置模型
- ✅ `QPWEvent.kt` - 分析事件模型
- ✅ `VersionModel.kt` - 版本模型

### Service 类
- ✅ `AnalyticsService.kt` - 分析服务（被 recipe 使用）

### Gradle 相关
- ✅ `Version.kt` - 版本管理（被 recipe 使用）
- ✅ `gradle/network/GetVersions.kt` - 版本获取（被 KMPTemplate 使用）

### 工具类
- ✅ `Utils.kt` - 工具函数（被 recipe 使用）
- ✅ `Extensions.kt` - 扩展函数（只保留 getImage）

---

## 当前目录结构

```
src/main/kotlin/wizard/
├── common/
│   ├── Extensions.kt          ✅ (只保留 getImage)
│   └── Utils.kt               ✅
│
└── projectwizard/
    ├── AndroidStudioTemplateProvider.kt  ✅
    ├── KMPTemplate.kt                    ✅
    ├── ProjectGenerationHelper.kt        ✅
    │
    ├── kmparch/
    │   ├── AndroidFileGenerator.kt       ✅
    │   ├── CommonFileGenerator.kt        ✅
    │   ├── HarmonyFileGenerator.kt       ✅
    │   ├── FileGenerator.kt              ✅
    │   ├── IOSFileGenerator.kt           ✅
    │   └── Template.kt                   ✅
    │
    ├── data/
    │   ├── KMPConfigModel.kt             ✅
    │   ├── QPWEvent.kt                   ✅
    │   └── VersionModel.kt               ✅
    │
    ├── gradle/
    │   ├── Version.kt                    ✅
    │   └── network/
    │       └── GetVersions.kt            ✅
    │
    ├── recipes/
    │   └── composeMultiplatformProjectRecipe.kt  ✅
    │
    └── service/
        └── AnalyticsService.kt           ✅
```

---

## 清理统计

### 删除的文件
- **Kotlin 源文件**: 6 个
- **删除的函数**: 20+ 个

### 保留的文件
- **Kotlin 源文件**: 17 个
- **保留的函数**: 1 个 (getImage)

### 代码行数减少
- **Extensions.kt**: 从 ~170 行减少到 ~15 行
- **总体减少**: ~500+ 行代码

---

## 清理效果

1. ✅ **移除了旧的 Android 项目生成器代码**
   - GradleKts.kt 及其依赖的 Library、Plugin 类
   - 相关的 StringBuilder 扩展函数

2. ✅ **移除了未使用的工具类**
   - File 接口及其相关函数
   - Project 扩展函数
   - RecipeExecutor 扩展函数

3. ✅ **移除了重复的枚举类**
   - ImageLibrary 和 NetworkLibrary（之前为了兼容性重新创建的）

4. ✅ **简化了 Extensions.kt**
   - 只保留了真正被使用的 getImage 函数
   - 删除了所有未使用的扩展函数

---

## 验证

所有删除的文件和函数都经过了搜索验证，确保：
- ❌ 没有被 CMP 模板使用
- ❌ 没有被其他活跃代码引用
- ✅ 只是旧代码的残留

现在的代码库更加精简、清晰，只包含 CMP 项目生成器真正需要的代码！🎉
