package org.jetbrains.plugins.template.projectsync

import java.nio.file.Files
import java.nio.file.Path

/**
 * Handles file conflicts during project creation by intelligently deciding
 * whether to copy, skip, or merge files.
 *
 * Thread-safe: All methods can be called from any thread.
 */
interface FileConflictResolver {
    /**
     * Determines if a file should be copied from source to target.
     * Returns false if target already exists to avoid conflicts.
     */
    fun shouldCopyFile(source: Path, target: Path): Boolean
    
    /**
     * Merges template settings.gradle.kts content with existing content.
     * Preserves existing module declarations and adds new ones.
     */
    fun mergeSettingsGradle(template: String, existing: String): String
    
    /**
     * Returns a set of file names that should be preserved in the target directory.
     * These files won't be overwritten during project generation.
     */
    fun preserveProjectFiles(targetDir: Path): Set<String>
}

/**
 * Default implementation of FileConflictResolver.
 */
class DefaultFileConflictResolver : FileConflictResolver {
    
    override fun shouldCopyFile(source: Path, target: Path): Boolean {
        // Skip if target already exists to avoid conflicts
        return !Files.exists(target)
    }
    
    override fun mergeSettingsGradle(template: String, existing: String): String {
        // Extract include statements from both files
        val existingIncludes = extractIncludeStatements(existing)
        val templateIncludes = extractIncludeStatements(template)
        
        // Combine includes, removing duplicates
        val allIncludes = (existingIncludes + templateIncludes).distinct()
        
        // Extract configuration sections from template
        val configSection = extractConfigSection(template)
        
        // Build merged content
        return buildString {
            // Root project name from existing or template
            val rootProjectName = extractRootProjectName(existing) 
                ?: extractRootProjectName(template) 
                ?: "project"
            appendLine("rootProject.name = \"$rootProjectName\"")
            
            // Feature preview
            if (template.contains("enableFeaturePreview")) {
                appendLine("enableFeaturePreview(\"TYPESAFE_PROJECT_ACCESSORS\")")
            }
            appendLine()
            
            // Configuration section
            appendLine(configSection)
            appendLine()
            
            // All includes
            allIncludes.forEach { include ->
                appendLine(include)
            }
        }
    }
    
    override fun preserveProjectFiles(targetDir: Path): Set<String> {
        return setOf(
            "local.properties",
            ".idea",
            ".gradle",
            "build",
            ".git",
            ".gitignore"
        )
    }
    
    private fun extractIncludeStatements(content: String): List<String> {
        return content.lines()
            .filter { it.trim().startsWith("include(") }
            .map { it.trim() }
    }
    
    private fun extractRootProjectName(content: String): String? {
        val regex = """rootProject\.name\s*=\s*"([^"]+)"""".toRegex()
        return regex.find(content)?.groupValues?.get(1)
    }
    
    private fun extractConfigSection(content: String): String {
        val lines = content.lines()
        val startIdx = lines.indexOfFirst { it.trim().startsWith("pluginManagement") }
        if (startIdx == -1) return getDefaultConfigSection()
        
        var braceCount = 0
        var endIdx = startIdx
        var foundDependencyManagement = false
        
        for (i in startIdx until lines.size) {
            val line = lines[i]
            braceCount += line.count { it == '{' }
            braceCount -= line.count { it == '}' }
            
            if (line.trim().startsWith("dependencyResolutionManagement")) {
                foundDependencyManagement = true
            }
            
            if (foundDependencyManagement && braceCount == 0) {
                endIdx = i
                break
            }
        }
        
        return if (endIdx > startIdx) {
            lines.subList(startIdx, endIdx + 1).joinToString("\n")
        } else {
            getDefaultConfigSection()
        }
    }
    
    private fun getDefaultConfigSection(): String {
        return """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            
            dependencyResolutionManagement {
                repositories {
                    google()
                    mavenCentral()
                }
            }
        """.trimIndent()
    }
}
