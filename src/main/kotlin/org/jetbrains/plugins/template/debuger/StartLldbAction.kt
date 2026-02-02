package org.jetbrains.plugins.template.debuger

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages

/**
 * 用于测试 LLDB 调试功能的 Action
 * 当前已集成到 HarmonyDebugRunner，此 Action 仅作为预留
 */
class StartLldbAction : AnAction() {

    private val log = Logger.getInstance(StartLldbAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        Messages.showInfoMessage(
            e.project,
            "请使用 Run Configuration 启动 C++ 调试",
            "LLDB 调试器"
        )
    }
}
