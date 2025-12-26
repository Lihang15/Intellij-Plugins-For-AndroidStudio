package org.jetbrains.plugins.template.debuger

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.application.ApplicationManager


class StartLldbDapAction : AnAction() {

    private val log = Logger.getInstance(StartLldbDapAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {

        // 启动一个后台线程
        ApplicationManager.getApplication().executeOnPooledThread {
            DapClient.runStart()
        }


    }

}
