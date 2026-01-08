package tim

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.wso2.lsp4intellij.IntellijLanguageClient
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.RawCommandServerDefinition

class TimStartupActivity : StartupActivity {

    companion object {
        private val LOG = Logger.getInstance(TimStartupActivity::class.java)
    }

    override fun runActivity(project: Project) {
        LOG.info("[TimLSPClient] TimStartupActivity started: Initializing LSP server for .tim files")

        try {
            // 语言服务器路径
            val serverPath = "/Users/admin/EazyWork/projects/Intellij-Plugins-For-AndroidStudio/lsp-8600/server/out/server.js"
            val serverFile = java.io.File(serverPath)
            
            LOG.info("[TimLSPClient] Checking if server file exists at: $serverPath")
            LOG.info("[TimLSPClient] Server file exists: ${serverFile.exists()}")
            LOG.info("[TimLSPClient] Server file can read: ${serverFile.canRead()}")

            if (!serverFile.exists()) {
                LOG.error("[TimLSPClient] ERROR: Server file does not exist at: $serverPath")
                return
            }

            // 创建 LSP 服务器定义
            // 第一个参数是扩展名（不带点），用于关联文件类型
            val serverDefinition = RawCommandServerDefinition(
                "tim",  // 文件扩展名，关联 .tim 文件
                arrayOf(
                    "node",
                    serverPath,
                    "--stdio"  // 使用 stdio 通信
                )
            )

            LOG.info("[TimLSPClient] Created server definition for .tim files")
            LOG.info("[TimLSPClient] Command: node $serverPath --stdio")
            
            // 注册 LSP 服务器定义
            LOG.info("[TimLSPClient] Registering LSP server definition...")
            val success = IntellijLanguageClient.addServerDefinition(serverDefinition)
            
            if (success != null) {
                LOG.info("[TimLSPClient] LSP server definition registered successfully")
                LOG.info("[TimLSPClient] LSP will start when opening .tim files")
            } else {
                LOG.warn("[TimLSPClient] Server definition registration returned null")
            }
            
        } catch (e: Exception) {
            LOG.error("[TimLSPClient] Error initializing LSP server for .tim files", e)
            e.printStackTrace()
        }
    }
}
