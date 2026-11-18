package com.example.features.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.model.PaperSpec
import com.example.data.model.OrigamiTemplate
import com.example.data.model.OrigamiTemplateId
import com.example.data.model.TemplateCatalog
import com.example.data.preferences.StudioPreferencesRepository
import com.example.data.repositories.ProjectRepository
import com.example.data.repositories.ProjectSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
  val searchQuery: String = "",
  val projects: List<ProjectSummary> = emptyList(),
  val templates: List<OrigamiTemplate> = TemplateCatalog.templates,
  val defaultGridSpacingMm: Double = 25.0,
  val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
  private val projectRepository: ProjectRepository,
  preferencesRepository: StudioPreferencesRepository,
) : ViewModel() {

  private val searchQuery = MutableStateFlow("")

  private val projectsFlow =
    searchQuery.flatMapLatest { query -> projectRepository.observeProjects(query) }

  val uiState: StateFlow<HomeUiState> =
    combine(searchQuery, projectsFlow, preferencesRepository.preferences) { query, projects, prefs ->
        HomeUiState(
          searchQuery = query,
          projects = projects,
          templates = TemplateCatalog.templates,
          defaultGridSpacingMm = prefs.defaultGridSpacingMm,
          isLoading = false,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(),
      )

  fun updateSearchQuery(query: String) {
    searchQuery.value = query
  }

  fun createProject(
    name: String,
    widthMm: Double,
    heightMm: Double,
    gridSpacingMm: Double,
    templateId: OrigamiTemplateId,
    onCreated: (String) -> Unit,
  ) {
    viewModelScope.launch {
      val safeWidth = widthMm.coerceIn(10.0, 2000.0)
      val safeHeight = heightMm.coerceIn(10.0, 2000.0)
      val safeGrid = gridSpacingMm.coerceIn(1.0, 500.0)
      val doc =
        projectRepository.createProject(
          name = name,
          paper = PaperSpec(widthMm = safeWidth, heightMm = safeHeight, gridSpacingMm = safeGrid),
          templateId = templateId,
        )
      onCreated(doc.id)
    }
  }

  fun renameProject(projectId: String, newName: String) {
    viewModelScope.launch { projectRepository.renameProject(projectId, newName) }
  }

  fun duplicateProject(projectId: String) {
    viewModelScope.launch { projectRepository.duplicateProject(projectId) }
  }

  fun deleteProject(projectId: String) {
    viewModelScope.launch { projectRepository.deleteProject(projectId) }
  }

  companion object {
    fun factory(
      projectRepository: ProjectRepository,
      preferencesRepository: StudioPreferencesRepository,
    ): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          return HomeViewModel(projectRepository, preferencesRepository) as T
        }
      }
  }
}
