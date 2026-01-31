package wizard.projectwizard

import com.android.tools.idea.wizard.template.*
import com.android.tools.idea.wizard.template.impl.defaultPackageNameParameter
import wizard.common.getImage
import wizard.projectwizard.recipes.kotlinMultiplatformProjectRecipe

val kotlinMultiplatformTemplate = template {
    name = "ProjectWizard - KMP"
    description = "Generate a Kotlin Multiplatform project"
    minApi = 23
    constraints = listOf(
        TemplateConstraint.AndroidX,
        TemplateConstraint.Kotlin
    )
    category = Category.Other  // Changed from Application to prevent auto-creation of app/ directory
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.NewProject, WizardUiContext.NewProjectExtraDetail)



    val isAndroidEnable = booleanParameter {
        name = "Android"
        default = true
        help = "Android platform"
    }

    val isIosEnable = booleanParameter {
        name = "iOS"
        default = true
        help = "iOS platform"
    }



    widgets(
        LabelWidget(""), 
        LabelWidget("Harmony"),
        LabelWidget("With Compose Multiplatform UI framework, or use ArkTsUI"),
        CheckBoxWidget(isAndroidEnable),
        LabelWidget("With Compose Multiplatform UI framework based on jetpack Compose"),
        CheckBoxWidget(isIosEnable),
        LabelWidget("With Compose Multiplatform UI framework, or use SwiftUI"),
        PackageNameWidget(defaultPackageNameParameter)
    )

    thumb = { getImage("KMPTemplate", "ohos") }

    recipe = { data: TemplateData ->
        kotlinMultiplatformProjectRecipe(
            moduleData = data as ModuleTemplateData,
            packageName = data.packageName,
            isAndroidEnable = isAndroidEnable.value,
            isIosEnable = isIosEnable.value,
            isHarmonyEnable = true,
        )
    }
}
