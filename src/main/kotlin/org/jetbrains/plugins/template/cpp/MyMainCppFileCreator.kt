package org.jetbrains.plugins.template.cpp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

/**
 * Helper to create my_main.cpp file if it doesn't exist.
 */
object MyMainCppFileCreator {
    
    private const val DEFAULT_CPP_CONTENT = """
#include <iostream>

int main() {
    std::cout << "Hello from MyMainApp!" << std::endl;
    return 0;
}
"""
    
    /**
     * Creates my_main.cpp in the project root if it doesn't exist.
     * Returns true if file was created or already exists.
     */
    fun ensureMyMainCppExists(project: Project): Boolean {
        val basePath = project.basePath ?: return false
        val myMainCppFile = File(basePath, "my_main.cpp")
        
        if (myMainCppFile.exists()) {
            return true
        }
        
        return try {
            myMainCppFile.writeText(DEFAULT_CPP_CONTENT.trimIndent())
            
            // Refresh VFS to make IDE aware of the new file
            VfsUtil.markDirtyAndRefresh(false, false, false, myMainCppFile)
            
            println("Created my_main.cpp at: ${myMainCppFile.absolutePath}")
            true
        } catch (e: Exception) {
            println("Failed to create my_main.cpp: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}
