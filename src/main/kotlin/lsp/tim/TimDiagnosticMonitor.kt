package lsp.tim

import com.intellij.openapi.diagnostic.Logger
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.wso2.lsp4intellij.IntellijLanguageClient
import lsp.tim.psi.TimPsiFile

/**
 * Tim 语言的诊断信息监控器
 * 用于调试 LSP 诊断信息是否正确映射到 UI
 */
class TimDiagnosticMonitor : ExternalAnnotator<PsiFile, List<String>>() {

    companion object {
        private val LOG = Logger.getInstance(TimDiagnosticMonitor::class.java)
    }

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): PsiFile? {
        if (file !is TimPsiFile) {
            LOG.info("[TimDiagnostic] File is not TimPsiFile: ${file.javaClass.name}")
            return null
        }
        
        LOG.info("[TimDiagnostic] Collecting diagnostic info for: ${file.name}")
        LOG.info("[TimDiagnostic] File path: ${file.virtualFile?.path}")
        LOG.info("[TimDiagnostic] Has errors: $hasErrors")
        
        return file
    }

    override fun doAnnotate(collectedInfo: PsiFile?): List<String> {
        if (collectedInfo == null) {
            LOG.info("[TimDiagnostic] No collected info, skipping annotation")
            return emptyList()
        }

        LOG.info("[TimDiagnostic] Processing diagnostics for: ${collectedInfo.name}")
        
        // 这里只是监控，实际的诊断由 LSPAnnotator 处理
        return listOf("Diagnostics processed")
    }

    override fun apply(file: PsiFile, annotationResult: List<String>, holder: AnnotationHolder) {
        LOG.info("[TimDiagnostic] Applying diagnostics to file: ${file.name}")
        LOG.info("[TimDiagnostic] Annotation results: $annotationResult")
        LOG.info("[TimDiagnostic] If you see this message, it means:")
        LOG.info("[TimDiagnostic]   1. ParserDefinition is working correctly")
        LOG.info("[TimDiagnostic]   2. ExternalAnnotator pipeline is active")
        LOG.info("[TimDiagnostic]   3. LSPAnnotator should have run before this")
    }
}
