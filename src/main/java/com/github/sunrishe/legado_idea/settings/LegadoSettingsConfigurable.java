package com.github.sunrishe.legado_idea.settings;

import com.github.sunrishe.legado_idea.util.UrlValidator;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class LegadoSettingsConfigurable implements Configurable {
    private JPanel panel;
    private final JBTextField urlField = new JBTextField();
    private final JBTextField titleField = new JBTextField();

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "阅读APP";
    }

    @Override
    public @Nullable JComponent createComponent() {
        panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("WEB 服务地址：", urlField)
                .addLabeledComponent("窗口标题：", titleField)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        LegadoSettings settings = LegadoSettings.getInstance();
        return !urlField.getText().trim().equals(settings.getWebServeUrl())
                || !titleField.getText().trim().equals(settings.getPanelTitle());
    }

    @Override
    public void apply() throws ConfigurationException {
        String url = urlField.getText().trim();
        if (!UrlValidator.isValid(url)) {
            throw new ConfigurationException("WEB 服务地址格式不正确，应为 http://IP:PORT");
        }
        LegadoSettings settings = LegadoSettings.getInstance();
        settings.setWebServeUrl(url);
        settings.setPanelTitle(titleField.getText().trim());
    }

    @Override
    public void reset() {
        LegadoSettings settings = LegadoSettings.getInstance();
        urlField.setText(settings.getWebServeUrl());
        titleField.setText(settings.getPanelTitle());
    }
}
