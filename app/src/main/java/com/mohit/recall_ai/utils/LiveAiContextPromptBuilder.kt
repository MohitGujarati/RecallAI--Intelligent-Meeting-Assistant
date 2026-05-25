package com.mohit.recall_ai.utils


/**
 * Utility to format context payloads specifically for the Gemini Live AI WebSocket.
 */
object LiveAiContextPromptBuilder {

    /**
     * Builds the initial context prompt for a single-meeting session.
     * @param toolInstructions Combined prompt instructions from [ToolRegistry.getPromptInstructions].
     */
    fun buildInitialPrompt(
        title: String,
        defaultSummary: String?,
        toolInstructions: String = ""
    ): String {
        return """
            You are 'Recall AI', an expert meeting assistant.
            You are currently having a real-time voice conversation with the user regarding a meeting titled "$title".

            Here is the summary of the meeting so far. Use this to answer the user's questions accurately.
            Keep your spoken responses natural, conversational, and concise. Do not read out lists verbatim unless asked.

            MEETING SUMMARY:
            ${defaultSummary ?: "No summary available yet."}

            $toolInstructions
        """.trimIndent()
    }

    /**
     * Builds the prompt instructing the standard REST SummaryService to generate
     * a dense, chronological, LLM-optimized dump (Used for >= 30m meetings).
     * * NOTE: This actively strips out UI XML tags used in our default summary.
     */
    fun buildDenseContextPrompt(fullTranscript: String): String {
        return """
            Read the following meeting transcript. Create a dense, highly detailed chronological summary of EVERYTHING discussed.
            This output will be fed directly into another LLM as context, NOT shown to a human.
            Do NOT use any XML tags, formatting, or conversational filler. Output raw, compressed data points, names, dates, and decisions.
            
            TRANSCRIPT:
            $fullTranscript
        """.trimIndent()
    }

    /**
     * Builds a global context prompt from ALL the user's meeting summaries.
     * Used by the home screen Live AI (meetingId == 0L).
     */
    /**
     * Builds a global context prompt from ALL the user's meeting summaries.
     * @param toolInstructions Combined prompt instructions from [ToolRegistry.getPromptInstructions].
     */
    fun buildGlobalPrompt(
        summaries: List<com.mohit.recall_ai.data.local.entity.Summary>,
        toolInstructions: String = ""
    ): String {
        if (summaries.isEmpty()) {
            return """
                You are 'Recall AI', a smart voice assistant.
                You are having a real-time voice conversation with the user.
                The user has no meeting records yet. Let them know and offer to help with anything else.
                Keep your spoken responses natural, conversational, and concise.

                $toolInstructions
            """.trimIndent()
        }

        val meetingsBlock = summaries.take(20).joinToString("\n\n---\n\n") { s ->
            buildString {
                appendLine("Meeting: ${s.title ?: "Untitled"}")
                if (!s.summary.isNullOrBlank()) appendLine("Summary: ${s.summary}")
                if (!s.keyPoints.isNullOrBlank()) appendLine("Key Points: ${s.keyPoints}")
                if (!s.actionItems.isNullOrBlank()) appendLine("Action Items: ${s.actionItems}")
            }
        }

        return """
            You are 'Recall AI', a smart voice assistant with full knowledge of the user's meeting history.
            You are having a real-time voice conversation. The user can ask you about ANY of their past meetings.

            Below are summaries, key points, and action items from their ${summaries.size} most recent meetings.
            Use this context to answer questions accurately.
            Keep your spoken responses natural, conversational, and concise.

            ══ MEETINGS ══
            $meetingsBlock

            $toolInstructions
        """.trimIndent()
    }
}