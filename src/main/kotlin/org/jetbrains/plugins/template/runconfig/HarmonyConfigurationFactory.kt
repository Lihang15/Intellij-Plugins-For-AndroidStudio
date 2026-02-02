package org.jetbrains.plugins.template.runconfig

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project

/**
 * 运行配置工厂
 */
class HarmonyConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = "HarmonyConfigurationFactory"

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return HarmonyRunConfiguration(project, this, "harmonyApp")
    }

    override fun getOptionsClass(): Class<out BaseState>? {
        return HarmonyRunConfigurationOptions::class.java
    }
}
