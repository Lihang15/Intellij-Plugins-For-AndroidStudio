package org.jetbrains.plugins.template.debuger

import com.google.gson.JsonObject
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValueChildrenList

/**
 * DAP 栈帧 - 显示在调用堆栈窗口
 */
class DapStackFrame(
    private val dapSession: DapDebugSession,
    private val frameJson: JsonObject,
    private val project: com.intellij.openapi.project.Project
) : XStackFrame() {
    
    private val frameId = frameJson.get("id")?.asInt ?: 0
    private val name = frameJson.get("name")?.asString ?: "unknown"
    private val line = frameJson.get("line")?.asInt ?: 0
    private val column = frameJson.get("column")?.asInt ?: 0
    
    private val sourcePath = frameJson.getAsJsonObject("source")
        ?.get("path")?.asString
    
    init {
        println("[DapStackFrame] 创建栈帧: frameId=$frameId, name=$name, line=$line, sourcePath=$sourcePath")
    }
    
    override fun getSourcePosition(): XSourcePosition? {
        println("[DapStackFrame.getSourcePosition] frameId=$frameId, sourcePath=$sourcePath, line=$line")
        
        if (sourcePath == null) {
            println("[DapStackFrame.getSourcePosition] sourcePath 为 null，返回 null")
            return null
        }
        
        // 尝试多种方式查找文件
        var file = LocalFileSystem.getInstance().findFileByPath(sourcePath)
        
        if (file == null) {
            println("[DapStackFrame.getSourcePosition] 直接路径找不到，尝试刷新文件系统")
            // 刷新文件系统缓存
            LocalFileSystem.getInstance().refreshAndFindFileByPath(sourcePath)
            file = LocalFileSystem.getInstance().findFileByPath(sourcePath)
        }
        
        if (file == null) {
            println("[DapStackFrame.getSourcePosition] ✗ 找不到文件: $sourcePath")
            println("[DapStackFrame.getSourcePosition] 尝试查找所有已打开的文件...")
            
            // 尝试通过文件名匹配
            val fileName = sourcePath.substringAfterLast('/')
            println("[DapStackFrame.getSourcePosition] 文件名: $fileName")
            
            return null
        }
        
        println("[DapStackFrame.getSourcePosition] ✓ 找到文件: ${file.path}")
        println("[DapStackFrame.getSourcePosition] 创建位置: line=${line - 1} (DAP line=$line)")
        
        val position = XDebuggerUtil.getInstance().createPosition(file, line - 1)
        println("[DapStackFrame.getSourcePosition] ✓ 位置创建成功")
        
        return position
    }
    
    override fun customizePresentation(component: ColoredTextContainer) {
        component.append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        if (sourcePath != null) {
            val fileName = sourcePath.substringAfterLast('/')
            component.append(" ($fileName:$line)", SimpleTextAttributes.GRAY_ATTRIBUTES)
        }
    }
    
    override fun computeChildren(node: XCompositeNode) {
        println("\n========== [DapStackFrame.computeChildren] 获取变量 ==========")
        println("[computeChildren] frameId=$frameId")
        
        // 获取作用域（scopes）
        dapSession.scopes(frameId) { response ->
            println("\n[computeChildren] scopes 回调执行")
            println("[computeChildren] response: $response")
            
            val body = response.getAsJsonObject("body")
            val scopes = body.getAsJsonArray("scopes")
            
            println("[computeChildren] scopes 数量: ${scopes?.size() ?: 0}")
            
            if (scopes == null || scopes.size() == 0) {
                println("[computeChildren] 没有 scopes，返回空")
                node.addChildren(XValueChildrenList.EMPTY, true)
                return@scopes
            }
            
            val children = XValueChildrenList()
            var pendingScopes = scopes.size()
            
            // 对每个 scope 获取变量
            for (i in 0 until scopes.size()) {
                val scope = scopes[i].asJsonObject
                val scopeName = scope.get("name")?.asString ?: "scope"
                val variablesReference = scope.get("variablesReference")?.asInt ?: 0
                
                println("[computeChildren] scope[$i]: name=$scopeName, variablesReference=$variablesReference")
                
                if (variablesReference > 0) {
                    // 获取变量
                    dapSession.variables(variablesReference) { varResponse ->
                        println("\n[computeChildren] variables 回调执行 for scope $scopeName")
                        println("[computeChildren] varResponse: $varResponse")
                        
                        val varBody = varResponse.getAsJsonObject("body")
                        val variables = varBody.getAsJsonArray("variables")
                        
                        println("[computeChildren] 变量数量: ${variables?.size() ?: 0}")
                        
                        if (variables != null) {
                            for (j in 0 until variables.size()) {
                                val variable = variables[j].asJsonObject
                                val varName = variable.get("name")?.asString ?: "?"
                                val varValue = variable.get("value")?.asString ?: ""
                                println("[computeChildren] 变量[$j]: name=$varName, value=$varValue")
                                val value = DapValue(dapSession, variable)
                                children.add(varName, value)
                                
                                // 在编辑器中显示变量值
                                showVariableInEditor(varName, varValue)
                            }
                        }
                        
                        pendingScopes--
                        println("[computeChildren] 剩余 pendingScopes: $pendingScopes")
                        if (pendingScopes == 0) {
                            println("[computeChildren] 所有变量获取完成，添加到 node")
                            node.addChildren(children, true)
                        }
                    }
                } else {
                    println("[computeChildren] scope $scopeName 没有变量引用")
                    pendingScopes--
                    if (pendingScopes == 0) {
                        node.addChildren(children, true)
                    }
                }
            }
        }
        
        println("========== [DapStackFrame.computeChildren] 结束 ==========\n")
    }
    
    /**
     * 在编辑器中显示变量值（内联提示）
     */
    private fun showVariableInEditor(varName: String, varValue: String) {
        println("[DapStackFrame.showVariableInEditor] 准备显示: $varName = $varValue")
        
        try {
            val sourcePos = getSourcePosition()
            if (sourcePos == null) {
                println("[showVariableInEditor] sourcePosition 为 null，无法显示")
                return
            }
            
            val file = sourcePos.file
            val lineNumber = sourcePos.line
            
            // 在 UI 线程中操作编辑器
            ApplicationManager.getApplication().invokeLater {
                try {
                    val fileEditorManager = FileEditorManager.getInstance(project)
                    val editors = fileEditorManager.getEditors(file)
                    
                    println("[showVariableInEditor] 找到 ${editors.size} 个编辑器")
                    
                    if (editors.isEmpty()) {
                        println("[showVariableInEditor] 没有打开的编辑器")
                        return@invokeLater
                    }
                    
                    // 获取文本编辑器
                    val editor = fileEditorManager.getSelectedTextEditor()
                    if (editor == null) {
                        println("[showVariableInEditor] 没有选中的文本编辑器")
                        return@invokeLater
                    }
                    
                    // 显示内联提示
                    DapInlineDebugRenderer.showVariableValue(
                        editor,
                        lineNumber,
                        varName,
                        varValue
                    )
                    
                } catch (e: Exception) {
                    println("[showVariableInEditor] ✗ 异常: ${e.message}")
                    e.printStackTrace()
                }
            }
            
        } catch (e: Exception) {
            println("[showVariableInEditor] ✗ 外部异常: ${e.message}")
            e.printStackTrace()
        }
    }
}
