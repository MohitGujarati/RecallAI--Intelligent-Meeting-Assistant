package com.example.recall_ai.data.remote.api

import android.util.Log

private const val TAG = "SummaryParser"

/**
 * Parses the complete LLM stream buffer into four structured sections.
 *
 * ── Parsing strategy ─────────────────────────────────────────────────
 * Primary: XML tags (<TITLE>...</TITLE>) extracted via regex.
 * Fallback: if XML parsing fails, treat the raw buffer as the summary
 * body and derive title/points from the text. This handles cases where
 * the model outputs markdown or plain text instead of strict XML.
 *
 * ── Called once, at end of stream ────────────────────────────────────
 * Only called after streaming completes. Not called on partial buffers.
 */
internal object SummaryParser {

    data class ParsedSummary(
        val title: String,
        val summary: String,
        val keyPoints: String,
        val actionItems: String
    )

    /**
     * Attempts to parse [buffer] into structured sections.
     * Returns null only if the buffer is blank.
     */
    fun parse(buffer: String): ParsedSummary? {
        if (buffer.isBlank()) {
            Log.e(TAG, "Buffer is empty — nothing to parse")
            return null
        }

        // ── Primary: XML extraction ──────────────────────────────────
        val title       = extractSection(buffer, "TITLE")
        val summary     = extractSection(buffer, "SUMMARY")
        val keyPoints   = extractSection(buffer, "KEY_POINTS")
        val actionItems = extractSection(buffer, "ACTION_ITEMS")

        // If at least one XML section found, use XML-based output
        if (title != null || summary != null || keyPoints != null || actionItems != null) {
            if (title == null)       Log.w(TAG, "TITLE section missing — using fallback")
            if (summary == null)     Log.w(TAG, "SUMMARY section missing — using fallback")
            if (keyPoints == null)   Log.w(TAG, "KEY_POINTS section missing — using fallback")
            if (actionItems == null) Log.w(TAG, "ACTION_ITEMS section missing — using fallback")

            return ParsedSummary(
                title       = title       ?: deriveFallbackTitle(buffer),
                summary     = summary     ?: buffer.take(500).trim(),
                keyPoints   = keyPoints   ?: "• Could not extract key points",
                actionItems = actionItems ?: "None identified"
            )
        }

        // ── Fallback: no XML tags found → parse raw text ─────────────
        Log.w(TAG, "No XML sections found — using raw text fallback (${buffer.length} chars)")
        return parseRawText(buffer)
    }

    /** Extracts content between <TAG> and </TAG>, trimmed. */
    private fun extractSection(text: String, tag: String): String? =
        Regex("<$tag>\\s*(.*?)\\s*</$tag>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    /**
     * Raw text fallback when the model outputs markdown, bullet lists,
     * or plain paragraphs instead of XML sections.
     *
     * Strategy:
     *   - Title: first non-blank line (stripped of markdown #)
     *   - Summary: first paragraph block (or first 500 chars)
     *   - Key Points: lines starting with •, -, *, or numbered lists
     *   - Action Items: lines after "action" header, or "None identified"
     */
    private fun parseRawText(buffer: String): ParsedSummary {
        val lines = buffer.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Title: first line, stripped of markdown heading markers
        val title = lines.firstOrNull()
            ?.removePrefix("#")?.removePrefix("#")?.removePrefix("#")
            ?.trim()
            ?.take(80)
            ?: "Meeting Summary"

        // Find bullet-point lines (•, -, *, or "1." etc.)
        val bulletRegex = Regex("^[•\\-\\*]\\s|^\\d+[.)]\\s")
        val bulletLines = lines.filter { bulletRegex.containsMatchIn(it) }

        // Summary: all non-bullet, non-heading lines joined
        val summaryLines = lines
            .drop(1)  // skip title line
            .filter { !bulletRegex.containsMatchIn(it) }
            .filter { !it.startsWith("#") }
            .filter { !it.startsWith("**") || !it.endsWith("**") }
        val summaryText = if (summaryLines.isNotEmpty()) {
            summaryLines.joinToString("\n")
        } else {
            // No plain paragraphs? Use the full buffer minus the title
            buffer.substringAfter("\n").take(500).trim()
        }

        // Key points: bullet lines (if any), otherwise "Could not extract"
        val keyPointsText = if (bulletLines.isNotEmpty()) {
            // Take up to first 7 bullet points
            bulletLines.take(7).joinToString("\n") { line ->
                if (line.startsWith("•")) line else "• ${line.removePrefix("-").removePrefix("*").trim()}"
            }
        } else {
            "• Could not extract key points"
        }

        return ParsedSummary(
            title       = title,
            summary     = summaryText.ifBlank { buffer.take(500).trim() },
            keyPoints   = keyPointsText,
            actionItems = "None identified"
        )
    }

    /**
     * Fallback title derived from the first meaningful line of the buffer.
     */
    private fun deriveFallbackTitle(buffer: String): String =
        buffer.lines()
            .firstOrNull { it.isNotBlank() && !it.startsWith("<") }
            ?.removePrefix("#")?.removePrefix("#")?.removePrefix("#")
            ?.take(60)
            ?.trim()
            ?: "Meeting Summary"
}