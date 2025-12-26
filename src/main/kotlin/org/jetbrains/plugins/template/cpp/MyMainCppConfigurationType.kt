package org.jetbrains.plugins.template.cpp

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.icons.AllIcons
import javax.swing.Icon

/**
 * C++ 运行配置类型
 */
class MyMainCppConfigurationType : ConfigurationTypeBase(
    "MyMainCppConfigurationType",
    "MyMainApp",
    "Run my_main.cpp application",
    AllIcons.RunConfigurations.Application
) {

    init {
        addFactory(MyMainCppConfigurationFactory(this))
    }

    companion object {
        fun getInstance(): MyMainCppConfigurationType {
            return ConfigurationType.CONFIGURATION_TYPE_EP
                .findExtensionOrFail(MyMainCppConfigurationType::class.java)
        }
    }
}
