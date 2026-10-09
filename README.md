# eva-cli

面向多 agent 编排框架的独立 Gradle 工程，优先支持 macOS，并兼容 Windows、Linux。该目录包含跨平台核心代码与 CLI，不包含 Compose UI。

当前提供 `Eva` 与 `Runtime` 基础入口，以及通过 CMD 插件处理文本命令的 CLI 模式。agent 编排、脚本执行与沙盒功能尚未实现。

插件开发可参考 [Echo Plugin 示例](examples/echo-plugin/README.md)，其中包含最小入口、
文本 Provider、插件清单、JVM JAR 加载测试及完整 `.eva` 发布任务。
在本目录执行 `./gradlew :examples:echo-plugin:assembleEvaPlugin`，即可生成包含独立源码工程、
API 源码快照和 JVM 产物的发布包；解压后可以独立构建和重新打包。

命令行输入输出由 [CMD Plugin](examples/cmd-plugin/README.md) 提供，根路由由必装的
[Root Plugin](examples/root-plugin/README.md) 提供。构建并安装 Root、Echo 和 CMD
三个插件后，在本目录执行 `./gradlew :cli:run --args="cmd"`，输入 `echo hello` 可得到
`Echo: hello`。

## 模块

| 模块 | 职责与依赖 |
| --- | --- |
| `core` | KMP 核心代码与数据模型，提供 JVM、Android、iOS targets，无 UI 依赖 |
| `cli` | JVM application 入口，依赖 `core`，处理命令行参数与输出 |
| `examples/root-plugin` | 系统根插件，提供唯一 `topic="/"` 的 Root Dispatcher |
| `examples/echo-plugin` | KMP 插件示例，当前提供 JVM target，依赖 `core` 的插件 API |
| `examples/cmd-plugin` | KMP 命令行适配插件，提供 `/CMD` Dispatcher、Provider 和 `.eva` 打包任务 |

核心入口面向 JVM、Android 和 iOS 共用；CLI 通过 JVM target 使用它。

本工程的版本统一在 `build.gradle.kts` 的 `allprojects.version` 中配置。

## 许可证

本项目按 Apache License 2.0 发布，详见 [LICENSE](LICENSE)。第三方依赖及其许可
仍以各自的声明为准。
