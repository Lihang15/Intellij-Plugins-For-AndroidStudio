package com.eazytec.plugins.template;

import java.util.HashSet;
import java.util.Set;

/**
 * KMP 项目设置
 */
public class KMPProjectSettings {
    private Set<String> selectedTemplates = new HashSet<>();
    
    public KMPProjectSettings() {
        // 默认选中 androidApp
        selectedTemplates.add("androidApp");
    }
    
    public Set<String> getSelectedTemplates() {
        return selectedTemplates;
    }
    
    public void setSelectedTemplates(Set<String> selectedTemplates) {
        this.selectedTemplates = selectedTemplates;
    }
}
