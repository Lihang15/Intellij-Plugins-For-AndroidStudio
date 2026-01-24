package org.jetbrains.plugins.template.device

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.ExecutionTargetProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.template.cpp.MyMainCppRunConfiguration

/**
 * Provides HarmonyOS devices as execution targets.
 * This makes devices appear in the Android device dropdown.
 */
class HarmonyExecutionTargetProvider : ExecutionTargetProvider() {
    
    init {
        println("=== HarmonyExecutionTargetProvider INITIALIZED ===")
    }
    
    override fun getTargets(project: Project, configuration: RunConfiguration): List<ExecutionTarget> {
        println("=== HarmonyExecutionTargetProvider.getTargets() CALLED ===")
        println("Project: ${project.name}")
        println("Configuration: ${configuration.name}, type: ${configuration::class.simpleName}")
        
        // Only provide targets for MyMainCpp configurations
        if (configuration !is MyMainCppRunConfiguration) {
            println("Not MyMainCpp configuration, returning empty list")
            return emptyList()
        }
        
        val deviceService = DeviceService.getInstance(project)
        val devices = deviceService.getConnectedDevices()
        val status = deviceService.getStatus()
        
        println("DeviceService status: $status")
        println("Found ${devices.size} devices from DeviceService")
        devices.forEach { device ->
            println("  - ${device.displayName} (${device.deviceId})")
        }
        
        // 如果设备服务还在加载中，添加一个监听器，等设备加载完成后更新
        if (status == DeviceService.State.LOADING && devices.isEmpty()) {
            println("DeviceService still loading, registering update listener")
            registerUpdateListener(project, deviceService)
        }
        
        val targets = devices.map { device ->
            val target = HarmonyExecutionTarget(device)
            println("Created target: $target")
            target
        }
        
        println("Returning ${targets.size} execution targets")
        return targets
    }
    
    /**
     * 注册一个监听器，当设备加载完成后更新 ExecutionTargetManager
     */
    private fun registerUpdateListener(project: Project, deviceService: DeviceService) {
        var listener: (() -> Unit)? = null
        listener = {
            println("Device update listener triggered, updating ExecutionTargetManager")
            val targetManager = ExecutionTargetManager.getInstance(project)
            targetManager.update()
            
            // 移除监听器，避免重复触发
            listener?.let { deviceService.removeListener(it) }
        }
        deviceService.addListener(listener)
    }
}
