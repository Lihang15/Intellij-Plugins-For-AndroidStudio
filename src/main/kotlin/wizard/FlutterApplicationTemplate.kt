package wizard


import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.TemplateConstraint
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val flutterApplicationTemplate
    get() = template {
        name = "My OHOS-KMP Project Template"
        description = "My OHOS-KMP Project Template"
        minApi = 21
        constraints = listOf(
            TemplateConstraint.AndroidX,
            TemplateConstraint.Kotlin
        )
        category = Category.Application
        formFactor = FormFactor.Mobile
        screens = listOf(WizardUiContext.NewProject, WizardUiContext.NewProjectExtraDetail)

        // 定义可配置参数
        val projectName = stringParameter {
            name = "Project Name"
            default = "eazy-kmp_app"
            help = "The name of your Flutter project"
        }

        val sdkPath = stringParameter {
            name = "OHOS SDK Path"
            default = getFlutterSdkPathFromEnv()
            help = "Path to OHOS SDK"
        }

        val org = stringParameter {
            name = "Organization"
            default = "com.example"
            help = "The organization name (reverse domain)"
        }

        val useKotlin = booleanParameter {
            name = "Use Kotlin for Android code"
            default = true
        }

        val useSwift = booleanParameter {
            name = "Use Swift for iOS code"
            default = true
        }
        val useohos = booleanParameter {
            name = "Use ohos for Arkts code"
            default = true
        }

//        val platforms = multipleChoiceParameter {
//            name = "Platforms"
//            help = "Select target platforms"
//            options = listOf("Android", "iOS", "Web", "Windows", "macOS", "Linux")
//            default = listOf("Android", "iOS")
//        }


        // 定义项目生成逻辑
        recipe = { data ->
            flutterApplicationRecipe(
                moduleData = data as ModuleTemplateData,
                projectName = projectName.value,
                sdkPath = sdkPath.value,
                org = org.value,
                useKotlin = useKotlin.value,
                useSwift = useSwift.value,

            )
        }

        widgets(
            TextFieldWidget(projectName),
            TextFieldWidget(sdkPath),
            TextFieldWidget(org),
            CheckBoxWidget(useKotlin),
            CheckBoxWidget(useSwift),
            CheckBoxWidget(useohos),
        )
        
        // 先不使用自定义缩略图，使用 Android Studio 内置的
        // 因为 thumb 方法把 File 当作相对于 Android Studio 模板资源目录的路径
        thumb { File("compose-activity-material3").resolve("template_compose_empty_activity_material3.png") }
    }

private fun getFlutterSdkPathFromEnv(): String {
    return System.getenv("FLUTTER_SDK") ?: ""
}