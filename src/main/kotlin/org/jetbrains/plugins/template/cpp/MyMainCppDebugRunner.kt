package org.jetbrains.plugins.template.cpp

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
import org.jetbrains.plugins.template.debuger.DapDebugProcess
import java.io.File

/**
 * MyMainApp 的 Debug Runner：
 * - 只对 Debug Executor 生效（Run 逻辑仍走默认 Runner）
 * - 先编译 my_main.cpp 生成 mymaincpp 可执行文件
 * - 启动 XDebugSession + DapDebugProcess，连接 lldb-dap 调试器
 */
class MyMainCppDebugRunner : GenericProgramRunner<RunnerSettings>() {

    override fun getRunnerId(): String = "MyMainCppDebugRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is MyMainCppRunConfiguration
    }

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val configuration = environment.runProfile as? MyMainCppRunConfiguration
            ?: throw ExecutionException("Invalid configuration")
        
        val project = configuration.project
        
        // 1. 先编译 C++ 程序
        val cppFilePath = configuration.getMyMainCppPath()
            ?: throw ExecutionException("找不到 my_main.cpp 文件")
        
        val outputPath = configuration.getOutputPath()
            ?: throw ExecutionException("无法确定输出路径")
        
        println("[MyMainCppDebugRunner] 开始编译 my_main.cpp...")
        val compileSuccess = compile(cppFilePath, outputPath)
        if (!compileSuccess) {
            throw ExecutionException("编译失败，无法启动调试")
        }
        println("[MyMainCppDebugRunner] 编译成功: $outputPath")
        
        // 2. 启动 XDebugSession
        val debuggerManager = XDebuggerManager.getInstance(project)
        val debugSession = debuggerManager.startSession(
            environment,
            object : com.intellij.xdebugger.XDebugProcessStarter() {
                override fun start(session: XDebugSession): XDebugProcess {
                    return DapDebugProcess(session, outputPath)
                }
            }
        )
        
        return debugSession.runContentDescriptor
    }
    
    /**
     * 编译 C++ 文件
     */
    private fun compile(cppFilePath: String, outputPath: String): Boolean {
        val compileCommand = ProcessBuilder(
            "clang++",
            "-g",
            "-O0",
            cppFilePath,
            "-o",
            outputPath
        )
        
        val projectDir = File(cppFilePath).parentFile
        compileCommand.directory(projectDir)
        
        return try {
            val process = compileCommand.start()
            val exitCode = process.waitFor()
            
            if (exitCode != 0) {
                val errorOutput = process.errorStream.bufferedReader().readText()
                println("[MyMainCppDebugRunner] 编译错误: $errorOutput")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            println("[MyMainCppDebugRunner] 编译异常: ${e.message}")
            false
        }
    }
}
