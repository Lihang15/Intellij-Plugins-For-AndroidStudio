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
        val lldbDapPath = findLldbDapPath()
        println("[DapDebugSession] 使用 lldb-dap: $lldbDapPath")
        
        val cmd = GeneralCommandLine(lldbDapPath)
        processHandler = OSProcessHandler(cmd)
        processHandler.startNotify()
        
        val process = processHandler.process
        input = BufferedInputStream(process.inputStream)
        output = process.outputStream
        
        isRunning = true
        
        // 监听 STDERR（根据记忆，lldb-dap 可能将响应发送到 stderr）
        Thread {
            try {
                val errorReader = process.errorStream.bufferedReader()
                while (isRunning) {
                    val line = errorReader.readLine() ?: break
                    println("[DapDebugSession] STDERR: $line")
                }
            } catch (e: Exception) {
                println("[DapDebugSession] STDERR 读取异常: ${e.message}")
            }
        }.start()
        
        // 启动消息读取线程
        Thread {
            try {
                println("[DapDebugSession] 开始监听 DAP 消息...")
                while (isRunning) {
                    val message = readDapMessage()
                    println("[DapDebugSession] 收到消息: ${message.take(200)}...") // 打印前200个字符
                    handleMessage(message)
                }
            } catch (e: Exception) {
                println("[DapDebugSession] 读取消息异常: ${e.message}")
                e.printStackTrace()
            }
        }.start()
        
        println("[DapDebugSession] lldb-dap 进程已启动，监听线程已开始")
    }
    
    /**
     * 发送 initialize 请求
     */
    fun initialize(callback: (JsonObject) -> Unit) {
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
        sendRequest(request, callback)
    }
    
    /**
     * 发送 launch 请求（启动目标程序）
     */
    fun launch(program: String, args: List<String> = emptyList(), callback: (JsonObject) -> Unit) {
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
        sendRequest(request, callback)
    }
    
    /**
     * 设置断点
     */
    fun setBreakpoints(sourceFile: String, lines: List<Int>, callback: (JsonObject) -> Unit) {
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
        sendRequest(request, callback)
    }
    
    /**
     * 配置完成
     */
    fun configurationDone(callback: (JsonObject) -> Unit) {
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "configurationDone"
        )
        sendRequest(request, callback)
    }
    
    /**
     * 继续执行
     */
    fun continue_(threadId: Int, callback: (JsonObject) -> Unit) {
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "continue",
            "arguments" to mapOf("threadId" to threadId)
        )
        sendRequest(request, callback)
    }
    
    /**
     * 单步进入（step into）
     */
    fun stepIn(threadId: Int, callback: (JsonObject) -> Unit) {
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "stepIn",
            "arguments" to mapOf("threadId" to threadId)
        )
        sendRequest(request, callback)
    }
    
    /**
     * 单步跳过（step over）
     */
    fun stepOver(threadId: Int, callback: (JsonObject) -> Unit) {
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "next",
            "arguments" to mapOf("threadId" to threadId)
        )
        sendRequest(request, callback)
    }
    
    /**
     * 单步退出（step out）
     */
    fun stepOut(threadId: Int, callback: (JsonObject) -> Unit) {
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "stepOut",
            "arguments" to mapOf("threadId" to threadId)
        )
        sendRequest(request, callback)
    }
    
    /**
     * 获取线程列表
     */
    fun threads(callback: (JsonObject) -> Unit) {
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "threads"
        )
        sendRequest(request, callback)
    }
    
    /**
     * 获取堆栈跟踪
     */
    fun stackTrace(threadId: Int, callback: (JsonObject) -> Unit) {
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
        sendRequest(request, callback)
    }
    
    /**
     * 获取作用域（scopes）
     */
    fun scopes(frameId: Int, callback: (JsonObject) -> Unit) {
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "scopes",
            "arguments" to mapOf("frameId" to frameId)
        )
        sendRequest(request, callback)
    }
    
    /**
     * 获取变量
     */
    fun variables(variablesReference: Int, callback: (JsonObject) -> Unit) {
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "variables",
            "arguments" to mapOf("variablesReference" to variablesReference)
        )
        sendRequest(request, callback)
    }
    
    /**
     * 断开连接
     */
    fun disconnect(callback: (JsonObject) -> Unit = {}) {
        val request = mapOf(
            "seq" to seqCounter.getAndIncrement(),
            "type" to "request",
            "command" to "disconnect",
            "arguments" to mapOf("terminateDebuggee" to true)
        )
        sendRequest(request, callback)
        isRunning = false
    }
    
    /**
     * 发送请求
     */
    private fun sendRequest(request: Map<String, Any>, callback: (JsonObject) -> Unit) {
        val seq = request["seq"] as Int
        pendingRequests[seq] = callback
        
        val json = gson.toJson(request)
        val command = request["command"] as String
        println("[DapDebugSession] >>> $command (seq=$seq)")
        println("[DapDebugSession] 请求内容: $json")
        
        try {
            sendDapMessage(json)
            println("[DapDebugSession] 请求已发送: $command")
        } catch (e: Exception) {
            println("[DapDebugSession] 发送请求失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 处理接收到的消息
     */
    private fun handleMessage(json: String) {
        try {
            val message = gson.fromJson(json, JsonObject::class.java)
            val type = message.get("type")?.asString
            
            println("[DapDebugSession] 解析消息类型: $type")
            
            when (type) {
                "response" -> {
                    val seq = message.get("request_seq")?.asInt
                    val command = message.get("command")?.asString
                    val success = message.get("success")?.asBoolean ?: false
                    
                    println("[DapDebugSession] <<< $command response (seq=$seq, success=$success)")
                    
                    if (!success) {
                        val errorMessage = message.get("message")?.asString
                        println("[DapDebugSession] 错误信息: $errorMessage")
                    }
                    
                    if (seq != null) {
                        val callback = pendingRequests.remove(seq)
                        if (callback != null) {
                            callback.invoke(message)
                            println("[DapDebugSession] 回调已执行: $command")
                        } else {
                            println("[DapDebugSession] 警告: 找不到 seq=$seq 的回调")
                        }
                    }
                }
                "event" -> {
                    val event = message.get("event")?.asString
                    println("[DapDebugSession] <<< Event: $event")
                    handleEvent(event, message)
                }
                else -> {
                    println("[DapDebugSession] 未知消息类型: $type")
                }
            }
        } catch (e: Exception) {
            println("[DapDebugSession] 解析消息失败: ${e.message}")
            println("[DapDebugSession] 原始消息: $json")
            e.printStackTrace()
        }
    }
    
    /**
     * 处理事件
     */
    private fun handleEvent(event: String?, message: JsonObject) {
        when (event) {
            "stopped" -> {
                val body = message.getAsJsonObject("body")
                val reason = body.get("reason")?.asString ?: "unknown"
                val threadId = body.get("threadId")?.asInt ?: 0
                println("[DapDebugSession] Stopped: reason=$reason, threadId=$threadId")
                onStopped?.invoke(threadId, reason)
            }
            "output" -> {
                val body = message.getAsJsonObject("body")
                val category = body.get("category")?.asString ?: "console"
                val output = body.get("output")?.asString ?: ""
                onOutput?.invoke(category, output)
            }
            "terminated" -> {
                println("[DapDebugSession] Terminated")
                onTerminated?.invoke()
            }
            "exited" -> {
                val body = message.getAsJsonObject("body")
                val exitCode = body.get("exitCode")?.asInt ?: 0
                println("[DapDebugSession] Exited with code: $exitCode")
            }
        }
    }
    
    /**
     * 发送 DAP 消息
     */
    private fun sendDapMessage(json: String) {
        val body = json.toByteArray(StandardCharsets.UTF_8)
        val header = "Content-Length: ${body.size}\r\n\r\n"
        
        output.write(header.toByteArray(StandardCharsets.US_ASCII))
        output.write(body)
        output.flush()
    }
    
    /**
     * 读取 DAP 消息
     */
    private fun readDapMessage(): String {
        try {
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
            println("[DapDebugSession] 收到 header: $headerText")
            
            val contentLength = headerText
                .split("\r\n")
                .firstOrNull { it.startsWith("Content-Length") }
                ?.split(":")
                ?.getOrNull(1)
                ?.trim()
                ?.toIntOrNull()
                ?: throw IllegalStateException("无法解析 Content-Length")
            
            println("[DapDebugSession] Content-Length: $contentLength")
            
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
            println("[DapDebugSession] 读取完整消息，长度: $contentLength")
            return result
        } catch (e: Exception) {
            println("[DapDebugSession] readDapMessage 异常: ${e.message}")
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
