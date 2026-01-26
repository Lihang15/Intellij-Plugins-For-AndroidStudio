package wizard.projectwizard.recipes

import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import wizard.projectwizard.service.AnalyticsService
import wizard.common.Utils
import wizard.projectwizard.data.CMPConfigModel
import wizard.projectwizard.data.CMPImageLibrary
import wizard.projectwizard.data.CMPNetworkLibrary
import wizard.projectwizard.cmparch.*
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.starters.local.GeneratorAsset
import com.intellij.ide.starters.local.GeneratorEmptyDirectory
import com.intellij.ide.starters.local.GeneratorTemplateFile
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.kotlin.idea.core.util.toVirtualFile
import wizard.projectwizard.gradle.Versions
import wizard.projectwizard.ProjectGenerationHelper
import com.intellij.openapi.diagnostic.thisLogger

fun composeMultiplatformProjectRecipe(
    moduleData: ModuleTemplateData,
    packageName: String,
    isAndroidEnable: Boolean,
    isIosEnable: Boolean,
    isDesktopEnable: Boolean,
    selectedNetworkLibrary: CMPNetworkLibrary,
    isRoomEnable: Boolean,
    selectedImageLibrary: CMPImageLibrary,
    isKoinEnable: Boolean,
    isNavigationEnable: Boolean,
    isDataDomainDiUiEnable: Boolean,
    screens: String,
) {
    val analyticsService = AnalyticsService.getInstance()
    val (projectData, _, _) = moduleData
    val packagePath = escapeKotlinIdentifier(packageName)

    val screenList = if (screens.isNotEmpty()) {
        screens.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    } else {
        listOf("Main")
    }

    val screenListString = StringBuilder().apply {
        screenList.forEachIndexed { index, it ->
            append("        composable<$it> {\n")
            if (isKoinEnable) append("            val viewModel = koinViewModel<${it}ViewModel>()\n")
            else append("            val viewModel = viewModel<${it}ViewModel>(it)\n")
            append("            val uiState by viewModel.uiState.collectAsStateWithLifecycle()\n")
            append("            val uiEffect = viewModel.uiEffect\n")
            append("            ${it}Screen(\n")
            append("                uiState = uiState,\n")
            append("                uiEffect = uiEffect,\n")
            append("                onAction = viewModel::onAction\n")
            append("            )\n")
            if (index == screenList.lastIndex) append("        }")
            else append("        }\n")
        }
    }.toString()

    val screensImportsString = StringBuilder().apply {
        screenList.forEachIndexed { index, it ->
            append("import $packagePath.ui.${it.lowercase()}.${it}Screen\n")
            append("import $packagePath.ui.${it.lowercase()}.${it}ViewModel\n")
            if (index == screenList.lastIndex) append("import $packagePath.navigation.Screen.$it")
            else append("import $packagePath.navigation.Screen.$it\n")
        }
    }.toString()

    val navigationScreens = StringBuilder().apply {
        screenList.forEachIndexed { index, it ->
            append("    @Serializable\n")
            if (index == screenList.lastIndex) append("    data object $it : Screen")
            else append("    data object $it : Screen\n")
        }
    }.toString()

    val viewModelImports = StringBuilder().apply {
        screenList.forEachIndexed { index, it ->
            if (index == screenList.lastIndex) append("import $packagePath.ui.${it.lowercase()}.${it}ViewModel")
            else append("import $packagePath.ui.${it.lowercase()}.${it}ViewModel\n")
        }
    }.toString()

    val viewModelModule = StringBuilder().apply {
        screenList.forEachIndexed { index, it ->
            if (index == screenList.lastIndex) append("    factoryOf(::${it}ViewModel)")
            else append("    factoryOf(::${it}ViewModel)\n")
        }
    }.toString()

    val config = CMPConfigModel().apply {
        this.isAndroidEnable = isAndroidEnable
        this.isIOSEnable = isIosEnable
        this.isDesktopEnable = isDesktopEnable
        this.selectedNetworkLibrary = selectedNetworkLibrary
        this.isRoomEnable = isRoomEnable
        this.isCoilEnable = selectedImageLibrary == CMPImageLibrary.Coil
        this.isKamelEnable = selectedImageLibrary == CMPImageLibrary.Kamel
        this.isKoinEnable = isKoinEnable
        this.isNavigationEnable = isNavigationEnable
        this.isDataDomainDiUiEnable = isDataDomainDiUiEnable
        this.screens = screenList
        this.packageName = packagePath
    }
    val isKtorEnable = config.selectedNetworkLibrary == CMPNetworkLibrary.Ktor
    val isKtorfitEnable = config.selectedNetworkLibrary == CMPNetworkLibrary.Ktorfit
    val dataModel = mutableMapOf(
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
        "IS_DESKTOP_ENABLE" to config.isDesktopEnable,
        "IS_KTOR_ENABLE" to isKtorEnable,
        "IS_KTORFIT_ENABLE" to isKtorfitEnable,
        "IS_ROOM_ENABLE" to config.isRoomEnable,
        "IS_COIL_ENABLE" to config.isCoilEnable,
        "IS_KAMEL_ENABLE" to config.isKamelEnable,
        "IS_KOIN_ENABLE" to config.isKoinEnable,
        "IS_NAVIGATION_ENABLE" to config.isNavigationEnable,
        "IS_DATA_DOMAIN_DI_UI_ENABLE" to config.isDataDomainDiUiEnable,
        "SCREENS" to screenList,
        "CMP_AGP" to Versions.versionList["cmp-agp"].orEmpty(),
        "CMP_KOTLIN" to Versions.versionList["cmp-kotlin"].orEmpty(),
        "CMP_ACTIVITY_COMPOSE" to Versions.versionList["cmp-activity-compose"].orEmpty(),
        "CMP_UI_TOOLING" to Versions.versionList["cmp-ui-tooling"].orEmpty(),
        "CMP_MULTIPLATFORM" to Versions.versionList["cmp-multiplatform"].orEmpty(),
        "CMP_KOIN" to Versions.versionList["cmp-koin"].orEmpty(),
        "CMP_KTOR" to Versions.versionList["cmp-ktor"].orEmpty(),
        "KTORFIT" to Versions.versionList["ktorfit"].orEmpty(),
        "CMP_NAVIGATION" to Versions.versionList["cmp-navigation"].orEmpty(),
        "CMP_KOTLINX_COROUTINES" to Versions.versionList["cmp-kotlinx-coroutines"].orEmpty(),
        "CMP_COIL" to Versions.versionList["cmp-coil"].orEmpty(),
        "CMP_KAMEL" to Versions.versionList["cmp-kamel"].orEmpty(),
        "CMP_KSP" to Versions.versionList["cmp-ksp"].orEmpty(),
        "CMP_ROOM" to Versions.versionList["cmp-room"].orEmpty(),
        "CMP_SQLITE" to Versions.versionList["cmp-sqlite"].orEmpty(),
        "CMP_KOTLINX_SERIALIZATION" to Versions.versionList["cmp-kotlinx-serialization"].orEmpty(),
        "SCREENS" to screenListString,
        "SCREENS_IMPORTS" to screensImportsString,
        "NAVIGATION_SCREENS" to navigationScreens,
        "START_DESTINATION" to screenList.first(),
        "VIEW_MODEL_IMPORTS" to viewModelImports,
        "VIEW_MODEL_MODULE" to viewModelModule,
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
            if (config.isDesktopEnable) DesktopFileGenerator(config) else null,
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
        message = "Your project is ready! 🚀 If you like the plugin, please comment and rate it on the plugin page. 🙏",
    )
}

