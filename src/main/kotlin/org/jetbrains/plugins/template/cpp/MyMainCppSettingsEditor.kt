package org.jetbrains.plugins.template.cpp

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import org.jetbrains.plugins.template.device.DeviceService
import org.jetbrains.plugins.template.device.HarmonyDevice
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 运行配置编辑器 UI
 */
class MyMainCppSettingsEditor(private val project: Project) : SettingsEditor<MyMainCppRunConfiguration>() {
    private val deviceComboBox = ComboBox<DeviceItem>()
    private val panel: JPanel
    private val deviceService = DeviceService.getInstance(project)

    init {
        println("=== MyMainCppSettingsEditor INIT ===")
        println("Project: ${project.name}")
        
        // 监听设备变化
        deviceService.addListener {
            println("Device list changed, updating UI...")
            updateDeviceList()
        }

        // 初始化设备列表
        println("Initial device list update...")
        updateDeviceList()

        panel = FormBuilder.createFormBuilder()
            // 注释掉默认的"目标设备"面板，避免与自定义设备列表组件冲突
            // .addLabeledComponent(JBLabel("目标设备:"), deviceComboBox, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        
        println("=== MyMainCppSettingsEditor INIT COMPLETE ===")
    }

    private fun updateDeviceList() {
        println("=== updateDeviceList() CALLED ===")
        val devices = deviceService.getConnectedDevices()
        println("Connected devices: ${devices.size}")
        devices.forEach { device ->
            println("  - ${device.displayName} (${device.deviceId})")
        }
        
        val selectedItem = deviceComboBox.selectedItem as? DeviceItem
        println("Current selected item: ${selectedItem?.displayText}")
        
        deviceComboBox.removeAllItems()
        
        if (devices.isEmpty()) {
            println("No devices, adding placeholder")
            deviceComboBox.addItem(DeviceItem(null, "无可用设备"))
            deviceComboBox.isEnabled = false
        } else {
            println("Adding ${devices.size} devices to combo box")
            devices.forEach { device ->
                val item = DeviceItem(device, device.displayName)
                println("  Adding: ${item.displayText}")
                deviceComboBox.addItem(item)
            }
            deviceComboBox.isEnabled = true
            
            // 尝试恢复之前的选择
            if (selectedItem != null) {
                val index = (0 until deviceComboBox.itemCount).find {
                    deviceComboBox.getItemAt(it)?.device?.deviceId == selectedItem.device?.deviceId
                }
                if (index != null) {
                    println("Restoring selection to index $index")
                    deviceComboBox.selectedIndex = index
                }
            }
        }
        
        println("ComboBox item count: ${deviceComboBox.itemCount}")
        println("ComboBox enabled: ${deviceComboBox.isEnabled}")
        println("=== updateDeviceList() COMPLETE ===")
    }

    override fun createEditor(): JComponent {
        return panel
    }

    override fun resetEditorFrom(configuration: MyMainCppRunConfiguration) {
        println("=== resetEditorFrom() CALLED ===")
        // 从配置中读取设备 ID
        val deviceId = configuration.getSelectedDeviceId()
        println("Configuration device ID: $deviceId")
        
        if (deviceId != null) {
            // 查找并选中对应的设备
            for (i in 0 until deviceComboBox.itemCount) {
                val item = deviceComboBox.getItemAt(i)
                println("  Checking item $i: ${item?.displayText} (${item?.device?.deviceId})")
                if (item?.device?.deviceId == deviceId) {
                    println("  Found matching device at index $i")
                    deviceComboBox.selectedIndex = i
                    break
                }
            }
        } else {
            println("No device ID in configuration")
        }
        println("=== resetEditorFrom() COMPLETE ===")
    }

    override fun applyEditorTo(configuration: MyMainCppRunConfiguration) {
        println("=== applyEditorTo() CALLED ===")
        // 保存选中的设备 ID 到配置
        val selectedItem = deviceComboBox.selectedItem as? DeviceItem
        println("Selected item: ${selectedItem?.displayText}")
        println("Selected device ID: ${selectedItem?.device?.deviceId}")
        configuration.setSelectedDeviceId(selectedItem?.device?.deviceId)
        println("=== applyEditorTo() COMPLETE ===")
    }

    /**
     * 设备下拉框项
     */
    private data class DeviceItem(
        val device: HarmonyDevice?,
        val displayText: String
    ) {
        override fun toString(): String = displayText
    }
}
