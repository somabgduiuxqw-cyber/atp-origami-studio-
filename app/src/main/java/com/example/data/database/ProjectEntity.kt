package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
  @PrimaryKey val id: String,
  val name: String,
  val widthMm: Double,
  val heightMm: Double,
  val gridSpacingMm: Double,
  val vertexCount: Int,
  val edgeCount: Int,
  val previewSnapshotJson: String,
  val filePath: String,
  val createdAt: Long,
  val updatedAt: Long,
)
