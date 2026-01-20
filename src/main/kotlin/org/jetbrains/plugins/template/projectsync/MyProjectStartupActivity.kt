package org.jetbrains.plugins.template.projectsync

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class MyProjectStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
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