package org.jetbrains.plugins.template.cpp

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import java.io.File

// 导入数据同步服务
import org.jetbrains.plugins.template.projectsync.DataSyncService

/**
 * 运行配置生产者 - 自动检测并创建运行配置
 */
class MyMainCppRunConfigurationProducer : LazyRunConfigurationProducer<MyMainCppRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory {
        return MyMainCppConfigurationType.getInstance().configurationFactories[0]
    }

    override fun isConfigurationFromContext(
        configuration: MyMainCppRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        // 检查项目中是否存在 my_main.cpp
        return configuration.hasMyMainCppFile()
    }

    override fun setupConfigurationFromContext(
        configuration: MyMainCppRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val project = context.project
        val projectPath = project.basePath ?: return false

         // 1. 验证数据同步情况
        verifySyncedData(project)


        // 检查项目根目录下是否存在 my_main.cpp
        val myMainCppFile = File(projectPath, "my_main.cpp")
        if (!myMainCppFile.exists()) {
            return false
        }

        // 设置运行配置名称
        configuration.name = "MyMainApp"
        return true
    }

     /**
     * 验证数据是否同步过来的调试方法
     */
    private fun verifySyncedData(project: com.intellij.openapi.project.Project) {
        val syncService = DataSyncService.getInstance(project)
        val model = syncService.dataModel

        println("==============================================")
        println("[DEBUG] MyMainCppRunConfigurationProducer 正在检查同步数据...")
        
        if (model != null) {
            println("[DEBUG] ✅ 同步成功！")
            println("[DEBUG] 版本号 (version): ${model.version}")
            println("[DEBUG] 状态 (status): ${model.status}")
            println("[DEBUG] 模块列表 (modules): ${model.modules.joinToString(", ")}")
            println("[DEBUG] 脚本执行时间: ${model.timestamp}")
        } else {
            println("[DEBUG] ❌ 同步尚未完成或失败 (dataModel is null)")
            // 提示：如果是刚打开项目就点这里，脚本可能还没跑完
        }
        println("==============================================")
    }
}
