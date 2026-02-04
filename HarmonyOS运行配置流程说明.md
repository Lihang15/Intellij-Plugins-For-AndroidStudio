# HarmonyOS 运行配置流程说明

## 📋 目录
1. [整体架构](#整体架构)
2. [核心组件](#核心组件)
3. [运行流程（Run模式）](#运行流程run模式)
4. [调试流程（Debug模式）](#调试流程debug模式)
5. [配置创建流程](#配置创建流程)
6. [设备选择流程](#设备选择流程)
7. [关键函数调用链](#关键函数调用链)

---

## 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    IntelliJ IDEA 平台                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────┐      ┌──────────────────┐            │
│  │ Run 按钮点击      │      │ Debug 按钮点击    │            │
│  └────────┬─────────┘      └────────┬─────────┘            │
│           │                         │                       │
│           ▼                         ▼                       │
│  ┌──────────────────┐      ┌──────────────────┐            │
│  │ DefaultRunner    │      │ HarmonyDebugRunner│            │
│  └────────┬─────────┘      └────────┬─────────┘            │
│           │                         │                       │
│           └──────────┬──────────────┘                       │
│                      ▼                                      │
│           ┌──────────────────────┐                         │
│           │ HarmonyRunProfileState│                         │
│           └──────────┬───────────┘                         │
│                      │                                      │
│                      ▼                                      │
│           ┌──────────────────────┐                         │
│           │ runOhosApp-Mac.sh    │                         │
│           │ (构建、部署、启动应用) │                         │
│           └──────────────────────┘                         │
└─────────────────────────────────────────────────────────────┘
```

---

## 核心组件

### 1. HarmonyConfigurationType
**作用**: 定义运行配置类型
- 注册到 IntelliJ 平台
- 提供配置类型的名称、图标
- 关联 HarmonyConfigurationFactory

**关键代码**:
```kotlin
class HarmonyConfigurationType : ConfigurationTypeBase(
    "HarmonyConfigurationType",
    "harmonyApp",  // 显示名称
    "Run HarmonyOS application",
    IconLoader.getIcon("/icons/harmony_logo.svg", ...)
)
```

### 2. HarmonyConfigurationFactory
**作用**: 创建运行配置实例
- 工厂模式，负责创建 HarmonyRunConfiguration
- 指定配置选项类（HarmonyRunConfigurationOptions）

**关键方法**:
```kotlin
override fun createTemplateConfiguration(project: Project): RunConfiguration {
    return HarmonyRunConfiguration(project, this, "harmonyApp")
}
```

### 3. HarmonyRunConfiguration
**作用**: 运行配置的核心类
- 存储配置数据（设备ID等）
- 提供配置编辑器（HarmonySettingsEditor）
- 创建运行状态（HarmonyRunProfileState）
- 检查项目是否为 HarmonyOS 项目

**关键方法**:
- `getConfigurationEditor()`: 返回配置UI编辑器
- `getState()`: 创建运行状态对象
- `hasHarmonyFile()`: 检查是否为 HarmonyOS 项目
- `getSelectedDeviceId()` / `setSelectedDeviceId()`: 设备管理

### 4. HarmonyRunProfileState
**作用**: 负责实际的构建和部署
- 获取选中的设备
- 准备并执行 runOhosApp-Mac.sh 脚本
- 管理进程生命周期

**关键方法**:
```kotlin
override fun startProcess(): ProcessHandler {
    // 1. 获取设备
    // 2. 准备脚本
    // 3. 执行脚本
    // 4. 返回进程处理器
}
```

### 5. HarmonyDebugRunner
**作用**: 处理调试模式
- 只在 Debug 模式下生效
- 启动 XDebugSession
- 连接 LLDB 调试器

**关键方法**:
```kotlin
override fun doExecute(...): RunContentDescriptor? {
    // 启动调试会话
    // 创建 LLDBDebugProcess
}
```

### 6. HarmonySettingsEditor
**作用**: 配置UI编辑器
- 显示设备选择下拉框
- 保存/读取配置

### 7. HarmonyRunConfigurationProducer
**作用**: 自动检测并创建运行配置
- 检测项目是否为 HarmonyOS 项目
- 自动创建 "harmonyApp" 运行配置

---

## 运行流程（Run模式）

### 流程图
```
用户点击 Run 按钮
    │
    ▼
IntelliJ 平台调用 DefaultRunner
    │
    ▼
DefaultRunner.execute(environment)
    │
    ▼
调用 HarmonyRunConfiguration.getState(executor, environment)
    │
    ▼
创建 HarmonyRunProfileState 实例
    │
    ▼
调用 HarmonyRunProfileState.startProcess()
    │
    ├─► 1. 从 DeviceService 获取选中的设备
    │   └─► 如果没有设备，抛出异常
    │
    ├─► 2. 从插件资源加载 runOhosApp-Mac.sh 脚本
    │   └─► 复制到临时目录
    │   └─► 设置执行权限
    │
    ├─► 3. 读取 local.properties 中的 local.ohos.path
    │   └─► 如果配置了外部路径，添加 -p 参数
    │
    ├─► 4. 构建命令行
    │   └─► bash <脚本路径> [-p <外部路径>] ohosArm64 <设备ID>
    │
    ├─► 5. 创建 ProcessHandler
    │   └─► KillableColoredProcessHandler
    │
    └─► 6. 返回 ProcessHandler
        │
        ▼
    脚本开始执行
        │
        ├─► Gradle 构建 (publishDebugBinariesToHarmonyApp)
        ├─► Hvigor 同步与 HAP 打包
        ├─► 推送 lldb-server 到设备
        ├─► 安装 HAP 到设备
        ├─► 启动应用
        └─► 启动 lldb-server 监听
```

### 详细步骤

#### 步骤 1: 获取设备
```kotlin
val selectedDevice = DeviceService.getInstance(project).getSelectedDevice()
if (selectedDevice == null) {
    throw ExecutionException("未选择 HarmonyOS 设备")
}
```

#### 步骤 2: 准备脚本
```kotlin
val scriptResource = this::class.java.getResource("/runscript/runOhosApp-Mac.sh")
val scriptPath = File(tempDir, "runOhosApp-Mac-${System.currentTimeMillis()}.sh")
scriptResource.openStream().use { input ->
    scriptPath.outputStream().use { output ->
        input.copyTo(output)
    }
}
scriptPath.setExecutable(true)
scriptPath.deleteOnExit()
```

#### 步骤 3: 读取外部路径配置
```kotlin
private fun readLocalOhosPath(project: Project): String? {
    val localPropsFile = File(basePath, "local.properties")
    val properties = java.util.Properties()
    localPropsFile.inputStream().use { properties.load(it) }
    return properties.getProperty("local.ohos.path")?.trim()
}
```

#### 步骤 4: 构建命令
```kotlin
val commandLine = if (localOhosPath != null) {
    GeneralCommandLine(
        "bash",
        scriptPath.absolutePath,
        "-p", localOhosPath,
        "ohosArm64",
        selectedDevice.deviceId
    )
} else {
    GeneralCommandLine(
        "bash",
        scriptPath.absolutePath,
        "ohosArm64",
        selectedDevice.deviceId
    )
}
commandLine.setWorkDirectory(projectBasePath)
```

#### 步骤 5: 执行脚本
脚本 `runOhosApp-Mac.sh` 执行以下操作：
1. **Gradle 构建**: `./gradlew :composeApp:publishDebugBinariesToHarmonyApp`
2. **切换到 harmonyApp 目录**
3. **Hvigor 同步**: `ohpm install --all`
4. **HAP 打包**: `hvigorw.js assembleHap`
5. **推送调试组件**: 
   - 推送 lldb-server 到设备
   - 设置执行权限
6. **安装 HAP**: `bm install -p <HAP路径>`
7. **启动应用**: `aa start -a EntryAbility -b com.example.harmonyapp`
8. **启动调试监听**: `lldb-server platform --listen ...`

---

## 调试流程（Debug模式）

### 流程图
```
用户点击 Debug 按钮
    │
    ▼
IntelliJ 平台检查 Runner
    │
    ▼
HarmonyDebugRunner.canRun(executorId, profile)
    │ (返回 true，因为 executorId == DefaultDebugExecutor.EXECUTOR_ID)
    ▼
HarmonyDebugRunner.doExecute(state, environment)
    │
    ├─► 1. 获取 HarmonyRunConfiguration
    │
    ├─► 2. 获取 XDebuggerManager
    │
    ├─► 3. 启动 XDebugSession
    │   │
    │   └─► 创建 XDebugProcessStarter
    │       │
    │       └─► start(session) 方法
    │           │
    │           └─► 创建 LLDBDebugProcess(session, executablePath)
    │
    └─► 4. 返回 debugSession.runContentDescriptor
        │
        ▼
    调试会话启动
        │
        └─► LLDBDebugProcess 连接到设备上的 lldb-server
```

### 关键代码
```kotlin
override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
    val configuration = environment.runProfile as? HarmonyRunConfiguration
        ?: throw ExecutionException("Invalid configuration")
    
    val project = configuration.project
    
    // 启动 XDebugSession
    val debuggerManager = XDebuggerManager.getInstance(project)
    val debugSession = debuggerManager.startSession(
        environment,
        object : com.intellij.xdebugger.XDebugProcessStarter() {
            override fun start(session: XDebugSession): XDebugProcess {
                // TODO: 确定正确的可执行文件路径
                val executablePath = ""
                return LLDBDebugProcess(session, executablePath)
            }
        }
    )
    
    return debugSession.runContentDescriptor
}
```

### 注意事项
- Debug 模式下，应用的构建和部署仍然由 `HarmonyRunProfileState` 完成
- `HarmonyDebugRunner` 只负责启动调试会话
- 调试器连接到设备上由脚本启动的 `lldb-server`

---

## 配置创建流程

### 自动创建流程
```
项目打开
    │
    ▼
IntelliJ 平台扫描 RunConfigurationProducer
    │
    ▼
HarmonyRunConfigurationProducer.setupConfigurationFromContext()
    │
    ├─► 1. 获取项目路径
    │
    ├─► 2. 调用 hasHarmonyOSProject(projectPath)
    │   │
    │   ├─► 检查是否存在 harmonyApp 目录
    │   │   └─► 如果存在，返回 true
    │   │
    │   └─► 检查 local.properties 中的 local.ohos.path
    │       └─► 如果配置且路径存在，返回 true
    │
    ├─► 3. 如果是 HarmonyOS 项目
    │   └─► 创建名为 "harmonyApp" 的运行配置
    │
    └─► 4. 返回 true/false
```

### 检测规则
项目被识别为 HarmonyOS 项目的条件（满足其一即可）：
1. **项目根目录下存在 `harmonyApp` 目录**
2. **`local.properties` 中配置了有效的 `local.ohos.path`**

### 关键代码
```kotlin
private fun hasHarmonyOSProject(projectPath: String): Boolean {
    // 规则 1：检查 harmonyApp 目录
    val harmonyAppDir = File(projectPath, "harmonyApp")
    if (harmonyAppDir.exists() && harmonyAppDir.isDirectory) {
        return true
    }

    // 规则 2：检查 local.properties
    val localPropertiesFile = File(projectPath, "local.properties")
    if (localPropertiesFile.exists()) {
        val properties = java.util.Properties()
        localPropertiesFile.inputStream().use { properties.load(it) }
        
        val ohosPath = properties.getProperty("local.ohos.path")?.trim()
        if (!ohosPath.isNullOrEmpty()) {
            val ohosDir = File(ohosPath)
            if (ohosDir.exists() && ohosDir.isDirectory) {
                return true
            }
        }
    }
    
    return false
}
```

---

## 设备选择流程

### 设备管理架构
```
┌─────────────────────────────────────────┐
│         DeviceService                   │
│  (单例，管理所有设备)                    │
│                                         │
│  - connectedDevices: List<HarmonyDevice>│
│  - selectedDevice: HarmonyDevice?       │
│  - listeners: List<DeviceListener>      │
└────────────┬────────────────────────────┘
             │
             ├─► FlutterStyleDeviceSelectorAction (工具栏设备选择器)
             │
             ├─► HarmonySettingsEditor (配置编辑器)
             │
             └─► HarmonyRunProfileState (运行时获取设备)
```

### 设备选择流程
```
用户在工具栏选择设备
    │
    ▼
FlutterStyleDeviceSelectorAction.actionPerformed()
    │
    ▼
DeviceService.setSelectedDevice(device)
    │
    ├─► 更新 selectedDevice
    │
    └─► 通知所有监听器
        │
        └─► HarmonySettingsEditor.updateDeviceList()
            │
            └─► 更新下拉框显示
```

### 配置保存流程
```
用户在配置编辑器中选择设备
    │
    ▼
HarmonySettingsEditor.applyEditorTo(configuration)
    │
    ▼
configuration.setSelectedDeviceId(deviceId)
    │
    ▼
保存到 HarmonyRunConfigurationOptions
    │
    └─► options.deviceId = deviceId
```

### 运行时获取设备
```
HarmonyRunProfileState.startProcess()
    │
    ▼
DeviceService.getInstance(project).getSelectedDevice()
    │
    ├─► 如果有设备，继续执行
    │
    └─► 如果没有设备，抛出异常
        └─► "未选择 HarmonyOS 设备"
```

---

## 关键函数调用链

### Run 模式完整调用链
```
1. 用户点击 Run 按钮
   ↓
2. IntelliJ Platform
   ↓
3. DefaultRunner.execute(environment)
   ↓
4. HarmonyRunConfiguration.getState(executor, environment)
   ↓
5. new HarmonyRunProfileState(environment)
   ↓
6. HarmonyRunProfileState.startProcess()
   ↓
7. DeviceService.getInstance(project).getSelectedDevice()
   ↓
8. this::class.java.getResource("/runscript/runOhosApp-Mac.sh")
   ↓
9. scriptPath.setExecutable(true)
   ↓
10. readLocalOhosPath(project)
    ↓
11. GeneralCommandLine(bash, scriptPath, ...)
    ↓
12. new KillableColoredProcessHandler(commandLine)
    ↓
13. ProcessTerminatedListener.attach(processHandler)
    ↓
14. return processHandler
    ↓
15. 脚本执行
    ├─► ./gradlew :composeApp:publishDebugBinariesToHarmonyApp
    ├─► ohpm install --all
    ├─► hvigorw.js assembleHap
    ├─► hdc file send lldb-server
    ├─► hdc shell bm install
    ├─► hdc shell aa start
    └─► hdc shell lldb-server platform --listen
```

### Debug 模式完整调用链
```
1. 用户点击 Debug 按钮
   ↓
2. IntelliJ Platform
   ↓
3. HarmonyDebugRunner.canRun(executorId, profile)
   ↓ (返回 true)
4. HarmonyDebugRunner.doExecute(state, environment)
   ↓
5. environment.runProfile as HarmonyRunConfiguration
   ↓
6. XDebuggerManager.getInstance(project)
   ↓
7. debuggerManager.startSession(environment, starter)
   ↓
8. XDebugProcessStarter.start(session)
   ↓
9. new LLDBDebugProcess(session, executablePath)
   ↓
10. return debugSession.runContentDescriptor
    ↓
11. 调试会话启动
    └─► LLDBDebugProcess 连接到设备上的 lldb-server
```

### 配置创建调用链
```
1. 项目打开
   ↓
2. IntelliJ Platform 扫描 RunConfigurationProducer
   ↓
3. HarmonyRunConfigurationProducer.setupConfigurationFromContext(...)
   ↓
4. hasHarmonyOSProject(projectPath)
   ├─► File(projectPath, "harmonyApp").exists()
   └─► readLocalProperties().getProperty("local.ohos.path")
   ↓
5. configuration.name = "harmonyApp"
   ↓
6. return true
   ↓
7. IntelliJ 创建运行配置
```

### 设备选择调用链
```
1. 用户在工具栏选择设备
   ↓
2. FlutterStyleDeviceSelectorAction.actionPerformed()
   ↓
3. DeviceService.setSelectedDevice(device)
   ↓
4. selectedDevice.set(device)
   ↓
5. notifyListeners()
   ↓
6. HarmonySettingsEditor.updateDeviceList()
   ↓
7. deviceComboBox.removeAllItems()
   ↓
8. deviceComboBox.addItem(DeviceItem(...))
   ↓
9. 用户保存配置
   ↓
10. HarmonySettingsEditor.applyEditorTo(configuration)
    ↓
11. configuration.setSelectedDeviceId(deviceId)
    ↓
12. options.deviceId = deviceId
```

---

## 数据流图

### 配置数据流
```
HarmonyRunConfigurationOptions (持久化存储)
    │
    ├─► deviceId: String?
    │
    ▼
HarmonyRunConfiguration (运行时配置)
    │
    ├─► getSelectedDeviceId(): String?
    ├─► setSelectedDeviceId(deviceId: String?)
    ├─► getSelectedDevice(): HarmonyDevice?
    │
    ▼
HarmonySettingsEditor (UI 编辑器)
    │
    ├─► resetEditorFrom(configuration)  // 读取配置
    └─► applyEditorTo(configuration)    // 保存配置
```

### 设备数据流
```
DeviceService (设备管理服务)
    │
    ├─► connectedDevices: List<HarmonyDevice>
    ├─► selectedDevice: AtomicReference<HarmonyDevice?>
    │
    ▼
HarmonyDevice (设备数据模型)
    │
    ├─► deviceId: String
    ├─► displayName: String
    ├─► status: DeviceStatus
    │
    ▼
使用设备的组件
    │
    ├─► FlutterStyleDeviceSelectorAction (工具栏)
    ├─► HarmonySettingsEditor (配置编辑器)
    └─► HarmonyRunProfileState (运行时)
```

---

## 总结

### 核心流程
1. **配置创建**: `HarmonyRunConfigurationProducer` 自动检测并创建配置
2. **设备选择**: 用户通过工具栏或配置编辑器选择设备
3. **运行/调试**: 
   - Run 模式: `HarmonyRunProfileState` 执行脚本
   - Debug 模式: `HarmonyDebugRunner` 启动调试会话
4. **脚本执行**: `runOhosApp-Mac.sh` 完成构建、部署、启动

### 关键设计模式
- **工厂模式**: `HarmonyConfigurationFactory` 创建配置
- **单例模式**: `DeviceService` 管理设备
- **观察者模式**: `DeviceService` 通知设备变化
- **策略模式**: Run/Debug 使用不同的 Runner

### 扩展点
- 添加新的设备类型: 扩展 `HarmonyDevice`
- 自定义构建流程: 修改 `runOhosApp-Mac.sh`
- 添加配置选项: 扩展 `HarmonyRunConfigurationOptions`
