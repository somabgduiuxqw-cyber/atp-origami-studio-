package com.example.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.StudioPreferences
import com.example.data.preferences.StudioPreferencesRepository
import com.example.data.preferences.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
  private val preferencesRepository: StudioPreferencesRepository,
) : ViewModel() {

  val preferences: StateFlow<StudioPreferences> =
    preferencesRepository.preferences.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = StudioPreferences(),
    )

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
  }

  fun setDefaultGridSpacing(spacingMm: Double) {
    viewModelScope.launch { preferencesRepository.setDefaultGridSpacing(spacingMm) }
  }

  fun setDefaultShowGrid(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setDefaultShowGrid(enabled) }
  }

  fun setDefaultSnapToGrid(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setDefaultSnapToGrid(enabled) }
  }

  fun setDefaultSnapToVertex(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setDefaultSnapToVertex(enabled) }
  }

  fun setDefaultSnapToIntersection(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setDefaultSnapToIntersection(enabled) }
  }

  fun setDefaultSnapToMidpoint(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setDefaultSnapToMidpoint(enabled) }
  }

  fun setShowConstructionLines(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setShowConstructionLines(enabled) }
  }

  fun setShowIntersections(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setShowIntersections(enabled) }
  }

  fun setShowCoordinateHud(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setShowCoordinateHud(enabled) }
  }

  companion object {
    fun factory(
      preferencesRepository: StudioPreferencesRepository,
    ): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          return SettingsViewModel(preferencesRepository) as T
        }
      }
  }
}
