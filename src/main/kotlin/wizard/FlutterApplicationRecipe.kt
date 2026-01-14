package wizard

import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VfsUtil
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

fun RecipeExecutor.flutterApplicationRecipe(
    moduleData: ModuleTemplateData,
    org: String,
    useKotlin: Boolean,
    useSwift: Boolean,
    useohos: Boolean,
    includeTest: Boolean
) {
    // 获取项目根目录（而不是 module 目录）
    // moduleData.rootDir 指向 abc/app，我们需要 abc/
    val moduleRoot = moduleData.rootDir.toPath()
    val projectRoot = moduleRoot.parent ?: moduleRoot
    
    println("[KMP-OHOS] Module root: $moduleRoot")
    println("[KMP-OHOS] Project root: $projectRoot")
    println("[KMP-OHOS] Parameters: useKotlin=$useKotlin, useSwift=$useSwift, useohos=$useohos")
    
    // 在后台异步生成项目结构
    ApplicationManager.getApplication().invokeLater {
        generateProjectStructure(
            projectRoot,
            org,
            useKotlin,
            useSwift,
            useohos,
            includeTest
        )
    }
}

private fun generateProjectStructure(
    projectRoot: Path,
    org: String,
    useKotlin: Boolean,
    useSwift: Boolean,
    useohos: Boolean,
    includeTest: Boolean
) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(
        null,
        "Generating KMP-OHOS Project Structure...",
        true
    ) {
        override fun run(indicator: ProgressIndicator) {
            try {
                indicator.text = "Finding template directory..."
                val templateRoot = findTemplateRoot()
                
                indicator.text = "Copying common files..."
                indicator.fraction = 0.1
                
                // 1. 复制通用文件（根目录下的文件，但跳过 settings.gradle.kts）
                copyRootFiles(templateRoot, projectRoot)
                
                indicator.fraction = 0.2
                
                // 2. 复制 lib-arith（共享库）
                indicator.text = "Copying shared library..."
                copyDirectory(templateRoot.resolve("lib-arith"), projectRoot.resolve("lib-arith"))
                
                indicator.fraction = 0.4
                
                // 3. 根据参数复制平台模块
                val includedModules = mutableListOf<String>(":lib-arith")
                
                if (useKotlin) {
                    indicator.text = "Copying Android module (composeApp)..."
                    copyDirectory(templateRoot.resolve("composeApp"), projectRoot.resolve("composeApp"))
                    includedModules.add(":composeApp")
                }
                
                indicator.fraction = 0.6
                
                if (useSwift) {
                    indicator.text = "Copying iOS module (iosApp)..."
                    copyDirectory(templateRoot.resolve("iosApp"), projectRoot.resolve("iosApp"))
                    includedModules.add(":iosApp")
                }
                
                indicator.fraction = 0.8
                
                if (useohos) {
                    indicator.text = "Copying HarmonyOS module (harmonyapp)..."
                    copyDirectory(templateRoot.resolve("harmonyapp"), projectRoot.resolve("harmonyapp"))
                    includedModules.add(":harmonyapp")
                }
                
                // 4. 生成自定义的 settings.gradle.kts
                indicator.text = "Generating settings.gradle.kts..."
                generateSettingsGradle(templateRoot, projectRoot, includedModules)
                
                indicator.fraction = 0.9
                
                // 4. 刷新文件系统并等待完成
                indicator.text = "Refreshing project..."
                ApplicationManager.getApplication().invokeAndWait {
                    VfsUtil.markDirtyAndRefresh(false, true, true, projectRoot.toFile())
                }
                
                // 等待一小段时间确保文件系统刷新完成
                Thread.sleep(500)
                
                indicator.fraction = 1.0
                println("[KMP-OHOS] Project structure generated successfully!")
                
                // 5. 在独立的 EDT 线程中打开项目（避免阻塞当前任务）
                ApplicationManager.getApplication().invokeLater {
                    openProject(projectRoot)
                }
                
            } catch (e: Exception) {
                println("[KMP-OHOS] Error generating project: ${e.message}")
                e.printStackTrace()
            }
        }
    })
}

private fun findTemplateRoot(): Path {
    // 方法 1：从工作目录查找（开发期调试）
    val workspace = Paths.get(System.getProperty("user.dir"), "kmptcp_kotlin_sample_template")
    if (Files.exists(workspace)) {
        println("[KMP-OHOS] Found template at workspace: $workspace")
        return workspace
    }
    
    // 方法 2：从插件安装目录查找
    try {
        val pluginId = com.intellij.openapi.extensions.PluginId.getId(
            "com.github.lihang15.intellijpluginsforandroidstudio"
        )
        val plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId)
        if (plugin != null) {
            val pluginPath = plugin.pluginPath
            val templatePath = pluginPath.resolve("kmptcp_kotlin_sample_template")
            if (Files.exists(templatePath)) {
                println("[KMP-OHOS] Found template at plugin path: $templatePath")
                return templatePath
            }
            
            // 尝试从插件的父目录查找（开发期）
            val devTemplatePath = pluginPath.parent?.resolve("kmptcp_kotlin_sample_template")
            if (devTemplatePath != null && Files.exists(devTemplatePath)) {
                println("[KMP-OHOS] Found template at dev path: $devTemplatePath")
                return devTemplatePath
            }
        }
    } catch (e: Exception) {
        println("[KMP-OHOS] Error finding template from plugin: ${e.message}")
        e.printStackTrace()
    }
    
    // 方法 3：从多个可能的开发路径查找
    val possiblePaths = listOf(
        "/Users/admin/EazyWork/projects/Intellij-Plugins-For-AndroidStudio/kmptcp_kotlin_sample_template",
        System.getProperty("user.home") + "/EazyWork/projects/Intellij-Plugins-For-AndroidStudio/kmptcp_kotlin_sample_template"
    )
    
    for (pathStr in possiblePaths) {
        val path = Paths.get(pathStr)
        if (Files.exists(path)) {
            println("[KMP-OHOS] Found template at fallback path: $path")
            return path
        }
    }
    
    error("Template directory 'kmptcp_kotlin_sample_template' not found. Searched paths: $workspace, plugin path, fallback paths")
}

private fun copyRootFiles(source: Path, target: Path) {
    // 只复制根目录的文件，不复制子目录
    Files.newDirectoryStream(source).use { stream ->
        stream.forEach { sourcePath ->
            if (Files.isRegularFile(sourcePath)) {
                val fileName = sourcePath.fileName.toString()
                // 跳过不需要的文件：隐藏文件、local.properties、settings.gradle.kts
                if (!fileName.startsWith(".") && 
                    fileName != "local.properties" && 
                    fileName != "settings.gradle.kts") {
                    val targetPath = target.resolve(fileName)
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
                    println("[KMP-OHOS] Copied: $fileName")
                }
            }
        }
    }
    
    // 复制 gradle 目录
    copyDirectory(source.resolve("gradle"), target.resolve("gradle"))
}

private fun copyDirectory(source: Path, target: Path) {
    if (!Files.exists(source)) {
        println("[KMP-OHOS] Skip non-existent directory: $source")
        return
    }
    
    println("[KMP-OHOS] Copying directory: ${source.fileName} -> ${target.fileName}")
    
    Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            val targetDir = target.resolve(source.relativize(dir))
            Files.createDirectories(targetDir)
            return FileVisitResult.CONTINUE
        }
        
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            val targetFile = target.resolve(source.relativize(file))
            Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
            return FileVisitResult.CONTINUE
        }
    })
}

private fun generateSettingsGradle(templateRoot: Path, projectRoot: Path, includedModules: List<String>) {
    try {
        // 读取模板的 settings.gradle.kts
        val templateSettings = templateRoot.resolve("settings.gradle.kts")
        if (!Files.exists(templateSettings)) {
            println("[KMP-OHOS] Template settings.gradle.kts not found, skipping")
            return
        }
        
        val templateContent = Files.readString(templateSettings)
        
        // 提取配置部分（pluginManagement 和 dependencyResolutionManagement）
        val pluginManagementStart = templateContent.indexOf("pluginManagement {")
        val dependencyManagementEnd = templateContent.indexOf("}", 
            templateContent.indexOf("dependencyResolutionManagement {") + 1) + 1
        
        val configSection = if (pluginManagementStart >= 0 && dependencyManagementEnd > 0) {
            templateContent.substring(pluginManagementStart, dependencyManagementEnd)
        } else {
            // 回退：使用基础配置
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            
            dependencyResolutionManagement {
                repositories {
                    google()
                    mavenCentral()
                }
            }
            """.trimIndent()
        }
        
        // 生成新的 settings.gradle.kts
        val newContent = buildString {
            appendLine("rootProject.name = \"${projectRoot.fileName}\"")
            appendLine("enableFeaturePreview(\"TYPESAFE_PROJECT_ACCESSORS\")")
            appendLine()
            appendLine(configSection)
            appendLine()
            
            // 添加模块
            includedModules.forEach { module ->
                appendLine("include(\"$module\")")
            }
        }
        
        val targetSettings = projectRoot.resolve("settings.gradle.kts")
        Files.writeString(targetSettings, newContent)
        
        println("[KMP-OHOS] Generated settings.gradle.kts with modules: $includedModules")
        
    } catch (e: Exception) {
        println("[KMP-OHOS] Error generating settings.gradle.kts: ${e.message}")
        e.printStackTrace()
    }
}

private fun openProject(projectPath: Path) {
    try {
        println("[KMP-OHOS] Opening project: $projectPath")
        
        // 确保项目目录存在
        if (!Files.exists(projectPath)) {
            println("[KMP-OHOS] Project path does not exist: $projectPath")
            return
        }
        
        // 检查是否有 settings.gradle.kts 或 build.gradle.kts
        val hasGradleConfig = Files.exists(projectPath.resolve("settings.gradle.kts")) ||
                             Files.exists(projectPath.resolve("build.gradle.kts"))
        
        if (!hasGradleConfig) {
            println("[KMP-OHOS] Warning: No Gradle configuration found in project")
        }
        
        val projectManager = ProjectManager.getInstance()
        
        // 使用 invokeLater 确保在 EDT 线程中执行
        ApplicationManager.getApplication().invokeLater {
            try {
                // 关闭当前的欢迎页面（如果有）
                val openProjects = projectManager.openProjects
                if (openProjects.isEmpty() || openProjects.all { it.name == "Default" }) {
                    // 使用更新的 API
                    projectManager.loadAndOpenProject(projectPath.toString())
                    println("[KMP-OHOS] Project opened successfully")
                } else {
                    println("[KMP-OHOS] A project is already open, skipping auto-open")
                }
            } catch (e: Exception) {
                println("[KMP-OHOS] Error in invokeLater: ${e.message}")
                e.printStackTrace()
            }
        }
    } catch (e: Exception) {
        println("[KMP-OHOS] Error opening project: ${e.message}")
        e.printStackTrace()
    }
}

// 仅供引用
private object FlutterApplicationRecipe