package org.jetbrains.plugins.template.debuger

import com.google.gson.JsonArray
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext
import org.jetbrains.plugins.template.debuger.LLDBDebugSession.Companion.log

/**
 * _ 暂停上下文
 */
class LLDBSuspendContext(
    private val process: LLDBDebugProcess,
    private val _Session: LLDBDebugSession,
    private val threadId: Int,
    private val stackFrames: JsonArray,
    private val project: com.intellij.openapi.project.Project
) : XSuspendContext() {
    
    private val executionStack = LLDBExecutionStack(_Session, threadId, stackFrames, project)
    
    init {
        log("LLDBSuspendContext.init", "=== 创建暂停上下文 ===")
        log("LLDBSuspendContext.init", "threadId=$threadId")
        log("LLDBSuspendContext.init", "stackFrames 数量=${stackFrames.size()}")
        for (i in 0 until stackFrames.size()) {
            log("LLDBSuspendContext.init", "  栈帧#$i: ${stackFrames[i]}")
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
