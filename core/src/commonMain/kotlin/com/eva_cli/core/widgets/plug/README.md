# .eva 插件发布包规范

`.eva` 使用 ZIP 格式，解压后直接成为一个可用 Android Studio 或 IntelliJ IDEA 打开的标准 KMP 工程。
源码、构建配置、插件清单和公共资源位于工程根目录，预编译产物统一放在 `prebuilt/`。
PC 加载 JVM JAR，Android 加载 DEX，iOS 在构建时选择 KLIB 或源码集成。
以下内容是发布格式与接入流程的设计约定。JVM 宿主已支持本地 `.eva` 校验、解压和安装；
Android 打包、iOS 构建接入以及运行时 JAR 加载仍需后续实现。发布包仅需包含所声明平台的产物。

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
  "apiVersion": 1,
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
`WidgetDispatcher`，Provider 继承 `WidgetProvider`。这两个类是插件扩展入口；
`BaseDispatcher` 和 `BaseProvider` 是宿主侧的基础实现，插件不应直接依赖它们的内部实现。

一个插件可以包含多个 Dispatcher 或 Provider，但每个具体组件只承担一种组件职责。
不要让同一个类同时继承 `WidgetDispatcher` 和 `WidgetProvider`；需要同时提供两种能力时，
应注册两个独立的组件实例，由插件入口负责创建和注册它们。

Dispatcher 示例：

```kotlin
import com.eva_cli.core.widgets.dispatcher.WidgetDispatcher
import com.eva_cli.core.widgets.event.Event

class ChatDispatcher : WidgetDispatcher() {
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
import com.eva_cli.core.widgets.provider.WidgetProvider
import kotlin.uuid.Uuid

class ChatProvider : WidgetProvider() {
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

`WidgetProvider` 已经包含 `Provider` 的宿主生命周期契约，具体 Provider 仍需实现自己的
元数据、事件处理和输出回调。若后续提供输入、输出或其他专用 Provider 基类，具体实现应
继承职责最接近的一个基类，不需要重复继承 `WidgetProvider`。

插件入口实现 `PluginEntry`，通过 `providers` 和 `dispatchers` 提供组件列表，未提供的
组件种类默认为空列表。同一入口实例始终持有同一组组件实例；宿主负责注册、绑定回调及
调用组件的 `start` / `stop`，读取列表不会启动组件。入口类名必须与 `plugin.json` 中
对应平台的 `entryClass` 或 `factory` 一致。

仓库内的 `eva-cli/examples/echo-plugin` 提供最小入口、文本回显 Provider 和 JVM 加载测试。
在 `eva-cli` 目录执行 `./gradlew :examples:echo-plugin:check` 可构建并验证该示例。

当前仓库中的扫描、备份和恢复流程仍在实现中；上述继承约定定义的是插件 API 边界，不能替代
宿主的组件注册和运行时调度实现。

## JVM 本地安装

JVM 宿主可通过 `PlugManager.installPlugin(PluginSource.File(uri, sha256))` 安装本地发布包。
安装器会先计算可选的 SHA-256 摘要，再将 ZIP 解压到 `Plugins` 目录下的临时目录，读取并校验
根目录 `plugin.json`，确认 `formatVersion` 和 `apiVersion` 均为当前版本、声明了 JVM 产物，
并检查 `minJavaVersion`、`artifact` 文件和 `entryClass` 类文件存在。校验成功后，目录会原子移动到
`Plugins/<id>/<version>`；相同清单已经安装时操作幂等，目标目录存在但清单不一致会失败。
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
每次读取都扫描 `Plugins/<id>/<version>`，无需再次安装或保留原 `.eva`；重启后仍可发现
已有安装，目录删除后下一次查询会反映变化。查询为同步文件 I/O，应在后台线程调用。

查询复用安装器的清单与 JVM 产物校验，跳过 `.eva-install-*` 临时目录、不完整、损坏、
不兼容及目录名与清单 ID/版本不一致的安装项，不跟随插件目录、版本目录或清单的符号链接。
`Plugins` 不存在时返回空列表，不创建目录；根路径不是普通目录或目录遍历失败时抛出
`IllegalStateException`。Android/iOS 查询当前抛出 `UnsupportedOperationException`。

列表信息不包含安装调用的 `alreadyInstalled` 标志，也不推算原始发布包的 SHA-256；
这些字段仍由 `installPlugin` 返回的 `InstalledPlugin` 提供。

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
