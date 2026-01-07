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
        println("[TimLSPClient] TimStartupActivity started: Initializing LSP server for .tim files")
        LOG.info("[TimLSPClient] TimStartupActivity started: Initializing LSP server for .tim files")

        try {
            // 验证语言服务器文件是否存在
            val serverPath = "/Users/admin/EazyWork/projects/Intellij-Plugins-For-AndroidStudio/lsp-8600/server/out/server.js"
            val serverFile = java.io.File(serverPath)
            println("[TimLSPClient] Checking if server file exists at: \$serverPath")
            println("[TimLSPClient] Server file exists: \${serverFile.exists()}")
            println("[TimLSPClient] Server file can read: \${serverFile.canRead()}")
            
            LOG.info("[TimLSPClient] Server file exists: \${serverFile.exists()}")
            LOG.info("[TimLSPClient] Server file can read: \${serverFile.canRead()}")

            if (!serverFile.exists()) {
                println("[TimLSPClient] ERROR: Server file does not exist at: \$serverPath")
                LOG.error("[TimLSPClient] ERROR: Server file does not exist at: \$serverPath")
                return
            }

            val serverDefinition = RawCommandServerDefinition(
                "tim",
                arrayOf(
                    "node",
                    serverPath,
                    "--stdio"
                )
            )

            println("[TimLSPClient] Creating server definition for .tim files")
            LOG.info("[TimLSPClient] Creating server definition for .tim files")
            
            println("[TimLSPClient] Registering LSP server definition for .tim files")
            LOG.info("[TimLSPClient] Registering LSP server definition for .tim files")
            
            val success = IntellijLanguageClient.addServerDefinition(serverDefinition)
            
            println(success)
            LOG.info("[TimLSPClient] LSP server definition registered successfully: \$success")

            // 验证服务器定义是否已添加
            println("[TimLSPClient] LSP server definition added for extension: tim")
            LOG.info("[TimLSPClient] LSP server definition added for extension: tim")
            
            println("[TimLSPClient] Server definition registered, but cannot verify extensions due to API limitations")
            LOG.info("[TimLSPClient] Server definition registered, but cannot verify extensions due to API limitations")
            

            
        } catch (e: Exception) {
            println("[TimLSPClient] ERROR initializing LSP server for .tim files: \${e.message}")
            e.printStackTrace()
            LOG.error("[TimLSPClient] Error initializing LSP server for .tim files", e)
        }
    }
}



//class TimStartupActivity : ProjectActivity {
//
//
//
//    override suspend fun execute(project: Project) {
//
//        println("[TimLSPClient] TimPreloadingActivity started: Initializing LSP server for .tim files")
//
//        val serverDefinition = RawCommandServerDefinition(
//            "tim",
//            arrayOf(
//                "node",
//                "/Users/admin/EazyWork/projects/Intellij-Plugins-For-AndroidStudio/lsp-8600/server/out/server.js",
//                "--stdio"
//            )
//        )
//
//        println("[TimLSPClient] Registering LSP server definition for .tim files")
//        IntellijLanguageClient.addServerDefinition(serverDefinition)
//        println("[TimLSPClient] LSP server definition registered successfully")
//
//        // 验证服务器定义是否已添加
//        println("[TimLSPClient] LSP server definition added for extension: tim")
//    }
//}
