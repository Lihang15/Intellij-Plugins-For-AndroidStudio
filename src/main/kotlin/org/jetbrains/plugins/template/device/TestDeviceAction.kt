package org.jetbrains.plugins.template.device

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

/**
 * Test action to manually check device status
 */
class TestDeviceAction : AnAction("Test Device List") {
    
    override fun actionPerformed(e: AnActionEvent) {
        println("=== TestDeviceAction.actionPerformed() CALLED ===")
        
        val project = e.project
        println("Project: $project")
        
        if (project == null) {
            println("!!! No project found")
            return
        }
        
        println("Getting DeviceService instance...")
        val deviceService = DeviceService.getInstance(project)
        println("DeviceService instance: $deviceService")
        
        // Trigger a refresh
        println("Calling refresh()...")
        deviceService.refresh()
        
        // Wait a moment for the refresh to complete
        println("Waiting 500ms...")
        Thread.sleep(500)
        
        println("Getting device info...")
        val devices = deviceService.getConnectedDevices()
        val selectedDevice = deviceService.getSelectedDevice()
        val status = deviceService.getStatus()
        
        println("Building message...")
        val message = buildString {
            appendLine("=== Device Service Status ===")
            appendLine("Status: $status")
            appendLine("Connected Devices: ${devices.size}")
            devices.forEachIndexed { index, device ->
                appendLine("  ${index + 1}. ${device.displayName} (${device.deviceId})")
            }
            appendLine()
            appendLine("Selected Device: ${selectedDevice?.displayName ?: "None"}")
            appendLine()
            appendLine("=== Debug Info ===")
            appendLine(deviceService.getDebugInfo())
            appendLine()
            appendLine("=== Direct HDC Test ===")
            
            val hdcPath = "/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc"
            val executor = HdcCommandExecutor(hdcPath)
            appendLine("HDC Available: ${executor.isHdcAvailable()}")
            
            if (executor.isHdcAvailable()) {
                println("Calling HDC directly...")
                val testDevices = executor.listDevices()
                appendLine("Direct HDC call returned ${testDevices.size} devices:")
                testDevices.forEach { device ->
                    appendLine("  - ${device.displayName} (${device.deviceId})")
                }
            }
            
            appendLine()
            appendLine("=== Troubleshooting ===")
            if (devices.isEmpty() && executor.isHdcAvailable()) {
                appendLine("⚠️ HDC works but poller shows 0 devices!")
                appendLine("Check IDE logs: Help > Show Log in Finder")
                appendLine("Search for: SimpleDevicePoller or DeviceService")
            }
        }
        
        println("Showing dialog...")
        Messages.showMessageDialog(
            project,
            message,
            "Device Status",
            Messages.getInformationIcon()
        )
        println("=== TestDeviceAction.actionPerformed() COMPLETE ===")
    }
}
