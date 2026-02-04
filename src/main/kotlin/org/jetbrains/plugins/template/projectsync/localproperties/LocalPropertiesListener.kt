package org.jetbrains.plugins.template.projectsync.localproperties

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFilePropertyEvent

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
    
    // 防抖：避免短时间内多次触发 Sync
    @Volatile
    private var lastSyncTime: Long = 0
    private val syncDebounceMs = 2000L  // 2秒内只触发一次

    override fun contentsChanged(event: VirtualFileEvent) {
        handleFileChange(event)
    }
    
    override fun propertyChanged(event: VirtualFilePropertyEvent) {
        // 也监听属性变化（如文件修改时间）
        handleFileChange(event)
    }
    
    private fun handleFileChange(event: VirtualFileEvent) {
        // 只处理 local.properties 文件
        if (event.file.name != "local.properties") return
        
        // 确保是项目根目录的 local.properties
        val projectBasePath = project.basePath ?: return
        val eventFilePath = event.file.path
        
        if (!eventFilePath.startsWith(projectBasePath)) {
            logger.debug("忽略非项目根目录的 local.properties: $eventFilePath")
            return
        }
        
        // 防抖：避免短时间内多次触发
        val now = System.currentTimeMillis()
        if (now - lastSyncTime < syncDebounceMs) {
            logger.debug("忽略重复的文件变化事件（防抖）")
            return
        }
        lastSyncTime = now
        
        logger.info("=== 检测到 local.properties 文件变化 ===")
        logger.info("文件路径: $eventFilePath")
        logger.info("事件类型: ${event.javaClass.simpleName}")
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
