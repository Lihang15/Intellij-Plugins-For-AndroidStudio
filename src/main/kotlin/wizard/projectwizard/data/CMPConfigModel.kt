package wizard.projectwizard.data


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.tools.idea.wizard.model.WizardModel

class CMPConfigModel : WizardModel() {
    var isAndroidEnable: Boolean by mutableStateOf(false)
    var isIOSEnable: Boolean by mutableStateOf(false)
    var isDesktopEnable: Boolean by mutableStateOf(false)
    var packageName by mutableStateOf("")

    override fun handleFinished() = Unit
}