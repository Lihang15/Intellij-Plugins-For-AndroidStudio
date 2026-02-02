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
import java.io.File

/**
 * harmonyApp 的 Debug Runner：
 * - 只对 Debug Executor 生效（Run 逻辑仍走默认 Runner）
 * - 先编译 my_main.cpp 生成 Harmony 可执行文件
 * - 启动 XDebugSession + LLDBDebugProcess，连接 lldb-_ 调试器
 */
class HarmonyDebugRunner : GenericProgramRunner<RunnerSettings>() {

    override fun getRunnerId(): String = "HarmonyDebugRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is HarmonyRunConfiguration
    }

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        println("\n========== [HarmonyDebugRunner.doExecute] 函数调用 ==========")
        
        val configuration = environment.runProfile as? HarmonyRunConfiguration
            ?: throw ExecutionException("Invalid configuration")
        
        val project = configuration.project
        println("[doExecute] 项目: ${project.name}")
        
        // 1. 先编译 C++ 程序
        val cppFilePath = configuration.getHarmonyPath()
            ?: throw ExecutionException("找不到 my_main.cpp 文件")
        
        val outputPath = configuration.getOutputPath()
            ?: throw ExecutionException("无法确定输出路径")
        
        println("[doExecute] C++ 源文件: $cppFilePath")
        println("[doExecute] 输出可执行文件: $outputPath")
        println("[doExecute] 步骤1: 开始编译 my_main.cpp...")
        val compileSuccess = compile(cppFilePath, outputPath)
        if (!compileSuccess) {
            println("[doExecute] ✗ 编译失败")
            throw ExecutionException("编译失败，无法启动调试")
        }
        println("[doExecute] ✓ 编译成功: $outputPath")
        
        // 2. 启动 XDebugSession
        println("[doExecute] 步骤2: 启动 XDebugSession")
        val debuggerManager = XDebuggerManager.getInstance(project)
        val debugSession = debuggerManager.startSession(
            environment,
            object : com.intellij.xdebugger.XDebugProcessStarter() {
                override fun start(session: XDebugSession): XDebugProcess {
                    println("[doExecute] 创建 LLDBDebugProcess")
                    return LLDBDebugProcess(session, outputPath)
                }
            }
        )
        
        println("[doExecute] ✓ 调试会话已启动")
        println("========== [HarmonyDebugRunner.doExecute] 函数结束 ==========\n")
        
        return debugSession.runContentDescriptor
    }
    
    /**
     * 编译 C++ 文件
     */
    private fun compile(cppFilePath: String, outputPath: String): Boolean {
        println("\n========== [HarmonyDebugRunner.compile] 函数调用 ==========")
        println("[compile] 源文件: $cppFilePath")
        println("[compile] 输出文件: $outputPath")
        
        val compileCommand = ProcessBuilder(
            "clang++",
            "-g",
            "-O0",
            cppFilePath,
            "-o",
            outputPath
        )
        
        println("[compile] 编译命令: clang++ -g -O0 $cppFilePath -o $outputPath")
        
        val projectDir = File(cppFilePath).parentFile
        compileCommand.directory(projectDir)
        println("[compile] 工作目录: ${projectDir.absolutePath}")
        
        return try {
            println("[compile] 执行编译...")
            val process = compileCommand.start()
            val exitCode = process.waitFor()
            
            println("[compile] 编译退出码: $exitCode")
            
            if (exitCode != 0) {
                val errorOutput = process.errorStream.bufferedReader().readText()
                println("[compile] ✗ 编译错误:")
                println(errorOutput)
                println("========== [HarmonyDebugRunner.compile] 编译失败 ==========\n")
                false
            } else {
                println("[compile] ✓ 编译成功")
                println("========== [HarmonyDebugRunner.compile] 编译成功 ==========\n")
                true
            }
        } catch (e: Exception) {
            println("[compile] ✗ 编译异常: ${e.message}")
            e.printStackTrace()
            println("========== [HarmonyDebugRunner.compile] 编译异常 ==========\n")
            false
        }
    }
}
