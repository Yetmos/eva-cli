# Echo Plugin

最小 Eva 业务逻辑插件：接收文本事件 `hello`，通过 `emit` 返回一个内容为
`Echo: hello` 的新事件。回复的 `metadata.providerId` 指向 EchoProvider，输入事件保持原样。

实现位于 KMP 的 `commonMain`，当前发布包支持 JVM，要求 Java 17 或更高版本。
仓库内通过 `project(":core")` 使用宿主 API；`.eva` 中包含独立源码工程和同版本 API
源码快照，解压后不依赖 EvaLauncher 仓库。首次构建需要联网下载 Gradle、Kotlin 和声明的依赖。

## 文件与职责

| 文件 | 职责 |
| --- | --- |
| `src/commonMain/kotlin/com/eva_cli/examples/echo/EchoPlugin.kt` | 实现 `PluginEntry`，公开无参构造器，仅提供 Echo Dispatcher 和 Echo Provider。 |
| `src/commonMain/kotlin/com/eva_cli/examples/echo/EchoProvider.kt` | 实现 `/Echo` topic 的文本回显及启停状态。 |
| `src/commonMain/kotlin/com/eva_cli/examples/echo/EchoDispatcher.kt` | 将 `/Echo` 事件交给 EchoProvider，并把回复向上转发。 |
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

独立工程使用 `sdk/plugin-api` 源码模块，内容从宿主现有的事件、Widget、WidgetContext、Provider、Dispatcher
和插件契约复制，不需要本地 Maven 仓库，也不要求 Android SDK。协程依赖随 SDK 暴露，支持 `scope` 和 `Flow`。
该快照保留宿主当前的
基础实现和未完成能力，并为 Dispatcher 扫描提供仅用于编译的 `PlugManager` 查询入口；
在独立 SDK 中调用该入口会抛出 `UnsupportedOperationException`。正式运行仍由宿主提供这些类，
宿主安装器、加载器和运行时注册表不随插件打包。
不要修改快照来扩展宿主 API，应先在宿主实现并更新插件对应的 API 版本。

仓库尚未发布统一插件 SDK，这种随包提供源码的方式用于保证当前示例可独立重建。
示例源码按 Apache License 2.0 发布，详见 `LICENSE`；第三方声明见
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
classpath 中的示例类，确认入口及 Provider 实际由生成的 JAR 加载，并验证 Context 的共享类型和组件绑定。
JAR 仅包含插件代码，Kotlin 运行库及宿主 API 由宿主提供。

解压工程执行相同任务时省略 `:examples:echo-plugin:` 前缀。
宿主加载时读取包根目录 `plugin.json`，从 `prebuilt/jvm/plugin.jar` 创建
`com.eva_cli.examples.echo.EchoPlugin`。JVM 宿主已提供 `PlugManager.installPlugin` 和 `loadPlugin`；
已有加载测试提供可执行的最小宿主流程。

## 宿主调用约定

1. 读取清单，使用其中的 `entryClass` 创建 `PluginEntry`。
2. 取得入口的 Provider 和 Dispatcher，为同一插件的组件绑定同一个 `WidgetContext`，再按 `topic` 注册。
3. 绑定 `provider.emit`，调用 `provider.start()`。
4. 将文本事件传给 `provider.receive(event)`，从回调取得回复。
5. 结束时停止组件，解除回调和组件引用；卸载时由宿主释放 Context 资源，再关闭 ClassLoader。

正式 JVM 宿主的 `PlugManager.loadPlugin` 自动执行 Context 绑定。上面的手工流程适用于测试宿主；
`EchoPluginLoadingTest` 使用不访问网络、数据库或文件的测试 Context，Echo 的文本回显行为保持不变。

生命周期方法 `start()`、`stop()` 与 `receive()` 均为挂起函数；宿主须在协程中串行调用。
未启动、已停止或
`enable = false` 时忽略输入；二进制事件也会被忽略。输出异常和协程取消向上传递，
并恢复处理状态；若输出回调停止了组件，则保留 `STOP` 状态。

示例提供 `/Echo` Dispatcher 和 Provider；根节点由独立的 root 插件提供。

当前插件接口为 `apiVersion: 4`：`Event.Metadata` 支持目标 topic，`Widget` 提供 `context` 和 `attach(context)`，
`PluginProvider`、`PluginDispatcher` 通过 `BaseWidget` 提供绑定实现。
从 API 2 迁移时，更新 SDK、重新编译和打包，并将清单的 `apiVersion` 设为 `4`；
直接实现 `Widget` 的类还需实现这两个成员。组件构造期间不得访问 Context，应在 `start()` 或后续调用中使用。
从 API 1 迁移还需将 Provider 的 `start/stop` 改为挂起函数，并将 Dispatcher 的 `feedUp` 改为 `emit`。
仅修改清单不能兼容旧二进制，旧 API 插件会被宿主拒绝。

## 使用宿主能力

组件通过继承的 `context` 访问能力；它是跨平台的 Eva API，不是 Android `Context`。
同一插件的组件共享 Context，不同插件分别拥有偏好、文件和数据库命名空间。
公共边界使用 Eva 类型，Ktor 和 DataStore 由宿主实现；插件无需接收宿主的第三方库实例。

例如，在挂起的 `receive()` 中使用网络、偏好和文件：

```kotlin
import com.eva_cli.core.widgets.context.NetworkRequest
import com.eva_cli.core.widgets.context.PluginFileArea

val response = context.network.execute(NetworkRequest("https://example.com/data"))
check(response.status in 200..299)
context.preferences.put("lastStatus", response.status.toString())
val lastStatus = context.preferences.get("lastStatus")
context.files.write("download/data.json", response.body)
val saved = context.files.read("download/data.json")
context.files.write("preview.json", saved, PluginFileArea.CACHE)
```

偏好值为字符串，结构化值由插件自行序列化；`preferences.values` 提供变更 `Flow`。
文件路径相对于 `DATA`、`CACHE` 或 `RESOURCES`，资源目录只读；不要传绝对路径或 `..`。
JVM 宿主将普通文件保存在 `Plugins/<id>/Data/files/`，缓存保存在 `Plugins/<id>/Cache/`，
偏好和数据库分别保存在同一插件的 `Data/preferences.preferences_pb` 和 `Data/databases/`。
这些目录与 `versions/<version>/` 同级，升级或卸载某个安装版本后继续保留。
宿主备份插件时默认不包含这些运行数据；需要一并保存持久数据时调用
`PlugManager.backup(includeRuntimeData = true)`。该选项只包含 `Data`，始终排除可重新生成的 `Cache`，
且备份或恢复前应先停止并卸载对应插件。
未声明公共资源的插件不能读取 `RESOURCES`。持久数据与安装版本分开保存，卸载加载器不会删除数据。

Room 数据库由插件提供 schema、DAO、生成代码和迁移。宿主管理数据库路径、实例复用与关闭，
通过 `PluginDatabase` 包装插件自己的 `RoomDatabase`，不向宿主注册 Room schema，也不要求宿主添加 Room 编译器。
以下代码放在已有 `ChatDatabase` 及其 `buildChatDatabase(path)` 构建函数的插件中：

```kotlin
import com.eva_cli.core.widgets.context.PluginDatabase
import com.eva_cli.core.widgets.context.PluginDatabaseFactory

class ChatStore(val room: ChatDatabase) : PluginDatabase {
    override fun close() = room.close()
}

// 插件级单例；buildChatDatabase 负责驱动、DAO 实现和迁移配置。
object ChatStoreFactory : PluginDatabaseFactory<ChatStore> {
    override fun open(path: String) = ChatStore(buildChatDatabase(path))
}

// 在组件生命周期方法中调用。
val store = context.databases.open("chat.db", ChatStoreFactory)
```

同一数据库名必须重复传入**同一个 factory 实例**，宿主才会返回缓存实例；
同名使用另一个 factory 会报错。不要在每次调用时创建新的 lambda factory。
Room 及插件专属运行依赖由插件声明和打包；Echo 本身不引入数据库 schema 或 Room 编译器。

权限申请通过 `context.permissions.check(permission)` / `request(permission)`，结果为
`GRANTED`、`DENIED` 或 `UNAVAILABLE`。没有交互宿主或平台不支持时返回 `UNAVAILABLE`；
应用通过 `PlugManager.permissions` 接入当前 UI 的权限桥接，并在 UI 销毁时移除旧桥接。
这不会自动添加 Android manifest 权限，Android/iOS 的动态加载接入仍未实现。

```kotlin
import com.eva_cli.core.widgets.context.PermissionResult

val result = context.permissions.request("android.permission.CAMERA")
if (result == PermissionResult.GRANTED) {
    // 在支持相机的平台继续业务操作。
}
```

组件可用 `context.scope.launch { ... }` 启动任务，并保存自己的 `Job`，在 `stop()` 中取消并等待它结束。
例如，在组件中观察偏好变化：

```kotlin
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

private var settingsJob: Job? = null

override suspend fun start() {
    check(settingsJob == null)
    settingsJob = context.scope.launch {
        context.preferences.values.collect { values -> println(values["theme"]) }
    }
}

override suspend fun stop() {
    settingsJob?.cancelAndJoin()
    settingsJob = null
}
```

这个 scope 属于整个插件，单个组件停止时不要取消共享 scope，也不要关闭共享数据库或网络服务。
宿主在 `unloadPlugin` 时取消 Context 的任务、关闭其服务后释放加载器；调用方仍须先停止并注销组件。
这些接口提供能力与资源管理，不是安全沙盒，同进程 JVM 插件仍可自行调用平台 API。

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
