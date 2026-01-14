package lsp.tim.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType

/**
 * Tim 语言的简单解析器
 * 由于使用 LSP 进行语言分析，这里只需要提供最基本的解析支持
 */
class TimParser : PsiParser {
    
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()
        
        // 简单地消费所有 token，不做复杂的语法分析
        // LSP 服务器会负责真正的语法分析
        while (!builder.eof()) {
            builder.advanceLexer()
        }
        
        rootMarker.done(root)
        return builder.treeBuilt
    }
}
