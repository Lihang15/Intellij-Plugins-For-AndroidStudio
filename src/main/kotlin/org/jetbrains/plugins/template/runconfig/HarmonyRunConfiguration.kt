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
     * 检查项目中是否存在 HarmonyOS 项目结构
     * 
     * 规则：
     * 1. 项目根目录下存在 harmonyApp 目录，或
     * 2. local.properties 中配置了有效的 local.ohos.path
     */
    fun hasHarmonyFile(): Boolean {
        val projectPath = project.basePath ?: run {
            println("[HarmonyOS] project.basePath is null")
            return false
        }
        
        println("[HarmonyOS] 检查项目路径: $projectPath")
        
        // 规则 1：检查项目根目录下是否存在 harmonyApp 目录
        val harmonyAppDir = File(projectPath, "harmonyApp")
        println("[HarmonyOS] 检查 harmonyApp 目录: ${harmonyAppDir.absolutePath}")
        println("[HarmonyOS]   - exists: ${harmonyAppDir.exists()}")
        println("[HarmonyOS]   - isDirectory: ${harmonyAppDir.isDirectory}")
        
        if (harmonyAppDir.exists() && harmonyAppDir.isDirectory) {
            println("[HarmonyOS] 找到 harmonyApp 目录")
            return true
        }

        // 规则 2：检查 local.properties 中的 local.ohos.path
        val localPropertiesFile = File(projectPath, "local.properties")
        println("[HarmonyOS] 检查 local.properties: ${localPropertiesFile.absolutePath}")
        println("[HarmonyOS]   - exists: ${localPropertiesFile.exists()}")
        
        if (localPropertiesFile.exists()) {
            try {
                val properties = java.util.Properties()
                localPropertiesFile.inputStream().use { properties.load(it) }
                
                val ohosPath = properties.getProperty("local.ohos.path")?.trim()
                println("[HarmonyOS]   - local.ohos.path: $ohosPath")
                
                if (!ohosPath.isNullOrEmpty()) {
                    // 检查配置的路径是否真实存在
                    val ohosDir = File(ohosPath)
                    println("[HarmonyOS]   - ohosPath exists: ${ohosDir.exists()}")
                    println("[HarmonyOS]   - ohosPath isDirectory: ${ohosDir.isDirectory}")
                    
                    if (ohosDir.exists() && ohosDir.isDirectory) {
                        println("[HarmonyOS] 找到有效的 local.ohos.path")
                        return true
                    }
                }
            } catch (e: Exception) {
                println("[HarmonyOS] 读取 local.properties 失败: ${e.message}")
                e.printStackTrace()
            }
        }

        println("[HarmonyOS] 未找到 HarmonyOS 项目结构")
        return false
    }
}
