package com.eva_cli.core.provider

// 接口类型：Mod(大模型)，AGENT，SKILL
enum class ProviderType {
	Mod,
	AGENT,
	SKILL
}

// Agent连接方式：CLI(命令行)，MCP
enum class AgentType {
	CLI,
	MCP
}

// 大模型类型：MUL(多模态)，TEXT(文本)，TTS(文字转语音)，STT(语音转文本)，IMG(图片)，VIDEO(视频)
enum class ModType {
	MUL,
	TEXT,
	TTS,
	STT,
	IMG,
	VIDEO
}

// TODO: 接口管理类
object ProviderManager {

}