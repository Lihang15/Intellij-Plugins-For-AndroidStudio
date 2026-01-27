package wizard.projectwizard.recipes

import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import wizard.projectwizard.service.AnalyticsService
import wizard.common.Utils
import wizard.projectwizard.data.CMPConfigModel
import wizard.projectwizard.cmparch.*
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.starters.local.GeneratorAsset
import com.intellij.ide.starters.local.GeneratorEmptyDirectory
import com.intellij.ide.starters.local.GeneratorResourceFile
import com.intellij.ide.starters.local.GeneratorTemplateFile
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.kotlin.idea.core.util.toVirtualFile
import wizard.projectwizard.ProjectGenerationHelper
import com.intellij.openapi.diagnostic.thisLogger

fun kotlinMultiplatformProjectRecipe(
    moduleData: ModuleTemplateData,
    packageName: String,
    isAndroidEnable: Boolean,
    isIosEnable: Boolean,
    isHarmonyEnable: Boolean,
) {
    val analyticsService = AnalyticsService.getInstance()
    val (projectData, _, _) = moduleData
    val packagePath = escapeKotlinIdentifier(packageName)

    val config = CMPConfigModel().apply {
        this.isAndroidEnable = isAndroidEnable
        this.isIOSEnable = isIosEnable
        this.isHarmonyEnable = isHarmonyEnable
        this.packageName = packagePath
    }
    
    val dataModel: MutableMap<String, Any> = mutableMapOf(
        "APP_NAME" to moduleData.themesData.appName,
        "APP_NAME_LOWERCASE" to moduleData.themesData.appName.lowercase(),
        "PACKAGE_NAME" to config.packageName,
        "MODULE_NAME" to moduleData.name,
        "BUNDLE_ID" to "\${BUNDLE_ID}",
        "TEAM_ID" to "\${TEAM_ID}",
        "PROJECT_DIR" to "\${PROJECT_DIR}",
        "USER_HOME" to "\${USER_HOME}",
        "ROOT_NODE" to "\${RootNode}",
        "PROJECT" to moduleData.themesData.appName,
        "BUILD_Versions_SDK_INT" to "\${Build.Versions.SDK_INT}",
        "JVM_JAVA_Versions" to "\${System.getProperty(\"java.Versions\")}",
        "IS_ANDROID_ENABLE" to config.isAndroidEnable,
        "IS_IOS_ENABLE" to config.isIOSEnable,
        "IS_DESKTOP_ENABLE" to config.isHarmonyEnable,
        "CMP_AGP" to "8.5.2",
        "CMP_KOTLIN" to "2.1.0",
        "CMP_ACTIVITY_COMPOSE" to "1.9.3",
        "CMP_UI_TOOLING" to "1.7.6",
        "CMP_MULTIPLATFORM" to "1.7.0-beta02",
        "CMP_KOTLINX_COROUTINES" to "1.9.0",
    )

    projectData.rootDir.toVirtualFile()?.apply {
        val logger = thisLogger()
        val fileTemplateManager = FileTemplateManager.getDefaultInstance()
        val generationHelper = ProjectGenerationHelper()
        val assets = mutableListOf<GeneratorAsset>()
        val platforms: List<FileGenerator> = listOfNotNull(
            CommonFileGenerator(config, dataModel, this),
            if (config.isAndroidEnable) AndroidFileGenerator(config) else null,
            if (config.isIOSEnable) IOSFileGenerator(config) else null,
            if (config.isHarmonyEnable) HarmonyFileGenerator(config) else null,
        )
        assets.addAll(platforms.flatMap { it.generate(fileTemplateManager, config.packageName) })
        
        // Generate all files with conflict resolution
        var filesCreated = 0
        var filesSkipped = 0
        
        assets.forEach { asset ->
            try {
                when (asset) {
                    is GeneratorEmptyDirectory -> {
                        Utils.createEmptyDirectory(this, asset.relativePath)
                        filesCreated++
                    }
                    is GeneratorTemplateFile -> {
                        // Use Utils but it will internally handle conflicts better now
                        Utils.generateFileFromTemplate(dataModel, this, asset)
                        filesCreated++
                    }
                    is GeneratorResourceFile -> {
                        // Copy static resource files (images, etc.)
                        Utils.copyResourceFile(this, asset)
                        filesCreated++
                    }
                    else -> {
                        logger.warn("Unknown asset type: $asset")
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to generate asset: ${asset}", e)
                filesSkipped++
            }
        }
        
        // Delete auto-generated app directory (created by Android Studio when category = Application)
        val appDir = this.findChild("app")
        if (appDir != null && appDir.exists()) {
            ApplicationManager.getApplication().runWriteAction {
                try {
                    appDir.delete(this)
                    logger.info("Deleted auto-generated app directory")
                } catch (e: Exception) {
                    logger.warn("Failed to delete app directory: ${e.message}")
                }
            }
        }
        
        // Single VFS refresh at the end
        logger.info("Project generation complete: $filesCreated files created, $filesSkipped skipped")
        generationHelper.flushVfsRefreshSync(this)
    }
    analyticsService.track("compose_multiplatform_project_created")

    Utils.showInfo(
        title = "Quick Project Wizard",
        message = "Your project is ready! If you like the plugin, please comment and rate it on the plugin page.",
    )
}

