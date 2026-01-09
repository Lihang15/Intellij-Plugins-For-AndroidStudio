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
import org.jetbrains.plugins.template.debuger.DapDebugSession.Companion.log
import org.jetbrains.plugins.template.debuger.DapDebugSession.Companion.logSeparator

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
        log("DapStackFrame.init", "创建栈帧: frameId=$frameId, name=$name, line=$line, sourcePath=$sourcePath")
        log("DapStackFrame.init", "完整 JSON: $frameJson")
    }
    
    override fun getSourcePosition(): XSourcePosition? {
        log("getSourcePosition", "=== 获取源位置 ===")
        log("getSourcePosition", "frameId=$frameId, sourcePath=$sourcePath, line=$line")
        
        if (sourcePath == null) {
            log("getSourcePosition", "sourcePath 为 null, 返回 null", "WARN")
            return null
        }
        
        var file = LocalFileSystem.getInstance().findFileByPath(sourcePath)
        log("getSourcePosition", "直接路径查找结果: ${file?.path ?: "null"}")
        
        if (file == null) {
            log("getSourcePosition", "尝试刷新文件系统")
            LocalFileSystem.getInstance().refreshAndFindFileByPath(sourcePath)
            file = LocalFileSystem.getInstance().findFileByPath(sourcePath)
        }
        
        if (file == null) {
            log("getSourcePosition", "尝试在项目中查找")
            val fileName = sourcePath.substringAfterLast('/')
            val projectBasePath = project.basePath
            
            if (projectBasePath != null) {
                val possiblePaths = listOf(
                    "$projectBasePath/$fileName",
                    "$projectBasePath/src/$fileName",
                    "$projectBasePath/src/main/$fileName"
                )
                
                for (possiblePath in possiblePaths) {
                    log("getSourcePosition", "尝试路径: $possiblePath")
                    file = LocalFileSystem.getInstance().findFileByPath(possiblePath)
                    if (file != null) {
                        log("getSourcePosition", "找到文件: ${file.path}")
                        break
                    }
                }
            }
        }
        
        if (file == null) {
            log("getSourcePosition", "找不到文件: $sourcePath", "ERROR")
            return null
        }
        
        log("getSourcePosition", "找到文件: ${file.path}")
        log("getSourcePosition", "创建位置: line=${line - 1} (DAP line=$line)")
        
        val position = XDebuggerUtil.getInstance().createPosition(file, line - 1)
        log("getSourcePosition", "位置创建成功")
        
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
        logSeparator("computeChildren", "获取变量")
        log("computeChildren", "frameId=$frameId")
        
        dapSession.scopes(frameId) { response ->
            log("computeChildren", "scopes 响应:\n$response")
            
            val children = parseVariablesOutput(response)
            log("computeChildren", "解析到变量数量: ${children.size()}")
            
            node.addChildren(children, true)
        }
    }
    
    /**
     * 解析 lldb 变量输出
     * 
     *  关键修复：不再使用 split("=") 人肉解析
     * 
     * LLDB 输出示例：
     * (int) a = 10
     * (std::vector<int>) vec = size=3 { [0]=1, [1]=2, [2]=3 }
     * (std::string) str = "hello = world"
     * 
     * 新解析策略：
     * 1. 找到第一个 ')' 之后的第一个 '=' 作为分隔符
     * 2. 保留原始格式，不进行 trim
     * 3. 对复杂类型（vector/map/string）直接显示 LLDB 原始输出
     */
    private fun parseVariablesOutput(output: String): XValueChildrenList {
        log("parseVariablesOutput", "=== 解析变量输出 ===")
        log("parseVariablesOutput", "原始输出:\n$output")
        
        val children = XValueChildrenList()
        val lines = output.split("\n")
        var varCount = 0
        
        for (line in lines) {
            val trimmed = line.trim()
            
            // 跳过空行和提示符
            if (trimmed.isEmpty() || 
                trimmed.startsWith("frame #") || 
                trimmed.startsWith("(lldb)") ||
                trimmed.startsWith("Process") ||
                trimmed.startsWith("thread #") ||
                trimmed.startsWith("*")) {
                continue
            }
            
            // 关键：正确解析变量格式: (type) name = value
            val varPattern = """^\(([^)]+)\)\s+(\w+)\s*=\s*(.+)$""".toRegex()
            val match = varPattern.find(trimmed)
            
            if (match != null) {
                val varType = match.groupValues[1]
                val varName = match.groupValues[2]
                val varValue = match.groupValues[3]  // 保留原始值，包括所有 '='
                
                log("parseVariablesOutput", "解析成功: name=$varName, type=$varType, value=$varValue")
                
                val varJson = com.google.gson.JsonObject()
                varJson.addProperty("name", varName)
                varJson.addProperty("value", varValue)
                varJson.addProperty("type", varType)
                varJson.addProperty("variablesReference", 0)
                
                val dapValue = DapValue(dapSession, varJson)
                children.add(varName, dapValue)
                varCount++
                
                // 在编辑器中显示变量值
                showVariableInEditor(varName, varValue)
            } else {
                log("parseVariablesOutput", "无法解析行: $trimmed", "WARN")
            }
        }
        
        log("parseVariablesOutput", "=== 共解析 $varCount 个变量 ===")
        return children
    }
    
    /**
     * 在编辑器中显示变量值（内联提示）
     */
    private fun showVariableInEditor(varName: String, varValue: String) {
        log("showVariableInEditor", "准备显示: $varName = $varValue")
        
        try {
            val sourcePos = getSourcePosition()
            if (sourcePos == null) {
                log("showVariableInEditor", "sourcePosition 为 null", "WARN")
                return
            }
            
            val file = sourcePos.file
            val lineNumber = sourcePos.line
            
            ApplicationManager.getApplication().invokeLater {
                try {
                    val fileEditorManager = FileEditorManager.getInstance(project)
                    val editors = fileEditorManager.getEditors(file)
                    
                    log("showVariableInEditor", "找到 ${editors.size} 个编辑器")
                    
                    if (editors.isEmpty()) {
                        log("showVariableInEditor", "没有打开的编辑器", "WARN")
                        return@invokeLater
                    }
                    
                    val editor = fileEditorManager.getSelectedTextEditor()
                    if (editor == null) {
                        log("showVariableInEditor", "没有选中的文本编辑器", "WARN")
                        return@invokeLater
                    }
                    
                    DapInlineDebugRenderer.showVariableValue(editor, lineNumber, varName, varValue)
                    
                } catch (e: Exception) {
                    log("showVariableInEditor", "异常: ${e.message}", "ERROR")
                    e.printStackTrace()
                }
            }
            
        } catch (e: Exception) {
            log("showVariableInEditor", "外部异常: ${e.message}", "ERROR")
            e.printStackTrace()
        }
    }
}
