package com.eazytec.plugins.template;

import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.platform.GeneratorPeerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

/**
 * KMP 项目生成器配置面板
 */
public class KMPProjectGeneratorPeer extends GeneratorPeerImpl<KMPProjectSettings> {
    
    private JPanel mainPanel;
    private JCheckBox androidAppCheckBox;
    private JCheckBox iosAppCheckBox;
    private JCheckBox myCppAppCheckBox;
    private final KMPProjectSettings settings = new KMPProjectSettings();
    
    public KMPProjectGeneratorPeer() {
        initComponents();
    }
    
    private void initComponents() {
        mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Title
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JLabel titleLabel = new JLabel("Select modules to include:");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        mainPanel.add(titleLabel, gbc);
        
        // Description
        gbc.gridy = 1;
        JLabel descLabel = new JLabel("Choose at least one module for your project:");
        descLabel.setFont(descLabel.getFont().deriveFont(Font.PLAIN, 12f));
        mainPanel.add(descLabel, gbc);
        
        // Add some spacing
        gbc.gridy = 2;
        mainPanel.add(Box.createVerticalStrut(10), gbc);
        
        // Android App Checkbox
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        androidAppCheckBox = new JCheckBox("Android App");
        androidAppCheckBox.setSelected(true); // 默认选中
        mainPanel.add(androidAppCheckBox, gbc);
        
        // iOS App Checkbox
        gbc.gridy = 4;
        iosAppCheckBox = new JCheckBox("iOS App");
        mainPanel.add(iosAppCheckBox, gbc);
        
        // C++ App Checkbox
        gbc.gridy = 5;
        myCppAppCheckBox = new JCheckBox("C++ App");
        mainPanel.add(myCppAppCheckBox, gbc);
        
        // Add vertical glue
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(Box.createVerticalGlue(), gbc);
    }
    
    @NotNull
    @Override
    public JPanel getComponent() {
        return mainPanel;
    }

    
    @NotNull
    @Override
    public KMPProjectSettings getSettings() {
        // 更新选中的模板
        Set<String> selectedTemplates = new HashSet<>();
        if (androidAppCheckBox.isSelected()) {
            selectedTemplates.add("androidApp");
        }
        if (iosAppCheckBox.isSelected()) {
            selectedTemplates.add("iosApp");
        }
        if (myCppAppCheckBox.isSelected()) {
            selectedTemplates.add("myCppApp");
        }
        settings.setSelectedTemplates(selectedTemplates);
        return settings;
    }
    
    @Nullable
    @Override
    public ValidationInfo validate() {
        if (!androidAppCheckBox.isSelected() && 
            !iosAppCheckBox.isSelected() && 
            !myCppAppCheckBox.isSelected()) {
            return new ValidationInfo("Please select at least one module", androidAppCheckBox);
        }
        return null;
    }
    
    @Override
    public boolean isBackgroundJobRunning() {
        return false;
    }
    
    @Override
    public void addSettingsListener(@NotNull SettingsListener listener) {
        // 可以添加监听器来响应设置变化
    }
}
