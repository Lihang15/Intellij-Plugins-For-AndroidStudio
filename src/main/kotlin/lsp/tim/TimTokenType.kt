package lsp.tim

import com.intellij.psi.tree.IElementType
import com.intellij.lang.Language

class TimTokenType(debugName: String) : IElementType(debugName, TimLanguage) {
    override fun toString(): String = "TimTokenType." + super.toString()
}
