package org.jetbrains.plugins.template.device

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import javax.swing.Icon

/**
 * 简化版设备选择器 - 不使用 CustomComponentAction
 * 使用标准的 AnAction，通过 text 和 icon 显示当前设备
 */
class SimpleDeviceSelectorAction : AnAction(), DumbAware {
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        println("=== SimpleDeviceSelectorAction.actionPerformed() ===")
        val project = e.project ?: return
        showDevicePopup(e.dataContext, e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT))
    }
    
    override fun update(e: AnActionEvent) {
        println("========================================")
        println("=== SimpleDeviceSelectorAction.update() CALLED ===")
        
        val project = e.project
        if (project == null) {
            println("Project is null, hiding")
            e.presentation.isVisible = false
            return
        }
        
        println("Project: ${project.name}")
        
        // 始终显示，不检查 MyMainApp
        e.presentation.isVisible = true
        e.presentation.isEnabled = true
        
        // 获取当前设备状态
        val deviceService = DeviceService.getInstance(project)
        val devices = deviceService.getConnectedDevices()
        val selectedDevice = deviceService.getSelectedDevice()
        val status = deviceService.getStatus()
        
        println("Status: $status, Devices: ${devices.size}, Selected: ${selectedDevice?.displayName}")
        
        // 设置显示文本和图标
        val text: String
        val icon: Icon
        
        when {
            status == DeviceService.State.LOADING -> {
                icon = AllIcons.Process.Step_1
                text = "Loading..."
            }
            status == DeviceService.State.INACTIVE -> {
                icon = AllIcons.General.Error
                text = "HDC Unavailable"
            }
            devices.isEmpty() -> {
                icon = AllIcons.Debugger.ThreadRunning
                text = "No Devices"
            }
            selectedDevice == null -> {
                icon = AllIcons.General.Warning
                text = "Select Device"
            }
            else -> {
                icon = selectedDevice.getIcon()
                text = selectedDevice.displayName
            }
        }
        
        e.presentation.text = text
        e.presentation.icon = icon
        
        println("Set presentation: text='$text', icon=$icon, visible=true, enabled=true")
        println("========================================")
    }
    
    private fun showDevicePopup(dataContext: DataContext, component: java.awt.Component?) {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return
        val deviceService = DeviceService.getInstance(project)
        val devices = deviceService.getConnectedDevices()
        
        println("Showing device popup, devices: ${devices.size}")
        
        if (devices.isEmpty()) {
            val group = DefaultActionGroup()
            group.add(object : AnAction("No Devices Connected") {
                override fun actionPerformed(e: AnActionEvent) {}
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = false
                }
                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.BGT
                }
            })
            group.addSeparator()
            group.add(object : AnAction("Waiting for HarmonyOS devices...") {
                override fun actionPerformed(e: AnActionEvent) {}
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = false
                }
                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.BGT
                }
            })
            
            val popup = JBPopupFactory.getInstance()
                .createActionGroupPopup(null, group, dataContext,
                    JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
            
            if (component != null) {
                popup.showUnderneathOf(component)
            } else {
                popup.showInBestPositionFor(dataContext)
            }
            return
        }
        
        val group = DefaultActionGroup()
        devices.forEach { device ->
            group.add(SelectDeviceAction(device, project))
        }
        
        val popup = JBPopupFactory.getInstance()
            .createActionGroupPopup(null, group, dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
        
        if (component != null) {
            popup.showUnderneathOf(component)
        } else {
            popup.showInBestPositionFor(dataContext)
        }
    }
    
    private class SelectDeviceAction(
        private val device: HarmonyDevice,
        private val project: com.intellij.openapi.project.Project
    ) : AnAction(device.displayName, null, device.getIcon()) {
        
        override fun actionPerformed(e: AnActionEvent) {
            DeviceService.getInstance(project).setSelectedDevice(device)
            println("Device selected: ${device.displayName}")
        }
        
        override fun getActionUpdateThread(): ActionUpdateThread {
            return ActionUpdateThread.BGT
        }
    }
}
