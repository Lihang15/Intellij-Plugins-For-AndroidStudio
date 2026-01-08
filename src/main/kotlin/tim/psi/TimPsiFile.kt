package tim.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import tim.TimFileType
import tim.TimLanguage

/**
 * Tim 语言的 PsiFile 实现
 * 这是 IntelliJ 平台理解 Tim 文件的核心类
 * ExternalAnnotator 需要这个类才能将诊断信息显示在编辑器中
 */
class TimPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, TimLanguage) {
    
    override fun getFileType(): FileType = TimFileType
    
    override fun toString(): String = "Tim File"
}
