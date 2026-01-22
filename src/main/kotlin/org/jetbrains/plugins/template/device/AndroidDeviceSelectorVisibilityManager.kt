package org.jetbrains.plugins.template.device

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.template.cpp.MyMainCppRunConfiguration

/**
 * 管理 Android 设备选择器的可见性
 * 当选择 MyMainApp 时隐藏 Android 设备选择器
 */
class AndroidDeviceSelectorVisibilityManager(private val project: Project) : RunManagerListener {
    
    companion object {
        // Android 设备选择器的 Action ID
        private val ANDROID_DEVICE_SELECTOR_IDS = listOf(
            "Android.DeviceSelector",
            "DeviceAndSnapshotComboBox",
            "AndroidDeviceChooser"
        )
    }
    
    override fun runConfigurationSelected(settings: RunnerAndConfigurationSettings?) {
        println("=== AndroidDeviceSelectorVisibilityManager.runConfigurationSelected() ===")
        println("Selected configuration: ${settings?.name}")
        
        if (settings == null) return
        
        val configuration = settings.configuration
        val isMyMainApp = configuration is MyMainCppRunConfiguration
        
        println("Is MyMainApp: $isMyMainApp")
        
        // 在 EDT 上更新 UI
        ApplicationManager.getApplication().invokeLater {
            updateAndroidDeviceSelectorVisibility(!isMyMainApp)
        }
    }
    
    /**
     * 更新 Android 设备选择器的可见性
     * 
     * @param visible true 显示，false 隐藏
     */
    private fun updateAndroidDeviceSelectorVisibility(visible: Boolean) {
        println("=== updateAndroidDeviceSelectorVisibility($visible) ===")
        
        val actionManager = ActionManager.getInstance()
        
        ANDROID_DEVICE_SELECTOR_IDS.forEach { actionId ->
            try {
                val action = actionManager.getAction(actionId)
                if (action != null) {
                    println("Found action: $actionId")
                    
                    // 尝试获取 Presentation 并设置可见性
                    // 注意：这可能不会立即生效，因为 Presentation 是在 update() 时创建的
                    // 我们需要找到另一种方法
                    
                    println("Action class: ${action.javaClass.name}")
                } else {
                    println("Action not found: $actionId")
                }
            } catch (e: Exception) {
                println("Error accessing action $actionId: ${e.message}")
            }
        }
        
        println("=== updateAndroidDeviceSelectorVisibility() END ===")
    }
}
