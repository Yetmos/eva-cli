package com.eva_cli.core.widgets.provider.impl

import kotlinx.serialization.json.JsonObject

/** Host supplied execution boundary for a Skill. */
fun interface SkillExecutor {
    suspend fun execute(skillId: String, arguments: JsonObject): ToolResult
}

data class SkillDefinition(
    val id: String,
    val description: String = "",
    val inputSchema: JsonObject = JsonObject(emptyMap()),
    val permissions: Set<String> = emptySet(),
    val timeoutMillis: Long? = null,
    val idempotent: Boolean = false,
)

/**
 * Adapter for declarative/local skills. The actual script, process or model call is
 * supplied by [SkillExecutor], so common code never shells out or bypasses host policy.
 */
abstract class SkillAdapter : ToolProvider() {
    protected abstract val skills: List<SkillDefinition>
    protected abstract val executor: SkillExecutor

    override suspend fun onStart() {
        clearTools()
        skills.forEach { skill ->
            registerTool(
                ToolDefinition(
                    descriptor = CapabilityDescriptor(
                        id = skill.id,
                        kind = CapabilityKind.SKILL,
                        inputSchema = skill.inputSchema,
                        permissions = skill.permissions,
                        idempotent = skill.idempotent,
                    ),
                    name = skill.id,
                    description = skill.description,
                    timeoutMillis = skill.timeoutMillis,
                ),
            ) { arguments -> executor.execute(skill.id, arguments) }
        }
    }
}
