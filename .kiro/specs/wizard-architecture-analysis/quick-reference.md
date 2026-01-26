# Wizard 快速参考手册

## 一句话总结

**Wizard 是一个基于 Android Studio 模板系统的 KMP 项目生成器，通过 FreeMarker 模板引擎根据用户配置生成完整的多平台项目结构。**

---

## 核心组件速查

### 1. 入口点
```
plugin.xml → AndroidStudioTemplateProvider → CMPTemplate
```

### 2. 配置流程
```
用户填写 UI → CMPTemplate 参数 → CMPConfigModel → dataModel
```

### 3. 生成流程
```
Recipe → FileGenerator → FreeMarker 模板 → 文件系统
```

---

## 关键文件清单

| 文件 | 作用 | 修改频率 |
|------|------|----------|
| `AndroidStudioTemplateProvider.kt` | 注册模板 | 低 |
| `CMPTemplate.kt` | UI 配置 | 中 |
| `CMPConfigModel.kt` | 数据模型 | 中 |
| `composeMultiplatformProjectRecipe.kt` | 生成逻辑 | 高 |
| `CommonFileGenerator.kt` | 文件生成器 | 高 |
| `Template.kt` | 模板常量 | 中 |
| `Utils.kt` | 工具方法 | 低 |
| `fileTemplates/code/*.ft` | 模板文件 | 高 |

---

## 添加新功能的步骤

### 场景 1: 添加新的库支持

1. **CMPTemplate.kt**: 添加 `booleanParameter` 或 `enumParameter`
2. **CMPConfigModel.kt**: 添加对应的状态字段
3. **Recipe**: 在 `dataModel` 中添加变量
4. **模板文件**: 使用 `<#if IS_XXX_ENABLE>` 条件生成代码
5. **测试**: 运行插件，创建项目验证

### 场景 2: 添加新的平台

1. **创建**: `XXXFileGenerator.kt` 继承 `FileGenerator`
2. **实现**: `generate()` 方法返回 `List<GeneratorAsset>`
3. **注册**: 在 Recipe 中添加到 `platforms` 列表
4. **模板**: 创建平台特定的模板文件
5. **测试**: 验证文件生成

### 场景 3: 修改现有模板

1. **找到**: `src/main/resources/fileTemplates/code/{name}.ft`
2. **编辑**: 使用 FreeMarker 语法修改
3. **变量**: 确保使用的变量在 `dataModel` 中存在
4. **测试**: 重新生成项目检查结果

---

## FreeMarker 语法速查

```freemarker
${VARIABLE}                    <#-- 变量替换 -->

<#if CONDITION>                <#-- 条件判断 -->
    内容
</#if>

<#if COND1>
    内容1
<#elseif COND2>
    内容2
<#else>
    内容3
</#if>

<#list ITEMS as item>          <#-- 循环 -->
    ${item}
</#list>

<#-- 注释 -->                  <#-- 不会出现在输出中 -->

${"${"}VAR}                    <#-- 转义，输出 ${VAR} -->
```

---

## 常用变量速查

### 基础变量
- `${PACKAGE_NAME}` - 包名
- `${APP_NAME}` - 应用名
- `${MODULE_NAME}` - 模块名

### 平台开关
- `${IS_ANDROID_ENABLE}` - Android
- `${IS_IOS_ENABLE}` - iOS
- `${IS_DESKTOP_ENABLE}` - Desktop

### 库开关
- `${IS_KOIN_ENABLE}` - Koin
- `${IS_KTOR_ENABLE}` - Ktor
- `${IS_NAVIGATION_ENABLE}` - Navigation

### 版本号
- `${CMP_KOTLIN}` - Kotlin 版本
- `${CMP_MULTIPLATFORM}` - Compose Multiplatform 版本
- `${CMP_KOIN}` - Koin 版本

### 生成的代码
- `${SCREENS}` - 导航代码块
- `${SCREENS_IMPORTS}` - 导入语句
- `${START_DESTINATION}` - 起始屏幕

---

## 调试技巧

### 1. 查看生成的文件内容
在 `Utils.generateFileFromTemplate()` 中添加:
```kotlin
println("Generated: ${asset.relativePath}")
println(writer.toString())
```

### 2. 检查 dataModel
在 Recipe 中添加:
```kotlin
dataModel.forEach { (k, v) -> println("$k = $v") }
```

### 3. 查看模板错误
FreeMarker 会抛出详细的错误信息，注意查看:
- 变量名是否正确
- 语法是否正确
- 模板文件是否存在

---

## 常见问题

### Q: 模板变量未定义
**A**: 在 `composeMultiplatformProjectRecipe` 的 `dataModel` 中添加该变量

### Q: 模板文件找不到
**A**: 检查:
1. 文件是否在 `src/main/resources/fileTemplates/code/` 下
2. 文件名是否正确 (包括扩展名)
3. `Template.kt` 中的常量是否正确

### Q: 生成的文件内容不对
**A**: 检查:
1. 模板文件内容
2. dataModel 中的变量值
3. FreeMarker 条件判断逻辑

### Q: 文件没有生成
**A**: 检查:
1. FileGenerator 是否添加了该文件
2. 条件判断是否正确
3. 路径是否正确

---

## 代码位置速查

### 添加新参数
```
src/main/kotlin/wizard/projectwizard/CMPTemplate.kt
```

### 添加新状态
```
src/main/kotlin/wizard/projectwizard/data/CMPConfigModel.kt
```

### 修改生成逻辑
```
src/main/kotlin/wizard/projectwizard/recipes/composeMultiplatformProjectRecipe.kt
```

### 添加文件生成
```
src/main/kotlin/wizard/projectwizard/cmparch/CommonFileGenerator.kt
src/main/kotlin/wizard/projectwizard/cmparch/AndroidFileGenerator.kt
src/main/kotlin/wizard/projectwizard/cmparch/IOSFileGenerator.kt
src/main/kotlin/wizard/projectwizard/cmparch/DesktopFileGenerator.kt
```

### 修改模板
```
src/main/resources/fileTemplates/code/*.ft
```

### 添加模板常量
```
src/main/kotlin/wizard/projectwizard/cmparch/Template.kt
```

---

## 架构图 (简化版)

```
┌─────────────────────────────────────────────┐
│              用户操作                        │
│         (New Project Wizard)                │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│          CMPTemplate.kt                     │
│  - 定义 UI 参数                              │
│  - 定义 widgets                             │
│  - 绑定 recipe                              │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│   composeMultiplatformProjectRecipe         │
│  1. 解析用户输入                             │
│  2. 创建 CMPConfigModel                     │
│  3. 构建 dataModel (变量映射)                │
│  4. 创建 FileGenerator 列表                 │
│  5. 生成文件                                 │
│  6. 刷新 VFS                                │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│         FileGenerator                       │
│  - CommonFileGenerator                      │
│  - AndroidFileGenerator                     │
│  - IOSFileGenerator                         │
│  - DesktopFileGenerator                     │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│      FreeMarker 模板引擎                     │
│  - 加载 .ft 文件                             │
│  - 替换变量                                  │
│  - 处理条件和循环                            │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│           生成的项目文件                     │
│  - .kt 文件                                  │
│  - .gradle.kts 文件                         │
│  - .xml 文件                                │
│  - 其他配置文件                              │
└─────────────────────────────────────────────┘
```

---

## 数据流 (简化版)

```
用户输入
    ↓
CMPTemplate 参数 (booleanParameter, enumParameter, stringParameter)
    ↓
CMPConfigModel (状态对象)
    ↓
dataModel: Map<String, Any> (模板变量)
    ↓
FileGenerator.generate() → List<GeneratorAsset>
    ↓
Utils.generateFileFromTemplate()
    ↓
FreeMarker 处理模板
    ↓
文件系统
```

---

## 扩展点总结

### 1. 添加新的配置选项
- **位置**: `CMPTemplate.kt`
- **方法**: 添加 `booleanParameter` / `enumParameter` / `stringParameter`

### 2. 添加新的数据模型字段
- **位置**: `CMPConfigModel.kt`
- **方法**: 添加 `var xxx by mutableStateOf(...)`

### 3. 添加新的模板变量
- **位置**: `composeMultiplatformProjectRecipe.kt`
- **方法**: 在 `dataModel` Map 中添加键值对

### 4. 添加新的文件生成
- **位置**: `CommonFileGenerator.kt` 或其他 Generator
- **方法**: 添加 `GeneratorTemplateFile` 到返回列表

### 5. 添加新的模板文件
- **位置**: `src/main/resources/fileTemplates/code/`
- **方法**: 创建 `.ft` 文件，使用 FreeMarker 语法

### 6. 添加新的平台支持
- **位置**: `wizard/projectwizard/cmparch/`
- **方法**: 创建新的 `FileGenerator` 实现类

---

## 测试流程

1. **构建插件**: `./gradlew buildPlugin`
2. **运行插件**: `./gradlew runIde`
3. **创建项目**: File → New → Project → ProjectWizard - KMP
4. **填写配置**: 选择平台、库等
5. **验证生成**: 检查生成的文件是否正确
6. **运行项目**: 确保项目可以编译和运行

---

## 性能优化建议

1. **批量 VFS 刷新**: 使用 `ProjectGenerationHelper.flushVfsRefreshSync()`
2. **异步操作**: 文件 I/O 在后台线程执行
3. **减少模板复杂度**: 复杂逻辑在 Recipe 中预处理
4. **缓存版本信息**: 避免重复网络请求

---

## 最佳实践

### 代码组织
- 一个模板文件对应一个输出文件
- 复杂逻辑在 Recipe 中处理
- 模板只负责展示

### 命名规范
- 变量: `UPPER_SNAKE_CASE`
- 布尔变量: `IS_XXX_ENABLE`
- 版本变量: `CMP_XXX`
- 模板常量: `UPPER_SNAKE_CASE`

### 条件判断
- 优先使用简单的 if/else
- 避免深层嵌套
- 使用生成的代码片段而不是模板循环

### 可维护性
- 添加注释说明用途
- 使用常量而不是硬编码字符串
- 保持代码简洁易读

---

## 相关资源

- **FreeMarker 文档**: https://freemarker.apache.org/
- **Android Studio 模板**: https://developer.android.com/studio/projects/templates
- **Kotlin Multiplatform**: https://kotlinlang.org/docs/multiplatform.html

---

## 总结

Wizard 系统的核心是：

1. **配置层** (CMPTemplate): 定义用户可配置的选项
2. **数据层** (CMPConfigModel + dataModel): 存储和传递配置数据
3. **生成层** (Recipe + FileGenerator): 根据配置生成文件列表
4. **模板层** (FreeMarker): 根据变量生成实际文件内容

理解这四层的关系和数据流转，就能轻松修改和扩展 Wizard 功能。

**记住**: 修改任何功能都需要在这四层中保持一致！
