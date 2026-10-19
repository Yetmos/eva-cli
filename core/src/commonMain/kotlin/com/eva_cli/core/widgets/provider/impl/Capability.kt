package com.eva_cli.core.widgets.provider.impl

import kotlinx.serialization.json.JsonObject

/** 已注册能力的、与来源无关的类型。 */
enum class CapabilityKind { PLANNER, TASK, CHECKER, TOOL, SKILL, MCP }

/** 供宿主选择和审计实现的元数据。 */
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

/** 适配器暴露的稳定错误分类；底层传输错误可以包装在 [cause] 中。 */
class CapabilityException(
    val code: CapabilityErrorCode,
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)

/** 轻量宿主级注册表；任务应在开始时解析并固定一个实现。 */
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
