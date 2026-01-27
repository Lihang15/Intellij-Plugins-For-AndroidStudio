# Wizard 架构分析总结

## 你的问题解答

### Q: 点了 Finish 之后，是开始生成模版，然后打开一个新窗口是吧？

**A**: 是的！完整流程如下：

1. **点击 Finish** → 验证输入
2. **显示进度对话框** → "Creating Project..."（框架自动显示）
3. **执行 Recipe** → 调用 `composeMultiplatformProjectRecipe()`（我们的代码）
4. **生成所有文件** → 使用 FileGenerator 和 FreeMarker 模板
5. **显示完成通知** → "Your project is ready! 🚀"（我们的代码）
6. **关闭进度对话框** → 框架自动关闭
7. **打开新窗口** → 框架自动打开新的 IDE 窗口
8. **加载项目** → 显示项目结构
9. **Gradle Sync** → 自动触发同步

### Q: 这个调用的哪个方法？

**A**: 分为两部分：

#### 1. 我们的代码（可以控制）

**Recipe 回调** - `KMPTemplate.kt`:
```kotlin
recipe = { data: TemplateData ->
    composeMultiplatformProjectRecipe(...)  // ← 这个方法
}
```

**文件生成** - `composeMultiplatformProjectRecipe.kt`:
```kotlin
fun composeMultiplatformProjectRecipe(...) {
    // 生成所有文件
}
```

**通知显示** - `Utils.kt`:
```kotlin
Utils.showInfo(
    title = "Quick Project Wizard",
    message = "Your project is ready! 🚀"
)
```

#### 2. 框架层（无法直接控制）

- **进度对话框**: Android Studio 框架自动管理
- **新窗口打开**: `ProjectManager.getInstance().openProject()` 由框架调用
- **Gradle Sync**: 框架自动触发

### Q: 新窗口打开好像还有一个弹窗显示正在生成，这个调用的哪个方法？

**A**: 这个进度弹窗由 **Android Studio 框架**自动管理，不是我们的代码调用的。

**进度对话框的特点**:
- 显示文本: "Creating Project..." 或类似内容
- 模态对话框（用户无法操作其他窗口）
- 在 Recipe 执行期间显示
- Recipe 完成后自动关闭

**如果想自定义进度信息**，可以使用 `ProgressManager` API:
```kotlin
fun composeMultiplatformProjectRecipe(...) {
    val indicator = ProgressManager.getInstance().progressIndicator
    indicator?.text = "Generating files..."
    indicator?.fraction = 0.5  // 50% 进度
}
```

---

## 核心要点

### 1. 我们能控制什么？

✅ Recipe 中的文件生成逻辑
✅ 自定义通知的显示
✅ 项目打开后的初始化（通过 postStartupActivity）

### 2. 框架控制什么？

❌ 进度对话框的显示和关闭
❌ 新窗口的创建和打开
❌ Gradle Sync 的触发

### 3. 关键调用路径

```
用户点击 Finish
    ↓
[框架] 显示进度对话框
    ↓
[我们] composeMultiplatformProjectRecipe() 执行
    ↓
[我们] FileGenerator.generate() 生成文件
    ↓
[我们] Utils.showInfo() 显示通知
    ↓
[框架] 关闭进度对话框
    ↓
[框架] ProjectManager.openProject() 打开新窗口
```

---

## 文档索引

1. **requirements.md** - 完整架构分析
2. **call-flow-diagram.md** - 可视化流程图
3. **template-modification-guide.md** - 模板修改指南
4. **quick-reference.md** - 快速参考手册
5. **finish-button-flow.md** - Finish 按钮详细流程
6. **SUMMARY.md** - 本文档（总结）

---

## 快速查找

- **修改模板**: 查看 `template-modification-guide.md`
- **添加新功能**: 查看 `quick-reference.md` 第 3 节
- **理解调用流程**: 查看 `call-flow-diagram.md`
- **Finish 按钮流程**: 查看 `finish-button-flow.md`
