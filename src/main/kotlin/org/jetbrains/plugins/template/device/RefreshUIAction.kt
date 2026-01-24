package org.jetbrains.plugins.template.device

import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages

/**
 * 强制刷新 UI - 触发所有 Action 的 update()
 */
class RefreshUIAction : AnAction("Refresh Device UI", "Force refresh device selector UI", null), DumbAware {
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            Messages.showErrorDialog("No project found", "Error")
            return
        }
        
        println("========================================")
        println("=== RefreshUIAction TRIGGERED ===")
        println("========================================")
        
        try {
            val deviceService = DeviceService.getInstance(project)
            val devices = deviceService.getConnectedDevices()
            val selectedDevice = deviceService.getSelectedDevice()
            
            println("Current state:")
            println("  Devices: ${devices.size}")
            devices.forEach { device ->
                println("    - ${device.displayName}")
            }
            println("  Selected: ${selectedDevice?.displayName ?: "None"}")
            
            // 触发 ActivityTracker 来强制刷新所有 Action
            println("Incrementing ActivityTracker...")
            ActivityTracker.getInstance().inc()
            
            println("ActivityTracker incremented")
            
            // 等待一下让 UI 更新
            Thread.sleep(500)
            
            val report = buildString {
                appendLine("UI Refresh Triggered")
                appendLine("=" .repeat(50))
                appendLine()
                appendLine("DeviceService State:")
                appendLine("  Devices: ${devices.size}")
                devices.forEach { device ->
                    appendLine("    - ${device.displayName}")
                }
                appendLine("  Selected: ${selectedDevice?.displayName ?: "None"}")
                appendLine()
                appendLine("Action Taken:")
                appendLine("  ✓ ActivityTracker.inc() called")
                appendLine("  ✓ This should trigger all Action.update() methods")
                appendLine()
                appendLine("Next Steps:")
                appendLine("  1. Check if device selector appears in toolbar")
                appendLine("  2. Check console for 'UnifiedDeviceSelectorAction.update()' logs")
                appendLine("  3. If still not visible, check Action visibility conditions")
            }
            
            println(report)
            
            Messages.showMessageDialog(
                project,
                report,
                "UI Refresh",
                Messages.getInformationIcon()
            )
            
        } catch (e: Exception) {
            val errorMsg = "Error: ${e.message}\n\n${e.stackTraceToString()}"
            println("!!! ERROR in RefreshUIAction:")
            println(errorMsg)
            
            Messages.showErrorDialog(
                project,
                errorMsg,
                "Refresh UI Error"
            )
        }
        
        println("========================================")
        println("=== RefreshUIAction COMPLETE ===")
        println("========================================")
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
