package tim.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import tim.TimLanguage
import tim.TimLexer
import tim.TimTokenTypes

/**
 * Tim 语言的 ParserDefinition
 * 这是让 IntelliJ 认识 Tim 语言文件结构的关键类
 * 配合 LSP4IntelliJ 使用，提供基本的 PSI 支持
 */
class TimParserDefinition : ParserDefinition {

    companion object {
        val FILE = IFileElementType(TimLanguage)
        val WHITESPACES = TokenSet.create(TimTokenTypes.WHITESPACE)
    }

    override fun createLexer(project: Project?): Lexer = TimLexer()

    override fun createParser(project: Project?): PsiParser = TimParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getWhitespaceTokens(): TokenSet = WHITESPACES

    override fun getCommentTokens(): TokenSet = TokenSet.EMPTY

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun createElement(node: ASTNode): PsiElement = TimPsiElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = TimPsiFile(viewProvider)
}
