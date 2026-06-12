package com.github.sunrishe.legado_idea;

import com.github.sunrishe.legado_idea.web.LegadoBrowserPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NotNull;

public final class LegadoToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        LegadoBrowserPanel browserPanel = new LegadoBrowserPanel();
        Content content = ContentFactory.getInstance().createContent(
                browserPanel.getComponent(),
                "",
                false
        );
        toolWindow.getContentManager().addContent(content);
        toolWindow.getContentManager().addContentManagerListener(new ContentManagerListener() {
            @Override
            public void contentRemoved(@NotNull ContentManagerEvent event) {
                if (content == event.getContent()) {
                    browserPanel.dispose();
                }
            }
        });
    }
}
