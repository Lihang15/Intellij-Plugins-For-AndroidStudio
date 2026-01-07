package tim

import com.intellij.psi.tree.IElementType

object TimTokenTypes {
    val KEYWORD = TimTokenType("KEYWORD")
    val BRACE = TimTokenType("BRACE")
    val IDENTIFIER = TimTokenType("IDENTIFIER")
    val ANNOTATION = TimTokenType("ANNOTATION")
    val WHITESPACE = TimTokenType("WHITESPACE")
}
