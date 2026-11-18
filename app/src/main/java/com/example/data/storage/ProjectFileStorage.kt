package com.example.data.storage

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProjectFileStorage(private val context: Context) {
  private val projectsDir: File
    get() = File(context.filesDir, "projects").apply { if (!exists()) mkdirs() }

  fun filePathForProject(projectId: String): String {
    return File(projectsDir, "$projectId.atp.json").absolutePath
  }

  suspend fun writeProjectJson(filePath: String, jsonContent: String) {
    withContext(Dispatchers.IO) {
      val target = File(filePath)
      target.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
      val tempFile = File(target.parentFile, "${target.name}.tmp")
      tempFile.writeText(jsonContent, Charsets.UTF_8)
      if (!tempFile.renameTo(target)) {
        target.writeText(jsonContent, Charsets.UTF_8)
        tempFile.delete()
      }
    }
  }

  suspend fun readProjectJson(filePath: String): String? {
    return withContext(Dispatchers.IO) {
      val file = File(filePath)
      if (!file.exists() || !file.isFile) return@withContext null
      runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }
  }

  suspend fun deleteProjectFile(filePath: String) {
    withContext(Dispatchers.IO) {
      val file = File(filePath)
      if (file.exists()) {
        file.delete()
      }
    }
  }
}
