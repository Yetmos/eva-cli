# Echo Plugin

最小 Eva 业务逻辑插件：接收文本事件 `hello`，通过 `emit` 返回一个内容为
`Echo: hello` 的新事件。回复的 `metadata.providerId` 指向 EchoProvider，输入事件保持原样。

实现位于 KMP 的 `commonMain`，当前发布包支持 JVM，要求 Java 17 或更高版本。
仓库内通过 `project(":core")` 使用宿主 API；`.eva` 中包含独立源码工程和同版本 API
源码快照，解压后不依赖 EvaLauncher 仓库。首次构建需要联网下载 Gradle、Kotlin 和声明的依赖。

## 文件与职责

| 文件 | 职责 |
| --- | --- |
| `src/commonMain/kotlin/com/eva_cli/examples/echo/EchoPlugin.kt` | 实现 `PluginEntry`，公开无参构造器，提供根 Dispatcher 和 Echo Provider。 |
| `src/commonMain/kotlin/com/eva_cli/examples/echo/EchoProvider.kt` | 实现 `/Echo` topic 的文本回显及启停状态。 |
| `src/commonMain/kotlin/com/eva_cli/examples/echo/RootDispatcher.kt` | 提供 `topic="/"` 的根 Dispatcher，由宿主 Booter 发现并启动。 |
| `plugin.json` | 声明插件 ID、版本、JVM 入口类及 JAR 相对路径。 |
| `src/jvmTest/kotlin/com/eva_cli/examples/echo/EchoPluginLoadingTest.kt` | 最小测试宿主：读清单、从实际 JAR 加载入口、绑定回调、收发事件并停止组件。 |
| `src/jvmTest/kotlin/com/eva_cli/examples/echo/EvaPluginPackageTest.kt` | 检查发布内容包含独立工程和 API 源码，且未混入本机配置与构建缓存。 |
| `packaging/eva-plugin.gradle.kts` | 仓库内与解压后共用的组装、测试接入和 `.eva` 打包任务。 |

## 在仓库内生成发布包

在 `eva-cli` 目录运行：

```sh
./gradlew :examples:echo-plugin:assembleEvaPlugin
```

如果当前位于 EvaLauncher 仓库根目录，先执行 `cd eva-cli`。
Windows PowerShell 使用 `.\gradlew.bat :examples:echo-plugin:assembleEvaPlugin`。

该任务编译插件、组装独立工程、运行 Provider 行为和实际 JAR 加载测试，再生成：

```text
examples/echo-plugin/build/distributions/echo-plugin-0.1.0-alpha.eva
```

`.eva` 是 ZIP 文件，解压后根目录为：

```text
plugin.json
README.md
LICENSE
THIRD_PARTY_NOTICES.md
settings.gradle.kts
build.gradle.kts
gradle.properties
gradlew
gradlew.bat
gradle/                 # 版本目录与 Wrapper
src/                    # 插件实现与测试
packaging/              # 打包脚本和独立工程模板
sdk/plugin-api/         # 当前宿主 API 的源码快照，仅用于编译和测试
prebuilt/jvm/plugin.jar # 本次构建生成的插件代码
```

当前插件没有私有运行依赖、公共资源或配置文件，因此不包含 `prebuilt/jvm/lib/`、
`resources/`。清单仅声明 JVM，不包含 Android DEX 或 iOS KLIB。

## 解压后二次开发

将 `.eva` 作为 ZIP 解压，在 Android Studio 或 IntelliJ IDEA 打开根目录。
也可以在解压目录执行：

```sh
./gradlew assembleEvaPlugin
```

`assembleEvaPlugin` 已依赖 `check`，会先运行测试再生成发布包。
Windows PowerShell 使用 `.\gradlew.bat assembleEvaPlugin`。
若解压工具未保留脚本执行权限，先执行 `chmod +x gradlew`。
产物位于 `build/distributions/`。修改源码后重新执行该命令即可得到新的 `.eva`。
源码构建不读取包内 `prebuilt/`，删除该目录后仍能编译、测试并重新打包。
每次打包仅收集声明的源码和构建输入，JAR 来自当前构建，不复制旧预编译产物或缓存。

独立工程使用 `sdk/plugin-api` 源码模块，内容从宿主现有的事件、Provider、Dispatcher
和插件契约复制，不需要本地 Maven 仓库，也不要求 Android SDK。该快照保留宿主当前的
基础实现和未完成能力，并为 Dispatcher 扫描提供仅用于编译的 `PlugManager` 查询入口；
在独立 SDK 中调用该入口会抛出 `UnsupportedOperationException`。正式运行仍由宿主提供这些类，
宿主安装器、加载器和运行时注册表不随插件打包。
不要修改快照来扩展宿主 API，应先在宿主实现并更新插件对应的 API 版本。

仓库尚未发布统一插件 SDK，这种随包提供源码的方式用于保证当前示例可独立重建。
源码许可沿用项目当前状态，未新增开源许可授权，详见 `LICENSE`；第三方声明见
`THIRD_PARTY_NOTICES.md`。

## 测试与本地加载

仓库内运行测试：

```sh
./gradlew :examples:echo-plugin:check
```

在解压工程中使用 `./gradlew check`。测试覆盖 Provider 行为、开发产物和发布暂存目录中
的 JAR 加载、发布结构检查。完整发布内容暂存于 `build/eva-staging/`，开发产物位于：

```text
examples/echo-plugin/build/plugin/
├── plugin.json
└── prebuilt/jvm/plugin.jar
```

仓库内只组装插件清单与开发产物：

```sh
./gradlew :examples:echo-plugin:prepareJvmPlugin
```

仓库内只运行加载测试：

```sh
./gradlew :examples:echo-plugin:jvmTest --tests '*EchoPluginLoadingTest'
```

加载测试以宿主的 `PluginEntry` ClassLoader 为共享 API 的来源，并屏蔽测试进程
classpath 中的示例类，确认入口及 Provider 实际由生成的 JAR 加载。
JAR 仅包含插件代码，Kotlin 运行库及宿主 API 由宿主提供。

解压工程执行相同任务时省略 `:examples:echo-plugin:` 前缀。
宿主加载时读取包根目录 `plugin.json`，从 `prebuilt/jvm/plugin.jar` 创建
`com.eva_cli.examples.echo.EchoPlugin`。JVM 宿主已提供 `PlugManager.installPlugin` 和 `loadPlugin`；
已有加载测试提供可执行的最小宿主流程。

## 宿主调用约定

1. 读取清单，使用其中的 `entryClass` 创建 `PluginEntry`。
2. 从 `entry.providers` 取得同一组组件实例，按 `topic` 注册。
3. 绑定 `provider.emit`，调用 `provider.start()`。
4. 将文本事件传给 `provider.receive(event)`，从回调取得回复。
5. 结束时调用 `provider.stop()`，解除回调和组件引用，再关闭 ClassLoader。

这个示例要求宿主串行调用生命周期方法和 `receive`。未启动、已停止或
`enable = false` 时忽略输入；二进制事件也会被忽略。输出异常和协程取消向上传递，
并恢复处理状态；若输出回调停止了组件，则保留 `STOP` 状态。

`PluginEntry` 仅提供组件，不负责启动它们。示例提供 `topic="/"` 的根 Dispatcher，
宿主 `Booter` 会从已加载插件中发现并启动它；宿主不再内置 RootDispatcher。
当前示例仍只有 `/Echo` Provider，完整事件路由和 `/Echo` Dispatcher 后续接入。

## 扩展示例

将包名和 `plugin.json` 的 `id`、`name`、`entryClass` 改成自己的插件信息，再替换
Provider 的 `receive` 实现。新增 Dispatcher 时继承 `PluginDispatcher`，并通过
入口的 `dispatchers` 属性提供实例；宿主仍负责回调绑定和生命周期。

## 发布新版本

发布新版本前同步以下配置，再运行 `assembleEvaPlugin`：

1. 构建版本：仓库内修改 `eva-cli/build.gradle.kts` 的 `allprojects.version`；
   解压后的独立工程修改根目录 `build.gradle.kts` 中的版本。
2. 插件清单：修改 `plugin.json` 的 `version`，并同步 `source.coordinate` 中的版本部分。
   例如发布 `0.1.1-alpha` 时，坐标应为 `com.eva_cli:echo-plugin:0.1.1-alpha`。
3. 如果更换 Kotlin 工具链，同步版本目录 `gradle/libs.versions.toml` 和清单的 `builtWith.kotlin`。

测试会检查清单与实际构建的一致性。新包文件名包含构建版本，例如
`echo-plugin-0.1.1-alpha.eva`。
