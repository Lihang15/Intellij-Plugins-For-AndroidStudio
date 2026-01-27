# 模板修改指南

## 概述

本文档详细说明如何修改 Wizard 的模板系统，包括模板文件的位置、语法、变量使用和修改流程。

---

## 1. 模板系统架构

### 1.1 模板引擎

**使用**: FreeMarker 2.3.33
**文档**: https://freemarker.apache.org/

### 1.2 模板位置

```
src/main/resources/fileTemplates/code/
├── android_main_activity.kt.ft
├── android_manifest.xml.ft
├── app_module.kt.ft
├── common_app.kt.ft
├── compose_screen.kt.ft
├── compose_view_model.kt.ft
├── contract.kt.ft
├── desktop_main.kt.ft
├── gradle.properties.ft
├── libs.versions.toml.ft
├── mvi.kt.ft
├── mvi_delegate.kt.ft
├── navigation_graph.kt.ft
├── navigation_screens.kt.ft
├── project_build.gradle.kts.ft
├── repository.kt.ft
├── repository_impl.kt.ft
├── service.kt.ft
├── settings.gradle.kts.ft
└── ... (共 44 个模板文件)
```

### 1.3 模板常量定义

**文件**: `wizard/projectwizard/cmparch/Template.kt`

```kotlin
object Template {
    const val ANDROID_MAIN_ACTIVITY = "android_main_activity.kt"
    const val COMMON_APP = "common_app.kt"
    const val COMPOSE_SCREEN = "compose_screen.kt"
    const val COMPOSE_VIEW_MODEL = "compose_view_model.kt"
    const val CONTRACT = "contract.kt"
    // ... 更多常量
}
```

**作用**: 
- 避免硬编码字符串
- 提供类型安全的模板引用
- 便于重构和查找引用

---

## 2. 模板文件命名规则

### 2.1 命名格式

```
{描述性名称}.{目标文件扩展名}.ft
```

**示例**:
- `common_app.kt.ft` → 生成 `App.kt`
- `android_manifest.xml.ft` → 生成 `AndroidManifest.xml`
- `project_build.gradle.kts.ft` → 生成 `build.gradle.kts`
- `libs.versions.toml.ft` → 生成 `libs.versions.toml`

### 2.2 常量与文件名对应

```kotlin
// Template.kt 中的常量
const val COMMON_APP = "common_app.kt"

// 对应的模板文件
common_app.kt.ft

// 使用方式
ftManager.getCodeTemplate(Template.COMMON_APP)
```

---

## 3. FreeMarker 语法速查

### 3.1 变量替换

```freemarker
${VARIABLE_NAME}
```

**示例**:
```kotlin
package ${PACKAGE_NAME}

class ${APP_NAME}Application {
    // ...
}
```

### 3.2 条件判断

```freemarker
<#if CONDITION>
    内容
</#if>

<#if CONDITION>
    内容1
<#else>
    内容2
</#if>

<#if CONDITION1>
    内容1
<#elseif CONDITION2>
    内容2
<#else>
    内容3
</#if>
```

**示例**:
```kotlin
<#if IS_KOIN_ENABLE>
import org.koin.core.context.startKoin
</#if>

fun main() {
<#if IS_KOIN_ENABLE>
    startKoin {
        modules(appModule)
    }
<#else>
    // 不使用 Koin
</#if>
}
```

### 3.3 循环

```freemarker
<#list COLLECTION as item>
    ${item}
</#list>
```

**示例**:
```kotlin
sealed class Screen {
<#list SCREENS as screen>
    data object ${screen} : Screen()
</#list>
}
```

### 3.4 注释

```freemarker
<#-- 这是注释，不会出现在生成的文件中 -->
```

### 3.5 转义

```freemarker
${"${"}VARIABLE}  <#-- 输出: ${VARIABLE} -->
```

---

## 4. 可用变量列表

### 4.1 基础变量

| 变量名 | 类型 | 示例值 | 说明 |
|--------|------|--------|------|
| `APP_NAME` | String | "MyApp" | 应用名称 |
| `APP_NAME_LOWERCASE` | String | "myapp" | 小写应用名 |
| `PACKAGE_NAME` | String | "com.example.myapp" | 包名 |
| `MODULE_NAME` | String | "composeApp" | 模块名 |
| `PROJECT` | String | "MyApp" | 项目名 |

### 4.2 平台开关

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `IS_ANDROID_ENABLE` | Boolean | 是否启用 Android |
| `IS_IOS_ENABLE` | Boolean | 是否启用 iOS |
| `IS_DESKTOP_ENABLE` | Boolean | 是否启用 Desktop |

### 4.3 库开关

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `IS_KTOR_ENABLE` | Boolean | 是否使用 Ktor |
| `IS_KTORFIT_ENABLE` | Boolean | 是否使用 Ktorfit |
| `IS_ROOM_ENABLE` | Boolean | 是否使用 Room |
| `IS_COIL_ENABLE` | Boolean | 是否使用 Coil |
| `IS_KAMEL_ENABLE` | Boolean | 是否使用 Kamel |
| `IS_KOIN_ENABLE` | Boolean | 是否使用 Koin |
| `IS_NAVIGATION_ENABLE` | Boolean | 是否使用 Navigation |
| `IS_DATA_DOMAIN_DI_UI_ENABLE` | Boolean | 是否使用分层架构 |

### 4.4 版本号

| 变量名 | 类型 | 示例值 |
|--------|------|--------|
| `CMP_AGP` | String | "8.2.0" |
| `CMP_KOTLIN` | String | "2.0.0" |
| `CMP_MULTIPLATFORM` | String | "1.6.10" |
| `CMP_KOIN` | String | "3.5.0" |
| `CMP_KTOR` | String | "2.3.7" |
| `KTORFIT` | String | "1.10.2" |
| `CMP_NAVIGATION` | String | "2.7.0-alpha07" |
| `CMP_COIL` | String | "3.0.0-alpha06" |
| `CMP_KAMEL` | String | "0.9.4" |
| `CMP_ROOM` | String | "2.7.0-alpha01" |
| `CMP_SQLITE` | String | "2.5.0-alpha01" |

### 4.5 生成的代码片段

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `SCREENS` | String | 导航代码片段 (composable 块) |
| `SCREENS_IMPORTS` | String | Screen 和 ViewModel 的导入语句 |
| `NAVIGATION_SCREENS` | String | Screen 对象定义 |
| `START_DESTINATION` | String | 起始屏幕名称 |
| `VIEW_MODEL_IMPORTS` | String | ViewModel 导入语句 |
| `VIEW_MODEL_MODULE` | String | Koin 模块中的 ViewModel 注册 |

### 4.6 特殊变量

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `BUNDLE_ID` | String | iOS Bundle ID (占位符) |
| `TEAM_ID` | String | iOS Team ID (占位符) |
| `PROJECT_DIR` | String | 项目目录 (占位符) |
| `USER_HOME` | String | 用户主目录 (占位符) |

---

## 5. 模板示例分析

### 5.1 简单模板: desktop_main.kt.ft

```kotlin
package ${PACKAGE_NAME}

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
<#if IS_KOIN_ENABLE>
import ${PACKAGE_NAME}.di.initKoin
</#if>

fun main() = application {
<#if IS_KOIN_ENABLE>
    initKoin()
</#if>
    Window(
        onCloseRequest = ::exitApplication,
        title = "${APP_NAME}",
    ) {
        App()
    }
}
```

**说明**:
- 使用 `${PACKAGE_NAME}` 替换包名
- 使用 `<#if IS_KOIN_ENABLE>` 条件生成 Koin 相关代码
- 使用 `${APP_NAME}` 设置窗口标题

### 5.2 复杂模板: navigation_graph.kt.ft

```kotlin
package ${PACKAGE_NAME}.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
<#if IS_KOIN_ENABLE>
import org.koin.compose.viewmodel.koinViewModel
</#if>
${SCREENS_IMPORTS}

@Composable
fun NavigationGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = ${START_DESTINATION}
    ) {
${SCREENS}
    }
}
```

**说明**:
- `${SCREENS_IMPORTS}` 插入所有 Screen 和 ViewModel 的导入
- `${START_DESTINATION}` 设置起始目的地
- `${SCREENS}` 插入所有 composable 块

### 5.3 条件模板: compose_view_model.kt.ft

```kotlin
package ${PACKAGE_NAME}.ui.${SCREEN_LOWERCASE}

<#if IS_KOIN_ENABLE>
import org.koin.core.component.KoinComponent
</#if>
import ${PACKAGE_NAME}.delegation.MVI
import ${PACKAGE_NAME}.delegation.MVIDelegate

class ${SCREEN}ViewModel : MVI<${SCREEN}State, ${SCREEN}Event, ${SCREEN}Effect> by MVIDelegate(
    ${SCREEN}State()
)<#if IS_KOIN_ENABLE>, KoinComponent</#if> {
    
    fun onAction(action: ${SCREEN}Action) {
        when (action) {
            // TODO: Handle actions
        }
    }
}
```

**说明**:
- 使用 `${SCREEN}` 和 `${SCREEN_LOWERCASE}` 动态生成屏幕相关代码
- 条件实现 `KoinComponent` 接口

---

## 6. 修改模板的完整流程

### 6.1 修改现有模板

**步骤**:

1. **找到模板文件**
   ```
   src/main/resources/fileTemplates/code/{模板名}.ft
   ```

2. **编辑模板内容**
   - 使用 FreeMarker 语法
   - 确保使用的变量在 `dataModel` 中定义

3. **测试生成**
   - 运行插件
   - 创建新项目
   - 检查生成的文件

4. **调试**
   - 如果变量未定义，在 `composeMultiplatformProjectRecipe.kt` 中添加
   - 如果语法错误，检查 FreeMarker 语法

### 6.2 添加新模板

**步骤**:

1. **创建模板文件**
   ```
   src/main/resources/fileTemplates/code/my_new_template.kt.ft
   ```

2. **在 Template.kt 中添加常量**
   ```kotlin
   object Template {
       // ...
       const val MY_NEW_TEMPLATE = "my_new_template.kt"
   }
   ```

3. **在 FileGenerator 中使用**
   ```kotlin
   class CommonFileGenerator(...) : FileGenerator(params) {
       override fun generate(...): List<GeneratorAsset> {
           return listOf(
               // ...
               GeneratorTemplateFile(
                   "path/to/output/MyFile.kt",
                   ftManager.getCodeTemplate(Template.MY_NEW_TEMPLATE)
               )
           )
       }
   }
   ```

4. **添加必要的变量到 dataModel**
   ```kotlin
   val dataModel = mutableMapOf(
       // ...
       "MY_VARIABLE" to "value"
   )
   ```

5. **测试**

### 6.3 添加新变量

**步骤**:

1. **在 Recipe 中定义变量**
   ```kotlin
   fun composeMultiplatformProjectRecipe(...) {
       val myNewVariable = "some value"
       
       val dataModel = mutableMapOf(
           // ...
           "MY_NEW_VARIABLE" to myNewVariable
       )
   }
   ```

2. **在模板中使用**
   ```kotlin
   // my_template.kt.ft
   val myValue = "${MY_NEW_VARIABLE}"
   ```

3. **测试**

---

## 7. 常见模板模式

### 7.1 条件导入

```kotlin
<#if IS_KOIN_ENABLE>
import org.koin.core.context.startKoin
</#if>
<#if IS_KTOR_ENABLE>
import io.ktor.client.HttpClient
</#if>
```

### 7.2 条件代码块

```kotlin
fun initialize() {
<#if IS_KOIN_ENABLE>
    startKoin {
        modules(appModule)
    }
</#if>
<#if IS_KTOR_ENABLE>
    val client = HttpClient()
</#if>
}
```

### 7.3 平台特定代码

```kotlin
dependencies {
<#if IS_ANDROID_ENABLE>
    implementation("androidx.activity:activity-compose:${CMP_ACTIVITY_COMPOSE}")
</#if>
<#if IS_IOS_ENABLE>
    // iOS specific dependencies
</#if>
<#if IS_DESKTOP_ENABLE>
    implementation(compose.desktop.currentOs)
</#if>
}
```

### 7.4 循环生成

```kotlin
sealed class Screen {
<#list SCREENS as screen>
    data object ${screen} : Screen()
</#list>
}
```

### 7.5 嵌套条件

```kotlin
<#if IS_DATA_DOMAIN_DI_UI_ENABLE>
    <#if IS_KOIN_ENABLE>
        // 使用 Koin 的分层架构
    <#else>
        // 不使用 Koin 的分层架构
    </#if>
</#if>
```

---

## 8. 调试技巧

### 8.1 查看生成的内容

在 `Utils.generateFileFromTemplate()` 中添加日志:

```kotlin
StringWriter().use { writer ->
    val template = "${asset.template.name}.${asset.template.extension}"
    getTemplate("${template}.ft").process(dataModel, writer)
    val content = writer.toString()
    println("Generated content for ${asset.relativePath}:")
    println(content)
    VfsUtil.saveText(outputFile, content)
}
```

### 8.2 检查变量值

在 Recipe 中打印 dataModel:

```kotlin
val dataModel = mutableMapOf(...)
println("DataModel: $dataModel")
```

### 8.3 FreeMarker 错误

常见错误:
- `Variable not found`: 变量未在 dataModel 中定义
- `Syntax error`: FreeMarker 语法错误
- `Template not found`: 模板文件路径错误

---

## 9. 最佳实践

### 9.1 变量命名

- 使用大写下划线命名: `MY_VARIABLE`
- 布尔变量使用 `IS_` 前缀: `IS_KOIN_ENABLE`
- 版本号使用 `CMP_` 前缀: `CMP_KOTLIN`

### 9.2 模板组织

- 每个模板只负责一个文件
- 复杂逻辑在 Recipe 中处理，模板只负责展示
- 使用生成的代码片段变量，而不是在模板中循环

### 9.3 条件判断

- 优先使用简单的 if/else
- 避免过深的嵌套
- 复杂逻辑在 Recipe 中预处理

### 9.4 可维护性

- 在 Template.kt 中定义常量
- 添加注释说明模板用途
- 保持模板简洁易读

---

## 10. 示例: 添加新功能

### 场景: 添加 SQLDelight 支持

**步骤 1: 在 KMPTemplate.kt 中添加参数**

```kotlin
val isSQLDelightEnable = booleanParameter {
    name = "SQLDelight"
    default = false
}

widgets(
    // ...
    CheckBoxWidget(isSQLDelightEnable),
)

recipe = { data: TemplateData ->
    composeMultiplatformProjectRecipe(
        // ...
        isSQLDelightEnable = isSQLDelightEnable.value,
    )
}
```

**步骤 2: 在 CMPConfigModel.kt 中添加状态**

```kotlin
class CMPConfigModel : WizardModel() {
    // ...
    var isSQLDelightEnable: Boolean by mutableStateOf(false)
}
```

**步骤 3: 在 Recipe 中添加变量**

```kotlin
fun composeMultiplatformProjectRecipe(
    // ...
    isSQLDelightEnable: Boolean,
) {
    val config = CMPConfigModel().apply {
        // ...
        this.isSQLDelightEnable = isSQLDelightEnable
    }
    
    val dataModel = mutableMapOf(
        // ...
        "IS_SQLDELIGHT_ENABLE" to isSQLDelightEnable,
        "CMP_SQLDELIGHT" to Versions.versionList["cmp-sqldelight"].orEmpty(),
    )
}
```

**步骤 4: 创建模板文件**

```kotlin
// src/main/resources/fileTemplates/code/database.kt.ft
package ${PACKAGE_NAME}.data.database

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

class Database(driverFactory: DatabaseDriverFactory) {
    private val driver = driverFactory.createDriver()
    // ...
}
```

**步骤 5: 在 Template.kt 中添加常量**

```kotlin
object Template {
    // ...
    const val DATABASE = "database.kt"
}
```

**步骤 6: 在 CommonFileGenerator 中使用**

```kotlin
override fun generate(...): List<GeneratorAsset> {
    return list.apply {
        // ...
        if (params.isSQLDelightEnable) {
            add(
                GeneratorTemplateFile(
                    "composeApp/src/commonMain/kotlin/$packageName/data/database/Database.kt",
                    ftManager.getCodeTemplate(Template.DATABASE)
                )
            )
        }
    }
}
```

**步骤 7: 修改 build.gradle.kts 模板**

```kotlin
// compose.gradle.kts.ft
plugins {
    // ...
<#if IS_SQLDELIGHT_ENABLE>
    id("app.cash.sqldelight") version "${CMP_SQLDELIGHT}"
</#if>
}

dependencies {
<#if IS_SQLDELIGHT_ENABLE>
    implementation("app.cash.sqldelight:runtime:${CMP_SQLDELIGHT}")
</#if>
}
```

---

## 总结

模板系统是 Wizard 的核心，理解模板的工作原理对于定制和扩展功能至关重要：

1. **模板位置**: `src/main/resources/fileTemplates/code/`
2. **模板引擎**: FreeMarker 2.3.33
3. **变量来源**: `composeMultiplatformProjectRecipe` 中的 `dataModel`
4. **使用方式**: 通过 `FileGenerator` 和 `Utils.generateFileFromTemplate()`

修改模板时，记住三个关键点：
1. 模板文件 (.ft)
2. 变量定义 (dataModel)
3. 生成器使用 (FileGenerator)

这三者必须保持一致才能正确生成项目文件。
