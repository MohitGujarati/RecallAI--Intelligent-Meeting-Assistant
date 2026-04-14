package com.example.recall_ai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.recall_ai.Auth.AuthViewModel
import com.example.recall_ai.ui.dashboard.ActionItemsScreen
import com.example.recall_ai.ui.dashboard.AllRecallsScreen
import com.example.recall_ai.ui.dashboard.DashboardScreen
import com.example.recall_ai.ui.liveai.LiveAiScreen
import com.example.recall_ai.ui.login.AccountScreen
import com.example.recall_ai.ui.login.LoginScreen
import com.example.recall_ai.ui.meetingdetail.MeetingDetailScreen
import com.example.recall_ai.ui.recording.RecordingScreen
import com.example.recall_ai.ui.reminders.RemindersScreen

@Composable
fun AppNavigation(navController: NavHostController) {

    // ── Determine start destination based on existing Firebase session ────
    // AuthViewModel.isSignedIn is initialised from AuthRepository.isSignedIn
    // (synchronous FirebaseAuth.currentUser != null) so its initialValue is
    // available immediately — no splash / loading state needed.
    val authViewModel: AuthViewModel = hiltViewModel()
    val isSignedIn by authViewModel.isSignedIn.collectAsStateWithLifecycle()

    val startDestination = if (isSignedIn) Screen.Dashboard.route else Screen.Login.route

    NavHost(
        navController    = navController,
        startDestination = startDestination
    ) {

        // ── Login (Google-only + Guest) ──────────────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onGuestClick = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToRecording = {
                    navController.navigate(Screen.Recording.route)
                },
                onNavigateToMeeting = { meetingId ->
                    navController.navigate(Screen.MeetingDetail(meetingId).route)
                },
                onNavigateToAllRecalls = {
                    navController.navigate(Screen.AllRecalls.route)
                },
                onNavigateToGlobalLiveAi = {
                    // meetingId = 0L signals "global context" mode in LiveAiRepository
                    navController.navigate(Screen.LiveAi(0L).route)
                },
                onNavigateToActionItems = {
                    navController.navigate(Screen.ActionItems.route)
                },
                onNavigateToReminders = {
                    navController.navigate(Screen.Reminders.route)
                },
                onNavigateToAccount = {
                    navController.navigate(Screen.Account.route)
                }
            )
        }

        composable(Screen.Recording.route) {
            RecordingScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.AllRecalls.route) {
            AllRecallsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMeeting = { meetingId ->
                    navController.navigate(Screen.MeetingDetail(meetingId).route)
                }
            )
        }

        // ── Action Items / To-Do ──────────────────────────────────────────
        composable(Screen.ActionItems.route) {
            ActionItemsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Reminders & Alarms ───────────────────────────────────────────
        composable(Screen.Reminders.route) {
            RemindersScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route     = Screen.MeetingDetail.ROUTE,
            arguments = listOf(
                navArgument(Screen.MeetingDetail.ARG) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val meetingId = backStackEntry.arguments
                ?.getLong(Screen.MeetingDetail.ARG)
                ?: return@composable

            MeetingDetailScreen(
                meetingId          = meetingId,
                onNavigateBack     = { navController.popBackStack() },
                onNavigateToLiveAi = { id ->
                    navController.navigate(Screen.LiveAi(id).route)
                }
            )
        }

        // ── Live AI ───────────────────────────────────────────────────────
        composable(
            route     = Screen.LiveAi.ROUTE,
            arguments = listOf(navArgument(Screen.LiveAi.ARG) { type = NavType.LongType })
        ) { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getLong(Screen.LiveAi.ARG)
                ?: return@composable

            LiveAiScreen(
                meetingId      = meetingId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Account / Profile ─────────────────────────────────────────────
        composable(Screen.Account.route) {
            AccountScreen(
                onNavigateBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }   // wipe the entire back stack
                    }
                }
            )
        }
    }
}