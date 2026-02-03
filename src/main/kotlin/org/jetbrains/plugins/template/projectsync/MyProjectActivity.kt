package org.jetbrains.plugins.template.projectsync

import com.intellij.execution.RunManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.plugins.template.runconfig.HarmonyConfigurationType
import org.jetbrains.plugins.template.runconfig.HarmonyFileCreator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import org.jetbrains.plugins.template.projectsync.localproperties.LocalPropertiesListener


class MyProjectActivity : ProjectActivity {

    private val logger = thisLogger()
    private val syncExecutor: SyncExecutor = DefaultSyncExecutor()

    override suspend fun execute(project: Project) {
        logger.info("MyProjectActivity starting for project: ${project.name}")
        
        // 延迟一下，等待 VFS 刷新和文件系统同步
        kotlinx.coroutines.delay(1000) // 延迟 1 秒
        
        // 在项目启动时自动检查并创建 harmonyApp 运行配置
        try {
            autoCreateharmonyAppConfiguration(project)
        } catch (e: Exception) {
            logger.error("Failed to create harmonyApp configuration", e)
        }

        // 开始工程同步 - 使用 launch 确保不阻塞项目打开
        CoroutineScope(Dispatchers.IO).launch {
            try {
                syncProjectData(project)
                // 注册 local.properties 文件监听器，自动触发 Gradle Sync
                LocalPropertiesListener(project).register()
            } catch (e: Exception) {
                // 捕获所有异常，确保不会阻止项目打开
                logger.error("Sync failed but project will continue to open", e)
            }
        }
        
        logger.info("MyProjectActivity completed for project: ${project.name}")
    }

    /**
     * 自动创建 harmonyApp 运行配置
     * 
     * 触发条件：
     * 1. 项目根目录下存在 harmonyApp 目录，或
     * 2. local.properties 中配置了有效的 local.ohos.path
     */
    private suspend fun autoCreateharmonyAppConfiguration(project: Project) {
        try {
            val basePath = project.basePath
            if (basePath == null) {
                logger.warn("[HarmonyOS] Project basePath is null, cannot create harmonyApp configuration")
                return
            }
            
            logger.info("[HarmonyOS] 开始检查是否需要创建 harmonyApp 运行配置...")
            logger.info("[HarmonyOS] 项目路径: $basePath")
            
            // 检查项目是否为 HarmonyOS 项目
            if (!isHarmonyOSProject(basePath)) {
                logger.info("[HarmonyOS] 不是 HarmonyOS 项目，跳过创建 harmonyApp 配置")
                return
            }
            
            logger.info("[HarmonyOS] ✅ 检测到 HarmonyOS 项目，准备创建运行配置")
            
            // 在 EDT 线程中操作 RunManager
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                try {
                    val runManager = RunManager.getInstance(project)
                    logger.info("[HarmonyOS] RunManager 获取成功")
                    
                    // 检查配置是否已存在
                    val existingConfig = runManager.allSettings.find { settings ->
                        val isHarmonyType = settings.type is HarmonyConfigurationType
                        val isHarmonyName = settings.name == "harmonyApp"
                        logger.info("[HarmonyOS] 检查配置: ${settings.name}, type=${settings.type::class.simpleName}, isHarmony=$isHarmonyType, isName=$isHarmonyName")
                        isHarmonyType && isHarmonyName
                    }
                    
                    if (existingConfig != null) {
                        logger.info("[HarmonyOS] harmonyApp 配置已存在，跳过创建")
                        return@invokeLater
                    }
                    
                    logger.info("[HarmonyOS] harmonyApp 配置不存在，开始创建...")

                    // 创建新配置
                    val configurationType = HarmonyConfigurationType.getInstance()
                    logger.info("[HarmonyOS] HarmonyConfigurationType 获取成功: $configurationType")
                    
                    val factory = configurationType.configurationFactories.firstOrNull()
                    
                    if (factory == null) {
                        logger.error("[HarmonyOS] ❌ HarmonyConfigurationType factory not found")
                        logger.error("[HarmonyOS] Available factories: ${configurationType.configurationFactories.size}")
                        return@invokeLater
                    }
                    
                    logger.info("[HarmonyOS] Factory 获取成功: $factory")

                    val settings = runManager.createConfiguration("harmonyApp", factory)
                    logger.info("[HarmonyOS] Configuration 创建成功: ${settings.name}")
                    
                    runManager.addConfiguration(settings)
                    logger.info("[HarmonyOS] Configuration 已添加到 RunManager")
                    
                    // 设置为选中的配置
                    if (runManager.selectedConfiguration == null) {
                        runManager.selectedConfiguration = settings
                        logger.info("[HarmonyOS] 设置为选中配置")
                    }
                    
                    logger.info("[HarmonyOS] ✅ harmonyApp 运行配置创建成功")
                    logger.info("[HarmonyOS] 当前配置总数: ${runManager.allSettings.size}")
                    runManager.allSettings.forEach { 
                        logger.info("[HarmonyOS]   - ${it.name} (${it.type::class.simpleName})")
                    }
                } catch (e: Exception) {
                    logger.error("[HarmonyOS] ❌ 在 EDT 中创建配置失败", e)
                }
            }
            
        } catch (e: Exception) {
            logger.error("[HarmonyOS] ❌ Failed to create harmonyApp configuration", e)
        }
    }
    
    /**
     * 检查是否为 HarmonyOS 项目
     * 
     * 规则：
     * 1. 项目根目录下存在 harmonyApp 目录，或
     * 2. local.properties 中配置了有效的 local.ohos.path
     */
    private fun isHarmonyOSProject(basePath: String): Boolean {
        // 规则 1：检查 harmonyApp 目录
        val harmonyAppDir = File(basePath, "harmonyApp")
        if (harmonyAppDir.exists() && harmonyAppDir.isDirectory) {
            logger.info("✅ 找到 harmonyApp 目录: ${harmonyAppDir.absolutePath}")
            return true
        }
        
        // 规则 2：检查 local.properties 中的 local.ohos.path
        val localPropertiesFile = File(basePath, "local.properties")
        if (localPropertiesFile.exists()) {
            try {
                val properties = java.util.Properties()
                localPropertiesFile.inputStream().use { properties.load(it) }
                
                val ohosPath = properties.getProperty("local.ohos.path")?.trim()
                if (!ohosPath.isNullOrEmpty()) {
                    val ohosDir = File(ohosPath)
                    if (ohosDir.exists() && ohosDir.isDirectory) {
                        logger.info("✅ 找到有效的 local.ohos.path: $ohosPath")
                        return true
                    } else {
                        logger.warn("local.ohos.path 配置的路径不存在: $ohosPath")
                    }
                }
            } catch (e: Exception) {
                logger.error("读取 local.properties 失败", e)
            }
        }
        
        return false
    }

    /**
     * 使用 SyncExecutor 同步项目数据
     * 在 Dispatchers.IO 上执行，确保不阻塞 EDT
     */
    private suspend fun syncProjectData(project: Project) {
        when (val result = syncExecutor.syncProject(project)) {
            is Result.Success -> {
                logger.info("Project sync completed successfully: version ${result.data.version}")
            }
            is Result.Failure -> {
                logger.error("Project sync failed: ${result.message}", result.error)
            }
        }
    }
}
