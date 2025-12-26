package org.jetbrains.plugins.template.debuger

import com.google.gson.JsonObject
import com.intellij.openapi.vfs.LocalFileSystem
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
    private val frameJson: JsonObject
) : XStackFrame() {
    
    private val frameId = frameJson.get("id")?.asInt ?: 0
    private val name = frameJson.get("name")?.asString ?: "unknown"
    private val line = frameJson.get("line")?.asInt ?: 0
    private val column = frameJson.get("column")?.asInt ?: 0
    
    private val sourcePath = frameJson.getAsJsonObject("source")
        ?.get("path")?.asString
    
    override fun getSourcePosition(): XSourcePosition? {
        if (sourcePath == null) return null
        
        val file = LocalFileSystem.getInstance().findFileByPath(sourcePath) ?: return null
        return XDebuggerUtil.getInstance().createPosition(file, line - 1)
    }
    
    override fun customizePresentation(component: ColoredTextContainer) {
        component.append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        if (sourcePath != null) {
            val fileName = sourcePath.substringAfterLast('/')
            component.append(" ($fileName:$line)", SimpleTextAttributes.GRAY_ATTRIBUTES)
        }
    }
    
    override fun computeChildren(node: XCompositeNode) {
        // 获取作用域（scopes）
        dapSession.scopes(frameId) { response ->
            val body = response.getAsJsonObject("body")
            val scopes = body.getAsJsonArray("scopes")
            
            if (scopes == null || scopes.size() == 0) {
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
                
                if (variablesReference > 0) {
                    // 获取变量
                    dapSession.variables(variablesReference) { varResponse ->
                        val varBody = varResponse.getAsJsonObject("body")
                        val variables = varBody.getAsJsonArray("variables")
                        
                        if (variables != null) {
                            for (j in 0 until variables.size()) {
                                val variable = variables[j].asJsonObject
                                val varName = variable.get("name")?.asString ?: "?"
                                val value = DapValue(dapSession, variable)
                                children.add(varName, value)
                            }
                        }
                        
                        pendingScopes--
                        if (pendingScopes == 0) {
                            node.addChildren(children, true)
                        }
                    }
                } else {
                    pendingScopes--
                    if (pendingScopes == 0) {
                        node.addChildren(children, true)
                    }
                }
            }
        }
    }
}
