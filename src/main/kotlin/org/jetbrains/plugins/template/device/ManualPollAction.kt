package org.jetbrains.plugins.template.device

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages

/**
 * 手动触发设备轮询 - 用于调试
 */
class ManualPollAction : AnAction("Manual Device Poll", "Manually trigger device polling", null), DumbAware {
    
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
        println("=== ManualPollAction TRIGGERED ===")
        println("========================================")
        
        try {
            val deviceService = DeviceService.getInstance(project)
            
            println("DeviceService instance: $deviceService")
            println("DeviceService status: ${deviceService.getStatus()}")
            
            // 直接调用 HDC 测试
            val hdcPath = "/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc"
            val hdcExecutor = HdcCommandExecutor(hdcPath)
            
            println("Testing HDC directly...")
            val devices = hdcExecutor.listDevices()
            println("HDC returned ${devices.size} devices:")
            devices.forEach { device ->
                println("  - ${device.displayName} (${device.deviceId})")
            }
            
            // 触发刷新
            println("Calling deviceService.refresh()...")
            deviceService.refresh()
            
            // 等待一下
            Thread.sleep(2000)
            
            // 检查结果
            val connectedDevices = deviceService.getConnectedDevices()
            val selectedDevice = deviceService.getSelectedDevice()
            
            val report = buildString {
                appendLine("Manual Poll Results:")
                appendLine()
                appendLine("HDC Direct Test:")
                appendLine("  Devices found: ${devices.size}")
                devices.forEach { device ->
                    appendLine("    - ${device.displayName}")
                }
                appendLine()
                appendLine("DeviceService State:")
                appendLine("  Status: ${deviceService.getStatus()}")
                appendLine("  Connected devices: ${connectedDevices.size}")
                connectedDevices.forEach { device ->
                    appendLine("    - ${device.displayName}")
                }
                appendLine("  Selected device: ${selectedDevice?.displayName ?: "None"}")
                appendLine()
                appendLine("Debug Info:")
                deviceService.getDebugInfo().lines().forEach { line ->
                    appendLine("  $line")
                }
            }
            
            println(report)
            
            Messages.showMessageDialog(
                project,
                report,
                "Manual Poll Results",
                Messages.getInformationIcon()
            )
            
        } catch (e: Exception) {
            val errorMsg = "Error: ${e.message}\n\n${e.stackTraceToString()}"
            println("!!! ERROR in ManualPollAction:")
            println(errorMsg)
            
            Messages.showErrorDialog(
                project,
                errorMsg,
                "Manual Poll Error"
            )
        }
        
        println("========================================")
        println("=== ManualPollAction COMPLETE ===")
        println("========================================")
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
