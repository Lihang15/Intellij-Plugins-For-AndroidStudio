package org.jetbrains.plugins.template.cpp

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import java.io.File

/**
 * MyMainCpp 运行配置
 */
class MyMainCppRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<MyMainCppRunConfigurationOptions>(project, factory, name) {

    override fun getOptions(): MyMainCppRunConfigurationOptions {
        return super.getOptions() as MyMainCppRunConfigurationOptions
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return MyMainCppSettingsEditor()
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return MyMainCppRunProfileState(environment)
    }

    /**
     * 检查项目中是否存在 my_main.cpp 文件
     */
    fun hasMyMainCppFile(): Boolean {
        val projectPath = project.basePath ?: return false
        val myMainCppFile = File(projectPath, "my_main.cpp")
        return myMainCppFile.exists()
    }

    /**
     * 获取 my_main.cpp 文件的完整路径
     */
    fun getMyMainCppPath(): String? {
        val projectPath = project.basePath ?: return null
        return File(projectPath, "my_main.cpp").absolutePath
    }

    /**
     * 获取编译输出文件路径
     */
    fun getOutputPath(): String? {
        val projectPath = project.basePath ?: return null
        return File(projectPath, "mymaincpp").absolutePath
    }
}
