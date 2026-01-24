package org.jetbrains.plugins.template.device

import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import org.jetbrains.plugins.template.cpp.MyMainCppRunConfiguration
import java.io.File

/**
 * 诊断工具 - 检查设备列表加载问题
 */
class DiagnosticAction : AnAction("Diagnose Device Loading", "Check why device list is not loading", null), DumbAware {
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            Messages.showErrorDialog("No project found", "Diagnostic Error")
            return
        }
        
        val report = buildString {
            appendLine("=== HarmonyOS Device Loading Diagnostic Report ===")
            appendLine()
            
            // 1. 检查 HDC 路径
            appendLine("1. HDC Availability Check:")
            val hdcPath = "/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc"
            val hdcFile = File(hdcPath)
            appendLine("   HDC Path: $hdcPath")
            appendLine("   Exists: ${hdcFile.exists()}")
            appendLine("   Executable: ${hdcFile.canExecute()}")
            
            if (hdcFile.exists()) {
                try {
                    val executor = HdcCommandExecutor(hdcPath)
                    val devices = executor.listDevices()
                    appendLine("   HDC Command Works: YES")
                    appendLine("   Devices Found: ${devices.size}")
                    devices.forEach { device ->
                        appendLine("     - ${device.displayName} (${device.deviceId})")
                    }
                } catch (e: Exception) {
                    appendLine("   HDC Command Works: NO")
                    appendLine("   Error: ${e.message}")
                }
            }
            appendLine()
            
            // 2. 检查 DeviceService
            appendLine("2. DeviceService Status:")
            try {
                val deviceService = DeviceService.getInstance(project)
                appendLine("   Service Instance: OK")
                appendLine("   Status: ${deviceService.getStatus()}")
                appendLine("   Connected Devices: ${deviceService.getConnectedDevices().size}")
                appendLine("   Selected Device: ${deviceService.getSelectedDevice()?.displayName ?: "None"}")
                appendLine()
                appendLine("   Debug Info:")
                deviceService.getDebugInfo().lines().forEach { line ->
                    appendLine("     $line")
                }
            } catch (e: Exception) {
                appendLine("   Service Instance: FAILED")
                appendLine("   Error: ${e.message}")
            }
            appendLine()
            
            // 3. 检查运行配置
            appendLine("3. Run Configuration Check:")
            val runManager = RunManager.getInstance(project)
            val allConfigs = runManager.allSettings
            appendLine("   Total Configurations: ${allConfigs.size}")
            
            val myMainAppConfigs = allConfigs.filter { it.configuration is MyMainCppRunConfiguration }
            appendLine("   MyMainApp Configurations: ${myMainAppConfigs.size}")
            myMainAppConfigs.forEach { config ->
                appendLine("     - ${config.name}")
            }
            
            val selectedConfig = runManager.selectedConfiguration
            appendLine("   Selected Configuration: ${selectedConfig?.name ?: "None"}")
            appendLine("   Is MyMainApp: ${selectedConfig?.configuration is MyMainCppRunConfiguration}")
            appendLine()
            
            // 4. 检查 my_main.cpp 文件
            appendLine("4. Project File Check:")
            val basePath = project.basePath
            if (basePath != null) {
                val myMainCpp = File(basePath, "my_main.cpp")
                appendLine("   my_main.cpp exists: ${myMainCpp.exists()}")
                appendLine("   Project path: $basePath")
            } else {
                appendLine("   Project basePath: NULL")
            }
            appendLine()
            
            // 5. 建议
            appendLine("5. Recommendations:")
            if (!hdcFile.exists()) {
                appendLine("   ⚠️  HDC not found - Install DevEco Studio or configure HDC path")
            }
            if (myMainAppConfigs.isEmpty()) {
                appendLine("   ⚠️  No MyMainApp configuration - Create one manually or restart project")
            }
            if (selectedConfig?.configuration !is MyMainCppRunConfiguration) {
                appendLine("   ⚠️  MyMainApp not selected - Select it from Run Configuration dropdown")
            }
            
            val deviceService = try {
                DeviceService.getInstance(project)
            } catch (e: Exception) {
                null
            }
            
            if (deviceService != null && deviceService.getStatus() == DeviceService.State.INACTIVE) {
                appendLine("   ⚠️  DeviceService is INACTIVE - Check HDC availability")
            }
            
            if (deviceService != null && deviceService.getConnectedDevices().isEmpty()) {
                appendLine("   ℹ️  No devices connected - Connect a HarmonyOS device or emulator")
            }
            
            appendLine()
            appendLine("=== End of Report ===")
        }
        
        println(report)
        
        // 显示报告
        Messages.showMessageDialog(
            project,
            report,
            "Device Loading Diagnostic",
            Messages.getInformationIcon()
        )
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
