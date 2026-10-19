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

/** 最小 JSON-RPC 传输接口；HTTP、SSE 和 stdio 实现由插件或宿主提供。 */
interface McpTransport {
    suspend fun request(method: String, params: JsonObject = JsonObject(emptyMap())): JsonObject
    suspend fun notify(method: String, params: JsonObject = JsonObject(emptyMap())) {
        request(method, params)
    }
    suspend fun close() = Unit
}

/** 通过宿主网络边界传输 JSON-RPC；不会绕过 PluginNetwork。 */
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

/** MCP 客户端适配器，将服务端工具暴露为 [ToolCapability]。 */
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
                    val detail = response["isError"]?.jsonPrimitive?.content
                    if (detail == "true") {
                        ToolResult.failure(
                            CapabilityErrorCode.EXECUTION_FAILED,
                            response["content"]?.toString() ?: "MCP tool execution failed",
                        )
                    } else {
                        ToolResult.success(
                            response["content"] ?: response["structuredContent"],
                            detail,
                        )
                    }
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
