package org.jetbrains.plugins.template.projectsync

/**
 * 与 sync-output-data.json 结构对应的 POJO
 */
data class SyncDataModel(
    var version: String = "",
    var status: String = "",
    var modules: List<String> = emptyList(),
    var timestamp: String = ""
)