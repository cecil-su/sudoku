package com.sudoku.game.ai

import com.sudoku.game.model.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal OpenAI-compatible chat client over [HttpURLConnection] (no extra deps,
 * matching the project's org.json approach). Non-streaming — responses come back
 * whole. All calls run on [Dispatchers.IO]; cancelling the calling coroutine drops
 * the result and the connection is closed in `finally` (best-effort cancellation).
 */
class AiClient {

    /** One chat turn. [tools] is a raw JSON array string of tool schemas (or null).
     *  Throws [AiException] on any non-2xx / transport error. */
    suspend fun chat(
        provider: AiProvider,
        messages: List<ChatMessage>,
        tools: String? = null
    ): ChatResult = withContext(Dispatchers.IO) {
        val model = provider.activeModel ?: provider.models.firstOrNull()
            ?: throw AiException("未选择模型")
        val body = JSONObject().apply {
            put("model", model)
            put("messages", encodeMessages(messages))
            put("stream", false)
            if (tools != null) {
                put("tools", JSONArray(tools))
                put("tool_choice", "auto")
            }
            // Reasoning models reject `temperature`; omit it for them.
            if (!isReasoning(model)) put("temperature", 0.3)
        }
        val text = post(endpoint(provider, "/chat/completions"), provider.apiKey, body.toString())
        parseChat(text)
    }

    /** Lists model ids via `GET /models`. Doubles as a connection test (success =
     *  reachable + key valid). Throws [AiException] on failure. */
    suspend fun listModels(provider: AiProvider): List<String> = withContext(Dispatchers.IO) {
        val text = get(endpoint(provider, "/models"), provider.apiKey)
        val data = JSONObject(text).optJSONArray("data") ?: JSONArray()
        (0 until data.length()).mapNotNull { data.getJSONObject(it).optString("id").ifEmpty { null } }
    }

    private fun endpoint(provider: AiProvider, path: String) =
        provider.baseUrl.trim().trimEnd('/') + path

    private fun post(url: String, apiKey: String, body: String): String =
        request(url, apiKey, "POST", body)

    private fun get(url: String, apiKey: String): String =
        request(url, apiKey, "GET", null)

    private fun request(url: String, apiKey: String, method: String, body: String?): String {
        // Never send the BYOK key over cleartext — https only (see [isSecureUrl]).
        if (!isSecureUrl(url)) throw AiException("仅支持 https 地址（避免 API Key 明文泄露）")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw AiException(friendlyError(code, text))
            return text
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException("网络错误：${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn.disconnect()
        }
    }

    private fun friendlyError(code: Int, body: String): String {
        val detail = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val hint = when (code) {
            401 -> "API Key 无效或未授权"
            402, 403 -> "账号无权限或余额不足"
            404 -> "地址或模型不存在（检查 Base URL / 模型 ID）"
            429 -> "请求过于频繁或额度用尽"
            in 500..599 -> "服务商暂时不可用"
            else -> null
        }
        val suffix = detail ?: hint
        return if (suffix != null) "HTTP $code：$suffix" else "HTTP $code"
    }

    private fun encodeMessages(messages: List<ChatMessage>): JSONArray = JSONArray().apply {
        for (m in messages) put(JSONObject().apply {
            put("role", m.role)
            if (m.content != null) put("content", m.content)
            if (m.toolCalls.isNotEmpty()) put("tool_calls", JSONArray().apply {
                for (t in m.toolCalls) put(JSONObject().apply {
                    put("id", t.id)
                    put("type", "function")
                    put("function", JSONObject().put("name", t.name).put("arguments", t.arguments))
                })
            })
            if (m.toolCallId != null) put("tool_call_id", m.toolCallId)
        })
    }

    private fun parseChat(text: String): ChatResult {
        val message = JSONObject(text)
            .getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val content = if (message.isNull("content")) null
        else message.optString("content").ifEmpty { null }
        val toolCalls = message.optJSONArray("tool_calls")?.let { arr ->
            (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                val fn = c.getJSONObject("function")
                ToolCall(
                    id = c.optString("id"),
                    name = fn.getString("name"),
                    arguments = fn.optString("arguments", "{}")
                )
            }
        } ?: emptyList()
        return ChatResult(content, toolCalls)
    }

    private fun isReasoning(model: String): Boolean =
        model.contains("reasoner", ignoreCase = true) ||
            model.startsWith("o1") || model.startsWith("o3") || model.startsWith("o4")
}

/** True iff [url] is an https endpoint — the only scheme we send the API key over,
 *  so a misconfigured `http://` base can't leak the BYOK key in cleartext. Pure
 *  (no IO / org.json) so it's unit-testable and shared with the provider editor. */
internal fun isSecureUrl(url: String): Boolean =
    url.trim().startsWith("https://", ignoreCase = true)
