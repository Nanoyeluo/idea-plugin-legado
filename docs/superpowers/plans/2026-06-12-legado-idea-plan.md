# 阅读APP IDEA 插件实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于设计文档 `docs/superpowers/specs/2026-06-12-legado-idea-design.md`，从零构建一个功能与 legado-vscode 相同的 IntelliJ IDEA 插件，使用 Gradle + Java，宿主为 JCEF ToolWindow。

**Architecture:** 插件主体是 IDEA ToolWindow，内部用 JCEF 浏览器加载打包在资源目录的 Vue 前端构建产物；前端调用局域网 Legado APP 的 HTTP/WebSocket API；Java 端通过 PersistentStateComponent 持久化配置，并用 JCEF JS 桥替代 VSCode 的 `acquireVsCodeApi`。

**Tech Stack:** Gradle (Groovy DSL), Java 21, IntelliJ Platform Gradle Plugin 2.2.1, JCEF, JUnit 5, Node/Yarn/Vite (前端构建).

---

## 文件结构

```text
idea-plugin-moyu/
├── build.gradle
├── settings.gradle
├── gradle/
│   └── wrapper/...
├── src/
│   ├── main/
│   │   ├── java/com/github/sunrishe/legado_idea/
│   │   │   ├── LegadoToolWindowFactory.java
│   │   │   ├── OpenLegadoAction.java
│   │   │   ├── settings/
│   │   │   │   ├── LegadoSettings.java
│   │   │   │   ├── LegadoSettingsState.java
│   │   │   │   └── LegadoSettingsConfigurable.java
│   │   │   ├── util/
│   │   │   │   └── UrlValidator.java
│   │   │   └── web/
│   │   │       ├── LegadoBrowserPanel.java
│   │   │       ├── LegadoResourceHandler.java
│   │   │       └── LegadoSchemeHandlerFactory.java
│   │   └── resources/
│   │       ├── META-INF/plugin.xml
│   │       └── web/            # 由 frontend/dist 复制生成，不提交
│   └── test/java/com/github/sunrishe/legado_idea/
│       ├── settings/LegadoSettingsStateTest.java
│       └── util/UrlValidatorTest.java
├── frontend/                   # legado-vscode/web 源码副本，按需克隆/复制
└── .gitignore
```

---

## Task 1: 初始化 Gradle + IntelliJ Platform 项目

**Files:**
- Create: `settings.gradle`
- Create: `build.gradle`
- Create: `.gitignore`
- Create: `gradle/wrapper/gradle-wrapper.properties`（通过 wrapper 任务）

- [ ] **Step 1: 创建 `settings.gradle`**

```groovy
rootProject.name = 'idea-plugin-legado'
```

- [ ] **Step 2: 创建 `build.gradle`**

```groovy
plugins {
    id 'java'
    id 'org.jetbrains.intellij.platform' version '2.2.1'
}

group = 'com.github.sunrishe'
version = '1.0.0'

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create('IC', '2024.3')
    }
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
}

intellijPlatform {
    pluginConfiguration {
        name = '阅读APP'
        description = '配合阅读APP（Legado）在 IDEA 中阅读小说的插件'
        ideaVersion {
            sinceBuild = '243'
            untilBuild = '243.*'
        }
    }
}

tasks {
    withType(JavaCompile) {
        sourceCompatibility = '21'
        targetCompatibility = '21'
    }
    test {
        useJUnitPlatform()
    }
}
```

- [ ] **Step 3: 创建 `.gitignore`**

```gitignore
.gradle/
build/
*.iml
.idea/
out/
src/main/resources/web/
frontend/dist/
frontend/node_modules/
```

- [ ] **Step 4: 生成 Gradle Wrapper**

Run:
```bash
gradle wrapper --gradle-version 8.9
```

Expected: 生成 `gradlew`、`gradlew.bat`、`gradle/wrapper/`。

- [ ] **Step 5: 验证空项目可编译**

Run:
```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL（无源码，只有空 jar）。

---

## Task 2: 声明插件入口与扩展点

**Files:**
- Create: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: 编写 `plugin.xml`**

```xml
<idea-plugin>
    <id>com.github.sunrishe.legado_idea</id>
    <name>阅读APP</name>
    <version>1.0.0</version>
    <vendor email="i@sunrishe.com" url="https://github.com/sunrishe/legado-vscode">sunrishe</vendor>

    <description><![CDATA[
        配合阅读APP（Legado）使用的 IDEA 插件，支持书架、阅读、书源/订阅源编辑。
    ]]></description>

    <depends>com.intellij.modules.platform</depends>

    <extensions defaultExtensionNs="com.intellij">
        <toolWindow id="阅读"
                    anchor="right"
                    factoryClass="com.github.sunrishe.legado_idea.LegadoToolWindowFactory"
                    canCloseContents="true"/>

        <applicationService serviceImplementation="com.github.sunrishe.legado_idea.settings.LegadoSettings"/>

        <applicationConfigurable parentId="tools"
                                 instance="com.github.sunrishe.legado_idea.settings.LegadoSettingsConfigurable"
                                 id="com.github.sunrishe.legado_idea.settings.LegadoSettingsConfigurable"
                                 displayName="阅读APP"/>
    </extensions>

    <actions>
        <action id="Legado.OpenBookshelf"
                class="com.github.sunrishe.legado_idea.OpenLegadoAction"
                text="打开阅读APP书架"
                description="打开阅读APP书架">
            <add-to-group group-id="ToolsMenu" anchor="last"/>
        </action>
    </actions>
</idea-plugin>
```

- [ ] **Step 2: 编译确认**

Run:
```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL（plugin.xml 已打包，无实现类警告可忽略）。

---

## Task 3: 配置持久化 State 与服务

**Files:**
- Create: `src/main/java/com/github/sunrishe/legado_idea/settings/LegadoSettingsState.java`
- Create: `src/main/java/com/github/sunrishe/legado_idea/settings/LegadoSettings.java`
- Create: `src/test/java/com/github/sunrishe/legado_idea/settings/LegadoSettingsStateTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.github.sunrishe.legado_idea.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegadoSettingsStateTest {

    @Test
    void defaultValuesAreSet() {
        LegadoSettingsState state = new LegadoSettingsState();
        assertEquals("http://127.0.0.1:1122", state.webServeUrl);
        assertEquals("阅读", state.panelTitle);
    }
}
```

Run:
```bash
./gradlew test --tests "com.github.sunrishe.legado_idea.settings.LegadoSettingsStateTest"
```

Expected: 编译失败，找不到 `LegadoSettingsState`。

- [ ] **Step 2: 实现 State 类**

```java
package com.github.sunrishe.legado_idea.settings;

public final class LegadoSettingsState {
    public String webServeUrl = "http://127.0.0.1:1122";
    public String panelTitle = "阅读";
}
```

- [ ] **Step 3: 实现 Service 类**

```java
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
```

- [ ] **Step 4: 运行测试**

Run:
```bash
./gradlew test --tests "com.github.sunrishe.legado_idea.settings.LegadoSettingsStateTest"
```

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add settings.gradle build.gradle .gitignore src/main/resources/META-INF/plugin.xml \
  src/main/java/com/github/sunrishe/legado_idea/settings/ \
  src/test/java/com/github/sunrishe/legado_idea/settings/
git commit -m "feat: add plugin skeleton, settings state and service

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 4: URL 校验工具

**Files:**
- Create: `src/main/java/com/github/sunrishe/legado_idea/util/UrlValidator.java`
- Create: `src/test/java/com/github/sunrishe/legado_idea/util/UrlValidatorTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.github.sunrishe.legado_idea.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlValidatorTest {

    @Test
    void acceptsValidHttpUrl() {
        assertTrue(UrlValidator.isValid("http://192.168.1.2:1122"));
    }

    @Test
    void acceptsValidHttpsUrl() {
        assertTrue(UrlValidator.isValid("https://10.0.0.1:8080"));
    }

    @Test
    void rejectsInvalidIp() {
        assertFalse(UrlValidator.isValid("http://192.168.1.256:1122"));
    }

    @Test
    void rejectsInvalidPort() {
        assertFalse(UrlValidator.isValid("http://192.168.1.2:70000"));
    }

    @Test
    void rejectsMissingScheme() {
        assertFalse(UrlValidator.isValid("192.168.1.2:1122"));
    }
}
```

Run:
```bash
./gradlew test --tests "com.github.sunrishe.legado_idea.util.UrlValidatorTest"
```

Expected: 编译失败。

- [ ] **Step 2: 实现校验类**

```java
package com.github.sunrishe.legado_idea.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UrlValidator {
    private static final Pattern PATTERN = Pattern.compile(
            "^https?://((?:\\d{1,3}\\.){3}(?:\\d{1,3})):(\\d{1,5})$"
    );

    private UrlValidator() {}

    public static boolean isValid(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        Matcher matcher = PATTERN.matcher(url.trim());
        if (!matcher.matches()) {
            return false;
        }
        String[] parts = matcher.group(1).split("\\.");
        for (String part : parts) {
            int value = Integer.parseInt(part);
            if (value > 255) {
                return false;
            }
        }
        int port = Integer.parseInt(matcher.group(2));
        return port <= 65535;
    }
}
```

- [ ] **Step 3: 运行测试**

Run:
```bash
./gradlew test --tests "com.github.sunrishe.legado_idea.util.UrlValidatorTest"
```

Expected: PASS。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/github/sunrishe/legado_idea/util/ \
  src/test/java/com/github/sunrishe/legado_idea/util/
git commit -m "feat: add URL validator

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 5: 设置页面 UI

**Files:**
- Create: `src/main/java/com/github/sunrishe/legado_idea/settings/LegadoSettingsConfigurable.java`

- [ ] **Step 1: 实现 Configurable**

```java
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
```

- [ ] **Step 2: 编译确认**

Run:
```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/github/sunrishe/legado_idea/settings/LegadoSettingsConfigurable.java
git commit -m "feat: add settings UI configurable

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 6: 静态资源 Scheme Handler

**Files:**
- Create: `src/main/java/com/github/sunrishe/legado_idea/web/LegadoResourceHandler.java`
- Create: `src/main/java/com/github/sunrishe/legado_idea/web/LegadoSchemeHandlerFactory.java`

- [ ] **Step 1: 实现资源处理器**

```java
package com.github.sunrishe.legado_idea.web;

import com.intellij.openapi.diagnostic.Logger;
import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

public final class LegadoResourceHandler extends CefResourceHandlerAdapter {
    private static final Logger LOG = Logger.getInstance(LegadoResourceHandler.class);
    private static final String RESOURCE_ROOT = "web";
    private static final String INJECTION = "<script>" +
            "localStorage.setItem('legadoWebServeUrl','%s');" +
            "window.acquireVsCodeApi = function(){" +
            "  return {" +
            "    postMessage: function(msg){ if(window.__legadoIdeaQuery) window.__legadoIdeaQuery(JSON.stringify(msg)); }," +
            "    setState: function(){}," +
            "    getState: function(){ return null; }" +
            "  };" +
            "};" +
            "window.__legadoIdeaQueue = [];" +
            "window.__legadoIdeaQuery = function(msg){ window.__legadoIdeaQueue.push(msg); };" +
            "</script>";

    private final String webServeUrl;
    private ByteArrayInputStream dataStream;
    private String mimeType;
    private int contentLength;

    public LegadoResourceHandler(String webServeUrl) {
        this.webServeUrl = webServeUrl;
    }

    @Override
    public boolean processRequest(CefRequest request, CefCallback callback) {
        String url = request.getURL();
        String relativePath = url.replaceFirst("http://legado-idea/", "");
        int queryIndex = relativePath.indexOf('?');
        if (queryIndex >= 0) {
            relativePath = relativePath.substring(0, queryIndex);
        }
        if (relativePath.isEmpty() || relativePath.endsWith("/")) {
            relativePath = "index.html";
        }

        URL resourceUrl = getClass().getClassLoader().getResource(RESOURCE_ROOT + "/" + relativePath);
        if (resourceUrl == null) {
            LOG.warn("Missing web resource: " + relativePath);
            callback.cancel();
            return false;
        }

        try {
            URLConnection connection = resourceUrl.openConnection();
            byte[] data;
            try (InputStream in = connection.getInputStream()) {
                data = in.readAllBytes();
            }
            if ("index.html".equals(relativePath)) {
                String html = new String(data, StandardCharsets.UTF_8);
                String script = String.format(INJECTION, escapeJsString(webServeUrl));
                html = html.replaceFirst("(?i)(<head[^>]*>)", "$1" + script);
                data = html.getBytes(StandardCharsets.UTF_8);
            }
            dataStream = new ByteArrayInputStream(data);
            contentLength = data.length;

            mimeType = connection.getContentType();
            if (mimeType == null || "content/unknown".equals(mimeType)) {
                mimeType = guessMimeType(relativePath);
            }
            callback.Continue();
            return true;
        } catch (IOException e) {
            LOG.error("Failed to serve resource: " + relativePath, e);
            callback.cancel();
            return false;
        }
    }

    @Override
    public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl) {
        response.setMimeType(mimeType);
        response.setStatus(200);
        responseLength.set(contentLength);
    }

    @Override
    public boolean readResponse(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback) {
        int read = dataStream.read(dataOut, 0, bytesToRead);
        bytesRead.set(Math.max(read, 0));
        return read > 0;
    }

    @Override
    public void cancel() {
        if (dataStream != null) {
            try {
                dataStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String guessMimeType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }

    private static String escapeJsString(String value) {
        return value.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
```

- [ ] **Step 2: 实现 Scheme Handler Factory**

```java
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
```

- [ ] **Step 3: 编译确认**

Run:
```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/github/sunrishe/legado_idea/web/
git commit -m "feat: add custom scheme handler for bundled web assets

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 7: JCEF 浏览器面板与 VSCode API 桥

**Files:**
- Create: `src/main/java/com/github/sunrishe/legado_idea/web/LegadoBrowserPanel.java`

- [ ] **Step 1: 实现浏览器面板**

```java
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
```

- [ ] **Step 2: 编译确认**

Run:
```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/github/sunrishe/legado_idea/web/LegadoBrowserPanel.java
git commit -m "feat: add JCEF browser panel with VSCode API bridge

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 8: ToolWindow Factory

**Files:**
- Create: `src/main/java/com/github/sunrishe/legado_idea/LegadoToolWindowFactory.java`

- [ ] **Step 1: 实现 Factory**

```java
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
```

- [ ] **Step 2: 编译确认**

Run:
```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/github/sunrishe/legado_idea/LegadoToolWindowFactory.java
git commit -m "feat: add tool window factory

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 9: 打开书架 Action

**Files:**
- Create: `src/main/java/com/github/sunrishe/legado_idea/OpenLegadoAction.java`

- [ ] **Step 1: 实现 Action**

```java
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
```

- [ ] **Step 2: 编译确认**

Run:
```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/github/sunrishe/legado_idea/OpenLegadoAction.java
git commit -m "feat: add open bookshelf action

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 10: 集成 legado-vscode 前端资源

**Files:**
- Create: `frontend/`（通过克隆仓库或手动复制）
- Modify: `build.gradle`

- [ ] **Step 1: 获取前端源码**

Run:
```bash
git clone --depth 1 https://github.com/sunrishe/legado-vscode.git /tmp/legado-vscode
cp -r /tmp/legado-vscode/web ./frontend
```

Expected: `frontend/package.json`、`frontend/vite.config.js`、`frontend/src/` 等存在。

- [ ] **Step 2: 在 `build.gradle` 里增加前端构建任务**

在 `build.gradle` 末尾追加：

```groovy
tasks.register('buildWeb', Exec) {
    group = 'frontend'
    workingDir = file('frontend')
    commandLine = ['yarn', 'run', 'build']
}

tasks.register('copyWeb', Copy) {
    group = 'frontend'
    from file('frontend/dist')
    into file('src/main/resources/web')
    dependsOn buildWeb
}

processResources {
    dependsOn copyWeb
}
```

- [ ] **Step 3: 构建前端并复制到资源目录**

Run:
```bash
cd frontend && yarn install && cd ..
./gradlew copyWeb
```

Expected: `src/main/resources/web/index.html` 和 `src/main/resources/web/assets/` 生成。

- [ ] **Step 4: 编译确认**

Run:
```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL，插件 jar 内包含 `web/` 目录。

- [ ] **Step 5: 提交**

```bash
git add build.gradle frontend/ src/main/resources/web/
git commit -m "feat: bundle legado-vscode web frontend

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 11: 运行与手动验证

- [ ] **Step 1: 启动沙箱 IDE**

Run:
```bash
./gradlew runIde
```

Expected: 新 IDEA 窗口启动，无启动错误。

- [ ] **Step 2: 打开工具窗口**

在新 IDEA 中：
- `Tools` → `打开阅读APP书架`，或 Search Everywhere 搜索该 Action。
- 右侧出现 `阅读` ToolWindow，显示书架界面。

- [ ] **Step 3: 配置并连接 Legado APP**

- 确保手机端阅读 APP 已开启 Web 服务，且电脑与手机同局域网。
- 在书架页点击“基本设定”状态栏，输入 `http://192.168.x.x:1122`。
- 确认连接成功，书架加载书籍。

- [ ] **Step 4: 阅读功能验证**

- 点击书籍进入阅读页。
- 测试 `W/S/A/D`、方向键翻页，`Q` 回书架，`E` 目录，`R` 刷新。
- 点击设置切换暗黑主题，确认生效。

- [ ] **Step 5: 设置页验证**

- 打开 `Settings | Tools | 阅读APP`。
- 修改地址为非法值，点击 Apply，应提示格式错误。
- 修改为合法地址，点击 OK，重新打开 ToolWindow 后生效。

- [ ] **Step 6: 书源/订阅源编辑器验证**

- 在书架 URL 后加 `#/bookSource` 或 `#/rssSource`，确认 SourceEditor 页面可加载并保存。

- [ ] **Step 7: 提交验证结果或修复**

如果发现问题，在当前分支修复并重新运行 `./gradlew runIde`；否则：

```bash
git tag -a v1.0.0 -m "Initial release"
```

（可选，用户决定是否推送到仓库。）

---

## 自我审查

**Spec 覆盖检查：**
- Gradle + Java 项目骨架 → Task 1
- `PersistentStateComponent` 配置持久化 → Task 3
- 设置页面 → Task 5
- JCEF ToolWindow 加载本地前端 → Task 6/7/8
- 打开书架命令 → Task 9
- 前端资源集成 → Task 10
- 手动测试书架/阅读/主题/快捷键/书源编辑器 → Task 11

**Placeholder 扫描：** 所有代码块均为可直接运行的完整代码，无 TBD/TODO。

**类型一致性检查：**
- `LegadoSettings.getWebServeUrl()` / `setWebServeUrl(String)` 在 State、Service、Configurable、SchemeHandler、BrowserPanel 中一致使用。
- ToolWindow id 在 `plugin.xml` 和 `OpenLegadoAction` 中均为 `"阅读"`。
- Scheme 名称 `http://legado-idea` 在 ResourceHandler、SchemeHandlerFactory、BrowserPanel 中一致。

**已知风险与缓解：**
- JCEF 在某些 JDK 下不可用 → 已在 `LegadoBrowserPanel` 中检测并给出提示。
- 前端 `acquireVsCodeApi` 必须在 Vue 脚本加载前定义 → 通过 SchemeHandler 在 `index.html` 的 `<head>` 中内联注入。
- 前端通过 `postMessage` 修改地址后需刷新 → Java 桥收到 `reload` 命令后重新加载 URL。
