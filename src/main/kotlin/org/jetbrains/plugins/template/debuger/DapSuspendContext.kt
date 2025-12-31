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
    private val stackFrames: JsonArray,
    private val project: com.intellij.openapi.project.Project
) : XSuspendContext() {
    
    private val executionStack = DapExecutionStack(dapSession, threadId, stackFrames, project)
    
    init {
        println("\n========== [DapSuspendContext] 创建 ==========")
        println("[DapSuspendContext] threadId=$threadId")
        println("[DapSuspendContext] stackFrames 数量=${stackFrames.size()}")
        println("========== [DapSuspendContext] 初始化完成 ==========\n")
    }
    
    override fun getActiveExecutionStack(): XExecutionStack? {
        println("[DapSuspendContext.getActiveExecutionStack] 返回 executionStack for threadId=$threadId")
        return executionStack
    }
    
    override fun getExecutionStacks(): Array<XExecutionStack> {
        println("[DapSuspendContext.getExecutionStacks] 返回 1 个 executionStack")
        return arrayOf(executionStack)
    }
}
