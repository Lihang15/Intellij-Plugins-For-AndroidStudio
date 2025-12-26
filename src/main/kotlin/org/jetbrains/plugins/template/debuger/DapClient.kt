package org.jetbrains.plugins.template.debuger

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import java.io.BufferedInputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

object DapClient {


    fun runStart() {
        try {
            // 启动 lldb-dap
            // 尝试多个可能的路径
            val possiblePaths = listOf(
                "/opt/homebrew/opt/llvm/bin/lldb-dap",
                "/usr/local/opt/llvm/bin/lldb-dap",
                "lldb-dap" // 从PATH环境变量查找
            )

            val lldbDapPath = possiblePaths.firstOrNull { path ->
                try {
                    java.io.File(path).exists() || path == "lldb-dap"
                } catch (e: Exception) {
                    false
                }
            } ?: throw IllegalStateException("lldb-dap not found. Please install LLVM or add lldb-dap to PATH")

            println("使用 lldb-dap 路径: $lldbDapPath")

            val cmd = GeneralCommandLine(lldbDapPath)
            val handler = OSProcessHandler(cmd)
            handler.startNotify()

            val process = handler.process   //  关键

            val input = BufferedInputStream(process.inputStream)
            val output = process.outputStream

            // 2️发送 initialize
            val initializeJson =
                """{"seq":1,"type":"request","command":"initialize","arguments":{"adapterID":"lldb","pathFormat":"path","linesStartAt1":true,"columnsStartAt1":true}}"""

            println("发送请求: $initializeJson")
            sendDAPMessage(output, initializeJson)

            // 3 读取并打印一条响应
            val response = readDAPMessage(input)
            println("====== LLDB-DAP RESPONSE ======")
            println(response)

            // 4️结束进程（只是 smoke test）
            handler.destroyProcess()
            println("lldb-dap 进程已关闭")
        } catch (e: Exception) {
            println("错误: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun sendDAPMessage(out: OutputStream, json: String) {
        val body = json.toByteArray(StandardCharsets.UTF_8)
        val header = "Content-Length: ${body.size}\r\n\r\n"

        out.write(header.toByteArray(StandardCharsets.US_ASCII))
        out.write(body)
        out.flush()
    }
    //handler.addProcessListener(...)
    // 不要用 ProcessListener 读 DAP 他是按行回调，它会破坏 Content-Length
    private fun readDAPMessage(input: BufferedInputStream): String {
        // 读 header，直到 \r\n\r\n
        val headerBytes = ArrayList<Byte>()
        while (true) {
            val b1 = input.read().toByte()
            val b2 = input.read().toByte()
            val b3 = input.read().toByte()
            val b4 = input.read().toByte()

            if (b1 == '\r'.code.toByte() &&
                b2 == '\n'.code.toByte() &&
                b3 == '\r'.code.toByte() &&
                b4 == '\n'.code.toByte()
            ) {
                break
            }

            headerBytes.add(b1)
            headerBytes.add(b2)
            headerBytes.add(b3)
            headerBytes.add(b4)
        }

        val headerText = String(headerBytes.toByteArray(), StandardCharsets.US_ASCII)

        val contentLength = headerText
            .split("\r\n")
            .first { it.startsWith("Content-Length") }
            .split(":")[1]
            .trim()
            .toInt()

        // 读 body
        val body = ByteArray(contentLength)
        var read = 0
        while (read < contentLength) {
            read += input.read(body, read, contentLength - read)
        }

        return String(body, StandardCharsets.UTF_8)
    }
}
