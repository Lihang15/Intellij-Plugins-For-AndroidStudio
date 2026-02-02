package org.jetbrains.plugins.template.device

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Represents a HarmonyOS device or emulator.
 *
 * @property deviceId The unique identifier for the device (e.g., "127.0.0.1:5555")
 * @property displayName User-friendly name for display in UI
 * @property isEmulator Whether this device is an emulator (true) or physical device (false)
 */
data class HarmonyDevice(
    val deviceId: String,
    val displayName: String,
    val isEmulator: Boolean = true
) {
    /**
     * Returns the appropriate icon for this device
     */
    fun getIcon(): Icon {
        return if (isEmulator) {
            // AllIcons.Nodes.Controller  // 模拟器图标
            IconLoader.getIcon("/icons/device_emulator_green.svg", HarmonyDevice::class.java)
        } else {
            // 真机图标：可以使用自定义图标
            // 方式1：使用内置图标
            // AllIcons.Nodes.Controller
            
            // 方式2：使用自定义图标（取消下面注释即可）
             IconLoader.getIcon("/icons/device_physical_blue.svg", HarmonyDevice::class.java)
        }
    }
    
    companion object {
        /**
         * Creates a HarmonyDevice from a device ID string.
         * Automatically determines if it's an emulator and generates a display name.
         *
         * @param deviceId The device ID from HDC output
         * @return A new HarmonyDevice instance
         */
        fun fromDeviceId(deviceId: String): HarmonyDevice {
            val trimmedId = deviceId.trim()
            val isEmulator = trimmedId.startsWith("127.0.0.1:") || trimmedId.startsWith("localhost:")
            
            val displayName = when {
                isEmulator && trimmedId.startsWith("127.0.0.1:") -> {
                    val port = trimmedId.substringAfter(":")
                    "Harmony-E-$port"
                }
                isEmulator && trimmedId.startsWith("localhost:") -> {
                    val port = trimmedId.substringAfter(":")
                    "Harmony-E-$port"
                }
                else -> trimmedId // Physical device, use ID as name
            }
            
            return HarmonyDevice(trimmedId, displayName, isEmulator)
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HarmonyDevice) return false
        return deviceId == other.deviceId
    }
    
    override fun hashCode(): Int {
        return deviceId.hashCode()
    }
}
