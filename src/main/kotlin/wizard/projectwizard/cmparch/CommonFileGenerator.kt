package wizard.projectwizard.cmparch

import wizard.projectwizard.data.CMPConfigModel
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.starters.local.GeneratorAsset
import com.intellij.ide.starters.local.GeneratorTemplateFile
import com.intellij.openapi.vfs.VirtualFile

class CommonFileGenerator(
    params: CMPConfigModel,
    private val dataModel: MutableMap<String, Any>,
    private val virtualFile: VirtualFile,
) : FileGenerator(params) {
    override fun generate(ftManager: FileTemplateManager, packageName: String): List<GeneratorAsset> {
        val list = mutableListOf<GeneratorAsset>()
        return list.apply {
            addAll(
                listOf(
                    GeneratorTemplateFile(
                        "build.gradle.kts",
                        ftManager.getCodeTemplate(Template.GRADLE_KTS)
                    ),
                    GeneratorTemplateFile(
                        "settings.gradle.kts",
                        ftManager.getCodeTemplate(Template.SETTINGS_GRADLE)
                    ),
                    GeneratorTemplateFile(
                        "gradle.properties",
                        ftManager.getCodeTemplate(Template.GRADLE_PROPERTIES)
                    ),
                    GeneratorTemplateFile(
                        "gradle/wrapper/gradle-wrapper.properties",
                        ftManager.getCodeTemplate(Template.GRADLE_WRAPPER_PROPERTIES)
                    ),
                    GeneratorTemplateFile(
                        "gradle/libs.versions.toml",
                        ftManager.getCodeTemplate(Template.TOML)
                    ),
                    GeneratorTemplateFile(
                        "my_main.cpp",
                        ftManager.getCodeTemplate(Template.MY_MAIN_CPP)
                    ),
                    GeneratorTemplateFile(
                        "composeApp/src/commonMain/kotlin/$packageName/App.kt",
                        ftManager.getCodeTemplate(Template.COMMON_APP)
                    ),
                    GeneratorTemplateFile(
                        "composeApp/src/commonMain/composeResources/drawable/compose-multiplatform.xml",
                        ftManager.getCodeTemplate(Template.COMMON_COMPOSE_RESOURCES_MULTIPLATFORM_XML)
                    ),
                    GeneratorTemplateFile(
                        "composeApp/build.gradle.kts",
                        ftManager.getCodeTemplate(Template.COMPOSE_GRADLE_KTS)
                    ),
                )
            )
        }.toList()
    }
}