package org.jetbrains.plugins.template.device

import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import javax.swing.Icon

/**
 * 最简单的工具栏设备选择器
 * 不使用 CustomComponentAction，只使用标准的 AnAction
 * 只在 MyMainApp 运行配置时显示
 */
class SimpleToolbarDeviceSelectorAction : AnAction(), DumbAware {
    
    private val knownProjects = mutableSetOf<Project>()
    
    init {
        println("=== SimpleToolbarDeviceSelectorAction INITIALIZED ===")
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        println("=== SimpleToolbarDeviceSelectorAction.actionPerformed() ===")
        val project = e.project ?: return
        showDevicePopup(e.dataContext, e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT))
    }
    
    override fun update(e: AnActionEvent) {
        println("=== SimpleToolbarDeviceSelectorAction.update() ===")
        
        val project = e.project
        if (project == null) {
            e.presentation.isVisible = false
            return
        }
        
        // 注册运行配置监听器（仅首次）
        if (!knownProjects.contains(project)) {
            knownProjects.add(project)
            
            // 监听运行配置变化
            project.messageBus.connect().subscribe(RunManagerListener.TOPIC, object : RunManagerListener {
                override fun runConfigurationSelected(settings: RunnerAndConfigurationSettings?) {
                    println("=== Run configuration changed: ${settings?.name} ===")
                    // 触发 UI 更新
                    ApplicationManager.getApplication().invokeLater {
                        ActivityTracker.getInstance().inc()
                    }
                }
            })
            
            println("=== Registered RunManagerListener for project ${project.name} ===")
        }
        
        // 检查当前选中的运行配置
        val runManager = com.intellij.execution.RunManager.getInstance(project)
        val selectedConfiguration = runManager.selectedConfiguration
        val isMyMainApp = selectedConfiguration?.configuration is org.jetbrains.plugins.template.cpp.MyMainCppRunConfiguration
        
        println("Selected configuration: ${selectedConfiguration?.name}, isMyMainApp: $isMyMainApp")
        
        // 只在 MyMainApp 配置时显示
        if (!isMyMainApp) {
            println("Not MyMainApp, hiding")
            e.presentation.isVisible = false
            return
        }
        
        println("Is MyMainApp, showing")
        e.presentation.isVisible = true
        e.presentation.isEnabled = true
        
        // 获取当前设备状态
        val deviceService = DeviceService.getInstance(project)
        val devices = deviceService.getConnectedDevices()
        val selectedDevice = deviceService.getSelectedDevice()
        
        println("Project: ${project.name}, Devices: ${devices.size}, Selected: ${selectedDevice?.displayName}")
        
        // 设置显示文本和图标
        val text: String
        val icon: Icon
        
        when {
            devices.isEmpty() -> {
                icon = AllIcons.Debugger.ThreadRunning
                text = "No HarmonyOS Devices"
            }
            selectedDevice == null -> {
                icon = AllIcons.General.Warning
                text = "Select HarmonyOS Device"
            }
            else -> {
                icon = selectedDevice.getIcon()
                text = selectedDevice.displayName
            }
        }
        
        e.presentation.text = text
        e.presentation.icon = icon
        e.presentation.description = "HarmonyOS Device: $text"
        
        println("Set: text='$text', icon=$icon, visible=true, enabled=true")
    }
    
    private fun showDevicePopup(dataContext: DataContext, component: java.awt.Component?) {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return
        val deviceService = DeviceService.getInstance(project)
        val devices = deviceService.getConnectedDevices()
        
        println("Showing device popup, devices: ${devices.size}")
        
        val group = DefaultActionGroup()
        
        if (devices.isEmpty()) {
            group.add(object : AnAction("No Devices Connected") {
                override fun actionPerformed(e: AnActionEvent) {}
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = false
                }
                override fun getActionUpdateThread() = ActionUpdateThread.BGT
            })
        } else {
            devices.forEach { device ->
                group.add(object : AnAction(device.displayName, null, device.getIcon()) {
                    override fun actionPerformed(e: AnActionEvent) {
                        deviceService.setSelectedDevice(device)
                        println("Device selected: ${device.displayName}")
                    }
                    override fun getActionUpdateThread() = ActionUpdateThread.BGT
                })
            }
        }
        
        val popup = JBPopupFactory.getInstance()
            .createActionGroupPopup(
                "Select HarmonyOS Device",
                group,
                dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                false
            )
        
        if (component != null) {
            popup.showUnderneathOf(component)
        } else {
            popup.showInBestPositionFor(dataContext)
        }
    }
}
