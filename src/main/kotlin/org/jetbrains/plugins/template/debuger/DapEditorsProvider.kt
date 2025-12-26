package org.jetbrains.plugins.template.debuger

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider

/**
 * DAP 编辑器提供者（用于表达式求值等）
 */
class DapEditorsProvider : XDebuggerEditorsProvider() {
    
    override fun getFileType(): FileType {
        return com.intellij.openapi.fileTypes.PlainTextFileType.INSTANCE
    }
    
    override fun createDocument(
        project: Project,
        expression: String,
        sourcePosition: com.intellij.xdebugger.XSourcePosition?,
        context: com.intellij.xdebugger.evaluation.EvaluationMode
    ): com.intellij.openapi.editor.Document {
        return com.intellij.openapi.editor.EditorFactory.getInstance()
            .createDocument(expression)
    }
}
