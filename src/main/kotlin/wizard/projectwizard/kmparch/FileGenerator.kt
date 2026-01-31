package wizard.projectwizard.kmparch

import wizard.projectwizard.data.KMPConfigModel
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.starters.local.GeneratorAsset

abstract class FileGenerator(protected val params: KMPConfigModel) {
    abstract fun generate(ftManager: FileTemplateManager, packageName: String): List<GeneratorAsset>
}