package org.jetbrains.plugins.template.debuger

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * LLDB 调试会话管理器
 * 负责与 lldb 进程的通信、MI命令收发、响应解析
 */
class DapDebugSession(private val executablePath: String) {
    
    private val seqCounter = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, (String) -> Unit>()
    
    private lateinit var processHandler: OSProcessHandler
    private lateinit var input: BufferedReader
    private lateinit var output: BufferedWriter
    
    // 用于存储当前调试状态
    private var targetLoaded = false
    private var isRunning = false
    private var breakpointCounter = AtomicInteger(1)
    
    // 用于缓存多行输出
    private val outputBuffer = StringBuilder()
    private var lastStopDetected = false
    
    var onStopped: ((threadId: Int, reason: String) -> Unit)? = null
    var onOutput: ((category: String, output: String) -> Unit)? = null
    var onTerminated: (() -> Unit)? = null
    
    /**
     * 启动 lldb 进程
     */
    fun start() {
        println("\n========== [DapDebugSession.start] 函数调用 ===========")
        val lldbPath = findLldbPath()
        println("[DapDebugSession.start] 使用 lldb: $lldbPath")
            
        val cmd = GeneralCommandLine(lldbPath)
        println("[DapDebugSession.start] 创建命令行: ${cmd.commandLineString}")
            
        // 创建 OSProcessHandler
        processHandler = OSProcessHandler(cmd)
        processHandler.startNotify()
        println("[DapDebugSession.start] 进程已启动")
            
        val process = processHandler.process
        input = BufferedReader(InputStreamReader(process.inputStream))
        output = BufferedWriter(OutputStreamWriter(process.outputStream))
        println("[DapDebugSession.start] 输入输出流已初始化")
            
        isRunning = true
            
        // 启动消息读取线程
        Thread {
            try {
                println("[DapDebugSession.start] 消息读取线程已启动，开始监听 LLDB 输出...")
                while (isRunning) {
                    val line = input.readLine() ?: break
                    if (line.isNotEmpty()) {
                        println("[DapDebugSession] 收到 lldb 输出: $line")
                        handleLldbOutput(line)
                    }
                }
            } catch (e: Exception) {
                println("[DapDebugSession] 读取消息异常: ${e.message}")
                e.printStackTrace()
            }
        }.start()
            
        // 等待 lldb 启动并显示第一个提示符
        Thread.sleep(300)
            
        println("[DapDebugSession.start] lldb 进程已启动，监听线程已开始")
        println("========== [DapDebugSession.start] 函数结束 ==========\n")
    }
    
    /**
     * 初始化（lldb不需要显式的initialize，直接返回成功）
     */
    fun initialize(callback: (String) -> Unit) {
        println("\n========== [DapDebugSession.initialize] 函数调用 ==========")
        println("[DapDebugSession.initialize] LLDB不需要显式初始化，直接返回成功")
        callback.invoke("initialized")
        println("========== [DapDebugSession.initialize] 函数结束 ==========\n")
    }
    
    /**
     * 加载目标程序
     */
    fun launch(program: String, args: List<String> = emptyList(), callback: (String) -> Unit) {
        println("\n========== [DapDebugSession.launch] 函数调用 ==========")
        println("[DapDebugSession.launch] program=$program, args=$args")
        
        // 发送 target create 命令
        val targetCmd = "target create \"$program\""
        sendCommand(targetCmd) { response ->
            println("[DapDebugSession.launch] target create 响应: $response")
            targetLoaded = true
            
            // 如果有参数，设置参数
            if (args.isNotEmpty()) {
                val argsStr = args.joinToString(" ") { "\"$it\"" }
                val settingsCmd = "settings set target.run-args $argsStr"
                sendCommand(settingsCmd) { argsResponse ->
                    println("[DapDebugSession.launch] 参数设置响应: $argsResponse")
                    callback.invoke("launched")
                }
            } else {
                callback.invoke("launched")
            }
        }
        
        println("========== [DapDebugSession.launch] 函数结束 ==========\n")
    }
    
    /**
     * 设置断点
     */
    fun setBreakpoints(sourceFile: String, lines: List<Int>, callback: (String) -> Unit) {
        println("\n========== [DapDebugSession.setBreakpoints] 函数调用 ===========")
        println("[DapDebugSession.setBreakpoints] 原始路径: $sourceFile")
        println("[DapDebugSession.setBreakpoints] 断点行号: $lines")
            
        if (lines.isEmpty()) {
            callback.invoke("no breakpoints")
            return
        }
            
        // 提取文件名（lldb 可能需要相对路径或文件名）
        val fileName = sourceFile.substringAfterLast('/')
        println("[DapDebugSession.setBreakpoints] 提取的文件名: $fileName")
            
        var processed = 0
        var allResponses = StringBuilder()
            
        lines.forEach { line ->
            // 优先尝试使用完整路径，如果失败则回退到文件名
            val cmd = "breakpoint set --file \"$sourceFile\" --line $line"
            println("[DapDebugSession.setBreakpoints] 发送命令: $cmd")
                
            sendCommand(cmd) { response ->
                println("[DapDebugSession.setBreakpoints] ===== 断点设置响应 START =====")
                println(response)
                println("[DapDebugSession.setBreakpoints] ===== 断点设置响应 END =====")
                    
                // 检查是否有错误或pending
                if (response.contains("error:") || response.contains("no locations")) {
                    println("[DapDebugSession.setBreakpoints] ⚠️ 警告: 完整路径失败，尝试使用文件名")
                    // 尝试使用文件名
                    val fallbackCmd = "breakpoint set --file \"$fileName\" --line $line"
                    println("[DapDebugSession.setBreakpoints] 回退命令: $fallbackCmd")
                    sendCommand(fallbackCmd) { fallbackResponse ->
                        println("[DapDebugSession.setBreakpoints] ===== 回退断点响应 START =====")
                        println(fallbackResponse)
                        println("[DapDebugSession.setBreakpoints] ===== 回退断点响应 END =====")
                        allResponses.append(fallbackResponse).append("\n")
                        processed++
                            
                        if (processed == lines.size) {
                            listAndVerifyBreakpoints(allResponses, callback)
                        }
                    }
                } else {
                    allResponses.append(response).append("\n")
                    processed++
                        
                    if (processed == lines.size) {
                        listAndVerifyBreakpoints(allResponses, callback)
                    }
                }
            }
        }
            
        println("========== [DapDebugSession.setBreakpoints] 函数结束 ==========\n")
    }
        
    /**
     * 列出并验证断点
     */
    private fun listAndVerifyBreakpoints(allResponses: StringBuilder, callback: (String) -> Unit) {
        println("[DapDebugSession.listAndVerifyBreakpoints] 所有断点已设置，列出验证...")
        sendCommand("breakpoint list") { listResponse ->
            println("[DapDebugSession.listAndVerifyBreakpoints] ===== 断点列表 START =====")
            println(listResponse)
            println("[DapDebugSession.listAndVerifyBreakpoints] ===== 断点列表 END =====")
            callback.invoke(allResponses.toString())
        }
    }
    
    /**
     * 配置完成，启动程序
     */
    fun configurationDone(callback: (String) -> Unit) {
        println("\n========== [DapDebugSession.configurationDone] 函数调用 ===========")
        println("[DapDebugSession.configurationDone] 准备运行程序...")
            
        // 发送 run 命令启动程序
        val cmd = "run"
        sendCommand(cmd) { response ->
            println("[DapDebugSession.configurationDone] ===== run 命令响应 START =====")
            println(response)
            println("[DapDebugSession.configurationDone] ===== run 命令响应 END =====")
            
            // 主动查询进程状态以确保UI同步
            println("[DapDebugSession.configurationDone] 主动查询进程状态...")
            Thread.sleep(100) // 短暂等待进程响应
            sendCommand("process status") { statusResponse ->
                println("[DapDebugSession.configurationDone] ===== 进程状态 START =====")
                println(statusResponse)
                println("[DapDebugSession.configurationDone] ===== 进程状态 END =====")
                
                // 检查是否已停止
                if (statusResponse.contains("stopped")) {
                    println("[DapDebugSession.configurationDone] ✓ 检测到进程已停止，触发停止事件处理")
                }
                
                callback.invoke("configuration done")
            }
        }
            
        println("========== [DapDebugSession.configurationDone] 函数结束 ==========\n")
    }
    
    /**
     * 继续执行
     */
    fun continue_(threadId: Int, callback: (String) -> Unit) {
        println("\n========== [DapDebugSession.continue_] 函数调用 ==========")
        println("[DapDebugSession.continue_] threadId=$threadId")
        
        val cmd = "continue"
        sendCommand(cmd) { response ->
            println("[DapDebugSession.continue_] 响应: $response")
            callback.invoke(response)
        }
        
        println("========== [DapDebugSession.continue_] 函数结束 ==========\n")
    }
    
    /**
     * 单步进入（step into）
     */
    fun stepIn(threadId: Int, callback: (String) -> Unit) {
        println("\n========== [DapDebugSession.stepIn] 函数调用 ==========")
        println("[DapDebugSession.stepIn] threadId=$threadId")
        
        val cmd = "step"
        sendCommand(cmd) { response ->
            println("[DapDebugSession.stepIn] 响应: $response")
            callback.invoke(response)
        }
        
        println("========== [DapDebugSession.stepIn] 函数结束 ==========\n")
    }
    
    /**
     * 单步跳过（step over）
     */
    fun stepOver(threadId: Int, callback: (String) -> Unit) {
        println("\n========== [DapDebugSession.stepOver] 函数调用 ==========")
        println("[DapDebugSession.stepOver] threadId=$threadId")
        
        val cmd = "next"
        sendCommand(cmd) { response ->
            println("[DapDebugSession.stepOver] 响应: $response")
            callback.invoke(response)
        }
        
        println("========== [DapDebugSession.stepOver] 函数结束 ==========\n")
    }
    
    /**
     * 单步退出（step out）
     */
    fun stepOut(threadId: Int, callback: (String) -> Unit) {
        println("\n========== [DapDebugSession.stepOut] 函数调用 ==========")
        println("[DapDebugSession.stepOut] threadId=$threadId")
        
        val cmd = "finish"
        sendCommand(cmd) { response ->
            println("[DapDebugSession.stepOut] 响应: $response")
            callback.invoke(response)
        }
        
        println("========== [DapDebugSession.stepOut] 函数结束 ==========\n")
    }
    
    /**
     * 获取线程列表
     */
    fun threads(callback: (String) -> Unit) {
        println("\n========== [DapDebugSession.threads] 函数调用 ==========")
        
        val cmd = "thread list"
        sendCommand(cmd) { response ->
            println("[DapDebugSession.threads] 响应: $response")
            callback.invoke(response)
        }
        
        println("========== [DapDebugSession.threads] 函数结束 ==========\n")
    }
    
    /**
     * 获取堆栈跟踪
     */
    fun stackTrace(threadId: Int, callback: (String) -> Unit) {
        println("\n========== [DapDebugSession.stackTrace] 函数调用 ==========")
        println("[DapDebugSession.stackTrace] threadId=$threadId")
        
        val cmd = "thread backtrace"
        sendCommand(cmd) { response ->
            println("[DapDebugSession.stackTrace] 响应: $response")
            callback.invoke(response)
        }
        
        println("========== [DapDebugSession.stackTrace] 函数结束 ==========\n")
    }
    
    /**
     * 获取作用域（scopes）
     */
    fun scopes(frameId: Int, callback: (String) -> Unit) {
        println("\n========== [DapDebugSession.scopes] 函数调用 ==========")
        println("[DapDebugSession.scopes] frameId=$frameId")
        
        val cmd = "frame variable"
        sendCommand(cmd) { response ->
            println("[DapDebugSession.scopes] 响应: $response")
            callback.invoke(response)
        }
        
        println("========== [DapDebugSession.scopes] 函数结束 ==========\n")
    }
    
    /**
     * 获取变量
     */
    fun variables(variablesReference: Int, callback: (String) -> Unit) {
        println("\n========== [DapDebugSession.variables] 函数调用 ==========")
        println("[DapDebugSession.variables] variablesReference=$variablesReference")
        
        val cmd = "frame variable"
        sendCommand(cmd) { response ->
            println("[DapDebugSession.variables] 响应: $response")
            callback.invoke(response)
        }
        
        println("========== [DapDebugSession.variables] 函数结束 ==========\n")
    }
    
    /**
     * 断开连接
     */
    fun disconnect(callback: (String) -> Unit = {}) {
        println("\n========== [DapDebugSession.disconnect] 函数调用 ==========")
        
        isRunning = false
        
        val cmd = "quit"
        try {
            output.write(cmd)
            output.newLine()
            output.flush()
            println("[DapDebugSession.disconnect] 已发送退出命令")
        } catch (e: Exception) {
            println("[DapDebugSession.disconnect] 发送退出命令异常: ${e.message}")
        }
        
        callback.invoke("disconnected")
        println("========== [DapDebugSession.disconnect] 函数结束 ==========\n")
    }
    
    /**
     * 发送命令到 lldb
     */
    private fun sendCommand(command: String, callback: (String) -> Unit) {
        val seq = seqCounter.getAndIncrement()
            
        println("\n========== [DapDebugSession.sendCommand] 发送命令到 lldb ===========")
        println("[sendCommand] 命令: $command, seq=$seq")
            
        pendingRequests[seq] = callback
        println("[sendCommand] 已注册回调函数 for seq=$seq")
            
        try {
            output.write(command)
            output.newLine()
            output.flush()
            println("[sendCommand] ✓ 命令已成功发送: $command")
        } catch (e: Exception) {
            println("[sendCommand] ✗ 发送命令失败: ${e.message}")
            e.printStackTrace()
        }
        println("========== 命令发送结束 ==========\n")
    }
    
    /**
     * 处理 lldb 输出
     */
    private fun handleLldbOutput(line: String) {
        println("[handleLldbOutput] 处理输出: $line")
        
        // 先添加到缓冲区
        outputBuffer.append(line).append("\n")
        
        // 检查是否包含命令提示符（可能在行尾或单独一行）
        val hasPrompt = line.trim() == "(lldb)" || line.trim().endsWith("(lldb)")
        
        if (hasPrompt) {
            println("[handleLldbOutput] ✓ 检测到命令提示符（行内容: '$line'）")
            
            // 立即获取缓存的完整输出（在添加新内容之前）
            val bufferedOutput = outputBuffer.toString()
            println("[handleLldbOutput] 缓冲区内容长度: ${bufferedOutput.length}")
            println("[handleLldbOutput] 缓冲区内容:\n$bufferedOutput")
            outputBuffer.clear()
            
            // 获取并执行最早的待处理回调
            val firstKey = pendingRequests.keys.minOrNull()
            if (firstKey != null) {
                val callback = pendingRequests.remove(firstKey)
                if (callback != null) {
                    println("[handleLldbOutput] 准备执行回调 for seq=$firstKey")
                    callback.invoke(bufferedOutput)
                    println("[handleLldbOutput] ✓ 已执行回调 for seq=$firstKey")
                } else {
                    println("[handleLldbOutput] ⚠ 警告: seq=$firstKey 的回调为 null")
                }
            } else {
                println("[handleLldbOutput] ⚠ 警告: 没有待处理的回调")
            }
        }
            
        // 检查是否是停止事件
        when {
            // 检测 "Process XXXX stopped" 这是停止的第一行
            line.contains("Process") && line.contains("stopped") -> {
                println("[handleLldbOutput] 检测到 Process stopped")
                lastStopDetected = true
                // 不立即触发，等待下一行的 thread 信息
            }
            // 检测 "* thread #X" 这是停止的第二行，包含 stop reason
            lastStopDetected && line.startsWith("*") && line.contains("thread #") -> {
                println("[handleLldbOutput] ✓✓✓ 检测到完整的停止事件 ✓✓✓")
                lastStopDetected = false
                
                // 解析线程ID和停止原因
                val threadId = extractThreadId(line)
                val reason = extractStopReason(line)
                println("[handleLldbOutput] threadId=$threadId, reason=$reason")
                println("[handleLldbOutput] 调用 onStopped 回调...")
                
                // 立即触发停止事件
                onStopped?.invoke(threadId, reason)
                println("[handleLldbOutput] onStopped 回调已执行")
            }
            // 检测 "Process XXXX exited"
            line.contains("Process") && line.contains("exited") -> {
                println("[handleLldbOutput] 检测到退出事件")
                onTerminated?.invoke()
            }
            // 检测 "Process XXXX resuming"
            line.contains("Process") && line.contains("resuming") -> {
                println("[handleLldbOutput] 程序恢复运行")
            }
            // 断点设置确认
            line.contains("Breakpoint") && line.contains("where =") -> {
                println("[handleLldbOutput] ✓ 断点设置确认: $line")
                onOutput?.invoke("console", line)
            }
            else -> {
                // 普通输出
                onOutput?.invoke("console", line)
            }
        }
    }
    
    /**
     * 从输出行中提取线程ID
     */
    private fun extractThreadId(line: String): Int {
        // 尝试从输出中解析线程ID，例如 "* thread #1"
        val pattern = "thread #(\\d+)".toRegex()
        val match = pattern.find(line)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }
        
    /**
     * 从输出行中提取停止原因
     */
    private fun extractStopReason(line: String): String {
        return when {
            line.contains("breakpoint") -> "breakpoint"
            line.contains("step") -> "step"
            line.contains("signal") -> "signal"
            else -> "unknown"
        }
    }
    

    
    /**
     * 查找 lldb 路径
     */
    private fun findLldbPath(): String {
        val possiblePaths = listOf(
            "/opt/homebrew/opt/llvm/bin/lldb",
            "lldb"
        )
        
        return possiblePaths.firstOrNull { path ->
            try {
                java.io.File(path).exists() || path == "lldb"
            } catch (e: Exception) {
                false
            }
        } ?: throw IllegalStateException("lldb not found. Please install LLVM or add lldb to PATH")
    }
}
