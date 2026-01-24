package org.jetbrains.plugins.template.device

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.icons.AllIcons

/**
 * 在 Tools 菜单中显示设备选择器的 Action
 * 用于测试和手动触发设备选择
 */
class ShowDeviceSelectorAction : AnAction("Show HarmonyOS Device Selector", "Show device selection popup", AllIcons.Debugger.ThreadRunning), DumbAware {
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val deviceService = DeviceService.getInstance(project)
        val devices = deviceService.getConnectedDevices()
        
        println("=== ShowDeviceSelectorAction ===")
        println("Devices: ${devices.size}")
        devices.forEach { println("  - ${it.displayName}") }
        
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
                e.dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                false
            )
        
        val component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
        if (component != null) {
            popup.showUnderneathOf(component)
        } else {
            popup.showInBestPositionFor(e.dataContext)
        }
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
}
