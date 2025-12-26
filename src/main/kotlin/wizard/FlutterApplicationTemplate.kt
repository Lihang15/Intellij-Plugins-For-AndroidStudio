package wizard


import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.TemplateConstraint
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.impl.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val flutterApplicationTemplate
    get() = template {
        name = "My Eazytec-KMP Project Template"
        description = "My Eazytec-KMP Project Template"
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
            name = "HOSO SDK Path"
            default = getFlutterSdkPathFromEnv()
            help = "Path to HOSO SDK"
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
        val usehoso = booleanParameter {
            name = "Use usehoso for Arkts code"
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
            CheckBoxWidget(usehoso),

        )
//        thumb { File("test.png") }
    }

private fun getFlutterSdkPathFromEnv(): String {
    return System.getenv("FLUTTER_SDK") ?: ""
}



