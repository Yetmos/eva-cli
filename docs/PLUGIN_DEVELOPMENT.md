# Eva 插件开发与打包

本文说明如何开发 Eva 插件、生成 `.eva` 发布包，并在 JVM 宿主中安装和加载插件。

当前仓库已经实现 JVM 插件的编译、打包、安装和加载。Android 动态加载与 iOS 构建期接入仍在开发中，因此本文的可执行流程以 JVM 为准。

## 开发环境

- Java 17 或更高版本
- Gradle Wrapper（仓库已提供 `gradlew` 和 `gradlew.bat`）
- IntelliJ IDEA 或 Android Studio
- 首次构建需要联网下载 Gradle、Kotlin 和其他依赖

推荐从 [Echo 插件示例](../plugins/echo-plugin/README.md) 开始。它包含最小入口、Provider、Dispatcher、测试和完整的 `.eva` 打包任务。

## 创建插件工程

在仓库根目录执行以下命令，先构建示例发布包：

```powershell
.\gradlew.bat :plugins:echo-plugin:assembleEvaPlugin
```

生成的文件位于：

```text
plugins/echo-plugin/build/distributions/echo-plugin-1.0.0-alpha.eva
```

`.eva` 是 ZIP 文件。解压后，包含 `settings.gradle.kts` 的目录就是一个可以独立打开和构建的 Gradle 工程。解压后的工程包含插件源码、构建脚本、插件 API 源码快照和当前构建生成的 JVM JAR。

解压后的工程可以直接复制为新插件工程。复制后至少需要修改：

- `settings.gradle.kts` 中的 `rootProject.name`；
- `build.gradle.kts` 中的 `group` 和 `version`；
- `plugin.json` 中的插件 ID、名称、版本、坐标和入口类；
- `src/` 下的包名、类名和业务逻辑；
- README、许可证和第三方依赖声明。

插件 ID 是稳定标识，升级版本时应保持不变。发布新版本时同时更新构建版本、`plugin.json.version` 和 `source.coordinate` 中的版本。

## 编写插件代码

插件入口实现 `PluginEntry`，并返回 Provider 和 Dispatcher 实例：

```kotlin
package com.example.chat

import com.eva_cli.core.widgets.plug.PluginEntry

class ChatPlugin : PluginEntry {
    override val providers = listOf(ChatProvider())
    override val dispatchers = listOf(ChatDispatcher())
}
```

入口类必须有公开无参构造器，且类名必须与 `plugin.json` 中的 `entryClass` 完全一致。入口应返回固定的组件实例集合，不要在每次读取属性时重新创建组件。

### Provider

Provider 负责接收和处理事件。通常继承 `PluginProvider`，实现 `id`、`name`、`desc`、`topic`、`enable`、`status`、`records`、`emit` 以及生命周期方法。

```kotlin
class ChatProvider : PluginProvider() {
    override val id = Uuid.random()
    override val name = "Chat Provider"
    override val desc = "Handles chat messages"
    override val topic = "/Chat"
    override var enable = true
    override var status = Provider.Status.STOP
    override var records: List<EventRecord> = emptyList()
    override var emit: (suspend (Event) -> Unit)? = null

    override suspend fun start() {
        status = Provider.Status.IDLE
    }

    override suspend fun stop() {
        status = Provider.Status.STOP
    }

    override suspend fun receive(event: Event) {
        if (!enable || status == Provider.Status.STOP) return
        // 读取 event.payload，处理业务，然后通过 emit 输出事件。
    }
}
```

`start()`、`stop()` 和 `receive()` 都是挂起函数，宿主应在协程中串行调用。组件停止时取消自己创建的 `Job`，不要取消属于整个插件的共享 `context.scope`。

### Dispatcher

Dispatcher 按 `topic` 路由事件，通常继承 `PluginDispatcher`：

```kotlin
class ChatDispatcher : PluginDispatcher() {
    override val name = "Chat Dispatcher"
    override val desc = "Routes chat events"
    override val topic = "/Chat"

    override suspend fun dispatch(event: Event) {
        if (!enable) return
        providers.values.single().receive(event)
    }

    override suspend fun receive(event: Event) {
        if (!enable) return
        checkNotNull(emit)(event)
    }
}
```

`topic` 必须是以 `/` 开头的规范路径。根 Dispatcher 使用 `/`；普通节点不能以 `/` 结尾，也不能包含空路径段、`.`、`..`、反斜杠或控制字符。

## 使用宿主能力

组件绑定宿主提供的 `WidgetContext` 后，可以通过 `context` 使用跨平台能力：

- `context.network`：网络请求；
- `context.preferences`：插件独立的字符串偏好；
- `context.files`：`DATA`、`CACHE` 和只读的 `RESOURCES` 文件区域；
- `context.databases`：由插件提供实现的数据库包装；
- `context.permissions`：权限查询和请求；
- `context.scope`：插件生命周期范围内的协程作用域。

文件路径必须是相对路径，不能使用绝对路径或 `..`。插件的持久数据和缓存由宿主按插件 ID 隔离保存。插件代码运行在宿主 JVM 进程内，这些接口提供资源边界，但不是安全沙盒。

## 配置 `plugin.json`

最小 JVM 清单如下：

```json
{
  "formatVersion": 1,
  "id": "com.example.chat",
  "name": "Chat Plugin",
  "version": "0.1.0",
  "apiVersion": 5,
  "platforms": {
    "jvm": {
      "artifact": "prebuilt/jvm/plugin.jar",
      "entryClass": "com.example.chat.ChatPlugin",
      "minJavaVersion": 17
    }
  },
  "source": {
    "coordinate": "com.example:chat-plugin:0.1.0",
    "directory": ".",
    "gradleProject": ":"
  },
  "builtWith": {
    "kotlin": "2.4.20"
  }
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `formatVersion` | `.eva` 包格式版本，当前为 `1`。 |
| `id` | 稳定且唯一的插件 ID，用于安装、加载、升级和卸载。 |
| `name` | 展示名称。 |
| `version` | 插件发布版本。 |
| `apiVersion` | 插件 API 版本，当前为 `5`。 |
| `platforms.jvm.artifact` | JVM JAR 相对于包根目录的路径。 |
| `platforms.jvm.entryClass` | 公开无参入口类的全限定名。 |
| `platforms.jvm.dependencies` | 可选的私有依赖目录。 |
| `source` | 解压后源码工程的位置和坐标。 |
| `builtWith.kotlin` | 构建插件时使用的 Kotlin 版本。 |

清单中的所有路径都相对于 `.eva` 解压后的根目录。不能只修改清单来兼容旧 API；升级 API 后必须更新 SDK、重新编译和重新打包。

## 依赖和资源

插件 JAR 不应重复打包宿主提供的 Eva API、Kotlin 运行库和约定共享库。插件自己的运行依赖应放在：

```text
prebuilt/jvm/lib/
```

并在清单中声明：

```json
"dependencies": "prebuilt/jvm/lib"
```

Echo 示例的打包脚本只处理插件 JAR。如果加入私有依赖，需要参考 `plugins/cmd-plugin/packaging/eva-plugin.gradle.kts`，在 `prepareJvmPlugin` 中复制依赖 JAR，并在独立工程中保持一致配置。

公共资源可以放在 `resources/`，再在清单中声明：

```json
"resources": "resources"
```

资源通过 `context.files` 的 `PluginFileArea.RESOURCES` 读取，该区域是只读的。

## 测试、打包和验证

在插件工程根目录执行：

```powershell
.\gradlew.bat check
.\gradlew.bat prepareJvmPlugin
.\gradlew.bat assembleEvaPlugin
```

其中：

- `check`：运行单元测试和 JVM 加载测试；
- `prepareJvmPlugin`：只生成开发加载目录；
- `assembleEvaPlugin`：运行检查、组装完整发布内容并生成 `.eva` 文件。

开发加载目录为：

```text
build/plugin/
├── plugin.json
└── prebuilt/jvm/plugin.jar
```

最终发布包为：

```text
build/distributions/<插件名>-<版本>.eva
```

`.eva` 解压后应直接包含 `plugin.json`、`settings.gradle.kts`、`src/` 和 `prebuilt/`。源码工程应能在删除 `prebuilt/` 后重新编译，避免误用旧的预编译产物。打包任务会排除 `build/`、`.gradle/`、`.kotlin/`、`.git/` 和本机配置文件。

## 在 JVM 宿主中安装和加载

JVM 宿主使用 `PlugManager` 安装本地包：

```kotlin
val installed = PlugManager.installPlugin(
    PluginSource.File("C:/plugins/chat-plugin-0.1.0.eva")
)

val loaded = PlugManager.loadPlugin(
    installed.manifest.id,
    installed.manifest.version,
)
```

安装器会校验 ZIP 路径、`plugin.json`、格式版本、API 版本、Java 版本、JAR 路径和入口类，然后把插件安装到：

```text
Plugins/<id>/versions/<version>/
```

安装后可以通过 `PlugManager.installedList` 查询已安装插件，通过 `PlugManager.loadedList` 查询已加载插件。停止组件并释放资源后卸载：

```kotlin
PlugManager.unloadPlugin("com.example.chat")
PlugManager.uninstallPlugin("com.example.chat", "0.1.0")
```

同一个插件 ID 同时存在多个有效版本时，批量加载会报告版本冲突；发布或升级时应先清理不需要的版本。安装、加载、卸载和备份是同步磁盘操作，应放在后台线程执行，并避免并发修改插件目录。

当前 URL 和 Git 来源安装接口尚未实现，本地 `PluginSource.File` 是可用的安装方式。

## 使用 CLI 管理插件

构建 CLI 发行版后，可以使用 `eva` 命令管理本地 `.eva` 包：

```text
eva plugin install <path> [sha256]
eva plugin list
eva plugin remove <id> <version>
eva plugin load <id> <version>
eva plugin unload <id>
```

例如，在仓库内直接运行：

```powershell
.\gradlew.bat :cli:run --args="plugin install ../plugins/echo-plugin/build/distributions/echo-plugin-1.0.0-alpha.eva"
.\gradlew.bat :cli:run --args="plugin list"
```

`install` 会校验并安装本地 `.eva` 文件；`list` 显示当前运行目录下的有效安装；`remove` 删除指定版本，已加载的插件必须先释放；`load` 和 `unload` 分别验证当前进程中的加载和释放流程。

CLI 每次执行都是独立进程，`load` 或 `unload` 完成后进程即退出，运行态不会跨命令保存。要让插件参与命令行处理，应先安装插件，再运行 `eva cmd`；`Eva.start()` 会自动加载已安装插件并启动 Dispatcher 树。

## 发布检查清单

发布前建议逐项确认：

1. `plugin.json` 的 `id`、`version`、`apiVersion`、`entryClass` 和 artifact 路径正确。
2. 入口类是公开类，并且有无参构造器。
3. 所有 Provider 和 Dispatcher 的 topic 合法且没有重复冲突。
4. `check` 和 JVM 实际加载测试通过。
5. 私有依赖已放入 `prebuilt/jvm/lib/`，并在清单中声明。
6. README、许可证和第三方声明已随包提供。
7. 解压 `.eva` 后可以独立执行 `check` 和 `assembleEvaPlugin`。
8. 发布包中的源码和 JAR 来自同一次构建，版本号保持一致。

## 平台状态

- **JVM/PC**：支持 `.eva` 安装、清单校验、JAR 加载和运行时组件注册。
- **Android**：清单模型已预留 Android 产物字段，但本地动态安装和加载尚未实现。
- **iOS**：设计上支持 KLIB 或源码构建期接入，当前运行时本地 `.eva` 安装尚未实现。

相关实现和规范：

- [Echo 插件示例](../plugins/echo-plugin/README.md)
- [`.eva` 发布包规范](../core/src/commonMain/kotlin/com/eva_cli/core/widgets/plug/README.md)
- [插件清单模型](../core/src/commonMain/kotlin/com/eva_cli/core/widgets/plug/PluginManifest.kt)
