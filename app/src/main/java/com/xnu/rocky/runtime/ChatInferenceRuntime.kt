//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-03-25
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime

import com.xnu.rocky.providers.*
import com.xnu.rocky.runtime.tools.Toolbox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ChatInferenceRuntime(
    private val toolbox: Toolbox
) {
    companion object {
        private const val TAG = "ChatInference"
    }

    suspend fun runInference(
        config: ProviderConfiguration,
        messages: MutableList<ChatMessage>,
        tools: List<ToolDefinition>,
        onDelta: (String) -> Unit,
        onToolCall: (String, String) -> Unit,
        onToolResult: (String, String) -> Unit,
        onUsage: (UsageData) -> Unit
    ): String {
        val client = ChatClient(config)
        var fullResponse = ""
        var maxIterations = 10
        LogManager.info("[INFER] runInference start, messages=${messages.size} tools=${tools.size} provider=${config.provider.displayName}", TAG)

        while (maxIterations > 0) {
            maxIterations--
            val accumulatedToolCalls = mutableMapOf<Int, ToolCallData>()
            var hasToolCalls = false
            var contentBuffer = StringBuilder()
            var streamDeltaCount = 0

            LogManager.info("[INFER] iteration ${10 - maxIterations}, calling streamChat...", TAG)

            client.streamChat(messages, tools).collect { delta ->
                streamDeltaCount++
                delta.content?.let { c ->
                    contentBuffer.append(c)
                    onDelta(c)
                }
                delta.toolCalls?.forEach { tc ->
                    hasToolCalls = true
                    val existing = accumulatedToolCalls.getOrPut(tc.index) {
                        ToolCallData(id = tc.id ?: "", function = ToolCallFunction())
                    }
                    val updatedFn = existing.function.copy(
                        name = tc.function?.name ?: existing.function.name,
                        arguments = existing.function.arguments + (tc.function?.arguments ?: "")
                    )
                    accumulatedToolCalls[tc.index] = existing.copy(
                        id = tc.id ?: existing.id,
                        function = updatedFn
                    )
                }
                delta.usage?.let { onUsage(it) }
            }

            LogManager.info("[INFER] stream done, deltaCount=$streamDeltaCount contentLen=${contentBuffer.length} hasToolCalls=$hasToolCalls", TAG)

            if (hasToolCalls) {
                val toolCallsList = accumulatedToolCalls.values.toList()
                LogManager.info("[INFER] toolCalls: ${toolCallsList.map { it.function.name }}", TAG)
                messages.add(ChatMessage(
                    role = "assistant",
                    content = contentBuffer.toString().ifBlank { null },
                    tool_calls = toolCallsList
                ))

                for (toolCall in toolCallsList) {
                    onToolCall(toolCall.function.name, toolCall.function.arguments)
                    val result = toolbox.execute(toolCall.function.name, toolCall.function.arguments)
                    onToolResult(toolCall.function.name, result)
                    messages.add(ChatMessage(
                        role = "tool",
                        content = result,
                        tool_call_id = toolCall.id,
                        name = toolCall.function.name
                    ))
                }
                fullResponse = contentBuffer.toString()
            } else {
                fullResponse = contentBuffer.toString()
                LogManager.info("[INFER] final response len=${fullResponse.length} content='${fullResponse.take(100)}'", TAG)
                messages.add(ChatMessage(role = "assistant", content = fullResponse))
                break
            }
        }

        if (maxIterations == 0) {
            LogManager.warning("[INFER] hit max iterations (10), returning partial response", TAG)
        }

        return fullResponse
    }
}
