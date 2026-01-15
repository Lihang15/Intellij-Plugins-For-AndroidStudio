package org.jetbrains.plugins.template.debuger

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XLineBreakpointTypeBase

/**
 * LLDB 行断点类型
 * 用于 C/C++ 文件的断点支持
 */
class LLDBLineBreakpointType : XLineBreakpointTypeBase(
    "lldb-line",
    "LLDB Line Breakpoints",
    LLDBEditorsProvider()
) {
    
    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
        // 支持 .cpp, .c, .h, .hpp 文件
        val extension = file.extension?.lowercase()
        return extension in listOf("cpp", "c", "h", "hpp", "cc", "cxx")
    }
}
