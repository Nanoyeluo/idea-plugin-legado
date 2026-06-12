package com.github.sunrishe.legado_idea.web;

import com.github.sunrishe.legado_idea.settings.LegadoSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefSchemeHandlerFactory;
import org.cef.network.CefRequest;
import org.jetbrains.annotations.NotNull;

public final class LegadoSchemeHandlerFactory implements CefSchemeHandlerFactory {
    @Override
    public LegadoResourceHandler create(@NotNull CefBrowser browser,
                                        @NotNull CefFrame frame,
                                        @NotNull String schemeName,
                                        @NotNull CefRequest request) {
        return new LegadoResourceHandler(LegadoSettings.getInstance().getWebServeUrl());
    }
}
