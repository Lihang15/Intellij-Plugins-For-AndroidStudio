package com.eazytec.plugins.template;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.ProjectGeneratorPeer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

/**
 * KMP 项目生成器 - 用于 Android Studio 的 New Project 向导
 */
public class KMPDirectoryProjectGenerator implements DirectoryProjectGenerator<KMPProjectSettings> {
    
    @NotNull
    @Override
    public String getName() {
        return "My KMP Project Template";
    }
    
    @Nullable
    @Override
    public Icon getLogo() {
        return com.intellij.util.PlatformIcons.PROJECT_ICON;
    }
    
    @Override
    public void generateProject(@NotNull Project project,
                                @NotNull VirtualFile baseDir,
                                @NotNull KMPProjectSettings settings,
                                @NotNull Module module) {
        try {
            createProjectStructure(baseDir.getPath(), settings);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create project: " + e.getMessage(), e);
        }
    }
    
    @NotNull
    @Override
    public ValidationResult validate(@NotNull String baseDirPath) {
        // 验证路径
        Path path = Paths.get(baseDirPath);
        if (Files.exists(path) && Files.isDirectory(path)) {
            try {
                if (Files.list(path).findAny().isPresent()) {
                    return new ValidationResult("Directory is not empty");
                }
            } catch (IOException e) {
                return new ValidationResult("Cannot access directory: " + e.getMessage());
            }
        }
        return ValidationResult.OK;
    }
    
    @NotNull
    @Override
    public ProjectGeneratorPeer<KMPProjectSettings> createPeer() {
        return new KMPProjectGeneratorPeer();
    }
    
    /**
     * 创建项目结构
     */
    private void createProjectStructure(String projectPath, KMPProjectSettings settings) throws IOException {
        Path targetPath = Paths.get(projectPath);
        
        // 查找模板路径
        Path templateBasePath = findTemplatePath();
        
        if (templateBasePath == null || !Files.exists(templateBasePath)) {
            throw new IOException("Template directory not found: myKMPProjectTemplate");
        }
        
        // 复制选中的模板
        Set<String> templates = settings.getSelectedTemplates();
        for (String template : templates) {
            Path sourcePath = templateBasePath.resolve(template);
            Path destPath = targetPath.resolve(template);
            
            if (Files.exists(sourcePath)) {
                copyDirectory(sourcePath, destPath);
            }
        }
    }
    
    /**
     * 查找模板路径
     */
    private Path findTemplatePath() {
        // 尝试1: 使用类加载器路径
        try {
            String pluginPath = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            Path path1 = Paths.get(pluginPath).getParent().getParent().getParent().resolve("myKMPProjectTemplate");
            if (Files.exists(path1)) {
                return path1;
            }
        } catch (Exception ignored) {}
        
        // 尝试2: 使用工作目录
        String workspacePath = System.getProperty("user.dir");
        Path path2 = Paths.get(workspacePath, "myKMPProjectTemplate");
        if (Files.exists(path2)) {
            return path2;
        }
        
        // 尝试3: 使用项目基础路径
        Path path3 = Paths.get("/Users/admin/EazyWork/projects/intellij-platform-plugin-template/myKMPProjectTemplate");
        if (Files.exists(path3)) {
            return path3;
        }
        
        return null;
    }
    
    /**
     * 递归复制目录
     */
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
