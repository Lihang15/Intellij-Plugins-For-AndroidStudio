package org.jetbrains.plugins.template.debuger

import com.google.gson.JsonArray
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import org.jetbrains.plugins.template.debuger.DapDebugSession.Companion.log

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
        log("DapExecutionStack.init", "创建执行栈: threadId=$threadId, 栈帧数=${stackFrames.size()}")
    }
    
    override fun getTopFrame(): XStackFrame? {
        log("getTopFrame", "threadId=$threadId, stackFrames.size=${stackFrames.size()}")
        if (stackFrames.size() == 0) {
            log("getTopFrame", "没有栈帧, 返回 null", "WARN")
            return null
        }
        
        val topFrame = stackFrames[0].asJsonObject
        log("getTopFrame", "返回顶层栈帧: $topFrame")
        return DapStackFrame(dapSession, topFrame, project)
    }
    
    override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer?) {
        log("computeStackFrames", "threadId=$threadId, firstFrameIndex=$firstFrameIndex")
        val frames = mutableListOf<XStackFrame>()
        
        for (i in firstFrameIndex until stackFrames.size()) {
            val frameJson = stackFrames[i].asJsonObject
            log("computeStackFrames", "添加栈帧#$i: $frameJson")
            frames.add(DapStackFrame(dapSession, frameJson, project))
        }
        
        log("computeStackFrames", "共添加 ${frames.size} 个栈帧")
        container?.addStackFrames(frames, true)
    }
}
