package com.eva_cli.core.widgets.provider.impl

import kotlinx.serialization.json.JsonObject

/** The source-independent kind of a registered capability. */
enum class CapabilityKind { PLANNER, TASK, CHECKER, TOOL, SKILL, MCP }

/** Metadata used by a host when selecting and auditing an implementation. */
data class CapabilityDescriptor(
    val id: String,
    val version: String = "1",
    val kind: CapabilityKind = CapabilityKind.TOOL,
    val inputSchema: JsonObject = JsonObject(emptyMap()),
    val outputSchema: JsonObject = JsonObject(emptyMap()),
    val permissions: Set<String> = emptySet(),
    val supportsStreaming: Boolean = false,
    val idempotent: Boolean = false,
)

enum class CapabilityErrorCode {
    INVALID_INPUT,
    PERMISSION_DENIED,
    UNAVAILABLE,
    TIMEOUT,
    CANCELLED,
    EXECUTION_FAILED,
    CHECK_FAILED,
}

/** Stable error categories exposed by adapters; transports may be wrapped as [cause]. */
class CapabilityException(
    val code: CapabilityErrorCode,
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)

/** A small host-level registry. A task should resolve and pin an implementation once. */
class CapabilityResolver {
    private val entries = linkedMapOf<String, Entry<*>>()

    data class Entry<T : Any>(
        val descriptor: CapabilityDescriptor,
        val capability: T,
    )

    fun <T : Any> register(descriptor: CapabilityDescriptor, capability: T) {
        require(descriptor.id.isNotBlank()) { "Capability id must not be blank" }
        check(entries[descriptor.id] == null) { "Capability already registered: ${descriptor.id}" }
        entries[descriptor.id] = Entry(descriptor, capability)
    }

    fun unregister(id: String): Boolean = entries.remove(id) != null

    fun descriptor(id: String): CapabilityDescriptor? = entries[id]?.descriptor

    fun resolve(id: String): Any? = entries[id]?.capability

    inline fun <reified T : Any> resolveAs(id: String): T? = resolve(id) as? T

    fun all(): List<CapabilityDescriptor> = entries.values.map { it.descriptor }
}
