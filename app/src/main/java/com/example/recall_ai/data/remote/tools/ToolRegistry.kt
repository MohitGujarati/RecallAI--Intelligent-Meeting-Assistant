package com.example.recall_ai.data.remote.tools

import android.util.Log
import com.example.recall_ai.data.local.dao.ReminderDao
import com.example.recall_ai.data.local.entity.Reminder
import com.example.recall_ai.data.local.entity.ReminderType
import com.example.recall_ai.service.reminder.ReminderAlarmScheduler
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ToolRegistry"

// ── Core Types ──────────────────────────────────────────────────────────

/**
 * Everything Gemini needs to know about a tool, plus the natural-language
 * instructions that tell the model *when* to invoke it.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any?>,
    val promptInstructions: String
)

/**
 * A single tool handler.  Receives the parsed args and an optional meetingId
 * (null for global-context sessions) and returns a [ToolResult].
 */
fun interface ToolHandler {
    suspend fun execute(args: Map<String, Any?>, meetingId: Long?): ToolResult
}

/**
 * Outcome of executing a tool — serialised as the `toolResponse` sent back to Gemini.
 */
data class ToolResult(
    val success: Boolean,
    val data: Map<String, Any?> = emptyMap(),
    val error: String? = null
) {
    /** Flattens into the map that [GeminiLiveClient.sendFunctionResponse] expects. */
    fun toResponseMap(): Map<String, Any?> = buildMap {
        put("success", success)
        if (error != null) put("error", error)
        putAll(data)
    }
}

// ── Registry ────────────────────────────────────────────────────────────

/**
 * Single source of truth for every Gemini function-calling tool.
 *
 * **Adding a new tool is a 3-step process — all in this file:**
 * 1. Define a [ToolDefinition] (schema + prompt instructions)
 * 2. Write a private `handleXxx` suspend function that returns [ToolResult]
 * 3. Add the entry to [tools]
 *
 * The rest of the system (GeminiLiveClient, LiveAiRepository,
 * LiveAiContextPromptBuilder) picks it up automatically.
 */
@Singleton
class ToolRegistry @Inject constructor(
    private val reminderDao: ReminderDao,
    private val alarmScheduler: ReminderAlarmScheduler
    // Inject future deps here (e.g. MindmapGenerator)
) {

    // ── Tool Catalogue ──────────────────────────────────────────────────

    private val tools: Map<String, Pair<ToolDefinition, ToolHandler>> = mapOf(

        // ┌─────────────────────────────────────────────────────────────────
        // │  SET REMINDER / TO-DO
        // └─────────────────────────────────────────────────────────────────
        "set_reminder" to Pair(
            ToolDefinition(
                name = "set_reminder",
                description = "Set a reminder or alarm for the user. " +
                        "Use this when the user asks to be reminded about something, " +
                        "wants a wake-up call, or mentions a time-sensitive event. " +
                        "Always confirm with the user before calling this function.",
                parameters = mapOf(
                    "type" to "OBJECT",
                    "properties" to mapOf(
                        "title" to mapOf(
                            "type" to "STRING",
                            "description" to "Short description of the reminder"
                        ),
                        "minutes_from_now" to mapOf(
                            "type" to "INTEGER",
                            "description" to "Minutes from now to fire the alarm. Omit if no specific time."
                        ),
                        "is_timed" to mapOf(
                            "type" to "BOOLEAN",
                            "description" to "true if the user specified a time (alarm), false for a to-do"
                        )
                    ),
                    "required" to listOf("title", "is_timed")
                ),
                promptInstructions = """
                    |══ REMINDERS & TO-DOS ══
                    |You have access to a set_reminder tool. When the user says things like "wake me in 20 minutes",
                    |"remind me to call John", "don't forget to...", "XYZ will be here in 20 mins", or any phrase
                    |implying a reminder, alarm, or to-do:
                    |1. First confirm with the user naturally: "Sure, I'll set a reminder for that. Should I go ahead?"
                    |2. Only after the user confirms, call the set_reminder function.
                    |3. If the user specifies a time (e.g. "in 20 minutes", "at 3pm"), set is_timed=true and provide minutes_from_now.
                    |4. If no time is specified (e.g. "remind me to buy groceries"), set is_timed=false — this saves it as a to-do.
                    |5. After calling the function, confirm naturally: "Done, I've set that for you."
                """.trimMargin()
            ),
            ToolHandler { args, meetingId -> handleSetReminder(args, meetingId) }
        )

        // ── Add future tools here ───────────────────────────────────────
        // "generate_mindmap" to Pair(ToolDefinition(...), ToolHandler { ... })
    )

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * JSON-ready list of function declarations for Gemini setup / request payloads.
     * Returns the format expected by `tools[].functionDeclarations[]`.
     */
    fun getDeclarations(): List<Map<String, Any?>> =
        tools.values.map { (def, _) ->
            mapOf(
                "name" to def.name,
                "description" to def.description,
                "parameters" to def.parameters
            )
        }

    /**
     * Combined natural-language instructions for every registered tool.
     * Append this to the system prompt so Gemini knows when to call each tool.
     */
    fun getPromptInstructions(): String =
        tools.values.joinToString("\n\n") { (def, _) -> def.promptInstructions }

    /**
     * Execute a tool by name.
     * Returns an error [ToolResult] for unknown tools rather than throwing.
     */
    suspend fun execute(
        name: String,
        args: Map<String, Any?>,
        meetingId: Long?
    ): ToolResult {
        val (_, handler) = tools[name]
            ?: return ToolResult(
                success = false,
                error = "Unknown tool: $name"
            ).also { Log.w(TAG, "Unknown tool requested: $name") }

        return try {
            handler.execute(args, meetingId)
        } catch (e: Exception) {
            Log.e(TAG, "Tool '$name' failed", e)
            ToolResult(success = false, error = e.message ?: "Unknown error")
        }
    }

    // ── Private Handler Implementations ─────────────────────────────────

    private suspend fun handleSetReminder(
        args: Map<String, Any?>,
        meetingId: Long?
    ): ToolResult {
        val title = args["title"] as? String ?: "Reminder"
        val isTimed = args["is_timed"] as? Boolean ?: false
        val minutesFromNow = (args["minutes_from_now"] as? Number)?.toInt()

        val triggerAtMillis = if (isTimed && minutesFromNow != null) {
            System.currentTimeMillis() + (minutesFromNow * 60_000L)
        } else null

        val reminder = Reminder(
            meetingId = meetingId,
            type = if (isTimed) ReminderType.ALARM else ReminderType.TODO,
            title = title,
            triggerAtMillis = triggerAtMillis
        )

        val insertedId = reminderDao.insert(reminder)
        val savedReminder = reminder.copy(id = insertedId)

        if (isTimed && triggerAtMillis != null) {
            alarmScheduler.schedule(savedReminder)
            Log.i(TAG, "Alarm scheduled: '$title' in $minutesFromNow min (id=$insertedId)")
        } else {
            Log.i(TAG, "TODO saved: '$title' (id=$insertedId)")
        }

        return ToolResult(
            success = true,
            data = mapOf(
                "reminder_id" to insertedId,
                "type" to if (isTimed) "alarm" else "todo",
                "message" to if (isTimed) "Alarm set for $minutesFromNow minutes from now"
                else "To-do item saved"
            )
        )
    }
}
