package org.jetbrains.plugins.template.device

import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.RunManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

/**
 * 切换到 HarmonyOS 设备
 */
class SwitchToHarmonyDeviceAction : AnAction(), DumbAware {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        println("=== SwitchToHarmonyDeviceAction START ===")
        
        // 获取当前选中的运行配置
        val runManager = RunManager.getInstance(project)
        val selectedConfig = runManager.selectedConfiguration
        
        if (selectedConfig == null) {
            showNotification(project, "没有选中的运行配置", NotificationType.WARNING)
            return
        }
        
        println("Selected configuration: ${selectedConfig.name}")
        
        // 获取 ExecutionTargetManager
        val targetManager = ExecutionTargetManager.getInstance(project)
        
        // 获取所有可用的 targets
        val allTargets = targetManager.getTargetsFor(selectedConfig.configuration)
        val harmonyTargets = allTargets.filterIsInstance<HarmonyExecutionTarget>()
        
        println("Total targets: ${allTargets.size}")
        println("HarmonyOS targets: ${harmonyTargets.size}")
        
        if (harmonyTargets.isEmpty()) {
            showNotification(
                project,
                "没有找到 HarmonyOS 设备\n\n请确保：\n1. HarmonyOS 模拟器正在运行\n2. DeviceService 已检测到设备",
                NotificationType.WARNING
            )
            return
        }
        
        // 切换到第一个 HarmonyOS 设备
        val firstHarmonyTarget = harmonyTargets.first()
        println("Switching to: ${firstHarmonyTarget.displayName}")
        
        targetManager.activeTarget = firstHarmonyTarget
        
        println("Active target after switch: ${targetManager.activeTarget.displayName}")
        
        showNotification(
            project,
            "已切换到 HarmonyOS 设备\n\n" +
            "设备: ${firstHarmonyTarget.displayName}\n" +
            "ID: ${firstHarmonyTarget.id}\n\n" +
            "现在可以运行 MyMainApp 了",
            NotificationType.INFORMATION
        )
        
        println("=== SwitchToHarmonyDeviceAction END ===")
    }
    
    private fun showNotification(project: com.intellij.openapi.project.Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("HarmonyOS Device")
            .createNotification(
                "切换 HarmonyOS 设备",
                content,
                type
            )
            .notify(project)
    }
}
