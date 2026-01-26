# LLDB 断点调试指南

## 当前状态
已添加详细的调试日志来追踪断点注册流程。代码已成功编译。

## 关键问题
断点没有被检测到，`registerBreakpoint()` 方法从未被调用。这表明 IntelliJ 没有将断点与我们的 `LLDBBreakpointHandler` 关联起来。

## 最可能的原因

### 1. **IDE 未重启** ⚠️ 最关键
`plugin.xml` 的修改（断点类型注册）需要完全重启 IDE 才能生效。

**操作步骤：**
1. 完全关闭 Android Studio（不是重新加载插件）
2. 重新打开 Android Studio
3. 重新运行插件
4. 在 `my_main.cpp` 中设置断点（应该看到红色圆点）
5. 启动调试

### 2. 断点类型未正确注册
检查 `plugin.xml` 中是否有：
```xml
<xdebugger.breakpointType implementation="org.jetbrains.plugins.template.debuger.LLDBLineBreakpointType"/>
```

### 3. 断点类型不匹配
用户设置的断点类型可能不是 `LLDBLineBreakpointType`。

## 新增的调试日志

运行调试后，你应该看到以下日志：

### 初始化阶段
```
=== LLDBDebugProcess 初始化 ===
可执行文件路径: /path/to/mymaincpp
Session: ProjectName
Session 类: ...
当前已注册的断点类型数量: X
✓ LLDBLineBreakpointType 已在扩展点注册  <-- 关键：必须看到这个
```

### sessionInitialized 阶段
```
=== sessionInitialized 开始 ===
断点管理器中的所有断点数量: X  <-- 应该 > 0
  断点类型: ...
  断点类型 ID: ...
  位置: /path/to/my_main.cpp:行号

=== 检查 BreakpointHandler 配置 ===
BreakpointHandler 支持的断点类型: org.jetbrains.plugins.template.debuger.LLDBLineBreakpointType
✓ 找到 LLDBLineBreakpointType 扩展  <-- 关键：必须看到这个
  ID: lldb-line

=== getBreakpointHandlers 被调用 ===  <-- 关键：必须被调用
返回 breakpointHandler: ...

=== 延迟后再次检查断点 ===
breakpointHandler 中已注册的断点数: X  <-- 应该 > 0
```

### 断点注册阶段（关键）
```
=== LLDBBreakpointHandler 构造函数 ===
支持的断点类型: org.jetbrains.plugins.template.debuger.LLDBLineBreakpointType

=== registerBreakpoint 被调用 ===  <-- 关键：如果没有这个，说明类型不匹配
断点信息:
  文件路径: /path/to/my_main.cpp
  行号: X
  lldbReady=false
已添加到 registeredBreakpoints, 当前总数: 1
LLDB 未就绪, 缓存断点: ...
```

## 诊断步骤

### 步骤 1：完全重启 IDE
**这是最重要的步骤！**
1. 关闭 Android Studio
2. 重新打开
3. 重新运行插件

### 步骤 2：检查断点是否显示为红色圆点
在 `my_main.cpp` 中点击行号左侧设置断点：
- ✓ 红色实心圆点 = 断点类型正确识别
- ✗ 灰色或其他颜色 = 断点类型未识别

### 步骤 3：运行调试并收集完整日志
1. 在 `my_main.cpp` 的第 11 行（`int x = 10;`）设置断点
2. 启动调试
3. 复制**完整的日志输出**，特别关注：
   - `=== LLDBDebugProcess 初始化 ===` 部分
   - `=== sessionInitialized 开始 ===` 部分
   - `=== getBreakpointHandlers 被调用 ===` 是否出现
   - `=== registerBreakpoint 被调用 ===` 是否出现（最关键）

### 步骤 4：分析日志

#### 场景 A：看到 "✗ 未找到 LLDBLineBreakpointType 扩展"
**原因：** IDE 未重启，plugin.xml 修改未生效
**解决：** 完全重启 IDE

#### 场景 B：看到 "✓ 找到 LLDBLineBreakpointType 扩展" 但没有 "=== registerBreakpoint 被调用 ==="
**原因：** 断点类型不匹配，用户设置的断点不是 LLDBLineBreakpointType
**解决：** 
1. 检查断点管理器中的断点类型 ID
2. 可能需要删除旧断点，重新设置
3. 确认 `canPutAt()` 方法返回 true

#### 场景 C：看到 "=== registerBreakpoint 被调用 ===" 但断点仍未命中
**原因：** 断点注册成功，但 LLDB 同步有问题
**解决：** 检查 LLDB 命令执行日志

## 预期的正常流程

1. **IDE 启动** → 加载 `LLDBLineBreakpointType` 扩展
2. **用户设置断点** → IntelliJ 检查 `canPutAt()` → 创建 `LLDBLineBreakpointType` 断点
3. **启动调试** → 创建 `LLDBDebugProcess`
4. **IntelliJ 调用** `getBreakpointHandlers()` → 返回 `LLDBBreakpointHandler`
5. **IntelliJ 调用** `registerBreakpoint()` → 缓存断点
6. **LLDB 连接** → `onLldbReady()` → `syncAllBreakpoints()` → 同步到 LLDB
7. **程序运行** → 命中断点 → 暂停

## 下一步

**请执行以下操作：**

1. ✅ **完全重启 Android Studio**（最重要）
2. ✅ 在 `my_main.cpp` 第 11 行设置断点
3. ✅ 启动调试
4. ✅ 复制**完整的日志输出**发给我

**特别关注日志中是否出现：**
- `✓ LLDBLineBreakpointType 已在扩展点注册`
- `=== getBreakpointHandlers 被调用 ===`
- `=== registerBreakpoint 被调用 ===`

如果这三个都出现了，说明断点注册成功，问题在 LLDB 同步阶段。
如果缺少任何一个，说明断点类型注册有问题。
