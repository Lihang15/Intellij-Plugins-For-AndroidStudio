package lsp.tim.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode

/**
 * Tim 语言的 PsiElement 实现
 * 用于表示 Tim 文件中的基本元素
 */
class TimPsiElement(node: ASTNode) : ASTWrapperPsiElement(node)
