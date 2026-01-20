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

val KMPApplicationTemplate
    get() = template {
        name = "KMP-OHOS Project Template"
        description = "Create a new KMP-OHOS Template Project"
        minApi = 21
        constraints = listOf(
            TemplateConstraint.AndroidX,
            TemplateConstraint.Kotlin
        )
        category = Category.Application
        formFactor = FormFactor.Mobile
        screens = listOf(WizardUiContext.NewProject, WizardUiContext.NewProjectExtraDetail)


        val org = stringParameter {
            name = "Description"
            default = "test"
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
            name = "Use Arkts for OHOS code"
            default = true
        }

        val includeTest = booleanParameter {
            name = "Includes tests"
            default = false
        }



//        val platforms = multipleChoiceParameter {
//            name = "Platforms"
//            help = "Select target platforms"
//            options = listOf("Android", "iOS", "Web", "Windows", "macOS", "Linux")
//            default = listOf("Android", "iOS")
//        }


        // 定义项目生成逻辑
        recipe = { data ->
            KMPApplicationRecipe(
                moduleData = data as ModuleTemplateData,
                org = org.value,
                useKotlin = useKotlin.value,
                useSwift = useSwift.value,
                useohos = useohos.value,
                includeTest = includeTest.value
            )
        }

        widgets(
            TextFieldWidget(org),
            CheckBoxWidget(useKotlin),
            CheckBoxWidget(useSwift),
            CheckBoxWidget(useohos),
            CheckBoxWidget(includeTest),
        )
        
        // 使用自定义图片作为模板缩略图
        thumb = { getImage("KMPApplicationTemplate", "test") }
    }

private fun getFlutterSdkPathFromEnv(): String {
    return System.getenv("FLUTTER_SDK") ?: ""
}