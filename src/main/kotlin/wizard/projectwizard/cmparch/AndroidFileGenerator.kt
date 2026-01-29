package wizard.projectwizard.cmparch

import wizard.projectwizard.data.CMPConfigModel
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.starters.local.GeneratorAsset
import com.intellij.ide.starters.local.GeneratorTemplateFile
import com.intellij.ide.starters.local.GeneratorResourceFile

class AndroidFileGenerator(params: CMPConfigModel) : FileGenerator(params) {
    override fun generate(ftManager: FileTemplateManager, packageName: String): List<GeneratorAsset> {
        return listOf(
            GeneratorTemplateFile(
                "composeApp/src/androidMain/kotlin/$packageName/MainActivity.kt",
                ftManager.getCodeTemplate(Template.ANDROID_MAIN_ACTIVITY)
            ),
            GeneratorTemplateFile(
                "composeApp/src/androidMain/kotlin/$packageName/Platform.android.kt",
                ftManager.getCodeTemplate(Template.PLATFORM_ANDROID)
            ),

            GeneratorTemplateFile(
                "composeApp/src/androidMain/AndroidManifest.xml",
                ftManager.getCodeTemplate(Template.ANDROID_MANIFEST)
            ),
            GeneratorTemplateFile(
                "composeApp/src/androidMain/res/values/strings.xml",
                ftManager.getCodeTemplate(Template.VALUES)
            ),
            GeneratorTemplateFile(
                "composeApp/src/androidMain/res/drawable/ic_lancher_background.xml",
                ftManager.getCodeTemplate(Template.DRAWABLE)
            ),
             GeneratorTemplateFile(
                "composeApp/src/androidMain/res/drawable/ic_lancher_foreground.xml",
                ftManager.getCodeTemplate(Template.DRAWABLE_V24)
            ),
            GeneratorTemplateFile(
                "composeApp/src/androidMain/res/mipmap-anydpi-v26/ic_launcher.xml",
                ftManager.getCodeTemplate(Template.MIPMAP_ANYDPI_V26_IC)
            ),
        
            GeneratorTemplateFile(
                "composeApp/src/androidMain/res/mipmap-anydpi-v26/ic_launcher_round.xml",
                ftManager.getCodeTemplate(Template.MIPMAP_ANYDPI_V26_IC_ROUND)
            ),
            GeneratorResourceFile(
               "composeApp/src/androidMain/res/mipmap-hdpi/ic_launcher_round.png", 
               this::class.java.classLoader.getResource("static/ic_launcher_round.png")!! 
            ),
            GeneratorResourceFile(
               "composeApp/src/androidMain/res/mipmap-hdpi/ic_launcher.png", 
               this::class.java.classLoader.getResource("static/ic_launcher.png")!! 
            ),

             GeneratorResourceFile(
               "composeApp/src/androidMain/res/mipmap-mdpi/ic_launcher.png", 
               this::class.java.classLoader.getResource("static/ic_launcher.png")!! 
            ),
             GeneratorResourceFile(
               "composeApp/src/androidMain/res/mipmap-mdpi/ic_launcher_round.png", 
               this::class.java.classLoader.getResource("static/ic_launcher_round.png")!! 
            ),
             GeneratorResourceFile(
               "composeApp/src/androidMain/res/mipmap-xhdpi/ic_launcher.png", 
               this::class.java.classLoader.getResource("static/ic_launcher.png")!! 
            ),
             GeneratorResourceFile(
               "composeApp/src/androidMain/res/mipmap-xhdpi/ic_launcher_round.png", 
               this::class.java.classLoader.getResource("static/ic_launcher_round.png")!! 
            ),
             GeneratorResourceFile(
               "composeApp/src/androidMain/res/mipmap-xxhdpi/ic_launcher.png", 
               this::class.java.classLoader.getResource("static/ic_launcher.png")!! 
            ),
             GeneratorResourceFile(
               "composeApp/src/androidMain/res/mipmap-xxhdpi/ic_launcher_round.png", 
               this::class.java.classLoader.getResource("static/ic_launcher_round.png")!! 
            ),
             GeneratorResourceFile(
               "composeApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher.png", 
               this::class.java.classLoader.getResource("static/ic_launcher.png")!! 
            ),
             GeneratorResourceFile(
               "composeApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher_round.png", 
               this::class.java.classLoader.getResource("static/ic_launcher_round.png")!! 
            ),
        )
    }
}