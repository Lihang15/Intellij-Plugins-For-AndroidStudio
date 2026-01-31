# Wizard 架构分析文档

## 概述

这是对当前 KMP (Kotlin Multiplatform) 项目向导 (Wizard) 的完整架构分析，包括代码调用路径、核心组件和工作流程。

---

## 1. 整体架构

### 1.1 入口点

**文件**: `AndroidStudioTemplateProvider.kt`
- **作用**: 作为 Android Studio/IntelliJ IDEA 的扩展点
- **注册位置**: `plugin.xml` 中通过 `<wizardTemplateProvider>` 注册
- **核心方法**: `getTemplates()` 返回可用的项目模板列表

```kotlin
class AndroidStudioTemplateProvider : WizardTemplateProvider() {
    override fun getTemplates(): List<Template> = listOf(composeMultiplatformTemplate)
}
```

### 1.2 模板定义

**文件**: `KMPTemplate.kt`
- **作用**: 定义项目向导的 UI 界面和配置参数
- **核心内容**:
  - 向导基本信息（名称、描述、分类）
  - 用户可配置的参数（平台选择、库选择等）
  - UI 组件（复选框、下拉框、文本框）
  - Recipe 回调（实际生成项目的逻辑）

---

## 2. 核心组件详解

### 2.1 配置参数 (KMPTemplate.kt)

向导提供以下可配置选项：

| 参数名 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `isAndroidEnable` | Boolean | true | 是否启用 Android 平台 |
| `isIosEnable` | Boolean | true | 是否启用 iOS 平台 |
| `isHarmonyEnable` | Boolean | false | 是否启用 Desktop 平台 |
| `selectedNetworkLibrary` | Enum | None | 网络库选择 (Ktor/Ktorfit/None) |
| `selectedImageLibrary` | Enum | None | 图片库选择 (Coil/Kamel/None) |
| `isKoinEnable` | Boolean | false | 是否启用 Koin 依赖注入 |
| `isNavigationEnable` | Boolean | false | 是否启用 Navigation |
| `isDataDomainDiUiEnable` | Boolean | false | 是否启用分层架构 |
| `screens` | String | "" | 要创建的屏幕列表 |

### 2.2 数据模型 (KMPConfigModel.kt)

**作用**: 存储用户选择的配置
- 继承自 `WizardModel`
- 使用 Compose 的 `mutableStateOf` 实现响应式
- 包含所有配置参数的状态

### 2.3 Recipe (composeMultiplatformProjectRecipe.kt)

**作用**: 项目生成的核心逻辑

**主要步骤**:
1. **解析用户输入**: 处理屏幕列表、包名等
2. **生成代码片段**: 根据配置生成导航代码、ViewModel 注册等
3. **构建配置对象**: 创建 `KMPConfigModel` 实例
4. **准备数据模型**: 创建模板变量映射 (dataModel)
5. **选择文件生成器**: 根据平台选择创建对应的生成器
6. **生成文件**: 调用生成器创建项目文件
7. **刷新 VFS**: 通知 IDE 文件系统变化

---

## 3. 文件生成系统

### 3.1 生成器架构

**基类**: `FileGenerator` (抽象类)
```kotlin
abstract class FileGenerator(protected val params: KMPConfigModel) {
    abstract fun generate(ftManager: FileTemplateManager, packageName: String): List<GeneratorAsset>
}
```

**实现类**:
- `CommonFileGenerator`: 生成通用文件（Gradle 配置、共享代码等）
- `AndroidFileGenerator`: 生成 Android 特定文件
- `IOSFileGenerator`: 生成 iOS 特定文件
- `HarmonyFileGenerator`: 生成 Desktop 特定文件

### 3.2 CommonFileGenerator 详解

**核心职责**:
1. 生成项目根目录文件（build.gradle.kts, settings.gradle.kts 等）
2. 生成 composeApp 模块文件
3. 根据配置生成架构文件（MVI、Repository、ViewModel 等）
4. 为每个屏幕生成对应的文件

**生成的文件类型**:
- **必需文件**: Gradle 配置、App.kt、资源文件
- **条件文件**: 
  - 启用分层架构时: Screen、ViewModel、Contract、Repository 等
  - 启用 Koin 时: AppModule.kt
  - 启用网络库时: Service.kt
  - 启用导航时: NavigationGraph.kt

### 3.3 模板系统

**模板位置**: `src/main/resources/fileTemplates/code/`

**模板引擎**: FreeMarker
- 使用 `.ft` 扩展名
- 支持变量替换、条件判断、循环等

**模板常量**: `Template.kt` 定义所有模板文件名

---

## 4. 完整调用流程

```
用户点击 "New Project"
    ↓
IDE 调用 AndroidStudioTemplateProvider.getTemplates()
    ↓
返回 composeMultiplatformTemplate (KMPTemplate.kt)
    ↓
IDE 显示向导 UI（根据 widgets 定义）
    ↓
用户填写配置并点击 "Finish"
    ↓
调用 recipe 回调 (composeMultiplatformProjectRecipe)
    ↓
1. 解析用户输入
   - 处理屏幕列表
   - 生成导航代码片段
   - 生成 ViewModel 注册代码
    ↓
2. 创建 KMPConfigModel
   - 存储所有配置参数
    ↓
3. 构建 dataModel (Map<String, Any>)
   - 包含所有模板变量
   - 版本号、包名、生成的代码片段等
    ↓
4. 创建文件生成器列表
   - CommonFileGenerator (必需)
   - AndroidFileGenerator (条件)
   - IOSFileGenerator (条件)
   - HarmonyFileGenerator (条件)
    ↓
5. 调用每个生成器的 generate() 方法
   - 返回 GeneratorAsset 列表
    ↓
6. 遍历 assets 生成文件
   - GeneratorEmptyDirectory → 创建空目录
   - GeneratorTemplateFile → 从模板生成文件
    ↓
7. 使用 Utils.generateFileFromTemplate()
   - 加载 FreeMarker 模板
   - 替换变量
   - 写入文件
    ↓
8. 刷新 VFS (Virtual File System)
   - 通知 IDE 文件系统变化
    ↓
9. 显示完成通知
```

---

## 5. 关键工具类

### 5.1 Utils.kt

**核心方法**:

1. **createEmptyDirectory**: 创建空目录
2. **generateFileFromTemplate**: 从模板生成文件
   - 使用 FreeMarker 处理模板
   - 创建目录结构
   - 写入文件内容
3. **showInfo**: 显示通知消息

### 5.2 ProjectGenerationHelper.kt

**作用**: 提供文件冲突解决和 VFS 刷新优化

**核心功能**:
- `copyFileWithConflictResolution`: 带冲突检测的文件复制
- `copyDirectoryWithConflictResolution`: 递归目录复制
- `writeFileWithConflictResolution`: 带合并功能的文件写入
- `flushVfsRefresh`: 批量刷新 VFS

---

## 6. 数据流

```
用户输入
    ↓
KMPTemplate 参数
    ↓
KMPConfigModel (配置对象)
    ↓
dataModel (模板变量 Map)
    ↓
FileGenerator.generate() → List<GeneratorAsset>
    ↓
Utils.generateFileFromTemplate()
    ↓
FreeMarker 模板处理
    ↓
文件系统
```

---

## 7. 扩展点分析

### 7.1 添加新平台

1. 创建新的 `FileGenerator` 实现类
2. 在 `composeMultiplatformProjectRecipe` 中添加条件判断
3. 创建对应的模板文件

### 7.2 添加新库支持

1. 在 `KMPTemplate.kt` 中添加新参数
2. 在 `KMPConfigModel.kt` 中添加状态字段
3. 在 `dataModel` 中添加对应变量
4. 在 `CommonFileGenerator` 中添加条件生成逻辑
5. 创建对应的模板文件

### 7.3 修改模板

**位置**: `src/main/resources/fileTemplates/code/`

**步骤**:
1. 找到对应的 `.ft` 文件
2. 修改 FreeMarker 模板内容
3. 确保使用的变量在 `dataModel` 中定义
4. 测试生成结果

---

## 8. 重要注意事项

### 8.1 线程安全

- 文件 I/O 操作应在后台线程执行
- VFS 刷新必须在 EDT (Event Dispatch Thread) 执行
- `ProjectGenerationHelper` 提供线程安全检查

### 8.2 冲突处理

- 使用 `FileConflictResolver` 处理文件冲突
- `settings.gradle.kts` 支持内容合并
- 其他文件默认跳过已存在的文件

### 8.3 性能优化

- 批量 VFS 刷新（避免每个文件单独刷新）
- 异步刷新（不阻塞 EDT）
- 使用 `VfsRefreshQueue` 队列化刷新操作

---

## 9. 版本管理

**文件**: `gradle/Versions.kt`
- 从远程获取最新版本号
- 在 `KMPTemplate.kt` 中通过 `runBlocking` 预加载
- 存储在 `Versions.versionList` Map 中

---

## 10. 分析服务

**文件**: `AnalyticsService.kt`
- 跟踪项目创建事件
- 用于统计和分析

---

## 11. Finish 按钮点击后的流程

### 11.1 完整调用链

```
用户点击 Finish
    ↓
[Android Studio 框架层 - 我们无法控制]
    ↓
1. 验证用户输入
2. 创建项目目录
3. 显示进度对话框 "Creating Project..."
    ↓
4. 在后台线程执行 Recipe
    ↓
[我们的代码 - 可以控制]
    ↓
5. composeMultiplatformProjectRecipe() 执行
   - 解析用户输入
   - 生成所有文件
   - 刷新 VFS
   - 显示完成通知
    ↓
[返回框架层]
    ↓
6. 关闭进度对话框
7. 打开新项目窗口
8. 触发 Gradle Sync
9. 显示项目结构
```

### 11.2 我们能控制的部分

**Recipe 执行逻辑** (`composeMultiplatformProjectRecipe.kt`):
- 文件生成逻辑
- VFS 刷新
- 自定义通知显示

**自定义通知** (`Utils.kt`):
```kotlin
Utils.showInfo(
    title = "Quick Project Wizard",
    message = "Your project is ready! 🚀"
)
```

**通知配置** (`plugin.xml`):
```xml
<notificationGroup id="QuickProjectWizard" displayType="BALLOON"/>
```

### 11.3 框架控制的部分（无法直接控制）

- **进度对话框**: 显示 "Creating Project..." 的模态对话框
- **新窗口打开**: 创建新的 IDE 窗口并加载项目
- **Gradle Sync**: 自动检测并触发 Gradle 同步

### 11.4 监听项目打开

如果需要在项目打开后执行自定义逻辑，可以使用 `postStartupActivity`:

```kotlin
class MyProjectActivity : StartupActivity {
    override fun runActivity(project: Project) {
        // 项目打开后执行
    }
}
```

注册在 `plugin.xml`:
```xml
<extensions defaultExtensionNs="com.intellij">
    <postStartupActivity implementation="com.example.MyProjectActivity"/>
</extensions>
```

---

## 总结

整个 Wizard 系统采用了清晰的分层架构：

1. **表现层**: KMPTemplate (UI 定义)
2. **控制层**: Recipe (业务逻辑)
3. **生成层**: FileGenerator (文件生成)
4. **工具层**: Utils, ProjectGenerationHelper (基础设施)
5. **框架层**: Android Studio (进度显示、窗口管理)

**关键理解**:
- 点击 Finish 后，大部分流程由 Android Studio 框架控制
- 我们只能控制 Recipe 执行期间的逻辑
- 进度对话框和新窗口打开由框架自动处理
- 可以通过 `postStartupActivity` 监听项目打开事件

这种设计使得系统易于扩展和维护，添加新功能只需在对应层次添加代码即可。
