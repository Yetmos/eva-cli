# .eva 插件发布包规范

`.eva` 使用 ZIP 格式，解压后直接成为一个可用 Android Studio 或 IntelliJ IDEA 打开的标准 KMP 工程。
源码、构建配置、插件清单和公共资源位于工程根目录，预编译产物统一放在 `prebuilt/`。
PC 加载 JVM JAR，Android 加载 DEX，iOS 在构建时选择 KLIB 或源码集成。
以下内容是发布格式与接入流程的设计约定，需由后续打包、安装和构建工具实现。

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

这些配套工具和任务尚属于设计约定；完成实现后，插件 README 应记录实际可执行的构建和调试命令。

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
