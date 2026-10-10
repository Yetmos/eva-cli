# CMD Plugin

CMD 插件提供 `/CMD` Dispatcher 和 Provider，将命令行输入转换为 Eva 文本事件，
并把运行时回复交给宿主输出。

```text
stdin -> CMDProvider -> CMDDispatcher -> Eva -> /Echo -> EchoProvider
stdout <- CMDProvider <- CMDDispatcher <- RootDispatcher
```

宿主通过 `CommandLinePort` 绑定实际的 stdin/stdout。插件不直接持有全局输入输出流，
因此可以被 CLI、测试或其他命令行宿主复用。

当前支持：

```text
echo hello
```

它会提交 `hello` 文本事件，并输出 `Echo: hello`。

运行时还必须安装提供唯一 `/` 根节点的 [Root Plugin](../root-plugin/README.md)。

构建发布包：

```sh
./gradlew :plugins:cmd-plugin:assembleEvaPlugin
```

安装 `.eva` 后，宿主通过现有 `PlugManager.installPlugin` / `loadPlugin` 加载它，再运行
`eva cmd`；CLI 会从已加载的 `CommandLinePort` 读取和输出文本。
