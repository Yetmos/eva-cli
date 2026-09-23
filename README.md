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

## 输入与输出

`IOManager` 是全局单例，支持动态注册多个 `InputProvider` 和 `OutProvider`：

```kotlin
IOManager.addInputProvider(consoleInput)
IOManager.addInputProvider(voiceInput)
IOManager.addOutputProvider(consoleOutput)
IOManager.addOutputProvider(windowOutput)

val runtime = Eva.start()

// 可在运行中增删；已启用的 Provider 添加时自动启动，移除时自动停止。
IOManager.addInputProvider(remoteInput)
IOManager.removeInputProvider(voiceInput)
IOManager.removeOutputProvider(windowOutput)

// 在协程中接收任一输入源的事件，并广播给当前输出源。
val event = IOManager.receive()
if (event != null) IOManager.emit(event)

runtime.stop()
```

注册按实例去重，增删方法返回是否发生变更；`inputProviders` 和 `outputProviders` 提供注册列表快照。`enable = false` 的 Provider 会保留在注册列表中，但不会启动或参与收发；运行中修改 `enable` 后，会在下一次 `receive()` 或 `emit()` 调用前同步。输入源并发等待，一个输入源挂起不会阻塞其他输入源。管理器停止、没有输入源或当前全部输入源耗尽时，`receive()` 返回 `null`；输入源的挂起等待需要响应协程取消，以便移除和停止。

`emit()` 需要先启动管理器，并按注册顺序广播；单个输出失败后仍会尝试其他输出，最后抛出异常。移除输出源不等待已经开始的输出调用结束。`stop()` 保留注册列表供再次启动使用，所有 `Runtime` 实例共享全局 IO 的启停状态。

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

EvaLauncher 根工程通过 `includeBuild("eva-cli")` 使用本地源码，并替换 `com.eva_cli:core` 依赖。共享 UI 使用 `core`，CLI 则在本工程内直接依赖 `core`。

从 EvaLauncher 根目录也可运行：

```sh
./gradlew :eva-cli:cli:run --args="--help"
```

未来迁出独立仓库并发布后，消费方可改用版本化的 `com.eva_cli:core` 依赖。当前尚未选择许可证，也尚未公开发布仓库或依赖包。
