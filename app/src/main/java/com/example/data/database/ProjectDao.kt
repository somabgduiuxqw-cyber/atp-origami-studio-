package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
  @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
  fun observeAllProjects(): Flow<List<ProjectEntity>>

  @Query("SELECT * FROM projects WHERE name LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
  fun searchProjects(query: String): Flow<List<ProjectEntity>>

  @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
  suspend fun getProjectById(id: String): ProjectEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertProject(project: ProjectEntity)

  @Query("UPDATE projects SET name = :name, updatedAt = :updatedAt WHERE id = :id")
  suspend fun updateProjectName(id: String, name: String, updatedAt: Long)

  @Query("DELETE FROM projects WHERE id = :id")
  suspend fun deleteProjectById(id: String)
}
