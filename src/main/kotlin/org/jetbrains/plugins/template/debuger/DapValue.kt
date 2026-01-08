package org.jetbrains.plugins.template.debuger

import com.google.gson.JsonObject
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import javax.swing.Icon

/**
 * DAP 变量值 - 显示在 Variables 窗口
 */
class DapValue(
    private val dapSession: DapDebugSession,
    private val variableJson: JsonObject
) : XNamedValue(variableJson.get("name")?.asString ?: "?") {
    
    private val value = variableJson.get("value")?.asString ?: ""
    private val type = variableJson.get("type")?.asString
    private val variablesReference = variableJson.get("variablesReference")?.asInt ?: 0
    
    override fun computePresentation(node: XValueNode, place: XValuePlace) {
        val typeText = if (type != null) "($type) " else ""
        node.setPresentation(null, typeText, value, variablesReference > 0)
    }
    
    override fun computeChildren(node: XCompositeNode) {
        if (variablesReference <= 0) {
            node.addChildren(XValueChildrenList.EMPTY, true)
            return
        }
        
        // 对于 lldb，我们不支持复杂的子变量结构
        // 如果需要，可以后续添加通过 print 命令解析
        node.addChildren(XValueChildrenList.EMPTY, true)
    }
}
