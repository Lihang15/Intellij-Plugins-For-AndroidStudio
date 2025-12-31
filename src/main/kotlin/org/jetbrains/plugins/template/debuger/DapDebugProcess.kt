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
        println("\n========== [DapDebugProcess] 初始化开始 ==========")
        println("[DapDebugProcess] 可执行文件路径: $executablePath")
        
        // 设置事件回调
        dapSession.onStopped = { threadId, reason ->
            println("\n========== [DapDebugProcess] onStopped 回调被触发 ==========")
            println("[DapDebugProcess.onStopped] threadId=$threadId, reason=$reason")
            currentThreadId = threadId
            handleStopped(threadId, reason)
        }
        
        dapSession.onOutput = { category, output ->
            println("[DapDebugProcess.onOutput] category=$category, output=$output")
            session.consoleView?.print(output, com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT)
        }
        
        dapSession.onTerminated = {
            println("[DapDebugProcess.onTerminated] 调试会话终止")
            session.stop()
        }
        
        println("========== [DapDebugProcess] 初始化完成 ==========\n")
    }
    
    override fun sessionInitialized() {
        println("\n========== [DapDebugProcess.sessionInitialized] 函数调用 ==========")
        
        // 1. 启动 lldb-dap
        println("[sessionInitialized] 步骤1: 启动 lldb-dap 进程")
        dapSession.start()
        
        // 2. 等待进程启动
        println("[sessionInitialized] 步骤2: 等待 500ms 让进程启动")
        Thread.sleep(500)
        
        // 3. 发送 initialize
        println("[sessionInitialized] 步骤3: 发送 initialize 请求")
        dapSession.initialize { initResponse ->
            println("\n[sessionInitialized] initialize 响应回调执行")
            println("[sessionInitialized] initResponse: ${initResponse}")
            
            // 4. 等待 initialize 完成
            println("[sessionInitialized] 步骤4: 等待 200ms")
            Thread.sleep(200)
            
            // 5. 设置断点
            println("[sessionInitialized] 步骤5: 开始同步断点")
            syncBreakpoints { 
                // 6. 断点设置完成后，发送 launch
                println("\n[sessionInitialized] 步骤6: 断点设置完成，等待 200ms")
                Thread.sleep(200)
                
                println("[sessionInitialized] 步骤7: 发送 launch 请求")
                dapSession.launch(executablePath) { launchResponse ->
                    println("\n[sessionInitialized] launch 响应回调执行")
                    println("[sessionInitialized] launchResponse: ${launchResponse}")
                    
                    // 7. 等待 launch 完成
                    println("[sessionInitialized] 步骤8: 等待 300ms")
                    Thread.sleep(300)
                    
                    // 8. 配置完成
                    println("[sessionInitialized] 步骤9: 发送 configurationDone 请求")
                    dapSession.configurationDone { configResponse ->
                        println("\n[sessionInitialized] configurationDone 响应回调执行")
                        println("[sessionInitialized] configResponse: ${configResponse}")
                        println("[sessionInitialized] 调试会话初始化完成！")
                    }
                }
            }
        }
        
        println("========== [DapDebugProcess.sessionInitialized] 函数结束 ==========\n")
    }
    
    override fun startStepOver(context: XSuspendContext?) {
        println("\n========== [DapDebugProcess.startStepOver] 函数调用 ==========")
        println("[startStepOver] currentThreadId=$currentThreadId")
        
        if (currentThreadId == 0) {
            println("[startStepOver] ✗ 错误: currentThreadId 为 0，无法执行单步操作")
            return
        }
        
        try {
            dapSession.stepOver(currentThreadId) {
                println("[startStepOver] stepOver 回调执行，响应: $it")
            }
        } catch (e: Exception) {
            println("[startStepOver] ✗ 异常: ${e.message}")
            e.printStackTrace()
        }
        
        println("========== [DapDebugProcess.startStepOver] 函数结束 ==========\n")
    }
    
    override fun startStepInto(context: XSuspendContext?) {
        println("\n========== [DapDebugProcess.startStepInto] 函数调用 ==========")
        println("[startStepInto] currentThreadId=$currentThreadId")
        
        if (currentThreadId == 0) {
            println("[startStepInto] ✗ 错误: currentThreadId 为 0，无法执行单步操作")
            return
        }
        
        try {
            dapSession.stepIn(currentThreadId) {
                println("[startStepInto] stepIn 回调执行，响应: $it")
            }
        } catch (e: Exception) {
            println("[startStepInto] ✗ 异常: ${e.message}")
            e.printStackTrace()
        }
        
        println("========== [DapDebugProcess.startStepInto] 函数结束 ==========\n")
    }
    
    override fun startStepOut(context: XSuspendContext?) {
        println("\n========== [DapDebugProcess.startStepOut] 函数调用 ==========")
        println("[startStepOut] currentThreadId=$currentThreadId")
        
        if (currentThreadId == 0) {
            println("[startStepOut] ✗ 错误: currentThreadId 为 0，无法执行单步操作")
            return
        }
        
        try {
            dapSession.stepOut(currentThreadId) {
                println("[startStepOut] stepOut 回调执行，响应: $it")
            }
        } catch (e: Exception) {
            println("[startStepOut] ✗ 异常: ${e.message}")
            e.printStackTrace()
        }
        
        println("========== [DapDebugProcess.startStepOut] 函数结束 ==========\n")
    }
    
    override fun resume(context: XSuspendContext?) {
        println("\n========== [DapDebugProcess.resume] 函数调用 ==========")
        println("[resume] currentThreadId=$currentThreadId")
        
        if (currentThreadId == 0) {
            println("[resume] ✗ 错误: currentThreadId 为 0，无法执行继续操作")
            return
        }
        
        try {
            dapSession.continue_(currentThreadId) { response ->
                println("[resume] continue 回调执行，响应: $response")
                val success = response.get("success")?.asBoolean ?: false
                println("[resume] continue 成功: $success")
                
                if (!success) {
                    val errorMsg = response.get("message")?.asString ?: "未知错误"
                    println("[resume] ✗ continue 失败: $errorMsg")
                }
            }
        } catch (e: Exception) {
            println("[resume] ✗ 异常: ${e.message}")
            e.printStackTrace()
        }
        
        println("========== [DapDebugProcess.resume] 函数结束 ==========\n")
    }
    
    override fun stop() {
        println("\n========== [DapDebugProcess.stop] 函数调用 ==========")
        println("[stop] 断开调试连接")
        dapSession.disconnect()
        println("========== [DapDebugProcess.stop] 函数结束 ==========\n")
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
        println("\n========== [DapDebugProcess.handleStopped] 函数调用 ==========")
        println("[handleStopped] threadId=$threadId, reason=$reason")
        
        // 更新当前线程 ID（重要！）
        if (threadId != 0) {
            currentThreadId = threadId
            println("[handleStopped] 已更新 currentThreadId=$currentThreadId")
        }
        
        // 获取堆栈信息
        println("[handleStopped] 请求堆栈跟踪信息")
        try {
            dapSession.stackTrace(threadId) { response ->
                println("\n[handleStopped] stackTrace 回调执行")
                println("[handleStopped] stackTrace response: $response")
                
                try {
                    val body = response.getAsJsonObject("body")
                    val stackFrames = body.getAsJsonArray("stackFrames")
                    
                    println("[handleStopped] stackFrames 数量: ${stackFrames?.size() ?: 0}")
                    
                    if (stackFrames != null && stackFrames.size() > 0) {
                        println("[handleStopped] 创建 suspend context")
                        val suspendContext = DapSuspendContext(this, dapSession, threadId, stackFrames, session.project)
                        
                        // 在 UI 线程中更新位置
                        println("[handleStopped] 在 UI 线程中调用 session.positionReached")
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            try {
                                session.positionReached(suspendContext)
                                println("[handleStopped] ✓ positionReached 调用成功")
                            } catch (e: Exception) {
                                println("[handleStopped] ✗ positionReached 调用异常: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                    } else {
                        println("[handleStopped] ⚠ 警告: stackFrames 为空")
                    }
                } catch (e: Exception) {
                    println("[handleStopped] ✗ 处理 stackTrace 响应异常: ${e.message}")
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            println("[handleStopped] ✗ stackTrace 请求异常: ${e.message}")
            e.printStackTrace()
        }
        
        println("========== [DapDebugProcess.handleStopped] 函数结束 ==========\n")
    }
    
    /**
     * 同步断点到 lldb-dap
     */
    private fun syncBreakpoints(onComplete: () -> Unit) {
        println("\n========== [DapDebugProcess.syncBreakpoints] 函数调用 ==========")
        
        val breakpointManager =
            XDebuggerManager.getInstance(session.project).breakpointManager

        val allBreakpoints = breakpointManager.allBreakpoints
        println("[syncBreakpoints] 总断点数: ${allBreakpoints.size}")

        val breakpointsByFile = allBreakpoints
            .filterIsInstance<XLineBreakpoint<*>>()
            .groupBy { it.sourcePosition?.file?.path ?: return@groupBy "" }
        
        println("[syncBreakpoints] 按文件分组后的断点: ${breakpointsByFile.keys}")

        if (breakpointsByFile.isEmpty() || breakpointsByFile.all { it.key.isEmpty() }) {
            println("[syncBreakpoints] 没有断点需要设置")
            println("========== [DapDebugProcess.syncBreakpoints] 函数结束 ==========\n")
            onComplete()
            return
        }

        var pendingFiles = breakpointsByFile.filter { it.key.isNotEmpty() }.size
        println("[syncBreakpoints] 待处理文件数: $pendingFiles")
        
        breakpointsByFile.forEach { (file, breakpoints) ->
            if (file.isEmpty()) return@forEach
            
            println("\n[syncBreakpoints] 处理文件: $file")

            val lines = breakpoints
                .filter { it.isEnabled }
                .mapNotNull { it.sourcePosition?.line?.plus(1) } // DAP 是 1-based
            
            println("[syncBreakpoints] 文件 $file 的断点行号: $lines")

            if (lines.isNotEmpty()) {
                dapSession.setBreakpoints(file, lines) { response ->
                    val success = response.get("success")?.asBoolean ?: false
                    println("\n[syncBreakpoints] setBreakpoints 回调执行")
                    println("[syncBreakpoints] 文件: $file")
                    println("[syncBreakpoints] 行号: $lines")
                    println("[syncBreakpoints] 成功: $success")
                    println("[syncBreakpoints] 响应: $response")
                    
                    pendingFiles--
                    println("[syncBreakpoints] 剩余待处理文件: $pendingFiles")
                    
                    if (pendingFiles == 0) {
                        println("[syncBreakpoints] 所有断点设置完成")
                        println("========== [DapDebugProcess.syncBreakpoints] 函数结束 ==========\n")
                        onComplete()
                    }
                }
            } else {
                println("[syncBreakpoints] 文件 $file 没有启用的断点")
                pendingFiles--
                if (pendingFiles == 0) {
                    println("[syncBreakpoints] 所有断点设置完成")
                    println("========== [DapDebugProcess.syncBreakpoints] 函数结束 ==========\n")
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
