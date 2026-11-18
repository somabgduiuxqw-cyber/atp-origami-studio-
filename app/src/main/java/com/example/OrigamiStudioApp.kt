package com.example

import android.app.Application

class OrigamiStudioApp : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(applicationContext)
  }
}
