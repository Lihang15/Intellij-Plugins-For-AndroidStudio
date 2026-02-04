# HarmonyOS 设备管理流程说明

## 📋 目录
1. [整体架构](#整体架构)
2. [核心组件](#核心组件)
3. [设备发现流程](#设备发现流程)
4. [设备选择流程](#设备选择流程)
5. [UI 更新流程](#ui-更新流程)
6. [关键函数调用链](#关键函数调用链)
7. [时序图](#时序图)

---

## 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      IntelliJ IDEA 平台                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              DeviceSelectorAction             │  │
│  │              (工具栏设备选择器 UI)                         │  │
│  └────────────────────┬─────────────────────────────────────┘  │
│                       │                                         │
│                       ▼                                         │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                   DeviceService                           │  │
│  │              (设备管理核心服务)                            │  │
│  │  - devices: List<HarmonyDevice>                           │  │
│  │  - selectedDevice: HarmonyDevice?                         │  │
│  │  - listeners: List<() -> Unit>                            │  │
│  └────────────────────┬─────────────────────────────────────┘  │
│                       │                                         │
│                       ▼                                         │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              SimpleDevicePoller                           │  │
│  │              (定时轮询设备)                                │  │
│  └────────────────────┬─────────────────────────────────────┘  │
│                       │                                         │
│                       ▼                                         │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              HdcCommandExecutor                           │  │
│  │              (执行 HDC 命令)                               │  │
│  └────────────────────┬─────────────────────────────────────┘  │
│                       │                                         │
└───────────────────────┼─────────────────────────────────────────┘
                        │
                        ▼
              ┌──────────────────┐
              │   HDC 命令行工具  │
              │  (hdc list targets)│
              └──────────────────┘
                        │
                        ▼
              ┌──────────────────┐
              │  HarmonyOS 设备   │
              │  (模拟器/真机)     │
              └──────────────────┘
```

---

## 核心组件

### 1. HarmonyDevice (数据模型)
**作用**: 表示一个 HarmonyOS 设备或模拟器

**属性**:
- `deviceId: String` - 设备唯一标识符（如 "127.0.0.1:5555"）
- `displayName: String` - 用户友好的显示名称（如 "harmony-E-5555"）
- `isEmulator: Boolean` - 是否为模拟器

**关键方法**:
```kotlin
fun getIcon(): Icon  // 返回设备图标（模拟器/真机）
companion object fun fromDeviceId(deviceId: String): HarmonyDevice  // 从设备ID创建实例
```

**设备识别规则**:
- 模拟器: deviceId 以 "127.0.0.1:" 或 "localhost:" 开头
- 真机: 其他格式的 deviceId

---

### 2. HdcCommandExecutor (命令执行器)
**作用**: 执行 HDC 命令并解析输出

**HDC 路径**: `/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc`

**关键方法**:
```kotlin
fun isHdcAvailable(): Boolean  // 检查 HDC 是否可用
fun listDevices(): List<HarmonyDevice>  // 列出所有连接的设备
private fun executeCommand(command: Array<String>): String  // 执行命令
private fun parseDeviceOutput(output: String): List<HarmonyDevice>  // 解析输出
```

**执行的命令**: `hdc list targets`

**输出格式**:
- 无设备: `[Empty]`
- 有设备: `127.0.0.1:5555` 或 `127.0.0.1:5555127.0.0.1:5557` (可能无换行)

---

### 3. SimpleDevicePoller (设备轮询器)
**作用**: 定时轮询设备列表，检测设备变化

**轮询间隔**: 10 秒（可配置）

**关键属性**:
- `hdcExecutor: HdcCommandExecutor` - HDC 命令执行器
- `onDevicesChanged: (List<HarmonyDevice>) -> Unit` - 设备变化回调
- `previousDevices: List<HarmonyDevice>` - 上次轮询的设备列表
- `isFirstPoll: Boolean` - 是否为首次轮询

**关键方法**:
```kotlin
fun start()  // 启动轮询
fun stop()  // 停止轮询
private fun pollDevices()  // 执行一次轮询
private fun hasDeviceListChanged(...): Boolean  // 检查设备列表是否变化
```

**轮询机制**:
- 使用 `AppExecutorUtil.getAppScheduledExecutorService()` 调度任务
- 首次轮询立即执行，后续每 10 秒执行一次
- 设备列表变化或首次轮询时触发回调

---

### 4. DeviceService (设备管理服务)
**作用**: 项目级服务，管理设备发现和选择

**服务级别**: `@Service(Service.Level.PROJECT)` - 每个项目一个实例

**关键属性**:
- `devices: AtomicReference<List<HarmonyDevice>>` - 当前设备列表（线程安全）
- `selectedDevice: AtomicReference<HarmonyDevice?>` - 当前选中的设备（线程安全）
- `listeners: MutableList<() -> Unit>` - 监听器列表（同步访问）
- `poller: SimpleDevicePoller?` - 设备轮询器
- `state: State` - 服务状态（INACTIVE, LOADING, READY）

**关键方法**:
```kotlin
fun addListener(listener: () -> Unit)  // 添加监听器
fun removeListener(listener: () -> Unit)  // 移除监听器
fun getConnectedDevices(): List<HarmonyDevice>  // 获取设备列表
fun getSelectedDevice(): HarmonyDevice?  // 获取选中的设备
fun setSelectedDevice(device: HarmonyDevice?)  // 设置选中的设备
fun refresh()  // 手动刷新设备
private fun startPolling()  // 启动轮询
private fun onDevicesChanged(newDevices: List<HarmonyDevice>)  // 设备变化回调
private fun fireChangeEvent()  // 通知所有监听器
```

**自动选择逻辑**:
- 如果没有选中设备且有设备连接，自动选择第一个设备
- 如果选中的设备断开连接，自动选择第一个可用设备（如果有）

---

### 5. DeviceSelectorAction (工具栏设备选择器)
**作用**: 工具栏上的设备选择下拉框

**实现接口**:
- `AnAction` - IntelliJ 动作
- `CustomComponentAction` - 自定义 UI 组件
- `DumbAware` - 在索引期间可用

**UI 组件**:
- 设备图标 (iconLabel)
- 设备名称 (textLabel)
- 下拉箭头 (arrowLabel)

**关键方法**:
```kotlin
override fun createCustomComponent(presentation: Presentation, place: String): JComponent
override fun update(e: AnActionEvent)  // 更新 UI 显示
override fun actionPerformed(e: AnActionEvent)  // 点击时显示设备列表
private fun showDevicePopup(...)  // 显示设备选择弹窗
private fun updateCustomComponent(...)  // 更新自定义组件
```

**显示逻辑**:
- 无设备: "No Devices" + 默认图标
- 有设备但未选择: "Select Device" + 默认图标
- 已选择设备: 设备名称 + 设备图标

---

### 6. GlobalRunConfigurationListener (全局配置监听器)
**作用**: 监听运行配置切换，触发 UI 刷新

**注册位置**: `plugin.xml` 的 `applicationListeners`

**关键方法**:
```kotlin
override fun runConfigurationSelected(settings: RunnerAndConfigurationSettings?)
```

**工作原理**:
- 当用户切换运行配置时被调用
- 调用 `ActivityTracker.getInstance().inc()` 强制刷新所有 Action 的 UI

---

## 设备发现流程

### 流程图
```
项目打开
    │
    ▼
DeviceService 初始化 (init)
    │
    ├─► state = LOADING
    │
    ├─► 检查 HDC 是否可用
    │   ├─► 如果不可用: 显示通知，state = INACTIVE
    │   └─► 如果可用: 继续
    │
    ├─► 创建 HdcCommandExecutor
    │
    ├─► 创建 SimpleDevicePoller
    │
    ├─► 启动轮询 (poller.start())
    │   │
    │   └─► 使用 AppExecutorUtil 调度定时任务
    │       ├─► 立即执行一次 pollDevices()
    │       └─► 每 10 秒执行一次 pollDevices()
    │
    └─► state = READY
        │
        ▼
    轮询循环开始
        │
        ▼
    pollDevices() 执行
        │
        ├─► hdcExecutor.listDevices()
        │   │
        │   ├─► executeCommand(["hdc", "list", "targets"])
        │   │
        │   ├─► 解析输出 parseDeviceOutput()
        │   │   ├─► 识别设备 ID
        │   │   ├─► 判断是否为模拟器
        │   │   └─► 创建 HarmonyDevice 对象
        │   │
        │   └─► 返回 List<HarmonyDevice>
        │
        ├─► 比较设备列表是否变化
        │   └─► hasDeviceListChanged()
        │
        ├─► 如果变化或首次轮询
        │   └─► 调用 onDevicesChanged(currentDevices)
        │       │
        │       └─► DeviceService.onDevicesChanged()
        │           │
        │           ├─► 更新 devices
        │           │
        │           ├─► 处理设备选择
        │           │   ├─► 无选中设备 + 有设备 → 自动选择第一个
        │           │   └─► 选中设备断开 → 选择第一个可用设备
        │           │
        │           └─► fireChangeEvent()
        │               │
        │               └─► 通知所有监听器
        │
        └─► 等待 10 秒后重复
```

### 详细步骤

#### 步骤 1: DeviceService 初始化
```kotlin
init {
    println("=== DeviceService INIT START ===")
    startPolling()
}
```

#### 步骤 2: 启动轮询
```kotlin
private fun startPolling() {
    state = State.LOADING
    val hdcPath = "/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc"
    val hdcExecutor = HdcCommandExecutor(hdcPath)
    
    if (!hdcExecutor.isHdcAvailable()) {
        state = State.INACTIVE
        showHdcNotFoundNotification(hdcPath)
        return
    }
    
    poller = SimpleDevicePoller(hdcExecutor, ::onDevicesChanged)
    poller?.start()
    state = State.READY
}
```

#### 步骤 3: 轮询设备
```kotlin
private fun pollDevices() {
    val currentDevices = hdcExecutor.listDevices()
    val changed = hasDeviceListChanged(previousDevices, currentDevices)
    
    if (changed || isFirstPoll) {
        previousDevices = currentDevices
        isFirstPoll = false
        
        ApplicationManager.getApplication().invokeLater {
            onDevicesChanged(currentDevices)
        }
    }
}
```

#### 步骤 4: 处理设备变化
```kotlin
private fun onDevicesChanged(newDevices: List<HarmonyDevice>) {
    val oldDevices = devices.getAndSet(newDevices)
    val current = selectedDevice.get()
    
    when {
        // 自动选择第一个设备
        current == null && newDevices.isNotEmpty() -> {
            selectedDevice.set(newDevices.first())
        }
        // 清除断开的设备选择
        current != null && !newDevices.contains(current) -> {
            selectedDevice.set(newDevices.firstOrNull())
        }
    }
    
    fireChangeEvent()
}
```

---

## 设备选择流程

### 用户选择设备流程
```
用户点击工具栏设备选择器
    │
    ▼
DeviceSelectorAction.actionPerformed()
    │
    ▼
showDevicePopup(dataContext, component)
    │
    ├─► 获取 DeviceService
    │
    ├─► 获取设备列表 getConnectedDevices()
    │
    ├─► 创建 ActionGroup
    │   └─► 为每个设备创建 SelectDeviceAction
    │
    ├─► 创建弹窗 JBPopupFactory.createActionGroupPopup()
    │
    └─► 显示弹窗 popup.showUnderneathOf(component)
        │
        ▼
    用户选择设备
        │
        ▼
    SelectDeviceAction.actionPerformed()
        │
        ▼
    DeviceService.setSelectedDevice(device)
        │
        ├─► selectedDevice.set(device)
        │
        └─► fireChangeEvent()
            │
            └─► 通知所有监听器
                │
                ├─► DeviceSelectorAction 更新 UI
                ├─► HarmonySettingsEditor 更新下拉框
                └─► 其他监听器...
```

### 自动选择设备流程
```
设备列表变化
    │
    ▼
DeviceService.onDevicesChanged(newDevices)
    │
    ├─► 检查当前选中的设备
    │
    ├─► 情况 1: 无选中设备 && 有设备连接
    │   └─► selectedDevice.set(newDevices.first())
    │
    ├─► 情况 2: 选中的设备断开连接
    │   └─► selectedDevice.set(newDevices.firstOrNull())
    │
    └─► fireChangeEvent()
```

---

## UI 更新流程

### 工具栏设备选择器更新流程
```
设备列表变化
    │
    ▼
DeviceService.fireChangeEvent()
    │
    └─► ApplicationManager.invokeLater {
        │
        └─► 调用所有监听器
            │
            └─► DeviceSelectorAction 的监听器
                │
                ▼
            queueUpdate(project, presentation)
                │
                ▼
            ModalityUiUtil.invokeLaterIfNeeded {
                │
                ▼
            updatePresentation(project, presentation)
                │
                ▼
            ActivityTracker.getInstance().inc()
                │
                ▼
            IntelliJ 平台触发所有 Action 的 update()
                │
                ▼
            DeviceSelectorAction.update(e)
                │
                ├─► 获取设备列表和选中设备
                │
                ├─► 确定显示文本和图标
                │   ├─► 无设备: "No Devices" + 默认图标
                │   ├─► 未选择: "Select Device" + 默认图标
                │   └─► 已选择: 设备名称 + 设备图标
                │
                └─► updateCustomComponent(presentation, icon, text)
                    │
                    └─► 更新 iconLabel, textLabel
                        └─► 触发 UI 重绘
```

### 配置编辑器更新流程
```
设备列表变化
    │
    ▼
DeviceService.fireChangeEvent()
    │
    └─► HarmonySettingsEditor 的监听器
        │
        ▼
    updateDeviceList()
        │
        ├─► deviceComboBox.removeAllItems()
        │
        ├─► 遍历设备列表
        │   └─► deviceComboBox.addItem(DeviceItem(device, displayName))
        │
        └─► 恢复之前的选择（如果设备仍然存在）
```

---

## 关键函数调用链

### 完整的设备发现调用链
```
1. 项目打开
   ↓
2. DeviceService.init()
   ↓
3. DeviceService.startPolling()
   ↓
4. HdcCommandExecutor(hdcPath)
   ↓
5. hdcExecutor.isHdcAvailable()
   ↓
6. SimpleDevicePoller(hdcExecutor, ::onDevicesChanged)
   ↓
7. poller.start()
   ↓
8. AppExecutorUtil.getAppScheduledExecutorService()
   ↓
9. executor.scheduleWithFixedDelay(::pollDevices, 0, 10, SECONDS)
   ↓
10. pollDevices() [立即执行]
    ↓
11. hdcExecutor.listDevices()
    ↓
12. executeCommand(["hdc", "list", "targets"])
    ↓
13. ProcessBuilder(*command).start()
    ↓
14. process.inputStream.bufferedReader().readLines()
    ↓
15. parseDeviceOutput(output)
    ↓
16. Regex("(127\\.0\\.0\\.1:\\d+|...)").findAll(output)
    ↓
17. HarmonyDevice.fromDeviceId(deviceId)
    ↓
18. return List<HarmonyDevice>
    ↓
19. hasDeviceListChanged(previousDevices, currentDevices)
    ↓
20. ApplicationManager.getApplication().invokeLater {
    ↓
21. onDevicesChanged(currentDevices)
    ↓
22. DeviceService.onDevicesChanged(newDevices)
    ↓
23. devices.getAndSet(newDevices)
    ↓
24. 处理设备选择逻辑
    ↓
25. fireChangeEvent()
    ↓
26. ApplicationManager.getApplication().invokeLater {
    ↓
27. 遍历 listeners
    ↓
28. listener() [调用每个监听器]
    ↓
29. DeviceSelectorAction 更新 UI
    ↓
30. HarmonySettingsEditor 更新下拉框
```

### 用户选择设备调用链
```
1. 用户点击工具栏设备选择器
   ↓
2. DeviceSelectorAction.actionPerformed(e)
   ↓
3. showDevicePopup(dataContext, component)
   ↓
4. DeviceService.getInstance(project).getConnectedDevices()
   ↓
5. DefaultActionGroup()
   ↓
6. devices.forEach { group.add(SelectDeviceAction(device, project)) }
   ↓
7. JBPopupFactory.getInstance().createActionGroupPopup(...)
   ↓
8. popup.showUnderneathOf(component)
   ↓
9. 用户点击设备
   ↓
10. SelectDeviceAction.actionPerformed(e)
    ↓
11. DeviceService.getInstance(project).setSelectedDevice(device)
    ↓
12. selectedDevice.getAndSet(device)
    ↓
13. fireChangeEvent()
    ↓
14. ApplicationManager.getApplication().invokeLater {
    ↓
15. 遍历 listeners
    ↓
16. listener() [调用每个监听器]
    ↓
17. UI 更新
```

---

## 时序图

### 设备发现时序图
```
项目      DeviceService    SimpleDevicePoller    HdcCommandExecutor    HDC命令
 │              │                   │                    │                │
 │─ 打开 ─────>│                   │                    │                │
 │              │                   │                    │                │
 │              │─ init() ─────────>│                   │                │
 │              │                   │                    │                │
 │              │                   │─ start() ────────>│                │
 │              │                   │                    │                │
 │              │                   │                    │─ listDevices()>│
 │              │                   │                    │                │
 │              │                   │                    │<─ 设备列表 ────│
 │              │                   │                    │                │
 │              │<─ onDevicesChanged()                   │                │
 │              │                   │                    │                │
 │              │─ fireChangeEvent()│                    │                │
 │              │                   │                    │                │
 │<─ UI 更新 ───│                   │                    │                │
 │              │                   │                    │                │
 │              │                   │─ [10秒后] ────────>│                │
 │              │                   │                    │                │
 │              │                   │                    │─ listDevices()>│
 │              │                   │                    │                │
 │              │                   │                    │<─ 设备列表 ────│
 │              │                   │                    │                │
 │              │<─ onDevicesChanged()                   │                │
 │              │                   │                    │                │
 │              │─ fireChangeEvent()│                    │                │
 │              │                   │                    │                │
 │<─ UI 更新 ───│                   │                    │                │
```

### 用户选择设备时序图
```
用户      工具栏Action    DeviceService    监听器们
 │              │                │             │
 │─ 点击 ─────>│                │             │
 │              │                │             │
 │              │─ showPopup() ─>│             │
 │              │                │             │
 │              │<─ 设备列表 ────│             │
 │              │                │             │
 │<─ 显示弹窗 ──│                │             │
 │              │                │             │
 │─ 选择设备 ──>│                │             │
 │              │                │             │
 │              │─ setSelectedDevice()         │
 │              │                │             │
 │              │                │─ fireChangeEvent()
 │              │                │             │
 │              │                │────────────>│
 │              │                │             │
 │              │<─ 更新 UI ─────│<────────────│
 │              │                │             │
 │<─ UI 刷新 ───│                │             │
```

---

## 总结

### 核心流程
1. **设备发现**: `SimpleDevicePoller` 每 10 秒轮询一次 HDC，检测设备变化
2. **设备管理**: `DeviceService` 维护设备列表和选中状态，通知监听器
3. **UI 更新**: `DeviceSelectorAction` 监听设备变化，更新工具栏显示
4. **用户交互**: 用户通过工具栏选择设备，触发 `DeviceService.setSelectedDevice()`

### 关键设计模式
- **单例模式**: `DeviceService` 每个项目一个实例
- **观察者模式**: `DeviceService` 通知监听器设备变化
- **定时任务**: `SimpleDevicePoller` 使用 `AppExecutorUtil` 调度轮询任务
- **线程安全**: 使用 `AtomicReference` 和 `synchronized` 保证线程安全

### 扩展点
- 修改轮询间隔: `SimpleDevicePoller` 构造函数的 `pollingIntervalSeconds` 参数
- 添加设备类型: 扩展 `HarmonyDevice.fromDeviceId()` 的识别逻辑
- 自定义设备图标: 修改 `HarmonyDevice.getIcon()` 方法
- 添加设备操作: 在 `DeviceSelectorAction` 的弹窗中添加更多操作
