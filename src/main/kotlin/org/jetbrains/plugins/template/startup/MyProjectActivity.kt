package org.jetbrains.plugins.template.startup

import com.intellij.execution.RunManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.plugins.template.cpp.MyMainCppConfigurationType
import org.jetbrains.plugins.template.cpp.MyMainCppRunConfiguration
import java.io.File

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")

        // 在项目启动时自动检查并创建 MyMainApp 运行配置
        autoCreateMyMainAppConfiguration(project)
    }

    /**
     * 如果项目根目录存在 my_main.cpp，则自动在 Run/Debug Configurations 中添加一个 MyMainApp 配置
     */
    private fun autoCreateMyMainAppConfiguration(project: Project) {
        val basePath = project.basePath ?: return
        val myMainCppFile = File(basePath, "my_main.cpp")
        if (!myMainCppFile.exists()) {
            return
        }

        val runManager = RunManager.getInstance(project)

        // 如果已经存在名为 MyMainApp 且类型为 MyMainCppConfigurationType 的配置，则不重复创建
        val existing = runManager.allSettings.any { settings ->
            settings.type is MyMainCppConfigurationType && settings.name == "MyMainApp"
        }
        if (existing) {
            return
        }

        val configurationType = MyMainCppConfigurationType.getInstance()
        val factory = configurationType.configurationFactories.firstOrNull() ?: return

        val settings = runManager.createConfiguration("MyMainApp", factory)
        // 这里目前不需要对 configuration 做特别初始化，使用默认行为：项目根目录 my_main.cpp

        runManager.addConfiguration(settings)
        // 不强制设为当前选中，只是让它出现在列表里；如果你希望默认选中，可以取消下一行注释
        // runManager.selectedConfiguration = settings
    }
}
