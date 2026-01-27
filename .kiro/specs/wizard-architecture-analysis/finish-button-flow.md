# Finish 按钮点击后的完整流程

## 概述

当用户在 New Project Wizard 中点击 **Finish** 按钮后，会触发一系列由 **Android Studio/IntelliJ IDEA 框架**控制的流程，包括项目生成、进度显示、新窗口打开等。

---

## 1. 完整调用链

### 1.1 框架层调用（Android Studio 内部）

```
用户点击 Finish 按钮
    ↓
[Android Studio 框架层]
    ↓
1. 验证用户输入
    ↓
2. 创建项目目录
    ↓
3. 显示进度对话框 "Creating Project..."
    ↓
4. 在后台线程执行 Recipe
    ↓
5. 调用我们的 recipe 回调
    ↓
[我们的代码]
composeMultiplatformProjectRecipe()
    ↓
6. 生成所有项目文件
    ↓
7. 刷新 VFS
    ↓
8. 显示完成通知
    ↓
[返回框架层]
    ↓
9. 关闭进度对话框
    ↓
10. 打开新项目窗口
    ↓
11. 触发 Gradle Sync
    ↓
12. 显示项目结构
```

---

## 2. 关键组件详解

### 2.1 Recipe 回调（我们的代码）

**位置**: `KMPTemplate.kt`

```kotlin
recipe = { data: TemplateData ->
    composeMultiplatformProjectRecipe(
        moduleData = data as ModuleTemplateData,
        packageName = data.packageName,
        isAndroidEnable = isAndroidEnable.value,
        // ... 其他参数
    )
}
```

**说明**:
- 这是我们唯一能控制的部分
- 在后台线程中执行（由框架管理）
- 执行期间会显示进度对话框

### 2.2 进度对话框（框架层）

**显示时机**: Recipe 开始执行前
**关闭时机**: Recipe 执行完成后

**特点**:
- 由 Android Studio 框架自动管理
- 显示 "Creating Project..." 或类似文本
- 可能显示进度条（取决于框架版本）
- 用户无法取消（模态对话框）

**我们无法直接控制这个对话框**，但可以通过以下方式间接影响：
- 在 Recipe 中添加日志输出
- 使用 `ProgressManager` API（如果需要更细粒度的进度）

### 2.3 完成通知（我们的代码）

**位置**: `composeMultiplatformProjectRecipe.kt`

```kotlin
Utils.showInfo(
    title = "Quick Project Wizard",
    message = "Your project is ready! 🚀 If you like the plugin, please comment and rate it on the plugin page. 🙏",
)
```

**配置**: `plugin.xml`

```xml
<notificationGroup id="QuickProjectWizard" displayType="BALLOON"/>
```

**说明**:
- 这是我们自己添加的通知
- 在 Recipe 执行完成后显示
- 使用 IntelliJ Platform 的通知系统
- 显示为气球提示（BALLOON）

### 2.4 新窗口打开（框架层）

**触发时机**: Recipe 执行完成后
**执行者**: Android Studio 框架

**流程**:
1. 框架检测到项目创建完成
2. 调用 `ProjectManager.getInstance().openProject(projectPath)`
3. 创建新的 IDE 窗口
4. 加载项目结构
5. 触发 Gradle Sync
6. 显示项目文件树

**我们无法直接控制这个过程**，但可以通过以下方式监听：
- 实现 `ProjectManagerListener`
- 使用 `postStartupActivity`

---

## 3. 时序图

```
用户                 框架                Recipe              通知系统           ProjectManager
 │                    │                    │                    │                    │
 │  点击 Finish        │                    │                    │                    │
 ├───────────────────>│                    │                    │                    │
 │                    │                    │                    │                    │
 │                    │  验证输入           │                    │                    │
 │                    ├─────────┐          │                    │                    │
 │                    │         │          │                    │                    │
 │                    │<────────┘          │                    │                    │
 │                    │                    │                    │                    │
 │                    │  创建项目目录       │                    │                    │
 │                    ├─────────┐          │                    │                    │
 │                    │         │          │                    │                    │
 │                    │<────────┘          │                    │                    │
 │                    │                    │                    │                    │
 │  <显示进度对话框>   │                    │                    │                    │
 │<───────────────────┤                    │                    │                    │
 │  "Creating..."     │                    │                    │                    │
 │                    │                    │                    │                    │
 │                    │  执行 Recipe        │                    │                    │
 │                    ├───────────────────>│                    │                    │
 │                    │                    │                    │                    │
 │                    │                    │  生成文件           │                    │
 │                    │                    ├─────────┐          │                    │
 │                    │                    │         │          │                    │
 │                    │                    │<────────┘          │                    │
 │                    │                    │                    │                    │
 │                    │                    │  刷新 VFS           │                    │
 │                    │                    ├─────────┐          │                    │
 │                    │                    │         │          │                    │
 │                    │                    │<────────┘          │                    │
 │                    │                    │                    │                    │
 │                    │                    │  showInfo()        │                    │
 │                    │                    ├───────────────────>│                    │
 │                    │                    │                    │                    │
 │                    │                    │                    │  显示通知           │
 │                    │                    │                    ├─────────┐          │
 │  <显示气球通知>     │                    │                    │         │          │
 │<───────────────────┼────────────────────┼────────────────────┤<────────┘          │
 │  "Project ready!"  │                    │                    │                    │
 │                    │                    │                    │                    │
 │                    │  Recipe 完成        │                    │                    │
 │                    │<───────────────────┤                    │                    │
 │                    │                    │                    │                    │
 │  <关闭进度对话框>   │                    │                    │                    │
 │<───────────────────┤                    │                    │                    │
 │                    │                    │                    │                    │
 │                    │  openProject()     │                    │                    │
 │                    ├───────────────────────────────────────────────────────────>│
 │                    │                    │                    │                    │
 │                    │                    │                    │                    │  创建新窗口
 │                    │                    │                    │                    ├─────────┐
 │                    │                    │                    │                    │         │
 │  <新窗口打开>       │                    │                    │                    │<────────┘
 │<───────────────────┼────────────────────┼────────────────────┼────────────────────┤
 │                    │                    │                    │                    │
 │                    │                    │                    │                    │  加载项目
 │                    │                    │                    │                    ├─────────┐
 │                    │                    │                    │                    │         │
 │                    │                    │                    │                    │<────────┘
 │                    │                    │                    │                    │
 │                    │                    │                    │                    │  Gradle Sync
 │                    │                    │                    │                    ├─────────┐
 │                    │                    │                    │                    │         │
 │  <显示项目结构>     │                    │                    │                    │<────────┘
 │<───────────────────┼────────────────────┼────────────────────┼────────────────────┤
 │                    │                    │                    │                    │
```

---

## 4. 我们能控制的部分

### 4.1 Recipe 执行逻辑

**文件**: `composeMultiplatformProjectRecipe.kt`

```kotlin
fun composeMultiplatformProjectRecipe(...) {
    // 1. 解析用户输入
    val screenList = screens.split(",")...
    
    // 2. 创建配置对象
    val config = CMPConfigModel().apply { ... }
    
    // 3. 构建数据模型
    val dataModel = mutableMapOf(...)
    
    // 4. 生成文件
    projectData.rootDir.toVirtualFile()?.apply {
        val assets = platforms.flatMap { it.generate(...) }
        assets.forEach { asset ->
            // 生成文件
        }
        
        // 5. 刷新 VFS
        generationHelper.flushVfsRefreshSync(this)
    }
    
    // 6. 发送分析事件
    analyticsService.track("compose_multiplatform_project_created")
    
    // 7. 显示完成通知
    Utils.showInfo(
        title = "Quick Project Wizard",
        message = "Your project is ready! 🚀"
    )
}
```

### 4.2 自定义通知

**文件**: `Utils.kt`

```kotlin
fun showInfo(title: String? = null, message: String, type: NotificationType = NotificationType.INFORMATION) {
    val notification = NotificationGroupManager.getInstance()
        .getNotificationGroup("QuickProjectWizard")
        .createNotification(
            title = title ?: "Quick Project Wizard",
            content = message,
            type = type,
        )
    notification.notify(null)
}
```

**配置**: `plugin.xml`

```xml
<notificationGroup id="QuickProjectWizard" displayType="BALLOON"/>
```

**通知类型**:
- `BALLOON`: 气球提示（默认）
- `STICKY_BALLOON`: 粘性气球（需要手动关闭）
- `TOOL_WINDOW`: 工具窗口
- `NONE`: 不显示

---

## 5. 我们无法直接控制的部分

### 5.1 进度对话框

**由框架管理**:
- 显示时机
- 对话框样式
- 进度文本
- 关闭时机

**间接影响方式**:
```kotlin
// 使用 ProgressManager API（可选）
ProgressManager.getInstance().runProcessWithProgressSynchronously({
    // 执行耗时操作
    ProgressManager.getInstance().progressIndicator.text = "Generating files..."
}, "Creating Project", false, null)
```

### 5.2 新窗口打开

**由框架管理**:
- 窗口创建
- 项目加载
- Gradle Sync
- UI 初始化

**监听方式**:
```kotlin
// 实现 ProjectManagerListener
class MyProjectManagerListener : ProjectManagerListener {
    override fun projectOpened(project: Project) {
        // 项目打开后的逻辑
    }
}
```

**注册**: `plugin.xml`
```xml
<applicationListeners>
    <listener class="com.example.MyProjectManagerListener"
              topic="com.intellij.openapi.project.ProjectManagerListener"/>
</applicationListeners>
```

### 5.3 Gradle Sync

**由框架自动触发**:
- 检测到 `build.gradle.kts` 文件
- 自动启动 Gradle Sync
- 显示 Sync 进度

**我们无需手动触发**，但可以监听：
```kotlin
// 使用 GradleSyncListener（如果需要）
```

---

## 6. 调试技巧

### 6.1 查看 Recipe 执行日志

在 Recipe 中添加日志：

```kotlin
fun composeMultiplatformProjectRecipe(...) {
    val logger = thisLogger()
    logger.info("Starting project generation...")
    
    // 生成文件
    assets.forEach { asset ->
        logger.info("Generating: ${asset.relativePath}")
        // ...
    }
    
    logger.info("Project generation complete")
}
```

### 6.2 查看框架日志

**位置**: `Help → Show Log in Finder/Explorer`

**搜索关键词**:
- "Creating project"
- "Opening project"
- "Template execution"
- "Recipe"

### 6.3 断点调试

在 Recipe 中设置断点：

```kotlin
fun composeMultiplatformProjectRecipe(...) {
    // 设置断点在这里
    val screenList = screens.split(",")
    
    // 或这里
    assets.forEach { asset ->
        // 断点
        Utils.generateFileFromTemplate(...)
    }
}
```

---

## 7. 常见问题

### Q1: 如何自定义进度对话框的文本？

**A**: 无法直接自定义框架的进度对话框，但可以使用 `ProgressManager` API：

```kotlin
fun composeMultiplatformProjectRecipe(...) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously({
        val indicator = ProgressManager.getInstance().progressIndicator
        
        indicator.text = "Generating project structure..."
        // 生成基础文件
        
        indicator.text = "Creating screens..."
        // 生成屏幕文件
        
        indicator.text = "Finalizing..."
        // 完成
    }, "Creating KMP Project", false, null)
}
```

### Q2: 如何在新窗口打开后执行自定义逻辑？

**A**: 使用 `postStartupActivity`：

```kotlin
class MyProjectActivity : StartupActivity {
    override fun runActivity(project: Project) {
        // 项目打开后执行
        println("Project opened: ${project.name}")
    }
}
```

**注册**: `plugin.xml`
```xml
<extensions defaultExtensionNs="com.intellij">
    <postStartupActivity implementation="com.example.MyProjectActivity"/>
</extensions>
```

### Q3: 如何显示更详细的进度信息？

**A**: 使用 `ProgressIndicator`：

```kotlin
fun composeMultiplatformProjectRecipe(...) {
    val indicator = ProgressManager.getInstance().progressIndicator
    
    val totalFiles = assets.size
    assets.forEachIndexed { index, asset ->
        indicator?.fraction = (index + 1).toDouble() / totalFiles
        indicator?.text = "Generating ${asset.relativePath}"
        
        Utils.generateFileFromTemplate(...)
    }
}
```

### Q4: 通知显示的时机可以控制吗？

**A**: 可以，通过调用 `Utils.showInfo()` 的位置控制：

```kotlin
fun composeMultiplatformProjectRecipe(...) {
    // 生成文件...
    
    // 在 Recipe 执行完成前显示
    Utils.showInfo(
        title = "Quick Project Wizard",
        message = "Your project is ready! 🚀"
    )
    
    // 或者延迟显示
    ApplicationManager.getApplication().invokeLater {
        Utils.showInfo(...)
    }
}
```

---

## 8. 完整流程总结

### 8.1 框架层（我们无法控制）

1. **验证输入**: 检查项目名、路径等
2. **创建目录**: 创建项目根目录
3. **显示进度**: 显示 "Creating Project..." 对话框
4. **执行 Recipe**: 调用我们的 recipe 回调
5. **关闭进度**: Recipe 完成后关闭对话框
6. **打开项目**: 创建新窗口并加载项目
7. **Gradle Sync**: 自动触发 Gradle 同步

### 8.2 我们的代码（可以控制）

1. **解析输入**: 处理用户配置
2. **生成文件**: 使用 FileGenerator 和模板
3. **刷新 VFS**: 通知 IDE 文件系统变化
4. **显示通知**: 显示自定义的完成通知
5. **发送分析**: 跟踪项目创建事件

### 8.3 关键 API

| API | 用途 | 位置 |
|-----|------|------|
| `recipe = { ... }` | Recipe 回调 | KMPTemplate.kt |
| `Utils.showInfo()` | 显示通知 | Utils.kt |
| `ProgressManager` | 进度管理 | IntelliJ Platform |
| `ProjectManager` | 项目管理 | IntelliJ Platform |
| `NotificationGroupManager` | 通知管理 | IntelliJ Platform |
| `VfsUtil` | 文件系统 | IntelliJ Platform |

---

## 9. 扩展建议

### 9.1 添加详细进度

```kotlin
fun composeMultiplatformProjectRecipe(...) {
    val indicator = ProgressManager.getInstance().progressIndicator
    
    indicator?.text = "Preparing project structure..."
    indicator?.fraction = 0.1
    
    // 生成基础文件
    indicator?.text = "Generating Gradle files..."
    indicator?.fraction = 0.3
    
    // 生成代码文件
    indicator?.text = "Creating source files..."
    indicator?.fraction = 0.6
    
    // 完成
    indicator?.text = "Finalizing project..."
    indicator?.fraction = 1.0
}
```

### 9.2 添加错误处理

```kotlin
fun composeMultiplatformProjectRecipe(...) {
    try {
        // 生成文件...
        
        Utils.showInfo(
            title = "Success",
            message = "Project created successfully!",
            type = NotificationType.INFORMATION
        )
    } catch (e: Exception) {
        Utils.showInfo(
            title = "Error",
            message = "Failed to create project: ${e.message}",
            type = NotificationType.ERROR
        )
    }
}
```

### 9.3 添加项目打开后的初始化

```kotlin
class MyProjectActivity : StartupActivity {
    override fun runActivity(project: Project) {
        // 检查是否是我们创建的项目
        val isOurProject = project.basePath?.let { path ->
            File(path, ".kmp-wizard-marker").exists()
        } ?: false
        
        if (isOurProject) {
            // 执行初始化逻辑
            println("KMP project opened!")
            
            // 显示欢迎消息
            Utils.showInfo(
                title = "Welcome",
                message = "Welcome to your new KMP project!"
            )
        }
    }
}
```

---

## 总结

点击 Finish 按钮后的流程主要由 **Android Studio 框架**控制，我们只能控制 **Recipe 执行**和**自定义通知**部分：

**我们能控制的**:
- Recipe 中的文件生成逻辑
- 自定义通知的显示
- 项目打开后的初始化（通过 postStartupActivity）

**框架控制的**:
- 进度对话框的显示和关闭
- 新窗口的创建和打开
- Gradle Sync 的触发

理解这个边界很重要，可以帮助我们更好地设计和调试 Wizard 功能。
