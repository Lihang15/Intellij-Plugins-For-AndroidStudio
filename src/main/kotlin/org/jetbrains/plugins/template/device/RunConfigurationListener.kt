package org.jetbrains.plugins.template.device

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.template.runconfig.HarmonyRunConfiguration

/**
 * 监听运行配置变化
 * 注意：此监听器保留用于未来扩展，当前主要用于日志记录
 */
class RunConfigurationListener(private val project: Project) : RunManagerListener {
    
    /**
     * 初始化时检查当前配置
     */
    fun initialize() {
        println("=== RunConfigurationListener.initialize() ===")
        val runManager = RunManager.getInstance(project)
        val selectedConfiguration = runManager.selectedConfiguration
        
        if (selectedConfiguration != null) {
            println("Current selected configuration: ${selectedConfiguration.name}")
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
        
        // 当选择 HarmonyRunConfiguration 时
        if (configuration is HarmonyRunConfiguration) {
            println("Harmony configuration selected")
            
            // 检查是否有选中的设备
            val deviceService = DeviceService.getInstance(project)
            val selectedDevice = deviceService.getSelectedDevice()
            val devices = deviceService.getConnectedDevices()
            
            println("Connected devices: ${devices.size}")
            println("Selected device: ${selectedDevice?.displayName ?: "None"}")
            
            if (selectedDevice == null && devices.isNotEmpty()) {
                // 自动选择第一个设备
                println("Auto-selecting first device: ${devices.first().displayName}")
                deviceService.setSelectedDevice(devices.first())
            }
        } else {
            println("Non-Harmony configuration selected: ${configuration.javaClass.simpleName}")
        }
        
        println("=== RunConfigurationListener.runConfigurationSelected() END ===")
    }
}
