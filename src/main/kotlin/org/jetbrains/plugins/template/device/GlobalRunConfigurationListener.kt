package org.jetbrains.plugins.template.device

import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.application.ApplicationManager

/**
 * 全局运行配置监听器
 * 当运行配置切换时，强制刷新所有 Action 的 UI
 */
class GlobalRunConfigurationListener : RunManagerListener {
    
    override fun runConfigurationSelected(settings: RunnerAndConfigurationSettings?) {
        println("========================================")
        println("=== GlobalRunConfigurationListener.runConfigurationSelected() ===")
        println("=== Configuration: ${settings?.name} ===")
        println("=== Type: ${settings?.configuration?.javaClass?.simpleName} ===")
        println("========================================")
        
        // 强制触发所有 Action 的 update() 方法
        ApplicationManager.getApplication().invokeLater {
            val tracker = ActivityTracker.getInstance()
            tracker.inc()
            println("=== ActivityTracker incremented ===")
        }
    }
    
    override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
        println("=== Run configuration added: ${settings.name} ===")
    }
    
    override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
        println("=== Run configuration removed: ${settings.name} ===")
    }
    
    override fun runConfigurationChanged(settings: RunnerAndConfigurationSettings) {
        println("=== Run configuration changed: ${settings.name} ===")
    }
}
