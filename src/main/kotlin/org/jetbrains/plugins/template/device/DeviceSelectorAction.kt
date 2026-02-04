package org.jetbrains.plugins.template.device

import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ModalityUiUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*

/**
 *  DeviceSelectorAction 的实现
 * 
 */
class DeviceSelectorAction : AnAction(), CustomComponentAction, DumbAware {
    
    private val logger = Logger.getInstance(DeviceSelectorAction::class.java)
    private val knownProjects = Collections.synchronizedList(mutableListOf<Project>())
    
    companion object {
        private const val ICON_LABEL_KEY = "iconLabel"
        private const val TEXT_LABEL_KEY = "textLabel"
        private const val ARROW_LABEL_KEY = "arrowLabel"
        private const val CUSTOM_COMPONENT_KEY = "customComponent"
        
        private const val TOOLBAR_FOREGROUND_KEY = "MainToolbar.foreground"
        private const val TOOLBAR_ICON_HOVER_BACKGROUND_KEY = "MainToolbar.Icon.hoverBackground"
        
        private val DEFAULT_DEVICE_ICON = AllIcons.Debugger.ThreadRunning
        private val DEFAULT_ARROW_ICON = AllIcons.General.ChevronDown
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        println("=== DeviceSelectorAction.actionPerformed() ===")
        val project = e.project ?: return
        
        showDevicePopup(e.dataContext, e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT))
    }
    
    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        println("========================================")
        println("=== DeviceSelectorAction.createCustomComponent() ===")
        println("Place: $place")
        println("========================================")
        
        val iconLabel = JBLabel(DEFAULT_DEVICE_ICON)
        val textLabel = JBLabel()
        val arrowLabel = JBLabel(DEFAULT_ARROW_ICON)
        
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
            
            override fun getPreferredSize(): Dimension {
                // 设置固定宽度，确保设备名称完整显示
                val fixedWidth = JBUI.scale(200)  // 固定 200 像素
                val height = JBUI.scale(28)
                return Dimension(fixedWidth, height)
            }
            
            override fun getMinimumSize(): Dimension {
                return Dimension(JBUI.scale(200), JBUI.scale(28))
            }
            
            override fun getMaximumSize(): Dimension {
                return Dimension(JBUI.scale(400), JBUI.scale(28))
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
        
        println("Custom component created successfully")
        return button
    }
    
    override fun update(e: AnActionEvent) {
        println("========================================")
        println("=== DeviceSelectorAction.update() ===")
        
        val project = e.project
        if (project == null) {
            println("Project is null, hiding")
            e.presentation.isVisible = false
            return
        }
        
        println("Project: ${project.name}")
        
        // 始终显示，就像 Flutter 那样
        e.presentation.isVisible = true
        
        val presentation = e.presentation
        
        // 注册监听器（仅首次）
        if (!knownProjects.contains(project)) {
            println("First time for project ${project.name}, registering listeners")
            knownProjects.add(project)
            
            ApplicationManager.getApplication().messageBus.connect()
                .subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
                    override fun projectClosed(closedProject: Project) {
                        knownProjects.remove(closedProject)
                    }
                })
            
            val deviceListener = {
                println("Device listener triggered")
                queueUpdate(project, presentation)
            }
            DeviceService.getInstance(project).addListener(deviceListener)
            
            ProjectManager.getInstance().addProjectManagerListener(project, object : ProjectManagerListener {
                override fun projectClosing(closingProject: Project) {
                    DeviceService.getInstance(closingProject).removeListener(deviceListener)
                }
            })
            
            // 立即更新
            updatePresentation(project, presentation)
        }
        
        // 更新显示内容
        val deviceService = DeviceService.getInstance(project)
        val devices = deviceService.getConnectedDevices()
        val selectedDevice = deviceService.getSelectedDevice()
        val status = deviceService.getStatus()
        
        println("Status: $status, Devices: ${devices.size}, Selected: ${selectedDevice?.displayName}")
        
        val text: String
        val icon: Icon
        
        when {
            devices.isEmpty() -> {
                val isLoading = status == DeviceService.State.LOADING
                text = if (isLoading) "Loading..." else "No Devices"
                icon = DEFAULT_DEVICE_ICON
            }
            selectedDevice == null -> {
                text = "Select Device"
                icon = DEFAULT_DEVICE_ICON
            }
            else -> {
                text = selectedDevice.displayName
                icon = selectedDevice.getIcon()
                presentation.isEnabled = true
            }
        }
        
        presentation.text = text
        presentation.icon = icon
        
        println("Set presentation: text='$text', icon=$icon")
        
        // 更新自定义组件
        updateCustomComponent(presentation, icon, text)
        
        println("========================================")
    }
    
    private fun updateCustomComponent(presentation: Presentation, icon: Icon, text: String) {
        val customComponent = presentation.getClientProperty(CUSTOM_COMPONENT_KEY) as? JButton
        if (customComponent != null) {
            val iconLabel = customComponent.getClientProperty(ICON_LABEL_KEY) as? JBLabel
            val textLabel = customComponent.getClientProperty(TEXT_LABEL_KEY) as? JBLabel
            
            iconLabel?.icon = icon
            if (textLabel != null) {
                textLabel.text = text
                textLabel.foreground = getToolbarForegroundColor()
                customComponent.invalidate()
                var parent = customComponent.parent
                while (parent != null) {
                    parent.invalidate()
                    parent = parent.parent
                }
                customComponent.revalidate()
                customComponent.repaint()
            }
            println("Updated custom component")
        }
    }
    
    private fun showDevicePopup(dataContext: DataContext, component: Component?) {
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
                group.add(SelectDeviceAction(device, project))
            }
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
        ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState()) {
            updatePresentation(project, presentation)
        }
    }
    
    private fun updatePresentation(project: Project, presentation: Presentation) {
        if (project.isDisposed) {
            return
        }
        
        ActivityTracker.getInstance().inc()
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
    ) : AnAction(device.displayName, null, device.getIcon()) {
        
        override fun actionPerformed(e: AnActionEvent) {
            DeviceService.getInstance(project).setSelectedDevice(device)
            println("Device selected: ${device.displayName}")
        }
        
        override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }
}
