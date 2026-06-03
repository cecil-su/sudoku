package com.sudoku.game.ai

import com.sudoku.game.model.AiProvider
import com.sudoku.game.model.DemoController
import com.sudoku.game.model.DemoStep
import org.json.JSONObject

/** One coach turn: [narration] to show/speak, the (possibly moved) [controller],
 *  and the updated [history] (user/assistant/tool messages, no system prompt). */
data class CoachTurn(
    val narration: String,
    val controller: DemoController,
    val history: List<ChatMessage>
)

/**
 * The AI "second controller" (10.4). A spoken/typed intent is sent to the
 * configured provider with the engine-verified trajectory as grounding and a set
 * of navigation tools (next/prev/replay/jumpTo/gotoCell) that map onto the SAME
 * [DemoController] the offline buttons drive. The model may only navigate and
 * explain — it never solves. Function calls are executed locally and fed back so
 * the model can narrate where it landed.
 */
class AiCoach(private val client: AiClient = AiClient()) {

    suspend fun respond(
        provider: AiProvider,
        controller: DemoController,
        givens: Array<IntArray>,
        history: List<ChatMessage>,
        userText: String
    ): CoachTurn {
        var ctrl = controller
        val convo = mutableListOf<ChatMessage>()
        convo += ChatMessage("system", systemPrompt(ctrl, givens))
        convo += history
        convo += ChatMessage("user", userText)

        for (round in 0 until MAX_ROUNDS) {
            val result = client.chat(provider, convo, TOOLS)
            convo += ChatMessage("assistant", content = result.content, toolCalls = result.toolCalls)
            if (result.toolCalls.isEmpty()) {
                return CoachTurn(result.content?.trim().orEmpty(), ctrl, convo.drop(1))
            }
            for (call in result.toolCalls) {
                ctrl = execute(ctrl, call)
                convo += ChatMessage("tool", content = describeState(ctrl), toolCallId = call.id)
            }
        }
        // Did not converge to a text answer within MAX_ROUNDS; fall back to the
        // factual current step and keep history valid (it must not end on a tool msg).
        val fallback = describeState(ctrl)
        convo += ChatMessage("assistant", content = fallback)
        return CoachTurn(fallback, ctrl, convo.drop(1))
    }

    private fun execute(ctrl: DemoController, call: ToolCall): DemoController {
        val args = runCatching { JSONObject(call.arguments) }.getOrNull() ?: JSONObject()
        return applyCoachTool(
            ctrl = ctrl,
            name = call.name,
            index = if (args.has("index")) args.optInt("index") else null,
            row = if (args.has("row")) args.optInt("row") else null,
            col = if (args.has("col")) args.optInt("col") else null
        )
    }

    private fun systemPrompt(ctrl: DemoController, givens: Array<IntArray>): String {
        val board = givens.joinToString("\n") { row -> row.joinToString("") { it.toString() } }
        val trajectory = ctrl.steps.mapIndexed { i, s -> "${i + 1}. [${s.techniqueName}] ${conclusion(s)}" }
            .joinToString("\n")
        return """
            你是一个耐心、亲切的数独教练，用通俗口语化中文讲解。
            下面是一道数独题，以及引擎已经【验证好】的逐步解法轨迹。你的职责是在这条轨迹上为学习者【导航】和【讲解】——解释"为什么这一步能这样填或排除"。
            铁律：绝不自己解题、绝不臆造步骤、绝不超出下面给定的轨迹；需要移动时【调用工具】，不要只在嘴上说要跳转。

            棋盘（9 行，每行 9 个数字，0 表示空格）：
            $board

            解法轨迹（共 ${ctrl.totalSteps} 步，序号从 1 开始）：
            $trajectory

            学习者当前看到的是第 ${ctrl.stepNumber} 步。

            工具用法（序号、行、列都从 1 开始）：
            - "下一步/继续" → next；"上一步/退回" → prev；"重来/从头看" → replay
            - "跳到第 X 步" → jumpTo(index=X)
            - "为什么这一格 / R几C几" → 先 gotoCell(row, col) 定位再讲
            - 导航后用一两句话讲解落点那一步；不需要导航的提问就直接讲当前步。
            回答简短，像在旁边带着一起做。
        """.trimIndent()
    }

    private fun describeState(ctrl: DemoController): String {
        val step = ctrl.current ?: return "已经没有更多步骤了"
        return "现在到第 ${ctrl.stepNumber}/${ctrl.totalSteps} 步：${step.techniqueName}。${conclusion(step)}"
    }

    private companion object {
        const val MAX_ROUNDS = 4

        val TOOLS = """
            [
              {"type":"function","function":{"name":"next","description":"前进到下一步","parameters":{"type":"object","properties":{}}}},
              {"type":"function","function":{"name":"prev","description":"回退到上一步","parameters":{"type":"object","properties":{}}}},
              {"type":"function","function":{"name":"replay","description":"回到第一步重新开始","parameters":{"type":"object","properties":{}}}},
              {"type":"function","function":{"name":"jumpTo","description":"跳到指定步（序号从1开始）","parameters":{"type":"object","properties":{"index":{"type":"integer"}},"required":["index"]}}},
              {"type":"function","function":{"name":"gotoCell","description":"跳到首次涉及指定格子的那一步（行列从1开始）","parameters":{"type":"object","properties":{"row":{"type":"integer"},"col":{"type":"integer"}},"required":["row","col"]}}}
            ]
        """.trimIndent()
    }
}

/** Human-readable conclusion of a step: a placement or the candidates it strikes.
 *  Rows/cols are rendered 1-based (R1C1..R9C9). */
internal fun conclusion(step: DemoStep): String {
    step.placement?.let { return "在 R${it.row + 1}C${it.col + 1} 填 ${it.value}" }
    if (step.eliminatedCells.isNotEmpty()) {
        return "排除候选：" + step.eliminatedCells.joinToString("；") { e ->
            "R${e.row + 1}C${e.col + 1} 去掉 ${e.values.joinToString("、")}"
        }
    }
    return step.narration
}

/**
 * Pure tool dispatch for the AI coach — maps a tool name + its (1-based) args onto
 * [DemoController] moves. Kept free of org.json so the 1-based→0-based conversion
 * (an easy off-by-one) is unit-testable. Unknown names and missing args are no-ops.
 */
internal fun applyCoachTool(
    ctrl: DemoController,
    name: String,
    index: Int?,
    row: Int?,
    col: Int?
): DemoController = when (name) {
    "next" -> ctrl.next()
    "prev" -> ctrl.prev()
    "replay" -> ctrl.replay()
    "jumpTo" -> if (index != null) ctrl.jumpTo(index - 1) else ctrl
    "gotoCell" -> if (row != null && col != null) ctrl.gotoCell(row - 1, col - 1) else ctrl
    else -> ctrl
}
