package org.jetbrains.plugins.template.cpp

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.template.debuger.DapClient

/**
 * MyMainApp 的 Debug Runner：
 * - 只对 Debug Executor 生效（Run 逻辑仍走默认 Runner）
 * - 复用现有的 MyMainCppRunProfileState 做编译和运行
 * - 同时在后台启动 lldb-dap（通过 DapClient）
 */
class MyMainCppDebugRunner : GenericProgramRunner<RunnerSettings>() {

    override fun getRunnerId(): String = "MyMainCppDebugRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is MyMainCppRunConfiguration
    }

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        // 先按正常方式执行（会调用 MyMainCppRunProfileState.startProcess -> 编译+运行）
        val executionResult = state.execute(environment.executor, this) ?: return null

        // 同时在后台启动 lldb-dap 做 DAP 通信示例
        ApplicationManager.getApplication().executeOnPooledThread {
            DapClient.runStart()
        }

        return RunContentBuilder(executionResult, environment).showRunContent(environment.contentToReuse)
    }
}
