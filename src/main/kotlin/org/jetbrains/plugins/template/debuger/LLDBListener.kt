package org.jetbrains.plugins.template.debuger

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger

/**
 * LLDB 事件监听器
 * 参考 Flutter 的 DartVmServiceListener 设计
 * 
 * 职责：
 * 1. 监听 LLDB 输出流
 * 2. 解析停止事件（断点、单步、异常）
 * 3. 在获取完整堆栈信息后触发 UI 更新
 */
class LLDBListener(
    private val debugProcess: LLDBDebugProcess,
    private val serviceWrapper: LLDBServiceWrapper
) {
    
    companion object {
        private val LOG = Logger.getInstance(LLDBListener::class.java)
    }
    
    // 回调函数
    var onStopped: ((threadId: Int, reason: String) -> Unit)? = null
    var onOutput: ((message: String) -> Unit)? = null
    var onTerminated: (() -> Unit)? = null
    
    // 停止事件检测状态
    private var lastStopDetected = false
    
    /**
     * 检查停止事件
     * 
     * LLDB 输出格式（两行）：
     * 1. "Process 91141 stopped"
     * 2. "* thread #1, queue = 'com.apple.main-thread', stop reason = breakpoint 2.1"
     * 
     * 关键：停止事件必须立即异步触发，不能等待命令回调
     */
    fun checkStopEvents(line: String) {
        when {
            // 第一步：检测到 "Process stopped"
            line.contains("Process") && line.contains("stopped") -> {
                LOG.info(">>> 检测到 Process stopped，等待线程信息...")
                lastStopDetected = true
            }
            
            // 第二步：在 lastStopDetected 之后检测到线程信息
            lastStopDetected && line.startsWith("*") && line.contains("thread #") -> {
                LOG.info(">>> 检测到线程信息，触发停止事件 <<<")
                lastStopDetected = false
                
                val threadId = extractThreadId(line)
                val reason = extractStopReason(line)
                LOG.info("停止事件: threadId=$threadId, reason=$reason")
                
                // 关键：立即在新线程中触发，不阻塞 LLDB 输出
                Thread {
                    handleStopEvent(threadId, reason)
                }.start()
            }
            
            // 退出事件
            line.contains("Process") && line.contains("exited") -> {
                LOG.info(">>> 检测到退出事件 <<<")
                lastStopDetected = false
                Thread {
                    onTerminated?.invoke()
                }.start()
            }
            
            // 恢复运行
            line.contains("Process") && line.contains("resuming") -> {
                LOG.info("程序恢复运行")
                lastStopDetected = false
            }
            
            // 断点设置确认
            line.contains("Breakpoint") && line.contains("where =") -> {
                LOG.info("断点设置确认: $line")
                onOutput?.invoke(line)
            }
            
            // 其他输出
            else -> {
                if (!line.startsWith("(lldb)") && line.isNotBlank()) {
                    onOutput?.invoke(line)
                }
            }
        }
    }
    
    /**
     * 处理停止事件
     * 
     * 关键流程：
     * 1. 先获取完整堆栈信息
     * 2. 再在 EDT 线程更新 UI
     */
    private fun handleStopEvent(threadId: Int, reason: String) {
        LOG.info("=== 处理停止事件 ===")
        LOG.info("threadId=$threadId, reason=$reason")
        
        // 更新当前线程 ID
        serviceWrapper.setCurrentThreadId(threadId)
        
        // 关键：必须先获取完整堆栈信息，再调用 positionReached
        serviceWrapper.getStackTrace(threadId) { stackFrames ->
            LOG.info("获取到 ${stackFrames.size} 个栈帧")
            
            if (stackFrames.isEmpty()) {
                LOG.warn("栈帧为空，无法同步 UI")
                return@getStackTrace
            }
            
            // 在 EDT 线程执行 UI 更新
            ApplicationManager.getApplication().invokeLater {
                try {
                    LOG.info("在 EDT 线程调用 onStopped 回调")
                    onStopped?.invoke(threadId, reason)
                    LOG.info("onStopped 回调完成")
                } catch (e: Exception) {
                    LOG.error("onStopped 回调异常", e)
                }
            }
        }
    }
    
    /**
     * 从输出行中提取线程 ID
     */
    private fun extractThreadId(line: String): Int {
        val pattern = "thread #(\\d+)".toRegex()
        val match = pattern.find(line)
        val threadId = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
        LOG.info("从 '$line' 提取 threadId=$threadId")
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
            line.contains("exception") -> "exception"
            else -> "unknown"
        }
        LOG.info("从 '$line' 提取 reason=$reason")
        return reason
    }
}
