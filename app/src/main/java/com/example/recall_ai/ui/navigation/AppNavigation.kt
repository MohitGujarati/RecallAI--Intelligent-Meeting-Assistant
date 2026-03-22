package com.example.recall_ai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.recall_ai.ui.dashboard.AllRecallsScreen
import com.example.recall_ai.ui.dashboard.DashboardScreen
import com.example.recall_ai.ui.meetingdetail.MeetingDetailScreen
import com.example.recall_ai.ui.recording.RecordingScreen

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Dashboard.route
    ) {

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
                meetingId      = meetingId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}