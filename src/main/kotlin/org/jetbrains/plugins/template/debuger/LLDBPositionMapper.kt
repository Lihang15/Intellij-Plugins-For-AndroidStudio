package org.jetbrains.plugins.template.debuger

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition

/**
 * LLDB 位置映射器
 * 参考 Flutter 的位置映射设计
 * 
 * 职责：
 * 1. IDE 路径 → LLDB 路径转换（设置断点时）
 * 2. LLDB 路径 → IDE 位置转换（停止事件时）
 * 3. 行号转换：IDE (0-based) ↔ LLDB (1-based)
 */
class LLDBPositionMapper(private val project: Project) {
    
    companion object {
        private val LOG = Logger.getInstance(LLDBPositionMapper::class.java)
    }
    
    /**
     * 获取断点路径（IDE → LLDB）
     * 用于设置断点时
     */
    fun getBreakpointPath(sourcePosition: XSourcePosition): String {
        val file = sourcePosition.file
        val path = file.path
        
        LOG.info("IDE 路径: $path")
        
        // LLDB 需要绝对路径
        return path
    }
    
    /**
     * 获取断点行号（IDE → LLDB）
     * IDE 是 0-based，LLDB 是 1-based
     */
    fun getBreakpointLine(sourcePosition: XSourcePosition): Int {
        val ideLine = sourcePosition.line
        val lldbLine = ideLine + 1
        
        LOG.info("IDE 行号: $ideLine → LLDB 行号: $lldbLine")
        return lldbLine
    }
    
    /**
     * 解析源位置（LLDB → IDE）
     * 用于停止事件时
     * 
     * @param filePath LLDB 返回的文件路径（可能是绝对路径或相对路径）
     * @param line LLDB 行号（1-based）
     * @return IDE 源位置（0-based）
     */
    fun parseSourcePosition(filePath: String, line: Int): XSourcePosition? {
        LOG.info("=== 解析源位置 ===")
        LOG.info("LLDB 路径: $filePath, LLDB 行号: $line")
        
        val file = findVirtualFile(filePath)
        if (file == null) {
            LOG.warn("找不到文件: $filePath")
            return null
        }
        
        // LLDB 是 1-based，IDE 是 0-based
        val ideLine = line - 1
        LOG.info("IDE 行号: $ideLine")
        
        val position = XDebuggerUtil.getInstance().createPosition(file, ideLine)
        LOG.info("位置创建成功: ${file.path}:$ideLine")
        
        return position
    }
    
    /**
     * 查找虚拟文件
     * 支持绝对路径和相对路径
     */
    fun findVirtualFile(filePath: String): VirtualFile? {
        LOG.info("查找文件: $filePath")
        
        // 1. 尝试直接路径
        var file = LocalFileSystem.getInstance().findFileByPath(filePath)
        if (file != null) {
            LOG.info("通过直接路径找到: ${file.path}")
            return file
        }
        
        // 2. 尝试刷新后查找
        LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
        file = LocalFileSystem.getInstance().findFileByPath(filePath)
        if (file != null) {
            LOG.info("通过刷新后找到: ${file.path}")
            return file
        }
        
        // 3. 尝试在项目中查找（相对路径）
        val fileName = filePath.substringAfterLast('/')
        val projectBasePath = project.basePath
        
        if (projectBasePath != null) {
            val possiblePaths = listOf(
                "$projectBasePath/$fileName",
                "$projectBasePath/$filePath",  // 可能是相对路径
                "$projectBasePath/src/$fileName",
                "$projectBasePath/src/main/$fileName"
            )
            
            for (possiblePath in possiblePaths) {
                LOG.info("尝试路径: $possiblePath")
                file = LocalFileSystem.getInstance().findFileByPath(possiblePath)
                if (file != null) {
                    LOG.info("找到文件: ${file.path}")
                    return file
                }
            }
        }
        
        LOG.warn("找不到文件: $filePath")
        return null
    }
}
