package org.jetbrains.plugins.template.device

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages

/**
 * 强制启动轮询器 - 用于调试
 */
class ForceStartPollerAction : AnAction("Force Start Poller", "Force start the device poller", null), DumbAware {
    
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
        println("=== ForceStartPollerAction TRIGGERED ===")
        println("========================================")
        
        try {
            val deviceService = DeviceService.getInstance(project)
            
            println("Getting DeviceService private fields via reflection...")
            
            // 使用反射访问私有字段
            val pollerField = DeviceService::class.java.getDeclaredField("poller")
            pollerField.isAccessible = true
            val poller = pollerField.get(deviceService) as? SimpleDevicePoller
            
            println("Poller instance: $poller")
            println("Poller running: ${poller?.isRunning()}")
            
            if (poller == null) {
                val report = "Poller is NULL!\n\nThis means SimpleDevicePoller was never created.\nCheck DeviceService.startPolling() method."
                println(report)
                Messages.showErrorDialog(project, report, "Poller Not Created")
                return
            }
            
            if (!poller.isRunning()) {
                println("Poller not running, calling start()...")
                poller.start()
                Thread.sleep(1000)
            }
            
            // 检查 scheduler - 避免直接访问 java.base 内部类
            var schedulerInfo = "Unable to access"
            var isShutdown = false
            var isTerminated = false
            
            try {
                val schedulerField = SimpleDevicePoller::class.java.getDeclaredField("scheduler")
                schedulerField.isAccessible = true
                val scheduler = schedulerField.get(poller)
                
                println("Scheduler: $scheduler")
                
                if (scheduler != null) {
                    schedulerInfo = scheduler.toString()
                    // 使用 ScheduledExecutorService 接口方法，而不是反射
                    if (scheduler is java.util.concurrent.ScheduledExecutorService) {
                        isShutdown = scheduler.isShutdown
                        isTerminated = scheduler.isTerminated
                        println("Scheduler shutdown: $isShutdown")
                        println("Scheduler terminated: $isTerminated")
                    }
                }
            } catch (e: Exception) {
                println("Could not access scheduler details: ${e.message}")
                schedulerInfo = "Access denied: ${e.message}"
            }
            
            // 尝试直接调用 pollDevices
            println("Attempting to call pollDevices() via reflection...")
            val pollDevicesMethod = SimpleDevicePoller::class.java.getDeclaredMethod("pollDevices")
            pollDevicesMethod.isAccessible = true
            pollDevicesMethod.invoke(poller)
            
            println("pollDevices() called successfully!")
            
            // 等待一下
            Thread.sleep(2000)
            
            // 检查结果
            val connectedDevices = deviceService.getConnectedDevices()
            val selectedDevice = deviceService.getSelectedDevice()
            
            val report = buildString {
                appendLine("Force Start Poller Results:")
                appendLine()
                appendLine("Poller State:")
                appendLine("  Instance: ${if (poller != null) "OK" else "NULL"}")
                appendLine("  Running: ${poller?.isRunning()}")
                appendLine()
                appendLine("Scheduler State:")
                appendLine("  Info: $schedulerInfo")
                appendLine("  Shutdown: $isShutdown")
                appendLine("  Terminated: $isTerminated")
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
                appendLine()
                appendLine("Action Taken:")
                appendLine("  ✓ Called pollDevices() directly via reflection")
                appendLine("  ✓ Waited 2 seconds for processing")
                appendLine()
                if (connectedDevices.isNotEmpty()) {
                    appendLine("✅ SUCCESS: Devices are now loaded!")
                } else {
                    appendLine("❌ FAILED: Devices still not loaded")
                    appendLine()
                    appendLine("This suggests the problem is in:")
                    appendLine("  1. pollDevices() execution")
                    appendLine("  2. onDevicesChanged() callback")
                    appendLine("  3. EDT invokeLater() not executing")
                }
            }
            
            println(report)
            
            Messages.showMessageDialog(
                project,
                report,
                "Force Start Poller Results",
                if (connectedDevices.isNotEmpty()) Messages.getInformationIcon() else Messages.getWarningIcon()
            )
            
        } catch (e: Exception) {
            val errorMsg = "Error: ${e.message}\n\n${e.stackTraceToString()}"
            println("!!! ERROR in ForceStartPollerAction:")
            println(errorMsg)
            
            Messages.showErrorDialog(
                project,
                errorMsg,
                "Force Start Poller Error"
            )
        }
        
        println("========================================")
        println("=== ForceStartPollerAction COMPLETE ===")
        println("========================================")
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
