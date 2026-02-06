# 设备列表 UI 更新机制详解

## 核心问题回答

**Q: 设备更新了，会删掉上次已经轮询出来的设备列表，重新创建吗？**

**A: 不会完全删除重建，而是采用"增量更新"的方式：**
1. **数据层**: 完全替换设备列表（`AtomicReference.getAndSet()`）
2. **UI 层**: 只更新变化的部分（文本和图标），不重建整个组件

---

## 详细流程分析

### 1. 数据层更新（DeviceService）

```kotlin
// DeviceService.onDevicesChanged()
private fun onDevicesChanged(newDevices: List<HarmonyDevice>) {
    // ① 原子操作：替换整个设备列表
    val oldDevices = devices.getAndSet(newDevices)
    
    // ② 处理设备选择逻辑
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
    
    // ③ 通知所有监听器
    fireChangeEvent()
}
```

**关键点**:
- 使用 `AtomicReference.getAndSet()` **原子替换**整个列表
- 不是增量添加/删除，而是完全替换
- 线程安全，无需额外同步

---

### 2. UI 层更新（DeviceSelectorAction）

#### 2.1 触发更新

```
设备列表变化
    ↓
DeviceService.fireChangeEvent()
    ↓
通知所有监听器
    ↓
DeviceSelectorAction 的监听器被调用
    ↓
queueUpdate(project, presentation)
    ↓
ModalityUiUtil.invokeLaterIfNeeded {
    updatePresentation(project, presentation)
}
    ↓
ActivityTracker.getInstance().inc()
    ↓
IntelliJ 平台触发所有 Action 的 update()
    ↓
DeviceSelectorAction.update(e)
```

#### 2.2 update() 方法的工作

```kotlin
override fun update(e: AnActionEvent) {
    val deviceService = DeviceService.getInstance(project)
    
    // ① 获取最新的设备列表和选中设备
    val devices = deviceService.getConnectedDevices()
    val selectedDevice = deviceService.getSelectedDevice()
    
    // ② 确定显示内容
    val text: String
    val icon: Icon
    when {
        devices.isEmpty() -> {
            text = "No Devices"
            icon = DEFAULT_DEVICE_ICON
        }
        selectedDevice == null -> {
            text = "Select Device"
            icon = DEFAULT_DEVICE_ICON
        }
        else -> {
            text = selectedDevice.displayName
            icon = selectedDevice.getIcon()
        }
    }
    
    // ③ 更新 Presentation（IntelliJ 的数据模型）
    presentation.text = text
    presentation.icon = icon
    
    // ④ 更新自定义 UI 组件
    updateCustomComponent(presentation, icon, text)
}
```

#### 2.3 updateCustomComponent() 的工作

```kotlin
private fun updateCustomComponent(presentation: Presentation, icon: Icon, text: String) {
    val customComponent = presentation.getClientProperty(CUSTOM_COMPONENT_KEY) as? JButton
    if (customComponent != null) {
        val iconLabel = customComponent.getClientProperty(ICON_LABEL_KEY) as? JBLabel
        val textLabel = customComponent.getClientProperty(TEXT_LABEL_KEY) as? JBLabel
        
        // ① 只更新图标和文本，不重建组件
        iconLabel?.icon = icon
        textLabel?.text = text
        textLabel?.foreground = getToolbarForegroundColor()
        
        // ② 触发重绘
        customComponent.invalidate()
        var parent = customComponent.parent
        while (parent != null) {
            parent.invalidate()
            parent = parent.parent
        }
        customComponent.revalidate()
        customComponent.repaint()
    }
}
```

**关键点**:
- **不重建组件**: 只更新现有 JLabel 的 `icon` 和 `text` 属性
- **触发重绘**: 调用 `invalidate()` → `revalidate()` → `repaint()`
- **向上传播**: 标记父组件也需要重新布局

---

### 3. 弹窗设备列表的更新

当用户点击设备选择器时，会显示设备列表弹窗：

```kotlin
private fun showDevicePopup(dataContext: DataContext, component: Component?) {
    val deviceService = DeviceService.getInstance(project)
    
    // ① 实时获取最新的设备列表（不是缓存）
    val devices = deviceService.getConnectedDevices()
    
    // ② 创建 ActionGroup
    val group = DefaultActionGroup()
    
    if (devices.isEmpty()) {
        // 无设备：显示禁用的提示项
        group.add(object : AnAction("No Devices Connected") {
            override fun actionPerformed(e: AnActionEvent) {}
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = false
            }
        })
    } else {
        // 有设备：为每个设备创建一个 Action
        devices.forEach { device ->
            group.add(SelectDeviceAction(device, project))
        }
    }
    
    // ③ 创建并显示弹窗
    val popup = JBPopupFactory.getInstance()
        .createActionGroupPopup(null, group, dataContext,
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
    
    popup.showUnderneathOf(component)
}
```

**关键点**:
- **每次点击都重新创建**: 弹窗是临时的，每次点击都会重新创建
- **实时数据**: 从 `DeviceService` 实时获取最新设备列表
- **完全重建**: 弹窗内的设备列表是完全重新创建的

---

## 完整的更新流程对比

### 场景 1: 设备连接/断开

```
HDC 检测到设备变化
    ↓
SimpleDevicePoller.pollDevices()
    ├─ 旧设备列表: [Device A, Device B]
    └─ 新设备列表: [Device A, Device B, Device C]
    ↓
DeviceService.onDevicesChanged([A, B, C])
    ├─ devices.getAndSet([A, B, C])  ← 完全替换
    ├─ 处理设备选择
    └─ fireChangeEvent()
    ↓
DeviceSelectorAction 监听器触发
    ↓
update() 方法执行
    ├─ 获取新设备列表: [A, B, C]
    ├─ 获取选中设备: Device A
    ├─ 确定显示: text="Device A", icon=A的图标
    └─ updateCustomComponent()
        ├─ iconLabel.icon = A的图标  ← 只更新属性
        ├─ textLabel.text = "Device A"  ← 只更新属性
        └─ 触发重绘
```

### 场景 2: 用户点击设备选择器

```
用户点击工具栏设备选择器
    ↓
showDevicePopup()
    ├─ 获取最新设备列表: [A, B, C]
    ├─ 创建 DefaultActionGroup
    ├─ 为每个设备创建 SelectDeviceAction
    │   ├─ SelectDeviceAction(A)  ← 新建
    │   ├─ SelectDeviceAction(B)  ← 新建
    │   └─ SelectDeviceAction(C)  ← 新建
    └─ 创建并显示弹窗  ← 完全新建
    ↓
用户选择 Device B
    ↓
SelectDeviceAction.actionPerformed()
    ↓
DeviceService.setSelectedDevice(B)
    ├─ selectedDevice.set(B)
    └─ fireChangeEvent()
    ↓
DeviceSelectorAction 监听器触发
    ↓
update() 方法执行
    ├─ 确定显示: text="Device B", icon=B的图标
    └─ updateCustomComponent()
        ├─ iconLabel.icon = B的图标  ← 只更新属性
        └─ textLabel.text = "Device B"  ← 只更新属性
```

---

## 性能优化点

### 1. 数据层优化

```kotlin
// ✅ 使用 AtomicReference，无锁操作
private val devices = AtomicReference<List<HarmonyDevice>>(emptyList())

// ✅ 使用不可变列表，避免并发修改
fun getConnectedDevices(): List<HarmonyDevice> {
    return devices.get()  // 返回的是不可变快照
}
```

### 2. UI 层优化

```kotlin
// ✅ 只更新变化的部分，不重建组件
iconLabel?.icon = icon  // 只设置属性
textLabel?.text = text  // 只设置属性

// ❌ 不这样做（性能差）
// customComponent.removeAll()
// customComponent.add(new JLabel(...))
```

### 3. 事件优化

```kotlin
// ✅ 使用 EDT 线程安全更新
ApplicationManager.getApplication().invokeLater {
    onDevicesChanged(currentDevices)
}

// ✅ 使用 ActivityTracker 批量触发更新
ActivityTracker.getInstance().inc()  // 触发所有 Action 的 update()
```

---

## 内存管理

### 设备列表的生命周期

```
创建: SimpleDevicePoller.pollDevices()
    ↓
存储: DeviceService.devices (AtomicReference)
    ↓
使用: 
    ├─ DeviceSelectorAction.update() - 读取显示
    ├─ showDevicePopup() - 创建弹窗列表
    └─ HarmonyRunProfileState - 获取选中设备
    ↓
替换: devices.getAndSet(newDevices)
    ├─ 旧列表被 GC 回收（如果没有其他引用）
    └─ 新列表成为当前列表
```

### 弹窗的生命周期

```
创建: showDevicePopup()
    ├─ 创建 DefaultActionGroup
    ├─ 创建 SelectDeviceAction 实例
    └─ 创建 JBPopup
    ↓
显示: popup.showUnderneathOf(component)
    ↓
用户操作:
    ├─ 选择设备 → 弹窗关闭
    └─ 点击外部 → 弹窗关闭
    ↓
销毁: 弹窗及其内容被 GC 回收
```

---

## 总结

### 数据层（DeviceService）
- ✅ **完全替换**: 使用 `AtomicReference.getAndSet()` 原子替换整个列表
- ✅ **线程安全**: 无需额外同步
- ✅ **不可变**: 返回的列表是不可变快照

### UI 层（DeviceSelectorAction）
- ✅ **增量更新**: 只更新图标和文本，不重建组件
- ✅ **按需创建**: 弹窗每次点击时重新创建
- ✅ **EDT 安全**: 所有 UI 操作都在 EDT 线程

### 性能特点
- **工具栏更新**: 非常快，只更新 2 个 JLabel 的属性
- **弹窗创建**: 稍慢，但可接受（通常只有几个设备）
- **内存占用**: 低，旧列表会被 GC 回收

### 设计优势
1. **简单**: 数据层完全替换，逻辑清晰
2. **高效**: UI 层增量更新，性能好
3. **安全**: 线程安全，无竞态条件
4. **灵活**: 易于扩展和维护
