# 插件重新加载指南

## 问题诊断

你的日志显示 `[doExecute] 创建 LLDBDebugProcess` 但没有看到 `LLDBDebugProcess` 内部的任何日志。这说明：

**新编译的代码没有被 IDE 加载！**

## 解决方案

### 方法 1：完全重启 IDE（推荐）

1. **完全关闭 Android Studio**
   - 不要只是关闭项目
   - 确保所有 Android Studio 窗口都关闭
   - 在 macOS 上，确保 Dock 中没有 Android Studio 图标

2. **验证进程已关闭**
   ```bash
   ps aux | grep "Android Studio"
   ```
   如果有进程，手动杀掉：
   ```bash
   killall -9 "Android Studio"
   ```

3. **重新打开 Android Studio**

4. **重新运行插件**
   - 使用 Gradle 任务：`runIde`
   - 或者在 IDE 中点击 "Run Plugin"

### 方法 2：使用 Gradle runIde（更可靠）

如果你是通过 Gradle 运行插件的，请确保：

1. **停止当前运行的插件实例**

2. **清理并重新构建**
   ```bash
   ./gradlew clean build -x test -x buildSearchableOptions -x prepareJarSearchableOptions
   ```

3. **运行插件**
   ```bash
   ./gradlew runIde
   ```

4. **在新打开的 IDE 窗口中测试**

### 方法 3：检查插件是否正确安装

如果你是通过安装插件的方式运行的：

1. **重新构建插件**
   ```bash
   ./gradlew buildPlugin
   ```

2. **找到生成的插件文件**
   ```bash
   ls -lh build/distributions/
   ```

3. **在 Android Studio 中重新安装**
   - `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk...`
   - 选择 `build/distributions/` 下的 `.zip` 文件
   - 重启 IDE

## 验证新代码是否加载

运行调试后，你应该在日志中看到：

```
=== LLDBDebugProcess 初始化 ===
可执行文件路径: /Users/admin/AndroidStudioProjects/faksbda/Harmony
Session: faksbda
Session 类: ...
当前已注册的断点类型数量: X
```

**如果没有看到这些日志，说明新代码还没有加载！**

## 调试日志查看方法

### 方法 1：实时查看日志
```bash
tail -f ~/Library/Logs/Google/AndroidStudio*/idea.log | grep -E "LLDB|Breakpoint|Harmony"
```

### 方法 2：查看完整日志
```bash
cat ~/Library/Logs/Google/AndroidStudio*/idea.log | grep -A 10 "LLDBDebugProcess"
```

### 方法 3：在 IDE 中查看
1. `Help` → `Show Log in Finder`
2. 打开 `idea.log`
3. 搜索 `LLDBDebugProcess`

## 当前状态���查清单

运行调试后，检查以下内容：

- [ ] 看到 `=== LLDBDebugProcess 初始化 ===`
- [ ] 看到 `=== LLDBBreakpointHandler 构造函数 ===`
- [ ] 看到 `当前已注册的断点类型数量: X`
- [ ] 看到 `✓ LLDBLineBreakpointType 已在扩展点注册` 或 `✗ LLDBLineBreakpointType 未在扩展点找到`
- [ ] 看到 `=== sessionInitialized 开始 ===`
- [ ] 看到 `断点管理器中的所有断点数量: X`
- [ ] 看到 `=== getBreakpointHandlers 被调用 ===`

**如果以上任何一项没有出现，说明新代码没有加载。**

## 下一步

1. **完全重启 IDE**（最重要）
2. **重新运行调试**
3. **查看日志**（使用上面的方法）
4. **把包含 `LLDBDebugProcess` 的所有日志发给我**

如果还是看不到日志，可能需要：
- 检查 Logger 配置
- 或者使用 `println()` 代替 `LOG.info()` 来确保输出
