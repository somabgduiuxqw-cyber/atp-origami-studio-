package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.AppContainer
import com.example.features.editor.EditorScreen
import com.example.features.editor.EditorViewModel
import com.example.features.projects.HomeScreen
import com.example.features.projects.HomeViewModel
import com.example.features.settings.SettingsScreen
import com.example.features.settings.SettingsViewModel

object StudioRoutes {
  const val HOME = "home"
  const val SETTINGS = "settings"
  const val EDITOR_ARG_PROJECT_ID = "projectId"
  const val EDITOR = "editor/{$EDITOR_ARG_PROJECT_ID}"

  fun editorRoute(projectId: String): String = "editor/$projectId"
}

@Composable
fun OrigamiNavGraph(container: AppContainer) {
  val navController = rememberNavController()

  NavHost(
    navController = navController,
    startDestination = StudioRoutes.HOME,
  ) {
    composable(StudioRoutes.HOME) {
      val homeViewModel: HomeViewModel =
        viewModel(
          factory =
            HomeViewModel.factory(
              projectRepository = container.projectRepository,
              preferencesRepository = container.preferencesRepository,
            )
        )
      HomeScreen(
        viewModel = homeViewModel,
        onOpenProject = { projectId ->
          navController.navigate(StudioRoutes.editorRoute(projectId))
        },
        onOpenSettings = {
          navController.navigate(StudioRoutes.SETTINGS)
        },
      )
    }

    composable(
      route = StudioRoutes.EDITOR,
      arguments = listOf(navArgument(StudioRoutes.EDITOR_ARG_PROJECT_ID) { type = NavType.StringType }),
    ) { backStackEntry ->
      val projectId =
        backStackEntry.arguments?.getString(StudioRoutes.EDITOR_ARG_PROJECT_ID) ?: return@composable
      val editorViewModel: EditorViewModel =
        viewModel(
          factory =
            EditorViewModel.factory(
              projectId = projectId,
              projectRepository = container.projectRepository,
              preferencesRepository = container.preferencesRepository,
            )
        )
      EditorScreen(
        viewModel = editorViewModel,
        onNavigateBack = { navController.popBackStack() },
      )
    }

    composable(StudioRoutes.SETTINGS) {
      val settingsViewModel: SettingsViewModel =
        viewModel(
          factory = SettingsViewModel.factory(container.preferencesRepository)
        )
      SettingsScreen(
        viewModel = settingsViewModel,
        onNavigateBack = { navController.popBackStack() },
      )
    }
  }
}
