package org.jetbrains.plugins.template.debuger

import com.google.gson.JsonObject
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.XDebuggerManager


/**
 * DAP 调试进程 - 连接 IntelliJ XDebugger 和 lldb-dap
 */
class DapDebugProcess(
    session: XDebugSession,
    private val executablePath: String
) : XDebugProcess(session) {
    
    private val dapSession = DapDebugSession(executablePath)
    private val breakpointHandler = DapBreakpointHandler(this)
    
    private var currentThreadId: Int = 0
    
    init {
        // 设置事件回调
        dapSession.onStopped = { threadId, reason ->
            currentThreadId = threadId
            handleStopped(threadId, reason)
        }
        
        dapSession.onOutput = { category, output ->
            session.consoleView?.print(output, com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT)
        }
        
        dapSession.onTerminated = {
            session.stop()
        }
    }
    
    override fun sessionInitialized() {
        // 1. 启动 lldb-dap
        dapSession.start()
        
        // 2. 等待进程启动
        Thread.sleep(500)
        
        // 3. 发送 initialize
        dapSession.initialize { initResponse ->
            println("[DapDebugProcess] Initialized")
            
            // 4. 等待 initialize 完成
            Thread.sleep(200)
            
            // 5. 设置断点
            syncBreakpoints { 
                // 6. 断点设置完成后，发送 launch
                Thread.sleep(200)
                
                dapSession.launch(executablePath) { launchResponse ->
                    println("[DapDebugProcess] Launched")
                    
                    // 7. 等待 launch 完成
                    Thread.sleep(300)
                    
                    // 8. 配置完成
                    dapSession.configurationDone { configResponse ->
                        println("[DapDebugProcess] Configuration done")
                    }
                }
            }
        }
    }
    
    override fun startStepOver(context: XSuspendContext?) {
        dapSession.stepOver(currentThreadId) {
            println("[DapDebugProcess] Step over")
        }
    }
    
    override fun startStepInto(context: XSuspendContext?) {
        dapSession.stepIn(currentThreadId) {
            println("[DapDebugProcess] Step into")
        }
    }
    
    override fun startStepOut(context: XSuspendContext?) {
        dapSession.stepOut(currentThreadId) {
            println("[DapDebugProcess] Step out")
        }
    }
    
    override fun resume(context: XSuspendContext?) {
        dapSession.continue_(currentThreadId) {
            println("[DapDebugProcess] Resume")
        }
    }
    
    override fun stop() {
        dapSession.disconnect()
    }
    
    override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>> {
        return arrayOf(breakpointHandler)
    }
    
    override fun getEditorsProvider(): XDebuggerEditorsProvider {
        return DapEditorsProvider()
    }
    
    /**
     * 处理 stopped 事件
     */
    private fun handleStopped(threadId: Int, reason: String) {
        // 获取堆栈信息
        dapSession.stackTrace(threadId) { response ->
            val body = response.getAsJsonObject("body")
            val stackFrames = body.getAsJsonArray("stackFrames")
            
            if (stackFrames != null && stackFrames.size() > 0) {
                val suspendContext = DapSuspendContext(this, dapSession, threadId, stackFrames)
                session.positionReached(suspendContext)
            }
        }
    }
    
    /**
     * 同步断点到 lldb-dap
     */
    private fun syncBreakpoints(onComplete: () -> Unit) {
        val breakpointManager =
            XDebuggerManager.getInstance(session.project).breakpointManager

        val allBreakpoints = breakpointManager.allBreakpoints

        val breakpointsByFile = allBreakpoints
            .filterIsInstance<XLineBreakpoint<*>>()
            .groupBy { it.sourcePosition?.file?.path ?: return@groupBy "" }

        if (breakpointsByFile.isEmpty() || breakpointsByFile.all { it.key.isEmpty() }) {
            println("[DapDebugProcess] No breakpoints to set")
            onComplete()
            return
        }

        var pendingFiles = breakpointsByFile.filter { it.key.isNotEmpty() }.size
        
        breakpointsByFile.forEach { (file, breakpoints) ->
            if (file.isEmpty()) return@forEach

            val lines = breakpoints
                .filter { it.isEnabled }
                .mapNotNull { it.sourcePosition?.line?.plus(1) } // DAP 是 1-based

            if (lines.isNotEmpty()) {
                dapSession.setBreakpoints(file, lines) { response ->
                    val success = response.get("success")?.asBoolean ?: false
                    println("[DapDebugProcess] Set breakpoints in $file: $lines (success=$success)")
                    
                    pendingFiles--
                    if (pendingFiles == 0) {
                        onComplete()
                    }
                }
            } else {
                pendingFiles--
                if (pendingFiles == 0) {
                    onComplete()
                }
            }
        }
    }
    
    /**
     * 获取 DAP 会话
     */
    fun getDapSession(): DapDebugSession = dapSession
}
