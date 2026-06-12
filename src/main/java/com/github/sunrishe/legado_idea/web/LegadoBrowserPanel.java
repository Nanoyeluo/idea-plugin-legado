package com.github.sunrishe.legado_idea.web;

import com.github.sunrishe.legado_idea.settings.LegadoSettings;
import com.github.sunrishe.legado_idea.settings.LegadoSettingsChangeListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefClient;
import com.intellij.ui.jcef.JBCefJSQuery;
import com.intellij.util.messages.MessageBusConnection;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LegadoBrowserPanel {
    private static final Logger LOG = Logger.getInstance(LegadoBrowserPanel.class);
    private static final String SCHEME = "http";
    private static final String DOMAIN = "legado-idea";
    private static boolean schemeRegistered = false;

    private final JPanel panel;
    private final JBCefBrowser browser;
    private final MessageBusConnection messageBusConnection;
    private final AtomicBoolean bridgeInstalled = new AtomicBoolean(false);

    public LegadoBrowserPanel() {
        LOG.info("LegadoBrowserPanel constructor started");
        if (!JBCefApp.isSupported()) {
            LOG.warn("JBCefApp.isSupported() returned false, creating fallback panel");
            panel = createJcefFallbackPanel();
            browser = null;
            messageBusConnection = null;
            return;
        }

        JBCefBrowser b = null;
        try {
            // isSupported() may return true while getInstance() still fails in some JBR setups.
            JBCefApp.getInstance();
            registerScheme();
            b = new JBCefBrowser();
            b.getJBCefClient().setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, 1);
            installBridgeOnLoad(b);
            b.loadURL(SCHEME + "://" + DOMAIN + "/index.html");
        } catch (Exception e) {
            LOG.warn("JCEF is not available in this runtime", e);
            if (b != null) {
                try { b.dispose(); } catch (Exception ignored) {}
            }
            panel = createJcefFallbackPanel();
            browser = null;
            messageBusConnection = null;
            return;
        }

        browser = b;
        panel = new JPanel(new BorderLayout());
        panel.add(browser.getComponent(), BorderLayout.CENTER);
        messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
        messageBusConnection.subscribe(LegadoSettingsChangeListener.TOPIC, this::reload);
        LOG.info("LegadoBrowserPanel constructor completed, subscribed to settings changes");
    }

    private static JPanel createJcefFallbackPanel() {
        JPanel p = new JPanel(new BorderLayout());
        JTextArea text = new JTextArea(
                "当前运行环境不支持 JCEF（嵌入式浏览器）。\n\n" +
                        "请使用 bundled JetBrains Runtime (JBR) 启动 IDE：\n" +
                        "Help → Find Action → \"Choose Boot JDK...\" → 选择 JetBrains Runtime。\n\n" +
                        "若通过 ./gradlew runIde 启动，请确认 build.gradle 中声明的 IntelliJ 平台依赖包含 JCEF 版 JBR。"
        );
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        p.add(new JScrollPane(text), BorderLayout.CENTER);
        return p;
    }

    public @NotNull JComponent getComponent() {
        return panel;
    }

    public void dispose() {
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
        }
        if (browser != null) {
            browser.dispose();
        }
    }

    private static synchronized void registerScheme() {
        if (schemeRegistered) return;
        org.cef.CefApp.getInstance().registerSchemeHandlerFactory(
                SCHEME, DOMAIN, new LegadoSchemeHandlerFactory());
        schemeRegistered = true;
    }

    private void installBridgeOnLoad(JBCefBrowser browser) {
        browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadingStateChange(org.cef.browser.CefBrowser cefBrowser,
                                             boolean isLoading,
                                             boolean canGoBack,
                                             boolean canGoForward) {
                if (!isLoading && bridgeInstalled.compareAndSet(false, true)) {
                    installJsBridge(browser);
                }
            }
        }, browser.getCefBrowser());
    }

    private void installJsBridge(JBCefBrowser browser) {
        LOG.info("LegadoBrowserPanel.installJsBridge() called");
        @SuppressWarnings("removal")
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
        String frameUrl = browser.getCefBrowser().getURL();
        if (frameUrl == null || frameUrl.isBlank()) {
            frameUrl = SCHEME + "://" + DOMAIN + "/index.html";
        }
        browser.getCefBrowser().executeJavaScript(script, frameUrl, 0);
        LOG.info("LegadoBrowserPanel.installJsBridge() script executed on frame: " + frameUrl);
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
        LOG.info("LegadoBrowserPanel.reload() called");
        if (browser == null) {
            LOG.warn("LegadoBrowserPanel.reload() browser is null, skipping");
            return;
        }
        bridgeInstalled.set(false);
        String cacheBustUrl = SCHEME + "://" + DOMAIN + "/index.html?t=" + System.currentTimeMillis();
        browser.loadURL(cacheBustUrl);
        LOG.info("LegadoBrowserPanel.reload() loadURL invoked: " + cacheBustUrl);
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
