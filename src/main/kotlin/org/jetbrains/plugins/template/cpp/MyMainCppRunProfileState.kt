package org.jetbrains.plugins.template.cpp

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.template.device.DeviceService
import org.jetbrains.plugins.template.device.HarmonyDevice
import java.io.File

/**
 * 运行状态 - 负责实际的编译和执行
 */
class MyMainCppRunProfileState(
    environment: ExecutionEnvironment
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val configuration = environment.runProfile as MyMainCppRunConfiguration
        val project = configuration.project

        // 从 DeviceService 获取选中的设备
        val selectedDevice: HarmonyDevice? = DeviceService.getInstance(project).getSelectedDevice()
        
        if (selectedDevice == null) {
            throw ExecutionException(
                "未选择 HarmonyOS 设备。\n" +
                "请在工具栏设备选择器中选择一个设备。"
            )
        }
        
        println("目标设备: ${selectedDevice.displayName} (${selectedDevice.deviceId})")

        // 获取文件路径
        val cppFilePath = configuration.getMyMainCppPath()
            ?: throw ExecutionException("找不到 my_main.cpp 文件")
        
        val outputPath = configuration.getOutputPath()
            ?: throw ExecutionException("无法确定输出路径")

        // 第一步：编译
        println("开始编译 my_main.cpp...")
        println("编译命令: clang++ -g -O0 $cppFilePath -o $outputPath")
        
        val compileResult = compile(project, cppFilePath, outputPath)
        if (!compileResult.success) {
            throw ExecutionException("编译失败\n${compileResult.errorMessage}")
        }
        
        println("编译成功！")
        println("-".repeat(50))
        println("开始运行程序...")
        println("设备: ${selectedDevice.deviceId}")
        println("-".repeat(50))

        // 第二步：运行（这里可以添加 HDC 命令来在设备上运行）
        val commandLine = GeneralCommandLine(outputPath)
        commandLine.setWorkDirectory(project.basePath)
        
        // TODO: 如果需要在设备上运行，可以使用 HDC 命令
        // 例如: hdc -t ${selectedDevice.deviceId} file send $outputPath /data/local/tmp/
        //      hdc -t ${selectedDevice.deviceId} shell /data/local/tmp/my_main

        val processHandler = KillableColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(processHandler)
        return processHandler
    }

    /**
     * 编译结果
     */
    private data class CompileResult(
        val success: Boolean,
        val errorMessage: String = ""
    )

    /**
     * 编译 C++ 文件
     */
    private fun compile(project: Project, cppFilePath: String, outputPath: String): CompileResult {
        val compileCommand = GeneralCommandLine(
            "clang++",
            "-g",
            "-O0",
            cppFilePath,
            "-o",
            outputPath
        )
        compileCommand.setWorkDirectory(project.basePath)

        try {
            val process = compileCommand.createProcess()
            val exitCode = process.waitFor()
            
            if (exitCode != 0) {
                val errorOutput = process.errorStream.bufferedReader().readText()
                return CompileResult(false, errorOutput)
            }
            
            return CompileResult(true)
        } catch (e: Exception) {
            return CompileResult(false, "编译异常: ${e.message}")
        }
    }
}
