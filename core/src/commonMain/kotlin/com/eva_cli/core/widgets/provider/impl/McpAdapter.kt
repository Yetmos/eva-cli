package com.eva_cli.core.widgets.provider.impl

import com.eva_cli.core.widgets.context.NetworkRequest
import com.eva_cli.core.widgets.context.PluginNetwork
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Minimal JSON-RPC transport. HTTP, SSE and stdio implementations belong to a plugin/host. */
interface McpTransport {
    suspend fun request(method: String, params: JsonObject = JsonObject(emptyMap())): JsonObject
    suspend fun notify(method: String, params: JsonObject = JsonObject(emptyMap())) {
        request(method, params)
    }
    suspend fun close() = Unit
}

/** JSON-RPC over the host network boundary. It deliberately does not bypass PluginNetwork. */
class HttpMcpTransport(
    private val network: PluginNetwork,
    private val endpoint: String,
    private val headers: Map<String, String> = emptyMap(),
    private val timeoutMillis: Long? = null,
) : McpTransport {
    private var nextId = 0L

    override suspend fun request(method: String, params: JsonObject): JsonObject {
        val id = ++nextId
        val request = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("id", JsonPrimitive(id))
            put("method", JsonPrimitive(method))
            put("params", params)
        }
        val response = network.execute(
            NetworkRequest(
                url = endpoint,
                method = "POST",
                headers = headers + mapOf(
                    "Content-Type" to "application/json",
                    "Accept" to "application/json, text/event-stream",
                ),
                body = Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), request).encodeToByteArray(),
                failOnHttpError = true,
                requestTimeoutMillis = timeoutMillis,
            ),
        )
        val envelope = Json.parseToJsonElement(response.body.decodeToString()).jsonObject
        val error = envelope["error"]?.jsonObject
        if (error != null) {
            throw CapabilityException(
                CapabilityErrorCode.EXECUTION_FAILED,
                error["message"]?.jsonPrimitive?.content ?: "MCP JSON-RPC error",
            )
        }
        return envelope["result"]?.jsonObject
            ?: throw CapabilityException(CapabilityErrorCode.UNAVAILABLE, "MCP response has no result")
    }

    override suspend fun notify(method: String, params: JsonObject) {
        val request = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("method", JsonPrimitive(method))
            put("params", params)
        }
        network.execute(
            NetworkRequest(
                url = endpoint,
                method = "POST",
                headers = headers + ("Content-Type" to "application/json"),
                body = Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), request).encodeToByteArray(),
                failOnHttpError = true,
                requestTimeoutMillis = timeoutMillis,
            ),
        )
    }
}

/** MCP client adapter that exposes server tools through [ToolCapability]. */
abstract class McpAdapter : ToolProvider() {
    protected abstract suspend fun createTransport(): McpTransport

    private var transport: McpTransport? = null

    override suspend fun onStart() {
        val connection = createTransport()
        try {
            connection.request("initialize", buildJsonObject {
                put("protocolVersion", JsonPrimitive("2025-06-18"))
                put("capabilities", buildJsonObject { })
                put("clientInfo", buildJsonObject {
                    put("name", JsonPrimitive("eva-cli"))
                    put("version", JsonPrimitive("1"))
                })
            })
            connection.notify("notifications/initialized")
            val result = connection.request("tools/list")
            val remoteTools = result["tools"] as? JsonArray ?: JsonArray(emptyList())
            clearTools()
            for (element in remoteTools) {
                val tool = element.jsonObject
                val name = tool["name"]?.jsonPrimitive?.content ?: continue
                val description = tool["description"]?.jsonPrimitive?.content.orEmpty()
                val schema = tool["inputSchema"]?.jsonObject ?: JsonObject(emptyMap())
                registerTool(
                    ToolDefinition(
                        descriptor = CapabilityDescriptor(
                            id = name,
                            kind = CapabilityKind.MCP,
                            inputSchema = schema,
                        ),
                        name = name,
                        description = description,
                    ),
                ) { arguments ->
                    val response = checkNotNull(transport).request(
                        "tools/call",
                        buildJsonObject {
                            put("name", JsonPrimitive(name))
                            put("arguments", arguments)
                        },
                    )
                    ToolResult.success(response["content"] ?: response["structuredContent"], response["isError"]?.jsonPrimitive?.content)
                }
            }
            transport = connection
        } catch (failure: Throwable) {
            connection.close()
            throw CapabilityException(CapabilityErrorCode.UNAVAILABLE, "MCP initialization failed", failure)
        }
    }

    override suspend fun onStop() {
        transport?.close()
        transport = null
        clearTools()
    }
}
