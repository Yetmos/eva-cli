package com.eva_cli.testplugins

import com.eva_cli.core.widgets.dispatcher.PluginDispatcher
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.plug.LoadingFixtureHooks
import com.eva_cli.core.widgets.plug.PluginEntry
import com.eva_cli.core.widgets.provider.Provider
import com.eva_cli.core.widgets.provider.PluginProvider
import com.eva_cli.testplugins.dependencies.PrivateDependency
import kotlin.uuid.Uuid

/** 这些类会被打包到真实的测试 JAR 中，与宿主 API 分开。 */
class LoadingEntry : PluginEntry {
    override val providers = mutableListOf<PluginProvider>(LoadingProvider())
    override val dispatchers = mutableListOf<PluginDispatcher>(LoadingDispatcher())
}

class LoadingProvider(
    override val topic: String = "/Loading",
    override val id: Uuid = Uuid.random(),
) : PluginProvider() {
    override val name = PrivateDependency().description
    override val desc = "Loading fixture"
    override var enable = true
    override var status = Provider.Status.STOP
    override var records: List<EventRecord> = emptyList()
    override var emit: (suspend (Event) -> Unit)? = null

    override suspend fun receive(event: Event) {
        emit?.invoke(event)
    }

    override suspend fun start(): Unit = error("Loading must not start providers")
    override suspend fun stop(): Unit = error("Unloading must not stop providers")
}

class LoadingDispatcher(
    override val topic: String = "/Loading",
    override val id: Uuid = Uuid.random(),
) : PluginDispatcher() {
    override val name = "Loading dispatcher"
    override val desc = "Loading fixture"

    override suspend fun receive(event: Event) {
        emit?.invoke(event)
    }

    override suspend fun dispatch(event: Event) {
        receive(event)
    }

    override suspend fun start(): Unit = error("Loading must not start dispatchers")
    override suspend fun stop(): Unit = error("Unloading must not stop dispatchers")
}

class QueryEntry : PluginEntry {
    override val providers = listOf(
        "/Room", "/Room/Child", "/Roommate", "/room", "/Room", "/", "/Room with space",
    ).mapTo(mutableListOf<PluginProvider>()) { LoadingProvider(it) }

    override val dispatchers = listOf(
        "/Zoo", "/", "/Room", "/Room/Second", "/Room/First", "/Room/First/Grandchild",
        "/Roommate/Other", "/room/Child", "/Room/Second",
        // 格式错误的候选项绝不能成为根节点或嵌套子节点。
        "", "relative", "//Room", "/Room/", "/Room//Child", "/Room/.", "/Room/..",
        "/Room/ padded", "/Room/trailing ", "/Room/\tcontrol", "/Room/back\\slash",
        "/.", "/..", "/ leading", "/trailing ", "/back\\slash",
    ).mapTo(mutableListOf<PluginDispatcher>()) { LoadingDispatcher(it) }
}

class ScanEntry : PluginEntry {
    override val providers = listOf("/Room", "/Room/Child", "/Roommate", "/room", "/Room")
        .map { LoadingProvider(it) }
    override val dispatchers = listOf(
        "/Room", "/Room/Zeta", "/Room/Alpha", "/Room/Zeta/Grandchild", "/Roommate/Child", "/room/Child",
    ).map { LoadingDispatcher(it) }
}

class OtherScanEntry : PluginEntry {
    override val providers = listOf(LoadingProvider("/Room"))
    override val dispatchers = listOf(LoadingDispatcher("/Room/Beta"))
}

class BootEntry : PluginEntry {
    override val dispatchers = listOf(BootDispatcher("/Child"), BootDispatcher("/"))
}

/** 非根 Dispatcher，可在生命周期测试中加入宿主树。 */
class QuietEntry : PluginEntry {
    override val dispatchers = listOf(QuietDispatcher("/Quiet"))
}

class QuietWorkerEntry : PluginEntry {
    override val dispatchers = listOf(QuietDispatcher("/QuietWorker"))
}

class QuietDispatcher(override val topic: String) : PluginDispatcher() {
    override val name = "Quiet dispatcher"
    override val desc = "Lifecycle test fixture"
    override suspend fun dispatch(event: Event) = Unit
    override suspend fun receive(event: Event) = Unit
}

class DuplicateRootsEntry : PluginEntry {
    override val dispatchers = listOf(BootDispatcher("/"), BootDispatcher("/"))
}

class BootDispatcher(override val topic: String) : PluginDispatcher() {
    override val name = "Plugin dispatcher"
    override val desc = "Boot lifecycle fixture"
    override suspend fun dispatch(event: Event) = Unit
    override suspend fun receive(event: Event) = Unit

    override suspend fun start() {
        super.start()
        LoadingFixtureHooks.onLifecycle?.invoke(topic, "start")
    }

    override suspend fun stop() {
        super.stop()
        LoadingFixtureHooks.onLifecycle?.invoke(topic, "stop")
    }

    override suspend fun backup() {
        LoadingFixtureHooks.onLifecycle?.invoke(topic, "backup")
    }

    override suspend fun restore() {
        LoadingFixtureHooks.onLifecycle?.invoke(topic, "restore")
    }
}

class DuplicateIdsEntry : PluginEntry {
    private val duplicateId = Uuid.random()
    override val providers = listOf(LoadingProvider("/Room", duplicateId), LoadingProvider("/Room", duplicateId))
    override val dispatchers = listOf(
        LoadingDispatcher("/Room/First", duplicateId), LoadingDispatcher("/Room/Second", duplicateId),
    )
}

class RepeatedComponentsEntry : PluginEntry {
    private val provider = LoadingProvider("/Room")
    private val dispatcher = LoadingDispatcher("/Room/Child")
    override val providers = listOf(provider, provider)
    override val dispatchers = listOf(dispatcher, dispatcher)
}

class ConstructorFailureEntry : PluginEntry {
    init {
        error("Constructor failed")
    }
}

class ProvidersFailureEntry : PluginEntry {
    override val providers: List<PluginProvider>
        get() = error("Providers getter failed")
}

class DispatchersFailureEntry : PluginEntry {
    override val dispatchers: List<PluginDispatcher>
        get() = error("Dispatchers getter failed")
}

class PrivateConstructorEntry private constructor() : PluginEntry
class RequiredArgumentEntry(val argument: String) : PluginEntry
class NotAnEntry

class HookEntry : PluginEntry {
    init {
        LoadingFixtureHooks.onConstruct?.invoke()
    }
}
