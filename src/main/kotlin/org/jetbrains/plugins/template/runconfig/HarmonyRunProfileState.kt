package org.jetbrains.plugins.template.runconfig

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
class HarmonyRunProfileState(
    environment: ExecutionEnvironment
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val configuration = environment.runProfile as HarmonyRunConfiguration
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

        // 获取项目根目录
        val projectBasePath = project.basePath
            ?: throw ExecutionException("无法获取项目根目录")
        
        // 从插件资源中获取脚本路径
        val scriptResource = this::class.java.getResource("/runscript/runOhosApp-Mac.sh")
            ?: throw ExecutionException("无法在插件资源中找到 runOhosApp-Mac.sh 脚本")
        
        // 将脚本复制到系统临时目录（用户看不到）
        val tempDir = System.getProperty("java.io.tmpdir")
        val scriptPath = File(tempDir, "runOhosApp-Mac-${System.currentTimeMillis()}.sh")
        try {
            scriptResource.openStream().use { input ->
                scriptPath.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            // 设置执行权限
            scriptPath.setExecutable(true)
            // 设置为 JVM 退出时自动删除
            scriptPath.deleteOnExit()
            println("✓ 脚本已准备: ${scriptPath.name} (临时文件)")
        } catch (e: Exception) {
            throw ExecutionException("无法准备脚本文件: ${e.message}")
        }
        
        println("-".repeat(50))
        println("执行 OHOS 部署脚本...")
        println("工作目录: $projectBasePath")
        println("平台: ohosArm64")
        println("设备: ${selectedDevice.deviceId}")
        println("-".repeat(50))

        // 构建命令：bash <临时脚本路径> ohosArm64 <设备ID>
        // 关键：工作目录设置为项目根目录，而不是临时目录
        val commandLine = GeneralCommandLine(
            "bash",
            scriptPath.absolutePath,
            "ohosArm64",
            selectedDevice.deviceId
        )
        commandLine.setWorkDirectory(projectBasePath)  // 工作目录 = 项目根目录

        val processHandler = KillableColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(processHandler)
        return processHandler
    }
}
