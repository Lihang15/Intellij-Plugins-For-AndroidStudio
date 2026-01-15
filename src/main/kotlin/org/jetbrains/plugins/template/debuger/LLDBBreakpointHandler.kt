package org.jetbrains.plugins.template.debuger

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.xdebugger.breakpoints.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * LLDB 断点处理器 - 重构版
 * 参考 Flutter 的 DartVmServiceBreakpointHandler 设计
 * 
 * 关键改进：
 * 1. 断点缓存机制 - 在 LLDB 就绪前缓存断点
 * 2. 统一同步 - onLldbReady() 时批量同步
 * 3. 线程安全 - 使用 ConcurrentHashMap 和 CopyOnWriteArrayList
 * 4. 使用反射获取 CidrLineBreakpointType - 兼容 IntelliJ 的 C/C++ 插件
 */
class LLDBBreakpointHandler(
    private val process: LLDBDebugProcess
) : XBreakpointHandler<XLineBreakpoint<XBreakpointProperties<*>>>(
    getCidrLineBreakpointTypeClass()
) {
    
    companion object {
        private val LOG = Logger.getInstance(LLDBBreakpointHandler::class.java)
        
        /**
         * 通过反射获取 CidrLineBreakpointType 类
         * 这样可以避免编译时依赖
         */
        @Suppress("UNCHECKED_CAST")
        private fun getCidrLineBreakpointTypeClass(): Class<out XBreakpointType<XLineBreakpoint<XBreakpointProperties<*>>, XBreakpointProperties<*>>> {
            return try {
                println("[LLDBBreakpointHandler] 尝试查找 CidrLineBreakpointType...")
                
                // 方法 1：直接通过类名查找
                val cidrClass = try {
                    Class.forName("com.jetbrains.cidr.execution.debugger.breakpoints.CidrLineBreakpointType")
                } catch (e: ClassNotFoundException) {
                    println("[LLDBBreakpointHandler] 方法1失败: ${e.message}")
                    null
                }
                
                if (cidrClass != null) {
                    println("[LLDBBreakpointHandler] ✓ 方法1成功：找到 CidrLineBreakpointType 类")
                    return cidrClass as Class<out XBreakpointType<XLineBreakpoint<XBreakpointProperties<*>>, XBreakpointProperties<*>>>
                }
                
                // 方法 2：从扩展点查找
                println("[LLDBBreakpointHandler] 尝试方法2：从扩展点查找...")
                val allTypes = XBreakpointType.EXTENSION_POINT_NAME.extensionList
                println("[LLDBBreakpointHandler] 找到 ${allTypes.size} 个断点类型")
                
                for (type in allTypes) {
                    val typeName = type.javaClass.name
                    println("[LLDBBreakpointHandler]   - $typeName (id=${type.id})")
                    
                    if (typeName.contains("CidrLineBreakpointType")) {
                        println("[LLDBBreakpointHandler] ✓ 方法2成功：找到 CidrLineBreakpointType")
                        return type.javaClass as Class<out XBreakpointType<XLineBreakpoint<XBreakpointProperties<*>>, XBreakpointProperties<*>>>
                    }
                }
                
                println("[LLDBBreakpointHandler] ✗ 方法2失败：未找到 CidrLineBreakpointType")
                println("[LLDBBreakpointHandler] 回退到 LLDBLineBreakpointType")
                LLDBLineBreakpointType::class.java as Class<out XBreakpointType<XLineBreakpoint<XBreakpointProperties<*>>, XBreakpointProperties<*>>>
                
            } catch (e: Exception) {
                println("[LLDBBreakpointHandler] ✗ 异常: ${e.message}")
                e.printStackTrace()
                LOG.warn("Failed to get CidrLineBreakpointType, falling back to LLDBLineBreakpointType", e)
                LLDBLineBreakpointType::class.java as Class<out XBreakpointType<XLineBreakpoint<XBreakpointProperties<*>>, XBreakpointProperties<*>>>
            }
        }
    }
    
    // 已注册的断点（key -> breakpoint）
    private val registeredBreakpoints = ConcurrentHashMap<String, XLineBreakpoint<XBreakpointProperties<*>>>()
    
    // 待同步的断点（LLDB 就绪前缓存）
    private val pendingBreakpoints = CopyOnWriteArrayList<XLineBreakpoint<XBreakpointProperties<*>>>()
    
    // LLDB 是否就绪
    @Volatile
    private var lldbReady = false
    
    init {
        println("\n========== [LLDBBreakpointHandler.init] 开始 ==========")
        LOG.info("=== LLDBBreakpointHandler 构造函数 ===")
        LOG.info("支持的断点类型: ${breakpointTypeClass.name}")
        LOG.info("Handler 实例: ${this.javaClass.name}@${System.identityHashCode(this)}")
        println("[LLDBBreakpointHandler.init] 支持的断点类型: ${breakpointTypeClass.name}")
        println("[LLDBBreakpointHandler.init] Handler 实例: ${this.javaClass.name}@${System.identityHashCode(this)}")
        println("========== [LLDBBreakpointHandler.init] 结束 ==========\n")
    }

    override fun registerBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>) {
        println("\n========== [LLDBBreakpointHandler.registerBreakpoint] 被调用 ==========")
        LOG.info("=== registerBreakpoint 被调用 ===")
        LOG.info("调用堆栈: ${Thread.currentThread().stackTrace.take(5).joinToString("\n  ")}")
        
        println("[registerBreakpoint] 断点类型: ${breakpoint.type.javaClass.name}")
        println("[registerBreakpoint] 断点类型 ID: ${breakpoint.type.id}")
        
        val pos = breakpoint.sourcePosition
        if (pos == null) {
            LOG.warn("sourcePosition 为 null, 跳过")
            println("[registerBreakpoint] ✗ sourcePosition 为 null, 跳过")
            println("========== [LLDBBreakpointHandler.registerBreakpoint] 结束 ==========\n")
            return
        }
        
        val filePath = pos.file.path
        val fileName = pos.file.name
        val line = breakpoint.line + 1  // LLDB 是 1-based
        val key = "$filePath:$line"
        
        LOG.info("断点信息:")
        LOG.info("  文件路径: $filePath")
        LOG.info("  文件名: $fileName")
        LOG.info("  行号: $line (IDE行号: ${breakpoint.line})")
        LOG.info("  key: $key")
        LOG.info("  lldbReady=$lldbReady")
        
        println("[registerBreakpoint] 文件路径: $filePath")
        println("[registerBreakpoint] 文件名: $fileName")
        println("[registerBreakpoint] 行号: $line (IDE行号: ${breakpoint.line})")
        println("[registerBreakpoint] key: $key")
        println("[registerBreakpoint] lldbReady=$lldbReady")
        
        registeredBreakpoints[key] = breakpoint
        LOG.info("已添加到 registeredBreakpoints, 当前总数: ${registeredBreakpoints.size}")
        println("[registerBreakpoint] 已添加到 registeredBreakpoints, 当前总数: ${registeredBreakpoints.size}")
        
        if (!lldbReady) {
            LOG.info("LLDB 未就绪, 缓存断点: $key")
            println("[registerBreakpoint] LLDB 未就绪, 缓存断点: $key")
            pendingBreakpoints.add(breakpoint)
            LOG.info("缓存后 pendingBreakpoints.size=${pendingBreakpoints.size}")
            println("[registerBreakpoint] 缓存后 pendingBreakpoints.size=${pendingBreakpoints.size}")
            println("========== [LLDBBreakpointHandler.registerBreakpoint] 结束 ==========\n")
            return
        }
        
        LOG.info("LLDB 已就绪, 直接同步断点")
        println("[registerBreakpoint] LLDB 已就绪, 直接同步断点")
        syncBreakpointToLldb(breakpoint, filePath, line, key)
        println("========== [LLDBBreakpointHandler.registerBreakpoint] 结束 ==========\n")
    }
    
    /**
     * 同步单个断点到 LLDB
     */
    private fun syncBreakpointToLldb(
        breakpoint: XLineBreakpoint<XBreakpointProperties<*>>,
        filePath: String,
        line: Int,
        key: String,
        callback: () -> Unit = {}
    ) {
        LOG.info("=== 同步断点到 LLDB ===")
        LOG.info("key=$key, filePath=$filePath, line=$line")
        
        val serviceWrapper = process.getServiceWrapper()
        serviceWrapper.setBreakpoint(filePath, line) { success ->
            LOG.info("设置结果: ${if (success) "成功" else "失败"}")
            
            ApplicationManager.getApplication().invokeLater {
                if (success) {
                    process.getDebugSession().setBreakpointVerified(breakpoint)
                    LOG.info("断点 $key 已验证")
                } else {
                    process.getDebugSession().setBreakpointInvalid(breakpoint, "Failed to set breakpoint in LLDB")
                    LOG.warn("断点 $key 设置失败")
                }
                callback()
            }
        }
    }
    
    /**
     * 标记 LLDB 已就绪
     */
    fun onLldbReady() {
        LOG.info("=== LLDB 已就绪 ===")
        LOG.info("pendingBreakpoints.size=${pendingBreakpoints.size}")
        
        lldbReady = true
        LOG.info("已设置 lldbReady=true")
    }
    
    /**
     * 同步所有缓存的断点
     */
    fun syncAllBreakpoints(callback: () -> Unit = {}) {
        LOG.info("=== 同步所有断点 ===")
        
        val breakpointsToSync = pendingBreakpoints.toList()
        pendingBreakpoints.clear()
        
        LOG.info("待同步断点数: ${breakpointsToSync.size}")
        
        if (breakpointsToSync.isEmpty()) {
            callback()
            return
        }
        
        var remaining = breakpointsToSync.size
        
        for ((index, bp) in breakpointsToSync.withIndex()) {
            val pos = bp.sourcePosition
            if (pos == null) {
                LOG.warn("断点 #$index sourcePosition 为 null, 跳过")
                remaining--
                if (remaining == 0) callback()
                continue
            }
            
            val filePath = pos.file.path
            val line = bp.line + 1
            val key = "$filePath:$line"
            
            LOG.info("同步缓存断点 #$index: $key")
            syncBreakpointToLldb(bp, filePath, line, key) {
                remaining--
                LOG.info("剩余待同步: $remaining")
                if (remaining == 0) {
                    LOG.info("所有断点同步完成")
                    callback()
                }
            }
        }
    }

    override fun unregisterBreakpoint(
        breakpoint: XLineBreakpoint<XBreakpointProperties<*>>,
        temporary: Boolean
    ) {
        LOG.info("=== 注销断点 ===")
        
        val pos = breakpoint.sourcePosition ?: return
        val filePath = pos.file.path
        val line = breakpoint.line + 1
        val key = "$filePath:$line"
        
        LOG.info("key=$key, lldbReady=$lldbReady, temporary=$temporary")
        
        registeredBreakpoints.remove(key)
        pendingBreakpoints.remove(breakpoint)
        
        if (lldbReady) {
            val serviceWrapper = process.getServiceWrapper()
            serviceWrapper.removeBreakpoint(filePath, line) {
                LOG.info("断点已从 LLDB 删除: $key")
            }
        }
    }
    
    /**
     * 获取已注册的断点
     */
    fun getXBreakpoints(): Set<XLineBreakpoint<XBreakpointProperties<*>>> {
        LOG.info("返回 ${registeredBreakpoints.size} 个断点")
        return registeredBreakpoints.values.toSet()
    }
    
    /**
     * 根据文件和行号查找断点
     */
    fun findBreakpoint(filePath: String, line: Int): XLineBreakpoint<XBreakpointProperties<*>>? {
        LOG.info("=== 查找断点 ===")
        LOG.info("查找: $filePath:$line")
        
        // 尝试完整路径
        val fullKey = "$filePath:$line"
        var bp = registeredBreakpoints[fullKey]
        if (bp != null) {
            LOG.info("通过完整路径找到: $fullKey")
            return bp
        }
        
        // 尝试只用文件名匹配
        val fileName = filePath.substringAfterLast('/')
        for ((key, breakpoint) in registeredBreakpoints) {
            if (key.endsWith("/$fileName:$line") || key.endsWith(":$fileName:$line") || key == "$fileName:$line") {
                LOG.info("通过文件名匹配找到: $key")
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
                    LOG.info("通过行号匹配找到: $key")
                    return breakpoint
                }
            }
        }
        
        LOG.warn("未找到匹配的断点")
        return null
    }
}
