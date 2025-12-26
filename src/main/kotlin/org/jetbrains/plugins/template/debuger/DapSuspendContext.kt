package org.jetbrains.plugins.template.debuger

import com.google.gson.JsonArray
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext

/**
 * DAP 暂停上下文 - 当程序在断点或单步时暂停
 */
class DapSuspendContext(
    private val process: DapDebugProcess,
    private val dapSession: DapDebugSession,
    private val threadId: Int,
    private val stackFrames: JsonArray
) : XSuspendContext() {
    
    private val executionStack = DapExecutionStack(dapSession, threadId, stackFrames)
    
    override fun getActiveExecutionStack(): XExecutionStack {
        return executionStack
    }
    
    override fun getExecutionStacks(): Array<XExecutionStack> {
        return arrayOf(executionStack)
    }
}
