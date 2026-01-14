package org.jetbrains.plugins.template.debuger

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.text.SimpleDateFormat
import java.util.Date

/**
 * LLDB 调试会话管理器
 * 负责与 lldb 进程的通信、MI命令收发、响应解析
 */
class DapDebugSession(private val executablePath: String) {
    
    companion object {
        private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS")
        private var logSeq = AtomicInteger(0)
        
        /** 带时间戳的日志输出 */
        fun log(tag: String, message: String, level: String = "INFO") {
            val timestamp = dateFormat.format(Date())
            val seq = logSeq.incrementAndGet()
            val thread = Thread.currentThread().name
            println("[$timestamp][$seq][$level][$thread][$tag] $message")
        }
        
        /** 打印调用栈 */
        fun logCallStack(tag: String, depth: Int = 5) {
            val stackTrace = Thread.currentThread().stackTrace
            val sb = StringBuilder("\n=== 调用栈 ===")
            for (i in 3 until minOf(3 + depth, stackTrace.size)) {
                val e = stackTrace[i]
                sb.append("\n  -> ${e.className}.${e.methodName}(${e.fileName}:${e.lineNumber})")
            }
            log(tag, sb.toString())
        }
        
        /** 打印分隔线 */
        fun logSeparator(tag: String, title: String) {
            log(tag, "\n" + "=".repeat(50) + "\n[ $title ]\n" + "=".repeat(50))
        }
        
        /** 打印 LLDB 发送的数据 */
        fun logLldbSend(command: String, seq: Int) {
            log("LLDB-SEND", "\n>>> [SEQ=$seq] 发送命令: $command")
        }
        
        /** 打印 LLDB 返回的数据 */
        fun logLldbReceive(response: String, seq: Int?) {
            val lines = response.split("\n").joinToString("\n    ") { it }
            log("LLDB-RECV", "\n<<< [SEQ=$seq] 收到响应 (长度=${response.length}):\n    $lines")
        }
    }
    
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
    
    // 标记是否收到了命令回显（以 (lldb) 开头的行）
    private var commandEchoReceived = false
    
    var onStopped: ((threadId: Int, reason: String) -> Unit)? = null
    var onOutput: ((category: String, output: String) -> Unit)? = null
    var onTerminated: (() -> Unit)? = null
    
    /**
     * 启动 lldb 进程
     */
    fun start() {
        logSeparator("DapDebugSession.start", "启动 LLDB 进程")
        logCallStack("DapDebugSession.start")
        
        val lldbPath = findLldbPath()
        log("start", "使用 lldb 路径: $lldbPath")
        log("start", "可执行文件路径: $executablePath")
            
        val cmd = GeneralCommandLine(lldbPath)
        log("start", "命令行: ${cmd.commandLineString}")
            
        processHandler = OSProcessHandler(cmd)
        processHandler.startNotify()
        log("start", "LLDB 进程已启动, PID: ${processHandler.process.pid()}")
            
        val process = processHandler.process
        input = BufferedReader(InputStreamReader(process.inputStream))
        output = BufferedWriter(OutputStreamWriter(process.outputStream))
        log("start", "输入输出流已初始化")
            
        isRunning = true
            
        Thread({
            log("LLDB-Reader", "=== LLDB 输出监听线程启动 ===")
            var lineCount = 0
            try {
                while (isRunning) {
                    val line = input.readLine()
                    if (line == null) {
                        log("LLDB-Reader", "readLine 返回 null, 退出循环")
                        break
                    }
                    if (line.isNotEmpty()) {
                        lineCount++
                        log("LLDB-Reader", "[LINE#$lineCount] \"$line\"")
                        handleLldbOutput(line)
                    }
                }
                log("LLDB-Reader", "监听循环结束, 共读取 $lineCount 行")
            } catch (e: Exception) {
                log("LLDB-Reader", "读取异常: ${e.message}", "ERROR")
                e.printStackTrace()
            }
        }, "LLDB-Reader-Thread").start()
            
        Thread.sleep(300)
        log("start", "启动完成")
    }
    
    /**
     * 初始化
     */
    fun initialize(callback: (String) -> Unit) {
        logSeparator("initialize", "初始化")
        logCallStack("initialize")
        log("initialize", "LLDB 不需要显式初始化, 当前状态: targetLoaded=$targetLoaded, isRunning=$isRunning")
        callback.invoke("initialized")
        log("initialize", "初始化回调已执行")
    }
    
    /**
     * 加载目标程序
     */
    fun launch(program: String, args: List<String> = emptyList(), callback: (String) -> Unit) {
        logSeparator("launch", "加载目标程序")
        logCallStack("launch")
        log("launch", "program: $program")
        log("launch", "args: $args")
        
        val targetCmd = "target create \"$program\""
        log("launch", "发送 target create 命令")
        
        sendCommand(targetCmd) { response ->
            log("launch", "target create 响应:\n$response")
            val success = !response.contains("error:")
            log("launch", "target create 结果: ${if (success) "成功" else "失败"}")
            
            targetLoaded = true
            
            if (args.isNotEmpty()) {
                val argsStr = args.joinToString(" ") { "\"$it\"" }
                val settingsCmd = "settings set target.run-args $argsStr"
                log("launch", "设置程序参数: $argsStr")
                sendCommand(settingsCmd) { argsResponse ->
                    log("launch", "参数设置响应: $argsResponse")
                    callback.invoke("launched")
                }
            } else {
                callback.invoke("launched")
            }
        }
    }
    
    /**
     * 设置断点
     */
    fun setBreakpoints(sourceFile: String, lines: List<Int>, callback: (String) -> Unit) {
        logSeparator("setBreakpoints", "设置断点")
        logCallStack("setBreakpoints")
        log("setBreakpoints", "源文件: $sourceFile")
        log("setBreakpoints", "行号列表: $lines")
        log("setBreakpoints", "targetLoaded: $targetLoaded")
            
        if (lines.isEmpty()) {
            log("setBreakpoints", "行号列表为空, 返回")
            callback.invoke("no breakpoints")
            return
        }
            
        val fileName = sourceFile.substringAfterLast('/')
        log("setBreakpoints", "提取的文件名: $fileName")
            
        var processed = 0
        var allResponses = StringBuilder()
            
        lines.forEachIndexed { index, line ->
            log("setBreakpoints", "--- 处理断点 #${index + 1}/${lines.size}: 行 $line ---")
            val cmd = "breakpoint set --file \"$sourceFile\" --line $line"
            log("setBreakpoints", "LLDB 命令: $cmd")
                
            sendCommand(cmd) { response ->
                log("setBreakpoints", "断点 $line 响应:\n$response")
                val hasError = response.contains("error:") || response.contains("no locations")
                
                if (hasError) {
                    log("setBreakpoints", "完整路径失败, 尝试使用文件名回退")
                    val fallbackCmd = "breakpoint set --file \"$fileName\" --line $line"
                    log("setBreakpoints", "回退命令: $fallbackCmd")
                    
                    sendCommand(fallbackCmd) { fallbackResponse ->
                        log("setBreakpoints", "回退断点响应:\n$fallbackResponse")
                        allResponses.append(fallbackResponse).append("\n")
                        processed++
                        log("setBreakpoints", "已处理: $processed/${lines.size}")
                        if (processed == lines.size) listAndVerifyBreakpoints(allResponses, callback)
                    }
                } else {
                    allResponses.append(response).append("\n")
                    processed++
                    log("setBreakpoints", "已处理: $processed/${lines.size}")
                    if (processed == lines.size) listAndVerifyBreakpoints(allResponses, callback)
                }
            }
        }
    }
        
    /**
     * 列出并验证断点
     */
    private fun listAndVerifyBreakpoints(allResponses: StringBuilder, callback: (String) -> Unit) {
        log("listAndVerifyBreakpoints", "验证断点设置结果")
        sendCommand("breakpoint list") { listResponse ->
            log("listAndVerifyBreakpoints", "LLDB 断点列表:\n$listResponse")
            val bpCount = "Breakpoint (\\d+):".toRegex().findAll(listResponse).count()
            log("listAndVerifyBreakpoints", "断点总数: $bpCount")
            callback.invoke(allResponses.toString())
        }
    }
    
    /**
     * 删除断点
     */
    fun deleteBreakpoint(sourceFile: String, line: Int, callback: (String) -> Unit) {
        logSeparator("deleteBreakpoint", "删除断点")
        logCallStack("deleteBreakpoint")
        log("deleteBreakpoint", "文件: $sourceFile, 行号: $line")
        
        sendCommand("breakpoint list") { listResponse ->
            log("deleteBreakpoint", "当前断点列表:\n$listResponse")
            
            val fileName = sourceFile.substringAfterLast('/')
            val bpIdPattern = "(\\d+): file = '([^']+)', line = (\\d+)".toRegex()
            val bpIdPattern2 = "(\\d+): .* at ([^:]+):(\\d+)".toRegex()
            var breakpointId: Int? = null
            
            for (lineStr in listResponse.split("\n")) {
                bpIdPattern.find(lineStr)?.let { match ->
                    val id = match.groupValues[1].toIntOrNull()
                    val file = match.groupValues[2]
                    val lineNum = match.groupValues[3].toIntOrNull()
                    log("deleteBreakpoint", "匹配模式1: id=$id, file=$file, lineNum=$lineNum")
                    if (lineNum == line && (file == sourceFile || file.endsWith(fileName) || sourceFile.endsWith(file))) {
                        breakpointId = id
                    }
                }
                if (breakpointId == null) {
                    bpIdPattern2.find(lineStr)?.let { match ->
                        val id = match.groupValues[1].split(".")[0].toIntOrNull()
                        val file = match.groupValues[2]
                        val lineNum = match.groupValues[3].toIntOrNull()
                        log("deleteBreakpoint", "匹配模式2: id=$id, file=$file, lineNum=$lineNum")
                        if (lineNum == line && (file.endsWith(fileName) || fileName.contains(file))) {
                            breakpointId = id
                        }
                    }
                }
            }
            
            if (breakpointId != null) {
                val deleteCmd = "breakpoint delete $breakpointId"
                log("deleteBreakpoint", "删除断点: $deleteCmd")
                sendCommand(deleteCmd) { deleteResponse ->
                    log("deleteBreakpoint", "删除响应: $deleteResponse")
                    callback.invoke(deleteResponse)
                }
            } else {
                log("deleteBreakpoint", "未找到匹配的断点", "WARN")
                callback.invoke("breakpoint not found")
            }
        }
    }
    
    /**
     * 配置完成，启动程序
     */
    fun configurationDone(callback: (String) -> Unit) {
        logSeparator("configurationDone", "配置完成/启动程序")
        logCallStack("configurationDone")
        log("configurationDone", "状态: targetLoaded=$targetLoaded, pendingRequests=${pendingRequests.size}")
            
        val cmd = "run"
        log("configurationDone", "发送 run 命令")
        
        sendCommand(cmd) { response ->
            log("configurationDone", "run 响应:\n$response")
            val hasError = response.contains("error:")
            val isLaunched = response.contains("Process") && response.contains("launched")
            val isStopped = response.contains("stopped")
            log("configurationDone", "响应分析: hasError=$hasError, isLaunched=$isLaunched, isStopped=$isStopped")
            
            Thread.sleep(100)
            log("configurationDone", "查询进程状态")
            sendCommand("process status") { statusResponse ->
                log("configurationDone", "进程状态:\n$statusResponse")
                when {
                    statusResponse.contains("stopped") -> log("configurationDone", "进程已停止(可能命中断点)")
                    statusResponse.contains("running") -> log("configurationDone", "进程运行中")
                    statusResponse.contains("exited") -> log("configurationDone", "进程已退出")
                }
                callback.invoke("configuration done")
            }
        }
    }
    
    /**
     * 继续执行
     */
    fun continue_(threadId: Int, callback: (String) -> Unit) {
        logSeparator("continue_", "继续执行")
        logCallStack("continue_")
        log("continue_", "threadId=$threadId")
        sendCommand("continue") { response ->
            log("continue_", "响应:\n$response")
            callback.invoke(response)
        }
    }
    
    /**
     * 单步进入
     */
    fun stepIn(threadId: Int, callback: (String) -> Unit) {
        logSeparator("stepIn", "单步进入")
        logCallStack("stepIn")
        log("stepIn", "threadId=$threadId")
        sendCommand("step") { response ->
            log("stepIn", "响应:\n$response")
            callback.invoke(response)
        }
    }
    
    /**
     * 单步跳过
     */
    fun stepOver(threadId: Int, callback: (String) -> Unit) {
        logSeparator("stepOver", "单步跳过")
        logCallStack("stepOver")
        log("stepOver", "threadId=$threadId")
        sendCommand("next") { response ->
            log("stepOver", "响应:\n$response")
            callback.invoke(response)
        }
    }
    
    /**
     * 单步退出
     */
    fun stepOut(threadId: Int, callback: (String) -> Unit) {
        logSeparator("stepOut", "单步退出")
        logCallStack("stepOut")
        log("stepOut", "threadId=$threadId")
        sendCommand("finish") { response ->
            log("stepOut", "响应:\n$response")
            callback.invoke(response)
        }
    }
    
    /**
     * 获取线程列表
     */
    fun threads(callback: (String) -> Unit) {
        logSeparator("threads", "获取线程列表")
        logCallStack("threads")
        sendCommand("thread list") { response ->
            log("threads", "响应:\n$response")
            callback.invoke(response)
        }
    }
    
    /**
     * 获取堆栈跟踪
     */
    fun stackTrace(threadId: Int, callback: (String) -> Unit) {
        logSeparator("stackTrace", "获取堆栈跟踪")
        logCallStack("stackTrace")
        log("stackTrace", "threadId=$threadId")
        sendCommand("thread backtrace") { response ->
            log("stackTrace", "堆栈响应:\n$response")
            callback.invoke(response)
        }
    }
    
    /**
     * 获取作用域/变量
     */
    fun scopes(frameId: Int, callback: (String) -> Unit) {
        logSeparator("scopes", "获取作用域")
        logCallStack("scopes")
        log("scopes", "frameId=$frameId")
        sendCommand("frame variable") { response ->
            log("scopes", "变量响应:\n$response")
            callback.invoke(response)
        }
    }
    
    /**
     * 获取变量
     */
    fun variables(variablesReference: Int, callback: (String) -> Unit) {
        logSeparator("variables", "获取变量")
        logCallStack("variables")
        log("variables", "variablesReference=$variablesReference")
        sendCommand("frame variable") { response ->
            log("variables", "变量响应:\n$response")
            callback.invoke(response)
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect(callback: (String) -> Unit = {}) {
        logSeparator("disconnect", "断开连接")
        logCallStack("disconnect")
        log("disconnect", "isRunning -> false")
        isRunning = false
        
        try {
            output.write("quit")
            output.newLine()
            output.flush()
            log("disconnect", "quit 命令已发送")
        } catch (e: Exception) {
            log("disconnect", "发送 quit 异常: ${e.message}", "ERROR")
        }
        callback.invoke("disconnected")
    }
    
    /**
     * 发送命令到 lldb
     */
    private fun sendCommand(command: String, callback: (String) -> Unit) {
        val seq = seqCounter.getAndIncrement()
        
        log("sendCommand", "=== 发送 LLDB 命令 ===")
        log("sendCommand", "SEQ: $seq, 命令: $command")
        log("sendCommand", "pendingRequests: ${pendingRequests.keys}")
        logLldbSend(command, seq)
            
        pendingRequests[seq] = callback
        log("sendCommand", "已注册回调 seq=$seq")
            
        try {
            output.write(command)
            output.newLine()
            output.flush()
            log("sendCommand", "命令已发送")
        } catch (e: Exception) {
            log("sendCommand", "发送失败: ${e.message}", "ERROR")
            e.printStackTrace()
        }
    }
    
    /**
     * 处理 lldb 输出
     * 
     * 关键改进：
     * 1. 停止事件（断点/step）立即推送，不等待回调
     * 2. 命令响应仍然走回调机制
     */
    private fun handleLldbOutput(line: String) {
        log("handleLldbOutput", "=== 处理 LLDB 输出 ===")
        log("handleLldbOutput", "原始行: \"$line\"")
        log("handleLldbOutput", "pendingRequests: ${pendingRequests.keys}, commandEchoReceived: $commandEchoReceived")
        
        val trimmedLine = line.trim()
        val startsWithPrompt = trimmedLine.startsWith("(lldb)")
        val isPromptOnly = trimmedLine == "(lldb)"
        
        log("handleLldbOutput", "startsWithPrompt=$startsWithPrompt, isPromptOnly=$isPromptOnly")
        
        // 关键修复：先检查停止事件（在缓冲之前）
        checkStopEvents(line)
        
        outputBuffer.append(line).append("\n")
        log("handleLldbOutput", "outputBuffer 长度: ${outputBuffer.length}")
        
        // 单独的 (lldb) 提示符
        if (isPromptOnly && pendingRequests.isNotEmpty()) {
            log("handleLldbOutput", "检测到 (lldb) 提示符, 触发回调")
            triggerCallback()
            return
        }
        
        // 命令回显
        if (startsWithPrompt && !isPromptOnly) {
            log("handleLldbOutput", "检测到命令回显")
            commandEchoReceived = true
            return
        }
        
        // 收到响应内容后触发回调
        if (commandEchoReceived && !startsWithPrompt && pendingRequests.isNotEmpty()) {
            log("handleLldbOutput", "收到命令响应, 触发回调")
            triggerCallback()
            return
        }
        
        log("handleLldbOutput", "继续等待更多输出...")
    }
    
    /**
     * 触发最早的待处理回调
     */
    private fun triggerCallback() {
        val bufferedOutput = outputBuffer.toString()
        outputBuffer.clear()
        commandEchoReceived = false
        
        val firstKey = pendingRequests.keys.minOrNull()
        log("triggerCallback", "firstKey=$firstKey, output 长度=${bufferedOutput.length}")
        logLldbReceive(bufferedOutput, firstKey)
        
        if (firstKey != null) {
            val callback = pendingRequests.remove(firstKey)
            if (callback != null) {
                log("triggerCallback", "执行回调 seq=$firstKey")
                try {
                    callback.invoke(bufferedOutput)
                    log("triggerCallback", "回调执行成功")
                } catch (e: Exception) {
                    log("triggerCallback", "回调异常: ${e.message}", "ERROR")
                    e.printStackTrace()
                }
            }
        }
    }
    
    /**
     * 检查停止事件
     * 
     * LLDB 输出格式（两行）：
     * 1. "Process 91141 stopped"
     * 2. "* thread #1, queue = 'com.apple.main-thread', stop reason = breakpoint 2.1"
     * 
     * 关键修复：
     * 1. 停止事件必须 **立即异步触发**，不能等待命令回调
     * 2. 使用独立线程推送，避免阻塞 LLDB 输出解析
     * 3. 必须按照两行顺序检测，确保不会重复触发
     */
    private fun checkStopEvents(line: String) {
        when {
            // 第一步：检测到 "Process stopped"
            line.contains("Process") && line.contains("stopped") -> {
                log("checkStopEvents", ">>> 检测到 Process stopped，等待线程信息...")
                lastStopDetected = true
            }
            // 第二步：在 lastStopDetected 之后检测到线程信息
            lastStopDetected && line.startsWith("*") && line.contains("thread #") -> {
                log("checkStopEvents", ">>> 检测到线程信息，触发停止事件 <<<")
                lastStopDetected = false
                
                val threadId = extractThreadId(line)
                val reason = extractStopReason(line)
                log("checkStopEvents", "停止事件: threadId=$threadId, reason=$reason")
                
                // 关键：立即在新线程中触发，不阻塞 LLDB 输出
                Thread {
                    try {
                        log("checkStopEvents", "异步触发 onStopped 回调...")
                        onStopped?.invoke(threadId, reason)
                        log("checkStopEvents", "onStopped 回调完成")
                    } catch (e: Exception) {
                        log("checkStopEvents", "onStopped 回调异常: ${e.message}", "ERROR")
                        e.printStackTrace()
                    }
                }.start()
            }
            // 退出事件
            line.contains("Process") && line.contains("exited") -> {
                log("checkStopEvents", ">>> 检测到退出事件 <<<")
                lastStopDetected = false
                Thread {
                    onTerminated?.invoke()
                }.start()
            }
            // 恢复运行
            line.contains("Process") && line.contains("resuming") -> {
                log("checkStopEvents", "程序恢复运行")
                lastStopDetected = false
            }
            // 断点设置确认
            line.contains("Breakpoint") && line.contains("where =") -> {
                log("checkStopEvents", "断点设置确认: $line")
                onOutput?.invoke("console", line)
            }
            // 其他输出
            else -> {
                if (!line.startsWith("(lldb)")) {
                    onOutput?.invoke("console", line)
                }
            }
        }
    }
    
    /**
     * 从输出行中提取线程ID
     */
    private fun extractThreadId(line: String): Int {
        val pattern = "thread #(\\d+)".toRegex()
        val match = pattern.find(line)
        val threadId = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
        log("extractThreadId", "从 '$line' 提取 threadId=$threadId")
        return threadId
    }
        
    /**
     * 从输出行中提取停止原因
     */
    private fun extractStopReason(line: String): String {
        val reason = when {
            line.contains("breakpoint") -> "breakpoint"
            line.contains("step") -> "step"
            line.contains("signal") -> "signal"
            else -> "unknown"
        }
        log("extractStopReason", "从 '$line' 提取 reason=$reason")
        return reason
    }
    
    /**
     * 查找 lldb 路径
     */
    private fun findLldbPath(): String {
        val possiblePaths = listOf(
            "/opt/homebrew/opt/llvm/bin/lldb",
            "lldb"
        )
        
        log("findLldbPath", "尝试查找 lldb: $possiblePaths")
        
        val path = possiblePaths.firstOrNull { p ->
            try {
                java.io.File(p).exists() || p == "lldb"
            } catch (e: Exception) {
                false
            }
        } ?: throw IllegalStateException("lldb not found")
        
        log("findLldbPath", "找到 lldb: $path")
        return path
    }
}
