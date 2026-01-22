package org.jetbrains.plugins.template.device

import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.RunManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

/**
 * 测试 ExecutionTarget 是否正常工作
 */
class TestExecutionTargetAction : AnAction(), DumbAware {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        println("=== TestExecutionTargetAction START ===")
        
        // 获取当前选中的运行配置
        val runManager = RunManager.getInstance(project)
        val selectedConfig = runManager.selectedConfiguration
        
        println("Selected configuration: ${selectedConfig?.name}")
        println("Configuration type: ${selectedConfig?.configuration?.javaClass?.simpleName}")
        
        if (selectedConfig == null) {
            showNotification(project, "没有选中的运行配置", NotificationType.WARNING)
            return
        }
        
        // 获取 ExecutionTargetManager
        val targetManager = ExecutionTargetManager.getInstance(project)
        println("ExecutionTargetManager: $targetManager")
        
        // 获取当前活动的 target
        val activeTarget = targetManager.activeTarget
        println("Active target: ${activeTarget.id} - ${activeTarget.displayName}")
        
        // 获取所有可用的 targets
        val allTargets = targetManager.getTargetsFor(selectedConfig.configuration)
        println("Available targets count: ${allTargets.size}")
        
        val message = buildString {
            appendLine("运行配置: ${selectedConfig.name}")
            appendLine("配置类型: ${selectedConfig.configuration.javaClass.simpleName}")
            appendLine("当前目标: ${activeTarget.displayName}")
            appendLine("可用目标数量: ${allTargets.size}")
            appendLine()
            appendLine("可用目标列表:")
            allTargets.forEach { target ->
                appendLine("  - ${target.displayName} (${target.id})")
                println("  Target: ${target.displayName} (${target.id}) - ${target.javaClass.simpleName}")
            }
        }
        
        showNotification(project, message, NotificationType.INFORMATION)
        
        println("=== TestExecutionTargetAction END ===")
    }
    
    private fun showNotification(project: com.intellij.openapi.project.Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("HarmonyOS Device")
            .createNotification(
                "ExecutionTarget 测试",
                content,
                type
            )
            .notify(project)
    }
}
