package org.jetbrains.plugins.template.debuger

import com.intellij.openapi.diagnostic.Logger
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext

/**
 * LLDB 暂停上下文 - 重构版
 * 参考 Flutter 的 DartVmServiceSuspendContext 设计
 * 
 * 关键改进：
 * 1. 使用预解析的栈帧列表（不再依赖 JsonArray）
 * 2. 移除对 LLDBDebugSession 的依赖
 * 3. 简化构造函数
 */
class LLDBSuspendContext(
    private val process: LLDBDebugProcess,
    private val threadId: Int,
    private val stackFrames: List<StackFrame>
) : XSuspendContext() {
    
    companion object {
        private val LOG = Logger.getInstance(LLDBSuspendContext::class.java)
    }
    
    private val executionStack = LLDBExecutionStack(process, threadId, stackFrames)
    
    init {
        LOG.info("=== 创建暂停上下文 ===")
        LOG.info("threadId=$threadId")
        LOG.info("stackFrames 数量=${stackFrames.size}")
        for ((index, frame) in stackFrames.withIndex()) {
            LOG.info("  栈帧#$index: ${frame.name} at ${frame.file}:${frame.line}")
        }
    }
    
    override fun getActiveExecutionStack(): XExecutionStack {
        LOG.info("返回 executionStack, threadId=$threadId")
        return executionStack
    }
    
    override fun getExecutionStacks(): Array<XExecutionStack> {
        LOG.info("返回 1 个 executionStack")
        return arrayOf(executionStack)
    }
}
