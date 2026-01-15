package org.jetbrains.plugins.template.debuger

import com.intellij.openapi.diagnostic.Logger
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame

/**
 * LLDB 执行栈 - 重构版
 * 参考 Flutter 的 DartVmServiceExecutionStack 设计
 * 
 * 关键改进：
 * 1. 使用预解析的栈帧列表
 * 2. 移除对 LLDBDebugSession 的依赖
 * 3. 使用 LLDBServiceWrapper 获取变量
 */
class LLDBExecutionStack(
    private val process: LLDBDebugProcess,
    private val threadId: Int,
    private val stackFrames: List<StackFrame>
) : XExecutionStack("Thread $threadId") {
    
    companion object {
        private val LOG = Logger.getInstance(LLDBExecutionStack::class.java)
    }
    
    init {
        LOG.info("创建执行栈: threadId=$threadId, 栈帧数=${stackFrames.size}")
    }
    
    override fun getTopFrame(): XStackFrame? {
        LOG.info("threadId=$threadId, stackFrames.size=${stackFrames.size}")
        if (stackFrames.isEmpty()) {
            LOG.warn("没有栈帧, 返回 null")
            return null
        }
        
        val topFrame = stackFrames[0]
        LOG.info("返回顶层栈帧: ${topFrame.name} at ${topFrame.file}:${topFrame.line}")
        return LLDBStackFrame(process, topFrame, threadId, 0)
    }
    
    override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer?) {
        LOG.info("threadId=$threadId, firstFrameIndex=$firstFrameIndex")
        val frames = mutableListOf<XStackFrame>()
        
        for (i in firstFrameIndex until stackFrames.size) {
            val frame = stackFrames[i]
            LOG.info("添加栈帧#$i: ${frame.name} at ${frame.file}:${frame.line}")
            frames.add(LLDBStackFrame(process, frame, threadId, i))
        }
        
        LOG.info("共添加 ${frames.size} 个栈帧")
        container?.addStackFrames(frames, true)
    }
}
