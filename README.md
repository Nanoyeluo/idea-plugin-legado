# Legado IDEA 插件

在 IntelliJ IDEA 中嵌入阅读 APP（Legado）的 Web 前端，连接手机端 Legado 的 Web 服务，实现书架浏览、阅读、书源/订阅源编辑。

> 本项目复刻自 [legado-vscode](https://github.com/sunrishe/legado-vscode) 的功能与前端界面。

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

---

## 功能

- 📚 **书架浏览**：查看 Legado 书架，点击书籍进入阅读。
- 📖 **小说阅读**：支持目录跳转、上一章/下一章、阅读设置（字体、字号、行距、主题）。
- 📝 **书源编辑**：编辑、导入、调试书源。
- 📰 **订阅源编辑**：编辑 RSS 订阅源。
- 🎨 **Darcula 主题**：前端 UI 适配 IDEA Darcula 暗色风格。
- 🔌 **设置同步**：在 IDEA 设置页中修改 Legado Web 服务地址，浏览器页面自动重新加载。

---

## 环境要求

- IntelliJ IDEA 2024.3 及以上版本（build 243+）
- bundled JetBrains Runtime（JBR），且包含 JCEF
- Java 21+
- Gradle 8.9+
- Node.js 16+ / npm 或 pnpm

---

## 安装

### 方式一：从 Release 下载（推荐）

1. 进入本项目的 [Releases](https://github.com/Nanoyeluo/idea-plugin-legado/releases) 页面。
2. 下载最新版本的 `legado_idea-x.x.x.zip`。
3. 在 IDEA 中打开 `Settings | Plugins`。
4. 点击右上角 ⚙️ 齿轮 → `Install Plugin from Disk...`。
5. 选择下载的 zip 文件，点击 **OK**，重启 IDEA。

### 方式二：自行构建

```bash
# 1. 克隆仓库
git clone https://github.com/Nanoyeluo/idea-plugin-legado.git
cd idea-plugin-legado

# 2. 生成 Gradle Wrapper（如尚未生成）
gradle wrapper --gradle-version 8.9

# 3. 构建插件（会自动构建前端并复制到 resources/web）
./gradlew buildPlugin

# 4. 安装插件
cp build/distributions/legado_idea-1.0.0.zip ~/Desktop/
```

然后在 IDEA 中通过 `Install Plugin from Disk...` 安装该 zip。

---

## 使用

### 1. 启动 Legado Web 服务

在手机上打开 Legado APP：

```
我的 → Web 服务 → 启动服务
```

记录显示的 `http://IP:PORT`，例如 `http://192.168.1.5:1122`。

### 2. 配置插件

在 IDEA 中打开：

```
Settings | Tools | 阅读APP
```

填入 **WEB 服务地址**，例如 `http://192.168.1.5:1122`，点击 **确定**。

### 3. 打开阅读工具窗口

```
View | Tool Windows | 阅读
```

或点击右侧工具窗口的 **阅读** 图标。

### 4. 开始使用

- 在书架页面点击书籍开始阅读。
- 在阅读页按 `E` 打开目录，按 `A/D` 或左右方向键切换章节。
- 通过右侧设置按钮调整阅读主题、字体、字号等。

---

## 开发

### 项目结构

```
.
├── src/main/java/com/github/sunrishe/legado_idea/    # Java 插件代码
│   ├── LegadoToolWindowFactory.java                  # 工具窗口工厂
│   ├── settings/                                     # 配置持久化与设置页
│   │   ├── LegadoSettings.java
│   │   ├── LegadoSettingsConfigurable.java
│   │   └── LegadoSettingsState.java
│   ├── web/                                          # JCEF 浏览器与资源处理
│   │   ├── LegadoBrowserPanel.java
│   │   ├── LegadoResourceHandler.java
│   │   └── LegadoSchemeHandlerFactory.java
│   └── util/                                         # 工具类
│       └── UrlValidator.java
├── src/main/resources/META-INF/plugin.xml            # 插件入口声明
├── src/main/resources/web/                           # 前端构建产物（由 Gradle 自动生成）
├── frontend/                                         # Vue 3 + Vite 前端源码
│   ├── src/
│   │   ├── views/                                    # 书架、阅读页
│   │   ├── components/                               # 组件
│   │   ├── pages/                                    # 多入口：bookshelf / source
│   │   ├── api/                                      # 与 Legado 服务通信
│   │   ├── config/                                   # 主题配置
│   │   └── assets/                                   # 样式与字体
│   └── package.json
├── build.gradle                                      # Gradle 构建配置（Groovy DSL）
└── README.md                                         # 本文件
```

### 常用命令

#### 构建前端

```bash
cd frontend
npm install
npm run build
```

#### 运行沙箱 IDE

```bash
./gradlew runIde
```

#### 运行测试

> 注意：`build.gradle` 中当前禁用了测试执行（`tasks.test { enabled = false }`），运行前需改为 `enabled = true`。

```bash
./gradlew test --tests "com.github.sunrishe.legado_idea.util.UrlValidatorTest"
./gradlew test --tests "com.github.sunrishe.legado_idea.settings.LegadoSettingsStateTest"
```

#### 复制前端产物到 resources/web

```bash
./gradlew copyWeb
```

### 前端开发

开发前需创建 `.env.development`：

```bash
cd frontend
echo "VITE_API=http://192.168.1.5:1122" > .env.development
npm install
npm run dev
```

前端路由：

- `/`：书架
- `/#/bookSource`：书源编辑
- `/#/rssSource`：订阅源编辑

---

## 技术说明

### JCEF 浏览器与本地资源加载

插件通过 `JBCefBrowser` 加载自定义 Scheme `http://legado-idea/index.html`。静态资源由 `LegadoSchemeHandlerFactory` + `LegadoResourceHandler` 从 `src/main/resources/web/` 提供。

处理 `index.html` 时，会注入一段脚本：

- 将 `LegadoSettings.getWebServeUrl()` 写入 `localStorage.legadoWebServeUrl`
- 定义 `window.acquireVsCodeApi`，使前端以为自己运行在 VSCode Webview 环境中

### JavaScript 桥

页面加载完成后，`LegadoBrowserPanel` 通过 `JBCefJSQuery` 注入 `window.__legadoIdeaQuery`，前端调用 `acquireVsCodeApi().postMessage(...)` 的消息会经由该桥传回 Java。

### 配置变更通知

使用 IntelliJ MessageBus，当 `LegadoSettingsConfigurable.apply()` 保存设置后，通知所有已打开的 `LegadoBrowserPanel` 重新加载页面，确保新的 Web 服务地址生效。

---

## 常见问题

### 1. 插件提示“当前运行环境不支持 JCEF”

请使用 bundled JetBrains Runtime (JBR) 启动 IDE：

```
Help | Find Action | "Choose Boot JDK..." → 选择 JetBrains Runtime
```

若通过 `./gradlew runIde` 启动，请确认 `build.gradle` 中声明的 IntelliJ 平台依赖包含 JCEF 版 JBR。

### 2. 第一次打开提示“网络连接失败”

默认地址为 `http://127.0.0.1:1122`。请确保 Legado Web 服务已启动，并在 `Settings | Tools | 阅读APP` 中填入正确的地址。

### 3. 修改 URL 后页面没有反应

请确认：

- 地址格式正确（如 `http://192.168.1.5:1122`）。
- 点击的是 IDEA 设置窗口右下角的 **确定** 或 **Apply**。
- 插件版本为最新，旧版本存在 JCEF JS 桥初始化问题。

### 4. Maven settings.xml 解析报错

若 IDEA 启动时报 `JDOMException: Unexpected character 'ý'`，请检查 `~/.m2/settings.xml` 的编码为 UTF-8，并删除文件开头的非法字符或 BOM。

---

## 贡献

欢迎提交 Issue 和 Pull Request。

---

## 许可证

[MIT](LICENSE)

---

## 致谢

- [legado-vscode](https://github.com/sunrishe/legado-vscode)：本项目的前端与功能灵感来源。
- [Legado](https://github.com/gedoor/legado)：优秀的开源阅读 APP。
