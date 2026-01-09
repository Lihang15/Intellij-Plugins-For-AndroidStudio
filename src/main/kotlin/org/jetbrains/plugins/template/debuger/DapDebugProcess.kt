package org.jetbrains.plugins.template.debuger

import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.XDebuggerManager
import com.google.gson.JsonArray
import org.jetbrains.plugins.template.debuger.DapDebugSession.Companion.log
import org.jetbrains.plugins.template.debuger.DapDebugSession.Companion.logSeparator
import org.jetbrains.plugins.template.debuger.DapDebugSession.Companion.logCallStack


/**
 * LLDB 调试进程 - 连接 IntelliJ XDebugger 和 lldb
 */
class DapDebugProcess(
    session: XDebugSession,
    private val executablePath: String
) : XDebugProcess(session) {
    
    private val dapSession = DapDebugSession(executablePath)
    private val breakpointHandler = DapBreakpointHandler(this)
    
    private var currentThreadId: Int = 0
    
    init {
        logSeparator("DapDebugProcess.init", "DapDebugProcess 构造函数")
        logCallStack("DapDebugProcess.init")
        log("DapDebugProcess.init", "可执行文件路径: $executablePath")
        log("DapDebugProcess.init", "dapSession: $dapSession")
        log("DapDebugProcess.init", "breakpointHandler: $breakpointHandler")
        
        // 设置事件回调
        dapSession.onStopped = { threadId, reason ->
            logSeparator("onStopped", "onStopped 回调触发")
            logCallStack("onStopped")
            log("onStopped", "threadId=$threadId, reason=$reason")
            currentThreadId = threadId
            handleStopped(threadId, reason)
        }
        
        dapSession.onOutput = { category, output ->
            log("onOutput", "category=$category, output=$output")
            session.consoleView?.print(output, com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT)
        }
        
        dapSession.onTerminated = {
            log("onTerminated", "调试会话终止")
            session.stop()
        }
        
        log("DapDebugProcess.init", "构造函数完成")
    }
    
    override fun sessionInitialized() {
        logSeparator("sessionInitialized", "sessionInitialized 开始")
        logCallStack("sessionInitialized")
        log("sessionInitialized", "时间戳: ${System.currentTimeMillis()}")
        
        // 1. 启动 lldb
        log("sessionInitialized", "步骤1: 启动 lldb 进程")
        dapSession.start()
        
        // 2. 等待进程启动
        log("sessionInitialized", "步骤2: 等待 200ms")
        Thread.sleep(200)
        
        // 3. 发送 initialize
        log("sessionInitialized", "步骤3: 发送 initialize 请求")
        dapSession.initialize { initResponse ->
            log("sessionInitialized", "initialize 响应: $initResponse")
            
            // 4. 等待 initialize 完成
            log("sessionInitialized", "步骤4: 等待 200ms")
            Thread.sleep(200)
            
            // 5. 加载目标程序
            log("sessionInitialized", "步骤5: 发送 launch 请求")
            dapSession.launch(executablePath) { launchResponse ->
                log("sessionInitialized", "launch 响应: $launchResponse")
                
                // 6. 等待 launch 完成
                log("sessionInitialized", "步骤6: 等待 300ms")
                Thread.sleep(300)
                
                // 7. 标记 LLDB 已就绪
                log("sessionInitialized", "步骤7: 调用 breakpointHandler.onLldbReady()")
                breakpointHandler.onLldbReady()
                log("sessionInitialized", "breakpointHandler.onLldbReady() 完成")
                
                // 8. 主动同步所有断点（因为 registerBreakpoint 可能未被调用）
                log("sessionInitialized", "步骤8: 主动同步断点")
                syncBreakpoints {
                    log("sessionInitialized", "syncBreakpoints 完成")
                    
                    // 9. 等待断点同步
                    log("sessionInitialized", "步骤9: 等待 300ms")
                    Thread.sleep(300)
                    
                    // 10. 运行程序
                    log("sessionInitialized", "步骤10: 发送 configurationDone 请求")
                    dapSession.configurationDone { configResponse ->
                        log("sessionInitialized", "configurationDone 响应: $configResponse")
                        log("sessionInitialized", "调试会话初始化完成!")
                    }
                }
            }
        }
        
        log("sessionInitialized", "同步部分结束（等待异步回调）")
    }
    
    override fun startStepOver(context: XSuspendContext?) {
        logSeparator("startStepOver", "单步跳过")
        logCallStack("startStepOver")
        log("startStepOver", "currentThreadId=$currentThreadId")
        
        if (currentThreadId == 0) {
            log("startStepOver", "currentThreadId 为 0, 无法执行", "ERROR")
            return
        }
        
        try {
            dapSession.stepOver(currentThreadId) {
                log("startStepOver", "响应: $it")
            }
        } catch (e: Exception) {
            log("startStepOver", "异常: ${e.message}", "ERROR")
            e.printStackTrace()
        }
    }
    
    override fun startStepInto(context: XSuspendContext?) {
        logSeparator("startStepInto", "单步进入")
        logCallStack("startStepInto")
        log("startStepInto", "currentThreadId=$currentThreadId")
        
        if (currentThreadId == 0) {
            log("startStepInto", "currentThreadId 为 0, 无法执行", "ERROR")
            return
        }
        
        try {
            dapSession.stepIn(currentThreadId) {
                log("startStepInto", "响应: $it")
            }
        } catch (e: Exception) {
            log("startStepInto", "异常: ${e.message}", "ERROR")
            e.printStackTrace()
        }
    }
    
    override fun startStepOut(context: XSuspendContext?) {
        logSeparator("startStepOut", "单步退出")
        logCallStack("startStepOut")
        log("startStepOut", "currentThreadId=$currentThreadId")
        
        if (currentThreadId == 0) {
            log("startStepOut", "currentThreadId 为 0, 无法执行", "ERROR")
            return
        }
        
        try {
            dapSession.stepOut(currentThreadId) {
                log("startStepOut", "响应: $it")
            }
        } catch (e: Exception) {
            log("startStepOut", "异常: ${e.message}", "ERROR")
            e.printStackTrace()
        }
    }
    
    override fun resume(context: XSuspendContext?) {
        logSeparator("resume", "继续执行")
        logCallStack("resume")
        log("resume", "currentThreadId=$currentThreadId")
        
        if (currentThreadId == 0) {
            log("resume", "currentThreadId 为 0, 无法执行", "ERROR")
            return
        }
        
        try {
            dapSession.continue_(currentThreadId) { response ->
                log("resume", "响应: $response")
            }
        } catch (e: Exception) {
            log("resume", "异常: ${e.message}", "ERROR")
            e.printStackTrace()
        }
    }
    
    override fun stop() {
        logSeparator("stop", "停止调试")
        logCallStack("stop")
        dapSession.disconnect()
        log("stop", "调试已断开")
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
        logSeparator("handleStopped", "处理停止事件")
        logCallStack("handleStopped")
        log("handleStopped", "threadId=$threadId, reason=$reason")
            
        // 更新当前线程 ID
        if (threadId != 0) {
            currentThreadId = threadId
            log("handleStopped", "已更新 currentThreadId=$currentThreadId")
        } else {
            log("handleStopped", "threadId 为 0", "WARN")
        }
            
        // 获取堆栈信息
        log("handleStopped", "请求堆栈跟踪信息")
        try {
            dapSession.stackTrace(threadId) { response ->
                log("handleStopped", "stackTrace 响应长度: ${response.length}")
                log("handleStopped", "stackTrace 响应:\n$response")
                    
                try {
                    val stackFrames = parseStackTrace(response)
                    log("handleStopped", "stackFrames 数量: ${stackFrames.size()}")
                    
                    // 输出每个栈帧的详细信息
                    for (i in 0 until stackFrames.size()) {
                        val frame = stackFrames[i].asJsonObject
                        log("handleStopped", "  栈帧#$i: ${frame}")
                    }
                        
                    val suspendContext = DapSuspendContext(this, dapSession, threadId, stackFrames, session.project)
                    
                    // 检查断点命中
                    if (reason == "breakpoint" && stackFrames.size() > 0) {
                        val firstFrame = stackFrames[0].asJsonObject
                        val sourcePath = firstFrame.getAsJsonObject("source")?.get("path")?.asString
                        val lineNum = firstFrame.get("line")?.asInt
                        log("handleStopped", "断点命中位置: $sourcePath:$lineNum")
                        
                        if (sourcePath != null && lineNum != null) {
                            val hitBreakpoint = breakpointHandler.findBreakpoint(sourcePath, lineNum)
                            log("handleStopped", "查找断点结果: ${if (hitBreakpoint != null) "找到" else "未找到"}")
                        }
                    }
                        
                    if (stackFrames.size() > 0) {
                        val firstFrame = stackFrames[0].asJsonObject
                        val sourceInfo = firstFrame.getAsJsonObject("source")?.get("path")?.asString ?: "unknown"
                        val lineInfo = firstFrame.get("line")?.asInt ?: 0
                        log("handleStopped", "第一帧位置: $sourceInfo:$lineInfo")
                    } else {
                        log("handleStopped", "stackFrames 为空", "WARN")
                    }
                        
                    // UI 线程更新
                    log("handleStopped", "在 UI 线程调用 session.positionReached")
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        try {
                            log("handleStopped", "正在调用 positionReached...")
                            session.positionReached(suspendContext)
                            log("handleStopped", "positionReached 调用成功")
                            
                            if (reason == "breakpoint" && stackFrames.size() > 0) {
                                val firstFrame = stackFrames[0].asJsonObject
                                val sourcePath = firstFrame.getAsJsonObject("source")?.get("path")?.asString
                                val lineNum = firstFrame.get("line")?.asInt
                                
                                if (sourcePath != null && lineNum != null) {
                                    val hitBreakpoint = breakpointHandler.findBreakpoint(sourcePath, lineNum)
                                    if (hitBreakpoint != null) {
                                        session.setBreakpointVerified(hitBreakpoint)
                                        log("handleStopped", "断点 UI 状态已更新")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            log("handleStopped", "positionReached 异常: ${e.message}", "ERROR")
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    log("handleStopped", "stackTrace 处理异常: ${e.message}", "ERROR")
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            log("handleStopped", "stackTrace 请求异常: ${e.message}", "ERROR")
            e.printStackTrace()
        }
    }
    
    /**
     * 解析 lldb 堆栈跟踪输出为 JsonArray 格式
     */
    private fun parseStackTrace(output: String): JsonArray {
        val stackFrames = JsonArray()
        
        log("parseStackTrace", "=== 开始解析堆栈输出 ===")
        log("parseStackTrace", "输出内容:\n$output")
        
        val lines = output.split("\n")
        var frameId = 0
        
        for (i in lines.indices) {
            val line = lines[i].trim()
            
            if (line.startsWith("frame #")) {
                log("parseStackTrace", "找到 frame 行: $line")
                val frameInfo = parseFrameLine(line, frameId)
                if (frameInfo != null) {
                    stackFrames.add(frameInfo)
                    log("parseStackTrace", "添加 frame #$frameId")
                    frameId++
                }
            }
        }
        
        log("parseStackTrace", "=== 解析完成, 共 ${stackFrames.size()} 个帧 ===")
        return stackFrames
    }
    
    /**
     * 解析单个帧行
     */
    private fun parseFrameLine(line: String, frameId: Int): com.google.gson.JsonObject? {
        try {
            log("parseFrameLine", "解析行: $line")
            
            val atIndex = line.indexOf(" at ")
            if (atIndex == -1) {
                log("parseFrameLine", "没有找到 ' at ', 跳过")
                return null
            }
            
            val afterAt = line.substring(atIndex + 4).trim()
            log("parseFrameLine", "' at ' 之后: $afterAt")
            
            val colonIndex = afterAt.indexOf(":")
            if (colonIndex == -1) {
                log("parseFrameLine", "没有找到 ':', 跳过")
                return null
            }
            
            val fileName = afterAt.substring(0, colonIndex).trim()
            val rest = afterAt.substring(colonIndex + 1)
            val lineNum = rest.split(":")[0].trim().toIntOrNull()
            
            if (lineNum == null) {
                log("parseFrameLine", "无法解析行号")
                return null
            }
            
            log("parseFrameLine", "文件名: $fileName, 行号: $lineNum")
            
            val projectPath = session.project.basePath ?: ""
            val fullPath = if (fileName.startsWith("/")) fileName else "$projectPath/$fileName"
            
            val backtickIndex = line.indexOf("`")
            val funcName = if (backtickIndex != -1 && backtickIndex < atIndex) {
                line.substring(backtickIndex + 1, atIndex).trim()
            } else {
                "unknown"
            }
            
            log("parseFrameLine", "函数名: $funcName, 完整路径: $fullPath")
            
            val frameObj = com.google.gson.JsonObject()
            frameObj.addProperty("id", frameId)
            frameObj.addProperty("name", funcName)
            frameObj.addProperty("line", lineNum)
            frameObj.addProperty("column", 0)
            
            val sourceObj = com.google.gson.JsonObject()
            sourceObj.addProperty("path", fullPath)
            frameObj.add("source", sourceObj)
            
            log("parseFrameLine", "解析成功: $frameObj")
            
            return frameObj
        } catch (e: Exception) {
            log("parseFrameLine", "解析失败: ${e.message}", "ERROR")
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * 同步断点到 lldb
     */
    private fun syncBreakpoints(onComplete: () -> Unit) {
        logSeparator("syncBreakpoints", "同步断点")
        logCallStack("syncBreakpoints")
        
        val cppExtensions = setOf("cpp", "c", "cc", "cxx", "h", "hpp", "hxx", "m", "mm")
        
        val breakpointManager = XDebuggerManager.getInstance(session.project).breakpointManager
        val allBreakpoints = breakpointManager.allBreakpoints
        log("syncBreakpoints", "总断点数: ${allBreakpoints.size}")

        val breakpointsByFile = allBreakpoints
            .filterIsInstance<XLineBreakpoint<*>>()
            .filter { bp ->
                val filePath = bp.sourcePosition?.file?.path ?: return@filter false
                val ext = filePath.substringAfterLast('.').lowercase()
                val isCpp = ext in cppExtensions
                log("syncBreakpoints", "断点 $filePath, ext=$ext, isCpp=$isCpp")
                isCpp
            }
            .groupBy { it.sourcePosition?.file?.path ?: return@groupBy "" }
        
        log("syncBreakpoints", "按文件分组: ${breakpointsByFile.keys}")

        if (breakpointsByFile.isEmpty() || breakpointsByFile.all { it.key.isEmpty() }) {
            log("syncBreakpoints", "没有断点需要设置")
            onComplete()
            return
        }

        var pendingFiles = breakpointsByFile.filter { it.key.isNotEmpty() }.size
        log("syncBreakpoints", "待处理文件数: $pendingFiles")
        
        breakpointsByFile.forEach { (file, breakpoints) ->
            if (file.isEmpty()) return@forEach
            
            log("syncBreakpoints", "处理文件: $file")

            val lines = breakpoints
                .filter { it.isEnabled }
                .mapNotNull { it.sourcePosition?.line?.plus(1) }
            
            log("syncBreakpoints", "断点行号: $lines")

            if (lines.isNotEmpty()) {
                val currentBreakpoints = breakpoints.filter { it.isEnabled }
                
                dapSession.setBreakpoints(file, lines) { response ->
                    log("syncBreakpoints", "setBreakpoints 响应: $response")
                    
                    val success = !response.contains("error:") && 
                                  (response.contains("Breakpoint") || response.contains("breakpoint"))
                    
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        currentBreakpoints.forEach { bp ->
                            if (success) {
                                session.setBreakpointVerified(bp)
                                log("syncBreakpoints", "断点 ${bp.sourcePosition?.file?.path}:${bp.line + 1} 已验证")
                            } else {
                                session.setBreakpointInvalid(bp, "Failed to set breakpoint in LLDB")
                                log("syncBreakpoints", "断点 ${bp.sourcePosition?.file?.path}:${bp.line + 1} 设置失败")
                            }
                        }
                    }
                    
                    pendingFiles--
                    log("syncBreakpoints", "剩余待处理: $pendingFiles")
                    
                    if (pendingFiles == 0) {
                        log("syncBreakpoints", "所有断点设置完成")
                        onComplete()
                    }
                }
            } else {
                log("syncBreakpoints", "文件 $file 没有启用的断点")
                pendingFiles--
                if (pendingFiles == 0) {
                    log("syncBreakpoints", "所有断点设置完成")
                    onComplete()
                }
            }
        }
    }
    
    /**
     * 获取 DAP 会话
     */
    fun getDapSession(): DapDebugSession = dapSession
    
    /**
     * 获取调试会话 (IntelliJ XDebugSession)
     */
    fun getDebugSession(): XDebugSession = session
    
    /**
     * 获取断点处理器
     */
    fun getBreakpointHandler(): DapBreakpointHandler = breakpointHandler
}
