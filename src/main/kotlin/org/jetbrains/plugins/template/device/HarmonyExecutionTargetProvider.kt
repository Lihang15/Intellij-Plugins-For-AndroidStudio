package org.jetbrains.plugins.template.device

import com.intellij.execution.ExecutionTarget
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
        
        println("Found ${devices.size} devices from DeviceService")
        devices.forEach { device ->
            println("  - ${device.displayName} (${device.deviceId})")
        }
        
        val targets = devices.map { device ->
            val target = HarmonyExecutionTarget(device)
            println("Created target: $target")
            target
        }
        
        println("Returning ${targets.size} execution targets")
        return targets
    }
}
