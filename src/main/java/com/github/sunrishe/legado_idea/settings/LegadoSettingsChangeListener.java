package com.github.sunrishe.legado_idea.settings;

import com.intellij.util.messages.Topic;

public interface LegadoSettingsChangeListener {
    Topic<LegadoSettingsChangeListener> TOPIC = Topic.create(
            "LegadoSettingsChanged", LegadoSettingsChangeListener.class);

    void settingsChanged();
}
