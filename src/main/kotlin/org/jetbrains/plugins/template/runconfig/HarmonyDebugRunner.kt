package org.jetbrains.plugins.template.runconfig

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import org.jetbrains.plugins.template.debuger.LLDBDebugProcess

/**
 * HarmonyApp 的 Debug Runner：
 * - 只对 Debug Executor 生效（Run 逻辑仍走默认 Runner）
 * - 启动 XDebugSession + LLDBDebugProcess，连接 LLDB 调试器
 * - 实际的应用构建和部署由 runOhosApp-Mac.sh 脚本完成
 */
class HarmonyDebugRunner : GenericProgramRunner<RunnerSettings>() {

    override fun getRunnerId(): String = "HarmonyDebugRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is HarmonyRunConfiguration
    }

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val configuration = environment.runProfile as? HarmonyRunConfiguration
            ?: throw ExecutionException("Invalid configuration")
        
        val project = configuration.project
        
        // 启动 XDebugSession
        val debuggerManager = XDebuggerManager.getInstance(project)
        val debugSession = debuggerManager.startSession(
            environment,
            object : com.intellij.xdebugger.XDebugProcessStarter() {
                override fun start(session: XDebugSession): XDebugProcess {
                    // TODO: 确定正确的可执行文件路径
                    // 由于实际的应用构建和部署由 runOhosApp-Mac.sh 脚本完成
                    // 需要根据脚本部署后的实际路径配置
                    val executablePath = ""  // 需要根据实际部署路径配置
                    return LLDBDebugProcess(session, executablePath)
                }
            }
        )
        
        return debugSession.runContentDescriptor
    }
    

}
