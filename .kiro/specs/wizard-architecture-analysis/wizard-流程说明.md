# Wizard 调用流程图

## 1. 高层架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      IntelliJ IDEA / Android Studio          │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              New Project Wizard UI                    │  │
│  └────────────────────┬─────────────────────────────────┘  │
│                       │                                      │
└───────────────────────┼──────────────────────────────────────┘
                        │
                        ▼
        ┌───────────────────────────────┐
        │  plugin.xml 扩展点注册         │
        │  <wizardTemplateProvider>     │
        └───────────────┬───────────────┘
                        │
                        ▼
        ┌───────────────────────────────┐
        │ AndroidStudioTemplateProvider │
        │   getTemplates()              │
        └───────────────┬───────────────┘
                        │
                        ▼
        ┌───────────────────────────────┐
        │    KMPTemplate.kt             │
        │  (模板定义 + UI 配置)          │
        └───────────────┬───────────────┘
                        │
                        ▼
        ┌───────────────────────────────┐
        │  composeMultiplatformProject  │
        │  Recipe (生成逻辑)             │
        └───────────────┬───────────────┘
                        │
                        ▼
        ┌───────────────────────────────┐
        │   FileGenerator 系统           │
        │  (文件生成器)                  │
        └───────────────┬───────────────┘
                        │
                        ▼
        ┌───────────────────────────────┐
        │   FreeMarker 模板引擎          │
        │   (生成实际文件)               │
        └───────────────────────────────┘
```

---

## 2. 详细调用序列图

```
用户                IDE              Provider         Template         Recipe          Generator        Utils
 │                  │                  │                │                │                │               │
 │  New Project     │                  │                │                │                │               │
 ├─────────────────>│                  │                │                │                │               │
 │                  │  getTemplates()  │                │                │                │               │
 │                  ├─────────────────>│                │                │                │               │
 │                  │                  │  return        │                │                │               │
 │                  │                  │  [KMPTemplate] │                │                │               │
 │                  │<─────────────────┤                │                │                │               │
 │                  │                  │                │                │                │               │
 │  <显示向导 UI>    │                  │                │                │                │               │
 │<─────────────────┤                  │                │                │                │               │
 │                  │                  │                │                │                │               │
 │  填写配置         │                  │                │                │                │               │
 │  点击 Finish      │                  │                │                │                │               │
 ├─────────────────>│                  │                │                │                │               │
 │                  │                  │  recipe()      │                │                │               │
 │                  │                  ├───────────────>│                │                │               │
 │                  │                  │                │  execute       │                │               │
 │                  │                  │                ├───────────────>│                │               │
 │                  │                  │                │                │  generate()    │               │
 │                  │                  │                │                ├───────────────>│               │
 │                  │                  │                │                │                │  createDir()  │
 │                  │                  │                │                │                ├──────────────>│
 │                  │                  │                │                │                │               │
 │                  │                  │                │                │                │  genFile()    │
 │                  │                  │                │                │                ├──────────────>│
 │                  │                  │                │                │                │               │
 │                  │                  │                │                │  [assets]      │               │
 │                  │                  │                │                │<───────────────┤               │
 │                  │                  │                │                │                │               │
 │                  │                  │                │  flushVFS()    │                │               │
 │                  │                  │                │<───────────────┤                │               │
 │                  │                  │                │                │                │               │
 │                  │                  │  done          │                │                │               │
 │                  │                  │<───────────────┤                │                │               │
 │                  │                  │                │                │                │               │
 │  <显示完成通知>    │                  │                │                │                │               │
 │<─────────────────┤                  │                │                │                │               │
 │                  │                  │                │                │                │               │
```

---

## 3. Recipe 内部流程详解

```
composeMultiplatformProjectRecipe()
    │
    ├─ 1. 解析用户输入
    │   ├─ 解析 screens 字符串 → List<String>
    │   ├─ 生成 screenListString (导航代码)
    │   ├─ 生成 screensImportsString (导入语句)
    │   ├─ 生成 navigationScreens (Screen 对象)
    │   ├─ 生成 viewModelImports (ViewModel 导入)
    │   └─ 生成 viewModelModule (Koin 模块)
    │
    ├─ 2. 创建配置对象
    │   └─ KMPConfigModel
    │       ├─ isAndroidEnable
    │       ├─ isIOSEnable
    │       ├─ isHarmonyEnable
    │       ├─ selectedNetworkLibrary
    │       ├─ isKoinEnable
    │       ├─ isNavigationEnable
    │       └─ ...
    │
    ├─ 3. 构建数据模型 (dataModel: Map)
    │   ├─ APP_NAME
    │   ├─ PACKAGE_NAME
    │   ├─ IS_ANDROID_ENABLE
    │   ├─ IS_KTOR_ENABLE
    │   ├─ CMP_KOTLIN (版本号)
    │   ├─ SCREENS (生成的代码)
    │   ├─ SCREENS_IMPORTS
    │   └─ ...
    │
    ├─ 4. 创建文件生成器
    │   ├─ CommonFileGenerator (必需)
    │   ├─ AndroidFileGenerator (条件)
    │   ├─ IOSFileGenerator (条件)
    │   └─ HarmonyFileGenerator (条件)
    │
    ├─ 5. 生成文件资产
    │   └─ platforms.flatMap { it.generate() }
    │       └─ 返回 List<GeneratorAsset>
    │
    ├─ 6. 遍历资产生成文件
    │   ├─ GeneratorEmptyDirectory
    │   │   └─ Utils.createEmptyDirectory()
    │   │
    │   └─ GeneratorTemplateFile
    │       └─ Utils.generateFileFromTemplate()
    │           ├─ 加载 FreeMarker 模板
    │           ├─ 处理变量替换
    │           ├─ 创建目录
    │           └─ 写入文件
    │
    ├─ 7. 刷新 VFS
    │   └─ generationHelper.flushVfsRefreshSync()
    │
    ├─ 8. 发送分析事件
    │   └─ analyticsService.track()
    │
    └─ 9. 显示完成通知
        └─ Utils.showInfo()
```

---

## 4. CommonFileGenerator 生成流程

```
CommonFileGenerator.generate()
    │
    ├─ 必需文件 (总是生成)
    │   ├─ build.gradle.kts
    │   ├─ settings.gradle.kts
    │   ├─ gradle.properties
    │   ├─ gradle/wrapper/gradle-wrapper.properties
    │   ├─ gradle/libs.versions.toml
    │   ├─ my_main.cpp
    │   ├─ composeApp/src/commonMain/kotlin/.../App.kt
    │   ├─ composeApp/src/commonMain/composeResources/...
    │   └─ composeApp/build.gradle.kts
    │
    └─ 条件文件 (根据配置生成)
        │
        ├─ if (isDataDomainDiUiEnable)
        │   │
        │   ├─ 为每个 Screen 生成:
        │   │   ├─ ui/{screen}/{Screen}Screen.kt
        │   │   ├─ ui/{screen}/{Screen}ViewModel.kt
        │   │   └─ ui/{screen}/{Screen}Contract.kt
        │   │
        │   ├─ 架构文件:
        │   │   ├─ navigation/Screen.kt
        │   │   ├─ delegation/MVI.kt
        │   │   ├─ delegation/MVIDelegate.kt
        │   │   ├─ common/CollectExtension.kt
        │   │   ├─ common/Constants.kt
        │   │   ├─ domain/repository/MainRepository.kt
        │   │   ├─ data/repository/MainRepositoryImpl.kt
        │   │   ├─ ui/components/EmptyScreen.kt
        │   │   └─ ui/components/LoadingBar.kt
        │   │
        │   ├─ if (isKoinEnable)
        │   │   └─ di/AppModule.kt
        │   │
        │   ├─ if (selectedNetworkLibrary == Ktor)
        │   │   └─ data/source/remote/MainService.kt
        │   │
        │   ├─ if (selectedNetworkLibrary == Ktorfit)
        │   │   └─ data/source/remote/MainService.kt (Ktorfit 版本)
        │   │
        │   └─ if (isNavigationEnable)
        │       └─ navigation/NavigationGraph.kt
        │
        └─ 返回 List<GeneratorAsset>
```

---

## 5. 模板处理流程

```
Utils.generateFileFromTemplate()
    │
    ├─ 1. 创建 FreeMarker Configuration
    │   └─ 设置模板加载路径: "fileTemplates/code"
    │
    ├─ 2. 解析输出路径
    │   ├─ 分离目录路径和文件名
    │   └─ 例: "composeApp/src/.../App.kt"
    │       ├─ dirPath: "composeApp/src/..."
    │       └─ fileName: "App.kt"
    │
    ├─ 3. 创建目标目录
    │   └─ VfsUtil.createDirectoryIfMissing()
    │
    ├─ 4. 创建输出文件
    │   └─ targetDir.createChildData()
    │
    ├─ 5. 加载并处理模板
    │   ├─ 获取模板文件: "{name}.{ext}.ft"
    │   ├─ 例: "common_app.kt.ft"
    │   └─ template.process(dataModel, writer)
    │       ├─ 替换变量: ${PACKAGE_NAME}
    │       ├─ 条件判断: <#if IS_KOIN_ENABLE>
    │       └─ 循环: <#list SCREENS as screen>
    │
    └─ 6. 保存文件
        └─ VfsUtil.saveText(outputFile, content)
```

---

## 6. 数据流转图

```
┌─────────────────┐
│   用户输入       │
│  - 平台选择      │
│  - 库选择        │
│  - 屏幕列表      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  KMPTemplate    │
│  参数对象        │
│  - booleanParam │
│  - enumParam    │
│  - stringParam  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ KMPConfigModel  │
│  配置状态对象    │
│  - isAndroid    │
│  - isIOS        │
│  - screens      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   dataModel     │
│  Map<String,Any>│
│  - PACKAGE_NAME │
│  - IS_*_ENABLE  │
│  - SCREENS      │
│  - 版本号        │
│  - 生成的代码    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  FileGenerator  │
│  - Common       │
│  - Android      │
│  - iOS          │
│  - Harmony     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ GeneratorAsset  │
│  - EmptyDir     │
│  - TemplateFile │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ FreeMarker 模板 │
│  .ft 文件        │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   生成的文件     │
│  - .kt          │
│  - .gradle.kts  │
│  - .xml         │
└─────────────────┘
```

---

## 7. 关键决策点

```
Recipe 执行
    │
    ├─ 是否启用分层架构?
    │   ├─ YES → 生成 MVI、Repository、ViewModel 等
    │   └─ NO  → 只生成基础文件
    │
    ├─ 选择了哪个网络库?
    │   ├─ Ktor    → 生成 Ktor Service
    │   ├─ Ktorfit → 生成 Ktorfit Service
    │   └─ None    → 不生成网络相关文件
    │
    ├─ 是否启用 Koin?
    │   ├─ YES → 生成 AppModule.kt + Koin 初始化
    │   └─ NO  → 使用 viewModel() 函数
    │
    ├─ 是否启用 Navigation?
    │   ├─ YES → 生成 NavigationGraph.kt
    │   └─ NO  → 不生成导航文件
    │
    ├─ 选择了哪些平台?
    │   ├─ Android → 添加 AndroidFileGenerator
    │   ├─ iOS     → 添加 IOSFileGenerator
    │   └─ Harmony → 添加 HarmonyFileGenerator
    │
    └─ 屏幕列表
        ├─ 为每个屏幕生成:
        │   ├─ Screen.kt
        │   ├─ ViewModel.kt
        │   └─ Contract.kt
        └─ 生成导航代码片段
```

---

## 8. 文件系统操作流程

```
Recipe
    │
    ├─ 生成 assets 列表
    │
    ├─ 遍历 assets
    │   │
    │   ├─ GeneratorEmptyDirectory
    │   │   └─ VfsUtil.createDirectoryIfMissing()
    │   │       └─ 创建空目录
    │   │
    │   └─ GeneratorTemplateFile
    │       └─ Utils.generateFileFromTemplate()
    │           │
    │           ├─ 解析路径
    │           │   └─ "a/b/c/file.kt" → dir="a/b/c", name="file.kt"
    │           │
    │           ├─ 创建目录
    │           │   └─ VfsUtil.createDirectoryIfMissing(parent, "a/b/c")
    │           │
    │           ├─ 创建文件
    │           │   └─ targetDir.createChildData(this, "file.kt")
    │           │
    │           ├─ 处理模板
    │           │   ├─ 加载: "file.kt.ft"
    │           │   └─ 处理: template.process(dataModel, writer)
    │           │
    │           └─ 保存文件
    │               └─ VfsUtil.saveText(file, content)
    │
    └─ 刷新 VFS
        └─ ProjectGenerationHelper.flushVfsRefreshSync()
            └─ VfsUtil.markDirtyAndRefresh(rootDir)
```

---

## 总结

整个 Wizard 系统的调用流程可以概括为：

1. **注册阶段**: plugin.xml → AndroidStudioTemplateProvider
2. **配置阶段**: KMPTemplate → 用户填写配置
3. **生成阶段**: Recipe → FileGenerator → Utils
4. **模板阶段**: FreeMarker → 文件系统
5. **完成阶段**: VFS 刷新 → 通知用户

每个阶段都有明确的职责，数据在各层之间流转，最终生成完整的项目结构。
