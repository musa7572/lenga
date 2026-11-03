package app.lenga.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import app.lenga.ui.screens.*
import app.lenga.viewmodel.StudyViewModel

private const val DEEP_LINK_BASE = "lenga://app"
private const val ANIM_DURATION = 300

sealed class Screen(val route: String) {
    object StudyDashboard : Screen("study_dashboard")
    object Quiz : Screen("quiz/{moduleId}") {
        fun createRoute(moduleId: String) = "quiz/$moduleId"
    }
    object ContentReader : Screen("content/{contentId}") {
        fun createRoute(contentId: String) = "content/$contentId"
    }
    object Reward : Screen("reward")
    object Progress : Screen("progress")
    object EmergencyUnlock : Screen("emergency_unlock")
    object Modes : Screen("modes")
    object EditMode : Screen("mode_edit/{modeId}") {
        const val NEW = "new"
        fun createRoute(modeId: String?) = "mode_edit/${modeId ?: NEW}"
    }
}

@Composable
fun StudyNavHost(navController: NavHostController, viewModel: StudyViewModel) {
    NavHost(
        navController = navController,
        startDestination = Screen.StudyDashboard.route,
        enterTransition = { slideInHorizontally(tween(ANIM_DURATION)) { it } + fadeIn(tween(ANIM_DURATION)) },
        exitTransition = { slideOutHorizontally(tween(ANIM_DURATION)) { -it / 3 } + fadeOut(tween(ANIM_DURATION / 2)) },
        popEnterTransition = { slideInHorizontally(tween(ANIM_DURATION)) { -it } + fadeIn(tween(ANIM_DURATION)) },
        popExitTransition = { slideOutHorizontally(tween(ANIM_DURATION)) { it } + fadeOut(tween(ANIM_DURATION / 2)) }
    ) {
        composable(
            Screen.StudyDashboard.route,
            deepLinks = listOf(navDeepLink { uriPattern = "$DEEP_LINK_BASE/dashboard" }),
            enterTransition = { fadeIn(tween(ANIM_DURATION)) },
            exitTransition = { fadeOut(tween(ANIM_DURATION / 2)) }
        ) {
            StudyDashboardScreen(
                viewModel = viewModel,
                onModuleClick = { moduleId ->
                    navController.navigate(Screen.Quiz.createRoute(moduleId))
                },
                onContentClick = { contentId ->
                    navController.navigate(Screen.ContentReader.createRoute(contentId))
                },
                onProgressClick = {
                    navController.navigate(Screen.Progress.route)
                },
                onEmergencyClick = {
                    navController.navigate(Screen.EmergencyUnlock.route)
                },
                onModesClick = {
                    navController.navigate(Screen.Modes.route)
                }
            )
        }

        composable(
            Screen.Quiz.route,
            deepLinks = listOf(navDeepLink { uriPattern = "$DEEP_LINK_BASE/quiz/{moduleId}" })
        ) { backStackEntry ->
            val moduleId = backStackEntry.arguments?.getString("moduleId") ?: ""
            QuizScreen(
                moduleId = moduleId,
                viewModel = viewModel,
                onQuizComplete = { passed ->
                    if (passed && viewModel.focusStatus.value?.isActive == true) {
                        navController.popBackStack(Screen.StudyDashboard.route, inclusive = false)
                    } else if (passed) {
                        navController.navigate(Screen.Reward.route) {
                            popUpTo(Screen.StudyDashboard.route)
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Screen.ContentReader.route,
            deepLinks = listOf(navDeepLink { uriPattern = "$DEEP_LINK_BASE/content/{contentId}" })
        ) { backStackEntry ->
            val contentId = backStackEntry.arguments?.getString("contentId") ?: ""
            ContentReaderScreen(
                contentId = contentId,
                viewModel = viewModel,
                onComplete = { navController.popBackStack() }
            )
        }

        composable(
            Screen.Reward.route,
            enterTransition = { scaleIn(tween(ANIM_DURATION), initialScale = 0.8f) + fadeIn(tween(ANIM_DURATION)) },
            exitTransition = { scaleOut(tween(ANIM_DURATION), targetScale = 0.8f) + fadeOut(tween(ANIM_DURATION / 2)) }
        ) {
            RewardScreen(
                viewModel = viewModel,
                onTimeUp = {
                    navController.navigate(Screen.StudyDashboard.route) {
                        popUpTo(0)
                    }
                }
            )
        }

        composable(
            Screen.Progress.route,
            deepLinks = listOf(navDeepLink { uriPattern = "$DEEP_LINK_BASE/progress" })
        ) {
            ProgressScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Screen.EmergencyUnlock.route,
            deepLinks = listOf(navDeepLink { uriPattern = "$DEEP_LINK_BASE/emergency" }),
            enterTransition = { slideInVertically(tween(ANIM_DURATION)) { it } + fadeIn(tween(ANIM_DURATION)) },
            popExitTransition = { slideOutVertically(tween(ANIM_DURATION)) { it } + fadeOut(tween(ANIM_DURATION / 2)) }
        ) {
            EmergencyUnlockScreen(
                viewModel = viewModel,
                onUnlock = {
                    navController.navigate(Screen.Reward.route) {
                        popUpTo(Screen.StudyDashboard.route)
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Screen.Modes.route) {
            ModesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate(Screen.EditMode.createRoute(null)) },
                onEdit = { id -> navController.navigate(Screen.EditMode.createRoute(id)) }
            )
        }

        composable(Screen.EditMode.route) { backStackEntry ->
            val modeId = backStackEntry.arguments?.getString("modeId")?.takeIf { it != Screen.EditMode.NEW }
            EditModeScreen(
                modeId = modeId,
                viewModel = viewModel,
                onDone = { navController.popBackStack() }
            )
        }
    }
}