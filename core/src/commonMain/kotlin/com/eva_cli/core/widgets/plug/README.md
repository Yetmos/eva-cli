# .eva 插件发布包规范

`.eva` 使用 ZIP 格式，解压后直接成为一个可用 Android Studio 或 IntelliJ IDEA 打开的标准 KMP 工程。
源码、构建配置、插件清单和公共资源位于工程根目录，预编译产物统一放在 `prebuilt/`。
PC 加载 JVM JAR，Android 加载 DEX，iOS 在构建时选择 KLIB 或源码集成。
以下内容是发布格式与接入流程的设计约定。JVM 宿主已支持本地 `.eva` 校验、解压、安装及 JAR 加载；
Android 打包、iOS 构建接入以及运行时调度树接入仍需后续实现。发布包仅需包含所声明平台的产物。

## 发布包结构

```text
chat-plugin-1.0.0.eva
├── plugin.json                              # 插件身份、版本、入口及产物位置
├── README.md                                # 插件说明、配置与使用方式
├── LICENSE                                  # 插件许可证
├── THIRD_PARTY_NOTICES.md                    # 第三方依赖许可说明，按需提供
├── settings.gradle.kts                      # KMP 工程入口
├── build.gradle.kts                         # 编译、测试与插件打包配置
├── gradle.properties
├── gradlew
├── gradlew.bat
│
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
│
├── src/
│   ├── commonMain/
│   │   └── kotlin/com/example/chat/
│   │       ├── ChatPlugin.kt                 # 统一插件入口
│   │       ├── ChatDispatcher.kt
│   │       └── ChatProvider.kt
│   ├── commonTest/
│   ├── jvmMain/
│   ├── jvmTest/
│   ├── androidMain/
│   └── iosMain/
│
├── resources/                               # 公共资源，通过宿主接口读取
│   ├── config.schema.json                   # 配置格式，可选
│   └── defaults.json                        # 默认配置，可选
│
└── prebuilt/                                # 当前发布版本的预编译产物
    ├── jvm/                                 # PC 运行时加载产物
    │   ├── plugin.jar                       # 插件实现
    │   └── lib/                             # 插件私有运行依赖
    │       └── dependency.jar
    ├── android/                             # Android 运行时加载产物
    │   └── plugin-dex.jar                   # classes.dex、必要的后续 DEX 及资源
    └── maven/                               # 编译期使用的 KMP Maven 仓库
        └── com/example/
            ├── chat-plugin/1.0.0/           # KMP 根模块
            │   ├── chat-plugin-1.0.0.pom
            │   ├── chat-plugin-1.0.0.module
            │   └── chat-plugin-1.0.0.jar     # Kotlin metadata 产物
            ├── chat-plugin-jvm/1.0.0/       # JVM 发布物及其 POM、module
            ├── chat-plugin-android/1.0.0/   # Android 发布物及其 POM、module
            ├── chat-plugin-iosarm64/1.0.0/  # iOS 真机
            │   ├── chat-plugin-iosarm64-1.0.0.klib
            │   ├── chat-plugin-iosarm64-1.0.0.pom
            │   └── chat-plugin-iosarm64-1.0.0.module
            └── chat-plugin-iossimulatorarm64/1.0.0/
                ├── chat-plugin-iossimulatorarm64-1.0.0.klib
                ├── chat-plugin-iossimulatorarm64-1.0.0.pom
                └── chat-plugin-iossimulatorarm64-1.0.0.module
```

`prebuilt/maven/` 收录 Gradle 的实际发布结果，模块名称和文件以发布配置为准。
其中包含预编译的 iOS KLIB，以及解析这些产物所需的根模块、POM、Gradle module 和 metadata。
若要求离线构建，还需提供缺失的传递依赖。

包根目录包含依赖声明、构建配置、源码、资源及必要的生成步骤，保证插件可以直接打开并重新构建。
源码构建应能在移除 `prebuilt/` 后完成；所需外部依赖通过明确配置的仓库解析。
`prebuilt/` 是发布版本的构建结果，修改源码不会自动更新其中的文件。
源码和二进制产物应来自同一次构建，可额外记录源码提交号。

## plugin.json 示例

以下字段是建议的自定义清单格式，版本和路径均为示例。

```json
{
  "formatVersion": 1,
  "id": "com.example.chat",
  "name": "Chat Plugin",
  "version": "1.0.0",
  "apiVersion": 4,
  "resources": "resources",
  "platforms": {
    "jvm": {
      "artifact": "prebuilt/jvm/plugin.jar",
      "dependencies": "prebuilt/jvm/lib",
      "entryClass": "com.example.chat.ChatPlugin",
      "minJavaVersion": 17
    },
    "android": {
      "artifact": "prebuilt/android/plugin-dex.jar",
      "entryClass": "com.example.chat.ChatPlugin",
      "minSdk": 29
    },
    "ios": {
      "repository": "prebuilt/maven",
      "coordinate": "com.example:chat-plugin:1.0.0",
      "factory": "com.example.chat.ChatPlugin"
    }
  },
  "source": {
    "directory": ".",
    "gradleProject": ":",
    "coordinate": "com.example:chat-plugin:1.0.0"
  },
  "builtWith": {
    "kotlin": "2.4.20"
  }
}
```

`formatVersion` 表示包格式版本，`apiVersion` 表示宿主插件接口版本，
`version` 表示插件自身的发布版本。插件 ID 在升级时保持稳定。
当前宿主 API 版本为 `4`，包格式版本仍为 `1`。API 4 在 `Event.Metadata` 增加目标 topic；API 3 在 `Widget` 增加 `context` 和
`attach(context)`，由 `BaseWidget` 提供一次绑定实现，宿主在生命周期调用前绑定插件能力。
API 2 插件须更新 SDK 后重新编译、打包并修改清单；直接实现 `Widget` 的组件须补齐这两个成员。
API 1 还须将 Provider 的 `start/stop` 改为挂起函数，并将 Dispatcher 的 `feedUp` 改为 `emit`。
宿主会拒绝安装或加载旧 API 插件，仅修改清单不能兼容旧二进制。
清单中的产物、依赖目录、仓库、源码和资源路径均相对于发布包根目录。
`source.directory` 为 `.`，表示源码工程位于解压后的根目录；`gradleProject` 为 `:`，表示根项目。
PC、Android 的 `entryClass` 用于反射实例化公开无参入口；
iOS 的 `factory` 用于生成静态注册代码，不用于运行时按类名加载。

### 清单数据模型

`commonMain` 中的 `PluginManifest` 对应上述 JSON 结构，使用现有的 Kotlin Serialization 编解码：

```kotlin
import com.eva_cli.core.widgets.plug.PluginManifest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val manifest = Json.decodeFromString<PluginManifest>(jsonText)
val json = Json.encodeToString(manifest)
```

`formatVersion`、`id`、`name`、`version`、`apiVersion`、`platforms` 为必填字段。
`platforms` 下的 `jvm`、`android`、`ios` 均可省略，省略时为 `null`，表示未声明该平台。
声明 JVM 或 Android 平台时，`artifact` 和 `entryClass` 必填；JVM 的 `dependencies`、
`minJavaVersion` 及 Android 的 `minSdk` 默认为 `null`，表示未声明。
iOS 的 `repository`、`coordinate`、`factory` 均必填。

顶层 `resources`、`source`、`builtWith` 可省略，默认均为 `null`。
声明 `source` 时，`coordinate` 必填，`directory` 默认 `.`，`gradleProject` 默认 `:`；
声明 `builtWith` 时，`kotlin` 必填。源码描述可省略不改变 `.eva` 包应包含源码工程的发布约定。
默认编码会省略具有默认值的字段；如需显式输出它们，可使用 `Json { encodeDefaults = true }`。

该模型仅描述清单并支持 JSON 编解码，不包含文件读取或插件加载。
安装流程仍需检查至少存在一个可用平台、包格式及 API 兼容性、字段取值和包内文件的有效性。

## 插件组件继承约定

插件中的具体组件应继承面向插件开放的 `Widget` 基类：Dispatcher 继承
`PluginDispatcher`，Provider 继承 `PluginProvider`。这两个类是插件扩展入口；
`BaseDispatcher` 和 `BaseProvider` 是宿主侧的基础实现，插件不应直接依赖它们的内部实现。

一个插件可以包含多个 Dispatcher 或 Provider，但每个具体组件只承担一种组件职责。
不要让同一个类同时继承 `PluginDispatcher` 和 `PluginProvider`；需要同时提供两种能力时，
应注册两个独立的组件实例，由插件入口负责创建和注册它们。

Dispatcher 示例：

```kotlin
import com.eva_cli.core.widgets.dispatcher.PluginDispatcher
import com.eva_cli.core.widgets.event.Event

class ChatDispatcher : PluginDispatcher() {
    override val name = "Chat Dispatcher"
    override val desc = "Dispatches chat events"
    override val topic = "/Chat"

    override suspend fun dispatch(event: Event) {
        // 将事件交给当前 topic 下的 Provider 或子 Dispatcher。
    }

    override suspend fun receive(event: Event) {
        // 处理 Provider 或子 Dispatcher 返回的事件。
    }
}
```

Provider 示例：

```kotlin
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.provider.Provider
import com.eva_cli.core.widgets.provider.PluginProvider
import kotlin.uuid.Uuid

class ChatProvider : PluginProvider() {
    override val id = Uuid.random()
    override val name = "Chat Provider"
    override val desc = "Handles chat events"
    override val topic = "/Chat"
    override var enable = true
    override var status = Provider.Status.IDLE
    override var records: List<EventRecord> = emptyList()
    override var emit: (suspend (event: Event) -> Unit)? = null

    override suspend fun receive(event: Event) {
        // 处理 Dispatcher 投递的事件。
    }
}
```

`PluginProvider` 已经包含 `Provider` 的宿主生命周期契约，具体 Provider 仍需实现自己的
元数据、事件处理和输出回调。若后续提供输入、输出或其他专用 Provider 基类，具体实现应
继承职责最接近的一个基类，不需要重复继承 `PluginProvider`。

插件入口实现 `PluginEntry`，通过 `providers` 和 `dispatchers` 提供组件列表，未提供的
组件种类默认为空列表。同一入口实例始终持有同一组组件实例；宿主负责绑定 `WidgetContext`、
注册、绑定回调及调用组件的 `start` / `stop`，读取列表不会启动组件。入口类名必须与 `plugin.json` 中
对应平台的 `entryClass` 或 `factory` 一致。

仓库内的 `eva-cli/examples/echo-plugin` 提供最小入口、文本回显 Provider 和 JVM 加载测试。
在 `eva-cli` 目录执行 `./gradlew :examples:echo-plugin:check` 可构建并验证该示例。

组件扫描已接入已加载插件查询；records 备份恢复和根节点消息路由仍在实现中。
上述继承约定定义的是插件 API 边界，不能替代宿主的完整运行时调度实现。

## WidgetContext 与宿主能力

`WidgetContext` 位于 `commonMain`，不是 Android `Context`。JVM 加载器为每个插件创建一个 Context，
在发布加载记录前为该插件的所有 Provider 和 Dispatcher 调用 `attach(context)`。
组件构造期间访问 `context` 会失败；在 `start()` 及之后的生命周期方法中使用它。
同一实例可以重复绑定同一个 Context，不能改绑其他 Context。`PluginEntry` 仍须有公开无参构造器。

| 能力 | API | 所有权与边界 |
| --- | --- | --- |
| 身份与协程 | `pluginId`、`scope` | ID 来自清单；scope 属于整个已加载插件。 |
| 网络 | `network.execute(NetworkRequest)` | 宿主使用 Ktor，公共请求/响应不包含 Ktor 类型。 |
| 偏好 | `preferences.get/put/remove`、`values` | 宿主使用 DataStore；字符串值由插件自行序列化，变更通过 Flow 提供。 |
| 数据库 | `databases.open(name, factory)` | 插件创建自己的数据库，宿主管理路径、复用和释放。 |
| 文件 | `files.read/write/delete/list` | 使用相对路径；DATA、CACHE 按插件分开，RESOURCES 只读。 |
| 权限 | `permissions.check/request` | 由宿主桥接平台权限；返回 GRANTED、DENIED 或 UNAVAILABLE。 |

每个插件的安装文件和运行数据集中在 `Plugins/<id>/`，布局如下：

```text
Plugins/<id>/
├── versions/<version>/           # 该版本的完整安装包与只读资源
├── Data/
│   ├── preferences.preferences_pb
│   ├── databases/<name>
│   └── files/
└── Cache/
```

偏好、数据库和普通文件由插件 ID 隔离，在不同版本间共享，升级或卸载加载器后保留。
`files` 的 `DATA` 对应 `Data/files/`，`CACHE` 对应 `Cache/`；目录在使用时按需创建。
`RESOURCES` 指向清单声明的公共资源目录；未声明时不能读取资源。
文件接口拒绝越界路径和符号链接，但同进程插件仍能自行调用 JVM API，因此 Context 不是安全沙盒。

旧布局不会自动迁移。升级宿主前先停止插件并备份，再将 `Plugins/<id>/<version>/`
移至 `Plugins/<id>/versions/<version>/`，将运行根目录下的 `Data/<id>/` 和 `Cache/<id>/`
分别移至 `Plugins/<id>/Data/` 和 `Plugins/<id>/Cache/`；目标存在时应先处理冲突，避免覆盖数据。
迁移后重新生成安装快照，旧布局的快照不适用于新的安装目录。

数据库 factory 返回实现 `PluginDatabase` 的插件对象，可将自身的 `RoomDatabase` 包装其中，
由其 `close()` 关闭数据库。Room schema、DAO、生成代码、驱动和迁移属于插件，由插件构建和打包；
宿主不需要导入插件数据库类或添加 Room 编译器。公共 API 不跨加载器传递宿主的 Room 类型。
同一个数据库名仅允许使用同一个 `PluginDatabaseFactory` 对象，后续调用复用已打开实例；
同名换用另一个 factory 会报错，应以插件级单例保存 factory，而不是反复创建 lambda。
同名数据库初始化期间拒绝递归或并发打开；可在初始化完成后重试。factory 在调用线程执行，应在后台调用。

权限服务默认返回 `UNAVAILABLE`；应用可为 `PlugManager.permissions` 设置自己的 `PluginPermissions`
实现，将请求转交当前 UI 生命周期。Context 动态访问该服务，宿主应在 UI 销毁时移除旧桥接。
Android 权限仍须由宿主 manifest 声明；此接口没有启用尚未完成的 Android/iOS 插件加载。

单个组件 `stop()` 应取消并等待自己创建的 Job，不能取消整个 `context.scope` 或关闭共享数据库、网络服务。
调用方停止并注销所有组件后执行 `unloadPlugin`，宿主释放 Context 的任务和服务，再关闭加载器。
应用持有的权限桥接由应用管理。释放加载器不删除持久数据，也不会终止插件自行创建且未纳入 scope 的线程。
同步卸载必须由插件 scope 和数据库 factory 外的宿主代码执行；从内部卸载会立即报错并保留加载记录。

网络、偏好、文件及 Room 包装示例见 `eva-cli/examples/echo-plugin/README.md`。
发布的独立 SDK 必须包含 `BaseWidget.kt`、`widgets/context/*.kt` 和已有协程依赖，
以便解析 `CoroutineScope` 与 `Flow`。插件仍须重新编译为 API 4，JAR 中不包含宿主 API 类。

## JVM 本地安装

JVM 宿主可通过 `PlugManager.installPlugin(PluginSource.File(uri, sha256))` 安装本地发布包。
安装器会先计算可选的 SHA-256 摘要，再将 ZIP 解压到 `Plugins` 目录下的临时目录，读取并校验
根目录 `plugin.json`，确认 `formatVersion` 和 `apiVersion` 均为当前版本、声明了 JVM 产物，
并检查 `minJavaVersion`、`artifact` 文件和 `entryClass` 类文件存在。校验成功后，目录会原子移动到
`Plugins/<id>/versions/<version>`；相同清单已经安装时操作幂等，目标目录存在但清单不一致会失败。
ZIP 中的绝对路径、父目录跳转和重复路径会被拒绝，失败时临时目录会清理。

URL 和 Git 来源当前会明确抛出 `UnsupportedOperationException`。Android 和 iOS 保留相同 API，
但本地安装器尚未接入各自的平台运行时。

## 查询已安装插件

JVM 宿主通过只读属性 `PlugManager.installedList` 获取当前安装目录的快照：

```kotlin
val installed = PlugManager.installedList
for (plugin in installed) {
    println("${plugin.manifest.id}:${plugin.manifest.version} -> ${plugin.directory}")
}
```

返回类型为 `List<InstalledPluginInfo>`，每项包含清单 `manifest` 和安装目录的绝对路径
`directory`。每个版本单独列出，按插件 ID、版本字符串排序（不是语义版本优先级）。
每次读取都扫描 `Plugins/<id>/versions/<version>`，无需再次安装或保留原 `.eva`；重启后仍可发现
已有安装，目录删除后下一次查询会反映变化。查询为同步文件 I/O，应在后台线程调用。

查询复用安装器的清单与 JVM 产物校验，跳过 `.eva-install-*` 临时目录、不完整、损坏、
不兼容及目录名与清单 ID/版本不一致的安装项，不跟随插件目录、版本目录或清单的符号链接。
`Plugins` 不存在时返回空列表，不创建目录；根路径不是普通目录或目录遍历失败时抛出
`IllegalStateException`。Android/iOS 查询当前抛出 `UnsupportedOperationException`。

列表信息不包含安装调用的 `alreadyInstalled` 标志，也不推算原始发布包的 SHA-256；
这些字段仍由 `installPlugin` 返回的 `InstalledPlugin` 提供。

## 加载与释放插件实例

JVM 宿主从已安装的指定版本创建插件入口，并获取该入口提供的组件：

```kotlin
val loaded = PlugManager.loadPlugin("com.example.echo", "0.1.0-alpha")
val entry = loaded.entry
val providers = loaded.providers
val dispatchers = loaded.dispatchers
val snapshot = PlugManager.loadedList

// 使用组件的运行时完成停止、解绑及移除组件引用后，释放 Context 和加载器。
val unloaded = PlugManager.unloadPlugin("com.example.echo")
```

`loadPlugin(id, version)` 返回 `LoadedPlugin`，包含清单、安装目录绝对路径、`PluginEntry`
实例、已绑定的 `context` 及加载时读取的 Provider、Dispatcher 列表快照。每个插件 ID 在进程内只加载一个版本；
同一版本和安装路径重复调用会返回同一个对象。切换版本或运行目录前，应先释放原实例。
入口构造、组件读取、校验或 Context 绑定失败时不发布加载记录，并释放此次创建的 Context 和加载器。

`loadedList` 返回按插件 ID 排序的已加载实例列表快照，不扫描磁盘，也不会自动加载插件。
后续加载、释放不会修改旧列表；列表中的入口和组件仍是同一批运行时对象。
`unloadPlugin(id)` 释放 Context、关闭加载器并移除记录，成功返回 `true`，未加载返回 `false`。
释放后可以重新加载得到新的入口实例；安装文件保留，删除安装文件仍使用 `uninstallPlugin`。

加载器读取主 JAR 和清单 `platforms.jvm.dependencies` 目录中的依赖 JAR，
复用安装器的清单与兼容性检查，并检查入口是主 JAR 中提供公开无参构造器的 `PluginEntry`。
插件类和资源优先从自身 JAR 和私有依赖加载；JDK、Kotlin、kotlinx 及宿主核心 API 优先使用宿主类型，
宿主未提供的类再从插件 JAR 查找，从而允许插件组件直接接收宿主事件。
依赖目录递归扫描，JAR 按路径排序；不跟随 JAR Manifest 的 `Class-Path` 或 `META-INF/INDEX.LIST`，
含这些隐式类路径声明的 JAR 会被拒绝，依赖应放在明确声明的目录中。
安装路径、清单、产物及依赖路径中的符号链接会被拒绝。

这三个 API 管理加载记录、Context 绑定和资源释放。组件的 topic 注册、事件回调绑定、`start/stop`
由宿主运行时负责；`Booter.start()` 从已加载组件中选择根调度器并启动。调用方停止组件并移除引用后再释放，
关闭 ClassLoader 不会强制终止插件线程，也不保证 JVM 立即回收已有类或外部仍持有的对象。
Context 和加载器均会尝试释放；清理报错时聚合异常并移除登记，避免继续返回已部分关闭的实例。
删除已加载版本、恢复快照或改写其 JAR 前，应先释放相关插件；不要与这些磁盘操作并发调用加载 API。

非法 ID/版本抛出 `IllegalArgumentException`；未安装、版本冲突、正在加载/释放或加载失败时
抛出 `IllegalStateException`。加载和释放包含同步磁盘访问，应在后台线程调用。
Android/iOS 当前的三个 API 均明确抛出 `UnsupportedOperationException`。

## 运行时批量加载

`com.eva_cli.core.runtime.Plugin.start()` 在 `Configure.start()` 之后、`Booter.start()`
之前调用，读取 `PlugManager.installedList` 并加载所有有效的已安装插件，供 Dispatcher 扫描。
空目录直接返回；无效安装沿用 `installedList` 的过滤规则。此阶段只加载入口与组件，
组件扫描、回调绑定和启停仍由后续的调度树生命周期负责。

同一 ID 存在多个有效安装版本时，在加载任何新插件前抛出 `IllegalStateException`，
错误包含冲突 ID 和版本；调用前须为每个 ID 保留一个安装版本，不自动选择最新版本。
重复调用复用同版本、同安装路径的已加载实例，也会发现之后新安装的插件。
加载失败会逆序释放本次新增的实例，保留调用前的已加载实例及安装文件；
清理失败会附加到原异常，不中断剩余实例的清理。

此方法执行同步文件 I/O，应在后台线程、组件启动前调用，与安装、加载、释放及运行目录
变更串行执行。Android/iOS 当前沿用 `PlugManager` 的未支持异常。
该方法仅负责加载阶段的回滚；后续 `Booter.start()` 失败时的调度树清理仍由运行时负责。

`Booter.start()` 将已加载插件中唯一 `topic == "/"` 的 `PluginDispatcher` 作为
根调度器，直接启动该实例；宿主不内置 RootDispatcher，也不依赖组件名称或具体实现类型。
根节点缺失或重复时，在启动任何组件前抛出 `IllegalStateException`。
根调度器负责扫描子节点、绑定回调并启动整棵树，`Booter` 不参与事件调度。
重复启动前须先停止；启动失败时保留根引用，调用方须执行 `Booter.stop()` 清理后再重试。
`stop()` 成功后释放根引用，未启动时停止为空操作。生命周期操作须串行执行。

## 按 topic 查询组件

```kotlin
val providers = PlugManager.findProviders("/Chat")
val children = PlugManager.findChildDispatchers("/Chat")
val topLevel = PlugManager.findChildDispatchers("/")
```

`findProviders(topic)` 返回已加载插件中 topic 完全匹配的 `PluginProvider` 列表。
`findChildDispatchers(topic)` 返回直接子级的 `PluginDispatcher` 列表：查询 `/Chat`
可匹配 `/Chat/Search`，不会匹配 `/Chat`、`/Chat/Search/Web` 或 `/Chatty/Search`。
查询根路径 `/` 可匹配 `/Chat`，根节点自身不属于子级。

topic 区分大小写，必须是以 `/` 开头的规范路径；根路径允许为 `/`，其余路径不能以 `/` 结尾。
路径段不允许为空、纯空白、带首尾空白、`.`、`..`，也不允许包含反斜杠或控制字符。
非法查询参数抛出 `IllegalArgumentException`；声明了非法 topic 的组件不会被这些查询匹配。

查询直接使用 `loadedList` 中的组件快照，结果按插件 ID、插件内声明顺序排列，
包含未启用的组件，返回原组件实例，不执行注册、回调绑定或生命周期方法。
仅安装而未加载的插件不会被查询到；释放后下一次查询不再包含该插件，旧结果列表保持不变。
无匹配时返回空列表。Android/iOS 当前随 `loadedList` 抛出 `UnsupportedOperationException`。

## Dispatcher 扫描组件

`BaseDispatcher.scanProviders()` 通过 `findProviders(topic)` 刷新当前节点的 `providers`；
`scanDispatchers()` 通过 `findChildDispatchers(topic)` 刷新 `dispatchers`，只处理直接子级。
两者按组件 ID 建立映射，保留原组件实例、查询顺序及未启用组件，不递归扫描或绑定回调。

每次扫描先构建并校验候选映射，再替换原映射内容；映射对象保持不变。
重复扫描不累积组件，不再出现在查询结果中的旧组件会被移除；空结果清空对应映射。
同一次扫描内的重复组件 ID、子 Dispatcher 与当前节点 ID 相同，或多个子 Dispatcher
占用同一 topic 时抛出 `IllegalStateException`；查询或校验失败保留该方法对应的原映射。
同一 topic 可以有多个不同 ID 的 Provider。两个扫描方法分别更新各自映射，不构成整棵树的事务。

扫描应在启动前或停止后串行调用。组件的 `start/stop` 和回调绑定由生命周期流程处理，
运行中的组件移除、热更新和全树冲突检查尚未接入。根调度器使用 `/` topic，
扫描不会自动修改根路径或为只有 Provider 的 topic 创建 Dispatcher。

## 卸载已安装插件

JVM 宿主通过插件 ID 和版本卸载指定安装：

```kotlin
val removed = PlugManager.uninstallPlugin("com.example.echo", "1.0.0")
```

删除 `Plugins/<id>/versions/<version>` 及其内容后返回 `true`；目标不存在时返回 `false`，不创建目录。
其他版本、插件及该插件的 `Data`、`Cache` 保留，空的 `versions`、ID 父目录和 `Plugins` 根目录也保留。下一次读取 `installedList`
会反映卸载结果。卸载不依赖清单或 JAR 校验，因此也可清理损坏、不完整或不兼容的安装。

ID 和版本必须是安全的单个目录名，非法参数抛出 `IllegalArgumentException`。
根目录、ID 目录、`versions` 或版本目录是符号链接或普通文件时拒绝卸载；目标目录内部的符号链接只删除
链接本身。目录结构无效或文件删除失败时抛出 `IllegalStateException`；删除失败可能留下部分
文件，可在解决文件占用或权限问题后重试。此操作同步访问磁盘，应在后台线程调用。
Android/iOS 当前抛出 `UnsupportedOperationException`。

## 备份与恢复插件

JVM 宿主通过以下方法保存和恢复插件安装快照：

```kotlin
PlugManager.backup(includeRuntimeData = false)
PlugManager.backup(includeRuntimeData = true)
PlugManager.restore()
```

`backup(includeRuntimeData = false)` 只将 `installedList` 中所有有效插件版本及其完整目录（JAR、源码、资源和空目录）
保存到运行目录下的 `Backup/plugins.zip`，不包含运行数据。传入
`includeRuntimeData = true` 时，同时备份已安装插件按 ID 共享的 `Data` 目录；`Cache` 始终排除。
每次成功备份更新这份快照，失败时保留上一次快照。快照包含格式版本、插件列表、文件目录清单及文件 SHA-256；
没有已安装插件时也会生成有效的空快照。无效安装、安装临时目录以及未启用参数时的各插件 `Data`、`Cache`
不会进入快照；安装版本目录和运行数据目录内的符号链接或特殊文件会使对应备份失败。
备份运行数据前，相关插件必须先停止并卸载，避免复制仍在写入的文件。

`restore()` 先在临时目录中校验快照清单、文件摘要及 JVM 插件产物，再替换各插件的 `versions` 目录。
含运行数据的快照还会精确替换快照中插件的 `Data`：备份时没有 `Data` 表示恢复时清除该目录；
快照外插件的 `Data` 和所有插件的 `Cache` 始终保留。没有运行数据的快照只替换 `versions`，保留当前运行数据。
因此备份后新增的安装版本会被移除，同版本修改过或删除的安装文件会恢复；空快照会清除所有安装版本。
快照保留，可重复恢复。根目录的宿主数据、`Events`、`Logs` 及已加载的插件实例不受影响；恢复运行数据前相关插件必须先卸载，恢复后应重新加载插件。

缺失、损坏或不兼容的快照会抛出 `IllegalStateException`，校验失败不会修改当前插件；
目录替换失败时会尝试回滚。备份和恢复不支持与安装、卸载或插件文件写入并发执行，
也不保证进程崩溃或断电时的事务恢复。两个方法均为同步磁盘操作，应在后台线程调用。
Android/iOS 当前与安装器保持一致，明确抛出 `UnsupportedOperationException`。

## 平台接入规则

- PC：解压后使用 URLClassLoader 加载 `prebuilt/jvm/plugin.jar` 和 `prebuilt/jvm/lib/` 中的私有依赖，
  创建插件入口并注册其 Dispatcher、Provider。
- Android：将 Android target 的编译结果及私有依赖经 D8 转换为 `prebuilt/android/plugin-dex.jar`，
  安装时通过 DexClassLoader 加载并注册。D8 不会自动合并所有非代码资源，打包时需单独处理。
- iOS 使用 KLIB：将包内 `prebuilt/maven/` 加入 Gradle 仓库，在宿主 iosMain 中依赖插件坐标，
  生成静态注册代码，最终统一链接进宿主 Framework 和应用。
- iOS 使用源码：通过 Gradle includeBuild 接入解压后的工程根目录，并配置依赖替换，
  在宿主 iosMain 中依赖插件模块，生成静态注册代码并随宿主编译。

同一插件在 iOS 构建中选择 KLIB 或源码中的一种方式，避免重复接入。
新增或更新 iOS 插件需要重新构建、签名和分发应用；源码及依赖须与宿主工具链兼容。

## 二次开发流程

1. 解压 `.eva`，直接在 Android Studio 或 IntelliJ IDEA 中打开包含 `settings.gradle.kts` 的根目录。
2. 根据 Gradle Wrapper、版本目录和清单中的工具链信息配置开发环境，解析插件 API 与其他依赖。
3. 修改 `src/` 中的实现及所需的 `resources/`、`plugin.json`，运行 commonTest 和对应平台测试。
4. 通过测试宿主或宿主开发模式加载本次构建的插件进行调试；iOS 从源码集成后重新构建宿主应用。
5. 使用统一打包任务 `assembleEvaPlugin` 生成新版本 `.eva`，再验证各平台的安装或编译接入。

为支持上述流程，插件 SDK 和宿主需要提供以下配套能力：

- 可获取的 `eva-plugin-api` 发布仓库、版本及兼容范围，让解压后的工程可以独立解析依赖。
- 测试宿主或开发模式，明确本次构建产物的加载位置、插件配置方式及日志入口。
- 统一构建与打包任务，从当前源码生成平台产物及清单，避免调试或发布时误用旧预编译文件。

Echo 示例已实现 JVM `assembleEvaPlugin` 和最小测试宿主，实际命令见示例 README。
在统一 `eva-plugin-api` 尚未发布期间，该示例随包提供当前 API 的源码模块以支持独立构建，
插件 JAR 不包含这份 API；统一 SDK 发布、其他平台打包和正式宿主开发模式仍待实现。

## 公共 API、依赖与资源

- 插件源码正常依赖统一的 `eva-plugin-api` KMP 模块，公共源码不统一使用 compileOnly，
  以保证 Kotlin/Native 可以解析对应的 API KLIB 和依赖元数据。
- JVM、Android 运行时打包阶段排除宿主提供的 API 及约定共享的运行库，并校验兼容版本。
  加载器以宿主 API 的 ClassLoader 为父级，保证接口类型一致。
- 启用混淆时保留插件入口及共享 API 的名称、签名。
- PC、Android 通过宿主接口读取解压后的公共资源；iOS 构建时将所需资源复制进应用 Bundle。
- Android DEX 加载不会自动安装 UI 资源、声明权限、注册 Activity/Service 或加载各 ABI 的 JNI 库。
  此结构首先面向业务逻辑插件，平台专属资源和原生库需要另行扩展打包与加载流程。
- 针对 Android API 34 及以上的应用，动态代码文件的创建、提取和加载须满足系统的只读要求。

## 打包与验证

自定义 `assembleEvaPlugin` 任务以当前源码和配置为输入，在临时组装目录中生成发布包：

1. 编译 JVM 产物并收集私有依赖，组装到 `prebuilt/jvm/`。
2. 编译 Android 产物并执行 D8，将 DEX 和必要资源组装到 `prebuilt/android/`。
3. 将 KMP 库发布到 `prebuilt/maven/`，保留根模块、各 target 的产物及依赖元数据。
4. 复制工程构建文件、`src/`、公共资源和说明文件到包根目录，生成或校验 `plugin.json` 的版本与路径。
5. 将组装目录中的内容压缩为 `.eva`，确保解压根目录直接包含 `settings.gradle.kts` 和 `plugin.json`。

源码收集使用明确的文件范围，排除旧 `prebuilt/`、`build/`、`.gradle/` 和 `.git/`，
避免把旧产物、缓存或当前打包输出再次纳入发布包。发布包中的预编译文件全部取自本次构建。

发布前验证 PC 实际加载、Android 设备上的 DEX 加载，以及 iOS 真机和模拟器目标的链接构建。
同时验证解压后可以直接打开工程，并在不依赖包内预编译产物的情况下，从源码重新编译和打包。

## Plugin 描述字段

宿主中的 `Plugin` 保存插件身份、来源、启用状态和业务配置。
`id`、`name` 根据 `plugin.json` 填充；发布版本、API 版本和平台产物位置仍由清单描述。

| 字段 | 必填 / 默认值 | 说明 |
| --- | --- | --- |
| `Plugin.id` | 必填 | 清单中的稳定唯一标识，用于安装、升级和去重。 |
| `Plugin.name` | 必填 | 清单中的插件展示名称。 |
| `Plugin.source` | 必填 | 插件来源信息，类型为 `PluginSource`。 |
| `Plugin.enabled` | `true` | 宿主是否启用插件。 |
| `Plugin.config` | 空 JSON 对象 | 插件业务配置，类型为 `JsonObject`，支持嵌套对象、数组和不同 JSON 值类型。 |

`PluginSource` 为密封接口，使用对应的数据类构造来源。
`sourceType` 由数据类确定，取值为 `URL`、`GIT`、`FILE`，无需单独配置。

| 来源 | 字段 | 必填 / 默认值 | 说明 |
| --- | --- | --- | --- |
| `PluginSource.Url` | `url` | 必填 | `.eva` 发布包下载地址。 |
| `PluginSource.Url` | `sha256` | `null` | 预期发布包 SHA-256 摘要，未指定时不提供摘要校验依据。 |
| `PluginSource.Git` | `repository` | 必填 | Git 仓库地址。 |
| `PluginSource.Git` | `ref` | `null` | 分支、标签或提交号；未指定时约定使用仓库默认分支。 |
| `PluginSource.Git` | `directory` | `"."` | 仓库内插件工程的相对目录，默认仓库根目录。 |
| `PluginSource.File` | `uri` | 必填 | 本地 `.eva` 文件路径或平台文件 URI。 |
| `PluginSource.File` | `sha256` | `null` | 预期发布包 SHA-256 摘要。 |

```kotlin
val plugin = Plugin(
    id = "com.example.chat",
    name = "Chat Plugin",
    source = PluginSource.Url(url = "https://example.com/chat-plugin-1.0.0.eva"),
)

val gitSource = PluginSource.Git(
    repository = "https://example.com/chat-plugin.git",
    ref = "v1.0.0",
)

val fileSource = PluginSource.File(uri = "/path/to/chat-plugin-1.0.0.eva")
```

清单中的 `source` 描述包内源码工程的位置和模块坐标，与宿主 `Plugin.source` 的来源信息用途不同。
上述模型仅保存字段；清单读取、下载、Git 获取、文件访问、摘要校验及启用和配置应用逻辑仍需由安装器或宿主实现。
