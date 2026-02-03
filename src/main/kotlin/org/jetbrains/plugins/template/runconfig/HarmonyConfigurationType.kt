package org.jetbrains.plugins.template.runconfig

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * harmonyApp运行配置类型
 */
class HarmonyConfigurationType : ConfigurationTypeBase(
    "HarmonyConfigurationType",
    "harmonyApp",
    "Run HarmonyOS application",
    IconLoader.getIcon("/icons/harmony_logo.svg", HarmonyConfigurationType::class.java)
) {

    init {
        addFactory(HarmonyConfigurationFactory(this))
    }

    companion object {
        fun getInstance(): HarmonyConfigurationType {
            return ConfigurationType.CONFIGURATION_TYPE_EP
                .findExtensionOrFail(HarmonyConfigurationType::class.java)
        }
    }
}
