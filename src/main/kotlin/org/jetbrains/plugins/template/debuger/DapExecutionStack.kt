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
    private val stackFrames: JsonArray
) : XExecutionStack("Thread $threadId") {
    
    override fun getTopFrame(): XStackFrame? {
        if (stackFrames.size() == 0) return null
        
        val topFrame = stackFrames[0].asJsonObject
        return DapStackFrame(dapSession, topFrame)
    }
    
    override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer?) {
        val frames = mutableListOf<XStackFrame>()
        
        for (i in firstFrameIndex until stackFrames.size()) {
            val frameJson = stackFrames[i].asJsonObject
            frames.add(DapStackFrame(dapSession, frameJson))
        }
        
        container?.addStackFrames(frames, true)
    }
}
