package org.jetbrains.plugins.template.debuger

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
import java.awt.Font
import java.awt.Graphics
import java.awt.Rectangle
import org.jetbrains.plugins.template.debuger.DapDebugSession.Companion.log

/**
 * DAP 内联调试渲染器 - 在编辑器中显示变量值
 */
object DapInlineDebugRenderer {
    
    /**
     * 在编辑器指定行的末尾显示变量值
     */
    fun showVariableValue(
        editor: Editor,
        line: Int,
        variableName: String,
        variableValue: String
    ) {
        log("showVariableValue", "=== 显示内联变量 ===")
        log("showVariableValue", "line=$line, $variableName = $variableValue")
        
        try {
            val document = editor.document
            if (line < 0 || line >= document.lineCount) {
                log("showVariableValue", "行号超出范围: line=$line, lineCount=${document.lineCount}", "WARN")
                return
            }
            
            val lineEndOffset = document.getLineEndOffset(line)
            log("showVariableValue", "lineEndOffset=$lineEndOffset")
            
            // 创建文本属性（灰色、斜体）
            val textAttributes = TextAttributes().apply {
                foregroundColor = JBColor.GRAY
                fontType = Font.ITALIC
            }
            
            // 添加内联提示
            val markupModel = editor.markupModel
            val rangeHighlighter = markupModel.addRangeHighlighter(
                lineEndOffset,
                lineEndOffset,
                HighlighterLayer.LAST + 1,
                textAttributes,
                HighlighterTargetArea.EXACT_RANGE
            )
            
            // 显示文本：变量名 = 值
            val inlayText = " // $variableName = $variableValue"
            
            // 使用 Inlay Hints API
            val inlayModel = editor.inlayModel
            val inlay = inlayModel.addInlineElement(
                lineEndOffset,
                true,
                object : EditorCustomElementRenderer {
                    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
                        val fontMetrics = editor.contentComponent.getFontMetrics(
                            editor.colorsScheme.getFont(EditorFontType.PLAIN)
                        )
                        return fontMetrics.stringWidth(inlayText)
                    }
                    
                    override fun paint(
                        inlay: Inlay<*>,
                        g: Graphics,
                        targetRegion: Rectangle,
                        textAttributes: TextAttributes
                    ) {
                        g.color = JBColor.GRAY
                        g.font = editor.colorsScheme.getFont(EditorFontType.ITALIC)
                        g.drawString(inlayText, targetRegion.x, targetRegion.y + editor.ascent)
                    }
                }
            )
            
            log("showVariableValue", "内联提示已添加: $inlayText")
            
            rangeHighlighter.putUserData(INLINE_DEBUG_KEY, true)
            
        } catch (e: Exception) {
            log("showVariableValue", "添加内联提示失败: ${e.message}", "ERROR")
            e.printStackTrace()
        }
    }
    
    /**
     * 清除编辑器中的所有内联调试提示
     */
    fun clearAllInlineHints(editor: Editor) {
        log("clearAllInlineHints", "=== 清除所有内联提示 ===")
        
        try {
            val markupModel = editor.markupModel
            val allHighlighters = markupModel.allHighlighters
            var removedCount = 0
            
            for (highlighter in allHighlighters) {
                if (highlighter.getUserData(INLINE_DEBUG_KEY) == true) {
                    markupModel.removeHighlighter(highlighter)
                    removedCount++
                }
            }
            
            val inlayModel = editor.inlayModel
            val allInlays = inlayModel.getInlineElementsInRange(0, editor.document.textLength)
            var disposedCount = 0
            
            for (inlay in allInlays) {
                inlay.dispose()
                disposedCount++
            }
            
            log("clearAllInlineHints", "已清除 $removedCount 个 highlighters, $disposedCount 个 inlays")
            
        } catch (e: Exception) {
            log("clearAllInlineHints", "清除失败: ${e.message}", "ERROR")
            e.printStackTrace()
        }
    }
    
    private val INLINE_DEBUG_KEY = Key.create<Boolean>("DAP_INLINE_DEBUG")
}
