package lsp.tim

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

            TimTokenTypes.NUMBER ->
                pack(DefaultLanguageHighlighterColors.NUMBER)

            TimTokenTypes.COLON ->
                pack(DefaultLanguageHighlighterColors.OPERATION_SIGN)

            TimTokenTypes.SEMICOLON ->
                pack(DefaultLanguageHighlighterColors.SEMICOLON)

            TimTokenTypes.COMMENT ->
                pack(DefaultLanguageHighlighterColors.LINE_COMMENT)

            TimTokenTypes.IDENTIFIER ->
                pack(DefaultLanguageHighlighterColors.IDENTIFIER)

            else -> emptyArray()
        }
}

