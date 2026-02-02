package org.jetbrains.plugins.template.projectsync.localproperties

import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

/**
 * Gradle Sync 完成后的 OHOS 路径同步扩展
 * 
 * 功能：
 * 1. 读取 local.properties 中的 local.ohos.path
 * 2. 验证路径存在性（空值允许）
 * 3. 同步到全局内存服务
 * 4. 将 OHOS 目录作为 Content Root 添加到项目视图
 */
class OhosPostSyncExtension : GradleSyncListenerWithRoot {
    
    private val logger = thisLogger()

    override fun syncSucceeded(project: Project, rootProjectPath: String) {
        logger.info("=== OhosPostSyncExtension: Gradle Sync 成功 ===")
        logger.info("Project: ${project.name}, Root: $rootProjectPath")
        
        // 使用后台线程处理，避免在 EDT 上执行耗时操作
        CoroutineScope(Dispatchers.IO).launch {
            try {
                processOhosPath(project)
            } catch (e: Exception) {
                logger.error("处理 OHOS 路径时出错", e)
            }
        }
    }

    /**
     * 处理 local.properties 中的 local.ohos.path
     */
    private fun processOhosPath(project: Project) {
        val baseDir = project.baseDir
        if (baseDir == null) {
            logger.warn("项目根目录为 null")
            return
        }
        
        val localProps = baseDir.findChild("local.properties")
        if (localProps == null) {
            logger.info("未找到 local.properties 文件")
            return
        }

        // 读取配置文件
        val props = Properties()
        localProps.inputStream.use { props.load(it) }

        val rawValue = props.getProperty("local.ohos.path")?.trim()
        val service = project.getService(OhosPathService::class.java)

        // ① 允许为空：直接清空内存
        if (rawValue.isNullOrEmpty()) {
            logger.info("local.ohos.path 为空，清空内存缓存")
            service.ohosPath = null
            return
        }

        logger.info("读取到 local.ohos.path: $rawValue")

        // ② 校验路径是否存在
        val path = Paths.get(rawValue)
        if (!Files.exists(path)) {
            val errorMsg = "local.ohos.path 指定的路径不存在: $rawValue"
            logger.error(errorMsg)
            throw ExternalSystemException(errorMsg)
        }

        logger.info("路径验证通过: ${path.toAbsolutePath()}")

        // ③ 同步到全局内存
        service.ohosPath = path
        logger.info("已同步到 OhosPathService")

        // ④ 将 OHOS 目录添加到现有模块的 Content Root（而不是创建独立模块）
        attachToExistingModule(project, path)
    }

    /**
     * 将 OHOS 目录添加到现有 Gradle 模块的 Content Root
     * 避免创建非 Gradle 模块导致编译错误
     */
    private fun attachToExistingModule(project: Project, path: java.nio.file.Path) {
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runWriteAction {
                try {
                    val moduleManager = ModuleManager.getInstance(project)
                    
                    // 先清理之前创建的独立 OHOS 模块（如果存在）
                    cleanupOldOhosModules(moduleManager)
                    
                    // 获取主模块（通常是第一个 Gradle 模块）
                    val mainModule = moduleManager.modules.firstOrNull()
                    if (mainModule == null) {
                        logger.warn("未找到任何模块，无法添加 OHOS Content Root")
                        return@runWriteAction
                    }
                    
                    val model = ModuleRootManager.getInstance(mainModule).modifiableModel
                    val url = VfsUtil.pathToUrl(path.toString())
                    
                    // 先清理所有指向 OHOS 路径的旧 Content Root
                    val oldEntries = model.contentEntries.filter { 
                        it.url.contains("OHOS") || it.url == url 
                    }
                    oldEntries.forEach { 
                        logger.info("清理旧的 OHOS Content Root: ${it.url}")
                        model.removeContentEntry(it) 
                    }
                    
                    // 添加新的 Content Root
                    val contentEntry = model.addContentEntry(url)
                    logger.info("成功添加 OHOS Content Root: $url")
                    
                    // 标记为排除编译（避免编译错误）
                    contentEntry.addExcludeFolder(url)
                    logger.info("已将 OHOS 目录标记为排除编译")
                    
                    model.commit()
                    
                    // 刷新 VFS
                    VfsUtil.findFile(path, true)?.refresh(false, true)
                    
                } catch (e: Exception) {
                    logger.error("添加 OHOS Content Root 失败", e)
                }
            }
        }
    }
    
    /**
     * 清理之前创建的独立 OHOS 模块
     * 这些模块会导致 "Compilation is not supported" 错误
     */
    private fun cleanupOldOhosModules(moduleManager: ModuleManager) {
        try {
            val oldModuleNames = listOf(
                "z-OHOS-External",
                "OHOS-External-Project",
                "OHOS-External"
            )
            
            val modifiableModel = moduleManager.getModifiableModel()
            var hasChanges = false
            
            oldModuleNames.forEach { oldName ->
                val oldModule = moduleManager.findModuleByName(oldName)
                if (oldModule != null) {
                    logger.info("删除旧的独立 OHOS 模块: $oldName")
                    modifiableModel.disposeModule(oldModule)
                    hasChanges = true
                }
            }
            
            if (hasChanges) {
                modifiableModel.commit()
                logger.info("已删除旧的独立 OHOS 模块")
            } else {
                modifiableModel.dispose()
            }
        } catch (e: Exception) {
            logger.warn("清理旧模块时出错", e)
        }
    }
}
