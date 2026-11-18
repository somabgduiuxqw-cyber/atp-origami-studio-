package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.StudioPreferences
import com.example.ui.navigation.OrigamiNavGraph
import com.example.ui.theme.AtpOrigamiStudioTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val appContainer = (application as OrigamiStudioApp).container

    setContent {
      val prefs by
        appContainer.preferencesRepository.preferences.collectAsStateWithLifecycle(
          initialValue = StudioPreferences()
        )
      AtpOrigamiStudioTheme(themeMode = prefs.themeMode) {
        OrigamiNavGraph(container = appContainer)
      }
    }
  }
}
