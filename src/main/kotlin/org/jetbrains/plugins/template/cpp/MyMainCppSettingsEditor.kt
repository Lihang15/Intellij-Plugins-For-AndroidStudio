package org.jetbrains.plugins.template.cpp

import com.intellij.openapi.options.SettingsEditor
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 运行配置编辑器 UI
 */
class MyMainCppSettingsEditor : SettingsEditor<MyMainCppRunConfiguration>() {
    private val panel = JPanel()

    init {
        panel.add(JLabel("此配置将编译并运行项目根目录下的 my_main.cpp 文件"))
    }

    override fun createEditor(): JComponent {
        return panel
    }

    override fun resetEditorFrom(configuration: MyMainCppRunConfiguration) {
        // 从配置中读取设置
    }

    override fun applyEditorTo(configuration: MyMainCppRunConfiguration) {
        // 应用设置到配置
    }
}
