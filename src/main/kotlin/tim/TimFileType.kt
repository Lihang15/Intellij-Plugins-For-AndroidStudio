package tim

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object TimFileType : LanguageFileType(TimLanguage) {

    override fun getName(): String = "Tim File"

    override fun getDescription(): String =
        "TIM language file"

    override fun getDefaultExtension(): String = "tim"

    override fun getIcon() = TimIcons.FILE
}

