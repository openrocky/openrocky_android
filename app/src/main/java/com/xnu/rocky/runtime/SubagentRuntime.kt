//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-12
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime

import com.xnu.rocky.providers.ChatClient
import com.xnu.rocky.providers.ChatMessage
import com.xnu.rocky.providers.ProviderConfiguration
import com.xnu.rocky.providers.ToolCallData
import com.xnu.rocky.providers.ToolCallFunction
import com.xnu.rocky.runtime.tools.Toolbox
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.math.min

data class SubagentTask(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val allowedTools: Set<String>? = null
)

data class SubagentResult(
    val taskID: String,
    val summary: String,
    val details: String,
    val toolsUsed: List<String>,
    val succeeded: Boolean,
    val elapsedSeconds: Double
)

data class DelegateTaskResult(
    val taskDescription: String,
    val results: List<SubagentResult>,
    val totalElapsedSeconds: Double
)

/**
 * Structured progress events emitted while a delegate-task is in flight.
 * Subscribers (the session runtime) translate these into timeline entries so
 * the user can watch the back-end agent work tool-by-tool instead of staring
 * at a blank "delegating..." line until the final summary lands.
 *
 * Mirrors iOS OpenRockySubagentEvent.
 */
sealed class SubagentEvent {
    data class PlanStarted(val subtaskCount: Int, val taskDescription: String) : SubagentEvent()
    data class SubtaskStarted(
        val taskID: String,
        val subtaskIndex: Int,
        val totalCount: Int,
        val description: String
    ) : SubagentEvent()
    data class ToolStarted(
        val taskID: String,
        val name: String,
        val isSkill: Boolean,
        val argsPreview: String
    ) : SubagentEvent()
    data class ToolCompleted(
        val taskID: String,
        val name: String,
        val isSkill: Boolean,
        val succeeded: Boolean,
        val resultPreview: String,
        val elapsedSeconds: Double
    ) : SubagentEvent()
    data class SubtaskCompleted(
        val taskID: String,
        val succeeded: Boolean,
        val summary: String,
        val elapsedSeconds: Double
    ) : SubagentEvent()
    data class PlanCompleted(
        val elapsedSeconds: Double,
        val succeededCount: Int,
        val totalCount: Int
    ) : SubagentEvent()
}

class SubagentRuntime(
    private val toolbox: Toolbox,
    private val configuration: ProviderConfiguration,
    private val timeoutMillis: Long = 60_000L,
    private val onEvent: ((SubagentEvent) -> Unit)? = null
) {
    companion object {
        /** Compact one-line preview of JSON args. Newlines collapsed, truncated to keep
         *  the timeline readable. No redaction — tool args are model-generated. */
        fun previewArguments(arguments: String): String {
            val collapsed = arguments
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim()
            return if (collapsed.length <= 80) collapsed else collapsed.take(80) + "..."
        }

        /** Compact one-line preview of a tool result for the timeline. */
        fun previewResult(result: String): String {
            val collapsed = result
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim()
            return if (collapsed.length <= 120) collapsed else collapsed.take(120) + "..."
        }
    }

    suspend fun execute(
        taskDescription: String,
        subtasks: List<SubagentTask>,
        context: String
    ): DelegateTaskResult {
        val start = System.currentTimeMillis()
        val tasks = if (subtasks.isEmpty()) listOf(SubagentTask(description = taskDescription)) else subtasks
        onEvent?.invoke(SubagentEvent.PlanStarted(subtaskCount = tasks.size, taskDescription = taskDescription))

        val totalCount = tasks.size
        val results = coroutineScope {
            tasks.mapIndexed { index, task ->
                async { runSingleAgent(task, index, totalCount, context) }
            }.awaitAll()
        }

        val elapsed = (System.currentTimeMillis() - start) / 1000.0
        onEvent?.invoke(SubagentEvent.PlanCompleted(
            elapsedSeconds = elapsed,
            succeededCount = results.count { it.succeeded },
            totalCount = results.size
        ))
        return DelegateTaskResult(
            taskDescription = taskDescription,
            results = results,
            totalElapsedSeconds = elapsed
        )
    }

    private suspend fun runSingleAgent(
        task: SubagentTask,
        subtaskIndex: Int,
        totalCount: Int,
        parentContext: String
    ): SubagentResult {
        val start = System.currentTimeMillis()
        onEvent?.invoke(SubagentEvent.SubtaskStarted(
            taskID = task.id,
            subtaskIndex = subtaskIndex,
            totalCount = totalCount,
            description = task.description
        ))
        val outcome: SubagentResult = try {
            withTimeout(timeoutMillis) {
                executeAgentLoop(task, parentContext).copy(
                    elapsedSeconds = (System.currentTimeMillis() - start) / 1000.0
                )
            }
        } catch (_: TimeoutCancellationException) {
            SubagentResult(
                taskID = task.id,
                summary = "Task failed: Subagent timed out.",
                details = "Subagent timed out after ${timeoutMillis / 1000} seconds.",
                toolsUsed = emptyList(),
                succeeded = false,
                elapsedSeconds = (System.currentTimeMillis() - start) / 1000.0
            )
        } catch (e: Exception) {
            SubagentResult(
                taskID = task.id,
                summary = "Task failed: ${e.message ?: "Unknown error"}",
                details = e.stackTraceToString(),
                toolsUsed = emptyList(),
                succeeded = false,
                elapsedSeconds = (System.currentTimeMillis() - start) / 1000.0
            )
        }
        onEvent?.invoke(SubagentEvent.SubtaskCompleted(
            taskID = outcome.taskID,
            succeeded = outcome.succeeded,
            summary = outcome.summary,
            elapsedSeconds = outcome.elapsedSeconds
        ))
        return outcome
    }

    private suspend fun executeAgentLoop(task: SubagentTask, parentContext: String): SubagentResult {
        val client = ChatClient(configuration)
        val tools = toolbox.subagentToolDefinitions(task.allowedTools)
        val messages = mutableListOf(
            ChatMessage(role = "system", content = buildSystemPrompt(parentContext)),
            ChatMessage(role = "user", content = task.description)
        )
        val toolsUsed = mutableListOf<String>()
        var finalText = ""
        val maxTurns = 10

        for (turn in 0 until maxTurns) {
            val accumulatedToolCalls = mutableMapOf<Int, ToolCallData>()
            val contentBuffer = StringBuilder()
            var hasToolCalls = false

            client.streamChat(messages, tools).collect { delta ->
                delta.content?.let { contentBuffer.append(it) }
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
            }

            if (!hasToolCalls) {
                finalText = contentBuffer.toString()
                break
            }

            val toolCallsList = accumulatedToolCalls.toSortedMap().values.toList()
            messages.add(
                ChatMessage(
                    role = "assistant",
                    content = contentBuffer.toString().ifBlank { null },
                    tool_calls = toolCallsList
                )
            )

            for (toolCall in toolCallsList) {
                val name = toolCall.function.name
                if (name.isBlank()) continue
                val isSkill = name.startsWith("skill-")
                val argsPreview = previewArguments(toolCall.function.arguments)
                onEvent?.invoke(SubagentEvent.ToolStarted(
                    taskID = task.id,
                    name = name,
                    isSkill = isSkill,
                    argsPreview = argsPreview
                ))

                val toolStart = System.currentTimeMillis()
                var succeeded = true
                val result: String = try {
                    toolbox.execute(name, toolCall.function.arguments)
                } catch (e: Exception) {
                    succeeded = false
                    """{"error":"${e.message ?: "Unknown error"}"}"""
                }
                val toolElapsed = (System.currentTimeMillis() - toolStart) / 1000.0
                toolsUsed += name

                onEvent?.invoke(SubagentEvent.ToolCompleted(
                    taskID = task.id,
                    name = name,
                    isSkill = isSkill,
                    succeeded = succeeded,
                    resultPreview = previewResult(result),
                    elapsedSeconds = toolElapsed
                ))

                messages.add(
                    ChatMessage(
                        role = "tool",
                        content = result,
                        tool_call_id = toolCall.id,
                        name = name
                    )
                )
            }

            if (turn == maxTurns - 1 && finalText.isBlank()) {
                finalText = contentBuffer.toString()
            }
        }

        if (finalText.isBlank()) {
            finalText = "Task completed."
        }
        return SubagentResult(
            taskID = task.id,
            summary = extractSummary(finalText),
            details = finalText,
            toolsUsed = toolsUsed,
            succeeded = true,
            elapsedSeconds = 0.0
        )
    }

    private fun buildSystemPrompt(parentContext: String): String {
        return """
            You are a focused task agent within OpenRocky.
            Complete the assigned task thoroughly using available tools.
            If tools fail, try alternatives.
            Start your final answer with one-sentence summary, then details.
            Do not make up information.

            Context: ${if (parentContext.isBlank()) "None." else parentContext}
        """.trimIndent()
    }

    private fun extractSummary(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return "Task completed."
        val end = trimmed.indexOfFirst { it in listOf('.', '!', '?', '。', '！', '？') }
        if (end in 0..min(199, trimmed.lastIndex)) {
            return trimmed.substring(0, end + 1)
        }
        return if (trimmed.length <= 200) trimmed else trimmed.take(200) + "..."
    }
}
