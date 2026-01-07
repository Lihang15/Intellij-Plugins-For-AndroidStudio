package tim

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class TimSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer() = TimLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        when (tokenType) {
            TimTokenTypes.KEYWORD ->
                pack(DefaultLanguageHighlighterColors.KEYWORD)

            TimTokenTypes.ANNOTATION ->
                pack(DefaultLanguageHighlighterColors.METADATA)

            TimTokenTypes.BRACE ->
                pack(DefaultLanguageHighlighterColors.BRACES)

            else -> emptyArray()
        }
}

