package org.jetbrains.plugins.template.cpp

import com.intellij.xdebugger.breakpoints.XBreakpointProperties

/**
 * C++ 断点属性
 * 目前为空，可扩展添加条件断点、命中次数等属性
 */
class CppBreakpointProperties : XBreakpointProperties<CppBreakpointProperties>() {
    
    override fun getState(): CppBreakpointProperties? = this
    
    override fun loadState(state: CppBreakpointProperties) {
        // 目前无需加载状态
    }
}
