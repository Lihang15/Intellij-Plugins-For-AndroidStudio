package org.jetbrains.plugins.template.runconfig

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

// 导入数据同步服务
import org.jetbrains.plugins.template.projectsync.DataSyncService

/**
 * 运行配置生产者 - 自动检测并创建运行配置
 * 
 * 识别规则：
 * 1. 项目根目录下存在 harmonyApp 目录，或
 * 2. local.properties 中配置了有效的 local.ohos.path
 */
class HarmonyRunConfigurationProducer : LazyRunConfigurationProducer<HarmonyRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory {
        return HarmonyConfigurationType.getInstance().configurationFactories[0]
    }

    override fun isConfigurationFromContext(
        configuration: HarmonyRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        // 检查项目中是否存在 HarmonyOS 项目结构
        return configuration.hasHarmonyFile()
    }

    override fun setupConfigurationFromContext(
        configuration: HarmonyRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val project = context.project
        val projectPath = project.basePath ?: return false

        // 验证数据同步情况
        verifySyncedData(project)

        // 检查是否存在 HarmonyOS 项目结构
        if (!hasHarmonyOSProject(projectPath)) {
            return false
        }

        // 设置运行配置名称
        configuration.name = "harmonyApp"
        return true
    }

    /**
     * 检查是否存在 HarmonyOS 项目结构
     * 
     * 规则：
     * 1. 项目根目录下存在 harmonyApp 目录，或
     * 2. local.properties 中配置了有效的 local.ohos.path
     */
    private fun hasHarmonyOSProject(projectPath: String): Boolean {
        // 规则 1：检查项目根目录下是否存在 harmonyApp 目录
        val harmonyAppDir = File(projectPath, "harmonyApp")
        if (harmonyAppDir.exists() && harmonyAppDir.isDirectory) {
            println("[HarmonyOS] 检测到 harmonyApp 目录: ${harmonyAppDir.absolutePath}")
            return true
        }

        // 规则 2：检查 local.properties 中的 local.ohos.path
        val localPropertiesFile = File(projectPath, "local.properties")
        if (localPropertiesFile.exists()) {
            try {
                val properties = java.util.Properties()
                localPropertiesFile.inputStream().use { properties.load(it) }
                
                val ohosPath = properties.getProperty("local.ohos.path")?.trim()
                if (!ohosPath.isNullOrEmpty()) {
                    // 检查配置的路径是否真实存在
                    val ohosDir = File(ohosPath)
                    if (ohosDir.exists() && ohosDir.isDirectory) {
                        println("[HarmonyOS] 检测到有效的 local.ohos.path: $ohosPath")
                        return true
                    } else {
                        println("[HarmonyOS] local.ohos.path 配置的路径不存在: $ohosPath")
                    }
                }
            } catch (e: Exception) {
                println("[HarmonyOS] 读取 local.properties 失败: ${e.message}")
            }
        }

        println("[HarmonyOS] 未检测到 HarmonyOS 项目结构")
        println("[HarmonyOS]  - 未找到 harmonyApp 目录")
        println("[HarmonyOS]  - 未找到有效的 local.ohos.path 配置")
        return false
    }

    /**
     * 验证数据是否同步过来的调试方法
     */
    private fun verifySyncedData(project: com.intellij.openapi.project.Project) {
        val syncService = DataSyncService.getInstance(project)
        val model = syncService.dataModel
        println("[DEBUG] HarmonyRunConfigurationProducer 正在检查同步数据...")
        
        if (model != null) {
            println("[DEBUG] 同步成功！")
            println("[DEBUG] 版本号 (version): ${model.version}")
            println("[DEBUG] 状态 (status): ${model.status}")
            println("[DEBUG] 模块列表 (modules): ${model.modules.joinToString(", ")}")
            println("[DEBUG] 脚本执行时间: ${model.timestamp}")
        } else {
            println("[DEBUG] 同步尚未完成或失败 (dataModel is null)")
            // 提示：如果是刚打开项目就点这里，脚本可能还没跑完
        }
    }
}
