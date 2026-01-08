package tim

import com.intellij.psi.tree.IElementType

object TimTokenTypes {
    val KEYWORD = TimTokenType("KEYWORD")
    val BRACE = TimTokenType("BRACE")
    val IDENTIFIER = TimTokenType("IDENTIFIER")
    val ANNOTATION = TimTokenType("ANNOTATION")
    val WHITESPACE = TimTokenType("WHITESPACE")
    val NUMBER = TimTokenType("NUMBER")
    val COLON = TimTokenType("COLON")
    val SEMICOLON = TimTokenType("SEMICOLON")
    val COMMENT = TimTokenType("COMMENT")
    val STRING = TimTokenType("STRING")
}
