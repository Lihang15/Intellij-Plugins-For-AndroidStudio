package org.jetbrains.plugins.template.device

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.icons.AllIcons
import org.jetbrains.plugins.template.cpp.MyMainCppRunConfiguration
import javax.swing.Icon

/**
 * Represents a HarmonyOS device as an execution target.
 * This allows devices to appear in the Android device dropdown.
 */
class HarmonyExecutionTarget(
    private val device: HarmonyDevice
) : ExecutionTarget() {
    
    override fun getId(): String {
        return "harmony-device:${device.deviceId}"
    }
    
    override fun getDisplayName(): String {
        return device.displayName
    }
    
    override fun getIcon(): Icon {
        return AllIcons.Debugger.ThreadRunning
    }
    
    override fun canRun(configuration: RunConfiguration): Boolean {
        println("=== HarmonyExecutionTarget.canRun() CALLED ===")
        println("Configuration: ${configuration.name}, type: ${configuration::class.simpleName}")
        val result = configuration is MyMainCppRunConfiguration
        println("Can run: $result")
        return result
    }
    
    /**
     * Gets the underlying HarmonyDevice
     */
    fun getDevice(): HarmonyDevice {
        return device
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HarmonyExecutionTarget) return false
        return device.deviceId == other.device.deviceId
    }
    
    override fun hashCode(): Int {
        return device.deviceId.hashCode()
    }
    
    override fun toString(): String {
        return "HarmonyExecutionTarget(${device.displayName})"
    }
}
