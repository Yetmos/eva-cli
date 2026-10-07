package com.eva_cli.testplugins

import com.eva_cli.core.widgets.dispatcher.WidgetDispatcher
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.plug.LoadingFixtureHooks
import com.eva_cli.core.widgets.plug.PluginEntry
import com.eva_cli.core.widgets.provider.Provider
import com.eva_cli.core.widgets.provider.WidgetProvider
import com.eva_cli.testplugins.dependencies.PrivateDependency
import kotlin.uuid.Uuid

/** These classes are packaged into real test JARs, separate from the host API. */
class LoadingEntry : PluginEntry {
    override val providers = mutableListOf<WidgetProvider>(LoadingProvider())
    override val dispatchers = mutableListOf<WidgetDispatcher>(LoadingDispatcher())
}

class LoadingProvider(
    override val topic: String = "/Loading",
    override val id: Uuid = Uuid.random(),
) : WidgetProvider() {
    override val name = PrivateDependency().description
    override val desc = "Loading fixture"
    override var enable = true
    override var status = Provider.Status.STOP
    override var records: List<EventRecord> = emptyList()
    override var emit: (suspend (Event) -> Unit)? = null

    override suspend fun receive(event: Event) {
        emit?.invoke(event)
    }

    override fun start(): Unit = error("Loading must not start providers")
    override fun stop(): Unit = error("Unloading must not stop providers")
}

class LoadingDispatcher(
    override val topic: String = "/Loading",
    override val id: Uuid = Uuid.random(),
) : WidgetDispatcher() {
    override val name = "Loading dispatcher"
    override val desc = "Loading fixture"

    override suspend fun receive(event: Event) {
        feedUp?.invoke(event)
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
    ).mapTo(mutableListOf<WidgetProvider>()) { LoadingProvider(it) }

    override val dispatchers = listOf(
        "/Zoo", "/", "/Room", "/Room/Second", "/Room/First", "/Room/First/Grandchild",
        "/Roommate/Other", "/room/Child", "/Room/Second",
        // Malformed candidates must never become root or nested children.
        "", "relative", "//Room", "/Room/", "/Room//Child", "/Room/.", "/Room/..",
        "/Room/ padded", "/Room/trailing ", "/Room/\tcontrol", "/Room/back\\slash",
        "/.", "/..", "/ leading", "/trailing ", "/back\\slash",
    ).mapTo(mutableListOf<WidgetDispatcher>()) { LoadingDispatcher(it) }
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
    override val providers: List<WidgetProvider>
        get() = error("Providers getter failed")
}

class DispatchersFailureEntry : PluginEntry {
    override val dispatchers: List<WidgetDispatcher>
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
