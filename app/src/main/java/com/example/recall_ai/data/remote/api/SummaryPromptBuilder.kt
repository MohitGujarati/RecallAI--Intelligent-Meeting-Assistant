package com.example.recall_ai.data.remote.api

import com.example.recall_ai.config.AiModelConfig

internal object SummaryPromptBuilder {

    val GEMINI_MODEL = AiModelConfig.GEMINI_SUMMARY_MODEL

    private const val MAX_TRANSCRIPT_CHARS = 120_000

    fun build(transcript: String, meetingTitle: String): String {
        val truncated = transcript.take(MAX_TRANSCRIPT_CHARS).let {
            if (transcript.length > MAX_TRANSCRIPT_CHARS)
                it + "\n\n[Transcript truncated — meeting was very long]"
            else it
        }

        return """
You are a professional meeting assistant. Analyze the transcript below and produce a structured summary.

CRITICAL: You MUST wrap each section in the exact XML tags shown below. Do NOT use markdown headers, do NOT omit the tags. The tags must appear exactly as: <TITLE>, </TITLE>, <SUMMARY>, </SUMMARY>, <KEY_POINTS>, </KEY_POINTS>, <ACTION_ITEMS>, </ACTION_ITEMS>.

Output your response EXACTLY in this format — no other text before or after:

<TITLE>A concise descriptive title for this meeting (max 10 words)</TITLE>

<SUMMARY>
Write 2 to 4 paragraphs summarizing the key discussion points, decisions made, and overall context. Be specific and factual. If the transcript is very short, provide a brief but complete summary.
</SUMMARY>

<KEY_POINTS>
• First key point or decision
• Second key point or decision
• Third key point (add more as needed, 3-7 total)
</KEY_POINTS>

<ACTION_ITEMS>
• Description of concrete next step or task
• Another action item if applicable
If no action items were identified, write: None identified.
</ACTION_ITEMS>

IMPORTANT RULES:
1. Always include ALL four XML sections, even for very short transcripts
2. Do NOT add any text outside the XML tags
3. Do NOT use markdown formatting (no #, no **, no ```)
4. Key points and action items MUST start with • (bullet character)

Meeting title: $meetingTitle

Transcript:
$truncated
        """.trimIndent()
    }

    fun buildGeminiRequestBody(prompt: String): String {
        val escaped = prompt
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        return """
{
  "contents": [{
    "role": "user",
    "parts": [{"text": "$escaped"}]
  }],
  "generationConfig": {
    "temperature": 0.3,
    "maxOutputTokens": 2048,
    "topP": 0.8
  },
  "safetySettings": [
    {"category": "HARM_CATEGORY_HARASSMENT",        "threshold": "BLOCK_NONE"},
    {"category": "HARM_CATEGORY_HATE_SPEECH",       "threshold": "BLOCK_NONE"},
    {"category": "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold": "BLOCK_NONE"},
    {"category": "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold": "BLOCK_NONE"}
  ]
}
        """.trimIndent()
    }
}