package org.jetbrains.plugins.template.projectsync.localproperties

import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
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
 * 4. 创建独立的 OHOS 模块（与主项目并列显示）
 */
class OhosPostSyncExtension : GradleSyncListenerWithRoot {
    
    private val logger = thisLogger()

    override fun syncSucceeded(project: Project, rootProjectPath: String) {
        println("=== OhosPostSyncExtension: Gradle Sync 成功 ===")
        println("Project: ${project.name}, Root: $rootProjectPath")
        logger.info("=== OhosPostSyncExtension: Gradle Sync 成功 ===")
        logger.info("Project: ${project.name}, Root: $rootProjectPath")
        
        // 使用后台线程处理，避免在 EDT 上执行耗时操作
        CoroutineScope(Dispatchers.IO).launch {
            try {
                processOhosPath(project)
            } catch (e: Exception) {
                println("[OHOS] 处理 OHOS 路径时出错: ${e.message}")
                e.printStackTrace()
                logger.error("处理 OHOS 路径时出错", e)
            }
        }
    }

    /**
     * 处理 local.properties 中的 local.ohos.path
     */
    private fun processOhosPath(project: Project) {
        println("[OHOS] 开始处理 OHOS 路径")
        logger.info("[OHOS] 开始处理 OHOS 路径")
        
        val baseDir = project.baseDir
        if (baseDir == null) {
            println("[OHOS] 项目根目录为 null")
            logger.warn("[OHOS] 项目根目录为 null")
            return
        }
        
        println("[OHOS] 项目根目录: ${baseDir.path}")
        logger.info("[OHOS] 项目根目录: ${baseDir.path}")
        
        val localProps = baseDir.findChild("local.properties")
        if (localProps == null) {
            println("[OHOS] 未找到 local.properties 文件")
            logger.info("[OHOS] 未找到 local.properties 文件")
            return
        }

        println("[OHOS] 找到 local.properties 文件: ${localProps.path}")
        logger.info("[OHOS] 找到 local.properties 文件: ${localProps.path}")

        // 读取配置文件
        val props = Properties()
        localProps.inputStream.use { props.load(it) }

        val rawValue = props.getProperty("local.ohos.path")?.trim()
        println("[OHOS] local.ohos.path 原始值: '$rawValue'")
        logger.info("[OHOS] local.ohos.path 原始值: '$rawValue'")
        
        val service = project.getService(OhosPathService::class.java)

        // ① 允许为空：直接清空内存并移除模块
        if (rawValue.isNullOrEmpty()) {
            println("[OHOS] local.ohos.path 为空，清空内存缓存并移除 OHOS 模块")
            logger.info("[OHOS] local.ohos.path 为空，清空内存缓存并移除 OHOS 模块")
            service.ohosPath = null
            removeOhosModule(project)
            return
        }

        println("[OHOS] 读取到 local.ohos.path: $rawValue")
        logger.info("[OHOS] 读取到 local.ohos.path: $rawValue")

        // ② 校验路径是否存在
        val path = Paths.get(rawValue)
        println("[OHOS] 解析后的路径: ${path.toAbsolutePath()}")
        logger.info("[OHOS] 解析后的路径: ${path.toAbsolutePath()}")
        
        if (!Files.exists(path)) {
            val errorMsg = "local.ohos.path 指定的路径不存在: $rawValue"
            println("[OHOS] $errorMsg")
            logger.error("[OHOS] $errorMsg")
            throw ExternalSystemException(errorMsg)
        }

        println("[OHOS] 路径验证通过: ${path.toAbsolutePath()}")
        logger.info("[OHOS] 路径验证通过: ${path.toAbsolutePath()}")

        // ③ 同步到全局内存
        service.ohosPath = path
        println("[OHOS] 已同步到 OhosPathService")
        logger.info("[OHOS] 已同步到 OhosPathService")

        // ④ 创建独立的 OHOS 模块（与主项目并列）
        println("[OHOS] 准备创建独立的 OHOS 模块")
        logger.info("[OHOS] 准备创建独立的 OHOS 模块")
        createOrUpdateOhosModule(project, path)
    }

    /**
     * 将 OHOS 目录添加为主模块的 Content Root
     * 不创建独立模块，避免 Gradle 编译冲突
     */
    private fun createOrUpdateOhosModule(project: Project, path: java.nio.file.Path) {
        println("[OHOS] 准备将 OHOS 目录添加为 Content Root，路径: ${path.toAbsolutePath()}")
        logger.info("[OHOS] 准备将 OHOS 目录添加为 Content Root，路径: ${path.toAbsolutePath()}")
        
        // 使用 invokeLater 确保在 EDT 上执行，并且不阻塞当前线程
        ApplicationManager.getApplication().invokeLater {
            println("[OHOS] 在 EDT 上开始 Content Root 操作")
            logger.info("[OHOS] 在 EDT 上开始 Content Root 操作")
            
            ApplicationManager.getApplication().runWriteAction {
                try {
                    println("[OHOS] 进入 WriteAction")
                    logger.info("[OHOS] 进入 WriteAction")
                    val moduleManager = ModuleManager.getInstance(project)
                    println("[OHOS] 获取 ModuleManager 成功，当前模块数: ${moduleManager.modules.size}")
                    logger.info("[OHOS] 获取 ModuleManager 成功，当前模块数: ${moduleManager.modules.size}")
                    
                    // 清理所有旧的独立 OHOS 模块（避免冲突）
                    println("[OHOS] 开始清理旧的独立模块")
                    logger.info("[OHOS] 开始清理旧的独立模块")
                    cleanupLegacyEntries(project, moduleManager, path)
                    println("[OHOS] 清理完成")
                    logger.info("[OHOS] 清理完成")
                    
                    // 获取主 Gradle 模块
                    val mainModule = moduleManager.modules.firstOrNull()
                    if (mainModule == null) {
                        println("[OHOS] 未找到主模块")
                        logger.error("[OHOS] 未找到主模块")
                        return@runWriteAction
                    }
                    
                    println("[OHOS] 找到主模块: ${mainModule.name}")
                    logger.info("[OHOS] 找到主模块: ${mainModule.name}")
                    
                    // 添加为 Content Root（标记为 Excluded，不参与编译）
                    addOhosAsExcludedContentRoot(mainModule, path)
                    
                    // 刷新 VFS
                    println("[OHOS] 开始刷新 VFS")
                    logger.info("[OHOS] 开始刷新 VFS")
                    refreshVirtualFileSystem(path)
                    
                    // 强制刷新项目视图
                    println("[OHOS] 刷新项目视图")
                    logger.info("[OHOS] 刷新项目视图")
                    ApplicationManager.getApplication().invokeLater {
                        project.baseDir?.refresh(false, true)
                    }
                    
                    println("[OHOS] Content Root 添加完成")
                    logger.info("[OHOS] Content Root 添加完成")
                    
                } catch (e: Exception) {
                    println("[OHOS]  添加 Content Root 失败: ${e.message}")
                    e.printStackTrace()
                    logger.error("[OHOS] 添加 Content Root 失败", e)
                }
            }
        }
    }
    
    /**
     * 将 OHOS 目录添加为主模块的 Excluded Content Root
     * 不参与编译，但可以浏览文件
     */
    private fun addOhosAsExcludedContentRoot(module: Module, path: java.nio.file.Path) {
        val model = ModuleRootManager.getInstance(module).modifiableModel
        val url = VfsUtil.pathToUrl(path.toString())
        
        try {
            // 检查是否已存在此 Content Root
            val existingEntry = model.contentEntries.find { it.url == url }
            if (existingEntry != null) {
                println("[OHOS] Content Root 已存在: $url")
                logger.info("[OHOS] Content Root 已存在: $url")
                model.dispose()
                return
            }
            
            println("[OHOS] 添加 Content Entry: $url")
            logger.info("[OHOS] 添加 Content Entry: $url")
            val contentEntry = model.addContentEntry(url)
            
            // 标记为 Excluded，不参与编译
            contentEntry.addExcludeFolder(url)
            println("[OHOS] 已将目录标记为 Excluded")
            logger.info("[OHOS] 已将目录标记为 Excluded")
            
            model.commit()
            println("[OHOS] 成功添加 Excluded Content Root")
            logger.info("[OHOS] 成功添加 Excluded Content Root")
        } catch (e: Exception) {
            println("[OHOS]  添加 Content Root 失败: ${e.message}")
            e.printStackTrace()
            model.dispose()
            throw e
        }
    }
    
    /**
     * 移除 OHOS Content Root（当路径为空时）
     */
    private fun removeOhosModule(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runWriteAction {
                try {
                    val moduleManager = ModuleManager.getInstance(project)
                    
                    // 清理所有旧的独立 OHOS 模块和所有 OHOS Content Root
                    cleanupLegacyEntries(project, moduleManager, null)
                    
                    logger.info("[OHOS] 已清理所有 OHOS 相关配置")
                } catch (e: Exception) {
                    logger.error("[OHOS] 清理 OHOS 配置失败", e)
                }
            }
        }
    }
    
    /**
     * 清理旧的实现方式（Content Root 和旧模块）
     * @param keepPath 要保留的路径，如果为 null 则清理所有
     */
    private fun cleanupLegacyEntries(project: Project, moduleManager: ModuleManager, keepPath: java.nio.file.Path?) {
        // 只清理旧的独立模块（导致编译错误的）
        cleanupOldOhosModules(moduleManager)
        
        // 不再自动清理 Content Root，避免误删用户手动添加的内容
        // 如果需要清理，只在配置为空时才清理
        if (keepPath == null) {
            cleanupOhosContentRootsFromMainModule(moduleManager, null)
        }
    }
    
    /**
     * 从主模块中清理 OHOS Content Root
     * 清理所有包含 OHOS 或 ohos 关键字的 Content Root（但保留指定路径）
     * @param keepPath 要保留的路径，如果为 null 则清理所有
     */
    private fun cleanupOhosContentRootsFromMainModule(moduleManager: ModuleManager, keepPath: java.nio.file.Path?) {
        try {
            val keepUrl = keepPath?.let { VfsUtil.pathToUrl(it.toString()) }
            
            // 遍历所有模块，清理其中的 OHOS Content Root
            moduleManager.modules.forEach { module ->
                val model = ModuleRootManager.getInstance(module).modifiableModel
                
                val ohosEntries = model.contentEntries.filter { entry ->
                    val url = entry.url
                    // 匹配 OHOS 相关路径，但排除：
                    // 1. 当前项目自己的目录
                    // 2. 要保留的路径
                    (url.contains("/OHOS", ignoreCase = true) || url.contains("/ohos", ignoreCase = true)) &&
                    !url.contains("${module.project.basePath}") &&
                    url != keepUrl  // 保留当前要添加的路径
                }
                
                if (ohosEntries.isNotEmpty()) {
                    ohosEntries.forEach { entry ->
                        logger.info("[OHOS] 从模块 ${module.name} 清理 Content Root: ${entry.url}")
                        model.removeContentEntry(entry)
                    }
                    model.commit()
                    logger.info("[OHOS] 已从模块 ${module.name} 清理 ${ohosEntries.size} 个 OHOS Content Root")
                } else {
                    model.dispose()
                }
            }
        } catch (e: Exception) {
            logger.warn("[OHOS] 清理 OHOS Content Root 时出错", e)
        }
    }
    
    /**
     * 刷新虚拟文件系统
     */
    private fun refreshVirtualFileSystem(path: java.nio.file.Path) {
        try {
            VfsUtil.findFile(path, true)?.refresh(false, true)
            logger.info("VFS 刷新完成: ${path.toAbsolutePath()}")
        } catch (e: Exception) {
            logger.warn("VFS 刷新失败", e)
        }
    }
    
    /**
     * 清理之前创建的独立 OHOS 模块
     * 这些独立模块会导致 "Compilation is not supported" 错误
     * 现在改用 Content Root 方案，必须删除所有由插件创建的独立模块
     */
    private fun cleanupOldOhosModules(moduleManager: ModuleManager) {
        try {
            val modifiableModel = moduleManager.getModifiableModel()
            var hasChanges = false
            
            // 删除所有由插件创建的 OHOS 相关模块（根据命名模式识别）
            val patterns = listOf(
                "z-OHOS-",
                "OHOS-External-",
                "ohos-external-"
            )
            
            moduleManager.modules.forEach { module ->
                if (patterns.any { module.name.startsWith(it) }) {
                    logger.info("[OHOS] 删除插件创建的独立模块: ${module.name}")
                    println("[OHOS] 删除插件创建的独立模块: ${module.name}")
                    modifiableModel.disposeModule(module)
                    hasChanges = true
                }
            }
            
            if (hasChanges) {
                modifiableModel.commit()
                logger.info("[OHOS] 已删除所有插件创建的独立模块")
                println("[OHOS] 已删除所有插件创建的独立模块")
            } else {
                modifiableModel.dispose()
            }
        } catch (e: Exception) {
            logger.warn("[OHOS] 清理旧模块时出错", e)
            println("[OHOS] 清理旧模块时出错: ${e.message}")
        }
    }
}
