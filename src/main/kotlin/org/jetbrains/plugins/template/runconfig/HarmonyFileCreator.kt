package org.jetbrains.plugins.template.runconfig

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

/**
 * Helper to create my_main.cpp file if it doesn't exist.
 */
object HarmonyFileCreator {
    
    private const val DEFAULT_CPP_CONTENT = """
#include <iostream>

int main() {
    std::cout << "Hello from harmonyApp!" << std::endl;
    return 0;
}
"""
    
    /**
     * Creates my_main.cpp in the project root if it doesn't exist.
     * Returns true if file was created or already exists.
     */
    fun ensureHarmonyExists(project: Project): Boolean {
        val basePath = project.basePath ?: return false
        val HarmonyFile = File(basePath, "my_main.cpp")
        
        if (HarmonyFile.exists()) {
            return true
        }
        
        return try {
            HarmonyFile.writeText(DEFAULT_CPP_CONTENT.trimIndent())
            
            // Refresh VFS to make IDE aware of the new file
            VfsUtil.markDirtyAndRefresh(false, false, false, HarmonyFile)
            
            println("Created my_main.cpp at: ${HarmonyFile.absolutePath}")
            true
        } catch (e: Exception) {
            println("Failed to create my_main.cpp: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}
