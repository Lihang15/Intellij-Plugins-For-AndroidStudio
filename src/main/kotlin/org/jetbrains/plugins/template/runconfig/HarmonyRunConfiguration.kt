package org.jetbrains.plugins.template.runconfig

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.template.device.DeviceService
import org.jetbrains.plugins.template.device.HarmonyDevice
import java.io.File

/**
 * Harmony 运行配置
 */
class HarmonyRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<HarmonyRunConfigurationOptions>(project, factory, name) {

    override fun getOptions(): HarmonyRunConfigurationOptions {
        return super.getOptions() as HarmonyRunConfigurationOptions
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        println("=== HarmonyRunConfiguration.getConfigurationEditor() CALLED ===")
        println("Project: ${project.name}")
        val editor = HarmonySettingsEditor(project)
        println("Editor created: $editor")
        return editor
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return HarmonyRunProfileState(environment)
    }

    /**
     * 获取选中的设备 ID
     */
    fun getSelectedDeviceId(): String? {
        return options.deviceId
    }

    /**
     * 设置选中的设备 ID
     */
    fun setSelectedDeviceId(deviceId: String?) {
        options.deviceId = deviceId
    }

    /**
     * 获取选中的设备对象
     */
    fun getSelectedDevice(): HarmonyDevice? {
        val deviceId = getSelectedDeviceId() ?: return null
        val deviceService = DeviceService.getInstance(project)
        return deviceService.getConnectedDevices().find { it.deviceId == deviceId }
    }

    /**
     * 检查项目中是否存在 my_main.cpp 文件
     */
    fun hasHarmonyFile(): Boolean {
        val projectPath = project.basePath ?: return false
        val HarmonyFile = File(projectPath, "my_main.cpp")
        return HarmonyFile.exists()
    }

    /**
     * 获取 my_main.cpp 文件的完整路径
     */
    fun getHarmonyPath(): String? {
        val projectPath = project.basePath ?: return null
        return File(projectPath, "my_main.cpp").absolutePath
    }

    /**
     * 获取编译输出文件路径
     */
    fun getOutputPath(): String? {
        val projectPath = project.basePath ?: return null
        return File(projectPath, "Harmony").absolutePath
    }
}
