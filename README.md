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

## 独立构建与运行

需要 Java 17 或更高版本。Android 构建还需要 Android SDK 37，并配置 `ANDROID_HOME` 或本目录的 `local.properties`；iOS 构建需要 macOS 和 Xcode。

从仓库根目录进入本工程：

```sh
cd eva-cli
./gradlew :cli:run --args="--help"
```

生成包含启动脚本和运行依赖的本地分发目录：

```sh
./gradlew :cli:installDist
```

产物位于 `cli/build/install/` 下。Windows PowerShell 将 `./gradlew` 替换为 `.\gradlew.bat`。

本工程自带 Gradle Wrapper、`settings.gradle.kts`、版本目录与 `gradle.properties`，可单独复制或迁出为独立仓库，不需要父目录的构建配置。

## 与 EvaLauncher 集成

EvaLauncher 根工程通过 `includeBuild("eva-cli")` 使用本地源码，并替换 `com.eva:core` 依赖。共享 UI 使用 `core`，CLI 则在本工程内直接依赖 `core`。

从 EvaLauncher 根目录也可运行：

```sh
./gradlew :eva-cli:cli:run --args="--help"
```

未来迁出独立仓库并发布后，消费方可改用版本化的 `com.eva:core` 依赖。当前尚未选择许可证，也尚未公开发布仓库或依赖包。
