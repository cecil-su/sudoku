package com.sudoku.game.ai

/**
 * A single message in an OpenAI-compatible chat conversation.
 *
 * [role] is "system" | "user" | "assistant" | "tool". An assistant message may
 * carry [toolCalls] (function calls it wants executed); a "tool" message answers
 * one call via [toolCallId].
 */
data class ChatMessage(
    val role: String,
    val content: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null
)

/** A function/tool call requested by the model. [arguments] is the raw JSON object string. */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

/** The assistant's reply: free-text [content] and/or [toolCalls] to execute. */
data class ChatResult(
    val content: String?,
    val toolCalls: List<ToolCall>
)

/** Networking / provider error surfaced to the UI with a human-readable message. */
class AiException(message: String) : Exception(message)
