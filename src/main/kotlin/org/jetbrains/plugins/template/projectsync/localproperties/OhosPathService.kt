package org.jetbrains.plugins.template.projectsync.localproperties

import com.intellij.openapi.components.Service
import java.nio.file.Path

/**
 * OHOS 路径全局服务
 * 
 * 存储从 local.properties 同步的 local.ohos.path 配置
 * 供插件其他部分全局访问
 */
@Service(Service.Level.PROJECT)
class OhosPathService {

    /**
     * OHOS 外部工程路径
     * 
     * - null: 未配置或配置为空
     * - non-null: 已验证存在的本地路径
     */
    @Volatile
    var ohosPath: Path? = null
        set(value) {
            field = value
            if (value != null) {
                println("[OhosPathService] OHOS 路径已更新: ${value.toAbsolutePath()}")
            } else {
                println("[OhosPathService] OHOS 路径已清空")
            }
        }
}
