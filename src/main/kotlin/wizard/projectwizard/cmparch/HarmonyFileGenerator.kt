package wizard.projectwizard.cmparch

import wizard.projectwizard.data.CMPConfigModel
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.starters.local.GeneratorAsset
import com.intellij.ide.starters.local.GeneratorTemplateFile
import com.intellij.ide.starters.local.GeneratorResourceFile

class HarmonyFileGenerator(params: CMPConfigModel) : FileGenerator(params) {
    override fun generate(ftManager: FileTemplateManager, packageName: String): List<GeneratorAsset> {
        return listOf(
            GeneratorTemplateFile(
                "composeApp/src/desktopMain/kotlin/$packageName/main.kt",
                ftManager.getCodeTemplate(Template.DESKTOP_MAIN)
            ),
             GeneratorTemplateFile(
                "harmonyApp/.gitignore",
                ftManager.getCodeTemplate(Template.GIT_IGNORE)
            ),
             GeneratorTemplateFile(
                "harmonyApp/build-profile.json5",
                ftManager.getCodeTemplate(Template.BUILD_PROFILE_JSON5)
            ),
             GeneratorTemplateFile(
                "harmonyApp/code-linter.json5",
                ftManager.getCodeTemplate(Template.CODE_LINTER_JSON5)
            ),
            GeneratorTemplateFile(
                "harmonyApp/hvigorfile.ts",
                ftManager.getCodeTemplate(Template.HVIGORFILE)
            ),
            GeneratorTemplateFile(
                "harmonyApp/oh-package-lock.json5",
                ftManager.getCodeTemplate(Template.OH_PACKAGE_LOCK_JSON5)
            ),
              GeneratorTemplateFile(
                "harmonyApp/oh-package.json5",
                ftManager.getCodeTemplate(Template.OH_PACKAGE_JSON5)
            ),

            GeneratorTemplateFile(
                "harmonyApp/AppScope/app.json5",
                ftManager.getCodeTemplate(Template.APP_JSON5)
            ),
            GeneratorTemplateFile(
                "harmonyApp/AppScope/resources/base/element/string.json",
                ftManager.getCodeTemplate(Template.STRING_JSON)
            ),
             GeneratorTemplateFile(
                "harmonyApp/AppScope/resources/base/element/string.json",
                ftManager.getCodeTemplate(Template.STRING_JSON)
            ),
             GeneratorTemplateFile(
                "harmonyApp/AppScope/resources/base/media/layered_image.json",
                ftManager.getCodeTemplate(Template.LAYERED_IMAGE)
            ),
             GeneratorResourceFile(
                "harmonyApp/AppScope/resources/base/media/background.png",  // 目标路径
               this::class.java.classLoader.getResource("static/background.png")!! 
            ),
              GeneratorResourceFile(
                "harmonyApp/AppScope/resources/base/media/foreground.png",  // 目标路径
               this::class.java.classLoader.getResource("static/foreground.png")!! 
            ),
        )
    }
}