package org.jetbrains.plugins.template.device

import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.template.cpp.MyMainCppRunConfiguration

/**
 * 监听运行配置变化，当切换到 MyMainApp 时更新 ExecutionTarget
 */
class RunConfigurationListener(private val project: Project) : RunManagerListener {
    
    /**
     * 初始化时检查并设置默认设备
     */
    fun initialize() {
        println("=== RunConfigurationListener.initialize() ===")
        val runManager = RunManager.getInstance(project)
        val selectedConfiguration = runManager.selectedConfiguration
        
        if (selectedConfiguration != null) {
            println("Current selected configuration: ${selectedConfiguration.name}")
            // 触发一次配置选择逻辑
            runConfigurationSelected(selectedConfiguration)
        } else {
            println("No configuration selected")
        }
    }
    
    override fun runConfigurationSelected(settings: RunnerAndConfigurationSettings?) {
        println("=== RunConfigurationListener.runConfigurationSelected() ===")
        println("Selected configuration: ${settings?.name}")
        println("Configuration type: ${settings?.configuration?.javaClass?.simpleName}")
        
        if (settings == null) return
        
        val configuration = settings.configuration
        
        // 当选择 MyMainCppRunConfiguration 时
        if (configuration is MyMainCppRunConfiguration) {
            println("MyMainCpp configuration selected, checking ExecutionTargets...")
            
            val targetManager = ExecutionTargetManager.getInstance(project)
            val allTargets = targetManager.getTargetsFor(configuration)
            
            println("Available targets for MyMainCpp: ${allTargets.size}")
            allTargets.forEach { target ->
                println("  - ${target.displayName} (${target.id}) - ${target.javaClass.simpleName}")
            }
            
            // 获取 HarmonyOS 设备
            val harmonyTargets = allTargets.filterIsInstance<HarmonyExecutionTarget>()
            println("HarmonyOS targets: ${harmonyTargets.size}")
            
            // 如果当前 active target 不是 HarmonyOS 设备，且有可用的 HarmonyOS 设备
            val currentTarget = targetManager.activeTarget
            println("Current active target: ${currentTarget.displayName} (${currentTarget.javaClass.simpleName})")
            
            if (currentTarget !is HarmonyExecutionTarget && harmonyTargets.isNotEmpty()) {
                // 尝试切换到第一个 HarmonyOS 设备
                val firstHarmony = harmonyTargets.first()
                println("Attempting to switch to HarmonyOS device: ${firstHarmony.displayName}")
                
                try {
                    targetManager.activeTarget = firstHarmony
                    println("Successfully switched to: ${targetManager.activeTarget.displayName}")
                } catch (e: Exception) {
                    println("Failed to switch target: ${e.message}")
                    e.printStackTrace()
                }
            } else if (currentTarget is HarmonyExecutionTarget) {
                println("Already using HarmonyOS device: ${currentTarget.displayName}")
            } else {
                println("No HarmonyOS devices available")
            }
        } else {
            println("Non-MyMainCpp configuration selected: ${configuration.javaClass.simpleName}")
        }
        
        println("=== RunConfigurationListener.runConfigurationSelected() END ===")
    }
}
