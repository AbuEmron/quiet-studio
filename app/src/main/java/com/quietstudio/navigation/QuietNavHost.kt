package com.quietstudio.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quietstudio.feature.editor.EditorScreen
import com.quietstudio.feature.export.ExportQueueScreen
import com.quietstudio.feature.home.HomeScreen
import com.quietstudio.feature.camera.CameraScreen
import com.quietstudio.feature.music.MusicLibraryScreen
import com.quietstudio.feature.projects.ProjectsScreen
import com.quietstudio.feature.record.RecordScreen
import com.quietstudio.feature.settings.SettingsScreen
import com.quietstudio.feature.templates.TemplatesScreen
import com.quietstudio.feature.visuals.VisualLibraryScreen
import com.quietstudio.ui.components.QuietBottomBar
import com.quietstudio.ui.components.QuietTab

object Routes {
    const val HOME = "home"
    const val RECORD_FOR = "record?projectId={projectId}"
    const val EDITOR = "editor/{projectId}"
    const val PROJECTS = "projects"
    const val MUSIC = "music"
    const val CAMERA = "camera"
    const val VISUALS = "visuals"
    const val TEMPLATES = "templates"
    const val EXPORTS = "exports"
    const val SETTINGS = "settings"

    fun editor(projectId: String) = "editor/$projectId"
    fun record(projectId: String? = null) =
        if (projectId == null) "record" else "record?projectId=$projectId"
}

private val TAB_ROUTES = mapOf(
    Routes.HOME to QuietTab.HOME,
    Routes.PROJECTS to QuietTab.PROJECTS,
    Routes.MUSIC to QuietTab.MUSIC,
    Routes.SETTINGS to QuietTab.SETTINGS,
)

@Composable
fun QuietNavHost(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val currentTab = TAB_ROUTES[currentRoute]

    fun switchTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(260))
            },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = {
                slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(220))
            },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onRecord = { navController.navigate(Routes.record()) },
                    onCamera = { navController.navigate(Routes.CAMERA) },
                    onProjects = { switchTab(Routes.PROJECTS) },
                    onMusic = { switchTab(Routes.MUSIC) },
                    onVisuals = { navController.navigate(Routes.VISUALS) },
                    onTemplates = { navController.navigate(Routes.TEMPLATES) },
                    onExports = { navController.navigate(Routes.EXPORTS) },
                    onSettings = { switchTab(Routes.SETTINGS) },
                    onOpenProject = { navController.navigate(Routes.editor(it)) },
                )
            }
            composable(
                Routes.RECORD_FOR,
                arguments = listOf(
                    navArgument("projectId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                ),
            ) { entry ->
                RecordScreen(
                    existingProjectId = entry.arguments?.getString("projectId"),
                    onDone = { projectId ->
                        navController.navigate(Routes.editor(projectId)) {
                            popUpTo(Routes.HOME)
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.EDITOR) { entry ->
                EditorScreen(
                    projectId = entry.arguments?.getString("projectId") ?: return@composable,
                    onBack = { navController.popBackStack() },
                    onExports = { navController.navigate(Routes.EXPORTS) },
                )
            }
            composable(Routes.PROJECTS) {
                ProjectsScreen(
                    onOpen = { navController.navigate(Routes.editor(it)) },
                    onBack = { switchTab(Routes.HOME) },
                )
            }
            composable(Routes.MUSIC) {
                MusicLibraryScreen(onBack = { switchTab(Routes.HOME) })
            }
            composable(Routes.CAMERA) {
                CameraScreen(
                    onBack = { navController.popBackStack() },
                    onClipReady = { id ->
                        navController.navigate(Routes.editor(id)) {
                            popUpTo(Routes.HOME)
                        }
                    },
                )
            }
            composable(Routes.VISUALS) {
                VisualLibraryScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.TEMPLATES) {
                TemplatesScreen(
                    onBack = { navController.popBackStack() },
                    onUseTemplate = { navController.navigate(Routes.record()) },
                )
            }
            composable(Routes.EXPORTS) {
                ExportQueueScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { switchTab(Routes.HOME) })
            }
        }

        if (currentTab != null) {
            Box(Modifier.align(Alignment.BottomCenter)) {
                QuietBottomBar(
                    current = currentTab,
                    onTab = { tab ->
                        when (tab) {
                            QuietTab.HOME -> switchTab(Routes.HOME)
                            QuietTab.PROJECTS -> switchTab(Routes.PROJECTS)
                            QuietTab.MUSIC -> switchTab(Routes.MUSIC)
                            QuietTab.SETTINGS -> switchTab(Routes.SETTINGS)
                        }
                    },
                    onRecord = { navController.navigate(Routes.record()) },
                )
            }
        }
    }
}
