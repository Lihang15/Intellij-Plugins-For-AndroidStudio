package org.jetbrains.plugins.template.device

import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import org.jetbrains.plugins.template.cpp.MyMainCppRunConfiguration

/**
 * 检查当前运行配置的 Action
 */
class CheckRunConfigAction : AnAction("Check Run Configuration", "Check current run configuration", null), DumbAware {
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val runManager = RunManager.getInstance(project)
        val selectedConfiguration = runManager.selectedConfiguration
        
        val configName = selectedConfiguration?.name ?: "null"
        val configType = selectedConfiguration?.configuration?.javaClass?.simpleName ?: "null"
        val isMyMainApp = selectedConfiguration?.configuration is MyMainCppRunConfiguration
        
        val message = """
            Current Run Configuration:
            
            Name: $configName
            Type: $configType
            Is MyMainCppRunConfiguration: $isMyMainApp
            
            Full class: ${selectedConfiguration?.configuration?.javaClass?.name ?: "null"}
        """.trimIndent()
        
        println("=== CheckRunConfigAction ===")
        println(message)
        
        Messages.showMessageDialog(
            project,
            message,
            "Run Configuration Info",
            Messages.getInformationIcon()
        )
    }
}
