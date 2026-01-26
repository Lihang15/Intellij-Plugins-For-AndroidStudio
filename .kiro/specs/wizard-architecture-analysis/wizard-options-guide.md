# Wizard 选项详细说明

## 概述

这份文档详细说明 Wizard 引导表单中每个选项的作用、生成的文件和依赖配置。

---

## 选项列表

### 1. Network Library (网络库)

**选项**: `None` | `Ktor` | `Ktorfit`

#### 作用
选择项目使用的网络请求库，用于 HTTP 请求、API 调用等。

#### None (默认)
- 不添加任何网络库
- 不生成网络相关文件
- 适合不需要网络请求的项目

#### Ktor
**用途**: 使用 Ktor Client 进行网络请求

**生成的文件**:
```
composeApp/src/commonMain/kotlin/{package}/
└── data/
    └── source/
        └── remote/
            └── MainService.kt  ← Ktor 版本的网络服务
```

**添加的依赖** (libs.versions.toml):
```toml
[versions]
ktor = "2.3.7"

[libraries]
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }  # iOS
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }  # Android
ktor-client-engine = { module = "io.ktor:ktor-client-engine", version.ref = "ktor" }  # Desktop
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
```

**示例代码** (MainService.kt):
```kotlin
class MainService(private val httpClient: HttpClient) {
    suspend fun getData(): Result<Data> {
        return try {
            val response = httpClient.get("https://api.example.com/data")
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

#### Ktorfit
**用途**: 使用 Ktorfit (类似 Retrofit 的 Ktor 封装) 进行网络请求

**生成的文件**:
```
composeApp/src/commonMain/kotlin/{package}/
└── data/
    └── source/
        └── remote/
            └── MainService.kt  ← Ktorfit 版本的网络服务
```

**添加的依赖** (libs.versions.toml):
```toml
[versions]
ktor = "2.3.7"
ktorfit = "1.10.2"
ksp = "2.0.0-1.0.21"

[libraries]
ktorfit = { module = "de.jensklingenberg.ktorfit:ktorfit-lib", version.ref = "ktorfit" }
# + Ktor 相关依赖

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

**示例代码** (MainService.kt):
```kotlin
interface MainService {
    @GET("data")
    suspend fun getData(): Data
}
```

---

### 2. Image Library (图片库)

**选项**: `None` | `Coil` | `Kamel`

#### 作用
选择项目使用的图片加载库，用于加载网络图片、缓存等。

#### None (默认)
- 不添加任何图片库
- 使用 Compose 原生的 Image 组件
- 适合只显示本地资源的项目

#### Coil
**用途**: 使用 Coil 3 (Compose Multiplatform 版本) 加载图片

**添加的依赖** (libs.versions.toml):
```toml
[versions]
coil = "3.0.0-alpha06"

[libraries]
coil = { module = "io.coil-kt.coil3:coil", version.ref = "coil" }
```

**使用示例**:
```kotlin
AsyncImage(
    model = "https://example.com/image.jpg",
    contentDescription = "Image",
    modifier = Modifier.size(200.dp)
)
```

**特点**:
- ✅ 支持 Android、iOS、Desktop
- ✅ 自动缓存
- ✅ 支持多种图片格式
- ✅ 性能优秀

#### Kamel
**用途**: 使用 Kamel 加载图片 (另一个 KMP 图片库)

**添加的依赖** (libs.versions.toml):
```toml
[versions]
kamel = "0.9.4"

[libraries]
kamel = { module = "media.kamel:kamel-image", version.ref = "kamel" }
```

**使用示例**:
```kotlin
KamelImage(
    resource = asyncPainterResource("https://example.com/image.jpg"),
    contentDescription = "Image",
    modifier = Modifier.size(200.dp)
)
```

**特点**:
- ✅ 专为 Compose Multiplatform 设计
- ✅ 轻量级
- ✅ 支持多种图片源

---

### 3. Koin (依赖注入)

**选项**: 复选框 (默认: 未勾选)

#### 作用
启用 Koin 依赖注入框架，用于管理应用的依赖关系。

#### 未勾选
- 使用 Compose 的 `viewModel()` 函数创建 ViewModel
- 手动管理依赖

#### 勾选后

**生成的文件**:
```
composeApp/src/commonMain/kotlin/{package}/
└── di/
    └── AppModule.kt  ← Koin 模块配置

composeApp/src/androidMain/kotlin/{package}/
└── MainApp.kt  ← Android Application 类 (初始化 Koin)
```

**添加的依赖** (libs.versions.toml):
```toml
[versions]
koin = "3.5.0"
ksp = "2.0.0-1.0.21"

[libraries]
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-compose-viewmodel = { module = "io.insert-koin:koin-compose-viewmodel", version.ref = "koin" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

**生成的代码示例**:

**AppModule.kt**:
```kotlin
val dataModule = module {
    single { HttpClient { ... } }  // 如果启用了网络库
    single<MainService> { MainService(get()) }
    single<MainRepository> { MainRepositoryImpl(get()) }
}

val viewModelModule = module {
    factoryOf(::HomeViewModel)
    factoryOf(::DetailViewModel)
    // ... 其他 ViewModel
}
```

**MainApp.kt** (Android):
```kotlin
class MainApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin()
    }
}
```

**使用方式**:
```kotlin
// 在 Composable 中
val viewModel = koinViewModel<HomeViewModel>()  // 使用 Koin
// 而不是
val viewModel = viewModel { HomeViewModel() }  // 手动创建
```

**影响的文件**:
- `AndroidManifest.xml`: 添加 `android:name=".MainApp"`
- `desktop_main.kt`: 添加 `initKoin()` 调用
- `iosapp.swift`: 添加 `AppModuleKt.doInitKoin()` 调用
- `NavigationGraph.kt`: 使用 `koinViewModel()` 而不是 `viewModel()`

---

### 4. Navigation (导航)

**选项**: 复选框 (默认: 未勾选)

#### 作用
启用 Jetpack Compose Navigation，用于管理应用的页面导航。

#### 未勾选
- 不生成导航相关文件
- 需要手动管理页面切换

#### 勾选后

**生成的文件**:
```
composeApp/src/commonMain/kotlin/{package}/
└── navigation/
    ├── Screen.kt           ← 定义所有屏幕
    └── NavigationGraph.kt  ← 导航图配置
```

**添加的依赖** (libs.versions.toml):
```toml
[versions]
navigationCompose = "2.7.0-alpha07"

[libraries]
navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
```

**生成的代码示例**:

**Screen.kt**:
```kotlin
sealed class Screen {
    @Serializable
    data object Home : Screen
    
    @Serializable
    data object Detail : Screen
    
    @Serializable
    data object Profile : Screen
}
```

**NavigationGraph.kt**:
```kotlin
@Composable
fun NavigationGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Home
    ) {
        composable<Home> {
            val viewModel = koinViewModel<HomeViewModel>()
            HomeScreen(...)
        }
        composable<Detail> {
            val viewModel = koinViewModel<DetailViewModel>()
            DetailScreen(...)
        }
        // ...
    }
}
```

**App.kt 的变化**:
```kotlin
@Composable
fun App() {
    val navController = rememberNavController()
    MaterialTheme {
        Scaffold { padding ->
            NavigationGraph()  // ← 使用导航图
        }
    }
}
```

---

### 5. Common-Data-Domain-DI-UI Packages (分层架构)

**选项**: 复选框 (默认: 未勾选)

#### 作用
启用完整的分层架构，包括 MVI 模式、Repository 模式、依赖注入等。

#### 未勾选
- 只生成基础的 `App.kt`
- 项目结构简单

#### 勾选后

**生成的目录结构**:
```
composeApp/src/commonMain/kotlin/{package}/
├── common/
│   ├── CollectExtension.kt  ← Flow 收集扩展
│   └── Constants.kt          ← 常量定义
├── data/
│   ├── repository/
│   │   └── MainRepositoryImpl.kt  ← Repository 实现
│   └── source/
│       └── remote/
│           └── MainService.kt  ← 网络服务 (如果启用)
├── delegation/
│   ├── MVI.kt          ← MVI 接口定义
│   └── MVIDelegate.kt  ← MVI 委托实现
├── di/
│   └── AppModule.kt    ← Koin 模块 (如果启用)
├── domain/
│   └── repository/
│       └── MainRepository.kt  ← Repository 接口
├── navigation/
│   ├── Screen.kt           ← 屏幕定义
│   └── NavigationGraph.kt  ← 导航图 (如果启用)
└── ui/
    ├── components/
    │   ├── EmptyScreen.kt  ← 空状态组件
    │   └── LoadingBar.kt   ← 加载组件
    └── {screen}/           ← 每个屏幕一个目录
        ├── {Screen}Screen.kt     ← UI 层
        ├── {Screen}ViewModel.kt  ← ViewModel
        └── {Screen}Contract.kt   ← State/Event/Effect 定义
```

**生成的文件数量**: 10+ 个基础文件 + 每个屏幕 3 个文件

**架构模式**: MVI (Model-View-Intent)

**示例代码**:

**HomeContract.kt**:
```kotlin
data class HomeState(
    val isLoading: Boolean = false,
    val data: List<Item> = emptyList(),
    val error: String? = null
)

sealed interface HomeEvent {
    data object LoadData : HomeEvent
    data class OnItemClick(val id: String) : HomeEvent
}

sealed interface HomeEffect {
    data class ShowToast(val message: String) : HomeEffect
    data class NavigateToDetail(val id: String) : HomeEffect
}
```

**HomeViewModel.kt**:
```kotlin
class HomeViewModel : MVI<HomeState, HomeEvent, HomeEffect> by MVIDelegate(HomeState()) {
    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.LoadData -> loadData()
            is HomeAction.OnItemClick -> navigateToDetail(action.id)
        }
    }
    
    private fun loadData() {
        // 加载数据逻辑
    }
}
```

**HomeScreen.kt**:
```kotlin
@Composable
fun HomeScreen(
    uiState: HomeState,
    uiEffect: Flow<HomeEffect>,
    onAction: (HomeAction) -> Unit
) {
    // UI 实现
}
```

---

### 6. Screens (屏幕列表)

**选项**: 文本输入框 (默认: 空)

#### 作用
定义要生成的屏幕列表，每个屏幕会生成对应的文件。

#### 输入格式
```
Home, Detail, Profile
```

用逗号分隔，支持空格。

#### 默认值
如果不输入，默认生成一个 `Main` 屏幕。

#### 生成的文件 (每个屏幕)

**前提**: 必须勾选 "Common-Data-Domain-DI-UI Packages"

**为每个屏幕生成**:
```
composeApp/src/commonMain/kotlin/{package}/ui/{screen}/
├── {Screen}Screen.kt     ← UI 层 (Composable 函数)
├── {Screen}ViewModel.kt  ← ViewModel (业务逻辑)
└── {Screen}Contract.kt   ← State/Event/Effect 定义
```

**示例**: 输入 `Home, Detail, Profile`

生成:
```
ui/
├── home/
│   ├── HomeScreen.kt
│   ├── HomeViewModel.kt
│   └── HomeContract.kt
├── detail/
│   ├── DetailScreen.kt
│   ├── DetailViewModel.kt
│   └── DetailContract.kt
└── profile/
    ├── ProfileScreen.kt
    ├── ProfileViewModel.kt
    └── ProfileContract.kt
```

**导航配置**:

**Screen.kt**:
```kotlin
sealed class Screen {
    @Serializable
    data object Home : Screen
    
    @Serializable
    data object Detail : Screen
    
    @Serializable
    data object Profile : Screen
}
```

**NavigationGraph.kt**:
```kotlin
NavHost(
    navController = navController,
    startDestination = Home  // ← 第一个屏幕是起始页
) {
    composable<Home> { HomeScreen(...) }
    composable<Detail> { DetailScreen(...) }
    composable<Profile> { ProfileScreen(...) }
}
```

**Koin 模块** (如果启用):
```kotlin
val viewModelModule = module {
    factoryOf(::HomeViewModel)
    factoryOf(::DetailViewModel)
    factoryOf(::ProfileViewModel)
}
```

---

## 选项组合建议

### 最小配置 (学习/原型)
```
✅ Android
✅ iOS
❌ Desktop
❌ Network Library: None
❌ Image Library: None
❌ Koin
❌ Navigation
❌ Common-Data-Domain-DI-UI Packages
Screens: (空)
```

**生成**: 只有基础的 `App.kt` 和平台特定文件

---

### 标准配置 (小型项目)
```
✅ Android
✅ iOS
❌ Desktop
Network Library: Ktor
Image Library: Coil
✅ Koin
✅ Navigation
✅ Common-Data-Domain-DI-UI Packages
Screens: Home, Detail
```

**生成**: 完整的分层架构 + 2 个屏幕

---

### 完整配置 (生产项目)
```
✅ Android
✅ iOS
✅ Desktop
Network Library: Ktorfit
Image Library: Coil
✅ Koin
✅ Navigation
✅ Common-Data-Domain-DI-UI Packages
Screens: Home, Detail, Profile, Settings
```

**生成**: 完整的企业级架构 + 4 个屏幕

---

## 依赖关系

### 必需依赖

| 选项 | 依赖的其他选项 |
|------|----------------|
| Network Library | 无 |
| Image Library | 无 |
| Koin | 无 |
| Navigation | **Common-Data-Domain-DI-UI Packages** |
| Screens | **Common-Data-Domain-DI-UI Packages** |

### 自动启用

| 选项 | 自动启用 |
|------|----------|
| Ktor/Ktorfit | KSP 插件 |
| Koin | KSP 插件 |
| 任何网络库 | kotlinx-serialization |

---

## 文件生成总览

### 基础文件 (总是生成)
```
build.gradle.kts
settings.gradle.kts
gradle.properties
gradle/wrapper/gradle-wrapper.properties
gradle/libs.versions.toml
composeApp/build.gradle.kts
composeApp/src/commonMain/kotlin/{package}/App.kt
composeApp/src/androidMain/kotlin/{package}/MainActivity.kt
composeApp/src/androidMain/AndroidManifest.xml
```

### 条件文件

| 选项 | 生成的文件数 | 文件类型 |
|------|-------------|----------|
| Ktor | 1 | MainService.kt |
| Ktorfit | 1 | MainService.kt (接口) |
| Koin | 2 | AppModule.kt, MainApp.kt |
| Navigation | 2 | Screen.kt, NavigationGraph.kt |
| Common-Data-Domain-DI-UI | 10+ | 架构文件 |
| 每个 Screen | 3 | Screen.kt, ViewModel.kt, Contract.kt |

### 计算公式
```
总文件数 = 基础文件(9) 
         + (Koin ? 2 : 0)
         + (Navigation ? 2 : 0)
         + (Network ? 1 : 0)
         + (Common-Data-Domain-DI-UI ? 10 : 0)
         + (屏幕数 × 3)
```

**示例**: 
- 标准配置 (Ktor + Coil + Koin + Navigation + 2 屏幕)
- = 9 + 2 + 2 + 1 + 10 + (2 × 3) = **30 个文件**

---

## 总结

### 快速参考

| 选项 | 用途 | 推荐场景 |
|------|------|----------|
| **Network Library** | HTTP 请求 | 需要调用 API |
| **Image Library** | 图片加载 | 需要显示网络图片 |
| **Koin** | 依赖注入 | 中大型项目 |
| **Navigation** | 页面导航 | 多页面应用 |
| **Common-Data-Domain-DI-UI** | 分层架构 | 生产项目 |
| **Screens** | 生成屏幕 | 快速搭建页面 |

### 建议

1. **学习阶段**: 不勾选任何选项，手动添加功能
2. **原型开发**: 只勾选 Navigation + 1-2 个屏幕
3. **生产项目**: 全部勾选，使用完整架构

这样可以根据项目需求灵活选择，避免生成不需要的代码。
