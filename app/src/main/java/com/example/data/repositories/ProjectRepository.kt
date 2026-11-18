package com.example.data.repositories

import com.example.core.geometry.GeometryEngine
import com.example.core.model.CreasePatternDocument
import com.example.core.model.PaperSpec
import com.example.data.database.ProjectDao
import com.example.data.database.ProjectEntity
import com.example.data.model.OrigamiTemplateId
import com.example.data.model.TemplateCatalog
import com.example.data.serializers.PreviewSnapshotDto
import com.example.data.serializers.ProjectJsonSerializer
import com.example.data.storage.ProjectFileStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ProjectSummary(
  val id: String,
  val name: String,
  val widthMm: Double,
  val heightMm: Double,
  val gridSpacingMm: Double,
  val vertexCount: Int,
  val edgeCount: Int,
  val preview: PreviewSnapshotDto?,
  val createdAt: Long,
  val updatedAt: Long,
)

class ProjectRepository(
  private val projectDao: ProjectDao,
  private val fileStorage: ProjectFileStorage,
  private val serializer: ProjectJsonSerializer,
) {
  fun observeProjects(query: String): Flow<List<ProjectSummary>> {
    val flow =
      if (query.isBlank()) {
        projectDao.observeAllProjects()
      } else {
        projectDao.searchProjects(query.trim())
      }
    return flow.map { entities ->
      entities.map { entity ->
        ProjectSummary(
          id = entity.id,
          name = entity.name,
          widthMm = entity.widthMm,
          heightMm = entity.heightMm,
          gridSpacingMm = entity.gridSpacingMm,
          vertexCount = entity.vertexCount,
          edgeCount = entity.edgeCount,
          preview = serializer.deserializePreview(entity.previewSnapshotJson),
          createdAt = entity.createdAt,
          updatedAt = entity.updatedAt,
        )
      }
    }
  }

  suspend fun createProject(
    name: String,
    paper: PaperSpec,
    templateId: OrigamiTemplateId = OrigamiTemplateId.BLANK,
  ): CreasePatternDocument {
    val projectId = GeometryEngine.newId()
    val trimmedName = name.trim().ifEmpty { "Untitled Crease Pattern" }
    val now = System.currentTimeMillis()
    val document =
      TemplateCatalog.buildDocument(
        templateId = templateId,
        projectId = projectId,
        projectName = trimmedName,
        paper = paper,
        now = now,
      )
    saveProject(document)
    return document
  }

  suspend fun loadProject(projectId: String): CreasePatternDocument? {
    val entity = projectDao.getProjectById(projectId) ?: return null
    val rawJson = fileStorage.readProjectJson(entity.filePath)
    if (rawJson != null) {
      val parsed = serializer.deserialize(rawJson)
      if (parsed != null) return parsed
    }
    // Recover minimal valid boundary sheet if file is missing or unreadable
    val recoveredPaper = PaperSpec(entity.widthMm, entity.heightMm, entity.gridSpacingMm)
    val (vertices, edges) =
      GeometryEngine.createBoundaryVerticesAndEdges(entity.widthMm, entity.heightMm)
    return CreasePatternDocument(
      id = entity.id,
      name = entity.name,
      paper = recoveredPaper,
      vertices = vertices,
      edges = edges,
      createdAt = entity.createdAt,
      updatedAt = entity.updatedAt,
    )
  }

  suspend fun saveProject(document: CreasePatternDocument) {
    val existingEntity = projectDao.getProjectById(document.id)
    val filePath = existingEntity?.filePath ?: fileStorage.filePathForProject(document.id)
    val json = serializer.serialize(document)
    fileStorage.writeProjectJson(filePath, json)

    val previewJson = serializer.serializePreview(document)
    val entity =
      ProjectEntity(
        id = document.id,
        name = document.name,
        widthMm = document.paper.widthMm,
        heightMm = document.paper.heightMm,
        gridSpacingMm = document.paper.gridSpacingMm,
        vertexCount = document.vertices.size,
        edgeCount = document.edges.size,
        previewSnapshotJson = previewJson,
        filePath = filePath,
        createdAt = document.createdAt,
        updatedAt = document.updatedAt,
      )
    projectDao.upsertProject(entity)
  }

  suspend fun renameProject(projectId: String, newName: String) {
    val trimmed = newName.trim()
    if (trimmed.isEmpty()) return
    val existing = loadProject(projectId)
    if (existing != null) {
      saveProject(existing.copy(name = trimmed, updatedAt = System.currentTimeMillis()))
    } else {
      projectDao.updateProjectName(projectId, trimmed, System.currentTimeMillis())
    }
  }

  suspend fun duplicateProject(projectId: String): CreasePatternDocument? {
    val original = loadProject(projectId) ?: return null
    val now = System.currentTimeMillis()
    val copy =
      original.copy(
        id = GeometryEngine.newId(),
        name = "${original.name} (Copy)",
        createdAt = now,
        updatedAt = now,
      )
    saveProject(copy)
    return copy
  }

  suspend fun deleteProject(projectId: String) {
    val entity = projectDao.getProjectById(projectId)
    if (entity != null) {
      fileStorage.deleteProjectFile(entity.filePath)
      projectDao.deleteProjectById(projectId)
    }
  }
}
