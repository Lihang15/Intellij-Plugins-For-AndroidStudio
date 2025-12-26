package org.jetbrains.plugins.template.cpp

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project

/**
 * 运行配置工厂
 */
class MyMainCppConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = "MyMainCppConfigurationFactory"

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return MyMainCppRunConfiguration(project, this, "MyMainApp")
    }

    override fun getOptionsClass(): Class<out BaseState>? {
        return MyMainCppRunConfigurationOptions::class.java
    }
}
