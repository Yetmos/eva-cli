# Provider 扩展

`Provider` 保留组件生命周期和事件协议。插件中的专职 Provider 都继承
`PluginProvider`，因此可以直接放入 `PluginEntry.providers`，继续使用现有的
Dispatcher 路由、`WidgetContext` 绑定和记录持久化。

| 类型 | 能力接口 | 用途 |
| --- | --- | --- |
| `IOProvider` | `InputCapability`、`OutputCapability` | 连接外部输入/输出；也可使用 `InputProvider` 或 `OutputProvider` |
| `TimerProvider` | `TimerCapability` | 启动后调度一次性或周期性协程任务 |

`InputCapability` 和 `OutputCapability` 提供安全的默认结果，适合只参与通用生命周期的 Provider。
`TimerProvider` 的调度器在 `start()` 时创建，并在 `stop()` 时取消；调用 `schedule` 前必须先启动 Provider。

