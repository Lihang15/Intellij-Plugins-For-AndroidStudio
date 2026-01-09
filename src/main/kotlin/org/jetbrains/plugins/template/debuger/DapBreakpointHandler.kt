package org.jetbrains.plugins.template.debuger

import com.intellij.xdebugger.breakpoints.*
import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import org.jetbrains.plugins.template.debuger.DapDebugSession.Companion.log
import org.jetbrains.plugins.template.debuger.DapDebugSession.Companion.logSeparator
import org.jetbrains.plugins.template.debuger.DapDebugSession.Companion.logCallStack
import org.jetbrains.plugins.template.cpp.CppLineBreakpointType
import org.jetbrains.plugins.template.cpp.CppBreakpointProperties

/**
 * DAP 行断点处理器 - 专门处理 C++ 断点
 */
class DapBreakpointHandler(
    private val process: DapDebugProcess
) : XBreakpointHandler<XLineBreakpoint<CppBreakpointProperties>>(
    CppLineBreakpointType::class.java
) {
    
    init {
        log("DapBreakpointHandler.init", "构造函数调用")
        logCallStack("DapBreakpointHandler.init")
    }
    
    private val registeredBreakpoints = ConcurrentHashMap<String, XLineBreakpoint<CppBreakpointProperties>>()
    private val lldbBreakpointIds = ConcurrentHashMap<String, Int>()
    private val pendingBreakpoints = CopyOnWriteArrayList<XLineBreakpoint<CppBreakpointProperties>>()
    
    @Volatile
    private var lldbReady = false

    override fun registerBreakpoint(breakpoint: XLineBreakpoint<CppBreakpointProperties>) {
        logSeparator("registerBreakpoint", "注册断点")
        logCallStack("registerBreakpoint")
        
        val pos = breakpoint.sourcePosition
        if (pos == null) {
            log("registerBreakpoint", "sourcePosition 为 null, 跳过", "WARN")
            return
        }
        
        val filePath = pos.file.path
        val line = breakpoint.line + 1  // LLDB 是 1-based
        val key = "$filePath:$line"
        
        log("registerBreakpoint", "断点信息: $key")
        log("registerBreakpoint", "lldbReady=$lldbReady, pendingBreakpoints.size=${pendingBreakpoints.size}")
        log("registerBreakpoint", "registeredBreakpoints.keys=${registeredBreakpoints.keys}")
        
        registeredBreakpoints[key] = breakpoint
        
        if (!lldbReady) {
            log("registerBreakpoint", "LLDB 未就绪, 缓存断点: $key")
            pendingBreakpoints.add(breakpoint)
            log("registerBreakpoint", "缓存后 pendingBreakpoints.size=${pendingBreakpoints.size}")
            return
        }
        
        log("registerBreakpoint", "LLDB 已就绪, 直接同步断点")
        syncBreakpointToLldb(breakpoint, filePath, line, key)
    }
    
    /**
     * 同步单个断点到 LLDB
     */
    private fun syncBreakpointToLldb(breakpoint: XLineBreakpoint<CppBreakpointProperties>, filePath: String, line: Int, key: String) {
        log("syncBreakpointToLldb", "=== 同步断点到 LLDB ===")
        log("syncBreakpointToLldb", "key=$key, filePath=$filePath, line=$line")
        
        val dapSession = process.getDapSession()
        dapSession.setBreakpoints(filePath, listOf(line)) { response ->
            log("syncBreakpointToLldb", "setBreakpoints 响应: $response")
            
            val success = !response.contains("error:") && 
                          (response.contains("Breakpoint") || response.contains("breakpoint"))
            log("syncBreakpointToLldb", "设置结果: ${if (success) "成功" else "失败"}")
            
            ApplicationManager.getApplication().invokeLater {
                if (success) {
                    process.getDebugSession().setBreakpointVerified(breakpoint)
                    log("syncBreakpointToLldb", "断点 $key 已验证")
                } else {
                    process.getDebugSession().setBreakpointInvalid(breakpoint, "Failed to set breakpoint in LLDB")
                    log("syncBreakpointToLldb", "断点 $key 设置失败", "WARN")
                }
            }
        }
    }
    
    /**
     * 标记 LLDB 已就绪，并同步所有缓存的断点
     */
    fun onLldbReady() {
        logSeparator("onLldbReady", "LLDB 已就绪")
        logCallStack("onLldbReady")
        log("onLldbReady", "lldbReady=$lldbReady, pendingBreakpoints.size=${pendingBreakpoints.size}")
        
        lldbReady = true
        log("onLldbReady", "已设置 lldbReady=true")
        
        val breakpointsToSync = pendingBreakpoints.toList()
        pendingBreakpoints.clear()
        
        log("onLldbReady", "待同步断点数: ${breakpointsToSync.size}")
        
        for ((index, bp) in breakpointsToSync.withIndex()) {
            val pos = bp.sourcePosition
            if (pos == null) {
                log("onLldbReady", "断点 #$index sourcePosition 为 null, 跳过", "WARN")
                continue
            }
            
            val filePath = pos.file.path
            val line = bp.line + 1
            val key = "$filePath:$line"
            
            log("onLldbReady", "同步缓存断点 #$index: $key")
            syncBreakpointToLldb(bp, filePath, line, key)
        }
        
        log("onLldbReady", "onLldbReady 完成")
    }

    override fun unregisterBreakpoint(
        breakpoint: XLineBreakpoint<CppBreakpointProperties>,
        temporary: Boolean
    ) {
        logSeparator("unregisterBreakpoint", "注销断点")
        logCallStack("unregisterBreakpoint")
        
        val pos = breakpoint.sourcePosition ?: return
        val filePath = pos.file.path
        val line = breakpoint.line + 1
        val key = "$filePath:$line"
        
        log("unregisterBreakpoint", "key=$key, lldbReady=$lldbReady, temporary=$temporary")
        
        registeredBreakpoints.remove(key)
        pendingBreakpoints.remove(breakpoint)
        
        if (lldbReady) {
            val dapSession = process.getDapSession()
            dapSession.deleteBreakpoint(filePath, line) { response ->
                log("unregisterBreakpoint", "deleteBreakpoint 响应: $response")
            }
        }
    }
    
    /**
     * 获取已注册的断点
     */
    fun getRegisteredBreakpoints(): Map<String, XLineBreakpoint<CppBreakpointProperties>> {
        log("getRegisteredBreakpoints", "返回 ${registeredBreakpoints.size} 个断点")
        return registeredBreakpoints
    }
    
    /**
     * 根据文件和行号查找断点
     */
    fun findBreakpoint(filePath: String, line: Int): XLineBreakpoint<CppBreakpointProperties>? {
        log("findBreakpoint", "=== 查找断点 ===")
        log("findBreakpoint", "查找: $filePath:$line")
        log("findBreakpoint", "已注册断点: ${registeredBreakpoints.keys}")
        
        // 尝试完整路径
        val fullKey = "$filePath:$line"
        var bp = registeredBreakpoints[fullKey]
        if (bp != null) {
            log("findBreakpoint", "通过完整路径找到: $fullKey")
            return bp
        }
        
        // 尝试只用文件名匹配
        val fileName = filePath.substringAfterLast('/')
        for ((key, breakpoint) in registeredBreakpoints) {
            if (key.endsWith("/$fileName:$line") || key.endsWith(":$fileName:$line") || key == "$fileName:$line") {
                log("findBreakpoint", "通过文件名匹配找到: $key")
                return breakpoint
            }
        }
        
        // 遍历所有断点查找行号匹配
        for ((key, breakpoint) in registeredBreakpoints) {
            val parts = key.split(":")
            if (parts.size >= 2) {
                val keyLine = parts.last().toIntOrNull()
                val keyFile = key.substringBeforeLast(":")
                if (keyLine == line && (keyFile.endsWith(fileName) || fileName.contains(keyFile.substringAfterLast('/')))) {
                    log("findBreakpoint", "通过行号匹配找到: $key")
                    return breakpoint
                }
            }
        }
        
        log("findBreakpoint", "未找到匹配的断点", "WARN")
        return null
    }
}
