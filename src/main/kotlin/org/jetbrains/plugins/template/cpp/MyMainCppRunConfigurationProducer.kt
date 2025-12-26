package org.jetbrains.plugins.template.cpp

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import java.io.File

/**
 * 运行配置生产者 - 自动检测并创建运行配置
 */
class MyMainCppRunConfigurationProducer : LazyRunConfigurationProducer<MyMainCppRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory {
        return MyMainCppConfigurationType.getInstance().configurationFactories[0]
    }

    override fun isConfigurationFromContext(
        configuration: MyMainCppRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        // 检查项目中是否存在 my_main.cpp
        return configuration.hasMyMainCppFile()
    }

    override fun setupConfigurationFromContext(
        configuration: MyMainCppRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val project = context.project
        val projectPath = project.basePath ?: return false
        
        // 检查项目根目录下是否存在 my_main.cpp
        val myMainCppFile = File(projectPath, "my_main.cpp")
        if (!myMainCppFile.exists()) {
            return false
        }

        // 设置运行配置名称
        configuration.name = "MyMainApp"
        return true
    }
}
