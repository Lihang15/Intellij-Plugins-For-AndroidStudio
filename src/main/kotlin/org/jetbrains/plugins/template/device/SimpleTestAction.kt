package org.jetbrains.plugins.template.device

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages

/**
 * 简单测试 - 不使用反射，直接测试设备检测
 */
class SimpleTestAction : AnAction("Simple Device Test", "Simple test without reflection", null), DumbAware {
    
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
        println("=== SimpleTestAction TRIGGERED ===")
        println("========================================")
        
        try {
            // 1. 直接测试 HDC
            println("Step 1: Testing HDC directly...")
            val hdcPath = "/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc"
            val hdcExecutor = HdcCommandExecutor(hdcPath)
            val hdcDevices = hdcExecutor.listDevices()
            
            println("HDC found ${hdcDevices.size} devices")
            hdcDevices.forEach { device ->
                println("  - ${device.displayName} (${device.deviceId})")
            }
            
            // 2. 检查 DeviceService
            println("\nStep 2: Checking DeviceService...")
            val deviceService = DeviceService.getInstance(project)
            val serviceDevices = deviceService.getConnectedDevices()
            val selectedDevice = deviceService.getSelectedDevice()
            val status = deviceService.getStatus()
            
            println("DeviceService status: $status")
            println("DeviceService has ${serviceDevices.size} devices")
            serviceDevices.forEach { device ->
                println("  - ${device.displayName} (${device.deviceId})")
            }
            println("Selected device: ${selectedDevice?.displayName ?: "None"}")
            
            // 3. 获取调试信息
            println("\nStep 3: Getting debug info...")
            val debugInfo = deviceService.getDebugInfo()
            println(debugInfo)
            
            // 4. 构建报告
            val report = buildString {
                appendLine("Simple Device Test Results")
                appendLine("=" .repeat(50))
                appendLine()
                
                appendLine("1. HDC Direct Test:")
                appendLine("   Devices found: ${hdcDevices.size}")
                if (hdcDevices.isEmpty()) {
                    appendLine("   ❌ No devices detected by HDC")
                } else {
                    appendLine("   ✅ HDC is working")
                    hdcDevices.forEach { device ->
                        appendLine("      - ${device.displayName}")
                    }
                }
                appendLine()
                
                appendLine("2. DeviceService State:")
                appendLine("   Status: $status")
                appendLine("   Devices in service: ${serviceDevices.size}")
                if (serviceDevices.isEmpty()) {
                    appendLine("   ❌ DeviceService has no devices")
                } else {
                    appendLine("   ✅ DeviceService has devices")
                    serviceDevices.forEach { device ->
                        appendLine("      - ${device.displayName}")
                    }
                }
                appendLine("   Selected: ${selectedDevice?.displayName ?: "None"}")
                appendLine()
                
                appendLine("3. Debug Info:")
                debugInfo.lines().forEach { line ->
                    appendLine("   $line")
                }
                appendLine()
                
                appendLine("4. Analysis:")
                when {
                    hdcDevices.isEmpty() -> {
                        appendLine("   ❌ Problem: No devices connected")
                        appendLine("   Solution: Connect a HarmonyOS device or start emulator")
                    }
                    serviceDevices.isEmpty() && hdcDevices.isNotEmpty() -> {
                        appendLine("   ❌ Problem: HDC works but DeviceService doesn't have devices")
                        appendLine("   This means pollDevices() is not executing!")
                        appendLine("   Solution: Check if SimpleDevicePoller is running")
                    }
                    serviceDevices.isNotEmpty() -> {
                        appendLine("   ✅ Everything is working!")
                        appendLine("   Devices are loaded in DeviceService")
                    }
                }
            }
            
            println(report)
            
            Messages.showMessageDialog(
                project,
                report,
                "Simple Device Test",
                if (serviceDevices.isNotEmpty()) Messages.getInformationIcon() else Messages.getWarningIcon()
            )
            
        } catch (e: Exception) {
            val errorMsg = "Error: ${e.message}\n\n${e.stackTraceToString()}"
            println("!!! ERROR in SimpleTestAction:")
            println(errorMsg)
            
            Messages.showErrorDialog(
                project,
                errorMsg,
                "Simple Test Error"
            )
        }
        
        println("========================================")
        println("=== SimpleTestAction COMPLETE ===")
        println("========================================")
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
