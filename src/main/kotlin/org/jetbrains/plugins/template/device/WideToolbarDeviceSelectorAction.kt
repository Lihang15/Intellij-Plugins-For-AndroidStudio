package org.jetbrains.plugins.template.device

import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 带自定义宽度的工具栏设备选择器
 * 使用 CustomComponentAction 来完全控制组件的宽度
 */
class WideToolbarDeviceSelectorAction : AnAction(), CustomComponentAction, DumbAware {
    
    private val knownProjects = mutableSetOf<Project>()
    
    companion object {
        private const val MIN_WIDTH = 250  // 最小宽度（像素）- 增加以显示更长的设备名
        private const val MAX_WIDTH = 500  // 最大宽度（像素）- 增加以支持超长设备名
    }
    
    init {
        println("=== WideToolbarDeviceSelectorAction INITIALIZED ===")
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        println("=== WideToolbarDeviceSelectorAction.actionPerformed() ===")
        val project = e.project ?: return
        showDevicePopup(e.dataContext, e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT))
    }
    
    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        println("=== createCustomComponent() ===")
        
        val iconLabel = JBLabel(AllIcons.Debugger.ThreadRunning)
        val textLabel = JBLabel("Loading...")
        val arrowLabel = JBLabel(AllIcons.General.ChevronDown)
        
        val button = object : JButton() {
            override fun getPreferredSize(): Dimension {
                // 使用更大的固定宽度，确保所有设备名都能完整显示
                val fixedWidth = JBUI.scale(200)  // 固定 200 像素
                val height = JBUI.scale(28)
                
                println("Fixed size: width=$fixedWidth, height=$height, text='${textLabel.text}'")
                return Dimension(fixedWidth, height)
            }
            
            override fun getMinimumSize(): Dimension {
                return Dimension(JBUI.scale(MIN_WIDTH), JBUI.scale(28))
            }
            
            override fun getMaximumSize(): Dimension {
                return Dimension(JBUI.scale(MAX_WIDTH), JBUI.scale(28))
            }
        }
        
        button.layout = BorderLayout()
        button.border = JBUI.Borders.empty(4, 8)
        button.isOpaque = false
        button.isContentAreaFilled = false
        button.isBorderPainted = false
        button.isFocusPainted = false
        
        val contentPanel = JPanel(BorderLayout(JBUI.scale(4), 0))
        contentPanel.isOpaque = false
        contentPanel.add(iconLabel, BorderLayout.WEST)
        contentPanel.add(textLabel, BorderLayout.CENTER)
        contentPanel.add(arrowLabel, BorderLayout.EAST)
        
        button.add(contentPanel, BorderLayout.CENTER)
        
        // 存储标签引用，用于后续更新
        button.putClientProperty("iconLabel", iconLabel)
        button.putClientProperty("textLabel", textLabel)
        button.putClientProperty("arrowLabel", arrowLabel)
        
        // 点击事件
        button.addActionListener {
            val dataContext = DataManager.getInstance().getDataContext(button)
            val project = CommonDataKeys.PROJECT.getData(dataContext)
            if (project != null) {
                showDevicePopup(dataContext, button)
            }
        }
        
        return button
    }
    
    override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
        println("=== updateCustomComponent() ===")
        
        if (component !is JButton) return
        
        val iconLabel = component.getClientProperty("iconLabel") as? JBLabel ?: return
        val textLabel = component.getClientProperty("textLabel") as? JBLabel ?: return
        
        // 更新图标和文本
        iconLabel.icon = presentation.icon
        textLabel.text = presentation.text ?: ""
        
        // 设置可见性
        component.isVisible = presentation.isVisible
        component.isEnabled = presentation.isEnabled
        
        // 强制重新计算大小
        component.invalidate()
        component.revalidate()
        component.repaint()
        
        println("Updated component: text='${textLabel.text}', visible=${component.isVisible}")
    }
    
    override fun update(e: AnActionEvent) {
        println("=== WideToolbarDeviceSelectorAction.update() ===")
        
        val project = e.project
        if (project == null) {
            e.presentation.isVisible = false
            return
        }
        
        // 注册运行配置监听器（仅首次）
        if (!knownProjects.contains(project)) {
            knownProjects.add(project)
            
            // 监听运行配置变化
            project.messageBus.connect().subscribe(RunManagerListener.TOPIC, object : RunManagerListener {
                override fun runConfigurationSelected(settings: RunnerAndConfigurationSettings?) {
                    println("=== Run configuration changed: ${settings?.name} ===")
                    // 触发 UI 更新
                    ApplicationManager.getApplication().invokeLater {
                        ActivityTracker.getInstance().inc()
                    }
                }
            })
            
            println("=== Registered RunManagerListener for project ${project.name} ===")
        }
        
        // 检查当前选中的运行配置
        val runManager = com.intellij.execution.RunManager.getInstance(project)
        val selectedConfiguration = runManager.selectedConfiguration
        val isMyMainApp = selectedConfiguration?.configuration is org.jetbrains.plugins.template.cpp.MyMainCppRunConfiguration
        
        println("Selected configuration: ${selectedConfiguration?.name}, isMyMainApp: $isMyMainApp")
        
        // 只在 MyMainApp 配置时显示
        if (!isMyMainApp) {
            println("Not MyMainApp, hiding")
            e.presentation.isVisible = false
            return
        }
        
        println("Is MyMainApp, showing")
        e.presentation.isVisible = true
        e.presentation.isEnabled = true
        
        // 获取当前设备状态
        val deviceService = DeviceService.getInstance(project)
        val devices = deviceService.getConnectedDevices()
        val selectedDevice = deviceService.getSelectedDevice()
        
        println("Project: ${project.name}, Devices: ${devices.size}, Selected: ${selectedDevice?.displayName}")
        
        // 设置显示文本和图标
        val text: String
        val icon: Icon
        
        when {
            devices.isEmpty() -> {
                icon = AllIcons.Debugger.ThreadRunning
                text = "No HarmonyOS Devices"
            }
            selectedDevice == null -> {
                icon = AllIcons.General.Warning
                text = "Select HarmonyOS Device"
            }
            else -> {
                icon = selectedDevice.getIcon()
                text = selectedDevice.displayName
            }
        }
        
        e.presentation.text = text
        e.presentation.icon = icon
        e.presentation.description = "HarmonyOS Device: $text"
        
        println("Set: text='$text', icon=$icon, visible=true, enabled=true")
    }
    
    private fun showDevicePopup(dataContext: DataContext, component: java.awt.Component?) {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return
        val deviceService = DeviceService.getInstance(project)
        val devices = deviceService.getConnectedDevices()
        
        println("Showing device popup, devices: ${devices.size}")
        
        val group = DefaultActionGroup()
        
        if (devices.isEmpty()) {
            group.add(object : AnAction("No Devices Connected") {
                override fun actionPerformed(e: AnActionEvent) {}
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = false
                }
                override fun getActionUpdateThread() = ActionUpdateThread.BGT
            })
        } else {
            devices.forEach { device ->
                group.add(object : AnAction(device.displayName, null, device.getIcon()) {
                    override fun actionPerformed(e: AnActionEvent) {
                        deviceService.setSelectedDevice(device)
                        println("Device selected: ${device.displayName}")
                    }
                    override fun getActionUpdateThread() = ActionUpdateThread.BGT
                })
            }
        }
        
        val popup = JBPopupFactory.getInstance()
            .createActionGroupPopup(
                "Select HarmonyOS Device",
                group,
                dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                false
            )
        
        if (component != null) {
            popup.showUnderneathOf(component)
        } else {
            popup.showInBestPositionFor(dataContext)
        }
    }
}
