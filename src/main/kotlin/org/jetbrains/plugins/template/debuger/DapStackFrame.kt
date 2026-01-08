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
        
        // 如果还是找不到，尝试在项目中查找
        if (file == null) {
            println("[DapStackFrame.getSourcePosition] 刷新后仍找不到，尝试在项目中查找")
            val fileName = sourcePath.substringAfterLast('/')
            println("[DapStackFrame.getSourcePosition] 文件名: $fileName")
            
            // 尝试在项目根目录中查找
            val projectBasePath = project.basePath
            if (projectBasePath != null) {
                val possiblePaths = listOf(
                    "$projectBasePath/$fileName",
                    "$projectBasePath/src/$fileName",
                    "$projectBasePath/src/main/$fileName"
                )
                
                for (possiblePath in possiblePaths) {
                    println("[DapStackFrame.getSourcePosition] 尝试路径: $possiblePath")
                    file = LocalFileSystem.getInstance().findFileByPath(possiblePath)
                    if (file != null) {
                        println("[DapStackFrame.getSourcePosition] ✓ 找到文件: ${file.path}")
                        break
                    }
                }
            }
        }
        
        if (file == null) {
            println("[DapStackFrame.getSourcePosition] ✗ 找不到文件: $sourcePath")
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
        
        // 直接获取当前帧的变量（lldb 不需要先获取 scopes）
        dapSession.scopes(frameId) { response ->
            println("\n[computeChildren] scopes(变量) 回调执行")
            println("[computeChildren] response: $response")
            
            // 解析 lldb 的变量输出
            val children = parseVariablesOutput(response)
            
            println("[computeChildren] 解析到变量数量: ${children.size()}")
            
            node.addChildren(children, true)
        }
        
        println("========== [DapStackFrame.computeChildren] 结束 ==========\n")
    }
    
    /**
     * 解析 lldb 变量输出
     */
    private fun parseVariablesOutput(output: String): XValueChildrenList {
        val children = XValueChildrenList()
        
        // 解析 lldb frame variable 的输出，例如:
        // (int) x = 10
        // (double) y = 3.14
        
        val lines = output.split("\n")
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("frame #") || trimmed.startsWith("(lldb)")) {
                continue
            }
            
            // 解析变量行，例如: (int) x = 10
            val parts = trimmed.split("=")
            if (parts.size >= 2) {
                val leftPart = parts[0].trim()
                val value = parts.drop(1).joinToString("=").trim()
                
                // 提取变量名和类型
                val nameMatch = "\\)\\s+(\\w+)$".toRegex().find(leftPart)
                if (nameMatch != null) {
                    val varName = nameMatch.groupValues[1]
                    
                    // 创建一个模拟的 JSON 对象
                    val varJson = com.google.gson.JsonObject()
                    varJson.addProperty("name", varName)
                    varJson.addProperty("value", value)
                    varJson.addProperty("type", leftPart.substringBefore(")").substringAfter("("))
                    varJson.addProperty("variablesReference", 0)
                    
                    val dapValue = DapValue(dapSession, varJson)
                    children.add(varName, dapValue)
                    
                    // 在编辑器中显示变量值
                    showVariableInEditor(varName, value)
                }
            }
        }
        
        return children
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
