# Provider 扩展

`Provider` 保留组件生命周期和事件协议。插件中的专职 Provider 都继承
`PluginProvider`，因此可以直接放入 `PluginEntry.providers`，继续使用现有的
Dispatcher 路由、`WidgetContext` 绑定和记录持久化。

| 类型 | 能力接口 | 用途 |
| --- | --- | --- |
| `PlannerProvider` | `PlannerCapability` | 将输入转换为 `PlanResult` 和步骤列表 |
| `IOProvider` | `InputCapability`、`OutputCapability` | 连接外部输入/输出；也可使用 `InputProvider` 或 `OutputProvider` |
| `TimerProvider` | `TimerCapability` | 启动后调度一次性或周期性协程任务 |
| `TaskProvider` | `TaskCapability` | 启动后提交可取消任务并查询 `TaskState` |
| `CheckerProvider` | `CheckerCapability` | 对输入或事件执行检查并返回 `CheckResult` |

插件只需继承对应抽象类并实现 `Widget` 元数据。例如：

```kotlin
class PolicyChecker : CheckerProvider() {
    override val id = Uuid.random()
    override val name = "Policy checker"
    override val desc = "Checks policy rules"
    override val topic = "/Checker/Policy"
    override var enable = true
    override var records = emptyList<EventRecord>()
    override var emit: (suspend (Event) -> Unit)? = null

    override suspend fun receive(event: Event) = Unit

    override suspend fun check(request: CheckRequest): CheckResult =
        CheckResult.passed("accepted")
}
```

`PlannerCapability`、`InputCapability`、`OutputCapability` 和
`CheckerCapability` 提供安全的默认结果，适合只参与通用生命周期的 Provider。
`TimerProvider` 和 `TaskProvider` 的调度器在 `start()` 时创建，并在 `stop()` 时取消；
调用 `schedule` 或 `submit` 前必须先启动 Provider。任务业务状态使用各自的
`TaskState`，不要把它映射成 `Provider.Status`。

