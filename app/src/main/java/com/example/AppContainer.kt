package com.example

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.preferences.StudioPreferencesRepository
import com.example.data.repositories.ProjectRepository
import com.example.data.serializers.ProjectJsonSerializer
import com.example.data.storage.ProjectFileStorage

class AppContainer(context: Context) {
  private val database: AppDatabase by lazy { AppDatabase.create(context) }
  private val fileStorage: ProjectFileStorage by lazy { ProjectFileStorage(context) }
  private val serializer: ProjectJsonSerializer by lazy { ProjectJsonSerializer() }

  val projectRepository: ProjectRepository by lazy {
    ProjectRepository(
      projectDao = database.projectDao(),
      fileStorage = fileStorage,
      serializer = serializer,
    )
  }

  val preferencesRepository: StudioPreferencesRepository by lazy {
    StudioPreferencesRepository(context)
  }
}
