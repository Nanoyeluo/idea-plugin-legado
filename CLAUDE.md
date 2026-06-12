# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

这是一个 IntelliJ IDEA 插件，复刻了 [legado-vscode](https://github.com/sunrishe/legado-vscode) 的功能：在 IDEA 中通过 JCEF 浏览器嵌入阅读 APP（Legado）的 Web 前端，连接手机端 Legado 的 Web 服务，实现书架浏览、阅读、书源/订阅源编辑。

- 插件 ID：`com.github.sunrishe.legado_idea`
- 构建工具：Gradle（Groovy DSL）+ Java 21
- 前端：Vue 3 + Vite，源码位于 `frontend/`，构建产物打包到 `src/main/resources/web/`
- 最低 IDE 版本：IntelliJ IDEA 2024.3（build 243）

## 常用命令

### 生成 Gradle Wrapper（当前缺少 `gradlew`/`gradlew.bat`）

```bash
gradle wrapper --gradle-version 8.9
```

生成后使用 `./gradlew`（Linux/macOS）或 `gradlew.bat`（Windows）代替 `gradle`。

### 构建插件

```bash
./gradlew build
```

该任务会先执行 `processResources`，其依赖 `copyWeb`，`copyWeb` 会调用 `frontend` 下的 `npm run build` 重新构建前端，并将 `frontend/dist` 复制到 `src/main/resources/web/`。

### 运行沙箱 IDE 验证插件

```bash
./gradlew runIde
```

### 单独运行测试

```bash
./gradlew test --tests "com.github.sunrishe.legado_idea.util.UrlValidatorTest"
./gradlew test --tests "com.github.sunrishe.legado_idea.settings.LegadoSettingsStateTest"
```

注意：`build.gradle` 中当前禁用了测试执行（`tasks.test { enabled = false }`），需要先移除或改为 `enabled = true` 才能运行测试。

### 前端开发

```bash
cd frontend
pnpm install
pnpm dev
```

开发前需创建 `.env.development` 并设置 `VITE_API` 为 Legado Web 服务地址：

```bash
echo "VITE_API=http://<ip>:<port>" > .env.development
```

前端路由：
- `/`：书架
- `/#/bookSource`：书源编辑
- `/#/rssSource`：订阅源编辑

### 构建前端产物

```bash
cd frontend
pnpm build
```

或在项目根目录通过 Gradle 一并触发：

```bash
./gradlew copyWeb
```

## 架构说明

### 插件入口

`src/main/resources/META-INF/plugin.xml` 声明了三个核心扩展：

1. **ToolWindow** `阅读`：右侧工具窗口，由 `LegadoToolWindowFactory` 创建。
2. **Application Service** `LegadoSettings`：通过 `PersistentStateComponent` 持久化配置（`webServeUrl`、`panelTitle`），存储文件为 `legado-idea.xml`。
3. **Application Configurable** `阅读APP`：设置页面，位于 `Settings | Tools | 阅读APP`，实现类为 `LegadoSettingsConfigurable`。

此外注册了一个 Action `Legado.OpenBookshelf`，放在 `Tools` 菜单末尾，用于激活 ToolWindow。

### JCEF 浏览器与本地资源加载

`LegadoBrowserPanel` 创建 `JBCefBrowser`，加载自定义 Scheme `http://legado-idea/index.html`。静态资源由 `LegadoSchemeHandlerFactory` + `LegadoResourceHandler` 从 `src/main/resources/web/` 提供。

处理 `index.html` 时，`LegadoResourceHandler` 会在 `<head>` 中注入一段脚本：

- 将 `LegadoSettings.getWebServeUrl()` 写入 `localStorage.legadoWebServeUrl`
- 定义 `window.acquireVsCodeApi`，使前端以为自己运行在 VSCode Webview 环境中

### JavaScript 桥

页面加载完成后，`LegadoBrowserPanel` 通过 `JBCefJSQuery` 注入 `window.__legadoIdeaQuery`，前端调用 `acquireVsCodeApi().postMessage(...)` 的消息会经由该桥传回 Java。

目前 Java 端处理两条消息：

- `command: "setConfiguration"`，`key: "legado-vscode.webServeUrl"`：更新持久化的 Web 服务地址。
- `command: "reload"`：重新加载 `http://legado-idea/index.html`。

### 前端与 Legado 服务通信

前端 `frontend/src/api/web.js` 从 `localStorage.legadoWebServeUrl` 读取地址，再通过 `frontend/src/api/axios.js` 发起 HTTP 请求到 Legado APP。非插件环境下前端会回退到 `import.meta.env.VITE_API` 或 `location.origin`。

### 配置校验

`LegadoSettingsConfigurable.apply()` 使用 `UrlValidator.isValid()` 校验地址格式，当前仅接受 `http://IP:PORT` 或 `https://IP:PORT` 的 IPv4 形式。

## 修改注意事项

- `src/main/resources/web/` 是构建产物，不应手动修改；修改后运行 `./gradlew copyWeb` 会被覆盖。
- 前端构建任务 `buildWeb` 在 Windows 上通过 `cmd /c npm run build` 执行，其他平台直接调用 `npm run build`。项目前端实际使用 pnpm，但 Gradle 任务当前调用的是 `npm`。
- `tasks.test { enabled = false }` 会导致 CI 或本地测试被跳过，运行测试前需要启用。
- JCEF 需要 bundled JetBrains Runtime 才可用，`LegadoBrowserPanel` 已做检测并显示中文提示。
- ToolWindow ID 在 `plugin.xml` 和 `OpenLegadoAction` 中均为 `"阅读"`，修改时请保持一致。
- Scheme 名称 `http://legado-idea` 在 `LegadoBrowserPanel`、`LegadoResourceHandler`、`LegadoSchemeHandlerFactory` 中保持一致。
