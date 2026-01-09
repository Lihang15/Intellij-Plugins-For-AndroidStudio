package org.jetbrains.plugins.template.debuger

import com.google.gson.JsonArray
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext
import org.jetbrains.plugins.template.debuger.DapDebugSession.Companion.log

/**
 * DAP 暂停上下文
 */
class DapSuspendContext(
    private val process: DapDebugProcess,
    private val dapSession: DapDebugSession,
    private val threadId: Int,
    private val stackFrames: JsonArray,
    private val project: com.intellij.openapi.project.Project
) : XSuspendContext() {
    
    private val executionStack = DapExecutionStack(dapSession, threadId, stackFrames, project)
    
    init {
        log("DapSuspendContext.init", "=== 创建暂停上下文 ===")
        log("DapSuspendContext.init", "threadId=$threadId")
        log("DapSuspendContext.init", "stackFrames 数量=${stackFrames.size()}")
        for (i in 0 until stackFrames.size()) {
            log("DapSuspendContext.init", "  栈帧#$i: ${stackFrames[i]}")
        }
    }
    
    override fun getActiveExecutionStack(): XExecutionStack? {
        log("getActiveExecutionStack", "返回 executionStack, threadId=$threadId")
        return executionStack
    }
    
    override fun getExecutionStacks(): Array<XExecutionStack> {
        log("getExecutionStacks", "返回 1 个 executionStack")
        return arrayOf(executionStack)
    }
}
