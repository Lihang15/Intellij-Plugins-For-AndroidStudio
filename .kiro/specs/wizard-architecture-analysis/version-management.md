# 版本管理系统详解

## 概述

`GetVersions.kt` 是一个**动态版本管理系统**，用于从远程 API 获取最新的库版本号，确保生成的项目使用最新的依赖版本。

---

## 核心文件

### 1. GetVersions.kt
**位置**: `src/main/kotlin/wizard/projectwizard/gradle/network/GetVersions.kt`

**作用**: 
- 从远程 API 获取最新版本号
- 如果网络失败，使用本地 Mock 版本作为后备

### 2. Version.kt
**位置**: `src/main/kotlin/wizard/projectwizard/gradle/Version.kt`

**作用**:
- 存储版本号的全局 Map
- 提供版本号的访问接口

---

## 工作流程

### 1. 初始化时机

**在 CMPTemplate.kt 中**:
```kotlin
val composeMultiplatformTemplate = template {
    name = "ProjectWizard - KMP"
    // ...
    
    runBlocking {
        try {
            getVersions()  // ← 在模板初始化时调用
        } catch (e: Exception) {
            println("Failed to fetch versions: ${e.message}")
        }
    }
    
    // ... 其他配置
}
```

**时机**: 
- 用户打开 New Project Wizard 时
- 在显示 UI 之前执行
- 异步获取版本号

---

### 2. 获取流程

```
用户打开 Wizard
    ↓
CMPTemplate 初始化
    ↓
runBlocking { getVersions() }
    ↓
尝试从远程 API 获取
    ↓
成功? ─── YES ──→ 更新 Versions.versionList
    │
    NO
    ↓
使用 Mock 版本 (本地后备)
    ↓
更新 Versions.versionList
    ↓
显示 Wizard UI
```

---

### 3. 远程 API

**URL**: `https://api.canerture.com/qpwizard/versions`

**超时**: 500ms (0.5 秒)

**返回格式**:
```json
[
  {
    "name": "cmp-kotlin",
    "value": "2.1.0"
  },
  {
    "name": "cmp-multiplatform",
    "value": "1.7.0-beta02"
  },
  // ...
]
```

**优点**:
- ✅ 始终使用最新版本
- ✅ 无需更新插件即可更新版本
- ✅ 集中管理版本号

**缺点**:
- ⚠️ 依赖网络连接
- ⚠️ 可能有延迟

---

### 4. Mock 版本 (后备方案)

**触发条件**:
- 网络请求失败
- 超时 (>500ms)
- API 返回错误

**Mock 版本列表** (GetVersions.kt):
```kotlin
private val mockVersions = listOf(
    VersionModel("cmp-agp", "8.5.2"),
    VersionModel("cmp-kotlin", "2.1.0"),
    VersionModel("cmp-activity-compose", "1.9.3"),
    VersionModel("cmp-ui-tooling", "1.7.6"),
    VersionModel("cmp-multiplatform", "1.7.0-beta02"),
    VersionModel("cmp-koin", "4.0.0"),
    VersionModel("cmp-ktor", "3.0.1"),
    VersionModel("cmp-navigation", "2.8.0-alpha08"),
    VersionModel("cmp-kotlinx-coroutines", "1.9.0"),
    VersionModel("cmp-coil", "3.0.0-alpha06"),
    VersionModel("cmp-kamel", "0.9.5"),
    VersionModel("cmp-ksp", "2.1.0-1.0.29"),
    VersionModel("cmp-room", "2.7.0-alpha08"),
    VersionModel("cmp-sqlite", "2.5.0-SNAPSHOT"),
    VersionModel("cmp-kotlinx-serialization", "1.7.3"),
    VersionModel("ktorfit", "2.6.4"),
    // ... 更多版本
)
```

**优点**:
- ✅ 离线可用
- ✅ 快速响应
- ✅ 保证可用性

---

## 版本使用位置

### 1. Recipe 中的 dataModel

**文件**: `composeMultiplatformProjectRecipe.kt`

```kotlin
val dataModel = mutableMapOf(
    // ... 其他变量
    "CMP_AGP" to Versions.versionList["cmp-agp"].orEmpty(),
    "CMP_KOTLIN" to Versions.versionList["cmp-kotlin"].orEmpty(),
    "CMP_ACTIVITY_COMPOSE" to Versions.versionList["cmp-activity-compose"].orEmpty(),
    "CMP_UI_TOOLING" to Versions.versionList["cmp-ui-tooling"].orEmpty(),
    "CMP_MULTIPLATFORM" to Versions.versionList["cmp-multiplatform"].orEmpty(),
    "CMP_KOIN" to Versions.versionList["cmp-koin"].orEmpty(),
    "CMP_KTOR" to Versions.versionList["cmp-ktor"].orEmpty(),
    "KTORFIT" to Versions.versionList["ktorfit"].orEmpty(),
    "CMP_NAVIGATION" to Versions.versionList["cmp-navigation"].orEmpty(),
    "CMP_KOTLINX_COROUTINES" to Versions.versionList["cmp-kotlinx-coroutines"].orEmpty(),
    "CMP_COIL" to Versions.versionList["cmp-coil"].orEmpty(),
    "CMP_KAMEL" to Versions.versionList["cmp-kamel"].orEmpty(),
    "CMP_KSP" to Versions.versionList["cmp-ksp"].orEmpty(),
    "CMP_ROOM" to Versions.versionList["cmp-room"].orEmpty(),
    "CMP_SQLITE" to Versions.versionList["cmp-sqlite"].orEmpty(),
    "CMP_KOTLINX_SERIALIZATION" to Versions.versionList["cmp-kotlinx-serialization"].orEmpty(),
)
```

这些变量会传递给 FreeMarker 模板。

---

### 2. 模板文件中使用

#### libs.versions.toml.ft

```toml
[versions]
agp = "${CMP_AGP}"
kotlin = "${CMP_KOTLIN}"
compose-multiplatform = "${CMP_MULTIPLATFORM}"
serialization = "${CMP_KOTLINX_SERIALIZATION}"
<#if IS_KOIN_ENABLE>
koin = "${CMP_KOIN}"
</#if>
<#if IS_KTOR_ENABLE || IS_KTORFIT_ENABLE>
ktor = "${CMP_KTOR}"
</#if>
<#if IS_KTORFIT_ENABLE>
ktorfit = "${KTORFIT}"
</#if>
<#if IS_NAVIGATION_ENABLE>
navigationCompose = "${CMP_NAVIGATION}"
</#if>
kotlinx-coroutines = "${CMP_KOTLINX_COROUTINES}"
<#if IS_COIL_ENABLE>
coil = "${CMP_COIL}"
</#if>
<#if IS_KAMEL_ENABLE>
kamel = "${CMP_KAMEL}"
</#if>
```

**生成结果** (libs.versions.toml):
```toml
[versions]
agp = "8.5.2"
kotlin = "2.1.0"
compose-multiplatform = "1.7.0-beta02"
serialization = "1.7.3"
koin = "4.0.0"
ktor = "3.0.1"
navigationCompose = "2.8.0-alpha08"
kotlinx-coroutines = "1.9.0"
coil = "3.0.0-alpha06"
```

---

## 版本号映射表

### KMP 相关版本

| 变量名 | 版本号 Key | 用途 |
|--------|-----------|------|
| `CMP_AGP` | `cmp-agp` | Android Gradle Plugin |
| `CMP_KOTLIN` | `cmp-kotlin` | Kotlin 版本 |
| `CMP_MULTIPLATFORM` | `cmp-multiplatform` | Compose Multiplatform |
| `CMP_KOIN` | `cmp-koin` | Koin 依赖注入 |
| `CMP_KTOR` | `cmp-ktor` | Ktor 网络库 |
| `KTORFIT` | `ktorfit` | Ktorfit 库 |
| `CMP_NAVIGATION` | `cmp-navigation` | Navigation Compose |
| `CMP_COIL` | `cmp-coil` | Coil 图片库 |
| `CMP_KAMEL` | `cmp-kamel` | Kamel 图片库 |
| `CMP_KSP` | `cmp-ksp` | KSP 插件 |
| `CMP_ROOM` | `cmp-room` | Room 数据库 |
| `CMP_KOTLINX_SERIALIZATION` | `cmp-kotlinx-serialization` | 序列化库 |
| `CMP_KOTLINX_COROUTINES` | `cmp-kotlinx-coroutines` | 协程库 |

---

## 数据流

```
远程 API / Mock 版本
    ↓
getVersions() 函数
    ↓
Versions.versionList (全局 Map)
    ↓
composeMultiplatformProjectRecipe
    ↓
dataModel (模板变量)
    ↓
FreeMarker 模板 (libs.versions.toml.ft)
    ↓
生成的 libs.versions.toml 文件
    ↓
Gradle 构建系统
```

---

## 调试和日志

### 查看版本获取日志

**位置**: IDE 控制台或日志文件

**成功获取**:
```
Fetching versions from remote API...
Successfully fetched 45 versions from remote API
```

**失败回退**:
```
Fetching versions from remote API...
Failed to fetch versions from remote: Connection timeout
Using mock/fallback versions instead
Loading mock versions as fallback...
Loaded 45 mock versions successfully
```

---

## 如何更新版本

### 方法 1: 更新远程 API (推荐)

**优点**: 
- ✅ 所有用户自动获取最新版本
- ✅ 无需更新插件

**步骤**:
1. 更新远程 API 的版本数据
2. 用户下次打开 Wizard 时自动获取

### 方法 2: 更新 Mock 版本

**优点**:
- ✅ 离线可用
- ✅ 保证可用性

**步骤**:
1. 修改 `GetVersions.kt` 中的 `mockVersions` 列表
2. 重新构建插件

**示例**:
```kotlin
private val mockVersions = listOf(
    VersionModel("cmp-kotlin", "2.1.0"),  // ← 更新这里
    VersionModel("cmp-multiplatform", "1.7.0-beta02"),  // ← 更新这里
    // ...
)
```

### 方法 3: 更新默认版本

**位置**: `Version.kt` 中的 `versionList`

**用途**: 作为最后的后备

**示例**:
```kotlin
object Versions {
    val versionList = mutableMapOf(
        "cmp-kotlin" to "2.1.0",  // ← 更新这里
        "cmp-multiplatform" to "1.7.0-beta02",  // ← 更新这里
        // ...
    )
}
```

---

## 优化建议

### 1. 增加超时时间

当前超时 500ms 可能太短，建议增加：

```kotlin
val client = HttpClient(CIO) {
    this.engine {
        requestTimeout = 3000  // ← 改为 3 秒
    }
}
```

### 2. 添加缓存机制

避免每次都请求网络：

```kotlin
object VersionCache {
    private var cachedVersions: List<VersionModel>? = null
    private var cacheTime: Long = 0
    private const val CACHE_DURATION = 3600_000 // 1 小时
    
    fun getCachedVersions(): List<VersionModel>? {
        if (System.currentTimeMillis() - cacheTime > CACHE_DURATION) {
            return null
        }
        return cachedVersions
    }
    
    fun setCachedVersions(versions: List<VersionModel>) {
        cachedVersions = versions
        cacheTime = System.currentTimeMillis()
    }
}
```

### 3. 添加版本验证

确保版本号格式正确：

```kotlin
private fun isValidVersion(version: String): Boolean {
    return version.matches(Regex("""\d+\.\d+\.\d+.*"""))
}
```

---

## 常见问题

### Q1: 为什么有时版本号不是最新的？

**A**: 可能原因：
1. 网络请求失败，使用了 Mock 版本
2. 远程 API 还没更新
3. 超时时间太短 (500ms)

**解决**: 
- 检查网络连接
- 查看日志确认是否成功获取
- 增加超时时间

### Q2: 如何强制使用特定版本？

**A**: 修改 `Version.kt` 中的默认值：

```kotlin
val versionList = mutableMapOf(
    "cmp-kotlin" to "2.0.0",  // ← 强制使用 2.0.0
    // ...
)
```

### Q3: Mock 版本和远程版本不一致怎么办？

**A**: 定期同步 Mock 版本：
1. 从远程 API 获取最新版本
2. 更新 `mockVersions` 列表
3. 重新构建插件

### Q4: 可以禁用远程获取吗？

**A**: 可以，注释掉 `getVersions()` 调用：

```kotlin
runBlocking {
    try {
        // getVersions()  // ← 注释掉
    } catch (e: Exception) {
        println("Failed to fetch versions: ${e.message}")
    }
}
```

这样会直接使用 `Version.kt` 中的默认版本。

---

## 总结

### 核心作用

`GetVersions.kt` 的核心作用是：
1. **动态获取最新版本号**
2. **确保生成的项目使用最新依赖**
3. **提供离线后备方案**

### 版本流向

```
远程 API → Versions.versionList → dataModel → 模板 → libs.versions.toml
```

### 优点

- ✅ 无需更新插件即可更新版本
- ✅ 集中管理版本号
- ✅ 有离线后备方案
- ✅ 自动化版本管理

### 改进空间

- ⚠️ 增加超时时间
- ⚠️ 添加缓存机制
- ⚠️ 添加版本验证
- ⚠️ 提供手动刷新选项

这个系统确保了生成的项目始终使用最新的库版本，提升了开发体验。
