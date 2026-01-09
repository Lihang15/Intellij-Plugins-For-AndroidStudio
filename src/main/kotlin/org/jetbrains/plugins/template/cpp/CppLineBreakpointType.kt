package org.jetbrains.plugins.template.cpp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XLineBreakpointType

/**
 * C++ 行断点类型
 * 用于在 .cpp/.c/.h/.hpp 文件中设置行断点
 */
class CppLineBreakpointType : XLineBreakpointType<CppBreakpointProperties>(
    "cpp-line-breakpoint",
    "C++ Line Breakpoint"
) {
    
    companion object {
        private val CPP_EXTENSIONS = setOf("cpp", "c", "cc", "cxx", "h", "hpp", "hxx", "m", "mm")
    }
    
    /**
     * 判断文件是否可以设置断点
     */
    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
        val extension = file.extension?.lowercase() ?: return false
        val canPut = extension in CPP_EXTENSIONS
        println("[CppLineBreakpointType] canPutAt: ${file.path}:$line, ext=$extension, result=$canPut")
        return canPut
    }
    
    /**
     * 创建断点属性
     */
    override fun createBreakpointProperties(file: VirtualFile, line: Int): CppBreakpointProperties {
        println("[CppLineBreakpointType] createBreakpointProperties: ${file.path}:$line")
        return CppBreakpointProperties()
    }
}
