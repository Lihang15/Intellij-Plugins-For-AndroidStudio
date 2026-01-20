package org.jetbrains.plugins.template.projectsync

import com.intellij.execution.RunManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.plugins.template.cpp.MyMainCppConfigurationType
import java.io.File
import com.google.gson.Gson
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")

        // 在项目启动时自动检查并创建 MyMainApp 运行配置
        autoCreateMyMainAppConfiguration(project)

        //开始工程同步
        syncProjectData(project)
    }

    /**
     * 如果项目根目录存在 my_main.cpp，则自动在 Run/Debug Configurations 中添加一个 MyMainApp 配置
     */
    private fun autoCreateMyMainAppConfiguration(project: Project) {
        val basePath = project.basePath ?: return
        val myMainCppFile = File(basePath, "my_main.cpp")
        if (!myMainCppFile.exists()) {
            return
        }

        val runManager = RunManager.getInstance(project)

        // 如果已经存在名为 MyMainApp 且类型为 MyMainCppConfigurationType 的配置，则不重复创建
        val existing = runManager.allSettings.any { settings ->
            settings.type is MyMainCppConfigurationType && settings.name == "MyMainApp"
        }
        if (existing) {
            return
        }

        val configurationType = MyMainCppConfigurationType.getInstance()
        val factory = configurationType.configurationFactories.firstOrNull() ?: return

        val settings = runManager.createConfiguration("MyMainApp", factory)
        // 这里目前不需要对 configuration 做特别初始化，使用默认行为：项目根目录 my_main.cpp

        runManager.addConfiguration(settings)
        // 不强制设为当前选中，只是让它出现在列表里；如果你希望默认选中，可以取消下一行注释
        // runManager.selectedConfiguration = settings
    }

      /**
     * sync 项目数据，供插件全局使用
     */
      private suspend fun syncProjectData(project: Project){
        val projectPath = project.basePath ?: return
        val scriptPath = "/Users/admin/generate.sh"
        val jsonFile = File(projectPath, "sync-output-data.json")

        // 在 IO 线程池中运行脚本和文件操作
        withContext(Dispatchers.IO) {
            try {
                val scriptFile = File(scriptPath)
                if (!scriptFile.exists()) {
                    println("ProjectSync Error: 脚本不存在于 $scriptPath")
                    return@withContext
                }

                // 1. 执行脚本，并将当前项目路径作为参数传递给脚本
                // 脚本内部可以使用 $1 获取当前项目根目录
                val process = ProcessBuilder("sh", scriptPath, projectPath)
                    .directory(File(projectPath)) // 设置工作目录为项目根目录
                    .redirectErrorStream(true)
                    .start()

                // 等待脚本执行结束（超时时间 30 秒）
                process.waitFor(30, TimeUnit.SECONDS)

                // 2. 检查并读取生成的 JSON 文件
                if (jsonFile.exists()) {
                    val content = FileUtil.loadFile(jsonFile, "UTF-8")
                    val model = Gson().fromJson(content, SyncDataModel::class.java)

                    // 3. 将数据存入 Service
                    DataSyncService.getInstance(project).dataModel = model
                    
                    // 4. 刷新虚拟文件系统，让 IDE 感知到新文件的生成
                    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jsonFile)
                    
                    println("ProjectSync Success: 数据已加载至模型，版本: ${model.version}")
                } else {
                    println("ProjectSync Error: 脚本执行完成但未发现 $jsonFile")
                }
            } catch (e: Exception) {
                println("ProjectSync Exception: ${e.message}")
                e.printStackTrace()
            }
        }
     }


}
