package wizard


import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.WizardTemplateProvider

class FlutterWizardTemplateProvider : WizardTemplateProvider() {

    override fun getTemplates(): List<Template> {
        return listOf(
            flutterApplicationTemplate
        )
    }
}
