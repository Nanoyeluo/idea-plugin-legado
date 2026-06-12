package com.github.sunrishe.legado_idea;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public final class OpenLegadoAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("阅读");
        if (toolWindow != null) {
            toolWindow.activate(null);
        }
    }
}
