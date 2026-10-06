# Echo Plugin

最小 Eva 业务逻辑插件：接收文本事件 `hello`，通过 `emit` 返回一个内容为
`Echo: hello` 的新事件。回复的 `metadata.providerId` 指向 EchoProvider，输入事件保持原样。

实现位于 KMP 的 `commonMain`，当前配置 JVM target，要求 Java 17 或更高版本。
插件通过 `project(":core")` 使用仓库内的宿主 API。

## 文件与职责

| 文件 | 职责 |
| --- | --- |
| `src/commonMain/kotlin/com/eva_cli/examples/echo/EchoPlugin.kt` | 实现 `PluginEntry`，公开无参构造器，持有一个 Provider 实例。 |
| `src/commonMain/kotlin/com/eva_cli/examples/echo/EchoProvider.kt` | 实现 `/Echo` topic 的文本回显及启停状态。 |
| `plugin.json` | 声明插件 ID、版本、JVM 入口类及 JAR 相对路径。 |
| `src/jvmTest/kotlin/com/eva_cli/examples/echo/EchoPluginLoadingTest.kt` | 最小测试宿主：读清单、从实际 JAR 加载入口、绑定回调、收发事件并停止组件。 |

## 构建与验证

在 `eva-cli` 目录运行：

```sh
./gradlew :examples:echo-plugin:check
```

该命令同时执行 Provider 行为测试和 JAR 加载测试，并生成：

```text
examples/echo-plugin/build/plugin/
├── plugin.json
└── prebuilt/jvm/plugin.jar
```

只组装插件清单与产物：

```sh
./gradlew :examples:echo-plugin:prepareJvmPlugin
```

只运行加载测试：

```sh
./gradlew :examples:echo-plugin:jvmTest --tests '*EchoPluginLoadingTest'
```

加载测试以宿主的 `PluginEntry` ClassLoader 为共享 API 的来源，并屏蔽测试进程
classpath 中的示例类，确认入口及 Provider 实际由生成的 JAR 加载。
JAR 仅包含插件代码，Kotlin 运行库及宿主 API 由宿主提供。

`build/plugin` 是本地开发产物，尚不是包含独立源码工程的 `.eva` 发布包。
示例随 `eva-cli` 工程构建；Android DEX、iOS 静态接入和 `.eva` 打包待后续实现。

## 宿主调用约定

1. 读取清单，使用其中的 `entryClass` 创建 `PluginEntry`。
2. 从 `entry.providers` 取得同一组组件实例，按 `topic` 注册。
3. 绑定 `provider.emit`，调用 `provider.start()`。
4. 将文本事件传给 `provider.receive(event)`，从回调取得回复。
5. 结束时调用 `provider.stop()`，解除回调和组件引用，再关闭 ClassLoader。

这个示例要求宿主串行调用生命周期方法和 `receive`。未启动、已停止或
`enable = false` 时忽略输入；二进制事件也会被忽略。输出异常和协程取消向上传递，
并恢复处理状态；若输出回调停止了组件，则保留 `STOP` 状态。

`PluginEntry` 仅提供组件，不负责启动它们。示例可由测试宿主直接驱动；接入正式
Dispatcher 树还需要宿主实现组件注册和 `scanProviders` / `scanDispatchers`。

## 扩展示例

将包名和 `plugin.json` 的 `id`、`name`、`entryClass` 改成自己的插件信息，再替换
Provider 的 `receive` 实现。新增 Dispatcher 时继承 `WidgetDispatcher`，并通过
入口的 `dispatchers` 属性提供实例；宿主仍负责回调绑定和生命周期。

当前示例版本与 `eva-cli` 工程版本一致；修改工程版本时同步更新清单，加载测试会检查两者一致。
