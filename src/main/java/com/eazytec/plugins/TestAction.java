package com.eazytec.plugins;


import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;

public class TestAction extends AnAction {



    @Override
    public void actionPerformed(AnActionEvent e) {

        Messages.showInfoMessage(
                "hello",
                "我可以写java"
        );
    }
}
