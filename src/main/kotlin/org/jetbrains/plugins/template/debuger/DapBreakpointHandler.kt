package org.jetbrains.plugins.template.debuger

import com.intellij.xdebugger.breakpoints.*

/**
 * DAP 行断点处理器
 *
 * Kotlin 下必须使用强制 cast（这是 IntelliJ API 的历史问题）
 */
@Suppress("UNCHECKED_CAST")
class DapBreakpointHandler(
    private val process: DapDebugProcess
) : XBreakpointHandler<XLineBreakpoint<*>>(
    XLineBreakpointType::class.java
            as Class<out XBreakpointType<XLineBreakpoint<*>, *>>
) {

    override fun registerBreakpoint(breakpoint: XLineBreakpoint<*>) {
        val pos = breakpoint.sourcePosition ?: return
        println(
            "[DapBreakpointHandler] Register breakpoint: " +
                    "${pos.file.path}:${breakpoint.line}"
        )
    }

    override fun unregisterBreakpoint(
        breakpoint: XLineBreakpoint<*>,
        temporary: Boolean
    ) {
        val pos = breakpoint.sourcePosition ?: return
        println(
            "[DapBreakpointHandler] Unregister breakpoint: " +
                    "${pos.file.path}:${breakpoint.line}"
        )
    }
}
