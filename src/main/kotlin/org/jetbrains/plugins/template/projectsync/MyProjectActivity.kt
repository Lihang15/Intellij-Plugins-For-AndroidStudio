package org.jetbrains.plugins.template.projectsync

import com.intellij.execution.RunManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.jetbrains.plugins.template.runconfig.HarmonyConfigurationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.plugins.template.projectsync.localproperties.LocalPropertiesListener


class MyProjectActivity : ProjectActivity {

    private val logger = thisLogger()
    private val syncExecutor: SyncExecutor = DefaultSyncExecutor()
    private val vfsChecker: VfsReadinessChecker = DefaultVfsReadinessChecker()

    override suspend fun execute(project: Project) {
        logger.info("[HarmonyOS] MyProjectActivity starting for project: ${project.name}")
        
        // Wait for VFS to be ready before checking project structure
        val vfsReady = waitForVfsReady(project)
        
        if (vfsReady) {
            // VFS is ready, proceed with configuration creation
            try {
                autoCreateharmonyAppConfiguration(project)
            } catch (e: Exception) {
                logger.error("[HarmonyOS] Failed to create harmonyApp configuration", e)
            }
        } else {
            // VFS not ready within timeout, register listener as fallback
            logger.warn("[HarmonyOS] VFS not ready within timeout, registering fallback listener")
            registerVfsFallbackListener(project)
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
        
        logger.info("[HarmonyOS] MyProjectActivity completed for project: ${project.name}")
    }

    /**
     * Waits for VFS to contain HarmonyOS project markers.
     * Returns true if VFS is ready, false if timeout.
     */
    private suspend fun waitForVfsReady(project: Project): Boolean {
        val basePath = project.basePath
        if (basePath == null) {
            logger.warn("[HarmonyOS] Project basePath is null")
            return false
        }
        
        logger.info("[HarmonyOS] Waiting for VFS to be ready...")
        
        // Check for either harmonyApp directory or local.properties
        val pathsToCheck = listOf(
            "harmonyApp",
            "local.properties"
        )
        
        val ready = vfsChecker.waitForPaths(
            project = project,
            paths = pathsToCheck,
            timeoutMs = 5000,
            pollIntervalMs = 100
        )
        
        if (ready) {
            logger.info("[HarmonyOS] VFS is ready, found project markers")
        } else {
            logger.warn("[HarmonyOS] VFS not ready within timeout")
        }
        
        return ready
    }

    /**
     * Registers a VFS listener as fallback for delayed file appearance.
     */
    private fun registerVfsFallbackListener(project: Project) {
        // Create a connection that we can disconnect later
        val connection = project.messageBus.connect()
        
        val listener = object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                // Check if any event involves harmonyApp directory or local.properties
                val relevantEvent = events.any { event ->
                    val path = event.path
                    path.contains("harmonyApp") || path.endsWith("local.properties")
                }
                
                if (relevantEvent) {
                    logger.info("[HarmonyOS] VFS event detected, attempting configuration creation")
                    
                    // Unregister this listener to prevent multiple attempts
                    connection.disconnect()
                    logger.info("[HarmonyOS] VFS fallback listener unregistered")
                    
                    // Attempt configuration creation
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            autoCreateharmonyAppConfiguration(project)
                        } catch (e: Exception) {
                            logger.error("[HarmonyOS] Fallback configuration creation failed", e)
                        }
                    }
                }
            }
        }
        
        connection.subscribe(
            VirtualFileManager.VFS_CHANGES,
            listener
        )
        
        logger.info("[HarmonyOS] VFS fallback listener registered")
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
            
            // Check ConfigurationCreationGuard before proceeding
            if (!ConfigurationCreationGuard.tryAcquire(basePath)) {
                logger.info("[HarmonyOS] Configuration creation already attempted for this project, skipping")
                return
            }
            
            logger.info("[HarmonyOS] 开始检查是否需要创建 harmonyApp 运行配置...")
            logger.info("[HarmonyOS] 项目路径: $basePath")
            
            // 检查项目是否为 HarmonyOS 项目 (使用 VFS API)
            if (!isHarmonyOSProjectVfs(project)) {
                logger.info("[HarmonyOS] 不是 HarmonyOS 项目，跳过创建 harmonyApp 配置")
                return
            }
            
            logger.info("[HarmonyOS] 检测到 HarmonyOS 项目，准备创建运行配置")
            
            // Use invokeAndWait for synchronous execution to ensure completion
            ApplicationManager.getApplication().invokeAndWait {
                try {
                    createConfigurationOnEdt(project)
                } catch (e: Exception) {
                    logger.error("[HarmonyOS] Failed to create configuration on EDT", e)
                }
            }
            
        } catch (e: Exception) {
            logger.error("[HarmonyOS] Failed to create harmonyApp configuration", e)
        }
    }

    /**
     * Creates the run configuration on EDT thread.
     * Must be called from EDT.
     */
    private fun createConfigurationOnEdt(project: Project) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        
        val runManager = RunManager.getInstance(project)
        logger.info("[HarmonyOS] RunManager obtained")
        
        // Check if configuration already exists (idempotent)
        val existingConfig = runManager.allSettings.find { settings ->
            settings.type is HarmonyConfigurationType && settings.name == "harmonyApp"
        }
        
        if (existingConfig != null) {
            logger.info("[HarmonyOS] Configuration already exists, skipping")
            return
        }
        
        logger.info("[HarmonyOS] Creating new configuration...")
        
        // Create configuration
        val configurationType = HarmonyConfigurationType.getInstance()
        val factory = configurationType.configurationFactories.firstOrNull()
        
        if (factory == null) {
            logger.error("[HarmonyOS] Configuration factory not found")
            return
        }
        
        val settings = runManager.createConfiguration("harmonyApp", factory)
        runManager.addConfiguration(settings)
        
        // Set as selected if no other configuration is selected
        if (runManager.selectedConfiguration == null) {
            runManager.selectedConfiguration = settings
            logger.info("[HarmonyOS] Set as selected configuration")
        }
        
        logger.info("[HarmonyOS] Configuration created successfully")
        logger.info("[HarmonyOS] Total configurations: ${runManager.allSettings.size}")
    }
    
    /**
     * 检查是否为 HarmonyOS 项目 (使用 VFS API)
     * 
     * 规则：
     * 1. 项目根目录下存在 harmonyApp 目录，或
     * 2. local.properties 中配置了有效的 local.ohos.path
     */
    private fun isHarmonyOSProjectVfs(project: Project): Boolean {
        return ApplicationManager.getApplication().runReadAction<Boolean> {
            val basePath = project.basePath ?: return@runReadAction false
            val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath)
                ?: return@runReadAction false
            
            // Rule 1: Check for harmonyApp directory
            val harmonyAppDir = baseDir.findChild("harmonyApp")
            if (harmonyAppDir != null && harmonyAppDir.isDirectory) {
                logger.info("[HarmonyOS] Found harmonyApp directory in VFS")
                return@runReadAction true
            }
            
            // Rule 2: Check local.properties for local.ohos.path
            val localPropsFile = baseDir.findChild("local.properties")
            if (localPropsFile != null && !localPropsFile.isDirectory) {
                try {
                    val content = String(localPropsFile.contentsToByteArray())
                    val properties = java.util.Properties()
                    properties.load(content.byteInputStream())
                    
                    val ohosPath = properties.getProperty("local.ohos.path")?.trim()
                    if (!ohosPath.isNullOrEmpty()) {
                        val ohosDir = LocalFileSystem.getInstance().findFileByPath(ohosPath)
                        if (ohosDir != null && ohosDir.isDirectory) {
                            logger.info("[HarmonyOS] Found valid local.ohos.path in VFS")
                            return@runReadAction true
                        } else {
                            logger.warn("[HarmonyOS] local.ohos.path configured but path not found: $ohosPath")
                        }
                    }
                } catch (e: Exception) {
                    logger.error("[HarmonyOS] Error reading local.properties", e)
                }
            }
            
            logger.info("[HarmonyOS] No HarmonyOS project markers found in VFS")
            false
        }
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
