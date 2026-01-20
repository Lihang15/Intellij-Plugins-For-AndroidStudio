package org.jetbrains.plugins.template.projectsync

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class DataSyncService(val project: Project) {
    // 内存中的数据模型，供插件其他 Action 或 RunConfiguration 使用
    var dataModel: SyncDataModel? = null

    companion object {
        fun getInstance(project: Project): DataSyncService = project.getService(DataSyncService::class.java)
    }
}