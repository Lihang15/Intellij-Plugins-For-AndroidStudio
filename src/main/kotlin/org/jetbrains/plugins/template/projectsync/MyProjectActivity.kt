package org.jetbrains.plugins.template.projectsync

import com.intellij.execution.RunManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.plugins.template.cpp.MyMainCppConfigurationType
import org.jetbrains.plugins.template.cpp.MyMainCppFileCreator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MyProjectActivity : ProjectActivity {

    private val logger = thisLogger()
    private val syncExecutor: SyncExecutor = DefaultSyncExecutor()

    override suspend fun execute(project: Project) {
        logger.info("MyProjectActivity starting for project: ${project.name}")
        
        // 延迟一下，等待 VFS 刷新和文件系统同步
        kotlinx.coroutines.delay(1000) // 延迟 1 秒
        
        // 在项目启动时自动检查并创建 MyMainApp 运行配置
        try {
            autoCreateMyMainAppConfiguration(project)
        } catch (e: Exception) {
            logger.error("Failed to create MyMainApp configuration", e)
        }

        // 开始工程同步 - 使用 launch 确保不阻塞项目打开
        CoroutineScope(Dispatchers.IO).launch {
            try {
                syncProjectData(project)
            } catch (e: Exception) {
                // 捕获所有异常，确保不会阻止项目打开
                logger.error("Sync failed but project will continue to open", e)
            }
        }
        
        logger.info("MyProjectActivity completed for project: ${project.name}")
    }

    /**
     * 如果项目根目录存在 my_main.cpp，则自动在 Run/Debug Configurations 中添加一个 MyMainApp 配置
     * 优化了重复检查逻辑，提前返回以提高性能
     * 添加了重试机制以处理文件系统延迟
     */
    private suspend fun autoCreateMyMainAppConfiguration(project: Project) {
        try {
            val basePath = project.basePath
            if (basePath == null) {
                logger.warn("Project basePath is null, cannot create MyMainApp configuration")
                return
            }
            
            // 重试机制：尝试 3 次，每次间隔 500ms
            var myMainCppFile: File? = null
            for (attempt in 1..3) {
                myMainCppFile = File(basePath, "my_main.cpp")
                logger.info("Attempt $attempt: Checking for my_main.cpp at: ${myMainCppFile.absolutePath}")
                
                if (myMainCppFile.exists()) {
                    logger.info("my_main.cpp found on attempt $attempt")
                    break
                }
                
                if (attempt < 3) {
                    logger.info("my_main.cpp not found, waiting 500ms before retry...")
                    kotlinx.coroutines.delay(500)
                    
                    // 刷新 VFS
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait {
                        val vfsFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                            .refreshAndFindFileByPath(basePath)
                        vfsFile?.refresh(false, true)
                    }
                }
            }
            
            // 最终检查
            if (myMainCppFile == null || !myMainCppFile.exists()) {
                logger.warn("my_main.cpp not found after 3 attempts, skipping MyMainApp configuration creation")
                return
            }
            
            logger.info("my_main.cpp confirmed, attempting to create MyMainApp configuration")

            val runManager = RunManager.getInstance(project)
            
            // 检查配置是否已存在
            val existingConfig = runManager.allSettings.find { settings ->
                settings.type is MyMainCppConfigurationType && settings.name == "MyMainApp"
            }
            
            if (existingConfig != null) {
                logger.info("MyMainApp configuration already exists")
                return
            }

            // 创建新配置
            val configurationType = MyMainCppConfigurationType.getInstance()
            val factory = configurationType.configurationFactories.firstOrNull()
            
            if (factory == null) {
                logger.error("MyMainCppConfigurationType factory not found")
                return
            }

            val settings = runManager.createConfiguration("MyMainApp", factory)
            runManager.addConfiguration(settings)
            
            // 可选：设置为选中的配置
            runManager.selectedConfiguration = settings
            
            logger.info("✅ MyMainApp configuration created and added successfully")
            logger.info("Total configurations: ${runManager.allSettings.size}")
        } catch (e: Exception) {
            logger.error("Failed to create MyMainApp configuration", e)
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
