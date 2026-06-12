package com.github.sunrishe.legado_idea.web;

import com.github.sunrishe.legado_idea.settings.LegadoSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class LegadoBrowserPanel {
    private static final Logger LOG = Logger.getInstance(LegadoBrowserPanel.class);
    private static final String SCHEME = "http";
    private static final String DOMAIN = "legado-idea";
    private static boolean schemeRegistered = false;

    private final JPanel panel;
    private final JBCefBrowser browser;
    private volatile boolean bridgeInstalled = false;

    public LegadoBrowserPanel() {
        if (!JBCefApp.isSupported()) {
            panel = new JPanel(new BorderLayout());
            panel.add(new JLabel(
                    "当前环境不支持 JCEF，请使用 bundled JetBrains Runtime 启动 IDE。",
                    SwingConstants.CENTER), BorderLayout.CENTER);
            browser = null;
            return;
        }

        registerScheme();
        browser = new JBCefBrowser();
        installBridgeOnLoad();
        browser.loadURL(SCHEME + "://" + DOMAIN + "/index.html");

        panel = new JPanel(new BorderLayout());
        panel.add(browser.getComponent(), BorderLayout.CENTER);
    }

    public @NotNull JComponent getComponent() {
        return panel;
    }

    public void dispose() {
        if (browser != null) {
            browser.dispose();
        }
    }

    private static synchronized void registerScheme() {
        if (schemeRegistered) return;
        JBCefApp.getInstance().getCefApp().registerSchemeHandlerFactory(
                SCHEME, DOMAIN, new LegadoSchemeHandlerFactory());
        schemeRegistered = true;
    }

    private void installBridgeOnLoad() {
        browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadingStateChange(org.cef.browser.CefBrowser cefBrowser,
                                             boolean isLoading,
                                             boolean canGoBack,
                                             boolean canGoForward) {
                if (!isLoading && !bridgeInstalled) {
                    bridgeInstalled = true;
                    installJsBridge();
                }
            }
        }, browser.getCefBrowser());
    }

    private void installJsBridge() {
        JBCefJSQuery query = JBCefJSQuery.create(browser);
        query.addHandler(request -> {
            handleFrontendMessage(request);
            return new JBCefJSQuery.Response("ok");
        });

        String script = "window.__legadoIdeaQuery = function(msg){ " + query.inject("msg") + " };" +
                "if(window.__legadoIdeaQueue){" +
                "  window.__legadoIdeaQueue.forEach(function(msg){ window.__legadoIdeaQuery(msg); });" +
                "  window.__legadoIdeaQueue = [];" +
                "}";
        browser.getCefBrowser().executeJavaScript(script, SCHEME + "://" + DOMAIN + "/index.html", 0);
    }

    private void handleFrontendMessage(String request) {
        try {
            String command = extractString(request, "command");
            if ("setConfiguration".equals(command)) {
                String key = extractString(request, "key");
                String value = extractString(request, "value");
                if ("legado-vscode.webServeUrl".equals(key) && value != null) {
                    LegadoSettings.getInstance().setWebServeUrl(value);
                }
            } else if ("reload".equals(command)) {
                reload();
            }
        } catch (Exception e) {
            LOG.error("Failed to handle frontend message: " + request, e);
        }
    }

    private void reload() {
        bridgeInstalled = false;
        browser.loadURL(SCHEME + "://" + DOMAIN + "/index.html");
    }

    private static String extractString(String json, String key) {
        String quoted = "\"" + key + "\"";
        int keyIndex = json.indexOf(quoted);
        if (keyIndex < 0) return null;
        int colonIndex = json.indexOf(':', keyIndex + quoted.length());
        if (colonIndex < 0) return null;
        int firstQuote = json.indexOf('"', colonIndex + 1);
        if (firstQuote < 0) return null;
        int secondQuote = json.indexOf('"', firstQuote + 1);
        return secondQuote < 0 ? null : json.substring(firstQuote + 1, secondQuote);
    }
}
