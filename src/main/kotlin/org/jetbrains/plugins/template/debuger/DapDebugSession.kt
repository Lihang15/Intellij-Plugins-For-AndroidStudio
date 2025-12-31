package org.jetbrains.plugins.template.debuger

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import java.io.BufferedInputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * DAP 调试会话管理器
 * 负责与 lldb-dap 进程的通信、消息收发、请求/响应匹配
 */
class DapDebugSession(private val executablePath: String) {
    
    private val gson = Gson()
    private val seqCounter = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, (JsonObject) -> Unit>()
    
    private lateinit var processHandler: OSProcessHandler
    private lateinit var input: BufferedInputStream
    private lateinit var output: OutputStream
    
    var onStopped: ((threadId: Int, reason: String) -> Unit)? = null
    var onOutput: ((category: String, output: String) -> Unit)? = null
    var onTerminated: (() -> Unit)? = null
    
    private var isRunning = false
    
    /**
     * 启动 lldb-dap 进程
     */
    fun start() {
        println("\n========== [DapDebugSession.start] 函数调用 ==========")
        val lldbDapPath = findLldbDapPath()
        println("[DapDebugSession.start] 使用 lldb-dap: $lldbDapPath")
        
        val cmd = GeneralCommandLine(lldbDapPath)
        println("[DapDebugSession.start] 创建命令行: ${cmd.commandLineString}")
        
        processHandler = OSProcessHandler(cmd)
        processHandler.startNotify()
        println("[DapDebugSession.start] 进程已启动")
        
        val process = processHandler.process
        input = BufferedInputStream(process.inputStream)
        output = process.outputStream
        println("[DapDebugSession.start] 输入输出流已初始化")
        
        isRunning = true
        
        // 监听 STDERR（根据记忆，lldb-dap 可能将响应发送到 stderr）
        Thread {
            try {
                println("[DapDebugSession.start] STDERR 监听线程已启动")
                val errorReader = process.errorStream.bufferedReader()
                while (isRunning) {
                    val line = errorReader.readLine() ?: break
                    println("[DapDebugSession.STDERR] $line")
                }
            } catch (e: Exception) {
                println("[DapDebugSession.STDERR] 读取异常: ${e.message}")
            }
        }.start()
        
        // 启动消息读取线程
        Thread {
            try {
                println("[DapDebugSession.start] 消息读取线程已启动，开始监听 DAP 消息...")
                while (isRunning) {
                    val message = readDapMessage()
                    println("\n========== [DapDebugSession] 收到 lldb-dap 消息 ==========")
                    println("[DapDebugSession] 消息内容: $message")
                    println("========== 消息结束 ==========\n")
                    handleMessage(message)
                }
            } catch (e: Exception) {
                println("[DapDebugSession] 读取消息异常: ${e.message}")
                e.printStackTrace()
            }
        }.start()
        
        println("[DapDebugSession.start] lldb-dap 进程已启动，监听线程已开始")
        println("========== [DapDebugSession.start] 函数结束 ==========\n")
    }
    
    /**
     * 发送 initialize 请求
     */
    fun initialize(callback: (JsonObject) -> Unit) {
        println("\n========== [DapDebugSession.initialize] 函数调用 ==========")
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "initialize",
            "arguments" to mapOf(
                "adapterID" to "lldb",
                "pathFormat" to "path",
                "linesStartAt1" to true,
                "columnsStartAt1" to true,
                "supportsVariableType" to true,
                "supportsVariablePaging" to true,
                "supportsRunInTerminalRequest" to true
            )
        )
        println("[DapDebugSession.initialize] 准备发送 initialize 请求")
        sendRequest(request, callback)
        println("========== [DapDebugSession.initialize] 函数结束 ==========\n")
    }
    
    /**
     * 发送 launch 请求（启动目标程序）
     */
    fun launch(program: String, args: List<String> = emptyList(), callback: (JsonObject) -> Unit) {
        println("\n========== [DapDebugSession.launch] 函数调用 ==========")
        println("[DapDebugSession.launch] program=$program, args=$args")
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "launch",
            "arguments" to mapOf(
                "program" to program,
                "args" to args,
                "cwd" to java.io.File(program).parent,
                "stopOnEntry" to false,
                "MIMode" to "lldb"
            )
        )
        println("[DapDebugSession.launch] 准备发送 launch 请求")
        sendRequest(request, callback)
        println("========== [DapDebugSession.launch] 函数结束 ==========\n")
    }
    
    /**
     * 设置断点
     */
    fun setBreakpoints(sourceFile: String, lines: List<Int>, callback: (JsonObject) -> Unit) {
        println("\n========== [DapDebugSession.setBreakpoints] 函数调用 ==========")
        println("[DapDebugSession.setBreakpoints] sourceFile=$sourceFile, lines=$lines")
        val breakpoints = lines.map { mapOf("line" to it) }
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "setBreakpoints",
            "arguments" to mapOf(
                "source" to mapOf("path" to sourceFile),
                "breakpoints" to breakpoints
            )
        )
        println("[DapDebugSession.setBreakpoints] 准备发送 setBreakpoints 请求")
        sendRequest(request, callback)
        println("========== [DapDebugSession.setBreakpoints] 函数结束 ==========\n")
    }
    
    /**
     * 配置完成
     */
    fun configurationDone(callback: (JsonObject) -> Unit) {
        println("\n========== [DapDebugSession.configurationDone] 函数调用 ==========")
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "configurationDone"
        )
        println("[DapDebugSession.configurationDone] 准备发送 configurationDone 请求")
        sendRequest(request, callback)
        println("========== [DapDebugSession.configurationDone] 函数结束 ==========\n")
    }
    
    /**
     * 继续执行
     */
    fun continue_(threadId: Int, callback: (JsonObject) -> Unit) {
        println("\n========== [DapDebugSession.continue_] 函数调用 ==========")
        println("[DapDebugSession.continue_] threadId=$threadId")
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "continue",
            "arguments" to mapOf("threadId" to threadId)
        )
        println("[DapDebugSession.continue_] 准备发送 continue 请求")
        sendRequest(request, callback)
        println("========== [DapDebugSession.continue_] 函数结束 ==========\n")
    }
    
    /**
     * 单步进入（step into）
     */
    fun stepIn(threadId: Int, callback: (JsonObject) -> Unit) {
        println("\n========== [DapDebugSession.stepIn] 函数调用 ==========")
        println("[DapDebugSession.stepIn] threadId=$threadId")
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "stepIn",
            "arguments" to mapOf("threadId" to threadId)
        )
        println("[DapDebugSession.stepIn] 准备发送 stepIn 请求")
        sendRequest(request, callback)
        println("========== [DapDebugSession.stepIn] 函数结束 ==========\n")
    }
    
    /**
     * 单步跳过（step over）
     */
    fun stepOver(threadId: Int, callback: (JsonObject) -> Unit) {
        println("\n========== [DapDebugSession.stepOver] 函数调用 ==========")
        println("[DapDebugSession.stepOver] threadId=$threadId")
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "next",
            "arguments" to mapOf("threadId" to threadId)
        )
        println("[DapDebugSession.stepOver] 准备发送 next 请求")
        sendRequest(request, callback)
        println("========== [DapDebugSession.stepOver] 函数结束 ==========\n")
    }
    
    /**
     * 单步退出（step out）
     */
    fun stepOut(threadId: Int, callback: (JsonObject) -> Unit) {
        println("\n========== [DapDebugSession.stepOut] 函数调用 ==========")
        println("[DapDebugSession.stepOut] threadId=$threadId")
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "stepOut",
            "arguments" to mapOf("threadId" to threadId)
        )
        println("[DapDebugSession.stepOut] 准备发送 stepOut 请求")
        sendRequest(request, callback)
        println("========== [DapDebugSession.stepOut] 函数结束 ==========\n")
    }
    
    /**
     * 获取线程列表
     */
    fun threads(callback: (JsonObject) -> Unit) {
        println("\n========== [DapDebugSession.threads] 函数调用 ==========")
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "threads"
        )
        println("[DapDebugSession.threads] 准备发送 threads 请求")
        sendRequest(request, callback)
        println("========== [DapDebugSession.threads] 函数结束 ==========\n")
    }
    
    /**
     * 获取堆栈跟踪
     */
    fun stackTrace(threadId: Int, callback: (JsonObject) -> Unit) {
        println("\n========== [DapDebugSession.stackTrace] 函数调用 ==========")
        println("[DapDebugSession.stackTrace] threadId=$threadId")
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "stackTrace",
            "arguments" to mapOf(
                "threadId" to threadId,
                "startFrame" to 0,
                "levels" to 20
            )
        )
        println("[DapDebugSession.stackTrace] 准备发送 stackTrace 请求")
        sendRequest(request, callback)
        println("========== [DapDebugSession.stackTrace] 函数结束 ==========\n")
    }
    
    /**
     * 获取作用域（scopes）
     */
    fun scopes(frameId: Int, callback: (JsonObject) -> Unit) {
        println("\n========== [DapDebugSession.scopes] 函数调用 ==========")
        println("[DapDebugSession.scopes] frameId=$frameId")
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "scopes",
            "arguments" to mapOf("frameId" to frameId)
        )
        println("[DapDebugSession.scopes] 准备发送 scopes 请求")
        sendRequest(request, callback)
        println("========== [DapDebugSession.scopes] 函数结束 ==========\n")
    }
    
    /**
     * 获取变量
     */
    fun variables(variablesReference: Int, callback: (JsonObject) -> Unit) {
        println("\n========== [DapDebugSession.variables] 函数调用 ==========")
        println("[DapDebugSession.variables] variablesReference=$variablesReference")
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "variables",
            "arguments" to mapOf("variablesReference" to variablesReference)
        )
        println("[DapDebugSession.variables] 准备发送 variables 请求")
        sendRequest(request, callback)
        println("========== [DapDebugSession.variables] 函数结束 ==========\n")
    }
    
    /**
     * 断开连接
     */
    fun disconnect(callback: (JsonObject) -> Unit = {}) {
        println("\n========== [DapDebugSession.disconnect] 函数调用 ==========")
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "disconnect",
            "arguments" to mapOf("terminateDebuggee" to true)
        )
        println("[DapDebugSession.disconnect] 准备发送 disconnect 请求")
        sendRequest(request, callback)
        isRunning = false
        println("========== [DapDebugSession.disconnect] 函数结束 ==========\n")
    }
    
    /**
     * 发送请求
     */
    private fun sendRequest(request: Map<String, Any>, callback: (JsonObject) -> Unit) {
        val seq = request["seq"] as Int
        val command = request["command"] as String
        
        println("\n========== [DapDebugSession.sendRequest] 发送请求到 lldb-dap ==========")
        println("[sendRequest] 命令: $command, seq=$seq")
        
        pendingRequests[seq] = callback
        println("[sendRequest] 已注册回调函数 for seq=$seq")
        
        val json = gson.toJson(request)
        println("[sendRequest] >>> 发送给 lldb-dap 的数据:")
        println(json)
        
        try {
            sendDapMessage(json)
            println("[sendRequest] ✓ 请求已成功发送: $command")
        } catch (e: Exception) {
            println("[sendRequest] ✗ 发送请求失败: ${e.message}")
            e.printStackTrace()
        }
        println("========== 请求发送结束 ==========\n")
    }
    
    /**
     * 处理接收到的消息
     */
    private fun handleMessage(json: String) {
        println("\n========== [DapDebugSession.handleMessage] 处理来自 lldb-dap 的消息 ==========")
        try {
            val message = gson.fromJson(json, JsonObject::class.java)
            val type = message.get("type")?.asString
            
            println("[handleMessage] 消息类型: $type")
            
            when (type) {
                "response" -> {
                    val seq = message.get("request_seq")?.asInt
                    val command = message.get("command")?.asString
                    val success = message.get("success")?.asBoolean ?: false
                    
                    println("[handleMessage] <<< lldb-dap 响应: $command (request_seq=$seq, success=$success)")
                    println("[handleMessage] 完整响应数据:")
                    println(json)
                    
                    if (!success) {
                        val errorMessage = message.get("message")?.asString
                        println("[handleMessage] ✗ 错误信息: $errorMessage")
                    }
                    
                    if (seq != null) {
                        val callback = pendingRequests.remove(seq)
                        if (callback != null) {
                            println("[handleMessage] 执行回调函数 for $command (seq=$seq)")
                            callback.invoke(message)
                            println("[handleMessage] ✓ 回调已执行: $command")
                        } else {
                            println("[handleMessage] ⚠ 警告: 找不到 seq=$seq 的回调函数")
                        }
                    }
                }
                "event" -> {
                    val event = message.get("event")?.asString
                    println("[handleMessage] <<< lldb-dap 事件: $event")
                    println("[handleMessage] 事件数据:")
                    println(json)
                    handleEvent(event, message)
                }
                else -> {
                    println("[handleMessage] ⚠ 未知消息类型: $type")
                }
            }
        } catch (e: Exception) {
            println("[handleMessage] ✗ 解析消息失败: ${e.message}")
            println("[handleMessage] 原始消息: $json")
            e.printStackTrace()
        }
        println("========== 消息处理结束 ==========\n")
    }
    
    /**
     * 处理事件
     */
    private fun handleEvent(event: String?, message: JsonObject) {
        println("\n========== [DapDebugSession.handleEvent] 处理事件: $event ==========")
        when (event) {
            "stopped" -> {
                val body = message.getAsJsonObject("body")
                val reason = body.get("reason")?.asString ?: "unknown"
                val threadId = body.get("threadId")?.asInt ?: 0
                println("[handleEvent] 停止事件: reason=$reason, threadId=$threadId")
                println("[handleEvent] 调用 onStopped 回调")
                onStopped?.invoke(threadId, reason)
            }
            "output" -> {
                val body = message.getAsJsonObject("body")
                val category = body.get("category")?.asString ?: "console"
                val output = body.get("output")?.asString ?: ""
                println("[handleEvent] 输出事件: category=$category")
                println("[handleEvent] 输出内容: $output")
                onOutput?.invoke(category, output)
            }
            "terminated" -> {
                println("[handleEvent] 终止事件")
                println("[handleEvent] 调用 onTerminated 回调")
                onTerminated?.invoke()
            }
            "exited" -> {
                val body = message.getAsJsonObject("body")
                val exitCode = body.get("exitCode")?.asInt ?: 0
                println("[handleEvent] 退出事件: exitCode=$exitCode")
            }
            "initialized" -> {
                println("[handleEvent] 初始化完成事件")
            }
            else -> {
                println("[handleEvent] 其他事件: $event")
            }
        }
        println("========== 事件处理结束 ==========\n")
    }
    
    /**
     * 发送 DAP 消息
     */
    private fun sendDapMessage(json: String) {
        val body = json.toByteArray(StandardCharsets.UTF_8)
        val header = "Content-Length: ${body.size}\r\n\r\n"
        
        println("[sendDapMessage] 发送 header: Content-Length: ${body.size}")
        println("[sendDapMessage] 发送 body 大小: ${body.size} bytes")
        
        output.write(header.toByteArray(StandardCharsets.US_ASCII))
        output.write(body)
        output.flush()
        
        println("[sendDapMessage] ✓ 数据已刷新到输出流")
    }
    
    /**
     * 读取 DAP 消息
     */
    private fun readDapMessage(): String {
        try {
            println("[readDapMessage] 开始读取消息...")
            // 读 header，直到 \r\n\r\n
            val headerBytes = ArrayList<Byte>()
            var consecutiveBytes = ArrayList<Byte>()
            
            while (true) {
                val b = input.read()
                if (b == -1) {
                    throw java.io.IOException("进程输入流已关闭")
                }
                
                val byte = b.toByte()
                consecutiveBytes.add(byte)
                headerBytes.add(byte)
                
                // 检查是否为 \r\n\r\n
                if (consecutiveBytes.size >= 4) {
                    val last4 = consecutiveBytes.takeLast(4)
                    if (last4[0] == '\r'.code.toByte() &&
                        last4[1] == '\n'.code.toByte() &&
                        last4[2] == '\r'.code.toByte() &&
                        last4[3] == '\n'.code.toByte()
                    ) {
                        // 移除最后的 \r\n\r\n
                        repeat(4) { headerBytes.removeAt(headerBytes.size - 1) }
                        break
                    }
                }
            }
            
            val headerText = String(headerBytes.toByteArray(), StandardCharsets.US_ASCII)
            println("[readDapMessage] 收到 header: $headerText")
            
            val contentLength = headerText
                .split("\r\n")
                .firstOrNull { it.startsWith("Content-Length") }
                ?.split(":")
                ?.getOrNull(1)
                ?.trim()
                ?.toIntOrNull()
                ?: throw IllegalStateException("无法解析 Content-Length")
            
            println("[readDapMessage] Content-Length: $contentLength bytes")
            
            // 读 body
            val body = ByteArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = input.read(body, read, contentLength - read)
                if (n == -1) {
                    throw java.io.IOException("读取 body 时进程输入流关闭")
                }
                read += n
            }
            
            val result = String(body, StandardCharsets.UTF_8)
            println("[readDapMessage] ✓ 读取完整消息，长度: $contentLength bytes")
            return result
        } catch (e: Exception) {
            println("[readDapMessage] ✗ 异常: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    /**
     * 查找 lldb-dap 路径
     */
    private fun findLldbDapPath(): String {
        val possiblePaths = listOf(
            "/opt/homebrew/opt/llvm/bin/lldb-dap",
            "/usr/local/opt/llvm/bin/lldb-dap",
            "lldb-dap"
        )
        
        return possiblePaths.firstOrNull { path ->
            try {
                java.io.File(path).exists() || path == "lldb-dap"
            } catch (e: Exception) {
                false
            }
        } ?: throw IllegalStateException("lldb-dap not found. Please install LLVM or add lldb-dap to PATH")
    }
}
