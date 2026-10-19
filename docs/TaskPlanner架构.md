# TaskPlanner 内置与外置实现方案

本文定义任务编排能力在 Eva 现有 `Widget`、`Provider` 和 `Dispatcher` 模型中的落位方式。方案支持内置实现、插件实现、MCP、Skill 和 Tools，同时不增加 `TaskDispatcher`。

## 设计结论

`TaskPlannerProvider` 是任务流程的唯一入口和编排者。根 `Dispatcher` 将任务事件路由到它；`TaskPlannerProvider` 再通过能力解析器调用 Planner、Task 和 Checker 能力。

```text
RootDispatcher
  -> TaskPlannerProvider
       -> CapabilityResolver
            -> PlannerCapability
            -> TaskCapability
            -> CheckerCapability
```

`TaskPlannerProvider` 是 `Provider`，不是 `Dispatcher`。它内部可以调度任务步骤，但不负责维护 Dispatcher 树，也不把每一次能力调用重新包装成路由事件。

## 统一职责

`TaskPlannerProvider` 负责：

- 接收任务请求并创建任务实例；
- 生成或获取 `Plan`；
- 管理 `TodoItem` 的状态、依赖和执行顺序；
- 调用 Planner、Task 和 Checker 能力；
- 处理重试、暂停、恢复、取消和超时；
- 持久化任务状态并通过事件返回结果。

能力实现负责单一职责：

| 能力 | 责任 |
| --- | --- |
| `PlannerCapability` | 根据任务请求生成计划和步骤 |
| `TaskCapability` | 执行单个任务步骤，返回状态、进度和结果 |
| `CheckerCapability` | 执行参数检查、权限检查和结果验证 |

MCP、Skill 和 Tool 都作为这些能力的实现来源，不直接管理完整任务流程。

## 统一数据模型

内置和外置实现必须共享同一套领域模型：

- `TaskRequest`：任务目标、输入和调用上下文；
- `Plan`：计划标识、版本和步骤列表；
- `TodoItem`：步骤标识、依赖、状态和重试信息；
- `TaskState`：待执行、执行中、成功、失败、取消和暂停等状态；
- `CheckResult`：检查结果、原因和后续建议；
- `ExecutionContext`：权限、超时、取消信号和事件 ID；
- `CapabilityDescriptor`：能力名称、版本、输入输出 schema 和权限要求。

统一模型可以保证任务恢复、事件记录和实现替换的一致性。

## 内置实现

内置实现放在宿主或核心模块中，由 `TaskPlannerProvider` 直接组合：

```text
TaskPlannerProvider
  ├─ BuiltinPlanner
  ├─ BuiltinTaskExecutor
  └─ BuiltinChecker
```

典型职责如下：

- `BuiltinPlanner`：使用规则、固定流程或宿主默认模型生成计划；
- `BuiltinTaskExecutor`：执行本地任务、内置 Tool 和 Skill；
- `BuiltinChecker`：执行参数、权限和结果检查；
- `TaskPlannerProvider`：统一管理计划状态机、任务持久化和生命周期。

内置实现适合默认任务流程、本地执行和低延迟场景。即使没有加载任何外部插件，系统也可以通过内置能力完成基本任务。

## 外置实现

外置实现通过能力注册表提供，来源可以是插件、MCP 服务、Skill 执行器、Tool Provider 或独立远程服务：

```text
TaskPlannerProvider
  -> CapabilityResolver
       ├─ PlannerProvider
       ├─ TaskProvider
       ├─ CheckerProvider
       ├─ MCP Adapter
       ├─ Skill Adapter
       └─ Tool Provider
```

外置 Provider 只实现对应能力：

- `PlannerProvider` 只负责计划生成；
- `TaskProvider` 只负责步骤执行；
- `CheckerProvider` 只负责验证；
- MCP、Skill 和 Tool 适配器将外部协议转换为统一能力接口。

外置实现应声明：

- 稳定的能力 ID 和版本；
- 输入、输出和错误 schema；
- 所需权限；
- 超时、取消和重试语义；
- 是否支持幂等执行；
- 是否支持流式进度。

`TaskPlannerProvider` 不直接实例化插件对象或 MCP 客户端，而是通过 `CapabilityResolver` 获取已注册的能力实现。

## 在插件中实现 TaskPlannerProvider

插件中的 `TaskPlannerProvider` 应继承 `PluginProvider`，并通过 `PluginEntry.providers` 暴露给宿主。根 `Dispatcher` 将任务事件直接路由到这个 Provider；插件内部不需要增加 `TaskDispatcher`。

一个插件可以在入口对象中组合任务规划器及其能力实现：

```kotlin
class AgentTaskPlugin : PluginEntry {
    private val planner = ModelPlanner()
    private val executor = ToolTaskExecutor()
    private val checker = TaskChecker()

    private val taskPlanner = TaskPlannerProvider(
        planner = planner,
        executor = executor,
        checker = checker,
    )

    override val providers = listOf(taskPlanner)
}
```

上面的代码是架构示意，`ModelPlanner`、`ToolTaskExecutor`、`TaskChecker` 和 `TaskPlannerProvider` 需要由实际插件实现或由共享 API 提供。入口类仍然遵守现有插件约定：公开、无参构造器，并且在 `providers` 中复用固定的组件实例。

`TaskPlannerProvider` 的职责包括：

- 接收任务事件并创建任务实例；
- 调用 Planner 生成 `Plan` 和 `TodoItem`；
- 按步骤调用 Task 能力并更新 `TaskState`；
- 调用 Checker 验证参数和执行结果；
- 处理重试、暂停、恢复、取消和超时；
- 持久化任务状态，并通过 `emit` 返回结果事件。

如果 Planner、Task 和 Checker 只在当前插件内部使用，建议实现为普通能力对象。只有在某个能力需要独立生命周期、独立持久化、被多个组件复用或由宿主动态替换时，才将它们分别实现为 `PluginProvider`：

```text
TaskPlannerProvider
  -> CapabilityResolver
       -> PlannerProvider
       -> TaskProvider
       -> CheckerProvider
```

这些外置 Provider 由宿主按现有 Provider 生命周期绑定 `WidgetContext`、启动和停止；`TaskPlannerProvider` 通过能力接口直接调用它们，不把每次调用重新包装成 Dispatcher 事件。

当前核心 API 已支持插件通过 `PluginEntry.providers` 暴露 Provider，但 `CapabilityResolver` 尚未作为正式公共 API 提供。插件可以先采用插件内组合方式实现；宿主级能力注册表完成后，再将同一组接口接入外置 Provider、MCP、Skill 和 Tools，而不改变 `TaskPlannerProvider` 的任务状态机。

## 调用流程

```text
Event
  -> RootDispatcher
  -> TaskPlannerProvider
  -> PlannerCapability.plan()
  -> TaskCapability.execute()
  -> CheckerCapability.check()
  -> 更新 Plan / TodoItem / TaskState
  -> 输出结果 Event
```

根 `Dispatcher` 只处理入口路由。Planner、Task 和 Checker 的具体调用由 `TaskPlannerProvider` 按计划顺序直接完成。

能力调用不需要重新经过 Dispatcher。只有远程服务、异步队列或必须独立审计的调用，才由对应适配器在内部处理传输和路由。

## 运行模式

| 模式 | Planner | Task | Checker | 适用场景 |
| --- | --- | --- | --- | --- |
| Inline | 内置 | 内置 | 内置 | 默认运行和简单任务 |
| External | 外置 | 外置 | 外置 | 插件、MCP 和远程服务 |
| Hybrid | 可混合 | 可混合 | 可混合 | 推荐的生产模式 |

Hybrid 模式按能力粒度替换实现，例如：

```text
外置 PlannerProvider
内置 TaskExecutor
外置 CheckerProvider
```

建议优先使用配置指定的外置能力；没有可用实现时回退到内置能力。回退策略必须按能力单独定义：Planner 可以回退，Checker 失败通常应暂停任务，Task 失败则根据幂等性决定是否重试。

## 能力选择

`CapabilityResolver` 可以按以下顺序选择实现：

```text
任务请求指定
  -> 插件或 MCP 注册实现
  -> 工作区配置
  -> 宿主默认外置实现
  -> 内置实现
```

同一任务实例应固定其能力绑定，避免执行过程中更换 Planner 或 Checker 导致计划和校验规则不一致。能力升级应影响新任务；运行中的任务应继续使用已记录的能力版本。

## 生命周期与持久化

`TaskPlannerProvider` 负责任务级状态：

- plan、todo 和任务状态；
- 当前步骤和重试次数；
- 暂停、恢复和取消信息；
- 任务事件与执行结果。

外置 Provider 负责能力级资源：

- MCP 连接、远程客户端或本地进程；
- 自己的 `start()` 和 `stop()`；
- 自己的配置、缓存和会话状态；
- 能力级超时和取消。

宿主按照现有 Provider 生命周期绑定 `WidgetContext`、启动和停止外置 Provider。任务状态由 `TaskPlannerProvider` 统一保存，因此替换外置实现不会改变已有任务的 plan 和 todo。

## 错误处理

能力实现应将底层错误转换为统一错误类别，例如：

- `InvalidInput`：输入或参数不符合 schema；
- `PermissionDenied`：权限不足；
- `Unavailable`：外部服务不可用；
- `Timeout`：超过执行时限；
- `Cancelled`：任务被取消；
- `ExecutionFailed`：执行本身失败；
- `CheckFailed`：结果未通过检查。

`TaskPlannerProvider` 根据错误类别决定重试、暂停、回退或终止，并把决定写入任务事件记录。

## 与现有插件体系的关系

插件入口继续通过 `PluginEntry.providers` 暴露 Provider。`TaskPlannerProvider` 可以作为插件 Provider 或宿主内置 Provider；Planner、Task、Checker Provider 也可以由插件提供。

Dispatcher 仍只负责 topic 路由和组件生命周期。能力发现、版本选择和能力绑定属于 `CapabilityResolver`，不需要新增 `TaskDispatcher` 或修改 Dispatcher 树的职责。

## 推荐落地顺序

1. 先确定统一的 `Plan`、`TodoItem`、`TaskState` 和 `CheckResult` 模型；
2. 实现内置 Planner、Task Executor 和 Checker，验证完整任务生命周期；
3. 让 `TaskPlannerProvider` 通过能力接口调用内置实现；
4. 增加 `CapabilityDescriptor` 和 `CapabilityResolver`；
5. 接入外置 `PlannerProvider`、`TaskProvider` 和 `CheckerProvider`；
6. 通过适配器接入 MCP、Skill 和 Tools；
7. 最后补充版本固定、故障回退、权限和远程调用策略。

最终结构保持稳定：

```text
RootDispatcher
  -> TaskPlannerProvider
       -> CapabilityResolver
            ├─ 内置 Planner / Task / Checker
            ├─ 外置 PlannerProvider
            ├─ 外置 TaskProvider
            ├─ 外置 CheckerProvider
            ├─ MCP
            ├─ Skill
            └─ Tools
```
