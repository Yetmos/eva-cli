package com.eva_cli.core.widgets.provider.impl.tool

import com.eva_cli.core.widgets.provider.impl.CapabilityDescriptor
import com.eva_cli.core.widgets.provider.impl.CapabilityKind
import kotlinx.serialization.json.JsonObject

/** 由宿主提供的 Skill 执行边界。 */
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
 * 声明式或本地 Skill 的适配器。实际脚本、进程或模型调用由 [SkillExecutor] 提供，
 * 因此公共代码不会直接启动外部进程或绕过宿主策略。
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
