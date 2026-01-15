package org.jetbrains.plugins.template.debuger

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.Alarm
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * LLDB Service Wrapper - 集中管理所有 LLDB 通信
 * 参考 Flutter 的 VmServiceWrapper 设计
 */
class LLDBServiceWrapper(
    private val debugProcess: LLDBDebugProcess
) : Disposable {
    
    companion object {
        private val LOG = Logger.getInstance(LLDBServiceWrapper::class.java)
    }
    
    // 连接状态
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        FAILED
    }
    
    // 单步选项
    enum class StepOption {
        Over,   // next
        Into,   // step
        Out     // finish
    }
    
    // 请求调度器（使用 IntelliJ 的 Alarm，避免手动线程管理）
    private val requestScheduler: Alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    
    // 待处理的请求（seq -> callback）
    private val pendingRequests = ConcurrentHashMap<Int, (String) -> Unit>()
    private val seqCounter = AtomicInteger(1)
    
    // LLDB 进程和 IO 流
    private lateinit var processHandler: OSProcessHandler
    private lateinit var input: BufferedReader
    private lateinit var output: BufferedWriter
    
    // 状态
    @Volatile
    private var connectionState = ConnectionState.DISCONNECTED
    
    @Volatile
    private var isRunning = false
    
    @Volatile
    private var currentThreadId: Int? = null
    
    // 输出缓冲
    private val outputBuffer = StringBuilder()
    private var commandEchoReceived = false
    
    // 监听器
    var listener: LLDBListener? = null
    var onConnected: (() -> Unit)? = null
    
    /**
     * 启动 LLDB 进程
     */
    fun start() {
        LOG.info("=== 启动 LLDB 进程 ===")
        connectionState = ConnectionState.CONNECTING
        
        val lldbPath = findLldbPath()
        LOG.info("使用 LLDB 路径: $lldbPath")
        
        val cmd = GeneralCommandLine(lldbPath)
        processHandler = OSProcessHandler(cmd)
        processHandler.startNotify()
        
        val process = processHandler.process
        input = BufferedReader(InputStreamReader(process.inputStream))
        output = BufferedWriter(OutputStreamWriter(process.outputStream))
        
        isRunning = true
        
        // 启动输出读取线程
        Thread({
            LOG.info("LLDB 输出监听线程启动")
            try {
                while (isRunning) {
                    val line = input.readLine() ?: break
                    if (line.isNotEmpty()) {
                        handleLldbOutput(line)
                    }
                }
                LOG.info("LLDB 输出监听线程结束")
            } catch (e: Exception) {
                LOG.error("LLDB 输出读取异常", e)
            }
        }, "LLDB-Reader-Thread").start()
        
        // 等待 LLDB 启动
        Thread.sleep(300)
        
        connectionState = ConnectionState.CONNECTED
        LOG.info("LLDB 连接成功")
        
        // 触发连接回调
        onConnected?.invoke()
    }
    
    /**
     * 加载目标程序
     */
    fun loadTarget(path: String, callback: () -> Unit) {
        LOG.info("=== 加载目标程序: $path ===")
        
        val targetCmd = "target create \"$path\""
        sendCommand(targetCmd) { response ->
            LOG.info("target create 响应: $response")
            val success = !response.contains("error:")
            
            if (success) {
                LOG.info("目标程序加载成功")
                callback()
            } else {
                LOG.error("目标程序加载失败: $response")
                connectionState = ConnectionState.FAILED
            }
        }
    }
    
    /**
     * 运行程序
     * 关键：使用 --stop-at-entry 让程序在入口点暂停
     */
    fun run(callback: () -> Unit = {}) {
        LOG.info("=== 运行程序 ===")
        
        // 关键修复：使用 --stop-at-entry 在入口点暂停
        // 这样可以确保断点在程序真正运行前已经设置好
        sendCommand("process launch --stop-at-entry") { response ->
            LOG.info("launch 响应: $response")
            callback()
        }
    }
    
    /**
     * 设置断点
     */
    fun setBreakpoint(file: String, line: Int, callback: (Boolean) -> Unit) {
        LOG.info("=== 设置断点 ===")
        LOG.info("文件: $file")
        LOG.info("行号: $line")
        
        println("\n========== [LLDBServiceWrapper.setBreakpoint] 开始 ==========")
        println("[setBreakpoint] 文件: $file")
        println("[setBreakpoint] 行号: $line")
        
        addRequest {
            val cmd = "breakpoint set --file \"$file\" --line $line"
            LOG.info("LLDB 命令: $cmd")
            println("[setBreakpoint] LLDB 命令: $cmd")
            
            sendCommand(cmd) { response ->
                LOG.info("断点设置响应:\n$response")
                println("[setBreakpoint] 响应:")
                println("--- 开始 ---")
                println(response)
                println("--- 结束 ---")
                
                val success = !response.contains("error:") && 
                              (response.contains("Breakpoint") || response.contains("breakpoint"))
                LOG.info("断点设置${if (success) "成功" else "失败"}: $file:$line")
                println("[setBreakpoint] 结果: ${if (success) "成功" else "失败"}")
                println("========== [LLDBServiceWrapper.setBreakpoint] 结束 ==========\n")
                
                callback(success)
            }
        }
    }
    
    /**
     * 删除断点
     */
    fun removeBreakpoint(file: String, line: Int, callback: () -> Unit = {}) {
        LOG.info("删除断点: $file:$line")
        
        addRequest {
            sendCommand("breakpoint list") { listResponse ->
                val fileName = file.substringAfterLast('/')
                val bpIdPattern = "(\\d+): file = '([^']+)', line = (\\d+)".toRegex()
                var breakpointId: Int? = null
                
                for (lineStr in listResponse.split("\n")) {
                    bpIdPattern.find(lineStr)?.let { match ->
                        val id = match.groupValues[1].toIntOrNull()
                        val bpFile = match.groupValues[2]
                        val bpLine = match.groupValues[3].toIntOrNull()
                        
                        if (bpLine == line && (bpFile == file || bpFile.endsWith(fileName) || file.endsWith(bpFile))) {
                            breakpointId = id
                        }
                    }
                }
                
                if (breakpointId != null) {
                    sendCommand("breakpoint delete $breakpointId") { deleteResponse ->
                        LOG.info("断点删除响应: $deleteResponse")
                        callback()
                    }
                } else {
                    LOG.warn("未找到匹配的断点: $file:$line")
                    callback()
                }
            }
        }
    }
    
    /**
     * 恢复线程执行
     */
    fun resumeThread(threadId: Int, stepOption: StepOption?) {
        LOG.info("恢复线程执行: threadId=$threadId, stepOption=$stepOption")
        
        val command = when (stepOption) {
            StepOption.Over -> "next"
            StepOption.Into -> "step"
            StepOption.Out -> "finish"
            null -> "continue"
        }
        
        addRequest {
            sendCommand(command) { response ->
                LOG.info("$command 响应: $response")
            }
        }
    }
    
    /**
     * 暂停线程
     */
    fun pauseThread(threadId: Int) {
        LOG.info("暂停线程: threadId=$threadId")
        
        addRequest {
            sendCommand("process interrupt") { response ->
                LOG.info("pause 响应: $response")
            }
        }
    }
    
    /**
     * 获取堆栈跟踪
     */
    fun getStackTrace(threadId: Int, callback: (List<StackFrame>) -> Unit) {
        LOG.info("获取堆栈跟踪: threadId=$threadId")
        println("\n========== [LLDBServiceWrapper.getStackTrace] 开始 ==========")
        println("[getStackTrace] threadId=$threadId")
        
        addRequest {
            sendCommand("thread backtrace") { response ->
                LOG.info("堆栈跟踪响应长度: ${response.length}")
                println("[getStackTrace] 响应长度: ${response.length}")
                println("[getStackTrace] 响应内容:")
                println("--- 开始 ---")
                println(response)
                println("--- 结束 ---")
                
                val stackFrames = parseStackTrace(response)
                LOG.info("解析出 ${stackFrames.size} 个栈帧")
                println("[getStackTrace] 解析出 ${stackFrames.size} 个栈帧")
                
                for ((index, frame) in stackFrames.withIndex()) {
                    println("[getStackTrace]   栈帧 #$index: ${frame.name} at ${frame.file}:${frame.line}")
                }
                
                println("========== [LLDBServiceWrapper.getStackTrace] 结束 ==========\n")
                callback(stackFrames)
            }
        }
    }
    
    /**
     * 获取变量
     */
    fun getVariables(frameId: Int, callback: (List<Variable>) -> Unit) {
        LOG.info("获取变量: frameId=$frameId")
        
        addRequest {
            sendCommand("frame select $frameId\nframe variable") { response ->
                LOG.info("变量响应长度: ${response.length}")
                val variables = parseVariables(response)
                LOG.info("解析出 ${variables.size} 个变量")
                callback(variables)
            }
        }
    }
    
    /**
     * 执行表达式求值
     */
    fun evaluateExpression(expression: String, callback: (String) -> Unit) {
        LOG.info("执行表达式求值: $expression")
        println("\n========== [LLDBServiceWrapper.evaluateExpression] 开始 ==========")
        println("[evaluateExpression] 表达式: $expression")
        
        addRequest {
            sendCommand(expression) { response ->
                println("[evaluateExpression] 响应长度: ${response.length}")
                println("[evaluateExpression] 响应内容:")
                println("--- 开始 ---")
                println(response)
                println("--- 结束 ---")
                println("========== [LLDBServiceWrapper.evaluateExpression] 结束 ==========\n")
                callback(response)
            }
        }
    }
    
    /**
     * 添加请求到调度器
     */
    private fun addRequest(runnable: Runnable) {
        if (!requestScheduler.isDisposed) {
            requestScheduler.addRequest(runnable, 0)
        }
    }
    
    /**
     * 发送命令到 LLDB
     * 支持多行命令（用 \n 分隔），但会等待每行的完整响应
     */
    private fun sendCommand(command: String, callback: (String) -> Unit) {
        val seq = seqCounter.getAndIncrement()
        LOG.info(">>> [SEQ=$seq] 发送命令: $command")
        println("[sendCommand] SEQ=$seq, 命令:")
        println("--- 开始 ---")
        println(command)
        println("--- 结束 ---")
        
        pendingRequests[seq] = callback
        
        try {
            // 检查是否是多行命令
            if (command.contains("\n")) {
                // 多行命令：逐行发送，每行之间等待
                val lines = command.split("\n").filter { it.isNotBlank() }
                println("[sendCommand] 多行命令，行数: ${lines.size}")
                
                for ((index, line) in lines.withIndex()) {
                    println("[sendCommand]   发送行 #$index: $line")
                    output.write(line)
                    output.newLine()
                    output.flush()
                    
                    // 给 LLDB 时间处理每个命令
                    if (index < lines.size - 1) {
                        Thread.sleep(100)
                    }
                }
            } else {
                // 单行命令：直接发送
                println("[sendCommand] 单行命令: $command")
                output.write(command)
                output.newLine()
                output.flush()
            }
            println("[sendCommand] ✓ 命令已发送")
        } catch (e: Exception) {
            LOG.error("发送命令失败", e)
            println("[sendCommand] ✗ 发送失败: ${e.message}")
            pendingRequests.remove(seq)
        }
    }
    
    /**
     * 处理 LLDB 输出
     */
    private fun handleLldbOutput(line: String) {
        // 记录所有输出
        LOG.info("LLDB输出: $line")
        
        // 先检查停止事件（优先级最高）
        listener?.checkStopEvents(line)
        
        // 缓冲输出
        outputBuffer.append(line).append("\n")
        
        val trimmedLine = line.trim()
        val isPromptOnly = trimmedLine == "(lldb)"
        val startsWithPrompt = trimmedLine.startsWith("(lldb)")
        
        // 单独的提示符 - 触发回调
        if (isPromptOnly && pendingRequests.isNotEmpty()) {
            triggerCallback()
            return
        }
        
        // 命令回显
        if (startsWithPrompt && !isPromptOnly) {
            commandEchoReceived = true
            return
        }
        
        // 收到响应内容后触发回调
        if (commandEchoReceived && !startsWithPrompt && pendingRequests.isNotEmpty()) {
            triggerCallback()
        }
    }
    
    /**
     * 触发最早的待处理回调
     */
    private fun triggerCallback() {
        val bufferedOutput = outputBuffer.toString()
        outputBuffer.clear()
        commandEchoReceived = false
        
        val firstKey = pendingRequests.keys.minOrNull()
        if (firstKey != null) {
            val callback = pendingRequests.remove(firstKey)
            LOG.info("<<< [SEQ=$firstKey] 收到响应 (长度=${bufferedOutput.length})")
            
            callback?.invoke(bufferedOutput)
        }
    }
    
    /**
     * 解析堆栈跟踪
     */
    private fun parseStackTrace(output: String): List<StackFrame> {
        val stackFrames = mutableListOf<StackFrame>()
        val lines = output.split("\n")
        var frameId = 0
        
        for (line in lines) {
            // 处理带 * 的当前帧（例如：* frame #0: ...）
            val trimmedLine = line.trim().removePrefix("*").trim()
            
            if (trimmedLine.startsWith("frame #")) {
                parseFrameLine(trimmedLine, frameId)?.let { frame ->
                    stackFrames.add(frame)
                    frameId++
                }
            }
        }
        
        return stackFrames
    }
    
    /**
     * 解析单个栈帧行
     */
    private fun parseFrameLine(line: String, frameId: Int): StackFrame? {
        try {
            val atIndex = line.indexOf(" at ")
            if (atIndex == -1) return null
            
            val afterAt = line.substring(atIndex + 4).trim()
            val colonIndex = afterAt.indexOf(":")
            if (colonIndex == -1) return null
            
            val fileName = afterAt.substring(0, colonIndex).trim()
            val rest = afterAt.substring(colonIndex + 1)
            val lineNum = rest.split(":")[0].trim().toIntOrNull() ?: return null
            
            val projectPath = debugProcess.getDebugSession().project.basePath ?: ""
            val fullPath = if (fileName.startsWith("/")) fileName else "$projectPath/$fileName"
            
            val backtickIndex = line.indexOf("`")
            val funcName = if (backtickIndex != -1 && backtickIndex < atIndex) {
                line.substring(backtickIndex + 1, atIndex).trim()
            } else {
                "unknown"
            }
            
            return StackFrame(
                id = frameId,
                name = funcName,
                file = fullPath,
                line = lineNum,
                column = 0
            )
        } catch (e: Exception) {
            LOG.error("解析栈帧失败: $line", e)
            return null
        }
    }
    
    /**
     * 解析变量
     */
    private fun parseVariables(output: String): List<Variable> {
        val variables = mutableListOf<Variable>()
        val lines = output.split("\n")
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("(") && trimmed.contains(")")) {
                try {
                    val typeEnd = trimmed.indexOf(")")
                    val type = trimmed.substring(1, typeEnd)
                    val rest = trimmed.substring(typeEnd + 1).trim()
                    
                    val parts = rest.split("=", limit = 2)
                    if (parts.size == 2) {
                        val name = parts[0].trim()
                        val value = parts[1].trim()
                        
                        variables.add(Variable(
                            name = name,
                            value = value,
                            type = type
                        ))
                    }
                } catch (e: Exception) {
                    LOG.warn("解析变量失败: $line", e)
                }
            }
        }
        
        return variables
    }
    
    /**
     * 查找 LLDB 路径
     */
    private fun findLldbPath(): String {
        val possiblePaths = listOf(
            "/opt/homebrew/opt/llvm/bin/lldb",
            "/usr/local/opt/llvm/bin/lldb",
            "lldb"
        )
        
        return possiblePaths.firstOrNull { path ->
            try {
                java.io.File(path).exists() || path == "lldb"
            } catch (e: Exception) {
                false
            }
        } ?: throw IllegalStateException("LLDB not found. Please install LLDB.")
    }
    
    /**
     * 获取连接状态
     */
    fun getConnectionState(): ConnectionState = connectionState
    
    /**
     * 是否已连接
     */
    fun isConnected(): Boolean = connectionState == ConnectionState.CONNECTED
    
    /**
     * 获取当前线程 ID
     */
    fun getCurrentThreadId(): Int? = currentThreadId
    
    /**
     * 设置当前线程 ID
     */
    fun setCurrentThreadId(threadId: Int) {
        currentThreadId = threadId
    }
    
    /**
     * 释放资源
     */
    override fun dispose() {
        LOG.info("=== 释放 LLDBServiceWrapper 资源 ===")
        isRunning = false
        
        try {
            output.write("quit")
            output.newLine()
            output.flush()
        } catch (e: Exception) {
            LOG.warn("发送 quit 命令失败", e)
        }
        
        requestScheduler.dispose()
        pendingRequests.clear()
    }
}

/**
 * 堆栈帧数据类
 */
data class StackFrame(
    val id: Int,
    val name: String,
    val file: String,
    val line: Int,
    val column: Int = 0
)

/**
 * 变量数据类
 */
data class Variable(
    val name: String,
    val value: String,
    val type: String,
    val variablesReference: Int = 0
)
