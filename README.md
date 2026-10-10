# eva-cli

面向多 agent 编排框架的独立 Gradle 工程，优先支持 macOS，并兼容 Windows、Linux。该目录包含跨平台核心代码与 CLI，不包含 Compose UI。

当前提供 `Eva` 与 `Runtime` 基础入口，以及通过 CMD 插件处理文本命令的 CLI 模式。agent 编排、脚本执行与沙盒功能尚未实现。

插件开发可参考 [Echo Plugin 示例](examples/echo-plugin/README.md)，其中包含最小入口、
文本 Provider、插件清单、JVM JAR 加载测试及完整 `.eva` 发布任务。
在本目录执行 `./gradlew :examples:echo-plugin:assembleEvaPlugin`，即可生成包含独立源码工程、
API 源码快照和 JVM 产物的发布包；解压后可以独立构建和重新打包。

命令行输入输出由 [CMD Plugin](examples/cmd-plugin/README.md) 提供，根路由由必装的
[Root Plugin](examples/root-plugin/README.md) 提供。构建并安装 Root、Echo 和 CMD
三个插件后，在本目录执行 `./gradlew :cli:run --args="cmd"`，输入 `echo hello` 可得到
`Echo: hello`。

## 模块

| 模块 | 职责与依赖 |
| --- | --- |
| `core` | KMP 核心代码与数据模型，提供 JVM、Android、iOS targets，无 UI 依赖 |
| `cli` | JVM application 入口，依赖 `core`，处理命令行参数与输出 |
| `examples/root-plugin` | 系统根插件，提供唯一 `topic="/"` 的 Root Dispatcher |
| `examples/echo-plugin` | KMP 插件示例，当前提供 JVM target，依赖 `core` 的插件 API |
| `examples/cmd-plugin` | KMP 命令行适配插件，提供 `/CMD` Dispatcher、Provider 和 `.eva` 打包任务 |

核心入口面向 JVM、Android 和 iOS 共用；CLI 通过 JVM target 使用它。

本工程的版本统一在 `build.gradle.kts` 的 `allprojects.version` 中配置。

## CLI 打包

CLI 使用 Gradle `application` 插件，编译和运行需要 Java 17 或更高版本。在本目录执行：

```sh
./gradlew :cli:installDist
```

该任务会生成一个包含启动脚本和全部依赖的发行目录：

```text
cli/build/install/eva/
├── bin/eva       # macOS/Linux
├── bin/eva.bat   # Windows
└── lib/          # CLI、core 及第三方依赖
```

可以直接运行帮助命令检查构建结果：

```sh
./cli/build/install/eva/bin/eva --help
```

安装所需插件后，再运行 `./cli/build/install/eva/bin/eva cmd`。

发布时可以生成压缩包：

```sh
./gradlew :cli:distZip
./gradlew :cli:distTar
```

产物位于 `cli/build/distributions/`，文件名类似
`eva-0.1.0-alpha.zip` 和 `eva-0.1.0-alpha.tar`。解压后从发行目录的
`bin/eva`（macOS/Linux）或 `bin/eva.bat`（Windows）启动即可。这种发行包不是单个
native 二进制文件，运行时需要 Java 17 或更高版本。

如果需要把 Java 运行时一起打入平台应用，可以使用 JDK 17 自带的 `jpackage`。
先执行 `installDist`，再在 `eva-cli` 目录执行：

```sh
mkdir -p cli/build/jpackage
jpackage \
  --type app-image \
  --name eva \
  --app-version 1.0.0 \
  --input cli/build/install/eva/lib \
  --main-jar cli-0.1.0-alpha.jar \
  --main-class com.eva_cli.MainKt \
  --dest cli/build/jpackage
```

`--main-jar` 中的版本号需要与 `cli/build/install/eva/lib/` 下实际生成的 CLI JAR
一致；`--app-version` 使用一至三段数字。`app-image` 会根据当前系统生成应用目录，
也可以改用系统支持的 `dmg`、`pkg`、`deb`、`rpm`、`msi` 或 `exe` 安装包类型。
不同操作系统需要分别打包。

CLI 本身不包含插件。运行 `eva cmd` 前，需要先安装 Root、CMD 等所需插件；插件的
`.eva` 发布包构建方式见 [插件开发](examples/echo-plugin/README.md)。

## 许可证

本项目按 Apache License 2.0 发布，详见 [LICENSE](LICENSE)。第三方依赖及其许可
仍以各自的声明为准。
