package org.jetbrains.plugins.template.debuger

import com.google.gson.JsonObject
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import javax.swing.Icon
import org.jetbrains.plugins.template.debuger.DapDebugSession.Companion.log

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
    
    init {
        log("DapValue.init", "创建变量: name=${variableJson.get("name")}, value=$value, type=$type")
    }
    
    override fun computePresentation(node: XValueNode, place: XValuePlace) {
        val typeText = if (type != null) "($type) " else ""
        log("computePresentation", "显示: $typeText$value, hasChildren=${variablesReference > 0}")
        node.setPresentation(null, typeText, value, variablesReference > 0)
    }
    
    override fun computeChildren(node: XCompositeNode) {
        log("computeChildren", "variablesReference=$variablesReference")
        if (variablesReference <= 0) {
            log("computeChildren", "无子元素")
            node.addChildren(XValueChildrenList.EMPTY, true)
            return
        }
        
        log("computeChildren", "复杂类型子变量暂不支持")
        node.addChildren(XValueChildrenList.EMPTY, true)
    }
}
