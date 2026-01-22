package org.jetbrains.plugins.template.device

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

/**
 * Force initialization test action
 */
class ForceInitAction : AnAction("Force Init Test") {
    
    override fun actionPerformed(e: AnActionEvent) {
        println("========================================")
        println("=== ForceInitAction START ===")
        println("========================================")
        
        System.out.println("System.out: ForceInitAction called")
        System.err.println("System.err: ForceInitAction called")
        
        val project = e.project
        println("Project: $project")
        
        if (project == null) {
            println("!!! NO PROJECT !!!")
            Messages.showMessageDialog(
                "No project found!",
                "Error",
                Messages.getErrorIcon()
            )
            return
        }
        
        println("Project name: ${project.name}")
        println("Project path: ${project.basePath}")
        
        try {
            println("Calling DeviceService.getInstance()...")
            val service = DeviceService.getInstance(project)
            println("Service obtained: $service")
            println("Service class: ${service.javaClass.name}")
            println("Service status: ${service.getStatus()}")
            println("Service devices: ${service.getConnectedDevices().size}")
            
            Messages.showMessageDialog(
                project,
                "DeviceService initialized!\n" +
                "Status: ${service.getStatus()}\n" +
                "Devices: ${service.getConnectedDevices().size}\n" +
                "Check console for detailed logs",
                "Success",
                Messages.getInformationIcon()
            )
        } catch (e: Exception) {
            println("!!! EXCEPTION !!!")
            e.printStackTrace()
            Messages.showMessageDialog(
                project,
                "Error: ${e.message}\n${e.stackTraceToString()}",
                "Error",
                Messages.getErrorIcon()
            )
        }
        
        println("========================================")
        println("=== ForceInitAction END ===")
        println("========================================")
    }
}
