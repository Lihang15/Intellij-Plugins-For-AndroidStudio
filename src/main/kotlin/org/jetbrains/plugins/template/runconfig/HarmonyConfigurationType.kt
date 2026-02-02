package org.jetbrains.plugins.template.runconfig

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.icons.AllIcons
import javax.swing.Icon

/**
 * C++ 运行配置类型
 */
class HarmonyConfigurationType : ConfigurationTypeBase(
    "HarmonyConfigurationType",
    "harmonyApp",
    "Run my_main.cpp application",
    AllIcons.RunConfigurations.Application
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
