package org.jetbrains.plugins.template.projectsync.localproperties

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * local.properties 文件监听器
 * 
 * 功能：
 * - 监听项目根目录的 local.properties 文件变化
 * - 文件变化时自动触发 Gradle Sync
 * - Gradle Sync 会触发 OhosPostSyncExtension 重新读取配置
 */
class LocalPropertiesListener(
    private val project: Project
) : VirtualFileListener {
    
    private val logger = thisLogger()

    override fun contentsChanged(event: VirtualFileEvent) {
        // 只处理 local.properties 文件
        if (event.file.name != "local.properties") return
        
        // 确保是项目根目录的 local.properties
        val projectBasePath = project.basePath ?: return
        val eventFilePath = event.file.path
        
        if (!eventFilePath.startsWith(projectBasePath)) {
            logger.debug("忽略非项目根目录的 local.properties: $eventFilePath")
            return
        }
        
        logger.info("=== 检测到 local.properties 文件变化 ===")
        logger.info("文件路径: $eventFilePath")
        logger.info("触发 Gradle Sync...")

        val request = GradleSyncInvoker.Request(
            GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED
        )

        GradleSyncInvoker.getInstance()
            .requestProjectSync(project, request)
            
        logger.info("Gradle Sync 请求已发送")
    }

    /**
     * 注册文件监听器
     */
    fun register() {
        VirtualFileManager.getInstance()
            .addVirtualFileListener(this, project)
        logger.info("LocalPropertiesListener 已注册")
    }
}
