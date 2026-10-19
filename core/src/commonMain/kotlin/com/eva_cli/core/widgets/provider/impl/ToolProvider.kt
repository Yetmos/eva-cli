package com.eva_cli.core.widgets.provider.impl

import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.provider.PluginProvider
import com.eva_cli.core.widgets.provider.Provider
import com.eva_cli.core.widgets.context.PermissionResult
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration.Companion.milliseconds

data class ToolRequest(
    val toolId: String,
    val arguments: JsonObject = JsonObject(emptyMap()),
    val context: Map<String, String> = emptyMap(),
)

data class ToolResult(
    val value: JsonElement? = null,
    val detail: String? = null,
    val error: CapabilityErrorCode? = null,
) {
    val succeeded: Boolean get() = error == null

    companion object {
        fun success(value: JsonElement? = null, detail: String? = null) = ToolResult(value, detail)
        fun failure(code: CapabilityErrorCode, detail: String? = null) = ToolResult(null, detail, code)
    }
}

data class ToolDefinition(
    val descriptor: CapabilityDescriptor,
    val name: String = descriptor.id,
    val description: String = "",
    val timeoutMillis: Long? = null,
    /** Optional lightweight validator. Full JSON Schema validation belongs to the host. */
    val validate: (JsonObject) -> Boolean = { true },
)

/** Common execution surface for local tools and remote adapters. */
interface ToolCapability {
    val tools: List<ToolDefinition>
    suspend fun invoke(request: ToolRequest): ToolResult
}

/**
 * Provider base for tools. It owns validation, permission checks and timeout mapping;
 * subclasses only register definitions and handlers.
 */
abstract class ToolProvider : PluginProvider(), ToolCapability {
    override var status: Provider.Status = Provider.Status.STOP

    override suspend fun receive(event: Event) = Unit

    private val definitions = linkedMapOf<String, ToolDefinition>()
    private val handlers = linkedMapOf<String, suspend (JsonObject) -> ToolResult>()

    override val tools: List<ToolDefinition>
        get() = definitions.values.toList()

    protected fun registerTool(
        definition: ToolDefinition,
        handler: suspend (JsonObject) -> ToolResult,
    ) {
        require(definition.descriptor.kind == CapabilityKind.TOOL ||
            definition.descriptor.kind == CapabilityKind.SKILL ||
            definition.descriptor.kind == CapabilityKind.MCP) {
            "Tool definition must have TOOL, SKILL or MCP kind"
        }
        check(definitions.put(definition.descriptor.id, definition) == null) {
            "Tool already registered: ${definition.descriptor.id}"
        }
        handlers[definition.descriptor.id] = handler
    }

    protected fun clearTools() {
        definitions.clear()
        handlers.clear()
    }

    override suspend fun start() {
        if (status != Provider.Status.STOP) return
        status = Provider.Status.STARTING
        try {
            onStart()
            status = Provider.Status.IDLE
        } catch (failure: Throwable) {
            status = Provider.Status.STOP
            throw failure
        }
    }

    override suspend fun stop() {
        if (status == Provider.Status.STOP) return
        onStop()
        status = Provider.Status.STOP
    }

    protected open suspend fun onStart() = Unit
    protected open suspend fun onStop() = Unit

    override suspend fun invoke(request: ToolRequest): ToolResult {
        val definition = definitions[request.toolId]
            ?: return ToolResult.failure(CapabilityErrorCode.INVALID_INPUT, "Unknown tool: ${request.toolId}")
        val handler = handlers[request.toolId]
            ?: return ToolResult.failure(CapabilityErrorCode.UNAVAILABLE, "Tool has no handler: ${request.toolId}")
        if (!enable || status == Provider.Status.STOP) {
            return ToolResult.failure(CapabilityErrorCode.UNAVAILABLE, "Tool provider is stopped")
        }
        if (!definition.validate(request.arguments)) {
            return ToolResult.failure(CapabilityErrorCode.INVALID_INPUT, "Arguments do not match ${request.toolId}")
        }
        for (permission in definition.descriptor.permissions) {
            val result = context.permissions.check(permission)
            if (result != PermissionResult.GRANTED) {
                return ToolResult.failure(CapabilityErrorCode.PERMISSION_DENIED, "Permission denied: $permission")
            }
        }
        status = Provider.Status.WORKING
        return try {
            if (definition.timeoutMillis == null) {
                handler(request.arguments)
            } else {
                withTimeout(definition.timeoutMillis.milliseconds) { handler(request.arguments) }
            }
        } catch (_: TimeoutCancellationException) {
            ToolResult.failure(CapabilityErrorCode.TIMEOUT, "Tool timed out: ${request.toolId}")
        } catch (_: kotlinx.coroutines.CancellationException) {
            ToolResult.failure(CapabilityErrorCode.CANCELLED, "Tool cancelled: ${request.toolId}")
        } catch (failure: CapabilityException) {
            ToolResult.failure(failure.code, failure.message)
        } catch (failure: Throwable) {
            ToolResult.failure(CapabilityErrorCode.EXECUTION_FAILED, failure.message)
        } finally {
            if (status != Provider.Status.STOP) status = Provider.Status.IDLE
        }
    }
}
