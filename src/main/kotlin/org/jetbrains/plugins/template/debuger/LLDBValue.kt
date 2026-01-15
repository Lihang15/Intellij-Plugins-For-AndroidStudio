package org.jetbrains.plugins.template.debuger

import com.intellij.openapi.diagnostic.Logger
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace

/**
 * LLDB 变量值 - 重构版
 * 参考 Flutter 的 DartVmServiceValue 设计
 * 
 * 关键改进：
 * 1. 使用预解析的 Variable 对象
 * 2. 移除对 LLDBDebugSession 的依赖
 */
class LLDBValue(
    private val process: LLDBDebugProcess,
    private val variable: Variable
) : XNamedValue(variable.name) {
    
    companion object {
        private val LOG = Logger.getInstance(LLDBValue::class.java)
    }
    
    init {
        LOG.info("创建变量: name=${variable.name}, value=${variable.value}, type=${variable.type}")
    }
    
    override fun computePresentation(node: XValueNode, place: XValuePlace) {
        val typeText = if (variable.type.isNotEmpty()) "(${variable.type}) " else ""
        LOG.info("显示: $typeText${variable.value}")
        node.setPresentation(null, typeText, variable.value, false)
    }
    
    override fun computeChildren(node: XCompositeNode) {
        LOG.info("复杂类型子变量暂不支持")
        node.addChildren(XValueChildrenList.EMPTY, true)
    }
}
