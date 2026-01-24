package org.jetbrains.plugins.template.device

import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import org.jetbrains.plugins.template.cpp.MyMainCppRunConfiguration

/**
 * 列出所有运行配置的 Action
 */
class ListRunConfigsAction : AnAction("List All Run Configurations", "List all run configurations", null), DumbAware {
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val runManager = RunManager.getInstance(project)
        val allConfigurations = runManager.allSettings
        val selectedConfiguration = runManager.selectedConfiguration
        
        val configList = allConfigurations.joinToString("\n") { config ->
            val isSelected = config == selectedConfiguration
            val isMyMainApp = config.configuration is MyMainCppRunConfiguration
            val marker = if (isSelected) "→ " else "  "
            "$marker${config.name} (${config.configuration.javaClass.simpleName}) [MyMainApp: $isMyMainApp]"
        }
        
        val message = """
            Total Configurations: ${allConfigurations.size}
            Selected: ${selectedConfiguration?.name ?: "null"}
            
            All Configurations:
            $configList
        """.trimIndent()
        
        println("=== ListRunConfigsAction ===")
        println(message)
        
        Messages.showMessageDialog(
            project,
            message,
            "All Run Configurations",
            Messages.getInformationIcon()
        )
    }
}
