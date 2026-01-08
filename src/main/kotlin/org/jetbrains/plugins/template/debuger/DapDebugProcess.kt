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
        
        // 1. 启动 lldb
        println("[sessionInitialized] 步骤1: 启动 lldb 进程")
        dapSession.start()
        
        // 2. 等待进程启动
        println("[sessionInitialized] 步骤2: 等待 200ms")
        Thread.sleep(200)
        
        // 3. 发送 initialize
        println("[sessionInitialized] 步骤3: 发送 initialize 请求")
        dapSession.initialize { initResponse ->
            println("\n[sessionInitialized] initialize 响应回调执行")
            println("[sessionInitialized] initResponse: ${initResponse}")
            
            // 4. 等待 initialize 完成
            println("[sessionInitialized] 步骤4: 等待 200ms")
            Thread.sleep(200)
            
            // 5. 先加载目标程序（关键！）
            println("[sessionInitialized] 步骤5: 发送 launch 请求（加载目标程序）")
            dapSession.launch(executablePath) { launchResponse ->
                println("\n[sessionInitialized] launch 响应回调执行")
                println("[sessionInitialized] launchResponse: ${launchResponse}")
                
                // 6. 等待 launch 完成
                println("[sessionInitialized] 步骤6: 等待 300ms")
                Thread.sleep(300)
                
                // 7. 现在设置断点（目标程序已加载）
                println("[sessionInitialized] 步骤7: 开始同步断点")
                syncBreakpoints { 
                    println("\n[sessionInitialized] 步骤8: 断点设置完成，等待 200ms")
                    Thread.sleep(200)
                    
                    // 8. 配置完成，运行程序
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
        println("\n========== [DapDebugProcess.handleStopped] 函数调用 ===========")
        println("[handleStopped] threadId=$threadId, reason=$reason")
        println("[handleStopped] ✓✓✓ 收到停止事件，准备通知 UI ✓✓✓")
            
        // 更新当前线程 ID（重要！）
        if (threadId != 0) {
            currentThreadId = threadId
            println("[handleStopped] 已更新 currentThreadId=$currentThreadId")
        } else {
            println("[handleStopped] ⚠ 警告: threadId 为 0")
        }
            
        // 获取堆栈信息
        println("[handleStopped] 请求堆栈跟踪信息")
        try {
            dapSession.stackTrace(threadId) { response ->
                println("\n[handleStopped] stackTrace 回调执行")
                println("[handleStopped] stackTrace response 长度: ${response.length}")
                println("[handleStopped] stackTrace response: $response")
                    
                try {
                    // 解析 lldb 的堆栈输出，创建一个模拟的 stackFrames JsonArray
                    val stackFrames = parseStackTrace(response)
                        
                    println("[handleStopped] stackFrames 数量: ${stackFrames.size()}")
                        
                    // 即使 stackFrames 为空，也要尝试通知 UI
                    val suspendContext = DapSuspendContext(this, dapSession, threadId, stackFrames, session.project)
                        
                    if (stackFrames.size() > 0) {
                        println("[handleStopped] ✓ 有栈帧信息，创建完整的 suspend context")
                        val firstFrame = stackFrames[0].asJsonObject
                        val sourceInfo = firstFrame.getAsJsonObject("source")?.get("path")?.asString ?: "unknown"
                        val lineInfo = firstFrame.get("line")?.asInt ?: 0
                        println("[handleStopped] 第一帧位置: $sourceInfo:$lineInfo")
                    } else {
                        println("[handleStopped] ⚠ 警告: stackFrames 为空，但仍会通知 UI")
                    }
                        
                    // 在 UI 线程中更新位置
                    println("[handleStopped] 在 UI 线程中调用 session.positionReached")
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        try {
                            println("[handleStopped] → 正在调用 session.positionReached...")
                            session.positionReached(suspendContext)
                            println("[handleStopped] ✓ positionReached 调用成功，UI 应该已更新")
                        } catch (e: Exception) {
                            println("[handleStopped] ✗ positionReached 调用异常: ${e.message}")
                            e.printStackTrace()
                        }
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
     * 解析 lldb 堆栈跟踪输出为 JsonArray 格式
     */
    private fun parseStackTrace(output: String): JsonArray {
        val stackFrames = JsonArray()
        
        println("[parseStackTrace] 开始解析堆栈输出")
        println("[parseStackTrace] 输出内容:\n$output")
        
        // 解析 lldb 的堆栈输出，例如:
        // * thread #1, queue = 'com.apple.main-thread', stop reason = breakpoint 1.1
        //     frame #0: 0x000000010000058c mymaincpp`main at my_main.cpp:11:15
        //    8   	}
        //    9
        //    10  	int main() {
        // -> 11  	    std::cout << "Debug Test Program" << std::endl;
        
        val lines = output.split("\n")
        var frameId = 0
        
        for (i in lines.indices) {
            val line = lines[i].trim()
            
            // 检测 frame行：以 "frame #" 开头
            if (line.startsWith("frame #")) {
                println("[parseStackTrace] 找到 frame 行: $line")
                // 解析帧信息
                val frameInfo = parseFrameLine(line, frameId)
                if (frameInfo != null) {
                    stackFrames.add(frameInfo)
                    println("[parseStackTrace] 添加 frame #$frameId")
                    frameId++
                }
            }
        }
        
        println("[parseStackTrace] 解析完成，共 ${stackFrames.size()} 个帧")
        return stackFrames
    }
    
    /**
     * 解析单个帧行
     * 例如: frame #0: 0x000000010000058c mymaincpp`main at my_main.cpp:11:15
     */
    private fun parseFrameLine(line: String, frameId: Int): com.google.gson.JsonObject? {
        try {
            println("[parseFrameLine] 解析行: $line")
            
            // 检查是否包含 " at " （有源码位置的 frame）
            val atIndex = line.indexOf(" at ")
            if (atIndex == -1) {
                println("[parseFrameLine] 没有找到 ' at '，跳过")
                return null
            }
            
            // 提取 " at " 之后的部分："my_main.cpp:11:15"
            val afterAt = line.substring(atIndex + 4).trim()
            println("[parseFrameLine] ' at ' 之后: $afterAt")
            
            // 分割文件名和行号
            val colonIndex = afterAt.indexOf(":")
            if (colonIndex == -1) {
                println("[parseFrameLine] 没有找到 ':'，跳过")
                return null
            }
            
            val fileName = afterAt.substring(0, colonIndex).trim()
            // 提取行号（可能有列号，如 11:15）
            val rest = afterAt.substring(colonIndex + 1)
            val lineNum = rest.split(":")[0].trim().toIntOrNull()
            
            if (lineNum == null) {
                println("[parseFrameLine] 无法解析行号")
                return null
            }
            
            println("[parseFrameLine] 文件名: $fileName, 行号: $lineNum")
            
            // 尝试构建完整路径
            val projectPath = session.project.basePath ?: ""
            val fullPath = if (fileName.startsWith("/")) {
                fileName
            } else {
                "$projectPath/$fileName"
            }
            
            // 提取函数名（在 ` 和  at 之间）
            val backtickIndex = line.indexOf("`")
            val funcName = if (backtickIndex != -1 && backtickIndex < atIndex) {
                line.substring(backtickIndex + 1, atIndex).trim()
            } else {
                "unknown"
            }
            
            println("[parseFrameLine] 函数名: $funcName")
            
            val frameObj = com.google.gson.JsonObject()
            frameObj.addProperty("id", frameId)
            frameObj.addProperty("name", funcName)
            frameObj.addProperty("line", lineNum)
            frameObj.addProperty("column", 0)
            
            val sourceObj = com.google.gson.JsonObject()
            sourceObj.addProperty("path", fullPath)
            frameObj.add("source", sourceObj)
            
            println("[parseFrameLine] ✓ 解析成功: funcName=$funcName, file=$fullPath, line=$lineNum")
            
            return frameObj
        } catch (e: Exception) {
            println("[parseFrameLine] ✗ 解析帧行失败: $line, error: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * 同步断点到 lldb
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
                .mapNotNull { it.sourcePosition?.line?.plus(1) } // lldb 是 1-based
            
            println("[syncBreakpoints] 文件 $file 的断点行号: $lines")

            if (lines.isNotEmpty()) {
                dapSession.setBreakpoints(file, lines) { response ->
                    println("\n[syncBreakpoints] setBreakpoints 回调执行")
                    println("[syncBreakpoints] 文件: $file")
                    println("[syncBreakpoints] 行号: $lines")
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
