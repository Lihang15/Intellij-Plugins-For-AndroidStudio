package org.jetbrains.plugins.template.device

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages

/**
 * 简单的测试 Action，用于验证工具栏是否工作
 */
class TestToolbarAction : AnAction("Test Toolbar", "Test if toolbar works", AllIcons.Actions.Execute), DumbAware {
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        println("========================================")
        println("=== TestToolbarAction.actionPerformed() CALLED ===")
        println("=== Project: ${e.project?.name} ===")
        println("========================================")
        
        Messages.showMessageDialog(
            e.project,
            "Toolbar Action Works!\nProject: ${e.project?.name}",
            "Test Toolbar",
            Messages.getInformationIcon()
        )
    }
    
    override fun update(e: AnActionEvent) {
        println("========================================")
        println("=== TestToolbarAction.update() CALLED ===")
        println("=== Project: ${e.project?.name} ===")
        println("========================================")
        
        e.presentation.isVisible = true
        e.presentation.isEnabled = true
    }
}
