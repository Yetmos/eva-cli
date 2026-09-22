# eva-cli

面向多 agent 编排框架的独立 Gradle 工程，优先支持 macOS，并兼容 Windows、Linux。该目录包含公共接口、运行时实现与 CLI，不包含 Compose UI。

当前仅有基础骨架：`EvaRuntime.info()` 返回 `RuntimeInfo(version, host)`，`LocalEvaRuntime` 提供本地 JVM 实现，CLI 支持 `--help`、`--version` 和 `info`。agent 编排、脚本执行与沙盒功能尚未实现。

## 模块

| 模块 | 职责与依赖 |
| --- | --- |
| `core` | KMP 公共接口与数据模型，提供 JVM、Android、iOS targets，无 UI 依赖 |
| `runtime` | JVM 运行时实现，依赖 `core`；未来容纳编排、脚本与平台沙盒适配 |
| `cli` | JVM application 入口，依赖 `runtime`，处理命令行参数与输出 |

CLI 与桌面应用可以复用同一份运行时实现。移动端可以依赖公共接口，目前未提供移动端运行时。

## 独立构建与运行

需要 Java 17 或更高版本。Android 构建还需要 Android SDK 37，并配置 `ANDROID_HOME` 或本目录的 `local.properties`；iOS 构建需要 macOS 和 Xcode。

从仓库根目录进入本工程：

```sh
cd eva-cli
./gradlew :cli:run --args="--help"
./gradlew :cli:run --args="--version"
./gradlew :cli:run --args="info"
```

生成包含启动脚本和运行依赖的本地分发目录：

```sh
./gradlew :cli:installDist
```

产物位于 `cli/build/install/` 下。Windows PowerShell 将 `./gradlew` 替换为 `.\gradlew.bat`。

本工程自带 Gradle Wrapper、`settings.gradle.kts`、版本目录与 `gradle.properties`，可单独复制或迁出为独立仓库，不需要父目录的构建配置。

## 与 EvaLauncher 集成

EvaLauncher 根工程通过 `includeBuild("eva-cli")` 使用本地源码，并替换 `com.eva:core`、`com.eva:runtime` 依赖。共享 UI 依赖 `core`，桌面入口负责创建 `LocalEvaRuntime` 并注入 UI。

从 EvaLauncher 根目录也可运行：

```sh
./gradlew :eva-cli:cli:run --args="info"
```

未来迁出独立仓库并发布后，消费方可改用版本化的 `com.eva:core`、`com.eva:runtime` 依赖。当前尚未选择许可证，也尚未公开发布仓库或依赖包。
