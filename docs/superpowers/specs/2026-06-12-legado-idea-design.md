# IDEA 版阅读APP（legado）插件设计文档

日期：2026-06-12  
状态：待实现

---

## 背景与目标

复刻 VSCode 插件 [legado-vscode](https://github.com/sunrishe/legado-vscode)，在 IntelliJ IDEA 中提供相同功能：连接手机端阅读 APP 的 Web 服务，浏览书架、阅读章节、管理书源/订阅源。

---

## 1. 整体架构

- **构建工具**：Gradle（Groovy DSL）+ Java 17/21，使用新版 IntelliJ Platform Gradle Plugin。
- **前端**：复用 `legado-vscode` 的 Vue 前端（`web/`），构建产物（`web/dist`）打包到 `src/main/resources/web/`。
- **宿主**：IDEA ToolWindow，内部嵌入 JCEF 浏览器面板（`JBCefBrowser`）加载本地前端资源。
- **配置持久化**：使用 `PersistentStateComponent` 保存 `webServeUrl` 和 `panelTitle`。
- **设置入口**：在 **Settings | Tools | 阅读APP** 中配置地址和标题。
- **命令入口**：注册 `Legado: 打开阅读APP书架` Action，激活 ToolWindow。

关键技术点：把 VSCode 的 `acquireVsCodeApi` 桥接到 IDEA 的 JCEF JS 桥，这样现有前端里的 `postMessage({command:'setConfiguration'...})` 和 `postMessage({command:'reload'})` 无需改动即可生效。

---

## 2. 核心组件与数据流

### 2.1 核心 Java 类

| 类名 | 职责 |
|------|------|
| `LegadoToolWindowFactory` | 实现 `ToolWindowFactory`，在 ToolWindow 里放置 `LegadoBrowserPanel`。 |
| `LegadoBrowserPanel` | 封装 JCEF 浏览器：解压静态资源、加载入口 HTML、注入 `legadoWebServeUrl`、注册 `acquireVsCodeApi` 桥。 |
| `LegadoSettings` / `LegadoSettingsState` | `PersistentStateComponent`，持久化 `webServeUrl`、`panelTitle`。 |
| `LegadoSettingsConfigurable` | 设置页面 UI（URL 输入框、标题输入框）。 |
| `OpenLegadoAction` / `OpenLegadoService` | 打开/聚焦 ToolWindow 的 Action。 |

### 2.2 运行流程

1. 插件启动时注册 ToolWindow 和 Action。
2. 用户执行 Action 后，`LegadoToolWindowFactory.createToolWindowContent` 被调用。
3. `LegadoBrowserPanel` 检查 `JBCefApp.isSupported()`，不支持则显示提示。
4. 将 `resources/web` 复制到系统临时目录，用 `file://` 加载 `index.html`。
5. 在 HTML `<head>` 中注入脚本，把 `LegadoSettings.getWebServeUrl()` 写入 `localStorage.legadoWebServeUrl`。
6. Vue 应用启动后按该地址调用 Legado APP 的 HTTP/WebSocket API。
7. 用户在前端点击“基本设定”修改地址 → 调用 `acquireVsCodeApi().postMessage` → Java 收到后更新 `LegadoSettings` 并刷新浏览器。

### 2.3 前端侧改动

- 因为 `web/src/api/web.js` 通过 `typeof acquireVsCodeApi === 'function'` 判断环境，我们只需要在加载页面时注入该函数即可。
- 通过 JCEF 的 `JBCefJSQuery` 或 `CefMessageRouter` 把前端消息转发到 Java。

---

## 3. 异常处理与边界情况

- **JCEF 不可用**：检测到不支持时，在 ToolWindow 里显示提示，引导用户换用支持 JCEF 的 JetBrains Runtime。
- **资源解压失败**：复制 `resources/web` 失败时，通过 `Notifications.Bus.notify` 弹出错误通知，并提供重试入口。
- **地址校验**：Settings 页面沿用 VSCode 插件的正则校验（`http://IP:port` 格式）。
- **配置同步**：用户从 IDEA 设置页修改地址后，再次加载页面会注入新值覆盖 `localStorage`。
- **多实例控制**：通过单例 `LegadoBrowserPanel` 保证同一时刻只有一个有效浏览器实例；关闭 ToolWindow 时释放 JCEF 资源。
- **跨域**：JCEF 加载本地页面访问局域网 Legado 服务，默认无额外 CORS 限制。

---

## 4. 测试计划

- **构建测试**：`./gradlew build` 成功，插件包可生成。
- **沙箱启动测试**：`./gradlew runIde` 后：
  - 执行 Action 打开 ToolWindow，能看到书架加载界面；
  - 在设置页填入 `http://192.168.x.x:1122`；
  - 确认前端能拉取书架、进入阅读页；
  - 测试 `W/S/A/D`、方向键、`Q/E/R` 快捷键；
  - 测试暗黑主题切换；
  - 测试书源/订阅源编辑器页面能正常打开并保存。
- **回归测试**：关闭 ToolWindow 再次打开，地址和最近阅读状态保持一致。
- **单元测试**：对 `LegadoSettingsState` 的序列化/反序列化、URL 校验工具类写少量 JUnit 测试。

> 注：核心阅读功能依赖真实 Legado APP Web 服务，端到端验证以手动测试为主。

---

## 5. 项目目录建议

```text
idea-plugin-moyu/
├── build.gradle
├── settings.gradle
├── gradle/
├── src/
│   ├── main/
│   │   ├── java/com/github/sunrishe/legado_idea/
│   │   │   ├── LegadoToolWindowFactory.java
│   │   │   ├── LegadoBrowserPanel.java
│   │   │   ├── OpenLegadoAction.java
│   │   │   ├── settings/
│   │   │   │   ├── LegadoSettings.java
│   │   │   │   ├── LegadoSettingsState.java
│   │   │   │   └── LegadoSettingsConfigurable.java
│   │   │   └── util/
│   │   │       └── UrlValidator.java
│   │   └── resources/
│   │       ├── META-INF/plugin.xml
│   │       └── web/            # legado-vscode/web/dist 构建产物
│   └── test/java/...
└── frontend/                   # 可选：legado-vscode/web 源码副本，用于重新构建
```

---

## 6. 依赖与前置条件

- IntelliJ IDEA 2024.3 及以上。
- Gradle + Java 17/21。
- Node.js + Yarn（用于重新构建前端产物）。
- 手机端阅读 APP 开启 Web 服务，且电脑与手机处于同一局域网。

---

## 7. 后续可扩展项

- 把阅读页以 Editor Tab 形式打开，提供更大阅读区域。
- 在 IDEA 状态栏增加连接状态小部件。
- 支持通过通知提示“最近阅读章节更新”。
