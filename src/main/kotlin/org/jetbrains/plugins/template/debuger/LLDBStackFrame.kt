package org.jetbrains.plugins.template.debuger

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValueChildrenList

/**
 * LLDB 栈帧 - 重构版
 * 参考 Flutter 的 DartVmServiceStackFrame 设计
 * 
 * 关键改进：
 * 1. 使用预解析的 StackFrame 对象
 * 2. 移除对 LLDBDebugSession 的依赖
 * 3. 使用 LLDBPositionMapper 处理位置映射
 */
class LLDBStackFrame(
    private val process: LLDBDebugProcess,
    private val frame: StackFrame,
    private val threadId: Int,
    private val frameIndex: Int
) : XStackFrame() {
    
    companion object {
        private val LOG = Logger.getInstance(LLDBStackFrame::class.java)
    }
    
    private val positionMapper = LLDBPositionMapper(process.getSession().project)
    
    // 创建表达式求值器
    private val evaluator = LLDBEvaluator(process, threadId, frameIndex)
    
    init {
        LOG.info("创建栈帧: frameIndex=$frameIndex, function=${frame.name}, line=${frame.line}, file=${frame.file}")
    }
    
    override fun getSourcePosition(): XSourcePosition? {
        LOG.info("=== 获取源位置 ===")
        LOG.info("frameIndex=$frameIndex, file=${frame.file}, line=${frame.line}")
        
        if (frame.file.isEmpty()) {
            LOG.warn("文件路径为空, 返回 null")
            return null
        }
        
        // 使用 LLDBPositionMapper 解析位置
        val position = positionMapper.parseSourcePosition(frame.file, frame.line)
        
        if (position == null) {
            LOG.error("找不到文件: ${frame.file}")
            return null
        }
        
        LOG.info("位置创建成功: ${position.file.path}:${position.line}")
        return position
    }
    
    override fun customizePresentation(component: ColoredTextContainer) {
        component.append(frame.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        if (frame.file.isNotEmpty()) {
            val fileName = frame.file.substringAfterLast('/')
            component.append(" ($fileName:${frame.line})", SimpleTextAttributes.GRAY_ATTRIBUTES)
        }
    }
    
    /**
     * 获取表达式求值器
     * 返回 LLDBEvaluator 实例，支持在调试窗口中计算表达式
     */
    override fun getEvaluator(): XDebuggerEvaluator {
        LOG.info("返回求值器: frameIndex=$frameIndex, threadId=$threadId")
        return evaluator
    }
    
    override fun computeChildren(node: XCompositeNode) {
        LOG.info("=== 获取变量 ===")
        LOG.info("frameIndex=$frameIndex")
        
        val serviceWrapper = process.getServiceWrapper()
        serviceWrapper.getVariables(frameIndex) { variables ->
            LOG.info("获取到 ${variables.size} 个变量")
            
            val children = XValueChildrenList()
            for (variable in variables) {
                LOG.info("添加变量: ${variable.name} = ${variable.value} (${variable.type})")
                children.add(variable.name, LLDBValue(process, variable))
                
                // 在编辑器中显示变量值
                showVariableInEditor(variable.name, variable.value)
            }
            
            node.addChildren(children, true)
        }
    }
    
    /**
     * 在编辑器中显示变量值（内联提示）
     */
    private fun showVariableInEditor(varName: String, varValue: String) {
        LOG.info("准备显示: $varName = $varValue")
        
        try {
            val sourcePos = getSourcePosition()
            if (sourcePos == null) {
                LOG.warn("sourcePosition 为 null")
                return
            }
            
            val file = sourcePos.file
            val lineNumber = sourcePos.line
            
            ApplicationManager.getApplication().invokeLater {
                try {
                    val project = process.getSession().project
                    val fileEditorManager = FileEditorManager.getInstance(project)
                    val editors = fileEditorManager.getEditors(file)
                    
                    LOG.info("找到 ${editors.size} 个编辑器")
                    
                    if (editors.isEmpty()) {
                        LOG.warn("没有打开的编辑器")
                        return@invokeLater
                    }
                    
                    val editor = fileEditorManager.getSelectedTextEditor()
                    if (editor == null) {
                        LOG.warn("没有选中的文本编辑器")
                        return@invokeLater
                    }
                    
                    LLDBInlineDebugRenderer.showVariableValue(editor, lineNumber, varName, varValue)
                    
                } catch (e: Exception) {
                    LOG.error("异常: ${e.message}", e)
                }
            }
            
        } catch (e: Exception) {
            LOG.error("外部异常: ${e.message}", e)
        }
    }
}
