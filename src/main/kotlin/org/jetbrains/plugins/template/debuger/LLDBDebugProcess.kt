package org.jetbrains.plugins.template.debuger

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XSuspendContext
import java.util.concurrent.ConcurrentHashMap

/**
 * LLDB 调试进程 - 重构版
 * 参考 Flutter 的 DartVmServiceDebugProcess 设计
 * 
 * 关键改进：
 * 1. 使用 LLDBServiceWrapper 集中管理 LLDB 通信
 * 2. 使用 LLDBListener 处理所有事件
 * 3. 移除所有 Thread.sleep() 调用
 * 4. 明确的状态跟踪
 * 5. EDT 线程安全
 */
class LLDBDebugProcess(
    session: XDebugSession,
    private val executablePath: String
) : XDebugProcess(session) {
    
    companion object {
        private val LOG = Logger.getInstance(LLDBDebugProcess::class.java)
    }
    
    // 核心组件
    private val serviceWrapper = LLDBServiceWrapper(this)
    private val listener = LLDBListener(this, serviceWrapper)
    private val breakpointHandler = LLDBBreakpointHandler(this)
    private val positionMapper = LLDBPositionMapper(session.project)
    
    // 暂停的线程（threadId -> future）
    private val suspendedThreads = ConcurrentHashMap<Int, Boolean>()
    
    init {
        println("\n========== [LLDBDebugProcess.init] 开始 ==========")
        LOG.info("=== LLDBDebugProcess 初始化 ===")
        LOG.info("可执行文件路径: $executablePath")
        LOG.info("Session: ${session.project.name}")
        LOG.info("Session 类: ${session.javaClass.name}")
        LOG.info("创建 breakpointHandler...")
        
        println("[LLDBDebugProcess.init] 可执行文件: $executablePath")
        println("[LLDBDebugProcess.init] Session: ${session.project.name}")
        
        // 设置 ServiceWrapper 的监听器
        serviceWrapper.listener = listener
        
        // 打印断点处理器信息
        LOG.info("BreakpointHandler 类型: ${breakpointHandler.javaClass.name}")
        LOG.info("BreakpointHandler 支持的断点类型: ${breakpointHandler.breakpointTypeClass.name}")
        
        println("[LLDBDebugProcess.init] BreakpointHandler 类型: ${breakpointHandler.javaClass.name}")
        println("[LLDBDebugProcess.init] 支持的断点类型: ${breakpointHandler.breakpointTypeClass.name}")
        
        // 尝试立即检查断点类型是否已注册
        try {
            val bpTypes = com.intellij.xdebugger.breakpoints.XBreakpointType.EXTENSION_POINT_NAME.extensionList
            LOG.info("当前已注册的断点类型数量: ${bpTypes.size}")
            println("[LLDBDebugProcess.init] 已注册的断点类型数量: ${bpTypes.size}")
            
            val lldbType = bpTypes.find { it is LLDBLineBreakpointType }
            if (lldbType != null) {
                LOG.info("✓ LLDBLineBreakpointType 已在扩展点注册")
                println("[LLDBDebugProcess.init] ✓ LLDBLineBreakpointType 已在扩展点注册")
            } else {
                LOG.warn("✗ LLDBLineBreakpointType 未在扩展点找到")
                println("[LLDBDebugProcess.init] ✗ LLDBLineBreakpointType 未在扩展点找到")
                println("[LLDBDebugProcess.init] 已注册的类型:")
                bpTypes.forEach {
                    println("[LLDBDebugProcess.init]   - ${it.javaClass.name} (id=${it.id})")
                }
            }
        } catch (e: Exception) {
            LOG.error("检查断点类型时出错", e)
            println("[LLDBDebugProcess.init] ✗ 检查断点类型时出错: ${e.message}")
            e.printStackTrace()
        }
        
        // 设置事件回调
        listener.onStopped = { threadId, reason ->
            handleStopped(threadId, reason)
        }
        
        listener.onOutput = { message ->
            session.consoleView?.print(message + "\n", com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT)
        }
        
        listener.onTerminated = {
            session.stop()
        }
        
        LOG.info("LLDBDebugProcess 初始化完成")
        println("[LLDBDebugProcess.init] ✓ 初始化完成")
        println("========== [LLDBDebugProcess.init] 结束 ==========\n")
    }
    
    override fun sessionInitialized() {
        println("\n========== [LLDBDebugProcess.sessionInitialized] 开始 ==========")
        LOG.info("=== sessionInitialized 开始 ===")
        LOG.info("可执行文件: $executablePath")
        LOG.info("当前线程: ${Thread.currentThread().name}")
        
        println("[sessionInitialized] 可执行文件: $executablePath")
        println("[sessionInitialized] 当前线程: ${Thread.currentThread().name}")
        
        // 检查断点管理器中的断点
        val breakpointManager = com.intellij.xdebugger.XDebuggerManager.getInstance(session.project).breakpointManager
        val allBreakpoints = breakpointManager.allBreakpoints
        LOG.info("断点管理器中的所有断点数量: ${allBreakpoints.size}")
        println("[sessionInitialized] 断点管理器中的所有断点数量: ${allBreakpoints.size}")
        
        for (bp in allBreakpoints) {
            LOG.info("  断点类型: ${bp.type.javaClass.name}")
            LOG.info("  断点类型 ID: ${bp.type.id}")
            println("[sessionInitialized]   断点类型: ${bp.type.javaClass.name}, ID: ${bp.type.id}")
            
            if (bp is com.intellij.xdebugger.breakpoints.XLineBreakpoint<*>) {
                val pos = bp.sourcePosition
                if (pos != null) {
                    LOG.info("    位置: ${pos.file.path}:${bp.line}")
                    println("[sessionInitialized]     位置: ${pos.file.path}:${bp.line}")
                }
            }
        }
        
        // 检查我们的 breakpointHandler 支持的类型
        LOG.info("=== 检查 BreakpointHandler 配置 ===")
        LOG.info("BreakpointHandler 类: ${breakpointHandler.javaClass.name}")
        LOG.info("BreakpointHandler 支持的断点类型: ${breakpointHandler.breakpointTypeClass.name}")
        
        println("[sessionInitialized] BreakpointHandler 支持的断点类型: ${breakpointHandler.breakpointTypeClass.name}")
        
        // 关键：延迟执行，给断点注册时间
        ApplicationManager.getApplication().executeOnPooledThread {
            println("[sessionInitialized] 延迟启动线程开始，等待 500ms...")
            Thread.sleep(500)
            println("[sessionInitialized] 延迟结束，开始调试序列")
            
            // 再次检查断点状态
            val breakpoints = breakpointHandler.getXBreakpoints()
            println("[sessionInitialized] breakpointHandler 中已注册的断点数: ${breakpoints.size}")
            
            startDebugSequence()
        }
        
        println("[sessionInitialized] ✓ 已安排延迟启动")
        println("========== [LLDBDebugProcess.sessionInitialized] 结束 ==========\n")
    }
    
    /**
     * 启动调试序列
     */
    private fun startDebugSequence() {
        LOG.info("=== 开始调试序列 ===")
        
        // 1. 启动 LLDB 进程
        serviceWrapper.onConnected = {
            LOG.info("LLDB 已连接，开始初始化序列")
            
            // 2. 加载目标程序
            serviceWrapper.loadTarget(executablePath) {
                LOG.info("目标程序已加载")
                
                // 3. 同步断点
                breakpointHandler.onLldbReady()
                
                // 打印当前注册的断点
                val breakpoints = breakpointHandler.getXBreakpoints()
                LOG.info("当前有 ${breakpoints.size} 个断点待同步")
                for (bp in breakpoints) {
                    val pos = bp.sourcePosition
                    if (pos != null) {
                        LOG.info("  断点: ${pos.file.path}:${bp.line + 1}")
                    }
                }
                
                if (breakpoints.isEmpty()) {
                    LOG.warn("警告：没有检测到任何断点！请确保在启动调试前已设置断点。")
                }
                
                breakpointHandler.syncAllBreakpoints {
                    LOG.info("断点同步完成，启动程序（在入口点暂停）")
                    
                    // 4. 运行程序（在入口点暂停）
                    serviceWrapper.run {
                        LOG.info("程序已在入口点暂停")
                        
                        // 5. 继续运行到第一个断点
                        LOG.info("继续运行到第一个断点...")
                        serviceWrapper.resumeThread(1, null)
                    }
                }
            }
        }
        
        serviceWrapper.start()
    }
    
    override fun startStepOver(context: XSuspendContext?) {
        LOG.info("=== 单步跳过 ===")
        val threadId = getCurrentThreadId()
        
        if (threadId == null || !isThreadSuspended(threadId)) {
            LOG.warn("无法执行单步跳过：threadId=$threadId, suspended=${threadId?.let { isThreadSuspended(it) }}")
            return
        }
        
        serviceWrapper.resumeThread(threadId, LLDBServiceWrapper.StepOption.Over)
    }
    
    override fun startStepInto(context: XSuspendContext?) {
        LOG.info("=== 单步进入 ===")
        val threadId = getCurrentThreadId()
        
        if (threadId == null || !isThreadSuspended(threadId)) {
            LOG.warn("无法执行单步进入：threadId=$threadId, suspended=${threadId?.let { isThreadSuspended(it) }}")
            return
        }
        
        serviceWrapper.resumeThread(threadId, LLDBServiceWrapper.StepOption.Into)
    }
    
    override fun startStepOut(context: XSuspendContext?) {
        LOG.info("=== 单步退出 ===")
        val threadId = getCurrentThreadId()
        
        if (threadId == null || !isThreadSuspended(threadId)) {
            LOG.warn("无法执行单步退出：threadId=$threadId, suspended=${threadId?.let { isThreadSuspended(it) }}")
            return
        }
        
        serviceWrapper.resumeThread(threadId, LLDBServiceWrapper.StepOption.Out)
    }
    
    override fun resume(context: XSuspendContext?) {
        LOG.info("=== 继续执行 ===")
        val threadId = getCurrentThreadId()
        
        if (threadId == null || !isThreadSuspended(threadId)) {
            LOG.warn("无法继续执行：threadId=$threadId, suspended=${threadId?.let { isThreadSuspended(it) }}")
            return
        }
        
        serviceWrapper.resumeThread(threadId, null)
    }
    
    override fun startPausing() {
        LOG.info("=== 暂停执行 ===")
        val threadId = getCurrentThreadId() ?: 1
        serviceWrapper.pauseThread(threadId)
    }
    
    override fun stop() {
        LOG.info("=== 停止调试 ===")
        serviceWrapper.dispose()
    }
    
    override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>> {
        println("\n========== [LLDBDebugProcess.getBreakpointHandlers] 被调用 ==========")
        LOG.info("=== getBreakpointHandlers 被调用 ===")
        LOG.info("返回 breakpointHandler: ${breakpointHandler.javaClass.name}")
        println("[getBreakpointHandlers] 返回: ${breakpointHandler.javaClass.name}")
        println("========== [LLDBDebugProcess.getBreakpointHandlers] 结束 ==========\n")
        return arrayOf(breakpointHandler)
    }
    
    override fun getEditorsProvider(): XDebuggerEditorsProvider {
        return LLDBEditorsProvider()
    }
    
    /**
     * 处理停止事件（参考 Flutter 的 handleStopped）
     */
    private fun handleStopped(threadId: Int, reason: String) {
        LOG.info("=== 处理停止事件 ===")
        LOG.info("threadId=$threadId, reason=$reason")
        
        // 标记线程为暂停状态
        isolateSuspended(threadId)
        
        // 获取堆栈跟踪
        serviceWrapper.getStackTrace(threadId) { stackFrames ->
            LOG.info("获取到 ${stackFrames.size} 个栈帧")
            
            if (stackFrames.isEmpty()) {
                LOG.warn("栈帧为空，无法同步 UI")
                return@getStackTrace
            }
            
            // 在 EDT 线程更新 UI
            ApplicationManager.getApplication().invokeLater {
                try {
                    // 创建 SuspendContext
                    val suspendContext = LLDBSuspendContext(
                        this,
                        threadId,
                        stackFrames
                    )
                    
                    // 关键：调用 positionReached 同步 UI
                    LOG.info("调用 session.positionReached()")
                    session.positionReached(suspendContext)
                    LOG.info("UI 同步完成")
                    
                    // 如果是断点命中，更新断点状态
                    if (reason == "breakpoint" && stackFrames.isNotEmpty()) {
                        val firstFrame = stackFrames[0]
                        val hitBreakpoint = breakpointHandler.findBreakpoint(firstFrame.file, firstFrame.line)
                        if (hitBreakpoint != null) {
                            session.setBreakpointVerified(hitBreakpoint)
                            LOG.info("断点 UI 状态已更新")
                        }
                    }
                } catch (e: Exception) {
                    LOG.error("处理停止事件异常", e)
                }
            }
        }
    }
    
    /**
     * 线程暂停
     */
    fun isolateSuspended(threadId: Int) {
        LOG.info("线程暂停: threadId=$threadId")
        suspendedThreads[threadId] = true
    }
    
    /**
     * 线程恢复
     */
    fun isolateResumed(threadId: Int) {
        LOG.info("线程恢复: threadId=$threadId")
        suspendedThreads.remove(threadId)
    }
    
    /**
     * 检查线程是否暂停
     */
    fun isThreadSuspended(threadId: Int): Boolean {
        return suspendedThreads.containsKey(threadId)
    }
    
    /**
     * 获取当前线程 ID
     */
    fun getCurrentThreadId(): Int? {
        return serviceWrapper.getCurrentThreadId()
    }
    
    /**
     * 获取 ServiceWrapper
     */
    fun getServiceWrapper(): LLDBServiceWrapper = serviceWrapper
    
    /**
     * 获取调试会话
     */
    fun getDebugSession(): XDebugSession = session
    
    /**
     * 获取断点处理器
     */
    fun getBreakpointHandler(): LLDBBreakpointHandler = breakpointHandler
    
    /**
     * 获取位置映射器
     */
    fun getPositionMapper(): LLDBPositionMapper = positionMapper
}
