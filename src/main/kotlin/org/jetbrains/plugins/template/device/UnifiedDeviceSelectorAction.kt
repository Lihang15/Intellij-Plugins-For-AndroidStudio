package org.jetbrains.plugins.template.device

import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.template.cpp.MyMainCppRunConfiguration
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*

/**
 * Unified device selector that shows different devices based on the selected run configuration.
 * - For MyMainApp: Shows HarmonyOS devices
 * - For other configurations: Hides itself (lets Android selector show)
 */
class UnifiedDeviceSelectorAction : AnAction(), CustomComponentAction, DumbAware {
    
    private val logger = Logger.getInstance(UnifiedDeviceSelectorAction::class.java)
    private val knownProjects = Collections.synchronizedList(mutableListOf<Project>())
    
    companion object {
        private const val ICON_LABEL_KEY = "iconLabel"
        private const val TEXT_LABEL_KEY = "textLabel"
        private const val ARROW_LABEL_KEY = "arrowLabel"
        private const val CUSTOM_COMPONENT_KEY = "customComponent"
        
        private const val TOOLBAR_FOREGROUND_KEY = "MainToolbar.foreground"
        private const val TOOLBAR_ICON_HOVER_BACKGROUND_KEY = "MainToolbar.Icon.hoverBackground"
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        showDevicePopup(e.dataContext, e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT))
    }
    
    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        val iconLabel = JBLabel(AllIcons.Debugger.ThreadRunning)
        val textLabel = JBLabel("Select Device")
        val arrowLabel = JBLabel(AllIcons.General.ChevronDown)
        
        textLabel.foreground = getToolbarForegroundColor()
        
        val contentPanel = JPanel(BorderLayout(4, 0))
        contentPanel.isOpaque = false
        contentPanel.add(iconLabel, BorderLayout.WEST)
        contentPanel.add(textLabel, BorderLayout.CENTER)
        contentPanel.add(arrowLabel, BorderLayout.EAST)
        
        val button = object : JButton() {
            override fun paintComponent(g: Graphics) {
                if (model.isRollover) {
                    val g2 = g.create() as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = getToolbarHoverBackgroundColor()
                    val arc = JBUIScale.scale(12)
                    g2.fillRoundRect(0, 0, width, height, arc, arc)
                    g2.dispose()
                }
                super.paintComponent(g)
            }
        }
        
        button.layout = BorderLayout()
        button.border = JBUI.Borders.empty(4, 8)
        button.add(contentPanel, BorderLayout.CENTER)
        button.isOpaque = false
        button.isContentAreaFilled = false
        button.isBorderPainted = false
        button.isFocusPainted = false
        button.isRolloverEnabled = true
        
        val labels = arrayOf(iconLabel, textLabel, arrowLabel)
        labels.forEach { label ->
            label.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    button.dispatchEvent(SwingUtilities.convertMouseEvent(label, e, button))
                }
                override fun mousePressed(e: MouseEvent) {
                    button.dispatchEvent(SwingUtilities.convertMouseEvent(label, e, button))
                }
                override fun mouseReleased(e: MouseEvent) {
                    button.dispatchEvent(SwingUtilities.convertMouseEvent(label, e, button))
                }
                override fun mouseEntered(e: MouseEvent) {
                    button.dispatchEvent(SwingUtilities.convertMouseEvent(label, e, button))
                }
                override fun mouseExited(e: MouseEvent) {
                    button.dispatchEvent(SwingUtilities.convertMouseEvent(label, e, button))
                }
            })
        }
        
        button.putClientProperty(ICON_LABEL_KEY, iconLabel)
        button.putClientProperty(TEXT_LABEL_KEY, textLabel)
        button.putClientProperty(ARROW_LABEL_KEY, arrowLabel)
        presentation.putClientProperty(CUSTOM_COMPONENT_KEY, button)
        
        button.addActionListener {
            val dataContext = DataManager.getInstance().getDataContext(button)
            val project = CommonDataKeys.PROJECT.getData(dataContext)
            if (project != null) {
                showDevicePopup(dataContext, button)
            }
        }
        
        // 初始化定时器，等待设备加载
        ApplicationManager.getApplication().invokeLater {
            val dataContext = DataManager.getInstance().getDataContext(button)
            val project = CommonDataKeys.PROJECT.getData(dataContext)
            if (project != null && !project.isDisposed) {
                var checkCount = 0
                val maxChecks = 20
                
                val timer = javax.swing.Timer(1000) { event ->
                    if (project.isDisposed) {
                        (event.source as javax.swing.Timer).stop()
                        return@Timer
                    }
                    
                    checkCount++
                    
                    val deviceService = DeviceService.getInstance(project)
                    val devices = deviceService.getConnectedDevices()
                    val selectedDevice = deviceService.getSelectedDevice()
                    val status = deviceService.getStatus()
                    
                    val icon: Icon
                    val text: String
                    var shouldStop = false
                    
                    when {
                        status == DeviceService.State.LOADING -> {
                            icon = AllIcons.Process.Step_1
                            text = "Loading..."
                        }
                        status == DeviceService.State.INACTIVE -> {
                            icon = AllIcons.General.Error
                            text = "HDC Unavailable"
                            shouldStop = true
                        }
                        devices.isEmpty() -> {
                            icon = AllIcons.Debugger.ThreadRunning
                            text = "Monitoring..."
                            if (checkCount >= maxChecks) {
                                shouldStop = true
                            }
                        }
                        selectedDevice != null -> {
                            icon = AllIcons.RunConfigurations.TestState.Run
                            text = selectedDevice.displayName
                            shouldStop = true
                        }
                        else -> {
                            icon = AllIcons.General.Warning
                            text = "Select Device"
                            shouldStop = true
                        }
                    }
                    
                    iconLabel.icon = icon
                    textLabel.text = text
                    textLabel.foreground = getToolbarForegroundColor()
                    button.revalidate()
                    button.repaint()
                    
                    if (shouldStop) {
                        (event.source as javax.swing.Timer).stop()
                    }
                }
                
                timer.isRepeats = true
                timer.start()
                button.putClientProperty("initTimer", timer)
            }
        }
        
        return button
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isVisible = false
            return
        }
        
        // 检查当前选中的运行配置
        val runManager = com.intellij.execution.RunManager.getInstance(project)
        val selectedConfiguration = runManager.selectedConfiguration
        val isMyMainApp = selectedConfiguration?.configuration is MyMainCppRunConfiguration
        
        // 只在 MyMainApp 配置时显示
        if (!isMyMainApp) {
            e.presentation.isVisible = false
            return
        }
        
        e.presentation.isVisible = true
        
        // 注册监听器
        if (!knownProjects.contains(project)) {
            knownProjects.add(project)
            
            val deviceListener = { queueUpdate(project, e.presentation) }
            val deviceService = DeviceService.getInstance(project)
            deviceService.addListener(deviceListener)
            
            // 立即触发一次更新
            queueUpdate(project, e.presentation)
            
            ProjectManager.getInstance().addProjectManagerListener(project, object : ProjectManagerListener {
                override fun projectClosed(closedProject: Project) {
                    knownProjects.remove(closedProject)
                    DeviceService.getInstance(closedProject).removeListener(deviceListener)
                }
            })
        }
        
        val targetManager = com.intellij.execution.ExecutionTargetManager.getInstance(project)
        val activeTarget = targetManager.activeTarget
        
        val deviceService = DeviceService.getInstance(project)
        val devices = deviceService.getConnectedDevices()
        val status = deviceService.getStatus()
        
        val displayDevice: HarmonyDevice? = when {
            activeTarget is HarmonyExecutionTarget -> activeTarget.getDevice()
            else -> deviceService.getSelectedDevice()
        }
        
        val icon: Icon
        val text: String
        
        when {
            status == DeviceService.State.LOADING -> {
                icon = AllIcons.Process.Step_1
                text = "Loading..."
            }
            status == DeviceService.State.INACTIVE -> {
                icon = AllIcons.General.Error
                text = "HDC Unavailable"
            }
            devices.isEmpty() -> {
                icon = AllIcons.Debugger.ThreadRunning
                text = "Monitoring..."
            }
            displayDevice == null -> {
                icon = AllIcons.General.Warning
                text = "Select Device"
            }
            else -> {
                icon = AllIcons.RunConfigurations.TestState.Run
                text = displayDevice.displayName
            }
        }
        
        e.presentation.text = text
        e.presentation.icon = icon
        
        val button = e.presentation.getClientProperty(CUSTOM_COMPONENT_KEY) as? JButton
        val iconLabel = button?.getClientProperty(ICON_LABEL_KEY) as? JBLabel
        val textLabel = button?.getClientProperty(TEXT_LABEL_KEY) as? JBLabel
        
        if (iconLabel != null && textLabel != null) {
            iconLabel.icon = icon
            textLabel.text = text
            textLabel.foreground = getToolbarForegroundColor()
        }
    }
    
    private fun showDevicePopup(dataContext: DataContext, component: Component?) {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return
        val deviceService = DeviceService.getInstance(project)
        val devices = deviceService.getConnectedDevices()
        
        if (devices.isEmpty()) {
            val group = DefaultActionGroup()
            group.add(object : AnAction("No Devices Connected") {
                override fun actionPerformed(e: AnActionEvent) {}
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = false
                }
                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.BGT
                }
            })
            group.addSeparator()
            group.add(object : AnAction("Waiting for HarmonyOS devices...") {
                override fun actionPerformed(e: AnActionEvent) {}
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = false
                }
                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.BGT
                }
            })
            
            val popup = JBPopupFactory.getInstance()
                .createActionGroupPopup(null, group, dataContext,
                    JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
            
            if (component != null) {
                popup.showUnderneathOf(component)
            } else {
                popup.showInBestPositionFor(dataContext)
            }
            return
        }
        
        val group = DefaultActionGroup()
        devices.forEach { device ->
            group.add(SelectDeviceAction(device, project))
        }
        
        val popup = JBPopupFactory.getInstance()
            .createActionGroupPopup(null, group, dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
        
        if (component != null) {
            popup.showUnderneathOf(component)
        } else {
            popup.showInBestPositionFor(dataContext)
        }
    }
    
    private fun queueUpdate(project: Project, presentation: Presentation) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                ActivityTracker.getInstance().inc()
                
                val button = presentation.getClientProperty(CUSTOM_COMPONENT_KEY) as? JButton
                if (button != null) {
                    val deviceService = DeviceService.getInstance(project)
                    val devices = deviceService.getConnectedDevices()
                    val selectedDevice = deviceService.getSelectedDevice()
                    val status = deviceService.getStatus()
                    
                    val icon: Icon
                    val text: String
                    
                    when {
                        status == DeviceService.State.LOADING -> {
                            icon = AllIcons.Process.Step_1
                            text = "Loading..."
                        }
                        status == DeviceService.State.INACTIVE -> {
                            icon = AllIcons.General.Error
                            text = "HDC Unavailable"
                        }
                        devices.isEmpty() -> {
                            icon = AllIcons.Debugger.ThreadRunning
                            text = "Monitoring..."
                        }
                        selectedDevice == null -> {
                            icon = AllIcons.General.Warning
                            text = "Select Device"
                        }
                        else -> {
                            icon = AllIcons.RunConfigurations.TestState.Run
                            text = selectedDevice.displayName
                        }
                    }
                    
                    val iconLabel = button.getClientProperty(ICON_LABEL_KEY) as? JBLabel
                    val textLabel = button.getClientProperty(TEXT_LABEL_KEY) as? JBLabel
                    
                    if (iconLabel != null && textLabel != null) {
                        iconLabel.icon = icon
                        textLabel.text = text
                        textLabel.foreground = getToolbarForegroundColor()
                        button.revalidate()
                        button.repaint()
                    }
                }
            }
        }
    }
    
    private fun getToolbarForegroundColor(): Color {
        return JBColor.namedColor(TOOLBAR_FOREGROUND_KEY, UIUtil.getLabelForeground())
    }
    
    private fun getToolbarHoverBackgroundColor(): Color {
        return JBColor.namedColor(TOOLBAR_ICON_HOVER_BACKGROUND_KEY, JBUI.CurrentTheme.ActionButton.hoverBackground())
    }
    
    private class SelectDeviceAction(
        private val device: HarmonyDevice,
        private val project: Project
    ) : AnAction(device.displayName) {
        
        override fun actionPerformed(e: AnActionEvent) {
            DeviceService.getInstance(project).setSelectedDevice(device)
            
            val targetManager = com.intellij.execution.ExecutionTargetManager.getInstance(project)
            val runManager = com.intellij.execution.RunManager.getInstance(project)
            val selectedConfig = runManager.selectedConfiguration
            
            if (selectedConfig != null) {
                val allTargets = targetManager.getTargetsFor(selectedConfig.configuration)
                val harmonyTarget = allTargets.filterIsInstance<HarmonyExecutionTarget>()
                    .find { it.getDevice().deviceId == device.deviceId }
                
                if (harmonyTarget != null) {
                    targetManager.activeTarget = harmonyTarget
                }
            }
        }
        
        override fun getActionUpdateThread(): ActionUpdateThread {
            return ActionUpdateThread.BGT
        }
    }
}
