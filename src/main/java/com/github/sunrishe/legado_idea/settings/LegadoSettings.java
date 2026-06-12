package com.github.sunrishe.legado_idea.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "LegadoSettings", storages = @Storage("legado-idea.xml"))
public final class LegadoSettings implements PersistentStateComponent<LegadoSettingsState> {

    private final LegadoSettingsState state = new LegadoSettingsState();

    public static LegadoSettings getInstance() {
        return ApplicationManager.getApplication().getService(LegadoSettings.class);
    }

    @Override
    public @Nullable LegadoSettingsState getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull LegadoSettingsState state) {
        XmlSerializerUtil.copyBean(state, this.state);
    }

    public String getWebServeUrl() {
        return state.webServeUrl;
    }

    public void setWebServeUrl(String webServeUrl) {
        state.webServeUrl = webServeUrl;
    }

    public String getPanelTitle() {
        return state.panelTitle;
    }

    public void setPanelTitle(String panelTitle) {
        state.panelTitle = panelTitle;
    }
}
