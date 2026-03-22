package com.example.recall_ai.ui.navigation

/**
 * Typed navigation routes for the app.
 *
 * All navigation in the codebase uses these objects/data classes — never
 * raw route strings. Adding a new screen means adding a new sealed subclass
 * and a new composable destination in AppNavigation.kt.
 */
sealed class Screen(val route: String) {

    /** Meeting list — app entry point */
    object Dashboard : Screen("dashboard")

    /** Active recording screen — shown when Record is pressed */
    object Recording : Screen("recording")

    /** All recalls list — shown when View All is tapped */
    object AllRecalls : Screen("all_recalls")

    /**
     * Meeting detail — transcript + summary.
     * Implemented in Chapter 8.
     */
    data class MeetingDetail(val meetingId: Long) : Screen("meeting/$meetingId") {
        companion object {
            const val ROUTE = "meeting/{meetingId}"
            const val ARG   = "meetingId"
        }
    }
}