package org.jetbrains.plugins.template.debuger

import com.google.gson.JsonArray
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame

/**
 * DAP 执行栈
 */
class DapExecutionStack(
    private val dapSession: DapDebugSession,
    private val threadId: Int,
    private val stackFrames: JsonArray,
    private val project: com.intellij.openapi.project.Project
) : XExecutionStack("Thread $threadId") {
    
    init {
        println("[DapExecutionStack] 创建执行栈: threadId=$threadId, 栈帧数=${stackFrames.size()}")
    }
    
    override fun getTopFrame(): XStackFrame? {
        println("[DapExecutionStack.getTopFrame] threadId=$threadId")
        if (stackFrames.size() == 0) {
            println("[DapExecutionStack.getTopFrame] 没有栈帧")
            return null
        }
        
        val topFrame = stackFrames[0].asJsonObject
        println("[DapExecutionStack.getTopFrame] 返回顶层栈帧")
        return DapStackFrame(dapSession, topFrame, project)
    }
    
    override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer?) {
        println("[DapExecutionStack.computeStackFrames] threadId=$threadId, firstFrameIndex=$firstFrameIndex")
        val frames = mutableListOf<XStackFrame>()
        
        for (i in firstFrameIndex until stackFrames.size()) {
            val frameJson = stackFrames[i].asJsonObject
            frames.add(DapStackFrame(dapSession, frameJson, project))
        }
        
        println("[DapExecutionStack.computeStackFrames] 添加 ${frames.size} 个栈帧")
        container?.addStackFrames(frames, true)
    }
}
