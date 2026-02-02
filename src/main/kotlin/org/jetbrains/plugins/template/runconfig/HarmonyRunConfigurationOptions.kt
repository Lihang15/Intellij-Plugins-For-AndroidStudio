package org.jetbrains.plugins.template.runconfig

import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.components.StoredProperty

/**
 * 运行配置选项
 */
class HarmonyRunConfigurationOptions : RunConfigurationOptions() {
    private val myDeviceId: StoredProperty<String?> = string("").provideDelegate(this, "deviceId")
    
    var deviceId: String?
        get() = myDeviceId.getValue(this)
        set(value) {
            myDeviceId.setValue(this, value)
        }
}
