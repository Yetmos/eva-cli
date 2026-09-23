# eva-cli

面向多 agent 编排框架的独立 Gradle 工程，优先支持 macOS，并兼容 Windows、Linux。该目录包含跨平台核心代码与 CLI，不包含 Compose UI。

当前仅有 `Eva` 与 `Runtime` 基础入口骨架，CLI 提供帮助和命令解析框架。agent 编排、脚本执行与沙盒功能尚未实现。

## 模块

| 模块 | 职责与依赖 |
| --- | --- |
| `core` | KMP 核心代码与数据模型，提供 JVM、Android、iOS targets，无 UI 依赖 |
| `cli` | JVM application 入口，依赖 `core`，处理命令行参数与输出 |

核心入口面向 JVM、Android 和 iOS 共用；CLI 通过 JVM target 使用它。

本工程的版本统一在 `build.gradle.kts` 的 `allprojects.version` 中配置。
